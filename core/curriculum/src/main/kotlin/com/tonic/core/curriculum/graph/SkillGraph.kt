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
     * `M10.MIXED_MODE` — docs/20-PHASE-2-SPEC.md §3's interleaving node, "arguably the most valuable
     * node in Phase 2." Degree identification with the mode randomized per item and never announced
     * before the answer.
     *
     * **Its active degree set is the ten-degree union of major and minor, not the seven of whichever
     * mode the item is in.** That is the whole node, expressed in one line. If the ladder showed only
     * the item's own degrees, it would announce the mode before a note sounded — a `♭3` button on
     * screen is a statement that this item is minor — and the learner would answer by reading the
     * buttons rather than by hearing the key. The union is exactly the alphabet §2.1 already specifies
     * for minor: `1, 2, ♭3, 3, 4, 5, ♭6, 6, ♭7, 7`.
     *
     * Its three gates are all real and none is implied by the others: the whole major set
     * (`M2.FULL_DIATONIC`), natural minor (`M10.MIN_NATURAL`), and the ability to *hear* which mode is
     * sounding (`M9.MODE_ID_CADENCE`). The third is the one that makes this node answerable at all —
     * without it a learner is being asked to name a degree in a mode they cannot identify.
     */
    val m10MixedModeNode: SkillNode =
        SkillNode(
            SkillIds.M10_MIXED_MODE,
            prerequisite = SkillIds.M10_MIN_NATURAL,
            activeDegrees = ScaleDegree.ALL_DIATONIC + ScaleDegree.ALL_NATURAL_MINOR,
            // Declared MAJOR and never read: randomizesMode sends the generator down a different path
            // entirely. Kept honest by the test that asserts modeFor is not what decides this.
            mode = Mode.MAJOR,
            alsoRequires = listOf(SkillIds.M2_FULL_DIATONIC, SkillIds.M9_MODE_ID_CADENCE),
        )

    /**
     * Whether a node draws its mode fresh per item rather than declaring one — `M10.MIXED_MODE` alone.
     *
     * Asked separately from [modeFor] because the two answer different questions and conflating them
     * is how a "mode" field ends up silently meaning "the mode of the last item generated." A node
     * either has a mode or it doesn't; this says which.
     */
    fun randomizesMode(skillId: SkillId): Boolean = skillId == SkillIds.M10_MIXED_MODE

    /**
     * Which mode the degree ladder should draw its *spine* from, for an item in [itemMode].
     *
     * The item's own mode everywhere except `M10.MIXED_MODE`, which is pinned to major — and the
     * pinning is not cosmetic. The ladder draws a mode's own seven degrees wide, on the spine, with
     * anything else hanging alongside them narrower (docs/20-PHASE-2-SPEC.md §8.3). If the spine
     * followed the item, then `♮3` would be wide and `♭3` narrow on a major item and the reverse on a
     * minor one — **the layout itself would announce the mode before a note sounded**, which is the
     * one thing this node exists to prevent, and it would do so more loudly than a changed button set
     * because the shape of the whole column would shift.
     *
     * Pinning it to major is also the honest reading of §2.1's model: an alteration is absolute
     * relative to major, so `♭3` is the same pitch relationship in either mode and belongs beside `3`
     * regardless of which mode is sounding.
     */
    fun ladderSpineMode(
        skillId: SkillId,
        itemMode: Mode,
    ): Mode = if (randomizesMode(skillId)) Mode.MAJOR else itemMode

    /**
     * The degrees a *target* may be drawn from for an item in [mode] at [skillId].
     *
     * Identical to [activeDegreesFor] for every node except `M10.MIXED_MODE`, where they genuinely
     * differ: the ladder shows all ten so the mode stays hidden, but a major item can only target a
     * major degree — asking for `♭3` under a major cadence would be asking about a note the key does
     * not contain.
     */
    fun targetDegreesFor(
        skillId: SkillId,
        mode: Mode,
    ): Set<ScaleDegree> =
        if (!randomizesMode(skillId)) {
            activeDegreesFor(skillId)
        } else {
            when (mode) {
                Mode.MAJOR -> ScaleDegree.ALL_DIATONIC
                Mode.MINOR -> ScaleDegree.ALL_NATURAL_MINOR
            }
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
                alsoRequires = listOf(SkillIds.M10_MIN_NATURAL),
            ),
            SkillNode(
                SkillIds.M12_PREDICT_CHROMATIC,
                prerequisite = SkillIds.M12_PREDICT_DIATONIC,
                activeDegrees = ScaleDegree.ALL_CHROMATIC,
                alsoRequires = listOf(SkillIds.M11_CHROM_FULL),
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
    fun degreeWeightsFor(
        skillId: SkillId,
        mode: Mode = Mode.MAJOR,
        recentDegrees: List<ScaleDegree> = emptyList(),
    ): Map<ScaleDegree, Double> {
        // MIXED_MODE first: a MEASURED correction, not a preference. Its active set is ten degrees but
        // any single item can only target seven, so `1, 2, 4, 5` - present in both modes - get twice
        // the exposure of `3, 6, 7, ♭3, ♭6, ♭7`, which are present in one. Sampled uniformly, a 30-item
        // window gave the mode-specific degrees 1 or 2 attempts against docs/03-CURRICULUM.md §5.5's
        // requirement of 3, so the node was quietly unmasterable - a failure with no symptom, since
        // every item generated correctly and mastery simply never arrived.
        //
        // The weight is derived rather than tuned: a degree gets the node's mode count divided by the
        // number of those modes it appears in, which is exactly the factor that equalizes exposure
        // across the union. Two modes, one appearance, weight 2. Two modes, two appearances, weight 1.
        // For every single-mode node that is 1/1 for every degree, which is why they take the empty
        // map below and their sampling is untouched.
        if (randomizesMode(skillId)) return mixedModeWeights(skillId, mode, recentDegrees)
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
    val recognitionNodes: List<SkillNode> = m2Nodes + m10Nodes + listOf(m10MixedModeNode) + m11Nodes

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
                // MIXED_MODE is a leaf: it interleaves the chains rather than continuing either, so
                // mastering it rolls into nothing.
                SkillIds.M10_MIXED_MODE -> return null
                else -> m2Nodes
            }
        val index = chain.indexOfFirst { it.id == skillId }
        return chain.getOrNull(index + 1)?.id
    }

    /**
     * `M10.MIXED_MODE`'s sampling weights: a structural half and a corrective half, both measured.
     *
     * **Structural.** Its active set is ten degrees but any single item can only target the seven of
     * its own mode, so `1, 2, 4, 5` — present in both — get twice the exposure of the six that belong
     * to one. The base weight is the node's mode count divided by the number of those modes the degree
     * appears in, which is exactly the factor that equalizes exposure across the union. Two modes, one
     * appearance, weight 2. Two modes, two appearances, weight 1. Every single-mode node computes 1/1
     * for every degree and so takes the empty map instead, leaving its sampling untouched.
     *
     * **Corrective.** Equal *expected* exposure is not enough here, and the difference is the whole
     * reason this function reads history at all. docs/03-CURRICULUM.md §5.5's coverage criterion asks
     * for `min(5, 30/n)` attempts on every active degree, which at ten degrees is three — and ten
     * degrees at three attempts is thirty items exactly. A qualifying window has to be a *perfect
     * partition* of the mastery window. [BalancedSampler] only enforces a ceiling, never a floor, so
     * under weights that were merely fair in expectation a covering window took a median of roughly
     * 440 items to arrive by luck, and on one seed did not arrive within two thousand. That is a node
     * a learner could practice for weeks without ever being certified on, and it fails silently:
     * every item generates correctly and mastery simply never comes.
     *
     * So a degree already at or above its expected share is damped and one behind it is boosted, in
     * proportion to the deficit. This keeps the choice genuinely random — a learner can never predict
     * the next degree — while making the flat windows the criterion needs common rather than rare.
     * Confined to this node, so no other node's stream moves and the Stage 2.0 golden corpus holds.
     */
    private fun mixedModeWeights(
        skillId: SkillId,
        mode: Mode,
        recentDegrees: List<ScaleDegree>,
    ): Map<ScaleDegree, Double> {
        val active = activeDegreesFor(skillId)
        val window = recentDegrees.takeLast(MasteryWindow.SIZE - 1)
        val counts = window.groupingBy { it }.eachCount()
        val required = minOf(MAX_ATTEMPTS_PER_DEGREE, MasteryWindow.SIZE / active.size)
        val candidates = targetDegreesFor(skillId, mode)
        val anyBehind = active.any { (counts[it] ?: 0) < required }

        return candidates.associateWith { degree ->
            val modesContaining = Mode.entries.count { degree in targetDegreesFor(skillId, it) }
            val structural = Mode.entries.size.toDouble() / modesContaining
            // A degree that already has its required share steps back sharply while any degree still
            // lacks one. Damped rather than excluded: it must stay pickable, or the sequence becomes
            // predictable the moment a learner notices which notes have stopped appearing - and
            // BalancedSampler's own 1.5x ceiling still sits on top, so the behind-degrees cannot clump
            // either.
            val satisfied = (counts[degree] ?: 0) >= required
            structural * if (anyBehind && satisfied) SATISFIED_DAMPING else 1.0
        }
    }

    /** How far a degree that already has its share steps back — see [mixedModeWeights]. */
    private const val SATISFIED_DAMPING = 0.06

    /** docs/03-CURRICULUM.md §5.5's documented per-degree attempt count, before window scaling. */
    private const val MAX_ATTEMPTS_PER_DEGREE = 5

    /**
     * The mastery window `M10.MIXED_MODE`'s corrective weighting aims at — the same 30 the evaluator
     * uses (docs/03-CURRICULUM.md §5.5). Declared here rather than depended on, because `:core:engine`
     * depends on `:core:curriculum` and not the other way round (docs/04-ARCHITECTURE.md §2); a test in
     * `:core:engine` pins the two together so they cannot drift.
     */
    object MasteryWindow {
        const val SIZE = 30
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
     * Gates this node needs beyond [prerequisite]. `M12.PREDICT_MINOR` also requires
     * `M10.MIN_NATURAL`, `M12.PREDICT_CHROMATIC` also requires `M11.CHROM_FULL`, and
     * `M10.MIXED_MODE` requires three things at once (docs/20-PHASE-2-SPEC.md §3).
     *
     * Modeled explicitly rather than by giving those nodes a different single parent, because chain
     * order and readiness are two different questions: collapsing them would put `M12.PREDICT_MINOR`
     * after `M10` in the prediction chain, which it is not.
     */
    val alsoRequires: List<SkillId> = emptyList(),
)
