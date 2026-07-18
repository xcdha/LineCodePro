package cn.lineai.tool;

import android.content.Context;
import cn.lineai.tool.builtin.AgentPipelineTool;
import cn.lineai.tool.builtin.AgentTool;
import cn.lineai.tool.builtin.FileDeleteTool;
import cn.lineai.tool.builtin.FileEditTool;
import cn.lineai.tool.builtin.FileReadTool;
import cn.lineai.tool.builtin.FileWriteTool;
import cn.lineai.tool.builtin.GlobTool;
import cn.lineai.tool.builtin.ImageGenerationTool;
import cn.lineai.tool.builtin.ImageUnderstandingTool;
import cn.lineai.tool.builtin.ListDirectoryTool;
import cn.lineai.tool.builtin.ShellExecuteTool;
import cn.lineai.tool.builtin.TodoUpdateTool;
import cn.lineai.tool.builtin.WebFetchTool;
import cn.lineai.tool.builtin.WebSearchTool;
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
        if (FileReadTool.NAME.equals(name) || GlobTool.NAME.equals(name) || ListDirectoryTool.NAME.equals(name)
                || WebSearchTool.NAME.equals(name) || WebFetchTool.NAME.equals(name)
                || ImageUnderstandingTool.NAME.equals(name)) return ToolDisplayCategory.READ;
        if (FileWriteTool.NAME.equals(name) || FileEditTool.NAME.equals(name)) return ToolDisplayCategory.WRITE;
        if (FileDeleteTool.NAME.equals(name)) return ToolDisplayCategory.DELETE;
        if (ShellExecuteTool.NAME.equals(name)) return ToolDisplayCategory.SHELL;
        if (AgentTool.NAME.equals(name)) return ToolDisplayCategory.AGENT;
        if (AgentPipelineTool.NAME.equals(name)) return ToolDisplayCategory.AGENT_PIPELINE;
        if (TodoUpdateTool.NAME.equals(name)) return ToolDisplayCategory.TODO;
        if (ImageGenerationTool.NAME.equals(name)) return ToolDisplayCategory.IMAGE_GENERATION;
        if (name.startsWith("phone_")) return ToolDisplayCategory.PHONE_CONTROL;
        if (name.startsWith("agentx_")) return ToolDisplayCategory.AGENT;
        if (name.startsWith("mcpx_")) return ToolDisplayCategory.GENERIC;
        return ToolDisplayCategory.GENERIC;
    }
}
