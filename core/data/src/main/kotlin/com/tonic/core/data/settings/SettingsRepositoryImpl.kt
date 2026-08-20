package com.tonic.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tonic.core.model.state.AppSettings
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.model.state.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** docs/05-DATA-MODEL.md §3's exact keys and defaults. */
internal object SettingsKeys {
    val LABEL_STYLE = stringPreferencesKey("label_style")
    val REFERENCE_A4_HZ = floatPreferencesKey("reference_a4_hz")
    val SESSION_LENGTH_MINUTES = intPreferencesKey("session_length_minutes")
    val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    val SOUND_EFFECTS_ENABLED = booleanPreferencesKey("sound_effects_enabled")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val DIAGNOSTIC_COMPLETED = booleanPreferencesKey("diagnostic_completed")
    val MODULE2_INTRO_SEEN = booleanPreferencesKey("module2_intro_seen")
    val MODULE9_INTRO_SEEN = booleanPreferencesKey("module9_intro_seen")
    val DAILY_REMINDER_ENABLED = booleanPreferencesKey("daily_reminder_enabled")
    val DAILY_REMINDER_TIME = stringPreferencesKey("daily_reminder_time")
}

internal class SettingsRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsRepository {
        override val settings: Flow<AppSettings> = dataStore.data.map { it.toAppSettings() }

        override suspend fun setLabelStyle(style: LabelStyle) {
            dataStore.edit { it[SettingsKeys.LABEL_STYLE] = style.name }
        }

        override suspend fun setReferenceA4Hz(hz: Float) {
            dataStore.edit { it[SettingsKeys.REFERENCE_A4_HZ] = hz }
        }

        override suspend fun setSessionLengthMinutes(minutes: Int) {
            dataStore.edit { it[SettingsKeys.SESSION_LENGTH_MINUTES] = minutes }
        }

        override suspend fun setHapticsEnabled(enabled: Boolean) {
            dataStore.edit { it[SettingsKeys.HAPTICS_ENABLED] = enabled }
        }

        override suspend fun setSoundEffectsEnabled(enabled: Boolean) {
            dataStore.edit { it[SettingsKeys.SOUND_EFFECTS_ENABLED] = enabled }
        }

        override suspend fun setThemeMode(mode: ThemeMode) {
            dataStore.edit { it[SettingsKeys.THEME_MODE] = mode.name }
        }

        override suspend fun setReduceMotion(enabled: Boolean) {
            dataStore.edit { it[SettingsKeys.REDUCE_MOTION] = enabled }
        }

        override suspend fun setOnboardingCompleted(completed: Boolean) {
            dataStore.edit { it[SettingsKeys.ONBOARDING_COMPLETED] = completed }
        }

        override suspend fun setModule2IntroSeen(seen: Boolean) {
            dataStore.edit { it[SettingsKeys.MODULE2_INTRO_SEEN] = seen }
        }

        override suspend fun setModule9IntroSeen(seen: Boolean) {
            dataStore.edit { it[SettingsKeys.MODULE9_INTRO_SEEN] = seen }
        }

        override suspend fun setDiagnosticCompleted(completed: Boolean) {
            dataStore.edit { it[SettingsKeys.DIAGNOSTIC_COMPLETED] = completed }
        }

        override suspend fun setDailyReminder(
            enabled: Boolean,
            time: String?,
        ) {
            dataStore.edit { prefs ->
                prefs[SettingsKeys.DAILY_REMINDER_ENABLED] = enabled
                if (time != null) {
                    prefs[SettingsKeys.DAILY_REMINDER_TIME] = time
                } else {
                    prefs.remove(SettingsKeys.DAILY_REMINDER_TIME)
                }
            }
        }
    }

private fun Preferences.toAppSettings(): AppSettings {
    val defaults = AppSettings()
    return AppSettings(
        labelStyle =
            this[SettingsKeys.LABEL_STYLE]?.let { runCatching { LabelStyle.valueOf(it) }.getOrNull() }
                ?: defaults.labelStyle,
        referenceA4Hz = this[SettingsKeys.REFERENCE_A4_HZ] ?: defaults.referenceA4Hz,
        sessionLengthMinutes = this[SettingsKeys.SESSION_LENGTH_MINUTES] ?: defaults.sessionLengthMinutes,
        hapticsEnabled = this[SettingsKeys.HAPTICS_ENABLED] ?: defaults.hapticsEnabled,
        soundEffectsEnabled = this[SettingsKeys.SOUND_EFFECTS_ENABLED] ?: defaults.soundEffectsEnabled,
        themeMode =
            this[SettingsKeys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: defaults.themeMode,
        reduceMotion = this[SettingsKeys.REDUCE_MOTION] ?: defaults.reduceMotion,
        onboardingCompleted = this[SettingsKeys.ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted,
        diagnosticCompleted = this[SettingsKeys.DIAGNOSTIC_COMPLETED] ?: defaults.diagnosticCompleted,
        module2IntroSeen = this[SettingsKeys.MODULE2_INTRO_SEEN] ?: defaults.module2IntroSeen,
        module9IntroSeen = this[SettingsKeys.MODULE9_INTRO_SEEN] ?: defaults.module9IntroSeen,
        dailyReminderEnabled = this[SettingsKeys.DAILY_REMINDER_ENABLED] ?: defaults.dailyReminderEnabled,
        dailyReminderTime = this[SettingsKeys.DAILY_REMINDER_TIME],
    )
}
