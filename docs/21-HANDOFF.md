# 21 — Handoff

**Written 2026-08-22, at `2c0386f` (runs #45 and #46, green).** A dated snapshot, not authority.
Every claim here is checkable against the repository, and this file was wrong twice in the session
that produced it — both times because something was asserted rather than measured. Check before
relying on it.

This covers everything built to date. The next phase is specified in `40-PHASE-4-SPEC.md`; §9 is the
bridge between the two.

---

## 1. Where the build stands

| Phase | Scope | Stages | State |
|---|---|---|---|
| 1 | `M0` diagnostic, `M2` major diatonic | 1.0–1.10 | Built, green |
| 2 | `M9` mode ID, `M10` minor, `M11` chromatic, `M12` audiation, data export | 2.0–2.8 | Built, green |
| 3 | Optional sung response | 3.0–3.6 | **Code complete, green — device measurements still owed (§3)** |
| 4 | Rhythm (`M3`) | 4.0–4.7 | Specified only. Not started |

Everything through run #45 is CI-verified: `scripts/verify.sh` passes across all twelve modules, the
Room schemas are committed, and the debug APK is signed by the committed key and signature-checked.

**Deliberately not built.** `M1` is folded into `M0`'s remediation path rather than shipped standalone
(`01-PRODUCT-SPEC.md` §3). `M4`–`M6` and `M8.MODAL`/`M8.EXTENDED_HARMONY` exist only as reserved
`SkillId` constants for schema stability (`03-CURRICULUM.md` §6). Note the deprecate-don't-recycle
rule: `M8.MINOR_MODE` and `M8.CHROMATIC_DEGREES` are shipped-then-dead strings that must never be
repointed — minor and chromatic shipped early instead, as `M10` and `M11`. `M7` is absent and its
number is not reused.

---

## 2. What "green" means here, and what it does not

**CI is the compiler.** This project is developed in a sandbox with no Android SDK, so nothing is
compiled locally — a push is the first time any code meets a compiler, at five to eleven minutes a
round. That single fact explains most of §6 and most of §7.

**Green means the logic and the simulated flows.** It does not mean the app was run:

- Every sung test drives `FakeMicrophoneSource`, which returns synthesized audio at exactly known
  frequencies. That is the right way to test the analyzer and no way at all to test capture.
  `AudioRecordMicrophoneSource` has never been observed running.
- Compose and Robolectric tests render composables; they do not exercise real touch, focus or audio
  routing.
- Whether a cadence *sounds* like a cadence is §3's problem, not CI's.

**One green number in this branch's history is misleading.** Run #45 finished in 1m01s because
Gradle's cache served nearly everything (`:core:curriculum:test FROM-CACHE`). The honest figures are
cold builds: 7m40s before Phase 3's tests, ~11m20s after. A cache-warm run is not a speedup, and it
prints no `[measure]` lines because the tests never re-executed.

---

## 3. What only a device can settle

Nothing here closes in the sandbox. The debug APK from any green run installs over the previous one,
and **singing is reachable as of `872dfec`** — Settings → Singing → *Answer by singing*.

Ranked by what the project learns per unit of effort:

1. **`M12.PREDICT_TRIAD` — is audiation a real task?** Hold a named degree across a one-to-five-second
   silence, then judge whether the note that follows matched it. Phase 3's highest-value target, and
   the assumption Stage 3.4's whole sung-prediction mechanism rests on. Nobody has confirmed a person
   can do it.
2. **`AudioRecord` vs Oboe** (`30-PHASE-3-SPEC.md` §9 q1). ⚠️ **This one now blocks two phases** —
   `40-PHASE-4-SPEC.md` §10 q1 inherits it verbatim and Stage 4.0 cannot start without it. See §9.
3. **Systematic vocal offset past 55 cents** (§9 q5, opened by Stage 3.5). Past roughly 55 cents of
   *consistent* offset the resolver stops returning "unclear" and starts returning a confident wrong
   degree — always the one below, on every alphabet including `M2`'s. How common such an offset
   actually is decides whether this is a real defect or a hypothetical. Do not design a fix first.
4. **`M10.MIXED_MODE`** — does the shape of the ladder give the mode away before the answer?
5. **The minor `i–iv–v–i` cadence**, by ear, at all eight fade levels (`20-PHASE-2-SPEC.md` §8.1
   decision 4).
6. **Stage 3.0's own acceptance**: capture latency, CPU, dropouts, and pitch accuracy on a voice
   through a real preamp rather than a synthesized sine.

Also unrecorded: **Stage 1.2's on-device listening pass was signed off 2026-08-21**, but the
subjective findings were never written down. If `PLUCK` is revisited against the Karplus-Strong
question in `06-AUDIO-ENGINE.md` §3, that verdict wants capturing rather than reconstructing.

---

## 4. Two structural failures worth internalizing

Both cost real time; both were invisible to a passing suite.

### 4.1 Three stages shipped behind a door with no handle

`sungResponseEnabled` landed in `AppSettings` at Stage 3.3, defaulting to false. Nothing in the app
could change it. No `RECORD_AUDIO` in the manifest, no permission request anywhere, and no
`AudioRecord` code at all — `UnavailableMicrophoneSource` was bound, reporting false forever. Stages
3.1, 3.3 and 3.4 were each built, fully tested, reported complete and CI-verified green, and **not one
could be reached by a person holding the phone.**

Nothing caught it because every sung test uses a fake microphone. The stage table said "half" for 3.0
and 3.2, which read as *the untestable part is missing*; the reachable part was missing too. The cause
was a defensible rule applied too widely — Stage 3.0's acceptance criteria are all device
measurements, so the whole stage was deferred, including the hundred lines of `AudioRecord` that need
no device to write. Closed 2026-08-22.

> **A stage is not done when its tests pass. It is done when a learner can get to it.** State the
> route, then check the route. `SungResponseSectionTest`'s first assertion is literally *can this be
> switched on at all*.

Not a one-off: `M9`'s explanation screen existed from Phase 2 Stage 2.2 — built, previewed, tested,
flagged — and no line of composition ever reached it. `IntroDispatchTest` exists because of an earlier
instance again.

### 4.2 Measurements written and discarded

`SungToleranceMeasurementTest`'s deliverable is the band table it prints. It went green in run #41 with
every line invisible, because `:core:curriculum` did not set
`testLogging { showStandardStreams = true }`. From the log that is indistinguishable from a
measurement nobody took.

Third time this repository has made that mistake — the first two are why `:core:audio` and
`:core:engine` already carried the setting. All three now do. `grep '\[measure\]' verify.log` is where
the numbers live, and only on a **cold** run.

---

## 5. Findings and decisions that outlived their stage

Recorded in the docs they affect; listed so you know they happened.

**Sung answers supplement, never replace** (`30-PHASE-3-SPEC.md` §5.4). On `M12` the learner sings into
the gap *and* still answers with the three-button control; the button scores. Collapsing them would
make one node mean "produce the pitch" for singers and "spot the mismatch" for everyone else — two
skills under one name, which §2 forbids. Consequences: `inputMethod` is always `TAP` on an `M12` row,
and `sungCents` there is measured from the degree that was *named*, not the nearest one the voice
landed on.

**Chromatic resolution is not less accurate than diatonic** (§5.5; closed §9 q4). Measured: diatonic
reads correctly to 45 cents of uniform detuning, chromatic to 44, both begin misreading at 55. The
major scale already contains semitone steps at `3`–`4` and `7`–`1`, so the binding geometry was fixed
in Phase 1 when `4` joined the alphabet at `M2.DEG_SET_4`. Restricting singing to diatonic contexts
would have improved accuracy by nothing measurable, so it was not done. What chromatic changes is
*breadth*: at a uniform −50 cents, `M2.FULL_DIATONIC` still reads 5 of 7 degrees and `M11.CHROM_FULL`
reads 0 of 12. That learner is never misread — the ambiguity margin catches every case — but singing
never once works for them. A usability fact no pass/fail can show.

**Explanations appear every time you enter a module, not once ever.** Maintainer instruction after live
use, verbatim: *"How It Works should come up anytime you enter a new module, every single time."* The
old rule used persisted flags, which made every dismissed screen permanently unreachable.
`08-UI-SPEC.md` §3a and `11-ONBOARDING-CLARITY.md` §5 carry the new rule; `05-DATA-MODEL.md` §4 marks
the `module*IntroSeen` flags retired — still stored, no longer read or written.

**A review item is not "entering" a module.** Up to 40% of a session is review of finished modules;
counting those as entry would stop practice several times a session to re-explain old material. The
playback gate takes the whole `PlannedSlot` for exactly this.

**Audio never plays under an explanation screen**, on any module, at any entry point. Enforced by a
per-item gate in `PracticeLoopEngine`.

**Settings' "Show explanations again" was added and removed** in one session. It worked around
seen-once; the new rule made it a no-op, and a button that does nothing is worse than none.

---

## 6. The CI record, honestly

Runs #25–#30 red consecutively, five of six unresolved references. #31–#35 green. Then #36, #37 and
#40 red, and #44 hung for 37 minutes.

**#36/#37/#40 were one change.** The commit that made singing reachable added new Android APIs, a new
module dependency and a manifest edit in a single push. Failures came out one per round because CI
reports only the first: the kover coverage floor → `NoNetworkPermissionTest`, whose "no
`uses-permission` of any kind" assumption that manifest edit invalidated → Android Lint's
`MissingPermission` → an unresolved reference.

**#44 hung on an unverified virtual-time conversion.** Mechanism in §8.

**#38, #39, #42 are `cancelled` because a push landed mid-run** (`concurrency: cancel-in-progress`),
not because anything failed.

### What the local checks do and do not cover

`scripts/ktlint.sh` and `scripts/symcheck.py` are fast and worth running every time. They are **not** a
prediction of green. Neither sees:

- a kover coverage floor,
- Android Lint,
- a test whose premise your change just invalidated,
- a coroutine deadlock,
- **member access on an existing object.** `symcheck` resolves *top-level* symbols, so
  `SkillIds.M10_NODES_IN_ORDER` — a member that does not exist — passed it clean and cost run #40. Any
  change naming members of an existing object needs those checked by hand. Same failure class as
  #27–#29, caught by nothing.

For a change touching Android APIs, a dependency, or the manifest, expect a round per category and say
so rather than predicting green.

---

## 7. Working notes

**Tooling:**

- `scripts/verify.sh` — the definition of green. Extracts compiler diagnostics (`e:` lines) and failing
  test names before falling back to a tail, because a 40-line tail on a compile failure ends with
  "Compilation error. See log for more details" and none of the details.
- `scripts/ktlint.sh` — standalone, no Android SDK, seconds. Pinned to the version the ktlint Gradle
  plugin resolves (1.5.0 at the time of writing); **the pin is load-bearing in both directions.** It
  was 1.7.2, derived by sampling CI runs, which is a *newer* ruleset than the build enforces: it
  agreed on everything the sampling happened to cover and silently disagreed elsewhere, passing a file
  with an unused import that `ktlintCheck` then failed. Re-derive it from the plugin's own resolved
  jar after any `ktlintGradle` bump.
- `scripts/symcheck.py` — unresolved-reference check. §6 says what it cannot do.

**Before every push:** `scripts/ktlint.sh`, and nothing else. As of 2026-08-23 the maintainer's
instruction is that **nothing is built or tested in the sandbox** — no `verify.sh`, no
`android-sdk.sh`, no `./gradlew`. A local run costs about ten minutes and resources needed elsewhere,
and CI is the gate. ktlint was carved back out on 2026-08-24 after five of eight consecutive rounds
failed on formatting alone: it is a standalone jar, five seconds, no SDK. `CLAUDE.md` §8 is
authoritative on this and this file is not.

That inverts most of §6 and the paragraphs above: they are kept because they are still true about
*what each check can and cannot see*, which is what makes a red CI run readable. What is no longer
true is the advice to run them here. The replacement is to keep each commit small and single-purpose,
so a red run points at one change, and to re-read the diff for the failures a compiler would have
caught — imports, exhaustive `when`s, every implementor of a widened interface.

And when you edit with a script, **assert the intended text is present afterward**. Run #29 was a
find-and-replace that matched nothing because an earlier replacement in the same script had already
changed the anchor text. It was pushed unverified.

**Reading a CI failure** — the `verify-log` artifact carries the full build log:

```bash
AID=$(curl -s ".../actions/runs/<RUN_ID>/artifacts" | jq '.artifacts[]|select(.name=="verify-log").id')
curl -sSL -o log.zip ".../actions/artifacts/$AID/zip"
unzip -q log.zip && grep -E "^e: |FAILED" verify.log
```

**Test timing** comes from an `afterTest` listener in the root `build.gradle.kts`: any test at or over
a second prints `[slow-test] <ms> <Class>.<name>`. It exists because a claim about which tests were
slow turned out to be wrong, and it doubles as a regression guard.

**Communication rules the maintainer has set** (also `CLAUDE.md` §8): be brief; reply format is *what
was done / what is needed from you / what is next* and nothing else; ask questions through the
interactive prompt, never as prose; answer the literal question asked rather than supplying context
around it.

---

## 8. Known-not-done

**Test suite wall-clock.** 44 tests take a second or more, totalling 3m44s; the largest single class is
`PracticeViewModelTest` at 44s. About 2m11s is tests driving the practice loop through real playback
delays — `PracticeViewModelTest` (44s), `SungPredictionTest` (29.9s), `SungAnswerFlowTest` (23.3s),
`PracticeLoopEngineTest` (16.9s), `SungMinorAndChromaticTest` (7.8s), `M2IntroTest` and
`M2SessionTraceTest` (4.7s each). Virtual time is the fix.

⚠️ **It was attempted on 2026-08-22 and hung CI for 37 minutes.** `SungPredictionTest` was converted to
`runTest(StandardTestDispatcher())`; run #44 sat on `Verify` until a revert push killed it. Two
mistakes, the second fatal:

1. `withTimeout` was removed because under `runTest` it is a *virtual*-time deadline that fires the
   instant nothing else is scheduled — correct, and a real hazard for any wait on real work.
2. What replaced it was an **unbounded** `while (found == null) { delay(POLL_MS) }`. In virtual time
   that is not a poll, it is an infinite loop: every iteration schedules more work, the scheduler never
   idles, `runTest`'s own real-time limit is starved, and the JVM spins until the runner dies.

The rule for the next attempt: **every virtual-time loop needs a virtual-time bound** (an iteration cap
or a deadline against `currentTime`), and **every wait on engine work needs a real-time bound on a real
dispatcher.** Two mechanisms, not one. `PracticeLoopEngine` holds a hardcoded
`CoroutineScope(SupervisorJob() + Dispatchers.Default)` and writes attempts to it fire-and-forget, so
no test scheduler can advance that work. Convert one class at a time, read the `[slow-test]` line, do
not push without both bounds.

The Robolectric/Compose classes near the top of that list (`DegreeLadderLayoutTest` 15s,
`AnswerAreaTest` 14.8s, `DiagnosticDebugSkipTest` 13.3s) are startup charged to whichever test runs
first. Virtual time does nothing for those.

**`sungOctaveAgnostic` is stored, surfaced in Settings, and read by nothing** — the analyzer folds
octaves unconditionally. Wiring it or deleting it is an open loose end.

**Data import** is deliberately absent; export alone shipped in Stage 2.1. `20-PHASE-2-SPEC.md` §6 has
the reasoning and says to revisit only against a concrete need.

---

## 9. Handing off to Phase 4

Read `40-PHASE-4-SPEC.md`. Rhythm (`M3`) is pedagogically independent of everything above — it shares
no skill nodes with pitch, and a learner can start `M3.BEAT_FIND` having never touched `M2`. What it
shares is the audio engine, and that is where this handoff matters.

**Three things from Phase 3 land directly on Stage 4.0.**

**The Oboe decision is now blocking.** Phase 3 §9 q1 was left open; Phase 4 §10 q1 inherits it and
Stage 4.0 cannot start without it. `06-AUDIO-ENGINE.md` §2 predicted this back in Phase 1 — "Phase 4
(rhythm) will require revisiting this" — and it is the one open question that has compounded across
two phases. `AudioPlayer` sits behind an interface precisely so the backend can be swapped without
touching callers; that groundwork is done and unused.

**Phase 4's simulation 2 is Phase 3's flat-singer test, and there is now evidence about how that shape
of problem behaves.** A uniformly-offset learner in Phase 3 was never *misread* — the ambiguity margin
caught every case — but on the widest alphabet they were never *readable* either, so the feature was
correct and useless to them simultaneously (§5). The rhythm analogue is a tolerance window narrow
enough that a calibrated-but-imprecise tapper is never scored wrong and also never scored right. Phase
4 §6.2's windows and §4.3's calibration are where that gets decided, and the Phase 3 lesson is:
**measure the band across every difficulty level before trusting it, and print the table.**
`SungToleranceMeasurementTest` is the pattern to copy — including §4.2's warning about where that
output goes.

**Phase 4's simulation 6 is §4.1's reachability trap wearing different clothes.** "Uncalibrated device:
production must be blocked with an explanation, not silently mis-scored" describes a *route* — and a
route is exactly what three Phase 3 stages shipped without. Write the test that asks whether a learner
can reach the blocked state and read the explanation, not only whether the block works.

**One thing that will not transfer.** Phase 4 §4.4 requires determinism under real time, and rhythm
tests will need genuine timing precision. §8's virtual-time hazard gets sharper here, not softer: the
engine's hardcoded `Dispatchers.Default` scope is already a problem for test time, and a phase whose
whole subject is *when things happened* should decide early whether that scope stays hardcoded — before
Stage 4.3's "byte-identical on replay" criterion comes to depend on it.
