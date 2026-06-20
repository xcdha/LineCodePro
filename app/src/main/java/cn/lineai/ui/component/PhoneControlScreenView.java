package cn.lineai.ui.component;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.data.repository.PhoneControlRepository;
import cn.lineai.ui.theme.LineTheme;
import java.util.ArrayList;
import java.util.List;

public final class PhoneControlScreenView extends ScreenScaffoldView {

    public interface Listener {
        void onBack();

        void onOpenAccessibilitySettings();

        void onPermissionAction(String permissionId);
    }

    private final boolean imageModelSet;
    private final boolean accessibilityEnabled;
    private final boolean disclaimerAccepted;
    private final Listener listener;
    private final PhoneControlRepository phoneControlRepository;
    private final List<View> interactiveViews = new ArrayList<>();

    public PhoneControlScreenView(Context context, boolean imageModelSet, boolean accessibilityEnabled,
                                  boolean disclaimerAccepted, Listener listener) {
        super(context, context.getString(R.string.screen_phone_control_title), listener::onBack, null);
        this.imageModelSet = imageModelSet;
        this.accessibilityEnabled = accessibilityEnabled;
        this.disclaimerAccepted = disclaimerAccepted;
        this.listener = listener;
        this.phoneControlRepository = new PhoneControlRepository(context);

        LinearLayout content = getContent();

        if (!imageModelSet) {
            content.addView(buildImageModelWarning(context));
        }

        content.addView(buildAccessibilityRow(context));

        if (disclaimerAccepted && accessibilityEnabled) {
            content.addView(new SectionHeaderView(context, context.getString(R.string.screen_phone_control_permission_management)));
            content.addView(buildPermissionRow(context, R.string.screen_phone_control_permission_screenshot, "screenshot", IconButtonView.ZAP));
            content.addView(buildPermissionRow(context, R.string.screen_phone_control_permission_click, "click", IconButtonView.PLAY));
            content.addView(buildPermissionRow(context, R.string.screen_phone_control_permission_swipe, "swipe", IconButtonView.SLIDERS_HORIZONTAL));
            content.addView(buildPermissionRow(context, R.string.screen_phone_control_permission_long_press, "longPress", IconButtonView.CLOCK_3));
            content.addView(buildPermissionRow(context, R.string.screen_phone_control_permission_view_hierarchy, "viewHierarchy", IconButtonView.SQUARE_FUNCTION));
            content.addView(buildPermissionRow(context, R.string.screen_phone_control_permission_view_action, "viewAction", IconButtonView.WRENCH));
        }

        if (!imageModelSet) {
            setInteractiveEnabled(false);
        }
    }

    private View buildImageModelWarning(Context context) {
        LinearLayout warning = new LinearLayout(context);
        warning.setOrientation(VERTICAL);
        warning.setBackground(LineTheme.rounded(context, LineTheme.DANGER_MUTED, 12));
        LineTheme.padding(warning, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = LineTheme.dp(context, LineTheme.MD);
        warning.setLayoutParams(params);

        TextView title = LineTheme.text(context, context.getString(R.string.screen_phone_control_no_image_model),
                LineTheme.FONT_MD, LineTheme.DANGER, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        warning.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView desc = LineTheme.text(context, context.getString(R.string.screen_phone_control_image_model_required),
                LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        desc.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, LineTheme.XS);
        warning.addView(desc, descParams);

        return warning;
    }

    private View buildAccessibilityRow(Context context) {
        String label = context.getString(R.string.screen_phone_control_accessibility_label);
        String desc;
        if (!disclaimerAccepted) {
            desc = context.getString(R.string.screen_phone_control_disclaimer_title);
        } else if (!accessibilityEnabled) {
            desc = context.getString(R.string.screen_phone_control_accessibility_status_disabled);
        } else {
            desc = context.getString(R.string.screen_phone_control_accessibility_status_enabled);
        }
        ActionRowView row = new ActionRowView(context, IconButtonView.SHIELD_CHECK, label, desc, false, true,
                () -> onAccessibilityRowClicked(context));
        interactiveViews.add(row);
        return row;
    }

    private void onAccessibilityRowClicked(Context context) {
        if (!disclaimerAccepted) {
            showDisclaimerDialog(context);
        } else if (!accessibilityEnabled) {
            listener.onOpenAccessibilitySettings();
        }
    }

    private void showDisclaimerDialog(Context context) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.screen_phone_control_disclaimer_title)
                .setMessage(R.string.screen_phone_control_disclaimer_text)
                .setPositiveButton(R.string.screen_phone_control_disclaimer_agree, (dialog, which) -> {
                    phoneControlRepository.setDisclaimerAccepted(true);
                    listener.onOpenAccessibilitySettings();
                })
                .setNegativeButton(R.string.screen_phone_control_disclaimer_disagree, null)
                .setCancelable(true)
                .show();
    }

    private View buildPermissionRow(Context context, int labelRes, String permissionId, int iconType) {
        ActionRowView row = new ActionRowView(context, iconType, context.getString(labelRes), null, false, false,
                () -> listener.onPermissionAction(permissionId));
        interactiveViews.add(row);
        return row;
    }

    private void setInteractiveEnabled(boolean enabled) {
        for (View view : interactiveViews) {
            view.setEnabled(enabled);
            view.setClickable(enabled);
            view.setAlpha(enabled ? 1f : 0.45f);
        }
    }
}
