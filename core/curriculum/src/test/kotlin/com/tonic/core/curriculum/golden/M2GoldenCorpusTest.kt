package com.tonic.core.curriculum.golden

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stage 2.0's literal acceptance criterion: generalizing the engine produces **zero** behavior change to
 * `M2` (docs/20-PHASE-2-SPEC.md §7). This test is the proof, not a proxy for it.
 *
 * The golden file was written from the pre-Phase-2 generator and is committed. It is not regenerated as
 * part of a normal run and must not be: a golden file that rewrites itself when it disagrees with the
 * code proves nothing at all. If Phase 2 legitimately changes `M2` output, that is a decision requiring
 * an explicit chat approval (CLAUDE.md §2 rule 4) and the file is regenerated in its own commit that
 * says what changed and why — never silently, and never bundled with the change that caused it.
 *
 * Regenerate deliberately with `-Dtonic.golden.regenerate=true`, which writes the file and then fails,
 * so a regeneration can never be mistaken for a passing run.
 */
class M2GoldenCorpusTest {
    @Test
    fun `M2 generation is byte-identical to the pre-Phase-2 corpus`() {
        val actual = M2GoldenCorpus.render()

        if (System.getProperty(REGENERATE_PROPERTY) == "true") {
            goldenFile().apply { parentFile.mkdirs() }.writeText(actual)
            error(
                "Golden corpus regenerated at ${goldenFile().path}. This run fails by design - review the " +
                    "diff, confirm every change is intended and approved, then re-run without " +
                    "-D$REGENERATE_PROPERTY.",
            )
        }

        val golden = goldenFile()
        assertTrue(
            golden.isFile,
            "Golden corpus missing at ${golden.path}. Create it once with -D$REGENERATE_PROPERTY=true, " +
                "from code known to be correct.",
        )

        val expected = golden.readText()
        if (expected != actual) {
            val expectedLines = expected.lines()
            val actualLines = actual.lines()
            val firstDivergence =
                expectedLines
                    .zip(actualLines)
                    .indexOfFirst { (e, a) -> e != a }
                    .takeIf { it >= 0 }
            val detail =
                if (firstDivergence == null) {
                    "line counts differ: golden ${expectedLines.size}, actual ${actualLines.size}"
                } else {
                    buildString {
                        appendLine("first divergence at line ${firstDivergence + 1}:")
                        appendLine("  golden: ${expectedLines[firstDivergence]}")
                        appendLine("  actual: ${actualLines[firstDivergence]}")
                    }
                }
            // assertEquals on two ~100KB strings prints an unreadable wall; the located divergence is
            // what a reader actually needs to judge whether M2's behavior moved.
            error("M2 generation diverged from the pre-Phase-2 golden corpus.\n$detail")
        }
    }

    @Test
    fun `the corpus covers every M2 node at every cadence-fade level`() {
        // Guards the guard: a corpus that silently stopped covering a skill or a fade level would keep
        // passing the byte-identity test above while proving progressively less.
        val rendered = M2GoldenCorpus.render()
        val runHeaders = rendered.lines().filter { it.startsWith("## run=") }
        val skills = runHeaders.mapNotNull { line -> line.substringAfter("skill=").substringBefore(" ").takeIf { it.isNotBlank() } }.distinct()
        val fadeConfigs = runHeaders.map { it.substringAfter("config=").substringBefore(" ") }.distinct()

        assertEquals(
            com.tonic.core.curriculum.graph.SkillGraph.m2Nodes
                .map { it.id.raw }
                .toSet(),
            skills.toSet(),
            "every M2 node must appear in the corpus",
        )
        for (fade in 0..com.tonic.core.model.items.DifficultyAxis.CADENCE_FADE.maxLevel) {
            assertTrue("fade$fade" in fadeConfigs, "cadence-fade level $fade is not covered by the corpus")
        }
    }

    private fun goldenFile(): File = File("src/test/resources/golden/m2-corpus.txt")

    private companion object {
        const val REGENERATE_PROPERTY = "tonic.golden.regenerate"
    }
}
