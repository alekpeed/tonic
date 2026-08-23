package com.tonic.core.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every migration this database ships, walked over a real database with real rows in it.
 *
 * docs/05-DATA-MODEL.md §4 requires this and says why: `attempts` is the source of truth every other
 * table replays from, there is no account and no backup to restore from, and
 * `fallbackToDestructiveMigration()` is therefore never called. A migration that silently dropped or
 * corrupted rows would destroy a learner's entire history with no way back, and would look exactly
 * like a working app until someone opened the Progress screen.
 *
 * So these do not merely assert that a migration *runs*. Each builds the old database, writes rows
 * into it, migrates, and reads them back — the only check that separates "the schema changed" from
 * "the data survived the schema changing."
 *
 * **Built from the committed schema JSON rather than from hand-written DDL.** `schemas/` is where
 * every historical table definition already lives, exactly as the build emitted it, and replaying its
 * `createSql` is what makes this test describe the database users actually have rather than one
 * reconstructed from memory. It also means a hand-edited schema file cannot quietly diverge from what
 * migrations are tested against.
 *
 * **Room does the validating, and that is the point of opening through the builder.** On finding an
 * older `user_version` Room runs the registered migrations and then checks the resulting schema
 * against the one compiled into this build, throwing if they differ. So a migration that adds the
 * wrong column type, or is missing from [Migrations.ALL] entirely, fails here — the second case being
 * one that a test naming the migration directly would pass while the app crashed on a phone.
 *
 * Runs on the JVM under Robolectric rather than as an instrumented test (CLAUDE.md §1: instrumented
 * tests only where unavoidable). `MigrationTestHelper` is deliberately not used: it resolves schemas
 * through instrumentation assets, which needs build-level asset wiring that AGP 9's DSL made
 * non-obvious, and going through `Room.databaseBuilder` tests the path the app itself takes anyway.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    /**
     * v1 → v2 adds Phase 3's `inputMethod` and `sungCents` to `attempts` (docs/30-PHASE-3-SPEC.md §7).
     *
     * The attempt written here is a version-1 row in every respect — it predates the columns entirely.
     * After the migration it must still be there, unchanged, and must read as what it actually was: a
     * tapped answer with no pitch measurement. `TAP` is the default because before Phase 3 the degree
     * ladder was the only way to answer anything, so it is a statement of fact rather than a guess;
     * `sungCents` stays null because inventing a zero would read as "sang it perfectly."
     */
    @Test
    fun `migrating 1 to 2 keeps every existing attempt and marks it tapped`() {
        createDatabaseAtVersion(1) { db ->
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

        openThroughRoom().use { db ->
            db.query("SELECT * FROM attempts").use { cursor ->
                assertEquals(1, cursor.count, "the migration must not drop the attempt log")
                assertTrue(cursor.moveToFirst())

                // The pre-existing columns, spot-checked across types and including a null: a
                // migration that rebuilt the table and lost a column's contents would still pass a
                // bare row count.
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
    }

    /**
     * The rest of the database, unmentioned by this migration and therefore the easiest thing to lose
     * to one. `skill_states` is materialized progress and `sessions` carries resume state; a migration
     * that rebuilt the world around `attempts` could take either with it and still pass the test above.
     */
    @Test
    fun `migrating 1 to 2 leaves the other tables untouched`() {
        createDatabaseAtVersion(1) { db ->
            db.execSQL(
                """
                INSERT INTO skill_states (
                    skillId, axisLevelsJson, staircaseStateJson, activeAxis, masteryStatus,
                    masteredAt, fsrsStability, fsrsDifficulty, fsrsLastReview, fsrsDue, fsrsReps,
                    fsrsLapses, totalAttempts, updatedAt
                ) VALUES (
                    'M2.DEG_SET_1', '{}', '{}', NULL, 'MASTERED',
                    500, 3.5, 5.0, 500, 900, 4,
                    1, 120, 500
                )
                """.trimIndent(),
            )
        }

        openThroughRoom().use { db ->
            db.query("SELECT masteryStatus, totalAttempts FROM skill_states").use { cursor ->
                assertTrue(cursor.moveToFirst(), "mastery must survive an unrelated migration")
                assertEquals("MASTERED", cursor.getString(0))
                assertEquals(120, cursor.getInt(1))
            }
        }
    }

    /**
     * v2 → v3 adds Phase 4's six rhythm columns to `attempts` (docs/40-PHASE-4-SPEC.md §8).
     *
     * The attempt written here is a version-2 row: a sung Phase 3 answer, which is the most recent
     * shape a row could have had before this migration and therefore the one most likely to be damaged
     * by it. After the migration it must still be there, with its Phase 3 columns intact, and every
     * rhythm column must read **null** — not zero. "No taps were recorded" and "the learner tapped
     * nothing" are different facts, and only the first one ever happened to a pitch attempt.
     */
    @Test
    fun `migrating 2 to 3 keeps every existing attempt and leaves its rhythm columns empty`() {
        createDatabaseAtVersion(2) { db ->
            db.execSQL(
                """
                INSERT INTO attempts (
                    skillId, sessionId, itemSeed, axisLevelsJson, targetLabel, responseLabel,
                    correct, latencyMs, replayCount, keyPitchClass, targetMidi, timbreId,
                    cadenceFadeLevel, timestamp, isWarmup, isAbandoned, isIndependenceCheckProbe,
                    inputMethod, sungCents
                ) VALUES (
                    'M2.FULL_DIATONIC', 9, 99999, '{}', '5', '5',
                    1, 2400, 0, 7, 67, 'REED',
                    4, 2000, 0, 0, 0,
                    'SUNG', -14
                )
                """.trimIndent(),
            )
        }

        openThroughRoom().use { db ->
            db.query("SELECT * FROM attempts").use { cursor ->
                assertEquals(1, cursor.count, "the migration must not drop the attempt log")
                assertTrue(cursor.moveToFirst())

                // Phase 3's columns, which this migration has no business touching.
                assertEquals("SUNG", cursor.getString(cursor.getColumnIndexOrThrow("inputMethod")))
                assertEquals(-14, cursor.getInt(cursor.getColumnIndexOrThrow("sungCents")))
                assertEquals("M2.FULL_DIATONIC", cursor.getString(cursor.getColumnIndexOrThrow("skillId")))
                assertEquals(99999L, cursor.getLong(cursor.getColumnIndexOrThrow("itemSeed")))
                assertEquals(2400L, cursor.getLong(cursor.getColumnIndexOrThrow("latencyMs")))

                // The six new ones, every one null rather than defaulted.
                for (column in RHYTHM_COLUMNS) {
                    assertTrue(
                        cursor.isNull(cursor.getColumnIndexOrThrow(column)),
                        "$column must be null on an attempt that was never tapped, not zero",
                    )
                }
            }
        }
    }

    /**
     * v3 → v4 — docs/40-PHASE-4-SPEC.md §5.3.
     *
     * Version 3 could hold a rhythm attempt but nothing in the app ever wrote one, so the row seeded
     * here is a hand-built one: it is the only way to prove that a database that *did* carry tap data
     * keeps it across this migration, and the case a real learner would hit if a branch build had ever
     * reached them. Its six version-3 columns must be untouched afterwards, and the two new ones must
     * read null — that attempt was scored without them, and inventing an event time would put words in
     * the item's mouth.
     */
    @Test
    fun `migrating 3 to 4 keeps a tapped attempt and leaves the new item columns empty`() {
        createDatabaseAtVersion(3) { db ->
            db.execSQL(
                """
                INSERT INTO attempts (
                    skillId, sessionId, itemSeed, axisLevelsJson, targetLabel, responseLabel,
                    correct, latencyMs, replayCount, keyPitchClass, targetMidi, timbreId,
                    cadenceFadeLevel, timestamp, isWarmup, isAbandoned, isIndependenceCheckProbe,
                    inputMethod, sungCents,
                    tapTimestampsMs, calibrationOffsetUsedMs, toleranceUsedMs,
                    perEventAsynchronyMs, extraTaps, missedTaps
                ) VALUES (
                    'M3.BEAT_DIV', 4, 4242, '{}', 'TAPPED', 'TAPPED',
                    1, 0, 0, 0, 0, 'PURE',
                    0, 3000, 0, 0, 0,
                    'TAP', NULL,
                    '[0.0,600.0]', 12.5, 150.0,
                    '[4.0,null]', 1, 1
                )
                """.trimIndent(),
            )
        }

        openThroughRoom().use { db ->
            db.query("SELECT * FROM attempts").use { cursor ->
                assertEquals(1, cursor.count, "the migration must not drop a tapped attempt")
                assertTrue(cursor.moveToFirst())

                assertEquals("[0.0,600.0]", cursor.getString(cursor.getColumnIndexOrThrow("tapTimestampsMs")))
                assertEquals("[4.0,null]", cursor.getString(cursor.getColumnIndexOrThrow("perEventAsynchronyMs")))
                assertEquals(12.5, cursor.getDouble(cursor.getColumnIndexOrThrow("calibrationOffsetUsedMs")))
                assertEquals(150.0, cursor.getDouble(cursor.getColumnIndexOrThrow("toleranceUsedMs")))
                assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("extraTaps")))
                assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("missedTaps")))

                for (column in RHYTHM_ITEM_COLUMNS) {
                    assertTrue(
                        cursor.isNull(cursor.getColumnIndexOrThrow(column)),
                        "$column must be null on an attempt scored before it existed",
                    )
                }
            }
        }
    }

    /**
     * A version-1 database taken all the way to the current version in one open.
     *
     * The case a per-step test cannot cover: a learner who installed before Phase 3 and updates once,
     * skipping every release in between. Room runs both migrations in sequence here, and a mistake in
     * how they compose — a column added twice, a step that assumes the previous one ran — surfaces
     * only on this path.
     */
    @Test
    fun `a version 1 database survives being taken all the way to the current version`() {
        createDatabaseAtVersion(1) { db ->
            db.execSQL(
                """
                INSERT INTO attempts (
                    skillId, sessionId, itemSeed, axisLevelsJson, targetLabel, responseLabel,
                    correct, latencyMs, replayCount, keyPitchClass, targetMidi, timbreId,
                    cadenceFadeLevel, timestamp, isWarmup, isAbandoned, isIndependenceCheckProbe
                ) VALUES (
                    'M2.DEG_SET_2', 1, 555, '{}', '2', '2',
                    1, 1100, 0, 3, 62, 'SOFT',
                    2, 100, 0, 0, 0
                )
                """.trimIndent(),
            )
        }

        openThroughRoom().use { db ->
            db.query("SELECT * FROM attempts").use { cursor ->
                assertEquals(1, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals("M2.DEG_SET_2", cursor.getString(cursor.getColumnIndexOrThrow("skillId")))
                assertEquals(555L, cursor.getLong(cursor.getColumnIndexOrThrow("itemSeed")))
                assertEquals(
                    "TAP",
                    cursor.getString(cursor.getColumnIndexOrThrow("inputMethod")),
                    "Phase 3's default must still apply after Phase 4's migration runs on top of it",
                )
                for (column in ALL_RHYTHM_COLUMNS) {
                    assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow(column)), "$column should be null")
                }
            }
        }
    }

    /**
     * Builds a database at a historical version by replaying that version's committed schema, then
     * hands it to [seed] to populate. Room's own `room_master_table` identity row is written from the
     * schema's `setupQueries`, which is what makes the result indistinguishable from a database this
     * app actually created at that version — without it Room would not recognize it as one of its own.
     */
    private fun createDatabaseAtVersion(
        version: Int,
        seed: (android.database.sqlite.SQLiteDatabase) -> Unit,
    ) {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(TEST_DB)

        val schemaFile = File(SCHEMA_DIR, "$version.json")
        assertTrue(
            schemaFile.isFile,
            "no committed schema for version $version at ${schemaFile.absolutePath} - " +
                "docs/05-DATA-MODEL.md §4 requires every version's JSON in the repository",
        )
        val schema = Json.parseToJsonElement(schemaFile.readText()).jsonObject["database"]!!.jsonObject

        val db = context.openOrCreateDatabase(TEST_DB, android.content.Context.MODE_PRIVATE, null)
        try {
            schema["entities"]!!.jsonArray.forEach { entity ->
                val table = entity.jsonObject["tableName"]!!.jsonPrimitive.content
                val create = entity.jsonObject["createSql"]!!.jsonPrimitive.content
                db.execSQL(create.replace("\${TABLE_NAME}", table))
                entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                    val createIndex = index.jsonObject["createSql"]!!.jsonPrimitive.content
                    db.execSQL(createIndex.replace("\${TABLE_NAME}", table))
                }
            }
            schema["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.version = version
            seed(db)
        } finally {
            db.close()
        }
    }

    /**
     * Opens the seeded database exactly as the app does — same builder, same registered migration
     * list. Room runs the migrations and validates the result against the compiled schema, so this
     * call is itself the assertion that the migration was correct and was registered.
     */
    private fun openThroughRoom(): TonicDatabase =
        Room
            .databaseBuilder(RuntimeEnvironment.getApplication(), TonicDatabase::class.java, TEST_DB)
            .addMigrations(*Migrations.ALL)
            .build()
            .also { it.openHelper.writableDatabase }

    private fun TonicDatabase.use(block: (androidx.sqlite.db.SupportSQLiteDatabase) -> Unit) {
        try {
            block(openHelper.writableDatabase)
        } finally {
            close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"

        /** The six columns docs/40-PHASE-4-SPEC.md §8 adds at version 3. */
        val RHYTHM_COLUMNS =
            listOf(
                "tapTimestampsMs",
                "calibrationOffsetUsedMs",
                "toleranceUsedMs",
                "perEventAsynchronyMs",
                "extraTaps",
                "missedTaps",
            )

        /** The two docs/40-PHASE-4-SPEC.md §5.3 adds at version 4 — the item side of a tapped attempt. */
        val RHYTHM_ITEM_COLUMNS = listOf("expectedEventTimesMs", "perEventFigures")

        /** Every rhythm column, whichever version introduced it. */
        val ALL_RHYTHM_COLUMNS = RHYTHM_COLUMNS + RHYTHM_ITEM_COLUMNS

        /**
         * Relative to the module directory, which is a Gradle test's working directory. The same path
         * `room { schemaDirectory(...) }` writes to in this module's build script.
         */
        val SCHEMA_DIR = File("schemas/com.tonic.core.data.db.TonicDatabase")
    }
}
