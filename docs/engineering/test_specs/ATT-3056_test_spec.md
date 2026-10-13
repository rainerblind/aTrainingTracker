# Stage 2 Requirement & Test Specification: ATT-3056 - First workout does not immediately appear in periods view upon completion

**Ticket**: [ATT-3056](https://atrainingtracker.atlassian.net/browse/ATT-3056)  
**Sub-task**: [ATT-3088](https://atrainingtracker.atlassian.net/browse/ATT-3088) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) / Aftermath Analytics  
**Active Sprint**: `Sprint 2026-41.7`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Formal Requirement Specification (`REQ-PER-014`)

### 1.1 Requirement Statement
The system SHALL guarantee that newly finalized workouts are immediately aggregated and presented across all period analytics views (Day, Week, Month, Year) without requiring an app restart, navigation change, or manual refresh, even on clean installations or initial profiles (ATT-3056, refining `REQ-PER-001` and `REQ-MIG-026`):

1. **Direct Workout Finalization Broadcast Handling (`PeriodsRepository.kt`)**:
   - `PeriodsRepository` SHALL register an active `BroadcastReceiver` listening for `TrackerService.TRACKING_FINISHED_INTENT` via both `LocalBroadcastManager` and context `sendBroadcast`.
   - Upon receiving `TRACKING_FINISHED_INTENT` with a valid `workoutId`, `PeriodsRepository` SHALL query the fresh `WorkoutData` from SQLite, verify `finished == true`, and execute `onWorkoutFinished(workout)`.

2. **Guaranteed Sync State & Forced Dynamic Emission (`PeriodsRepository.kt`)**:
   - In `PeriodsRepository.onWorkoutFinished(workout: WorkoutData)`: The method SHALL execute under `rebuildMutex.withLock` to eliminate race conditions with background migrations (`performHierarchicalMigration()`).
   - In the SQLite transaction, after upserting the day summary and rolling up to Week, Month, and Year parent periods, the repository SHALL execute `dbManager.setSyncFinished(db, true)`, ensuring `isSyncFinished()` evaluates to `true` on fresh installs.
   - Following the transaction, `onWorkoutFinished()` SHALL invoke `loadFromDatabase(forceIncremental = true)`, forcing immediate in-memory loading and emitting the updated period lists into `_groupedPeriods.value` regardless of migration flag status.

3. **Finished Workout Gating in Range Queries (`WorkoutSummariesDatabaseManager.java` & `PeriodsRepository.kt`)**:
   - In `WorkoutSummariesDatabaseManager.getWorkoutsInRangeCursor()`, the query SHALL append `AND WorkoutSummaries.FINISHED = 1` (or equivalent filter in `fetchWorkoutsInDay()`), ensuring unfinalized or in-progress workouts are strictly excluded from historical period summaries.

4. **Active Period View Refresh (`PeriodsViewModel.kt`)**:
   - In `PeriodsViewModel.loadPeriods()`: In addition to `workoutRepo.loadAllWorkouts()`, the ViewModel SHALL trigger `periodsRepo.checkIntegrityAndSync()` to ensure any pending discrepancy between SQLite and `PeriodSummaries.db` is reconciled upon screen entry.

5. **Preservation of System Invariants**:
   - O(1) self-healing integrity check (`REQ-MIG-026`), background hierarchical dual-phase migration (`REQ-PER-001`), spatial boundary framing, memory caps on vector tracks (`REQ-PER-012`), and 100% full clean-room unit test pass rate MUST be strictly preserved.

---

## 2. Test Specification (`TST-PER-020`)

### 2.1 Acceptance Criteria (Given-When-Then)

- **Criterion 1 (Fresh Install Dynamic Period Emission)**:
  - *Given* a fresh install or clean profile with zero previous workouts (`isSyncFinished() == false`),
  - *When* the athlete completes and saves their first workout (`TRACKING_FINISHED_INTENT`),
  - *Then* `PeriodsRepository` processes the intent, updates `PeriodSummaries.db`, sets `isSyncFinished(true)`, and emits populated Day and Week periods into `_groupedPeriods` immediately without requiring an app restart.

- **Criterion 2 (Seamless UI State Transition)**:
  - *Given* an athlete viewing `PeriodsScreen` on an empty profile (rendering `EmptyStatePlaceholder`),
  - *When* the first workout is finished,
  - *Then* `_groupedPeriods` emission updates `PeriodsViewModel.groupedPeriods`, and the UI automatically transitions to displaying populated period cards.

- **Criterion 3 (Concurrency & Migration Race Immunity)**:
  - *Given* `onWorkoutFinished()` invoked while a background migration is pending or active,
  - *When* updating database records,
  - *Then* execution is serialized via `rebuildMutex.withLock` to prevent data wipe or race conditions.

- **Criterion 4 (Unfinalized Workout Filtering)**:
  - *Given* an active or aborted workout in SQLite with `FINISHED = 0`,
  - *When* `fetchWorkoutsInDay()` or `getWorkoutsInRangeCursor()` executes,
  - *Then* the unfinalized workout is strictly excluded from period calculations.

- **Criterion 5 (Clean-Room Test Suite Non-Regression)**:
  - *Given* the complete unit test suite across all modules,
  - *When* executed via `./gradlew testDebugUnitTest`,
  - *Then* 100% of unit tests pass with 0 failures and 0 regressions.

---

## 3. Targeted Test Verification Plan

1. **Unit Tests (`PeriodsRepositorySyncTest.kt`)**:
   - `testFirstWorkout_whenFinishedOnFreshInstall_emitsGroupedPeriodsImmediately()`:
     - Verifies `onWorkoutFinished()` sets `isSyncFinished(true)` and emits non-empty periods into `groupedPeriods`.
   - `testTrackingFinishedBroadcast_triggersOnWorkoutFinished()`:
     - Verifies broadcast receiver triggers processing upon receiving `TRACKING_FINISHED_INTENT`.
   - `testOnWorkoutFinished_serializesUnderRebuildMutex()`:
     - Verifies mutex serialization prevents concurrent migration wipe.
   - `testUnfinishedWorkouts_areExcludedFromDayAggregation()`:
     - Verifies `FINISHED = 0` workouts are not included in period metrics.

2. **Clean-Room Regression**:
   - Run `./gradlew testDebugUnitTest` across all modules.
