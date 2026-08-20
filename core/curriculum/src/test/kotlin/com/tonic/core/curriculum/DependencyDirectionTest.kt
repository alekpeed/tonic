package com.tonic.core.curriculum

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Enforces docs/04-ARCHITECTURE.md §2 for `:core:curriculum`. See the
 * identical test in `:core:model` for rationale.
 */
class DependencyDirectionTest {
    @Test
    fun `main source set contains no android imports`() {
        val violations = scanForForbiddenImports(File("src/main"))
        assertTrue(
            violations.isEmpty(),
            "Forbidden android.*/androidx.* imports found in :core:curriculum:\n" +
                violations.joinToString("\n") { (file, line) -> "  $file: $line" },
        )
    }

    companion object {
        private val FORBIDDEN = Regex("""^\s*import\s+(android|androidx)\..*""")

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
