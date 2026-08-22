# 06 — Audio Engine

## 1. Decisions

**All audio is synthesized in Kotlin at runtime. There are no audio assets in the APK.**

Reasons, in order of weight:
1. Copyright exposure is zero. No sample licensing, ever.
2. Timbre variation (`02-PEDAGOGY.md` §5) requires arbitrary pitches across a wide register in four timbre families. Sampling that properly means hundreds of files; synthesizing it means a few hundred lines of code.
3. Exact control over tuning, envelope, and onset timing — which matters because onset artifacts can leak cues.
4. APK size.

**Output path: `android.media.AudioTrack` in `MODE_STREAM`, PCM float, from a dedicated thread.**

Not `MediaPlayer` (no sample-accurate scheduling), not `SoundPool` (sample-based), not `ExoPlayer` (wrong tool). Not Oboe/NDK **in Phase 1**: Oboe's value is low round-trip latency for input-driven interaction, and Phase 1 has no tap-timing or mic input. Phase 4 (rhythm) will require revisiting this. Architect `AudioPlayer` behind an interface so the backend can be swapped without touching callers.

**Rendering is pure; playback is not.** All synthesis functions take parameters and return `FloatArray`. They are unit-testable on the JVM with no device. Only the thin `AudioPlayer` wrapper touches Android.

## 2. Signal chain

```
ItemPlan ──> Voice list ──> per-voice render ──> mix ──> master limiter ──> dither/clip ──> AudioTrack
```

- Sample rate: 48000 Hz. Query `AudioTrack.getNativeOutputSampleRate` and prefer the device native rate if it differs; resampling in the framework is a source of subtle artifacts.
- Internal processing: 32-bit float, mono synthesis, duplicated to stereo at output (`ENCODING_PCM_FLOAT`, `CHANNEL_OUT_STEREO`).
- Headroom: mix at −12 dBFS nominal. A soft limiter catches sums; hard clipping must never occur, because clipping generates harmonics that are themselves a pitch cue.

## 3. Timbre bank

Four families, all synthesized. Each must be recognizable as a distinct sound while having unambiguous pitch.

| `TimbreId` | Method | Character |
|---|---|---|
| `PURE` | Single sine + slight ADSR | Neutral reference. Hardest for some listeners — no harmonic reinforcement |
| `SOFT` | Additive: fundamental + 2nd + 3rd partial, decaying amplitudes, gentle attack | Organ-like, easy, forgiving |
| `PLUCK` | Decaying additive (4 partials, each with its own exponential decay — higher harmonics fade faster) plus a brief seeded noise "pick" transient | Guitar/harp-like, strong transient |
| `REED` | Additive with odd-harmonic emphasis + slight vibrato (5 Hz, ±8 cents) after 200 ms onset | Sustained wind/voice-like |

**Deviation from an earlier draft of this spec:** PLUCK was originally specified as Karplus-Strong (delay line + averaging filter). Two Karplus-Strong implementations were built and both produced measurably wrong pitch under the FFT accuracy test (`09-BUILD-PLAN.md` Stage 1.2) — one locked onto a harmonic multiple of the target rather than the fundamental — with no way to verify a DSP fix by ear in the build environment. Since correct pitch is non-negotiable for a scale-degree curriculum, PLUCK ships as decaying-additive synthesis instead: the same phase-accurate partial mechanism already verified correct for PURE/SOFT/REED, with per-partial exponential decay reproducing a plucked string's audible signature (bright attack settling into a purer tone). See `TimbreBank.renderPluck`'s own KDoc for the full account. Revisit a real Karplus-Strong implementation once there's a device to verify it by ear.

Requirements:

- Every timbre must produce a clear, unambiguous fundamental across MIDI 36–96. Test this — an additive patch that is fine at MIDI 60 can be perceptually ambiguous at MIDI 84.
- Perceived loudness must be matched across timbres. Equal peak amplitude is not equal loudness. Normalize by an RMS or simple loudness estimate per timbre per register, and verify by ear before shipping. **An uncontrolled loudness difference is a confound: it becomes an unintended cue.**
- Vibrato depth must stay well under a semitone so it never obscures pitch identity.

## 4. Envelope

Universal ADSR with per-timbre parameters. Constraints:

- Attack ≥ 5 ms always. A zero-length attack produces a click, and clicks are broadband transients that carry no pitch but do carry attention.
- Release ≥ 20 ms with a smooth taper to zero.
- Note tails must not overlap the next note unless the plan explicitly says so (a drone does; sequential cadence tones do not).

## 5. Reference plan rendering

`ReferencePlan` is produced by `:core:curriculum` from the `CADENCE_FADE` level. `:core:audio` renders it. The audio layer must not decide *what* the reference is — it only decides how it sounds.

| Fade level | Rendered as |
|---|---|
| L0 | I–IV–V–I, block chords, root position, ~600 ms each, then 300 ms gap, then target |
| L1 | Same, but flagged `reusableForItems = 2..3` — the ViewModel does not re-request it |
| L2 | V–I only |
| L3 | I triad only, ~800 ms |
| L4 | Tonic root sustained under the entire item at −18 dB relative to target |
| L5 | Tonic root ~400 ms, then a silent gap (default 1500 ms), then target |
| L6 | Nothing per item; the block's opening established the key |
| L7 | As L6, with the block's inter-item gap growing linearly across the block |

Chords are rendered as three or four simultaneous voices with slight per-voice onset jitter (≤8 ms) so they do not sound synthetically fused. Jitter is seeded, not random.

## 6. Tuning

- Twelve-tone equal temperament. `f = a4 * 2^((midi - 69) / 12)`, `a4` from settings (default 440.0).
- The tuning function lives in `:core:model` — it is domain logic, and the diagnostic's cent-level thresholds depend on it.
- Cent offsets must be expressible for `M0.PITCH_DIR` sub-semitone trials: `f = base * 2^(cents / 1200)`.

## 7. Playback wrapper

```kotlin
interface AudioPlayer {
    suspend fun play(buffer: PcmBuffer): PlaybackHandle
    fun stop()
    val state: StateFlow<PlaybackState>
}
```

Requirements:

- Dedicated thread, `THREAD_PRIORITY_URGENT_AUDIO`.
- Buffer size: at least `getMinBufferSize` × 2. Under-runs are audible and destroy the exercise.
- **Pre-render the next item while the current one awaits an answer.** Rendering is cheap but not free, and the gap between answer and next item must feel instant.
- Full item audio is rendered to a single contiguous buffer before playback begins. Do not stream-generate mid-item; a hiccup mid-cadence corrupts the exercise.

## 8. Audio focus and interruptions

Mandatory handling:

- `AUDIOFOCUS_LOSS_TRANSIENT` (call, notification): pause immediately, discard the current item, restore on regain. Do not score an item the user could not hear.
- `AUDIOFOCUS_LOSS`: end the session cleanly, persist resume state.
- `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`: do **not** duck. Ducking changes the loudness of a stimulus mid-item, which corrupts the trial. Pause instead.
- `ACTION_AUDIO_BECOMING_NOISY` (headphones unplugged): pause, discard current item.
- Any interruption mid-item marks the attempt `abandoned` and it does not enter the confusion matrix or the mastery window.

## 9. Headphone guidance

The app should note once, non-blockingly, that headphones or decent speakers improve the experience — phone speakers roll off low frequencies badly, which affects low-register items. Do not gate on it. Instead, constrain `REGISTER_SPREAD` level 0–1 to MIDI 55–84, a range phone speakers reproduce acceptably.

## 10. Testing the audio layer

See `10-TESTING.md`. Summary of what must be tested without a device:

- Frequency accuracy: render a note, FFT the buffer, assert the peak bin is within 1 cent of target.
- Loudness matching: render the same MIDI note in all four timbres, assert RMS within a defined tolerance.
- No clipping: assert `max(abs(sample)) < 1.0` across a corpus of generated items.
- No discontinuities: assert no sample-to-sample delta exceeds a threshold (catches click-producing envelope bugs).
- Determinism: same seed produces a byte-identical buffer.
- Silence correctness: gaps are actually silent, not low-level noise.
