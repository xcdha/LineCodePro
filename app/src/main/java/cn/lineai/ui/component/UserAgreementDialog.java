package cn.lineai.ui.component;

import android.app.AlertDialog;
import android.content.Context;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import cn.lineai.R;
import cn.lineai.ui.theme.LineTheme;

public final class UserAgreementDialog {

    private UserAgreementDialog() {
    }

    public static void show(Context context, Runnable onAgree, Runnable onDisagree) {
        if (context == null) {
            return;
        }

        FrameLayout container = new FrameLayout(context);
        container.setBackgroundColor(LineTheme.SURFACE);
        int padding = LineTheme.dp(context, LineTheme.LG);
        container.setPadding(padding, padding, padding, 0);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView textView = new TextView(context);
        textView.setText(R.string.user_agreement_text);
        textView.setTextColor(LineTheme.TEXT);
        textView.setTextSize(15);
        textView.setLineSpacing(0, 1.2f);
        textView.setPadding(0, 0, 0, padding);

        scrollView.addView(textView);
        container.addView(scrollView);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.user_agreement_title)
                .setView(container)
                .setPositiveButton(R.string.user_agreement_agree, (d, which) -> {
                    if (onAgree != null) {
                        onAgree.run();
                    }
                })
                .setNegativeButton(R.string.user_agreement_disagree, (d, which) -> {
                    if (onDisagree != null) {
                        onDisagree.run();
                    }
                })
                .setCancelable(false)
                .create();

        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }
}
