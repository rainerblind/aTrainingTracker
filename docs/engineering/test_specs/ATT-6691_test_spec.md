# Stage 2: Requirement & Test Specification - ATT-6691: Backfill Climbs.db from Historical Workouts & Backup Parity

**Ticket**: [ATT-6691](https://atrainingtracker.atlassian.net/browse/ATT-6691) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **Branch**: `feature/ATT-6691`  
**Requirement Mapping**: `REQ-MAP-066` (`REQ-MAP-066.1`..`REQ-MAP-066.3`) | **Test Spec ID**: `TST-MAP-068` (`TST-MAP-068.1`..`TST-MAP-068.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (`REQ-MAP-066`)

### 1.1 Problem Statement & Rationale
Prior to `V5.0.0`, `Climbs.db` did not exist; `ATT-2766` added post-workout climb extraction exclusively for forward-looking finished or imported workouts (`isNewImportOrFinish == true`). Under `Rule 80` (*Three-Way Parity: Live + Import + Historical Database Self-Healing*), legacy workouts and restored `.attbackup` archives lacking `Climbs.db` leave athletes with empty climb coverage. Furthermore, `Climbs.db` is unmanaged in `DatabaseSchemaRegistry` and `DatabaseConnectionManager`, risking WAL corruption and backup omissions.

### 1.2 Functional & Architectural Requirements
* **REQ-MAP-066.1** (*One-Time Historical Workout Backfill*): On startup or initialization, `WorkoutRepository` SHALL inspect `SharedPreferences` for boolean flag `climbs_workout_backfill_v1`. If false, it SHALL asynchronously dispatch an idempotent background pass on `Dispatchers.IO` iterating finished outdoor GPS workouts (`Sport IN ('BIKE', 'RUN', 'UNKNOWN')`, `trainer = 0` or null, non-empty `mapPolyline` and $\ge 2$ trackpoints). Qualifying ascents SHALL be extracted via `ClimbDetector.detectClimbs(points, routeId = null, context = application)` and persisted via `ClimbsDatabaseManager.insertClimbsWithDeduplicationBatch(detected, thresholdMeters = 50.0f)`. Upon completion, `climbs_workout_backfill_v1` SHALL be marked true and `LiveClimbsRepository.refreshClimbs()` SHALL be invoked. Subsequent calls SHALL be O(1) no-ops.
* **REQ-MAP-066.2** (*Database Registry & Connection Quiescence Parity*): `DatabaseSchemaRegistry` SHALL register `ClimbsDbHelper.DATABASE_NAME` (`Climbs.db`) mapped to version 1. `DatabaseConnectionManager.getActiveDatabases(context)` SHALL include `ClimbsDatabaseManager.getInstance(context).database`, participating in WAL checkpointing (`checkpointWithRetry`), safe connection shutdown, and VACUUM snapshots. `ClimbsDatabaseManager` SHALL expose `val database: SQLiteDatabase get() = dbHelper.writableDatabase` and `fun getDatabase(): SQLiteDatabase`.
* **REQ-MAP-066.3** (*Backup Restore Self-Healing*): In `MigrationEngine.performFullRestore()`, when restoring an archive where `Climbs.db` was absent from the staged databases directory (legacy pre-V5.0.0 backup), the system SHALL clear/reset `climbs_workout_backfill_v1` in restored preferences to trigger automatic historical backfill on next launch.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Historical Backfill Execution)**: *Given* an athlete updating from a legacy version with historical outdoor workouts and unset `climbs_workout_backfill_v1`, *When* `WorkoutRepository` initializes, *Then* qualifying climbs are extracted into `Climbs.db` with 50m spatial deduplication, `climbs_workout_backfill_v1` is set to true, and `LiveClimbsRepository.refreshClimbs()` is called.
* **AC-2 (Backfill Idempotency)**: *Given* `climbs_workout_backfill_v1` is true, *When* `WorkoutRepository` starts or backfill is triggered, *Then* zero database scanning occurs and the function returns 0 immediately.
* **AC-3 (Schema & Connection Registry Parity)**: *Given* `DatabaseSchemaRegistry` and `DatabaseConnectionManager`, *When* queried, *Then* `Climbs.db` is present in `getSupportedDatabases()` (version 1) and its open SQLite connection is included in `getActiveDatabases(context)`.
* **AC-4 (Legacy Backup Restore Self-Healing)**: *Given* an `.attbackup` archive lacking `Climbs.db`, *When* `MigrationEngine.performFullRestore` executes, *Then* `climbs_workout_backfill_v1` is cleared/set to false in preferences so the backfill re-runs upon app restart.

---

## 2. Test Specification & Traceability Matrix (`TST-MAP-068`)

| Req ID | Test Case | Target Component | Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-MAP-066.1` | `TST-MAP-068.1` | `WorkoutHistoricalClimbBackfillTest.kt` | Verify historical backfill extracts climbs from finished outdoor sessions, applies 50m deduplication, sets preference flag, and asserts subsequent run is idempotent no-op. |
| `REQ-MAP-066.2` | `TST-MAP-068.2` | `ClimbsDatabaseRegistryTest.kt` | Verify `DatabaseSchemaRegistry` includes `Climbs.db` with version 1, and `DatabaseConnectionManager.getActiveDatabases()` returns `Climbs.db` handle for WAL quiescence. |
| `REQ-MAP-066.3` | `TST-MAP-068.3` | `MigrationEngineClimbsRestoreTest.kt` | Verify `MigrationEngine.performFullRestore()` resets `climbs_workout_backfill_v1` when restoring an archive lacking `Climbs.db`. |
| `REQ-MAP-066` | `TST-MAP-068.4` | Targeted Test Suite | Execute tests across `database.Climbs*`, `migration.Database*`, and `WorkoutHistoricalClimbBackfillTest` asserting 100% pass rate. |

---

## 3. Localization Audit
* **Parity Status**: Audited. No user-facing strings added or modified; 100% parity preserved across all 9 supported locales.
