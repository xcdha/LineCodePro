package cn.lineai.ai.prompt;

import cn.lineai.data.service.SkillFileManager;
import cn.lineai.model.ExtensionAgentConfig;
import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.model.McpToolSummary;
import cn.lineai.model.SkillRecord;
import java.util.ArrayList;
import java.util.List;

/**
 * Skill 扩展提示词构建器。
 * 将 Agent、MCP 和 Skill 扩展数据拼装为注入 system prompt 的文本。
 * 不持有仓库引用，所有数据通过方法参数传入。
 */
public final class SkillPromptBuilder {

    private static final int MAX_SKILL_PROMPT_CHARS = 18000;

    private final SkillFileManager fileManager;

    public SkillPromptBuilder(SkillFileManager fileManager) {
        this.fileManager = fileManager;
    }

    /**
     * 构造注入到 system prompt 的扩展文本。
     *
     * @param agents 代理扩展列表
     * @param mcps   MCP 扩展列表
     * @param skills Skill 列表
     * @return 拼装好的提示词文本，无内容时返回空字符串
     */
    public String buildExtensionPrompt(
            List<ExtensionAgentConfig> agents,
            List<ExtensionMcpConfig> mcps,
            List<SkillRecord> skills
    ) {
        StringBuilder builder = new StringBuilder();
        boolean hasContent = false;

        builder.append("## 扩展\n以下扩展来自设置里的\u300c扩展\u300d页面。自定义 Agent、HTTP MCP 和 Skills 都由 SQLite 配置动态注入。\n");

        ArrayList<ExtensionAgentConfig> enabledAgents = new ArrayList<>();
        for (ExtensionAgentConfig agent : agents) {
            if (agent.isEnabled()) {
                enabledAgents.add(agent);
            }
        }
        if (!enabledAgents.isEmpty()) {
            hasContent = true;
            builder.append("\n### 自定义 Agent\n");
            for (ExtensionAgentConfig agent : enabledAgents) {
                builder.append("- ").append(agent.getName()).append(" (").append(agent.getSlug()).append(")\n");
                if (agent.getTrigger().length() > 0) {
                    builder.append("  - 触发条件: ").append(agent.getTrigger()).append('\n');
                }
                if (agent.getPrompt().length() > 0) {
                    builder.append("  - Agent 提示词: ").append(limitInline(agent.getPrompt(), 1600)).append('\n');
                }
                builder.append("  - 工具: ").append(join(agent.getToolNames(), ", ", "无")).append('\n');
                builder.append("  - MCP: ").append(join(agent.getMcpIds(), ", ", "无")).append('\n');
            }
        }

        ArrayList<ExtensionMcpConfig> enabledMcps = new ArrayList<>();
        for (ExtensionMcpConfig mcp : mcps) {
            if (mcp.isEnabled()) {
                enabledMcps.add(mcp);
            }
        }
        if (!enabledMcps.isEmpty()) {
            hasContent = true;
            builder.append("\n### 自定义 HTTP MCP\n");
            for (ExtensionMcpConfig mcp : enabledMcps) {
                builder.append("- ").append(mcp.getName()).append(": ").append(mcp.getUrl())
                        .append(" (").append(enabledToolNames(mcp)).append(")\n");
            }
        }

        ArrayList<SkillRecord> enabledSkills = new ArrayList<>();
        for (SkillRecord skill : skills) {
            if (skill.isEnabled()) {
                enabledSkills.add(skill);
            }
        }
        if (!enabledSkills.isEmpty()) {
            hasContent = true;
            builder.append("\n### 已安装 Skills\n");
            int usedChars = 0;
            for (SkillRecord skill : enabledSkills) {
                String header = "#### Skill: " + skill.getName()
                        + "\n安装位置: " + skill.getLocationLabel()
                        + "\nSKILL.md: " + skill.getSkillMdPath()
                        + "\nRoot: " + skill.getRootPath();
                String body = fileManager.readSkillPrompt(skill);
                String block = body.length() == 0 ? header : header + "\n\n" + body;
                if (usedChars + block.length() > MAX_SKILL_PROMPT_CHARS) {
                    builder.append("#### Skills 提示词已截断\n已达到提示词长度上限，剩余 Skills 仅按路径和工具描述处理。\n");
                    break;
                }
                builder.append(block).append("\n\n");
                usedChars += block.length();
            }
        }

        return hasContent ? builder.toString().trim() : "";
    }

    private String enabledToolNames(ExtensionMcpConfig mcp) {
        ArrayList<String> names = new ArrayList<>();
        for (McpToolSummary tool : mcp.getTools()) {
            if (tool.isEnabled()) {
                names.add(tool.getName());
            }
        }
        return join(names, ", ", "未启用 tools");
    }

    private String join(List<String> values, String separator, String empty) {
        if (values == null || values.isEmpty()) {
            return empty;
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(separator);
            }
            builder.append(value);
        }
        return builder.length() == 0 ? empty : builder.toString();
    }

    private String limitInline(String value, int maxChars) {
        String text = safe(value).replace('\r', '\n').replace("\n", "\\n").trim();
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
