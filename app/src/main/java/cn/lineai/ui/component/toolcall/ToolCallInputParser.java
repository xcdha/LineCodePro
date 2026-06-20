package cn.lineai.ui.component.toolcall;

import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolCategory;
import org.json.JSONObject;

/**
 * 工具调用输入解析工具：从 ToolCall 提取 JSON 输入，并生成用于卡片展示的标签文本。
 */
final class ToolCallInputParser {
    private ToolCallInputParser() {
    }

    static JSONObject parseInput(ToolCall toolCall) {
        if (toolCall == null || toolCall.getArguments().trim().length() == 0) {
            return new JSONObject();
        }
        try {
            return new JSONObject(toolCall.getArguments());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    static String inputLabel(String name, JSONObject input) {
        if (input == null) {
            return name;
        }
        String[] keys = new String[] {"file_path", "pattern", "query", "url", "path", "command"};
        for (String key : keys) {
            String value = input.optString(key);
            if (value.length() > 0) {
                return value;
            }
        }
        return name == null ? "" : name;
    }

    static String displayInputLabel(String name, JSONObject input, String workspacePath) {
        if (input == null) {
            return name == null ? "" : name;
        }
        if (ToolCategory.isPhoneControlType(name)) {
            return phoneControlLabel(name, input);
        }
        if ("file_read".equals(name)) {
            String filePath = input.optString("file_path");
            if (filePath.length() > 0) {
                return ToolCallPathDisplay.workspaceDisplayPath(workspacePath, filePath);
            }
        }
        if ("list_dir".equals(name)) {
            String path = input.optString("path");
            if (path.length() > 0) {
                return ToolCallPathDisplay.workspaceDisplayPath(workspacePath, path);
            }
        }
        return inputLabel(name, input);
    }

    private static String phoneControlLabel(String name, JSONObject input) {
        if ("phone_screenshot".equals(name)) {
            return "保存当前屏幕截图";
        }
        if ("phone_view_hierarchy".equals(name)) {
            return "读取当前窗口 View 层级";
        }
        if ("phone_global_action".equals(name)) {
            String action = input.optString("action");
            return action.length() == 0 ? "执行系统动作" : globalActionLabel(action);
        }
        if ("phone_click_view".equals(name)) {
            String resourceId = input.optString("resource_id");
            if (resourceId.length() > 0) {
                return "点击 View：" + resourceId;
            }
            String text = input.optString("text");
            if (text.length() > 0) {
                return "点击文本：" + text;
            }
        }
        if ("phone_swipe".equals(name)) {
            return "从 (" + input.optInt("x1") + ", " + input.optInt("y1") + ") 滑到 ("
                    + input.optInt("x2") + ", " + input.optInt("y2") + ")";
        }
        if ("phone_click".equals(name) || "phone_long_press".equals(name) || "phone_click_view".equals(name)) {
            return "坐标 (" + input.optInt("x") + ", " + input.optInt("y") + ")";
        }
        return name == null ? "" : name;
    }

    static String phoneControlActionName(String name) {
        if ("phone_screenshot".equals(name)) return "截图";
        if ("phone_click".equals(name)) return "点击";
        if ("phone_swipe".equals(name)) return "滑动";
        if ("phone_long_press".equals(name)) return "长按";
        if ("phone_view_hierarchy".equals(name)) return "查看 View 层级";
        if ("phone_click_view".equals(name)) return "View 点击";
        if ("phone_global_action".equals(name)) return "系统动作";
        return "手机操作";
    }

    private static String globalActionLabel(String action) {
        if ("back".equals(action)) return "返回";
        if ("home".equals(action)) return "回到主页";
        if ("exit_app".equals(action)) return "退出当前应用";
        if ("recents".equals(action)) return "打开最近任务";
        if ("notifications".equals(action)) return "打开通知栏";
        if ("quick_settings".equals(action)) return "打开快捷设置";
        if ("power_dialog".equals(action)) return "打开电源菜单";
        if ("lock_screen".equals(action)) return "锁定屏幕";
        return "系统动作：" + action;
    }
}
