package cn.lineai.data.db.migration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import cn.lineai.data.db.LineCodeSchema;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

public final class MigrationsTest {
    @Test
    public void migrationListIsNotEmpty() {
        assertFalse(Migrations.all().isEmpty());
    }

    @Test
    public void migrationListTargetsAreStrictlyIncreasing() {
        List<DatabaseMigration> all = Migrations.all();
        int previous = 0;
        for (DatabaseMigration migration : all) {
            assertTrue("migration target versions must be strictly increasing, got "
                            + migration.getTargetVersion() + " after " + previous,
                    migration.getTargetVersion() > previous);
            previous = migration.getTargetVersion();
        }
        assertEquals(LineCodeSchema.VERSION, previous);
    }

    @Test
    public void migrationTargetsDoNotExceedSchemaVersion() {
        for (DatabaseMigration migration : Migrations.all()) {
            assertTrue("migration " + migration.getClass().getSimpleName()
                            + " targets " + migration.getTargetVersion()
                            + " which is beyond LineCodeSchema.VERSION="
                            + LineCodeSchema.VERSION,
                    migration.getTargetVersion() <= LineCodeSchema.VERSION);
        }
    }

    @Test
    public void migrationTargetsAreUnique() {
        HashSet<Integer> seen = new HashSet<>();
        for (DatabaseMigration migration : Migrations.all()) {
            assertTrue("duplicate migration target version: " + migration.getTargetVersion(),
                    seen.add(migration.getTargetVersion()));
        }
    }
}
