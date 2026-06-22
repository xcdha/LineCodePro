package cn.lineai.mvp;

import android.content.Context;
import android.widget.Toast;
import cn.lineai.R;
import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.ai.ModelClient;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.message.UserModelMessage;
import cn.lineai.ai.prompt.SystemPromptProvider;
import cn.lineai.ai.protocol.OpenAiCompatibleCapabilities;
import cn.lineai.context.ContextCompactionService;
import cn.lineai.context.ContextManager;
import cn.lineai.context.MemoryExtractionService;
import cn.lineai.data.repository.AiBehaviorSettingsRepository;
import cn.lineai.data.repository.ChatModeRepository;
import cn.lineai.data.repository.ConversationRecord;
import cn.lineai.data.repository.ConversationStore;
import cn.lineai.data.repository.DiffRecord;
import cn.lineai.data.repository.DiffRepository;
import cn.lineai.data.repository.DiffStore;
import cn.lineai.data.repository.ExtensionStore;
import cn.lineai.data.repository.FileTreeStore;
import cn.lineai.data.repository.InputSettingsRepository;
import cn.lineai.data.repository.IpcFileTreeStore;
import cn.lineai.data.repository.IpcProviderStore;
import cn.lineai.data.repository.LearningContextStore;
import cn.lineai.data.repository.MessageRecord;
import cn.lineai.data.repository.OutputSettingsRepository;
import cn.lineai.data.repository.ProjectRecord;
import cn.lineai.data.repository.ProjectStore;
import cn.lineai.data.repository.PromptTemplateRepository;
import cn.lineai.data.repository.SshFileTreeStore;
import cn.lineai.data.repository.ThemeSettingsRepository;
import cn.lineai.data.repository.ToolSettingsRepository;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.mvp.agent.AgentExecutionController;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.ChatMode;
import cn.lineai.model.FileTreeNode;
import cn.lineai.model.InputAttachment;
import cn.lineai.model.McpToolConfig;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextParser;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.model.ModelStore;
import cn.lineai.model.SheetOption;
import cn.lineai.model.SshConfig;
import cn.lineai.service.KeepAliveService;
import cn.lineai.ssh.SshService;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolExecutor;
import cn.lineai.tool.ToolExecutionCoordinator;
import cn.lineai.tool.ToolRegistry;
import cn.lineai.tool.ToolResult;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.builtin.AgentTool;
import cn.lineai.tool.builtin.FileToolPathPolicy;
import cn.lineai.tool.builtin.HttpServerTool;
import cn.lineai.workspace.SafPathResolver;
import cn.lineai.workspace.StoragePermissionManager;
import cn.lineai.workspace.WorkspacePaths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MainCoordinator extends MainCoordinatorDelegates {
    private static final String TAG = "MainCoordinator";
    private static final long STREAM_RENDER_INTERVAL_MS = 80L;
    private String agentTerminatedMessage() {
        return context.getString(R.string.message_agent_terminated);
    }

    private final Context context;

    private final ChatSessionStore chatSessionStore = new ChatSessionStore();
    private final ArrayList<ChatMessage> messages = chatSessionStore.mutableMessages();
    private final ScreenNavigationController screenNavigationController = new ScreenNavigationController();
    private final Set<String> expandedFilePaths = new HashSet<>();
    private final MainThreadDispatcher mainThread;
    private final BackgroundTaskRunner backgroundTasks;
    private final ChatUiStateAssembler chatUiStateAssembler;
    private final ToolMessageController toolMessageController;
    private final ConversationPersistenceController conversationPersistenceController;
    private final ExtensionDraftController extensionDraftController;
    private final ExtensionManagementController extensionManagementController;
    private final ModelPromptController modelPromptController;
    private final DirectoryPickerController directoryPickerController;
    private final StorageMaintenanceController storageMaintenanceController;
    private final ContextCompactionController contextCompactionController;
    private final IpcProviderController ipcProviderController;
    private final GenerationController generationController = new GenerationController();
    private final GenerationFlowController generationFlowController;
    private final ChatInteractionController chatInteractionController;
    private final ModelManagementController modelManagementController;
    private final SettingsManagementController settingsManagementController;
    private final SshFileTreeController sshFileTreeController;
    private final IpcFileTreeController ipcFileTreeController;
    private final FileOperationController fileOperationController;
    private final PermissionModeController permissionModeController;
    private final ProjectWorkspaceController projectWorkspaceController;
    private final ModelStore modelRepository;
    private final AiBehaviorSettingsRepository aiBehaviorSettingsRepository;
    private final ChatModeRepository chatModeRepository;
    private final InputSettingsRepository inputSettingsRepository;
    private final OutputSettingsRepository outputSettingsRepository;
    private final ThemeSettingsRepository themeSettingsRepository;
    private final PromptTemplateRepository promptTemplateRepository;
    private final ConversationStore conversationRepository;
    private final ProjectStore projectRepository;
    private final LearningContextStore learningContextRepository;
    private final MemoryExtractionService memoryExtractionService;
    private final ToolSettingsStore toolSettingsRepository;
    private final ExtensionStore extensionRepository;
    private final IpcProviderStore ipcProviderRepository;
    private final cn.lineai.ipc.IpcProviderManager ipcProviderManager;
    private boolean generationKeepAliveActive = false;
    private final DiffStore diffRepository;
    private final FileTreeStore fileTreeRepository;
    private final IpcFileTreeStore ipcFileTreeRepository;
    private final SshService sshService;
    private final SshFileTreeStore sshFileTreeRepository;
    private final ContextManager contextManager;
    private final ContextCompactionService contextCompactionService;
    private final ModelClient modelClient;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final ToolExecutionCoordinator toolExecutionCoordinator;
    private final SystemPromptProvider systemPromptProvider;
    private final StoragePermissionManager storagePermissionManager;
    private final SafPathResolver safPathResolver;
    private final LineCodeArchiveController lineCodeArchiveController;
    private final AgentExecutionController agentExecutionController;
    private final cn.lineai.state.TodoStateStore todoStateStore;
    private MainContract.View view;
    private final ScreenNavigationController.Host navigationHost = new ScreenNavigationController.Host() {
        @Override
        public void hideOverlays() {
            if (view != null) {
                view.hideOverlays();
            }
        }

        @Override
        public void showScreen(String screenId) {
            if (view != null) {
                view.showScreen(screenId);
            }
        }

        @Override
        public void showChatScreen() {
            if (view != null) {
                view.showChatScreen();
            }
        }
    };
    private ModelCancellationToken currentCancellationToken;
    private String projectLabel = "LineCode";
    private String projectPath = "";
    private String projectSource = WorkspacePaths.SOURCE_DEFAULT;
    private final AttachmentPickerCoordinator attachmentPickerController;

    public MainCoordinator(Context context) {
        this(new MainDependencies(context));
    }

    MainCoordinator(MainDependencies dependencies) {
        this.context = dependencies.context;
        modelRepository = dependencies.modelRepository;
        modelManagementController = new ModelManagementController(
                modelRepository,
                new ModelManagementController.Host() {
                    @Override
                    public void refreshModelsScreen() {
                        refreshVisibleScreen("models");
                    }

                    @Override
                    public void returnToModelsScreen() {
                        returnToScreen("models");
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                }
        );
        aiBehaviorSettingsRepository = dependencies.aiBehaviorSettingsRepository;
        chatModeRepository = dependencies.chatModeRepository;
        inputSettingsRepository = dependencies.inputSettingsRepository;
        outputSettingsRepository = dependencies.outputSettingsRepository;
        themeSettingsRepository = dependencies.themeSettingsRepository;
        promptTemplateRepository = dependencies.promptTemplateRepository;
        conversationRepository = dependencies.conversationRepository;
        projectRepository = dependencies.projectRepository;
        learningContextRepository = dependencies.learningContextRepository;
        memoryExtractionService = dependencies.memoryExtractionService;
        toolSettingsRepository = dependencies.toolSettingsRepository;
        extensionRepository = dependencies.extensionRepository;
        ipcProviderRepository = dependencies.ipcProviderRepository;
        ipcProviderManager = dependencies.ipcProviderManager;
        diffRepository = dependencies.diffRepository;
        fileTreeRepository = dependencies.fileTreeRepository;
        ipcFileTreeRepository = dependencies.ipcFileTreeRepository;
        sshService = dependencies.sshService;
        sshFileTreeRepository = dependencies.sshFileTreeRepository;
        contextManager = dependencies.contextManager;
        contextCompactionService = dependencies.contextCompactionService;
        modelClient = dependencies.modelClient;
        toolRegistry = dependencies.toolRegistry;
        toolExecutor = dependencies.toolExecutor;
        toolExecutionCoordinator = dependencies.toolExecutionCoordinator;
        systemPromptProvider = dependencies.systemPromptProvider;
        storagePermissionManager = dependencies.storagePermissionManager;
        safPathResolver = dependencies.safPathResolver;
        mainThread = dependencies.mainThreadDispatcher;
        backgroundTasks = dependencies.backgroundTaskRunner;
        sshFileTreeController = new SshFileTreeController(
                sshFileTreeRepository,
                new SshFileTreeController.Host() {
                    @Override
                    public boolean isSshExecutionMode() {
                        return MainCoordinator.this.isSshExecutionMode();
                    }

                    @Override
                    public String projectPath() {
                        return projectPath;
                    }

                    @Override
                    public String projectLabel() {
                        return projectLabel;
                    }

                    @Override
                    public boolean isExpanded(String path) {
                        return expandedFilePaths.contains(path);
                    }

                    @Override
                    public void addExpandedPath(String path) {
                        if (path != null && path.length() > 0) {
                            expandedFilePaths.add(path);
                        }
                    }

                    @Override
                    public void setProjectPathFromSshRoot(String path) {
                        projectPath = path == null ? "" : path;
                    }

                    @Override
                    public String basename(String path) {
                        return MainCoordinator.this.basename(path);
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                },
                backgroundTasks::execute,
                mainThread::post
        );
        ipcFileTreeController = new IpcFileTreeController(
                ipcFileTreeRepository,
                new IpcFileTreeController.Host() {
                    @Override
                    public boolean isTerminalProviderExecutionMode() {
                        return MainCoordinator.this.isTerminalProviderExecutionMode();
                    }

                    @Override
                    public String projectPath() {
                        return projectPath;
                    }

                    @Override
                    public String projectLabel() {
                        return projectLabel;
                    }

                    @Override
                    public boolean isExpanded(String path) {
                        return expandedFilePaths.contains(path);
                    }

                    @Override
                    public void addExpandedPath(String path) {
                        if (path != null && path.length() > 0) {
                            expandedFilePaths.add(path);
                        }
                    }

                    @Override
                    public void setProjectPathFromIpcRoot(String path) {
                        projectPath = path == null ? "" : path;
                    }

                    @Override
                    public String basename(String path) {
                        return MainCoordinator.this.basename(path);
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                },
                backgroundTasks::execute,
                mainThread::post
        );
        fileOperationController = new FileOperationController(
                fileTreeRepository,
                sshFileTreeRepository,
                ipcFileTreeRepository,
                new FileOperationController.Host() {
                    @Override
                    public boolean isSshExecutionMode() {
                        return MainCoordinator.this.isSshExecutionMode();
                    }

                    @Override
                    public boolean isTerminalProviderExecutionMode() {
                        return MainCoordinator.this.isTerminalProviderExecutionMode();
                    }

                    @Override
                    public void showInputDialog(String title, String message, String initialValue, String actionId) {
                        if (view != null) {
                            view.showInputDialog(title, message, initialValue, actionId);
                        }
                    }

                    @Override
                    public void showConfirmationDialog(String title, String message, String confirmLabel, boolean danger, String actionId) {
                        if (view != null) {
                            view.showConfirmationDialog(title, message, confirmLabel, danger, actionId);
                        }
                    }

                    @Override
                    public void showFileActionDialog(String title, String subtitle, ArrayList<SheetOption> options) {
                        if (view != null) {
                            view.showFileActionDialog(title, subtitle, options);
                        }
                    }

                    @Override
                    public void addExpandedPath(String path) {
                        if (path != null && path.length() > 0) {
                            expandedFilePaths.add(path);
                        }
                    }

                    @Override
                    public void refreshSshDirectoryAfterFileOperation(String path) {
                        sshFileTreeController.refreshDirectoryAfterFileOperation(path);
                    }

                    @Override
                    public void refreshIpcDirectoryAfterFileOperation(String path) {
                        ipcFileTreeController.refreshDirectoryAfterFileOperation(path);
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }

                    @Override
                    public void showNotice(String text) {
                        MainCoordinator.this.showNotice(text);
                    }

                    @Override
                    public String basename(String path) {
                        return MainCoordinator.this.basename(path);
                    }

                    @Override
                    public String parentPath(String path) {
                        return MainCoordinator.this.parentPath(path);
                    }
                },
                backgroundTasks::execute,
                mainThread::post
        );
        permissionModeController = new PermissionModeController(
                toolSettingsRepository,
                chatModeRepository,
                new PermissionModeController.Host() {
                    @Override
                    public boolean hasExternalStorageAccess() {
                        return storagePermissionManager.hasExternalStorageAccess();
                    }

                    @Override
                    public String storagePermissionMessage() {
                        return storagePermissionManager.permissionDeniedMessage();
                    }

                    @Override
                    public void showPermissionSheet(ArrayList<SheetOption> options) {
                        if (view != null) {
                            view.showSheet(context.getString(R.string.sheet_title_permissions), options);
                        }
                    }
                }
        );
        projectWorkspaceController = new ProjectWorkspaceController(
                context,
                projectRepository,
                toolSettingsRepository,
                sshFileTreeRepository,
                storagePermissionManager,
                safPathResolver,
                new ProjectWorkspaceController.Host() {
                    @Override
                    public boolean isViewAttached() {
                        return view != null;
                    }

                    @Override
                    public boolean isTermuxSshHost() {
                        return MainCoordinator.this.isTermuxSshHost();
                    }

                    @Override
                    public void applyProject(ProjectRecord project) {
                        MainCoordinator.this.applyProject(project);
                    }

                    @Override
                    public void resetTodoState() {
                        MainCoordinator.this.resetTodoState();
                    }

                    @Override
                    public void requestSshFileTreeLoad(boolean force) {
                        MainCoordinator.this.requestSshFileTreeLoad(force);
                    }

                    @Override
                    public void showSheet(String title, List<SheetOption> options) {
                        if (view != null) {
                            view.showSheet(title, options);
                        }
                    }

                    @Override
                    public void showInputDialog(String title, String message, String initialValue, String actionId) {
                        if (view != null) {
                            view.showInputDialog(title, message, initialValue, actionId);
                        }
                    }

                    @Override
                    public void showConfirmationDialog(String title, String message, String confirmLabel, boolean danger, String actionId) {
                        if (view != null) {
                            view.showConfirmationDialog(title, message, confirmLabel, danger, actionId);
                        }
                    }

                    @Override
                    public void hideOverlays() {
                        if (view != null) {
                            view.hideOverlays();
                        }
                    }

                    @Override
                    public void openExternalProjectPicker() {
                        if (view != null) {
                            view.openExternalProjectPicker();
                        }
                    }

                    @Override
                    public void openManageAllFilesPermissionSettings() {
                        if (view != null) {
                            view.openManageAllFilesPermissionSettings();
                        }
                    }

                    @Override
                    public void requestLegacyStoragePermissions() {
                        if (view != null) {
                            view.requestLegacyStoragePermissions();
                        }
                    }

                    @Override
                    public void showNotice(String text) {
                        MainCoordinator.this.showNotice(text);
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                },
                backgroundTasks::execute,
                mainThread::post
        );
        settingsManagementController = new SettingsManagementController(
                aiBehaviorSettingsRepository,
                inputSettingsRepository,
                promptTemplateRepository,
                learningContextRepository,
                outputSettingsRepository,
                themeSettingsRepository,
                toolSettingsRepository,
                new SettingsManagementController.Host() {
                    @Override
                    public String currentProjectPath() {
                        return projectPath;
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }

                    @Override
                    public void recreateForTheme(String screenId) {
                        if (view != null) {
                            view.recreateForTheme(screenId);
                        }
                    }

                    @Override
                    public void afterMcpExecutionModeChanged(String executionMode) {
                        applyProject(projectRepository.ensureSelectedProjectPath(executionMode));
                        sshFileTreeController.invalidateFileTree();
                        ipcFileTreeController.invalidateFileTree();
                        requestSshFileTreeLoad(true);
                        requestIpcFileTreeLoad(true);
                        refreshVisibleScreen("mcp");
                        MainCoordinator.this.render();
                    }

                    @Override
                    public void refreshMcpScreen() {
                        refreshVisibleScreen("mcp");
                    }

                    @Override
                    public void returnToToolSettings() {
                        returnToScreen("toolSettings");
                    }
                }
        );
        lineCodeArchiveController = new LineCodeArchiveController(
                dependencies.lineCodeArchiveService,
                new LineCodeArchiveController.Host() {
                    @Override
                    public void openExportPicker(String fileName) {
                        if (view != null) {
                            view.openLineCodeExportPicker(fileName);
                        }
                    }

                    @Override
                    public void persistBeforeExport() {
                        persistCurrentConversation();
                    }

                    @Override
                    public void openImportPicker() {
                        if (view != null) {
                            view.openLineCodeImportPicker();
                        }
                    }

                    @Override
                    public void showImportConfirmation(String sourceName) {
                        if (view != null) {
                            view.showConfirmationDialog(
                                    "覆盖导入 .linecode",
                                    "将从「" + sourceName + "」恢复数据库、聊天记录、配置和 .linecode 工作区文件。当前本机数据会被覆盖。",
                                    context.getString(R.string.common_confirm),
                                    true,
                                    "data:import_linecode"
                            );
                        }
                    }

                    @Override
                    public void beforeImport() {
                        cancelActiveGeneration();
                        chatSessionStore.setStreaming(false);
                        stopGenerationKeepAlive();
                    }

                    @Override
                    public void afterImport() {
                        reloadAfterLineCodeImport();
                    }

                    @Override
                    public void showNotice(String text) {
                        MainCoordinator.this.showNotice(text);
                    }
                },
                backgroundTasks::execute,
                mainThread::post
        );
        todoStateStore = dependencies.todoStateStore;
        chatUiStateAssembler = new ChatUiStateAssembler(
                modelRepository,
                aiBehaviorSettingsRepository,
                inputSettingsRepository,
                outputSettingsRepository,
                contextManager
        );
        toolMessageController = new ToolMessageController(messages, this::nextId);
        conversationPersistenceController = new ConversationPersistenceController(
                context,
                chatSessionStore,
                messages,
                conversationRepository,
                aiBehaviorSettingsRepository,
                learningContextRepository,
                new ConversationPersistenceController.Host() {
                    @Override
                    public String projectPath() {
                        return projectPath;
                    }

                    @Override
                    public String defaultConversationTitle(Context context) {
                        return context.getString(R.string.drawer_new_conversation);
                    }
                }
        );
        extensionDraftController = new ExtensionDraftController(
                modelRepository,
                modelClient,
                toolRegistry,
                toolSettingsRepository,
                extensionRepository
        );
        extensionManagementController = new ExtensionManagementController(
                extensionRepository,
                ipcProviderRepository,
                toolRegistry,
                new ExtensionManagementController.Host() {
                    @Override
                    public String projectPath() {
                        return projectPath;
                    }

                    @Override
                    public void returnToScreen(String screenId) {
                        MainCoordinator.this.returnToScreen(screenId);
                    }

                    @Override
                    public void refreshVisibleScreen(String screenId) {
                        MainCoordinator.this.refreshVisibleScreen(screenId);
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                }
        );
        modelPromptController = new ModelPromptController(
                messages,
                chatSessionStore,
                aiBehaviorSettingsRepository,
                chatModeRepository,
                promptTemplateRepository,
                learningContextRepository,
                contextManager,
                modelRepository,
                extensionRepository,
                systemPromptProvider,
                toolSettingsRepository,
                toolRegistry,
                todoStateStore,
                new ModelPromptController.Host() {
                    @Override
                    public String syncModePermission() {
                        return MainCoordinator.this.syncModePermission();
                    }

                    @Override
                    public String projectPath() {
                        return projectPath;
                    }

                    @Override
                    public String projectSource() {
                        return projectSource;
                    }

                    @Override
                    public boolean isTerminalProviderExecutionMode() {
                        return MainCoordinator.this.isTerminalProviderExecutionMode();
                    }
                }
        );
        directoryPickerController = new DirectoryPickerController(
                fileTreeRepository,
                sshFileTreeRepository,
                backgroundTasks::execute,
                mainThread::post,
                new DirectoryPickerController.Host() {
                    @Override
                    public boolean isViewAttached() {
                        return view != null;
                    }

                    @Override
                    public String projectPath() {
                        return projectPath;
                    }

                    @Override
                    public boolean isTermuxSshHost() {
                        return MainCoordinator.this.isTermuxSshHost();
                    }

                    @Override
                    public void applySelectedProject(String path, boolean ssh) {
                        projectWorkspaceController.applyDirectoryPickerProject(path, ssh);
                    }

                    @Override
                    public void hideDirectoryPicker() {
                        if (view != null) {
                            view.hideDirectoryPicker();
                        }
                    }

                    @Override
                    public void showDirectoryPicker(
                            String title,
                            String subtitle,
                            FileTreeNode tree,
                            String selectedPath,
                            boolean loading,
                            String message
                    ) {
                        if (view != null) {
                            view.showDirectoryPicker(title, subtitle, tree, selectedPath, loading, message);
                        }
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                }
        );
        storageMaintenanceController = new StorageMaintenanceController(
                context,
                messages,
                chatSessionStore,
                conversationRepository,
                new StorageMaintenanceController.Host() {
                    @Override
                    public void clearCurrentConversation() {
                        generationFlowController.clearSessionAutoToolConfirmations();
                        resetTodoState();
                    }

                    @Override
                    public void refreshStorageScreen() {
                        refreshVisibleScreen("storage");
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                }
        );
        contextCompactionController = new ContextCompactionController(
                context,
                messages,
                chatSessionStore,
                modelRepository,
                aiBehaviorSettingsRepository,
                contextCompactionService,
                contextManager,
                backgroundTasks,
                mainThread,
                new ContextCompactionController.Host() {
                    @Override
                    public String nextId() {
                        return MainCoordinator.this.nextId();
                    }

                    @Override
                    public void persistCurrentConversation() {
                        MainCoordinator.this.persistCurrentConversation();
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }

                    @Override
                    public void showNotice(String message) {
                        MainCoordinator.this.showNotice(message);
                    }

                    @Override
                    public void startInitialModelRequest(
                            int generationId,
                            ModelConfig selectedModel,
                            ModelCancellationToken cancellationToken,
                            String userInput
                    ) {
                        generationFlowController.startInitialModelRequest(generationId, selectedModel, cancellationToken, userInput);
                    }

                    @Override
                    public void startGenerationKeepAlive() {
                        MainCoordinator.this.startGenerationKeepAlive();
                    }

                    @Override
                    public void stopGenerationKeepAlive() {
                        MainCoordinator.this.stopGenerationKeepAlive();
                    }

                    @Override
                    public void setCurrentCancellationToken(ModelCancellationToken cancellationToken) {
                        currentCancellationToken = cancellationToken;
                    }

                    @Override
                    public ModelCancellationToken currentCancellationToken() {
                        return currentCancellationToken;
                    }

                    @Override
                    public void showSheet(String title, List<SheetOption> options) {
                        if (view != null) {
                            view.showSheet(title, new ArrayList<>(options));
                        }
                    }
                }
        );
        ipcProviderController = new IpcProviderController(
                context,
                ipcProviderRepository,
                dependencies.ipcProviderScanner,
                ipcProviderManager,
                new IpcProviderController.Host() {
                    @Override
                    public boolean isTerminalProviderExecutionMode() {
                        return MainCoordinator.this.isTerminalProviderExecutionMode();
                    }

                    @Override
                    public void applyTerminalProviderProjectPath(String path, String label) {
                        projectPath = path == null ? "" : path;
                        projectSource = WorkspacePaths.SOURCE_EXTERNAL;
                        projectLabel = label == null || label.length() == 0 ? "LineCode" : label;
                        expandedFilePaths.clear();
                        if (projectPath.length() > 0) {
                            expandedFilePaths.add(projectPath);
                        }
                    }

                    @Override
                    public void clearTerminalProviderProjectPath() {
                        projectPath = "";
                        projectLabel = "LineCode";
                        projectSource = WorkspacePaths.SOURCE_DEFAULT;
                        expandedFilePaths.clear();
                    }

                    @Override
                    public void requestIpcFileTreeLoad(boolean force) {
                        MainCoordinator.this.requestIpcFileTreeLoad(force);
                    }

                    @Override
                    public void refreshVisibleScreen(String screenId) {
                        MainCoordinator.this.refreshVisibleScreen(screenId);
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                }
        );
        agentExecutionController = new AgentExecutionController(
                modelClient,
                aiBehaviorSettingsRepository,
                (cn.lineai.data.repository.ToolSettingsRepository) toolSettingsRepository,
                toolExecutor,
                toolRegistry,
                (cn.lineai.data.repository.ExtensionRepository) extensionRepository
        );
        generationFlowController = new GenerationFlowController(
                messages,
                chatSessionStore,
                modelClient,
                aiBehaviorSettingsRepository,
                memoryExtractionService,
                extensionRepository,
                toolRegistry,
                toolExecutor,
                toolExecutionCoordinator,
                toolSettingsRepository,
                toolMessageController,
                modelPromptController,
                generationController,
                agentExecutionController,
                todoStateStore,
                mainThread,
                backgroundTasks,
                new GenerationFlowController.Host() {
                    @Override
                    public String nextId() {
                        return MainCoordinator.this.nextId();
                    }

                    @Override
                    public String projectPath() {
                        return projectPath;
                    }

                    @Override
                    public String projectSource() {
                        return projectSource;
                    }

                    @Override
                    public String currentConversationId() {
                        return chatSessionStore.getCurrentConversationId();
                    }

                    @Override
                    public String syncModePermission() {
                        return MainCoordinator.this.syncModePermission();
                    }

                    @Override
                    public void persistCurrentConversation() {
                        MainCoordinator.this.persistCurrentConversation();
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }

                    @Override
                    public void stopGenerationKeepAlive() {
                        MainCoordinator.this.stopGenerationKeepAlive();
                    }

                    @Override
                    public void setCurrentCancellationToken(ModelCancellationToken cancellationToken) {
                        currentCancellationToken = cancellationToken;
                    }
                }
        );
        chatInteractionController = new ChatInteractionController(
                messages,
                chatSessionStore,
                conversationRepository,
                modelRepository,
                chatModeRepository,
                toolSettingsRepository,
                contextCompactionController,
                generationFlowController,
                new ChatInteractionController.Host() {
                    @Override
                    public String nextId() {
                        return MainCoordinator.this.nextId();
                    }

                    @Override
                    public String agentTerminatedMessage() {
                        return MainCoordinator.this.agentTerminatedMessage();
                    }

                    @Override
                    public String syncModePermission() {
                        return MainCoordinator.this.syncModePermission();
                    }

                    @Override
                    public void ensureCurrentConversation() {
                        MainCoordinator.this.ensureCurrentConversation();
                    }

                    @Override
                    public void persistCurrentConversation() {
                        MainCoordinator.this.persistCurrentConversation();
                    }

                    @Override
                    public void loadConversation(String id) {
                        MainCoordinator.this.loadConversation(id);
                    }

                    @Override
                    public void cancelActiveGeneration() {
                        MainCoordinator.this.cancelActiveGeneration();
                    }

                    @Override
                    public void startGenerationKeepAlive() {
                        MainCoordinator.this.startGenerationKeepAlive();
                    }

                    @Override
                    public void stopGenerationKeepAlive() {
                        MainCoordinator.this.stopGenerationKeepAlive();
                    }

                    @Override
                    public void setCurrentCancellationToken(ModelCancellationToken cancellationToken) {
                        currentCancellationToken = cancellationToken;
                    }

                    @Override
                    public void markStreamingMessagesStopped() {
                        MainCoordinator.this.markStreamingMessagesStopped();
                    }

                    @Override
                    public void resetTodoState() {
                        MainCoordinator.this.resetTodoState();
                    }

                    @Override
                    public void hideOverlays() {
                        if (view != null) {
                            view.hideOverlays();
                        }
                    }

                    @Override
                    public void showChatScreen() {
                        if (view != null) {
                            view.showChatScreen();
                        }
                    }

                    @Override
                    public void setComposerDraft(String text, ArrayList<InputAttachment> attachments) {
                        if (view != null) {
                            view.setComposerDraft(text, attachments);
                        }
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                }
        );
        attachmentPickerController = new AttachmentPickerCoordinator(
                fileTreeRepository,
                sshFileTreeRepository,
                ipcFileTreeRepository,
                backgroundTasks::execute,
                mainThread::post,
                new AttachmentPickerCoordinator.Host() {
                    @Override
                    public boolean isStreaming() {
                        return chatSessionStore.isStreaming();
                    }

                    @Override
                    public boolean isSshExecutionMode() {
                        return MainCoordinator.this.isSshExecutionMode();
                    }

                    @Override
                    public boolean isTerminalProviderExecutionMode() {
                        return MainCoordinator.this.isTerminalProviderExecutionMode();
                    }

                    @Override
                    public String projectPath() {
                        return projectPath;
                    }

                    @Override
                    public String defaultHomePath() {
                        return projectRepository.getDefaultHomePath();
                    }

                    @Override
                    public boolean isViewAttached() {
                        return view != null;
                    }

                    @Override
                    public void showAttachmentPicker(String title, FileTreeNode tree, boolean loading, String message, String source) {
                        if (view != null) {
                            view.showAttachmentPicker(title, tree, loading, message, source);
                        }
                    }
                }
        );
        applyProject(projectRepository.ensureSelectedProjectPath(toolSettingsRepository.getExecutionMode()));
        expandedFilePaths.add(projectPath);
        loadCurrentConversation();
    }

    @Override
    public void attachView(MainContract.View view) {
        this.view = view;
        applyProject(projectRepository.ensureSelectedProjectPath(toolSettingsRepository.getExecutionMode()));
        expandedFilePaths.add(projectPath);
        render();
        requestSshFileTreeLoad(false);
        requestIpcFileTreeLoad(false);
        projectWorkspaceController.validateSelectedProjectAvailabilityOnStartup();
    }

    @Override
    public void detachView() {
        view = null;
    }

    @Override
    public void destroy() {
        ipcProviderManager.removeStateListener(ipcProviderController);
        detachView();
        cancelActiveGeneration();
        stopGenerationKeepAlive();
        HttpServerTool.stopActiveServer();
        backgroundTasks.shutdownNow();
    }

    @Override
    public void onMenuClick() {
        requestSshFileTreeLoad(false);
        requestIpcFileTreeLoad(false);
        if (view != null) {
            view.showDrawer();
        }
    }

    @Override
    public void onProjectClick() {
        projectWorkspaceController.showProjectSheet();
    }

    @Override
    public void onPermissionClick() {
        permissionModeController.showPermissionSheet();
    }

    @Override
    public void onNewConversation() {
        chatInteractionController.newConversation();
    }

    @Override
    public void onConversationSelected(String id) {
        chatInteractionController.selectConversation(id);
    }

    @Override
    public void onConversationDeleted(String id) {
        chatInteractionController.deleteConversation(id);
    }

    @Override
    public void onCurrentProjectRemoveRequested() {
        projectWorkspaceController.removeCurrentProject();
    }

    @Override
    public void onFileNodeSelected(String path, boolean directory) {
        if (path == null || path.length() == 0) {
            return;
        }
        if (directory) {
            if (expandedFilePaths.contains(path)) {
                expandedFilePaths.remove(path);
                if (isSshExecutionMode()) {
                    sshFileTreeController.rebuildCachedTree();
                }
                if (isTerminalProviderExecutionMode()) {
                    ipcFileTreeController.rebuildCachedTree();
                }
            } else {
                expandedFilePaths.add(path);
                if (isSshExecutionMode()) {
                    sshFileTreeController.requestDirectoryLoad(path, false, false);
                    sshFileTreeController.rebuildCachedTree();
                }
                if (isTerminalProviderExecutionMode()) {
                    ipcFileTreeController.requestDirectoryLoad(path, false, false);
                    ipcFileTreeController.rebuildCachedTree();
                }
            }
            render();
        }
    }

    @Override
    public void onFileNodeLongPressed(String path, String name, boolean directory, boolean root) {
        fileOperationController.showFileNodeActions(path, name, directory, root);
    }

    @Override
    public void onFileTreeActivated() {
        requestSshFileTreeLoad(false);
        requestIpcFileTreeLoad(false);
        render();
    }

    @Override
    public void onFileTreeRefresh() {
        expandedFilePaths.clear();
        if (projectPath.length() > 0) {
            expandedFilePaths.add(projectPath);
        }
        requestSshFileTreeLoad(true);
        requestIpcFileTreeLoad(true);
        render();
    }

    @Override
    public void onDirectoryPickerNodeSelected(String path) {
        directoryPickerController.onNodeSelected(path);
    }

    @Override
    public void onDirectoryPickerConfirmed() {
        directoryPickerController.onConfirmed();
    }

    @Override
    public void onDirectoryPickerCancelled() {
        directoryPickerController.onCancelled();
    }

    @Override
    public void onDialogInputSubmitted(String actionId, String value) {
        String id = actionId == null ? "" : actionId;
        if (projectWorkspaceController.handleDialogInput(id, value)) {
            return;
        }
        if (id.startsWith("file:create_file:")) {
            fileOperationController.createFileFromInput(id.substring("file:create_file:".length()), value);
            return;
        }
        if (id.startsWith("file:create_folder:")) {
            fileOperationController.createFolderFromInput(id.substring("file:create_folder:".length()), value);
            return;
        }
        if (id.startsWith("file:rename:")) {
            fileOperationController.renameFileNodeFromInput(id.substring("file:rename:".length()), value);
        }
    }

    @Override
    public void onDialogConfirmed(String actionId) {
        String id = actionId == null ? "" : actionId;
        if (id.startsWith("file:delete:")) {
            fileOperationController.deleteFileNode(id.substring("file:delete:".length()));
            return;
        }
        if ("data:import_linecode".equals(id)) {
            lineCodeArchiveController.confirmImport();
        }
    }

    @Override
    public void onMoreClick() {
        if (view == null) {
            return;
        }
        ArrayList<SheetOption> options = new ArrayList<>();
        options.add(new SheetOption("tutorial", "教程", "打开初学者教程", false));
        options.add(new SheetOption("settings", context.getString(R.string.screen_settings_title), "模型、主题、数据管理", false));
        options.add(new SheetOption("compact", "压缩上下文", "将早期上下文总结为隐藏摘要", false));
        options.add(new SheetOption("clear", "清空对话", "清空当前对话消息", false));
        view.showSheet("更多", options);
    }

    @Override
    public void onSendMessage(String text) {
        chatInteractionController.sendMessage(text);
    }

    @Override
    public void onSendMessage(String text, List<InputAttachment> attachments) {
        chatInteractionController.sendMessage(text, attachments);
    }

    @Override
    public void onRecallMessage(String messageId) {
        chatInteractionController.recallMessage(messageId);
    }

    @Override
    public void onAttachmentPickerRequested() {
        attachmentPickerController.onAttachmentPickerRequested();
    }

    @Override
    public void onAttachmentPickerNodeSelected(String path, boolean directory) {
        attachmentPickerController.onAttachmentPickerNodeSelected(path, directory);
    }

    @Override
    public void onAttachmentPickerCancelled() {
        attachmentPickerController.onAttachmentPickerCancelled();
    }

    @Override
    public void onChatModeChanged(String mode) {
        chatInteractionController.changeChatMode(mode);
    }

    @Override
    public void onStopGeneration() {
        chatInteractionController.stopGeneration();
    }

    private void showCompactConfirmation() {
        if (view != null) {
            contextCompactionController.showCompactConfirmation();
        }
    }

    private void startManualContextCompaction() {
        contextCompactionController.startManualContextCompaction();
    }

    private void startContextCompaction(
            int generationId,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            boolean continueAfterCompaction,
            String activeUserMessageId,
            String userInput
    ) {
        contextCompactionController.startContextCompaction(
                generationId,
                selectedModel,
                cancellationToken,
                continueAfterCompaction,
                activeUserMessageId,
                userInput
        );
    }

    @Override
    public void onToolReview(String toolCallId, String state, String diffId) {
        if (toolCallId == null || toolCallId.length() == 0) {
            return;
        }
        if (generationFlowController.isPendingToolReview(toolCallId)) {
            generationFlowController.handleToolReview(state);
            return;
        }
        String normalizedState = "rejected".equals(state) ? "rejected" : "accepted";
        String resolvedDiffId = diffId == null ? "" : diffId;
        if ("rejected".equals(normalizedState)) {
            if (resolvedDiffId.length() == 0) {
                resolvedDiffId = findToolMessageDiffId(toolCallId);
            }
            if (resolvedDiffId.length() > 0) {
                String targetDiffId = resolvedDiffId;
                backgroundTasks.execute("linecode-diff-revert", () -> {
                    DiffRecord diffRecord = diffRepository.getDiff(targetDiffId);
                    DiffRepository.RevertResult result = diffRepository.revertDiff(targetDiffId);
                    String filePath = diffRecord == null ? "" : diffRecord.getFilePath();
                    mainThread.post(() -> {
                        updateToolReview(toolCallId, targetDiffId, result.isSuccess() ? "rejected" : "", result.getMessage());
                        if (result.isSuccess()) {
                            refreshFileTreeAfterRevert(filePath);
                        }
                        persistCurrentConversation();
                        render();
                    });
                });
                return;
            }
        }
        updateToolReview(toolCallId, resolvedDiffId, normalizedState, "");
        persistCurrentConversation();
        render();
    }

    @Override
    public void onSheetOptionSelected(String id) {
        if (projectWorkspaceController.handleSheetOption(id)) {
            return;
        }
        if (id != null && id.startsWith("file:create_file:")) {
            fileOperationController.requestCreateFile(id.substring("file:create_file:".length()));
            return;
        } else if (id != null && id.startsWith("file:create_folder:")) {
            fileOperationController.requestCreateFolder(id.substring("file:create_folder:".length()));
            return;
        } else if (id != null && id.startsWith("file:copy:")) {
            fileOperationController.copyFileNode(id.substring("file:copy:".length()));
        } else if (id != null && id.startsWith("file:paste:")) {
            fileOperationController.pasteFileNode(id.substring("file:paste:".length()));
            return;
        } else if (id != null && id.startsWith("file:rename:")) {
            fileOperationController.requestRenameFileNode(id.substring("file:rename:".length()));
            return;
        } else if (id != null && id.startsWith("file:delete:")) {
            fileOperationController.requestDeleteFileNode(id.substring("file:delete:".length()));
            return;
        } else if (permissionModeController.applyPermissionModeOption(id)) {
            // Handled above.
        } else if ("settings".equals(id)) {
            showScreen("settings");
        } else if ("tutorial".equals(id)) {
            showScreen("tutorial");
        } else if ("compact".equals(id)) {
            showCompactConfirmation();
            return;
        } else if ("compact:confirm".equals(id)) {
            startManualContextCompaction();
        } else if ("compact:cancel".equals(id)) {
            // The bottom sheet is closed below.
        } else if ("clear".equals(id)) {
            chatInteractionController.clearCurrentConversation();
        }
        if (view != null && !"settings".equals(id) && !"tutorial".equals(id)) {
            view.hideOverlays();
        }
        render();
    }

    @Override
    public void onScreenBack() {
        navigateScreenBack("");
    }

    @Override
    public void onScreenBackFrom(String screenId) {
        navigateScreenBack(screenId);
    }

    private void navigateScreenBack(String visibleScreenId) {
        screenNavigationController.backFrom(visibleScreenId, navigationHost);
    }

    @Override
    protected MainContract.View delegateView() {
        return view;
    }

    @Override
    protected SettingsManagementController settingsDelegate() {
        return settingsManagementController;
    }

    @Override
    protected LineCodeArchiveController archiveDelegate() {
        return lineCodeArchiveController;
    }

    @Override
    protected ExtensionManagementController extensionManagementDelegate() {
        return extensionManagementController;
    }

    @Override
    protected ExtensionDraftController extensionDraftDelegate() {
        return extensionDraftController;
    }

    @Override
    protected IpcProviderController ipcProviderDelegate() {
        return ipcProviderController;
    }

    @Override
    protected ModelManagementController modelManagementDelegate() {
        return modelManagementController;
    }

    @Override
    protected void delegateShowScreen(String screenId) {
        showScreen(screenId);
    }

    @Override
    protected void delegateRefreshVisibleScreen(String screenId) {
        refreshVisibleScreen(screenId);
    }

    @Override
    protected void delegateRender() {
        render();
    }

    @Override
    protected void delegateReloadExtensions() {
        toolRegistry.reloadExtensions();
    }

    private void reloadAfterLineCodeImport() {
        toolRegistry.reloadExtensions();
        applyProject(projectRepository.ensureSelectedProjectPath(toolSettingsRepository.getExecutionMode()));
        sshFileTreeController.invalidateFileTree();
        ipcFileTreeController.invalidateFileTree();
        requestSshFileTreeLoad(true);
        requestIpcFileTreeLoad(true);
        ConversationRecord current = conversationRepository.getCurrentConversation();
        if (current == null) {
            chatSessionStore.clearCurrentConversation();
        } else {
            applyConversation(current);
        }
        refreshVisibleScreen("data");
        render();
    }

    @Override
    public List<ConversationRecord> getConversationMetas() {
        return conversationRepository.getConversationMetas();
    }

    @Override
    public String getCurrentConversationId() {
        return chatSessionStore.getCurrentConversationId();
    }

    @Override
    public FileTreeNode getFileTree() {
        if (isTerminalProviderExecutionMode()) {
            return ipcFileTreeController.getFileTree();
        }
        if (isSshExecutionMode()) {
            return sshFileTreeController.getFileTree();
        }
        return fileTreeRepository.buildTree(projectPath, expandedFilePaths);
    }

    @Override
    public boolean canRemoveCurrentProject() {
        return projectWorkspaceController.canRemoveCurrentProject();
    }

    @Override
    public void onModelQuickSwitch(String modelId) {
        if (chatSessionStore.isStreaming()) {
            return;
        }
        modelRepository.setSelectedModelId(modelId);
        render();
    }

    @Override
    public void onModelTest(ModelConfig model) {
        backgroundTasks.execute("linecode-model-test", () -> {
            long startTime = System.currentTimeMillis();
            try {
                ModelCompletionResponse response = modelClient.complete(model,
                        Collections.singletonList(new UserModelMessage("Calculate 1+1 and reply with any result.")));
                long duration = System.currentTimeMillis() - startTime;
                String rawText = response.getText() == null ? "" : response.getText().trim();
                boolean hasData = rawText.length() > 0;
                String summary = context.getString(hasData
                        ? R.string.screen_model_add_test_success
                        : R.string.screen_model_add_test_success_no_data, duration);
                String message = summary + "\n\n" + context.getString(R.string.screen_model_add_test_raw_response) + "\n" + rawText;
                mainThread.post(() -> {
                    if (view != null) {
                        view.showConfirmationDialog(
                                context.getString(R.string.screen_model_add_test_result_title),
                                message,
                                context.getString(R.string.screen_model_add_test_result_confirm),
                                false,
                                "modelTestResult");
                    }
                });
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                mainThread.post(() -> Toast.makeText(context,
                        context.getString(R.string.screen_model_add_test_error, message) + " (" + duration + "ms)",
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override
    public void onExternalProjectTreePicked(String treeUri) {
        projectWorkspaceController.onExternalProjectTreePicked(treeUri);
    }

    @Override
    public void onExternalProjectPickerCancelled() {
        projectWorkspaceController.onExternalProjectPickerCancelled();
    }

    @Override
    public void onStoragePermissionResult() {
        projectWorkspaceController.onStoragePermissionResult();
    }

    private void showScreen(String screenId) {
        screenNavigationController.showScreen(screenId, navigationHost);
    }

    private void refreshVisibleScreen(String screenId) {
        if (view != null) {
            view.invalidateScreen(screenId);
        }
        screenNavigationController.refreshVisibleScreen(screenId, navigationHost);
    }

    private void returnToScreen(String screenId) {
        if (view != null) {
            view.invalidateScreen(screenId);
        }
        screenNavigationController.returnToScreen(screenId, navigationHost);
    }

    private void applyProject(ProjectRecord project) {
        if (project == null) {
            return;
        }
        projectLabel = project.getLabel().length() == 0 ? "LineCode" : project.getLabel();
        projectSource = project.getSource();
        projectPath = WorkspacePaths.displayPath(project.getPath());
        if (!WorkspacePaths.SOURCE_SSH.equals(projectSource) && projectPath.length() == 0) {
            projectPath = projectRepository.getDefaultHomePath();
        }
        expandedFilePaths.clear();
        if (projectPath.length() > 0) {
            expandedFilePaths.add(projectPath);
        }
        sshFileTreeController.invalidateFileTree();
    }

    private void startSshDirectoryPicker() {
        directoryPickerController.startSsh();
    }

    private void requestSshFileTreeLoad(boolean force) {
        sshFileTreeController.requestFileTreeLoad(force);
    }

    private void requestIpcFileTreeLoad(boolean force) {
        ipcFileTreeController.requestFileTreeLoad(force);
    }

    private boolean isSshExecutionMode() {
        return ToolSettingsRepository.EXECUTION_SSH.equals(toolSettingsRepository.getExecutionMode());
    }

    private boolean isTerminalProviderExecutionMode() {
        return ToolSettingsRepository.EXECUTION_TERMINAL_PROVIDER.equals(toolSettingsRepository.getExecutionMode());
    }

    private boolean isTermuxSshHost() {
        SshConfig config = sshService.getConfig();
        String host = config == null ? "" : config.getHost();
        return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host);
    }

    private String basename(String path) {
        return WorkspacePaths.basename(path == null ? "" : path);
    }

    private String parentPath(String path) {
        String value = path == null ? "" : path.trim();
        int index = value.lastIndexOf('/');
        if (index <= 0) {
            return projectPath;
        }
        return value.substring(0, index);
    }

    private void showNotice(String text) {
        messages.add(new ChatMessage(nextId(), ChatMessage.Role.ASSISTANT, text, false));
        render();
    }

    private String syncModePermission() {
        String mode = chatModeRepository.getMode();
        chatModeRepository.applyMode(mode, toolSettingsRepository);
        return chatModeRepository.getMode();
    }

    private void render() {
        if (view == null) {
            return;
        }
        String activeChatMode = syncModePermission();
        view.render(chatUiStateAssembler.assemble(
                projectLabel,
                projectSource,
                projectPath,
                chatSessionStore.getCurrentConversationId(),
                activeChatMode,
                chatSessionStore.isStreaming(),
                messages
        ));
    }

    private void resetTodoState() {
        if (todoStateStore != null) {
            todoStateStore.clear();
        }
    }

    private void cancelActiveGeneration() {
        generationFlowController.cancelActiveGeneration();
        if (currentCancellationToken != null) {
            currentCancellationToken.cancel();
            currentCancellationToken = null;
        }
        stopGenerationKeepAlive();
    }

    private void startGenerationKeepAlive() {
        if (generationKeepAliveActive) {
            return;
        }
        generationKeepAliveActive = true;
        try {
            KeepAliveService.startGeneration(context);
        } catch (Exception ignored) {
        }
    }

    private void stopGenerationKeepAlive() {
        if (!generationKeepAliveActive) {
            return;
        }
        generationKeepAliveActive = false;
        try {
            KeepAliveService.stopGeneration(context);
        } catch (Exception ignored) {
        }
    }

    private void markStreamingMessagesStopped() {
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message.isStreaming()) {
                if (message.isCompactBlock()) {
                    messages.set(i, message.withCompactStatus(ChatMessage.COMPACT_STATUS_ERROR, false));
                } else {
                    messages.set(i, message.withContent(message.getContent(), message.getReasoningContent(), false));
                }
            }
        }
    }

    private String findToolMessageDiffId(String toolCallId) {
        return toolMessageController.findToolMessageDiffId(toolCallId);
    }

    private void updateToolReview(String toolCallId, String diffId, String reviewState, String reviewMessage) {
        toolMessageController.updateToolReview(toolCallId, diffId, reviewState, reviewMessage);
    }

    private void refreshFileTreeAfterRevert(String filePath) {
        String parentPath = parentPath(filePath);
        if (parentPath.length() > 0) {
            expandedFilePaths.add(parentPath);
        }
        if (isSshExecutionMode()) {
            sshFileTreeController.refreshDirectoryAfterFileOperation(parentPath);
        }
        if (isTerminalProviderExecutionMode()) {
            ipcFileTreeController.refreshDirectoryAfterFileOperation(parentPath);
        }
    }

    private void loadCurrentConversation() {
        conversationPersistenceController.loadCurrentConversation();
    }

    private void loadConversation(String id) {
        conversationPersistenceController.loadConversation(id);
        chatInteractionController.resetModelTracking();
    }

    private void applyConversation(ConversationRecord conversation) {
        conversationPersistenceController.applyConversation(conversation);
    }

    private void ensureCurrentConversation() {
        conversationPersistenceController.ensureCurrentConversation();
    }

    private void persistCurrentConversation() {
        conversationPersistenceController.persistCurrentConversation();
    }

    private String nextId() {
        return chatSessionStore.nextMessageId();
    }

    @Override
    public void onClearDiffCache() {
        storageMaintenanceController.clearDiffCache();
    }

    @Override
    public void onClearChatHistory() {
        storageMaintenanceController.clearChatHistory();
    }

    @Override
    public void onKeepAliveSettingsChanged() {
        storageMaintenanceController.applyKeepAliveSettings();
    }
}
