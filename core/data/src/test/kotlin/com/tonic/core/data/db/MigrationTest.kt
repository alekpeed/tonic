package com.tonic.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every migration this database ships, walked over a real file-backed database with real rows in it.
 *
 * docs/05-DATA-MODEL.md §4 requires this and says why: `attempts` is the source of truth every other
 * table replays from, there is no account and no backup to restore from, and
 * `fallbackToDestructiveMigration()` is therefore never called. A migration that silently drops or
 * corrupts rows would destroy a learner's entire history with no way back, and would look exactly
 * like a working app until someone opened the Progress screen.
 *
 * So these do not merely assert that the migration *runs*. Each writes rows at the old version, runs
 * the migration, and reads the rows back — the only check that distinguishes "the schema changed"
 * from "the data survived the schema changing."
 *
 * Runs on the JVM under Robolectric rather than as an instrumented test (CLAUDE.md §1: instrumented
 * tests only where unavoidable). `room.testing` is already a `testImplementation` dependency and
 * `MigrationTestHelper` needs only a `Context` and the committed schema JSON.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            TonicDatabase::class.java,
        )

    /**
     * v1 → v2 adds Phase 3's `inputMethod` and `sungCents` to `attempts` (docs/30-PHASE-3-SPEC.md §7).
     *
     * The attempt written here is a version-1 row in every respect — it predates the columns entirely.
     * After the migration it must still be there, unchanged, and must read as what it actually was: a
     * tapped answer with no pitch measurement. `'TAP'` is the default because before Phase 3 the
     * degree ladder was the only way to answer anything, so it is a statement of fact rather than a
     * guess; `sungCents` stays null because inventing a zero would read as "sang it perfectly."
     */
    @Test
    fun `migrating 1 to 2 keeps every existing attempt and marks it tapped`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO attempts (
                    skillId, sessionId, itemSeed, axisLevelsJson, targetLabel, responseLabel,
                    correct, latencyMs, replayCount, keyPitchClass, targetMidi, timbreId,
                    cadenceFadeLevel, timestamp, isWarmup, isAbandoned, isIndependenceCheckProbe
                ) VALUES (
                    'M2.DEG_SET_1', 7, 12345, '{}', '3', '5',
                    0, 1800, 2, 0, 64, 'PURE',
                    3, 1000, 0, 0, 0
                )
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, Migrations.MIGRATION_1_2)

        migrated.query("SELECT * FROM attempts").use { cursor ->
            assertEquals(1, cursor.count, "the migration must not drop the attempt log")
            assertTrue(cursor.moveToFirst())

            // The pre-existing columns, spot-checked across types: a migration that rebuilt the table
            // and lost a column's contents would still pass a bare row count.
            assertEquals("M2.DEG_SET_1", cursor.getString(cursor.getColumnIndexOrThrow("skillId")))
            assertEquals(12345L, cursor.getLong(cursor.getColumnIndexOrThrow("itemSeed")))
            assertEquals("3", cursor.getString(cursor.getColumnIndexOrThrow("targetLabel")))
            assertEquals("5", cursor.getString(cursor.getColumnIndexOrThrow("responseLabel")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("correct")))
            assertEquals(2, cursor.getInt(cursor.getColumnIndexOrThrow("replayCount")))
            assertEquals(1000L, cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")))

            // The new ones.
            assertEquals(
                "TAP",
                cursor.getString(cursor.getColumnIndexOrThrow("inputMethod")),
                "an attempt from before Phase 3 was tapped - there was no other way to answer",
            )
            assertTrue(
                cursor.isNull(cursor.getColumnIndexOrThrow("sungCents")),
                "no pitch was ever measured for this attempt; a zero would read as a perfect one",
            )
        }
    }

    /**
     * The same migration reached the way a real device reaches it — through `Room.databaseBuilder`
     * with the migration list the app actually registers, rather than by naming the migration
     * directly. A migration that exists but was never added to [Migrations.ALL] passes the test above
     * and crashes on a user's phone; this is the case that catches that.
     */
    @Test
    fun `the registered migration list carries a v1 database all the way to the current version`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO attempts (
                    skillId, sessionId, itemSeed, axisLevelsJson, targetLabel, responseLabel,
                    correct, latencyMs, replayCount, keyPitchClass, targetMidi, timbreId,
                    cadenceFadeLevel, timestamp, isWarmup, isAbandoned, isIndependenceCheckProbe
                ) VALUES (
                    'M2.DEG_SET_1', 1, 99, '{}', '1', '1',
                    1, 900, 0, 0, 60, 'PURE',
                    0, 500, 0, 0, 0
                )
                """.trimIndent(),
            )
        }

        val db =
            androidx.room.Room
                .databaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    TonicDatabase::class.java,
                    TEST_DB,
                ).addMigrations(*Migrations.ALL)
                .build()

        try {
            db.openHelper.writableDatabase.query("SELECT inputMethod, sungCents FROM attempts").use { cursor ->
                assertTrue(cursor.moveToFirst(), "the row must survive the full builder path too")
                assertEquals("TAP", cursor.getString(0))
                assertNull(cursor.getString(1))
            }
        } finally {
            db.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
