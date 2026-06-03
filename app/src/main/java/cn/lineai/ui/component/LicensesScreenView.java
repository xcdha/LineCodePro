package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import cn.lineai.ui.theme.LineTheme;

public final class LicensesScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();
    }

    public LicensesScreenView(Context context, Listener listener) {
        super(context, "开源许可", listener::onBack, null);
        LinearLayout content = getContent();
        LineTheme.padding(content, LineTheme.MD, LineTheme.MD, LineTheme.MD, 100);

        addLicense(content, "openai/codex", "source reference · Apache-2.0", "OpenAI Codex is licensed under the Apache License, Version 2.0.");
        addLicense(content, "lucide-react-native", "1.14.0 · ISC", "Lucide 图标来自 LineAI node_modules，并转换为 Android vector drawable。");
        addLicense(content, "react-native", "0.85.3 · MIT", "LineAI 参考工程中的 React Native 运行时许可。");
        addLicense(content, "react", "19.2.3 · MIT", "LineAI 参考工程中的 React 许可。");
        addLicense(content, "highlight.js", "11.11.1 · BSD-3-Clause", "代码高亮依赖许可。");
        addLicense(content, "commonmark-java", "0.28.0 · BSD-2-Clause", "Markdown 解析与 GFM 表格扩展依赖许可。");
    }

    private void addLicense(LinearLayout content, String name, String meta, String text) {
        Context context = content.getContext();
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_ELEVATED, 12));
        LineTheme.padding(item, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);
        item.addView(LineTheme.text(context, name, LineTheme.FONT_MD, LineTheme.TEXT, Typeface.NORMAL), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        item.addView(LineTheme.text(context, meta, LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        item.addView(LineTheme.text(context, text, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        content.addView(item, params);
    }
}
