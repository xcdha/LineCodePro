package cn.lineai.tool.builtin;

import cn.lineai.service.LineCodeAccessibilityService;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class PhoneClickViewByIdTool extends BaseTool {
    @Override
    public String getName() {
        return "phone_click_view_by_id";
    }

    @Override
    public String getDescription() {
        return "根据 View 的 resource id 点击当前窗口中可点击的视图。resource_id 可传完整 id（如 package:id/name）或简写 name。";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.SYSTEM;
    }

    @Override
    public JSONObject getParameters() throws JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("resource_id", new JSONObject().put("type", "string").put("description", "View 的 resource id")))
                .put("required", new JSONArray().put("resource_id"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        LineCodeAccessibilityService service = LineCodeAccessibilityService.getInstance();
        if (service == null) {
            return error("无障碍服务未开启");
        }
        String resourceId = input.optString("resource_id");
        if (resourceId.length() == 0) {
            return error("缺少 resource_id");
        }
        boolean success = service.clickById(resourceId);
        return success ? ok("点击 View 成功: " + resourceId) : error("点击 View 失败: " + resourceId);
    }
}
