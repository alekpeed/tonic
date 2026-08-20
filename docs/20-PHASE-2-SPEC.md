# 20 — Phase 2 Specification: Minor Mode, Chromatic Degrees, Audiation, Export

**Status: designed ahead of Phase 1 validation.** Sections marked ⚠️ contain decisions that should be revisited once Phase 1 has been used in earnest, because they depend on how the core loop actually performs with a real learner. Everything else is stable.

**Prerequisite: Phase 1 complete and stable.** Specifically `M2.FULL_DIATONIC` mastered and `M2.INDEPENDENCE_CHECK` passing for at least one real user. Do not begin Phase 2 against an unstable Phase 1 — every mechanism here inherits Phase 1's engine, and building on a shifting base is how the cadence-fade bug chain happened.

---

## 1. What Phase 2 adds, and why these four things together

| Addition | Rationale |
|---|---|
| Minor mode (`M8.MINOR_MODE` promoted to Phase 2) | Half of tonal music is minor. A learner who can only hear major has half a skill. Deferred from Phase 1 only to limit surface area, not because it is advanced. |
| Chromatic degrees | The five notes outside the diatonic set. Needed before any real-music work, and needed to make "wrong note" perceptible rather than just "a note I don't have a button for." |
| Prediction / audiation items | Phase 1 trains recognition of a sounded note. This trains hearing a note *before* it sounds, which is the actual endpoint (`02-PEDAGOGY.md` §6 reserved this item type). |
| Data export | The attempt log is the source of truth and currently has no way out of the device. Small, self-contained, and unblocks the user inspecting their own progress. |

These are grouped because the first three share one engine change (expanding the answer alphabet and mode handling) and the fourth is independent, small, and a natural fit for a phase that is otherwise curriculum-heavy.

## 2. Pedagogy additions

These extend `02-PEDAGOGY.md`. Every invariant in that document's §10 still holds — tonal context on every item, scale degrees not intervals, fading harmonic support, randomized keys, varied timbre, no singing/notation/keyboard requirement.

### 2.1 Minor mode: which minor, and how it is labeled

**Decision: natural minor is the base; harmonic and melodic minor are introduced as alterations of it, not as separate modes.**

The problem minor creates is that scale degrees 6 and 7 are unstable across the three forms — natural minor has ♭6 and ♭7, harmonic minor raises 7, melodic minor raises both ascending. If each form is treated as a separate scale with its own labels, the learner has three competing systems for the same key.

Instead:

- Natural minor is the reference. Degrees are labeled `1, 2, ♭3, 4, 5, ♭6, ♭7`.
- The raised 7 (harmonic minor's leading tone) is labeled `7` (natural). It is presented as an *alteration* — a note that pulls to 1 more strongly than ♭7 does.
- The raised 6 is labeled `6`. Same treatment.
- The learner's answer alphabet in minor is therefore: `1, 2, ♭3, 3, 4, 5, ♭6, 6, ♭7, 7` — with the naturals introduced later as chromatic alterations, per §2.2.

This means the *label* for a given pitch is consistent regardless of which minor form the item is drawn from, which is the property that matters.

⚠️ **Revisit after Phase 1:** if `08-UI-SPEC.md` §3's degree ladder proves hard to read at seven positions, ten positions in minor will be worse. The ladder may need a different layout for minor, or chromatic degrees may need to be a secondary row rather than inline positions. This depends on how the ladder actually performs, which Phase 1 will tell us.

### 2.2 Chromatic degrees in major

Introduced in a fixed order, chosen by how strongly each pulls toward a stable tone (strongest pull first, because a strong pull is easier to hear):

1. `♯4` — the tritone, pulls hard to 5. Most distinctive chromatic note in the scale.
2. `♭7` — pulls down to 6, and is the flat seventh that makes a dominant seventh chord.
3. `♭6` — pulls down to 5.
4. `♭3` — pulls down to 2, and is the mode-defining note (major/minor ambiguity).
5. `♭2` — pulls down to 1. Rarest in common practice, introduced last.

Each is introduced against the already-mastered diatonic set, never in isolation. The learner must be able to distinguish `♯4` from both `4` and `5`, which is the actual skill — a chromatic note is only meaningful relative to the diatonic notes it sits between.

### 2.3 Prediction / audiation items

The Phase 1 item is: reference → target sounds → identify it. The Phase 2 audiation item inverts this: reference → **the learner is told which degree is coming** → a silent gap → the note sounds → the learner reports whether it matched what they expected.

Structure:

1. Establish key per the usual cadence-fade level.
2. Display the target degree on screen (e.g. "5"). No audio.
3. A silent gap of configurable length — this is where the learner audiates.
4. A note sounds. It is either the stated degree, or a near neighbor.
5. Learner answers: `MATCHED` / `DIDN'T MATCH`.

This trains internal pitch generation rather than reaction to an external stimulus, which is the Gordon-derived goal in `02-PEDAGOGY.md` §6. It is also self-verifying: a learner who cannot audiate will perform at chance, and the confusion data will show it.

**Difficulty axes for prediction items:**

| Axis | Levels | Meaning |
|---|---|---|
| `PREDICT_GAP` | 0–3 | Silent gap length: 1000ms → 2000ms → 3500ms → 5000ms. Longer is harder — the internal representation must be held. |
| `PREDICT_DEVIATION` | 0–3 | How far the actual note deviates when it doesn't match: adjacent diatonic degree → same degree wrong octave → chromatic neighbor → same degree detuned 30 cents |

Level 3 of `PREDICT_DEVIATION` (a slightly detuned correct degree) is deliberately at the edge of what is reasonable and should be treated as an advanced target, not a required mastery gate.

⚠️ **Revisit after Phase 1:** the `MATCHED / DIDN'T MATCH` binary is vulnerable to response bias — a learner can score 50% by always answering one way, and 75%+ by guessing strategically. Phase 1's `M0.SAME_DIFF` already handles this with d-prime (`07-ADAPTIVE-ENGINE.md` §2) and the same treatment applies here. If d-prime proves insufficient in practice, an alternative is a three-way answer (`MATCHED / TOO LOW / TOO HIGH`) which is harder to game.

### 2.4 Mode identification as its own skill

Before a learner can identify degrees *in* minor, they must hear *that* it is minor. A prerequisite skill: reference plays, learner answers `MAJOR` / `MINOR`. Trivial for a trained ear, non-trivial for a beginner, and it gates everything else in minor.

---

## 3. Curriculum: modules and skill nodes

Extends `03-CURRICULUM.md`. All identifiers below are new and permanent.

### Module M9 — Mode Identification (new module, Phase 2)

| SkillId | Task | Answer alphabet | Prerequisite |
|---|---|---|---|
| `M9.MODE_ID_CADENCE` | Identify mode from a full cadence | `{MAJOR, MINOR}` | `M2.FULL_DIATONIC` mastered |
| `M9.MODE_ID_TRIAD` | Identify mode from a bare tonic triad | `{MAJOR, MINOR}` | `M9.MODE_ID_CADENCE` |
| `M9.MODE_ID_MELODIC` | Identify mode from a short melodic fragment, no harmony | `{MAJOR, MINOR}` | `M9.MODE_ID_TRIAD` |

Mastery per node: ≥90% over a rolling 30, d-prime ≥ 2.0 (binary answer — accuracy alone is insufficient, same reasoning as `M0.SAME_DIFF`).

### Module M10 — Minor Mode Functional Recognition

Mirrors `M2`'s structure exactly. Same six difficulty axes, same cadence-fade mechanic, same mastery criteria including the `CADENCE_FADE ≥ 4` requirement.

| SkillId | Active degree set | Prerequisite |
|---|---|---|
| `M10.MIN_SET_1` | `{1, ♭3, 5}` | `M9.MODE_ID_TRIAD` mastered |
| `M10.MIN_SET_2` | `{1, 2, ♭3, 5}` | `M10.MIN_SET_1` |
| `M10.MIN_SET_3` | `{1, 2, ♭3, 5, ♭6}` | `M10.MIN_SET_2` |
| `M10.MIN_SET_4` | `{1, 2, ♭3, 4, 5, ♭6}` | `M10.MIN_SET_3` |
| `M10.MIN_NATURAL` | `{1, 2, ♭3, 4, 5, ♭6, ♭7}` | `M10.MIN_SET_4` |
| `M10.MIN_HARMONIC` | adds `7` (raised) | `M10.MIN_NATURAL` |
| `M10.MIN_MELODIC` | adds `6` (raised) | `M10.MIN_HARMONIC` |
| `M10.MIN_INDEPENDENCE_CHECK` | 30 items at `CADENCE_FADE` L6 | `M10.MIN_MELODIC` |

The minor cadence is `i–iv–V–i` (with a major V, the common-practice default, since the raised leading tone is what makes the cadence read as minor rather than modal). ⚠️ Revisit: an all-natural-minor `i–iv–v–i` may be worth offering as a variant once `M10.MIN_NATURAL` is mastered, to avoid training the learner to expect a raised 7 in every minor context.

### Module M11 — Chromatic Degrees

| SkillId | Adds | Prerequisite |
|---|---|---|
| `M11.CHROM_SHARP4` | `♯4` to full diatonic major | `M2.INDEPENDENCE_CHECK` passed |
| `M11.CHROM_FLAT7` | `♭7` | `M11.CHROM_SHARP4` |
| `M11.CHROM_FLAT6` | `♭6` | `M11.CHROM_FLAT7` |
| `M11.CHROM_FLAT3` | `♭3` | `M11.CHROM_FLAT6` |
| `M11.CHROM_FLAT2` | `♭2` | `M11.CHROM_FLAT3` |
| `M11.CHROM_FULL` | All twelve chromatic degrees | `M11.CHROM_FLAT2` |

Mastery adds one criterion beyond `M2`'s five: **the newly added chromatic degree must itself reach ≥80% accuracy in the window**, not just contribute to an overall average. Without this a learner masters `M11.CHROM_FLAT2` while being at chance on `♭2` specifically, carried by seven confident diatonic answers.

### Module M12 — Audiation / Prediction

| SkillId | Scope | Prerequisite |
|---|---|---|
| `M12.PREDICT_TRIAD` | Predict within `{1, 3, 5}` | `M2.FULL_DIATONIC` mastered |
| `M12.PREDICT_DIATONIC` | Predict within full diatonic major | `M12.PREDICT_TRIAD` |
| `M12.PREDICT_MINOR` | Predict within natural minor | `M12.PREDICT_DIATONIC` + `M10.MIN_NATURAL` |
| `M12.PREDICT_CHROMATIC` | Predict within full chromatic | `M12.PREDICT_DIATONIC` + `M11.CHROM_FULL` |

Mastery: d-prime ≥ 2.0 over a rolling 30 at `PREDICT_GAP` ≥ 2, plus overall accuracy ≥ 85%.

### Interleaving across major and minor

Once both `M2.FULL_DIATONIC` and `M10.MIN_NATURAL` are mastered, sessions must interleave major and minor items **without announcing the mode**. This is the real skill: identifying a degree when you don't know in advance which mode you're in. A new node covers it:

| SkillId | Task | Prerequisite |
|---|---|---|
| `M10.MIXED_MODE` | Degree ID, mode randomized per item, unannounced | `M2.FULL_DIATONIC` + `M10.MIN_NATURAL` + `M9.MODE_ID_CADENCE` |

This is arguably the most valuable node in Phase 2 and should not be treated as optional.

---

## 4. Engine changes

Extends `07-ADAPTIVE-ENGINE.md`. Most of Phase 1's engine is reused unchanged — this is the payoff for the layering work.

**Unchanged:** staircase (including the per-axis step size correction in §2a), axis scheduler, confusion tracker, mastery evaluator's five criteria, FSRS scheduling, session composer.

**Changes required:**

1. **`Mode` becomes a first-class item parameter.** Currently `Mode.MAJOR` is effectively hardcoded. Generators, reference plan builders, and the degree↔semitone mapping must all take mode as input.
2. **Answer alphabet becomes variable-length beyond 7.** The ladder, the confusion matrix, and the balanced sampler all currently assume at most seven positions. Chromatic degrees push this to twelve.
3. **Two new difficulty axes** (`PREDICT_GAP`, `PREDICT_DEVIATION`) that apply only to `M12` items. The axis scheduler must handle axes that are skill-specific rather than universal — currently all six axes apply to every `M2` node.
4. **A new item type** (`PredictionItem`) alongside `FunctionalRecognitionItem`. The sealed `Item` hierarchy in `:core:model` was designed for this; verify it actually accommodates a fundamentally different interaction shape without forcing changes upstream.
5. **d-prime scoring for binary-answer nodes** (`M9.*`, `M12.*`) reusing the existing implementation from `M0`.

**Explicitly not changing:** the mastery criteria structure, the FSRS grade mapping, the session composition ratios. If Phase 2 tempts a change to any of those, that is a signal something is being modeled wrong — stop and ask.

---

## 5. UI additions

Extends `08-UI-SPEC.md`. Every requirement in that document holds, including §2a (every screen has a way out) and §3a (first-run explanation before any new task shape).

### 5.1 Mandatory explanations for each new item type

Per `11-ONBOARDING-CLARITY.md` §4, **each of these gets its own first-run explanation screen with a worked example** before it may ship:

- Minor mode degree identification (`M10`) — must explain what changed from major and why `♭3` is labeled that way.
- Mode identification (`M9`) — must explain what "major" and "minor" are being asked about, in plain terms, without assuming the user knows.
- Chromatic degrees (`M11`) — must explain what a note "between" the familiar ones means and why it has a new label.
- Prediction items (`M12`) — this one needs the most careful treatment, because the interaction is genuinely different: the user must understand they are meant to *imagine* the note before it plays. A worked example is not optional here; it is the only way this task is comprehensible.

### 5.2 The degree ladder in minor and chromatic contexts

⚠️ This is the highest-risk UI question in Phase 2 and depends on Phase 1 evidence.

Requirements regardless of layout chosen:

- Chromatic degrees must be visually distinguishable from diatonic ones at a glance — a `♭6` button should not look identical to a `6` button.
- Adding chromatic positions must not shrink diatonic buttons below the minimum touch target (`08-UI-SPEC.md` §3).
- The ladder must remain scroll-free at twelve positions on a 5-inch screen at 200% font scale, or the layout must change. Given Phase 1 already had rendering/contrast problems at seven positions with gaps, assume this needs a real redesign, not an incremental extension.
- Per the open question in `11-ONBOARDING-CLARITY.md` §6 (bare numerals vs. semantic labels), whatever is decided there applies here too and gets harder with twelve positions.

### 5.3 Prediction item screen

A distinct screen layout, not a variant of the practice screen:

- The target degree is displayed prominently during the silent gap — this is the *instruction*, not the answer, and must not look like a revealed answer.
- The silent gap needs a visible, non-anxiety-inducing indicator that time is passing and audio is coming. A countdown timer is wrong (creates pressure, violates `02-PEDAGOGY.md` §6's no-timer-pressure rule); a calm progress indication is right.
- Answer controls are two buttons (`MATCHED` / `DIDN'T MATCH`), not the ladder.

### 5.4 Mode indication in mixed-mode practice

In `M10.MIXED_MODE`, the mode is deliberately **not** shown before the answer — that is the skill. But after the answer, the feedback must state which mode it was, or the learner cannot learn from a mistake.

---

## 6. Data export

Small, self-contained, independent of everything above.

**What exports:** the full `attempts` table, `skill_states`, `confusion_cells`, `sessions`, and `diagnostic_results`, as a single JSON file. This is the complete state — per `05-DATA-MODEL.md` §1, everything else is derivable from `attempts`.

**How:** a share/save action from Settings producing a timestamped file via Android's standard file/share mechanism. No network, no account, no cloud — consistent with `01-PRODUCT-SPEC.md` §4.

**Import:** ⚠️ deliberately **not** in Phase 2. Import means merge conflicts, schema-version mismatches, and a path to corrupting a working profile. Export alone is safe and unblocks the user inspecting their data. Revisit import only if there is a concrete need (device migration), and treat it as its own design problem.

**Privacy note:** the export includes `amusia_indicator_flag` from `diagnostic_results`. Per `02-PEDAGOGY.md` §8 this must never be surfaced evaluatively to the user — an exported raw data file is not a user-facing evaluative surface, but the field name itself is legible to anyone who opens the file. **Decision: exclude `amusia_indicator_flag` from export entirely.** It is internal routing state, not user progress, and its presence in a shareable file is a risk with no upside.

---

## 7. Build plan

Same discipline as `09-BUILD-PLAN.md`: sequential stages, each ending in a **STOP** gate with a delta report. Same verification standard — production-wiring traces, not just passing tests.

| Stage | Content | Key acceptance criteria |
|---|---|---|
| 2.0 | Engine generalization: mode as parameter, variable answer alphabet, skill-specific axes | All Phase 1 tests still pass unchanged. Determinism holds. No behavior change to `M2`. |
| 2.1 | Data export | Exported JSON round-trips to identical state when parsed. `amusia_indicator_flag` absent. Works offline. |
| 2.2 | `M9` mode identification + explanation screen | d-prime scoring correct. Explanation screen with worked example present and reachable. |
| 2.3 | `M10` minor mode, sets 1–4 + explanation screen | Cadence-fade mechanic works identically to `M2`. Minor cadence renders correctly at all 8 fade levels. |
| 2.4 | `M10` natural/harmonic/melodic + independence check | Degree labeling consistent across all three minor forms. |
| 2.5 | `M11` chromatic degrees | New per-degree mastery criterion enforced. Ladder legible at 12 positions (or redesigned). |
| 2.6 | `M12` prediction items + explanation screen | New item type and screen. d-prime scoring. Gap indicator creates no time pressure. |
| 2.7 | `M10.MIXED_MODE` interleaving | Mode randomized and unannounced pre-answer, stated post-answer. |
| 2.8 | Hardening + full-phase acceptance | All Phase 1 criteria still met. New simulations for every new node type. |

**Stage 2.0 is the risky one.** Generalizing the engine while keeping every Phase 1 behavior byte-identical is exactly the kind of change that silently breaks things — the determinism race in Phase 1 came from a similar "small, safe" refactor. Treat Stage 2.0's acceptance criterion ("no behavior change to `M2`") as literal: same seeds must produce the same items before and after.

### Required simulations (extends `10-TESTING.md` §5)

Each new node type needs a simulated learner, matching the Phase 1 pattern:

1. Mode-identification learner: competent, masters `M9`.
2. Mode-deaf learner: at chance on `M9.MODE_ID_TRIAD`, correctly never certified.
3. Minor learner: masters `M10` including the independence check.
4. Major-only learner: masters `M2`, at chance on `M10.MIXED_MODE` — confirms mixed-mode genuinely tests something `M2` alone does not.
5. Chromatic learner: masters `M11`, with the per-degree criterion verified to block a learner who is at chance on the newly added degree specifically.
6. Prediction learner: masters `M12` at `PREDICT_GAP` ≥ 2.
7. Biased prediction responder: always answers `MATCHED`. Must never be certified — this is the d-prime check.

---

## 8. Open questions requiring a decision before Stage 2.0

**All four were decided by the maintainer on 2026-08-20, before Stage 2.0 began. The decisions are recorded below and are binding; the original questions are kept so the reasoning stays legible.**

1. **Degree ladder at twelve positions** (§5.2) — extend the existing layout, or redesign? Depends on Phase 1 evidence about how the ladder actually performs.
2. **Bare numerals vs. semantic labels** (`11-ONBOARDING-CLARITY.md` §6) — still unresolved from Phase 1, and chromatic degrees make it more urgent, not less.
3. **`MATCHED / DIDN'T MATCH` vs. three-way answer for prediction items** (§2.3) — depends on whether d-prime proves sufficient against response bias.
4. **`M10`'s minor cadence with major or minor V** (§3) — default is major V; whether to add a natural-minor variant is open.

### 8.1 Decisions

**1 — Ladder: redesign, on piano geometry.** The seven-slot diatonic column stays as the spine and keeps its vertical pitch mapping. The five chromatic degrees hang off the *boundaries* between their diatonic neighbors: narrower, horizontally offset, vertically centered on the seam — the geometry of black keys against white ones. Twelve positions therefore cost no additional vertical height, and chromatic degrees are distinguished from diatonic ones by shape and position rather than by fill, which is the same principle that fixed the inactive-gap defect (`08-UI-SPEC.md` §3).

Extension was not rejected on taste. It is arithmetically impossible: every slot is a fixed `56.dp` touch target with `8.dp` spacing and every slot renders whether or not it is active, so seven slots are `7×56 + 6×8 = 440dp` and twelve would be `12×56 + 11×8 = 760dp`, against roughly `568dp` of usable height on the 5-inch reference screen `08-UI-SPEC.md` §3 names — exceeded before any other UI is placed. Keeping a twelve-slot column would require abandoning either the 56dp minimum or the no-scroll rule, both of which are explicit requirements.

**Consequence for the build plan:** `09-BUILD-PLAN.md` Stage 7 claimed "ladder fits seven degrees plus gaps on a 5-inch screen, no scroll, at 200% font scale" and `08-UI-SPEC.md` §9 requires testing the ladder at maximum scale, but no test in the repository ever measured either — there are no instrumented tests and no Compose UI tests at all, though `ui-test-junit4` and Robolectric are both already available. That criterion was reported met without mechanical verification, and by the arithmetic above the current 440dp ladder plus the practice screen's chrome plausibly overflows a 5-inch screen today. **Stage 2.0 therefore opens with a layout-measurement harness, before any refactor**: it retires the Phase 1 debt and it is the only way Stage 2.5's "ladder legible at 12 positions" can be verified rather than asserted.

**2 — Labeling: a fading subtitle beneath the numeral.** The numeral stays the canonical label. Each button carries a small, persistent subtitle for the first N sessions after its degree set is unlocked, then drops it. Chosen over relationship phrases ("Home / Up a bit / Up more"), which have no honest phrasing that separates `♯4` from `4` and `5` and so collapse at exactly the point Phase 2 needs them; and over solfège-as-default, since chromatic solfège (`di, ri, fi, se, le, te`) is more foreign to a beginner than `♭6`, not less. Subtitles are the only option that scales to twelve positions, and being presentation-only they live entirely in `:core:ui` and add nothing to Stage 2.0's risk.

**3 — Prediction: three buttons throughout, scored as a binary at the introductory node.** `MATCHED / TOO LOW / TOO HIGH` is the control layout from the first `M12` item onward, so the interaction never changes shape mid-module. At `M12.PREDICT_TRIAD` either directional answer scores simply as "detected a mismatch" — a learner who hears that it was wrong but cannot yet name the direction is not penalized for a skill that belongs to `M1.HIGH_LOW`. From `M12.PREDICT_DIATONIC` onward direction is scored.

Response bias was not the deciding factor, because d-prime already defeats it: an always-`MATCHED` responder over 30 trials yields a hit rate and a false-alarm rate that are equal, so `z(hit) − z(fa)` is 0 and §7's simulation 7 rejects it under either answer format. The deciding factor is that §2.3's claim that "the confusion data will show it" is **false under a binary** — a 2×2 matrix records that a learner was wrong without recording anything about what they heard instead. Direction is the smallest addition that makes the confusion data diagnostic, and it drops guessing from 50% to 33% as a side effect.

**4 — Minor cadence: both, bound to the node rather than randomized.** `i–iv–v–i`, all natural minor, is the reference cadence for `M10.MIN_SET_1` through `M10.MIN_NATURAL`. `i–iv–V–i` with the major V arrives at `M10.MIN_HARMONIC`, which is precisely where the raised `7` becomes an answer option.

The rule this enforces: **the reference must never sound a pitch the learner has no button for and has not been taught.** A major V in early minor sounds a `♮7` in every single cadence while the learner's active set contains only `♭7` and no `♮7` button exists — reproducing, in a pedagogically active form, the exact "the chords contain notes my buttons don't have" confusion reported from live Phase 1 use in major. The trade-off is accepted knowingly: `i–iv–v–i` establishes the tonic less forcefully without a leading tone, and Stage 2.3 must verify by spectral test and by ear that it still establishes a key at every fade level. Randomizing the two per item was rejected: it adds reference variance at the same time the `CADENCE_FADE` axis is already varying the reference, which is the confound `07-ADAPTIVE-ENGINE.md` §3 exists to prevent.

### 8.2 Deviation: the ladder may scroll

**Accepted 2026-08-20, while fixing a Phase 1 defect the Stage 2.0 layout harness surfaced.**

Three requirements cannot all hold on the 5-inch screen `08-UI-SPEC.md` §3 names:

1. every touch target is at least 56dp (`08-UI-SPEC.md` §1),
2. the ladder never scrolls during an answer (§3, as originally written),
3. the practice screen carries the chrome §4 mandates — header, time bar, phase indicator, phase caption, replay, help/skip.

Seven buttons need `7×56 + 6×8 = 440dp`. A 5-inch screen has ~568dp after its system bars, and §4's chrome occupies ~248dp of that even with every discretionary spacer removed, leaving ~308dp. The shortfall is arithmetic, not styling.

Phase 1 resolved this conflict by accident, in the worst available way: `Column` squeezed its trailing children, so degrees 1, 2 and 3 rendered at **zero height** and the tonic was unpressable at `M2.DEG_SET_1` — the node every user starts on. Nothing detected it because no test measured layout.

**Resolution: requirement 2 gives.** Touch targets are inviolable; the ladder scrolls when the active set cannot fit. Two changes mean this almost never engages in practice:

- Inactive gaps now occupy a slim slot rather than a full 56dp button slot. They are not touch targets — no `onClick`, cleared from the accessibility tree — so §1 never applied to them. At `M2.DEG_SET_1` this alone reclaimed 176dp.
- The practice chrome's doubled spacing was removed (a `Spacer(lg)` stacked directly on the phase indicator's own `lg` top padding, 48dp for one visual break). No mandated element was dropped.

Measured result on the 5-inch reference device: every active button is at or above the touch target at both 100% and 200% font scale, and the starting node shows all of its buttons at once with no scrolling. The full seven-degree set scrolls slightly, which is the case §3's author assumed away.

**This also settles the shape of decision 1's redesign.** Twelve inline positions were already impossible at 760dp; the piano geometry that hangs chromatic degrees off the seams between diatonic neighbors is now the only design that fits, and Stage 2.5 must verify it against `PracticeScreenLayoutTest` rather than against a preview.

### 8.3 Two degrees can share one scale position

**Found in Stage 2.4, while building the three minor forms.**

Harmonic minor is natural minor plus a raised 7, so `♭7` and `♮7` are both answers and both sit at scale position 7. Melodic minor does the same at position 6. The ladder matched each position to a single degree, so the second one was dropped silently — a learner practicing harmonic minor would have had no button for the note that *defines* harmonic minor.

The fix is §8.1 decision 1's geometry, arriving a stage earlier than planned and generalized: a slot draws **every** active degree at that position, side by side, sorted by pitch with the lower on the left. The mode's own diatonic degree keeps the spine and is drawn at twice the width; an alteration of it hangs alongside, narrower. Distinguished by size and position rather than by fill, and twelve positions cost no more vertical height than seven — which is the property §8.2 depends on.

This is the same mechanism `M11` needs in Stage 2.5, so chromatic degrees in major require no further ladder work: `♯4` hangs off position 4 exactly as `♮7` hangs off position 7.

Two consequences worth stating. Button test tags are keyed by the degree's canonical label rather than its position, since a position no longer identifies a button — identical for every unaltered degree, so nothing about `M2` changed. And the independence check is now per chain rather than hardcoded to `M2.FULL_DIATONIC`: before this, minor could be mastered end to end without ever being asked to hold a key unaided, which is the one thing the check exists to establish.

### 8.4 Stage 2.5 findings and deviations

**Two wiring bugs, both found by tracing production paths rather than by a failing test.**

*The minor explanation screen was unreachable.* `PracticeViewModel.introKindFor` resolved the right `IntroKind` and had tests; `M10IntroContent` was written, previewed and tested as a composable. `PracticeScreen` then called `M2IntroContent` unconditionally, so a learner arriving at minor was shown the major explanation — the exact denial §5.1 and `08-UI-SPEC.md` §3a exist to prevent. The defect was one line between two correct halves, and nothing in the suite tested the join. The dispatch is now its own composable (`IntroForKind`) with a test per kind, so the join has something to fail against. `M9IntroContent` remains unreachable for a different and already-recorded reason: `M9` has no route from Home at all.

*`M10` and `M11` nodes had no mastery lifecycle.* `SkillStateReducer` gated its whole reduction — staircase, mastery verdict, FSRS scheduling — on `moduleId != M2`, which was correct while `M2` was the only recognition module. Left alone, every minor and chromatic node would have accumulated attempts forever, stayed `IN_PROGRESS` permanently, never scheduled a review and never advanced to a successor, and this stage's own per-degree criterion would have been written and then never consulted by anything a learner could reach. The predicate is now membership of `SkillGraph`, which is the property that actually decides whether a full reduction is defined for a skill.

**Deviation: the `M11` explanation screen has no worked-example button.** §5.1 asks for "its own first-run explanation screen with a worked example." The other three screens demonstrate a *new task shape*; `M11` is the same task with a wider answer set, and the honest demonstration of "`♯4` sits between `4` and `5`" is the ladder itself, one screen away, where `4` and `5` are visible on either side of it. Playing a chromatic note in isolation, before the learner has the ladder in front of them, demonstrates nothing they can act on — it is a note with no frame, which is the one thing `02-PEDAGOGY.md` §1 says never to present. The screen instead spends its length on the belief it has to overturn: that a key contains seven notes because that is all the ladder has ever shown.

**The focus criterion's five attempts are a ceiling, not a constant.** §3 asks that the newly added chromatic degree reach ≥80% accuracy in the window, and the implementation requires a minimum sample before that percentage means anything. Five is not always reachable: `03-CURRICULUM.md` §5.4 caps any degree at 1.5× its expected rate, and at twelve simultaneously active degrees no weighting can lift one degree past about three attempts in a 30-item window. The requirement therefore scales to what the window can deliver, capped at the documented 5 — the same resolution §5.5's own per-degree coverage rule already reached when it hit the identical wall. Nodes with room still require the full 5; only the widest sets relax. The accuracy bar does not relax, which means at three attempts the new degree must be answered perfectly.

**Overlap with the existing criteria, stated plainly.** On a learner who is simply *wrong* about the new degree, `WEAKEST_DEGREE_ACCURACY` already blocks — it holds every active degree to 80%. What the new criterion adds is coverage: the general rule ignores a degree with zero attempts entirely, and its own coverage requirement scales down as the set widens, so at twelve degrees two lucky answers on `♭2` satisfy every older criterion. That case is what the new one refuses, and it is what its test isolates.
