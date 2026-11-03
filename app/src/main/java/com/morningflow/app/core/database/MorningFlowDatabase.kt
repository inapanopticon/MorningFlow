package com.morningflow.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TemplateEntity::class, ModuleEntity::class, SessionRecordEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MorningFlowDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao

    abstract fun sessionRecordDao(): SessionRecordDao

    companion object {
        private const val DATABASE_NAME = "morningflow.db"

        fun build(context: Context): MorningFlowDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                MorningFlowDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

        /**
         * Replaces the two fallback flags with a single `isDefault`, and drops the
         * built-in system fallback row - version 2 seeds that at runtime instead.
         *
         * Both tables are rebuilt, and the order matters: `templates` reaches its final
         * name *before* the new `modules` table is created, so the foreign key is
         * written against `templates` rather than a scratch name. Renaming a table does
         * not rewrite the foreign keys that other tables declare against it, so creating
         * `modules` first would leave it pointing at a `templates_new` that no longer
         * exists - the table reads fine, then every insert fails.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE templates_new (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        applicableDays TEXT NOT NULL,
                        applicableStart TEXT NOT NULL,
                        applicableEnd TEXT NOT NULL,
                        priority INTEGER NOT NULL,
                        isDefault INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT INTO templates_new(
                        id, name, applicableDays, applicableStart, applicableEnd,
                        priority, isDefault, createdAt, updatedAt
                    )
                    SELECT id, name, applicableDays, applicableStart, applicableEnd,
                        priority, isUserFallback, createdAt, updatedAt
                    FROM templates
                    WHERE isSystemFallback = 0
                    """.trimIndent(),
                )
                // templates reaches its final name first, so the foreign key below can
                // name it directly.
                database.execSQL("DROP TABLE templates")
                database.execSQL("ALTER TABLE templates_new RENAME TO templates")
                database.execSQL(
                    """
                    CREATE TABLE modules_new (
                        id TEXT NOT NULL,
                        templateId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        behaviours TEXT NOT NULL,
                        squatTarget INTEGER NOT NULL,
                        stepTarget INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(templateId) REFERENCES templates(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT INTO modules_new(
                        id, templateId, type, enabled, behaviours,
                        squatTarget, stepTarget, position
                    )
                    SELECT id, templateId, type, enabled, behaviours,
                        squatTarget, stepTarget, position
                    FROM modules
                    WHERE templateId IN (SELECT id FROM templates)
                    """.trimIndent(),
                )
                database.execSQL("DROP TABLE modules")
                database.execSQL("ALTER TABLE modules_new RENAME TO modules")
                database.execSQL("CREATE INDEX index_modules_templateId ON modules(templateId)")
            }
        }

        /**
         * Short used to mean today's Medium timing; Short is now a quicker version of each
         * step. Saved routines move to Medium so nobody's morning gets shorter without
         * them choosing it. The schema itself is unchanged.
         *
         * In the stored behaviour list a mode sits between two unit separators
         * (`id␟KEY␟MODE␟locked`), so matching the separators changes only the mode field.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "UPDATE modules SET behaviours = " +
                        "REPLACE(behaviours, char(31) || 'SHORT' || char(31), char(31) || 'MEDIUM' || char(31))",
                )
            }
        }
    }
}
