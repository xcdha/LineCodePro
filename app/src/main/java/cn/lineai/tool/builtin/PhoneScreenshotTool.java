package cn.lineai.tool.builtin;

import android.graphics.Bitmap;
import android.util.Base64;
import cn.lineai.service.LineCodeAccessibilityService;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import java.io.ByteArrayOutputStream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class PhoneScreenshotTool extends BaseTool {
    @Override
    public String getName() {
        return "phone_screenshot";
    }

    @Override
    public String getDescription() {
        return "截取当前屏幕并返回 base64 编码的 PNG 图片。需要无障碍服务已开启。";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.READ;
    }

    @Override
    public JSONObject getParameters() throws JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject())
                .put("required", new JSONArray());
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        LineCodeAccessibilityService service = LineCodeAccessibilityService.getInstance();
        if (service == null) {
            return error("无障碍服务未开启");
        }
        Bitmap bitmap = service.takeScreenshot();
        if (bitmap == null) {
            return error("截图失败");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        byte[] bytes = output.toByteArray();
        bitmap.recycle();
        String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
        return ok("data:image/png;base64," + base64);
    }
}
