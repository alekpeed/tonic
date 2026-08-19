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
) {
    val attemptRepository = FakeAttemptRepository()
    val skillStateRepository = FakeSkillStateRepository(attemptRepository)
    val confusionRepository = FakeConfusionRepository()
    val sessionRepository = FakeSessionRepository()
    val audioPlayer = FakeAudioPlayer()
    val audioInterruptions = FakeAudioInterruptions()
    val settingsRepository = FakeSettingsRepository(settings)
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
        PracticeViewModel(engine, audioPlayer, skillStateRepository, sessionRepository, settingsRepository, clock)
}
