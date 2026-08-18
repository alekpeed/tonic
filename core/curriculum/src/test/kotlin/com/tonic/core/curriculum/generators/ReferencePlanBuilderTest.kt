package com.tonic.core.curriculum.generators

import com.tonic.core.model.items.CadenceFadeLevel
import com.tonic.core.model.items.ReferenceElement
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.PitchClass
import com.tonic.core.model.music.TimbreId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReferencePlanBuilderTest {
    private fun build(level: CadenceFadeLevel) =
        ReferencePlanBuilder.build(
            cadenceFadeLevel = level,
            keyPitchClass = PitchClass(0),
            mode = Mode.MAJOR,
            tonicMidi = 60,
            timbre = TimbreId.SOFT,
            chordDurationMs = 600,
            gapAfterReferenceMs = 300,
            targetDurationMs = 700,
            seed = 1L,
        )

    @Test
    fun `L0 is four chords - the I-IV-V-I cadence`() {
        val plan = build(CadenceFadeLevel.L0)
        assertEquals(4, plan.elements.size)
        assertTrue(plan.elements.all { it is ReferenceElement.ChordEvent })
        val roots = plan.elements.map { (it as ReferenceElement.ChordEvent).midiNotes.first() }
        assertEquals(listOf(60, 65, 67, 60), roots) // I(60) IV(65) V(67) I(60) in C major
    }

    @Test
    fun `L2 is V-I - two chords`() {
        val plan = build(CadenceFadeLevel.L2)
        assertEquals(2, plan.elements.size)
        val roots = plan.elements.map { (it as ReferenceElement.ChordEvent).midiNotes.first() }
        assertEquals(listOf(67, 60), roots)
    }

    @Test
    fun `L3 is the tonic triad only at 800ms`() {
        val plan = build(CadenceFadeLevel.L3)
        assertEquals(1, plan.elements.size)
        val chord = plan.elements.single() as ReferenceElement.ChordEvent
        assertEquals(listOf(60, 64, 67), chord.midiNotes) // C E G
        assertEquals(800, chord.durationMs)
    }

    @Test
    fun `L4 is a single drone spanning gap plus target`() {
        val plan = build(CadenceFadeLevel.L4)
        val drone = plan.elements.single() as ReferenceElement.DroneEvent
        assertEquals(60, drone.midi)
        assertEquals(-18.0, drone.relativeDb)
        assertEquals(300L + 700L, drone.durationMs)
    }

    @Test
    fun `L5 is a tone then a 1500ms silence`() {
        val plan = build(CadenceFadeLevel.L5)
        assertEquals(2, plan.elements.size)
        assertIs<ReferenceElement.ToneEvent>(plan.elements[0])
        val silence = plan.elements[1] as ReferenceElement.Silence
        assertEquals(1500, silence.durationMs)
    }

    @Test
    fun `L6 and L7 are empty per-item plans`() {
        assertEquals(emptyList(), build(CadenceFadeLevel.L6).elements)
        assertEquals(emptyList(), build(CadenceFadeLevel.L7).elements)
    }

    @Test
    fun `chord voicings are triads a third and a fifth above the root`() {
        val plan = build(CadenceFadeLevel.L3)
        val chord = plan.elements.single() as ReferenceElement.ChordEvent
        assertEquals(3, chord.midiNotes.size)
        assertEquals(4, chord.midiNotes[1] - chord.midiNotes[0]) // major third
        assertEquals(7, chord.midiNotes[2] - chord.midiNotes[0]) // perfect fifth
    }
}
