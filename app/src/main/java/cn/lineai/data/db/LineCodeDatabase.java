package cn.lineai.data.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import cn.lineai.data.db.migration.DatabaseMigration;
import cn.lineai.data.db.migration.Migrations;

public final class LineCodeDatabase extends SQLiteOpenHelper {
    private static final String TAG = "LineCodeDatabase";
    private static volatile LineCodeDatabase instance;

    public static LineCodeDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (LineCodeDatabase.class) {
                if (instance == null) {
                    instance = new LineCodeDatabase(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private LineCodeDatabase(Context context) {
        super(context, LineCodeSchema.DATABASE_NAME, null, LineCodeSchema.VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        executeAll(db, LineCodeSchema.CREATE_SQL);
        executeAll(db, LineCodeSchema.MIGRATIONS_SQL);
        for (String sql : LineCodeSchema.OPTIONAL_FTS_SQL) {
            try {
                db.execSQL(sql);
            } catch (RuntimeException e) {
                Log.w(TAG, "FTS schema unavailable, continuing without it: " + sql, e);
            }
        }
        writeSchemaVersionMetadata(db, LineCodeSchema.VERSION);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        executeAll(db, LineCodeSchema.CREATE_SQL);
        for (String sql : LineCodeSchema.OPTIONAL_FTS_SQL) {
            try {
                db.execSQL(sql);
            } catch (RuntimeException e) {
                Log.w(TAG, "FTS schema unavailable on open, continuing without it: " + sql, e);
            }
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == newVersion) {
            return;
        }
        applyMigrations(db, oldVersion, newVersion);
        writeSchemaVersionMetadata(db, newVersion);
    }

    private void applyMigrations(SQLiteDatabase db, int fromVersion, int toVersion) {
        executeAll(db, LineCodeSchema.MIGRATIONS_SQL);
        for (DatabaseMigration migration : Migrations.all()) {
            int target = migration.getTargetVersion();
            if (target > fromVersion && target <= toVersion) {
                migration.apply(db);
                ContentValues values = new ContentValues();
                values.put("version", target);
                values.put("applied_at", System.currentTimeMillis());
                db.insertWithOnConflict("schema_migrations", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
        }
    }

    private void writeSchemaVersionMetadata(SQLiteDatabase db, int version) {
        db.execSQL("INSERT OR REPLACE INTO metadata(key, value, updated_at) VALUES('schema_version', ?, ?)",
                new Object[] {String.valueOf(version), System.currentTimeMillis()});
    }

    private void executeAll(SQLiteDatabase db, String[] statements) {
        for (String sql : statements) {
            db.execSQL(sql);
        }
    }
}
