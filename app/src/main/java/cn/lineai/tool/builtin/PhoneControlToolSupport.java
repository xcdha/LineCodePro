package cn.lineai.tool.builtin;

import android.content.Context;
import cn.lineai.service.LineCodeAccessibilityService;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolResult;

final class PhoneControlToolSupport {
    private PhoneControlToolSupport() {
    }

    static LineCodeAccessibilityService service(Context context) {
        return LineCodeAccessibilityService.getReadyInstance(context);
    }

    static ToolResult unavailable(BaseTool tool, Context context) {
        if (context != null && LineCodeAccessibilityService.isServiceEnabled(context)) {
            return toolError(tool, "无障碍服务已授权，但当前连接未就绪。请返回应用后重试，或关闭再开启 LineCode 无障碍服务。缺少前台连接时无法控制手机。");
        }
        return toolError(tool, "无障碍服务未开启，请先在系统无障碍设置中开启 LineCode。");
    }

    private static ToolResult toolError(BaseTool tool, String message) {
        return new ToolResult("", tool.getName(), message, true);
    }
}
