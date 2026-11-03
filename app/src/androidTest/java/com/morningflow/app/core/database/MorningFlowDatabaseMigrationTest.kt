package com.morningflow.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test.db"

/**
 * Runs the database migrations against a real SQLite file, which the Robolectric
 * unit tests cannot do - they always start at the current schema, so nothing there would
 * notice a migration that drops a column or leaves a table behind.
 *
 * [MigrationTestHelper] creates the database from the exported `1.json`, runs the
 * migration, and then validates the result against `2.json`. That schema check is the
 * part worth having: a migration that produces almost-but-not-quite the current schema
 * fails here rather than on someone's phone.
 */
@RunWith(AndroidJUnit4::class)
class MorningFlowDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MorningFlowDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    /**
     * Version 1 kept two fallback flags; version 2 replaced them with a single
     * `isDefault`. A template the user had marked as their own fallback has to come
     * through as the default, and its modules have to come with it.
     */
    @Test
    fun migrate1To2_keepsAUserFallbackTemplateAsTheDefault() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO templates(
                    id, name, applicableDays, applicableStart, applicableEnd,
                    priority, isSystemFallback, isUserFallback, createdAt, updatedAt
                ) VALUES (
                    'user-fallback', 'My Morning', 'MONDAY', '06:00', '09:00',
                    5, 0, 1, 1000, 2000
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO modules(
                    id, templateId, type, enabled, behaviours,
                    squatTarget, stepTarget, position
                ) VALUES (
                    'module-1', 'user-fallback', 'LEAVE_BED', 1, '', 0, 0, 0
                )
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            MorningFlowDatabase.MIGRATION_1_2,
        )

        db.query("SELECT id, name, isDefault FROM templates").use { cursor ->
            assertTrue("the template did not survive the migration", cursor.moveToFirst())
            assertEquals("user-fallback", cursor.getString(0))
            assertEquals("My Morning", cursor.getString(1))
            assertEquals("a user fallback should become the default", 1, cursor.getInt(2))
            assertFalse("no other template should exist", cursor.moveToNext())
        }
        db.query("SELECT id, templateId FROM modules").use { cursor ->
            assertTrue("the module was left behind", cursor.moveToFirst())
            assertEquals("module-1", cursor.getString(0))
            assertEquals("user-fallback", cursor.getString(1))
        }
    }

    /**
     * Version 2 has no notion of a system fallback - it is seeded at runtime instead - so
     * the old built-in row is dropped. Its modules have to go with it rather than linger
     * pointing at a template that no longer exists.
     */
    @Test
    fun migrate1To2_dropsTheSystemFallbackAndItsModules() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO templates(
                    id, name, applicableDays, applicableStart, applicableEnd,
                    priority, isSystemFallback, isUserFallback, createdAt, updatedAt
                ) VALUES
                    ('system', 'System Fallback', 'MONDAY', '05:00', '11:00', 1, 1, 0, 1, 1),
                    ('kept', 'Kept', 'TUESDAY', '06:00', '10:00', 2, 0, 0, 1, 1)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO modules(
                    id, templateId, type, enabled, behaviours,
                    squatTarget, stepTarget, position
                ) VALUES
                    ('m-system', 'system', 'LEAVE_BED', 1, '', 0, 0, 0),
                    ('m-kept', 'kept', 'LEAVE_BED', 1, '', 0, 0, 0)
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            MorningFlowDatabase.MIGRATION_1_2,
        )

        db.query("SELECT id FROM templates ORDER BY id").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("kept", cursor.getString(0))
            assertFalse("the system fallback should have been dropped", cursor.moveToNext())
        }
        db.query("SELECT id FROM modules ORDER BY id").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("m-kept", cursor.getString(0))
            assertFalse("the dropped template's modules were orphaned", cursor.moveToNext())
        }
    }

    /** Session history is untouched by this migration and must survive it intact. */
    @Test
    fun migrate1To2_leavesSessionHistoryAlone() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO session_records(
                    id, templateId, templateName, startedAt, endedAt,
                    actualDurationSeconds, status, results
                ) VALUES (
                    'record-1', 'user-fallback', 'My Morning', 1000, 2800,
                    1800, 'COMPLETED', 'LEAVE_BED:VERIFIED'
                )
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            MorningFlowDatabase.MIGRATION_1_2,
        )

        db.query("SELECT templateName, actualDurationSeconds, status, results FROM session_records")
            .use { cursor ->
                assertTrue("the session record did not survive", cursor.moveToFirst())
                assertEquals("My Morning", cursor.getString(0))
                assertEquals(1800, cursor.getInt(1))
                assertEquals("COMPLETED", cursor.getString(2))
                assertEquals("LEAVE_BED:VERIFIED", cursor.getString(3))
            }
    }

    /**
     * Version 3 turns saved Short behaviours into Medium, because Short is now a quicker
     * version of each step. Other modes, and the rest of each entry, stay as they were.
     */
    @Test
    fun migrate2To3_movesShortBehavioursToMedium() {
        val unit = '\u001F'
        val entry = '\u001E'
        val before = listOf("b1${unit}LEAVE_BED${unit}SHORT${unit}false", "b2${unit}BRUSH_TEETH${unit}LONG${unit}false",
            "b3${unit}MAKE_BED${unit}OFF${unit}false").joinToString(entry.toString())
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO templates(
                    id, name, applicableDays, applicableStart, applicableEnd,
                    priority, isDefault, createdAt, updatedAt
                ) VALUES ('t', 'Mine', 'MONDAY', '06:00', '09:00', 1, 1, 1, 1)
                """.trimIndent(),
            )
            db.execSQL(
                "INSERT INTO modules(id, templateId, type, enabled, behaviours, squatTarget, stepTarget, position) " +
                    "VALUES ('m', 't', 'LEAVE_BED', 1, ?, 0, 0, 0)",
                arrayOf(before),
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MorningFlowDatabase.MIGRATION_2_3)

        db.query("SELECT behaviours FROM modules").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(before.replace("${unit}SHORT$unit", "${unit}MEDIUM$unit"), cursor.getString(0))
        }
    }

    /** An install with no templates yet still has to land on a valid version 2 schema. */
    @Test
    fun migrate1To2_handlesAnEmptyDatabase() {
        helper.createDatabase(TEST_DB, 1).close()

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            MorningFlowDatabase.MIGRATION_1_2,
        )

        db.query("SELECT COUNT(*) FROM templates").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }
}
