# Stage 3 Implementation Plan: ATT-3056 - First workout does not immediately appear in periods view upon completion

**Ticket**: [ATT-3056](https://atrainingtracker.atlassian.net/browse/ATT-3056)  
**Sub-task**: [ATT-3089](https://atrainingtracker.atlassian.net/browse/ATT-3089) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) / Aftermath Analytics  
**Active Sprint**: `Sprint 2026-41.7`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Domain & Root Cause Analysis

### 1.1 Observed Defect
On a fresh install or clean athlete profile with 0 prior workouts:
1. `isSyncFinished()` in `SyncStatus.db` evaluates to `false`.
2. When the athlete finishes their first workout, `PeriodsRepository.onWorkoutFinished(workout)` runs.
3. `onWorkoutFinished` updates `PeriodSummaries.db`, but does **not** call `dbManager.setSyncFinished(db, true)`.
4. It calls `loadFromDatabase()` with default `forceIncremental = false`.
5. At line 777 in `PeriodsRepository.kt`:
   ```kotlin
   val isFinished = withContext(Dispatchers.IO) { dbManager.isSyncFinished() }
   if (!isFinished && !forceIncremental) return
   ```
   Because `isFinished == false` and `forceIncremental == false`, `loadFromDatabase()` returns immediately without emitting `_groupedPeriods.value`.
6. `_groupedPeriods.value` remains empty (`listOf(emptyList(), emptyList(), emptyList(), emptyList())`), leaving `PeriodsScreen` stuck on `EmptyStatePlaceholder`.
7. Concurrently, `onWorkoutFinished()` lacks `rebuildMutex.withLock` protection, risking race conditions if background migration or integrity scans are active.
8. `PeriodsRepository` lacks direct listening to `TrackerService.TRACKING_FINISHED_INTENT`.
9. `WorkoutSummariesDatabaseManager.getWorkoutsInRangeCursor()` lacks a `FINISHED = 1` filter, allowing unfinalized workouts to be queried.
10. `PeriodsViewModel.loadPeriods()` does not invoke `periodsRepo.checkIntegrityAndSync()`.

---

## 2. Proposed Architecture & Implementation Steps

### Step 1: `WorkoutSummariesDatabaseManager.java` - Filter Finished Workouts in Range Queries
- In `getWorkoutsInRangeCursor(long startTimeS, long endTimeS)`:
  - Append ` AND " + WorkoutSummaries.FINISHED + " = 1"` to the selection query.
  - Ensures queries for day/period workouts strictly include finalized sessions.

### Step 2: `PeriodsRepository.kt` - Concurrency, Guaranteed Sync State & Forced Dynamic Emission
1. **Mutex Protection & Sync Flag in `onWorkoutFinished`**:
   - Wrap the execution of `onWorkoutFinished(workout: WorkoutData)` in `rebuildMutex.withLock`.
   - In the database transaction:
     ```kotlin
     dbManager.runInTransaction { db ->
         val workoutsInDay = fetchWorkoutsInDay(dayStart)
         aggregateWorkoutsToDay(workoutsInDay, dayStart)?.let { 
             dbManager.upsertPeriod(db, it)
         }
         rollupDayToParents(db, ldt)
         dbManager.setSyncFinished(db, true)
     }
     ```
   - Invoke `loadFromDatabase(forceIncremental = true)` following the transaction.
2. **Direct BroadcastReceiver for `TRACKING_FINISHED_INTENT`**:
   - Register a `BroadcastReceiver` in `init` using both `LocalBroadcastManager` and `ContextCompat.registerReceiver`.
   - On receiving `TRACKING_FINISHED_INTENT`:
     - Extract `workoutId`.
     - Load fresh `WorkoutData` from `workoutSummariesManager`.
     - If `workout.finished`, invoke `onWorkoutFinished(workout)`.
3. **Guard in `onWorkoutDeleted` and `onWorkoutSportChanged`**:
   - Ensure `rebuildMutex.withLock` is applied to `onWorkoutDeleted` and `onWorkoutSportChanged` to preserve transaction serialization.

### Step 3: `PeriodsViewModel.kt` - Active Integrity Reconciliation
- In `loadPeriods()`:
  - Concurrently trigger `periodsRepo.checkIntegrityAndSync()`.

### Step 4: Unit Testing (`PeriodsRepositorySyncTest.kt`)
- Create unit test suite verifying:
  - Fresh install scenario (`isSyncFinished == false`, 0 workouts) emits non-empty periods into `_groupedPeriods` immediately upon `onWorkoutFinished()`.
  - Direct broadcast receiver handles `TRACKING_FINISHED_INTENT`.
  - Mutex serialization prevents race conditions with migration.
  - Unfinished workouts (`FINISHED = 0`) are excluded from range queries.

---

## 3. Risk Assessment & Invariants

1. **Deadlock Prevention**: `rebuildMutex` is non-reentrant. Inside the lock, only standard SQLite transactions (`db.beginTransaction()`) and pure math aggregations are run. No nested mutex acquisitions occur.
2. **Dual-Phase Migration Invariant**: `performHierarchicalMigration()` remains unchanged under `rebuildMutex`, preserving the dual-phase rollup formula for bulk history migrations.
3. **O(1) Self-Healing Invariant**: `checkIntegrityAndSync()` continues to compare `workoutSummariesManager.getFinishedWorkoutCount()` with `dbManager.getTotalDayWorkoutsCount()`.
4. **Clean-Room Test Pass Rate**: 100% of the unit test suite must pass with 0 failures.

---

## 4. UI Consistency Audit

**UI Consistency Audit: N/A (non-UI ticket)**  
This ticket operates exclusively within the data and domain layer (`PeriodsRepository`, `PeriodsViewModel`, `WorkoutSummariesDatabaseManager`). The existing Compose UI components (`PeriodsScreen`, `PeriodSummaryCard`, `EmptyStatePlaceholder`) already observe `PeriodsViewModel.groupedPeriods` and reactively recompose without requiring visual layout, design token, or composable structure modifications.

---

## 5. Verification Plan

1. Execute targeted unit test suite `PeriodsRepositorySyncTest`.
2. Execute full clean-room unit test suite `./gradlew testDebugUnitTest`.
3. Verify zero regressions.
