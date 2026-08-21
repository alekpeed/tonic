package com.tonic.core.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.data.db.TonicDatabase
import com.tonic.core.engine.debug.DebugMasterySeeder
import com.tonic.core.engine.replay.SkillStateReducer
import com.tonic.core.model.ids.SkillId
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
 * The debug jump tool's sequence, run against a **real Room database** rather than hand-written fakes.
 *
 * This test exists because of a process failure worth recording. The jump tool shipped three times and
 * crashed on a real device all three times, while its unit tests passed all three times — because those
 * tests ran against in-memory fakes *this same author wrote*, which encoded the same assumptions as the
 * code under test. A fake cannot disagree with you; only a real dependency can. `:core:data` has had
 * `testImplementation(project(":core:engine"))` and Robolectric-hosted Room since Stage 5, so this was
 * always available and simply never used for the debug path.
 *
 * What it covers that the fakes structurally could not: real SQLite (batch insert limits, delete
 * semantics, `allForSkill`'s ordering contract) and the real [SkillStateReducer] reading rows back
 * through the real mappers — the round trip through `axisLevelsJson`, not an object held in a map.
 */
@RunWith(AndroidJUnit4::class)
class DebugJumpAgainstRealDatabaseTest {
    private var db: TonicDatabase? = null

    @After
    fun tearDown() {
        db?.close()
    }

    private fun open(): TonicDatabase =
        Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TonicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { db = it }

    /** The three repositories the jumper actually holds, over one real database. */
    private class Fixture(
        db: TonicDatabase,
    ) {
        val attempts: AttemptRepository = AttemptRepositoryImpl(db.attemptDao())
        val skillStates: SkillStateRepository =
            SkillStateRepositoryImpl(db.skillStateDao(), db.attemptDao(), SkillStateReducer)
        val debugProgress: DebugProgressRepository =
            DebugProgressRepositoryImpl(db.attemptDao(), db.skillStateDao(), db.confusionStateDao())
        val sessions: SessionRepository = SessionRepositoryImpl(db.sessionDao())
    }

    /** [com.tonic.feature.settings.debug.DebugSkillJumper.jumpTo]'s loop, verbatim. */
    private suspend fun jumpTo(
        fixture: Fixture,
        target: SkillId,
    ): List<SkillId> {
        fixture.debugProgress.resetProgress()
        val startedAt = Instant.EPOCH
        val session = fixture.sessions.create(rootSeed = 1L, plannedItemCount = 0, startedAt = startedAt)
        val sessionId = requireNotNull(session.id)

        val mastered = mutableSetOf<SkillId>()
        val seeded = mutableListOf<SkillId>()
        while (true) {
            val next = SkillGraph.currentNodeFor { it in mastered }
            if (next == target) break
            check(seeded.size < SkillGraph.practiceChain.size) { "never reached ${target.raw}" }
            val generated =
                DebugMasterySeeder.attemptsToMaster(next, sessionId, startedAt.plusSeconds(seeded.size * 3_600L))
            fixture.debugProgress.recordAttempts(generated)
            fixture.skillStates.rebuildFromAttempts(next)
            mastered += next
            seeded += next
        }
        return seeded
    }

    private suspend fun masteredIn(fixture: Fixture): Set<SkillId> =
        fixture.skillStates
            .observeAll()
            .first()
            .filterValues { it.masteryState == MasteryState.MASTERED }
            .keys

    @Test
    fun `a jump to the far end of the chain survives a real database`() {
        runBlocking {
            val fixture = Fixture(open())
            val target = SkillGraph.practiceChain.last().id

            val seeded = jumpTo(fixture, target)

            assertEquals(
                SkillGraph.practiceChain.map { it.id }.takeWhile { it != target },
                seeded,
            )
            val mastered = masteredIn(fixture)
            assertEquals(seeded.toSet(), mastered, "rows came back from SQLite unmastered")
            assertEquals(target, SkillGraph.currentNodeFor { it in mastered })
        }
    }

    @Test
    fun `pressing a later node then an earlier one - the sequence that crashed on device`() {
        runBlocking {
            val fixture = Fixture(open())
            jumpTo(fixture, SkillGraph.practiceChain.last().id)

            val seeded = jumpTo(fixture, SkillGraph.practiceChain[1].id)

            assertEquals(listOf(SkillGraph.practiceChain.first().id), seeded)
            assertEquals(seeded.toSet(), masteredIn(fixture), "the reset left mastered rows behind")
        }
    }

    @Test
    fun `reset actually empties the tables, so jumps do not accumulate`() {
        runBlocking {
            val fixture = Fixture(open())
            jumpTo(fixture, SkillGraph.practiceChain.last().id)
            val afterFirst = db!!.attemptDao().allAttempts().size
            assertTrue(afterFirst > 0, "the first jump wrote nothing at all")

            jumpTo(fixture, SkillGraph.practiceChain.last().id)

            assertEquals(afterFirst, db!!.attemptDao().allAttempts().size, "attempts accumulated across jumps")
        }
    }

    @Test
    fun `TIMING measure a jump to the far end`() {
        runBlocking {
            val fixture = Fixture(open())
            val target = SkillGraph.practiceChain.last().id
            val t0 = System.nanoTime()
            val seeded = jumpTo(fixture, target)
            val ms = (System.nanoTime() - t0) / 1_000_000
            val attempts = db!!.attemptDao().allAttempts().size
            println("TIMING " + target.raw + ": " + ms + "ms, " + seeded.size + " nodes, " + attempts + " attempts")
        }
    }

    @Test
    fun `every node is reachable in one press against real SQLite`() {
        runBlocking {
            val fixture = Fixture(open())
            for (node in SkillGraph.practiceChain) {
                jumpTo(fixture, node.id)
                val mastered = masteredIn(fixture)
                assertEquals(
                    node.id,
                    SkillGraph.currentNodeFor { it in mastered },
                    "${node.id.raw} was not reachable in one press",
                )
            }
        }
    }
}
