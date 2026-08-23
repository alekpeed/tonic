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

    /**
     * v3 → v4: `attempts` gains the item side of a rhythm attempt — the time each expected event was
     * due, and the rhythmic figure each one belongs to (docs/40-PHASE-4-SPEC.md §5.3).
     *
     * Version 3 recorded the performance and not the question. That was enough to show a learner where
     * their taps landed and not enough to judge them: §5.3 criterion 3 measures drift as the *trend*
     * of asynchrony against elapsed time, which has no x-axis without the event times, and criterion 5
     * holds each rhythmic figure to 80%, which needs to know which beat each event belonged to. A
     * production item's `targetLabel` is only ever `"TAPPED"`, so the figure cannot come from there.
     *
     * A separate version rather than two more columns folded into v3, even though v3 is unreleased and
     * no row anywhere carries rhythm data yet — nothing in the app has ever written one. Editing a
     * shipped version in place leaves any database already opened at v3 permanently unopenable, with
     * no migration that can rescue it, and the cost of being wrong about who has run a branch build is
     * a learner's whole history (§1). One `ALTER TABLE` is the cheaper side of that trade.
     */
    val MIGRATION_3_4 =
        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attempts ADD COLUMN expectedEventTimesMs TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE attempts ADD COLUMN perEventFigures TEXT DEFAULT NULL")
            }
        }

    /** In order, for both [androidx.room.RoomDatabase.Builder.addMigrations] and the migration tests. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
