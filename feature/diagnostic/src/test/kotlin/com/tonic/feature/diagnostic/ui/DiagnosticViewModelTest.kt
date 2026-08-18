package com.tonic.feature.diagnostic.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.items.Item
import com.tonic.core.model.time.Clock
import com.tonic.feature.diagnostic.engine.DiagnosticLoopEngine
import com.tonic.feature.diagnostic.engine.FakeAudioPlayer
import com.tonic.feature.diagnostic.engine.FakeDiagnosticRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Robolectric (viewModelScope needs a real Main dispatcher - same reasoning as
 * `:feature:practice`'s `PracticeViewModelTest`) and real time (Main mapped to `Dispatchers.Default`,
 * not a virtual-time TestDispatcher - `DiagnosticLoopEngine` has no background dispatcher of its own,
 * unlike Stage 6/7's practice loop, but real time keeps this consistent with that precedent regardless).
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Fixture {
        val audioPlayer = FakeAudioPlayer()
        val diagnosticRepository = FakeDiagnosticRepository()
        val clock = Clock { Instant.EPOCH }
        val engine = DiagnosticLoopEngine(audioPlayer, diagnosticRepository, clock)
        val viewModel = DiagnosticViewModel(engine)

        suspend fun awaitInputEnabled(): DiagnosticUiState =
            withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.inputEnabled } }

        /**
         * [DiagnosticViewModel.onAnswerSelected] is fire-and-forget (`viewModelScope.launch {}`, the
         * right shape for a real click handler) - waiting only for "inputEnabled" again after calling
         * it is a stale-read race, since [uiState] can still show the *previous* item's already-true
         * `inputEnabled` for a moment before that launched coroutine has actually run and flipped it to
         * false. Tracking the last item actually answered and waiting for a *different* one closes that
         * window - the same fix `:feature:practice`'s Stage 7 tests needed for the same reason.
         */
        suspend fun answerCorrectlyUntilFinished() {
            withTimeout(TIMEOUT_MS) {
                var lastAnswered: Item? = null
                while (true) {
                    val state =
                        viewModel.uiState.first {
                            it.isFinished ||
                                (it.inputEnabled && it.currentItem != null && it.currentItem != lastAnswered)
                        }
                    if (state.isFinished) return@withTimeout
                    val item = state.currentItem!!
                    lastAnswered = item
                    viewModel.onAnswerSelected(correctLabel(item))
                }
            }
        }

        companion object {
            const val TIMEOUT_MS = 15_000L
        }
    }

    private companion object {
        fun correctLabel(item: Item): String =
            when (item) {
                is Item.PitchDirectionItem ->
                    if (item.secondCentsOffset >
                        0
                    ) {
                        AnswerAlphabet.HigherLower.HIGHER
                    } else {
                        AnswerAlphabet.HigherLower.LOWER
                    }
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
                else -> error("unexpected item type: ${item::class.simpleName}")
            }
    }

    @Test
    fun `before begin, the screen shows the intro state and has not started the engine`() =
        runBlocking {
            val fixture = Fixture()
            assertFalse(fixture.viewModel.uiState.value.hasStarted)
            assertTrue(fixture.diagnosticRepository.saved.isEmpty())
        }

    @Test
    fun `begin starts the engine and the first item becomes answerable`() =
        runBlocking {
            val fixture = Fixture()
            fixture.viewModel.begin()

            val state = fixture.awaitInputEnabled()
            assertTrue(state.hasStarted)
            assertNotNull(state.currentItem)
            assertNotNull(state.currentSubTest)
            Unit
        }

    @Test
    fun `begin is idempotent - calling it twice does not start a second run`() =
        runBlocking {
            val fixture = Fixture()
            fixture.viewModel.begin()
            fixture.awaitInputEnabled()

            fixture.viewModel.begin()
            fixture.answerCorrectlyUntilFinished()

            assertEquals(1, fixture.diagnosticRepository.saved.size)
        }

    @Test
    fun `a high-accuracy run finishes with a PROCEED_TO_PRACTICE outcome`() =
        runBlocking {
            val fixture = Fixture()
            fixture.viewModel.begin()
            fixture.answerCorrectlyUntilFinished()

            val state = fixture.viewModel.uiState.value
            assertTrue(state.isFinished)
            assertEquals(DiagnosticOutcome.PROCEED_TO_PRACTICE, state.outcome)
        }

    @Test
    fun `replay plays the current item again without advancing`() =
        runBlocking {
            val fixture = Fixture()
            fixture.viewModel.begin()
            fixture.awaitInputEnabled()
            val itemBefore = fixture.viewModel.uiState.value.currentItem
            val buffersBefore = fixture.audioPlayer.playedBuffers.size

            fixture.viewModel.onReplay()
            withTimeout(Fixture.TIMEOUT_MS) {
                while (fixture.audioPlayer.playedBuffers.size == buffersBefore) delay(10)
            }

            assertEquals(buffersBefore + 1, fixture.audioPlayer.playedBuffers.size)
            assertEquals(itemBefore, fixture.viewModel.uiState.value.currentItem)
        }
}
