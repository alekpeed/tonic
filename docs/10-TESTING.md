# 10 — Testing Strategy

## 1. Principle

The correctness of this app is mostly invisible. A staircase that converges to the wrong point, a generator that quietly under-samples degree 6, a timbre that is 3 dB louder than the others — none of these produce a crash, a visual defect, or a user complaint that identifies the cause. They produce an app that teaches badly.

Therefore the test suite is not primarily about preventing crashes. It is about proving that the pedagogy is implemented as specified.

## 2. Test distribution

| Layer | Type | Where | Speed |
|---|---|---|---|
| `:core:model` | JVM unit | local | ms |
| `:core:curriculum` | JVM unit + property-based | local | ms |
| `:core:engine` | JVM unit + simulation | local | ms–s |
| `:core:audio` (render) | JVM unit + DSP analysis | local | s |
| `:core:audio` (playback) | Instrumented | device | slow |
| `:core:data` | Instrumented (Room) | device/Robolectric | medium |
| `:feature:*` | Compose UI test | device/Robolectric | medium |
| End-to-end | Manual + scripted | device | slow |

The bulk of the value is in the first four rows, all of which run without a device. Target ≥85% line coverage on `:core:model`, `:core:curriculum`, `:core:engine`.

## 3. Determinism is a testable property

Every generator and engine function is pure and seeded (`CLAUDE.md` §5). Test it directly:

- Same seed + same state → identical output, asserted structurally, not by hash alone (a hash test tells you it broke, not how).
- Different seeds → different output, with no accidental degeneracy (e.g. all seeds producing key of C).
- No test may depend on wall-clock time. `Clock` is injected everywhere; a fixed test clock is used.
- No test may use unseeded `Random`.

A flaky test in this project is a bug in the test or a determinism violation in the code. Do not add retries. Find it.

## 4. Property-based testing

Use property tests for the generators. The properties that matter:

- For every valid `(skill, axisLevels, seed)`: the generated item is playable, the target degree is in the active set, the target MIDI is within the register bounds for that axis level, and the reference plan matches the cadence-fade level.
- Over any 20-item window: degree frequency balance holds.
- Over any sequence: no consecutive duplicate (key, degree, octave) triple.
- For every cadence-fade level 0–7: the rendered plan duration is within expected bounds.

Enumerate the axis-level space exhaustively where it is small enough — six axes with 3–8 levels each is a few thousand combinations, which is cheap to test completely rather than sample.

## 5. Simulation testing (the most important tests)

Test the adaptive engine against **simulated learners** with known properties. This is how you find out whether the engine actually works before a human uses it.

Build a `SimulatedResponder` with a configurable psychometric function: probability of a correct answer as a function of difficulty level, plus a configurable bias and lapse rate.

Required simulations:

1. **Staircase convergence.** Responder with a known 70.7% point. Assert the staircase converges near it. Report the measured value.
2. **Improving learner.** Responder whose ability rises over 500 items. Assert axis levels advance, mastery is eventually reached, and progression is monotonic-ish without thrashing.
3. **Plateaued learner.** Ability fixed mid-range. Assert the engine stabilizes rather than oscillating, and that mastery is *not* falsely granted.
4. **Struggling learner.** Ability low. Assert the 60%-accuracy safety valve fires, difficulty drops, and the user is never trapped in an unwinnable state.
5. **Biased responder.** Always answers "1". Assert mastery is never granted and the confusion tracker identifies the pattern.
6. **Cadence-dependent learner.** High accuracy at fade L0–L3, chance at L6. Assert `M2.INDEPENDENCE_CHECK` fails and the fade axis is lowered. **This simulation directly tests the app's central pedagogical claim** — if it passes a learner who cannot function without the crutch, the app is broken in exactly the way competing apps are broken.
7. **Long-horizon retention.** Simulated learner over 90 days of sessions with forgetting. Assert FSRS scheduling produces sensible review intervals and mastered nodes do not decay unnoticed.

These simulations are the primary evidence that the engine is correct. Treat their output as a report, not just a pass/fail.

## 6. Audio testing without ears

All render-path tests run on the JVM against `FloatArray` output.

| Property | Method | Assertion |
|---|---|---|
| Pitch accuracy | FFT, find peak bin, interpolate | within 1 cent of target |
| Loudness matching | RMS per timbre, same note | within defined tolerance across all four |
| No clipping | max abs sample | < 1.0 |
| No clicks | max sample-to-sample delta | below threshold |
| Silence in gaps | RMS over the gap window | below noise floor |
| Envelope shape | attack/release segment analysis | attack ≥ 5 ms, release ≥ 20 ms |
| Determinism | byte comparison | identical for identical seed |
| Register integrity | pitch accuracy at MIDI 40/55/69/84/96 | passes at every point, every timbre |

Implement a small FFT in the test source set rather than adding a production dependency.

**What cannot be automated:** whether a timbre sounds like a musical note with an unambiguous pitch, and whether loudness matching is perceptually convincing. That requires listening on a real device. It is a required manual gate at Stage 1.2 (`09-BUILD-PLAN.md`).

## 7. Persistence testing

- Room `MigrationTestHelper` test for every migration, loading a real prior-version database file.
- `rebuildFromAttempts()` verified against a synthetic 1,000-attempt log: rebuilt state must equal incrementally-maintained state exactly. This is the test that justifies treating `attempts` as the source of truth.
- Interruption/resume: kill mid-session, restore, assert no attempt is lost or double-counted.
- Concurrency: attempts written while the engine reads state — assert no torn reads.

## 8. UI testing

- Compose tests for the degree ladder: correct button set per active degree set, all visual states, click routing, content descriptions present.
- Layout test at 200% font scale on a 5-inch reference device: no scroll, no clipping.
- Screenshot tests are optional; if added, they must not be the primary correctness signal.

## 9. Copy compliance test (mandatory)

An automated test that scans `strings.xml` for forbidden terms and fails the build on any match.

Forbidden list (minimum — extend as needed): `tone deaf`, `tone-deaf`, `amusia`, `tin ear`, `talent`, `gifted`, `natural ability`, `no ear for music`, and any guilt/loss/urgency streak phrasing.

This is not stylistic pedantry. `02-PEDAGOGY.md` §8 makes the language constraint a hard requirement, and a hard requirement that is only enforced by review will eventually be violated.

## 10. What not to test

- Do not write timing-sensitive tests against real audio playback in CI. They are flaky and prove little. Test the render path deterministically; test playback manually on a device.
- Do not test Compose internals, Room internals, or the framework.
- Do not write tests that assert implementation details of pure functions (which private helper was called). Assert behavior.

## 11. Manual test checklist (per stage gate)

Run on a real mid-range device, with headphones:

- [ ] All four timbres, full register — pitch clear, loudness matched, no clicks
- [ ] All eight cadence-fade levels sound as intended
- [ ] Incoming call mid-item → paused, item discarded, resumes cleanly
- [ ] Headphones unplugged mid-item → paused, item discarded
- [ ] Backgrounded and restored → session intact
- [ ] Rotation mid-item → no audio restart, no state loss
- [ ] Airplane mode → full functionality
- [ ] Force stop mid-session → resume offered, no data loss
- [ ] Full diagnostic run → under 6 minutes, sensible placement
- [ ] 10-minute session → no audio glitches, no frame drops, no battery anomaly
