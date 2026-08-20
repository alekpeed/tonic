package com.tonic.feature.practice.ui

import com.tonic.core.model.state.AppSettings
import com.tonic.core.model.time.Clock
import com.tonic.feature.practice.engine.FakeAttemptRepository
import com.tonic.feature.practice.engine.FakeAudioInterruptions
import com.tonic.feature.practice.engine.FakeAudioPlayer
import com.tonic.feature.practice.engine.FakeConfusionRepository
import com.tonic.feature.practice.engine.FakeSessionRepository
import com.tonic.feature.practice.engine.FakeSkillStateRepository
import com.tonic.feature.practice.engine.PracticeLoopEngine
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
        PracticeViewModel(engine, audioPlayer, skillStateRepository, sessionRepository, settingsRepository, clock)
}
