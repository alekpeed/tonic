package com.tonic.core.data.export

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.data.db.TonicDatabase
import com.tonic.core.data.repository.AttemptRepositoryImpl
import com.tonic.core.data.repository.DiagnosticRepositoryImpl
import com.tonic.core.data.repository.SessionRepositoryImpl
import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.state.DiagnosticResult
import com.tonic.core.model.state.EntryPoint
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Stage 2.1's acceptance criteria, against a real Room database rather than fakes —
 * docs/20-PHASE-2-SPEC.md §7: "exported JSON round-trips to identical state when parsed;
 * `amusia_indicator_flag` absent; works offline."
 */
@RunWith(AndroidJUnit4::class)
class DataExportRepositoryTest {
    private lateinit var db: TonicDatabase
    private lateinit var exporter: DataExportRepository
    private val now = Instant.parse("2026-08-20T12:00:00Z")

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TonicDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        exporter =
            DataExportRepositoryImpl(
                db.attemptDao(),
                db.sessionDao(),
                db.skillStateDao(),
                db.confusionStateDao(),
                db.diagnosticResultDao(),
                Clock { now },
            )
    }

    @After
    fun tearDown() = db.close()

    /** A small but genuinely populated profile: a session, two attempts, and a completed diagnostic. */
    private fun seedProfile() =
        runBlocking {
            val sessions = SessionRepositoryImpl(db.sessionDao())
            val attempts = AttemptRepositoryImpl(db.attemptDao())
            val diagnostics = DiagnosticRepositoryImpl(db.diagnosticResultDao())

            val session = sessions.create(rootSeed = 99L, plannedItemCount = 12, startedAt = now)
            val sessionId = session.id!!
            attempts.record(attempt(sessionId, targetLabel = "3", responseLabel = "3", correct = true))
            attempts.record(attempt(sessionId, targetLabel = "5", responseLabel = "1", correct = false))
            diagnostics.save(
                DiagnosticResult(
                    pitchDirectionThresholdCents = 40,
                    discriminationDPrime = 2.4,
                    tonalMemorySpan = 4,
                    amusiaIndicatorFlag = true,
                    recommendedEntry = EntryPoint.M2_STAGE_1,
                    initialAxisLevels = mapOf(DifficultyAxis.CADENCE_FADE to 1),
                    completedAt = now,
                    seed = 7L,
                ),
            )
        }

    private fun attempt(
        sessionId: Long,
        targetLabel: String,
        responseLabel: String?,
        correct: Boolean,
    ) = Attempt(
        skillId = SkillIds.M2_DEG_SET_1,
        sessionId = sessionId,
        itemSeed = 123L,
        axisLevels = mapOf(DifficultyAxis.CADENCE_FADE to 2),
        targetLabel = targetLabel,
        responseLabel = responseLabel,
        correct = correct,
        latencyMs = 1_500L,
        replayCount = 1,
        keyPitchClass = 5,
        targetMidi = 69,
        timbreId = "PURE",
        cadenceFadeLevel = 2,
        timestamp = now,
    )

    @Test
    fun `the exported JSON round-trips to an identical document`() {
        seedProfile()

        val original = runBlocking { exporter.buildExport() }
        val json = runBlocking { exporter.exportToJson() }
        val parsed = Json.decodeFromString<TonicExport>(json)

        // Structural equality across every section, not a spot check: if a field were dropped or
        // renamed in only one direction, this is what catches it.
        assertEquals(original, parsed)
        assertEquals(2, parsed.attempts.size)
        assertEquals(1, parsed.sessions.size)
        assertEquals(1, parsed.diagnosticResults.size)
    }

    @Test
    fun `the amusia indicator never appears in an export, in any form`() {
        // docs/20-PHASE-2-SPEC.md §6 and docs/02-PEDAGOGY.md §8. The seeded diagnostic above sets the
        // flag to *true*, so a leak here would be a real one rather than a vacuous pass. Checked against
        // the serialized text, not just the object graph: the risk being guarded is a shareable file
        // containing a field legible as "amusia", whatever route put it there.
        seedProfile()

        val json = runBlocking { exporter.exportToJson() }

        assertFalse(json.contains("amusia", ignoreCase = true), "export leaked the amusia indicator")
        // The measurements the flag is derived from are still exported - they are ordinary results.
        assertTrue(json.contains("pitch_direction_threshold_cents"))
        assertTrue(json.contains("discrimination_d_prime"))
    }

    @Test
    fun `the resume scratchpad is not exported, but the fact of it is`() {
        seedProfile()

        val json = runBlocking { exporter.exportToJson() }
        val parsed = Json.decodeFromString<TonicExport>(json)

        assertFalse(json.contains("resumeStateJson"), "the mid-session scratchpad is not user progress")
        assertFalse(parsed.sessions.single().wasResumableAtExport, "this session was never interrupted")
    }

    @Test
    fun `an export of an untouched install is valid and empty rather than absent`() {
        // A user who exports before practicing gets a well-formed file, not a crash and not a zero-byte
        // one. Every section is present and empty.
        val json = runBlocking { exporter.exportToJson() }
        val parsed = Json.decodeFromString<TonicExport>(json)

        assertEquals(TonicExport.CURRENT_SCHEMA_VERSION, parsed.schemaVersion)
        assertEquals(now.toEpochMilli(), parsed.exportedAtEpochMs)
        assertTrue(parsed.attempts.isEmpty())
        assertTrue(parsed.sessions.isEmpty())
        assertTrue(parsed.skillStates.isEmpty())
        assertTrue(parsed.confusionStates.isEmpty())
        assertTrue(parsed.diagnosticResults.isEmpty())
    }

    @Test
    fun `every attempt field survives the round trip`() {
        // The attempts list is the source of truth (docs/05-DATA-MODEL.md §1) - everything else is
        // derivable from it, so a silently dropped column here is the one that actually loses history.
        seedProfile()

        val parsed = Json.decodeFromString<TonicExport>(runBlocking { exporter.exportToJson() })
        val first = parsed.attempts.first()

        assertEquals(SkillIds.M2_DEG_SET_1.raw, first.skillId)
        assertEquals("3", first.targetLabel)
        assertEquals("3", first.responseLabel)
        assertTrue(first.correct)
        assertEquals(1_500L, first.latencyMs)
        assertEquals(1, first.replayCount)
        assertEquals(5, first.keyPitchClass)
        assertEquals(69, first.targetMidi)
        assertEquals("PURE", first.timbreId)
        assertEquals(2, first.cadenceFadeLevel)
        assertEquals(now.toEpochMilli(), first.timestampEpochMs)

        // An unanswered item keeps its null rather than being coerced to something answerable.
        assertEquals("1", parsed.attempts[1].responseLabel)
        assertFalse(parsed.attempts[1].correct)
    }

    @Test
    fun `the suggested file name is stable, sortable, and filesystem-safe`() {
        val name = runBlocking { exporter.suggestedFileName() }

        assertEquals("tonic-export-${now.toEpochMilli()}.json", name)
        assertTrue(name.none { it in ILLEGAL_FILENAME_CHARS }, "file name must be safe on any filesystem")
    }

    private companion object {
        val ILLEGAL_FILENAME_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', ' ')
    }
}
