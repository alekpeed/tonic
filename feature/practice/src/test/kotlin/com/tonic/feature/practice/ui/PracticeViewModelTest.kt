package com.tonic.feature.practice.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.Item
import com.tonic.core.model.state.AppSettings
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.state.SkillState
import com.tonic.core.model.time.Clock
import com.tonic.feature.practice.engine.FakeAttemptRepository
import com.tonic.feature.practice.engine.FakeAudioInterruptions
import com.tonic.feature.practice.engine.FakeAudioPlayer
import com.tonic.feature.practice.engine.FakeConfusionRepository
import com.tonic.feature.practice.engine.FakeMicrophoneSource
import com.tonic.feature.practice.engine.FakeSessionRepository
import com.tonic.feature.practice.engine.FakeSkillStateRepository
import com.tonic.feature.practice.engine.PracticeLoopEngine
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * docs/09-BUILD-PLAN.md Stage 7: the ViewModel is thin by design (docs/04-ARCHITECTURE.md §3, "no
 * pedagogical logic") - these tests cover the two things that genuinely live here: resolving which
 * node to practice (docs/08-UI-SPEC.md §2's Home screen doesn't exist yet, see [PracticeViewModel]'s
 * KDoc), and the docs/08-UI-SPEC.md §3/§4 feedback-and-pacing sequencing around the headless engine
 * proven correct in Stage 6. Runs under Robolectric (docs/10-TESTING.md's established pattern for
 * anything that touches `Dispatchers.Main`, since `viewModelScope` needs a real `Looper` to
 * initialize against - a plain JVM unit test's unmocked `android.os.Looper` throws). Main is set to a
 * REAL dispatcher rather than a virtual-time `TestDispatcher`: [PracticeLoopEngine]'s own pre-rendering
 * genuinely runs on `Dispatchers.Default` (Stage 6, by design), so a virtual-time scheduler on Main
 * alone can't reliably wait for that cross-dispatcher work - real time plus [Fixture.awaitItemChangeFrom]
 * (below) is simpler and matches Stage 6's own `runBlocking`-based tests.
 */
@RunWith(AndroidJUnit4::class)
class PracticeViewModelTest {
    /**
     * Every view model built during a test, cleared before [Dispatchers.resetMain].
     *
     * Without this the tests were intermittently flaky, failing inside `TestMainDispatcher` with a
     * concurrent-modification error - three of them in one run, none in the next, depending only on the
     * order test classes happened to execute in. The cause is that a view model whose scope is never
     * cancelled keeps running coroutines on `Dispatchers.Main` after its test returns, and `resetMain()`
     * then swaps the dispatcher out from under them. Clearing the store cancels `viewModelScope` first,
     * so there is nothing in flight to race.
     *
     * docs/10-TESTING.md §3: "A flaky test in this project is a bug in the test or a determinism
     * violation in the code. Do not add retries. Find it."
     */
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default)
    }

    @After
    fun tearDown() {
        stores.forEach { it.clear() }
        stores.clear()
        Dispatchers.resetMain()
    }

    /** Puts [viewModel] under a store this test will clear, so its scope is cancelled at teardown. */
    private fun <T : ViewModel> retain(viewModel: T): T {
        val store = ViewModelStore()
        ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <V : ViewModel> create(modelClass: Class<V>): V = viewModel as V
            },
        )[viewModel::class.java]
        stores += store
        return viewModel
    }

    private inner class Fixture(
        settings: AppSettings = AppSettings(),
    ) {
        val attemptRepository = FakeAttemptRepository()
        val skillStateRepository = FakeSkillStateRepository(attemptRepository)
        val confusionRepository = FakeConfusionRepository()
        val sessionRepository = FakeSessionRepository()
        val audioPlayer = FakeAudioPlayer()
        val audioInterruptions = FakeAudioInterruptions()
        val settingsRepository = FakeSettingsRepository(settings)
        val microphoneSource = FakeMicrophoneSource()
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
        val viewModel =
            PracticeViewModel(
                engine,
                audioPlayer,
                skillStateRepository,
                sessionRepository,
                settingsRepository,
                microphoneSource,
                clock,
            ).also { retain(it) }

        /**
         * Waits for input to actually be accepted, not just for an item to exist - matching how the
         * real ladder gates input on [PracticeUiState.inputEnabled].
         *
         * Dismisses the first-run explanation on the way, as a real learner must: the screen overlays
         * the ladder, so no answer can be given under it. This became load-bearing when the playback
         * hold became per-item - an open intro now correctly silences *every* item behind it, so a test
         * that answers items with the intro still up counts fewer played buffers than the live flow
         * produces. Run #21 failed the contrast-sequence test exactly that way.
         */
        suspend fun startAndAwaitFirstItem(): Item.FunctionalRecognitionItem {
            viewModel.startIfNeeded()
            withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.item != null } }
            if (viewModel.uiState.value.showIntro) viewModel.onIntroDismissed()
            return withTimeout(TIMEOUT_MS) {
                viewModel.uiState.first { it.item != null && it.inputEnabled }.recognitionItem!!
            }
        }

        suspend fun awaitItemChangeFrom(previous: Item.FunctionalRecognitionItem) {
            withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.item != null && it.item != previous } }
        }
    }

    @Test
    fun `startIfNeeded resolves the first non-mastered M2 node and populates the first item`() =
        runBlocking {
            val fixture = Fixture()
            val item = fixture.startAndAwaitFirstItem()

            assertFalse(fixture.viewModel.uiState.value.isLoading)
            assertEquals(SkillIds.M2_DEG_SET_1, item.skill)
        }

    @Test
    fun `an interrupted session is offered back instead of silently starting a fresh one`() =
        runBlocking {
            // docs/10-TESTING.md §11: "force stop mid-session -> resume offered, no data loss."
            val first = Fixture()
            first.startAndAwaitFirstItem()
            first.viewModel.onAppBackgrounded()
            withTimeout(TIMEOUT_MS) { first.viewModel.uiState.first { it.isPaused } }
            first.engine.awaitPersistence()
            val interrupted = first.sessionRepository.findResumable()!!

            // Cold relaunch: a brand-new ViewModel over a repository that still holds the session row.
            val second = Fixture()
            second.sessionRepository.adopt(interrupted)
            second.viewModel.startIfNeeded()

            val offered = withTimeout(TIMEOUT_MS) { second.viewModel.uiState.first { !it.isLoading } }
            assertEquals(
                interrupted.id,
                offered.resumableSession?.id,
                "the interrupted session must be offered, not replaced",
            )
            assertNull(offered.item, "nothing should be playing while the offer is on screen")
        }

    @Test
    fun `accepting the resume offer continues the interrupted session`() =
        runBlocking {
            val first = Fixture()
            first.startAndAwaitFirstItem()
            first.viewModel.onAppBackgrounded()
            withTimeout(TIMEOUT_MS) { first.viewModel.uiState.first { it.isPaused } }
            first.engine.awaitPersistence()
            val interrupted = first.sessionRepository.findResumable()!!

            val second = Fixture()
            second.sessionRepository.adopt(interrupted)
            second.viewModel.startIfNeeded()
            withTimeout(TIMEOUT_MS) { second.viewModel.uiState.first { it.resumableSession != null } }

            second.viewModel.onResumeSession()

            val running = withTimeout(TIMEOUT_MS) { second.viewModel.uiState.first { it.item != null } }
            assertNull(running.resumableSession, "the offer clears once accepted")
            assertEquals(interrupted.id, running.sessionId, "it continues the same session row, not a new one")
        }

    @Test
    fun `declining the resume offer closes it out so it is not offered again`() =
        runBlocking {
            val first = Fixture()
            first.startAndAwaitFirstItem()
            first.viewModel.onAppBackgrounded()
            withTimeout(TIMEOUT_MS) { first.viewModel.uiState.first { it.isPaused } }
            first.engine.awaitPersistence()
            val interrupted = first.sessionRepository.findResumable()!!

            val second = Fixture()
            second.sessionRepository.adopt(interrupted)
            second.viewModel.startIfNeeded()
            withTimeout(TIMEOUT_MS) { second.viewModel.uiState.first { it.resumableSession != null } }

            second.viewModel.onStartFreshSession()

            val running = withTimeout(TIMEOUT_MS) { second.viewModel.uiState.first { it.item != null } }
            assertTrue(
                running.sessionId != interrupted.id,
                "starting fresh must create a new session, not reuse the declined one",
            )
            assertNull(
                second.sessionRepository.findResumable(),
                "the declined session must stop offering itself at every launch",
            )
        }

    @Test
    fun `startIfNeeded is idempotent - calling it twice does not start a second session`() =
        runBlocking {
            val fixture = Fixture()
            val firstItem = fixture.startAndAwaitFirstItem()

            fixture.viewModel.startIfNeeded()
            // No new session means no second "item changed" event to await - a short real wait is the
            // simplest way to assert the negative (nothing happened) without a matching event to key off.
            delay(200)

            assertEquals(firstItem, fixture.viewModel.uiState.value.item)
        }

    @Test
    fun `startIfNeeded skips an already-mastered node and resolves its successor`() =
        runBlocking {
            val fixture = Fixture()
            fixture.skillStateRepository.update(
                SkillState.initial(SkillIds.M2_DEG_SET_1).copy(masteryState = MasteryState.MASTERED),
            )

            val item = fixture.startAndAwaitFirstItem()

            assertEquals(SkillIds.M2_DEG_SET_2, item.skill)
        }

    @Test
    fun `the label style in uiState reflects settings`() =
        runBlocking {
            val fixture = Fixture(AppSettings(labelStyle = LabelStyle.SOLFEGE))
            fixture.startAndAwaitFirstItem()

            assertEquals(LabelStyle.SOLFEGE, fixture.viewModel.uiState.value.labelStyle)
        }

    @Test
    fun `selecting the correct degree eventually advances to a new item`() =
        runBlocking {
            val fixture = Fixture()
            val item = fixture.startAndAwaitFirstItem()

            fixture.viewModel.onDegreeSelected(item.targetDegree)
            fixture.awaitItemChangeFrom(item)

            assertEquals(1, fixture.attemptRepository.all.size)
            assertTrue(
                fixture.attemptRepository.all
                    .single()
                    .correct,
            )
        }

    @Test
    fun `selecting an incorrect degree plays the contrast sequence before advancing`() =
        runBlocking {
            val fixture = Fixture()
            val item = fixture.startAndAwaitFirstItem()
            val wrongDegree = item.activeDegrees.first { it != item.targetDegree }
            val buffersBefore = fixture.audioPlayer.playedBuffers.size

            fixture.viewModel.onDegreeSelected(wrongDegree)
            fixture.awaitItemChangeFrom(item)

            assertEquals(1, fixture.attemptRepository.all.size)
            assertFalse(
                fixture.attemptRepository.all
                    .single()
                    .correct,
            )
            // target-in-context + chosen note + target again, then the next item's own auto-play.
            assertTrue(fixture.audioPlayer.playedBuffers.size >= buffersBefore + 4)
        }

    @Test
    fun `skip abandons the current item without recording a response`() =
        runBlocking {
            val fixture = Fixture()
            val item = fixture.startAndAwaitFirstItem()

            fixture.viewModel.onSkip()
            fixture.awaitItemChangeFrom(item)

            // The abandoned attempt is written on the async chain now (docs/04-ARCHITECTURE.md §5), so
            // join it rather than assuming the item change implies the write already landed.
            fixture.engine.awaitPersistence()
            val recorded = fixture.attemptRepository.all.single()
            assertTrue(recorded.isAbandoned)
            assertEquals(null, recorded.responseLabel)
        }

    @Test
    fun `replay plays the current item again without advancing or recording an attempt`() =
        runBlocking {
            val fixture = Fixture()
            val item = fixture.startAndAwaitFirstItem()
            val buffersBefore = fixture.audioPlayer.playedBuffers.size

            fixture.viewModel.onReplay()
            withTimeout(TIMEOUT_MS) {
                while (fixture.audioPlayer.playedBuffers.size == buffersBefore) delay(10)
            }

            assertEquals(buffersBefore + 1, fixture.audioPlayer.playedBuffers.size)
            assertTrue(fixture.attemptRepository.all.isEmpty())
            assertEquals(item, fixture.viewModel.uiState.value.item)
        }

    private companion object {
        /** Lifted off Fixture when that became an inner class — Kotlin forbids a companion there. */
        const val TIMEOUT_MS = 5_000L
    }
}
