package cn.lineai.ui.markdown;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;

public final class MarkdownCodeBlockView extends LinearLayout {
    public MarkdownCodeBlockView(Context context, String code, String language) {
        this(context, code, language, false);
    }

    public MarkdownCodeBlockView(Context context, String code, String language, boolean wrap) {
        super(context);
        setOrientation(VERTICAL);
        setBackground(LineTheme.roundedStroke(context, LineTheme.CODE_BG, 8, LineTheme.CODE_BORDER));
        LineTheme.padding(this, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);

        String lang = language == null ? "" : language.trim();
        if (lang.length() > 0) {
            TextView label = LineTheme.text(context, lang, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            labelParams.bottomMargin = LineTheme.dp(context, LineTheme.SM);
            addView(label, labelParams);
        }

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        TextView text = LineTheme.text(context, code == null ? "" : code, LineTheme.FONT_SM, LineTheme.TEXT, Typeface.NORMAL);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextIsSelectable(true);
        text.setIncludeFontPadding(false);
        text.setLineSpacing(LineTheme.dp(context, 3), 1.0f);
        text.setSingleLine(false);
        text.setHorizontallyScrolling(!wrap);
        if (wrap) {
            addView(text, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            return;
        }
        scroll.addView(text, new HorizontalScrollView.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(scroll, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }
}
