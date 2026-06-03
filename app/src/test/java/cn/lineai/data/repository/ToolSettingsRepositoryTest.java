package cn.lineai.data.repository;

import cn.lineai.model.McpToolConfig;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolRegistry;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

public final class ToolSettingsRepositoryTest {
    @Test
    public void toolPromptUsesRegisteredToolMetadata() {
        ToolRegistry registry = new ToolRegistry();
        Map<String, BaseTool> toolByName = new LinkedHashMap<>();
        for (BaseTool tool : registry.getAll()) {
            toolByName.put(tool.getName(), tool);
        }
        Set<String> enabled = new LinkedHashSet<>();
        enabled.add("file_read");
        enabled.add("web_search");
        enabled.add("agent");
        enabled.add("agent_pipeline");
        McpToolConfig config = new McpToolConfig(
                "mixed",
                "动态工具",
                "",
                true,
                new String[] {"file_read", "web_search", "agent", "agent_pipeline", "shell_execute"}
        );

        String prompt = ToolSettingsRepository.renderToolPrompt(
                java.util.Collections.singletonList(config),
                enabled,
                toolByName,
                false
        );

        Assert.assertTrue(prompt.contains("file_read [read]"));
        Assert.assertTrue(prompt.contains("读取文件内容"));
        Assert.assertTrue(prompt.contains("\"file_path\""));
        Assert.assertTrue(prompt.contains("web_search [read]"));
        Assert.assertTrue(prompt.contains("\"query\""));
        Assert.assertTrue(prompt.contains("agent [system]"));
        Assert.assertTrue(prompt.contains("agent_pipeline [system]"));
        Assert.assertTrue(prompt.contains("\"depends_on\""));
        Assert.assertFalse(prompt.contains("shell_execute"));
        Assert.assertTrue(prompt.contains("<tool_calls><tool_call name=\"工具名\">"));
    }

    @Test
    public void nativeToolPromptForbidsInlineToolMarkup() {
        ToolRegistry registry = new ToolRegistry();
        Map<String, BaseTool> toolByName = new LinkedHashMap<>();
        for (BaseTool tool : registry.getAll()) {
            toolByName.put(tool.getName(), tool);
        }
        Set<String> enabled = new LinkedHashSet<>();
        enabled.add("file_read");
        McpToolConfig config = new McpToolConfig(
                "file_ops",
                "文件操作",
                "",
                true,
                new String[] {"file_read"}
        );

        String prompt = ToolSettingsRepository.renderToolPrompt(
                java.util.Collections.singletonList(config),
                enabled,
                toolByName,
                true
        );

        Assert.assertTrue(prompt.contains("原生 tools/function calling"));
        Assert.assertTrue(prompt.contains("不要把工具调用 JSON、XML、<tool_calls>"));
    }
}
