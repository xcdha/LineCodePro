package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.model.ChatMessage;
import cn.lineai.ui.theme.LineTheme;

public final class UserMessageView extends LinearLayout {
    private final TextView contentText;
    private String lastContent = "";

    public UserMessageView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.RIGHT);
        LineTheme.padding(this, LineTheme.LG, 0, LineTheme.LG, 6);

        contentText = LineTheme.text(context, "", 16, LineTheme.TEXT_ON_COLOR, Typeface.NORMAL);
        contentText.setLineSpacing(LineTheme.dp(context, 2), 1.0f);
        contentText.setBackground(LineTheme.userBubble(context));
        LineTheme.padding(contentText, LineTheme.MD, 5, LineTheme.MD, 5);
        int horizontalPaddingPx = LineTheme.dp(context, LineTheme.LG) * 2;
        int availableWidth = context.getResources().getDisplayMetrics().widthPixels - horizontalPaddingPx;
        contentText.setMaxWidth((int) (availableWidth * 0.80f));
        addView(contentText, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        MessageActionBarView actionBar = new MessageActionBarView(context, MessageActionBarView.ALIGN_RIGHT, true);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(context, 22));
        actionParams.topMargin = LineTheme.dp(context, 3);
        addView(actionBar, actionParams);
    }

    public void bind(ChatMessage message) {
        String content = message.getContent();
        if (!lastContent.equals(content)) {
            contentText.setText(content);
            lastContent = content;
        }
    }
}
