package com.tonic.core.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Stage 3.6's third acceptance criterion, structurally: "`sungCents` provably unread by the engine."
 *
 * **Why a source scan and not another behavioral test.** `SungDataIsNeverAdaptiveTest` already proves
 * the *behavior* — replay one history twice, once tapped and once sung, and demand an identical
 * `SkillState` — and it is the better test of the two, because it would catch an engine that read the
 * column through some indirection this file cannot see. What it cannot do is prove a negative about
 * code that does not exist yet. A future change that starts consulting `sungCents` in the staircase
 * would have to also produce a divergent state to fail that test, and the plausible first version of
 * such a change is a small tie-breaker that fails it only on some inputs.
 *
 * This one fails on the import. Together they cover both halves of "provably": one says the engine
 * does not behave as if it reads the field, the other says it does not mention it.
 *
 * docs/30-PHASE-3-SPEC.md §7 names the four things that must never read it — `Staircase`,
 * `AxisScheduler`, `MasteryEvaluator`, `ConfusionTracker` — and all four live in this module.
 */
class SungDataNeverReachesTheEngineTest {
    @Test
    fun `no adaptive code mentions the sung columns`() {
        val offenders =
            File("src/main")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    file
                        .readLines()
                        .withIndex()
                        .filter { (_, line) -> SUNG_FIELDS.containsMatchIn(stripComment(line)) }
                        .map { (index, line) -> "${file.path}:${index + 1}: ${line.trim()}" }
                }.toList()

        assertTrue(
            offenders.isEmpty(),
            "docs/30-PHASE-3-SPEC.md §7: sung data is for display and analysis only and must never be " +
                "read by Staircase, AxisScheduler, MasteryEvaluator or ConfusionTracker. Found:\n" +
                offenders.joinToString("\n") { "  $it" },
        )
    }

    private companion object {
        /**
         * Both Phase 3 columns, not just `sungCents`.
         *
         * `inputMethod` is under the same rule and is the easier one to reach for innocently — an
         * engine that treated a sung attempt as worth slightly less, or that scheduled review
         * differently for one input, would violate §2's "sung and tapped attempts are not separate
         * skill states" without ever touching a cent value.
         */
        val SUNG_FIELDS = Regex("""\b(sungCents|inputMethod)\b""")

        /**
         * Comments are exempt, and deliberately so: the engine's KDoc *should* be free to explain why
         * it ignores these fields. A rule that punished the explanation would push the reasoning out
         * of the code, which is the opposite of what this repository wants.
         */
        fun stripComment(line: String): String {
            val trimmed = line.trim()
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return ""
            return line.substringBefore("//")
        }
    }
}
