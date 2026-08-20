# 07 — Adaptive Engine

Five cooperating pieces, all pure functions over state, all in `:core:engine`.

1. `Staircase` — sets difficulty within an axis
2. `AxisScheduler` — decides which axis to move
3. `ConfusionTracker` — finds specific weaknesses and drives remediation
4. `MasteryEvaluator` — decides when a node is done
5. `ReviewScheduler` (FSRS) — decides when to revisit a mastered node
6. `SessionComposer` — assembles the actual practice session

---

## 1. Design stance

Two different problems are being solved and they need different mechanisms:

- **Within a skill, how hard should the next item be?** This is a psychophysical threshold problem. Solution: adaptive staircase.
- **When should a learned skill be revisited so it isn't lost?** This is a retention problem. Solution: spaced repetition.

Conflating them produces a system that is good at neither. Keep them separate.

---

## 2. Staircase

**Transformed up-down, 2-down/1-up.** Two consecutive correct responses increase difficulty by one step; one incorrect response decreases it by one step. This converges on approximately 70.7% correct performance — the standard target in auditory perceptual learning and a reasonable operating point for "desirable difficulty": hard enough to drive learning, not so hard the user quits.

```kotlin
data class StaircaseState(
    val level: Int,
    val consecutiveCorrect: Int,
    val reversals: List<Int>,       // levels at which direction flipped
    val stepSize: Int,
    val lastDirection: Direction
)

fun update(state: StaircaseState, correct: Boolean, bounds: IntRange): StaircaseState
```

### 2a. Step size is per-axis, not global (correction — supersedes any single global constant)

**Bug on record, found through live use:** an implementation used one global `INITIAL_STEP_SIZE = 2` for every axis, including `CADENCE_FADE`. Two consecutive correct answers — reachable within the first handful of items — jumped a first-time user from L0 (full four-chord cadence) straight to L2 (V–I only, two chords), skipping L1 entirely. This is not merely a pacing complaint: it was independently confirmed on real rendered audio that at L2 the two chords are functionally indistinguishable to someone who hasn't yet built the perceptual skill the exercise is meant to teach — same RMS loudness (measured ratio 0.992), same timbre, same duration, no gap between them. The only thing separating them is harmonic resolution, which is the very thing L2 assumes the user can already do. Vaulting a beginner there after two correct guesses strands them with no way to answer correctly except chance.

**Requirement: `stepSize` is configured per axis, not globally.**

| Axis | Initial step size | Rationale |
|---|---|---|
| `CADENCE_FADE` | **1** | Every level must be genuinely passed through, not skipped. This axis directly controls whether the user has any means at all of identifying the answer — see the L2 finding above. A skipped level here is not a minor pacing issue, it's a removed rung. |
| All other axes (`TIMBRE_VARIETY`, `REGISTER_SPREAD`, `OCTAVE_DISPLACE`, `TEMPO_DENSITY`, `KEY_SPREAD`) | 2, as originally specified | These axes change difficulty without removing the user's means of answering at all. Skipping a level here degrades performance; it doesn't strand the user. Faster initial convergence remains appropriate. |

Step-size halving after the second reversal (§2 as written) still applies per axis on top of this — the correction is only to the *initial* value for `CADENCE_FADE`.

**Also on record from the same investigation, must be fixed alongside this:**

- **L1 currently does not exist in practice.** `ReferencePlanBuilder` sets `reusableForItems` on the L1 plan (intended per §3's table below: "full cadence, then 2–3 items answered before it repeats"), but nothing in the production path reads that field — every item rebuilds its reference plan fresh from the current axis level, so L1 renders identically to L0. With `CADENCE_FADE` step size corrected to 1, the user will now dwell at L1 rather than skip it, so this can no longer be left unimplemented — either `reusableForItems` must actually be honored (the plan persists across the specified number of items before regenerating), or L1 must be redefined as a level that doesn't depend on cross-item reuse to be distinct from L0. Resolve this before shipping the step-size fix; a corrected step size that dwells at a non-functional level is not an improvement.
- **Warmup items must not silently change the cadence level mid-session.** The current warmup behavior (`07 §8`: first 5 items of a session run one level easier) reduces `CADENCE_FADE` for those items specifically and unconditionally, independent of the staircase. A user can hear a sudden chord-count change at item 6 with no relationship to anything they just did. This must be announced the same way any other axis-level change is announced (see `11-ONBOARDING-CLARITY.md` §9.3) — the warmup-to-normal transition at item 6 is itself a level change and is not exempt from the announcement requirement just because it's schedule-driven rather than performance-driven.

Rules:

- Step size starts at 2 for fast initial convergence, halves to 1 after the second reversal. Never below 1.
- Level is clamped to the axis bounds from `03-CURRICULUM.md` §5.3.
- Convergence is declared after 6 reversals; the estimated threshold is the mean of the last 4 reversal levels. `M0` uses this to terminate a diagnostic sub-test early.
- Abandoned attempts (audio interruption, session kill) do not update the staircase.

For `M0.SAME_DIFF` and `M0.AMUSIA_SCREEN`, do **not** use raw accuracy — these have a yes/no response format where a biased responder inflates accuracy. Use **d-prime** with a log-linear correction for extreme rates:

```
hitRate = (hits + 0.5) / (signalTrials + 1)
faRate  = (falseAlarms + 0.5) / (noiseTrials + 1)
dPrime  = z(hitRate) - z(faRate)
```

Implement the inverse normal CDF (`z`) directly; do not add a statistics dependency for one function.

---

## 3. Axis scheduler

Six axes move independently, but **only one axis moves at a time.** Moving several at once makes it impossible to know which change caused a performance drop, and it stacks difficulty faster than the learner can absorb.

Priority order for M2 (the axis at the top is advanced first when the user is succeeding):

1. `CADENCE_FADE` — the pedagogically critical one, and the mastery criterion depends on it
2. `TIMBRE_VARIETY` — generalization, needed early to prevent overfitting to one sound
3. `KEY_SPREAD` — prevents accidental absolute-pitch shortcutting
4. `OCTAVE_DISPLACE` — function-across-octave generalization
5. `REGISTER_SPREAD`
6. `TEMPO_DENSITY` — least important; slow is fine

Algorithm:

```
if activeAxis == null:
    activeAxis = highest-priority axis not yet at max
run staircase on activeAxis only
if activeAxis staircase has converged (6 reversals) OR reconfirms max with two consecutive correct after already having arrived there:
    freeze activeAxis at its converged level
    activeAxis = next axis by priority not yet frozen/maxed
    reset staircase state for the new activeAxis
if the user's accuracy drops below 60% over 15 items:
    step the activeAxis down 2 levels and clear its reversal history
```

The last rule is a safety valve. A user who is drowning must be rescued regardless of what the staircase thinks.

**Arriving at max vs. reconfirming it.** The first trial that lands an axis on its max level does *not* freeze it, and neither does a single correct answer afterwards. Freezing an axis at max means freezing it forever: `pickNextAxis` excludes maxed axes from both the fresh pick and the maintenance pass, so a maxed axis is never revisited and the safety valve — which only ever touches the *active* axis — can never rescue the learner from it. The evidence bar therefore has to be higher than one answer.

Two things are required before an axis freezes at max: the level must have already been sitting at max *before* the triggering trial, and that trial must complete a genuine upward move — two consecutive correct answers, clamped by the ceiling. A single correct answer is not enough. The staircase does not move the level on a first correct response, so "level is at max" is trivially still true after one lucky answer; treating that as confirmation is a coin flip, not evidence. For a learner performing at chance on a 7-degree set it is a 1-in-7 shot per trial at being permanently pinned to `CADENCE_FADE` 7 — no reference at all, the most information-free level on the axis, and precisely the stranding §2a exists to prevent. Requiring the pair makes it (1/7)² for that learner while remaining trivial for one who genuinely belongs at max.

A miss at any point in that sequence steps back down immediately, same as any other staircase step, revealing the cliff instead of locking it in. Six real reversals still freeze immediately regardless, unaffected by this rule.

This was a latent bug independent of step size, found in live use and confirmed by simulation: with §2a's corrected `CADENCE_FADE` step size of 1 the learner visits level 6 far more often than at step 2, which exposed it — 1621 of 1800 simulated items stranded at level 7, mastery never reached. With the rule corrected, the same learner settles at a level they can actually sustain.

**Revisiting frozen axes:** after all axes are frozen, run a maintenance pass — the scheduler unfreezes the highest-priority non-max axis and resumes. Progression is a loop, not a single sweep.

---

## 4. Confusion tracking and remediation

Maintain a `(target, response)` count matrix per skill, over a rolling window of the last 100 attempts.

Derived signals:

- **Weak degree:** accuracy for a specific target below 80%.
- **Confusion pair:** a specific (target → response) cell exceeding 10% of that target's attempts.

Remediation, applied to item generation weights:

```
baseWeight = 1.0 for every degree in the active set
weight(d) *= 1 + 2.0 * max(0, 0.80 - accuracy(d)) / 0.80     // up to 3x
cap: no degree exceeds 2.5x the lowest weight
```

The cap matters. Unbounded oversampling of a weak degree turns the session into a single-degree grind, which is both demoralizing and pedagogically wrong — the skill is discriminating among degrees, and that requires them to co-occur.

**Contrast injection:** when a confusion pair (X→Y) is flagged, the generator schedules deliberate near-adjacent presentations of X and Y within the same block (not back-to-back — separated by 2–4 items). Discrimination is trained by controlled contrast, not by repetition of one member.

Common expected pairs, worth logging explicitly: 4↔3, 7↔1, 2↔1, 6↔5. These are the tendency tones resolving to their targets and are the predictable difficulty.

---

## 5. Placement mapping (M0 → M2 initial axis levels)

| Measured | Value | `CADENCE_FADE` start | `TIMBRE_VARIETY` start | `KEY_SPREAD` start |
|---|---|---|---|---|
| `pitchDirectionThresholdCents` | > 200 | → M1 remediation | — | — |
| | 100–200 | 0 | 0 | 0 |
| | 50–99 | 0 | 1 | 0 |
| | 20–49 | 1 | 1 | 1 |
| | < 20 | 1 | 2 | 1 |

`tonalMemorySpan` modifies `TEMPO_DENSITY` start (span ≥4 → level 1). All other axes start at 0.

Placement is a starting guess, not a verdict. The staircase corrects it within roughly 15 items either way.

---

## 6. Review scheduling (FSRS)

**Use FSRS, not SM-2.** FSRS is the current open-spaced-repetition standard, has been Anki's default since v23.10 (2023), and in the open FSRS benchmark predicts recall more accurately than SM-2 in the overwhelming majority of collections, typically requiring 20–30% fewer reviews for the same retention.

Check whether a maintained Kotlin/JVM FSRS implementation exists before writing one (`open-spaced-repetition` publishes ports in several languages). **Verify what actually exists rather than assuming** — if no suitable JVM port is available or maintained, implement FSRS-6 from the published algorithm specification with default parameters. Do not implement SM-2 as a shortcut.

### The critical adaptation

FSRS was designed for declarative flashcards. Ear training is a perceptual/procedural skill. The mapping:

- A **card** is a *skill node at its mastery configuration* — e.g. `M2.DEG_SET_2` — not an individual note or item.
- A **review** is a **probe block** of 10 items on that skill at its mastered axis levels.
- The **grade** is derived from block accuracy:

| Block accuracy | FSRS grade |
|---|---|
| < 60% | `AGAIN` (1) — and demote the node to `IN_PROGRESS`, resume active work |
| 60–79% | `HARD` (2) |
| 80–92% | `GOOD` (3) |
| > 92% | `EASY` (4) |

- Only **mastered** nodes are scheduled for review. Nodes in progress are being practiced anyway.
- Do not run FSRS on individual scale degrees. That granularity produces noisy grades and a scheduling system that thrashes.

Parameters: start with published defaults. Per-user parameter optimization requires a substantial review history and is out of scope for Phase 1 — leave the hook, do not build it.

---

## 7. Mastery evaluation

Implements `03-CURRICULUM.md` §5.5 exactly. Evaluate after every attempt; it is cheap and it makes progression feel responsive.

```kotlin
fun evaluate(
    window: List<Attempt>,          // last 30 at current axis levels
    activeDegrees: Set<ScaleDegree>,
    axes: Map<DifficultyAxis, Int>,
    confusion: ConfusionMatrix
): MasteryVerdict
```

All five criteria must hold. Report *which* criterion is unmet — the progress UI shows the user what is actually blocking them, which is far more useful than a percentage bar.

On mastery: unlock the successor node, seed its axis levels from the current node's (with `CADENCE_FADE` reduced one step), initialize its FSRS state, and hand the current node to the review scheduler.

---

## 8. Session composer

Default session: 5 minutes, roughly 40–50 items. User-configurable 3/5/10 minutes.

The minutes setting is honored twice, and both halves are required: it sizes the plan up front (an item-count estimate at ~9 items/minute), **and** it is a wall-clock bound the practice loop enforces at item boundaries — when elapsed time reaches the budget, the session completes at the end of the current item and the plan's remainder is cancelled (the estimate assumes a pace a deliberate beginner does not hit, so without the bound a "3 minute" session can run far past 3 minutes). An item in flight always finishes — cutting mid-stimulus is time pressure, which `02-PEDAGOGY.md` §6 prohibits — and a running `M2.INDEPENDENCE_CHECK` block is never severed, since its 30 probes only evaluate as a complete block. The budget survives interruption: an interrupted session persists its remaining seconds in the resume state, and resuming continues from that remainder rather than granting a fresh budget — the promise is N minutes of practice, however many sittings it takes.

Composition, in priority order:

1. **Due reviews** (mastered nodes past their FSRS due date) — up to 40% of the session, oldest-due first.
2. **Current node work** — the remainder, the bulk.
3. **Remediation items** — woven into (2) via the generator weights, not as a separate block.

Ordering within the session:

- **First 5 items are a warm-up:** the current node at one cadence-fade level *easier* than current. Cold-start performance is systematically worse and should not pollute the mastery window. Mark warm-up attempts and exclude them from mastery evaluation and the staircase (still record them).
- **Block new work, interleave known work.** The contextual-interference literature is clear that interleaving hurts acquisition and helps retention, and that novices are overwhelmed by it. So: a newly unlocked node is practiced in a contiguous block until it reaches 70% accuracy over 15 items; after that it is interleaved with due reviews.
- **Never end on a failure.** If the final planned item is answered incorrectly, append one item at a reduced difficulty. This is a retention measure, and it is not dishonest — it does not alter scoring.

Seeding: the session has a `rootSeed`; each item seed is `rootSeed` combined with the item index via a fixed mixing function. The whole session is reproducible from one number.

---

## 9. What is deliberately not built

- Per-user FSRS parameter optimization.
- Item-response-theory ability estimation. The staircase is sufficient and far simpler.
- Any machine-learned difficulty model. There is no data, no server, and no need.
- Response-time-based scoring. Latency is recorded and used for diagnostics only. See `02-PEDAGOGY.md` §6.
