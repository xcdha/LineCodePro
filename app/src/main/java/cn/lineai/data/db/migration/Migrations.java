package cn.lineai.data.db.migration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class Migrations {
    private static final List<DatabaseMigration> ALL = Collections.unmodifiableList(Arrays.asList(
            new AddToolCallObservabilityColumns()
    ));

    private Migrations() {
    }

    public static List<DatabaseMigration> all() {
        return ALL;
    }
}
