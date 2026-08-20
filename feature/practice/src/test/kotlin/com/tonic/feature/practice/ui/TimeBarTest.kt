package com.tonic.feature.practice.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.model.state.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * The practice bar is a time bar, not an item counter — reported from live use: "the timer still
 * continues to not move. This needs to be timed from the time I start the practice." It fills with
 * wall-clock time from session start toward the configured session length, independent of how many
 * items were answered. Real (short) delays rather than virtual time, for the same cross-dispatcher
 * reason as [PracticeViewModelTest]'s header note.
 */
@RunWith(AndroidJUnit4::class)
class TimeBarTest {
    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @After fun tearDown() = Dispatchers.resetMain()

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }

    @Test
    fun `the bar advances with the wall clock while no items are answered, and caps at full`() =
        runBlocking {
            val fixture = PracticeFixture(AppSettings(module2IntroSeen = true, sessionLengthMinutes = 5))
            fixture.viewModel.startIfNeeded()
            withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.item != null } }
            assertEquals(0f, fixture.viewModel.uiState.value.timeFraction, "nothing elapsed yet")

            // Half the 5-minute budget passes with the user just sitting on the first item.
            fixture.now = fixture.now.plusSeconds(150)
            val halfway =
                withTimeout(TIMEOUT_MS) {
                    fixture.viewModel.uiState
                        .first { it.timeFraction > 0.49f }
                        .timeFraction
                }
            assertEquals(0.5f, halfway, absoluteTolerance = 0.02f)

            // Past the end: the bar reads full - never beyond - even before an item boundary ends the session.
            fixture.now = fixture.now.plusSeconds(600)
            withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.timeFraction == 1f } }
            Unit
        }
}
