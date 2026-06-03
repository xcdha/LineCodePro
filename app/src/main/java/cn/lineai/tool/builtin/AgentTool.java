package cn.lineai.tool.builtin;

import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

public final class AgentTool extends BaseTool {
    public static final String TYPE_EXPLORE = "explore";
    public static final String TYPE_SUB_CODING = "sub-coding";

    @Override
    public String getName() {
        return "agent";
    }

    @Override
    public String getDescription() {
        return "分派一个子 Agent 处理任务。explore 用于只读代码探索，sub-coding 用于边界清晰的编程子任务。";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.SYSTEM;
    }

    @Override
    public JSONObject getParameters() throws org.json.JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("type", new JSONObject()
                                .put("type", "string")
                                .put("enum", new JSONArray().put(TYPE_EXPLORE).put(TYPE_SUB_CODING))
                                .put("description", "Agent 类型：explore 只读探索，sub-coding 编程子任务"))
                        .put("description", new JSONObject()
                                .put("type", "string")
                                .put("description", "3-8 个词的任务标题"))
                        .put("prompt", new JSONObject()
                                .put("type", "string")
                                .put("description", "分派给 Agent 的详细任务、范围、限制和验收方式")))
                .put("required", new JSONArray().put("type").put("description").put("prompt"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        String type = normalizeType(input.optString("type"));
        String description = input.optString("description").trim();
        String prompt = input.optString("prompt").trim();
        if (!TYPE_EXPLORE.equals(type) && !TYPE_SUB_CODING.equals(type)) {
            return error("Agent 类型只能是 explore 或 sub-coding。");
        }
        if (description.length() == 0) {
            return error("Agent description 不能为空。");
        }
        if (prompt.length() == 0) {
            return error("Agent prompt 不能为空。");
        }
        if (context == null || context.getAgentRunner() == null) {
            return error("Agent 执行器未接入，无法运行子 Agent。");
        }
        try {
            JSONObject normalized = new JSONObject(input.toString())
                    .put("type", type)
                    .put("description", description)
                    .put("prompt", prompt);
            return context.getAgentRunner().runAgent(normalized, context);
        } catch (Exception e) {
            return error("Agent 参数解析失败: " + e.getMessage());
        }
    }

    public static String normalizeType(String value) {
        String type = value == null ? "" : value.trim().toLowerCase(java.util.Locale.US);
        if ("sub_coding".equals(type) || "subcoding".equals(type) || "coding".equals(type)) {
            return TYPE_SUB_CODING;
        }
        return type;
    }

    private ToolResult error(String content) {
        return new ToolResult("", getName(), content, true);
    }
}
