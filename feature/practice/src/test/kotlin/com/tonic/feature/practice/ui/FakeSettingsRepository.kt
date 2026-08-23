package com.tonic.feature.practice.ui

import com.tonic.core.data.settings.SettingsRepository
import com.tonic.core.model.rhythm.CalibrationSlot
import com.tonic.core.model.rhythm.RhythmCalibration
import com.tonic.core.model.state.AppSettings
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.model.state.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow

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

    override suspend fun setAudibleTapsEnabled(enabled: Boolean) {
        state.value = state.value.copy(audibleTapsEnabled = enabled)
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
