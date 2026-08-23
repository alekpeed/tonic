package com.tonic.feature.practice.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.tonic.core.model.state.AppSettings
import com.tonic.core.model.state.PracticeTrack
import com.tonic.core.model.time.Clock
import com.tonic.feature.practice.engine.FakeAttemptRepository
import com.tonic.feature.practice.engine.FakeAudioInterruptions
import com.tonic.feature.practice.engine.FakeAudioPlayer
import com.tonic.feature.practice.engine.FakeConfusionRepository
import com.tonic.feature.practice.engine.FakeMicrophoneSource
import com.tonic.feature.practice.engine.FakeSessionRepository
import com.tonic.feature.practice.engine.FakeSkillStateRepository
import com.tonic.feature.practice.engine.PracticeLoopEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.Instant

/**
 * The real [PracticeViewModel] over the real [PracticeLoopEngine], with only the boundaries faked -
 * audio, persistence and the platform's audio focus. Shared by the ViewModel tests so a constructor
 * change lands in one place rather than several.
 */
internal class PracticeFixture(
    settings: AppSettings = AppSettings(),
    /** Reuse another fixture's persistence, to model "the same device, a later launch" - the engine and ViewModel are always fresh. */
    shared: PracticeFixture? = null,
    /** Which curriculum the session walks - docs/40-PHASE-4-SPEC.md §2. Arrives on the route in the app. */
    track: PracticeTrack = PracticeTrack.PITCH,
) {
    /** Mutable so time-dependent behavior (the wall-clock budget, the time bar) can be driven from a test. */
    var now: Instant = Instant.EPOCH

    val attemptRepository: FakeAttemptRepository = shared?.attemptRepository ?: FakeAttemptRepository()
    val skillStateRepository: FakeSkillStateRepository =
        shared?.skillStateRepository ?: FakeSkillStateRepository(attemptRepository)
    val confusionRepository: FakeConfusionRepository = shared?.confusionRepository ?: FakeConfusionRepository()
    val sessionRepository: FakeSessionRepository = shared?.sessionRepository ?: FakeSessionRepository()
    val audioPlayer = FakeAudioPlayer()
    val audioInterruptions = FakeAudioInterruptions()
    val settingsRepository = FakeSettingsRepository(settings)
    val microphoneSource = FakeMicrophoneSource()
    val clock = Clock { now }
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
            SavedStateHandle(mapOf(PracticeTrack.ROUTE_ARG to track.name)),
        )

    /** Holds [viewModel] so [clear] can cancel its scope. See [Companion.clearAll]. */
    private val store = ViewModelStore()

    init {
        retain(viewModel)
        LIVE += this
    }

    /**
     * Cancels this fixture's `viewModelScope` and waits for it to finish.
     *
     * The wait is the part that matters. `ViewModelStore.clear` cancels cooperatively and returns
     * immediately, which leaves a window where a coroutine is still unwinding on `Dispatchers.Main` -
     * and that window is exactly what `resetMain()` loses a race to. Joining closes it. Bounded,
     * because docs/21-HANDOFF.md §8 is a standing warning about unbounded waits in this repository's
     * tests; a scope that will not finish in a second after cancellation is a bug worth failing on
     * rather than hanging on.
     */
    fun clear() {
        store.clear()
        runBlocking {
            withTimeout(CLEAR_TIMEOUT_MS) {
                viewModel.viewModelScope.coroutineContext.job
                    .join()
            }
        }
    }

    /** Puts [viewModel] under a store this fixture owns, so clearing the store cancels its scope. */
    private fun <T : ViewModel> retain(viewModel: T) {
        ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <V : ViewModel> create(modelClass: Class<V>): V = viewModel as V
            },
        )[viewModel::class.java]
    }

    internal companion object {
        private const val CLEAR_TIMEOUT_MS = 1_000L

        /**
         * Every fixture built since the last [clearAll].
         *
         * A registry rather than per-test bookkeeping because fixtures are constructed inside test
         * bodies, sometimes more than one, and a test that forgets to clear one reintroduces exactly
         * the flake this exists to remove - silently, and only under load. [MainDispatcherRule] drains
         * it, so no test has to remember.
         *
         * Safe as shared mutable state only because this module's tests run one at a time in one JVM
         * (no `maxParallelForks`, and Robolectric does not parallelize within a fork). If that ever
         * changes, this becomes per-thread rather than global.
         */
        private val LIVE = mutableListOf<PracticeFixture>()

        /** Cancels every fixture built during the test that just finished. */
        fun clearAll() {
            LIVE.forEach { it.clear() }
            LIVE.clear()
        }
    }

    /**
     * Starts a session and clears the explanation screen the way a person does, leaving a live item.
     *
     * Entering a module always raises its explanation now (docs/08-UI-SPEC.md §3a), and that screen
     * covers the ladder and silences the item behind it. A test that skips it is not exercising the
     * app's flow: it answers, skips or times an exercise that no learner could have reached.
     */
    suspend fun startPastIntro(timeoutMs: Long = 10_000L) {
        viewModel.startIfNeeded()
        // One wait, then one decision, because the item and its explanation now arrive in the same
        // state emission. The earlier two-phase version sampled `showIntro` between two updates and
        // could see the item before the screen went up - it then skipped the dismissal and left the
        // caller running against an explanation that appeared a moment later.
        withTimeout(timeoutMs) { viewModel.uiState.first { it.item != null } }
        if (viewModel.uiState.value.showIntro) {
            viewModel.onIntroDismissed()
            withTimeout(timeoutMs) { viewModel.uiState.first { !it.showIntro } }
        }
    }
}
