package cn.lineai.ui.component;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.R;
import cn.lineai.log.ErrorLogEntry;
import cn.lineai.log.ErrorLogFileProvider;
import cn.lineai.log.ErrorLogRepository;
import cn.lineai.ui.theme.LineTheme;
import java.util.List;

public final class ErrorLogsScreenView extends ScreenScaffoldView {
    private final ErrorLogRepository repository;

    public ErrorLogsScreenView(Context context, Runnable onBack) {
        super(context, context.getString(R.string.screen_error_logs_title), onBack, clearButton(context));
        repository = new ErrorLogRepository(context);
        getRightAction().setOnClickListener(v -> {
            repository.clear();
            Toast.makeText(getContext(), R.string.screen_error_logs_cleared, Toast.LENGTH_SHORT).show();
            render();
        });
        render();
    }

    private void render() {
        LinearLayout content = getContent();
        content.removeAllViews();
        List<ErrorLogEntry> logs = repository.list();
        if (logs.isEmpty()) {
            TextView empty = LineTheme.text(getContext(), getContext().getString(R.string.screen_error_logs_empty), LineTheme.FONT_MD, LineTheme.TEXT_TERTIARY, android.graphics.Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            LineTheme.padding(empty, LineTheme.LG, LineTheme.XXL, LineTheme.LG, LineTheme.XXL);
            content.addView(empty, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            return;
        }
        SettingsSectionView section = new SettingsSectionView(getContext(), getContext().getString(R.string.screen_error_logs_section_title));
        for (int i = 0; i < logs.size(); i++) {
            ErrorLogEntry entry = logs.get(i);
            section.addRow(new ActionRowView(getContext(), IconButtonView.FILE_TEXT, entry.getTitle(), entry.getSubtitle(), false, true, () -> openLog(entry)), i < logs.size() - 1, 68);
        }
        content.addView(section, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void openLog(ErrorLogEntry entry) {
        try {
            Uri uri = ErrorLogFileProvider.uriFor(getContext(), entry.getFile());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "text/plain");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getContext().startActivity(Intent.createChooser(intent, getContext().getString(R.string.screen_error_logs_open_with)));
        } catch (Exception e) {
            Toast.makeText(getContext(), R.string.screen_error_logs_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private static IconButtonView clearButton(Context context) {
        IconButtonView button = new IconButtonView(context, IconButtonView.TRASH_2);
        button.setIconColor(LineTheme.DANGER);
        button.setIconSizeDp(36, 20);
        return button;
    }
}
