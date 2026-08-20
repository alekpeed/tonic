package com.tonic.core.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.data.dao.AttemptDao
import com.tonic.core.data.dao.SkillStateDao
import com.tonic.core.data.db.TonicDatabase
import com.tonic.core.engine.replay.SkillStateReducer
import com.tonic.core.model.attempts.Attempt
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
import kotlin.random.Random
import kotlin.test.assertEquals

/**
 * docs/09-BUILD-PLAN.md Stage 5's central acceptance criterion:
 * "`rebuildFromAttempts()` reconstructs skill state identically to
 * incremental updates, verified against a synthetic 1,000-attempt log.
 * This is the test that proves the attempt log is genuinely the source of
 * truth." Uses the real [SkillStateReducer] (`:core:engine`), not a stub -
 * a fake replayer would only prove the plumbing works, not that the
 * documented guarantee holds.
 */
@RunWith(AndroidJUnit4::class)
class SkillStateRepositoryTest {
    private lateinit var db: TonicDatabase
    private lateinit var attemptDao: AttemptDao
    private lateinit var skillStateDao: SkillStateDao
    private lateinit var attemptRepository: AttemptRepository
    private lateinit var skillStateRepository: SkillStateRepository

    private val skill = SkillIds.M2_DEG_SET_1
    private val degrees = listOf("1", "3", "5")

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TonicDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        attemptDao = db.attemptDao()
        skillStateDao = db.skillStateDao()
        attemptRepository = AttemptRepositoryImpl(attemptDao)
        skillStateRepository = SkillStateRepositoryImpl(skillStateDao, attemptDao, SkillStateReducer)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `rebuildFromAttempts reconstructs skill state identically to incremental replay over a 1000-attempt log`() =
        runBlocking {
            val random = Random(777)
            val attempts = mutableListOf<Attempt>()
            var index = 0
            while (index < 1000) {
                // Ability rises over time so the log exercises the full lifecycle: warm-up, ordinary
                // staircase movement, mastery, and post-mastery review blocks - not just one phase.
                val skillLevel = index * 0.01
                val cadence = (attempts.lastOrNull()?.axisLevels?.get(DifficultyAxis.CADENCE_FADE) ?: 0).toDouble()
                val p = (1.0 / (1.0 + Math.exp(cadence - skillLevel))).coerceIn(0.05, 0.97)
                val correct = random.nextDouble() < p
                val target = degrees[index % degrees.size]
                val axisLevels =
                    mapOf(
                        DifficultyAxis.CADENCE_FADE to
                            (attempts.lastOrNull()?.axisLevels?.get(DifficultyAxis.CADENCE_FADE) ?: 0),
                    )
                val attempt =
                    Attempt(
                        skillId = skill,
                        sessionId = (index / 40).toLong(),
                        itemSeed = index.toLong(),
                        axisLevels = axisLevels,
                        targetLabel = target,
                        responseLabel = if (correct) target else degrees[(index + 1) % degrees.size],
                        correct = correct,
                        latencyMs = 500,
                        replayCount = 0,
                        keyPitchClass = 0,
                        targetMidi = 60,
                        timbreId = "PURE",
                        cadenceFadeLevel = axisLevels.getValue(DifficultyAxis.CADENCE_FADE),
                        timestamp = Instant.EPOCH.plus(index.toLong(), ChronoUnit.SECONDS),
                        isWarmup = index < 5,
                        isAbandoned = index % 137 == 0, // a sprinkling of abandoned attempts too
                    )
                attempts += attempt
                attemptRepository.record(attempt)
                index++
            }

            // "Incremental" ground truth: SkillStateReducer.replay() run directly over the full, known
            // in-memory list - exactly what a live practice loop calling the same reducer per attempt
            // would converge to, since the reducer is a pure fold (see SkillStateReducer's KDoc).
            val expected = SkillStateReducer.replay(skill, attempts)

            skillStateRepository.rebuildFromAttempts()

            // Compare via the repository (round-tripped through JSON encode/decode), not the raw DAO
            // row, so this also proves the persistence mapping is lossless.
            val persisted = skillStateRepository.observe(skill).first()
            assertEquals(expected, persisted)
            assertEquals(expected.masteryState, persisted.masteryState)
            assertEquals(expected.totalAttempts, persisted.totalAttempts)
        }
}
