package com.tonic.core.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.data.db.TonicDatabase
import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.attempts.RhythmAttemptData
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class AttemptRepositoryTest {
    private lateinit var db: TonicDatabase
    private lateinit var repository: AttemptRepository

    private val skill = SkillIds.M2_DEG_SET_1

    private fun attempt(
        index: Int,
        target: String,
    ) = Attempt(
        skillId = skill,
        sessionId = 1L,
        itemSeed = index.toLong(),
        axisLevels = mapOf(DifficultyAxis.CADENCE_FADE to 2),
        targetLabel = target,
        responseLabel = target,
        correct = true,
        latencyMs = 500,
        replayCount = 0,
        keyPitchClass = 0,
        targetMidi = 60,
        timbreId = "PURE",
        cadenceFadeLevel = 2,
        timestamp = Instant.EPOCH.plus(index.toLong(), ChronoUnit.SECONDS),
    )

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TonicDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = AttemptRepositoryImpl(db.attemptDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `record round-trips every field, including axis levels through the JSON column`() =
        runBlocking {
            val original = attempt(0, "3")
            repository.record(original)

            val stored = repository.windowFor(skill, size = 10).single()
            assertEquals(original.skillId, stored.skillId)
            assertEquals(original.axisLevels, stored.axisLevels)
            assertEquals(original.targetLabel, stored.targetLabel)
            assertEquals(original.correct, stored.correct)
            assertEquals(original.timestamp, stored.timestamp)
        }

    @Test
    fun `a tapped attempt round-trips its whole rhythm record`() =
        runBlocking {
            // docs/40-PHASE-4-SPEC.md §4.4: tap times "are recorded with the attempt, which makes any
            // real session fully replayable and any scoring bug reproducible offline." That promise is
            // only kept if what comes back out is what went in, including the two per-event lists a
            // mastery verdict is computed from.
            val rhythm =
                RhythmAttemptData(
                    tapTimesMs = listOf(4.0, 611.0, 1188.0),
                    calibrationOffsetMs = 12.5,
                    toleranceHalfWidthMs = 150.0,
                    expectedEventTimesMs = listOf(0.0, 600.0, 1200.0, 1800.0),
                    perEventFigures = listOf("ta", "ta", "ta-di", "ta-di"),
                    perEventAsynchronyMs = listOf(4.0, 11.0, -12.0, null),
                    extraTaps = 0,
                    missedTaps = 1,
                )
            repository.record(attempt(0, "TAPPED").copy(skillId = SkillIds.M3_BEAT_DIV, rhythm = rhythm))

            val stored = repository.windowFor(SkillIds.M3_BEAT_DIV, size = 10).single()
            assertEquals(rhythm, stored.rhythm)
            // And the two things a mastery verdict actually reads, so a codec that round-tripped the
            // lists in the wrong order would still fail here.
            assertEquals(0.75, stored.rhythm?.patternAccuracy)
            assertEquals(mapOf("ta" to (2 to 2), "ta-di" to (1 to 2)), stored.rhythm?.figureOutcomes())
        }

    @Test
    fun `a rhythm record whose per-event lists disagree is dropped rather than guessed at`() =
        runBlocking {
            // The lists are only meaningful aligned, so a row where they disagree cannot say which tap
            // belongs to which beat. docs/05-DATA-MODEL.md §2: "do not crash, do not silently
            // misinterpret." Padding it would produce a plausible attempt describing a performance
            // nobody gave, and that attempt would then be graded.
            db.attemptDao().insert(
                attempt(0, "TAPPED").copy(skillId = SkillIds.M3_BEAT_DIV).toEntity().copy(
                    tapTimestampsMs = "[0.0,600.0]",
                    perEventAsynchronyMs = "[0.0,null,4.0]",
                    expectedEventTimesMs = "[0.0,600.0]",
                    perEventFigures = "[\"ta\",\"ta\"]",
                ),
            )

            val stored = repository.windowFor(SkillIds.M3_BEAT_DIV, size = 10).single()
            assertNull(stored.rhythm)
            assertEquals("TAPPED", stored.targetLabel, "the attempt itself survives; only its taps go")
        }

    @Test
    fun `windowFor returns the most recent N attempts in chronological order`() =
        runBlocking {
            for (i in 0 until 40) repository.record(attempt(i, target = (i % 3).toString()))

            val window = repository.windowFor(skill, size = 10)
            assertEquals(10, window.size)
            assertEquals((30L * 1_000), window.first().timestamp.toEpochMilli() - Instant.EPOCH.toEpochMilli())
            assertEquals(
                true,
                window.zipWithNext().all { (a, b) ->
                    a.timestamp < b.timestamp
                },
                "expected ascending order",
            )
        }

    @Test
    fun `recentAttempts is a live Flow that reflects newly recorded attempts`() =
        runBlocking {
            repository.record(attempt(0, "1"))
            assertEquals(1, repository.recentAttempts(skill, limit = 30).first().size)

            repository.record(attempt(1, "3"))
            assertEquals(2, repository.recentAttempts(skill, limit = 30).first().size)
        }

    @Test
    fun `attempts for a different skill never appear in another skill's window`() =
        runBlocking {
            repository.record(attempt(0, "1"))
            val otherSkillAttempt = attempt(1, "1").copy(skillId = SkillIds.M2_DEG_SET_2)
            repository.record(otherSkillAttempt)

            assertEquals(1, repository.windowFor(skill, size = 10).size)
            assertEquals(1, repository.windowFor(SkillIds.M2_DEG_SET_2, size = 10).size)
        }
}
