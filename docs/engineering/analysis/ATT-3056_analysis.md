# Stage 1 Analysis: ATT-3056 - First workout does not immediately appear in periods view upon completion

**Ticket**: [ATT-3056](https://atrainingtracker.atlassian.net/browse/ATT-3056)  
**Sub-task**: [ATT-3087](https://atrainingtracker.atlassian.net/browse/ATT-3087) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) / Aftermath Analytics  
**Active Sprint**: `Sprint 2026-41.7`  
**Author**: AI Agent 1 (Investigator)  
**Date**: 2026-10-10  

---

## 1. Problem Statement & Observed Behavior

When an athlete records and completes their very first workout on a fresh install or clean profile, the finished workout does not immediately appear in the Period views (Today / Week / Month / Year summary statistics and activity lists).

The athlete has to either restart the app, navigate away and back, or perform a manual refresh before the newly finalized workout is reflected in the period statistics.

---

## 2. Requirement Mapping & Historical Context

- **Requirement Mapping**: Refines and hardens `REQ-PER-001` (*Historical Hierarchical Period Summaries*) and `REQ-MIG-026` (*O(1) Self-Healing Integrity Verification between SQLite WorkoutSummaries and PeriodSummaries.db*). Formulated under formal requirement `REQ-PER-014` (*Immediate Period View Reactive Synchronization on Workout Finalization*).
- **Target Living Documents**:
  - Requirement: `REQ-PER-014` in `docs/requirements.md`
  - Verification: `TST-PER-012` in `docs/tests.md`

---

## 3. Forensic Call-Site Investigation & Architectural Trace

### 3.1 Call-Site Inventory & Method Signatures

| Component | File & Line Reference | Method / Symbol | Current Observed Behavior | Failure Mode |
| :--- | :--- | :--- | :--- | :--- |
| **TrackerService** | `TrackerService.java:1005-1016` | `endWorkout()` | Broadcasts `TRACKING_FINISHED_INTENT` with `WORKOUT_ID`. | Dispatches intent, but `PeriodsRepository` does not listen directly. |
| **WorkoutRepository** | `WorkoutRepository.kt:230-243` | `workoutUpdateReceiver.onReceive()` | Dispatches `launch { reloadWorkoutData(workoutId) }`. | Relies on `allWorkouts.value` presence and asynchronous timing. |
| **WorkoutRepository** | `WorkoutRepository.kt:1088-1096` | `reloadWorkoutData(workoutId: Long)` | Evaluates `isNewImportOrFinish` and calls `PeriodsRepository.getInstance(application).onWorkoutFinished(freshWorkoutData)`. | Calls `onWorkoutFinished` asynchronously before `addOrUpdateWorkout` completes in memory. |
| **PeriodsRepository** | `PeriodsRepository.kt:351-370` | `onWorkoutFinished(workout: WorkoutData)` | Updates SQLite day bucket, rolls up to parents, then executes `loadFromDatabase()`. | Calls `loadFromDatabase()` with default `forceIncremental = false` without setting `setSyncFinished(true)`. |
| **PeriodsRepository** | `PeriodsRepository.kt:775-778` | `loadFromDatabase(forceIncremental: Boolean, precalculatedGroups: WorkoutGroups?)` | Checks `val isFinished = withContext(Dispatchers.IO) { dbManager.isSyncFinished() }`. If `!isFinished && !forceIncremental`, returns immediately. | **Immediate Early Exit**: On fresh install, `isSyncFinished()` is `false`. Aborts silently and never emits `_groupedPeriods`. |
| **PeriodsRepository** | `PeriodsRepository.kt:114-122` | `init { ... }` | Launches initial scan or integrity check. | Runs `performHierarchicalMigration()` concurrently without mutex coordination with `onWorkoutFinished()`. |
| **PeriodsViewModel** | `PeriodsViewModel.kt:209-213` | `loadPeriods()` | Calls `workoutRepo.loadAllWorkouts()`. | Does not trigger `periodsRepo.checkIntegrityAndSync()` or `loadFromDatabase(forceIncremental = true)`. |
| **WorkoutSummariesDatabaseManager** | `WorkoutSummariesDatabaseManager.java:455-464` | `getWorkoutsInRangeCursor(long startTimeS, long endTimeS)` | Queries range by `TIME_START`. | Does not filter `FINISHED = 1`, risking inclusion of incomplete track sessions. |

---

## 4. Root Cause Analysis

### Root Cause 1: `isSyncFinished()` Guard Aborts Incremental In-Memory Emission
On a fresh install, `SyncStatus.db` is empty; `dbManager.isSyncFinished()` returns `false`.
When `PeriodsRepository.onWorkoutFinished(workout)` runs:
1. It inserts the day row and rolls up week/month/year into `PeriodSummaries.db`.
2. It does **not** call `dbManager.setSyncFinished(db, true)`.
3. It calls `loadFromDatabase()` with default parameter `forceIncremental = false`.
4. At line 777:
   ```kotlin
   val isFinished = withContext(Dispatchers.IO) { dbManager.isSyncFinished() }
   if (!isFinished && !forceIncremental) return
   ```
   Because `isFinished == false` and `forceIncremental == false`, `loadFromDatabase()` aborts immediately!
5. `_groupedPeriods.value` remains `listOf(emptyList(), emptyList(), emptyList(), emptyList())`, leaving the UI in `EmptyStatePlaceholder`.

### Root Cause 2: Concurrency & Missing Mutex Coordination with Initial Migration
In `PeriodsRepository.kt`:
- `performHierarchicalMigration()` runs under `rebuildMutex.withLock`. At line 245, it calls `dbManager.deleteAll(db)` and `dbManager.setSyncFinished(db, false)`.
- `onWorkoutFinished()` does **not** acquire `rebuildMutex`. If `onWorkoutFinished()` runs while migration is in progress, the initial reset in migration wipes the freshly calculated period records.

### Root Cause 3: Indirect Dependency on `WorkoutRepository` Without Autonomous Broadcast Listener
`PeriodsRepository` does not register a `BroadcastReceiver` for `TrackerService.TRACKING_FINISHED_INTENT`. If `WorkoutRepository` has not loaded workouts into memory (`allWorkouts.value.isEmpty()`), or if `reloadWorkoutData` drops the call due to duplicate guard set filtering (`reloadingWorkoutIds`), `PeriodsRepository` never receives the trigger.

### Root Cause 4: Passive UI Lifecycle in `PeriodsViewModel.loadPeriods()`
`PeriodsViewModel.loadPeriods()` only requests `workoutRepo.loadAllWorkouts()`. When the athlete switches to the Periods tab after tracking, no self-healing check (`checkIntegrityAndSync()`) or forced load (`loadFromDatabase(forceIncremental = true)`) is requested.

---

## 5. Chesterton's Fence Archaeology

1. **`isSyncFinished()` Guard (`ATT-379`, `ATT-909`)**:
   - *Original Purpose*: Introduced in Sprint 2026-41.2 to prevent the period UI from displaying partial, incomplete statistics during long-running background migrations of large historical archives (>1,000 workouts).
   - *Failure in Fresh Profile*: On fresh installs, no large migration exists (0 workouts), but `isSyncFinished()` remains `false`. When the first workout completes, the guard erroneously suppresses the single-workout rollup.
   - *Preservation Strategy*: The guard must remain active for bulk background migrations, but `onWorkoutFinished()` must explicitly set `dbManager.setSyncFinished(db, true)` upon committing the first valid period, and invoke `loadFromDatabase(forceIncremental = true)`.

2. **Self-Healing Integrity Check (`REQ-MIG-026`, `ATT-909`)**:
   - *Original Purpose*: Compares `workoutSummariesManager.getFinishedWorkoutCount()` with `dbManager.getTotalDayWorkoutsCount()`. If SQLite has more finished workouts than PeriodSummaries, it initiates background migration.
   - *Preservation Strategy*: Maintain the O(1) comparison invariant, but trigger `checkIntegrityAndSync()` actively when `loadPeriods()` is called and when `TRACKING_FINISHED_INTENT` is received.

---

## 6. Proposed Solution Architecture & Concurrency Model

```
+-----------------------------------------------------------------------------------+
| TrackerService.endWorkout()                                                       |
| - Sets FINISHED = 1 in SQLite                                                     |
| - Broadcasts TRACKING_FINISHED_INTENT (System & LocalBroadcastManager)             |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| PeriodsRepository BroadcastReceiver                                               |
| - Listens directly to TRACKING_FINISHED_INTENT                                    |
| - Extracts workoutId                                                              |
| - Fetches fresh WorkoutData from DB                                               |
| - Triggers onWorkoutFinished(workoutData)                                         |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| PeriodsRepository.onWorkoutFinished(workoutData)                                  |
| - Synchronizes under rebuildMutex to prevent race with performHierarchicalMigration |
| - Updates Day period in PeriodSummaries.db                                        |
| - Rolls up Week, Month, Year periods                                              |
| - Calls dbManager.setSyncFinished(db, true)                                       |
| - Calls loadFromDatabase(forceIncremental = true)                                 |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| StateFlow Emitted: _groupedPeriods.value updated                                  |
| - PeriodsViewModel.groupedPeriods receives new lists                             |
| - PeriodsScreen automatically recomposes: EmptyState -> Populated Period Cards   |
+-----------------------------------------------------------------------------------+
```

### 6.1 Concurrency & Mutex Safety Model
- `rebuildMutex` will guard `onWorkoutFinished()` write transactions:
  ```kotlin
  fun onWorkoutFinished(workout: WorkoutData) {
      scope.launch {
          rebuildMutex.withLock {
              withContext(Dispatchers.IO) {
                  dbManager.runInTransaction { db ->
                      val workoutsInDay = fetchWorkoutsInDay(dayStart)
                      aggregateWorkoutsToDay(workoutsInDay, dayStart)?.let { 
                          dbManager.upsertPeriod(db, it)
                      }
                      rollupDayToParents(db, ldt)
                      dbManager.setSyncFinished(db, true)
                  }
              }
              loadFromDatabase(forceIncremental = true)
          }
      }
  }
  ```
- **Deadlock Prevention**: `rebuildMutex` is a non-reentrant Kotlin Coroutines mutex (`Mutex`). Inside the lock, only standard SQLite transactions (`db.beginTransaction()`) are executed. No nested coroutines or secondary mutexes are acquired.
- **Cold-Start Sequence Preservation**: Initial app cold start will continue to run `loadFromDatabase()` and background `performHierarchicalMigration()` or `checkIntegrityAndSync()`. Since `rebuildMutex` serializes migration and dynamic updates, neither will wipe or corrupt the other.

---

## 7. Scope & Boundary Enforcements

- **In Scope**:
  - `PeriodsRepository.kt`: Direct broadcast receiver for `TRACKING_FINISHED_INTENT`, `rebuildMutex.withLock` protection in `onWorkoutFinished()`, `dbManager.setSyncFinished(db, true)`, `loadFromDatabase(forceIncremental = true)`.
  - `PeriodsViewModel.kt`: Invoke `periodsRepo.checkIntegrityAndSync()` in `loadPeriods()`.
  - `WorkoutSummariesDatabaseManager.java`: Add `FINISHED = 1` condition in `getWorkoutsInRangeCursor()`.
  - Unit tests verifying first workout instant appearance without restart.
- **Out of Scope**:
  - Modifying the visual layout of `PeriodSummaryCard.kt` or `PeriodsTabsScreen.kt`.
  - Changing sport-specific mathematical calculations or distance formulas.
