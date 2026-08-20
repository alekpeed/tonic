package com.tonic.core.curriculum.graph

import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
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
     * M11's chromatic nodes — docs/20-PHASE-2-SPEC.md §3, in §2.2's introduction order: strongest pull
     * toward a stable tone first, because a strong pull is easier to hear. Each node adds exactly one
     * degree to the previous set, "introduced against the already-mastered diatonic set, never in
     * isolation" — the skill is telling `♯4` from the `4` and `5` it sits between, which only exists as
     * a question when all three are on screen.
     */
    val m11Nodes: List<SkillNode> =
        run {
            val diatonic = ScaleDegree.ALL_DIATONIC
            val ids =
                listOf(
                    SkillIds.M11_CHROM_SHARP4,
                    SkillIds.M11_CHROM_FLAT7,
                    SkillIds.M11_CHROM_FLAT6,
                    SkillIds.M11_CHROM_FLAT3,
                    SkillIds.M11_CHROM_FLAT2,
                )
            var accumulated = diatonic
            val progressive =
                ids.mapIndexed { index, id ->
                    accumulated = accumulated + ScaleDegree.CHROMATIC_INTRODUCTION_ORDER[index]
                    SkillNode(
                        id = id,
                        prerequisite = if (index == 0) SkillIds.M2_INDEPENDENCE_CHECK else ids[index - 1],
                        activeDegrees = accumulated,
                        introduces = ScaleDegree.CHROMATIC_INTRODUCTION_ORDER[index],
                    )
                }
            // CHROM_FULL introduces nothing new - all twelve are already in play by CHROM_FLAT2. It is
            // the consolidation node, and its mastery is judged on the whole set rather than on one
            // degree, which is why it has no focus degree (see focusDegreeFor).
            progressive + SkillNode(SkillIds.M11_CHROM_FULL, prerequisite = ids.last(), activeDegrees = accumulated)
        }

    /**
     * `M12`'s prediction nodes — docs/20-PHASE-2-SPEC.md §3. They widen the pool the *stated* degree
     * is drawn from; the interaction itself never changes shape (§8.1 decision 3).
     *
     * `PREDICT_MINOR` and `PREDICT_CHROMATIC` both follow `PREDICT_DIATONIC` and neither precedes the
     * other, so this is a fan rather than a chain. Each declares the additional prerequisite the spec
     * names — minor prediction needs `M10.MIN_NATURAL`, chromatic prediction needs `M11.CHROM_FULL` —
     * in [alsoRequires], because a node has one structural parent and these have two real gates.
     */
    val m12Nodes: List<SkillNode> =
        listOf(
            SkillNode(
                SkillIds.M12_PREDICT_TRIAD,
                prerequisite = SkillIds.M2_FULL_DIATONIC,
                activeDegrees = ScaleDegree.TONIC_TRIAD,
            ),
            SkillNode(
                SkillIds.M12_PREDICT_DIATONIC,
                prerequisite = SkillIds.M12_PREDICT_TRIAD,
                activeDegrees = ScaleDegree.ALL_DIATONIC,
            ),
            SkillNode(
                SkillIds.M12_PREDICT_MINOR,
                prerequisite = SkillIds.M12_PREDICT_DIATONIC,
                activeDegrees = ScaleDegree.ALL_NATURAL_MINOR,
                mode = Mode.MINOR,
                alsoRequires = SkillIds.M10_MIN_NATURAL,
            ),
            SkillNode(
                SkillIds.M12_PREDICT_CHROMATIC,
                prerequisite = SkillIds.M12_PREDICT_DIATONIC,
                activeDegrees = ScaleDegree.ALL_CHROMATIC,
                alsoRequires = SkillIds.M11_CHROM_FULL,
            ),
        )

    /**
     * Which family of difficulty axes and which mastery evaluator a node belongs to.
     *
     * A node's *scope* is the thing that decides how it is scheduled and how it is judged, and it is
     * asked for often enough — by the axis scheduler, the replayer, the session composer — that
     * deriving it from a module-id spelling at each call site is how the `moduleId != M2` bug in
     * `SkillStateReducer` happened. Answered once, here.
     */
    fun scopeFor(skillId: SkillId): DifficultyAxis.Scope =
        when (skillId) {
            in m12Nodes.map { it.id } -> DifficultyAxis.Scope.PREDICTION
            else -> DifficultyAxis.Scope.RECOGNITION
        }

    /**
     * Whether a prediction node scores the *direction* of a mismatch, or only that one was detected —
     * docs/20-PHASE-2-SPEC.md §8.1 decision 3.
     *
     * False at `M12.PREDICT_TRIAD` only. The three-button layout is present from the module's first
     * item so the interaction never changes shape, but naming the direction of a mismatch is
     * `M1.HIGH_LOW`'s skill, and the introductory audiation node must not fail a learner for it.
     * Non-prediction nodes answer true vacuously: they have no direction to collapse.
     */
    fun scoresDirection(skillId: SkillId): Boolean = skillId != SkillIds.M12_PREDICT_TRIAD

    /**
     * The one degree a node introduces and is judged on, or null.
     *
     * Declared by the node rather than derived by differencing it against its prerequisite. Differencing
     * looked tidier and was wrong twice over: `M11.CHROM_SHARP4`'s prerequisite is
     * `M2.INDEPENDENCE_CHECK`, a gate rather than a degree-set parent, so the difference was undefined
     * for the very first chromatic node; and it would have handed a focus degree to every `M2` and `M10`
     * node too, applying docs/20-PHASE-2-SPEC.md §3's sixth criterion to nodes the spec never asks it
     * of. The nodes that have one declare it at construction, beside the degree they add, so the two
     * cannot disagree.
     */
    fun focusDegreeFor(skillId: SkillId): ScaleDegree? = byId[skillId]?.introduces

    /**
     * Sampling weights for a node's target degrees, or empty for uniform.
     *
     * Only `M11` returns anything: its nodes each introduce one chromatic degree, and that degree has
     * to appear often enough for [com.tonic.core.engine.mastery.MasteryEvaluator]'s focus criterion to
     * have a real sample behind it. Uniform sampling across twelve active degrees gives roughly two
     * attempts per degree in a 30-item window, and two answers cannot distinguish hearing a note from
     * guessing it. Weighting is also the pedagogically right shape: §2.2's "each is introduced against
     * the already-mastered diatonic set" means the new note is the *subject* of the node, not one
     * twelfth of it.
     *
     * Deliberately empty for `M2` and `M10`, whose degree sets widen by one too: applying this to them
     * would change long-settled generation for no benefit, and the Stage 2.0 golden corpus would
     * (correctly) reject it.
     */
    fun degreeWeightsFor(skillId: SkillId): Map<ScaleDegree, Double> {
        if (skillId !in m11Nodes.map { it.id }) return emptyMap()
        val focus = focusDegreeFor(skillId) ?: return emptyMap()
        return mapOf(focus to CHROMATIC_FOCUS_WEIGHT)
    }

    /** Enough to lift the new degree clear of the focus criterion's five-attempt floor. */
    private const val CHROMATIC_FOCUS_WEIGHT = 3.0

    /**
     * Nodes whose mastery triggers an independence check — docs/03-CURRICULUM.md §5.6 for `M2`, and
     * docs/20-PHASE-2-SPEC.md §3 for `M10`. The last node of each chain: the check asks whether the
     * learner can hold a key without the cadence propping it up, which only means anything once the
     * whole degree set is in play.
     */
    fun triggersIndependenceCheck(skillId: SkillId): Boolean =
        skillId == m2Nodes.last().id || skillId == m10Nodes.last().id

    /** Every recognition node the practice loop can run, in either mode. */
    val recognitionNodes: List<SkillNode> = m2Nodes + m10Nodes + m11Nodes

    /** Every node with a degree set, a generator and a mastery lifecycle — recognition and prediction alike. */
    val allNodes: List<SkillNode> = recognitionNodes + m12Nodes

    private val byId: Map<SkillId, SkillNode> = allNodes.associateBy { it.id }

    fun node(skillId: SkillId): SkillNode = byId[skillId] ?: error("Not a recognition skill node: $skillId")

    /**
     * Whether this skill is a recognition node with a full mastery lifecycle — a degree set, a
     * staircase, the mastery criteria, FSRS review.
     *
     * The predicate the replayer needs, and deliberately not "is it `M2`". Membership of this graph is
     * the property that actually decides whether the full reduction is defined for a skill; module
     * identity only happened to coincide with it while `M2` was the only recognition module. `M10` and
     * `M11` are the same shape and must replay the same way.
     */
    fun isRecognitionNode(skillId: SkillId): Boolean =
        skillId in byId && scopeFor(skillId) == DifficultyAxis.Scope.RECOGNITION

    /** Whether this skill is any node this graph knows about, recognition or prediction. */
    fun isKnownNode(skillId: SkillId): Boolean = skillId in byId

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
        val chain =
            when (skillId) {
                in m10Nodes.map { it.id } -> m10Nodes
                in m11Nodes.map { it.id } -> m11Nodes
                in m12Nodes.map { it.id } -> m12Nodes
                else -> m2Nodes
            }
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
    /**
     * The degree this node exists to teach, if it has one — only `M11`'s chromatic nodes do. Drives
     * both the sampling weight that gets it heard and the mastery criterion that judges it
     * (docs/20-PHASE-2-SPEC.md §3).
     */
    val introduces: ScaleDegree? = null,
    /**
     * A second gate this node needs beyond [prerequisite], for the two `M12` nodes that genuinely have
     * one: predicting in minor also requires `M10.MIN_NATURAL`, predicting chromatically also requires
     * `M11.CHROM_FULL` (docs/20-PHASE-2-SPEC.md §3). Modeled explicitly rather than by giving those
     * nodes a different single parent, because the chain order and the readiness gate are two different
     * questions and collapsing them would put `M12.PREDICT_MINOR` after `M10` in the prediction chain,
     * which it is not.
     */
    val alsoRequires: SkillId? = null,
)
