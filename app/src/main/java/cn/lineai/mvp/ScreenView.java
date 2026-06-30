package cn.lineai.mvp;

public interface ScreenView {
    void showScreen(String screenId);

    void showChatScreen();

    void openExternalUrl(String url);

    void recreateForTheme(String screenId);

    default void evictScreen(String screenId) {
        invalidateScreen(screenId);
    }

    void invalidateScreen(String screenId);
}
