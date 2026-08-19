# 05 — Data Model

Local only. Room for relational state, DataStore Preferences for settings. No network, no sync, no export in Phase 1 (export is a Phase 2 candidate).

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
| `resumeStateJson` | String? | Non-null if interrupted; enables resume |

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
| `daily_reminder_enabled` | Boolean | `false` |
| `daily_reminder_time` | String? | null |

`daily_reminder_enabled` defaults to **false**. Opt-in only. See `08-UI-SPEC.md` §7.

`module2_intro_seen` records that the Module 2 explanation screen and its worked example have been shown once — `11-ONBOARDING-CLARITY.md` §5: "shown automatically once, on first encounter with that exercise type," and never again automatically. It gates the automatic showing only; the screen stays reachable on demand forever, and recalling it does not depend on or change this flag.

---

## 4. Migrations

- Room `exportSchema = true`. Schema JSON committed to the repo.
- Every schema change ships an explicit `Migration`. `fallbackToDestructiveMigration()` is **forbidden** — losing a user's ear training progress is unacceptable and unrecoverable.
- Every migration gets a `MigrationTestHelper` test that loads a real prior-version database and verifies the upgrade.

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
