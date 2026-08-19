package com.tonic.core.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.data.db.TonicDatabase
import com.tonic.core.engine.replay.SkillStateReducer
import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.state.MasteryState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * docs/01-PRODUCT-SPEC.md §5 criterion 5: "All progress survives app kill, device reboot, and app
 * update." Uses a real *file-backed* database, not [Room.inMemoryDatabaseBuilder] - an in-memory
 * database can't outlive closing the connection, so it can't stand in for "the process died." Opening a
 * brand-new [TonicDatabase] instance against the same on-disk file after fully closing the first one is
 * the actual mechanism a real app-kill-and-relaunch goes through.
 *
 * Deliberately does NOT assert the interrupted session's own queue position is resumable -
 * `SessionRepository.updateResumeState`/`findResumable` exist and are unit-tested on their own
 * (`SessionRepositoryTest`) but aren't wired to any app lifecycle event yet, so resuming the *exact*
 * interrupted session isn't offered. What this test proves is the thing the success criterion actually
 * names: recorded attempts and the mastery/skill state derived from them - the durable pedagogical
 * progress a user has already earned - are never lost, because both are written to Room synchronously
 * as they happen ([com.tonic.feature.practice.engine.PracticeLoopEngine] records an attempt and rebuilds
 * skill state on every single answer, not in a batch at session end).
 */
@RunWith(AndroidJUnit4::class)
class ProgressDurabilityTest {
    private val dbName = "progress-durability-test.db"
    private var db: TonicDatabase? = null

    @After
    fun tearDown() {
        db?.close()
        RuntimeEnvironment.getApplication().deleteDatabase(dbName)
    }

    private fun openDatabase(): TonicDatabase =
        Room
            .databaseBuilder(RuntimeEnvironment.getApplication(), TonicDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
            .also { db = it }

    private fun attempt(
        index: Int,
        sessionId: Long,
        correct: Boolean,
    ) = Attempt(
        skillId = SkillIds.M2_DEG_SET_1,
        sessionId = sessionId,
        itemSeed = index.toLong(),
        axisLevels = mapOf(DifficultyAxis.CADENCE_FADE to 0),
        targetLabel = "1",
        responseLabel = if (correct) "1" else "3",
        correct = correct,
        latencyMs = 400,
        replayCount = 0,
        keyPitchClass = 0,
        targetMidi = 60,
        timbreId = "PURE",
        cadenceFadeLevel = 0,
        timestamp = Instant.EPOCH.plusSeconds(index.toLong()),
    )

    @Test
    fun `attempts and skill state survive closing and reopening the database - a simulated app kill`() =
        runBlocking {
            val firstProcess = openDatabase()
            val attemptRepository = AttemptRepositoryImpl(firstProcess.attemptDao())
            val skillStateRepository =
                SkillStateRepositoryImpl(firstProcess.skillStateDao(), firstProcess.attemptDao(), SkillStateReducer)
            val sessionRepository = SessionRepositoryImpl(firstProcess.sessionDao())

            val session = sessionRepository.create(rootSeed = 1L, plannedItemCount = 50, startedAt = Instant.EPOCH)
            repeat(12) { i ->
                val attempt = attempt(i, session.id!!, correct = true)
                attemptRepository.record(attempt)
                skillStateRepository.rebuildFromAttempts(attempt.skillId)
            }
            sessionRepository.updateResumeState(session.id!!, completedItemCount = 12, resumeState = null)

            val stateBeforeKill = skillStateRepository.observe(SkillIds.M2_DEG_SET_1).first()
            assertEquals(12, stateBeforeKill.totalAttempts)

            // Simulated app kill: the process (and every in-memory repository/ViewModel it held) is gone.
            firstProcess.close()

            // Cold relaunch: brand-new database connection and brand-new repository instances, reading
            // the same on-disk file - nothing carried over from the first process except what Room wrote.
            val secondProcess = openDatabase()
            val attemptsAfterKill =
                AttemptRepositoryImpl(
                    secondProcess.attemptDao(),
                ).windowFor(SkillIds.M2_DEG_SET_1, 100)
            val skillStateAfterKill =
                SkillStateRepositoryImpl(secondProcess.skillStateDao(), secondProcess.attemptDao(), SkillStateReducer)
                    .observe(SkillIds.M2_DEG_SET_1)
                    .first()
            val sessionAfterKill = SessionRepositoryImpl(secondProcess.sessionDao()).findById(session.id!!)

            assertEquals(12, attemptsAfterKill.size, "no attempt was lost")
            assertTrue(attemptsAfterKill.all { it.correct }, "recorded outcomes weren't corrupted")
            assertEquals(stateBeforeKill, skillStateAfterKill, "mastery/axis progress survives intact")
            assertEquals(MasteryState.IN_PROGRESS, skillStateAfterKill.masteryState)
            assertEquals(12, sessionAfterKill?.completedItemCount, "the session row's own progress survives too")
        }
}
