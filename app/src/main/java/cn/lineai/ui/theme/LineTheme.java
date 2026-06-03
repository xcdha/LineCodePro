package cn.lineai.ui.theme;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.widget.TextView;

public final class LineTheme {
    public static final int BG = Color.parseColor("#000000");
    public static final int SURFACE = Color.parseColor("#0A0A0A");
    public static final int SURFACE_ELEVATED = Color.parseColor("#141414");
    public static final int SURFACE_LIGHT = Color.parseColor("#1C1C1E");
    public static final int ACCENT = Color.parseColor("#30D158");
    public static final int ACCENT_DIM = Color.parseColor("#1A3A2A");
    public static final int ACCENT_MUTED = Color.argb(26, 48, 209, 88);
    public static final int ACCENT_MUTED_2 = Color.argb(38, 48, 209, 88);
    public static final int USER_BUBBLE = Color.parseColor("#0A84FF");
    public static final int AI_BUBBLE = Color.parseColor("#1C1C1E");
    public static final int TEXT = Color.parseColor("#FFFFFF");
    public static final int TEXT_SECONDARY = Color.parseColor("#8E8E93");
    public static final int TEXT_TERTIARY = Color.parseColor("#636366");
    public static final int TEXT_ON_COLOR = Color.parseColor("#FFFFFF");
    public static final int BORDER = Color.parseColor("#1C1C1E");
    public static final int BORDER_LIGHT = Color.parseColor("#2C2C2E");
    public static final int INPUT_BG = Color.parseColor("#1C1C1E");
    public static final int CODE_BG = Color.parseColor("#151515");
    public static final int CODE_BORDER = Color.parseColor("#2C2C2E");
    public static final int DANGER = Color.parseColor("#F85149");
    public static final int DANGER_MUTED = Color.argb(28, 248, 81, 73);
    public static final int DANGER_MUTED_2 = Color.argb(61, 248, 81, 73);
    public static final int WARNING = Color.parseColor("#FF9F0A");
    public static final int SUCCESS = Color.parseColor("#30D158");
    public static final int OVERLAY = Color.argb(165, 0, 0, 0);
    public static final int DIFF_ADD_BG = Color.argb(46, 48, 209, 88);
    public static final int DIFF_DEL_BG = Color.argb(46, 255, 69, 58);
    public static final int DIFF_ADD_TEXT = Color.parseColor("#30D158");
    public static final int DIFF_DEL_TEXT = Color.parseColor("#FF453A");

    public static final int XS = 4;
    public static final int SM = 8;
    public static final int MD = 12;
    public static final int LG = 16;
    public static final int XL = 20;
    public static final int XXL = 24;

    public static final int FONT_XS = 11;
    public static final int FONT_SM = 13;
    public static final int FONT_MD = 15;
    public static final int FONT_LG = 17;
    public static final int FONT_XL = 20;
    public static final int FONT_XXL = 28;

    private LineTheme() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    public static GradientDrawable roundedStroke(Context context, int color, float radiusDp, int strokeColor) {
        GradientDrawable drawable = rounded(context, color, radiusDp);
        drawable.setStroke(Math.max(1, dp(context, 1)), strokeColor);
        return drawable;
    }

    public static GradientDrawable roundedTop(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        float radius = dp(context, radiusDp);
        drawable.setCornerRadii(new float[] {
                radius, radius,
                radius, radius,
                0, 0,
                0, 0
        });
        return drawable;
    }

    public static GradientDrawable userBubble(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(USER_BUBBLE);
        float large = dp(context, 16);
        float small = dp(context, 4);
        drawable.setCornerRadii(new float[] {
                large, large,
                large, large,
                small, small,
                large, large
        });
        return drawable;
    }

    public static void padding(View view, int left, int top, int right, int bottom) {
        Context context = view.getContext();
        view.setPadding(dp(context, left), dp(context, top), dp(context, right), dp(context, bottom));
    }

    public static TextView text(Context context, String value, int sizeSp, int color, int style) {
        TextView textView = new TextView(context);
        textView.setText(value);
        textView.setTextColor(color);
        textView.setTextSize(sizeSp);
        textView.setIncludeFontPadding(false);
        textView.setLineSpacing(dp(context, 2), 1.0f);
        if (style != Typeface.NORMAL) {
            textView.setTypeface(Typeface.DEFAULT, style);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            textView.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
        }
        return textView;
    }

    public static TextView textMedium(Context context, String value, int sizeSp, int color) {
        TextView textView = text(context, value, sizeSp, color, Typeface.NORMAL);
        textView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return textView;
    }
}
