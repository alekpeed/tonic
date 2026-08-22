package com.tonic.core.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Stage 3.6's second acceptance criterion: "no audio persisted."
 *
 * docs/30-PHASE-3-SPEC.md §7, in its own words: "Captured audio exists in memory for the duration of
 * one attempt and is discarded. Nothing recorded, nothing cached, nothing exported." That is the
 * promise the microphone permission was granted on, and unlike most promises in this repository it
 * cannot be checked by observing behavior — a build that wrote a buffer to a cache file would pass
 * every existing test, because nothing downstream reads that file.
 *
 * So it is checked structurally, in the two places a violation could live: the persistence layer must
 * not know what a captured buffer is, and neither layer may open a file for writing.
 *
 * **What this deliberately does not do is ban `FloatArray` outright.** Synthesis produces one for
 * every item played and `PcmBuffer` is built on it; a rule that broad would fire constantly, be
 * suppressed, and then be worth nothing. The narrow rule is about *persistence* knowing about audio,
 * which is the actual failure mode.
 */
class NoAudioPersistedTest {
    @Test
    fun `the persistence layer does not know what captured audio is`() {
        val dataMain = File("src/main")
        assertTrue(dataMain.isDirectory, "expected :core:data's sources at ${dataMain.absolutePath}")
        val offenders = scan(dataMain, CAPTURE_TYPES)

        assertTrue(
            offenders.isEmpty(),
            "docs/30-PHASE-3-SPEC.md §7: captured audio is never persisted, so :core:data must have no " +
                "reason to name it. Found:\n" + offenders.joinToString("\n") { "  $it" },
        )
    }

    /**
     * Neither `:core:data` nor `:core:audio` writes a file.
     *
     * `:core:audio` is in scope from a test in `:core:data` because a file path is a string and the
     * module boundary is not what protects this: the class that holds a live microphone buffer is the
     * one with the means and the motive to dump it while debugging, and a temporary debug write is
     * exactly the violation §7 describes. Reading the other module's sources needs no dependency on
     * it, and the alternative — the same test duplicated in two modules — drifts.
     */
    @Test
    fun `neither the data nor the audio layer opens a file for writing`() {
        // Checked before scanning, because an empty result and a wrong path are indistinguishable
        // afterward. A relative path is a fragile thing to rest a guarantee on, and this test's whole
        // value is that it fails when it should - one that silently scanned nothing would be worse
        // than not having it, because it would read as a passing guarantee.
        val audioMain = File("../audio/src/main")
        assertTrue(
            audioMain.isDirectory,
            "expected :core:audio's sources at ${audioMain.absolutePath} - if the layout moved, fix this " +
                "path rather than letting the scan pass on nothing",
        )

        val offenders = scan(File("src/main"), FILE_WRITES) + scan(audioMain, FILE_WRITES)

        assertTrue(
            offenders.isEmpty(),
            "docs/30-PHASE-3-SPEC.md §7: nothing recorded, nothing cached. A file write in either layer " +
                "has to be argued for. Found:\n" + offenders.joinToString("\n") { "  $it" },
        )
    }

    private companion object {
        /** The types that carry live microphone samples. Naming one here means holding onto capture. */
        val CAPTURE_TYPES = Regex("""\b(CapturedAudio|MicrophoneSource|PcmBuffer)\b""")

        /**
         * Ways to put bytes on disk. Not exhaustive against a determined author — nothing short of a
         * sandbox is — but it covers every route a person would reach for without meaning any harm,
         * which is the population this rule is actually for.
         */
        val FILE_WRITES =
            Regex(
                """\b(FileOutputStream|FileWriter|writeBytes|writeText|openFileOutput|createTempFile|""" +
                    """RandomAccessFile|Files\.write)\b""",
            )

        fun scan(
            dir: File,
            pattern: Regex,
        ): List<String> {
            if (!dir.exists()) return emptyList()
            return dir
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    file
                        .readLines()
                        .withIndex()
                        .filter { (_, line) -> pattern.containsMatchIn(stripComment(line)) }
                        .map { (index, line) -> "${file.path}:${index + 1}: ${line.trim()}" }
                }.toList()
        }

        /** Comments are exempt — the docs explaining why audio is never written must stay writable. */
        fun stripComment(line: String): String {
            val trimmed = line.trim()
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return ""
            return line.substringBefore("//")
        }
    }
}
