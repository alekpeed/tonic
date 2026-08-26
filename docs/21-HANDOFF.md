# 21 — Handoff

**Written 2026-08-24, at `18f12de` (run #87, green).** A dated snapshot, not authority. Check every
claim here against the repository before relying on it — this file has been wrong before, always
because something was asserted rather than measured.

This covers everything built to date, what changed since the last handoff (`2c0386f`, 2026-08-22), and
what is left before Phase 4 can be called closed. There is no Phase 5 spec yet — see §7.

---

## 1. Where the build stands

| Phase | Scope | State |
|---|---|---|
| 1 | `M0` diagnostic, `M2` major diatonic | Built, green |
| 2 | `M9` mode ID, `M10` minor, `M11` chromatic, `M12` audiation, data export | Built, green |
| 3 | Optional sung response | Code complete, green. Device measurements mostly still owed (`30-PHASE-3-SPEC.md` §9) |
| 4 | Rhythm (`M3`) | **Built through the independence check** (Stage 4.6: compound meter, meter change, `M3.INDEPENDENCE_CHECK`). Hardening pass and every device measurement it owes are outstanding — §5, §6 below |

Everything through `18f12de` is CI-verified: `scripts/verify.sh` passes, Room schemas are committed,
the debug APK is signed by the committed key and signature-checked.

**Deliberately not built**, unchanged from the last handoff: `M1` folded into `M0`'s remediation path;
`M4`–`M6` and `M8.MODAL`/`M8.EXTENDED_HARMONY` are reserved `SkillId` constants only, for schema
stability; `M8.MINOR_MODE`/`M8.CHROMATIC_DEGREES` are shipped-then-dead and must never be repointed —
minor and chromatic shipped early as `M10`/`M11`; `M7` does not exist and its number is retired.

---

## 2. What CI green means, and what it doesn't

**CI is the compiler.** This project is developed in a sandbox with no Android SDK, so nothing is
compiled locally — a push is the first time any code meets a compiler.

**Green means the logic and the simulated flows, not real hardware.** A green suite proves the code is
internally consistent given its test doubles; it says nothing about whether those doubles model
hardware correctly. Two concrete, still-live instances:

- Every sung-response test drives `FakeMicrophoneSource`, which returns synthesized audio at exactly
  known frequencies — the right way to test the pitch analyzer, no way at all to test real capture.
- `OutputTimebaseTest` (rhythm) proves the frame→instant arithmetic is right *given a correct reading*;
  it says nothing about whether the platform's own reported readings are correct — that needs a device
  (`40-PHASE-4-SPEC.md` §10 q2).

Conflating "the logic is right" with "the measurement is right" is how a green suite comes to stand for
something it never checked. Keep the two claims separate in review, not just in code.

---

## 3. Process changed since the last handoff — read this before doing anything else

The maintainer cut the verification ceremony on 2026-08-23, effective for the rest of this project:
this is a personal, single-user app, not a shipped product. `CLAUDE.md` §2 is now the only authority on
process; it is reproduced here only so this doesn't read as contradicting the rest of this file.

**Stopped:** stage-gate approvals between stages, per-stage delta reports, production-wiring traces
except for the adaptive engine and audio, mandatory explanation screens built ahead of need, per-node
simulations beyond real observed failures, and docs updates for every code change.

**Kept, because each caught a real bug:** determinism (byte-identical given seed+state); verifying a
feature is actually reachable from production code, not just built and tested; moving any
support-removing or retention-increasing axis one level at a time; a manual listening pass on a real
device for any audio change.

**Default mode: build it, test that it works, move on.** One short summary at the end of a phase, not
per stage. This handoff is that summary for Phase 4's build portion.

The doc set itself was cut to match on 2026-08-24: build-plan tables, stage gates, acceptance criteria,
required-simulation lists and per-stage verification requirements are gone from `09-BUILD-PLAN.md` and
specs 20/30/40 — deleted, not superseded. `CLAUDE.md` is now the only file in the repository containing
instructions; everything else, this file included, is reference.

---

## 4. What changed since `2c0386f`

- **Phase 4 built out to Stage 4.6.** Calibration screen (§4.3 of `40-PHASE-4-SPEC.md`, six-step
  explain-then-run-then-result flow), `ProductionGate`/`BlockReason` wired into
  `PracticeViewModel.blockProductionSessionIfNeeded()` (checked before a session is planned, not
  per-item), `M3.COMPOUND`, `M3.METER_CHANGE`, `M3.INDEPENDENCE_CHECK`.
- **`M3.DOWNBEAT` was unanswerable and is now fixed.** Its pattern used to be plain identical beats with
  only the metronome's accent removed — nothing in the *sound* indicated where the bar began, so the
  node could not be answered correctly by ear. It now plays a once-per-bar figure
  (`M3ItemGenerator.barSignature`), and the rotation that decides where in the bar playback starts is
  threaded through both pattern construction and the recorded answer, so the audio and the scoring
  agree. `ROUTING_SUSPENDED` (the mechanism that let a node be pulled from routing without leaving the
  curriculum) is empty again but was kept rather than deleted — it is the right tool the next time a
  node turns out to be unanswerable.
- **A real routing bug, found and fixed:** `SkillGraph.gateSatisfied` treated a suspended node as
  satisfied unconditionally, which silently unlocked every node *behind* it too — a learner who had
  mastered nothing was routed as if they'd cleared `M3.BEAT_FIND`. Fixed to recurse through the
  suspended node's own gates.
- **A signed debug APK was delivered and is under live device testing** — see §5.
- **The Settings debug "jump to node" tool only ever listed the pitch chain** (`18f12de`, today). No
  `M3` node had a button — not just `M3.DOWNBEAT`, all of them — because `debugJumpTargets` was built
  from `SkillGraph.practiceChain` alone. `DebugSkillJumper.jumpTo()` had the identical gap (it checked
  and walked `practiceChain` only), and the post-jump navigation was hardcoded to
  `PracticeTrack.PITCH`, so fixing the button list alone would still have landed a rhythm jump on the
  wrong track's practice screen. All three fixed together; CI green on run #87.

---

## 5. Device testing — status

The debug APK from run #87 (or later) installs over the previous one. Findings so far, from the
maintainer's own device:

**Confirmed good:**
- Calibration (Settings → the 20-tap run) — flow understood correctly, feels right.
- Metronome — playable-along-with.
- Tap surface — feels instant.

**Still open, in the order they'd teach the most:**
1. **Tap-scoring agreement with the tester's own sense of timing**, at more than one tempo — asked for,
   never reported. This is where a fixed tolerance window would show its seams first (too tight at a
   slow tempo, too loose at a fast one).
2. **`M3.DOWNBEAT` answerability**, now that the pattern actually carries the answer *and* the debug
   jump list can reach it. Not yet tried.
3. **Everything past `M3.BEAT_DIV`** — `M3.COMPOUND`, `M3.METER_CHANGE`, `M3.INDEPENDENCE_CHECK` — has
   had zero device time. All three are only reachable through real play or, as of `18f12de`, the debug
   jump list; before that fix they were unreachable by any means short of grinding the whole chain.
4. **Calibration consistency across separate runs** — does re-running it (Settings is explicitly
   re-runnable) produce a stable offset, or does it wander?
5. **The Bluetooth block** (§4.2 of `40-PHASE-4-SPEC.md`) — untested on a real Bluetooth output device.

---

## 6. What Phase 4's hardening pass still owes

From `40-PHASE-4-SPEC.md` §7.6 and §10, condensed to what's actually actionable next:

**Built, but with a gap:**

| Gap | Consequence |
|---|---|
| No explanation/worked example for tapping (§7.1) | A learner meets the tap surface with no introduction. Calibration has one; recognition and production items don't |
| No visual pulse (§7.3) | No visual beat at low `METRONOME_FADE` levels — nothing is broken, the support is just absent |
| Recognition-only path not written down (§7.5) | `M3.SUBDIV_RECOG` gates on `M3.BEAT_DIV`, which is tapped — a learner who can't tap has no independently traversable recognition path through rhythm yet |

**Open questions that need a device, not a decision made in the sandbox:**

- **Oboe/NDK vs. `AudioTrack`** (§10 q1) — deliberately deferred at Stage 4.0, still open. A device is
  now in hand for the first time since this question was raised; this is the point to measure rather
  than defer again.
- **Whether Android's reported output latency is trustworthy** (§10 q2) — decides whether calibration
  can ever measure against *heard* beats (§4.3) rather than the *scheduled* ones it uses today (a
  known, documented approximation — see §7.6's second design note in the spec).
- **`TIMING_TOLERANCE` bands per `METRONOME_FADE` level** — unmeasured. `SungToleranceMeasurementTest`
  from Phase 3 is the pattern to copy: measure, print the table (`testLogging { showStandardStreams =
  true }`, and read it from a **cold** run — a cached run prints nothing because nothing re-executes).
- **Criterion 3's drift threshold, 0.02** — reasoned from the tolerance math, not measured against a
  real learner's unaided drift (`40-PHASE-4-SPEC.md` §5.3).
- **Interleaving rhythm with pitch in one session** (§10 q3) — undecided, wants usage evidence.
- **Audible tap feedback default** (§10 q4) — currently off by default; revisit with real testing.

None of these block using the app. They block calling the hardening pass finished.

---

## 7. Handing off

No Phase 5 spec exists. `M4`–`M6` and `M8`'s reserved identifiers stay exactly that — reserved, not
started — per `CLAUDE.md` §2's "don't implement later modules while you're in there." The next real
work is finishing what Phase 4 owes, in roughly this order:

1. Keep collecting device-testing feedback against §5 above — tap-scoring agreement and
   `M3.DOWNBEAT` are the two with the most information per minute spent.
2. Now that a device exists, take the Oboe/`AudioTrack` measurement (§10 q1) rather than deferring it a
   third time.
3. Decide the §6 gaps (explanation screen, visual pulse, recognition-only path) based on whether device
   testing actually surfaces them as confusing — not preemptively, per the process change in §3.
4. Once the device measurements are in, `09-BUILD-PLAN.md`'s "what each phase delivered" table gets
   Phase 4 added and this file's Phase 4 row above changes from "hardening outstanding" to "built."

---

## 8. Structural lessons still worth carrying forward

**A feature is not done until production code calls it — and "production code" includes debug
tooling.** The `18f12de` bug (§4) is a new instance of the same shape that cost three stages in Phase 3
(`sungResponseEnabled` landing with no way to switch it on, each stage a correct screen and a correct
resolver joined by one wrong line in the composition layer, in a place nothing tested): something
existed, was correctly implemented, and simply was never wired to be *reached*. The debug jump list is
not user-facing, so it sat outside the reachability discipline `CLAUDE.md` §2 names — worth treating
debug affordances as subject to the same rule, not exempt from it because a real learner never sees
them. Generalized: state the route, then check the route.

**A scripted edit is not verified by writing it.** A find-and-replace once matched nothing because an
earlier replacement in the same script had already changed the anchor text, and it was pushed
unverified. When editing with a script, assert the intended text is actually present afterward — not
just that the script exited zero.

**Measurements get written and then made invisible.** Three separate times now, a real measurement test
went green with its printed numbers swallowed because a module's `build.gradle.kts` lacked
`testLogging { showStandardStreams = true }`. `:core:audio`, `:core:curriculum` and `:core:engine` all
carry it now. Any new measurement test in a module that doesn't yet: check first.

**Reading a CI failure** — the `verify-log` artifact carries the full build log; `scripts/verify.sh`
extracts compiler diagnostics and failing test names before falling back to a raw tail, because a tail
on a compile failure ends with "See log for more details" and none of the details.

**Local checks that exist and what they can't see:** `scripts/ktlint.sh` (run before every push, per
`CLAUDE.md` §8 — the sole local-run exception) and `scripts/symcheck.py` are fast and worth it, but
neither sees a kover coverage floor, Android Lint, a test whose premise your change invalidated, a
coroutine deadlock, or **member access on an existing object** — `symcheck` only resolves top-level
symbols, so a nonexistent member of a real object passes it clean. A change naming members of an
existing type needs those checked by hand.

**The `ktlint.sh` pin is load-bearing in both directions.** It must be re-derived from the ktlint
Gradle plugin's own resolved jar after any `ktlintGradle` version bump — a pin sampled from CI runs
once agreed with the build everywhere it happened to be sampled and silently disagreed elsewhere,
passing a file locally that `ktlintCheck` then failed in CI.

**Virtual-time test conversion is a known hazard, not yet safely done anywhere in this codebase.** An
attempt on 2026-08-22 hung CI for 37 minutes: replacing a `withTimeout` real-time bound with an
unbounded `while (found == null) { delay(POLL_MS) }` is fine under a real dispatcher and an infinite
loop under `runTest`'s virtual time, because every iteration schedules more work and the scheduler
never idles. `PracticeLoopEngine` still holds a hardcoded
`CoroutineScope(SupervisorJob() + Dispatchers.Default)` that no test scheduler can advance. The rule for
the next attempt: every virtual-time loop needs a virtual-time bound (an iteration cap or a deadline
against `currentTime`), and every wait on engine work needs a real-time bound on a real dispatcher —
two separate mechanisms. Convert one test class at a time.
