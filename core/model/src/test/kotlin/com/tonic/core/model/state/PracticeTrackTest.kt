package com.tonic.core.model.state

import kotlin.test.Test
import kotlin.test.assertEquals

/** Which curriculum a session walks — docs/40-PHASE-4-SPEC.md §2. */
class PracticeTrackTest {
    @Test
    fun `a route argument round-trips`() {
        for (track in PracticeTrack.entries) {
            assertEquals(track, PracticeTrack.parse(track.name))
        }
    }

    @Test
    fun `anything unreadable falls back to pitch`() {
        // A route argument arrives as text and can be absent, misspelled, or left over from an older
        // build across a process restore. None of those is worth crashing a session over, and pitch
        // is what every session did before the argument existed.
        assertEquals(PracticeTrack.PITCH, PracticeTrack.parse(null))
        assertEquals(PracticeTrack.PITCH, PracticeTrack.parse(""))
        assertEquals(PracticeTrack.PITCH, PracticeTrack.parse("percussion"))
    }

    @Test
    fun `case does not matter`() {
        assertEquals(PracticeTrack.RHYTHM, PracticeTrack.parse("rhythm"))
    }
}
