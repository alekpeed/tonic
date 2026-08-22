package com.tonic.feature.practice.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.CadenceFadeLevel
import com.tonic.core.model.state.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
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
    @get:Rule val mainDispatcher = MainDispatcherRule()

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

    /**
     * The rule the maintainer set after live use, replacing seen-once: entering a module means seeing
     * its explanation, every time. `module2IntroSeen` is deliberately left `true` here — a stale flag
     * from before the change must not suppress anything.
     */
    @Test
    fun `it is shown again on a later visit, even once it has been seen before`() =
        runBlocking {
            val fixture = PracticeFixture(AppSettings(module2IntroSeen = true))
            fixture.viewModel.startIfNeeded()
            withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.showIntro } }
            assertTrue(fixture.viewModel.uiState.value.showIntro)
        }

    /** Dismissing it starts the exercise; it does not spend a one-time allowance, because there is none. */
    @Test
    fun `dismissing it starts practice and does not consume anything`() =
        runBlocking {
            val fixture = PracticeFixture(AppSettings(module2IntroSeen = false))
            fixture.viewModel.startIfNeeded()
            withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.showIntro } }

            fixture.viewModel.onIntroDismissed()

            withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.item != null && !it.showIntro } }
            assertFalse(fixture.viewModel.uiState.value.showIntro)
        }

    /** Within one visit it does not come back between items - only leaving and returning counts as entering. */
    @Test
    fun `it does not reappear on later items of a module already entered`() =
        runBlocking {
            val fixture = PracticeFixture(AppSettings())
            fixture.viewModel.startIfNeeded()
            val first = withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.showIntro } }
            assertEquals(IntroKind.M2, first.introKind)
            fixture.viewModel.onIntroDismissed()

            val item =
                withTimeout(TIMEOUT_MS) {
                    fixture.viewModel.uiState.first { it.item != null && it.inputEnabled }
                }
            val answered = item.recognitionItem!!
            fixture.viewModel.onDegreeSelected(answered.targetDegree)
            withTimeout(TIMEOUT_MS) {
                fixture.viewModel.uiState.first { it.item != null && it.item != answered }
            }

            assertFalse(
                fixture.viewModel.uiState.value.showIntro,
                "the module was already entered; the screen belongs to entering, not to every item",
            )
        }

    @Test
    fun `the help affordance re-opens the full explanation at any time`() =
        runBlocking {
            val fixture = PracticeFixture(AppSettings())
            fixture.viewModel.startIfNeeded()
            withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.showIntro } }
            fixture.viewModel.onIntroDismissed()
            withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.item != null && !it.showIntro } }

            fixture.viewModel.onOpenIntro()

            assertTrue(fixture.viewModel.uiState.value.showIntro, "§5: always reachable on demand")
            assertFalse(
                fixture.viewModel.uiState.value.introAnswerRevealed,
                "recalling it starts from the same place, not from the answer",
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
