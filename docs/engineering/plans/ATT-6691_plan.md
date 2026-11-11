# Stage 3: Implementation Plan - ATT-6691: Backfill Climbs.db from Historical Workouts & Backup Parity

**Ticket**: [ATT-6691](https://rainerblind.atlassian.net/browse/ATT-6691)  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-MAP-066` (`REQ-MAP-066.1`..`REQ-MAP-066.3`)  
**Test Mapping**: `TST-MAP-068` (`TST-MAP-068.1`..`TST-MAP-068.4`)  
**Branch**: `feature/ATT-6691`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background
`ATT-2766` added climb extraction for fresh imports/finishes (`isNewImportOrFinish == true`), but legacy workouts and restored `.attbackup` archives lacking `Climbs.db` leave athletes without local climb coverage (`Rule 80`). Furthermore, `Climbs.db` is unmanaged in `DatabaseSchemaRegistry` and `DatabaseConnectionManager`, risking WAL corruption and omitted backups.

---

## 2. Traceability & Requirements Mapping
* **REQ-MAP-066.1** $\rightarrow$ `TST-MAP-068.1`: One-time idempotent background historical backfill (`climbs_workout_backfill_v1`) in `WorkoutRepository`.
* **REQ-MAP-066.2** $\rightarrow$ `TST-MAP-068.2`: Register `Climbs.db` in `DatabaseSchemaRegistry` (v1) and `DatabaseConnectionManager.getActiveDatabases()`.
* **REQ-MAP-066.3** $\rightarrow$ `TST-MAP-068.3`: Backup restore hook in `MigrationEngine` resetting `climbs_workout_backfill_v1` when `Climbs.db` is missing from archive.
* **REQ-MAP-066** $\rightarrow$ `TST-MAP-068.4`: Targeted and full regression suite verification.

---

## 3. System Invariants & Preserved Behavior
1. **Idempotency**: Flag `climbs_workout_backfill_v1` ensures exactly 1 execution; subsequent invocations are O(1) no-ops.
2. **Spatial Deduplication**: 50m start/summit radius prevents duplicate climb generation across workouts and routes.
3. **Non-Blocking IO**: Backfill runs asynchronously on `Dispatchers.IO` with zero UI/startup thread disruption.
4. **Human Gate**: Parent ticket advances to `Final Review (Human)`, never directly to `Erledigt`.

---

## 4. Proposed Architectural Changes
* **`ClimbsDatabaseManager.kt`**: Expose `val database: SQLiteDatabase` and `fun getDatabase(): SQLiteDatabase`.
* **`DatabaseSchemaRegistry.kt`**: Map `Climbs.db` to schema version 1.
* **`DatabaseConnectionManager.kt`**: Include `ClimbsDatabaseManager.database` in `getActiveDatabases()`.
* **`MigrationEngine.kt`**: In `performFullRestore()`, detect if `Climbs.db` was missing from archive; if so, clear `climbs_workout_backfill_v1` from restored preferences.
* **`WorkoutRepository.kt`**: Declare `PREF_CLIMBS_WORKOUT_BACKFILL_V1 = "climbs_workout_backfill_v1"`, implement `backfillHistoricalClimbs()`, `triggerHistoricalClimbBackfillIfPending()`, and invoke in `init {}`.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Database Registry & Connection Quiescence Parity (`REQ-MAP-066.2`)
* Files: `ClimbsDatabaseManager.kt`, `DatabaseSchemaRegistry.kt`, `DatabaseConnectionManager.kt`.
* Changes: Expose `database` handle on `ClimbsDatabaseManager`; register `Climbs.db` in `DatabaseSchemaRegistry` and `DatabaseConnectionManager.getActiveDatabases()`.

### Step 2: Backup Restore Self-Healing Hook (`REQ-MAP-066.3`)
* Files: `MigrationEngine.kt`.
* Changes: If `Climbs.db` was absent from restored archive, reset `climbs_workout_backfill_v1` in shared preferences after staging swap.

### Step 3: Historical Workout Backfill Pipeline (`REQ-MAP-066.1`)
* Files: `WorkoutRepository.kt`.
* Changes: Query finished outdoor GPS workouts (`Sport IN ('BIKE', 'RUN', 'UNKNOWN')`, `trainer = 0` / null, non-empty `mapPolyline`), extract climbs via `ClimbDetector.detectClimbs`, insert via `insertClimbsWithDeduplicationBatch`, refresh `LiveClimbsRepository`, and set `climbs_workout_backfill_v1 = true`. Wire into `init {}`.

### Step 4: Unit Test Implementation (`TST-MAP-068.1`..`TST-MAP-068.3`)
* Files: `WorkoutHistoricalClimbBackfillTest.kt`, `ClimbsDatabaseRegistryTest.kt`, `MigrationEngineClimbsRestoreTest.kt`.
* Changes: Author comprehensive unit tests covering backfill execution, idempotency, schema/connection registry, and backup restore reset.

### Step 5: Targeted Unit Test Execution (`TST-MAP-068.4`)
* Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutHistoricalClimbBackfillTest" --tests "com.atrainingtracker.trainingtracker.database.ClimbsDatabaseRegistryTest" --tests "com.atrainingtracker.trainingtracker.migration.MigrationEngineClimbsRestoreTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted tests under `parallel_sprint_lock.py`, followed by clean-room test suite in Stage 5.
* **Rollback**: Clean revert of `feature/ATT-6691` branch without impacting `sprint/2026-46.2`.
