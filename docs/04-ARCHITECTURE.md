# 04 — Architecture

## 1. Principle

The parts of this app that are hard to get right — item generation, difficulty adaptation, scheduling, mastery evaluation — are pure logic. They must be testable on the JVM in milliseconds, without an emulator, without audio hardware, without Compose.

Therefore: **all pedagogical logic lives in pure Kotlin modules with zero Android dependencies.** Android is a delivery mechanism for sound and pixels, nothing more. This is also what makes a later Ubuntu build tractable.

## 2. Gradle module graph

```
:app
 ├── :feature:diagnostic
 ├── :feature:practice
 ├── :feature:progress
 ├── :feature:settings
 │
 ├── :core:engine        (pure Kotlin/JVM)
 ├── :core:curriculum    (pure Kotlin/JVM)
 ├── :core:model         (pure Kotlin/JVM)
 ├── :core:audio         (Android library)
 ├── :core:data          (Android library)
 └── :core:ui            (Android library — design system, shared composables)
```

Allowed dependencies:

| Module | May depend on |
|---|---|
| `:core:model` | nothing (stdlib only) |
| `:core:curriculum` | `:core:model` |
| `:core:engine` | `:core:model`, `:core:curriculum` |
| `:core:audio` | `:core:model` |
| `:core:data` | `:core:model` |
| `:core:ui` | `:core:model` |
| `:feature:*` | `:core:*` (all) |
| `:app` | everything |

Forbidden:

- `:core:model`, `:core:curriculum`, `:core:engine` importing `android.*` or `androidx.*`. Enforce with a Gradle check or a lint rule; a plain unit test that scans imports is acceptable if simpler.
- `:feature:X` depending on `:feature:Y`.
- Any module depending on `:app`.

## 3. Module responsibilities

### `:core:model`
Domain vocabulary. No behavior beyond trivial computation.

Types: `SkillId`, `ModuleId`, `ScaleDegree`, `PitchClass`, `Mode`, `TimbreId`, `DifficultyAxis`, `CadenceFadeLevel`, `ReferencePlan`, `ItemTiming`, `Item` (sealed), `Attempt`, `AnswerAlphabet`, `MasteryState`, `DiagnosticResult`, `SessionPlan`, `Clock` (interface).

Rule: no I/O, no coroutines, no framework. Value classes for IDs.

### `:core:curriculum`
The skill graph and the item generators.

- `SkillGraph` — nodes, prerequisites, active degree sets, mastery criteria. Declarative, defined in code as immutable data (not JSON — compile-time safety is worth more than runtime editability here).
- `ItemGenerator` implementations, one per item type. Pure, seeded, deterministic.
- `ReferencePlanBuilder` — turns a `CADENCE_FADE` level into a concrete plan of what to play before the target.
- `BalancedSampler` — enforces the rolling-window frequency balance from `03-CURRICULUM.md` §5.4.

### `:core:engine`
Adaptation and scheduling. See `07-ADAPTIVE-ENGINE.md`.

- `Staircase` — transformed up-down procedure per axis.
- `AxisScheduler` — decides which axis moves next.
- `MasteryEvaluator` — evaluates the five criteria.
- `ConfusionTracker` — maintains the (target, response) matrix and derives remediation weights.
- `ReviewScheduler` — FSRS at skill granularity.
- `SessionComposer` — assembles a session from due reviews, current node work, and remediation.

All of these take state in and return new state out. No mutation of shared state, no side effects, no clock reads (inject `Clock`).

### `:core:audio`
Synthesis and playback. See `06-AUDIO-ENGINE.md`.

- `SynthEngine` — renders a `ReferencePlan` + target into PCM.
- `TimbreBank` — the four synthesized timbre families.
- `AudioPlayer` — `AudioTrack` wrapper, streaming mode, dedicated thread, lifecycle-aware.
- `AudioFocusManager` — handles focus loss, headphone disconnect, phone calls.

Rule: the PCM *rendering* functions should be pure and testable (`FloatArray` in, `FloatArray` out). Only the playback wrapper touches Android.

### `:core:data`
Persistence. See `05-DATA-MODEL.md`.

- Room database, DAOs, entities.
- DataStore for settings.
- Repositories that expose `:core:model` domain types and `Flow`s. Entities never escape this module.

### `:core:ui`
Design system: color scheme, typography, spacing tokens, the degree ladder widget, shared buttons, feedback surfaces. See `08-UI-SPEC.md`.

### `:feature:*`
Each is: a Compose screen tree + a ViewModel + a UI state model. ViewModels orchestrate `:core:engine` and `:core:data` and `:core:audio`. They contain no pedagogical logic — if a ViewModel is deciding difficulty, that logic belongs in `:core:engine`.

## 4. The practice loop (control flow)

```
PracticeViewModel
  ├─ on start: SessionComposer.compose(userState, clock) -> SessionPlan
  ├─ for each planned slot:
  │    ├─ ItemGenerator.generate(skill, axes, seed) -> Item      [:core:curriculum]
  │    ├─ SynthEngine.render(item) -> PcmBuffer                  [:core:audio]
  │    ├─ AudioPlayer.play(buffer)                               [:core:audio]
  │    ├─ await user answer (or replay request)
  │    ├─ Attempt recorded -> AttemptRepository                  [:core:data]
  │    ├─ Staircase.update(...), ConfusionTracker.record(...)    [:core:engine]
  │    └─ render feedback, advance
  └─ on end: MasteryEvaluator.evaluate(...), ReviewScheduler.schedule(...)
```

Every seed used is persisted with the attempt. A session is fully replayable from its stored seeds — this is the debugging affordance that makes audio/pedagogy bugs tractable.

## 5. Threading

- Audio rendering and playback: dedicated background thread, never the main thread. Pre-render the next item's PCM while the current item awaits an answer.
- Engine and generation: `Dispatchers.Default`.
- Room: `Dispatchers.IO`, via suspend DAOs.
- UI state: `StateFlow` collected with `collectAsStateWithLifecycle`.

Audio playback must not stutter because a database write is in flight. Persist attempts asynchronously and do not block the loop on them.

## 6. Error handling

- Audio device failure (focus lost, output disconnected): pause the session, hold state, offer resume. Never lose an in-progress session.
- Room failure: surface a non-destructive error, keep the session in memory, retry.
- Corrupt or unmigratable persisted state: fall back to a fresh profile only with explicit user confirmation. Never silently wipe progress.

## 7. Suggested file tree

```
tonic/
├── CLAUDE.md
├── docs/                       (this document set)
├── gradle/libs.versions.toml
├── settings.gradle.kts
├── app/
│   └── src/main/kotlin/…/TonicApplication.kt, MainActivity.kt, NavGraph.kt
├── core/
│   ├── model/src/main/kotlin/…/{ids,music,items,attempts,state}/
│   ├── curriculum/src/main/kotlin/…/{graph,generators,sampling}/
│   ├── engine/src/main/kotlin/…/{staircase,scheduling,mastery,confusion,session}/
│   ├── audio/src/main/kotlin/…/{synth,timbre,player,focus}/
│   ├── data/src/main/kotlin/…/{db,entity,dao,repository,settings}/
│   └── ui/src/main/kotlin/…/{theme,components}/
└── feature/
    ├── diagnostic/
    ├── practice/
    ├── progress/
    └── settings/
```
