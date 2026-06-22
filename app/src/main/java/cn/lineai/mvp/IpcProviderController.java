package cn.lineai.mvp;

import android.content.Context;
import android.util.Log;
import cn.lineai.data.repository.IpcProviderStore;
import cn.lineai.ipc.BaseIpcProvider;
import cn.lineai.ipc.IpcProviderConfig;
import cn.lineai.ipc.IpcProviderConnectionState;
import cn.lineai.ipc.IpcProviderManager;
import cn.lineai.ipc.IpcProviderScanner;
import cn.lineai.ipc.IpcProviderStateListener;
import cn.lineai.ipc.IpcProviderType;
import cn.lineai.ipc.ScannedProvider;
import cn.lineai.ipc.terminal.TerminalIpcProvider;
import java.util.Collections;
import java.util.List;

public final class IpcProviderController implements IpcProviderStateListener {
    public interface Host {
        boolean isTerminalProviderExecutionMode();

        void applyTerminalProviderProjectPath(String path, String label);

        void clearTerminalProviderProjectPath();

        void requestIpcFileTreeLoad(boolean force);

        void refreshVisibleScreen(String screenId);

        void render();
    }

    private static final String TAG = "IpcProviderController";

    private final Context context;
    private final IpcProviderStore ipcProviderStore;
    private final IpcProviderScanner ipcProviderScanner;
    private final IpcProviderManager ipcProviderManager;
    private final Host host;
    private List<ScannedProvider> terminalProviderScanResults = Collections.emptyList();
    private boolean terminalProviderHasScanned;
    private boolean ipcProjectPathApplied;

    public IpcProviderController(
            Context context,
            IpcProviderStore ipcProviderStore,
            IpcProviderScanner ipcProviderScanner,
            IpcProviderManager ipcProviderManager,
            Host host
    ) {
        this.context = context.getApplicationContext();
        this.ipcProviderStore = ipcProviderStore;
        this.ipcProviderScanner = ipcProviderScanner;
        this.ipcProviderManager = ipcProviderManager;
        this.host = host;
        if (this.ipcProviderManager != null) {
            this.ipcProviderManager.addStateListener(this);
        }
        restoreIpcProviders();
    }

    @Override
    public void onStateChanged(BaseIpcProvider provider, IpcProviderConnectionState newState, Throwable cause) {
        onIpcProviderStateChanged(provider, newState, cause);
    }

    public List<ScannedProvider> onTerminalProviderScan() {
        if (ipcProviderScanner == null) {
            terminalProviderScanResults = Collections.emptyList();
        } else {
            terminalProviderScanResults = ipcProviderScanner.scan(context, IpcProviderType.TERMINAL);
        }
        terminalProviderHasScanned = true;
        return terminalProviderScanResults;
    }

    public List<ScannedProvider> getTerminalProviderScanResults() {
        return terminalProviderScanResults;
    }

    public boolean hasTerminalProviderScanned() {
        return terminalProviderHasScanned;
    }

    public void onTerminalProviderSaved(IpcProviderConfig config) {
        if (ipcProviderStore == null || config == null) {
            return;
        }
        IpcProviderConfig saved = ipcProviderStore.saveProvider(config);
        if (saved.isEnabled() && ipcProviderManager != null) {
            ipcProviderManager.registerAndBind(saved);
        }
        refreshTerminalProviderScreen();
    }

    public void onTerminalProviderEnabledChanged(String id, boolean enabled) {
        if (ipcProviderStore == null || ipcProviderManager == null) {
            return;
        }
        ipcProviderStore.setProviderEnabled(id, enabled);
        if (enabled) {
            IpcProviderConfig config = findIpcProvider(id);
            if (config != null) {
                ipcProviderManager.registerAndBind(config);
            }
        } else {
            ipcProviderManager.unregisterAndUnbind(id);
        }
        refreshTerminalProviderScreen();
    }

    public void onTerminalProviderDeleted(String id) {
        if (ipcProviderStore == null || ipcProviderManager == null) {
            return;
        }
        ipcProviderManager.unregisterAndUnbind(id);
        ipcProviderStore.deleteProvider(id);
        refreshTerminalProviderScreen();
    }

    private void restoreIpcProviders() {
        if (ipcProviderStore == null || ipcProviderManager == null) {
            return;
        }
        List<IpcProviderConfig> providers = ipcProviderStore.getProviders();
        for (IpcProviderConfig config : providers) {
            if (!config.isEnabled()) {
                continue;
            }
            try {
                ipcProviderManager.registerAndBind(config);
            } catch (RuntimeException e) {
                Log.w(TAG, "重连 IPC 提供者失败: " + config.getId(), e);
            }
        }
    }

    private void onIpcProviderStateChanged(
            BaseIpcProvider provider,
            IpcProviderConnectionState newState,
            Throwable cause) {
        if (provider == null || provider.getProviderType() != IpcProviderType.TERMINAL) {
            return;
        }
        if (newState == IpcProviderConnectionState.CONNECTED) {
            applyIpcProjectPath((TerminalIpcProvider) provider);
            return;
        }
        if ((newState == IpcProviderConnectionState.DISCONNECTED
                || newState == IpcProviderConnectionState.FAILED)
                && host != null
                && host.isTerminalProviderExecutionMode()
                && ipcProjectPathApplied) {
            host.clearTerminalProviderProjectPath();
            ipcProjectPathApplied = false;
            host.render();
        }
    }

    private void applyIpcProjectPath(TerminalIpcProvider provider) {
        if (provider == null || host == null) {
            return;
        }
        String home;
        try {
            home = provider.getHomePath();
        } catch (Exception e) {
            Log.w(TAG, "读取 IPC home 失败", e);
            home = "";
        }
        if (home.length() == 0) {
            return;
        }
        String label = provider.getConfig() == null ? "" : provider.getConfig().getName();
        host.applyTerminalProviderProjectPath(home, label);
        ipcProjectPathApplied = true;
        host.requestIpcFileTreeLoad(true);
        host.render();
    }

    private IpcProviderConfig findIpcProvider(String id) {
        if (id == null || id.length() == 0 || ipcProviderStore == null) {
            return null;
        }
        for (IpcProviderConfig config : ipcProviderStore.getProviders()) {
            if (id.equals(config.getId())) {
                return config;
            }
        }
        return null;
    }

    private void refreshTerminalProviderScreen() {
        if (host == null) {
            return;
        }
        host.refreshVisibleScreen("terminalProvider");
        host.render();
    }
}
