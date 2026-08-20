package com.tonic.core.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.data.db.TonicDatabase
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.state.PlannedSlot
import com.tonic.core.model.state.ResumeState
import com.tonic.core.model.state.SessionPlan
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SessionRepositoryTest {
    private lateinit var db: TonicDatabase
    private lateinit var repository: SessionRepository

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TonicDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = SessionRepositoryImpl(db.sessionDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun resumeState() =
        ResumeState(
            plan =
                SessionPlan(
                    rootSeed = 42L,
                    plannedSlots = listOf(PlannedSlot(SkillIds.M2_DEG_SET_1, mapOf(DifficultyAxis.CADENCE_FADE to 2))),
                ),
            completedSlotIndex = 3,
        )

    @Test
    fun `a freshly created session has no end time and is not resumable until interrupted`() =
        runBlocking {
            val session = repository.create(rootSeed = 7L, plannedItemCount = 50, startedAt = Instant.EPOCH)
            assertNotNull(session.id)
            assertNull(session.endedAt)
            assertNull(
                repository.findResumable(),
                "a fresh, in-progress session with no resumeState isn't resumable yet",
            )
        }

    @Test
    fun `updateResumeState makes an interrupted session findable, complete clears it`() =
        runBlocking {
            val session = repository.create(rootSeed = 7L, plannedItemCount = 50, startedAt = Instant.EPOCH)
            repository.updateResumeState(session.id!!, completedItemCount = 12, resumeState = resumeState())

            val resumable = repository.findResumable()
            assertNotNull(resumable)
            assertEquals(12, resumable.completedItemCount)
            assertEquals(resumeState(), resumable.resumeState)

            repository.complete(session.id!!, completedItemCount = 50, endedAt = Instant.EPOCH.plusSeconds(300))
            assertNull(repository.findResumable(), "a completed session must never be offered for resume")
        }

    @Test
    fun `findResumable never returns an already-ended session`() =
        runBlocking {
            val session = repository.create(rootSeed = 1L, plannedItemCount = 10, startedAt = Instant.EPOCH)
            repository.complete(session.id!!, completedItemCount = 10, endedAt = Instant.EPOCH.plusSeconds(60))
            assertNull(repository.findResumable())
        }

    @Test
    fun `findById returns the matching session, null for an unknown id`() =
        runBlocking {
            val session = repository.create(rootSeed = 3L, plannedItemCount = 20, startedAt = Instant.EPOCH)
            assertEquals(session, repository.findById(session.id!!))
            assertNull(repository.findById(session.id!! + 999))
        }

    @Test
    fun `recentCompletedSessions returns only ended sessions, newest first, bounded by limit`() =
        runBlocking {
            val incomplete = repository.create(rootSeed = 1L, plannedItemCount = 10, startedAt = Instant.EPOCH)
            val first =
                repository.create(rootSeed = 2L, plannedItemCount = 10, startedAt = Instant.EPOCH.plusSeconds(10))
            repository.complete(first.id!!, completedItemCount = 10, endedAt = Instant.EPOCH.plusSeconds(20))
            val second =
                repository.create(rootSeed = 3L, plannedItemCount = 10, startedAt = Instant.EPOCH.plusSeconds(30))
            repository.complete(second.id!!, completedItemCount = 10, endedAt = Instant.EPOCH.plusSeconds(40))

            val recent = repository.recentCompletedSessions(limit = 10)
            assertEquals(listOf(second.id, first.id), recent.map { it.id })
            assertTrue(recent.none { it.id == incomplete.id })

            assertEquals(1, repository.recentCompletedSessions(limit = 1).size)
        }

    @Test
    fun `discardResumable clears exactly the resumable session and leaves its attempts' row history intact`() =
        runBlocking {
            val session = repository.create(rootSeed = 9L, plannedItemCount = 27, startedAt = Instant.EPOCH)
            repository.updateResumeState(
                session.id!!,
                completedItemCount = 4,
                resumeState =
                    ResumeState(
                        plan = SessionPlan(rootSeed = 9L, plannedSlots = emptyList()),
                        completedSlotIndex = 3,
                    ),
            )
            kotlin.test.assertNotNull(repository.findResumable(), "precondition: a resumable session exists")

            val discarded = repository.discardResumable(Instant.EPOCH.plusSeconds(60))

            kotlin.test.assertTrue(discarded)
            kotlin.test.assertNull(repository.findResumable(), "the offer must stop recurring")
            val row = repository.findById(session.id!!)
            kotlin.test.assertNotNull(row, "the row is closed, not deleted - attempts reference it")
            kotlin.test.assertEquals(4, row.completedItemCount, "the work done before discarding stays recorded")
        }

    @Test
    fun `discardResumable with nothing saved is a no-op that says so`() =
        runBlocking {
            val session = repository.create(rootSeed = 10L, plannedItemCount = 27, startedAt = Instant.EPOCH)
            repository.complete(session.id!!, 27, Instant.EPOCH.plusSeconds(300))
            kotlin.test.assertFalse(repository.discardResumable(Instant.EPOCH.plusSeconds(600)))
        }
}
