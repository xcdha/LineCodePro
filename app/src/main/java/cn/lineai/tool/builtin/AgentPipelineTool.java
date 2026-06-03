package cn.lineai.tool.builtin;

import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

public final class AgentPipelineTool extends BaseTool {
    @Override
    public String getName() {
        return "agent_pipeline";
    }

    @Override
    public String getDescription() {
        return "创建多个有依赖关系的 Agent 任务流水线。无依赖的任务可作为同一层级处理，有依赖的任务会等待上游结果。";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.SYSTEM;
    }

    @Override
    public JSONObject getParameters() throws org.json.JSONException {
        JSONObject agentSchema = new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("id", new JSONObject()
                                .put("type", "string")
                                .put("description", "流水线内唯一标识"))
                        .put("type", new JSONObject()
                                .put("type", "string")
                                .put("enum", new JSONArray().put(AgentTool.TYPE_EXPLORE).put(AgentTool.TYPE_SUB_CODING))
                                .put("description", "Agent 类型"))
                        .put("description", new JSONObject()
                                .put("type", "string")
                                .put("description", "简短任务标题"))
                        .put("prompt", new JSONObject()
                                .put("type", "string")
                                .put("description", "详细任务描述"))
                        .put("depends_on", new JSONObject()
                                .put("type", "array")
                                .put("items", new JSONObject().put("type", "string"))
                                .put("description", "依赖的 Agent ID 列表")))
                .put("required", new JSONArray().put("id").put("type").put("description").put("prompt"));

        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("agents", new JSONObject()
                                .put("type", "array")
                                .put("items", agentSchema)
                                .put("description", "Agent 任务列表")))
                .put("required", new JSONArray().put("agents"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        JSONArray agents = input.optJSONArray("agents");
        if (agents == null || agents.length() == 0) {
            return error("agent_pipeline.agents 不能为空。");
        }
        for (int i = 0; i < agents.length(); i++) {
            JSONObject agent = agents.optJSONObject(i);
            if (agent == null) {
                return error("agents[" + i + "] 必须是对象。");
            }
            if (agent.optString("id").trim().length() == 0) {
                return error("agents[" + i + "].id 不能为空。");
            }
            String id = agent.optString("id").trim();
            JSONArray dependencies = agent.optJSONArray("depends_on");
            if (dependencies != null) {
                for (int j = 0; j < dependencies.length(); j++) {
                    if (id.equals(dependencies.optString(j).trim())) {
                        return error("Agent 不能依赖自身: " + id);
                    }
                }
            }
            String type = AgentTool.normalizeType(agent.optString("type"));
            if (!AgentTool.TYPE_EXPLORE.equals(type) && !AgentTool.TYPE_SUB_CODING.equals(type)) {
                return error("agents[" + i + "].type 只能是 explore 或 sub-coding。");
            }
            if (agent.optString("description").trim().length() == 0) {
                return error("agents[" + i + "].description 不能为空。");
            }
            if (agent.optString("prompt").trim().length() == 0) {
                return error("agents[" + i + "].prompt 不能为空。");
            }
        }
        if (context == null || context.getAgentRunner() == null) {
            return error("Agent 流水线执行器未接入，无法运行。");
        }
        return context.getAgentRunner().runAgentPipeline(input, context);
    }

    private ToolResult error(String content) {
        return new ToolResult("", getName(), content, true);
    }
}
