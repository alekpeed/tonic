# 00 — Document Index

Specification set for **Tonic**, an Android ear training app built from absolute zero for a user with no musical background.

## Repository placement

```
<repo-root>/
├── CLAUDE.md          <- repository root, so Claude Code picks it up automatically
└── docs/
    ├── 00-README.md   <- this file
    │
    │   00-10: the original specification set. Phase-independent unless stated.
    ├── 01-PRODUCT-SPEC.md
    ├── 02-PEDAGOGY.md
    ├── 03-CURRICULUM.md
    ├── 04-ARCHITECTURE.md
    ├── 05-DATA-MODEL.md
    ├── 06-AUDIO-ENGINE.md
    ├── 07-ADAPTIVE-ENGINE.md
    ├── 08-UI-SPEC.md
    ├── 09-BUILD-PLAN.md
    ├── 10-TESTING.md
    │
    │   11: written after Phase 1 shipped, from direct use.
    ├── 11-ONBOARDING-CLARITY.md
    │
    │   20+: one document per phase beyond the first, numbered by phase.
    ├── 20-PHASE-2-SPEC.md
    ├── 21-HANDOFF.md
    ├── 30-PHASE-3-SPEC.md
    └── 40-PHASE-4-SPEC.md
```

`11-ONBOARDING-CLARITY.md` is not optional reading. It was written after a real first-contact failure
and it wins over `08-UI-SPEC.md` on any explanation detail.

`21-HANDOFF.md` is working notes, not authority — a dated snapshot whose claims should be checked
against the repo before being relied on.

## Reading order

**First session, read in full:** `CLAUDE.md`, `01`, `02`, `09`, `11`.

`09-BUILD-PLAN.md` opens with "Where the build actually is" — read that before assuming anything about
what is or is not built.

`02-PEDAGOGY.md` is the document to internalize. It is the reason the app exists and the source of every constraint that will look arbitrary from inside the code.

`09-BUILD-PLAN.md` records what each phase delivered, and is where a stage number cited in a source comment can be looked up.

## Authority order

If two documents conflict:

1. `CLAUDE.md`
2. `02-PEDAGOGY.md`
3. The document specific to the subsystem
4. Everything else

If the conflict is not resolvable that way, stop and ask.

## Fixed parameters

| Parameter | Value |
|---|---|
| Platform | Android native, Kotlin, Jetpack Compose |
| Persistence | Local only. Room + DataStore. No network |
| Phase 1 scope | Module 0 (Diagnostic) + Module 2 (Diatonic Functional Recognition) |
| Input mode | Recognition only. No microphone in Phase 1 |
| Instrument assumption | None. No keyboard, no notation, no instrument required |
| Audio | Fully synthesized. No sample assets |
| Desktop build | Deferred. Architecture keeps it possible |

## Two things most likely to go wrong

1. **The cadence-fade axis gets treated as a nice-to-have.** It is the mechanism that separates this app from every competitor that produces learners dependent on a crutch. It is a mastery criterion (`03` §5.5), it has a dedicated simulation test (`10` §5, simulation 6), and it is measured by success criterion 6 (`01` §5). If it slips, the product is pointless.

2. **Difficulty axes get moved simultaneously.** The scheduler moves exactly one at a time (`07` §3). Moving several is the intuitive optimization and it destroys the ability to attribute a performance drop to a cause.

## Handoff note

These documents specify a design. They do not contain code. The estimates of what is hard are: the audio engine's perceptual quality (loudness matching across timbres, unambiguous pitch across register) and the adaptive engine's stability under real user behavior. Budget accordingly, and take the Stage 1.2 and Stage 1.4 gates seriously — those two are where this project either works or quietly does not.
