# 30 — Phase 3 Specification: Optional Sung Response

**Status: designed ahead of Phase 2 completion.** Sections marked ⚠️ depend on evidence Phase 1/2 will produce and should be revisited before Stage 3.0 starts.

**Prerequisite: Phase 2 complete and stable.** Phase 3 adds an alternative *input mode* to exercises that already exist. If those exercises are still changing, this work will be rebuilt.

---

## 1. What Phase 3 adds

One thing: the ability to answer by singing instead of tapping.

No new exercises. No new curriculum. No new skill nodes. Every existing exercise in `M2`, `M9`, `M10`, `M11`, `M12` gains a second, optional way to respond — you sing the note, the app listens through the microphone and checks your pitch.

This is deliberately the narrowest phase in the plan. It touches every exercise but adds no teaching content, which makes it high-risk-of-scope-creep and worth guarding tightly.

## 2. The non-negotiable constraint

**Singing is never required. Every skill in the app must remain fully reachable using tap-only input, forever.**

This is already law in `02-PEDAGOGY.md` §7 and `01-PRODUCT-SPEC.md` §4. Restating it here because Phase 3 is where it would be easiest to violate by accident — e.g. a mastery criterion that can only be satisfied with sung data, or a node that unlocks only after singing.

Concretely, this means:

- A user who never grants microphone permission must be able to reach every mastery state, every independence check, and every node in the app.
- Sung and tapped attempts are **not** separate skill states. They contribute to the same `SkillState` for a given node — the input method is a property of the attempt, not of the skill.
- No UI copy may imply singing is the "real" or "advanced" way to do it. It's an option, not a tier.
- Turning singing off mid-progression loses nothing.

### 2.1 Why optional, restated

The evidence that vocal production accelerates perceptual learning is real but correlational and partly dissociated — better discriminators sing more accurately, and discrimination training improves singing, but "singing improves hearing" is not established causally. Meanwhile the cost of requiring it is concrete and immediate: it demands privacy, a quiet room, a working mic, and willingness to sing out loud. That asymmetry — speculative benefit, certain cost — is why it stays optional.

## 3. The core risk this phase must not fall into

**The app must not accidentally start testing singing ability instead of ear training.**

These are different skills and they come apart. A person can know with certainty that the target is scale degree 5, audiate it perfectly, and still sing it flat — because vocal pitch production is a motor skill with its own learning curve, and it's affected by vocal range, time of day, congestion, confidence, and whether someone is in earshot.

If the app scores that person as wrong, it is measuring the wrong thing and will corrupt the very data (`attempts`, confusion matrix, staircase) that all adaptation depends on.

Mitigations, all required:

1. **Generous pitch tolerance** (§5.2) — the question is "did you aim at the right note," not "are you a good singer."
2. **Degree-level scoring, not cent-level.** The answer is which scale degree you sang, resolved to the nearest one. A sung note 40 cents flat of degree 5 is *degree 5*, answered correctly.
3. **Octave-agnostic by default.** Singing degree 5 in whatever octave is comfortable is correct. Vocal range varies enormously and forcing a specific octave tests range, not hearing.
4. **A separate, non-scoring accuracy readout.** How close you were in cents is shown as information, never as a grade, and never enters the staircase or mastery evaluation.
5. **An explicit "I know it but can't sing it" path** — see §6.3.

## 4. Curriculum impact

**None.** No new modules, no new skill nodes, no new mastery criteria.

The only curriculum-adjacent change: `Attempt` gains an `inputMethod` field (`TAP` / `SUNG`), so the confusion matrix and analytics can distinguish them without treating them as different skills.

⚠️ **Revisit:** if real usage shows sung attempts have systematically different error patterns (plausible — singing may surface confusions tapping hides, or vice versa), it may be worth reporting them separately in the Progress screen. Do not build that until there's data.

## 5. Technical design

### 5.1 Pitch detection

Approach: real-time monophonic pitch detection on mic input, running on-device.

- **Algorithm:** autocorrelation-based (YIN or MPM). These are well-understood, cheap, and adequate for monophonic sung input. Neural approaches (CREPE) are more accurate but far heavier and unjustified here — degree-level resolution does not need state-of-the-art cent accuracy.
- **Placement:** off the main thread. Given Phase 1 chose `AudioTrack` over Oboe (`06-AUDIO-ENGINE.md` §1) because there was no input path, Phase 3 is where that decision gets revisited — low-latency *input* is exactly what Oboe is for. Evaluate whether `AudioRecord` on a dedicated thread suffices or whether Oboe/NDK is warranted. Report the decision with measurements rather than assuming.
- **All processing on-device.** No audio leaves the phone, ever. No upload, no cloud inference. This is consistent with the app's local-only stance and is also the only defensible position for continuous mic capture.

### 5.2 Scoring a sung response

```
1. Capture audio for a bounded window after the user starts singing.
2. Detect pitch frame-by-frame; discard frames below a confidence threshold.
3. Reject the attempt as "unclear" (not "wrong") if too few confident frames.
4. Take a stable central estimate — median of the sustained portion, not the onset
   (attack is unstable; people scoop into notes).
5. Reduce to pitch class relative to the established tonic; ignore octave.
6. Resolve to the nearest scale degree in the current answer alphabet.
7. That degree is the answer. Score it exactly as a tapped answer would be.
```

Key properties:

- **Scooping is tolerated** by ignoring the onset. Untrained singers slide into pitch; penalizing that tests technique, not hearing.
- **"Unclear" is not "wrong."** A mumble, a cough, silence, or background noise produces a retry prompt, never a recorded incorrect attempt. This matters enormously — a false "wrong" corrupts the staircase and the confusion matrix.
- **Ambiguity band — decided 2026-08-21: treat as unclear and re-prompt.** Presenting the two candidates
  for confirmation was rejected: handing back a shortlist that contains the answer is a materially easier
  question than the one the exercise asked, and it would arrive at precisely the moment the learner knew
  least — converting a recall task into a recognition task exactly where the distinction matters most.
  Silent rounding stays ruled out for the reason given when this was first written.

  **Calibration: 10 cents of margin** (`DegreeResolver.AMBIGUITY_MARGIN_CENTS`), meaning the sung pitch
  must land within five cents of the exact midpoint between two neighbors to be refused. Tightened from a
  first attempt at 20 during Stage 3.1. Adjacent degrees sit 100 cents apart, so their midpoint is 50
  cents from each, and a 20-cent margin therefore refuses everyone whose nearest degree is 40–50 cents
  away. A singer 40 cents flat of the tonic is not a coin-flip — the runner-up is half again as far — and
  flat is exactly how untrained singers miss, which is the case §3 mitigation 1 exists to protect. A band
  that wide would have been the defect §3 warns about, wearing caution as a disguise.

### 5.3 Where singing applies

| Module | Sung response | Notes |
|---|---|---|
| `M2` (major diatonic) | Yes | Primary use case |
| `M10` (minor) | Yes | Same mechanism |
| `M11` (chromatic) | Yes | ⚠️ Tolerance bands narrow considerably with 12 degrees — see §5.2 ambiguity band |
| `M9` (mode ID) | No | Answer is `MAJOR`/`MINOR`, not a pitch. Nothing to sing. |
| `M12` (prediction) | **Yes — highest value here** | See §5.4 |
| `M0` (diagnostic) | No | Diagnostic must work with zero permissions granted |

### 5.4 Prediction items are where singing matters most

`M12` asks you to audiate a named degree during a silent gap, then judge whether the sounded note matched. The tap version can be passed by guessing — a coin flip gets 50%.

The sung version closes that hole: **sing the degree during the gap, before the note plays.** The app captures what you sang, then plays the target. You cannot fake this — either you produced the right pitch from an internal representation or you didn't.

This is the strongest argument for building Phase 3 at all, and it should be prioritized within the phase accordingly.

**Decided 2026-08-21: the sung prediction supplements the judgment, it does not replace it.**

The learner sings during the gap, then still answers with the three-button control
(`MATCHED` / `TOO LOW` / `TOO HIGH`, per `20-PHASE-2-SPEC.md` §8.1 decision 3). Both are recorded; the
button answer is what scores.

The reason is §2's invariant, not richness of data. If singing replaced the judgment, the sung and
tapped versions of `M12` would stop being the same skill — one would be "produce the pitch," the other
"recognize the mismatch" — and a node cannot have two different mastery meanings depending on which
input the learner chose. That would violate "sung and tapped attempts are not separate skill states"
directly, and it would make a tap-only user's `M12` mastery mean something weaker than a singer's,
which §2 forbids in as many words.

Supplementing also keeps the sung signal honest as *evidence about* the judgment rather than a
substitute for it: a learner who sings the right pitch and then misreports the direction has a
specific, diagnosable problem, and collapsing the two would hide it.

**Built in Stage 3.4, 2026-08-22.** Four decisions the above did not settle:

1. **The capture starts itself**, as soon as the audiation gap opens, with no button to press. Every
   other sung answer in the app is opened by tapping "Sing"; here the gap is 1–5 seconds and *is* the
   exercise, so requiring a press would substitute a manual task for the thing being measured, and at
   `PREDICT_GAP` level 0 there is not time for both. The learner has already opted in globally (§6.1)
   and the explanation says the app listens during the silence.
2. **The window is bounded by arithmetic, not by the microphone.** A 200 ms lead-in lets the cadence's
   release decay before the mic opens; a 250 ms tail guard closes it before the note sounds. §6.5's
   "reference audio and the answer window should not overlap" is met by separating them in time, which
   needs no echo cancellation and no device to verify. A gap too short to hold a usable window yields
   no sung evidence rather than a stream of unreadable captures — the learner is scored identically.
3. **`inputMethod` stays `TAP` on every `M12` attempt**, because it records how the *scoring* answer
   arrived and on a prediction item that is always the button. `sungCents` is what marks the row as
   sung. Recording `SUNG` would make the column mean one thing on `M2` and another on `M12`, and would
   imply a sung mastery path §2 forbids from existing.
4. **`sungCents` is measured from the degree that was named**, not from whichever degree the voice
   landed nearest. The analyzer's own reading answers the right question for `M2`, where the nearest
   degree *is* the answer, and the wrong one here: a learner who held `5` when asked for `3` would
   otherwise appear to have sung almost perfectly.

The consequence worth stating plainly: on `M12` a sung pitch can never make an answer right or wrong.
It is recorded beside the judgment and read by nobody who decides anything.

## 6. UI and UX

Extends `08-UI-SPEC.md`. All of it applies, including §2a (every screen has a way out) and §3a / `11-ONBOARDING-CLARITY.md` (explanation with a worked example before any new task shape).

### 6.1 Permission and onboarding

- Microphone permission is requested **only** when the user actively opts into singing, never at install or first launch.
- The request must be preceded by a plain explanation: what the mic is used for, that audio never leaves the device, that it's optional, and that everything works without it.
- Denying or revoking permission silently disables singing. No nagging, no repeat prompts, no degraded experience elsewhere.

**Built 2026-08-22, in Settings.** The opt-in is a switch in a `Singing` section; pressing it without
permission raises the app's own explanation first and the system dialog only on confirm. A refusal
leaves the setting off and is never raised again by the app.

⚠️ **The lesson this stage cost.** `sungResponseEnabled` existed from Stage 3.3, defaulted to false,
and had no control anywhere — so Stages 3.1, 3.3 and 3.4 were each built, tested and reported
complete while being unreachable by any learner. Every one of those stages tests against a fake
microphone, so none of them could reveal it. **A stage is not done when its tests pass; it is done
when a person can get to it.** Later stages should state the route a learner takes to the thing being
built, and check it.

### 6.2 Explanation and worked example (mandatory)

Per `11-ONBOARDING-CLARITY.md` §1 and §4, before the first sung item:

1. Plain explanation of what will happen and what to do.
2. **A worked example** — this is not optional and is harder here than elsewhere. The user needs to hear an example of a sung answer being accepted, and understand that approximate is fine.
3. Explicit statement that singing quality is not being judged, only which note you aimed for.
4. Dismiss and start. Recallable later via the same help affordance.

### 6.3 The "I know it but can't sing it" path

Every sung item must offer a one-tap fallback to answer by tapping instead, always visible, no penalty, no different scoring. Someone who can hear it but can't produce it must never be stuck.

### 6.4 Feedback

- **Correct/incorrect** — identical treatment to tapped answers.
- **A separate, clearly non-scoring pitch readout** — "you were about a quarter-tone flat." Informational, framed neutrally, never a grade, never in the mastery window.
- **On a prediction item, the readout also names the degree that was held** — "You sang 5" — for a
  correct answer and an incorrect one alike. This is the one thing the button cannot express: whether
  the note being judged against was the note that was asked for. §5.4's diagnosability argument cuts
  both ways, and a learner who audiated the wrong degree entirely can only act on that if told. The
  cents line is shown only when the named degree *was* held: attached to someone who sang a different
  degree it would report a large number about the wrong question, which reads as a harsh grade on a
  voice that is not being marked.
- ⚠️ **Real-time visual pitch feedback while singing** (a moving indicator showing where your voice is relative to the target): valuable for learning to sing in tune, but it turns the exercise into a *matching* task rather than a *recall* task if shown before the answer is committed. **Decision: no real-time feedback during the answer window.** Show it after, as review. Revisit only with evidence.

### 6.5 Environment

The app must handle: background noise, a mic the user has covered, and the app's own audio bleeding into the mic. Reference audio and the answer window should not overlap; if they must, echo cancellation is required. An unusable signal produces "unclear," never "wrong."

## 7. Data model

Extends `05-DATA-MODEL.md`.

- `Attempt` gains `inputMethod: TAP | SUNG`.
- `Attempt` gains `sungCents: Int?` — deviation from the target degree's true pitch, nullable, **for display and analysis only**. Never read by `Staircase`, `AxisScheduler`, `MasteryEvaluator`, or `ConfusionTracker`. Add a test asserting this, in the same spirit as Phase 1's `replayCount` check.
- Settings gain `sung_response_enabled: Boolean` (default `false`) and `sung_octave_agnostic: Boolean` (default `true`).
- **No raw audio is ever persisted.** Captured audio exists in memory for the duration of one attempt and is discarded. Nothing recorded, nothing cached, nothing exported.

## 8. Build plan

| Stage | Content | Key acceptance criteria |
|---|---|---|
| 3.0 | Mic capture + pitch detection, headless | Detection accurate to within a semitone on synthetic and recorded test signals. Runs off main thread. No dropouts. Report measured latency and CPU. **Code complete 2026-08-22** (`AudioRecordMicrophoneSource`, bound in `AudioBindingsModule`); ⚠️ every measurement in this row is still owed and needs a device |
| 3.1 | Scoring pipeline (§5.2) | Scooping tolerated. "Unclear" never scores as wrong. Ambiguity-band decision implemented per §5.2. Octave-agnostic verified. |
| 3.2 | Permission flow + explanation + worked example | Full app functionality with permission denied. Explanation reachable. Copy meets `11-ONBOARDING-CLARITY.md` §9.4. **Built 2026-08-22** — the opt-in, its explanation and the request live in `SungResponseSection`; `SungResponseSectionTest` pins the explanation-before-request ordering |
| 3.3 | Sung response in `M2` | Tap-only path fully unaffected. Sung and tapped attempts share one `SkillState`. Fallback-to-tap always available. |
| 3.4 | Sung prediction in `M12` | Sung answer captured during the gap, before the target plays. Cannot be gamed by guessing. **Built 2026-08-22** — see §5.4's four implementation decisions |
| 3.5 | `M10` and `M11` | Chromatic tolerance bands verified not to produce systematic misreads. |
| 3.6 | Hardening + acceptance | Every Phase 1/2 criterion still met. No audio persisted. `sungCents` provably unread by the engine. |

Same discipline throughout: STOP gate per stage, delta report with production-wiring traces, no starting the next stage until the current one is verified.

### Required simulations (extends `10-TESTING.md` §5)

1. **Accurate singer** — sings correct degrees within tolerance. Masters normally.
2. **Consistently flat singer** — knows every answer, sings uniformly ~50 cents flat. **Must still master.** This is the single most important test in the phase; if it fails, the app is testing singing, not hearing.
3. **Unclear-audio user** — mumbles/noise on a fraction of attempts. Those attempts must not enter the mastery window or the confusion matrix as wrong.
4. **Tap-only user** — never grants mic permission. Must reach full mastery, including every independence check, identically to a Phase 2 user.
5. **Mixed-input user** — alternates tapping and singing. Both feed one `SkillState` coherently; no double-counting, no split progression.
6. **Guessing predictor (sung)** — sings random pitches on `M12`. Must never be certified.
   ⚠️ Note what this does and does not prove once §5.4 is decided as *supplement*: sung pitches never
   reach scoring, so a random singer who also guesses the button is refused by d-prime, which Phase 2
   already establishes. The case actually at risk is the inverse — a learner **singing the named degree
   perfectly and guessing the judgment**, who has not mastered a node whose whole question is the
   comparison. `SungPredictionSimulationTest` runs both, plus a third check that perfect audiation
   leaves a competent learner's mastery timeline identical item for item.

## 9. Open questions before Stage 3.0

1. **`AudioRecord` vs. Oboe/NDK** (§5.1) — decide with measurements, not assumption.
2. ~~**Ambiguity-band handling** (§5.2) — re-prompt or confirm.~~ **Decided 2026-08-21: re-prompt, at a 10-cent margin.** See §5.2.
3. ~~**Sung prediction: replace or supplement the binary judgment** (§5.4).~~ **Decided 2026-08-21: supplement.** See §5.4.
4. **Chromatic tolerance** (§5.3) — whether 12-degree resolution is viable for sung input at all, or whether singing should be limited to diatonic contexts.

⚠️ All four benefit from Phase 1/2 evidence. Answer them at the start of Phase 3, not now.
