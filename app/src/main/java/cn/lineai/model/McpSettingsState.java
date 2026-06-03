package cn.lineai.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class McpSettingsState {
    private final String executionMode;
    private final List<McpToolConfig> configs;
    private final WebSearchConfig webSearchConfig;

    public McpSettingsState(String executionMode, List<McpToolConfig> configs) {
        this(executionMode, configs, WebSearchConfig.defaultConfig());
    }

    public McpSettingsState(String executionMode, List<McpToolConfig> configs, WebSearchConfig webSearchConfig) {
        this.executionMode = executionMode == null ? "local" : executionMode;
        this.configs = configs == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(configs));
        this.webSearchConfig = webSearchConfig == null ? WebSearchConfig.defaultConfig() : webSearchConfig;
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public List<McpToolConfig> getConfigs() {
        return configs;
    }

    public WebSearchConfig getWebSearchConfig() {
        return webSearchConfig;
    }
}
