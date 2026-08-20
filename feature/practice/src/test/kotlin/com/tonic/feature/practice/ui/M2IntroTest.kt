package com.tonic.feature.practice.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.CadenceFadeLevel
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * docs/11-ONBOARDING-CLARITY.md §3 and §5 — the Module 2 explanation screen and its worked example.
 * Robolectric for the same reason every other ViewModel test here needs it: `viewModelScope` wants a
 * real Main dispatcher.
 */
@RunWith(AndroidJUnit4::class)
class M2IntroTest {
    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @After fun tearDown() = Dispatchers.resetMain()

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }

    @Test
    fun `the explanation is shown automatically before the first practice item, on a fresh install`() =
        runBlocking {
            val fixture = PracticeFixture(AppSettings(module2IntroSeen = false))
            fixture.viewModel.startIfNeeded()
            val state = withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.showIntro } }
            assertTrue(state.showIntro)
            assertFalse(state.introAnswerRevealed, "the answer must not be visible before it's asked for")
        }

    @Test
    fun `it is never shown automatically once it has been seen`() =
        runBlocking {
            val fixture = PracticeFixture(AppSettings(module2IntroSeen = true))
            fixture.viewModel.startIfNeeded()
            // Wait for the loop to actually produce an item, so this isn't just observing an early frame.
            withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.item != null } }
            assertFalse(fixture.viewModel.uiState.value.showIntro, "§5: never shown again automatically")
        }

    @Test
    fun `dismissing it records that it was seen, so the next launch goes straight to practice`() =
        runBlocking {
            val fixture = PracticeFixture(AppSettings(module2IntroSeen = false))
            fixture.viewModel.startIfNeeded()
            withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.showIntro } }

            fixture.viewModel.onIntroDismissed()

            withTimeout(TIMEOUT_MS) {
                fixture.settingsRepository.settings.first { it.module2IntroSeen }
            }
            assertFalse(fixture.viewModel.uiState.value.showIntro)
        }

    @Test
    fun `the help affordance re-opens the full explanation without un-recording that it was seen`() =
        runBlocking {
            val fixture = PracticeFixture(AppSettings(module2IntroSeen = true))
            fixture.viewModel.startIfNeeded()
            withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.item != null } }

            fixture.viewModel.onOpenIntro()

            assertTrue(fixture.viewModel.uiState.value.showIntro, "§5: always reachable on demand")
            assertFalse(
                fixture.viewModel.uiState.value.introAnswerRevealed,
                "recalling it starts from the same place, not from the answer",
            )
            assertTrue(
                fixture.settingsRepository.settings
                    .first()
                    .module2IntroSeen,
                "recalling it must not reset the flag - that would make it auto-show again",
            )
        }

    @Test
    fun `the answer is revealed only when asked for, and matches the example actually played`() =
        runBlocking {
            val fixture = PracticeFixture(AppSettings(module2IntroSeen = false))
            fixture.viewModel.startIfNeeded()
            withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.showIntro } }

            assertFalse(fixture.viewModel.uiState.value.introAnswerRevealed)
            fixture.viewModel.onRevealWorkedExampleAnswer()
            assertTrue(fixture.viewModel.uiState.value.introAnswerRevealed)

            val example = WorkedExample.generate()
            assertEquals(
                example.targetDegree.degree.toString(),
                fixture.viewModel.workedExampleAnswer,
                "the revealed answer is read off the real generated item, never hardcoded",
            )
        }

    @Test
    fun `playing the example goes through the real audio path`() =
        runBlocking {
            val fixture = PracticeFixture(AppSettings(module2IntroSeen = false))
            fixture.viewModel.startIfNeeded()
            withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.showIntro } }
            val before = fixture.audioPlayer.playedBuffers.size

            fixture.viewModel.onPlayWorkedExample()

            withTimeout(TIMEOUT_MS) {
                while (fixture.audioPlayer.playedBuffers.size <= before) kotlinx.coroutines.yield()
            }
            assertTrue(
                fixture.audioPlayer.playedBuffers
                    .last()
                    .durationMs > 0,
                "§3 requires the example in real audio, not a description of it",
            )
        }

    /**
     * The example is a genuine generated item at floor difficulty, so what the screen demonstrates is
     * exactly the mechanic the first real item will use - a hand-built mock would be free to drift.
     */
    @Test
    fun `the worked example is a real item at the easiest settings, identical every time`() {
        val first = WorkedExample.generate()
        val second = WorkedExample.generate()
        assertEquals(first.seed, second.seed, "every user sees the same example")
        assertEquals(first.targetDegree, second.targetDegree)
        assertEquals(SkillIds.M2_DEG_SET_1, first.skill)
        assertEquals(
            CadenceFadeLevel.L0,
            first.referencePlan.cadenceFadeLevel,
            "the example must use the fullest reference the app has, not a faded one",
        )
        assertTrue(
            first.activeDegrees.map { it.degree }.containsAll(listOf(1, 3, 5)),
            "it must demonstrate the same three buttons the first real item offers",
        )
    }
}
