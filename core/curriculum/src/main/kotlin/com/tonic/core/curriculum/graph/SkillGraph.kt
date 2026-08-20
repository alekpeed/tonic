package com.tonic.core.curriculum.graph

import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.music.Mode
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

    /**
     * M10's minor nodes — docs/20-PHASE-2-SPEC.md §3. Structurally identical to [m2Nodes]: the same
     * widening degree sets, the same cadence-fade mechanic, the same mastery criteria. What differs is
     * the mode, and therefore which degrees are in the set: minor's third, sixth and seventh carry a
     * flat, so `♭3` is `ScaleDegree(3, -1)` and never a reinterpretation of `3` (§2.1).
     *
     * Stage 2.3 builds sets 1-4; the three minor forms and the independence check are Stage 2.4.
     */
    val m10Nodes: List<SkillNode> =
        listOf(
            SkillNode(
                SkillIds.M10_MIN_SET_1,
                prerequisite = SkillIds.M9_MODE_ID_TRIAD,
                activeDegrees = setOf(ScaleDegree(1), ScaleDegree(3, -1), ScaleDegree(5)),
                mode = Mode.MINOR,
            ),
            SkillNode(
                SkillIds.M10_MIN_SET_2,
                prerequisite = SkillIds.M10_MIN_SET_1,
                activeDegrees = setOf(ScaleDegree(1), ScaleDegree(2), ScaleDegree(3, -1), ScaleDegree(5)),
                mode = Mode.MINOR,
            ),
            SkillNode(
                SkillIds.M10_MIN_SET_3,
                prerequisite = SkillIds.M10_MIN_SET_2,
                activeDegrees =
                    setOf(
                        ScaleDegree(1),
                        ScaleDegree(2),
                        ScaleDegree(3, -1),
                        ScaleDegree(5),
                        ScaleDegree(6, -1),
                    ),
                mode = Mode.MINOR,
            ),
            SkillNode(
                SkillIds.M10_MIN_SET_4,
                prerequisite = SkillIds.M10_MIN_SET_3,
                activeDegrees =
                    setOf(
                        ScaleDegree(1),
                        ScaleDegree(2),
                        ScaleDegree(3, -1),
                        ScaleDegree(4),
                        ScaleDegree(5),
                        ScaleDegree(6, -1),
                    ),
                mode = Mode.MINOR,
            ),
            SkillNode(
                SkillIds.M10_MIN_NATURAL,
                prerequisite = SkillIds.M10_MIN_SET_4,
                activeDegrees = ScaleDegree.ALL_NATURAL_MINOR,
                mode = Mode.MINOR,
            ),
            // Harmonic and melodic minor are natural minor plus an alteration, never separate scales
            // (docs/20-PHASE-2-SPEC.md §2.1). That is what keeps one label meaning one pitch across all
            // three forms - the ♭7 a learner already knows stays ♭7, and the new note is ♮7 beside it.
            SkillNode(
                SkillIds.M10_MIN_HARMONIC,
                prerequisite = SkillIds.M10_MIN_NATURAL,
                activeDegrees = ScaleDegree.ALL_NATURAL_MINOR + ScaleDegree(7),
                mode = Mode.MINOR,
            ),
            SkillNode(
                SkillIds.M10_MIN_MELODIC,
                prerequisite = SkillIds.M10_MIN_HARMONIC,
                activeDegrees = ScaleDegree.ALL_NATURAL_MINOR + ScaleDegree(7) + ScaleDegree(6),
                mode = Mode.MINOR,
            ),
        )

    /**
     * Nodes whose mastery triggers an independence check — docs/03-CURRICULUM.md §5.6 for `M2`, and
     * docs/20-PHASE-2-SPEC.md §3 for `M10`. The last node of each chain: the check asks whether the
     * learner can hold a key without the cadence propping it up, which only means anything once the
     * whole degree set is in play.
     */
    fun triggersIndependenceCheck(skillId: SkillId): Boolean =
        skillId == m2Nodes.last().id || skillId == m10Nodes.last().id

    /** Every recognition node the practice loop can run, in either mode. */
    val recognitionNodes: List<SkillNode> = m2Nodes + m10Nodes

    private val byId: Map<SkillId, SkillNode> = recognitionNodes.associateBy { it.id }

    fun node(skillId: SkillId): SkillNode = byId[skillId] ?: error("Not a recognition skill node: $skillId")

    /**
     * The mode a node's items are generated in. The single place that answers it, so a generator never
     * has to infer mode from a skill id's spelling.
     */
    fun modeFor(skillId: SkillId): Mode = node(skillId).mode

    fun activeDegreesFor(skillId: SkillId): Set<ScaleDegree> = node(skillId).activeDegrees

    /** Null for the root node ([SkillIds.M2_DEG_SET_1], whose prerequisite is M0 placement or M1 exit). */
    fun prerequisiteFor(skillId: SkillId): SkillId? = node(skillId).prerequisite

    /** The node immediately after [skillId] in mastery order, or null if it's [SkillIds.M2_FULL_DIATONIC]. */
    fun successorOf(skillId: SkillId): SkillId? {
        // Within the node's own module: mastering the last M2 node does not roll into M10, which is
        // gated on M9 instead (docs/20-PHASE-2-SPEC.md §3).
        val chain = if (skillId in m10Nodes.map { it.id }) m10Nodes else m2Nodes
        val index = chain.indexOfFirst { it.id == skillId }
        return chain.getOrNull(index + 1)?.id
    }

    private fun degrees(vararg values: Int): Set<ScaleDegree> = values.map { ScaleDegree(it) }.toSet()
}

/** One recognition skill node: its active degree set, prerequisite and mode. Mastery criteria live in `:core:engine` (Stage 4). */
data class SkillNode(
    val id: SkillId,
    val prerequisite: SkillId?,
    val activeDegrees: Set<ScaleDegree>,
    /** Major unless stated. Every Phase 1 node is major, so the default reproduces them exactly. */
    val mode: Mode = Mode.MAJOR,
)
