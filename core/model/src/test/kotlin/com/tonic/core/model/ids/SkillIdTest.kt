package com.tonic.core.model.ids

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
}
