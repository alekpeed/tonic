package com.tonic.core.model.time

import java.time.Instant

/**
 * Every place that would otherwise read the wall clock — item generation,
 * scheduling, mastery evaluation — takes a [Clock] instead. This is what
 * makes those functions replayable from a stored seed and testable with a
 * fixed instant instead of real time. See CLAUDE.md §5.
 */
interface Clock {
    fun now(): Instant
}

/** The only production implementation. Everything else in `:core:*` should be testing against a fake. */
class SystemClock : Clock {
    override fun now(): Instant = Instant.now()
}
