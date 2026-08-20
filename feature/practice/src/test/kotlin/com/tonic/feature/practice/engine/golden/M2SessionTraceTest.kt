package com.tonic.feature.practice.engine.golden

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The behavioral baseline for the practice loop, guarding the refactor that makes
 * [com.tonic.feature.practice.engine.PracticeLoopEngine] polymorphic over item types.
 *
 * Same contract as the Stage 2.0 generation corpus: captured from the pre-refactor engine, committed,
 * and never regenerated silently. If the loop's behavior on `M2` legitimately changes, that is a
 * decision needing explicit approval (CLAUDE.md §2 rule 4) and the file is regenerated in its own
 * commit stating what moved and why. Regenerate with `-Dtonic.golden.regenerate=true`, which writes the
 * file and then fails, so it can never be mistaken for a passing run.
 */
class M2SessionTraceTest {
    @Test
    fun `a full M2 session behaves exactly as it did before the loop refactor`() {
        val actual = runBlocking { M2SessionTrace.render() }

        if (System.getProperty(REGENERATE_PROPERTY) == "true") {
            goldenFile().apply { parentFile.mkdirs() }.writeText(actual)
            error(
                "Session trace regenerated at ${goldenFile().path}. This run fails by design - review " +
                    "the diff, confirm every change is intended, then re-run without " +
                    "-D$REGENERATE_PROPERTY.",
            )
        }

        val golden = goldenFile()
        assertTrue(
            golden.isFile,
            "Session trace missing at ${golden.path}. Create it with -D$REGENERATE_PROPERTY=true.",
        )

        val expected = golden.readText()
        if (expected != actual) {
            val e = expected.lines()
            val a = actual.lines()
            val at = e.zip(a).indexOfFirst { (x, y) -> x != y }.takeIf { it >= 0 }
            val detail =
                if (at == null) {
                    "line counts differ: golden ${e.size}, actual ${a.size}"
                } else {
                    "first divergence at line ${at + 1}:\n  golden: ${e[at]}\n  actual: ${a[at]}"
                }
            error("The practice loop's M2 behavior changed.\n$detail")
        }
    }

    @Test
    fun `the trace is reproducible across runs, or it is worthless as a baseline`() {
        // The loop pre-renders the next item on a background coroutine while the current one is on
        // screen. That overlap is exactly what made generation non-deterministic once before, and a
        // baseline that drifts between runs would fail at random and teach everyone to ignore it.
        // Three runs, because two agreeing could still be luck.
        val runs = (1..3).map { runBlocking { M2SessionTrace.render() } }

        assertEquals(runs[0], runs[1], "run 1 and run 2 disagree - the loop is not deterministic")
        assertEquals(runs[1], runs[2], "run 2 and run 3 disagree - the loop is not deterministic")
    }

    private fun goldenFile(): File = File("src/test/resources/golden/m2-session-trace.txt")

    private companion object {
        const val REGENERATE_PROPERTY = "tonic.golden.regenerate"
    }
}
