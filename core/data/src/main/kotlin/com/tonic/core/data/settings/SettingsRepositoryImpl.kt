package com.tonic.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tonic.core.model.rhythm.CalibrationSlot
import com.tonic.core.model.rhythm.RhythmCalibration
import com.tonic.core.model.rhythm.RhythmCalibrations
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
    val MODULE10_INTRO_SEEN = booleanPreferencesKey("module10_intro_seen")
    val MODULE11_INTRO_SEEN = booleanPreferencesKey("module11_intro_seen")
    val MODULE12_INTRO_SEEN = booleanPreferencesKey("module12_intro_seen")
    val MIXED_MODE_INTRO_SEEN = booleanPreferencesKey("mixed_mode_intro_seen")
    val SUNG_RESPONSE_ENABLED = booleanPreferencesKey("sung_response_enabled")
    val SUNG_OCTAVE_AGNOSTIC = booleanPreferencesKey("sung_octave_agnostic")
    val SUNG_RESPONSE_INTRO_SEEN = booleanPreferencesKey("sung_response_intro_seen")

    /**
     * Rhythm calibration, per output route - docs/40-PHASE-4-SPEC.md §4.3. Four keys rather than the
     * two that section's settings list names: the spread is stored alongside each offset because §4.3
     * step 5 measures it and then requires tolerance windows to widen for a learner whose taps scatter,
     * which cannot happen if the number is computed and thrown away. Absent means never calibrated on
     * that route, which is a state the app acts on rather than a missing value to default.
     */
    val RHYTHM_CALIBRATION_OFFSET_SPEAKER = floatPreferencesKey("rhythm_calibration_offset_speaker")
    val RHYTHM_CALIBRATION_SPREAD_SPEAKER = floatPreferencesKey("rhythm_calibration_spread_speaker")
    val RHYTHM_CALIBRATION_TAPS_SPEAKER = intPreferencesKey("rhythm_calibration_taps_speaker")
    val RHYTHM_CALIBRATION_OFFSET_WIRED = floatPreferencesKey("rhythm_calibration_offset_wired")
    val RHYTHM_CALIBRATION_SPREAD_WIRED = floatPreferencesKey("rhythm_calibration_spread_wired")
    val RHYTHM_CALIBRATION_TAPS_WIRED = intPreferencesKey("rhythm_calibration_taps_wired")

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

        override suspend fun setModule10IntroSeen(seen: Boolean) {
            dataStore.edit { it[SettingsKeys.MODULE10_INTRO_SEEN] = seen }
        }

        override suspend fun setModule11IntroSeen(seen: Boolean) {
            dataStore.edit { it[SettingsKeys.MODULE11_INTRO_SEEN] = seen }
        }

        override suspend fun setModule12IntroSeen(seen: Boolean) {
            dataStore.edit { it[SettingsKeys.MODULE12_INTRO_SEEN] = seen }
        }

        override suspend fun setRhythmCalibration(
            slot: CalibrationSlot,
            calibration: RhythmCalibration,
        ) {
            val keys = calibrationKeys(slot)
            dataStore.edit {
                it[keys.offset] = calibration.offsetMs.toFloat()
                it[keys.spread] = calibration.spreadMs.toFloat()
                it[keys.taps] = calibration.tapsUsed
            }
        }

        override suspend fun clearRhythmCalibration(slot: CalibrationSlot) {
            val keys = calibrationKeys(slot)
            dataStore.edit {
                it.remove(keys.offset)
                it.remove(keys.spread)
                it.remove(keys.taps)
            }
        }

        override suspend fun setSungResponseEnabled(enabled: Boolean) {
            dataStore.edit { it[SettingsKeys.SUNG_RESPONSE_ENABLED] = enabled }
        }

        override suspend fun setSungOctaveAgnostic(enabled: Boolean) {
            dataStore.edit { it[SettingsKeys.SUNG_OCTAVE_AGNOSTIC] = enabled }
        }

        override suspend fun setSungResponseIntroSeen(seen: Boolean) {
            dataStore.edit { it[SettingsKeys.SUNG_RESPONSE_INTRO_SEEN] = seen }
        }

        override suspend fun setMixedModeIntroSeen(seen: Boolean) {
            dataStore.edit { it[SettingsKeys.MIXED_MODE_INTRO_SEEN] = seen }
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

/** The three keys one [CalibrationSlot] is stored under. */
private class CalibrationKeys(
    val offset: Preferences.Key<Float>,
    val spread: Preferences.Key<Float>,
    val taps: Preferences.Key<Int>,
)

/**
 * Slot to keys, in one place.
 *
 * One mapping, used by both the write and the clear, so a route can never be written under one slot's
 * keys and cleared under another's - which would leave a stale constant applying to a route the learner
 * believes they reset.
 */
private fun calibrationKeys(slot: CalibrationSlot): CalibrationKeys =
    when (slot) {
        CalibrationSlot.SPEAKER ->
            CalibrationKeys(
                SettingsKeys.RHYTHM_CALIBRATION_OFFSET_SPEAKER,
                SettingsKeys.RHYTHM_CALIBRATION_SPREAD_SPEAKER,
                SettingsKeys.RHYTHM_CALIBRATION_TAPS_SPEAKER,
            )

        CalibrationSlot.WIRED ->
            CalibrationKeys(
                SettingsKeys.RHYTHM_CALIBRATION_OFFSET_WIRED,
                SettingsKeys.RHYTHM_CALIBRATION_SPREAD_WIRED,
                SettingsKeys.RHYTHM_CALIBRATION_TAPS_WIRED,
            )
    }

/**
 * One route's stored calibration, or null if it has never been measured there.
 *
 * Keyed on the *offset* being present, not on all three keys. The offset is the constant scoring
 * applies; a stored offset with a missing spread is a partial write, not an uncalibrated route, and
 * defaulting the two diagnostic fields is better than discarding a real measurement.
 */
private fun Preferences.readCalibration(
    offsetKey: Preferences.Key<Float>,
    spreadKey: Preferences.Key<Float>,
    tapsKey: Preferences.Key<Int>,
): RhythmCalibration? {
    val offset = this[offsetKey] ?: return null
    return RhythmCalibration(
        offsetMs = offset.toDouble(),
        spreadMs = (this[spreadKey] ?: 0f).toDouble(),
        tapsUsed = this[tapsKey] ?: 0,
    )
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
        module10IntroSeen = this[SettingsKeys.MODULE10_INTRO_SEEN] ?: defaults.module10IntroSeen,
        module11IntroSeen = this[SettingsKeys.MODULE11_INTRO_SEEN] ?: defaults.module11IntroSeen,
        module12IntroSeen = this[SettingsKeys.MODULE12_INTRO_SEEN] ?: defaults.module12IntroSeen,
        mixedModeIntroSeen = this[SettingsKeys.MIXED_MODE_INTRO_SEEN] ?: defaults.mixedModeIntroSeen,
        sungResponseEnabled = this[SettingsKeys.SUNG_RESPONSE_ENABLED] ?: defaults.sungResponseEnabled,
        sungOctaveAgnostic = this[SettingsKeys.SUNG_OCTAVE_AGNOSTIC] ?: defaults.sungOctaveAgnostic,
        sungResponseIntroSeen =
            this[SettingsKeys.SUNG_RESPONSE_INTRO_SEEN] ?: defaults.sungResponseIntroSeen,
        rhythmCalibrations =
            RhythmCalibrations(
                speaker =
                    readCalibration(
                        SettingsKeys.RHYTHM_CALIBRATION_OFFSET_SPEAKER,
                        SettingsKeys.RHYTHM_CALIBRATION_SPREAD_SPEAKER,
                        SettingsKeys.RHYTHM_CALIBRATION_TAPS_SPEAKER,
                    ),
                wired =
                    readCalibration(
                        SettingsKeys.RHYTHM_CALIBRATION_OFFSET_WIRED,
                        SettingsKeys.RHYTHM_CALIBRATION_SPREAD_WIRED,
                        SettingsKeys.RHYTHM_CALIBRATION_TAPS_WIRED,
                    ),
            ),
        dailyReminderEnabled = this[SettingsKeys.DAILY_REMINDER_ENABLED] ?: defaults.dailyReminderEnabled,
        dailyReminderTime = this[SettingsKeys.DAILY_REMINDER_TIME],
    )
}
