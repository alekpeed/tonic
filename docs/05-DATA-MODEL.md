# 05 — Data Model

Local only. Room for relational state, DataStore Preferences for settings. No network, no sync.

Export landed in Phase 2 (`20-PHASE-2-SPEC.md` §6): the whole database is readable out as one JSON file from Settings, through the system's create-document picker, so the user chooses the destination and the app needs no storage permission. It remains local by construction — the app declares no network permission at all, so an export is a file and can only ever be a file. There is deliberately **no import**. Two fields are deliberately omitted from an export: `resumeStateJson`, a mid-session scratchpad that is not progress, and `amusiaIndicatorFlag`, which is internal routing state that `02-PEDAGOGY.md` §8 forbids ever surfacing evaluatively — a shareable file carrying a column legible as "amusia" is exactly that.

Single-user. There is no user table and no user ID column. If multi-profile is ever needed it becomes a schema migration, and that is an acceptable cost versus carrying a dead foreign key everywhere now.

---

## 1. Room entities

### `attempts`

The append-only event log. Everything else is derivable from this table; treat it as the source of truth and the other tables as materialized state.

| Column | Type | Notes |
|---|---|---|
| `id` | Long PK autogen | |
| `skillId` | String | e.g. `M2.DEG_SET_2` |
| `sessionId` | Long | FK → `sessions.id` |
| `itemSeed` | Long | Regenerates the exact item |
| `axisLevelsJson` | String | Snapshot of axis levels at generation time |
| `targetLabel` | String | Canonical answer, e.g. `"3"` or `"HIGHER"` |
| `responseLabel` | String? | Null if skipped/abandoned |
| `correct` | Boolean | |
| `latencyMs` | Long | Recorded, never scored. See `02-PEDAGOGY.md` §6 |
| `replayCount` | Int | How many times the user re-heard the item |
| `keyPitchClass` | Int | 0–11 |
| `targetMidi` | Int | |
| `timbreId` | String | |
| `cadenceFadeLevel` | Int | Denormalized from axes for cheap querying — this is the axis that matters most |
| `timestamp` | Long | Epoch millis, injected clock |
| `isWarmup` | Boolean | Recorded but excluded from mastery evaluation and the staircase — `07-ADAPTIVE-ENGINE.md` §8 |
| `isAbandoned` | Boolean | Interruption/session kill (incoming call, headphone unplug, backgrounded, rotated) discarded the item rather than scoring it — excluded from every adaptive computation |
| `isIndependenceCheckProbe` | Boolean | One of the 30 forced-`CADENCE_FADE`-L6 `M2.INDEPENDENCE_CHECK` probes (`03-CURRICULUM.md` §5.6). Still folded into axis-level replay (a failed check lowers `CADENCE_FADE`, and that has to survive a rebuild the same way any other axis move does) but excluded from the ordinary mastery window and FSRS review-block accumulation |
| `inputMethod` | String | `TAP` or `SUNG` — how the **scoring** answer was given (`30-PHASE-3-SPEC.md` §4). Added in schema v2, defaulting to `TAP`: before Phase 3 the degree ladder was the only way to answer anything, so for every earlier row that is a statement of fact, not a guess. Recorded, never adapted on — §2 makes sung and tapped attempts one `SkillState`. **Always `TAP` on `M12.*`, including when the learner sang**: a prediction item's answer is the three-button judgment, and the sung pitch supplements it rather than replacing it (`30-PHASE-3-SPEC.md` §5.4). `sungCents` is what marks such a row as sung |
| `sungCents` | Int? | Signed deviation of the sung pitch from the degree it is measured against; negative is flat. On a recognition item that is the degree the learner answered with; on a prediction item it is the degree the item **named** — the one they were asked to audiate, not the note that eventually sounded, which they had not heard when they sang (`30-PHASE-3-SPEC.md` §5.4). Folded into ±600 cents, so the same degree in any octave reads alike (`sung_octave_agnostic`). Null for every tapped attempt and for any capture whose pitch could not be read. **For display and analysis only** (`30-PHASE-3-SPEC.md` §7) — never read by `Staircase`, `AxisScheduler`, `MasteryEvaluator` or `ConfusionTracker`, the same guarantee `replayCount` has and for the same reason. `SungDataIsNeverAdaptiveTest` asserts it by replaying one history twice and demanding an identical `SkillState`; `SungPredictionSimulationTest` asserts the same over a 400-item prediction run |

Indices: `(skillId, timestamp)`, `(sessionId)`, `(skillId, targetLabel, responseLabel)`.

Retention: keep everything. An attempt row is small and the analysis value is high. Revisit only if a real device shows a problem.

### `skill_states`

Materialized per-skill state. Rebuildable from `attempts` — provide a rebuild function and test it.

| Column | Type | Notes |
|---|---|---|
| `skillId` | String PK | |
| `axisLevelsJson` | String | Current level per `DifficultyAxis` |
| `staircaseStateJson` | String | Reversal history, current step size, direction, consecutive-correct counter, per axis |
| `activeAxis` | String? | Which axis the scheduler is currently moving |
| `masteryStatus` | String | `LOCKED`, `AVAILABLE`, `IN_PROGRESS`, `MASTERED` |
| `masteredAt` | Long? | |
| `fsrsStability` | Double | |
| `fsrsDifficulty` | Double | |
| `fsrsLastReview` | Long? | |
| `fsrsDue` | Long? | |
| `fsrsReps` | Int | |
| `fsrsLapses` | Int | |
| `totalAttempts` | Int | |
| `updatedAt` | Long | |

### `confusion_state`

| Column | Type |
|---|---|
| `skillId` | String PK |
| `stateJson` | String |
| `updatedAt` | Long |

**Deviation from the original plan** (an aggregated `confusion_cells` table, one row per `(skillId, targetLabel, responseLabel)` with plain `count`/`windowCount` columns): that shape can *display* a snapshot but can't correctly *maintain* a true last-100-attempts sliding window on its own — when the window slides and the oldest pair drops out, you need to know which specific pair that was, and an aggregated count can't tell you. `stateJson` stores the engine's actual `ConfusionState` (the ordered `recentPairs` list plus `allTimeCounts`) as JSON, the same way `staircaseStateJson` already stores other algorithmic internal state. The aggregated `ConfusionMatrix` a caller needs is derived on demand from this via `ConfusionTracker.toMatrix()`. Same tolerance rules as §2 apply.

### `sessions`

| Column | Type | Notes |
|---|---|---|
| `id` | Long PK autogen | |
| `startedAt` | Long | |
| `endedAt` | Long? | Null = abandoned or in progress |
| `plannedItemCount` | Int | |
| `completedItemCount` | Int | |
| `rootSeed` | Long | The session seed; all item seeds derive from it |
| `resumeStateJson` | String? | Non-null if interrupted; enables resume. Carries the plan, the last scored slot index, and the remaining wall-clock budget in seconds (`07-ADAPTIVE-ENGINE.md` §8) — nullable, so rows written before the budget field existed still decode |

### `diagnostic_results`

One row per completed diagnostic run. Keep history — re-screening after the M1 path needs the prior result for comparison.

| Column | Type |
|---|---|
| `id` | Long PK autogen |
| `pitchDirectionThresholdCents` | Int |
| `discriminationDPrime` | Double |
| `tonalMemorySpan` | Int |
| `amusiaIndicatorFlag` | Boolean |
| `recommendedEntry` | String |
| `initialAxisLevelsJson` | String |
| `seed` | Long |
| `completedAt` | Long |

`amusiaIndicatorFlag` is internal. No DAO method returning it may be called from a composable. Enforce by exposing it only through a repository method used by the placement logic.

---

## 2. JSON columns

`axisLevelsJson`, `staircaseStateJson`, `initialAxisLevelsJson`, and `resumeStateJson` are `kotlinx.serialization` encoded.

Rules:

- Use `@Serializable` data classes in `:core:model`, not maps of strings.
- Every serializable state class carries a `schemaVersion: Int`.
- Deserialization must tolerate unknown fields (`ignoreUnknownKeys = true`) and must have an explicit fallback path if `schemaVersion` is newer than the code understands: do not crash, do not silently misinterpret — degrade to defaults and log.

Do not query inside JSON columns. Anything you need to filter on gets its own column (as `cadenceFadeLevel` does).

---

## 3. DataStore Preferences

Settings only. No progress data.

| Key | Type | Default |
|---|---|---|
| `label_style` | String | `NUMBERS` (alt: `SOLFEGE`) |
| `reference_a4_hz` | Float | `440.0` |
| `session_length_minutes` | Int | `5` |
| `haptics_enabled` | Boolean | `true` |
| `sound_effects_enabled` | Boolean | `true` |
| `theme_mode` | String | `SYSTEM` |
| `reduce_motion` | Boolean | `false` |
| `onboarding_completed` | Boolean | `false` |
| `diagnostic_completed` | Boolean | `false` |
| `module2_intro_seen` | Boolean | `false` |
| `module9_intro_seen` | Boolean | `false` |
| `module10_intro_seen` | Boolean | `false` |
| `module11_intro_seen` | Boolean | `false` |
| `module12_intro_seen` | Boolean | `false` |
| `mixed_mode_intro_seen` | Boolean | `false` |
| `sung_response_enabled` | Boolean | `false` |
| `sung_octave_agnostic` | Boolean | `true` |
| `sung_response_intro_seen` | Boolean | `false` |
| `rhythm_calibration_offset_speaker` | Float? | absent |
| `rhythm_calibration_spread_speaker` | Float? | absent |
| `rhythm_calibration_taps_speaker` | Int? | absent |
| `rhythm_calibration_offset_wired` | Float? | absent |
| `rhythm_calibration_spread_wired` | Float? | absent |
| `rhythm_calibration_taps_wired` | Int? | absent |
| `daily_reminder_enabled` | Boolean | `false` |
| `daily_reminder_time` | String? | null |

`daily_reminder_enabled` defaults to **false**. Opt-in only. See `08-UI-SPEC.md` §7.

The six `rhythm_calibration_*` keys are Phase 4's (`40-PHASE-4-SPEC.md` §4.3), added at Stage 4.1.
**Absent is a meaningful state, not a missing value with a default.** An uncalibrated route blocks
tapping and explains why (§9 simulation 6); a calibration read as `0.0` instead would be a claim that
this device has no output latency, which is never true and is wrong by tens of milliseconds in the
direction that makes a well-timed learner look like they are rushing. Nothing anywhere substitutes a
default for an absent offset.

Two deliberate deviations from that spec's own settings list, which names only the two offset keys:

- **The spread is stored too.** §4.3 step 5 measures it and then requires that a learner whose taps
  scatter gets wider tolerance windows rather than a failure. A number computed and discarded cannot
  widen anything.
- **The tap count is stored too.** A constant derived from the bare minimum of six taps and one derived
  from a full run deserve different confidence, and nothing else in the record distinguishes them.

Absence is keyed on the *offset*. A stored offset with a missing spread is a partial write, not an
uncalibrated route, and defaulting the two diagnostic fields beats discarding a real measurement.

`SPEAKER` and `WIRED` are the only two slots. USB output shares the wired constant — an assumption
recorded on `AudioOutputRoute.USB` and in `40-PHASE-4-SPEC.md` §4.3, not a measurement, and if a device
shows it wrong this grows a third slot and a third pair of keys rather than being quietly repointed.

The three `sung_*` keys are Phase 3's (`30-PHASE-3-SPEC.md` §7). `sung_response_enabled` defaults false
because §6.1 forbids requesting the microphone from anyone who has not actively opted into singing, and
a default of true would do exactly that. `sung_octave_agnostic` defaults **true** because §3 mitigation 3
makes octave-agnosticism a mitigation against the phase's central risk rather than a preference: forcing
a register tests vocal range, not hearing. `sung_response_intro_seen` is `08-UI-SPEC.md` §3a's per-shape
flag applied to singing, which is its own task shape — same question, different answer control.

`module9_intro_seen`, `module10_intro_seen`, `module11_intro_seen`, `module12_intro_seen` and `mixed_mode_intro_seen` were the same mechanism for Phase 2's task shapes, and are retired on the same terms. The per-shape split they encoded still holds in the code that replaced them: each module has its own explanation, and having met one says nothing about the others.

**`module2_intro_seen` and its Phase 2 siblings are retired: still stored, no longer written or read.** They recorded that a module's explanation screen had been shown once, and gated it from ever showing automatically again. That rule was dropped on 2026-08-22 by the maintainer's instruction — a module's explanation now appears every time the learner enters that module (`08-UI-SPEC.md` §3a, `11-ONBOARDING-CLARITY.md` §5), governed by in-memory state for the current visit and by nothing durable at all.

The keys and their `AppSettings` fields are kept rather than migrated away, so the stored schema stays stable and an install carrying `true` values from an older build needs no migration: nothing consults them. Do not repurpose them, and do not reintroduce a read without changing §3a first.

---

## 4. Migrations

- Room `exportSchema = true`. Schema JSON committed to the repo.
- Every schema change ships an explicit `Migration`. `fallbackToDestructiveMigration()` is **forbidden** — losing a user's ear training progress is unacceptable and unrecoverable.
- Every migration gets a `MigrationTestHelper` test that loads a real prior-version database and verifies the upgrade. The test must write rows at the old version and read them back after — asserting the migration *ran* is not the same as asserting the data *survived it*, and only the second one matters.
- Migrations live in `core/data/.../db/Migrations.kt` and every one is registered in `Migrations.ALL`, which `DatabaseModule` passes to the builder. A migration that exists but was never registered passes its own test and crashes on a device, so the migration tests include one case that goes through `Room.databaseBuilder` with that list rather than naming a migration directly.
- CI fails the build if a run generates schema JSON that is not committed, and prints the file. Schemas are only useful if they are in the repo before the next migration needs them.

### Version history

| Version | Change |
|---|---|
| 1 | Initial schema |
| 2 | `attempts` gains `inputMethod` and `sungCents` for Phase 3's optional sung response (`30-PHASE-3-SPEC.md` §7). Two added columns, both defaulted; the append-only log is never rewritten |
| 3 | `attempts` gains the six rhythm columns of `40-PHASE-4-SPEC.md` §8 — `tapTimestampsMs`, `calibrationOffsetUsedMs`, `toleranceUsedMs`, `perEventAsynchronyMs`, `extraTaps`, `missedTaps`. All nullable, none defaulted to a value: null is the honest reading for every row written before Phase 4, because "no taps were recorded" and "the learner tapped nothing" are different facts and only the first ever happened to a pitch attempt |

The two list columns hold JSON text rather than rows in a related table. `attempts` is an append-only
log that is replayed whole (§1), a tap list is meaningless apart from the attempt it belongs to, and
nothing will ever query across taps — a join table would add a migration surface and a delete cascade
in exchange for a query nobody writes. `perEventAsynchronyMs` is a list of *nullable* numbers, because
a missed event has no asynchrony and a zero there would read as a tap that landed exactly on it.

Tap times are stored as **corrected milliseconds from the pattern's start**, not as raw monotonic
instants. A raw instant means nothing once the playback it was measured against is over; the offsets
are what `40-PHASE-4-SPEC.md` §4.4 needs to make a recorded session replayable. The calibration
constant and the tolerance window that were applied are stored beside them, so an attempt can be
re-scored later against exactly what it faced rather than against today's settings.

---

## 5. Repositories (public surface of `:core:data`)

```kotlin
interface AttemptRepository {
    suspend fun record(attempt: Attempt)
    fun recentAttempts(skillId: SkillId, limit: Int): Flow<List<Attempt>>
    suspend fun windowFor(skillId: SkillId, size: Int): List<Attempt>
}

interface SkillStateRepository {
    fun observe(skillId: SkillId): Flow<SkillState>
    fun observeAll(): Flow<Map<SkillId, SkillState>>
    suspend fun update(state: SkillState)
    suspend fun dueForReview(now: Instant): List<SkillId>
    suspend fun rebuildFromAttempts()   // must be tested against a known log
}

interface ConfusionRepository {
    suspend fun record(skillId: SkillId, target: String, response: String)
    suspend fun matrixFor(skillId: SkillId): ConfusionMatrix
}

interface SessionRepository { /* create, complete, findResumable */ }

interface DiagnosticRepository {
    suspend fun save(result: DiagnosticResult)
    suspend fun latest(): DiagnosticResult?
}

interface SettingsRepository { /* typed Flow accessors over DataStore */ }
```

Room entities and DAOs are `internal` to `:core:data`. Features see domain types only.

---

## 6. Backup

`android:allowBackup` — set **false** for Phase 1. Auto Backup restoring a partial or stale Room database onto a live install causes corruption that is worse than starting over. Revisit with an explicit export/import feature later.
