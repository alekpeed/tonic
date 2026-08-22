# 21 — Handoff

**Written 2026-08-22, revised the same day once run #31 reported.** A dated snapshot, not
authority. Check every claim here against the repo before relying on it — this file has been stale
before and will be again.

Branch: `claude/handoff-stage-3-3-verified-d5j1pg`. Head at time of writing: `e95c17d` (run #34, green).
That branch is `claude/review-files-zip-docs-xmro7r` plus this document's correction and Stage 3.4.

---

## 1. Read this first: the CI record, honestly

Runs #25–#30 were red, consecutively. The maintainer's assessment — that this is unacceptable and
that the app is not complicated enough to justify it — is correct. The causes were:

| Run | Cause | Category |
|---|---|---|
| 25 | An `android { sourceSets... }` line AGP 9 rejects at configuration | my error |
| 26 | The Room schema guard, shipped in the same commit as the file it flags | self-inflicted |
| 27 | `result.attempts` — the property is `allAttempts` | typo |
| 28 | `InputMethod` used three times, never imported | typo |
| 29 | `LADDER_BUTTON` referenced five times; the companion holding it never landed | typo |
| 30 | A real race: item and explanation published in two separate state updates | real defect |

**Five of six were unresolved references.** The root cause is not the code. It is that this sandbox
has no Android SDK, so CI is the compiler as well as the test runner, at five to nine minutes a
round — and large batches of new code were pushed with only `ktlint` run locally, which checks
formatting and not whether a symbol exists. Each round reported only the first error, so the next
one surfaced only on the next round.

**Do not repeat this.** Before every push:

```bash
scripts/ktlint.sh                                        # formatting, seconds
python3 scripts/symcheck.py $(git diff --name-only HEAD -- '*.kt')   # unresolved references
```

And when you make an edit with a script, **assert the intended text is present afterward**. Run #29
was a find-and-replace that matched nothing because an earlier replacement in the same script had
already changed the anchor text. It was pushed unverified.

Runs #31 through #34 were green, and the streak is over — four in a row, including a whole stage
landed in one push. It ended because those two checks were run before pushing, not because the code
got simpler. Keep running them.

---

## 2. What is actually built and green

Everything through **run #34** (`e95c17d`, this branch's head) is CI-verified green: all of Phase 1,
all of Phase 2, this branch's explanation-screen overhaul, and Stages 3.3 and 3.4 entire.

**Verified in CI:**

- The intro/explanation system, rebuilt (see §4).
- Debug APK published by every run, signed by a committed key so builds install over each other.
- `apksigner`-based signature verification.
- The Room schema-committed guard.
- Stage 3.3's sung-answer path entire — everything from `0f4c39f` onward:
  - `Attempt.inputMethod` / `sungCents`, Room v2 + migration, `MigrationTest`
  - `SungDataIsNeverAdaptiveTest`
  - `SungLearnerSimulationTest`
  - `MicrophoneSource` / `CapturedAudio` / `UnavailableMicrophoneSource`
  - `SungAnswerControl`, `onSingAnswer`, `SungAnswerControlTest`, `SungAnswerFlowTest`

- Stage 3.4's sung prediction, added in `e95c17d` and green on its first run:
  - `AudiatedPitch` + `AudiatedPitchTest`
  - `captureAudiation` in `PracticeViewModel`, opening the mic inside the audiation gap
  - `SungPredictionTest` (five cases, through the real ViewModel, engine and analyzer)
  - `SungPredictionSimulationTest`, and `sungCentsFor` on the prediction harness

Run #31 (`8e343ac`) is where the sung stack first went green; #32 held it on the head commit.
**Stages 3.3 and 3.4 are complete, and the next work is Stage 3.5, `M10` and `M11`.**

One thing this does not mean: green in CI is not green on a device. Every one of those tests runs
against `UnavailableMicrophoneSource`. The sung path is verified as logic and as a simulated flow,
and has not once met a real microphone — which is why §3 still reports 3.0 and 3.2 as half-built and
why §5 is unchanged.

---

## 3. Phase 3 progress against the spec

`docs/30-PHASE-3-SPEC.md` §8's stage table:

| Stage | State |
|---|---|
| 3.0 Mic capture + pitch detection | **Half.** `PitchDetector` (MPM) built and measured — worst error 7.69 cents against a 100-cent requirement. Real capture is not built: `MicrophoneSource` is an interface and the bound implementation reports itself unavailable. Latency, CPU, dropouts and recorded-signal accuracy all need hardware. |
| 3.1 Scoring pipeline | **Built and green.** `DegreeResolver` + `SungResponseAnalyzer`. Ambiguity band decided at 10 cents. |
| 3.2 Permission + explanation | **Half.** The sung explanation screen exists and is tested. The permission flow is not built — it needs a device. |
| 3.3 Sung response in `M2` | **Done.** All three acceptance criteria have tests, green in runs #31 and #32. Simulated only — no real microphone in the loop, per §2. |
| 3.4 Sung prediction in `M12` | **Done.** Both acceptance criteria have tests, green in run #34. The capture window is bounded by arithmetic — 200 ms lead-in, 250 ms guard before the note — so the ordering §5.4 depends on holds without a device to check it. Simulated only, same caveat as 3.3. |
| 3.5 `M10` and `M11` | **Next up.** Not started. ⚠️ Its open question (can anyone sing chromatically enough to be scored?) needs device measurements, so the stage may not be closeable here. |
| 3.6 Hardening | Not started. |

§9's four open questions: two settled and recorded in the spec (ambiguity band; sung prediction
supplements rather than replaces — and §5.4 now also carries the four implementation decisions Stage
3.4 had to make on top of that one). Two still open — `AudioRecord` vs Oboe, and chromatic singing
viability — and **both need device measurements**, so neither can be closed in this environment.

---

## 4. Decisions taken this session that changed shipped behavior

These are recorded in the docs they affect; listed here so you know they happened.

**Explanations now appear every time you enter a module, not once ever.** Maintainer instruction
after live use, verbatim: *"How It Works should come up anytime you enter a new module, every single
time."* The old rule was tracked by persisted flags, which made every already-dismissed screen
permanently unreachable — ending a session did not bring it back, and neither did clearing the saved
session, because that is a different store. `docs/08-UI-SPEC.md` §3a and
`docs/11-ONBOARDING-CLARITY.md` §5 carry the new rule; `docs/05-DATA-MODEL.md` §4 marks the
`module*IntroSeen` flags retired — still stored, no longer read or written.

**A review item is not "entering" a module.** Up to 40% of a session is spaced-repetition review of
finished modules. Counting those as entry would stop practice several times a session to re-explain
old material. The playback gate takes the whole `PlannedSlot` for exactly this.

**Audio never plays under an explanation screen**, on any module, at any entry point. This was the
original live-use report and is now enforced by a per-item gate in `PracticeLoopEngine`.

**`M9`'s explanation screen was never wired.** It had existed since Phase 2 Stage 2.2 — built,
previewed, tested, with its own seen-once flag — and no dispatch line ever reached it. Now wired.
Worth internalizing as a pattern: this codebase has produced fully-built, fully-tested screens that
nothing could reach. `IntroDispatchTest` exists because of an earlier instance of exactly this.

**Settings' "Show explanations again" was added and then removed** in the same session. It existed to
work around seen-once; the new rule made it a no-op, and a button that does nothing is worse than no
button.

---

## 5. What the maintainer still needs to do on a device

Nothing here can be closed without hardware. The APK from any green run installs over the previous
one (committed debug key, CI-verified), so progress survives updates.

1. **`M12.PREDICT_TRIAD`** — is holding a named degree across a 1–5 second silent gap a real task or
   an impossible one? This is Phase 3's highest-value target and is still unvalidated.
2. **`M10.MIXED_MODE`** — does the ladder give the mode away?
3. The minor `i–iv–v–i` cadence still owes a by-ear check at every fade level
   (`docs/20-PHASE-2-SPEC.md` §8.1 decision 4).

---

## 6. Working notes for whoever picks this up

**Tooling that exists and should be used:**

- `scripts/verify.sh` — the definition of green. Now extracts compiler diagnostics (`e:` lines) and
  failing test names before falling back to a tail, because a 40-line tail on a compile failure ends
  with "Compilation error. See log for more details" and none of the details.
- `scripts/ktlint.sh` — standalone, no Android SDK, seconds. Version pinned to 1.7.2 to match CI;
  **the pin is load-bearing**, 1.8.0 flags files CI passes.
- `scripts/symcheck.py` — unresolved-reference check, described in §1.

**Reading a CI failure without waiting for a summary:** the `verify-log` artifact has the full build
log. Download and grep it directly:

```bash
curl -sSL -o log.zip "https://api.github.com/repos/alekpeed/tonic/actions/artifacts/<ID>/zip"
unzip -q log.zip && grep -E "^e: |FAILED" verify.log
```

**Pushing cancels in-flight runs** (`concurrency: cancel-in-progress`). Several runs in this
branch's history are `cancelled` because of a push landing mid-run, not because anything failed.

**Communication rules the maintainer has set** (now in `CLAUDE.md` §8, and they were repeated
several times before they stuck): be brief; reply format is *what was done / what is needed from you
/ what is next* and nothing else; ask questions through the interactive prompt, never as prose;
answer the literal question asked rather than supplying context around it.
