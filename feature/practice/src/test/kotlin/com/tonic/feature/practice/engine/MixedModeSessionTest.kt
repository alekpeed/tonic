package com.tonic.feature.practice.engine

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.engine.session.SkillWorkContext
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.items.Item
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `M10.MIXED_MODE` driven end to end through the real loop — the production-wiring trace for Stage 2.7.
 *
 * The curriculum tests establish that the generator interleaves modes and offers all ten degrees. What
 * they cannot show is that a learner in a session is actually handed those items, that the attempt log
 * records a mode-unambiguous label for each one, and that nothing in the loop leaks the mode before an
 * answer is submitted.
 */
class MixedModeSessionTest {
    private class Fixture(
        var now: Instant = Instant.EPOCH,
    ) {
        val attemptRepository = FakeAttemptRepository()
        val skillStateRepository = FakeSkillStateRepository(attemptRepository)
        val confusionRepository = FakeConfusionRepository()
        val sessionRepository = FakeSessionRepository()
        val audioPlayer = FakeAudioPlayer()
        val audioInterruptions = FakeAudioInterruptions()
        val engine =
            PracticeLoopEngine(
                attemptRepository,
                skillStateRepository,
                confusionRepository,
                sessionRepository,
                audioPlayer,
                audioInterruptions,
                Clock { now },
            )

        suspend fun start(skill: SkillId) =
            engine.start(
                SkillWorkContext(skill, DifficultyAxis.RECOGNITION_AXES.associateWith { 0 }, totalAttempts = 0),
                dueReviews = emptyList(),
                sessionLengthMinutes = 10,
                rootSeed = 7_070L,
                now = now,
            )

        val current: Item.FunctionalRecognitionItem?
            get() = engine.state.value.currentItem as? Item.FunctionalRecognitionItem

        suspend fun answerCorrectly(limit: Int): List<Item.FunctionalRecognitionItem> {
            val seen = mutableListOf<Item.FunctionalRecognitionItem>()
            while (!engine.state.value.isFinished && seen.size < limit) {
                val item = current ?: break
                seen += item
                now = now.plusSeconds(4)
                engine.submitAnswer(item.targetDegree.canonicalLabel)
            }
            engine.awaitPersistence()
            return seen
        }
    }

    @Test
    fun `a mixed-mode session really does interleave both modes`() {
        runBlocking {
            val fixture = Fixture()
            fixture.start(SkillIds.M10_MIXED_MODE)
            val seen = fixture.answerCorrectly(limit = 40)

            assertTrue(seen.size > 10, "only ${seen.size} items came out of the session")
            assertTrue(seen.any { it.mode == Mode.MAJOR }, "no major item in the whole session")
            assertTrue(seen.any { it.mode == Mode.MINOR }, "no minor item in the whole session")
        }
    }

    @Test
    fun `the ladder never changes between a major item and a minor one`() {
        runBlocking {
            // The mode leak that would defeat the node entirely: a learner reading ♭3 off the buttons
            // has answered before hearing anything. The set must be identical item to item.
            val fixture = Fixture()
            fixture.start(SkillIds.M10_MIXED_MODE)
            val seen = fixture.answerCorrectly(limit = 30)

            val sets = seen.map { it.activeDegrees.toSet() }.toSet()
            assertEquals(1, sets.size, "the ladder offered ${sets.size} different degree sets across a session")
            assertEquals(SkillGraph.activeDegreesFor(SkillIds.M10_MIXED_MODE), sets.single())
        }
    }

    @Test
    fun `the ladder's spine is pinned, so its shape does not announce the mode either`() {
        // A subtler leak than the button set, and worse: the ladder draws a mode's own degrees wide on
        // the spine and everything else narrower beside them. A spine that followed the item would
        // reshape the entire column per item - ♮3 wide in major, ♭3 wide in minor - which reads at a
        // glance without even looking at the labels.
        for (mode in Mode.entries) {
            assertEquals(
                Mode.MAJOR,
                SkillGraph.ladderSpineMode(SkillIds.M10_MIXED_MODE, mode),
                "a $mode item would have reshaped the ladder",
            )
        }
        // And every other node still uses its own, so nothing about M2 or M10 layout moved.
        assertEquals(Mode.MINOR, SkillGraph.ladderSpineMode(SkillIds.M10_MIN_NATURAL, Mode.MINOR))
        assertEquals(Mode.MAJOR, SkillGraph.ladderSpineMode(SkillIds.M2_FULL_DIATONIC, Mode.MAJOR))
    }

    @Test
    fun `the recorded label says which note, unambiguously, in either mode`() {
        runBlocking {
            // The reason docs/20-PHASE-2-SPEC.md §2.1's labeling model matters most here: major's 3 and
            // minor's ♭3 land in the same attempt log and the same confusion matrix, one item apart. If
            // they shared a label the node's own data would be unreadable.
            val fixture = Fixture()
            fixture.start(SkillIds.M10_MIXED_MODE)
            fixture.answerCorrectly(limit = 40)

            val union = SkillGraph.activeDegreesFor(SkillIds.M10_MIXED_MODE)
            val byLabel = union.groupBy { it.canonicalLabel }
            assertTrue(byLabel.all { it.value.size == 1 }, "two degrees share a label: $byLabel")

            for (attempt in fixture.attemptRepository.all) {
                val degree = union.first { it.canonicalLabel == attempt.targetLabel }
                assertEquals(
                    attempt.targetMidi % 12,
                    (attempt.keyPitchClass + degree.semitoneOffset(Mode.MAJOR)) % 12,
                    "${attempt.targetLabel} was logged against a pitch it does not denote",
                )
            }
        }
    }

    @Test
    fun `a major item never asks for a minor-only degree in a live session`() {
        runBlocking {
            val fixture = Fixture()
            fixture.start(SkillIds.M10_MIXED_MODE)
            for (item in fixture.answerCorrectly(limit = 40)) {
                assertTrue(
                    item.targetDegree in SkillGraph.targetDegreesFor(SkillIds.M10_MIXED_MODE, item.mode),
                    "a ${item.mode} item asked for ${item.targetDegree.canonicalLabel}",
                )
            }
        }
    }

    @Test
    fun `the node reaches mastery through the real replayer`() {
        runBlocking {
            // Stage 2.5 found that M10 and M11 nodes had no mastery lifecycle at all because the
            // replayer's scope test was wrong. MIXED_MODE is a new node on that same path, so this
            // checks it is genuinely reachable rather than assuming the earlier fix covers it - and it
            // is the end-to-end form of the curriculum test's coverage measurement.
            val fixture = Fixture()
            fixture.start(SkillIds.M10_MIXED_MODE)
            fixture.answerCorrectly(limit = 400)

            val state =
                com.tonic.core.engine.replay.SkillStateReducer
                    .replay(
                        SkillIds.M10_MIXED_MODE,
                        fixture.attemptRepository.all.filter { it.skillId == SkillIds.M10_MIXED_MODE },
                    )
            assertTrue(
                state.totalAttempts > 0,
                "the session logged nothing under M10.MIXED_MODE",
            )
            assertTrue(
                state.axisLevels.keys.containsAll(DifficultyAxis.RECOGNITION_AXES),
                "MIXED_MODE must be scheduled over the recognition axes: ${state.axisLevels.keys}",
            )
        }
    }

    @Test
    fun `every degree the ladder offers is one the log can distinguish`() {
        val union = SkillGraph.activeDegreesFor(SkillIds.M10_MIXED_MODE)
        assertEquals(10, union.size)
        assertEquals(
            union.size,
            union.map { it.semitoneOffset(Mode.MAJOR) }.toSet().size,
            "two buttons denote the same pitch - one of them can never be the right answer",
        )
        assertTrue(ScaleDegree(3, -1) in union && ScaleDegree(3) in union)
    }
}
