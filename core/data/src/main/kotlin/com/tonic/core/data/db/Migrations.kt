package com.tonic.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema change this database has ever undergone — docs/05-DATA-MODEL.md §4.
 *
 * `fallbackToDestructiveMigration()` is never called anywhere in this codebase, deliberately: the
 * attempts table is the source of truth everything else replays from, so losing it loses a learner's
 * entire history, and there is no backup to restore from in an offline, account-free app. Every entry
 * here ships with a `MigrationTest` case that walks a real database from the previous version and
 * checks the data survived.
 */
internal object Migrations {
    /**
     * v1 → v2: `attempts` gains `inputMethod` and `sungCents` for Phase 3's optional sung response
     * (docs/30-PHASE-3-SPEC.md §7).
     *
     * Two added columns and nothing else. The log is append-only, so no existing row is touched or
     * rewritten: `inputMethod` defaults to `'TAP'`, which is not a guess but a statement of fact —
     * before Phase 3 the degree ladder was the only way to answer anything. `sungCents` is nullable
     * and stays null, because no pitch was ever measured for those attempts and inventing a zero
     * would read as "sang it perfectly."
     */
    val MIGRATION_1_2 =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attempts ADD COLUMN inputMethod TEXT NOT NULL DEFAULT 'TAP'")
                db.execSQL("ALTER TABLE attempts ADD COLUMN sungCents INTEGER DEFAULT NULL")
            }
        }

    /** In order, for both [androidx.room.RoomDatabase.Builder.addMigrations] and the migration tests. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
