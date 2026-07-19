package cn.lineai.tool.builtin;

import cn.lineai.tool.BaseTool;
import cn.lineai.tool.R;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import cn.lineai.tool.ToolResult;
import java.util.ArrayList;
import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONObject;

public final class AgentPipelineTool extends BaseTool {
    public static final String NAME = "agent_pipeline";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean isAllowedInReadonlyMode() {
        return true;
    }

    @Override
    public String getDescription() {
        return "创建多个有依赖关系的 Agent 任务流水线。sub-coding 必须声明唯一 write_scope，多个 Agent 不能写同一文件或重叠目录。";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.SYSTEM;
    }

    @Override
    public ToolDisplayCategory getDisplayCategory() {
        return ToolDisplayCategory.AGENT_PIPELINE;
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
                                .put("description", "详细任务描述。必须写明任务边界、验收方式，以及不能修改 write_scope 外的文件"))
                        .put("read_scope", new JSONObject()
                                .put("type", "array")
                                .put("items", new JSONObject().put("type", "string"))
                                .put("description", "允许读取的文件或目录路径列表。为空时仍应只读取完成任务所需的最小范围"))
                        .put("write_scope", new JSONObject()
                                .put("type", "array")
                                .put("items", new JSONObject().put("type", "string"))
                                .put("description", "sub-coding 允许写入的唯一文件或目录路径列表；explore 必须留空。多个 Agent 的 write_scope 不能相同、包含或被包含"))
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
            return error(context.getString(R.string.tool_pipeline_agents_empty));
        }
        HashSet<String> ids = new HashSet<>();
        ArrayList<WriteScopeOwner> writeScopes = new ArrayList<>();
        for (int i = 0; i < agents.length(); i++) {
            JSONObject agent = agents.optJSONObject(i);
            if (agent == null) {
                return error(context.getString(R.string.tool_pipeline_agent_not_object, i));
            }
            if (agent.optString("id").trim().length() == 0) {
                return error(context.getString(R.string.tool_pipeline_agent_id_empty, i));
            }
            String id = agent.optString("id").trim();
            if (ids.contains(id)) {
                return error(context.getString(R.string.tool_pipeline_agent_id_duplicate, id));
            }
            ids.add(id);
            JSONArray dependencies = agent.optJSONArray("depends_on");
            if (dependencies != null) {
                for (int j = 0; j < dependencies.length(); j++) {
                    if (id.equals(dependencies.optString(j).trim())) {
                        return error(context.getString(R.string.tool_pipeline_agent_self_depend, id));
                    }
                }
            }
            String type = AgentTool.normalizeType(agent.optString("type"));
            if (!AgentTool.TYPE_EXPLORE.equals(type) && !AgentTool.TYPE_SUB_CODING.equals(type)) {
                return error(context.getString(R.string.tool_pipeline_agent_invalid_type, i));
            }
            ArrayList<String> writeScope = scopeList(agent.optJSONArray("write_scope"));
            if (AgentTool.TYPE_EXPLORE.equals(type) && !writeScope.isEmpty()) {
                return error(context.getString(R.string.tool_pipeline_explore_no_write, id));
            }
            if (AgentTool.TYPE_SUB_CODING.equals(type) && writeScope.isEmpty()) {
                return error(context.getString(R.string.tool_pipeline_coding_needs_write, id));
            }
            HashSet<String> localScopes = new HashSet<>();
            for (String scope : writeScope) {
                String normalizedScope = normalizeScope(scope);
                if (normalizedScope.length() == 0) {
                    continue;
                }
                if (localScopes.contains(normalizedScope)) {
                    return error(context.getString(R.string.tool_pipeline_scope_duplicate, id, scope));
                }
                localScopes.add(normalizedScope);
                for (WriteScopeOwner owner : writeScopes) {
                    if (scopesOverlap(normalizedScope, owner.scope)) {
                        return error(context.getString(R.string.tool_pipeline_scope_overlap,
                                owner.agentId, owner.originalScope, id, scope));
                    }
                }
                writeScopes.add(new WriteScopeOwner(id, normalizedScope, scope));
            }
            if (agent.optString("description").trim().length() == 0) {
                return error(context.getString(R.string.tool_pipeline_agent_description_empty, i));
            }
            if (agent.optString("prompt").trim().length() == 0) {
                return error(context.getString(R.string.tool_pipeline_agent_prompt_empty, i));
            }
        }
        if (context == null || context.getAgentRunner() == null) {
            return error(context.getString(R.string.tool_pipeline_runner_not_available));
        }
        return context.getAgentRunner().runAgentPipeline(input, context);
    }

    private ArrayList<String> scopeList(JSONArray array) {
        ArrayList<String> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i).trim();
            if (value.length() > 0) {
                values.add(value);
            }
        }
        return values;
    }

    private String normalizeScope(String value) {
        String text = value == null ? "" : value.trim().replace('\\', '/');
        while (text.startsWith("./")) {
            text = text.substring(2);
        }
        while (text.contains("//")) {
            text = text.replace("//", "/");
        }
        while (text.endsWith("/") && text.length() > 1) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private boolean scopesOverlap(String left, String right) {
        if (left.length() == 0 || right.length() == 0) {
            return false;
        }
        if (".".equals(left) || ".".equals(right) || "/".equals(left) || "/".equals(right)) {
            return true;
        }
        return left.equals(right)
                || left.startsWith(right + "/")
                || right.startsWith(left + "/");
    }

    private static final class WriteScopeOwner {
        private final String agentId;
        private final String scope;
        private final String originalScope;

        WriteScopeOwner(String agentId, String scope, String originalScope) {
            this.agentId = agentId == null ? "" : agentId;
            this.scope = scope == null ? "" : scope;
            this.originalScope = originalScope == null ? "" : originalScope;
        }
    }
}
