# Handoff — 2026-08-21

Working state of the `claude/review-files-zip-docs-xmro7r` branch. Written for whoever picks this up
next, including a future session of mine. Read `CLAUDE.md` first; this covers only what is not already
in the specs.

---

## 1. Where things stand

**Phase 2 is code-complete.** All nine stages (2.0–2.8) are built, committed, and pushed. The full
verification gate is green: build, every test, both golden corpora byte-identical under `--rerun-tasks`.

**Phase 2 has never been verified by a human.** Nothing in Phase 2 has been heard by anyone. Until
today a tester could not reach any of it — the first mastery gate stood in the way and there was no way
past it. That is now fixed (§4), and manual verification is the single most valuable next action.

**Phase 3 must not be started without an explicit instruction** (`CLAUDE.md` §2.3). For reference it is
optional sung response: mic permission, pitch detection, never a gate.

---

## 2. Branch and recent history

Branch: `claude/review-files-zip-docs-xmro7r` (pushed, up to date with origin).

```
9e06565  Put the intake skip inside the trap it exists to escape
5cd3f69  Make the debug jump instant, navigate, and skip the diagnostic
c1dfd68  Keep Home current instead of showing a startup snapshot
ab929bb  Make the debug jump tool absolute, off-main-thread, and non-fatal
668138a  Add debug-only "jump to node" tool; fix an unmasterable M11 node
4e74ae9  Make the Phase 2 audio listenable, and measure what can be measured without ears
9f81a46  Phase 2 Stage 2.8: hardening, the seven simulations, and one answer to "what next"
8eeb869  Drop the M7 identifiers: the real-music bridge is removed, not deferred
0d026c3  Phase 2 Stage 2.7: M10.MIXED_MODE, and the ladder that stopped announcing the mode
86429b2  Phase 2 Stage 2.6: M12 audiation, and the item that runs backwards
b6cc449  Phase 2 Stage 2.5: M11 chromatic degrees, and two wiring bugs found on the way
```

The six commits from `4e74ae9` onward are not Phase 2 stages. They are the debug tooling built so a
human could test Phase 2 at all, plus the bugs that surfaced while building it.

---

## 3. Verification

**Always use `./scripts/verify.sh`.** It runs `./gradlew build`, then re-derives both golden baselines
under `--rerun-tasks`, and exits with gradle's own code.

**Never pipe gradle to anything.** `./gradlew build -q 2>&1 | grep -v ... | tail -N` reports the exit
code of `tail`, not gradle. That silently hid two real failures across two commits earlier in this
project, and both were reported as "full build green" when they were not. `verify.sh` exists to make
that mistake structurally impossible.

The same trap has a second form worth knowing: a backgrounded `verify.sh` was reported by the harness as
"completed (exit code 0)" while the script itself had returned 1. If you background it, capture the code
yourself:

```
./scripts/verify.sh > verify.log 2>&1; echo "EXIT=$?" | tee -a verify.log
```

**Module tests are not sufficient.** Every `:feature:*` and `:core:*` test suite passed while `:app`
failed to compile (a Hilt `MissingBinding` — see §6). Only the full gate builds `:app`.

---

## 4. Testing Phase 2 on a device

The debug tooling is `BuildConfig.DEBUG`-only and absent from release builds.

**From a clean install, three taps to anywhere in Phase 2:**

1. On the diagnostic's first screen, tap **"Skip intake (debug build only)"** — clears onboarding and
   the diagnostic without answering anything.
2. Home → Settings → scroll to **"Developer options (debug build only)"**.
3. Tap any node. It erases progress, marks every earlier node complete, and navigates straight into
   practicing that node.

A jump is **absolute**: same button, same result, from any prior state, in any order. It erases existing
progress by design — say so to anyone testing who has real progress they care about.

### What needs a human specifically

| Node | The question only a person can answer |
|---|---|
| `M9.*` | Does major vs. minor register as *sound*, or is it a guess? |
| `M10.MIN_*` | Is `♭3` hearable as its own degree, or just "wrong"? |
| `M10.MIXED_MODE` | Mode is unannounced. Does the ladder give it away anyway? |
| `M11.CHROM_*` | Twelve ladder positions — legible and tappable on a real phone? |
| `M12.PREDICT_*` | Is holding a named degree across a 1–5s silent gap a real task or an impossible one? |

`M12` is the likeliest to be wrong. It is the newest mechanic and the least like anything else in the app.

### Audio verification without a device

`:core:audio` has two test-only tools, both from `4e74ae9`:

- `Phase2SpectralTest` — FFT measurement with parabolic bin interpolation. Verifies the 30-cent detune
  and chromatic pitch spacing to cent accuracy.
- `AudioSampleExportTest` — opt-in WAV export off the real `SynthEngine` path:
  `./gradlew :core:audio:test -Dtonic.audio.export=/some/dir`

These prove the synthesis is numerically correct. They cannot tell you whether it sounds like music.

---

## 5. Outstanding items

### 5.1 The M7 docs are inconsistent with the code — do this first

M7 (real-music bridge) was dropped from the product. The code identifiers were removed in `8eeb869`,
but the maintainer's updated doc files were announced and never actually arrived, and the code half was
committed anyway. **The docs still describe M7 as a reserved future module**, which is exactly the state
`CLAUDE.md` §3 forbids:

| File | Line | Problem |
|---|---|---|
| `docs/03-CURRICULUM.md` | 28 | Module table still lists M7 as "Real Music Bridge — reserve only" |
| `docs/03-CURRICULUM.md` | 206 | Names `M7.REAL_MELODY, M7.REAL_HARMONY` — identifiers that no longer exist |
| `docs/09-BUILD-PLAN.md` | 198 | Later-phases table still lists Phase 7, real-music bridge |
| `docs/02-PEDAGOGY.md` | 122 | §9 frames the copyright analysis as a constraint on "the future real-music bridge module" |

The fix is small: drop the module-table row and the reserved-identifier entry, drop the phase row, and
reframe §9's copyright analysis as the reasoning behind a closed decision rather than a live constraint.
Check with the maintainer whether they still want to supply their own wording first — they intended to.

### 5.2 Phase 2 human verification

See §4. Unstarted, and it gates any honest claim that Phase 2 works.

### 5.3 Phase 3

Blocked on explicit instruction. Do not start it.

---

## 6. What cost real time, and why

Recorded because the pattern matters more than any individual bug.

**The debug jump tool shipped three times and broke on a real device three times, while its unit tests
passed all three times.** The tests ran against in-memory fakes written by the same author as the code,
so they encoded the same assumptions as the thing they were checking. *A fake cannot disagree with you.*

The tooling to have caught this was already in the repo and was simply never pointed at the feature:

- `:core:data` has had Robolectric-hosted **real Room** since Stage 5 (9 test files use it).
- `:feature:practice` has had Robolectric-hosted **real Compose screens** since Stage 7 (7 test files).

Pointing both at the debug feature found things immediately: the real-SQLite test disproved the leading
theory about where the crash was, and the screen test reproduced the reported symptom ("the press
produced no visible outcome") on its first run. Both new tests are now in place —
`DebugJumpAgainstRealDatabaseTest` and `SettingsDebugSectionTest`.

**Corollary for anyone continuing here:** there is no emulator in this environment and there cannot be
(no KVM, zero virtualization CPU flags, no `adb`, no system images). That is a real constraint, but it
was never the reason these bugs shipped. Robolectric plus real Room plus real Compose covers most of it.
Reach for a fake only when the real dependency genuinely cannot run.

### Specific traps hit

- **Kotlin default arguments hide wiring bugs.** `debugJumpInProgress` was never passed from
  `SettingsScreen` to `SettingsContent`; the default swallowed it with no compiler warning, so the
  progress indicator never activated. Found by rendering the screen, not by reading it.
- **Dagger does not understand default arguments.** A constructor default
  (`dispatcher: CoroutineDispatcher = Dispatchers.Default`) failed the whole app graph with
  `MissingBinding` — while every module test still passed.
- **`LazyColumn` only composes visible items.** A test asserting on a row below the fold finds nothing
  and cannot distinguish "not rendered" from "not yet composed". The list now carries a `settings_list`
  test tag so tests can `performScrollToNode` to it.
- **A skip hatch must be reachable from inside the trap.** The intake skip originally lived behind
  Settings, which is only reachable from Home, which bounces a fresh install straight to the diagnostic.
  It is now on the diagnostic's own intro screen.

### One thing never resolved

The specific crash the tester reported on the second build was never reproduced. The rewrite removed
most of what could have caused it — hundreds of SQLite writes on the main thread, an unsatisfiable
walk that threw, and an uncaught coroutine — rather than pinpointing one root cause. If it recurs, the
harness to reproduce it properly now exists; use it rather than guessing.

---

## 7. Real bugs found and fixed along the way

Not tooling problems — genuine defects in the product, found because building the debug tool forced
every node in the graph to be driven to mastery for the first time.

- **`M11.CHROM_FLAT6` was mathematically unmasterable.** At exactly ten active degrees, `DEGREE_COVERAGE`
  demands 3 attempts from each of the other nine (27) while `FOCUS_DEGREE` demanded 4 for the introduced
  one — 31 required attempts in a window that holds 30. No learner, real or synthetic, could ever have
  mastered that node, and nothing about a single window looks wrong in isolation.
  `MasteryEvaluator.requiredFocusAttempts` now also caps at the window budget remaining after every other
  degree's coverage floor. Only `CHROM_FLAT6`'s value changes (4 → 3).
- **Home showed a startup snapshot forever.** `HomeViewModel` read skill state once via `.first()` behind
  a `started` guard, so returning to Home after mastering a node showed the node you were on when the app
  launched. It now collects the Flow.

---

## 8. Quick reference

```bash
./scripts/verify.sh                 # the only trustworthy gate
./gradlew assembleDebug             # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew ktlintFormat              # before committing
./gradlew :core:audio:test -Dtonic.audio.export=/dir   # WAV export
```

Phase 2 spec: `docs/20-PHASE-2-SPEC.md`. Its §8.4–8.7 record per-stage findings and deviations, which
is where the reasoning behind anything surprising in Stages 2.5–2.8 lives.
