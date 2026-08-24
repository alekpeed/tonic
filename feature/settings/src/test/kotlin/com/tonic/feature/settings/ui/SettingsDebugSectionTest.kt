package com.tonic.feature.settings.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.state.PracticeTrack
import com.tonic.core.model.time.Clock
import com.tonic.core.ui.theme.TonicTheme
import com.tonic.feature.settings.debug.DebugSkillJumper
import com.tonic.feature.settings.debug.FakeAttemptRepository
import com.tonic.feature.settings.debug.FakeDebugProgressRepository
import com.tonic.feature.settings.debug.FakeSkillStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Settings screen, rendered and pressed.
 *
 * There was no test of this screen at all until a tester hit a crash on it three builds running, while
 * every unit test passed. The ViewModel was covered, the jumper was covered, the database path was
 * covered — the *screen* was not, so nothing ever executed a composition or a click, which is where the
 * remaining failure had to be. Rendering it is cheap (Robolectric hosts Compose here already, as
 * `:feature:practice` has done since Stage 7) and it is the only layer that exercises the string
 * resources, the tags, and the actual press.
 */
@RunWith(AndroidJUnit4::class)
class SettingsDebugSectionTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Scrolls the lazy list until [tag] is composed, then clicks it. */
    private fun scrollToAndClick(tag: String) {
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    /** Waits for the outcome line, so a slow jump is distinguished from a stuck one. */
    private fun awaitResult() {
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithTag("settings_debug_jump_result").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun viewModel(): SettingsViewModel {
        val attempts = FakeAttemptRepository()
        val skillStates = FakeSkillStateRepository(attempts)
        return SettingsViewModel(
            FakeSettingsRepository(),
            FakeSessionRepository(),
            FakeDataExportRepository(),
            DebugSkillJumper(
                FakeDebugProgressRepository(attempts, skillStates),
                skillStates,
                FakeSettingsRepository(),
                Clock { Instant.EPOCH },
            ),
            Clock { Instant.EPOCH },
        )
    }

    @Test
    fun `the screen renders with the debug section present`() {
        compose.setContent { TonicTheme { SettingsScreen(viewModel = viewModel()) } }
        compose.waitForIdle()

        // Every node in the chain gets a button - if the section rendered at all, the first one exists.
        val first = SkillGraph.practiceChain.first().id
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_debug_jump_${first.raw}"))
        assertTrue(
            compose.onAllNodesWithTag("settings_debug_jump_${first.raw}").fetchSemanticsNodes().isNotEmpty(),
            "the debug section did not render - BuildConfig.DEBUG gating or the section itself is broken",
        )
    }

    @Test
    fun `pressing a debug button completes and reports, without the composition falling over`() {
        compose.setContent { TonicTheme { SettingsScreen(viewModel = viewModel()) } }
        compose.waitForIdle()

        scrollToAndClick("settings_debug_jump_${SkillIds.M9_MODE_ID_CADENCE.raw}")
        awaitResult()
    }

    @Test
    fun `a successful jump asks the host to navigate`() {
        // The whole point of the button, and the thing it did not do for three builds: seeding progress
        // and staying put is not a jump. The host supplies navigation, so this is the only place the
        // wiring can be checked short of the app itself.
        var navigated = 0
        compose.setContent {
            TonicTheme { SettingsScreen(viewModel = viewModel(), onDebugJumpFinished = { navigated++ }) }
        }
        compose.waitForIdle()

        scrollToAndClick("settings_debug_jump_" + SkillIds.M10_MIXED_MODE.raw)
        compose.waitUntil(timeoutMillis = 30_000) { navigated > 0 }

        assertEquals(1, navigated, "a jump must navigate exactly once")
    }

    @Test
    fun `every M3 node gets a button too, and jumping to one routes to the rhythm track`() {
        // The real bug: debugJumpTargets only ever listed the pitch chain, so M3.DOWNBEAT (and every
        // other rhythm node) had no button at all. A rhythm target must also route into RHYTHM, not
        // silently into PITCH like the first fix for the missing button alone would still have done.
        var routedTo: PracticeTrack? = null
        compose.setContent {
            TonicTheme {
                SettingsScreen(viewModel = viewModel(), onDebugJumpFinished = { track -> routedTo = track })
            }
        }
        compose.waitForIdle()

        scrollToAndClick("settings_debug_jump_${SkillIds.M3_DOWNBEAT.raw}")
        compose.waitUntil(timeoutMillis = 30_000) { routedTo != null }

        assertEquals(PracticeTrack.RHYTHM, routedTo)
    }

    @Test
    fun `pressing several buttons in a row - a later node, then an earlier one`() {
        // The device sequence, through the real screen: the composition must survive repeated presses
        // in whatever order they come, which is the failure a tester hit in under a minute.
        compose.setContent { TonicTheme { SettingsScreen(viewModel = viewModel()) } }
        compose.waitForIdle()

        for (target in listOf(SkillIds.M11_CHROM_FLAT2, SkillIds.M2_DEG_SET_2, SkillIds.M10_MIXED_MODE)) {
            scrollToAndClick("settings_debug_jump_${target.raw}")
            awaitResult()
        }
    }
}
