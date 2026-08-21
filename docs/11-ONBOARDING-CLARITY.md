# 11 — Onboarding and In-App Explanation (User-Facing Clarity)

## Why this document exists

The shipped Phase 1 build drops the user directly into a chord-cadence-plus-scale-degree exercise with no explanation of what is about to happen, what the sounds mean, or what the answer buttons represent. A user with real music theory background could not figure out the task without extended back-and-forth. That is a shipped defect, not an acceptable consequence of "don't front-load theory."

`01-PRODUCT-SPEC.md` §6 says don't gate the first exercise behind a wall of theory or a forced tutorial. That rule was correct and stays. It was misapplied to also mean "don't explain the mechanic," which is wrong and is the actual root cause of the confusion. **Explaining what a button does and what a sound means is not theory. It's the minimum bar for a shippable interface**, the same bar any commercial app — a game, a fitness app, a language app — clears before asking the user to do anything.

This document is the standard the app must meet. Treat every rule below as a requirement, not a suggestion, and treat any future exercise type the same way before it ships.

---

## 1. The standard: explain, then demonstrate, then let go

Every new exercise type gets three things, in this order, the first time a user encounters it:

1. **A one-screen, plain-language explanation** of what will happen and what to do. No jargon that hasn't been defined in the same breath it's used. Two to four short sentences, not a paragraph wall.
2. **One worked example**, narrated, where the app plays the sounds and shows what the correct answer would be and why — without the user having to answer anything yet. This is the single highest-leverage fix. An abstract description of "identify where the note sits relative to home" means nothing on first contact; hearing it demonstrated once, with the answer shown, makes it concrete immediately.
3. **A dismiss/start button.** After that, get out of the way completely. No repeated tutorials, no forced re-explanation on every session — see §4 for how this stays skippable on return visits.

This is not a tutorial mode the user must complete correctly to proceed. There's no quiz on the explanation. It's shown once, it's short, and then it's gone unless recalled deliberately (§5).

## 2. What went wrong, named specifically, so it isn't repeated

- **Terms were used before being defined.** "Home," "degree," "cadence," "the I chord" were all encountered by the user before any of them were explained in the app itself. Every term the app uses in-exercise must be defined in the explanation screen, in the order it will be heard, using the exact words the exercise itself uses.
- **The relationship between the reference and the target was never stated.** The user could hear that a chord sequence played and then a note played, but nothing told them the note was to be judged *against* that reference, specifically against its tonic. This causal link — reference establishes home, then you judge the target against home — must be stated outright, not left to be inferred from the audio alone.
- **The answer options were unexplained.** Buttons appeared with no stated meaning. Every answer control must have its meaning stated in the same explanation screen, matched to what it will sound like.
- **A worked example was never given.** This compounds all three problems above — without one concrete instance to anchor the abstract description to, "identify the note's position relative to home" stays theoretical no matter how it's worded.
- **A reduced reference (fewer chords, less support) can itself be ambiguous to a beginner**, independent of whether it's explained. See `07-ADAPTIVE-ENGINE.md` §2a — this document's explanation requirement does not substitute for that fix; a perfectly worded explanation of an unanswerable question does not make the question answerable.

## 3. Required content for the Module 2 explanation screen specifically

This supersedes and expands `08-UI-SPEC.md` §3a with concrete required copy content (not final copy — final wording is a copywriting pass, but every point below must be present in some form):

1. **What plays first, stated concretely:** "You'll hear a short sequence of chords. This establishes a 'home' sound — the note and chord everything else will be compared to." Reference that the reference always returns to or centers on that same home chord.
2. **What plays second:** "Then you'll hear a single note, by itself."
3. **What the task is:** "Your job: decide whether that single note is the same as 'home,' or one of the other notes relative to it."
4. **What the buttons mean, tied to what they'll sound like:** state plainly what the currently visible buttons correspond to — not bare unexplained numerals alone (see §6).
5. **One worked, playable example**, narrated end to end: play the reference, play a target note, show which button is correct and why, in real audio, before asking the user to answer anything themselves.
6. A single **"Start"** button. No comprehension check, no forced repeat viewing.

## 4. Every future module gets the same treatment before it ships

This is not a one-time patch for Module 2. Before any future module (rhythm, dictation, harmony) plays its first exercise for a first-time user, it must have gone through the same three-step standard in §1, with content specific to that module's mechanic. This requirement is now permanent and belongs in `09-BUILD-PLAN.md`'s acceptance criteria for every future-phase stage that introduces a new exercise type — no stage introducing a new task shape is complete without its explanation screen and worked example.

## 5. Recall, not repetition

The explanation is shown automatically once, on first encounter with that exercise type. After that:

- It is never shown again automatically.
- It is always reachable on demand via a small, low-emphasis help affordance on the practice screen itself (an icon or a text link — implementation detail for `08-UI-SPEC.md`, not this document).
- Recalling it manually shows the exact same explanation and worked example, not an abbreviated version. If it was clear enough to help the first time, it should still be there intact the tenth time the user forgets and looks it up.

## 6. Answer-button labeling: reconsider the bare numbers

`08-UI-SPEC.md` §3 currently specifies numbers (1, 3, 5, ...) as the default label on the degree ladder, with solfège as an alternate. That default is now in question, not settled.

The problem observed directly in real use: even with a full explanation, bare numerals on buttons carry no inherent meaning to a first-time user until they've internalized what those numbers refer to — and that internalization is exactly the thing not yet built on first contact. Bare numerals may be the *right* label once the mapping is second nature, while being actively counterproductive on the first several sessions.

**Requirement:** the button labels — or a persistent, unobtrusive annotation near them — should carry semantic meaning on early encounters, not just positional numbers. Options worth considering, to be resolved in an `08-UI-SPEC.md` revision:

- Labels that state relationship directly on first exposure, e.g. "Home," "Up a bit," "Up more" — fading to bare numbers once a mastery threshold is hit within a skill node.
- A persistent small subtitle under each numeral for the first N sessions of a newly-unlocked degree set, then removed.
- Solfège as the earlier default rather than numbers, since "do" carries an inherent "home" connotation that "1" does not, for a user without a lifetime of counting-based musical habit.

This is flagged as an open design decision, to be resolved in its own pass, because it changes a settled part of `08-UI-SPEC.md` and deserves its own consideration rather than being bundled into another fix silently.

## 7. What "shippable" actually implies for this spec

Concretely, holding the app to a real shipping standard means:

- No exercise ships without passing the three-step standard in §1, verified by someone who has never seen the app pressing "start" cold and being able to complete the first item correctly without external help.
- The explanation and worked example are treated as core product surface, not optional polish — they get the same design attention as the exercise itself, not a bolted-on tooltip.
- "The user can technically figure it out with effort" is not the bar. The bar is: a reasonably attentive user gets it from the explanation and the one worked example, without needing to ask anyone anything.

## 8. Immediate required fixes, in priority order

Found through direct use, not theoretical review. Fix in this order — later items assume earlier ones are done, and building copy/UI on top of an unfixed lower-numbered item wastes the work:

1. **`CADENCE_FADE` step-size correction and the L1/warmup consequences (`07-ADAPTIVE-ENGINE.md` §2a).** This is the actual root cause of the worst incident on record — a user stranded at an audibly ambiguous difficulty level. Fix this first; an explanation layered on top of an unanswerable question does not make the question answerable.
2. **Diagnostic runtime and progress indicator (§9.2).** Measure actual runtime against the 6-minute target; fix early-termination if it isn't firing; replace the ambiguous progress bar with an honest sub-test counter.
3. **Stage labeling and transition announcement (§9.1).** Every screen states what stage it's in. Moving from calibration to practice is announced, not silent.
4. **Silent difficulty/axis changes, including warmup transitions (§9.3).** Any change to the reference structure, active degree set, or other axis — including the scheduled warmup-to-normal transition at item 6 — is announced on screen when it happens, in plain forward-progress language.
5. **Module 2 explanation screen with worked example (§3).**

Nothing else gets worked on until all five of these are done. A first-time user must be able to sit down, always know what stage they're in, always know how much of the diagnostic remains, always be told when the exercise changes shape, always have a real means of answering whatever is currently on screen, and correctly answer the first real item without outside help.

## 9. Always-on labeling and progress transparency (mandatory, currently missing)

**Gap on record, found through direct use:** a user moved from the diagnostic through several sub-tests into live practice with no screen ever stating which stage they were in, the diagnostic ran far longer than its own spec target with no visible indication of why or how much remained, and the difficulty system silently changed the reference structure mid-session with zero on-screen signal — a change that, per `07-ADAPTIVE-ENGINE.md` §2a, was also a genuine pacing bug and not merely an announcement gap. None of this is acceptable, and none of it requires relitigating the method — it requires the app to say what is happening, at all times, in plain terms, and to only ever put the user in front of a question they have a real means of answering.

This section is a floor. Every screen in the app must clear it.

### 9.1 Stage identity is always visible

Every screen belongs to a named stage the user can see. At minimum: "Calibration" (Module 0) and "Practice" (Module 2) are distinct, visibly labeled states — a small persistent header or equivalent, not something the user has to infer from context. Moving from one to the other is itself announced, not silent: a brief transition screen or banner stating plainly that calibration finished and practice is starting, before the first practice item plays.

### 9.2 Diagnostic length and progress must be real, not ambiguous

- The diagnostic's actual runtime must be measured against `01-PRODUCT-SPEC.md` §5's under-6-minutes target and `07-ADAPTIVE-ENGINE.md` §2's early-termination-on-convergence logic. If a real run is taking meaningfully longer than that in practice, the early-stop logic itself is a bug and must be fixed, not just relabeled.
- The progress indicator during the diagnostic must reflect genuine, honest progress — sub-test count completed out of sub-tests remaining, not a bar whose fill rate is decoupled from actual remaining work. An indicator that "moves when it wants to" is worse than no indicator, because it actively misleads. If exact remaining time can't be known (adaptive termination means length isn't fixed), say so honestly — e.g., a per-sub-test counter ("Sub-test 2 of 4") rather than a continuous bar implying false precision.

### 9.3 No silent difficulty changes, ever

When the adaptive engine changes the reference structure, the active degree set, or any other axis in a way that changes what the exercise sounds or looks like, the app must say so on screen, briefly, before or as it happens. Not a theory explanation, not a re-justification of the pedagogy — a plain, short statement: what changed, framed as forward progress, not as an unexplained shift. E.g., "Fewer chords now — you're ready for less support." This applies to every axis in `03-CURRICULUM.md` §5.3, not only the cadence-fade axis specifically, and it applies to scheduled warmup transitions (per `07-ADAPTIVE-ENGINE.md` §2a) exactly as it applies to performance-driven staircase changes — a level change is a level change regardless of what triggered it.

The user must never be in a position of noticing the exercise changed shape and having no idea whether that's the app working correctly or the app malfunctioning. Silence on a genuine change is indistinguishable from a bug and must be treated as a bug.

### 9.4 Register of language: assume nothing, state everything

Every explanation screen, every label, every in-the-moment prompt must be written so that a first-time user with zero context can act correctly without asking anyone anything. Concretely:

- Every term used in the UI is defined at first use, in the same screen, in plain words, before or exactly when the user needs it.
- Every exercise type gets a concrete worked example before the user is asked to perform it for the first time (per §1–§3 of this document).
- Every stage transition, every difficulty change, and every new mechanic is announced plainly, not inferred from behavior.
- Copy is short. No paragraph walls. If an explanation can't be said in two to four short sentences plus one example, it's saying too much at once and should be split or cut.

This is the standard the whole app is held to from here forward: not "a capable adult could eventually figure this out," but "a first-time user acts correctly on the first try, with no outside help, because the app told them what they needed to know exactly when they needed it, and was never asked to answer something it hadn't given them the means to answer."
