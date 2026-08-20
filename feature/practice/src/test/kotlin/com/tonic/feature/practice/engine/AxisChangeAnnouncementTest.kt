package com.tonic.feature.practice.engine

import com.tonic.core.engine.session.SkillWorkContext
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.AxisChange
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.state.SkillState
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * docs/11-ONBOARDING-CLARITY.md §9.3: "no silent difficulty changes, ever." Every axis level move that
 * changes what the exercise sounds like has to be announced, whether the staircase caused it or the
 * session schedule did — "a level change is a level change regardless of what triggered it."
 */
class AxisChangeAnnouncementTest {
    private class Fixture {
        val attemptRepository = FakeAttemptRepository()
        val skillStateRepository = FakeSkillStateRepository(attemptRepository)
        val confusionRepository = FakeConfusionRepository()
        val sessionRepository = FakeSessionRepository()
        val audioPlayer = FakeAudioPlayer()
        val audioInterruptions = FakeAudioInterruptions()
        val clock = Clock { Instant.EPOCH }
        val engine =
            PracticeLoopEngine(
                attemptRepository,
                skillStateRepository,
                confusionRepository,
                sessionRepository,
                audioPlayer,
                audioInterruptions,
                clock,
            )
    }

    /** Drives [items] answers at [accuracy], collecting the announcement seen on each presented item. */
    private fun drive(
        accuracy: Double,
        seed: Long,
        items: Int,
        startingCadence: Int = 0,
    ): List<Pair<Int, AxisChange>> =
        runBlocking {
            val fixture = Fixture()
            val levels =
                DifficultyAxis.entries.associateWith { 0 } +
                    (DifficultyAxis.CADENCE_FADE to startingCadence)
            // The engine reads *live* levels at generation time, so seeding the repository - not just
            // the start context - is what puts the node genuinely mid-progression.
            fixture.skillStateRepository.update(
                SkillState.initial(SkillIds.M2_DEG_SET_1).copy(axisLevels = levels),
            )
            fixture.engine.start(
                SkillWorkContext(
                    SkillIds.M2_DEG_SET_1,
                    axisLevels = levels,
                    totalAttempts = 0,
                ),
                dueReviews = emptyList(),
                sessionLengthMinutes = 20,
                rootSeed = seed,
                now = Instant.EPOCH,
            )
            val random = Random(seed)
            val seen = mutableListOf<Pair<Int, AxisChange>>()
            var index = 0
            while (!fixture.engine.state.value.isFinished && index < items) {
                val state = fixture.engine.state.value
                val item = state.currentItem ?: break
                state.axisChange?.let { seen += index to it }
                index++
                val correct = random.nextDouble() < accuracy
                val label =
                    if (correct) {
                        item.targetDegree.degree.toString()
                    } else {
                        item.activeDegrees
                            .filterNot { it == item.targetDegree }
                            .random(random)
                            .degree
                            .toString()
                    }
                fixture.engine.submitAnswer(label)
            }
            seen
        }

    @Test
    fun `the scheduled warmup-to-normal transition is announced, not silent`() =
        runBlocking {
            // Two sessions on one fixture, because axis levels are *derived* by replaying the attempt
            // log - seeding a level with no attempts behind it is simply recomputed away. Session one
            // earns a cadence level the honest way; session two is the returning user whose first five
            // items run one level easier (docs/07-ADAPTIVE-ENGINE.md §8) and then jump back at item 6.
            // At CADENCE_FADE 0 there is nothing to announce - `reducedCadenceFade` floors there - so
            // this transition only exists for a user already above the floor, which is the user who
            // reported hearing it.
            val fixture = Fixture()
            val context =
                SkillWorkContext(
                    SkillIds.M2_DEG_SET_1,
                    axisLevels = DifficultyAxis.entries.associateWith { 0 },
                    totalAttempts = 0,
                )

            fixture.engine.start(context, emptyList(), 20, rootSeed = 4242L, now = Instant.EPOCH)
            repeat(30) {
                val item = fixture.engine.state.value.currentItem ?: return@repeat
                fixture.engine.submitAnswer(item.targetDegree.degree.toString())
            }
            fixture.engine.awaitPersistence()
            val earned =
                fixture.skillStateRepository
                    .observe(SkillIds.M2_DEG_SET_1)
                    .first()
                    .axisLevels[DifficultyAxis.CADENCE_FADE] ?: 0
            assertTrue(earned > 0, "session one must actually raise the cadence axis for this to be testable")

            fixture.engine.start(context, emptyList(), 20, rootSeed = 99L, now = Instant.EPOCH)
            val seen = mutableListOf<Pair<Int, AxisChange>>()
            var index = 0
            while (!fixture.engine.state.value.isFinished && index < 12) {
                val state = fixture.engine.state.value
                val item = state.currentItem ?: break
                state.axisChange?.let { seen += index to it }
                index++
                fixture.engine.submitAnswer(item.targetDegree.degree.toString())
            }

            // Located by shape rather than pinned to an exact index: warm-up slots sit at the front of
            // the *composed plan*, and how far in they run depends on how many due reviews
            // `SessionComposer` interleaved ahead of them. What must hold is that leaving the warm-up
            // restores the reference and says so, early, before the session settles.
            val warmupEnd =
                seen.firstOrNull { (_, change) ->
                    change.axis == DifficultyAxis.CADENCE_FADE && change.isIncrease
                }
            assertNotNull(
                warmupEnd,
                "leaving the warm-up restores a level of reference and must be announced. " +
                    "Earned level $earned, saw: $seen",
            )
            assertTrue(
                warmupEnd.first <= WARMUP_ITEM_COUNT,
                "the warm-up is the first $WARMUP_ITEM_COUNT items, so its end must be announced by then - " +
                    "was item ${warmupEnd.first + 1} of $seen",
            )
            assertEquals(
                earned,
                warmupEnd.second.to,
                "the warm-up ends by restoring the node's real level, not some intermediate one",
            )
        }

    @Test
    fun `an announcement is attached only to the item on which the level actually moved`() {
        val seen = drive(accuracy = 1.0, seed = 4242L, items = 12)
        assertTrue(seen.isNotEmpty(), "a fast-improving learner must trip at least one announcement")
        val indices = seen.map { it.first }
        assertEquals(indices.distinct(), indices, "at most one announcement per item - it must not repeat")
        assertTrue(
            seen.all { (_, change) -> change.from != change.to },
            "an announcement must never be emitted for a level that did not move",
        )
    }

    @Test
    fun `a steady session with no level movement stays silent`() {
        // Nothing to announce is the common case; §9.3 asks for a signal on change, not chatter.
        val seen = drive(accuracy = 1.0, seed = 4242L, items = 12)
        assertTrue(
            seen.size < 12,
            "announcements must be the exception, not attached to every item - saw ${seen.size} of 12",
        )
    }

    private companion object {
        /** docs/07-ADAPTIVE-ENGINE.md §8's warm-up length; item indices here are 0-based. */
        const val WARMUP_ITEM_COUNT = 5
    }
}
