package com.tonic.feature.practice.engine

import com.tonic.core.engine.mastery.IndependenceCheck
import com.tonic.core.engine.session.SkillWorkContext
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * docs/09-BUILD-PLAN.md Stage 6: wires generate -> render -> pre-render ->
 * play -> answer -> record -> adapt end to end with no real UI. Uses the
 * real `:core:curriculum`/`:core:engine` pieces throughout - only the
 * repositories are in-memory fakes (Stage 5 already proved the real ones
 * correct against a database) and [FakeAudioPlayer] stands in for actual
 * device audio output, which cannot be exercised in this environment.
 */
class PracticeLoopEngineTest {
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

    private fun freshNode(skillId: com.tonic.core.model.ids.SkillId = SkillIds.M2_DEG_SET_1) =
        SkillWorkContext(skillId, axisLevels = DifficultyAxis.entries.associateWith { 0 }, totalAttempts = 0)

    /** Drives the engine with a responder that answers correctly with probability [accuracy], seeded for determinism. */
    private suspend fun drive(
        fixture: Fixture,
        accuracy: Double,
        seed: Long,
        thinkTimeMs: Long = 0,
    ) {
        val random = Random(seed)
        while (!fixture.engine.state.value.isFinished) {
            val item = fixture.engine.state.value.recognitionItem ?: break
            if (thinkTimeMs > 0) delay(thinkTimeMs)
            val correct = random.nextDouble() < accuracy
            val label =
                if (correct) {
                    item.targetDegree.degree.toString()
                } else {
                    item.activeDegrees
                        .filterNot { it == item.targetDegree }
                        .randomOrNull(random)
                        ?.degree
                        ?.toString()
                        ?: item.targetDegree.degree.toString()
                }
            fixture.engine.submitAnswer(label)
        }
    }

    /**
     * docs/08-UI-SPEC.md §3a. The explanation screen is shown over a session that has already started,
     * so dismissing it lands on a ready item rather than a spinner - but the first item used to *play*
     * underneath it, so the learner heard the chords while still reading the sentence explaining what
     * the chords were for. The explanation and the thing it explains arrived together, which teaches
     * neither. Reported from live use on every module.
     *
     * Held means held: rendered, queued, pre-rendering the next one, and silent.
     */
    @Test
    fun `a held session prepares the first item without playing it`() =
        runBlocking {
            val fixture = Fixture()
            val introOpen = true
            fixture.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 5,
                rootSeed = 1L,
                now = Instant.EPOCH,
                holdPlaybackFor = { introOpen },
            )

            assertNotNull(fixture.engine.state.value.recognitionItem, "the item must still be prepared")
            assertEquals(0, fixture.audioPlayer.playedBuffers.size, "nothing may play while the intro is up")
        }

    /** And the exercise begins on Start: the prepared item plays then, exactly once. */
    @Test
    fun `releasing the hold plays the prepared item once`() =
        runBlocking {
            val fixture = Fixture()
            val introOpen = true
            fixture.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 5,
                rootSeed = 1L,
                now = Instant.EPOCH,
                holdPlaybackFor = { introOpen },
            )
            fixture.engine.releaseHeldPlayback()

            assertEquals(1, fixture.audioPlayer.playedBuffers.size)

            // A second dismiss must not restart it - the hold is lifted, not re-armed.
            fixture.engine.releaseHeldPlayback()
            assertEquals(1, fixture.audioPlayer.playedBuffers.size)
        }

    /** The hold is for the first item only: once lifted, the loop plays normally again. */
    @Test
    fun `later items play normally after the hold is lifted`() =
        runBlocking {
            val fixture = Fixture()
            var introOpen = true
            fixture.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 5,
                rootSeed = 1L,
                now = Instant.EPOCH,
                holdPlaybackFor = { introOpen },
            )
            // Dismissal in the ViewModel closes the intro *and* releases - both, in that order, or the
            // gate would hold the next item for a screen no longer on it.
            introOpen = false
            fixture.engine.releaseHeldPlayback()
            val afterFirst = fixture.audioPlayer.playedBuffers.size

            val item = assertNotNull(fixture.engine.state.value.recognitionItem)
            fixture.engine.submitAnswer(item.targetDegree.canonicalLabel, latencyMs = 100)

            assertTrue(
                fixture.audioPlayer.playedBuffers.size > afterFirst,
                "the second item must play on its own, with no further release",
            )
        }

    @Test
    fun `starting a session generates and plays the first item`() =
        runBlocking {
            val fixture = Fixture()
            fixture.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 5,
                rootSeed = 1L,
                now = Instant.EPOCH,
            )

            val state = fixture.engine.state.value
            assertNotNull(state.recognitionItem)
            assertEquals(1, fixture.audioPlayer.playedBuffers.size)
            assertFalse(state.isFinished)
            // The Summary screen's own `summary/{sessionId}` nav argument (docs/09-BUILD-PLAN.md Stage 9)
            // - must be the real persisted id, not a placeholder, from the moment the session exists.
            val sessionId = assertNotNull(state.sessionId)
            assertEquals(sessionId, fixture.sessionRepository.get(sessionId).id!!)
        }

    @Test
    fun `submitAnswer records exactly one attempt per item and advances to a new item`() =
        runBlocking {
            val fixture = Fixture()
            fixture.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 5,
                rootSeed = 1L,
                now = Instant.EPOCH,
            )
            val firstItem = fixture.engine.state.value.recognitionItem!!

            fixture.engine.submitAnswer(firstItem.targetDegree.degree.toString())

            // Persistence is asynchronous now (docs/04-ARCHITECTURE.md §5 - the loop must not block on
            // it), so assertions about what reached the repository join the write chain explicitly
            // instead of assuming submitAnswer already awaited it.
            fixture.engine.awaitPersistence()
            assertEquals(1, fixture.attemptRepository.all.size)
            assertTrue(
                fixture.attemptRepository.all
                    .single()
                    .correct,
            )
            val secondItem = fixture.engine.state.value.recognitionItem
            assertNotNull(secondItem)
            assertTrue(fixture.audioPlayer.playedBuffers.size >= 2)
        }

    @Test
    fun `a session completes fully - every planned item gets exactly one scored attempt`() =
        runBlocking {
            val fixture = Fixture()
            // ~9 items/minute (SessionComposer) * 6 minutes clears 50 items comfortably.
            fixture.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 6,
                rootSeed = 42L,
                now = Instant.EPOCH,
            )
            val planned = fixture.engine.state.value.itemsPlanned
            assertTrue(planned >= 50, "expected at least 50 planned items for a 6-minute session, got $planned")

            drive(fixture, accuracy = 0.7, seed = 42L)

            assertTrue(fixture.engine.state.value.isFinished)
            assertEquals(planned, fixture.attemptRepository.all.size)
            assertEquals(planned, fixture.engine.state.value.itemsCompleted)
            assertTrue(fixture.attemptRepository.all.none { it.isAbandoned })
        }

    @Test
    fun `a session is fully replayable from rootSeed - same seed and same responses reproduce identical items`() =
        runBlocking {
            val fixtureA = Fixture()
            fixtureA.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 4,
                rootSeed = 777L,
                now = Instant.EPOCH,
            )
            drive(fixtureA, accuracy = 0.65, seed = 999L)

            val fixtureB = Fixture()
            fixtureB.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 4,
                rootSeed = 777L,
                now = Instant.EPOCH,
            )
            drive(fixtureB, accuracy = 0.65, seed = 999L)

            val seedsA = fixtureA.attemptRepository.all.map { it.itemSeed }
            val seedsB = fixtureB.attemptRepository.all.map { it.itemSeed }
            assertEquals(
                seedsA,
                seedsB,
                "the same rootSeed and the same response sequence must generate identical items",
            )

            val targetsA = fixtureA.attemptRepository.all.map { it.targetLabel }
            val targetsB = fixtureB.attemptRepository.all.map { it.targetLabel }
            assertEquals(targetsA, targetsB)
        }

    @Test
    fun `pre-rendering keeps the visible cost of advancing to the next item well under the user's think time`() =
        runBlocking {
            val fixture = Fixture()
            fixture.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 3,
                rootSeed = 5L,
                now = Instant.EPOCH,
            )

            val thinkTimeMs = 30L
            val submitDurationsMs = mutableListOf<Long>()
            var guard = 0
            while (!fixture.engine.state.value.isFinished && guard < 30) {
                val item = fixture.engine.state.value.recognitionItem ?: break
                delay(thinkTimeMs) // simulated user think time - this is when pre-rendering has to finish
                val t0 = System.nanoTime()
                fixture.engine.submitAnswer(item.targetDegree.degree.toString())
                submitDurationsMs += (System.nanoTime() - t0) / 1_000_000
                guard++
            }

            val average = submitDurationsMs.average()
            val worst = submitDurationsMs.max()
            // docs/09-BUILD-PLAN.md Stage 6 says "measure and report it". An assertion message is only
            // emitted on failure, so a green run reported nothing - print it so the number is visible in
            // the test output either way.
            println(
                "[Stage 6 inter-item latency] submitAnswer cost over ${submitDurationsMs.size} items: " +
                    "avg=${"%.2f".format(average)}ms worst=${worst}ms " +
                    "(think-time budget ${thinkTimeMs}ms) per-call=$submitDurationsMs",
            )
            assertTrue(
                average < thinkTimeMs,
                "expected submitAnswer's own cost (average ${average}ms) to stay well under the " +
                    "${thinkTimeMs}ms think-time window that pre-rendering had to work with - " +
                    "measured per-call: $submitDurationsMs",
            )
        }

    @Test
    fun `submitAnswer does not block on persistence - the write lands after it returns, not before`() =
        runBlocking {
            // docs/04-ARCHITECTURE.md §5: "Persist attempts asynchronously and do not block the loop on
            // them." Uses a repository that stalls on write: if submitAnswer still awaited persistence,
            // its own cost would include that stall. The write must still land - just not on this path.
            val fixture = Fixture()
            val slowWriteMs = 300L
            fixture.attemptRepository.writeDelayMs = slowWriteMs
            fixture.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 5,
                rootSeed = 11L,
                now = Instant.EPOCH,
            )
            val item = fixture.engine.state.value.recognitionItem!!

            val t0 = System.nanoTime()
            fixture.engine.submitAnswer(item.targetDegree.degree.toString(), autoAdvance = false)
            val submitMs = (System.nanoTime() - t0) / 1_000_000

            println(
                "[Fix 3] submitAnswer returned in ${submitMs}ms with a ${slowWriteMs}ms database write " +
                    "outstanding (pre-change this call awaited the full write chain)",
            )
            assertTrue(
                submitMs < slowWriteMs,
                "submitAnswer took ${submitMs}ms with a ${slowWriteMs}ms write in flight - it is still blocking on persistence",
            )

            // Not dropped: the write completes, it just isn't on the user-facing path.
            fixture.engine.awaitPersistence()
            assertEquals(1, fixture.attemptRepository.all.size, "the attempt must still be persisted, just later")
        }

    @Test
    fun `the next item's difficulty still reflects the attempt just recorded`() =
        runBlocking {
            // The risk in making persistence async: axis levels for the next item are read back from
            // skill state, which is rebuilt from the attempt log. renderNext() joins the write chain
            // before that read, so the ordering guarantee holds even though submitAnswer no longer waits.
            val fixture = Fixture()
            fixture.attemptRepository.writeDelayMs = 40
            fixture.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 10,
                rootSeed = 21L,
                now = Instant.EPOCH,
            )

            drive(fixture, accuracy = 1.0, seed = 21L)
            fixture.engine.awaitPersistence()

            val recorded = fixture.attemptRepository.all.filterNot { it.isAbandoned }
            val state = fixture.skillStateRepository.observe(SkillIds.M2_DEG_SET_1).first()
            assertEquals(
                recorded.size,
                state.totalAttempts,
                "every recorded attempt must be reflected in the rebuilt skill state - no write was lost or reordered",
            )
        }

    @Test
    fun `abandoning the current item discards it - recorded as abandoned, never scored or adapted`() =
        runBlocking {
            val fixture = Fixture()
            fixture.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 5,
                rootSeed = 1L,
                now = Instant.EPOCH,
            )

            fixture.engine.abandonCurrentItem()

            val recorded = fixture.attemptRepository.all.single()
            assertTrue(recorded.isAbandoned)
            assertNull(recorded.responseLabel)
            assertFalse(recorded.correct)
            assertEquals(1, fixture.audioPlayer.stopCount)

            // Never adapted: the skill state is still exactly SkillState.initial(), as if this attempt
            // had never happened.
            val state = fixture.skillStateRepository.observe(SkillIds.M2_DEG_SET_1)
            assertEquals(MasteryState.LOCKED, state.first().masteryState)
            assertEquals(0, state.first().totalAttempts)
        }

    @Test
    fun `replay increments the replay count on the eventual attempt without recording its own attempt`() =
        runBlocking {
            val fixture = Fixture()
            fixture.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 5,
                rootSeed = 1L,
                now = Instant.EPOCH,
            )
            val item = fixture.engine.state.value.recognitionItem!!

            fixture.engine.replay()
            fixture.engine.replay()
            assertTrue(fixture.attemptRepository.all.isEmpty(), "replay must not record an attempt")
            assertEquals(3, fixture.audioPlayer.playedBuffers.size, "1 auto-play on arrival + 2 replays")

            fixture.engine.submitAnswer(item.targetDegree.degree.toString())
            fixture.engine.awaitPersistence()
            assertEquals(
                2,
                fixture.attemptRepository.all
                    .single()
                    .replayCount,
            )
        }

    @Test
    fun `submitAnswer with autoAdvance=false does not advance until proceedToNextItem is called`() =
        runBlocking {
            val fixture = Fixture()
            fixture.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 5,
                rootSeed = 1L,
                now = Instant.EPOCH,
            )
            val item = fixture.engine.state.value.recognitionItem!!
            val wrongLabel =
                item.activeDegrees
                    .first { it != item.targetDegree }
                    .degree
                    .toString()

            fixture.engine.submitAnswer(wrongLabel, autoAdvance = false)

            fixture.engine.awaitPersistence()
            assertEquals(1, fixture.attemptRepository.all.size, "the attempt is still recorded, just not synchronously")
            assertEquals(
                item,
                fixture.engine.state.value.recognitionItem,
                "must not have advanced past the answered item",
            )

            fixture.engine.proceedToNextItem()
            assertTrue(
                fixture.engine.state.value.recognitionItem != item || fixture.engine.state.value.isFinished,
                "proceedToNextItem should now advance",
            )
        }

    @Test
    fun `playIncorrectContrast plays target-in-context, chosen note, then target again - not via replayCount`() =
        runBlocking {
            val fixture = Fixture()
            fixture.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 5,
                rootSeed = 1L,
                now = Instant.EPOCH,
            )
            val item = fixture.engine.state.value.recognitionItem!!
            val wrongDegree = item.activeDegrees.first { it != item.targetDegree }
            val wrongLabel = wrongDegree.degree.toString()

            fixture.engine.submitAnswer(wrongLabel, autoAdvance = false)
            val buffersBefore = fixture.audioPlayer.playedBuffers.size
            fixture.engine.playIncorrectContrast(wrongLabel)
            fixture.engine.awaitPersistence()

            assertEquals(
                buffersBefore + 3,
                fixture.audioPlayer.playedBuffers.size,
                "target-in-context, chosen note, target again - three plays",
            )
            assertEquals(
                0,
                fixture.attemptRepository.all
                    .single()
                    .replayCount,
                "a system-triggered contrast replay must not be counted as a user replay",
            )

            fixture.engine.proceedToNextItem()
            assertTrue(fixture.engine.state.value.recognitionItem != item)
        }

    @Test
    fun `mastering a node unlocks its successor, seeded from its axis levels with cadence fade reduced one step`() =
        runBlocking {
            val fixture = Fixture()
            fixture.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 90,
                rootSeed = 100L,
                now = Instant.EPOCH,
            )

            drive(fixture, accuracy = 0.95, seed = 100L)

            val degSet1 = fixture.skillStateRepository.observe(SkillIds.M2_DEG_SET_1).first()
            assertEquals(
                MasteryState.MASTERED,
                degSet1.masteryState,
                "expected mastery within a 90-minute session at 95% accuracy",
            )

            val degSet2 = fixture.skillStateRepository.observe(SkillIds.M2_DEG_SET_2).first()
            assertEquals(MasteryState.AVAILABLE, degSet2.masteryState)
            val expectedCadence = (degSet1.axisLevels.getValue(DifficultyAxis.CADENCE_FADE) - 1).coerceAtLeast(0)
            assertEquals(expectedCadence, degSet2.axisLevels[DifficultyAxis.CADENCE_FADE])
        }

    /**
     * The success path end to end through the *real* loop: mastery of `M2_FULL_DIATONIC` automatically
     * queues `M2.INDEPENDENCE_CHECK` (docs/03-CURRICULUM.md §5.6) and a learner who is genuinely
     * accurate with no harmonic reference passes it.
     *
     * The mirror case — a cadence-dependent learner who must *not* be certified — is
     * docs/10-TESTING.md §5's simulation 6 and is covered by
     * `com.tonic.core.engine.simulation.CadenceDependentLearnerSimulationTest`, confirmed still passing
     * under the corrected pacing of docs/07-ADAPTIVE-ENGINE.md §2a. It is deliberately not duplicated
     * here: with CADENCE_FADE stepping one level at a time, such a learner never reaches mastery through
     * this loop at all, because the staircase keeps probing above their threshold and the trailing
     * 30-attempt window never clears the 90% bar while the level is >= 4. That is mastery criterion 5
     * working as intended, not a gap in coverage.
     */
    @Test
    fun `a competent learner masters M2_FULL_DIATONIC, triggers the independence check, and passes it`() =
        runBlocking {
            val fixture = Fixture()
            fixture.engine.start(
                freshNode(SkillIds.M2_FULL_DIATONIC),
                dueReviews = emptyList(),
                sessionLengthMinutes = 200,
                rootSeed = 600L,
                now = Instant.EPOCH,
            )

            val random = Random(600L)
            var guard = 0
            var independenceCheckSeen = false
            var cadenceBeforeCheck: Int? = null
            while (!fixture.engine.state.value.isFinished && guard < 3000) {
                val state = fixture.engine.state.value
                val item = state.recognitionItem ?: break
                if (state.isIndependenceCheckProbe) {
                    if (!independenceCheckSeen) {
                        cadenceBeforeCheck =
                            fixture.skillStateRepository
                                .observe(SkillIds.M2_FULL_DIATONIC)
                                .first()
                                .axisLevels[DifficultyAxis.CADENCE_FADE]
                    }
                    independenceCheckSeen = true
                }
                // Accurate at every cadence level, reference or none - the profile the check exists to
                // certify. Contrast the cadence-dependent responder in simulation 6.
                val correct = random.nextDouble() < COMPETENT_ACCURACY
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
                guard++
            }
            fixture.engine.awaitPersistence()

            assertTrue(
                independenceCheckSeen,
                "expected the independence check to run automatically once M2_FULL_DIATONIC was mastered",
            )

            val finalState = fixture.skillStateRepository.observe(SkillIds.M2_FULL_DIATONIC).first()
            assertEquals(MasteryState.MASTERED, finalState.masteryState)
            assertEquals(
                DifficultyAxis.CADENCE_FADE.maxLevel,
                cadenceBeforeCheck,
                "a learner accurate at every level rides CADENCE_FADE to its ceiling before the check runs",
            )

            val probes = fixture.attemptRepository.all.filter { it.isIndependenceCheckProbe }
            assertEquals(
                IndependenceCheck.REQUIRED_ITEMS,
                probes.size,
                "the check is a fixed-size block - docs/03-CURRICULUM.md §5.6",
            )
            assertTrue(
                probes.all { it.cadenceFadeLevel == INDEPENDENCE_CHECK_CADENCE_LEVEL },
                "every probe must run with no harmonic reference at all, whatever the node settled at",
            )
            val accuracy = probes.count { it.correct }.toDouble() / probes.size
            assertTrue(
                accuracy >= IndependenceCheck.PASS_THRESHOLD,
                "expected a passing block, measured $accuracy",
            )
            assertEquals(
                DifficultyAxis.CADENCE_FADE.maxLevel,
                finalState.axisLevels[DifficultyAxis.CADENCE_FADE],
                "passing must leave the fade axis alone - only a failure steps it down",
            )
        }

    private companion object {
        /** Accurate regardless of how much reference is playing - see the test's own KDoc. */
        const val COMPETENT_ACCURACY = 0.95

        /** docs/03-CURRICULUM.md §5.6: the check always runs at CADENCE_FADE L6, no reference at all. */
        const val INDEPENDENCE_CHECK_CADENCE_LEVEL = 6
    }
}
