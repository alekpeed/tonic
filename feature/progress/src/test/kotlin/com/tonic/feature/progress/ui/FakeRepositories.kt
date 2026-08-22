package com.tonic.feature.progress.ui

import com.tonic.core.data.repository.AttemptRepository
import com.tonic.core.data.repository.ConfusionRepository
import com.tonic.core.data.repository.SkillStateRepository
import com.tonic.core.data.settings.SettingsRepository
import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.rhythm.CalibrationSlot
import com.tonic.core.model.rhythm.RhythmCalibration
import com.tonic.core.model.state.AppSettings
import com.tonic.core.model.state.ConfusionMatrix
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.model.state.SkillState
import com.tonic.core.model.state.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * In-memory stand-ins scoped directly to what [ProgressViewModel] reads - unlike `:feature:practice`'s
 * fakes, these don't need to reproduce real adaptive-engine behavior (the engine algorithms themselves
 * are already proven in `:core:engine`'s own tests); each is settable directly to a known fixture so
 * [ProgressViewModelTest] can assert against hand-computed expected values.
 */
class FakeAttemptRepository : AttemptRepository {
    private val bySkill = mutableMapOf<SkillId, List<Attempt>>()

    override suspend fun record(attempt: Attempt) {
        bySkill[attempt.skillId] = (bySkill[attempt.skillId] ?: emptyList()) + attempt
    }

    override fun recentAttempts(
        skillId: SkillId,
        limit: Int,
    ): Flow<List<Attempt>> = MutableStateFlow(bySkill[skillId] ?: emptyList()).asStateFlow()

    override suspend fun windowFor(
        skillId: SkillId,
        size: Int,
    ): List<Attempt> = (bySkill[skillId] ?: emptyList()).takeLast(size)

    fun setAttempts(
        skillId: SkillId,
        attempts: List<Attempt>,
    ) {
        bySkill[skillId] = attempts
    }
}

class FakeSkillStateRepository : SkillStateRepository {
    private val states = mutableMapOf<SkillId, SkillState>()
    private val flow = MutableStateFlow<Map<SkillId, SkillState>>(emptyMap())

    override fun observe(skillId: SkillId): Flow<SkillState> =
        flow.asStateFlow().map { it[skillId] ?: SkillState.initial(skillId) }

    override fun observeAll(): Flow<Map<SkillId, SkillState>> = flow.asStateFlow()

    override suspend fun update(state: SkillState) {
        states[state.skillId] = state
        flow.value = states.toMap()
    }

    override suspend fun dueForReview(now: Instant): List<SkillId> = emptyList()

    override suspend fun rebuildFromAttempts() = Unit

    override suspend fun rebuildFromAttempts(skillId: SkillId) = Unit

    fun setState(state: SkillState) {
        states[state.skillId] = state
        flow.value = states.toMap()
    }
}

class FakeConfusionRepository : ConfusionRepository {
    private val matrices = mutableMapOf<SkillId, ConfusionMatrix>()

    override suspend fun record(
        skillId: SkillId,
        target: String,
        response: String,
    ) = Unit

    override suspend fun matrixFor(skillId: SkillId): ConfusionMatrix =
        matrices[skillId] ?: ConfusionMatrix(skillId, emptyList())

    fun setMatrix(matrix: ConfusionMatrix) {
        matrices[matrix.skillId] = matrix
    }
}

class FakeSettingsRepository(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings = state

    override suspend fun setLabelStyle(style: LabelStyle) {
        state.value = state.value.copy(labelStyle = style)
    }

    override suspend fun setReferenceA4Hz(hz: Float) {
        state.value = state.value.copy(referenceA4Hz = hz)
    }

    override suspend fun setSessionLengthMinutes(minutes: Int) {
        state.value = state.value.copy(sessionLengthMinutes = minutes)
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        state.value = state.value.copy(hapticsEnabled = enabled)
    }

    override suspend fun setSoundEffectsEnabled(enabled: Boolean) {
        state.value = state.value.copy(soundEffectsEnabled = enabled)
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.value = state.value.copy(themeMode = mode)
    }

    override suspend fun setReduceMotion(enabled: Boolean) {
        state.value = state.value.copy(reduceMotion = enabled)
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        state.value = state.value.copy(onboardingCompleted = completed)
    }

    override suspend fun setModule9IntroSeen(seen: Boolean) {
        settings.value = settings.value.copy(module9IntroSeen = seen)
    }

    override suspend fun setModule10IntroSeen(seen: Boolean) {
        settings.value = settings.value.copy(module10IntroSeen = seen)
    }

    override suspend fun setModule11IntroSeen(seen: Boolean) {
        settings.value = settings.value.copy(module11IntroSeen = seen)
    }

    override suspend fun setModule12IntroSeen(seen: Boolean) {
        settings.value = settings.value.copy(module12IntroSeen = seen)
    }

    override suspend fun setMixedModeIntroSeen(seen: Boolean) {
        settings.value = settings.value.copy(mixedModeIntroSeen = seen)
    }

    override suspend fun setSungResponseEnabled(enabled: Boolean) {
        settings.value = settings.value.copy(sungResponseEnabled = enabled)
    }

    override suspend fun setRhythmCalibration(
        slot: CalibrationSlot,
        calibration: RhythmCalibration,
    ) {
        settings.value =
            settings.value.copy(
                rhythmCalibrations = settings.value.rhythmCalibrations.with(slot, calibration),
            )
    }

    override suspend fun clearRhythmCalibration(slot: CalibrationSlot) {
        settings.value =
            settings.value.copy(
                rhythmCalibrations =
                    when (slot) {
                        CalibrationSlot.SPEAKER -> settings.value.rhythmCalibrations.copy(speaker = null)
                        CalibrationSlot.WIRED -> settings.value.rhythmCalibrations.copy(wired = null)
                    },
            )
    }

    override suspend fun setSungOctaveAgnostic(enabled: Boolean) {
        settings.value = settings.value.copy(sungOctaveAgnostic = enabled)
    }

    override suspend fun setSungResponseIntroSeen(seen: Boolean) {
        settings.value = settings.value.copy(sungResponseIntroSeen = seen)
    }

    override suspend fun setModule2IntroSeen(seen: Boolean) {
        state.value = state.value.copy(module2IntroSeen = seen)
    }

    override suspend fun setDiagnosticCompleted(completed: Boolean) {
        state.value = state.value.copy(diagnosticCompleted = completed)
    }

    override suspend fun setDailyReminder(
        enabled: Boolean,
        time: String?,
    ) {
        state.value = state.value.copy(dailyReminderEnabled = enabled, dailyReminderTime = time)
    }
}
