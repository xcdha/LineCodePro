package cn.lineai.tool;

import cn.lineai.tool.builtin.AgentPipelineTool;
import cn.lineai.tool.builtin.AgentTool;
import cn.lineai.tool.builtin.FileDeleteTool;
import cn.lineai.tool.builtin.FileEditTool;
import cn.lineai.tool.builtin.FileReadTool;
import cn.lineai.tool.builtin.FileWriteTool;
import cn.lineai.tool.builtin.GlobTool;
import cn.lineai.tool.builtin.HttpServerTool;
import cn.lineai.tool.builtin.ImageGenerationTool;
import cn.lineai.tool.builtin.ImageUnderstandingTool;
import cn.lineai.tool.builtin.ListDirectoryTool;
import cn.lineai.tool.builtin.ShellExecuteTool;
import cn.lineai.tool.builtin.TodoUpdateTool;
import cn.lineai.tool.builtin.WebFetchTool;
import cn.lineai.tool.builtin.WebSearchTool;

/**
 * 工具分类枚举，同时承载基于工具名称的类型判断逻辑，
 * 供 UI 层与工具层共用，避免判断逻辑散落在各处。
 */
public enum ToolCategory {
    READ("read"),
    GENERATE("generate"),
    WRITE("write"),
    SYSTEM("system");

    private final String value;

    ToolCategory(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Deprecated
    public static boolean isReadType(String name) {
        return FileReadTool.NAME.equals(name) || GlobTool.NAME.equals(name) || ListDirectoryTool.NAME.equals(name)
                || WebSearchTool.NAME.equals(name) || WebFetchTool.NAME.equals(name)
                || ImageUnderstandingTool.NAME.equals(name)
                || ImageGenerationTool.NAME.equals(name);
    }

    @Deprecated
    public static boolean isWriteType(String name) {
        return FileWriteTool.NAME.equals(name) || FileEditTool.NAME.equals(name);
    }

    @Deprecated
    public static boolean isDeleteType(String name) {
        return FileDeleteTool.NAME.equals(name);
    }

    @Deprecated
    public static boolean isHttpType(String name) {
        return HttpServerTool.NAME.equals(name);
    }

    @Deprecated
    public static boolean isShellType(String name) {
        return ShellExecuteTool.NAME.equals(name);
    }

    @Deprecated
    public static boolean isAgentType(String name) {
        return AgentTool.NAME.equals(name);
    }

    @Deprecated
    public static boolean isAgentPipelineType(String name) {
        return AgentPipelineTool.NAME.equals(name);
    }

    @Deprecated
    public static boolean isTodoType(String name) {
        return TodoUpdateTool.NAME.equals(name);
    }

    @Deprecated
    public static boolean isImageGenerationType(String name) {
        return ImageGenerationTool.NAME.equals(name);
    }

    @Deprecated
    public static boolean isPhoneControlType(String name) {
        return name != null && name.startsWith("phone_");
    }

    @Deprecated
    public static boolean isCustomMcpType(String name) {
        return name != null && name.startsWith("mcpx_");
    }

    @Deprecated
    public static boolean isCustomAgentType(String name) {
        return name != null && name.startsWith("agentx_");
    }
}
