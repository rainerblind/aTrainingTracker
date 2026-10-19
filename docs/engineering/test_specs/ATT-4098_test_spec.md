# Stage 2: Requirement & Test Specification - ATT-4098: [Medium] Protect async workout finalization with foreground service lifecycle or expedited WorkManager job

**Ticket**: [ATT-4098](https://rainerblind.atlassian.net/browse/ATT-4098)  
**Sub-task**: [ATT-4122](https://rainerblind.atlassian.net/browse/ATT-4122) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Stability & Resource Leaks*)  
**Target Release**: `None` (Assigned upon Sprint Review completion per Rule 14)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-TRK-034` (*Foreground-Protected Asynchronous Workout Finalization and Stop Lifecycle Isolation*)  
**Test Spec ID**: `TST-TRK-026`  
**Branch**: `feature/ATT-4098`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Requirement Specification (`REQ-TRK-034`)

### 1.1 Problem Statement & Rationale
Stopping a workout previously triggered `stopService(new Intent(this, TrackerService.class))` directly from `TrainingApplication.stopTracking()`, causing Android to synchronously execute `TrackerService.onDestroy()`, unbind `mBanalConnection`, and call `stopForeground(true)`. Once `onDestroy()` returned, Android demoted the host process to a cached/background priority while `endWorkout()` was still running asynchronously on `mDbExecutor`. On long workouts with heavy post-processing (e.g. sample aggregation, route clustering, stream serialization), system memory pressure could trigger the Low Memory Killer (LMK) to terminate the process mid-finalization, resulting in missing or truncated workout analytics and export streams. Additionally, unbinding `mBanalConnection` in `onDestroy()` caused `BANALService` to terminate concurrently before `endWorkout()` finished reading sensor accumulations. Lastly, the async database initialization abort path contained a redundant call to `TrainingApplication.resetTrackingStateOnAbort()`.

### 1.2 Functional & Architectural Requirements
`TrackerService` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`) and `TrainingApplication` (`app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`) SHALL guarantee that asynchronous workout finalization on `mDbExecutor` is protected within the Android Foreground Service lifecycle, keeping process priority elevated and `BANALService` bound until `endWorkout()` completes (`ATT-4098`, extending `REQ-TRK-015`, `REQ-TRK-030`, and `REQ-TRK-033`):

1. **Graceful Stop Command Dispatch via `ACTION_STOP` (`REQ-TRK-034.1`)**:
   * `TrackerService` SHALL define `public static final String ACTION_STOP = "com.atrainingtracker.trainingtracker.tracker.TrackerService.ACTION_STOP";`.
   * `TrackerService` SHALL expose `public static boolean isRunning()` (returning `sInstance != null`).
   * In `TrainingApplication.stopTracking()`, instead of issuing `stopService(new Intent(this, TrackerService.class))`, `TrainingApplication` SHALL issue:
     ```java
     if (TrackerService.isRunning()) {
         Intent stopIntent = new Intent(this, TrackerService.class);
         stopIntent.setAction(TrackerService.ACTION_STOP);
         startService(stopIntent);
     }
     ```
   * `TrainingApplication.stopTracking()` SHALL immediately reset manual/auto pause locks (`setManualPauseLock(false, this)`, `setAutoPaused(false, this)`), stop the Pebble watchapp, reset `cTrackingMode = TrackingMode.READY`, `sActiveWorkoutId = -1`, and broadcast `TRACKING_STATE_CHANGED`.

2. **Foreground Priority Protection During Asynchronous Finalization (`REQ-TRK-034.2`)**:
   * In `TrackerService.onStartCommand(@Nullable Intent intent, int flags, int startId)`:
     When `intent != null && ACTION_STOP.equals(intent.getAction())`:
     * If `TrackerService` is already stopping (`mIsStopping == true`) or not actively tracking (`mWorkoutID <= 0` and `mBaseFileName == null`), it SHALL immediately return `START_NOT_STICKY` (calling `performStopSelf()` if idle).
     * Otherwise, `TrackerService` SHALL set `volatile boolean mIsStopping = true;`.
     * `TrackerService` SHALL immediately cancel `mTrackerHandle` and invoke `mScheduler.shutdownNow()` so no further 1 Hz ticks are sampled or submitted to `mDbExecutor`.
     * `TrackerService` SHALL unregister transient receivers (`mAltitudeCorrectionReceiver`, `mSearchingFinishedReceiver`, `mUserSelectedSportTypeChangedReceiver`, `mLapSummaryReceiver`) and release `mAutoPauseManager`.
     * `TrackerService` SHALL **retain** its Foreground Service status (`stopForeground` is NOT called yet) and maintain its active foreground notification, guaranteeing `PROCESS_STATE_FOREGROUND_SERVICE` priority throughout database finalization.
     * `TrackerService` SHALL enqueue the terminal `finalizationTask` onto `mDbExecutor`.

3. **Deferred Service Teardown and BANAL Unbinding on Completion (`REQ-TRK-034.3`)**:
   * Inside `finalizationTask` on `mDbExecutor`:
     * `try { if (!mTrackingInterrupted) { endWorkout(); } } catch (Throwable t) { Log.e(TAG, ...); } finally { ... }`
     * Throughout `endWorkout()`, `mBanalConnection` SHALL remain actively bound and `mBanalService` available to query active devices, lap numbers, and sensor accumulations.
     * Inside the `finally` block of `finalizationTask`:
       * `mBanalService = null;`
       * `releaseWakeLock();`
       * `mFinalizationFuture.complete(null);`
       * Dispatch to the Android main thread via `runOnMainThread(...)`:
         - `unbindService(mBanalConnection);`
         - `performStopForeground();`
         - `performStopSelf();`

4. **Safety Net in `onDestroy()` and Async-Init Abort Consolidation (`REQ-TRK-034.4`)**:
   * If `TrackerService.onDestroy()` is invoked unexpectedly before `ACTION_STOP` finalization runs:
     * If `mFinalizationFuture != null`, `onDestroy()` SHALL wait for `mFinalizationFuture` up to a bounded timeout (e.g. 5 seconds) to ensure database consistency before returning.
     * If `mFinalizationFuture == null`, `onDestroy()` SHALL execute the fallback finalization as specified in `REQ-TRK-033`.
   * In `TrackerService.java:624-638` (the `mDbExecutor.submit` `catch` block on `START_NORMAL` initialization failure):
     * The duplicate call to `TrainingApplication.resetTrackingStateOnAbort()` SHALL be removed, relying strictly on `runOnMainThread(this::handleTrackingAborted)` which resets `TrainingApplication` tracking state and broadcasts `TRACKING_ABORTED_INTENT` on the main thread.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: Net-new requirement `REQ-TRK-034`, extending `REQ-TRK-015` (`ATT-2641`), `REQ-TRK-030` (`ATT-3986`), and `REQ-TRK-033` (`ATT-4006`). Targets `TrackerService.java`, `TrainingApplication.java`, `TrackerServiceFinalizationTest.kt`, `TrackerServiceResilienceTest.kt`.
2. **Historical Origin & Commit Trace**: `ATT-4006` (`814919cd`) moved `endWorkout()` to `mDbExecutor` from `onDestroy()`. Review Finding 2 identified that calling `stopForeground()` before `mDbExecutor` finishes demotes the process, risking LMK kill.
3. **Root Reason for Existing Formulation**: `stopForeground()` was previously placed in `onDestroy()` because `stopService()` was the sole stop trigger.
4. **Preservation of Core Invariants**: Retains non-blocking UI, strict FIFO ordering on `mDbExecutor`, dedicated `FINISHED = 1` transaction, and `TRACKING_FINISHED_INTENT` broadcast.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Foreground Retention During Async Finalization)**:
  * *Given* an active workout tracking session in `TrackerService`,
  * *When* `ACTION_STOP` is received by `onStartCommand()`,
  * *Then* `stopForeground()` and `stopSelf()` are NOT invoked while `endWorkout()` runs on `mDbExecutor`, and are only called on the main thread after `finalizationTask` finishes.
* **Criterion 2 (BANAL Binding Maintained Throughout `endWorkout`)**:
  * *Given* an active workout with bound `mBanalConnection`,
  * *When* workout finalization runs via `ACTION_STOP`,
  * *Then* `mBanalService` remains non-null and bound throughout `endWorkout()` execution, and `unbindService(mBanalConnection)` is invoked only after `endWorkout()` finishes.
* **Criterion 3 (Idempotent Stop Handling)**:
  * *Given* a `TrackerService` currently executing `ACTION_STOP` finalization,
  * *When* a duplicate `ACTION_STOP` is delivered,
  * *Then* the duplicate intent is safely ignored without re-triggering finalization or crashing.
* **Criterion 4 (Consolidated Async-Init Abort)**:
  * *Given* `TrackerService` fails during async `START_NORMAL` database creation,
  * *When* the exception is caught,
  * *Then* `TrainingApplication.resetTrackingStateOnAbort()` is invoked once on the main thread via `handleTrackingAborted()`, restoring state to `TrackingMode.READY`.

### 1.5 System Invariants
* Rule 29 (`FINISHED = 1` committed first), `WakeLock` held until finalization completes, zero main-thread database blocking, and 100% clean-room test pass rate MUST NOT be broken.

---

## 2. Test Specification (`TST-TRK-026`)

### Test Case 1: `testActionStop_MaintainsForegroundStatusDuringEndWorkoutAndStopsSelfAfterCompletion` (`TST-TRK-026.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceFinalizationTest.kt`
* **Preconditions**: `TrackerService` configured with mock/spy for `performStopForeground()` and `performStopSelf()`, and a real `mDbExecutor` running a delayed `endWorkout()`.
* **Action**: Send `ACTION_STOP` to `onStartCommand()`.
* **Expected Result**:
  1. `performStopForeground()` and `performStopSelf()` are NOT called immediately upon return from `onStartCommand()`.
  2. After `finalizationFuture` completes, `performStopForeground()` and `performStopSelf()` have each been called exactly once.

### Test Case 2: `testActionStop_KeepsBanalServiceBoundUntilEndWorkoutCompletes` (`TST-TRK-026.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceFinalizationTest.kt`
* **Preconditions**: `TrackerService` with mock `BANALServiceComm`, recording whether `mBanalService` was non-null during `endWorkout()`.
* **Action**: Trigger `ACTION_STOP` and inspect `mBanalService` during `endWorkout()`.
* **Expected Result**: `mBanalService` is verified non-null inside `endWorkout()`, and unbind is verified to occur after `endWorkout()` returns.

### Test Case 3: `testActionStop_WhenDuplicateStopReceived_IgnoresSecondStopSafely` (`TST-TRK-026.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceFinalizationTest.kt`
* **Preconditions**: `TrackerService` in progress with first `ACTION_STOP`.
* **Action**: Call `onStartCommand` with `ACTION_STOP` a second time.
* **Expected Result**: Second call returns `START_NOT_STICKY` immediately and does not enqueue a second finalization task.

### Test Case 4: `testTrainingApplication_StopTracking_DispatchesActionStopWhenServiceRunning` (`TST-TRK-026.4`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TrainingApplicationTest.kt` or `TrackerServiceFinalizationTest.kt`
* **Preconditions**: `TrackerService.isRunning()` returns `true`.
* **Action**: Invoke `TrainingApplication.stopTracking()`.
* **Expected Result**: An intent with action `TrackerService.ACTION_STOP` is dispatched to `startService()`, and tracking mode resets to `READY`.

### Test Case 5: `testAsyncInitAbort_ConsolidatedResetTrackingStateCalledOnceOnMainThread` (`TST-TRK-026.5`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceAsyncInitTest.kt`
* **Preconditions**: Async init failure simulation.
* **Action**: Run `START_NORMAL` failure path.
* **Expected Result**: Tracking state is restored cleanly via `handleTrackingAborted()` without duplicate calls before `runOnMainThread`.

### Test Case 6: Clean-Room Regression Suite (`TST-TRK-026.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Requirement Clause | Test Case | Target Artifact | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-034.1` (Graceful Stop via `ACTION_STOP`) | `TST-TRK-026.4` | `TrainingApplication.java`, `TrackerService.java` | Unit Test |
| `REQ-TRK-034.2` (Foreground Retention) | `TST-TRK-026.1`, `TST-TRK-026.3` | `TrackerService.java` | Unit Test |
| `REQ-TRK-034.3` (Deferred Teardown & BANAL Retention) | `TST-TRK-026.2` | `TrackerService.java` | Unit Test |
| `REQ-TRK-034.4` (Lifecycle Fallback & Abort Consolidation) | `TST-TRK-026.5` | `TrackerService.java` | Unit Test |
