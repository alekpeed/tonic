package com.tonic.feature.settings.ui

import com.tonic.core.data.settings.SettingsRepository
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

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.value = state.value.copy(themeMode = mode)
    }

    override suspend fun setReduceMotion(enabled: Boolean) {
        state.value = state.value.copy(reduceMotion = enabled)
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        state.value = state.value.copy(onboardingCompleted = completed)
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
