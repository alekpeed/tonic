package com.tonic.core.model.state

import com.tonic.core.model.rhythm.RhythmCalibrations

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
    /**
     * Whether each tap on the rhythm tap surface makes a sound — docs/40-PHASE-4-SPEC.md §7.2.
     *
     * **Off by default, and that is a decision rather than a starting point.** §7.2 calls audible tap
     * feedback "genuinely double-edged": it helps a learner hear their own timing against the
     * metronome, and it also adds output latency to their own feedback loop and can mask the pattern
     * they are trying to reproduce. Haptic and visual feedback are unconditional; this one is the
     * learner's call, and §7.2's note that it should be revisited with real testing stands.
     *
     * Separate from [soundEffectsEnabled], which governs the app's own interface sounds. This is part
     * of the exercise rather than decoration on it, and someone who wants a silent interface may still
     * want to hear their own tapping.
     */
    val audibleTapsEnabled: Boolean = false,
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
    /**
     * The minor-mode explanation screen has been shown once (docs/05-DATA-MODEL.md §3
     * `module10_intro_seen`). A separate flag from [module2IntroSeen] on purpose: minor is a new task
     * shape under docs/08-UI-SPEC.md §3a, and having seen the major explanation says nothing about
     * whether a learner has been told what `♭3` means.
     */
    val module10IntroSeen: Boolean = false,
    /**
     * The Module 9 (mode identification) explanation screen and its worked example have been shown
     * once. Its own flag rather than a shared "intros seen" one, because docs/08-UI-SPEC.md §3a is
     * per *task shape*: having met degree identification tells a user nothing about being asked
     * whether something sounds major or minor.
     */
    val module9IntroSeen: Boolean = false,
    /**
     * The chromatic-degrees explanation screen has been shown once (docs/05-DATA-MODEL.md §3
     * `module11_intro_seen`). Its own flag for the same docs/08-UI-SPEC.md §3a reason as the others:
     * knowing what `♭3` means in minor does not tell a learner why a note exists between 4 and 5 in
     * major, which is the belief `M11` has to overturn.
     */
    val module11IntroSeen: Boolean = false,
    /**
     * The audiation explanation screen has been shown once (docs/05-DATA-MODEL.md §3
     * `module12_intro_seen`). The most load-bearing of these flags: docs/20-PHASE-2-SPEC.md §5.1 calls
     * `M12`'s worked example "the only way this task is comprehensible," and a learner who reaches a
     * silent screen without having seen it has no way to know the silence is the exercise.
     */
    val module12IntroSeen: Boolean = false,
    /**
     * The mixed-mode explanation has been shown once (docs/05-DATA-MODEL.md §3
     * `mixed_mode_intro_seen`). Its own flag, not `M10`'s: having been told what `♭3` means says
     * nothing about being told that the mode will stop being announced and the ladder has grown to ten
     * buttons because of it.
     */
    val mixedModeIntroSeen: Boolean = false,
    /**
     * Whether the learner has opted into answering by singing — docs/30-PHASE-3-SPEC.md §7,
     * `sung_response_enabled`, default false.
     *
     * Default false is not merely a conservative starting value: §2 makes singing permanently optional,
     * and §6.1 requires the microphone permission to be requested only when the learner actively opts
     * in, never at install or first launch. A default of true would request the microphone from someone
     * who never asked to sing, which is the behavior that rule exists to forbid.
     */
    val sungResponseEnabled: Boolean = false,
    /**
     * Whether a sung degree counts in any octave — docs/30-PHASE-3-SPEC.md §7, `sung_octave_agnostic`,
     * default **true**.
     *
     * True by default because §3 mitigation 3 makes octave-agnosticism a mitigation against the phase's
     * central risk, not a convenience: forcing a specific octave tests vocal range rather than hearing,
     * and vocal range varies enormously between learners. Turning it off is the unusual choice.
     */
    val sungOctaveAgnostic: Boolean = true,
    /**
     * Whether the sung-response explanation has been shown once — docs/08-UI-SPEC.md §3a's per-shape
     * flag, applied to singing. Answering by voice is a different answer control and a different thing
     * to do, so it is its own task shape and gets its own flag rather than riding on any module's.
     */
    val sungResponseIntroSeen: Boolean = false,
    /**
     * The learner's measured timing constants for rhythm production, one per output route —
     * docs/40-PHASE-4-SPEC.md §4.3, `rhythm_calibration_*`.
     *
     * Empty by default, and that is a meaningful state rather than a placeholder: an uncalibrated route
     * blocks tapping with an explanation (§9 simulation 6), because a missing constant applied as zero
     * is indistinguishable from a perfectly-calibrated device and wrong by the device's whole output
     * latency. It sits in settings rather than Room for the same reason everything else here does —
     * losing it on reinstall costs one short calibration screen, not a learner's progress.
     */
    val rhythmCalibrations: RhythmCalibrations = RhythmCalibrations(),
    /** Opt-in only - docs/05-DATA-MODEL.md §3: "defaults to false. Opt-in only." See docs/08-UI-SPEC.md §7. */
    val dailyReminderEnabled: Boolean = false,
    /** `"HH:mm"`, null when [dailyReminderEnabled] is false. */
    val dailyReminderTime: String? = null,
)
