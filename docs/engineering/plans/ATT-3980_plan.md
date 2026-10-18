# Stage 3: Implementation Plan - ATT-3980: [Highest] Resumed workout writes all samples to a table named "null"

**Ticket**: [ATT-3980](https://rainerblind.atlassian.net/browse/ATT-3980)  
**Sub-task**: [ATT-4047](https://rainerblind.atlassian.net/browse/ATT-4047) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2135](https://rainerblind.atlassian.net/browse/ATT-2135) (*Tracking & Recording*)  
**Target Release**: `TBD (Set on completion per Rule 14)`  
**Active Sprint**: `2026-42.9` (`id = 377`)  
**Requirement Mapping**: `REQ-TRK-026`  
**Test Mapping**: `TST-TRK-018`  
**Branch**: `feature/ATT-3980`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Background

When an active workout is resumed via user action (`RESUME_BY_USER`) or recreated by Android after a process restart (`RESUME_SERVICE_RECREATION`), `TrackerService.onCreate()` binds asynchronously to `BANALService`, so `mBanalService` is `null` when `onStartCommand()` executes. Because `recreateValuesWhenResuming()` is deferred to `mBanalConnection.onServiceConnected()`, `mBaseFileName` is still `null` at line 613 of `onStartCommand()`, which evaluates `mSamplesTableName = WorkoutSamplesDatabaseManager.getTableName(null)` -> `"\"null\""`. Furthermore, when `mBanalConnection.onServiceConnected()` subsequently invokes `recreateValuesWhenResuming()` -> `applyResumedState(state)`, `applyResumedState()` sets `mBaseFileName = state.baseFileName` **without recomputing `mSamplesTableName`**, leaving `mSamplesTableName` permanently set to `"\"null\""` for the remainder of the workout. Finally, `handleMissingColumns()` silently catches and discards the `SQLiteException` (`no such table: "null"`) while `summariesDb.setTransactionSuccessful()` still commits, causing total post-resume sample loss with zero error logs.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TRK-026` (*Resumed Workout Sample Table Resolution, Asynchronous BANAL Binding Barrier, and Sample Write Error Surfacing in `TrackerService`*)
* **Test Mapping**: `TST-TRK-018` (*Resumed Workout Sample Table Resolution, Asynchronous BANAL Binding Barrier & Sample Write Error Surfacing Verification Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing `START_NORMAL` (`TrackerServiceWorkoutStartTest`) and synchronous resume (`TrackerServiceResumeTest`) suites continue to pass cleanly.
2. **Structural Wiring Contract**: `BANALService.checkOrInitializeLocationDevices();` in `TrackerService.onCreate()` and `mBanalConnection.onServiceConnected()` remains intact (`FreshInstallLocationInitTest`).
3. **Thread Safety & Dispatcher Affinity**: Database operations and `mTableInitializationFuture` synchronization remain thread-safe across `mSingleThreadPool` and the main service thread.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `WorkoutSamplesDatabaseManager.java` (`com.atrainingtracker.trainingtracker.database`)
* Harden `getTableName(@NonNull String baseFileName)` to reject `null`, empty/blank (`baseFileName.trim().isEmpty()`), or literal `"null"` (`"null".equalsIgnoreCase(baseFileName.trim())`) by throwing an `IllegalArgumentException`, preventing `"\"null\""` from ever being produced.

### Component 2: `TrackerService.java` (`com.atrainingtracker.trainingtracker.tracker`)
1. **`applyResumedState(@NonNull ResumedWorkoutState state)`**:
   * Immediately compute and assign `mSamplesTableName = WorkoutSamplesDatabaseManager.getTableName(mBaseFileName)` when `mBaseFileName` is non-null/non-empty.
   * Complete `mTableInitializationFuture.complete(null)` so any queued tasks (such as `mAltitudeCorrectionReceiver`) unblock only after `mWorkoutID`, `mBaseFileName`, and `mSamplesTableName` are initialized.
   * Invoke `notifyTrackingStarted(mWorkoutID)` when `mWorkoutID > 0` so deferred resume broadcasts the restored workout ID rather than `0`.
2. **`notifyTrackingStarted(long workoutId)`**:
   * Guard with `if (getBaseContext() == null) return;` so direct unit test instantiations of `TrackerService` without a base context do not throw `NullPointerException`.
3. **`onStartCommand(Intent intent, int flags, int startId)`**:
   * Initialize `mTableInitializationFuture = new CompletableFuture<>()` for `START_NORMAL`, `RESUME_BY_USER`, and `RESUME_SERVICE_RECREATION` (while keeping the field declaration default `CompletableFuture.completedFuture(null)` for direct reflection tests in `TrackerServiceWorkoutStartTest`).
   * Remove the premature `mTableInitializationFuture = CompletableFuture.completedFuture(null)` assignments in `RESUME_BY_USER` and `RESUME_SERVICE_RECREATION`.
   * Only compute `mSamplesTableName = WorkoutSamplesDatabaseManager.getTableName(mBaseFileName)` in `onStartCommand()` if `startType != StartType.START_NORMAL && mBaseFileName != null && !mBaseFileName.trim().isEmpty()`.
   * Only call `notifyTrackingStarted(mWorkoutID)` in `onStartCommand()` for `START_NORMAL` (since `applyResumedState()` now invokes `notifyTrackingStarted(mWorkoutID)` once `mWorkoutID` is restored for both synchronous and deferred resume flows).
4. **`mBanalConnection.onServiceConnected(ComponentName name, IBinder service)`**:
   * Execute `recreateValuesWhenResuming()` **before** `mBanalService.createNewLap()` and `onSearchingFinished()` when `mStartType == RESUME_BY_USER` or `mStartType == RESUME_SERVICE_RECREATION`, ensuring `mWorkoutID`, `mLapNr`, `mBaseFileName`, and `mSamplesTableName` are restored before lap increments or active tracking transitions (`sampleAndWriteToDb()`).
5. **`mAltitudeCorrectionReceiver`**:
   * Capture `final long workoutId = mWorkoutID;` and `final String samplesTable = mSamplesTableName;` **inside** `mTableInitializationFuture.thenAcceptAsync(...)` rather than before the future completes.
6. **`sampleAndWriteToDb()` and `handleMissingColumns()`**:
   * Guard `sampleAndWriteToDb()` at entry: if `mSamplesTableName == null || mSamplesTableName.trim().isEmpty() || "\"null\"".equalsIgnoreCase(mSamplesTableName.trim())`, log `Log.e(TAG, ...)` and return early before starting the database transaction.
   * In `handleMissingColumns()`, if `e.getMessage()` does not contain `" has no column named "`, or if `ALTER TABLE` fails when adding a missing column, log `Log.e(TAG, ...)` with the exception and rethrow `e` so `summariesDb.setTransactionSuccessful()` is not called on a failed sample insert.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Harden `WorkoutSamplesDatabaseManager.getTableName()`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSamplesDatabaseManager.java`
* **Changes**: Validate `baseFileName != null && !baseFileName.trim().isEmpty() && !"null".equalsIgnoreCase(baseFileName.trim())` in `getTableName()`, throwing `IllegalArgumentException` if violated.

### Step 2: Fix Deferred Resume State Synchronization, Callback Ordering, and Sample Write Error Surfacing in `TrackerService.java`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
* **Changes**:
  * Update `applyResumedState()` to recompute `mSamplesTableName`, complete `mTableInitializationFuture`, and call `notifyTrackingStarted(mWorkoutID)`.
  * Guard `notifyTrackingStarted()` against `getBaseContext() == null`.
  * Update `onStartCommand()` to initialize an incomplete `mTableInitializationFuture = new CompletableFuture<>()` for resume flows and guard `getTableName(mBaseFileName)` behind `mBaseFileName != null && !mBaseFileName.trim().isEmpty()`.
  * Reorder `mBanalConnection.onServiceConnected()` so `recreateValuesWhenResuming()` runs before `createNewLap()` and `onSearchingFinished()`.
  * Move `workoutId` and `samplesTable` reads inside `mTableInitializationFuture.thenAcceptAsync(...)` in `mAltitudeCorrectionReceiver`.
  * Add invalid `mSamplesTableName` guard in `sampleAndWriteToDb()` and `Log.e` + rethrow in `handleMissingColumns()`.

### Step 3: Expand Automated Unit Tests (`TST-TRK-018`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResumeTest.kt`
* **Changes**: Add unit tests covering `applyResumedState` table name synchronization, deferred `RESUME_SERVICE_RECREATION` and `RESUME_BY_USER` via `mBanalConnection.onServiceConnected()` with end-to-end `sampleAndWriteToDb()` verification, `WorkoutSamplesDatabaseManager.getTableName()` invalid input rejection, and `handleMissingColumns()` non-column `SQLiteException` surfacing.
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.*" --tests "com.atrainingtracker.banalservice.devices.FreshInstallLocationInitTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests (`TrackerServiceResumeTest`, `TrackerServiceWorkoutStartTest`, `FreshInstallLocationInitTest`) during Stage 4 construction, followed by the full clean-room unit test suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-3980` allows full revert without affecting `sprint/2026-42.9` or `develop`.
