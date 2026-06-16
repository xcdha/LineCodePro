package cn.lineai.data.db.migration;

import android.database.sqlite.SQLiteDatabase;
import cn.lineai.data.db.LineCodeSchema;

public final class AddToolCallObservabilityColumns extends DatabaseMigration {
    @Override
    public int getTargetVersion() {
        return 2;
    }

    @Override
    public void apply(SQLiteDatabase db) {
        for (String sql : LineCodeSchema.ADD_COLUMNS_SQL) {
            db.execSQL(sql);
        }
    }
}
