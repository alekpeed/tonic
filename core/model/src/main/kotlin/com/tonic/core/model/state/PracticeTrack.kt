package com.tonic.core.model.state

/**
 * Which curriculum a practice session works through — docs/40-PHASE-4-SPEC.md §2.
 *
 * Two tracks rather than one order, because §2 makes rhythm genuinely parallel to pitch: `M3` "shares
 * no prerequisites with pitch" and a learner can start `M3.BEAT_FIND` "having never touched `M2`".
 * `SkillGraph` already models that — the two chains partition the graph and no prerequisite crosses
 * between them — and this is the same fact at the level a session is started from.
 *
 * Which one a learner should be doing right now is genuinely undecided (§10 q3), so nothing in the app
 * decides it for them: the learner picks, and the choice rides in on the practice route.
 */
enum class PracticeTrack {
    /** `M2`, `M9`–`M12` — the pitch curriculum, and what every session before Phase 4 ran. */
    PITCH,

    /** `M3` — rhythm. */
    RHYTHM,

    ;

    companion object {
        /**
         * The name this rides under on the practice route.
         *
         * Here rather than on either side, because both the route that writes it (`:app`) and the view
         * model that reads it (`:feature:practice`) need the same string and neither can see the
         * other. A key defined twice is a key that gets renamed once.
         */
        const val ROUTE_ARG: String = "track"

        /**
         * [name] parsed back, falling back to [PITCH].
         *
         * A route argument arrives as text and can be absent, misspelled, or left over from an older
         * build of the app across a process restore. None of those is worth crashing a learner's
         * session over, and the pitch track is what every session did before this argument existed.
         */
        fun parse(raw: String?): PracticeTrack = entries.firstOrNull { it.name.equals(raw, true) } ?: PITCH
    }
}
