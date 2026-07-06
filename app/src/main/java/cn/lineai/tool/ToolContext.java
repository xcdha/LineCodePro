package cn.lineai.tool;

import cn.lineai.ai.ModelClient;
import cn.lineai.ai.protocol.ModelProtocolFactory;
import cn.lineai.data.repository.PromptTemplateRepository;
import cn.lineai.data.repository.SshFileTreeStore;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.model.ModelStore;
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
    private final ToolSettingsStore toolSettingsStore;
    private final ModelStore modelRepository;
    private final SshFileTreeStore sshFileTreeRepository;
    private final ModelProtocolFactory modelProtocolFactory;
    private final ModelClient modelClient;
    private final PromptTemplateRepository promptTemplateRepository;

    public ToolContext(String homePath) {
        this(homePath, Collections.emptyList(), null, "", null, null, null, null, null, null, null, null);
    }

    public ToolContext(String homePath, AgentRunner agentRunner) {
        this(homePath, Collections.emptyList(), agentRunner, "", null, null, null, null, null, null, null, null);
    }

    public ToolContext(String homePath, AgentRunner agentRunner, String toolCallId) {
        this(homePath, Collections.emptyList(), agentRunner, toolCallId, null, null, null, null, null, null, null, null);
    }

    public ToolContext(String homePath, AgentRunner agentRunner, String toolCallId, ProgressListener progressListener) {
        this(homePath, Collections.emptyList(), agentRunner, toolCallId, progressListener, null, null, null, null, null, null, null);
    }

    public ToolContext(
            String homePath,
            AgentRunner agentRunner,
            String toolCallId,
            ProgressListener progressListener,
            TodoStateStore todoStateStore
    ) {
        this(homePath, Collections.emptyList(), agentRunner, toolCallId, progressListener, todoStateStore, null, null, null, null, null, null);
    }

    public ToolContext(
            String homePath,
            List<String> extraWriteRoots,
            AgentRunner agentRunner,
            String toolCallId,
            ProgressListener progressListener
    ) {
        this(homePath, extraWriteRoots, agentRunner, toolCallId, progressListener, null, null, null, null, null, null, null);
    }

    public ToolContext(
            String homePath,
            List<String> extraWriteRoots,
            AgentRunner agentRunner,
            String toolCallId,
            ProgressListener progressListener,
            TodoStateStore todoStateStore
    ) {
        this(homePath, extraWriteRoots, agentRunner, toolCallId, progressListener, todoStateStore, null, null, null, null, null, null);
    }

    public ToolContext(
            String homePath,
            List<String> extraWriteRoots,
            AgentRunner agentRunner,
            String toolCallId,
            ProgressListener progressListener,
            TodoStateStore todoStateStore,
            ToolSettingsStore toolSettingsStore,
            ModelStore modelRepository,
            SshFileTreeStore sshFileTreeRepository,
            ModelProtocolFactory modelProtocolFactory
    ) {
        this(homePath, extraWriteRoots, agentRunner, toolCallId, progressListener, todoStateStore, toolSettingsStore, modelRepository, sshFileTreeRepository, modelProtocolFactory, null, null);
    }

    public ToolContext(
            String homePath,
            List<String> extraWriteRoots,
            AgentRunner agentRunner,
            String toolCallId,
            ProgressListener progressListener,
            TodoStateStore todoStateStore,
            ToolSettingsStore toolSettingsStore,
            ModelStore modelRepository,
            SshFileTreeStore sshFileTreeRepository,
            ModelProtocolFactory modelProtocolFactory,
            ModelClient modelClient,
            PromptTemplateRepository promptTemplateRepository
    ) {
        this.homePath = homePath == null ? "" : homePath;
        this.extraWriteRoots = immutableRoots(extraWriteRoots);
        this.agentRunner = agentRunner;
        this.toolCallId = toolCallId == null ? "" : toolCallId;
        this.progressListener = progressListener;
        this.todoStateStore = todoStateStore;
        this.toolSettingsStore = toolSettingsStore;
        this.modelRepository = modelRepository;
        this.sshFileTreeRepository = sshFileTreeRepository;
        this.modelProtocolFactory = modelProtocolFactory;
        this.modelClient = modelClient;
        this.promptTemplateRepository = promptTemplateRepository;
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
        return new ToolContext(homePath, extraWriteRoots, agentRunner, nextToolCallId, progressListener, todoStateStore, toolSettingsStore, modelRepository, sshFileTreeRepository, modelProtocolFactory, modelClient, promptTemplateRepository);
    }

    public void reportToolProgress(String toolName, String content, boolean error) {
        if (progressListener != null && toolCallId.length() > 0) {
            progressListener.onToolProgress(toolCallId, toolName, content == null ? "" : content, error);
        }
    }

    public TodoStateStore getTodoStateStore() {
        return todoStateStore;
    }

    public ToolSettingsStore getToolSettingsStore() {
        return toolSettingsStore;
    }

    public ModelStore getModelRepository() {
        return modelRepository;
    }

    public SshFileTreeStore getSshFileTreeRepository() {
        return sshFileTreeRepository;
    }

    public ModelProtocolFactory getModelProtocolFactory() {
        return modelProtocolFactory;
    }

    public ModelClient getModelClient() {
        return modelClient;
    }

    public PromptTemplateRepository getPromptTemplateRepository() {
        return promptTemplateRepository;
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
