package com.tonic.feature.practice.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.state.AppSettings
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.state.SkillState
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
import kotlin.test.assertTrue

/**
 * docs/08-UI-SPEC.md §3a as a shipping gate, applied to minor: a learner meeting `M10` for the first
 * time gets the minor explanation, and a learner who has only ever practiced major is never shown it.
 */
@RunWith(AndroidJUnit4::class)
class MinorIntroTest {
    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @After fun tearDown() = Dispatchers.resetMain()

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }

    /** Marks every major node mastered, which is what routes the next session into minor. */
    private suspend fun PracticeFixture.masterAllOfMajor() {
        for (id in SkillIds.M2_NODES_IN_ORDER) {
            skillStateRepository.update(
                SkillState.initial(id).copy(masteryState = MasteryState.MASTERED),
            )
        }
    }

    @Test
    fun `a learner still working through major is never shown the minor explanation`() =
        runBlocking {
            val fixture = PracticeFixture(AppSettings(module2IntroSeen = true))
            fixture.viewModel.startIfNeeded()
            val state = withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.item != null } }

            assertEquals(IntroKind.NONE, state.introKind)
            assertEquals(false, state.showIntro)
        }

    @Test
    fun `reaching minor for the first time shows the minor explanation, not the major one`() =
        runBlocking {
            val fixture = PracticeFixture(AppSettings(module2IntroSeen = true))
            fixture.masterAllOfMajor()

            fixture.viewModel.startIfNeeded()
            val state = withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.showIntro } }

            assertEquals(
                IntroKind.M10,
                state.introKind,
                "having seen the major explanation says nothing about knowing what ♭3 means",
            )
        }

    @Test
    fun `dismissing the minor explanation marks only minor as seen`() =
        runBlocking {
            // Marking both would silently rob a learner of an explanation they never received.
            val fixture = PracticeFixture(AppSettings(module2IntroSeen = false))
            fixture.masterAllOfMajor()

            fixture.viewModel.startIfNeeded()
            withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.showIntro } }
            fixture.viewModel.onIntroDismissed()

            val settings =
                withTimeout(TIMEOUT_MS) { fixture.settingsRepository.settings.first { it.module10IntroSeen } }
            assertTrue(settings.module10IntroSeen)
            assertEquals(false, settings.module2IntroSeen, "the major explanation was never shown here")
        }

    @Test
    fun `a minor session actually serves minor items`() =
        runBlocking {
            // The end-to-end point of Stage 2.3: routing, generation, loop and ladder all agreeing.
            val fixture = PracticeFixture(AppSettings(module2IntroSeen = true, module10IntroSeen = true))
            fixture.masterAllOfMajor()

            fixture.viewModel.startIfNeeded()
            val state = withTimeout(TIMEOUT_MS) { fixture.viewModel.uiState.first { it.item != null } }
            val item = state.recognitionItem!!

            assertEquals(com.tonic.core.model.music.Mode.MINOR, item.mode)
            assertTrue(
                item.activeDegrees.any { it.alteration < 0 },
                "a minor node's answer set must contain an altered degree - got " +
                    item.activeDegrees.joinToString { it.canonicalLabel },
            )
            assertTrue(
                state.activeDegrees.any { it.canonicalLabel == "b3" },
                "the ladder must offer ♭3 as a real button",
            )
        }
}
