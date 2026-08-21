# 03 — Curriculum and Skill Graph

Phase 1 skills are fully specified here and must be implemented exactly. Future modules are listed with reserved identifiers only — define the constants so the schema is stable, implement nothing.

---

## 1. Identifier scheme

```
SkillId  ::=  "M" <module> "." <skill> [ "." <variant> ]
```

Examples: `M0.PITCH_DIR`, `M2.DEG_SET_1`, `M2.FULL_DIATONIC`.

Identifiers are permanent. Once shipped, a `SkillId` string is never reused for a different meaning. Deprecate, do not recycle.

## 2. Reserved module IDs

| ID | Name | Phase | Status |
|---|---|---|---|
| M0 | Diagnostic and Placement | 1 | **Build** |
| M1 | Pitch Primitives | 1 | **Build** (as M0 remediation path) |
| M2 | Diatonic Functional Recognition | 1 | **Build** |
| M3 | Rhythm | 4 | Reserve only |
| M4 | Melodic Dictation | 5 | Reserve only |
| M5 | Harmony | 6 | Reserve only |
| M6 | Harmonic Dictation and Polyphony | 6 | Reserve only |
| M8 | Advanced / Chromatic / Modal | 8 | Reserve only — but see the note below |

---

## 3. Module 0 — Diagnostic and Placement

Purpose: place the user on the difficulty axes of M2, and detect indicators warranting the extended discrimination path. Target duration under 6 minutes. Adaptive length: it terminates early once estimates stabilize.

### M0.PITCH_DIR — Pitch direction

Two tones, same timbre, sequential. "Was the second note higher or lower?"

- Answer alphabet: `{HIGHER, LOWER}`
- Adaptive on interval size: start 12 semitones, converge via 2-down/1-up staircase down to a floor of 1 semitone, then to sub-semitone cent differences (100, 75, 50, 30, 20, 10 cents) if the user is performing at ceiling.
- Terminate after staircase convergence (see `07-ADAPTIVE-ENGINE.md` §2) or 24 items, whichever first.
- Output: `pitchDirectionThresholdCents`.

### M0.SAME_DIFF — Same or different

Two tones, sometimes identical, sometimes differing by a small amount. "Same or different?"

- Answer alphabet: `{SAME, DIFFERENT}`
- 50% catch trials with truly identical tones. Track false-alarm rate separately from miss rate; a user who answers "different" indiscriminately must not be scored as sensitive. Use d-prime, not raw accuracy.
- Output: `discriminationDPrime`.

### M0.TONAL_MEMORY — Short tonal memory span

A short sequence of 2–5 tones plays, then replays with exactly one note possibly altered. "Was the second version the same or different?"

- Adaptive on sequence length.
- Output: `tonalMemorySpan` (integer, 2–5+).

### M0.AMUSIA_SCREEN — Distorted-tunes style screen

A short, app-composed melodic phrase (original material, synthesized, no third-party tunes) plays either intact or with one pitch displaced out of key. "Did that sound right, or did something sound off?"

- 16 items, half intact, half altered. Alteration magnitude fixed and clearly supra-threshold for a typical listener (displacement of 1–3 semitones out of the established key).
- Scored by d-prime.
- Output: `amusiaIndicatorFlag: Boolean` — **internal only**. See `02-PEDAGOGY.md` §8 for absolute constraints on how this may and may not be surfaced.
- Threshold: flag if d-prime falls below the configured cut and the pitch-direction threshold is also elevated (both conditions, not either). Two independent weak signals reduce false positives, which matter enormously here.

### M0 output contract

```kotlin
data class DiagnosticResult(
    val pitchDirectionThresholdCents: Int,
    val discriminationDPrime: Double,
    val tonalMemorySpan: Int,
    val amusiaIndicatorFlag: Boolean,
    val recommendedEntry: EntryPoint,   // M1_REMEDIATION or M2_STAGE_1
    val initialAxisLevels: Map<DifficultyAxis, Int>,
    val completedAt: Instant,
    val seed: Long
)
```

Placement rules:

- `amusiaIndicatorFlag == true` **or** `pitchDirectionThresholdCents > 200` → `M1_REMEDIATION`.
- otherwise → `M2_STAGE_1`, with `initialAxisLevels` derived from the measured values per the table in `07-ADAPTIVE-ENGINE.md` §5.

---

## 4. Module 1 — Pitch Primitives (remediation path)

Only entered from M0 placement. Purpose: build discrimination to the point where functional work is viable. Wide differences, slow progression, generous mastery windows.

| SkillId | Task | Answer alphabet | Exit criterion |
|---|---|---|---|
| `M1.HIGH_LOW` | Which of two tones is higher | `{HIGHER, LOWER}` | 90% over 20 items at ≤200 cents |
| `M1.SAME_DIFF` | Same or different | `{SAME, DIFFERENT}` | d-prime ≥ 2.0 at ≤100 cents |
| `M1.CONTOUR` | Did a 3-note figure rise, fall, or turn | `{UP, DOWN, UP_DOWN, DOWN_UP}` | 85% over 20 items |
| `M1.STEP_LEAP` | Was that a small move or a big move | `{STEP, LEAP}` | 85% over 20 items |

On completion, re-run `M0.AMUSIA_SCREEN` silently. Clear the flag if the screen now passes. The user is told they are progressing, not that a flag cleared.

---

## 5. Module 2 — Diatonic Functional Recognition (core)

### 5.1 The item

1. Establish the key according to the current cadence-fade level (`02-PEDAGOGY.md` §3).
2. Play one target note drawn from the active degree set, in a randomized key, timbre, and register per current axis levels.
3. User selects a scale degree from the degree ladder.
4. Feedback per `02-PEDAGOGY.md` §6.

### 5.2 Skill nodes

| SkillId | Active degree set | Prerequisite |
|---|---|---|
| `M2.DEG_SET_1` | `{1, 3, 5}` | M0 placement or M1 exit |
| `M2.DEG_SET_2` | `{1, 2, 3, 5}` | `M2.DEG_SET_1` mastered |
| `M2.DEG_SET_3` | `{1, 2, 3, 5, 6}` | `M2.DEG_SET_2` mastered |
| `M2.DEG_SET_4` | `{1, 2, 3, 4, 5, 6}` | `M2.DEG_SET_3` mastered |
| `M2.FULL_DIATONIC` | `{1, 2, 3, 4, 5, 6, 7}` | `M2.DEG_SET_4` mastered |

Each node carries its own independent set of axis levels and its own FSRS state. Mastering a node does not reset the next node's axes to zero: inherit the previous node's levels, reduced by one step on the cadence-fade axis to absorb the added difficulty.

### 5.3 Difficulty axes

Six independent axes. Only one axis moves at a time; see the axis scheduler in `07-ADAPTIVE-ENGINE.md` §3.

| Axis | Levels | Meaning |
|---|---|---|
| `CADENCE_FADE` | 0–7 | Harmonic reference strength. Table in `02-PEDAGOGY.md` §3 |
| `TIMBRE_VARIETY` | 0–4 | 0 = one fixed timbre; 4 = all families, randomized per item, reference and target may differ |
| `REGISTER_SPREAD` | 0–3 | Range from which the target pitch is drawn, in octaves around the reference |
| `OCTAVE_DISPLACE` | 0–2 | 0 = target within reference octave; 1 = ±1 octave; 2 = ±2 octaves |
| `TEMPO_DENSITY` | 0–3 | Duration of reference and target, and gap length. Faster/shorter is harder |
| `KEY_SPREAD` | 0–2 | 0 = key drawn from 3 keys; 1 = 7 keys; 2 = all 12 |

`KEY_SPREAD` never sits at a state where a single key repeats across consecutive items more than twice — enforce in the generator.

### 5.4 Item generator contract

```kotlin
fun generateM2Item(
    skill: SkillId,
    axes: Map<DifficultyAxis, Int>,
    seed: Long
): FunctionalRecognitionItem
```

Must be pure. Must be deterministic. Must not read the clock or global state.

```kotlin
data class FunctionalRecognitionItem(
    val skill: SkillId,
    val key: PitchClass,
    val mode: Mode,                 // MAJOR only in Phase 1
    val targetDegree: ScaleDegree,
    val targetMidi: Int,
    val referencePlan: ReferencePlan,   // derived from CADENCE_FADE level
    val timbre: TimbreId,
    val referenceTimbre: TimbreId,
    val timing: ItemTiming,
    val answerAlphabet: List<ScaleDegree>,
    val seed: Long
)
```

Generator constraints:

- The target degree is drawn from the active set with **balanced frequency** over a rolling window — no degree may appear more than 1.5× the expected rate over any 20-item span. Naive uniform sampling produces clumps that distort the confusion matrix.
- Degrees the confusion matrix flags as weak are oversampled at a controlled rate. See `07-ADAPTIVE-ENGINE.md` §4.
- Never emit the same (key, degree, octave) triple twice in a row.

### 5.5 Mastery criteria

A node is mastered when **all** hold, evaluated over a rolling window of the last 30 attempts at the node's current axis levels:

1. Overall accuracy ≥ 90%.
2. Every degree in the active set has ≥ 5 attempts in the window.
3. No individual degree is below 80% accuracy.
4. No single confusion pair (target X answered as Y) accounts for more than 15% of total attempts in the window.
5. `CADENCE_FADE` level ≥ 4.

Criterion 5 is the one that matters. Without it a user can "master" a node while remaining entirely dependent on the cadence crutch.

### 5.6 Graduation to functional independence

A separate, non-blocking assessment: `M2.INDEPENDENCE_CHECK`. Runs automatically once `M2.FULL_DIATONIC` is mastered. 30 items at `CADENCE_FADE` L6, all axes at the user's current level otherwise. Passing is ≥85%, which is success criterion 6 in `01-PRODUCT-SPEC.md`. Failure is not punitive: it lowers the fade axis and schedules more work.

---

## 6. Reserved future skill identifiers

Define these as constants now for schema stability. Do not implement.

```
M3.BEAT_FIND, M3.BEAT_DIV, M3.SUBDIV, M3.RESTS, M3.SYNCOPATION,
M3.COMPOUND, M3.METER_CHANGE
M4.FRAG_2, M4.FRAG_3, M4.PHRASE_SHORT, M4.PHRASE_FULL, M4.RHYTHM_FIRST
M5.QUALITY_MAJ_MIN, M5.QUALITY_EXT, M5.BASS_DEGREE, M5.FUNCTION_IVV,
M5.FUNCTION_DIATONIC, M5.INVERSIONS, M5.VOICE_LEADING
M6.BASS_SOPRANO, M6.TWO_VOICE, M6.FOUR_VOICE
M8.MINOR_MODE, M8.CHROMATIC_DEGREES, M8.MODAL, M8.EXTENDED_HARMONY
```

Note: `M8.MINOR_MODE` — minor is deliberately not in Phase 1. Minor introduces mode ambiguity (natural/harmonic/melodic) that complicates degree labeling and would double the Phase 1 surface for no pedagogical gain at the beginner stage.
