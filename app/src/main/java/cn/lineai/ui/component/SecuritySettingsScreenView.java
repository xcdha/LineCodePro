package cn.lineai.ui.component;

import android.content.Context;
import android.widget.LinearLayout;
import cn.lineai.R;
import cn.lineai.model.OutputSettings;
import cn.lineai.ui.theme.LineTheme;

public final class SecuritySettingsScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        void onAllowAnyHttpChanged(boolean enabled);

        void onBrowserJavaScriptChanged(boolean enabled);
    }

    private final Listener listener;

    public SecuritySettingsScreenView(Context context, OutputSettings settings, Listener listener) {
        super(context, context.getString(R.string.screen_security_title), listener::onBack, null);
        this.listener = listener;
        OutputSettings safeSettings = settings == null
                ? new OutputSettings(false, OutputSettings.BROWSER_BUILTIN)
                : settings;
        boolean allowAnyHttp = safeSettings.isAllowAnyHttp();
        boolean browserJavaScriptEnabled = safeSettings.isBrowserJavaScriptEnabled();
        LinearLayout content = getContent();

        SettingsSectionView http = new SettingsSectionView(context, context.getString(R.string.screen_security_section_http));
        http.addRow(new SwitchRowView(context, IconButtonView.SHIELD_CHECK,
                context.getString(R.string.settings_row_security_allow_any_http_title),
                context.getString(R.string.settings_row_security_allow_any_http_desc),
                allowAnyHttp,
                (buttonView, isChecked) -> listener.onAllowAnyHttpChanged(isChecked)), false);
        content.addView(http, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView browser = new SettingsSectionView(context, context.getString(R.string.screen_security_section_browser));
        browser.addRow(new SwitchRowView(context, IconButtonView.CODE,
                context.getString(R.string.screen_output_browser_js_label),
                context.getString(R.string.screen_output_browser_js_desc),
                browserJavaScriptEnabled,
                (buttonView, isChecked) -> listener.onBrowserJavaScriptChanged(isChecked)), false);
        content.addView(browser, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }
}
