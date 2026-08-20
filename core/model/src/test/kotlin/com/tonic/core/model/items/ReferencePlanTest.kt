package com.tonic.core.model.items

import com.tonic.core.model.music.TimbreId
import kotlin.test.Test
import kotlin.test.assertEquals

/** [ReferencePlan.sequentialDurationMs] is what the practice screen times its "reference" phase from - see its KDoc for the mismatch it fixes. */
class ReferencePlanTest {
    @Test
    fun `sequential duration sums the elements that actually precede the target`() {
        val plan =
            ReferencePlan(
                CadenceFadeLevel.L0,
                listOf(
                    ReferenceElement.ChordEvent(listOf(60, 64, 67), 600, TimbreId.PURE),
                    ReferenceElement.Silence(300),
                    ReferenceElement.ToneEvent(60, 500, TimbreId.PURE),
                ),
            )
        assertEquals(1400, plan.sequentialDurationMs)
    }

    @Test
    fun `a drone underlays the item rather than preceding it, so it contributes nothing`() {
        val plan =
            ReferencePlan(
                CadenceFadeLevel.L4,
                listOf(ReferenceElement.DroneEvent(48, 5_000, TimbreId.PURE, relativeDb = -18.0)),
            )
        assertEquals(0, plan.sequentialDurationMs)
    }

    @Test
    fun `an empty plan - an L1 group item or an L6 block item - sounds for zero ms`() {
        assertEquals(0, ReferencePlan(CadenceFadeLevel.L6, emptyList()).sequentialDurationMs)
    }
}
