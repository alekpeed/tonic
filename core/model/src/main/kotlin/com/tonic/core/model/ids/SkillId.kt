package com.tonic.core.model.ids

import kotlinx.serialization.Serializable

/**
 * `"M" <module> "." <skill> [ "." <variant> ]`, e.g. `M2.DEG_SET_1`.
 * docs/03-CURRICULUM.md §1: identifiers are permanent — once shipped, a
 * [SkillId] string is never reused for a different meaning.
 *
 * `@Serializable` because it appears inside the JSON-encoded state blobs
 * described in docs/05-DATA-MODEL.md §2 (e.g. a session's resume state).
 */
@Serializable
@JvmInline
value class SkillId(
    val raw: String,
) {
    init {
        require(FORMAT.matches(raw)) { "Malformed SkillId: \"$raw\"" }
    }

    val moduleId: ModuleId
        get() = ModuleId.fromCode(raw.substringBefore('.'))

    override fun toString(): String = raw

    companion object {
        private val FORMAT = Regex("""M\d+\.[A-Z0-9_]+(\.[A-Z0-9_]+)?""")
    }
}

/**
 * Every skill identifier that exists in the schema, Phase 1 and reserved.
 * docs/03-CURRICULUM.md §6 and CLAUDE.md §2 rule 5: future-module
 * identifiers are declared now, for schema stability, without being
 * implemented.
 */
object SkillIds {
    // --- M0: Diagnostic and Placement (Phase 1, built) ---
    val M0_PITCH_DIR = SkillId("M0.PITCH_DIR")
    val M0_SAME_DIFF = SkillId("M0.SAME_DIFF")
    val M0_TONAL_MEMORY = SkillId("M0.TONAL_MEMORY")
    val M0_AMUSIA_SCREEN = SkillId("M0.AMUSIA_SCREEN")

    // --- M1: Pitch Primitives, entered only as M0 remediation (Phase 1, built) ---
    val M1_HIGH_LOW = SkillId("M1.HIGH_LOW")
    val M1_SAME_DIFF = SkillId("M1.SAME_DIFF")
    val M1_CONTOUR = SkillId("M1.CONTOUR")
    val M1_STEP_LEAP = SkillId("M1.STEP_LEAP")

    // --- M2: Diatonic Functional Recognition (Phase 1, built) ---
    val M2_DEG_SET_1 = SkillId("M2.DEG_SET_1")
    val M2_DEG_SET_2 = SkillId("M2.DEG_SET_2")
    val M2_DEG_SET_3 = SkillId("M2.DEG_SET_3")
    val M2_DEG_SET_4 = SkillId("M2.DEG_SET_4")
    val M2_FULL_DIATONIC = SkillId("M2.FULL_DIATONIC")
    val M2_INDEPENDENCE_CHECK = SkillId("M2.INDEPENDENCE_CHECK")

    /** In mastery order — the prerequisite chain from docs/03-CURRICULUM.md §5.2. */
    val M2_NODES_IN_ORDER = listOf(M2_DEG_SET_1, M2_DEG_SET_2, M2_DEG_SET_3, M2_DEG_SET_4, M2_FULL_DIATONIC)

    // --- Reserved, unbuilt: docs/03-CURRICULUM.md §6. Do not implement. ---
    val M3_BEAT_FIND = SkillId("M3.BEAT_FIND")
    val M3_BEAT_DIV = SkillId("M3.BEAT_DIV")
    val M3_SUBDIV = SkillId("M3.SUBDIV")
    val M3_RESTS = SkillId("M3.RESTS")
    val M3_SYNCOPATION = SkillId("M3.SYNCOPATION")
    val M3_COMPOUND = SkillId("M3.COMPOUND")
    val M3_METER_CHANGE = SkillId("M3.METER_CHANGE")

    val M4_FRAG_2 = SkillId("M4.FRAG_2")
    val M4_FRAG_3 = SkillId("M4.FRAG_3")
    val M4_PHRASE_SHORT = SkillId("M4.PHRASE_SHORT")
    val M4_PHRASE_FULL = SkillId("M4.PHRASE_FULL")
    val M4_RHYTHM_FIRST = SkillId("M4.RHYTHM_FIRST")

    val M5_QUALITY_MAJ_MIN = SkillId("M5.QUALITY_MAJ_MIN")
    val M5_QUALITY_EXT = SkillId("M5.QUALITY_EXT")
    val M5_BASS_DEGREE = SkillId("M5.BASS_DEGREE")
    val M5_FUNCTION_IVV = SkillId("M5.FUNCTION_IVV")
    val M5_FUNCTION_DIATONIC = SkillId("M5.FUNCTION_DIATONIC")
    val M5_INVERSIONS = SkillId("M5.INVERSIONS")
    val M5_VOICE_LEADING = SkillId("M5.VOICE_LEADING")

    val M6_BASS_SOPRANO = SkillId("M6.BASS_SOPRANO")
    val M6_TWO_VOICE = SkillId("M6.TWO_VOICE")
    val M6_FOUR_VOICE = SkillId("M6.FOUR_VOICE")

    val M7_REAL_MELODY = SkillId("M7.REAL_MELODY")
    val M7_REAL_HARMONY = SkillId("M7.REAL_HARMONY")

    // Phase 2, docs/20-PHASE-2-SPEC.md §3. M9 identifies *which mode* is sounding and gates everything
    // in minor: before a learner can name a degree within minor, they have to hear that it is minor.
    val M9_MODE_ID_CADENCE = SkillId("M9.MODE_ID_CADENCE")
    val M9_MODE_ID_TRIAD = SkillId("M9.MODE_ID_TRIAD")
    val M9_MODE_ID_MELODIC = SkillId("M9.MODE_ID_MELODIC")

    /** In prerequisite order — each node strips away a layer of harmonic support. */
    val M9_NODES_IN_ORDER = listOf(M9_MODE_ID_CADENCE, M9_MODE_ID_TRIAD, M9_MODE_ID_MELODIC)

    // M10 mirrors M2's structure in natural minor - same six axes, same cadence-fade mechanic, same
    // mastery criteria (docs/20-PHASE-2-SPEC.md §3). Every id is declared now so the schema is stable;
    // Stage 2.3 builds sets 1-4, Stage 2.4 the three minor forms and the independence check.
    val M10_MIN_SET_1 = SkillId("M10.MIN_SET_1")
    val M10_MIN_SET_2 = SkillId("M10.MIN_SET_2")
    val M10_MIN_SET_3 = SkillId("M10.MIN_SET_3")
    val M10_MIN_SET_4 = SkillId("M10.MIN_SET_4")
    val M10_MIN_NATURAL = SkillId("M10.MIN_NATURAL")
    val M10_MIN_HARMONIC = SkillId("M10.MIN_HARMONIC")
    val M10_MIN_MELODIC = SkillId("M10.MIN_MELODIC")
    val M10_MIN_INDEPENDENCE_CHECK = SkillId("M10.MIN_INDEPENDENCE_CHECK")
    val M10_MIXED_MODE = SkillId("M10.MIXED_MODE")

    // M11 introduces the five notes outside the diatonic set, one node at a time, in the pull-strength
    // order of docs/20-PHASE-2-SPEC.md §2.2. Each node adds exactly one degree to the previous set.
    val M11_CHROM_SHARP4 = SkillId("M11.CHROM_SHARP4")
    val M11_CHROM_FLAT7 = SkillId("M11.CHROM_FLAT7")
    val M11_CHROM_FLAT6 = SkillId("M11.CHROM_FLAT6")
    val M11_CHROM_FLAT3 = SkillId("M11.CHROM_FLAT3")
    val M11_CHROM_FLAT2 = SkillId("M11.CHROM_FLAT2")
    val M11_CHROM_FULL = SkillId("M11.CHROM_FULL")

    // M12 inverts the recognition task: the learner is told which degree is coming, holds it across a
    // silent gap, and judges what actually sounded (docs/20-PHASE-2-SPEC.md §2.3). Its nodes widen the
    // degree pool the stated degree is drawn from rather than adding a new interaction each time.
    val M12_PREDICT_TRIAD = SkillId("M12.PREDICT_TRIAD")
    val M12_PREDICT_DIATONIC = SkillId("M12.PREDICT_DIATONIC")
    val M12_PREDICT_MINOR = SkillId("M12.PREDICT_MINOR")
    val M12_PREDICT_CHROMATIC = SkillId("M12.PREDICT_CHROMATIC")

    /**
     * In prerequisite order. `PREDICT_MINOR` and `PREDICT_CHROMATIC` both follow `PREDICT_DIATONIC`
     * and neither precedes the other — this list is the order they unlock in, not a claim that
     * chromatic prediction requires minor prediction.
     */
    val M12_NODES_IN_ORDER =
        listOf(M12_PREDICT_TRIAD, M12_PREDICT_DIATONIC, M12_PREDICT_MINOR, M12_PREDICT_CHROMATIC)

    val M8_MINOR_MODE = SkillId("M8.MINOR_MODE")
    val M8_CHROMATIC_DEGREES = SkillId("M8.CHROMATIC_DEGREES")
    val M8_MODAL = SkillId("M8.MODAL")
    val M8_EXTENDED_HARMONY = SkillId("M8.EXTENDED_HARMONY")
}
