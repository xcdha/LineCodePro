package cn.lineai.tool.builtin;

import android.content.Context;
import cn.lineai.service.LineCodeAccessibilityService;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class PhoneGlobalActionTool extends BaseTool {
    private final Context context;

    public PhoneGlobalActionTool(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    @Override
    public String getName() {
        return "phone_global_action";
    }

    @Override
    public String getDescription() {
        return "执行系统全局动作。action 可选 back、home、exit_app、recents、notifications、quick_settings、power_dialog、lock_screen。";
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
                        .put("action", new JSONObject()
                                .put("type", "string")
                                .put("enum", new JSONArray()
                                        .put("back")
                                        .put("home")
                                        .put("exit_app")
                                        .put("recents")
                                        .put("notifications")
                                        .put("quick_settings")
                                        .put("power_dialog")
                                        .put("lock_screen"))
                                .put("description", "要执行的系统动作")))
                .put("required", new JSONArray().put("action"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext toolContext) {
        LineCodeAccessibilityService service = PhoneControlToolSupport.service(context);
        if (service == null) {
            return PhoneControlToolSupport.unavailable(this, context);
        }
        String action = input.optString("action").trim();
        if (action.length() == 0) {
            return error("缺少 action");
        }
        boolean success = service.performPhoneAction(action);
        return success ? ok("系统动作成功: " + action) : error("系统动作失败或不支持: " + action);
    }
}
