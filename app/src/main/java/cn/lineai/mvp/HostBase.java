package cn.lineai.mvp;

abstract class HostBase implements CoordinatorHost {
    private final MainCoordinator coordinator;

    HostBase(MainCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    protected MainCoordinator coordinator() {
        return coordinator;
    }

    @Override
    public String basename(String path) {
        return coordinator.basename(path);
    }

    @Override
    public void render() {
        coordinator.render();
    }

    @Override
    public String parentPath(String path) {
        return coordinator.parentPath(path);
    }

    public String projectPath() {
        return coordinator.projectPath();
    }

    public String projectLabel() {
        return coordinator.projectLabel();
    }

    public boolean isSshExecutionMode() {
        return coordinator.isSshExecutionMode();
    }

    public boolean isTerminalProviderExecutionMode() {
        return coordinator.isTerminalProviderExecutionMode();
    }

    public void showNotice(String text) {
        coordinator.showNotice(text);
    }

    public boolean isViewAttached() {
        return coordinator.isViewAttached();
    }

    public void viewHideOverlays() {
        coordinator.viewHideOverlays();
    }

    public void viewShowScreen(String screenId) {
        coordinator.viewShowScreen(screenId);
    }

    public void viewShowChatScreen() {
        coordinator.viewShowChatScreen();
    }

    public void refreshVisibleScreen(String screenId) {
        coordinator.refreshVisibleScreen(screenId);
    }

    public void returnToScreen(String screenId) {
        coordinator.returnToScreen(screenId);
    }
}
