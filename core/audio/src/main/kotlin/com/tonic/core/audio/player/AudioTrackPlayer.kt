package com.tonic.core.audio.player

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import com.tonic.core.audio.synth.PcmBuffer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `AudioTrack` in `MODE_STREAM`, PCM float, on a dedicated thread —
 * docs/06-AUDIO-ENGINE.md §7. Not `MediaPlayer` (no sample-accurate
 * scheduling) or `SoundPool` (sample-based) — see docs/06-AUDIO-ENGINE.md §1.
 *
 * **Unverified on a real device.** This module was built in an environment
 * with no Android device or emulator (no `/dev/kvm`). Everything here
 * compiles and follows the spec's requirements (thread priority, buffer
 * sizing, mono-to-stereo duplication, MODE_STREAM), but actual audio output
 * — and the manual listening gate docs/09-BUILD-PLAN.md Stage 2 requires —
 * has not been exercised.
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

        @Volatile private var currentTrack: AudioTrack? = null
        private val stopRequested = AtomicBoolean(false)

        override suspend fun play(buffer: PcmBuffer): PlaybackHandle {
            stop()
            stopRequested.set(false)
            val completion = CompletableDeferred<Unit>()

            withContext(playbackDispatcher) {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

                val stereo = toInterleavedStereo(buffer.samples)
                val minBufferBytes =
                    AudioTrack.getMinBufferSize(
                        buffer.sampleRate,
                        AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_FLOAT,
                    )
                // "Buffer size: at least getMinBufferSize x 2. Under-runs are audible and destroy the exercise."
                val trackBufferBytes = (minBufferBytes * 2).coerceAtLeast(stereo.size * Float.SIZE_BYTES)

                val track =
                    AudioTrack
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
                                .setSampleRate(buffer.sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                .build(),
                        ).setBufferSizeInBytes(trackBufferBytes)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()

                currentTrack = track
                track.play()
                _state.value = PlaybackState.PLAYING

                var offset = 0
                while (offset < stereo.size && !stopRequested.get()) {
                    val written = track.write(stereo, offset, stereo.size - offset, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) break
                    offset += written
                }

                if (stopRequested.get()) {
                    track.stop()
                    _state.value = PlaybackState.STOPPED
                } else {
                    // Let the hardware drain the buffer it already has before tearing down.
                    track.stop()
                    _state.value = PlaybackState.COMPLETED
                }
                track.release()
                currentTrack = null
                completion.complete(Unit)
            }

            return object : PlaybackHandle {
                override suspend fun awaitCompletion() {
                    completion.await()
                }
            }
        }

        override fun stop() {
            stopRequested.set(true)
            currentTrack?.let {
                runCatching { it.pause() }
                runCatching { it.flush() }
            }
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
    }
