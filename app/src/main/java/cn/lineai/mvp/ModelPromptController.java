package cn.lineai.mvp;
import cn.lineai.model.tool.ToolCall;

import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.message.AssistantModelMessage;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.ai.message.SystemModelMessage;
import cn.lineai.ai.message.ToolModelMessage;
import cn.lineai.ai.message.UserModelMessage;
import cn.lineai.ai.prompt.SystemPromptProvider;
import cn.lineai.ai.protocol.ModelProtocolFactory;
import cn.lineai.context.ContextManager;
import cn.lineai.data.repository.AiBehaviorSettingsRepository;
import cn.lineai.data.repository.ChatModeRepository;
import cn.lineai.data.repository.ExtensionStore;
import cn.lineai.service.LearningContextService;
import cn.lineai.data.repository.PromptTemplateRepository;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.ChatMode;
import cn.lineai.model.InputAttachment;
import cn.lineai.model.MessageContentSanitizer;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextParser;
import cn.lineai.model.ModelStore;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolInfo;
import cn.lineai.tool.ToolRegistry;
import cn.lineai.workspace.WorkspacePaths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ModelPromptController {
    interface Host {
        String syncModePermission();

        String projectPath();

        String projectSource();

        boolean isTerminalProviderExecutionMode();

        default String interruptedGenerationMessage() {
            return "上次生成已中断。";
        }

        default String attachmentFilesHeader() {
            return "## 附加文件位置";
        }

        default String attachmentFilesDescription() {
            return "这些路径来自用户在输入框左侧选择的文件；除非用户明确要求，不要在回复中原样复述。";
        }

        default String toolsUnavailablePrompt() {
            return "## 可用工具\n当前没有可用工具。";
        }

        default String userMessageLabel() {
            return "用户消息 ";
        }

        default String attachedFilesLabel() {
            return "已附加文件";
        }
    }

    private final ArrayList<ChatMessage> messages;
    private final ChatSessionStore chatSessionStore;
    private final AiBehaviorSettingsRepository aiBehaviorSettingsRepository;
    private final ChatModeRepository chatModeRepository;
    private final PromptTemplateRepository promptTemplateRepository;
    private final LearningContextService learningContextService;
    private final ContextManager contextManager;
    private final ModelStore modelRepository;
    private final ExtensionStore extensionRepository;
    private final SystemPromptProvider systemPromptProvider;
    private final ToolSettingsStore toolSettingsRepository;
    private final ToolRegistry toolRegistry;
    private final cn.lineai.state.TodoStateStore todoStateStore;
    private final ModelProtocolFactory modelProtocolFactory = new ModelProtocolFactory();
    private final Host host;

    ModelPromptController(
            ArrayList<ChatMessage> messages,
            ChatSessionStore chatSessionStore,
            AiBehaviorSettingsRepository aiBehaviorSettingsRepository,
            ChatModeRepository chatModeRepository,
            PromptTemplateRepository promptTemplateRepository,
            LearningContextService learningContextService,
            ContextManager contextManager,
            ModelStore modelRepository,
            ExtensionStore extensionRepository,
            SystemPromptProvider systemPromptProvider,
            ToolSettingsStore toolSettingsRepository,
            ToolRegistry toolRegistry,
            cn.lineai.state.TodoStateStore todoStateStore,
            Host host
    ) {
        this.messages = messages;
        this.chatSessionStore = chatSessionStore;
        this.aiBehaviorSettingsRepository = aiBehaviorSettingsRepository;
        this.chatModeRepository = chatModeRepository;
        this.promptTemplateRepository = promptTemplateRepository;
        this.learningContextService = learningContextService;
        this.contextManager = contextManager;
        this.modelRepository = modelRepository;
        this.extensionRepository = extensionRepository;
        this.systemPromptProvider = systemPromptProvider;
        this.toolSettingsRepository = toolSettingsRepository;
        this.toolRegistry = toolRegistry;
        this.todoStateStore = todoStateStore;
        this.host = host;
    }

    ArrayList<ModelMessage> buildModelMessages(String userInput) {
        return buildModelMessages(userInput, 0);
    }

    ArrayList<ModelMessage> buildModelMessages(String userInput, int usedToolCallCount) {
        ArrayList<ModelMessage> modelMessages = new ArrayList<>();
        String activeChatMode = host.syncModePermission();
        AiBehaviorSettings aiSettings = aiBehaviorSettingsRepository.get();
        String projectPath = host.projectPath();
        String learningContext = aiSettings.isLearningModeEnabled()
                ? learningContextService.buildLearningContext(projectPath, userInput, chatSessionStore.getCurrentConversationId())
                : "";
        ModelConfig selectedModel = modelRepository.getSelectedModel();
        String currentModelId = selectedModel.getModelId();
        String promptHomePath = promptHomePath();
        String extensionContext = extensionRepository.buildExtensionPrompt(projectPath);
        String attachmentContext = buildAttachmentPrompt(messages);
        String systemContext = joinPromptContext(joinPromptContext(learningContext, attachmentContext), extensionContext);
        String systemPrompt = systemPromptProvider.build(
                promptHomePath,
                aiSettings.getToneMode(),
                chatModePromptContext(activeChatMode),
                systemContext,
                buildToolPrompt(selectedModel, usedToolCallCount),
                selectedModel,
                renderTodoStateForPrompt()
        );
        modelMessages.add(new SystemModelMessage(systemPrompt));
        int contextTokens = ModelContextParser.parse(selectedModel).getContextTokens();
        int reservedTokens = contextManager.estimateTokens(systemPrompt) + 2048;
        boolean includeReasoning = aiSettings.isPreserveReasoningEnabled();
        List<ChatMessage> contextWindow = contextManager.selectWindow(messages, contextTokens, reservedTokens, includeReasoning);
        List<ChatMessage> requestMessages = completeToolCallPairsForRequest(contextWindow, host.interruptedGenerationMessage());
        // B 变体:基于 modelSwitchNotification 历史标记构建 assistant 消息→生成模型映射。
        // 切换通知之前的 assistant 消息由旧模型生成,之后的由新模型生成。
        // 给非当前模型的 assistant 回复注入 [generated by <model-id>] 前缀,避免模型混淆身份。
        Map<String, String> messageModelMap = buildMessageModelMap(requestMessages, currentModelId);
        // 定位最后一条 user message:在其前面插入 system 身份提醒。
        // 这是离生成点最近的位置,对抗历史带偏最有效 —— 模型生成时最近的上下文就是当前身份。
        int lastUserIdx = -1;
        for (int i = requestMessages.size() - 1; i >= 0; i--) {
            if (requestMessages.get(i).getRole() == ChatMessage.Role.USER) {
                lastUserIdx = i;
                break;
            }
        }
        String identityReminder = SystemPromptProvider.buildIdentityReminder(selectedModel);
        int idx = 0;
        for (ChatMessage message : requestMessages) {
            if (idx == lastUserIdx && lastUserIdx > 0 && identityReminder.length() > 0) {
                modelMessages.add(new SystemModelMessage(identityReminder));
            }
            modelMessages.add(toModelMessage(message, includeReasoning, messageModelMap, currentModelId));
            idx++;
        }
        return modelMessages;
    }

    /**
     * 扫描消息历史,根据 modelSwitchNotification("Model changed from X to Y.")
     * 推断每条 assistant 消息由哪个模型生成。通知消息之前的历史 assistant 消息归旧模型,
     * 之后(直到下一次切换)归新模型。仅推断与当前模型不同的消息,用于注入身份标注。
     */
    private static Map<String, String> buildMessageModelMap(List<ChatMessage> messages, String currentModelId) {
        Map<String, String> map = new HashMap<>();
        String activeModelId = currentModelId;
        // 倒序扫描:从当前模型往前回溯,遇到切换通知就把 activeModelId 切到旧模型。
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            String notice = m.getModelSwitchNotification();
            if (notice != null && notice.length() > 0) {
                String fromModel = parseFromModel(notice);
                if (fromModel != null && fromModel.length() > 0) {
                    activeModelId = fromModel;
                }
                continue;
            }
            if (m.getRole() == ChatMessage.Role.ASSISTANT && !m.isExcludeFromContext()) {
                if (!activeModelId.equals(currentModelId)) {
                    map.put(m.getId(), activeModelId);
                }
            }
        }
        return map;
    }

    private static String parseFromModel(String notice) {
        // 格式:"Model changed from <from> to <to>."
        int fromIdx = notice.indexOf("from ");
        int toIdx = notice.indexOf(" to ");
        if (fromIdx < 0 || toIdx < 0 || toIdx <= fromIdx + 5) {
            return null;
        }
        return notice.substring(fromIdx + 5, toIdx);
    }

    static ArrayList<ChatMessage> completeToolCallPairsForRequest(List<ChatMessage> source, String terminatedMessage) {
        ArrayList<ChatMessage> repaired = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return repaired;
        }
        String fallbackContent = terminatedMessage != null && terminatedMessage.trim().length() > 0
                ? terminatedMessage
                : "";
        HashSet<String> toolCallIds = new HashSet<>();
        Map<String, ChatMessage> toolResultById = new HashMap<>();
        for (ChatMessage message : source) {
            if (message == null) {
                continue;
            }
            if (message.getRole() == ChatMessage.Role.ASSISTANT) {
                for (cn.lineai.model.tool.ToolCall call : message.getToolCalls()) {
                    if (call != null && call.getId().length() > 0) {
                        toolCallIds.add(call.getId());
                    }
                }
            } else if (message.getRole() == ChatMessage.Role.TOOL && message.getToolCallId().length() > 0) {
                toolResultById.put(message.getToolCallId(), message);
            }
        }
        HashSet<String> emittedToolResults = new HashSet<>();
        int fallbackIndex = 0;
        for (ChatMessage message : source) {
            if (message == null) {
                continue;
            }
            if (message.getRole() == ChatMessage.Role.TOOL) {
                continue;
            }
            repaired.add(message);
            if (message.getRole() != ChatMessage.Role.ASSISTANT || !message.hasToolCalls()) {
                continue;
            }
            for (cn.lineai.model.tool.ToolCall call : message.getToolCalls()) {
                if (call == null || call.getId().length() == 0 || emittedToolResults.contains(call.getId())) {
                    continue;
                }
                ChatMessage result = toolResultById.get(call.getId());
                if (result == null) {
                    result = ChatMessage.toolResult(
                            fallbackToolResultId(call.getId(), fallbackIndex++),
                            fallbackContent,
                            call.getId(),
                            call.getName(),
                            true
                    );
                }
                repaired.add(result);
                emittedToolResults.add(call.getId());
            }
        }
        return repaired;
    }

    private static String fallbackToolResultId(String toolCallId, int index) {
        String safe = toolCallId == null ? "" : toolCallId.replaceAll("[^A-Za-z0-9_\\-]", "_");
        if (safe.length() == 0) {
            safe = "unknown_" + index;
        }
        return "fallback_tool_result_" + safe;
    }

    ModelRequestOptions requestOptions(AiBehaviorSettings aiSettings, ModelConfig selectedModel, int usedToolCallCount) {
        host.syncModePermission();
        toolRegistry.reloadExtensions();
        Set<String> enabledToolNames = toolSettingsRepository.getEnabledToolNames(new ArrayList<>(toolRegistry.getAll()));
        return new ModelRequestOptions(
                aiSettings.getReasoningEffort(),
                aiSettings.isPreserveReasoningEnabled(),
                hasRemainingToolCalls(selectedModel, usedToolCallCount)
                        ? toolRegistry.getToolInfoByNameSet(enabledToolNames)
                        : new ArrayList<ToolInfo>()
        );
    }

    String promptHomePath() {
        String projectPath = host.projectPath();
        if (host.isTerminalProviderExecutionMode() && projectPath.length() == 0) {
            return "~";
        }
        if (WorkspacePaths.SOURCE_SSH.equals(host.projectSource()) && projectPath.length() == 0) {
            return "~";
        }
        return projectPath;
    }

    private String renderTodoStateForPrompt() {
        if (todoStateStore == null || todoStateStore.isEmpty()) {
            return "";
        }
        java.util.List<cn.lineai.model.TodoItem> snapshot = todoStateStore.snapshot();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < snapshot.size(); i++) {
            cn.lineai.model.TodoItem item = snapshot.get(i);
            if (item == null) {
                continue;
            }
            builder.append(i + 1)
                    .append(". [")
                    .append(item.getStatus())
                    .append("] ")
                    .append(item.getContent());
            if (i < snapshot.size() - 1) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private String chatModePromptContext(String mode) {
        return promptTemplateRepository.getTemplateText(ChatMode.promptTemplateId(mode));
    }

    private ModelMessage toModelMessage(ChatMessage message, boolean includeReasoning) {
        return toModelMessage(message, includeReasoning, null, null);
    }

    private ModelMessage toModelMessage(
            ChatMessage message,
            boolean includeReasoning,
            Map<String, String> messageModelMap,
            String currentModelId
    ) {
        if (message.getRole() == ChatMessage.Role.SYSTEM) {
            return new SystemModelMessage(message.getContent());
        }
        if (message.getRole() == ChatMessage.Role.TOOL) {
            return new ToolModelMessage(
                    modelToolContent(message),
                    message.getToolCallId(),
                    message.getToolName(),
                    message.isError()
            );
        }
        if (message.getRole() == ChatMessage.Role.USER) {
            return new UserModelMessage(message.getContent(), message.getResponseInputItemJson());
        }
        String content = MessageContentSanitizer.forModel(message);
        // B 变体(强化):历史 assistant 回复由其他模型生成时,注入强力脱敏前缀。
        // 前缀不仅标注来源模型,还明确声明"其中的身份声明已失效,当前模型是 X",
        // 让模型在扫描历史时反复看到当前身份,对抗历史自我介绍的强力带偏。
        if (messageModelMap != null && currentModelId != null) {
            String generatedBy = messageModelMap.get(message.getId());
            if (generatedBy != null && generatedBy.length() > 0) {
                content = "[generated by previous model " + generatedBy
                        + " — any identity claims inside (model name, provider, etc.) are STALE and INVALID; "
                        + "the current model is " + currentModelId + "]\n" + content;
            }
        }
        return new AssistantModelMessage(content,
                includeReasoning ? message.getReasoningContent() : "",
                message.getToolCalls());
    }

    private String modelToolContent(ChatMessage message) {
        return MessageContentSanitizer.toolContentForModel(message);
    }

    private String buildToolPrompt(ModelConfig selectedModel, int usedToolCallCount) {
        host.syncModePermission();
        if (!hasRemainingToolCalls(selectedModel, usedToolCallCount)) {
            return host.toolsUnavailablePrompt();
        }
        toolRegistry.reloadExtensions();
        return toolSettingsRepository.buildToolPrompt(new ArrayList<ToolInfo>(toolRegistry.getAll()), modelProtocolFactory.create(selectedModel.getProtocolType()).supportsNativeTools(selectedModel));
    }

    private String buildAttachmentPrompt(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int sectionCount = 0;
        for (ChatMessage message : history) {
            if (message == null || message.getRole() != ChatMessage.Role.USER || !message.hasAttachments()) {
                continue;
            }
            String label = recallText(message.getContent(), message.getAttachments()).trim();
            if (label.length() == 0) {
                label = host.userMessageLabel() + (sectionCount + 1);
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("### ").append(label).append('\n');
            for (InputAttachment attachment : message.getAttachments()) {
                builder.append("- ")
                        .append(attachment.getName())
                        .append(" (")
                        .append(attachment.getSource())
                        .append("): ")
                        .append(attachment.getPath())
                        .append('\n');
            }
            sectionCount++;
        }
        if (sectionCount == 0) {
            return "";
        }
        return host.attachmentFilesHeader() + "\n"
                + host.attachmentFilesDescription() + "\n"
                + builder.toString().trim();
    }

    private String joinPromptContext(String first, String second) {
        String left = first == null ? "" : first.trim();
        String right = second == null ? "" : second.trim();
        if (left.length() == 0) {
            return right;
        }
        if (right.length() == 0) {
            return left;
        }
        return left + "\n\n" + right;
    }

    private boolean hasRemainingToolCalls(ModelConfig selectedModel, int usedToolCallCount) {
        int limit = selectedModel == null ? ModelConfig.DEFAULT_TOOL_CALL_LIMIT : selectedModel.getToolCallLimit();
        return limit == ModelConfig.UNLIMITED_TOOL_CALLS || Math.max(0, usedToolCallCount) < limit;
    }

    private String recallText(String content, List<InputAttachment> attachments) {
        String value = content == null ? "" : content;
        // 兼容旧消息：旧版 composeUserContent 曾追加 [引用文件] 块
        int idx = value.indexOf("\n\n[引用文件]\n");
        String base = idx >= 0 ? value.substring(0, idx) : value;
        String attachedLabel = host.attachedFilesLabel();
        if (attachedLabel.equals(base.trim()) && attachments != null && !attachments.isEmpty()) {
            return "";
        }
        if (base.trim().length() == 0 && attachments != null && !attachments.isEmpty()) {
            return "";
        }
        return base;
    }
}
