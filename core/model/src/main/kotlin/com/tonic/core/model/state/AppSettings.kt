package com.tonic.core.model.state

/** How scale degrees are displayed — docs/05-DATA-MODEL.md §3 `label_style`. Never changes what's stored as a targetLabel, only how it's shown. */
enum class LabelStyle { NUMBERS, SOLFEGE }

/** docs/05-DATA-MODEL.md §3 `theme_mode`. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The full settings bundle — docs/05-DATA-MODEL.md §3. Backed by DataStore
 * Preferences, not Room (settings, not progress data — losing settings on
 * reinstall is fine; losing ear-training progress is not).
 */
data class AppSettings(
    val labelStyle: LabelStyle = LabelStyle.NUMBERS,
    val referenceA4Hz: Float = 440.0f,
    val sessionLengthMinutes: Int = 5,
    val hapticsEnabled: Boolean = true,
    val soundEffectsEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val reduceMotion: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val diagnosticCompleted: Boolean = false,
    /**
     * The Module 2 explanation screen and its worked example have been shown once
     * (docs/05-DATA-MODEL.md §3 `module2_intro_seen`). Gates only the *automatic* showing -
     * docs/11-ONBOARDING-CLARITY.md §5: "It is never shown again automatically... It is always
     * reachable on demand," and recalling it neither depends on nor changes this.
     */
    val module2IntroSeen: Boolean = false,
    /** Opt-in only - docs/05-DATA-MODEL.md §3: "defaults to false. Opt-in only." See docs/08-UI-SPEC.md §7. */
    val dailyReminderEnabled: Boolean = false,
    /** `"HH:mm"`, null when [dailyReminderEnabled] is false. */
    val dailyReminderTime: String? = null,
)
