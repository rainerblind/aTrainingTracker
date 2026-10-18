# Stage 2: Requirement & Test Specification - ATT-4006: [Medium] TrackerService onDestroy blocks main thread and leaks scheduler thread

**Ticket**: [ATT-4006](https://rainerblind.atlassian.net/browse/ATT-4006)  
**Sub-task**: [ATT-4090](https://rainerblind.atlassian.net/browse/ATT-4090) (`[Req & Test Spec]`)  
**Parent Epic**: None (*Standalone Bug*)  
**Target Release**: `None` (Assigned upon Sprint Review completion per Rule 14)  
**Active Sprint**: `2026-42.9`  
**Requirement Mapping**: `REQ-TRK-033` (*Non-Blocking Asynchronous Workout Teardown, Scheduler Thread Lifecycle Hygiene, and Main-Thread Initialization Abort Recovery*)  
**Test Spec ID**: `TST-TRK-025`  
**Branch**: `feature/ATT-4006`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Requirement Specification (`REQ-TRK-033`)

### 1.1 Problem Statement & Rationale
Stopping a workout in `TrackerService.onDestroy()` previously executed `mDbExecutor.awaitTermination(5, TimeUnit.SECONDS)` and `endWorkout()` synchronously on the Android main thread, causing multi-second UI freezes and ANR risk on long rides, while `mScheduler` (`Executors.newScheduledThreadPool(1)`) was never shut down in `onDestroy()`, leaking one worker thread per workout and allowing an in-flight tick to race with `endWorkout()` or hit the shut-down `mDbExecutor`. Additionally, when asynchronous `START_NORMAL` workout initialization failed inside `mDbExecutor.submit(...)`, `TrackerService` stopped itself without resetting `TrainingApplication.cTrackingMode`, leaving the application stuck in `TrackingMode.TRACKING`.

### 1.2 Functional & Architectural Requirements
`TrackerService` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`) and `TrainingApplication` (`app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`) SHALL guarantee non-blocking main-thread service teardown (`< 100 ms`), deterministic `mScheduler` thread termination with zero thread accumulation across sequential workouts, background finalization on `mDbExecutor` with guaranteed `WakeLock` release and completion broadcasts, and main-thread `TrainingApplication` state reset on initialization abort (`ATT-4006`, extending `REQ-STB-009`, `REQ-TRK-016`, and `REQ-TRK-030`):

1. **Named Single-Thread Executors & Deterministic `mScheduler` Shutdown (`REQ-TRK-033.1`)**:
   * `TrackerService.mScheduler` and `TrackerService.mDbExecutor` SHALL use named threads (`"TrackerService-Scheduler"` and `"TrackerService-DbExecutor"`) so worker threads are identifiable and auditable across workouts.
   * In `TrackerService.onDestroy()`, `TrackerService` SHALL cancel `mTrackerHandle` (`mTrackerHandle.cancel(true); mTrackerHandle = null;`) and immediately invoke `mScheduler.shutdownNow()` (with a bounded `mScheduler.awaitTermination(20, TimeUnit.MILLISECONDS)` guard when called outside the scheduler thread) **before** enqueuing terminal finalization onto `mDbExecutor` and shutting down `mDbExecutor`, ensuring zero leaked scheduler threads and preventing any further 1 Hz ticks from racing with `endWorkout()` or submitting tasks after `mDbExecutor.shutdown()`.
   * `TrackerService.tracker.run()` SHALL immediately return without sampling if `mScheduler.isShutdown()` or `Thread.currentThread().isInterrupted()`, and all `mDbExecutor` submissions (`executeOnDbExecutor`) SHALL guard against `mDbExecutor.isShutdown()` and `RejectedExecutionException`.
2. **Non-Blocking `onDestroy()` & Off-Main-Thread FIFO Finalization (`REQ-TRK-033.2`)**:
   * `TrackerService.onDestroy()` SHALL NOT invoke `mDbExecutor.awaitTermination(5, TimeUnit.SECONDS)` or execute `endWorkout()` synchronously on the Android main thread; main-thread execution of `onDestroy()` SHALL complete in `< 100 ms` even when slow tasks are queued on `mDbExecutor`.
   * `TrackerService.onDestroy()` SHALL unregister broadcast receivers, release `mAutoPauseManager`, and call `unbindService(mBanalConnection)` on the main thread, and SHALL enqueue a terminal finalization task onto `mDbExecutor` **before** calling `mDbExecutor.shutdown()` (falling back to inline execution only if `mDbExecutor` is already shut down or rejects execution).
   * Because `mDbExecutor` is a single-thread sequential FIFO executor, all previously queued 1 Hz sample writes SHALL drain to completion before the terminal finalization task executes `if (!mTrackingInterrupted) { endWorkout(); }` on `mDbExecutor`.
   * The terminal finalization task SHALL clear `mBanalService = null`, invoke `releaseWakeLock()`, and complete `mFinalizationFuture` (`@VisibleForTesting public CompletableFuture<Void> getFinalizationFuture()`) inside a guaranteed `finally` block so the `PARTIAL_WAKE_LOCK` is held throughout background finalization and always released upon completion.
3. **Main-Thread State Reset & Broadcast on Asynchronous Initialization / Startup Abort (`REQ-TRK-033.3`)**:
   * `TrainingApplication.cTrackingMode` SHALL be declared `volatile` and `TrainingApplication` SHALL expose `public void onTrackingAborted()` and `public static void resetTrackingStateOnAbort()` which reset `cTrackingMode = TrackingMode.READY`, `sActiveWorkoutId = -1`, `mWorkoutID = -1`, clear manual/auto pause locks (`setManualPauseLock(false, ...)`, `setAutoPaused(false, ...)`), stop the Pebble watchapp, cancel `TRACKING_NOTIFICATION_ID`, and broadcast `TRACKING_STATE_CHANGED`.
   * `TrackerService` SHALL define `public static final String TRACKING_ABORTED_INTENT = "de.rainerblind.trainingtracker.TrackerService.TRACKING_ABORTED_INTENT";` and `protected void handleTrackingAborted()`, which dispatches via `runOnMainThread(...)` to reset `TrainingApplication` tracking state (`onTrackingAborted()` / `resetTrackingStateOnAbort()`), broadcast `TRACKING_ABORTED_INTENT`, display the tracking-interrupted notification (`showTrackingInterruptedNotification()`), and stop the service (`performStopSelf()`).
   * When `START_NORMAL` asynchronous database/table initialization fails in `mDbExecutor.submit(...)` (or when `onStartCommand` / `applyResumedState` aborts with `mTrackingInterrupted = true`), `TrackerService` SHALL invoke `handleTrackingAborted()` so `TrainingApplication.getTrackingMode()` is reset to `TrackingMode.READY` on the main thread and never left stuck in `TrackingMode.TRACKING`.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: Net-new requirement `REQ-TRK-033`, extending `REQ-STB-009` (`ATT-1245` / `ATT-1262`), `REQ-TRK-016` (`ATT-2972`), and `REQ-TRK-030` (`ATT-3986`). Targets `TrackerService.java`, `TrainingApplication.java`, `TrackerServiceFinalizationTest.kt`, `TrackerServiceResilienceTest.kt`, and `TrackerServiceAsyncInitTest.kt`.
2. **Historical Origin & Commit Trace**: Commit `3d033495` (`ATT-1245` / `ATT-1262`) introduced `mDbExecutor` and `mTableInitializationFuture` for non-blocking `onStartCommand`, and commit `dc61fdfb` (`ATT-3986`) isolated `FINISHED = 1` and reordered `finalizeLiveSession()`.
3. **Root Reason for Existing Formulation**: `onDestroy()` originally called `mDbExecutor.shutdown(); mDbExecutor.awaitTermination(5, TimeUnit.SECONDS);` on the main thread so that queued sample writes would finish before `endWorkout()` ran synchronously on the main thread.
4. **Preservation of Core Invariants**: Enqueuing the terminal `endWorkout()` + `releaseWakeLock()` task onto the single-threaded `mDbExecutor` before `mDbExecutor.shutdown()` preserves strict FIFO ordering after all pending sample writes while eliminating main-thread blocking, and `handleTrackingAborted()` preserves `REQ-STB-009` Clause 6 (`mTrackingInterrupted = true`, notification, `performStopSelf()`) while restoring `TrainingApplication.getTrackingMode()` to `TrackingMode.READY`.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Non-Blocking `onDestroy` on Long Workouts)**:
  * *Given* a workout where `mDbExecutor` has slow pending database work queued (e.g., 500 ms simulated sample drain and finalization),
  * *When* the user stops tracking and `TrackerService.onDestroy()` executes on the calling (main) thread,
  * *Then* `onDestroy()` returns in `< 100 ms` without blocking on `mDbExecutor`, and `endWorkout()`, `TRACKING_FINISHED_INTENT`, and `releaseWakeLock()` complete asynchronously on `mDbExecutor` in strict FIFO order after the pending sample work.
* **Criterion 2 (Zero Scheduler / Executor Thread Leak Across Multiple Workouts)**:
  * *Given* several workouts started and finished in a row (`N = 5` consecutive `TrackerService` lifecycles),
  * *When* each workout finishes via `onDestroy()` and its `finalizationFuture` completes,
  * *Then* `mScheduler.isShutdown()` is `true` after every workout and the number of live `"TrackerService-Scheduler"` and `"TrackerService-DbExecutor"` threads does not grow (returning to `0`).
* **Criterion 3 (Main-Thread State Reset on Async Initialization Failure)**:
  * *Given* `TrainingApplication` in `TrackingMode.TRACKING` while `TrackerService.onStartCommand` (`START_NORMAL`) runs async initialization on `mDbExecutor`,
  * *When* `createNewWorkout()` or `createNewTable()` throws an exception,
  * *Then* `TrackerService` dispatches abort handling to the main thread (`runOnMainThread`), resetting `TrainingApplication.getTrackingMode()` to `TrackingMode.READY` (`isTracking() == false`), broadcasting `TRACKING_ABORTED_INTENT`, showing the interrupted notification, and calling `performStopSelf()`.

### 1.5 System Invariants
* Rule 29 (`FINISHED = 1` dedicated transaction and `TRACKING_FINISHED_INTENT` in `finally`), `WakeLock` held until background finalization finishes, zero `returnDefaultValues = true`, and 100% clean-room test pass rate MUST NOT be broken.

---

## 2. Test Specification (`TST-TRK-025`)

### Test Case 1: `testOnDestroy_DoesNotBlockMainThreadAndRunsEndWorkoutAndWakeLockReleaseInFifoOrderOnDbExecutor` (`TST-TRK-025.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceFinalizationTest.kt`
* **Preconditions**: `TrackerService` with a real single-thread `mDbExecutor` that has a 300 ms simulated pending sample write task queued, and a tracked event sequence list (`sampleWriteFinished`, `endWorkoutExecuted`, `wakeLockReleased`).
* **Action**: Call `service.onDestroy()` on the test thread and measure elapsed time on the calling thread; then await `service.finalizationFuture.get(3, TimeUnit.SECONDS)`.
* **Expected Result**:
  1. `service.onDestroy()` returns on the calling thread in `< 100 ms` (while the 300 ms sample task is still running on `mDbExecutor`).
  2. Once `finalizationFuture` completes, the recorded event order is strictly `["sampleWriteFinished", "endWorkoutExecuted"]`, `endWorkout()` ran on `"TrackerService-DbExecutor"`, and `mBanalService` is cleared to `null`.

### Test Case 2: `testMultipleWorkoutsInARow_ShutsDownSchedulerAndDoesNotLeakTrackerThreads` (`TST-TRK-025.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceFinalizationTest.kt`
* **Preconditions**: Baseline count of live threads matching `"TrackerService-Scheduler"` and `"TrackerService-DbExecutor"`.
* **Action**: Start and destroy 5 consecutive `TrackerService` instances (`onStartCommand` scheduling `mTrackerHandle` on `mScheduler`, followed by `onDestroy()` and awaiting `finalizationFuture`).
* **Expected Result**:
  1. After each `onDestroy()`, `service.isSchedulerShutdownForTesting()` is `true` and `service.isDbExecutorShutdownForTesting()` is `true`.
  2. After all 5 workouts finish, the count of live `"TrackerService-Scheduler"` threads and `"TrackerService-DbExecutor"` threads does not grow above baseline (`0` leaked threads).

### Test Case 3: `testTableCreationFailure_ResetsTrainingApplicationTrackingModeOnMainThreadAndBroadcastsAbort` (`TST-TRK-025.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceAsyncInitTest.kt`
* **Preconditions**: `TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)`, `TrainingApplication.setActiveWorkoutIdForTesting(999L)`, and `service.tableCreationException = SQLException("Disk I/O failure")`.
* **Action**: Invoke `service.onStartCommand(startNormalIntent, 0, 1)` and await `service.stopSelfLatch.await(5, TimeUnit.SECONDS)`.
* **Expected Result**:
  1. `TrainingApplication.getTrackingMode()` is reset to `TrackingMode.READY` (`TrainingApplication.isTracking() == false`) and `TrainingApplication.getActiveWorkoutID() == -1L`.
  2. `runOnMainThread` was invoked to execute the abort recovery on the main thread.
  3. `TRACKING_ABORTED_INTENT` is broadcast, `showTrackingInterruptedNotification()` is called, `performStopSelf()` is called, and `onDestroy()` does not call `endWorkout()`.

### Test Case 4: 9-Language Localization & Specifier Audit (`TST-TRK-025.4`)
* **Scope**: Localization Parity Test
* **Goal**: Verify string presence and matching format specifiers across all 9 locales (`EN, DE, ES, FR, IT, JA, NL, PL, PT`) via `TranslationParityTest` (no new string resources are introduced by `ATT-4006`; existing `tracking_interrupted_notification_*` strings remain intact).
* **Expected Result**: 100% parity across all 9 locales.

### Test Case 5: Clean-Room Regression Suite (`TST-TRK-025.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full unit test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-025.1` | Unit | `TrackerService.onDestroy`, `TrackerService.endWorkout` | `REQ-TRK-033.2` | Specified |
| `TST-TRK-025.2` | Unit | `TrackerService.onDestroy`, `mScheduler.shutdownNow` | `REQ-TRK-033.1` | Specified |
| `TST-TRK-025.3` | Unit | `TrackerService.handleTrackingAborted`, `TrainingApplication.onTrackingAborted` | `REQ-TRK-033.3`, `REQ-STB-009` | Specified |
| `TST-TRK-025.4` | Localization | `TranslationParityTest` | `REQ-TRK-033`, `REQ-UI-106` | Specified |
| `TST-TRK-025.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
