package cn.lineai.mvp;

import android.content.Context;
import cn.lineai.R;
import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.ai.ModelCompletionException;
import cn.lineai.context.ContextCompactionResult;
import cn.lineai.context.ContextCompactionService;
import cn.lineai.context.ContextManager;
import cn.lineai.data.repository.AiBehaviorSettingsRepository;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextParser;
import cn.lineai.model.ModelStore;
import cn.lineai.model.SheetOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

final class ContextCompactionController {
    interface Host {
        String nextId();

        void persistCurrentConversation();

        void render();

        void showNotice(String message);

        void startInitialModelRequest(
                int generationId,
                ModelConfig selectedModel,
                ModelCancellationToken cancellationToken,
                String userInput
        );

        void startGenerationKeepAlive();

        void stopGenerationKeepAlive();

        void setCurrentCancellationToken(ModelCancellationToken cancellationToken);

        ModelCancellationToken currentCancellationToken();

        void showSheet(String title, List<SheetOption> options);
    }

    private final Context context;
    private final ArrayList<ChatMessage> messages;
    private final ChatSessionStore chatSessionStore;
    private final ModelStore modelRepository;
    private final AiBehaviorSettingsRepository aiBehaviorSettingsRepository;
    private final ContextCompactionService contextCompactionService;
    private final ContextManager contextManager;
    private final BackgroundTaskRunner backgroundTasks;
    private final MainThreadDispatcher mainThread;
    private final Host host;

    ContextCompactionController(
            Context context,
            ArrayList<ChatMessage> messages,
            ChatSessionStore chatSessionStore,
            ModelStore modelRepository,
            AiBehaviorSettingsRepository aiBehaviorSettingsRepository,
            ContextCompactionService contextCompactionService,
            ContextManager contextManager,
            BackgroundTaskRunner backgroundTasks,
            MainThreadDispatcher mainThread,
            Host host
    ) {
        this.context = context.getApplicationContext();
        this.messages = messages;
        this.chatSessionStore = chatSessionStore;
        this.modelRepository = modelRepository;
        this.aiBehaviorSettingsRepository = aiBehaviorSettingsRepository;
        this.contextCompactionService = contextCompactionService;
        this.contextManager = contextManager;
        this.backgroundTasks = backgroundTasks;
        this.mainThread = mainThread;
        this.host = host;
    }

    boolean shouldAutoCompactBeforeRequest(ModelConfig selectedModel, String activeUserMessageId) {
        if (selectedModel == null) {
            return false;
        }
        AiBehaviorSettings aiSettings = aiBehaviorSettingsRepository.get();
        int contextTokens = ModelContextParser.parse(selectedModel).getContextTokens();
        if (!contextCompactionService.shouldCompact(messages, contextTokens, contextManager, aiSettings.isPreserveReasoningEnabled())) {
            return false;
        }
        ArrayList<ChatMessage> preservedTail = getAutoCompactPreservedTail(activeUserMessageId);
        HashSet<String> preservedIds = messageIdSet(preservedTail);
        for (ChatMessage message : messages) {
            if (preservedIds.contains(message.getId()) || message.isExcludeFromContext()) {
                continue;
            }
            // 已压缩产生的隐藏摘要不再计入"可压缩内容"，避免对摘要反复压缩。
            if (message.isHidden() && message.getResponseInputItemJson().length() > 0) {
                continue;
            }
            if (message.getContent().trim().length() > 0 || message.getReasoningContent().trim().length() > 0 || message.hasToolCalls()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 软触发判断：上下文占用达到 SOFT_COMPACT_TRIGGER_RATIO（50%）但未达硬触发（80%）时，
     * 对最早的一部分消息做增量压缩，保留近期消息不动。需要存在可拆分的 head 才会触发。
     */
    boolean shouldAutoSoftCompactBeforeRequest(ModelConfig selectedModel, String activeUserMessageId) {
        if (selectedModel == null) {
            return false;
        }
        AiBehaviorSettings aiSettings = aiBehaviorSettingsRepository.get();
        int contextTokens = ModelContextParser.parse(selectedModel).getContextTokens();
        if (!contextCompactionService.shouldSoftCompact(messages, contextTokens, contextManager, aiSettings.isPreserveReasoningEnabled())) {
            return false;
        }
        ArrayList<ChatMessage> preservedTail = getAutoCompactPreservedTail(activeUserMessageId);
        HashSet<String> preservedIds = messageIdSet(preservedTail);
        ArrayList<ChatMessage> baseMessages = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (preservedIds.contains(message.getId())) {
                continue;
            }
            baseMessages.add(message);
        }
        List<List<ChatMessage>> split = contextCompactionService.splitForSoftCompact(baseMessages);
        List<ChatMessage> head = split.get(0);
        return hasCompactableBaseMessages(head);
    }

    void showCompactConfirmation() {
        ArrayList<SheetOption> options = new ArrayList<>();
        options.add(new SheetOption("compact:confirm", "确认压缩",
                "把早期上下文总结成隐藏摘要，旧消息仍保留在历史中。", false));
        options.add(new SheetOption("compact:cancel", context.getString(R.string.common_cancel), "返回当前对话", false));
        host.showSheet("压缩上下文", options);
    }

    void startManualContextCompaction() {
        if (chatSessionStore.isStreaming()) {
            return;
        }
        ModelConfig selectedModel = modelRepository.getSelectedModel();
        if (selectedModel == null) {
            host.showNotice("还没有可用模型。请先配置模型，再压缩上下文。");
            return;
        }
        if (messages.size() < 4) {
            host.showNotice("当前上下文不足，无需压缩。");
            return;
        }
        chatSessionStore.ensureCurrentConversation(System.currentTimeMillis());
        int generationId = chatSessionStore.nextGenerationId();
        ModelCancellationToken cancellationToken = new ModelCancellationToken();
        host.setCurrentCancellationToken(cancellationToken);
        chatSessionStore.setStreaming(true);
        host.startGenerationKeepAlive();
        startContextCompaction(generationId, selectedModel, cancellationToken, false, "", "");
    }

    void startContextCompaction(
            int generationId,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            boolean continueAfterCompaction,
            String activeUserMessageId,
            String userInput
    ) {
        ArrayList<ChatMessage> preservedTail = continueAfterCompaction
                ? getAutoCompactPreservedTail(activeUserMessageId)
                : new ArrayList<>();
        HashSet<String> preservedIds = messageIdSet(preservedTail);
        ArrayList<ChatMessage> baseMessages = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (!preservedIds.contains(message.getId())) {
                baseMessages.add(message);
            }
        }
        if (!hasCompactableBaseMessages(baseMessages)) {
            if (continueAfterCompaction) {
                host.startInitialModelRequest(generationId, selectedModel, cancellationToken, userInput);
            } else {
                chatSessionStore.setStreaming(false);
                host.setCurrentCancellationToken(null);
                host.stopGenerationKeepAlive();
                host.render();
            }
            return;
        }
        String progressId = host.nextId();
        messages.add(ChatMessage.compactProgress(progressId, ChatMessage.COMPACT_STATUS_RUNNING));
        host.persistCurrentConversation();
        host.render();

        ArrayList<ChatMessage> baseSnapshot = new ArrayList<>(baseMessages);
        backgroundTasks.execute("linecode-context-compact", () -> {
            try {
                ContextCompactionResult result = contextCompactionService.compact(selectedModel, baseSnapshot, cancellationToken);
                mainThread.post(() -> finishContextCompaction(
                        generationId,
                        selectedModel,
                        cancellationToken,
                        continueAfterCompaction,
                        userInput,
                        baseSnapshot,
                        preservedIds,
                        progressId,
                        result
                ));
            } catch (ModelCompletionException e) {
                mainThread.post(() -> failContextCompaction(
                        generationId,
                        selectedModel,
                        cancellationToken,
                        continueAfterCompaction,
                        userInput,
                        progressId,
                        "上下文压缩失败：" + e.getMessage()
                ));
            } catch (OutOfMemoryError oom) {
                // 兜底：service 内部已捕获 OOM 并转换为 ModelCompletionException，
                // 但 modelClient/responsesSummaryProtocol 自身的字符串累积仍可能
                // 在后台线程上抛 OOM。这里释放 baseSnapshot 引用并降级到失败路径，
                // 避免整个进程被默认 UncaughtExceptionHandler 杀掉。
                baseSnapshot.clear();
                System.gc();
                mainThread.post(() -> failContextCompaction(
                        generationId,
                        selectedModel,
                        cancellationToken,
                        continueAfterCompaction,
                        userInput,
                        progressId,
                        "上下文过大，压缩时内存不足，请手动清理早期对话后重试"
                ));
            }
        });
    }

    /**
     * 启动软触发增量压缩：把早期消息（head）压缩成摘要，保留近期消息（tail）不动。
     * 与 {@link #startContextCompaction} 不同，这里只压缩最早的一部分消息，
     * 避免一次性处理大量历史导致 transcript 字符串过大。
     */
    void startSoftContextCompaction(
            int generationId,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            boolean continueAfterCompaction,
            String activeUserMessageId,
            String userInput
    ) {
        ArrayList<ChatMessage> preservedTail = continueAfterCompaction
                ? getAutoCompactPreservedTail(activeUserMessageId)
                : new ArrayList<>();
        HashSet<String> preservedIds = messageIdSet(preservedTail);
        ArrayList<ChatMessage> baseMessages = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (!preservedIds.contains(message.getId())) {
                baseMessages.add(message);
            }
        }
        List<List<ChatMessage>> split = contextCompactionService.splitForSoftCompact(baseMessages);
        ArrayList<ChatMessage> headSnapshot = new ArrayList<>(split.get(0));
        if (!hasCompactableBaseMessages(headSnapshot)) {
            // 没有可压缩的 head：直接继续后续请求，不阻塞流程。
            if (continueAfterCompaction) {
                host.startInitialModelRequest(generationId, selectedModel, cancellationToken, userInput);
            } else {
                chatSessionStore.setStreaming(false);
                host.setCurrentCancellationToken(null);
                host.stopGenerationKeepAlive();
                host.render();
            }
            return;
        }
        String progressId = host.nextId();
        messages.add(ChatMessage.compactProgress(progressId, ChatMessage.COMPACT_STATUS_RUNNING));
        host.persistCurrentConversation();
        host.render();

        backgroundTasks.execute("linecode-context-compact-soft", () -> {
            try {
                ContextCompactionResult result = contextCompactionService.compact(selectedModel, headSnapshot, cancellationToken);
                mainThread.post(() -> finishSoftContextCompaction(
                        generationId,
                        selectedModel,
                        cancellationToken,
                        continueAfterCompaction,
                        userInput,
                        headSnapshot,
                        preservedIds,
                        progressId,
                        result
                ));
            } catch (ModelCompletionException e) {
                mainThread.post(() -> failContextCompaction(
                        generationId,
                        selectedModel,
                        cancellationToken,
                        continueAfterCompaction,
                        userInput,
                        progressId,
                        "上下文压缩失败：" + e.getMessage()
                ));
            } catch (OutOfMemoryError oom) {
                headSnapshot.clear();
                System.gc();
                mainThread.post(() -> failContextCompaction(
                        generationId,
                        selectedModel,
                        cancellationToken,
                        continueAfterCompaction,
                        userInput,
                        progressId,
                        "上下文过大，压缩时内存不足，请手动清理早期对话后重试"
                ));
            }
        });
    }

    private void finishContextCompaction(
            int generationId,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            boolean continueAfterCompaction,
            String userInput,
            ArrayList<ChatMessage> baseSnapshot,
            HashSet<String> preservedIds,
            String progressId,
            ContextCompactionResult result
    ) {
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            markCompactProgress(progressId, ChatMessage.COMPACT_STATUS_ERROR, false);
            host.persistCurrentConversation();
            host.render();
            return;
        }
        if (!chatSessionStore.isActiveGeneration(generationId)) {
            return;
        }
        if (result == null || result.getSummaryContent().trim().length() == 0) {
            failContextCompaction(generationId, selectedModel, cancellationToken, continueAfterCompaction, userInput,
                    progressId, "上下文压缩失败：模型没有返回摘要。");
            return;
        }
        HashSet<String> baseIds = messageIdSet(baseSnapshot);
        ArrayList<ChatMessage> compacted = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (progressId.equals(message.getId()) || preservedIds.contains(message.getId())) {
                continue;
            }
            compacted.add(baseIds.contains(message.getId()) ? message.withExcludeFromContext(true) : message);
        }
        // 摘要必须进入上下文（excludeFromContext=false），否则模型侧会像"上下文被清空"一样丢失历史。
        // 注意：不能用 .withResponseInputItemJson(...) 链式构造，它会基于当前 excludeFromContext 副本，
        // 这里显式传 false 保证摘要一定进上下文。
        ChatMessage summaryMessage = new ChatMessage(
                host.nextId(),
                ChatMessage.Role.USER,
                result.getSummaryContent(),
                "",
                false,
                true,
                false,
                Collections.emptyList(),
                Collections.emptyList(),
                "",
                "",
                false,
                "",
                "",
                "",
                "",
                result.getResponseInputItemJson());
        compacted.add(summaryMessage);
        for (ChatMessage message : messages) {
            if (preservedIds.contains(message.getId())) {
                compacted.add(message);
            }
        }
        compacted.add(ChatMessage.compactProgress(progressId, ChatMessage.COMPACT_STATUS_DONE)
                .withCompactStatus(ChatMessage.COMPACT_STATUS_DONE, false));
        messages.clear();
        messages.addAll(compacted);
        host.persistCurrentConversation();
        host.render();
        if (continueAfterCompaction) {
            host.startInitialModelRequest(generationId, selectedModel, cancellationToken, userInput);
            return;
        }
        chatSessionStore.setStreaming(false);
        host.setCurrentCancellationToken(null);
        host.stopGenerationKeepAlive();
        host.render();
    }

    /**
     * 软触发压缩完成后的合并逻辑：仅把 headSnapshot 中的消息标记 excludeFromContext，
     * 摘要插在 head 之后；tail（不在 head 也不在 preservedTail 的近期消息）原样保留。
     * 这保证近期上下文不被压缩，模型仍能看到最近几轮的真实对话。
     */
    private void finishSoftContextCompaction(
            int generationId,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            boolean continueAfterCompaction,
            String userInput,
            ArrayList<ChatMessage> headSnapshot,
            HashSet<String> preservedIds,
            String progressId,
            ContextCompactionResult result
    ) {
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            markCompactProgress(progressId, ChatMessage.COMPACT_STATUS_ERROR, false);
            host.persistCurrentConversation();
            host.render();
            return;
        }
        if (!chatSessionStore.isActiveGeneration(generationId)) {
            return;
        }
        if (result == null || result.getSummaryContent().trim().length() == 0) {
            failContextCompaction(generationId, selectedModel, cancellationToken, continueAfterCompaction, userInput,
                    progressId, "上下文压缩失败：模型没有返回摘要。");
            return;
        }
        HashSet<String> headIds = messageIdSet(headSnapshot);
        // 期望最终顺序：[head(标记 excludeFromContext)] + [summary] + [tail(原样)] + [preservedTail] + [progressDone]
        // head 是被压缩成摘要的早期消息；tail 是保留的近期消息；preservedTail 是当前 user 消息或
        // assistant+tool 序列。把摘要插在 head 与 tail 之间，让模型看到"前情提要 → 近期上下文 → 当前问题"。
        ArrayList<ChatMessage> headCompacted = new ArrayList<>();
        ArrayList<ChatMessage> tailCompacted = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (progressId.equals(message.getId()) || preservedIds.contains(message.getId())) {
                continue;
            }
            if (headIds.contains(message.getId())) {
                headCompacted.add(message.withExcludeFromContext(true));
            } else {
                tailCompacted.add(message);
            }
        }
        ChatMessage summaryMessage = new ChatMessage(
                host.nextId(),
                ChatMessage.Role.USER,
                result.getSummaryContent(),
                "",
                false,
                true,
                false,
                Collections.emptyList(),
                Collections.emptyList(),
                "",
                "",
                false,
                "",
                "",
                "",
                "",
                result.getResponseInputItemJson());
        ArrayList<ChatMessage> compacted = new ArrayList<>();
        compacted.addAll(headCompacted);
        compacted.add(summaryMessage);
        compacted.addAll(tailCompacted);
        for (ChatMessage message : messages) {
            if (preservedIds.contains(message.getId())) {
                compacted.add(message);
            }
        }
        compacted.add(ChatMessage.compactProgress(progressId, ChatMessage.COMPACT_STATUS_DONE)
                .withCompactStatus(ChatMessage.COMPACT_STATUS_DONE, false));
        messages.clear();
        messages.addAll(compacted);
        host.persistCurrentConversation();
        host.render();
        if (continueAfterCompaction) {
            host.startInitialModelRequest(generationId, selectedModel, cancellationToken, userInput);
            return;
        }
        chatSessionStore.setStreaming(false);
        host.setCurrentCancellationToken(null);
        host.stopGenerationKeepAlive();
        host.render();
    }

    private void failContextCompaction(
            int generationId,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            boolean continueAfterCompaction,
            String userInput,
            String progressId,
            String message
    ) {
        if (!chatSessionStore.isActiveGeneration(generationId)) {
            return;
        }
        markCompactProgress(progressId, ChatMessage.COMPACT_STATUS_ERROR, false);
        if (message != null && message.trim().length() > 0) {
            messages.add(new ChatMessage(host.nextId(), ChatMessage.Role.ASSISTANT, message, false)
                    .withExcludeFromContext(true));
        }
        host.persistCurrentConversation();
        host.render();
        if (continueAfterCompaction && (cancellationToken == null || !cancellationToken.isCancelled())) {
            host.startInitialModelRequest(generationId, selectedModel, cancellationToken, userInput);
            return;
        }
        chatSessionStore.setStreaming(false);
        host.setCurrentCancellationToken(null);
        host.stopGenerationKeepAlive();
        host.render();
    }

    private ArrayList<ChatMessage> getAutoCompactPreservedTail(String activeUserMessageId) {
        ArrayList<ChatMessage> contextMessages = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (!message.isExcludeFromContext()) {
                contextMessages.add(message);
            }
        }
        if (contextMessages.isEmpty()) {
            return new ArrayList<>();
        }
        ChatMessage last = contextMessages.get(contextMessages.size() - 1);
        if (last.getRole() == ChatMessage.Role.USER
                && (activeUserMessageId == null || activeUserMessageId.length() == 0 || activeUserMessageId.equals(last.getId()))) {
            ArrayList<ChatMessage> tail = new ArrayList<>();
            tail.add(last);
            return tail;
        }
        if (last.getRole() == ChatMessage.Role.TOOL) {
            for (int i = contextMessages.size() - 1; i >= 0; i--) {
                ChatMessage message = contextMessages.get(i);
                if (message.getRole() == ChatMessage.Role.ASSISTANT && message.hasToolCalls()) {
                    return new ArrayList<>(contextMessages.subList(i, contextMessages.size()));
                }
            }
        }
        return new ArrayList<>();
    }

    private HashSet<String> messageIdSet(List<ChatMessage> source) {
        HashSet<String> ids = new HashSet<>();
        if (source == null) {
            return ids;
        }
        for (ChatMessage message : source) {
            if (message != null) {
                ids.add(message.getId());
            }
        }
        return ids;
    }

    private boolean hasCompactableBaseMessages(List<ChatMessage> source) {
        if (source == null) {
            return false;
        }
        for (ChatMessage message : source) {
            if (message == null || message.isExcludeFromContext()) {
                continue;
            }
            // 已压缩产生的隐藏摘要不是"待压缩"内容。
            if (message.isHidden() && message.getResponseInputItemJson().length() > 0) {
                continue;
            }
            if (message.isCompactBlock()) {
                continue;
            }
            if (message.getContent().trim().length() > 0
                    || message.getReasoningContent().trim().length() > 0
                    || message.hasToolCalls()
                    || message.getResponseInputItemJson().length() > 0) {
                return true;
            }
        }
        return false;
    }

    private void markCompactProgress(String progressId, String status, boolean nextStreaming) {
        int index = findMessageIndex(progressId);
        if (index < 0) {
            return;
        }
        messages.set(index, messages.get(index).withCompactStatus(status, nextStreaming));
    }

    private int findMessageIndex(String id) {
        if (id == null || id.length() == 0) {
            return -1;
        }
        for (int i = 0; i < messages.size(); i++) {
            if (id.equals(messages.get(i).getId())) {
                return i;
            }
        }
        return -1;
    }
}
