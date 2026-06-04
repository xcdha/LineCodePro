package cn.lineai.model;

import java.util.Collections;
import java.util.List;

public final class ChatUiState {
    private final String projectLabel;
    private final String projectPath;
    private final String modelLabel;
    private final String contextLabel;
    private final int contextPercent;
    private final boolean streaming;
    private final boolean hasConfiguredModel;
    private final boolean thinkingScrollEnabled;
    private final boolean thinkingAutoExpandEnabled;
    private final boolean codeWrapEnabled;
    private final String browserMode;
    private final String chatMode;
    private final List<ChatMessage> messages;

    public ChatUiState(
            String projectLabel,
            String projectPath,
            String modelLabel,
            String contextLabel,
            int contextPercent,
            boolean streaming,
            boolean hasConfiguredModel,
            List<ChatMessage> messages
    ) {
        this(
                projectLabel,
                projectPath,
                modelLabel,
                contextLabel,
                contextPercent,
                streaming,
                hasConfiguredModel,
                true,
                false,
                false,
                OutputSettings.BROWSER_BUILTIN,
                ChatMode.DEFAULT,
                messages
        );
    }

    public ChatUiState(
            String projectLabel,
            String projectPath,
            String modelLabel,
            String contextLabel,
            int contextPercent,
            boolean streaming,
            boolean hasConfiguredModel,
            boolean thinkingScrollEnabled,
            boolean thinkingAutoExpandEnabled,
            List<ChatMessage> messages
    ) {
        this(
                projectLabel,
                projectPath,
                modelLabel,
                contextLabel,
                contextPercent,
                streaming,
                hasConfiguredModel,
                thinkingScrollEnabled,
                thinkingAutoExpandEnabled,
                false,
                OutputSettings.BROWSER_BUILTIN,
                ChatMode.DEFAULT,
                messages
        );
    }

    public ChatUiState(
            String projectLabel,
            String projectPath,
            String modelLabel,
            String contextLabel,
            int contextPercent,
            boolean streaming,
            boolean hasConfiguredModel,
            boolean thinkingScrollEnabled,
            boolean thinkingAutoExpandEnabled,
            boolean codeWrapEnabled,
            String browserMode,
            List<ChatMessage> messages
    ) {
        this(
                projectLabel,
                projectPath,
                modelLabel,
                contextLabel,
                contextPercent,
                streaming,
                hasConfiguredModel,
                thinkingScrollEnabled,
                thinkingAutoExpandEnabled,
                codeWrapEnabled,
                browserMode,
                ChatMode.DEFAULT,
                messages
        );
    }

    public ChatUiState(
            String projectLabel,
            String projectPath,
            String modelLabel,
            String contextLabel,
            int contextPercent,
            boolean streaming,
            boolean hasConfiguredModel,
            boolean thinkingScrollEnabled,
            boolean thinkingAutoExpandEnabled,
            boolean codeWrapEnabled,
            String browserMode,
            String chatMode,
            List<ChatMessage> messages
    ) {
        this.projectLabel = projectLabel;
        this.projectPath = projectPath == null ? "" : projectPath;
        this.modelLabel = modelLabel;
        this.contextLabel = contextLabel;
        this.contextPercent = contextPercent;
        this.streaming = streaming;
        this.hasConfiguredModel = hasConfiguredModel;
        this.thinkingScrollEnabled = thinkingScrollEnabled;
        this.thinkingAutoExpandEnabled = thinkingAutoExpandEnabled;
        this.codeWrapEnabled = codeWrapEnabled;
        this.browserMode = OutputSettings.normalizeBrowserMode(browserMode);
        this.chatMode = ChatMode.normalize(chatMode);
        this.messages = messages == null ? Collections.emptyList() : Collections.unmodifiableList(messages);
    }

    public String getProjectLabel() {
        return projectLabel;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public String getModelLabel() {
        return modelLabel;
    }

    public String getContextLabel() {
        return contextLabel;
    }

    public int getContextPercent() {
        return contextPercent;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public boolean hasConfiguredModel() {
        return hasConfiguredModel;
    }

    public boolean isThinkingScrollEnabled() {
        return thinkingScrollEnabled;
    }

    public boolean isThinkingAutoExpandEnabled() {
        return thinkingAutoExpandEnabled;
    }

    public boolean isCodeWrapEnabled() {
        return codeWrapEnabled;
    }

    public String getBrowserMode() {
        return browserMode;
    }

    public String getChatMode() {
        return chatMode;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }
}
