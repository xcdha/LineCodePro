package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;

public final class AboutScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        void onOpenLicenses();
    }

    public AboutScreenView(Context context, Listener listener) {
        super(context, "关于 LineCode", listener::onBack, null);
        LinearLayout content = getContent();
        LineTheme.padding(content, LineTheme.LG, LineTheme.LG, LineTheme.LG, 100);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        LineTheme.padding(header, 0, LineTheme.XL, 0, LineTheme.XL);

        FrameLayout iconContainer = new FrameLayout(context);
        iconContainer.setBackground(LineTheme.rounded(context, LineTheme.ACCENT_MUTED, 44));
        IconButtonView logo = new IconButtonView(context, IconButtonView.CODE);
        logo.setIconColor(LineTheme.ACCENT);
        logo.setIconSizeDp(88, 48);
        logo.setClickable(false);
        iconContainer.addView(logo, new FrameLayout.LayoutParams(LineTheme.dp(context, 88), LineTheme.dp(context, 88), Gravity.CENTER));
        header.addView(iconContainer, new LinearLayout.LayoutParams(LineTheme.dp(context, 88), LineTheme.dp(context, 88)));

        TextView name = LineTheme.text(context, "LineCode", LineTheme.FONT_XL, LineTheme.TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        header.addView(name, nameParams);
        TextView version = LineTheme.text(context, "APK 1.0 (1)", LineTheme.FONT_MD, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        versionParams.topMargin = LineTheme.dp(context, LineTheme.XS);
        header.addView(version, versionParams);
        TextView patch = LineTheme.text(context, "热补丁 内置 (1)", LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams patchParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        patchParams.topMargin = LineTheme.dp(context, 3);
        header.addView(patch, patchParams);
        content.addView(header, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        addGroupTitle(content, "版本");
        addItem(content, IconButtonView.PACKAGE, "APK 版本", "1.0 (1)", null);
        addItem(content, IconButtonView.DOWNLOAD, "热补丁版本", "内置 (1)", null);
        addItem(content, IconButtonView.REFRESH_CW, "检查更新", "手动检查热更新包", null);

        addGroupTitle(content, "开发者");
        addItem(content, IconButtonView.USER, "作者", "LangLang03", null);
        addItem(content, IconButtonView.MESSAGE_CIRCLE, "QQ", "3772548978", null);

        addGroupTitle(content, "法律信息");
        addItem(content, IconButtonView.FILE_TEXT, "开源许可列表", "", listener::onOpenLicenses);

        TextView footer = LineTheme.text(context, "Copyright 2025 LangLang. All rights reserved.", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        footerParams.topMargin = LineTheme.dp(context, LineTheme.XL);
        content.addView(footer, footerParams);
    }

    private void addGroupTitle(LinearLayout content, String title) {
        TextView text = LineTheme.text(context(), title, LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(context(), LineTheme.LG);
        params.bottomMargin = LineTheme.dp(context(), LineTheme.SM);
        params.leftMargin = LineTheme.dp(context(), LineTheme.XS);
        content.addView(text, params);
    }

    private void addItem(LinearLayout content, int icon, String label, String value, Runnable onClick) {
        ActionRowView row = new ActionRowView(context(), icon, label, value, false, onClick != null, onClick);
        row.setBackground(LineTheme.rounded(context(), LineTheme.SURFACE_ELEVATED, 12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = LineTheme.dp(context(), LineTheme.SM);
        content.addView(row, params);
    }

    private Context context() {
        return getContext();
    }
}
