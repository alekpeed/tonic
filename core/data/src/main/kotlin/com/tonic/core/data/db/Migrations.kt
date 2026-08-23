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

    /**
     * v2 → v3: `attempts` gains the six rhythm columns of docs/40-PHASE-4-SPEC.md §8.
     *
     * Six added columns and nothing else, all nullable, none defaulted to a value. Null is the honest
     * reading for every row written before Phase 4: those attempts had no taps, so "no taps recorded"
     * and "zero taps recorded" must not become the same thing — the second would say a learner sat
     * through a rhythm item without moving, which never happened.
     *
     * The two list columns are stored as text rather than as a related table. The attempts table is an
     * append-only log that is replayed whole (§1), a tap list is meaningless apart from the attempt it
     * belongs to, and nothing ever queries across taps; a join table would add a migration surface and
     * a delete cascade for no query anyone will write.
     */
    val MIGRATION_2_3 =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attempts ADD COLUMN tapTimestampsMs TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE attempts ADD COLUMN calibrationOffsetUsedMs REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE attempts ADD COLUMN toleranceUsedMs REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE attempts ADD COLUMN perEventAsynchronyMs TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE attempts ADD COLUMN extraTaps INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE attempts ADD COLUMN missedTaps INTEGER DEFAULT NULL")
            }
        }

    /** In order, for both [androidx.room.RoomDatabase.Builder.addMigrations] and the migration tests. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
