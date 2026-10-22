# Stage 5: Walkthrough & Verification - ATT-3549: Replace Unbounded Full-Table Query in TrackerService Crash-Resume with Targeted LIMIT 1 Query

**Ticket**: [ATT-3549](https://rainerblind.atlassian.net/browse/ATT-3549)  
**Sub-task**: [ATT-3674](https://rainerblind.atlassian.net/browse/ATT-3674) (`[Test] Clean-room regression and walkthrough for targeted crash-resume query`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Requirement Mapping**: `REQ-DAT-026` (*Targeted Single-Row Query with Atomic State Reconstruction for TrackerService Crash-Resume*)  
**Test Mapping**: `TST-DAT-021` (*Targeted Single-Row Query with Atomic State Reconstruction Verification Suite*)  
**Branch**: `feature/ATT-3549`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Executive Summary & Verification Overview

Prior to ATT-3549, `TrackerService.recreateValuesWhenResuming()` performed an unbounded `null` projection query over `WORKOUTS_TABLE` with no `LIMIT` clause and no `ORDER BY` clause, followed by `cursor.moveToLast()`. In long-lived databases with thousands of workouts, this created severe architectural and runtime issues:
1. **Memory & I/O Overhead**: The Android `CursorWindow` (typically 2MB) loaded up to its maximum capacity with thousands of historical workout records across all columns, despite the service needing only the most recent workout's state.
2. **Resource Inefficiency & Crash Vulnerability**: Traversing to the last row forced the database cursor to walk all retrieved rows, risking OOM and window reallocation under high-volume databases.
3. **Cursor Leak Hazard**: The cursor was not guaranteed to close cleanly under runtime exceptions or early returns.

ATT-3549 systematically resolves these defects through:
1. **Targeted 7-Column Projection & `ORDER BY _id DESC LIMIT 1`**:
   - Explicit projection array `RESUME_WORKOUT_PROJECTION` requesting only:
     `WorkoutSummariesDatabaseManager._ID`,
     `WorkoutSummariesDatabaseManager.CURRENT_STATUS_COLUMN_NAME`,
     `WorkoutSummariesDatabaseManager.SPORT_TYPE_ID_COLUMN_NAME`,
     `WorkoutSummariesDatabaseManager.START_TIME_MILLIS_COLUMN_NAME`,
     `WorkoutSummariesDatabaseManager.DURATION_MILLIS_COLUMN_NAME`,
     `WorkoutSummariesDatabaseManager.ACTIVE_DURATION_MILLIS_COLUMN_NAME`,
     `WorkoutSummariesDatabaseManager.DISTANCE_COLUMN_NAME`.
   - Query constraint `orderBy = WorkoutSummariesDatabaseManager._ID + " DESC LIMIT 1"`, returning at most 1 row directly from SQLite index.
2. **Deterministic Try-With-Resources**:
   - `try (Cursor cursor = ...)` guarantees deterministic release of the `CursorWindow` and file descriptor even on runtime exceptions.
3. **Atomic Immutable State Reconstruction (`ResumedWorkoutState`)**:
   - `ResumedWorkoutState` extracts all 7 fields into an immutable record-like DTO using `cursor.getColumnIndexOrThrow(...)`.
   - `applyResumedState(...)` applies the fields atomically to `TrackerService` internal state and notifies `BANALService` accumulator only after extraction succeeds, preventing partial state corruption.
4. **Empty-Table & Missing-Column Resilience**:
   - When table is empty (`cursor.moveToFirst()` returns `false`), the method exits cleanly without throwing `CursorIndexOutOfBoundsException`.
   - Any `IllegalArgumentException` thrown by `getColumnIndexOrThrow` is caught and logged via `Log.e(...)`, failing fast and preventing silent state corruption.
5. **Regression Verification**:
   - Complete unit test suite (`TrackerServiceResumeTest.kt`) verifies targeted projection, limit clause, atomic state transfer, empty table handling, and fail-fast logging.
   - Clean-room test suite passes 100% with zero regressions.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-DAT-026.1` (Targeted Single-Row Query with `LIMIT 1` & Projection) | `TST-DAT-021.1` | `TrackerServiceResumeTest.recreateValuesWhenResuming_queriesOnlyLastWorkoutWithTargetedProjection` | **PASSED** | `Verified` |
| `REQ-DAT-026.2` (Deterministic Try-With-Resources Management) | `TST-DAT-021.2` | `TrackerServiceResumeTest.kt` & `lint_cursor_safety.py` | **PASSED** | `Verified` |
| `REQ-DAT-026.3` (Atomic State Reconstruction) | `TST-DAT-021.3` | `TrackerServiceResumeTest.recreateValuesWhenResuming_queriesOnlyLastWorkoutWithTargetedProjection` | **PASSED** | `Verified` |
| `REQ-DAT-026.4` (Empty Table and Schema Drift Resilience) | `TST-DAT-021.4` | `TrackerServiceResumeTest.recreateValuesWhenResuming_emptyTable_doesNotThrow` & `schemaDrift_missingColumn_logsErrorAndFailsFast` | **PASSED** | `Verified` |
| Clean-Room Full Suite Regression | `TST-DAT-021.5` | `./gradlew testDebugUnitTest` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Architecture Tests
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.service.TrackerServiceResumeTest"
```
**Output**:
```
BUILD SUCCESSFUL in 4s
32 actionable tasks: 1 executed, 31 up-to-date
```

### Static Architecture Guard CLI Linter
```bash
python3 tools/lint_cursor_safety.py
```
**Output**:
```
SUCCESS: 100% of legacy database queries are protected by try-with-resources or authorized @CursorProvider.
```

### Full Clean-Room Unit Test Suite Execution
```bash
./gradlew testDebugUnitTest
```
**Output**:
```
BUILD SUCCESSFUL in 1m 58s
32 actionable tasks: 12 executed, 20 up-to-date
```

---

## 4. Code Changes Summary

- **Production Code Refactored**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/service/TrackerService.java`:
    - Defined constant projection `RESUME_WORKOUT_PROJECTION`.
    - Added static immutable class `ResumedWorkoutState`.
    - Refactored `recreateValuesWhenResuming()` to query `ORDER BY _id DESC LIMIT 1` within `try-with-resources`.
    - Added atomic `applyResumedState(ResumedWorkoutState)` method.
    - Added `@VisibleForTesting` package-private state getters.
- **Tests Added**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/service/TrackerServiceResumeTest.kt`:
    - Tests single-row projection, atomic assignment, and accumulator sync.
    - Tests empty-table handling without out-of-bounds exceptions.
    - Tests missing-column fail-fast error logging.
- **Documentation Synchronized**:
  - `docs/requirements.md` (`REQ-DAT-026` marked `Verified`).
  - `docs/tests.md` (`TST-DAT-021` marked `Verified`).
