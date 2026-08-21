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

**Consequence for the build plan:** `09-BUILD-PLAN.md` Stage 1.7 claimed "ladder fits seven degrees plus gaps on a 5-inch screen, no scroll, at 200% font scale" and `08-UI-SPEC.md` §9 requires testing the ladder at maximum scale, but no test in the repository ever measured either — there are no instrumented tests and no Compose UI tests at all, though `ui-test-junit4` and Robolectric are both already available. That criterion was reported met without mechanical verification, and by the arithmetic above the current 440dp ladder plus the practice screen's chrome plausibly overflows a 5-inch screen today. **Stage 2.0 therefore opens with a layout-measurement harness, before any refactor**: it retires the Phase 1 debt and it is the only way Stage 2.5's "ladder legible at 12 positions" can be verified rather than asserted.

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

### 8.5 Stage 2.6 findings and deviations

**A third wiring bug, of the same family as §8.4's two.** `PracticeContent` rendered the degree ladder unconditionally, so an `M9` item — which has no degrees — put an **empty ladder on screen and no major/minor buttons at all**. The item type, the generator, the ViewModel's answer handler and the explanation screen were all built in Stage 2.2 and correct; the screen never grew a branch for them, and `M9` is unreachable from Home, so nothing exercised the gap. `M12` needed the same dispatch, and writing three buttons for it while leaving `M9`'s two unwritten would have meant authoring the branch and deliberately breaking one arm of it. The answer control is now dispatched on item type in one place (`AnswerArea`), with a test per type.

Three of these now, all the same shape: a module built correctly end to end except for the single line that connects two correct halves, in a place no existing test looked. The pattern is worth naming rather than fixing three times — a new item type touches the generator, the loop, the mastery evaluator, the answer control and the explanation screen, and only the last two live in the composition layer where nothing had a seam to test. Both seams now have one.

**A fourth, in the scheduler.** `AxisScheduler` hardcoded the recognition scheduling priority. For an `M12` node it would have picked `CADENCE_FADE` — an axis prediction items do not have and whose level changes nothing about them — so the staircase would have been measuring noise while `PREDICT_GAP` never moved, and §3's `PREDICT_GAP ≥ 2` mastery criterion would have been permanently unreachable. `AxisSchedulerState` now carries its scope. Stage 2.0 had already built `schedulingPriorityFor(scope)`; nothing called it.

**Decision: `M12` never fades its cadence.** §4 says prediction items do not use `CADENCE_FADE`, and every `M12` item therefore plays the full four-chord establishment. This is not a simplification. The skill under test is holding a degree against a *known* tonic; with a faded reference, a wrong answer would be ambiguous between "could not audiate" and "lost the key," which is the confound `07-ADAPTIVE-ENGINE.md` §3 exists to prevent. The independence question is answered by the `M2` chain, which is `M12.PREDICT_TRIAD`'s prerequisite.

**Decision: the silent gap lives inside the rendered buffer.** It would have been simpler to play the cadence, pause on the UI thread, then play the note. The gap *is* the `PREDICT_GAP` axis, so its length has to be exact; two plays separated by a screen-timed pause drift apart under any scheduling hiccup, and the axis would be measuring the device rather than the learner.

**Decision: d-prime is computed on the collapsed answer.** The three-button layout records `MATCHED` / `TOO_LOW` / `TOO_HIGH`; signal detection needs two categories. Collapsing to matched-versus-not is the honest mapping — the signal being detected is a *mismatch*, and its direction is a separate, finer judgment. A learner who reliably detects mismatches but names the direction wrong half the time then has a good d-prime and a poor accuracy, which is exactly the shape their skill has, and the verdict says both.

**`M12`'s accuracy bar is 85%, `M9`'s stays 90%**, as §3 states for each. Prediction has an irreducible noise floor — even a fluent audiator misses a 30-cent bend sometimes — and holding it to 90% would make `PREDICT_DEVIATION` level 3 a mastery blocker, which §2.3 explicitly rules out.

**Scoring versus recording at `M12.PREDICT_TRIAD`.** §8.1 decision 3 says either directional answer scores as "detected a mismatch" at the introductory node. The *recorded* label stays directional even there: direction was added to make the confusion data diagnostic, and collapsing it in the log would discard that at the one node where a beginner's errors are most informative. Failing to notice a mismatch at all is never excused, at any node.

### 8.6 Stage 2.7 findings and decisions

**The ladder had to stop announcing the mode, twice over.** §3 says `M10.MIXED_MODE` randomizes the mode "unannounced," and the announcement it is easiest to overlook is the answer control itself.

*The button set.* If the ladder showed the item's own seven degrees, a `♭3` on screen would state that this item is minor before a note sounded, and a learner could answer by reading the buttons. So the node's active set is the ten-degree union of both modes — which is exactly the alphabet §2.1 already specifies for minor, `1, 2, ♭3, 3, 4, 5, ♭6, 6, ♭7, 7`. That forces a distinction no earlier node needed: **what the ladder shows and what an item may target are now different sets.** A major item still cannot ask for `♭3`; asking would be asking about a note the key does not contain.

*The spine.* Subtler and worse. The ladder draws a mode's own degrees wide, on the spine, with alterations hanging narrower beside them (§8.3). A spine that followed the item would make `♮3` wide on a major item and `♭3` wide on a minor one — the whole column reshaping per item, readable at a glance without even looking at the labels. The spine is therefore pinned to major at this node. That is also the honest reading of §2.1's model: an alteration is absolute relative to major, so `♭3` belongs beside `3` whichever mode is sounding.

**The mode is held across a reference group.** At `CADENCE_FADE` L1, and in the L6/L7 audiation blocks, the first item plays a cadence and the rest sound a bare note against it. Re-rolling the mode inside a block would ask about a key the learner was never given — unanswerable rather than merely hard, the failure `07-ADAPTIVE-ENGINE.md` §2a exists to prevent. `ReferenceGroup` now carries its mode alongside its key and tonic.

**A measured correction to sampling, and the reason it was needed.** `03-CURRICULUM.md` §5.5's coverage criterion asks every active degree for `min(5, 30/n)` attempts. At ten degrees that is three, and ten degrees at three attempts is thirty items exactly — a qualifying window has to be a *perfect partition* of the mastery window. That is survivable only because the evaluator runs on every attempt over a rolling window, so the node needs one covering window to exist rather than every window to be one.

Two things had to change for one to arrive in a practice session rather than a career:

1. `BalancedSampler`'s ceiling is "1.5× the expected rate", and *expected* is a property of the pool the history came from, not of the shortlist a given call is choosing between. Deriving it from the seven candidates of one mode, while the history held all ten, computed a ceiling of `1.5 × 20/7 ≈ 4.3` where the honest figure is `1.5 × 20/10 = 3.0` — loose enough that the balance rule barely bound. The call now passes the universe size explicitly; it defaults to the candidate count, which is what every other caller means, so no other node moved.
2. Weights at this node have a structural half and a corrective half. Structural: a degree present in one mode gets twice the weight of one present in both, which is exactly the factor that equalizes expected exposure across the union. Corrective: a degree that already has its required share is damped sharply while any degree still lacks one — damped rather than excluded, so the sequence never becomes predictable, with the 1.5× ceiling still sitting on top to prevent clumping.

Measured across twelve independent seeds: before, a covering window took a median of roughly 440 items and on one seed did not arrive within two thousand — a node a learner could practice for weeks without ever being certified on, failing silently, since every item generates correctly and mastery simply never comes. After, the range is 30–71 items, 30 being the earliest a 30-item window can close at all. `M2.DEG_SET_4`, which sits at the same zero-slack arithmetic (six degrees × five attempts = thirty), was measured unchanged at a median of 78 — pre-existing, tolerable, and deliberately left alone rather than "fixed" by loosening a shipped criterion.

**It gets an explanation screen, which §5.1 does not list.** `08-UI-SPEC.md` §3a's test for a new task shape is "a different question, a different answer control, **or a different thing to listen for**." This node is the third: the learner must hear which key they are in before the degree means anything, and the ladder has silently grown from seven buttons to ten because of it. Its worked example is deliberately a minor item — a major one would demonstrate a flow indistinguishable from ordinary practice and teach nothing about why the ladder changed.

**Post-answer, the mode is stated** (§5.4). Mandatory here and noise everywhere else: a learner who picks `3` on a minor item and is told only "wrong, it was `♭3`" cannot tell whether they misheard the degree or misheard the key, which are different problems with different fixes.

### 8.7 Stage 2.8 findings and decisions

**`M9` was unreachable because it had no node, not because it had no route.** `M10.MIN_SET_1` declared `M9.MODE_ID_TRIAD` as its prerequisite while nothing of that name existed in `SkillGraph` — a gate pointing at nothing. `M9` now has its three nodes, carrying no active degrees (no degree is being named; the answer is a property of the whole passage) and a new `DifficultyAxis.Scope.MODE_ID` with no axes at all, which is §3's own statement that "`M9`'s three nodes *are* its progression" expressed as a type rather than as a comment. `SkillStateReducer` gained the matching branch: routing a learner to a node with no mastery path would have stranded them there forever, and since `M9` gates all of minor that would have been worse than the unreachability it replaced.

**Three screens were answering "what are you working on" separately, and had already diverged.** Home and the progress screen walked `M2` alone; the practice loop walked the whole chain. Past the major nodes, Home would have reported `M2.FULL_DIATONIC` indefinitely while sessions ran minor — two screens disagreeing about one fact, which is worse than either being wrong alone, because neither looks broken. All three now call `SkillGraph.currentNodeFor`, and the progress screen's mastery map covers the whole chain rather than stopping where the major nodes end.

**A REAL BUG the unification exposed: every `M11` node was unreachable under honest gate-checking.** `M11.CHROM_SHARP4`'s declared prerequisite is `M2.INDEPENDENCE_CHECK`, which `03-CURRICULUM.md` §5.6 calls "a separate, non-blocking assessment" — never a node a learner is routed to, therefore never mastered, therefore a gate that can never open. Gating on a non-blocking assessment is a contradiction in the spec's own terms; what the prerequisite means is that the `M2` chain is finished, and the node whose mastery *fires* the check is exactly that milestone. `gatesFor` resolves an independence check to its triggering node, leaving the declared prerequisite exactly as the spec writes it. The old practice loop walked its chain by position and ignored prerequisites entirely, so `M11` had been reachable by accident; checking gates properly is what surfaced the dangling one.

**A REAL BUG in the simulation harness itself.** It labeled attempts with `degree.toString()`, the pre-Stage-2.3 form that silently drops the alteration — so any minor, chromatic or mixed-mode simulation would have scored `♭3` and `♮3` as the same answer, and every §7 simulation below would have been measuring something other than what it claimed. Byte-identical for every Phase 1 degree, all of which carry alteration 0, so the Phase 1 simulations are provably unmoved.

**§7's seven simulations are built, each with its own positive control.** That pairing is deliberate: a negative assertion ("this learner is never certified") passes just as readily when a node is unmasterable by anyone, so each refusal sits beside a run proving the same node *is* reachable by a learner who deserves it. Simulation 4 is the one that would embarrass the app if it failed — a major-only learner masters `M2` and is refused `M10.MIXED_MODE`, which is the whole claim §3 makes for that node.

**The verification gate.** Stages 2.5 and 2.6 were reported green off `./gradlew build -q | grep -v … | tail`, whose exit status is `tail`'s and therefore always zero. Two real failures sat hidden across two commits. `scripts/verify.sh` runs the build with no pipe between the caller and Gradle's status, then re-derives both golden baselines under `--rerun-tasks`, and exits with Gradle's code.
