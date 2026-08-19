package com.tonic.core.engine.streak

import java.time.LocalDate

/**
 * The Home screen's passive streak counter — docs/08-UI-SPEC.md §7: "a missed day does not reset it
 * immediately; a grace mechanism absorbs occasional misses." Pure and deterministic (CLAUDE.md §5):
 * [today] is supplied by the caller rather than read from the system clock, and [practiceDates] is
 * plain data the caller derives from session history, so this is a straightforward pure function of
 * its inputs.
 */
object StreakCalculator {
    /**
     * docs/08-UI-SPEC.md §7 doesn't name an exact grace budget - this build's own tuning, same category
     * as the diagnostic's undocumented constants (Stage 8's `AMUSIA_DPRIME_CUT`). One grace day forgives
     * the single most common lapse ("missed yesterday, back today") without turning the counter into one
     * that never resets; needs calibration against real usage before shipping.
     */
    const val DEFAULT_GRACE_DAYS = 1

    /**
     * Walks backward day by day from [today], counting consecutive practiced days. [today] itself is
     * never required - the day isn't over yet, so not having practiced *yet* today must not break a
     * streak that is otherwise intact. Every other gap consumes one of [graceDays] budgeted forgiveness
     * days; the walk stops, and the streak so far is returned, the first time a gap is hit with no grace
     * left. A large gap further in the past than that never resets an otherwise-current streak to zero -
     * it simply isn't counted, which is the entire point of "does not reset it immediately."
     */
    fun currentStreak(
        practiceDates: Set<LocalDate>,
        today: LocalDate,
        graceDays: Int = DEFAULT_GRACE_DAYS,
    ): Int {
        if (practiceDates.isEmpty()) return 0

        var streak = 0
        var graceRemaining = graceDays
        var cursor = if (today in practiceDates) today else today.minusDays(1)

        while (true) {
            when {
                cursor in practiceDates -> {
                    streak++
                    cursor = cursor.minusDays(1)
                }
                graceRemaining > 0 -> {
                    graceRemaining--
                    cursor = cursor.minusDays(1)
                }
                else -> return streak
            }
        }
    }
}
