# Stage 3: Implementation Plan - ATT-6716: FIT HRV Reset, Incremental Flush & 5s Grid Recomputation

**Ticket**: [ATT-6716](https://rainerblind.atlassian.net/browse/ATT-6716)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PER-034` (`REQ-PER-034.1`, `REQ-PER-034.2`, `REQ-PER-034.3`, `REQ-PER-034.5`)  
**Test Mapping**: `TST-PER-042` (`TST-PER-042.1`, `TST-PER-042.2`, `TST-PER-042.3`, `TST-PER-042.5`, `TST-PER-042.6`)  
**Branch**: `feature/ATT-6716` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
Sprint `2026-46.1` follow-up identified three critical defects:
1. `FitWorkoutParser.kt` flattens `HrvMesg` intervals and `LegacyImportEngine.kt` passes constant `startMs` for all beats; after 2000 ms, `drift > 2000ms` resets timestamps back to `startMs`, corrupting timestamps and writing NULL post-workout HRV streams.
2. `TrackerService.java` buffers all live RR intervals in RAM (`mLiveRrQueue`) without incremental flushing, fails to gate pre-start/paused beats, resets `mRrBeatIndexCounter` to 0 on resume, and races `HrvPostWorkoutRecomputer` against summary extrema without dispatching `refreshWorkout`.
3. `HrvPostWorkoutRecomputer.kt` scans all beats quadratically and computes DFA $\alpha_1$ at 1 Hz ($>2 \cdot 10^8$ ops on 3h rides), omits session SDNN, and lacks a `UNIQUE(workout_id, beat_index)` index in `WorkoutRrDbHelper.kt`.

---

## 2. Traceability & Requirements Mapping
* `REQ-PER-034.1` (Schema v2, UNIQUE index & repository routing) $\rightarrow$ `TST-PER-042.1` (`WorkoutRrDatabaseManagerTest`)
* `REQ-PER-034.2` (Incremental batched flushing, state gating & resume counter) $\rightarrow$ `TST-PER-042.2` (`WorkoutRrRepositoryTest`)
* `REQ-PER-034.3` (Two-pointer 5s grid sliding window, session SDNN & refresh) $\rightarrow$ `TST-PER-042.3` (`HrvPostWorkoutRecomputerTest`)
* `REQ-PER-034.5` (Interleaved FIT anchor reconstruction & multi-minute import) $\rightarrow$ `TST-PER-042.5` (`LegacyImportEngineFitHrvTest`)
* `REQ-PRO-001` (Tier 1 regression suite) $\rightarrow$ `TST-PER-042.6` (Clean-room build & test pass)

---

## 3. System Invariants & Preserved Behavior
1. **Mathematical Invariants**: 120s centered DFA $\alpha_1$ ($\le 5\%$ artifact gate) and 60s centered rMSSD math remain strictly intact; 5s grid decimation aligns with `ATT-3543` Scope §2.
2. **Schema & Migration Safety**: Bump `WorkoutRrDbHelper.DB_VERSION` from 1 to 2; `onUpgrade` deduplicates existing entries and creates unique index without table rewrite; `DatabaseSchemaRegistry` reflects v2 (Rule 62, Rule 64).
3. **Repository Routing**: All database access routed through `WorkoutRrRepository` (never bypassing to raw database managers).

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Interleaved FIT Anchor Reconstruction (`FitWorkoutParser.kt` & `LegacyImportEngine.kt`)
* Files: `FitWorkoutParser.kt`, `LegacyImportEngine.kt`.
* Action: Define `FitRrBeat(rrMs, anchorTimestampMs)`. Track active `RecordMesg` timestamp during `MesgBroadcaster` parsing and anchor each `HrvMesg` beat. In `LegacyImportEngine.kt`, pass `beat.anchorTimestampMs` to `reconstructor.reconstruct()`, enabling monotonic progression and resyncing strictly across pauses.

### Step 2: Incremental Batched Flushing & Resume Counter (`TrackerService.java`)
* Files: `TrackerService.java`.
* Action: Gate `onRrBeat` with `mStatus == STATUS.TRACKING && mWorkoutID > 0`. Flush `mLiveRrQueue` in batched transactions every 100 beats or 30s. On `START_NORMAL`, set counter to 0; on resume, set counter to `getWorkoutRrRepository().getMaxBeatIndex(workoutId) + 1`. In `endWorkout()`, flush remaining queue, sequence recompute after summary finalization, and call `WorkoutRepository.refreshWorkout(workoutId)`.

### Step 3: Schema v2 Migration & UNIQUE Constraint (`WorkoutRrDbHelper.kt`, `WorkoutRrDatabaseManager.kt`, `WorkoutRrRepository.kt`)
* Files: `WorkoutRrDbHelper.kt`, `WorkoutRrDatabaseManager.kt`, `WorkoutRrRepository.kt`, `DatabaseSchemaRegistryHrvTest.kt`.
* Action: Bump `DB_VERSION` to 2. In `onUpgrade`, deduplicate and create `CREATE UNIQUE INDEX IF NOT EXISTS idx_workout_rr_workout_beat`. Add `getMaxBeatIndex` and `insertIntervals` with `INSERT OR REPLACE`. Route `HrvPostWorkoutRecomputer` through `WorkoutRrRepository`.

### Step 4: Two-Pointer 5s Grid Recomputation & Session SDNN (`HrvPostWorkoutRecomputer.kt`, `SensorType.java`)
* Files: `HrvPostWorkoutRecomputer.kt`, `SensorType.java`, `strings_hrv.xml`.
* Action: Add `SensorType.HRV_SDNN`. In `HrvPostWorkoutRecomputer.kt`, sort beats monotonically and maintain two advancing pointers for `[t - 60s, t + 60s]` and `[t - 30s, t + 30s]`. Compute DFA $\alpha_1$ and rMSSD on a 5s grid, step-latching to intermediate 1-Hz samples. Compute unbiased session SDNN across valid non-artifact beats ($N \ge 2$, artifact rate $\le 5\%$) and persist to `WorkoutSummaries` via `updateExtremaValue`. Dispatch `WorkoutRepository.refreshWorkout(workoutId)` on completion.

### Step 5: Unit Tests & Regression Verification
* Files: `LegacyImportEngineFitHrvTest.kt`, `WorkoutRrDatabaseManagerTest.kt`, `HrvPostWorkoutRecomputerTest.kt`, `DatabaseSchemaRegistryHrvTest.kt`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.tracker.*" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 5. Verification & Rollback Plan
* **Verification**: Tier 1 Robolectric/JVM tests verifying multi-minute FIT import, 5s grid evaluation, SDNN persistence, and v2 schema migration.
* **Rollback**: Isolated branch `feature/ATT-6716` allows clean reset without touching sprint branch.
