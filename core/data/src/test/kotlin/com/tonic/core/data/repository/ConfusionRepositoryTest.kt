package com.tonic.core.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.data.db.TonicDatabase
import com.tonic.core.engine.confusion.ConfusionTracker
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.time.Instant
import kotlin.test.assertEquals

/** Uses the real [ConfusionTracker] (`:core:engine`) - see [SkillStateRepositoryTest]'s KDoc for why. */
@RunWith(AndroidJUnit4::class)
class ConfusionRepositoryTest {
    private lateinit var db: TonicDatabase
    private lateinit var repository: ConfusionRepository

    private val skill = SkillIds.M2_FULL_DIATONIC
    private val fixedClock = Clock { Instant.parse("2026-01-01T00:00:00Z") }

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TonicDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = ConfusionRepositoryImpl(db.confusionStateDao(), ConfusionTracker, fixedClock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `record persists across repository instances - the sliding window survives a process restart`() =
        runBlocking {
            repeat(5) { repository.record(skill, "1", "1") }
            repeat(3) { repository.record(skill, "4", "1") }

            // A fresh repository instance over the SAME database, simulating a process restart - the
            // window state must have actually been persisted, not just held in memory.
            val reloaded = ConfusionRepositoryImpl(db.confusionStateDao(), ConfusionTracker, fixedClock)
            val matrix = reloaded.matrixFor(skill)

            assertEquals(3, matrix.attemptsFor("4"))
            assertEquals(0.0, matrix.accuracyFor("4"))
            assertEquals(8, matrix.cells.sumOf { it.windowCount })
        }

    @Test
    fun `an unrecorded skill has an empty matrix, not a crash`() =
        runBlocking {
            val matrix = repository.matrixFor(SkillIds.M2_DEG_SET_1)
            assertEquals(0, matrix.cells.size)
        }

    @Test
    fun `different skills keep independent confusion state`() =
        runBlocking {
            repository.record(skill, "1", "1")
            repository.record(SkillIds.M2_DEG_SET_1, "1", "3")

            assertEquals(1, repository.matrixFor(skill).attemptsFor("1"))
            assertEquals(1.0, repository.matrixFor(skill).accuracyFor("1"))
            assertEquals(0.0, repository.matrixFor(SkillIds.M2_DEG_SET_1).accuracyFor("1"))
        }
}
