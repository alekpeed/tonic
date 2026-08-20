package com.tonic.feature.diagnostic.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * docs/09-BUILD-PLAN.md Stage 8 acceptance: "the amusia flag is not reachable from any composable."
 * Enforced structurally - [DiagnosticUiState] never carries [com.tonic.core.model.state.DiagnosticResult]
 * or its `amusiaIndicatorFlag` field, only the derived [DiagnosticOutcome] - but a structural guarantee
 * is only as good as the next person not quietly reaching around it, so this scans every composable
 * source file for the literal field name too, as a second, independent check.
 */
class AmusiaFlagReachabilityTest {
    @Test
    fun `no composable source file references amusiaIndicatorFlag in actual code`() {
        val uiSourceDir = File("src/main/kotlin/com/tonic/feature/diagnostic/ui")
        assertTrue(uiSourceDir.exists(), "expected ${uiSourceDir.absolutePath} to exist")

        val offenders =
            uiSourceDir
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { withoutComments(it.readText()).contains("amusiaIndicatorFlag") }
                .map { it.name }
                .toList()

        assertTrue(
            offenders.isEmpty(),
            "amusiaIndicatorFlag referenced in code (not just a comment) in: $offenders - it must never reach a composable",
        )
    }

    /** Strips `/* */`/KDoc block comments and `//` line comments - explaining *why* the flag is excluded, in a comment, is expected and fine; the field must just never appear in real code. */
    private fun withoutComments(source: String): String =
        source
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lineSequence()
            .joinToString("\n") { it.substringBefore("//") }
}
