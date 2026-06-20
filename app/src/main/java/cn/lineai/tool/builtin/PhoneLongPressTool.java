package cn.lineai.tool.builtin;

import cn.lineai.service.LineCodeAccessibilityService;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class PhoneLongPressTool extends BaseTool {
    @Override
    public String getName() {
        return "phone_long_press";
    }

    @Override
    public String getDescription() {
        return "在屏幕指定坐标执行长按操作。参数 x、y 为屏幕像素坐标，duration_ms 为按压时长（可选，默认 800）。";
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
                        .put("x", new JSONObject().put("type", "number").put("description", "屏幕 X 坐标"))
                        .put("y", new JSONObject().put("type", "number").put("description", "屏幕 Y 坐标"))
                        .put("duration_ms", new JSONObject().put("type", "number").put("description", "长按时长（毫秒），默认 800")))
                .put("required", new JSONArray().put("x").put("y"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        LineCodeAccessibilityService service = LineCodeAccessibilityService.getInstance();
        if (service == null) {
            return error("无障碍服务未开启");
        }
        int x = input.optInt("x");
        int y = input.optInt("y");
        int durationMs = input.optInt("duration_ms", 800);
        boolean success = service.longPress(x, y, durationMs);
        return success ? ok("长按成功: (" + x + ", " + y + ")") : error("长按失败");
    }
}
