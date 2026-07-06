package cn.lineai.ui.component;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.R;
import cn.lineai.model.ExtensionItemUiModel;
import cn.lineai.model.ExtensionKindUiModel;
import cn.lineai.model.SkillRecord;
import cn.lineai.ui.MainChatView;
import cn.lineai.ui.theme.LineTheme;
import java.util.List;

public final class ExtensionDetailScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        void onAddAgent();

        void onEditAgent(String id);

        void onAddMcp();

        void onEditMcp(String id);

        void onCreateSkill(String location, String name, String description, String content);

        void onInstallSkill(String location, String sourcePath, String name);

        void onInstallSkillFromUri(String location, String uri, String displayName);

        void onEnabledChanged(String kind, String id, boolean enabled);

        void onDelete(String kind, String id);
    }

    private final String kind;
    private final Listener listener;
    private final ExtensionKindUiModel uiModel;

    public ExtensionDetailScreenView(Context context, ExtensionKindUiModel uiModel, Listener listener) {
        super(context, uiModel != null ? uiModel.getTitle() : context.getString(R.string.screen_extensions_section_linecode), listener::onBack, addButton(context, uiModel, listener));
        this.kind = uiModel != null ? uiModel.getKind() : "";
        this.listener = listener;
        this.uiModel = uiModel;
        getRightAction().setOnClickListener(v -> handleAdd());
        LinearLayout content = getContent();
        LineTheme.padding(content, 0, 0, 0, 100);

        String sectionTitle = uiModel != null ? uiModel.getSectionTitle() : context.getString(R.string.screen_extension_detail_section_install_other);
        int sectionIcon = uiModel != null ? uiModel.getIconType() : IconButtonView.PACKAGE;
        String sectionInlineTitle = uiModel != null ? uiModel.getInlineTitle() : context.getString(R.string.screen_extension_detail_inline_title_linecode);
        String sectionInlineDesc = uiModel != null ? uiModel.getInlineDesc() : context.getString(R.string.screen_extension_detail_inline_desc_linecode);

        SettingsSectionView add = new SettingsSectionView(context, sectionTitle);
        add.addRow(new ActionRowView(context, sectionIcon, sectionInlineTitle, sectionInlineDesc, false, true, this::handleAdd), false);
        content.addView(add, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView installed = new SettingsSectionView(context, context.getString(R.string.screen_extension_detail_section_installed));
        renderInstalled(installed);
        content.addView(installed, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void handleAdd() {
        if (uiModel != null) {
            switch (uiModel.getAddActionType()) {
                case ExtensionKindUiModel.ADD_ACTION_AGENT:
                    listener.onAddAgent();
                    return;
                case ExtensionKindUiModel.ADD_ACTION_MCP:
                    listener.onAddMcp();
                    return;
                case ExtensionKindUiModel.ADD_ACTION_SKILL:
                    showSkillActions();
                    return;
                default:
                    break;
            }
        }
        Toast.makeText(getContext(), getContext().getString(R.string.screen_extension_detail_empty_linecode), Toast.LENGTH_SHORT).show();
    }

    private void renderInstalled(SettingsSectionView installed) {
        if (uiModel == null) {
            installed.addRow(empty(getContext().getString(R.string.screen_extension_detail_empty_linecode)), false);
            return;
        }
        List<ExtensionItemUiModel> items = uiModel.getInstalledItems();
        if (items == null || items.isEmpty()) {
            installed