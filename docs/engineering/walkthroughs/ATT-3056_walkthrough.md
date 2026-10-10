# Stage 5 Walkthrough: ATT-3056 - First workout does not immediately appear in periods view upon completion

**Ticket**: [ATT-3056](https://atrainingtracker.atlassian.net/browse/ATT-3056)  
**Sub-task**: [ATT-3091](https://atrainingtracker.atlassian.net/browse/ATT-3091) (`[Test]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) / Aftermath Analytics  
**Sprint**: `Sprint 2026-41.7`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Executive Summary & Problem Domain

### 1.1 The Issue
On a fresh application installation or a clean athlete profile with 0 prior recorded workouts:
1. When an athlete recorded and finalized their very first workout, the newly completed workout did not appear in the Period summaries view (Day, Week, Month, Year).
2. The UI remained stuck in `EmptyStatePlaceholder` until the user manually restarted the application, rotated the screen, or navigated away and back.

### 1.2 Root Cause Analysis
1. **Initial Migration Flag State**: On a fresh install, `isSyncFinished()` in `PeriodSummariesDatabaseManager` defaults to `false`. In `PeriodsRepository.loadFromDatabase()`, loading and emitting period lists was gated on `isSyncFinished()`, returning empty lists whenever the migration flag was not set.
2. **Missing Sync Flag Update on Workout Finalization**: `PeriodsRepository.onWorkoutFinished()` inserted/updated the day summary and rolled up metrics to week/month/year tables, but never updated `setSyncFinished(true)` nor forced emission into `_groupedPeriods.value`.
3. **Decoupled Broadcast Lifecycle**: `TrackerService.onTrackingFinished()` broadcasted `TRACKING_FINISHED_INTENT`. While `HistoryViewModel` was observing workouts, `PeriodsRepository` relied solely on `WorkoutsRepository` cache refreshes without directly listening for finalization or guaranteeing synchronization.
4. **Missing Unfinished Workout Gating**: `WorkoutSummariesDatabaseManager.getWorkoutsInRangeCursor()` did not filter for `FINISHED = 1`, creating potential discrepancies if in-progress or aborted sessions were loaded.

---

## 2. Implemented Architecture & Solution (`REQ-PER-014`)

### 2.1 Broadcast Listening & Reactive Processing
`PeriodsRepository.kt` now registers a dedicated `BroadcastReceiver` listening for `TrackerService.TRACKING_FINISHED_INTENT` via both `LocalBroadcastManager` and `ContextCompat.registerReceiver`. Upon receipt:
- It queries the newly completed workout from SQLite.
- It validates `workout.finished == true`.
- It executes `onWorkoutFinished(workout)` under `rebuildMutex.withLock`.

### 2.2 Guaranteed Sync Completion & Forced Emission
In `PeriodsRepository.onWorkoutFinished()`:
- Wrapped securely under `rebuildMutex.withLock` to eliminate race conditions with background migrations (`performHierarchicalMigration()`).
- After upserting the day summary and cascading rollups across Week, Month, and Year periods, it explicitly calls `dbManager.setSyncFinished(db, true)`.
- It calls `loadFromDatabase(forceIncremental = true)`, immediately reading updated records from SQLite and emitting them into `_groupedPeriods.value`.

### 2.3 Unfinished Workout Filtering
In `WorkoutSummariesDatabaseManager.java`, `getWorkoutsInRangeCursor()` now enforces `AND " + WorkoutSummaries.FINISHED + " = 1`, ensuring unfinalized tracking sessions are strictly excluded from period analytics.

### 2.4 Active ViewModel Reconciliation
In `PeriodsViewModel.loadPeriods()`, the ViewModel triggers `periodsRepo.checkIntegrityAndSync()`, guaranteeing that any latent discrepancy between SQLite workout summaries and `PeriodSummaries.db` is immediately reconciled upon screen entry.

---

## 3. Verification & Test Evidence (`TST-PER-020`)

### 3.1 Targeted Unit Tests (`PeriodsRepositorySyncTest.kt`)
The following targeted unit and integration tests were established:
- `testFirstWorkout_whenFinishedOnFreshInstall_emitsGroupedPeriodsImmediately`:
  - Sets up an empty database with `isSyncFinished == false`.
  - Simulates workout completion via `onWorkoutFinished()`.
  - Asserts `isSyncFinished()` transitions to `true`.
  - Asserts `groupedPeriods.value` emits non-empty periods immediately.
- `testTrackingFinishedBroadcast_triggersOnWorkoutFinished`:
  - Fires `TRACKING_FINISHED_INTENT` via `LocalBroadcastManager`.
  - Verifies reactive processing and immediate emission.
- `testOnWorkoutFinished_serializesUnderRebuildMutex`:
  - Verifies that concurrent calls serialize cleanly without database locks or corruptions.
- `testUnfinishedWorkouts_areExcludedFromDayAggregation`:
  - Verifies that workouts with `FINISHED = 0` are excluded from day and period rollups.

### 3.2 Clean-Room Test Suite Execution
Full unit test suite executed cleanly:
```bash
./gradlew testDebugUnitTest --rerun-tasks
```
Result: **BUILD SUCCESSFUL (0 failures, 2307+ tests passed)**.

---

## 4. Requirement Traceability Matrix

| Requirement ID | Test Case ID | Scope | Verification Status |
| :--- | :--- | :--- | :--- |
| `REQ-PER-014` | `TST-PER-020` | Immediate Period View Reactive Synchronization on Workout Finalization | **Verified** |

---

## 5. Non-Regression & Invariant Audit

- **O(1) Integrity Verification (`REQ-MIG-026`)**: Fully preserved. Quick hash/count check remains active.
- **Hierarchical Rollup Math (`REQ-PER-001`)**: All dual-phase rollup calculations remain intact.
- **Chesterton's Fence Preservation**: `rebuildMutex` guarantees that background migrations and single-workout rollups never execute concurrently.
- **Zero Architecture Drift**: Changes are confined to repository synchronization and query gating.
