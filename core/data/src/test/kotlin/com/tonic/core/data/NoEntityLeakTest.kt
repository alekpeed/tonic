package com.tonic.core.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * docs/09-BUILD-PLAN.md Stage 5 acceptance: "Repositories expose only
 * domain types; a test asserts no Room entity is public." Kotlin's `internal`
 * visibility on every `*Entity` class already makes this a compile error if
 * violated (a public interface cannot reference an internal type in its
 * public signature) - this test is the explicit, documented regression
 * guard for that property, in the same source-scanning style as
 * `DependencyDirectionTest` in the pure modules.
 */
class NoEntityLeakTest {
    @Test
    fun `no public repository or settings interface references an entity type`() {
        val publicApiFiles =
            File("src/main/kotlin/com/tonic/core/data/repository")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" && !it.name.endsWith("Impl.kt") && it.name != "Mappers.kt" }
                .toList() +
                File("src/main/kotlin/com/tonic/core/data/settings")
                    .walkTopDown()
                    .filter { it.isFile && it.extension == "kt" && !it.name.endsWith("Impl.kt") }
                    .toList() +
                // Added with Phase 2 Stage 2.1. The export package is public API too, and it is the one
                // place that legitimately reads every entity in the database - so it is exactly where
                // an entity would most plausibly leak into a public signature.
                File("src/main/kotlin/com/tonic/core/data/export")
                    .walkTopDown()
                    .filter { it.isFile && it.extension == "kt" && !it.name.endsWith("Impl.kt") }
                    .toList()

        assertTrue(publicApiFiles.isNotEmpty(), "sanity check: found no repository/settings interface files to scan")

        val violations =
            publicApiFiles.flatMap { file ->
                file
                    .readLines()
                    .filter { line -> ENTITY_REFERENCE.containsMatchIn(line) }
                    .map { file.path to it.trim() }
            }

        assertTrue(
            violations.isEmpty(),
            "Public :core:data interfaces must never reference a Room entity:\n" +
                violations.joinToString("\n") { (file, line) -> "  $file: $line" },
        )
    }

    @Test
    fun `every entity class is declared internal`() {
        val entityFiles =
            File("src/main/kotlin/com/tonic/core/data/entity")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .toList()
        assertTrue(entityFiles.isNotEmpty(), "sanity check: found no entity files to scan")

        val violations =
            entityFiles.filterNot { file ->
                file.readLines().any { INTERNAL_ENTITY_DECLARATION.containsMatchIn(it) }
            }

        assertTrue(
            violations.isEmpty(),
            "Every Room entity must be declared `internal` so it structurally cannot leak through a " +
                "public API:\n" + violations.joinToString("\n") { "  ${it.path}" },
        )
    }

    companion object {
        private val ENTITY_REFERENCE = Regex("""\bEntity\b""")
        private val INTERNAL_ENTITY_DECLARATION = Regex("""internal\s+data class\s+\w*Entity\b""")
    }
}
