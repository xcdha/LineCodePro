package cn.lineai.data.db.migration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import cn.lineai.data.db.LineCodeSchema;
import org.junit.Test;

public final class AddToolCallObservabilityColumnsTest {
    @Test
    public void targetsSchemaVersionTwo() {
        AddToolCallObservabilityColumns migration = new AddToolCallObservabilityColumns();
        assertEquals(2, migration.getTargetVersion());
    }

    @Test
    public void usesAddColumnsSqlFromSchema() {
        assertNotNull(LineCodeSchema.ADD_COLUMNS_SQL);
        assertTrue(LineCodeSchema.ADD_COLUMNS_SQL.length >= 2);
        boolean hasDuration = false;
        boolean hasErrorMessage = false;
        for (String sql : LineCodeSchema.ADD_COLUMNS_SQL) {
            if (sql.contains("ALTER TABLE tool_calls ADD COLUMN duration_ms")) {
                hasDuration = true;
            }
            if (sql.contains("ALTER TABLE tool_calls ADD COLUMN error_message")) {
                hasErrorMessage = true;
            }
        }
        assertTrue("ADD_COLUMNS_SQL must include duration_ms column", hasDuration);
        assertTrue("ADD_COLUMNS_SQL must include error_message column", hasErrorMessage);
    }

    @Test
    public void allAddColumnsAreTargetedAtToolCalls() {
        for (String sql : LineCodeSchema.ADD_COLUMNS_SQL) {
            assertTrue("expected ALTER TABLE tool_calls in: " + sql,
                    sql.contains("ALTER TABLE tool_calls"));
        }
    }
}
