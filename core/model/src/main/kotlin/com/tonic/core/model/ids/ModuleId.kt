package com.tonic.core.model.ids

/**
 * Reserved module identifiers, docs/03-CURRICULUM.md §2 and docs/20-PHASE-2-SPEC.md §3. M0 and M2 were
 * built in Phase 1 (M1 is folded into M0's remediation path per docs/01-PRODUCT-SPEC.md §3); Phase 2
 * adds M9–M12. M3–M8 stay reserved so the schema is stable when later phases add them — CLAUDE.md §2
 * rule 5.
 */
enum class ModuleId(
    val code: String,
) {
    M0("M0"),
    M1("M1"),
    M2("M2"),
    M3("M3"),
    M4("M4"),
    M5("M5"),
    M6("M6"),
    M7("M7"),
    M8("M8"),

    // Phase 2 — docs/20-PHASE-2-SPEC.md §3. Numbered after the reserved block rather than inserted
    // among it, so no existing identifier shifts meaning.
    M9("M9"),
    M10("M10"),
    M11("M11"),
    M12("M12"),
    ;

    companion object {
        fun fromCode(code: String): ModuleId =
            entries.find { it.code == code }
                ?: throw IllegalArgumentException("Unknown module id: $code")
    }
}
