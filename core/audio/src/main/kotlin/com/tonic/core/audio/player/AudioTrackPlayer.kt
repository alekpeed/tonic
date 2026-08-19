package com.tonic.core.audio.player

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import com.tonic.core.audio.synth.PcmBuffer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `AudioTrack` in `MODE_STREAM`, PCM float, on a dedicated thread —
 * docs/06-AUDIO-ENGINE.md §7. Not `MediaPlayer` (no sample-accurate
 * scheduling) or `SoundPool` (sample-based) — see docs/06-AUDIO-ENGINE.md §1.
 *
 * Three properties this class has to get right, all of them learned the hard way:
 *
 * 1. **A write is not a playback.** `write` returns when frames are copied into the track's buffer,
 *    not when they are heard. Teardown is gated on [PlayoutMonitor] watching the playback head reach
 *    the last written frame — see that class for what breaks otherwise.
 * 2. **[play] starts playback; it does not perform it.** Callers sequence audio against
 *    [PlaybackHandle.awaitCompletion] (`:feature:diagnostic` gates its answer buttons on it,
 *    `:feature:practice` chains the three-part incorrect-answer contrast on it), while others fire a
 *    replay and move on. So [play] hands back a handle promptly and the streaming runs on
 *    [playbackScope]; only the handle waits.
 * 3. **The buffer is primed before the transport starts.** Starting an empty track and racing to
 *    fill it under-runs on the first frames — "under-runs are audible and destroy the exercise."
 *
 * **Still unverified by an automated test.** [PlayoutMonitor]'s decision logic is unit-tested, but
 * nothing here exercises a real `AudioTrack`: this module is built in an environment with no device
 * or emulator (no `/dev/kvm`), and the unit-test android.jar returns stub values. The manual
 * listening gate docs/09-BUILD-PLAN.md Stage 2 requires is how this gets confirmed.
 */
@Singleton
class AudioTrackPlayer
    @Inject
    constructor() : AudioPlayer {
        private val _state = MutableStateFlow(PlaybackState.IDLE)
        override val state: StateFlow<PlaybackState> = _state.asStateFlow()

        private val playbackDispatcher =
            Executors
                .newSingleThreadExecutor { runnable ->
                    Thread(runnable, "AudioTrackPlayer").apply {
                        isDaemon = true
                    }
                }.asCoroutineDispatcher()

        /**
         * Survives any one caller's coroutine: a `play` whose caller is cancelled mid-note must still
         * tear its track down rather than leak it. Not `GlobalScope` (CLAUDE.md §7) — it is owned by
         * this `@Singleton` and shut down with it.
         */
        private val playbackScope = CoroutineScope(playbackDispatcher + SupervisorJob())

        /** Serializes [play] against itself so two callers can't interleave stop/join/start. */
        private val startLock = Mutex()

        /** Guards [currentTrack]'s lifecycle — [stop] runs on the caller's thread, teardown on the audio thread. */
        private val trackLock = Any()

        private var currentTrack: AudioTrack? = null
        private val stopRequested = AtomicBoolean(false)
        private var playbackJob: Job? = null

        override suspend fun play(buffer: PcmBuffer): PlaybackHandle {
            val completion = CompletableDeferred<Unit>()
            startLock.withLock {
                // Stop first, then join: stopping unblocks a write that is blocked on a full buffer and
                // short-circuits the previous drain, so the join can't wait out the old clip's duration.
                stop()
                playbackJob?.join()
                stopRequested.set(false)
                playbackJob = playbackScope.launch { stream(buffer, completion) }
            }
            return object : PlaybackHandle {
                override suspend fun awaitCompletion() {
                    completion.await()
                }
            }
        }

        override fun stop() {
            stopRequested.set(true)
            synchronized(trackLock) {
                currentTrack?.let {
                    // pause + flush, not stop: stop() asks the track to drain what it holds, which is the
                    // opposite of what an interruption wants (docs/06-AUDIO-ENGINE.md §8 - "never a soft fade").
                    runCatching { it.pause() }
                    runCatching { it.flush() }
                }
            }
        }

        private suspend fun stream(
            buffer: PcmBuffer,
            completion: CompletableDeferred<Unit>,
        ) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            var track: AudioTrack? = null
            try {
                val stereo = toInterleavedStereo(buffer.samples)
                val totalFrames = buffer.samples.size
                track = buildTrack(buffer.sampleRate)
                synchronized(trackLock) { currentTrack = track }

                var offset = prime(track, stereo)
                track.play()
                _state.value = PlaybackState.PLAYING

                while (offset < stereo.size && !stopRequested.get()) {
                    val written = track.write(stereo, offset, stereo.size - offset, AudioTrack.WRITE_BLOCKING)
                    if (written <= 0) break
                    offset += written
                }

                val playedOut = !stopRequested.get() && awaitPlayout(track, totalFrames, buffer.durationMs)
                _state.value = if (playedOut) PlaybackState.COMPLETED else PlaybackState.STOPPED
            } finally {
                synchronized(trackLock) {
                    currentTrack = null
                    track?.let {
                        runCatching { it.stop() }
                        runCatching { it.release() }
                    }
                }
                // Always, including on cancellation - a caller awaiting this handle must never hang.
                completion.complete(Unit)
            }
        }

        private fun buildTrack(sampleRate: Int): AudioTrack {
            val minBufferBytes =
                AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_FLOAT,
                )
            // "Buffer size: at least getMinBufferSize x 2. Under-runs are audible and destroy the exercise."
            // Bounded rather than sized to the whole clip: an oversized buffer swallows every write
            // instantly, which is what let teardown outrun playback before.
            val floorBytes = bytesForMs(PRIME_MS, sampleRate)
            val trackBufferBytes =
                if (minBufferBytes > 0) (minBufferBytes * 2).coerceAtLeast(floorBytes) else floorBytes

            return AudioTrack
                .Builder()
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                ).setAudioFormat(
                    AudioFormat
                        .Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                ).setBufferSizeInBytes(trackBufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }

        /**
         * Fills the track before the transport starts, so the first frames are already queued when the
         * speaker wakes. Non-blocking on purpose: a blocking write here would never return, because
         * nothing drains the buffer until [AudioTrack.play].
         */
        private fun prime(
            track: AudioTrack,
            stereo: FloatArray,
        ): Int {
            var offset = 0
            while (offset < stereo.size) {
                val written = track.write(stereo, offset, stereo.size - offset, AudioTrack.WRITE_NON_BLOCKING)
                if (written <= 0) break
                offset += written
            }
            return offset
        }

        /** True if every written frame was heard; false if [stop] cut in or the head never arrived. */
        private suspend fun awaitPlayout(
            track: AudioTrack,
            totalFrames: Int,
            durationMs: Double,
        ): Boolean {
            val monitor = PlayoutMonitor(totalFrames, PlayoutMonitor.pollsFor(durationMs, POLL_INTERVAL_MS))
            while (!stopRequested.get()) {
                when (monitor.observe(track.playbackHeadPosition)) {
                    PlayoutMonitor.Verdict.DONE -> return true
                    PlayoutMonitor.Verdict.GIVE_UP -> return false
                    PlayoutMonitor.Verdict.KEEP_WAITING -> delay(POLL_INTERVAL_MS)
                }
            }
            return false
        }

        /** Mono -> stereo by sample duplication — docs/06-AUDIO-ENGINE.md §2. */
        private fun toInterleavedStereo(mono: FloatArray): FloatArray {
            val out = FloatArray(mono.size * 2)
            for (i in mono.indices) {
                out[i * 2] = mono[i]
                out[i * 2 + 1] = mono[i]
            }
            return out
        }

        private fun bytesForMs(
            ms: Long,
            sampleRate: Int,
        ): Int = (ms * sampleRate / 1000L).toInt() * STEREO_CHANNELS * Float.SIZE_BYTES

        private companion object {
            /** Buffer floor, and the amount queued before the transport starts. */
            const val PRIME_MS = 250L

            /** Fine enough that stopping feels immediate, coarse enough not to spin the audio thread. */
            const val POLL_INTERVAL_MS = 10L

            const val STEREO_CHANNELS = 2
        }
    }
