package com.tonic.feature.diagnostic.engine

import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.items.Item
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression guard for a freeze on the live M0 screen. The engine used to publish `inputEnabled =
 * true` *before* installing the receiver that [DiagnosticLoopEngine.submitAnswer] completes. An
 * answer arriving in that window found the previous item's already-completed receiver, was dropped,
 * and left the diagnostic with input disabled and no next item — stuck for good, with the only exit
 * being to kill the app.
 *
 * The window opens the instant an item's audio ends, which is exactly when a quick user taps, so this
 * drives the engine from a *different* dispatcher than the one running the run loop and answers with
 * no delay at all. It is a race, so it is probabilistic by nature: before the ordering fix this hung
 * until the timeout, reliably enough to fail the existing Stage 8 ViewModel tests on ordinary runs.
 */
class AnswerHandoffTest {
    private companion object {
        // Generous on purpose. This drives two genuinely concurrent dispatchers to reach a
        // microseconds-wide handoff window, so it is sensitive to CPU contention rather than to the
        // behavior under test - it timed out during a full parallel `gradle build` while passing every
        // run in isolation. A too-tight bound here reports scheduler starvation as a product bug.
        const val TIMEOUT_MS = 120_000L
    }

    private fun anyLabel(item: Item): String = item.answerAlphabet.labels.first()

    @Test
    fun `answering the instant input opens is accepted, and the run reaches its result`() =
        runBlocking {
            val engine =
                DiagnosticLoopEngine(
                    FakeAudioPlayer(),
                    FakeDiagnosticRepository(),
                    FakeSkillStateRepository(),
                    FakeSettingsRepository(),
                    Clock { Instant.EPOCH },
                )

            withTimeout(TIMEOUT_MS) {
                // The run loop and the answerer are genuinely concurrent here, unlike the single-threaded
                // Stage 8 engine tests - that separation is what makes the handoff window reachable.
                val run = async(Dispatchers.Default) { engine.start(rootSeed = 7L) }

                var answered = 0
                var previous: Item? = null
                while (true) {
                    val state =
                        engine.state.first {
                            it.isFinished ||
                                (it.inputEnabled && it.currentItem != null && it.currentItem != previous)
                        }
                    if (state.isFinished) break
                    previous = state.currentItem
                    // No delay, no yield: submit in the same breath as the state that enabled input.
                    engine.submitAnswer(anyLabel(state.currentItem!!))
                    answered++
                }
                run.await()

                assertTrue(answered > 0, "the run never presented an answerable item")
                assertTrue(
                    engine.state.value.isFinished,
                    "a dropped answer strands the run: input disabled, no next item, no result",
                )
            }
        }

    /** The same handoff, but every answer is the deliberately wrong one — the abandoned/incorrect path must not stall either. */
    @Test
    fun `a consistently incorrect responder also reaches a result without stalling`() =
        runBlocking {
            val engine =
                DiagnosticLoopEngine(
                    FakeAudioPlayer(),
                    FakeDiagnosticRepository(),
                    FakeSkillStateRepository(),
                    FakeSettingsRepository(),
                    Clock { Instant.EPOCH },
                )

            withTimeout(TIMEOUT_MS) {
                val run = async(Dispatchers.Default) { engine.start(rootSeed = 11L) }
                var previous: Item? = null
                while (true) {
                    val state =
                        engine.state.first {
                            it.isFinished ||
                                (it.inputEnabled && it.currentItem != null && it.currentItem != previous)
                        }
                    if (state.isFinished) break
                    previous = state.currentItem
                    engine.submitAnswer(wrongestLabel(state.currentItem!!))
                }
                run.await()
                assertTrue(engine.state.value.isFinished)
            }
        }

    private fun wrongestLabel(item: Item): String {
        val correct =
            when (item) {
                is Item.PitchDirectionItem ->
                    if (item.secondCentsOffset > 0) {
                        AnswerAlphabet.HigherLower.HIGHER
                    } else {
                        AnswerAlphabet.HigherLower.LOWER
                    }
                is Item.SameDifferentItem ->
                    if (item.isCatchTrial) {
                        AnswerAlphabet.SameDifferent.SAME
                    } else {
                        AnswerAlphabet.SameDifferent.DIFFERENT
                    }
                is Item.TonalMemoryItem ->
                    if (item.alteredIndex == null) {
                        AnswerAlphabet.SameDifferent.SAME
                    } else {
                        AnswerAlphabet.SameDifferent.DIFFERENT
                    }
                is Item.AmusiaScreenItem ->
                    if (item.isAltered) AnswerAlphabet.IntactAltered.ALTERED else AnswerAlphabet.IntactAltered.INTACT
                else -> error("unexpected item type in M0 diagnostic: ${item::class.simpleName}")
            }
        return item.answerAlphabet.labels.first { it != correct }
    }
}
