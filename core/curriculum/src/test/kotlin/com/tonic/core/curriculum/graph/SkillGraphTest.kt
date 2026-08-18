package com.tonic.core.curriculum.graph

import com.tonic.core.model.ids.SkillIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SkillGraphTest {
    @Test
    fun `active degree sets match docs 03-CURRICULUM section 5-2`() {
        assertEquals(setOf(1, 3, 5), SkillGraph.activeDegreesFor(SkillIds.M2_DEG_SET_1).map { it.degree }.toSet())
        assertEquals(setOf(1, 2, 3, 5), SkillGraph.activeDegreesFor(SkillIds.M2_DEG_SET_2).map { it.degree }.toSet())
        assertEquals(setOf(1, 2, 3, 5, 6), SkillGraph.activeDegreesFor(SkillIds.M2_DEG_SET_3).map { it.degree }.toSet())
        assertEquals(
            setOf(1, 2, 3, 4, 5, 6),
            SkillGraph.activeDegreesFor(SkillIds.M2_DEG_SET_4).map { it.degree }.toSet(),
        )
        assertEquals(
            setOf(1, 2, 3, 4, 5, 6, 7),
            SkillGraph
                .activeDegreesFor(SkillIds.M2_FULL_DIATONIC)
                .map {
                    it.degree
                }.toSet(),
        )
    }

    @Test
    fun `prerequisite chain matches docs 03-CURRICULUM section 5-2`() {
        assertNull(SkillGraph.prerequisiteFor(SkillIds.M2_DEG_SET_1))
        assertEquals(SkillIds.M2_DEG_SET_1, SkillGraph.prerequisiteFor(SkillIds.M2_DEG_SET_2))
        assertEquals(SkillIds.M2_DEG_SET_2, SkillGraph.prerequisiteFor(SkillIds.M2_DEG_SET_3))
        assertEquals(SkillIds.M2_DEG_SET_3, SkillGraph.prerequisiteFor(SkillIds.M2_DEG_SET_4))
        assertEquals(SkillIds.M2_DEG_SET_4, SkillGraph.prerequisiteFor(SkillIds.M2_FULL_DIATONIC))
    }

    @Test
    fun `successorOf walks the mastery order and ends at null`() {
        assertEquals(SkillIds.M2_DEG_SET_2, SkillGraph.successorOf(SkillIds.M2_DEG_SET_1))
        assertEquals(SkillIds.M2_FULL_DIATONIC, SkillGraph.successorOf(SkillIds.M2_DEG_SET_4))
        assertNull(SkillGraph.successorOf(SkillIds.M2_FULL_DIATONIC))
    }
}
