package com.tonic.core.model.rhythm

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The sentence shown after a tapped attempt — docs/40-PHASE-4-SPEC.md §7.4 and §6.3.
 *
 * §6.3 permits raw asynchrony to be *shown* ("you were a little ahead") while forbidding it from being
 * scored, and this is the whole of the showing. What is worth testing is that it stays a direction: a
 * function that quietly became sensitive to magnitude would be a precision grade wearing words, which
 * §7.4 rules out in as many words.
 */
class TimingNoteTest {
    private val window = 150.0

    @Test
    fun `a learner centred on the beat is told so`() {
        val asynchronies = listOf(5.0, -8.0, 3.0, -2.0)
        assertEquals(TimingNote.ON_BEAT, TimingNote.of(asynchronies, window))
    }

    @Test
    fun `consistently early reads as ahead, consistently late as behind`() {
        val early = List(4) { -80.0 }
        val late = List(4) { 80.0 }
        assertEquals(TimingNote.AHEAD, TimingNote.of(early, window))
        assertEquals(TimingNote.BEHIND, TimingNote.of(late, window))
    }

    @Test
    fun `the note is a direction, not a magnitude`() {
        // Two performances, one twice as far off as the other, both consistently early. The learner is
        // told the same thing - which is the point. §7.4 forbids a precision grade, and a note that
        // sharpened as the error grew would be one delivered in words.
        val slightlyEarly = List(6) { -70.0 }
        val muchEarlier = List(6) { -140.0 }
        assertEquals(TimingNote.of(slightlyEarly, window), TimingNote.of(muchEarlier, window))
    }

    @Test
    fun `it scales with the window rather than with milliseconds`() {
        // The same 40 ms lean is most of a tight window and a fraction of a loose one. §6.2 makes the
        // window a fraction of the beat for exactly this reason; a note fixed in milliseconds would
        // tell a learner they were ahead at one tempo and on the beat at another for one performance.
        val lean = List(4) { -40.0 }
        assertEquals(TimingNote.AHEAD, TimingNote.of(lean, toleranceHalfWidthMs = 50.0))
        assertEquals(TimingNote.ON_BEAT, TimingNote.of(lean, toleranceHalfWidthMs = 300.0))
    }

    @Test
    fun `one wild tap does not decide what the learner is told`() {
        // Median, not mean, and for the same reason Calibrator uses one. The mean here is late; every
        // tap but one is early.
        val asynchronies = listOf(-70.0, -75.0, -68.0, -72.0, 900.0)
        assertEquals(TimingNote.AHEAD, TimingNote.of(asynchronies, window))
    }

    @Test
    fun `a missed event is not a tap that landed on time`() {
        // Nulls are dropped rather than read as zero. Counting them as on-the-beat would tell a
        // learner who played almost nothing that their timing was excellent.
        val asynchronies = listOf(-80.0, null, -85.0, null)
        assertEquals(TimingNote.AHEAD, TimingNote.of(asynchronies, window))
    }

    @Test
    fun `too little to judge says nothing at all`() {
        assertEquals(TimingNote.NONE, TimingNote.of(listOf(-90.0), window))
        assertEquals(TimingNote.NONE, TimingNote.of(listOf(null, null), window))
        assertEquals(TimingNote.NONE, TimingNote.of(emptyList(), window))
    }
}
