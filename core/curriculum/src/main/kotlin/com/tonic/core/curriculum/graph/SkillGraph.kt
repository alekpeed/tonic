package com.tonic.core.curriculum.graph

import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.music.ScaleDegree

/**
 * The Phase 1 skill graph — docs/03-CURRICULUM.md §3/§5. Declarative,
 * defined as immutable code data rather than JSON: "compile-time safety is
 * worth more than runtime editability here" (docs/04-ARCHITECTURE.md §3).
 */
object SkillGraph {
    /** M2's five nodes, in mastery order — docs/03-CURRICULUM.md §5.2. */
    val m2Nodes: List<SkillNode> =
        listOf(
            SkillNode(SkillIds.M2_DEG_SET_1, prerequisite = null, activeDegrees = degrees(1, 3, 5)),
            SkillNode(SkillIds.M2_DEG_SET_2, prerequisite = SkillIds.M2_DEG_SET_1, activeDegrees = degrees(1, 2, 3, 5)),
            SkillNode(
                SkillIds.M2_DEG_SET_3,
                prerequisite = SkillIds.M2_DEG_SET_2,
                activeDegrees = degrees(1, 2, 3, 5, 6),
            ),
            SkillNode(
                SkillIds.M2_DEG_SET_4,
                prerequisite = SkillIds.M2_DEG_SET_3,
                activeDegrees = degrees(1, 2, 3, 4, 5, 6),
            ),
            SkillNode(
                SkillIds.M2_FULL_DIATONIC,
                prerequisite = SkillIds.M2_DEG_SET_4,
                activeDegrees = degrees(1, 2, 3, 4, 5, 6, 7),
            ),
        )

    private val byId: Map<SkillId, SkillNode> = m2Nodes.associateBy { it.id }

    fun node(skillId: SkillId): SkillNode = byId[skillId] ?: error("Not an M2 skill node: $skillId")

    fun activeDegreesFor(skillId: SkillId): Set<ScaleDegree> = node(skillId).activeDegrees

    /** Null for the root node ([SkillIds.M2_DEG_SET_1], whose prerequisite is M0 placement or M1 exit). */
    fun prerequisiteFor(skillId: SkillId): SkillId? = node(skillId).prerequisite

    /** The node immediately after [skillId] in mastery order, or null if it's [SkillIds.M2_FULL_DIATONIC]. */
    fun successorOf(skillId: SkillId): SkillId? {
        val index = m2Nodes.indexOfFirst { it.id == skillId }
        return m2Nodes.getOrNull(index + 1)?.id
    }

    private fun degrees(vararg values: Int): Set<ScaleDegree> = values.map { ScaleDegree(it) }.toSet()
}

/** One M2 skill node: its active degree set and prerequisite. Mastery criteria live in `:core:engine` (Stage 4). */
data class SkillNode(
    val id: SkillId,
    val prerequisite: SkillId?,
    val activeDegrees: Set<ScaleDegree>,
)
