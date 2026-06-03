package cn.lineai.tool;

import org.json.JSONObject;

public final class ToolContext {
    public interface AgentRunner {
        ToolResult runAgent(JSONObject input, ToolContext context);

        ToolResult runAgentPipeline(JSONObject input, ToolContext context);
    }

    private final String homePath;
    private final AgentRunner agentRunner;
    private final String toolCallId;

    public ToolContext(String homePath) {
        this(homePath, null, "");
    }

    public ToolContext(String homePath, AgentRunner agentRunner) {
        this(homePath, agentRunner, "");
    }

    public ToolContext(String homePath, AgentRunner agentRunner, String toolCallId) {
        this.homePath = homePath == null ? "" : homePath;
        this.agentRunner = agentRunner;
        this.toolCallId = toolCallId == null ? "" : toolCallId;
    }

    public String getHomePath() {
        return homePath;
    }

    public AgentRunner getAgentRunner() {
        return agentRunner;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public ToolContext withToolCallId(String nextToolCallId) {
        return new ToolContext(homePath, agentRunner, nextToolCallId);
    }
}
