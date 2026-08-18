package com.tonic.core.ui.labels

import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.LabelStyle
import kotlin.test.Test
import kotlin.test.assertEquals

/** docs/02-PEDAGOGY.md §2: "1=do, 2=re, 3=mi, 4=fa, 5=sol, 6=la, 7=ti." */
class DegreeLabelsTest {
    @Test
    fun `numbers style renders the raw degree number`() {
        for (degree in 1..7) {
            assertEquals(degree.toString(), ScaleDegree(degree).displayLabel(LabelStyle.NUMBERS))
        }
    }

    @Test
    fun `solfege style maps every diatonic degree to its movable-do syllable`() {
        val expected = mapOf(1 to "Do", 2 to "Re", 3 to "Mi", 4 to "Fa", 5 to "Sol", 6 to "La", 7 to "Ti")
        for ((degree, syllable) in expected) {
            assertEquals(syllable, ScaleDegree(degree).displayLabel(LabelStyle.SOLFEGE))
        }
    }
}
