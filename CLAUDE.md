# CLAUDE.md — Project Constitution

Working title: **Tonic**. An ear training app for Android, built from absolute zero for a learner with no musical background, no instrument, and no notation literacy.

This file governs all work in this repository. Read it before every session. If any instruction elsewhere conflicts with this file, this file wins unless the human says otherwise in chat.

---

## 1. Stack (fixed)

| Concern | Choice |
|---|---|
| Language | Kotlin (JVM target 17) |
| Platform | Android native, minSdk 26, targetSdk 35 |
| UI | Jetpack Compose (Material 3) |
| DI | Hilt |
| Async | Coroutines + Flow |
| Persistence | Room (relational state) + DataStore Preferences (settings) |
| Audio | `android.media.AudioTrack`, PCM synthesized in Kotlin. **No sample assets. No MediaPlayer. No SoundPool.** |
| Build | Gradle Kotlin DSL, version catalog (`libs.versions.toml`) |
| Testing | JUnit5 + kotlin.test on JVM; Turbine for Flow; Compose UI tests; instrumented tests only where unavoidable |

Do **not** introduce a new library without asking first. Do **not** add a network layer — Phase 1 is fully offline, local-only, and has no account system, no analytics, no telemetry upload, no crash reporting SDK.

A desktop (Ubuntu) build is a **future** consideration. It is not in scope now, but see §4: the pure-Kotlin core modules must stay free of Android dependencies so that a later Compose Multiplatform or JVM port is mechanical rather than a rewrite.

---

## 2. Phase discipline (hard rule)

The build is phased. The current phase is defined in `docs/09-BUILD-PLAN.md`.

**Phase 1 scope is Module 0 (Diagnostic) and Module 2 (Diatonic Functional Recognition) only.**

Rules:

1. Build only what the current phase specifies. Do not implement later modules "while you're in there."
2. At every checkpoint marked **STOP** in `09-BUILD-PLAN.md`, halt, report what was built, report what was verified, and wait for explicit approval before continuing.
3. Do not start a new phase without an explicit affirmative instruction in chat.
4. If a spec is ambiguous or a decision is required that is not covered by these documents, **stop and ask**. Do not guess and proceed.
5. Reserved identifiers for future modules (M1, M3–M8) exist in `docs/03-CURRICULUM.md`. Define the enum/ID constants so the schema is stable, but leave the implementations unbuilt.

---

## 3. Document map

| File | Authority over |
|---|---|
| `CLAUDE.md` | Everything. Conventions, prohibitions, phase discipline |
| `docs/01-PRODUCT-SPEC.md` | Scope, non-goals, success criteria |
| `docs/02-PEDAGOGY.md` | Method. Non-negotiable teaching rules the code must honor |
| `docs/03-CURRICULUM.md` | Skill graph, item generation rules, mastery criteria |
| `docs/04-ARCHITECTURE.md` | Module boundaries, layering, dependency rules |
| `docs/05-DATA-MODEL.md` | Room schema, DataStore keys, migrations |
| `docs/06-AUDIO-ENGINE.md` | Synthesis, timbres, scheduling, tuning |
| `docs/07-ADAPTIVE-ENGINE.md` | Staircase, spaced repetition, mastery, remediation |
| `docs/08-UI-SPEC.md` | Screens, widgets, states, accessibility |
| `docs/09-BUILD-PLAN.md` | Phase order, acceptance criteria, STOP gates |
| `docs/10-TESTING.md` | Test strategy and determinism requirements |

If you change behavior that a document describes, update that document in the same commit. Documents that disagree with the code are worse than no documents.

---

## 4. Layering rules (enforced, not aspirational)

Dependency direction is strictly one-way:

```
:app  ->  :feature:*  ->  :core:engine  ->  :core:curriculum  ->  :core:model
                      ->  :core:data    ->  :core:model
                      ->  :core:audio   ->  :core:model
```

- `:core:model`, `:core:curriculum`, `:core:engine` are **pure Kotlin JVM modules**. They must not import `android.*`, `androidx.*`, or any Android framework type. They are unit-testable on the JVM with no emulator.
- `:core:audio` and `:core:data` are Android library modules. They may depend on Android APIs. They must not depend on `:feature:*`.
- `:feature:*` modules do not depend on each other. Cross-feature navigation goes through `:app`.
- No feature module reaches into Room entities directly. Repositories in `:core:data` expose domain types from `:core:model`.

Violating the layering to "make it work" is not acceptable. If the layering blocks something, stop and ask.

---

## 5. Determinism (critical)

Item generation, difficulty selection, and scheduling must be **deterministic given (seed, state)**.

- Every generated exercise item is produced by a pure function `generate(skillId, params, seed) -> Item`.
- No use of `Random()` without an injected seed. No `System.currentTimeMillis()` inside generation or engine logic — time is injected via a `Clock` abstraction.
- The same seed and state must produce a byte-identical item on every run and every device.

This is what makes the engine testable and the app debuggable. Do not compromise it.

---

## 6. Coding conventions

- Kotlin official code style. `ktlint` clean.
- Explicit visibility modifiers on public API of `:core:*` modules. `internal` by default elsewhere.
- No `!!`. No swallowed exceptions. No `GlobalScope`.
- Prefer sealed interfaces + exhaustive `when` over open classes and `else` branches.
- Data classes for state; value classes for IDs (`@JvmInline value class SkillId(val raw: String)`).
- Compose: stateless composables, state hoisted to ViewModels, `@Preview` for every non-trivial composable.
- One public type per file where practical. File name matches the type.
- KDoc on every public function in `:core:*`. Explain **why**, not what.
- Comments explain intent and pedagogy rationale where relevant. A future reader must understand why the cadence fades, not just that it does.

---

## 7. Prohibitions

Do not, without explicit chat approval:

- Add a dependency, plugin, or Gradle module not listed in the specs.
- Add network access, analytics, telemetry, ads, crash reporting, or any account system.
- Add audio sample files or any third-party audio asset. All sound is synthesized. This is both an APK-size and a copyright decision.
- Bundle, embed, or reference commercial music recordings. See `docs/02-PEDAGOGY.md` §9.
- Implement absolute-pitch training. It is explicitly out of scope.
- Require the user to sing, read notation, or use a piano keyboard to answer any question.
- Add gamification mechanics beyond those in `docs/08-UI-SPEC.md` §7. No loss-aversion pressure, no guilt notifications, no engagement dark patterns.
- Refactor across module boundaries in a commit that also adds a feature.
- Delete or rewrite existing tests to make a build pass.

---

## 8. Communication style for this project

- Direct and technical. No filler, no praise, no restating the request back.
- American English spelling and grammar throughout — code, comments, docs, and UI strings.
- Report what you built, what you verified, and what you did not verify. State uncertainty explicitly.
- When you finish a phase, produce a short delta report: files added, decisions made, deviations from spec (with reasons), open questions.
- Do not claim something works if you have not run it.

---

## 9. Safety and user-facing language

Module 0 includes a screen for signs of congenital amusia. See `docs/02-PEDAGOGY.md` §8 for mandatory wording constraints. In short: the app **never** diagnoses, never uses the phrase "tone deaf," and never tells a user they cannot learn. A flag routes to a longer discrimination-training path and nothing else. This is a hard requirement, not a style preference.
