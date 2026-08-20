package com.tonic.core.data.settings

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

    /** [enabled] and [time] must agree: false always pairs with a null time - docs/05-DATA-MODEL.md §3. */
    suspend fun setDailyReminder(
        enabled: Boolean,
        time: String?,
    )
}
