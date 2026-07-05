package cn.lineai.tool;

import android.content.Context;
import org.json.JSONObject;

/**
 * 工具显示信息解析器：根据工具名解析显示分类、标签、动作名。
 * 供UI层使用，将显示路由逻辑从UI层下沉到tool层。
 */
public final class ToolDisplayResolver {
    private final ToolRegistry registry;

    private static ToolDisplayResolver defaultInstance;

    public ToolDisplayResolver(ToolRegistry registry) {
        this.registry = registry;
    }

    public static void setDefault(ToolDisplayResolver resolver) {
        defaultInstance = resolver;
    }

    public static ToolDisplayResolver getDefault() {
        return defaultInstance;
    }

    public ToolDisplayCategory getDisplayCategory(String name) {
        if (registry != null) {
            return registry.getCachedDisplayCategory(name);
        }
        return fallbackDisplayCategory(name);
    }

    public String getDisplayLabel(Context context, String name, JSONObject input, String workspacePath) {
        if (registry != null) {
            BaseTool tool = registry.get(name);
            if (tool != null) {
                String label = tool.getDisplayLabel(context, input, workspacePath);
                if (label != null) {
                    return label;
                }
            }
        }
        return null;
    }

    public String getActionName(Context context, String name) {
        if (registry != null) {
            BaseTool tool = registry.get(name);
            if (tool != null) {
                String actionName = tool.getActionName(context);
                if (actionName != null) {
                    return actionName;
                }
            }
        }
        return null;
    }

    public static ToolDisplayCategory fallbackDisplayCategory(String name) {
        if (name == null) return ToolDisplayCategory.GENERIC;
        if ("file_read".equals(name) || "glob".equals(name) || "list_dir".equals(name)
                || "web_search".equals(name) || "web_fetch".equals(name)
                || "image_understanding".equals(name)) return ToolDisplayCategory.READ;
        if ("file_write".equals(name) || "file_edit".equals(name)) return ToolDisplayCategory.WRITE;
        if ("file_delete".equals(name)) return ToolDisplayCategory.DELETE;
        if ("http_server".equals(name)) return ToolDisplayCategory.HTTP;
        if ("shell_execute".equals(name)) return ToolDisplayCategory.SHELL;
        if ("agent".equals(name)) return ToolDisplayCategory.AGENT;
        if ("agent_pipeline".equals(name)) return ToolDisplayCategory.AGENT_PIPELINE;
        if ("todo_update".equals(name)) return ToolDisplayCategory.TODO;
        if ("image_generation".equals(name)) return ToolDisplayCategory.IMAGE_GENERATION;
        if (name.startsWith("phone_")) return ToolDisplayCategory.PHONE_CONTROL;
        if (name.startsWith("agentx_")) return ToolDisplayCategory.AGENT;
        if (name.startsWith("mcpx_")) return ToolDisplayCategory.GENERIC;
        return ToolDisplayCategory.GENERIC;
    }
}
