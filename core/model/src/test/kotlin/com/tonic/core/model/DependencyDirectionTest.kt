package com.tonic.core.model

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Enforces docs/04-ARCHITECTURE.md §2: `:core:model` is a pure Kotlin/JVM
 * module and must never import `android.*` or `androidx.*`. A later Compose
 * Multiplatform / desktop port depends on this staying true.
 *
 * This is a plain source scan rather than a lint rule, per
 * docs/09-BUILD-PLAN.md Stage 0: "a plain unit test that scans imports is
 * acceptable if simpler."
 */
class DependencyDirectionTest {
    @Test
    fun `main source set contains no android imports`() {
        val violations = scanForForbiddenImports(File("src/main"))
        assertTrue(
            violations.isEmpty(),
            "Forbidden android.*/androidx.* imports found in :core:model:\n" +
                violations.joinToString("\n") { (file, line) -> "  $file: $line" },
        )
    }

    companion object {
        private val FORBIDDEN = Regex("""^\s*import\s+(android|androidx)\..*""")

        /**
         * Returns (relative path, offending import line) for every forbidden
         * import found under [mainSourceDir]. Shared by the identical test in
         * :core:curriculum and :core:engine — kept as a plain top-level
         * function rather than a shared test-fixture module, since the whole
         * point is that each pure module can verify itself with zero
         * cross-module test dependency.
         */
        fun scanForForbiddenImports(mainSourceDir: File): List<Pair<String, String>> {
            if (!mainSourceDir.exists()) return emptyList()
            return mainSourceDir
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    file
                        .readLines()
                        .filter { FORBIDDEN.matches(it) }
                        .map { file.path to it.trim() }
                }.toList()
        }
    }
}
