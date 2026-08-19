package com.tonic.feature.diagnostic.engine

import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.items.Item
import com.tonic.core.model.state.EntryPoint
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * docs/09-BUILD-PLAN.md Stage 8: wires generate -> render -> play -> answer -> adapt -> advance across
 * all four M0 sub-tests, headless. Uses the real `:core:curriculum`/`:core:engine` pieces throughout -
 * only [FakeAudioPlayer] (no device in this environment) and [FakeDiagnosticRepository] are fakes.
 */
class DiagnosticLoopEngineTest {
    private class Fixture {
        val audioPlayer = FakeAudioPlayer()
        val diagnosticRepository = FakeDiagnosticRepository()
        val skillStateRepository = FakeSkillStateRepository()
        val settingsRepository = FakeSettingsRepository()
        val clock = Clock { Instant.EPOCH }
        val engine =
            DiagnosticLoopEngine(audioPlayer, diagnosticRepository, skillStateRepository, settingsRepository, clock)
    }

    /** Answers correctly with probability [accuracy], seeded for determinism, until the run finishes. */
    private suspend fun drive(
        fixture: Fixture,
        accuracy: Double,
        seed: Long,
    ) {
        val random = Random(seed)
        while (true) {
            val state = fixture.engine.state.first { it.inputEnabled || it.isFinished }
            if (state.isFinished) return
            val item = state.currentItem!!
            val correct = random.nextDouble() < accuracy
            fixture.engine.submitAnswer(correctLabel(item).let { if (correct) it else wrongLabel(item) })
        }
    }

    private fun correctLabel(item: Item): String =
        when (item) {
            is Item.PitchDirectionItem ->
                if (item.secondCentsOffset > 0) AnswerAlphabet.HigherLower.HIGHER else AnswerAlphabet.HigherLower.LOWER
            is Item.SameDifferentItem ->
                if (item.isCatchTrial) AnswerAlphabet.SameDifferent.SAME else AnswerAlphabet.SameDifferent.DIFFERENT
            is Item.TonalMemoryItem ->
                if (item.alteredIndex ==
                    null
                ) {
                    AnswerAlphabet.SameDifferent.SAME
                } else {
                    AnswerAlphabet.SameDifferent.DIFFERENT
                }
            is Item.AmusiaScreenItem ->
                if (item.isAltered) AnswerAlphabet.IntactAltered.ALTERED else AnswerAlphabet.IntactAltered.INTACT
            else -> error("unexpected item type in M0 diagnostic: ${item::class.simpleName}")
        }

    private fun wrongLabel(item: Item): String = item.answerAlphabet.labels.first { it != correctLabel(item) }

    @Test
    fun `a typical responder completes the diagnostic and is placed into M2_STAGE_1`() =
        runBlocking {
            val fixture = Fixture()
            val run = async { fixture.engine.start(rootSeed = 42L) }
            drive(fixture, accuracy = 0.9, seed = 42L)
            run.await()

            val state = fixture.engine.state.value
            assertTrue(state.isFinished)
            val result = assertNotNull(state.result)
            assertEquals(EntryPoint.M2_STAGE_1, result.recommendedEntry)
            assertFalse(result.amusiaIndicatorFlag)
            assertEquals(1, fixture.diagnosticRepository.saved.size)
            assertEquals(result, fixture.diagnosticRepository.saved.single())

            val placedState = assertNotNull(fixture.skillStateRepository.get(SkillIds.M2_DEG_SET_1))
            assertEquals(MasteryState.AVAILABLE, placedState.masteryState)
            assertEquals(result.initialAxisLevels, placedState.axisLevels)
            assertTrue(fixture.settingsRepository.settings.value.diagnosticCompleted)
        }

    @Test
    fun `a typical responder's run fits comfortably under the 6-minute budget by audio content duration`() =
        runBlocking {
            val fixture = Fixture()
            val run = async { fixture.engine.start(rootSeed = 7L) }
            drive(fixture, accuracy = 0.9, seed = 7L)
            run.await()

            val totalAudioMs = fixture.audioPlayer.playedBuffers.sumOf { it.durationMs }
            assertTrue(
                totalAudioMs < 6 * 60 * 1000,
                "total played audio duration was ${totalAudioMs}ms, over the 6-minute budget",
            )
        }

    @Test
    fun `a poor responder on both pitch direction and the amusia screen is flagged and routed to M1 remediation`() =
        runBlocking {
            val fixture = Fixture()
            val run = async { fixture.engine.start(rootSeed = 99L) }
            // Answers randomly (50/50 on a binary choice) throughout - chance-level performance on
            // every sub-test, which should elevate the pitch-direction threshold past 200 cents and
            // drive the amusia screen's d-prime toward zero.
            drive(fixture, accuracy = 0.5, seed = 99L)
            run.await()

            val result = assertNotNull(fixture.engine.state.value.result)
            assertEquals(EntryPoint.M1_REMEDIATION, result.recommendedEntry)

            // M1 remediation content is out of Phase 1 scope (CLAUDE.md §2) - M2_DEG_SET_1 must still be
            // unlocked at the all-zero default, or Home would have nothing practiceable at all.
            val placedState = assertNotNull(fixture.skillStateRepository.get(SkillIds.M2_DEG_SET_1))
            assertEquals(MasteryState.AVAILABLE, placedState.masteryState)
            assertTrue(placedState.axisLevels.values.all { it == 0 })
            assertTrue(fixture.settingsRepository.settings.value.diagnosticCompleted)
        }

    @Test
    fun `replaying the same rootSeed and responses reproduces an identical result - determinism is not optional`() =
        runBlocking {
            val fixtureA = Fixture()
            val runA = async { fixtureA.engine.start(rootSeed = 555L) }
            drive(fixtureA, accuracy = 0.85, seed = 123L)
            runA.await()

            val fixtureB = Fixture()
            val runB = async { fixtureB.engine.start(rootSeed = 555L) }
            drive(fixtureB, accuracy = 0.85, seed = 123L)
            runB.await()

            val resultA = fixtureA.engine.state.value.result!!
            val resultB = fixtureB.engine.state.value.result!!
            assertEquals(resultA.copy(completedAt = Instant.EPOCH), resultB.copy(completedAt = Instant.EPOCH))
        }

    @Test
    fun `replay plays the current item again without recording an answer`() =
        runBlocking {
            val fixture = Fixture()
            val run = async { fixture.engine.start(rootSeed = 1L) }
            fixture.engine.state.first { it.inputEnabled }
            val buffersBefore = fixture.audioPlayer.playedBuffers.size

            fixture.engine.replay()
            assertEquals(buffersBefore + 1, fixture.audioPlayer.playedBuffers.size)

            drive(fixture, accuracy = 1.0, seed = 1L)
            run.await()
            assertTrue(fixture.engine.state.value.isFinished)
        }
}
