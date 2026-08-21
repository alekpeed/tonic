# 01 — Product Specification

## 1. What this is

An Android ear training application that takes a person with **no musical background whatsoever** and builds functional aural skill from the ground up.

"No musical background" is meant literally. The target user:

- plays no instrument and is not required to acquire one
- cannot read staff notation and will not be taught to as a prerequisite
- does not know what a "third" or a "key" is
- may not be confident they can hear pitch differences at all

Every design decision follows from that. Any exercise that requires an instrument, a keyboard, notation, or singing in order to answer is disqualified.

## 2. What the user gets

The ability to hear a note in a piece of music and know **what it is doing** — that it is the tonic, or the leading tone resolving upward, or the third of the chord — without an instrument in hand and without conscious calculation. In the literature this is **functional hearing** and its endpoint is **audiation**: hearing and comprehending music internally when no sound is present.

This is deliberately not the same product as "identify this interval," which is what most competing apps train and which transfers poorly to real listening. See `02-PEDAGOGY.md` §1.

## 3. Phase 1 scope

Phase 1 ships:

- **Module 0 — Diagnostic and Placement.** Pitch direction discrimination, same/different, short tonal memory span, and a soft screen for congenital amusia indicators. Outputs starting difficulty for the adaptive engine.
- **Module 2 — Diatonic Functional Recognition.** The core loop. A key is established, a note sounds, the user identifies its scale degree. Difficulty advances along six independent axes, including progressive removal of the harmonic reference (cadence fade).
- **Full supporting infrastructure**, built to final quality, not stubbed: audio synthesis engine, adaptive difficulty engine, spaced repetition scheduler, confusion tracking, persistence, progress display.

Module 1 (pitch primitives) is folded into Module 0's remediation path in Phase 1 rather than shipped as a standalone module.

## 4. Explicit non-goals for Phase 1

| Not building | Why |
|---|---|
| Microphone input / sung response | Deferred to Phase 3. Evidence that production accelerates perception is correlational, and mandatory singing drives dropout. Recognition is the spine. |
| Rhythm module | Independent track; requires low-latency input and latency calibration, the hardest engineering problem in the project. Phase 4. |
| Melodic dictation | Depends on mastered functional recognition. Phase 5. |
| Harmony / chord identification | Phase 6. |
| Real-music excerpts | Not in the product at all. Clearing a commercial recording means clearing two separate copyrights per track, for material the synthesis engine already covers. Closed decision — see `02-PEDAGOGY.md` §9. |
| Absolute pitch training | Out of scope permanently for the core product. Adult acquisition evidence exists but is small-sample and pre-selected; it is not required for musicianship and wastes beginner effort. |
| Staff notation | Optional display far later. Never a prerequisite. |
| Accounts, cloud sync, social features, leaderboards | Local-only. No network. |
| Tablet/foldable optimization | Phone-first. Do not break on tablets, but do not design for them. |
| Desktop build | Later. Architecture keeps the door open; nothing more. |

## 5. Success criteria for Phase 1

Functional:

1. A user who has never used the app can complete the diagnostic in under 6 minutes and be placed at a sensible starting difficulty.
2. A user can complete a 5-minute practice session and see accurate progress reflected afterward.
3. The app functions fully in airplane mode. There is no code path that requires the network.
4. Audio playback is glitch-free on a mid-range device (target: no buffer underruns during a 10-minute session).
5. All progress survives app kill, device reboot, and app update.

Pedagogical (measurable in-app):

6. A user who reaches mastery of the full diatonic set at cadence-fade level L4 or higher can identify scale degrees **without** a preceding cadence at ≥85% accuracy. This is the real test — it distinguishes functional hearing from cadence-crutch dependence, which is the documented failure mode of existing functional trainers.
7. Accuracy generalizes across timbre and register: performance on an untrained timbre is within 10 percentage points of trained timbre performance.

Engineering:

8. `:core:model`, `:core:curriculum`, and `:core:engine` have ≥85% line coverage from JVM unit tests with no emulator.
9. Item generation is reproducible: a recorded session can be replayed exactly from its seeds.

## 6. Anti-goals in user experience

The app must not:

- shame the user for a lapse or a wrong answer
- use notification pressure, loss-aversion framing, or streak-guilt to drive return visits
- present a wall of theory before the first exercise
- gate the first exercise behind an account, tutorial, or paywall
- imply that any user is incapable of learning

## 7. Naming

"Tonic" is a working title and may be replaced. Do not hardcode it. All user-facing strings live in `strings.xml`; the app name is a single resource.
