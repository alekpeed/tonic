# 09 — Build Record

**Reference, not instructions.** This file records what was built and in what order. It contains no
gates, no acceptance criteria and nothing to do. `CLAUDE.md` is the only file in this repository that
tells anyone to do anything.

It is kept for two reasons: source comments cite stage numbers, and knowing which stage a piece of
behavior arrived in is how its reasoning gets found.

## Where the build is

| Phase | Content | State |
|---|---|---|
| 1 | `M0` diagnostic, `M2` major diatonic | Built |
| 2 | Minor, chromatic, audiation, export | Built |
| 3 | Optional sung response | Built. Device measurements still owed — `21-HANDOFF.md` §3 |
| 4 | Rhythm (`M3`) | Built through compound meter, meter change and the independence check. Hardening and the device pass remain |
| 5+ | Not started | See below |

What "built" does not cover:

- **Phase 1 and 2 audio were accepted by ear** (2026-08-21). Subjective findings were not written
  down. If `PLUCK` is revisited against the Karplus-Strong question in `06-AUDIO-ENGINE.md` §3, that
  verdict wants capturing.
- **Task viability was never checked by performing the exercises** — whether a learner can hold a
  degree across `M12`'s silent gap, whether the ladder leaks the mode at `M10.MIXED_MODE`, whether
  twelve ladder positions are tappable, whether the leading-tone-free `i–iv–v–i` minor cadence
  establishes a key at every fade level.
- **Phase 4 has had no device at all.** Output latency, calibration stability, the Bluetooth path and
  whether the metronome sounds like something a person can play along with are all unmeasured.

## Stage numbering

Every stage number carries its phase: `1.0`–`1.10` for Phase 1, `2.0`–`2.8`, `3.0`–`3.6`, `4.0`–`4.7`.

Phase 1's stages were originally bare integers, `Stage 0`–`Stage 10`, from when Phase 1 was the only
phase. That collided the moment it wasn't. **Renumbered 2026-08-21: `Stage N` in any older text means
`Stage 1.N`** — the mapping to apply to source comments still citing the old form.

## What each phase delivered

**Phase 1** — the whole spine, in ten stages. Gradle skeleton and module wiring with a test that fails
on an `android.*` import in a pure module (1.0); `:core:model` with tuning math and a `Clock`
abstraction (1.1); `:core:audio` — four synthesized timbres, ADSR, mixing, limiter, `AudioTrack`
playback, focus handling (1.2); `:core:curriculum` — skill graph, `M0` and `M2` generators,
`BalancedSampler` (1.3); `:core:engine` — staircase, d-prime, axis scheduler, confusion tracker,
mastery evaluator, FSRS, session composer (1.4); `:core:data` — Room, DataStore, repositories, and
`rebuildFromAttempts()` proving the attempt log is the source of truth (1.5); the headless practice
loop (1.6); the practice UI and the degree ladder (1.7); the diagnostic UI (1.8); home, summary,
progress and settings (1.9); hardening and the release build (1.10).

**Phase 2** — `M9` mode identification, `M10` minor, `M11` chromatic degrees, `M12` audiation, and
data export. Design in `20-PHASE-2-SPEC.md`.

**Phase 3** — the optional sung response across `M2`, `M10`, `M11` and `M12`. Design in
`30-PHASE-3-SPEC.md`.

**Phase 4** — rhythm. Timing infrastructure and the production gate; per-route latency calibration;
patterns, Takadimi syllables and the eight-level metronome fade; tap scoring; recognition items;
the tap surface, feedback and the calibration screen; then compound meter, meter change and the
independence check. Design in `40-PHASE-4-SPEC.md`.

## Later phases

No spec exists for any of these — only a name and a reason.

| Phase | Content | Notes |
|---|---|---|
| 5 | Melodic dictation (`M4`) | |
| 6 | Harmony and harmonic dictation (`M5`, `M6`) | Bass-line first |
| 8 | Advanced/modal (`M8`), desktop build | |

**Phase 7 is retired and its number is not reused.** It was the real-music bridge; the module was
dropped and its identifiers removed from the code (`02-PEDAGOGY.md` §9). The gap stays a gap for the
same reason `03-CURRICULUM.md` §1 forbids recycling a shipped `SkillId`: a number that once meant
something specific is worse than useless when it silently starts meaning something else.
