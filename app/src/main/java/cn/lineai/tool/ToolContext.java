package cn.lineai.tool;

import cn.lineai.state.TodoStateStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;

public final class ToolContext {
    public interface AgentRunner {
        ToolResult runAgent(JSONObject input, ToolContext context);

        ToolResult runAgentPipeline(JSONObject input, ToolContext context);
    }

    public interface ProgressListener {
        void onToolProgress(String toolCallId, String toolName, String content, boolean error);
    }

    private final String homePath;
    private final List<String> extraWriteRoots;
    private final AgentRunner agentRunner;
    private final String toolCallId;
    private final ProgressListener progressListener;
    private final TodoStateStore todoStateStore;

    public ToolContext(String homePath) {
        this(homePath, Collections.emptyList(), null, "", null, null);
    }

    public ToolContext(String homePath, AgentRunner agentRunner) {
        this(homePath, Collections.emptyList(), agentRunner, "", null, null);
    }

    public ToolContext(String homePath, AgentRunner agentRunner, String toolCallId) {
        this(homePath, Collections.emptyList(), agentRunner, toolCallId, null, null);
    }

    public ToolContext(String homePath, AgentRunner agentRunner, String toolCallId, ProgressListener progressListener) {
        this(homePath, Collections.emptyList(), agentRunner, toolCallId, progressListener, null);
    }

    public ToolContext(
            String homePath,
            AgentRunner agentRunner,
            String toolCallId,
            ProgressListener progressListener,
            TodoStateStore todoStateStore
    ) {
        this(homePath, Collections.emptyList(), agentRunner, toolCallId, progressListener, todoStateStore);
    }

    public ToolContext(
            String homePath,
            List<String> extraWriteRoots,
            AgentRunner agentRunner,
            String toolCallId,
            ProgressListener progressListener
    ) {
        this(homePath, extraWriteRoots, agentRunner, toolCallId, progressListener, null);
    }

    public ToolContext(
            String homePath,
            List<String> extraWriteRoots,
            AgentRunner agentRunner,
            String toolCallId,
            ProgressListener progressListener,
            TodoStateStore todoStateStore
    ) {
        this.homePath = homePath == null ? "" : homePath;
        this.extraWriteRoots = immutableRoots(extraWriteRoots);
        this.agentRunner = agentRunner;
        this.toolCallId = toolCallId == null ? "" : toolCallId;
        this.progressListener = progressListener;
        this.todoStateStore = todoStateStore;
    }

    public String getHomePath() {
        return homePath;
    }

    public List<String> getExtraWriteRoots() {
        return extraWriteRoots;
    }

    public AgentRunner getAgentRunner() {
        return agentRunner;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public ToolContext withToolCallId(String nextToolCallId) {
        return new ToolContext(homePath, extraWriteRoots, agentRunner, nextToolCallId, progressListener, todoStateStore);
    }

    public void reportToolProgress(String toolName, String content, boolean error) {
        if (progressListener != null && toolCallId.length() > 0) {
            progressListener.onToolProgress(toolCallId, toolName, content == null ? "" : content, error);
        }
    }

    public TodoStateStore getTodoStateStore() {
        return todoStateStore;
    }

    private List<String> immutableRoots(List<String> roots) {
        ArrayList<String> values = new ArrayList<>();
        if (roots != null) {
            for (String root : roots) {
                if (root != null && root.trim().length() > 0) {
                    values.add(root.trim());
                }
            }
        }
        return Collections.unmodifiableList(values);
    }
}
