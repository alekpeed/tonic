# 09 — Build Plan

## How to use this document

Work through the stages in order. Each stage ends with a **STOP** gate.

At a STOP gate:
1. Halt. Do not begin the next stage.
2. Report: files added/changed, what was verified and how, deviations from spec with reasons, open questions.
3. Wait for explicit approval in chat.

Do not batch stages. Do not "get ahead." A stage that is 90% done and blocked is a better report than three stages half-built.

---

## Stage 0 — Skeleton

Build:
- Gradle project, version catalog, all modules from `04-ARCHITECTURE.md` §2, empty but wired.
- Dependency direction enforcement: a JVM test in `:core:model`/`:core:curriculum`/`:core:engine` that fails if any `android.*` or `androidx.*` import appears in those source sets.
- Hilt wired, `MainActivity` with an empty Compose scaffold, navigation graph with placeholder routes.
- ktlint configured and passing.
- CI-equivalent local command documented in the README: one command that builds, lints, and runs all JVM tests.

Acceptance:
- `./gradlew build` succeeds.
- The dependency-direction test exists and demonstrably fails when an Android import is added to a pure module (verify by temporarily adding one).
- App launches to an empty screen.

**STOP.**

---

## Stage 1 — Domain model

Build `:core:model` in full: all types listed in `04-ARCHITECTURE.md` §3, tuning math (`06-AUDIO-ENGINE.md` §6), `Clock` abstraction, serializable state classes with `schemaVersion`.

Acceptance:
- Tuning: MIDI 69 → 440.0 Hz exactly; cent offsets correct to within floating-point tolerance; round-trip midi↔frequency stable across MIDI 21–108.
- Scale degree ↔ semitone-offset mapping correct for major mode.
- Serialization round-trips for every `@Serializable` type, including unknown-field tolerance.
- ≥90% coverage on this module.

**STOP.**

---

## Stage 2 — Audio engine

Build `:core:audio` per `06-AUDIO-ENGINE.md`: four timbres, ADSR, mixing, limiter, reference-plan rendering, `AudioTrack` player, focus handling.

Acceptance (all automated except the last):
- FFT test: rendered note peak within 1 cent of target, for all four timbres across MIDI 40, 55, 69, 84, 96.
- Loudness: RMS across timbres for the same note within the defined tolerance.
- No clipping across a corpus of 200 generated buffers.
- No sample-to-sample discontinuity above threshold (click detection).
- Byte-identical output for identical seeds.
- All eight cadence-fade levels render plans of the expected structure and duration.
- **Manual verification on a real device required at this gate.** Listen to all four timbres across the register. Confirm pitch is unambiguous, loudness is matched, and there are no clicks. Report subjective findings — this cannot be automated and it is where the audio quality is actually decided.

**STOP.**

---

## Stage 3 — Curriculum and generators

Build `:core:curriculum`: skill graph, `M0` and `M2` item generators, `ReferencePlanBuilder`, `BalancedSampler`.

Acceptance:
- Determinism: 10,000 generations with fixed seeds are reproducible.
- Balance: over 20-item windows, no degree exceeds 1.5× expected frequency.
- No (key, degree, octave) triple repeats consecutively.
- `KEY_SPREAD` constraint holds: no key repeats more than twice consecutively.
- Generated items respect axis bounds at every level, for every axis combination (property-based test).
- All degrees in the active set are reachable at every axis configuration.

**STOP.**

---

## Stage 4 — Adaptive engine

Build `:core:engine` per `07-ADAPTIVE-ENGINE.md`: staircase, d-prime, axis scheduler, confusion tracker, mastery evaluator, FSRS scheduler, session composer.

FSRS note: **first check whether a maintained Kotlin/JVM FSRS implementation exists.** Report what you find before implementing. If nothing suitable exists, implement FSRS-6 from the published spec with default parameters, and say so explicitly.

Acceptance:
- Staircase converges to ~70.7% against a simulated responder with a known psychometric function. Run this simulation and report the measured convergence point — this is the single best check that the engine works.
- Step-size halving, reversal counting, and bounds clamping behave per spec.
- d-prime matches hand-computed values for known hit/false-alarm rates, including the extreme-rate correction.
- Axis scheduler moves exactly one axis at a time; the 60%-accuracy safety valve fires correctly.
- Confusion weights respect the 2.5× cap.
- Mastery evaluator: all five criteria tested independently, each shown to block on its own.
- FSRS grade mapping from block accuracy is correct at every boundary.
- Session composer honors warm-up exclusion, blocking-then-interleaving, and never-end-on-failure.

**STOP.**

---

## Stage 5 — Persistence

Build `:core:data` per `05-DATA-MODEL.md`: entities, DAOs, repositories, DataStore, schema export.

Acceptance:
- `rebuildFromAttempts()` reconstructs skill state identically to incremental updates, verified against a synthetic 1,000-attempt log. This is the test that proves the attempt log is genuinely the source of truth.
- `allowBackup=false` set; `fallbackToDestructiveMigration` absent from the codebase.
- Repositories expose only domain types; a test asserts no Room entity is public.
- DataStore defaults correct, including `daily_reminder_enabled = false`.

**STOP.**

---

## Stage 6 — Practice loop (headless)

Wire the loop end to end with no real UI: a test harness or debug screen that runs generation → render → play → answer → record → adapt.

Acceptance:
- A 50-item session completes without audio glitches on a real device.
- Attempts persist correctly; the session is fully replayable from `rootSeed`.
- Pre-rendering keeps inter-item latency imperceptible; measure and report it.
- Interruption handling verified: incoming call, headphone unplug, app backgrounded, device rotated. In every case the current item is discarded rather than scored.

**STOP.**

---

## Stage 7 — Practice UI

Build `:core:ui` and `:feature:practice` per `08-UI-SPEC.md`. The degree ladder is the centerpiece — build it carefully.

Acceptance:
- Ladder fits seven degrees plus gaps on a 5-inch screen, no scroll, at 200% font scale.
- All button states render correctly; correct/incorrect signaled by more than color.
- Replay unlimited and unpenalized.
- TalkBack navigates the ladder sensibly.
- Dark theme complete.
- `@Preview` for every composable.

**STOP.**

---

## Stage 8 — Diagnostic UI

Build `:feature:diagnostic`. All four M0 sub-tests, adaptive termination, placement output.

Acceptance:
- Completes in under 6 minutes for a typical responder (simulate and measure).
- Placement mapping (`07-ADAPTIVE-ENGINE.md` §5) produces correct axis levels for boundary inputs.
- **Copy audit:** no evaluative language, no score display, no diagnosis, and the forbidden terms from `02-PEDAGOGY.md` §8 and `08-UI-SPEC.md` §10 appear nowhere in `strings.xml`. Add an automated test that greps the string resources for the forbidden terms.
- The amusia flag is not reachable from any composable.

**STOP.**

---

## Stage 9 — Home, summary, progress, settings

Build the remaining features.

Acceptance:
- Mastery map shows the blocking criterion in plain language.
- Per-degree accuracy accurate against a known attempt log.
- Confusion view produces correct plain-language statements.
- Settings changes take effect immediately (label style especially).
- Streak includes the forgiveness mechanic; no prohibited mechanics present.

**STOP.**

---

## Stage 10 — Hardening

- Full-app pass: rotation, process death, low memory, airplane mode, storage pressure.
- Verify no network permission in the manifest at all.
- R8/ProGuard configuration; verify release build works, especially serialization.
- APK size check.
- Performance: no dropped frames in the practice loop; no ANRs.
- Update every doc that drifted from the implementation.

Acceptance: all Phase 1 success criteria in `01-PRODUCT-SPEC.md` §5 demonstrably met, with evidence per criterion.

**STOP. Phase 1 complete.**

---

## Later phases (do not start without instruction)

| Phase | Content | Notes |
|---|---|---|
| 2 | Minor mode, chromatic degrees, prediction/audiation items, data export | |
| 3 | Optional sung response | Mic permission, pitch detection, real-time feedback. Never a gate |
| 4 | Rhythm (M3) | **Requires revisiting the audio backend** — low-latency input likely means Oboe/NDK, plus round-trip latency calibration |
| 5 | Melodic dictation (M4) | |
| 6 | Harmony and harmonic dictation (M5, M6) | Bass-line first |
| 7 | Real-music bridge (M7) | Blocked on owned/licensed audio. See `02-PEDAGOGY.md` §9 |
| 8 | Advanced/modal (M8), desktop build | |
