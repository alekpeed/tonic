package com.tonic.core.audio.synth

import com.tonic.core.model.items.ReferenceElement
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.TimbreId
import com.tonic.core.model.music.Tuning
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the Phase 2 audio material to WAV files a human can listen to, off the same synthesis path
 * the app plays through.
 *
 * **Opt-in and skipped by default** (`-Dtonic.audio.export=<dir>`), like the golden-corpus
 * regeneration: it writes files rather than asserting anything, and a test that writes on every build
 * is a test that gets ignored.
 *
 * It exists because the verification gap this project has carried since Stage 2 is not a missing
 * emulator — it is that nobody has *heard* the audio. An emulator would not close it either: no
 * amount of tooling here can listen. What can be done is to put the exact bytes the app would play in
 * front of someone who can, without asking them to install anything first. The questions that need
 * ears, in the order they matter:
 *
 * 1. Does `i–iv–v–i`, all natural minor, actually establish a key? It has no leading tone, which is
 *    the trade docs/20-PHASE-2-SPEC.md §8.1 decision 4 accepted knowingly and asked to be checked.
 * 2. Is a 30-cent bend audible on a phone speaker? It is the finest discrimination the app asks for.
 * 3. Do the chromatic degrees sound like notes between the familiar ones, or merely wrong?
 */
class AudioSampleExportTest {
    private val outputDir: File? = System.getProperty(EXPORT_PROPERTY)?.let(::File)

    @Test
    fun `export listenable samples`() {
        val dir = outputDir ?: return
        dir.mkdirs()

        // 1. The two cadences, side by side. The whole of decision 4 is audible in this pair.
        write(dir, "01-cadence-major.wav", cadence(Mode.MAJOR))
        write(dir, "02-cadence-minor-natural.wav", cadence(Mode.MINOR))

        // 2. The 30-cent bend, against the note it is bending. Played as a pair so the comparison is
        //    the thing being judged, which is how the exercise presents it too.
        val base = Tuning.midiToHz(67)
        write(dir, "03-detune-reference-then-flat30.wav", pair(base, Tuning.offsetByCents(base, -30.0)))
        write(dir, "04-detune-reference-then-sharp30.wav", pair(base, Tuning.offsetByCents(base, 30.0)))
        // The semitone version, for calibration: this is what level 2 sounds like, and the bend above
        // has to be recognizably subtler without being inaudible.
        write(dir, "05-semitone-for-comparison.wav", pair(base, Tuning.midiToHz(68)))

        // 3. A chromatic degree in context: 4, then ♯4, then 5, so the "between" claim is testable.
        write(dir, "06-chromatic-4-sharp4-5.wav", sequence(listOf(65, 66, 67)))

        // 4. M12's shape at the longest gap, so the silence can be judged as an exercise rather than
        //    as a stall. Cadence, breath, five seconds of nothing, one note.
        write(dir, "07-audiation-5s-gap.wav", audiation())

        val written = dir.listFiles()?.filter { it.extension == "wav" }.orEmpty()
        assertTrue(written.size >= 7, "expected 7 samples, wrote ${written.size}")
        println("AUDIO EXPORT: ${written.size} files in ${dir.absolutePath}")
    }

    private fun cadence(mode: Mode): PcmBuffer {
        val tonic = 60
        val steps = listOf(1, 4, 5, 1)
        val chords =
            steps.mapIndexed { i, step ->
                val notes =
                    listOf(step, step + 2, step + 4).map { s ->
                        val zero = s - 1
                        val degree = mode.degreeAtStep(Math.floorMod(zero, 7) + 1)
                        tonic + degree.semitoneOffset(mode) + Math.floorDiv(zero, 7) * 12
                    }
                SynthEngine.renderElement(
                    ReferenceElement.ChordEvent(notes, 900L, TimbreId.SOFT),
                    seed = 100L + i,
                )
            }
        return SynthEngine.concatBuffers(chords)
    }

    /** Two tones with a short gap — the reference, then the thing being compared to it. */
    private fun pair(
        firstHz: Double,
        secondHz: Double,
    ): PcmBuffer =
        SynthEngine.concatBuffers(
            listOf(
                SynthEngine.renderTone(firstHz, TimbreId.PURE, 900L, seed = 1L),
                PcmBuffer.silence(PcmBuffer.msToSamples(400L)),
                SynthEngine.renderTone(secondHz, TimbreId.PURE, 900L, seed = 2L),
            ),
        )

    private fun sequence(midi: List<Int>): PcmBuffer =
        SynthEngine.concatBuffers(
            midi.flatMapIndexed { i, m ->
                listOf(
                    SynthEngine.renderNote(m, TimbreId.PURE, 700L, seed = i.toLong()),
                    PcmBuffer.silence(PcmBuffer.msToSamples(300L)),
                )
            },
        )

    private fun audiation(): PcmBuffer =
        SynthEngine.concatBuffers(
            listOf(
                cadence(Mode.MAJOR),
                PcmBuffer.silence(PcmBuffer.msToSamples(400L)),
                // PREDICT_GAP level 3, the longest the app uses.
                PcmBuffer.silence(PcmBuffer.msToSamples(5_000L)),
                SynthEngine.renderNote(67, TimbreId.PURE, 900L, seed = 9L),
            ),
        )

    /** 16-bit PCM mono WAV. Written by hand rather than with javax.sound, which is headless-hostile. */
    private fun write(
        dir: File,
        name: String,
        buffer: PcmBuffer,
    ) {
        val samples = buffer.samples
        val dataBytes = samples.size * 2
        val out = java.io.ByteArrayOutputStream()

        fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))

        fun le32(v: Int) =
            out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))

        fun le16(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))

        ascii("RIFF")
        le32(36 + dataBytes)
        ascii("WAVE")
        ascii("fmt ")
        le32(16)
        le16(1)
        le16(1)
        le32(buffer.sampleRate)
        le32(buffer.sampleRate * 2)
        le16(2)
        le16(16)
        ascii("data")
        le32(dataBytes)
        for (s in samples) {
            val clamped = s.coerceIn(-1f, 1f)
            le16((clamped * 32_767f).toInt())
        }
        File(dir, name).writeBytes(out.toByteArray())
    }

    private companion object {
        const val EXPORT_PROPERTY = "tonic.audio.export"
    }
}
