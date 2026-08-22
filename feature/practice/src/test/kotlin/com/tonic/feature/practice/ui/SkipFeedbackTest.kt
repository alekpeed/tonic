package com.tonic.feature.practice.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.model.state.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * docs/08-UI-SPEC.md §2a: a control that claims to advance must give immediate visible confirmation.
 * Skip always advanced correctly through abandonCurrentItem(), but gave zero acknowledgment — and a
 * skip into a similar-sounding item is indistinguishable from the button doing nothing.
 */
@RunWith(AndroidJUnit4::class)
class SkipFeedbackTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }

    @Test
    fun `skip acknowledges immediately, presents a genuinely new item, and the notice clears itself`() =
        runBlocking {
            val fixture = PracticeFixture(AppSettings(module2IntroSeen = true))
            fixture.startPastIntro(TIMEOUT_MS)
            val before = fixture.viewModel.uiState.value.item!!

            fixture.viewModel.onSkip()

            val acknowledged =
                withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.skipAcknowledged } }
            assertTrue(acknowledged.skipAcknowledged, "the press must visibly do something at once")

            val after =
                withTimeout(TIMEOUT_MS) {
                    fixture.viewModel.uiState.first { it.item != null && it.item?.seed != before.seed }
                }
            assertNotNull(after.item)
            assertNotEquals(before.seed, after.item?.seed, "skip must land on a genuinely new item")

            withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { !it.skipAcknowledged } }
            fixture.engine.awaitPersistence()
            assertTrue(
                fixture.attemptRepository.all
                    .single()
                    .isAbandoned,
                "the skipped item is recorded abandoned, never scored",
            )
        }
}
