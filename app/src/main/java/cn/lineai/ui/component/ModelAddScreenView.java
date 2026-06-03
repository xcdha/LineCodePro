package cn.lineai.ui.component;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.ai.ModelCompletionException;
import cn.lineai.ai.protocol.ModelCatalogClient;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.model.ModelProviderPreset;
import cn.lineai.ui.theme.LineTheme;
import cn.lineai.ui.util.KeyboardController;
import java.util.ArrayList;
import java.util.List;

public final class ModelAddScreenView extends LinearLayout {
    public interface Listener {
        void onBack();

        void onSave(ModelConfig model);
    }

    private static final String[] PROVIDER_LABELS = new String[] {"OpenAI", "Codex", "Anthropic", "本地"};

    private final TextView saveAction;
    private final TextView providerLabelView;
    private final EditText nameInput;
    private final ModelProviderPreset preset;
    private final ModelConfig editingModel;
    private final ModelCatalogClient catalogClient = new ModelCatalogClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<String> fetchedModelIds = new ArrayList<>();
    private LinearLayout queryButton;
    private TextView queryLabel;
    private TextView queryText;
    private IconButtonView queryIcon;
    private TextView baseUrlHintView;
    private LinearLayout modelInputHost;
    private Switch customIdSwitch;
    private EditText baseUrlInput;
    private EditText apiKeyInput;
    private EditText modelIdInput;
    private EditText toolCallLimitInput;
    private final String[] selectedModelId = new String[] {""};
    private final boolean local;
    private final boolean lockedPreset;
    private final String providerLabel;
    private final ModelProtocolType[] protocolType = new ModelProtocolType[1];
    private boolean saveEnabled;
    private boolean fetchingModels;

    public ModelAddScreenView(Context context, ModelProviderPreset preset, boolean local, Listener listener) {
        this(context, preset, local, null, listener);
    }

    public ModelAddScreenView(Context context, ModelProviderPreset preset, boolean local, ModelConfig editingModel, Listener listener) {
        super(context);
        this.editingModel = editingModel;
        boolean editing = editingModel != null;
        this.local = local || (editing && editingModel.getProtocolType() == ModelProtocolType.LOCAL_GGUF);
        this.preset = preset;
        this.lockedPreset = preset != null || editing;
        this.protocolType[0] = editing ? editingModel.getProtocolType() : this.local ? ModelProtocolType.LOCAL_GGUF : preset == null ? ModelProtocolType.OPENAI_COMPATIBLE : preset.getProtocolType();
        this.providerLabel = this.local ? "本地" : editing ? cleanProviderLabel(editingModel.getProviderLabel()) : preset == null ? null : preset.getLabel();
        setOrientation(VERTICAL);
        setBackgroundColor(LineTheme.BG);

        saveAction = LineTheme.textMedium(context, "保存", LineTheme.FONT_MD, LineTheme.TEXT_TERTIARY);
        saveAction.setGravity(Gravity.CENTER);
        LineTheme.padding(saveAction, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        addView(new ScreenHeaderView(context, editing ? "修改模型" : "添加模型", listener::onBack, saveAction), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        LineTheme.padding(content, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        scrollView.addView(content, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        providerLabelView = label(context, providerTitle());
        content.addView(providerLabelView, labelParams(context, LineTheme.LG, LineTheme.SM));
        LinearLayout providerRow = new LinearLayout(context);
        providerRow.setOrientation(HORIZONTAL);
        for (int i = 0; i < PROVIDER_LABELS.length; i++) {
            final int index = i;
            boolean enabled = !lockedPreset || isActiveProviderIndex(index);
            addToggle(providerRow, PROVIDER_LABELS[i], isActiveProviderIndex(index), enabled, () -> {
                if (index == 3) {
                    Toast.makeText(context, "请从“加载本地模型”进入本地模型表单。", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (this.local) {
                    Toast.makeText(context, "请返回后进入自定义模型表单。", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!lockedPreset) {
                    protocolType[0] = protocolForIndex(index);
                    fetchedModelIds.clear();
                    selectedModelId[0] = "";
                    if (modelIdInput != null) {
                        modelIdInput.setText("");
                    }
                    updateProviderToggles(providerRow);
                    updateBaseUrlHint();
                    renderModelIdInput(customIdSwitch != null && customIdSwitch.isChecked());
                    updateQueryState();
                    updateSaveState();
                }
            });
        }
        content.addView(providerRow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        content.addView(label(context, "名称"), labelParams(context, LineTheme.LG, LineTheme.SM));
        nameInput = input(context, editing ? editingModel.getName() : "", this.local ? "如 Qwen2.5 7B 本地" : preset == null ? "如 GPT-4o、Claude Sonnet" : "可留空，默认使用模型 ID", false, false);
        content.addView(nameInput, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (this.local) {
            addLocalUi(context, content);
            baseUrlInput = null;
            apiKeyInput = null;
            customIdSwitch = null;
            queryButton = null;
            queryLabel = null;
            queryText = null;
            queryIcon = null;
            baseUrlHintView = null;
            modelInputHost = null;
            modelIdInput = null;
        } else {
            content.addView(label(context, "Base URL"), labelParams(context, LineTheme.LG, LineTheme.SM));
            baseUrlInput = input(context, editing ? editingModel.getBaseUrl() : preset == null ? "" : preset.getBaseUrl(), preset == null ? "https://api.example.com/v1" : preset.getPlaceholder(), false, false);
            content.addView(baseUrlInput, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            baseUrlHintView = LineTheme.text(context, hintFor(preset), LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            baseUrlHintView.setLineSpacing(LineTheme.dp(context, 3), 1f);
            LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            hintParams.topMargin = LineTheme.dp(context, LineTheme.SM);
            content.addView(baseUrlHintView, hintParams);

            content.addView(label(context, "API Key"), labelParams(context, LineTheme.LG, LineTheme.SM));
            apiKeyInput = input(context, editing ? editingModel.getApiKey() : "", "sk-...", false, true);
            content.addView(apiKeyInput, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            LinearLayout modelIdHeader = new LinearLayout(context);
            modelIdHeader.setOrientation(HORIZONTAL);
            modelIdHeader.setGravity(Gravity.CENTER_VERTICAL);
            TextView modelIdLabel = label(context, "模型 ID");
            modelIdHeader.addView(modelIdLabel, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout switchWrap = new LinearLayout(context);
            switchWrap.setOrientation(HORIZONTAL);
            switchWrap.setGravity(Gravity.CENTER_VERTICAL);
            TextView customText = LineTheme.textMedium(context, "自定义", LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY);
            switchWrap.addView(customText, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            customIdSwitch = new Switch(context);
            tintSwitch(customIdSwitch);
            LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            switchParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
            switchWrap.addView(customIdSwitch, switchParams);
            modelIdHeader.addView(switchWrap, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            content.addView(modelIdHeader, labelParams(context, LineTheme.LG, LineTheme.SM));

            modelInputHost = new LinearLayout(context);
            modelInputHost.setOrientation(VERTICAL);
            content.addView(modelInputHost, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            modelIdInput = input(context, editing ? editingModel.getModelId() : "", "输入模型 ID", false, false);
            queryLabel = LineTheme.text(context, "请先查询并选择模型", LineTheme.FONT_MD, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            queryLabel.setSingleLine(true);
            queryLabel.setEllipsize(TextUtils.TruncateAt.END);
            queryButton = createQueryButton(context);
            selectedModelId[0] = editing ? editingModel.getModelId() : "";
            customIdSwitch.setChecked(editing);
            renderModelIdInput(editing);

            customIdSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                renderModelIdInput(isChecked);
                updateSaveState();
            });
            queryButton.setOnClickListener(v -> {
                if (!canQuery() || fetchingModels) {
                    return;
                }
                if (!fetchedModelIds.isEmpty()) {
                    showModelPicker(fetchedModelIds);
                    return;
                }
                fetchModelCatalog();
            });

            content.addView(label(context, "工具调用次数限制"), labelParams(context, LineTheme.LG, LineTheme.SM));
            toolCallLimitInput = input(
                    context,
                    String.valueOf(editing ? editingModel.getToolCallLimit() : ModelConfig.DEFAULT_TOOL_CALL_LIMIT),
                    "200",
                    false,
                    false
            );
            toolCallLimitInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
            content.addView(toolCallLimitInput, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            TextView toolLimitHint = LineTheme.text(context, "-1 表示不限制；0 表示禁止工具调用；默认 200。", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            toolLimitHint.setLineSpacing(LineTheme.dp(context, 3), 1f);
            LinearLayout.LayoutParams toolLimitHintParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            toolLimitHintParams.topMargin = LineTheme.dp(context, LineTheme.SM);
            content.addView(toolLimitHint, toolLimitHintParams);
        }

        addLearningCard(context, content);

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSaveState();
                updateQueryState();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        nameInput.addTextChangedListener(watcher);
        if (!this.local) {
            baseUrlInput.addTextChangedListener(watcher);
            apiKeyInput.addTextChangedListener(watcher);
            modelIdInput.addTextChangedListener(watcher);
            toolCallLimitInput.addTextChangedListener(watcher);
            TextWatcher catalogWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    fetchedModelIds.clear();
                    if (customIdSwitch != null && !customIdSwitch.isChecked()) {
                        selectedModelId[0] = "";
                        renderModelIdInput(false);
                    }
                    updateSaveState();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            };
            baseUrlInput.addTextChangedListener(catalogWatcher);
            apiKeyInput.addTextChangedListener(catalogWatcher);
        }

        saveAction.setOnClickListener(v -> {
            if (!saveEnabled) {
                return;
            }
            ModelConfig model = buildModelConfig(context);
            if (model != null) {
                listener.onSave(model);
            }
        });
        updateBaseUrlHint();
        updateQueryState();
        updateSaveState();
    }

    private void addLocalUi(Context context, LinearLayout content) {
        content.addView(label(context, "模型文件"), labelParams(context, LineTheme.LG, LineTheme.SM));
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(LineTheme.dp(context, 74));
        card.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_LIGHT, 12, LineTheme.BORDER_LIGHT));
        LineTheme.padding(card, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);
        FrameLayout iconWrap = new FrameLayout(context);
        iconWrap.setBackground(LineTheme.rounded(context, LineTheme.ACCENT_MUTED, 8));
        IconButtonView fileIcon = new IconButtonView(context, IconButtonView.FILE_UP);
        fileIcon.setIconColor(LineTheme.ACCENT);
        fileIcon.setIconSizeDp(38, 20);
        fileIcon.setClickable(false);
        iconWrap.addView(fileIcon, new FrameLayout.LayoutParams(LineTheme.dp(context, 38), LineTheme.dp(context, 38), Gravity.CENTER));
        card.addView(iconWrap, new LinearLayout.LayoutParams(LineTheme.dp(context, 38), LineTheme.dp(context, 38)));

        LinearLayout fileText = new LinearLayout(context);
        fileText.setOrientation(VERTICAL);
        LinearLayout.LayoutParams fileTextParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        fileTextParams.leftMargin = LineTheme.dp(context, LineTheme.MD);
        fileTextParams.rightMargin = LineTheme.dp(context, LineTheme.MD);
        card.addView(fileText, fileTextParams);
        fileText.addView(LineTheme.text(context, "选择 GGUF 模型文件", LineTheme.FONT_MD, LineTheme.TEXT_TERTIARY, Typeface.BOLD));
        TextView desc = LineTheme.text(context, "通过 SAF 选择文件，应用会导入本地副本", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, 3);
        fileText.addView(desc, descParams);
        IconButtonView down = new IconButtonView(context, IconButtonView.CHEVRON_DOWN);
        down.setIconColor(LineTheme.TEXT_TERTIARY);
        down.setIconSizeDp(16, 14);
        down.setClickable(false);
        card.addView(down, new LinearLayout.LayoutParams(LineTheme.dp(context, 16), LineTheme.dp(context, 16)));
        card.setOnClickListener(v -> Toast.makeText(context, "本地文件选择器稍后接入。", Toast.LENGTH_SHORT).show());
        content.addView(card, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        content.addView(label(context, "上下文长度"), labelParams(context, LineTheme.LG, LineTheme.SM));
        EditText ctx = input(context, "4096", "4096", false, false);
        ctx.setInputType(InputType.TYPE_CLASS_NUMBER);
        content.addView(ctx, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView hint = LineTheme.text(context, "手机端建议从 4096 开始；保存后模型 ID 会自动带上上下文标记。", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        content.addView(hint, hintParams);

        content.addView(label(context, "加速"), labelParams(context, LineTheme.LG, LineTheme.SM));
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        addToggle(row, "自动", true, true, null);
        addToggle(row, "CPU", false, true, null);
        addToggle(row, "NPU", false, true, null);
        content.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView accelHint = LineTheme.text(context, "自动模式会优先尝试 Android Hexagon NPU，设备或模型不支持时回退 CPU。", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        accelHint.setLineSpacing(LineTheme.dp(context, 3), 1f);
        LinearLayout.LayoutParams accelHintParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        accelHintParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        content.addView(accelHint, accelHintParams);
    }

    private void renderModelIdInput(boolean custom) {
        KeyboardController.clearFocusAndHide(modelInputHost);
        modelInputHost.removeAllViews();
        if (custom) {
            detachFromParent(modelIdInput);
            modelInputHost.addView(modelIdInput, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            return;
        }
        detachFromParent(queryLabel);
        detachFromParent(queryButton);
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout selector = new LinearLayout(getContext());
        selector.setOrientation(HORIZONTAL);
        selector.setGravity(Gravity.CENTER_VERTICAL);
        selector.setBackground(LineTheme.roundedStroke(getContext(), LineTheme.SURFACE_LIGHT, 12, LineTheme.BORDER_LIGHT));
        LineTheme.padding(selector, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);
        boolean hasSelected = selectedModelId[0].length() > 0;
        queryLabel.setText(hasSelected ? selectedModelId[0] : "请先查询并选择模型");
        queryLabel.setTextColor(hasSelected ? LineTheme.TEXT : LineTheme.TEXT_TERTIARY);
        selector.addView(queryLabel, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        IconButtonView down = new IconButtonView(getContext(), IconButtonView.CHEVRON_DOWN);
        down.setIconColor(LineTheme.TEXT_TERTIARY);
        down.setIconSizeDp(16, 14);
        down.setClickable(false);
        selector.addView(down, new LinearLayout.LayoutParams(LineTheme.dp(getContext(), 16), LineTheme.dp(getContext(), 16)));
        selector.setOnClickListener(v -> queryButton.performClick());
        row.addView(selector, new LinearLayout.LayoutParams(0, LineTheme.dp(getContext(), 48), 1f));
        LinearLayout.LayoutParams queryParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(getContext(), 48));
        queryParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        row.addView(queryButton, queryParams);
        modelInputHost.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void detachFromParent(View view) {
        if (view == null || view.getParent() == null) {
            return;
        }
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    private LinearLayout createQueryButton(Context context) {
        LinearLayout button = new LinearLayout(context);
        button.setOrientation(HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setMinimumWidth(LineTheme.dp(context, 76));
        LineTheme.padding(button, LineTheme.LG, 0, LineTheme.LG, 0);

        queryIcon = new IconButtonView(context, IconButtonView.SEARCH);
        queryIcon.setClickable(false);
        queryIcon.setIconSizeDp(16, 16);
        button.addView(queryIcon, new LinearLayout.LayoutParams(LineTheme.dp(context, 16), LineTheme.dp(context, 16)));

        queryText = LineTheme.text(context, "查询", LineTheme.FONT_MD, LineTheme.TEXT_TERTIARY, Typeface.BOLD);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        textParams.leftMargin = LineTheme.dp(context, LineTheme.XS);
        button.addView(queryText, textParams);
        return button;
    }

    private void fetchModelCatalog() {
        fetchingModels = true;
        updateQueryState();
        String baseUrl = effectiveBaseUrl();
        String apiKey = value(apiKeyInput);
        ModelProtocolType type = protocolType[0];
        new Thread(() -> {
            try {
                List<String> ids = catalogClient.fetch(type, baseUrl, apiKey);
                mainHandler.post(() -> {
                    fetchingModels = false;
                    fetchedModelIds.clear();
                    fetchedModelIds.addAll(ids);
                    updateQueryState();
                    if (ids.isEmpty()) {
                        Toast.makeText(getContext(), "未获取到模型列表，请检查 Base URL 和 API Key。", Toast.LENGTH_LONG).show();
                        return;
                    }
                    showModelPicker(ids);
                });
            } catch (ModelCompletionException e) {
                mainHandler.post(() -> {
                    fetchingModels = false;
                    updateQueryState();
                    Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }, "linecode-model-catalog").start();
    }

    private void showModelPicker(List<String> ids) {
        Context context = getContext();
        KeyboardController.clearFocusAndHide(this);
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(VERTICAL);
        panel.setBackground(LineTheme.roundedTop(context, LineTheme.SURFACE_ELEVATED, 16));

        View handle = new View(context);
        handle.setBackground(LineTheme.rounded(context, LineTheme.TEXT_TERTIARY, 2));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 36), LineTheme.dp(context, 4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        handleParams.bottomMargin = LineTheme.dp(context, LineTheme.XS);
        panel.addView(handle, handleParams);

        TextView title = LineTheme.text(context, "选择模型", LineTheme.FONT_LG, LineTheme.TEXT, Typeface.BOLD);
        LineTheme.padding(title, LineTheme.LG, 0, LineTheme.LG, LineTheme.MD);
        panel.addView(title, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        View divider = new View(context);
        divider.setBackgroundColor(LineTheme.BORDER_LIGHT);
        panel.addView(divider, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1));

        ScrollView scroll = new ScrollView(context);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        int maxHeight = LineTheme.dp(context, 420);
        panel.addView(scroll, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, maxHeight));

        for (String id : ids) {
            addPickerRow(list, dialog, id, false);
        }
        addPickerRow(list, dialog, "自定义 ID...", true);
        panel.addView(new View(context), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LineTheme.dp(context, 34)));

        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }
    }

    private void addPickerRow(LinearLayout list, Dialog dialog, String label, boolean custom) {
        Context context = list.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        LineTheme.padding(row, LineTheme.LG, 14, LineTheme.LG, 14);
        TextView text = LineTheme.text(context, label, LineTheme.FONT_MD, custom ? LineTheme.ACCENT : LineTheme.TEXT, Typeface.NORMAL);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(text, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        if (!custom && label.equals(selectedModelId[0])) {
            IconButtonView check = new IconButtonView(context, IconButtonView.CHECK);
            check.setIconColor(LineTheme.ACCENT);
            check.setIconSizeDp(18, 16);
            check.setClickable(false);
            row.addView(check, new LinearLayout.LayoutParams(LineTheme.dp(context, 18), LineTheme.dp(context, 18)));
        }

        row.setOnClickListener(v -> {
            dialog.dismiss();
            if (custom) {
                selectedModelId[0] = "";
                customIdSwitch.setChecked(true);
                modelIdInput.setText("");
                renderModelIdInput(true);
            } else {
                selectedModelId[0] = label;
                if (nameInput.getText().toString().trim().length() == 0 && preset != null) {
                    nameInput.setText(label);
                }
                customIdSwitch.setChecked(false);
                renderModelIdInput(false);
            }
            updateSaveState();
        });
        list.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private ModelConfig buildModelConfig(Context context) {
        if (local) {
            Toast.makeText(context, "请先选择 GGUF 文件。本地推理稍后接入。", Toast.LENGTH_SHORT).show();
            return null;
        }
        String baseUrl = effectiveBaseUrl();
        String apiKey = value(apiKeyInput);
        String modelId = customIdSwitch.isChecked() ? value(modelIdInput) : selectedModelId[0];
        Integer toolCallLimit = parseToolCallLimit();
        String name = value(nameInput);
        if (name.length() == 0) {
            name = modelId;
        }
        if (modelId.length() == 0 || name.length() == 0) {
            Toast.makeText(context, "请填写名称和模型 ID", Toast.LENGTH_SHORT).show();
            return null;
        }
        if (apiKey.length() == 0) {
            Toast.makeText(context, "请填写 API Key", Toast.LENGTH_SHORT).show();
            return null;
        }
        if (toolCallLimit == null) {
            Toast.makeText(context, "工具调用次数限制只能是 -1 或大于等于 0 的整数", Toast.LENGTH_SHORT).show();
            return null;
        }
        String label = providerLabel != null ? providerLabel : protocolType[0].getLabel();
        return new ModelConfig(editingModel == null ? "" : editingModel.getId(), name, protocolType[0], label, baseUrl, apiKey, modelId, toolCallLimit);
    }

    private void updateProviderToggles(LinearLayout providerRow) {
        for (int i = 0; i < providerRow.getChildCount(); i++) {
            TextView button = (TextView) providerRow.getChildAt(i);
            boolean active = isActiveProviderIndex(i);
            button.setTextColor(active ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY);
            button.setBackground(LineTheme.rounded(getContext(), active ? LineTheme.ACCENT : LineTheme.SURFACE_LIGHT, 12));
            button.setAlpha(lockedPreset && !active ? 0.45f : 1f);
        }
    }

    private void updateBaseUrlHint() {
        if (local || baseUrlHintView == null) {
            return;
        }
        baseUrlHintView.setText(hintFor(lockedPreset ? preset : null));
        if (!lockedPreset && baseUrlInput != null) {
            baseUrlInput.setHint(placeholderFor(protocolType[0]));
        }
        if (providerLabelView != null) {
            providerLabelView.setText(providerTitle());
        }
    }

    private void updateQueryState() {
        if (local || queryButton == null) {
            return;
        }
        boolean canQuery = canQuery() && !fetchingModels;
        if (queryText != null) {
            queryText.setText(fetchingModels ? "查询中" : "查询");
            queryText.setTextColor((canQuery || fetchingModels) ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_TERTIARY);
        }
        if (queryIcon != null) {
            queryIcon.setIconColor((canQuery || fetchingModels) ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_TERTIARY);
        }
        queryButton.setBackground(LineTheme.rounded(getContext(), (canQuery || fetchingModels) ? LineTheme.ACCENT : LineTheme.SURFACE_LIGHT, 12));
        queryButton.setEnabled(canQuery);
    }

    private void updateSaveState() {
        boolean canSave;
        if (local) {
            canSave = false;
        } else {
            String id = customIdSwitch.isChecked() ? value(modelIdInput) : selectedModelId[0];
            String name = value(nameInput);
            canSave = (name.length() > 0 || id.length() > 0)
                    && id.length() > 0
                    && value(apiKeyInput).length() > 0
                    && parseToolCallLimit() != null;
        }
        saveEnabled = canSave;
        saveAction.setTextColor(canSave ? LineTheme.ACCENT : LineTheme.TEXT_TERTIARY);
        saveAction.setAlpha(canSave ? 1f : 0.45f);
    }

    private void addLearningCard(Context context, LinearLayout content) {
        LinearLayout learning = new LinearLayout(context);
        learning.setOrientation(VERTICAL);
        learning.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_LIGHT, 12, LineTheme.BORDER_LIGHT));
        LineTheme.padding(learning, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);
        learning.addView(LineTheme.text(context, "学习模式已移至 AI 行为", LineTheme.FONT_MD, LineTheme.TEXT, Typeface.BOLD));
        TextView desc = LineTheme.text(context, "请在设置 → AI 行为中统一开启或关闭学习模式，避免每个模型重复配置。", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        desc.setLineSpacing(LineTheme.dp(context, 3), 1f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, LineTheme.XS);
        learning.addView(desc, descParams);
        LinearLayout.LayoutParams learningParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        learningParams.topMargin = LineTheme.dp(context, LineTheme.XL);
        content.addView(learning, learningParams);
    }

    private void addToggle(LinearLayout row, String label, boolean active, boolean enabled, Runnable onClick) {
        Context context = row.getContext();
        TextView button = LineTheme.text(context, label, LineTheme.FONT_MD, active ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(LineTheme.rounded(context, active ? LineTheme.ACCENT : LineTheme.SURFACE_LIGHT, 12));
        button.setAlpha(enabled || active ? 1f : 0.45f);
        if (enabled && onClick != null) {
            button.setOnClickListener(v -> onClick.run());
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LineTheme.dp(context, 46), 1f);
        if (row.getChildCount() > 0) {
            params.leftMargin = LineTheme.dp(context, LineTheme.SM);
        }
        row.addView(button, params);
    }

    private boolean isActiveProviderIndex(int index) {
        if (index == 3) {
            return local;
        }
        return !local && protocolForIndex(index) == protocolType[0];
    }

    private ModelProtocolType protocolForIndex(int index) {
        if (index == 1) {
            return ModelProtocolType.CODEX_RESPONSES;
        }
        if (index == 2) {
            return ModelProtocolType.ANTHROPIC_MESSAGES;
        }
        return ModelProtocolType.OPENAI_COMPATIBLE;
    }

    private boolean canQuery() {
        return !local && effectiveBaseUrl().length() > 0 && value(apiKeyInput).length() > 0;
    }

    private String effectiveBaseUrl() {
        String baseUrl = value(baseUrlInput);
        if (baseUrl.length() > 0) {
            return baseUrl;
        }
        if (preset != null && preset.getBaseUrl() != null && preset.getBaseUrl().length() > 0) {
            return preset.getBaseUrl();
        }
        return defaultBaseUrlFor(protocolType[0]);
    }

    private String defaultBaseUrlFor(ModelProtocolType type) {
        if (type == ModelProtocolType.ANTHROPIC_MESSAGES) {
            return "https://api.anthropic.com";
        }
        return "https://api.openai.com/v1";
    }

    private String placeholderFor(ModelProtocolType type) {
        if (type == ModelProtocolType.ANTHROPIC_MESSAGES) {
            return "https://api.example.com/anthropic";
        }
        return "https://api.example.com/v1";
    }

    private String hintFor(ModelProviderPreset preset) {
        if (preset != null) {
            return preset.getHint();
        }
        if (protocolType[0] == ModelProtocolType.CODEX_RESPONSES) {
            return "Codex 使用 Responses API，也必须填到 /v1 结尾，例如 https://api.example.com/v1；不要加 /responses。";
        }
        if (protocolType[0] == ModelProtocolType.ANTHROPIC_MESSAGES) {
            return "Anthropic 协议必须填到 /anthropic 结尾，例如 https://api.example.com/anthropic；不要加 /v1/messages。";
        }
        return "OpenAI 兼容协议必须填到 /v1 结尾，例如 https://api.example.com/v1；不要只填域名，也不要加 /chat/completions。";
    }

    private String providerTitle() {
        if (providerLabel != null && providerLabel.length() > 0) {
            return "提供商：" + providerLabel;
        }
        if (lockedPreset) {
            return "提供商：" + protocolType[0].getLabel();
        }
        return "提供商";
    }

    private String cleanProviderLabel(String label) {
        if (label == null || label.length() == 0 || "自定义".equals(label)) {
            return null;
        }
        return label;
    }

    private TextView label(Context context, String text) {
        return LineTheme.textMedium(context, text, LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY);
    }

    private LinearLayout.LayoutParams labelParams(Context context, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(context, top);
        params.bottomMargin = LineTheme.dp(context, bottom);
        return params;
    }

    private EditText input(Context context, String value, String placeholder, boolean multiline, boolean secure) {
        EditText input = new EditText(context);
        input.setText(value == null ? "" : value);
        input.setHint(placeholder);
        input.setHintTextColor(LineTheme.TEXT_TERTIARY);
        input.setTextColor(LineTheme.TEXT);
        input.setTextSize(LineTheme.FONT_MD);
        input.setSingleLine(!multiline);
        input.setMinHeight(LineTheme.dp(context, multiline ? 120 : 48));
        input.setIncludeFontPadding(false);
        input.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_LIGHT, 12, LineTheme.BORDER_LIGHT));
        input.setPadding(LineTheme.dp(context, LineTheme.LG), LineTheme.dp(context, LineTheme.MD), LineTheme.dp(context, LineTheme.LG), LineTheme.dp(context, LineTheme.MD));
        input.setInputType(secure
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : multiline ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        return input;
    }

    private void tintSwitch(Switch toggle) {
        int[][] states = new int[][] {
                new int[] {android.R.attr.state_checked},
                new int[] {-android.R.attr.state_checked}
        };
        toggle.setThumbTintList(new ColorStateList(states, new int[] {LineTheme.ACCENT, LineTheme.TEXT_TERTIARY}));
        toggle.setTrackTintList(new ColorStateList(states, new int[] {LineTheme.ACCENT_DIM, LineTheme.SURFACE_LIGHT}));
    }

    private String value(EditText input) {
        return input == null || input.getText() == null ? "" : input.getText().toString().trim();
    }

    private Integer parseToolCallLimit() {
        if (local || toolCallLimitInput == null) {
            return ModelConfig.DEFAULT_TOOL_CALL_LIMIT;
        }
        String value = value(toolCallLimitInput);
        if (value.length() == 0) {
            return null;
        }
        try {
            int limit = Integer.parseInt(value);
            if (limit < ModelConfig.UNLIMITED_TOOL_CALLS) {
                return null;
            }
            return ModelConfig.normalizeToolCallLimit(limit);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        KeyboardController.clearFocusAndHide(this);
        super.onDetachedFromWindow();
    }
}
