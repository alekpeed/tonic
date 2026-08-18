package com.tonic.feature.diagnostic.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * docs/09-BUILD-PLAN.md Stage 8 acceptance: "no evaluative language, no score display, no diagnosis,
 * and the forbidden terms from docs/02-PEDAGOGY.md §8 and docs/08-UI-SPEC.md §10 appear nowhere in
 * `strings.xml`." Reads the real resource file rather than a copy, so a future string addition is
 * covered automatically.
 */
class CopyAuditTest {
    private val forbiddenTerms =
        listOf("tone deaf", "tone-deaf", "talent", "gifted", "natural ability")

    @Test
    fun `strings xml contains none of the forbidden amusia-adjacent terms`() {
        val stringsXml = File("src/main/res/values/strings.xml")
        assertTrue(stringsXml.exists(), "expected ${stringsXml.absolutePath} to exist")
        val content = stringsXml.readText().lowercase()

        for (term in forbiddenTerms) {
            assertTrue(
                term !in content,
                "forbidden term \"$term\" found in strings.xml - docs/02-PEDAGOGY.md §8 / docs/08-UI-SPEC.md §10",
            )
        }
    }
}
