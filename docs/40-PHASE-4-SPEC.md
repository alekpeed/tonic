# 40 — Phase 4 Specification: Rhythm

**Reference, not instructions.** Sections marked ⚠️ rest on evidence nobody has gathered yet — mostly device measurements. Treat them as reasoned guesses, not settled design.

**Prerequisite: Phase 3 complete and stable, OR explicitly skipped.** Unlike Phases 2 and 3, rhythm is *pedagogically independent* of everything built so far — it shares no skill nodes with pitch work and could in principle ship before Phase 3. But it shares the audio engine, and Phase 3's Oboe/NDK decision (`30-PHASE-3-SPEC.md` §5.1) directly determines this phase's foundation. If Phase 3 is skipped, that decision moves here and must be made first.

---

## 1. What Phase 4 adds

Rhythm as a complete, parallel track: hearing the beat, feeling subdivision, reproducing patterns, and internalizing meter without external support.

This is the first phase since Phase 1 to add a genuinely new *interaction modality*. Every exercise to date has been: listen, then choose from a set of labeled options. Rhythm requires **timed production** — tapping a pattern back — which means the app must for the first time measure *when* something happened, not just *what* was chosen.

That single change is the source of nearly all this phase's difficulty. It was flagged as the hardest engineering problem in the project as far back as `01-PRODUCT-SPEC.md` §4, and that assessment stands.

## 2. Why rhythm is a separate track, not an extension

Rhythm shares no prerequisites with pitch. A learner can start `M3.BEAT_FIND` on day one having never touched `M2`. The original curriculum (`03-CURRICULUM.md` §4, note on M3) says exactly this: rhythm is independent and can run in parallel.

Consequences:

- `M3` nodes have no pitch prerequisites and gate nothing in the pitch track.
- Rhythm items may be interleaved into the same session as pitch items, or practiced alone. ⚠️ Whether to interleave by default is an open question — see §10.
- Rhythm has its **own** difficulty axes, its own staircase state, and its own mastery evaluation. It does not share axes with `M2`/`M10`/`M11`.
- A learner who only ever does rhythm must have a coherent, complete experience.

## 3. Pedagogy

Extends `02-PEDAGOGY.md`. Its invariants still hold where applicable — no notation required, no instrument required, no singing required, varied stimulus, faded support, plain explanation before every new task shape.

### 3.1 Takadimi, not Kodály

**Decision: Takadimi is the syllable system. Numeric counting ("1-e-and-a") is an optional alternate display. Kodály ta/ti-ti is not implemented.**

Takadimi is *beat-oriented*: the beat is always **ta**, a division is **ta-di**, a subdivision is **ta-ka-di-mi**. The syllable tells you where you are *within the beat*, so an identical-sounding pattern gets identical syllables regardless of the notated note values or the meter around it.

Kodály syllables are *note-value-oriented* (ta = quarter, ti-ti = two eighths), which means the same syllable means different metric positions in different meters, and compound meter becomes incoherent. For an app that never shows notation and teaches feel rather than reading, beat-orientation is the correct choice.

This mirrors the pitch track's logic exactly: scale degrees label *function within a key*, Takadimi labels *position within a beat*. Both are relative systems, and that consistency is deliberate.

### 3.2 The metronome fade — rhythm's cadence fade

**This is the pedagogical centerpiece and the direct analog of `CADENCE_FADE`.**

In the pitch track, harmonic support is withdrawn in graded steps so the learner must eventually hold the tonal center internally. Rhythm has exactly the same problem and takes exactly the same solution: **external timekeeping is withdrawn in graded steps so the learner must eventually hold the beat internally.**

| Level | Support provided |
|---|---|
| L0 | Metronome on every subdivision, continuous throughout |
| L1 | Metronome on every beat, continuous throughout |
| L2 | Metronome on beat 1 of each bar only, continuous |
| L3 | Full count-in (2 bars), then metronome continues under the pattern |
| L4 | Full count-in (2 bars), then metronome stops for the pattern |
| L5 | Count-in of 1 bar, then silence |
| L6 | Count-in of 2 beats only, then silence |
| L7 | Tempo stated once at block start; no count-in per item |

L4 is the critical transition — the first level where the learner must keep time unaided. L6–L7 are where genuine internal pulse is trained.

**The L2→L3 step adds support back, and that is deliberate.** Decided at Stage 4.2, after the
implementation surfaced it: L2 sounds downbeats only under the pattern, while L3 "continues" on every
beat, so a learner climbing the axis hears *more* external pulse at L3 than at L2. The ladder is
therefore not monotonic in how much sounds — it trades two things against each other. L0–L2 thin what
plays *under* the pattern; L3 restarts at beat density but bounds the metronome with a count-in; L4–L7
then shorten that count-in to nothing. Read as "how much is the learner leaning on the metronome while
producing," the sequence still only ever gets harder, because from L4 there is nothing to lean on at
all. `MetronomePlannerTest` asserts the discontinuity explicitly so that changing L3's density is a
deliberate act rather than an accident.

The same lesson from Phase 1 applies with full force: **this axis must move one level at a time.** `07-ADAPTIVE-ENGINE.md` §2a already requires per-axis step size; `METRONOME_FADE` takes `initialStepSize = 1` for the identical reason `CADENCE_FADE` does — skipping a level here doesn't make an item harder, it makes it unanswerable.

### 3.3 Perception before production

Every rhythmic concept is introduced in **recognition** form before **production** form.

- Recognition: "Which of these three patterns did you just hear?" — pure perception, tap timing irrelevant.
- Production: "Tap that pattern back." — perception plus motor execution.

Rationale, and it's the same structural argument as `30-PHASE-3-SPEC.md` §3: production conflates two skills. A learner who perceives a syncopation correctly but taps it sloppily is not failing at rhythm perception. Introducing recognition first means the app can tell those apart, and a learner is never blocked by motor execution on a concept they actually hear correctly.

### 3.4 Beat induction is a first-class skill, not a warmup

Finding the beat in music that doesn't announce it — hearing where "one" is — is a genuine, trainable, frequently-absent skill. It is not a trivial precursor to "real" rhythm work.

`M3.BEAT_FIND` and `M3.DOWNBEAT` are therefore substantial nodes with their own progression, not a single introductory screen.

### 3.5 Tempo is a difficulty axis in both directions

Unlike most axes, tempo is not monotonic. Around 90–120 BPM is easiest — near the natural spontaneous tapping rate for most people. Both faster and *slower* are harder: fast because it exceeds motor comfort, slow because long inter-beat intervals require genuine internal timekeeping rather than reactive entrainment.

The `TEMPO_DEVIATION` axis therefore measures **distance from a comfortable center**, not raw BPM.

## 4. The latency problem

This is the technical centerpiece. Everything in §3 is moot if the app cannot tell when a tap happened relative to when a sound was heard.

### 4.1 Why it's hard

Measuring "did the user tap on the beat" requires knowing two things precisely:

1. **When the sound actually reached the user's ears** — not when the app called `play()`. Between those lies audio buffering, mixing, the device's output path, and (catastrophically) any wireless transport.
2. **When the user's finger actually touched the glass** — not when the app's callback ran. Touch digitizer sampling, input dispatch, and UI thread scheduling all intervene.

Both are device-dependent, both vary by tens of milliseconds, and the *difference* between them is a systematic offset that will make every single measurement wrong in the same direction if uncorrected. A 60 ms uncorrected offset at 120 BPM is roughly an eighth of a beat — enough to score a perfectly-timed learner as consistently rushing.

### 4.2 Bluetooth is disqualifying

Bluetooth audio adds 100–300 ms of latency, varies by codec and device pair, and is not reliably queryable. At those magnitudes, rhythm production is not merely inaccurate — it is impossible to calibrate stably.

**Requirement: the app must detect Bluetooth audio output and, when active, disable production (tapping) exercises with a plain explanation, offering recognition exercises instead.** Not a warning the user can dismiss into a broken experience — an actual mode change. Wired headphones or the device speaker are required for tapping.

⚠️ Verify whether Android's reported audio latency (`AudioTrack.getTimestamp()`, `AudioManager` properties) is trustworthy enough to *measure* the offset rather than merely detect the transport. Report findings; do not assume.

### 4.3 Calibration

A required, explicit calibration step before the first production exercise, and re-runnable from Settings.

```
1. A steady metronome plays (audible, unambiguous).
2. The user taps along for a bounded number of beats.
3. Discard the first few taps (entrainment is unstable at the start).
4. Compute the median signed asynchrony — the systematic offset.
5. Also compute the spread — how consistent the user is.
6. Store the offset. Subtract it from every subsequent measurement.
```

Design requirements:

- **Median, not mean.** One distracted tap must not skew the constant.
- **Spread is diagnostic, not scored.** A large spread means either an inconsistent user or an unstable device path; either way, tolerance windows should widen rather than the user being failed.
- **Re-calibrate on output route change.** Plugging in headphones changes the offset. Detect the route change and either re-calibrate or invalidate the stored constant.
- **Calibration is per output route**, not global. Store separately for speaker and wired output. ⚠️ USB output shares the wired constant as of Stage 4.0. That grouping is an assumption, not a measurement — a USB DAC's latency is its own — and it is grouped only because inventing a third stored constant before anyone has measured a USB device would be adding schema on a guess. Confirming or splitting it needs a device.
- ⚠️ **Sanity bounds.** An implausible measured offset (negative beyond a threshold, or larger than a beat) means calibration failed — re-prompt rather than storing garbage.

### 4.4 Determinism under real time

`CLAUDE.md` §5 requires deterministic generation, and Phase 1's determinism race (`07-ADAPTIVE-ENGINE.md` history) showed how easily that's lost. Rhythm introduces genuinely non-deterministic input — real human tap times.

The rule that preserves testability:

- **Item generation stays fully deterministic.** Which pattern, what tempo, what metronome level — all pure functions of `(skill, axes, seed)`, exactly as now.
- **Scoring is a pure function of `(pattern, tapTimestamps, calibrationOffset, tolerance)`.** No clock reads inside scoring, no ambient state. Given the same recorded taps, scoring must produce byte-identical results forever.
- **Tap timestamps are captured at the input layer and passed in as data.** They are recorded with the attempt, which makes any real session fully replayable and any scoring bug reproducible offline.

This is the same pattern as Phase 1's seed-recording, extended to timing.

## 5. Curriculum

Extends `03-CURRICULUM.md`. Module `M3`, using the identifiers reserved in Phase 1 plus additions.

### 5.1 Skill nodes

| SkillId | Task | Mode | Prerequisite |
|---|---|---|---|
| `M3.BEAT_FIND` | Tap along with a steady pulse | Production | none |
| `M3.DOWNBEAT` | Identify which beat is "one" | Recognition | `M3.BEAT_FIND` |
| `M3.BEAT_DIV_RECOG` | Distinguish beat / division patterns | Recognition | `M3.DOWNBEAT` |
| `M3.BEAT_DIV` | Tap back beat and division patterns (ta, ta-di) | Production | `M3.BEAT_DIV_RECOG` |
| `M3.SUBDIV_RECOG` | Distinguish subdivision patterns | Recognition | `M3.BEAT_DIV` |
| `M3.SUBDIV` | Tap back subdivisions (ta-ka-di-mi) | Production | `M3.SUBDIV_RECOG` |
| `M3.RESTS` | Patterns containing silence | Production | `M3.SUBDIV` |
| `M3.SYNCOPATION_RECOG` | Recognize off-beat emphasis | Recognition | `M3.RESTS` |
| `M3.SYNCOPATION` | Tap back syncopated patterns | Production | `M3.SYNCOPATION_RECOG` |
| `M3.COMPOUND` | Compound meter (beat divides in three) | Both | `M3.SUBDIV` |
| `M3.METER_CHANGE` | Meter changes mid-pattern | Both | `M3.COMPOUND` + `M3.SYNCOPATION` |
| `M3.INDEPENDENCE_CHECK` | 30 production items at `METRONOME_FADE` L6 | Production | `M3.METER_CHANGE` |

Note the recognition/production alternation, per §3.3. Each concept is heard before it is produced.

### 5.2 Difficulty axes

| Axis | Levels | Step | Meaning |
|---|---|---|---|
| `METRONOME_FADE` | 0–7 | **1** | External timekeeping withdrawn. §3.2 |
| `PATTERN_LENGTH` | 0–4 | 2 | 1 bar → 4 bars |
| `TEMPO_DEVIATION` | 0–3 | 2 | Distance from ~100 BPM, both directions (§3.5) |
| `RHYTHMIC_DENSITY` | 0–3 | 2 | Proportion of subdivided vs. plain beats |
| `TIMING_TOLERANCE` | 0–3 | 2 | How tight the accuracy window is (§6.2) |
| `TIMBRE_VARIETY` | 0–3 | 2 | Reused from the pitch track — same generalization argument |

`METRONOME_FADE` takes step size 1 for the reason in §3.2. All others follow the default of 2 per `07-ADAPTIVE-ENGINE.md` §2a.

Axis scheduler priority: `METRONOME_FADE` first (pedagogically critical, and the mastery criterion depends on it), then `TIMING_TOLERANCE`, then `RHYTHMIC_DENSITY`, `PATTERN_LENGTH`, `TEMPO_DEVIATION`, `TIMBRE_VARIETY`.

### 5.3 Mastery criteria

Recognition nodes use the existing five criteria from `03-CURRICULUM.md` §5.5 unchanged.

Production nodes replace the per-degree criteria with rhythm equivalents. All must hold over a rolling 30:

1. Overall pattern accuracy ≥ 90% (right notes in the right places).
2. Timing consistency within the current tolerance on ≥ 85% of correct patterns.
3. No systematic drift — the learner is not progressively rushing or dragging across a pattern. This is measured as trend across the pattern, not as raw asynchrony, because a constant offset is a calibration artifact while a growing offset is a real timekeeping failure.
4. `METRONOME_FADE` ≥ 4 — the learner has produced correctly with no metronome under the pattern.
5. No single rhythmic figure below 80% accuracy.

Criterion 4 is the analog of `CADENCE_FADE ≥ 4` and matters for the same reason: without it, a learner "masters" rhythm while never having kept time unaided.

**Implementation notes, added at Stage 4.5 when these were built.** Two of the five needed a decision the wording above does not settle, and both are recorded here rather than left in the code.

*Criterion 2, "timing consistency within the current tolerance on ≥ 85% of correct patterns."* Read word for word this is vacuous: a pattern is only *correct* once every tap has already landed inside the tolerance, so the share of correct patterns whose taps are inside the tolerance is always 100%. Two readings could be meant, and §6 rules one of them out. A spread or variance statistic over the asynchronies would be the raw magnitude §6.3 forbids from entering mastery, dressed differently, and it would contradict §6.1's "gating only via `TIMING_TOLERANCE`". What remains — and what is implemented — is that criterion 1 measures the share of *events* struck and criterion 2 the share of *patterns* struck whole at the window the learner currently faces. The gating §6.1 describes then falls out on its own: raising `TIMING_TOLERANCE` narrows the window, and the same performance stops counting as a whole pattern.

*Criterion 3's threshold.* Drift is held as a slope in fractions of a beat per beat, so it means the same thing at every tempo and pattern length, and the window's **median** is taken, signed — "systematic" is the operative word, and a learner who rushes one pattern and drags the next is inconsistent rather than drifting, which criteria 1 and 2 already charge for when it costs them the window. The bar is **0.02**, ⚠️ reasoned and not measured. It is set from the band where the criterion can do any work at all: drift steep enough to leave the tolerance window already fails criteria 1 and 2, so what is left to catch is a trend that stays *inside* it — a learner starting half a window early and ending half a window late, every tap inside, nothing scored wrong, and plainly not holding tempo. Over the sixteen beats `PATTERN_LENGTH` L4 produces in common time, at `TIMING_TOLERANCE` L0's quarter-beat window, that learner's slope is 0.033; 0.02 sits below it. On a one-bar pattern the same slope accumulates 6% of a beat, so it does not fail someone whose short pattern merely leans. Device data on the drift real learners produce unaided is what would move it.

*Not added: a coverage floor for production figures.* The recognition criteria require ≥ 5 attempts per figure before judging one; §5.3's production list has no equivalent and none was invented. A figure is a *beat's* fill, so a 30-pattern window carries hundreds of figure instances rather than thirty, and the sample problem coverage exists to solve does not arise at that scale.

### 5.4 The independence check

`M3.INDEPENDENCE_CHECK` runs at `METRONOME_FADE` L6 — a two-beat count-in, then silence. Passing means the learner produced multi-bar patterns accurately with essentially no external pulse. That is the actual goal of the module.

## 6. Scoring

### 6.1 What "correct" means

A production attempt is scored on two independent dimensions:

- **Pattern accuracy** — did the right number of events occur in the right metric positions? This is the primary score and the one that gates mastery.
- **Timing precision** — how tightly did they land? Secondary, informational at low levels, gating only via `TIMING_TOLERANCE` at higher ones.

Separating these is essential. A learner who taps the correct rhythm slightly loosely is *right, imprecisely*. A learner who taps a different rhythm precisely is simply wrong. Collapsing both into one number loses the distinction the app most needs.

### 6.2 Tolerance windows

Each expected event has a window around it. A tap inside the window matches that event.

- Windows are expressed as a **fraction of the beat**, not fixed milliseconds — 50 ms is generous at 60 BPM and impossible at 200 BPM.
- `TIMING_TOLERANCE` level 0 is deliberately forgiving. The early goal is "did you feel the pattern," not "are you a session drummer."
- Windows must never overlap. At high densities and tight tempi, adjacent events can crowd; when windows would overlap, the tolerance is clamped rather than allowing one tap to match two events.
- Extra taps (more than expected) and missing taps are both errors, and are recorded distinctly — they mean different things pedagogically.

### 6.3 What is recorded and what is scored

Recorded on every attempt: full tap timestamp list, calibration offset used, tolerance applied, per-event asynchrony.

**Scored:** pattern accuracy, and timing only via the tolerance window.

**Never scored:** raw asynchrony magnitude. It's shown to the user as feedback ("you were a little ahead") and used for drift analysis, but it does not enter the staircase or mastery evaluation directly. Same rule and same rationale as `replayCount` in Phase 1 and `sungCents` in Phase 3 — and unlike those two, this one needs a real test written, since Phase 3 §7's cited precedent was found not to exist.

## 7. UI

Extends `08-UI-SPEC.md` in full — including §2a (every screen has a way out) and `11-ONBOARDING-CLARITY.md` (explanation plus worked example before any new task shape, stage labeling, no silent difficulty changes).

### 7.1 What rhythm needs explaining about

Rhythm introduces the app's first genuinely new interaction since Phase 1. Only calibration has an
explanation screen so far — the others are unbuilt (§7.6). What each would need to get across:

- Recognition items — what you're listening for, what the choices mean.
- Production items — that you'll tap, where you tap, what the count-in means, that approximate is fine at first.
- Calibration — why it exists, in plain terms ("your phone has a small delay; this measures it"). Built.

A production worked example only works if it *demonstrates* a tapped answer — the pattern, the taps
landing, the result. A verbal description of tapping does not convey it.

### 7.2 The tap surface

- One large tap target, not a small button. Comfortable for repeated tapping.
- Immediate visual and haptic response on every tap, independent of scoring. The user must feel the app register the touch instantly, even if scoring happens later.
- ⚠️ **Audible tap feedback** (a click on each tap) is genuinely double-edged: it helps the user hear their own timing against the metronome, but it also adds output latency to their own feedback loop and can mask the pattern. **Default: haptic and visual yes, audible no**, with an optional toggle. Revisit with real testing.

### 7.3 Showing the beat

At low `METRONOME_FADE` levels, a visual pulse may accompany the audible metronome. But it must **fade on the same schedule as the audio** — a visual metronome that persists after the audio stops defeats the entire point of the fade axis. This is precisely the "crutch that is silently re-supplied" failure that Phase 1 hit with the cadence.

### 7.4 Feedback after a production attempt

- Whether the pattern was right.
- A simple visual of where taps landed relative to where events were — this is the most instructive feedback in the whole module, and worth designing properly rather than reducing to a percentage.
- Plain-language timing note ("slightly ahead of the beat"), framed neutrally.
- Never a precision grade or score.

### 7.5 Accessibility

Motor impairment affects tapping in a way it doesn't affect choosing from a list. Therefore: **recognition nodes must form a complete, coherent path through every rhythmic concept.** A user who cannot tap accurately must still be able to learn and demonstrate rhythmic understanding through recognition alone, even though they cannot complete production nodes or `M3.INDEPENDENCE_CHECK`. Document this explicitly rather than leaving it emergent.

### 7.6 What is not built

Rhythm is reachable, playable and scored. These parts of §7 are unbuilt, and none of them is declined
— they are simply not there.

| Gap | Section | Consequence |
|---|---|---|
| Explanation and worked example for tapping | §7.1 | A learner meets the tap surface with no introduction. Calibration has one; recognition and production do not |
| Visual pulse | §7.3 | No visual beat at low fade levels. Nothing is *wrong* — the failure §7.3 warns about is a pulse that outlives the audio, and there is no pulse — but the support it describes is absent |
| Recognition-only path, written down | §7.5 | The recognition nodes do not form an independently traversable path: `M3.SUBDIV_RECOG` gates on `M3.BEAT_DIV`, which is tapped. Closing it means changing prerequisites, not adding nodes |

Two design notes on what *is* built, both deliberate deviations worth not rediscovering:

**Calibration measures against scheduled instants, not heard ones.** §4.3 asks for the instants each
beat was heard, from the output timebase. The constant exists to cancel a systematic error;
`PracticeViewModel` scores taps against the scheduled start of a pattern, so the error it makes
contains output latency, and a constant measured against heard beats would omit that term and
under-correct by exactly it. The version §4.3 describes is better and waits on §10 q2 — whether
Android's reported output timing can be trusted at all. Both sides move together when it is answered.

**Where taps landed is drawn as one cell per expected sound**, with the beat down the middle and the
tolerance window as the cell's width. A single timeline was tried first and does not work: a tap 20 ms
off in a pattern lasting 2.4 seconds is under one percent of the width, so every mark sits on its
event and the picture says "perfect" to everyone. Underneath it, one sentence naming a direction and
never a number — §6.3's sanctioned showing of raw asynchrony, and §7.4's "never a precision grade".

## 8. Data model and engine

Extends `05-DATA-MODEL.md` and `07-ADAPTIVE-ENGINE.md`.

- New `Item` subtype: `RhythmItem` (pattern, tempo, meter, metronome plan, expected event times).
- `Attempt` gains rhythm fields: `tapTimestamps`, `calibrationOffsetUsed`, `toleranceUsed`, `perEventAsynchrony`, `extraTaps`, `missedTaps`.
- New settings: `rhythm_calibration_offset_speaker`, `rhythm_calibration_offset_wired`, `rhythm_audible_tap_feedback` (default off), `rhythm_visual_pulse` (default on). As built at Stage 4.1 the two calibration keys are six: each slot stores its offset, its spread and its tap count, because §4.3 step 5 measures a spread and then requires it to widen tolerance windows, which a discarded number cannot do. `05-DATA-MODEL.md` §3 carries the reasoning. `rhythm_audible_tap_feedback` is built; `rhythm_visual_pulse` is not (§7.6).
- Six new difficulty axes (§5.2), registered as rhythm-specific — the axis scheduler already handles skill-specific axes as of Phase 2's Stage 2.0.
- Confusion tracking adapts: the "confusion matrix" for rhythm is over *rhythmic figures*, not labels — which patterns get mistaken for which. Same remediation weighting logic applies.

**Engine work is otherwise reused.** Staircase, axis scheduler, mastery evaluator, FSRS, session composer all apply unchanged. As with Phase 2, if this phase tempts a change to any of those, that signals something is being modeled wrong — stop and ask.

## 10. Open questions

1. **Oboe/NDK vs. `AudioTrack`** — inherited from Phase 3 if unanswered there. Decide with measurements on real hardware. ⚠️ **Still open, and deliberately so.** At Stage 4.0 the maintainer instructed that `AudioTrack` stays for now and that the backend-agnostic parts of the stage be built rather than the phase stalling on a measurement the development sandbox cannot produce. That is a decision to defer, not an answer to this question: nothing has been measured. `06-AUDIO-ENGINE.md` §1 records it and states what would reverse it. Settling it needs a device.
2. **Whether Android's reported output latency is trustworthy** (§4.2) or whether calibration must derive everything empirically.
3. **Interleaving rhythm with pitch in one session** (§2) — pedagogically attractive (variety, and the contextual-interference literature favors it), but it doubles the explanation burden per session and risks the app feeling scattered. ⚠️ Decide with real usage evidence from Phase 2/3.
4. **Audible tap feedback default** (§7.2).
5. **Whether `M3.BEAT_FIND` should be reachable from the very first launch**, as a genuine alternative entry point to `M0`/`M2` for a user who bounces off pitch work. Tempting — rhythm is more immediately accessible for many beginners — but it complicates onboarding and placement. ⚠️ Consider only after Phase 1's on-ramp difficulty is resolved.
