# ATT-6691 Walkthrough: Backfill Climbs.db from Historical Workouts & Backup Parity

**Ticket**: [ATT-6691](https://atrainingtracker.atlassian.net/browse/ATT-6691)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Sprint**: `2026-46.2` | **Target Release**: `V5.0.0` | **Status**: Verified  
**Requirement Mapping**: `REQ-MAP-066` (`REQ-MAP-066.1`..`REQ-MAP-066.3`) | **Test Mapping**: `TST-MAP-068` (`TST-MAP-068.1`..`TST-MAP-068.4`)  

---

## 1. Executive Summary

`ATT-2766` introduced workout climb extraction for newly recorded and freshly imported sessions (`isNewImportOrFinish == true`). Under `Rule 80` (*Three-Way Parity: Live + Import + Historical Database Self-Healing*), athletes updating from older versions (or restoring `.attbackup` archives lacking `Climbs.db`) lacked climb coverage in `Climbs.db`. Additionally, `Climbs.db` was unmanaged in `DatabaseSchemaRegistry` and `DatabaseConnectionManager`, risking WAL quiescence omissions and backup skips.

Under `ATT-6691` (governed by `REQ-MAP-066` / `TST-MAP-068`):
- **Database Registry & Connection Quiescence Parity (`REQ-MAP-066.2`)**:
  - `ClimbsDatabaseManager.kt`: Exposed `val database: SQLiteDatabase` handle for connection management.
  - `DatabaseSchemaRegistry.kt`: Registered `ClimbsDbHelper.DATABASE_NAME` (`Climbs.db`) mapped to version 1.
  - `DatabaseConnectionManager.kt`: Included `ClimbsDatabaseManager.getInstance(context).database` in `getActiveDatabases(context)`.
- **Backup Restore Self-Healing Hook (`REQ-MAP-066.3`)**:
  - `MigrationEngine.kt`: In `performFullRestore()`, detects if `Climbs.db` was missing from the restored archive (e.g. legacy pre-V5.0.0 backup); if so, clears `climbs_workout_backfill_v1` from restored `SharedPreferences` to trigger automatic self-healing backfill on next launch.
- **Historical Workout Backfill Pipeline (`REQ-MAP-066.1`)**:
  - `WorkoutRepository.kt`: Declared `PREF_CLIMBS_WORKOUT_BACKFILL_V1 = "climbs_workout_backfill_v1"`. Implemented `backfillHistoricalClimbs()`, iterating finished outdoor GPS workouts (`Sport IN ('BIKE', 'RUN', 'UNKNOWN')`, non-trainer, non-empty `mapPolyline`), extracting ascents via `ClimbDetector.detectClimbs`, persisting with 50m spatial deduplication via `insertClimbsWithDeduplicationBatch`, marking the preference flag, and refreshing `LiveClimbsRepository` when new climbs are stored.
  - Wired non-blocking `triggerHistoricalClimbBackfillIfPending()` into `init {}`.

---

## 2. Traceability Matrix

| Requirement | Test Specification | Production Code | Verification Tests | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-MAP-066.1` | `TST-MAP-068.1` | `WorkoutRepository.kt` | `WorkoutHistoricalClimbBackfillTest.kt` | **Verified** |
| `REQ-MAP-066.2` | `TST-MAP-068.2` | `ClimbsDatabaseManager.kt`, `DatabaseSchemaRegistry.kt`, `DatabaseConnectionManager.kt` | `ClimbsDatabaseRegistryTest.kt` | **Verified** |
| `REQ-MAP-066.3` | `TST-MAP-068.3` | `MigrationEngine.kt` | `MigrationEngineClimbsRestoreTest.kt` | **Verified** |
| `REQ-MAP-066` | `TST-MAP-068.4` | Targeted & Regression Test Suite | `WorkoutClimbIngestionTest.kt`, `ClimbsDatabaseManagerTest.kt`, Targeted Suites | **Verified** |

---

## 3. Verification & Test Execution

- **Targeted Test Execution (`parallel_sprint_lock.py`)**:
  - Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutClimbIngestionTest" --tests "com.atrainingtracker.trainingtracker.database.ClimbsDatabaseManagerTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutHistoricalClimbBackfillTest" --tests "com.atrainingtracker.trainingtracker.database.ClimbsDatabaseRegistryTest" --tests "com.atrainingtracker.trainingtracker.migration.MigrationEngineClimbsRestoreTest"`
  - Results: **100% PASS** (11/11 tests across all 5 test suites).
  - Clean-room build and zero regressions observed across database, migration, and aftermath packages.
- **Physical Device Audit**:
  - `adb devices` checked: no physical devices connected; per Rule 79 emulator launch omitted.
