package com.tonic.core.data.settings

import com.tonic.core.model.rhythm.CalibrationSlot
import com.tonic.core.model.rhythm.RhythmCalibration
import com.tonic.core.model.state.AppSettings
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.model.state.ThemeMode
import kotlinx.coroutines.flow.Flow

/** Typed accessors over the DataStore-backed settings bundle — docs/05-DATA-MODEL.md §3/§5. */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setLabelStyle(style: LabelStyle)

    suspend fun setReferenceA4Hz(hz: Float)

    suspend fun setSessionLengthMinutes(minutes: Int)

    suspend fun setHapticsEnabled(enabled: Boolean)

    suspend fun setSoundEffectsEnabled(enabled: Boolean)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setReduceMotion(enabled: Boolean)

    suspend fun setOnboardingCompleted(completed: Boolean)

    suspend fun setDiagnosticCompleted(completed: Boolean)

    /** See [com.tonic.core.model.state.AppSettings.module2IntroSeen]. */
    suspend fun setModule2IntroSeen(seen: Boolean)

    suspend fun setModule10IntroSeen(seen: Boolean)

    /** See [com.tonic.core.model.state.AppSettings.module9IntroSeen]. */
    suspend fun setModule9IntroSeen(seen: Boolean)

    /** See [com.tonic.core.model.state.AppSettings.module11IntroSeen]. */
    suspend fun setModule11IntroSeen(seen: Boolean)

    /** See [com.tonic.core.model.state.AppSettings.module12IntroSeen]. */
    suspend fun setModule12IntroSeen(seen: Boolean)

    /** See [com.tonic.core.model.state.AppSettings.mixedModeIntroSeen]. */
    suspend fun setMixedModeIntroSeen(seen: Boolean)

    /**
     * Stores one route's measured timing constant — docs/40-PHASE-4-SPEC.md §4.3.
     *
     * Per slot, never global. The slot must be the one the run was actually measured on: a constant
     * measured through the speaker and written to the wired slot is worse than no calibration at all,
     * because an uncalibrated route blocks and explains itself while a wrongly-calibrated one scores
     * the learner confidently and wrongly.
     */
    suspend fun setRhythmCalibration(
        slot: CalibrationSlot,
        calibration: RhythmCalibration,
    )

    /**
     * Forgets one route's constant, returning it to the uncalibrated state.
     *
     * §4.3 requires a route change to "either re-calibrate or invalidate the stored constant," and this
     * is the invalidate half. Also what a learner's "calibrate again" reaches if a run then fails —
     * better to block and say why than to leave a constant nobody trusts silently in force.
     */
    suspend fun clearRhythmCalibration(slot: CalibrationSlot)

    /** See [com.tonic.core.model.state.AppSettings.sungResponseEnabled]. */
    suspend fun setSungResponseEnabled(enabled: Boolean)

    /** See [com.tonic.core.model.state.AppSettings.sungOctaveAgnostic]. */
    suspend fun setSungOctaveAgnostic(enabled: Boolean)

    /** See [com.tonic.core.model.state.AppSettings.sungResponseIntroSeen]. */
    suspend fun setSungResponseIntroSeen(seen: Boolean)

    /** [enabled] and [time] must agree: false always pairs with a null time - docs/05-DATA-MODEL.md §3. */
    suspend fun setDailyReminder(
        enabled: Boolean,
        time: String?,
    )
}
