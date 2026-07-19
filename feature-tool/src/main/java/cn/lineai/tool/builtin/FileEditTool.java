package cn.lineai.tool.builtin;

import cn.lineai.tool.BaseTool;
import cn.lineai.tool.R;
import cn.lineai.tool.ToolArgs;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import cn.lineai.tool.ToolResult;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public final class FileEditTool extends BaseTool {
    public static final String NAME = "file_edit";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "编辑文件内容。通过 old_string/new_string 搜索替换修改文件。";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.WRITE;
    }

    @Override
    public ToolDisplayCategory getDisplayCategory() {
        return ToolDisplayCategory.WRITE;
    }

    @Override
    public boolean shouldRecordDiff() {
        return true;
    }

    @Override
    public JSONObject getParameters() throws org.json.JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("file_path", new JSONObject().put("type", "string").put("description", "文件的绝对或相对路径"))
                        .put("old_string", new JSONObject().put("type", "string").put("description", "要搜索的原始文本，必须唯一或明确"))
                        .put("new_string", new JSONObject().put("type", "string").put("description", "替换后的新文本")))
                .put("required", new org.json.JSONArray().put("file_path").put("old_string").put("new_string"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        try {
            String path = input.optString("file_path");
            String oldString = input.optString("old_string");
            String newString = input.optString("new_string");
            ToolArgs.requireNonEmpty(path, "file_path");
            if (oldString.length() == 0) {
                return error(context.getString(R.string.tool_file_edit_old_string_empty));
            }
            File file = FileToolPathPolicy.resolve(context, path);
            if (!file.exists()) {
                return error(context.getString(R.string.tool_file_edit_not_found, FileToolPathPolicy.displayPath(context.getHomePath(), file)));
            }
            if (file.isDirectory()) {
                return error(context.getString(R.string.tool_file_edit_is_directory, path));
            }
            String content = FileIo.readUtf8(file);
            if (!content.contains(oldString)) {
                return error(context.getString(R.string.tool_file_edit_no_match));
            }
            int count = countOccurrences(content, oldString);
            String next = content.replace(oldString, newString);
            FileOutputStream output = new FileOutputStream(file, false);
            try {
                output.write(next.getBytes(StandardCharsets.UTF_8));
            } finally {
                output.close();
            }
            return ok(context.getString(R.string.tool_file_edit_success, FileToolPathPolicy.displayPath(context.getHomePath(), file), count));
        } catch (Exception e) {
            return error(context.getString(R.string.tool_file_edit_failed, e.getMessage()));
        }
    }

    private int countOccurrences(String content, String value) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(value, index)) >= 0) {
            count++;
            index += value.length();
        }
        return count;
    }
}
