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

## 2. Working rules

The build is phased; `docs/09-BUILD-PLAN.md` records what each phase delivered. Phases 1–3 are built.
Phase 4 (rhythm) is built through compound meter, meter change and the independence check; its
hardening pass and every device measurement it owes are outstanding.

**Default mode: build it, test that it works, move on.** No stage gates, no per-stage delta reports, no
waiting for approval between stages. Work continuously through a phase. Ask only on a genuine design
decision or when something is actually broken.

These four are kept because each caught a real bug:

1. **Determinism is non-negotiable.** The same seed and state produce the same output, byte for byte,
   on every run and every device. See §5.
2. **A feature is not done until production code calls it.** Check that it is reachable, not just that
   it exists and passes tests. `ProductionGate` was complete, correct and never invoked; the
   microphone was bound to an "unavailable" stub for three stages; `PlaybackTimebaseSource` was never
   bound at all. Each looked finished.
3. **Any axis that removes support or increases required retention moves one level at a time.**
   `CADENCE_FADE`, `METRONOME_FADE`, `PREDICT_GAP`. Skipping a level does not make an item harder, it
   makes it unanswerable.
4. **Audio changes need a listening pass on a real device.** Nothing on the JVM can tell you whether a
   timbre is pitch-ambiguous or a metronome is playable-along-with.

Two standing constraints that are not process:

- Reserved identifiers for unbuilt modules (M4–M6, M8) exist in `docs/03-CURRICULUM.md` §6. Define the
  enum/ID constants so the schema is stable; leave the implementations unbuilt. `M8.MINOR_MODE` and
  `M8.CHROMATIC_DEGREES` are dead rather than pending — Phase 2 shipped that work as M10 and M11 — and
  are kept unreused, never repointed. M7 does not exist and its number is retired
  (`docs/02-PEDAGOGY.md` §9).
- Do not implement later modules "while you're in there."

---

## 3. Document map

**`CLAUDE.md` is the only file in this repository that contains instructions.** Everything below is
reference: design reasoning, decisions and open questions. If one of them reads as an order, it is
stale — this file wins.

| File | Covers |
|---|---|
| `docs/01-PRODUCT-SPEC.md` | Scope, non-goals, success criteria |
| `docs/02-PEDAGOGY.md` | Method. The teaching rules the code honors |
| `docs/03-CURRICULUM.md` | Skill graph, item generation, mastery criteria |
| `docs/04-ARCHITECTURE.md` | Module boundaries, layering, dependency rules |
| `docs/05-DATA-MODEL.md` | Room schema, DataStore keys, migrations |
| `docs/06-AUDIO-ENGINE.md` | Synthesis, timbres, scheduling, tuning |
| `docs/07-ADAPTIVE-ENGINE.md` | Staircase, spaced repetition, mastery, remediation |
| `docs/08-UI-SPEC.md` | Screens, widgets, states, accessibility |
| `docs/09-BUILD-PLAN.md` | Record of what was built, in what order |
| `docs/10-TESTING.md` | Test strategy, determinism, the four simulations |
| `docs/11-ONBOARDING-CLARITY.md` | In-app explanation standard. Wins over `08` on any explanation detail |
| `docs/20-PHASE-2-SPEC.md` | Minor, chromatic, audiation, export. §3 defines M9–M12; §8 records decisions |
| `docs/21-HANDOFF.md` | Working notes, not authority. A dated snapshot — check its claims against the repo |
| `docs/30-PHASE-3-SPEC.md` | Optional sung response. §5.5 and §9 carry its measurements and open questions |
| `docs/40-PHASE-4-SPEC.md` | Rhythm (M3). §7.6 lists what is not built; §10 the open questions |

Update a document when a **design decision** changes. Not for implementation details, and not to
record that something was built.

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
- **Be brief.** Default to a few lines. Reasoning belongs in commit messages and KDoc, where it is
  durable and skippable, not in chat. Long-form output is by request only.
- **Reply format, every time:** what was done, what is needed from the human (omit if nothing), what is
  next. Nothing else — no background, no caveats, no findings that were not asked for.
- Ask questions through the interactive question prompt, not as prose in the reply.
- American English spelling and grammar throughout — code, comments, docs, and UI strings.
- Report what you built, what you verified, and what you did not verify. State uncertainty explicitly.
- **Do not build or test locally. CI is the only gate.** By the maintainer's instruction: no
  `scripts/verify.sh`, no `scripts/android-sdk.sh`, no `./gradlew`. A local build in this sandbox
  takes around ten minutes and consumes resources the maintainer needs elsewhere, and that cost
  outweighs what it catches. `.github/workflows/verify.yml` runs `scripts/verify.sh` on every push and
  pull request, and it is the authoritative signal — it always was, since it runs on a known-clean
  machine and checks things a local run does not (the committed Room schemas, the APK signature).

  The scripts stay in the repository. They are what CI runs, and `android-sdk.sh` documents exactly
  which SDK packages the gate needs. They are simply not to be run here.

- **One exception: run `scripts/ktlint.sh` before every push.** Granted by the maintainer after five
  of eight consecutive CI rounds failed on formatting alone — line wrapping, import order,
  first-line-of-body — and never on logic. It is a standalone jar, already cached, needing no Android
  SDK and no Gradle daemon, and it finishes in about five seconds; the cost the rule above exists to
  avoid is not present here. Its pin is derived from the ktlint the Gradle plugin resolves, so what it
  reports is what the build reports.

  It is a lint check and nothing more. A clean run says nothing about whether the code compiles, and
  reporting green still requires a CI run.

  What this costs, stated plainly so it is not rediscovered: nothing can be confirmed before pushing,
  so some pushes will be red and each fix costs a full CI round. Two habits follow from that, and they
  are not optional. **Keep commits small and single-purpose**, so a red run points at one thing rather
  than at a batch. **Re-read the diff adversarially before pushing** — imports, exhaustive `when`s,
  every implementor of an interface you widened — because the compiler is no longer available to do it
  for you and those are the failures it would have caught in seconds.

- **Never report green without a source, and the only source is a CI run.** Name it: the workflow run
  and its conclusion. "It should pass" is not a report, module tests are not the gate, and a change
  that has not been through CI is unverified — say so rather than implying otherwise.
- One short summary at the end of a phase: what was built, what was decided, what is still open. Not per stage.
- Do not claim something works if you have not run it.

---

## 9. Safety and user-facing language

Module 0 includes a screen for signs of congenital amusia. See `docs/02-PEDAGOGY.md` §8 for mandatory wording constraints. In short: the app **never** diagnoses, never uses the phrase "tone deaf," and never tells a user they cannot learn. A flag routes to a longer discrimination-training path and nothing else. This is a hard requirement, not a style preference.
