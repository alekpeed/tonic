# 02 — Pedagogy (Method Source of Truth)

This document defines the teaching method. The code exists to implement it. Where an engineering convenience conflicts with a rule here, the rule wins or the human is asked.

---

## 1. Functional hearing is the backbone. Intervals are not.

**Rule: every pitch question is asked in a tonal context, and the answer is a scale degree, not an interval name.**

Rationale. Interval training presents two notes in isolation and asks for the distance between them. The problem is that intervals are context-free while real music is not: the same interval occupies many different positions in a key and does not sound functionally alike in each. Learners drilled on intervals routinely score well on interval quizzes and remain unable to transcribe a simple melody. This is the transfer failure that motivates the whole product.

Functional training instead establishes a key and asks what a note is *doing* within it. Function generalizes: scale degree 3 is recognizably scale degree 3 in any key and any octave. That generalization is exactly what real musicianship requires and what interval drilling does not produce.

Consequences for the code:

- The answer alphabet for Module 2 is `{1, 2, 3, 4, 5, 6, 7}` (plus chromatic degrees in later phases), never `{m2, M2, m3, ...}`.
- Intervals may appear far later as a **derived** concept. They are never the foundation and never the Phase 1 answer type.
- Every item generator must accept a key context and must not emit context-free note pairs.

## 2. Labeling: scale-degree numbers primary, movable-do optional

**Rule: the default label set is Arabic scale-degree numbers. Movable-do solfège is a user-toggleable alternate display. Fixed-do is not implemented.**

Numbers require no vocabulary acquisition, are language-neutral, and map directly to function. Movable-do is offered because a substantial population learns it and because it supports later singing work; it maps 1:1 onto the numbers (`1=do, 2=re, 3=mi, 4=fa, 5=sol, 6=la, 7=ti`).

Note on the evidence: one controlled study (Hung, 2012, University of San Francisco) found fixed-do produced higher sight-singing pitch accuracy with large effect sizes — but its 85 subjects were college music majors with piano training from childhood. That population is the opposite of this app's target user, and fixed-do requires absolute-pitch-like note naming that a beginner does not have. Movable-do/numbers is the correct choice here. Do not revisit without instruction.

Implementation: labels are a presentation concern. The domain model stores `ScaleDegree` as an integer plus alteration. The label set is a display mapping only.

## 3. The cadence fade is the core difficulty mechanic

**Rule: the harmonic reference that establishes the key must progressively weaken as the learner improves, on its own difficulty axis.**

This is the single most important design element in the app and the one that fixes the main known defect of existing functional trainers.

The defect: apps that play a full I–IV–V–I cadence before every single question produce learners who cannot identify anything once the cadence stops. The cadence becomes a crutch that is re-supplied so often the learner never has to internalize the tonal center. That is pattern-matching, not audiation.

The fix: treat harmonic support as a resource that is withdrawn in graded steps.

| Level | Reference provided before the target note |
|---|---|
| L0 | Full cadence I–IV–V–I, before every item |
| L1 | Full cadence, then 2–3 items answered before it repeats |
| L2 | Short cadence V–I only |
| L3 | Tonic triad only |
| L4 | Tonic drone sustained underneath the item |
| L5 | Brief tonic flash, then a silent gap, then the item |
| L6 | No reference per item; key established once at block start |
| L7 | As L6, with the silent gap lengthening across the block |

L6–L7 are where genuine audiation is trained: the learner must *retain* the tonic internally rather than have it handed back. Success criterion 6 in `01-PRODUCT-SPEC.md` is measured at these levels.

## 4. Degree introduction order

**Rule: stable tones first, then the tones that resolve into them.**

Stage progression for the active degree set:

1. `{1, 3, 5}` — the tonic triad. Maximally stable, maximally distinct.
2. `{1, 2, 3, 5}` — add 2 (moves between two stable tones).
3. `{1, 2, 3, 5, 6}` — add 6.
4. `{1, 2, 3, 4, 5, 6}` — add 4 (tendency tone, resolves to 3).
5. `{1, 2, 3, 4, 5, 6, 7}` — add 7 (leading tone, strongest pull).

Rationale: 4 and 7 are the active/tendency tones. They are easiest to hear *because* of their pull, but they are also the most confusable with their resolutions (4↔3, 7↔1) and require the tonal center to be securely internalized first.

Minor mode and chromatic degrees are Phase 2. Reserve the identifiers now; do not implement.

## 5. Timbre, register, and octave generalization are mandatory from item one

**Rule: no skill may be trained on a single timbre or a single octave.**

Perceptual learning is notoriously specific to the trained stimulus. A skill trained exclusively on one piano sample becomes "recognizing that piano sample," not hearing pitch function. Stimulus variability is the documented fix.

Requirements:

- Minimum four synthesized timbre families available from the first exercise (see `06-AUDIO-ENGINE.md` §3): pure/sine, soft additive, plucked string, sustained reed.
- Timbre variety is an independent difficulty axis: it begins narrow (one family, fixed register) and widens.
- Octave displacement is an independent axis: the target note may appear an octave above or below where the reference established it. Scale degree 5 is scale degree 5 in any octave, and the learner must come to hear it that way.
- Key/tonic is randomized across items. Never train in C only. This is what prevents accidental absolute-pitch shortcutting.

## 6. Audiation, not reaction time

**Rule: the learner commits an internal answer before verification, and feedback re-anchors the answer in context.**

- No timer pressure in Phase 1. Response latency is *recorded* (it is diagnostic) but never scored, never displayed as a rank, and never used to fail an item.
- On an incorrect answer, the app replays the target note **in its tonal context**, then plays the note the learner chose, then the target again. Discrimination is trained by contrast, not by being told a label.
- On a correct answer, brief confirmation, no fanfare, next item.

Prediction exercises (audiate the next note before it plays) are specified for Phase 2. Reserve the item type; do not build.

## 7. Recognition is the spine; singing is a later optional overlay

**Rule: no exercise may require vocal production to be answered.**

The evidence that singing accelerates perceptual learning is real but correlational and partly dissociated — better discriminators sing more accurately, and discrimination training improves singing, but the causal arrow toward "singing improves hearing" is not established. Meanwhile, requiring singing is a documented barrier: it demands privacy, confidence, and a microphone.

Phase 3 adds sung response as a fully supported **parallel** track with real-time pitch feedback. It never becomes a gate. Any user must be able to reach the highest mastery level in the app without ever making a sound.

## 8. Amusia screening: soft flag, never a diagnosis

**Rule: the app screens for indicators, routes accordingly, and says nothing evaluative to the user.**

Background. Congenital amusia is a real condition. The classic prevalence figure of 4.2% comes from Kalmus & Fry (1980, *Annals of Human Genetics* 43:369–382) using the Distorted Tunes Test. Peretz & Vuvan (2017, *European Journal of Human Genetics* 25:625–630), testing roughly 20,000 participants, revised this substantially downward to about 1.5%. Web/remote administration is known to over-identify relative to lab conditions. The Montreal Battery of Evaluation of Amusia (Peretz et al., 2003) is the standard instrument.

Mandatory constraints on implementation:

- The screen produces an **internal** flag that changes the curriculum path. Nothing more.
- The app **never** displays a diagnosis, a score, a percentile, or a probability of amusia.
- The phrase "tone deaf" and its synonyms must not appear anywhere in the codebase's user-facing strings.
- The app never tells a user they may be unable to learn. Pitch discrimination thresholds are trainable and improve with practice; that is the framing.
- A flagged user is routed to an extended fine-grained discrimination path with wider initial pitch differences and slower progression. The user-visible explanation is neutral: the app is starting with the basics and will build up.
- The flag is revisable. Re-screening happens automatically after sustained progress, and the flag can clear.

If a wording decision is uncertain, stop and ask. Do not improvise copy in this area.

## 9. Real music and copyright (a closed decision, and the reasoning behind it)

**All audio in this app is synthesized in-app. There are no third-party audio assets and no licensing exposure. This is permanent, not a Phase 1 convenience.**

An earlier plan reserved a real-music bridge module — `M7`, playing excerpts of commercial recordings — as a future phase. It was **dropped from the product, not deferred**: the identifier is gone from `ModuleId`, and the gap is pinned by a test rather than merely left. `ModuleId.fromCode("M7")` throws, and so does resolving `SkillId("M7.REAL_MELODY").moduleId`. An `M7.*` string surviving in an attempt log from a build that predates the removal therefore fails loudly instead of resolving to whatever module later inherits the number.

The reasoning is kept here rather than deleted with the module, because this is the kind of decision that gets quietly reopened by someone who has not priced it.

Using a commercial recording requires clearing **two separate copyrights** — a synchronization license for the underlying composition, from the publisher, and a master use license for the specific sound recording, from the label. The US Copyright Office treats the recording and the underlying work as distinct, separately owned, separately licensed works, so clearing one clears nothing about the other. There is no reliable short-clip safe harbor: the Sixth Circuit's *Bridgeport v. Dimension Films* holding effectively required licensing even for a roughly two-second, three-note sample, and the existence of a micro-licensing market weighs against a fair use defense for a commercial app.

That is a per-track negotiation with two rights holders, repeated for every excerpt, in exchange for material whose pedagogical work the synthesis engine already does. The curriculum never depended on hearing a particular recording — it depends on hearing a degree in a key, which `:core:audio` renders at any pitch, in any key, in four timbres, for free. The trade was not worth making.

**If it is ever reopened**, the only admissible sources are: self-commissioned or self-recorded performances owned outright, public-domain compositions in self-owned recordings, or properly licensed royalty-free catalogs. Commercial masters do not become admissible under any framing — not as short clips, not as "educational use," not behind a paywall. And the reopening is a product decision requiring an explicit instruction, not something a future module picks up because a spec once mentioned it.

## 10. Summary of hard invariants

Any pull request violating one of these is wrong regardless of test results:

1. Every pitch item carries a tonal context.
2. Answers are scale degrees, never interval names.
3. Harmonic support fades along a difficulty axis.
4. Key is randomized; the app is never trained in one key.
5. Timbre and register vary.
6. No item requires singing, notation, or a keyboard.
7. The amusia screen never speaks to the user in evaluative terms.
8. No third-party audio assets.
