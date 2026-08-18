package com.tonic.core.model.ids

/**
 * Reserved module identifiers, docs/03-CURRICULUM.md §2. Only M0 and M2 are
 * built in Phase 1 (M1 is folded into M0's remediation path per
 * docs/01-PRODUCT-SPEC.md §3). M3–M8 are reserved so the schema is stable
 * when later phases add them — CLAUDE.md §2 rule 5.
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
    ;

    companion object {
        fun fromCode(code: String): ModuleId =
            entries.find { it.code == code }
                ?: throw IllegalArgumentException("Unknown module id: $code")
    }
}
