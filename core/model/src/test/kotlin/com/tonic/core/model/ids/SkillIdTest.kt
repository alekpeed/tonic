package com.tonic.core.model.ids

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SkillIdTest {
    @Test
    fun `accepts module dot skill`() {
        val id = SkillId("M2.DEG_SET_1")
        assertEquals(ModuleId.M2, id.moduleId)
        assertEquals("M2.DEG_SET_1", id.toString())
    }

    @Test
    fun `accepts module dot skill dot variant`() {
        val id = SkillId("M2.DEG_SET_1.EASY")
        assertEquals(ModuleId.M2, id.moduleId)
    }

    @Test
    fun `rejects malformed ids`() {
        assertFailsWith<IllegalArgumentException> { SkillId("nonsense") }
        assertFailsWith<IllegalArgumentException> { SkillId("M2") }
        assertFailsWith<IllegalArgumentException> { SkillId("M2.lowercase") }
        assertFailsWith<IllegalArgumentException> { SkillId("2.DEG_SET_1") }
    }

    @Test
    fun `well known ids are all well formed and map to the right module`() {
        assertEquals(ModuleId.M0, SkillIds.M0_PITCH_DIR.moduleId)
        assertEquals(ModuleId.M1, SkillIds.M1_HIGH_LOW.moduleId)
        assertEquals(ModuleId.M2, SkillIds.M2_FULL_DIATONIC.moduleId)
        assertEquals(ModuleId.M8, SkillIds.M8_MODAL.moduleId)
    }

    @Test
    fun `m2 nodes are in mastery order`() {
        assertEquals(
            listOf(
                SkillIds.M2_DEG_SET_1,
                SkillIds.M2_DEG_SET_2,
                SkillIds.M2_DEG_SET_3,
                SkillIds.M2_DEG_SET_4,
                SkillIds.M2_FULL_DIATONIC,
            ),
            SkillIds.M2_NODES_IN_ORDER,
        )
    }

    @Test
    fun `module id round trips through its code`() {
        ModuleId.entries.forEach { assertEquals(it, ModuleId.fromCode(it.code)) }
        assertFailsWith<IllegalArgumentException> { ModuleId.fromCode("M99") }
    }

    @Test
    fun `M7 does not exist, and its number is not reused`() {
        // The real-music bridge was removed from the product entirely rather than deferred, so unlike
        // the reserved M3-M6 and M8 codes there is nothing coming that would claim this slot. An enum
        // gap is exactly the kind of thing a later edit refills without noticing, so it is pinned:
        // any M7.* surviving in a stored attempt log from a build that predates the removal must fail
        // loudly rather than resolve to whatever module inherited the number.
        assertTrue(ModuleId.entries.none { it.code == "M7" })
        assertFailsWith<IllegalArgumentException> { ModuleId.fromCode("M7") }
        assertFailsWith<IllegalArgumentException> { SkillId("M7.REAL_MELODY").moduleId }

        // And the codes around it are untouched, so the removal took nothing else with it.
        assertEquals(ModuleId.M6, ModuleId.fromCode("M6"))
        assertEquals(ModuleId.M8, ModuleId.fromCode("M8"))
    }
}
