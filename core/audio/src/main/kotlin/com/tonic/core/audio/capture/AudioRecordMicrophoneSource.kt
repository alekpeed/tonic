package com.tonic.core.audio.capture

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * Real microphone capture, via `AudioRecord` — docs/30-PHASE-3-SPEC.md §5.1 and Stage 3.0.
 *
 * **Why this exists at all, stated plainly.** For three stages it did not, and
 * [UnavailableMicrophoneSource] was bound in its place, because Stage 3.0's acceptance criteria —
 * measured latency, CPU, dropout behavior, accuracy on recorded rather than synthesized signal — are
 * all properties of hardware that no test in this repository can observe. That reasoning was wrong in
 * one specific way: the *measurements* need a device, but the code does not, and deferring both
 * together meant Stages 3.1, 3.3 and 3.4 were built on a boundary nothing could cross. Singing was
 * unreachable in the shipped app, and the one question Phase 3 exists to answer — whether audiating a
 * degree across a silent gap is a real task — could not be asked of a person.
 *
 * So: this is written, and its measurements are still owed. Nothing here has been observed running on
 * a phone. What is verifiable on the JVM is verified elsewhere — everything downstream of the buffer
 * this returns is a pure function ([com.tonic.core.audio.pitch.PitchDetector],
 * [com.tonic.core.audio.pitch.SungResponseAnalyzer]) and is tested against synthesized audio at
 * exactly known frequencies. What remains unverified is the part this class is: whether `AudioRecord`
 * fills the array, how long it takes to start, and what the signal looks like once a real room and a
 * real preamp are in it.
 *
 * **Nothing captured here is persisted, cached, or exported** — §7, and the reason the buffer is
 * returned rather than written anywhere. It lives in memory for one attempt and is dropped.
 */
class AudioRecordMicrophoneSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : MicrophoneSource {
        /**
         * Permission, checked live on every read rather than cached.
         *
         * A learner can revoke microphone access in system settings while the app is in the
         * background, and §6.1 requires that to disable singing silently — "no nagging, no repeat
         * prompts, no degraded experience elsewhere." A value captured at construction would leave the
         * app offering a sung answer it could no longer take, and the failure would surface as a
         * mysteriously unreadable capture rather than as the control quietly not being there.
         */
        override val isAvailable: Boolean
            get() =
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED

        /**
         * Records up to [maxDurationMs] of mono audio, or returns empty if it cannot.
         *
         * Empty is the only failure mode this exposes, deliberately. Every caller already treats an
         * empty capture as "unclear" and re-prompts rather than scoring (§5.2), so a denied
         * permission, a microphone held by another app, and a device that refuses to initialize all
         * land in the branch that is already correct — none of them can become a wrong answer.
         */
        override suspend fun record(maxDurationMs: Long): CapturedAudio {
            if (!isAvailable) return CapturedAudio.empty(SAMPLE_RATE)
            // IO rather than Default: this blocks on a hardware read for the whole window, which would
            // occupy a Default worker for seconds at a time. The caller is already a coroutine that has
            // put the exercise into its answer state, so suspending here costs the UI nothing.
            return withContext(Dispatchers.IO) { readWindow(maxDurationMs) }
        }

        /**
         * Suppressed because lint cannot see the check, not because there isn't one.
         *
         * `MissingPermission` fires on the `AudioRecord` constructor below. The permission *is*
         * verified — [isAvailable] is read at the top of [record], and again on the line before the
         * constructor here so the guard sits where a reader looking at the risky call will find it —
         * but the check happens through a property getter and lint's dataflow does not follow it.
         *
         * The second half of lint's own advice is satisfied too: it asks that revocable-permission
         * calls "be prepared to handle the calls throwing an exception if the user rejects the request
         * at runtime," which is exactly what the `SecurityException` branch is for. Permission can be
         * revoked between the check and the constructor, and on a real phone eventually will be.
         */
        @SuppressLint("MissingPermission")
        private suspend fun readWindow(maxDurationMs: Long): CapturedAudio {
            // Re-read rather than trusting the caller's check: this is the last line before the
            // microphone is opened, and it is the one a reader will look for.
            if (!isAvailable) return CapturedAudio.empty(SAMPLE_RATE)
            val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, ENCODING)
            if (minBuffer <= 0) return CapturedAudio.empty(SAMPLE_RATE)

            val recorder =
                try {
                    AudioRecord(
                        audioSource(),
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        ENCODING,
                        minBuffer * BUFFER_HEADROOM,
                    )
                } catch (e: IllegalArgumentException) {
                    // Thrown for a parameter combination this device will not accept. Not recoverable
                    // by retrying, and not an error the learner should ever see: singing is optional.
                    return CapturedAudio.empty(SAMPLE_RATE)
                } catch (e: SecurityException) {
                    // Permission revoked between the check above and here. A real race on a real phone.
                    return CapturedAudio.empty(SAMPLE_RATE)
                }

            try {
                if (recorder.state != AudioRecord.STATE_INITIALIZED) return CapturedAudio.empty(SAMPLE_RATE)
                recorder.startRecording()
                if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    return CapturedAudio.empty(SAMPLE_RATE)
                }
                return CapturedAudio(readSamples(recorder, maxDurationMs, minBuffer), SAMPLE_RATE)
            } catch (e: IllegalStateException) {
                return CapturedAudio.empty(SAMPLE_RATE)
            } finally {
                // Both, in this order, and unconditionally. A recorder left running holds the
                // microphone against the rest of the system and lights the OS recording indicator on
                // a screen where nothing is being recorded — which is a promise broken in public.
                runCatching { recorder.stop() }
                recorder.release()
            }
        }

        /**
         * Reads until the window is full, converting to the `[-1, 1]` floats the analyzer expects.
         *
         * The conversion divides by 32768 rather than 32767 — the negative end of a signed 16-bit
         * sample reaches -32768, and dividing by the positive maximum would let a single fully
         * negative peak land outside the range every downstream stage assumes.
         */
        private suspend fun readSamples(
            recorder: AudioRecord,
            maxDurationMs: Long,
            chunkFrames: Int,
        ): FloatArray {
            val wanted = (SAMPLE_RATE * maxDurationMs / MS_PER_SECOND).toInt()
            val out = FloatArray(wanted)
            val chunk = ShortArray(chunkFrames.coerceAtMost(wanted).coerceAtLeast(1))
            var filled = 0

            while (filled < wanted) {
                // Cancellation is checked per chunk, not per window: skipping an item or an audio
                // interruption cancels the capture, and a loop that only checked at the end would hold
                // the microphone for the remainder of a five-second window after the reason for it
                // had gone.
                coroutineContext.ensureActive()
                val room = (wanted - filled).coerceAtMost(chunk.size)
                val read = recorder.read(chunk, 0, room)
                // Negative values are AudioRecord's error codes (ERROR_INVALID_OPERATION and friends);
                // zero means the device gave nothing this pass. Either way there is no more signal
                // coming, and returning what was captured is better than looping on a dead recorder.
                if (read <= 0) break
                for (i in 0 until read) {
                    out[filled + i] = chunk[i] / PCM_16_SCALE
                }
                filled += read
            }
            return if (filled == wanted) out else out.copyOf(filled)
        }

        /**
         * The least-processed input this device will give us.
         *
         * `UNPROCESSED` skips the automatic gain control, noise suppression and echo cancellation that
         * a phone applies to voice by default. Those are tuned to make speech intelligible over a
         * call, and every one of them can move a sustained pitch: AGC pumps the amplitude the detector
         * uses for its clarity threshold, and suppression reshapes exactly the steady harmonic content
         * the McLeod method measures. It is optional hardware, so `VOICE_RECOGNITION` — which
         * conventionally applies the least processing of the always-available sources — is the
         * fallback rather than `MIC`.
         *
         * ⚠️ Unmeasured. Whether the difference between these two is audible to the analyzer is one of
         * the things a device has to answer.
         */
        private fun audioSource(): Int {
            val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val unprocessed =
                manager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
            return if (unprocessed) {
                MediaRecorder.AudioSource.UNPROCESSED
            } else {
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            }
        }

        private companion object {
            /** Matches `PcmBuffer.DEFAULT_SAMPLE_RATE`, so capture and synthesis share one rate end to end. */
            const val SAMPLE_RATE = 48_000
            const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
            const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

            /**
             * Multiplier on the device's minimum buffer.
             *
             * The minimum is the smallest size that works, not a comfortable one: at that size a
             * scheduling hiccup drops samples, and a dropout in the middle of a sustained note is
             * exactly the artifact that turns a clean answer into "unclear". Memory is not the
             * constraint here — the largest window this app ever asks for is a few seconds of mono.
             */
            const val BUFFER_HEADROOM = 4

            const val MS_PER_SECOND = 1_000L

            /** 2^15. See [readSamples] for why not 32767. */
            const val PCM_16_SCALE = 32_768.0f
        }
    }
