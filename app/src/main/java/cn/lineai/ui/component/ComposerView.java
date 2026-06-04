package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import cn.lineai.model.ChatMode;
import cn.lineai.model.ChatUiState;
import cn.lineai.ui.theme.LineTheme;

public final class ComposerView extends LinearLayout {
    public interface Listener {
        void onSend(String text);

        void onModeChanged(String mode);

        void onStop();
    }

    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextView modelText;
    private final TextView contextText;
    private final LinearLayout modeSelectorButton;
    private final TextView modeSelectorText;
    private final IconButtonView modeSelectorChevron;
    private final EditText input;
    private final IconButtonView sendButton;
    private PopupWindow modePopup;
    private boolean streaming;
    private String chatMode = ChatMode.DEFAULT;
    private Listener listener;

    public ComposerView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(LineTheme.BG);
        setWillNotDraw(false);
        LineTheme.padding(this, LineTheme.LG, LineTheme.SM, LineTheme.LG, LineTheme.LG);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(VERTICAL);
        panel.setMinimumHeight(LineTheme.dp(context, 148));
        panel.setBackground(LineTheme.roundedStroke(context, LineTheme.INPUT_BG, 22, LineTheme.BORDER));
        addView(panel, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout metaRow = new LinearLayout(context);
        metaRow.setOrientation(HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        LineTheme.padding(metaRow, LineTheme.LG, 0, LineTheme.LG, 0);
        panel.addView(metaRow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LineTheme.dp(context, 34)));

        modelText = LineTheme.textMedium(context, "", LineTheme.FONT_XS, LineTheme.TEXT_SECONDARY);
        modelText.setSingleLine(true);
        metaRow.addView(modelText, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        contextText = LineTheme.text(context, "", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.BOLD);
        LinearLayout.LayoutParams contextParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        contextParams.leftMargin = LineTheme.dp(context, LineTheme.MD);
        metaRow.addView(contextText, contextParams);

        android.view.View divider = new android.view.View(context);
        divider.setBackgroundColor(LineTheme.BORDER_LIGHT);
        panel.addView(divider, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1));

        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(HORIZONTAL);
        inputRow.setGravity(Gravity.TOP);
        LineTheme.padding(inputRow, LineTheme.SM, LineTheme.SM, LineTheme.SM, 0);
        panel.addView(inputRow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        IconButtonView attach = new IconButtonView(context, IconButtonView.PLUS);
        attach.setIconColor(LineTheme.TEXT_SECONDARY);
        attach.setIconSizeDp(40, 22);
        attach.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_LIGHT, 20));

        input = new EditText(context);
        input.setTextColor(LineTheme.TEXT);
        input.setHintTextColor(LineTheme.TEXT_TERTIARY);
        input.setHint("输入消息...");
        input.setTextSize(LineTheme.FONT_MD);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setMaxLines(6);
        input.setMinHeight(LineTheme.dp(context, 68));
        input.setMaxHeight(LineTheme.dp(context, 152));
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        input.setIncludeFontPadding(false);
        input.setPadding(LineTheme.dp(context, LineTheme.SM), LineTheme.dp(context, LineTheme.SM),
                LineTheme.dp(context, LineTheme.SM), LineTheme.dp(context, LineTheme.SM));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        inputRow.addView(input, inputParams);

        sendButton = new IconButtonView(context, IconButtonView.ARROW_UP);
        sendButton.setIconSizeDp(40, 22);
        sendButton.setOnClickListener(v -> {
            if (streaming) {
                if (listener != null) {
                    listener.onStop();
                }
                return;
            }
            String value = input.getText().toString();
            if (value.trim().isEmpty()) {
                return;
            }
            if (listener != null) {
                listener.onSend(value);
            }
            input.setText("");
        });

        LinearLayout modeRow = new LinearLayout(context);
        modeRow.setOrientation(HORIZONTAL);
        modeRow.setGravity(Gravity.CENTER_VERTICAL);
        LineTheme.padding(modeRow, LineTheme.SM, 0, LineTheme.SM, LineTheme.SM);
        panel.addView(modeRow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams attachParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 40), LineTheme.dp(context, 40));
        attachParams.rightMargin = LineTheme.dp(context, LineTheme.SM);
        modeRow.addView(attach, attachParams);

        modeSelectorButton = new LinearLayout(context);
        modeSelectorButton.setOrientation(HORIZONTAL);
        modeSelectorButton.setGravity(Gravity.CENTER_VERTICAL);
        modeSelectorButton.setClickable(true);
        modeSelectorButton.setFocusable(true);
        modeSelectorButton.setOnClickListener(v -> showModePopup(modeSelectorButton));
        LineTheme.padding(modeSelectorButton, LineTheme.SM, 0, LineTheme.XS, 0);
        modeSelectorText = LineTheme.textMedium(context, modeLabel(chatMode), LineTheme.FONT_XS, LineTheme.TEXT);
        modeSelectorText.setGravity(Gravity.CENTER_VERTICAL);
        modeSelectorText.setSingleLine(true);
        modeSelectorButton.addView(modeSelectorText, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        modeSelectorChevron = new IconButtonView(context, IconButtonView.CHEVRON_DOWN);
        modeSelectorChevron.setIconColor(LineTheme.TEXT_SECONDARY);
        modeSelectorChevron.setIconSizeDp(18, 13);
        modeSelectorChevron.setClickable(false);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 18), LineTheme.dp(context, 18));
        chevronParams.leftMargin = LineTheme.dp(context, 1);
        modeSelectorButton.addView(modeSelectorChevron, chevronParams);
        modeRow.addView(modeSelectorButton, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(context, 34)));

        View spacer = new View(context);
        modeRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 40), LineTheme.dp(context, 40));
        sendParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        modeRow.addView(sendButton, sendParams);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendButton();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        updateSendButton();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void render(ChatUiState state) {
        streaming = state.isStreaming();
        modelText.setText(state.getModelLabel());
        contextText.setText(state.getContextLabel());
        contextText.setTextColor(state.getContextPercent() >= 80 ? LineTheme.WARNING : LineTheme.TEXT_TERTIARY);
        chatMode = state.getChatMode();
        if (streaming && modePopup != null) {
            modePopup.dismiss();
        }
        input.setEnabled(!streaming);
        input.setHint(state.hasConfiguredModel() ? "输入消息..." : "请先到设置 → 模型管理配置模型");
        updateModeButtons();
        updateSendButton();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        borderPaint.setColor(LineTheme.BORDER);
        borderPaint.setStrokeWidth(1f);
        canvas.drawLine(0, 0, getWidth(), 0, borderPaint);
    }

    private void updateSendButton() {
        boolean hasText = input.getText().toString().trim().length() > 0;
        if (streaming) {
            sendButton.setIconType(IconButtonView.STOP);
            sendButton.setIconColor(LineTheme.TEXT_ON_COLOR);
            sendButton.setIconSizeDp(40, 18);
            sendButton.setBackground(LineTheme.rounded(getContext(), LineTheme.DANGER, 20));
        } else {
            sendButton.setIconType(IconButtonView.ARROW_UP);
            sendButton.setIconColor(hasText ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_TERTIARY);
            sendButton.setIconSizeDp(40, 22);
            sendButton.setBackground(LineTheme.rounded(getContext(), hasText ? LineTheme.ACCENT : LineTheme.SURFACE_LIGHT, 20));
        }
        sendButton.setEnabled(streaming || hasText);
        sendButton.setAlpha(sendButton.isEnabled() ? 1f : 0.72f);
    }

    private void updateModeButtons() {
        modeSelectorText.setText(modeLabel(chatMode));
        modeSelectorText.setTextColor(streaming ? LineTheme.TEXT_TERTIARY : LineTheme.TEXT);
        modeSelectorChevron.setIconColor(streaming ? LineTheme.TEXT_TERTIARY : LineTheme.TEXT_SECONDARY);
        modeSelectorButton.setBackground(LineTheme.rounded(getContext(), LineTheme.INPUT_BG, 17));
        modeSelectorButton.setEnabled(!streaming);
        modeSelectorButton.setAlpha(streaming ? 0.62f : 1f);
    }

    private void showModePopup(View anchor) {
        if (streaming) {
            return;
        }
        if (modePopup != null && modePopup.isShowing()) {
            modePopup.dismiss();
            return;
        }
        Context context = getContext();
        int popupWidth = LineTheme.dp(context, 112);
        int rowHeight = LineTheme.dp(context, 38);
        int popupHeight = rowHeight * 3 + LineTheme.dp(context, 6);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        content.setBackground(LineTheme.roundedStroke(context, LineTheme.INPUT_BG, 14, LineTheme.BORDER_LIGHT));
        LineTheme.padding(content, 3, 3, 3, 3);
        content.addView(modeOption(context, "Chat", ChatMode.CHAT), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
        content.addView(modeOption(context, "Plan", ChatMode.PLAN), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
        content.addView(modeOption(context, "Agent", ChatMode.AGENT), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
        modePopup = new PopupWindow(content, popupWidth, popupHeight, true);
        modePopup.setOutsideTouchable(true);
        modePopup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int centeredX = location[0] + (anchor.getWidth() - popupWidth) / 2;
        int popupX = Math.max(LineTheme.dp(context, LineTheme.SM),
                Math.min(centeredX, screenWidth - popupWidth - LineTheme.dp(context, LineTheme.SM)));
        modePopup.showAtLocation(this, Gravity.NO_GRAVITY, popupX, Math.max(0, location[1] - popupHeight - LineTheme.dp(context, 8)));
    }

    private TextView modeOption(Context context, String label, String mode) {
        boolean selected = mode.equals(chatMode);
        TextView item = LineTheme.textMedium(context, label, LineTheme.FONT_SM,
                selected ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setSingleLine(true);
        item.setPadding(LineTheme.dp(context, LineTheme.MD), 0, LineTheme.dp(context, LineTheme.MD), 0);
        item.setBackground(LineTheme.rounded(context, selected ? LineTheme.ACCENT : android.graphics.Color.TRANSPARENT, 11));
        item.setClickable(true);
        item.setOnClickListener(v -> {
            if (modePopup != null) {
                modePopup.dismiss();
            }
            if (!mode.equals(chatMode) && listener != null) {
                listener.onModeChanged(mode);
            }
        });
        return item;
    }

    private String modeLabel(String mode) {
        if (ChatMode.CHAT.equals(mode)) {
            return "Chat";
        }
        if (ChatMode.PLAN.equals(mode)) {
            return "Plan";
        }
        return "Agent";
    }
}
