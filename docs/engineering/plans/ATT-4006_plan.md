# Stage 3: Implementation Plan - ATT-4006: [Medium] TrackerService onDestroy blocks main thread and leaks scheduler thread

**Ticket**: [ATT-4006](https://rainerblind.atlassian.net/browse/ATT-4006)  
**Sub-task**: [ATT-4091](https://rainerblind.atlassian.net/browse/ATT-4091) (`[Impl-Plan]`)  
**Parent Epic**: None (*Standalone Bug*)  
**Target Release**: `None` (Assigned upon Sprint Review completion per Rule 14)  
**Active Sprint**: `2026-42.9`  
**Requirement Mapping**: `REQ-TRK-033` (*Non-Blocking `TrackerService.onDestroy()` Finalization on `mDbExecutor`, Deterministic `mScheduler` Thread Teardown, and `TrainingApplication` State Recovery on Async Init Failure*)  
**Test Mapping**: `TST-TRK-025` (*Non-Blocking `TrackerService.onDestroy()` Finalization on `mDbExecutor`, Deterministic `mScheduler` Shutdown & Async Init Failure Recovery Verification Suite*)  
**Branch**: `feature/ATT-4006`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Background

[`TrackerService`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java) has three lifecycle and concurrency defects around workout teardown and startup abort:
1. **`mScheduler` Thread Leak & Late-Tick Race (`TrackerService.java:171, 792–794`)**: `mScheduler` (`Executors.newScheduledThreadPool(1)`) is never shut down in `onDestroy()`. Only `mTrackerHandle.cancel(true)` is invoked, leaving an idle core thread (`pool-N-thread-1`) alive per workout and permitting an in-flight tick to race with `mDbExecutor.shutdown()` or `endWorkout()`.
2. **Main-Thread ANR Blocking in `onDestroy()` (`TrackerService.java:796–807`)**: `onDestroy()` blocks the Android main thread with `mDbExecutor.awaitTermination(5, TimeUnit.SECONDS)` and then runs `endWorkout()` (which performs 5+ synchronous SQLite transactions, Douglas-Peucker simplification, and route clustering) directly on the main thread.
3. **Stuck `TrainingApplication.cTrackingMode` on Async `START_NORMAL` Initialization Failure (`TrackerService.java:596–606`, `TrainingApplication.java:1515`)**: When `createNewWorkout` or `createNewTable` throws on `mDbExecutor`, `TrackerService` sets `mTrackingInterrupted = true` and calls `stopSelf()`, leaving `TrainingApplication.cTrackingMode` stuck at `TrackingMode.TRACKING`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TRK-033` (*Non-Blocking `TrackerService.onDestroy()` Finalization on `mDbExecutor`, Deterministic `mScheduler` Thread Teardown, and `TrainingApplication` State Recovery on Async Init Failure*)
  * `REQ-TRK-033.1`: Deterministic `mScheduler.shutdownNow()` in `onDestroy()` prior to queuing `finalizationTask` on `mDbExecutor`.
  * `REQ-TRK-033.2`: Non-blocking `onDestroy()` via FIFO `finalizationTask` on `mDbExecutor` followed by `mDbExecutor.shutdown()`, with `mBanalService = null`, `releaseWakeLock()`, and `mFinalizationFuture.complete(null)` in `finally`.
  * `REQ-TRK-033.3`: `TrainingApplication.onTrackingAborted()` / `resetTrackingStateOnAbort()` and `TrackerService.TRACKING_ABORTED_INTENT` / `handleTrackingAborted()` dispatched via `runOnMainThread(...)` when `START_NORMAL` initialization fails.
* **Test Mapping**: `TST-TRK-025` (`TST-TRK-025.1`, `TST-TRK-025.2`, `TST-TRK-025.3`, `TST-TRK-025.4`)

---

## 3. System Invariants & Preserved Behavior

1. **FIFO Sample Write Completion Before `endWorkout()` (`REQ-TRK-015`)**: Because `mDbExecutor` is a single-threaded FIFO `ExecutorService`, queuing `finalizationTask` on `mDbExecutor` *before* calling `mDbExecutor.shutdown()` guarantees that all previously queued 1 Hz sample writes, lap writes, and altitude corrections finish in strict FIFO order before `endWorkout()` executes.
2. **Main-Thread Service Unbind & Receiver Cleanup**: `unbindService(mBanalConnection)`, `LifecycleUtils.safeUnregisterReceiver(...)`, `mAutoPauseManager.release()`, and `stopForeground(true)` remain on the main thread in `onDestroy()` to satisfy Android's `ServiceConnection` and `BroadcastReceiver` lifecycle rules, while `mBanalService = null` and `releaseWakeLock()` are deferred to the `finally` block of `finalizationTask` on `mDbExecutor` so `endWorkout()` can still read `mBanalService` snapshot data.
3. **Fault-Isolated `endWorkout()` & `finalizeLiveSession()` (`REQ-TRK-030`)**: `endWorkout()` and `finalizeLiveSession()` remain unmodified and preserve their dedicated `FINISHED = 1` transaction and `TRACKING_FINISHED_INTENT` terminal broadcast.
4. **Zero Regressions in Existing Unit Tests**: `TrackerServiceFinalizationTest.kt`, `TrackerServiceResilienceTest.kt`, `TrackerServiceAsyncInitTest.kt`, `TrackerServiceResumeTest.kt`, and `SensorAndDeviceConcurrencyStressTest.kt` all pass 100%.
5. **Subtask Self-Sufficiency & Parent Human Gate Invariance (Rule 3 & Rule 14)**: Subtasks transition directly to `Erledigt` via `freigabe` upon passing Gate audit (`review_agent.py`) with empty `fixVersions`; parent ticket `ATT-4006` transitions to `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: [`TrainingApplication.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java)
* Declare `cTrackingMode` as `volatile`:
  ```java
  @NonNull
  protected static volatile TrackingMode cTrackingMode = TrackingMode.READY;
  ```
* Add static helper `resetTrackingStateOnAbort()` and instance method `onTrackingAborted()`:
  ```java
  public static void resetTrackingStateOnAbort() {
      setManualPauseLock(false, cAppContext);
      setAutoPaused(false, cAppContext);
      cTrackingMode = TrackingMode.READY;
      sActiveWorkoutId = -1;
  }

  public void onTrackingAborted() {
      if (DEBUG) Log.w(TAG, "onTrackingAborted: resetting tracking state to READY");
      setManualPauseLock(false, this);
      setAutoPaused(false, this);
      stopPebbleWatchapp();
      cTrackingMode = TrackingMode.READY;
      sActiveWorkoutId = -1;
      mWorkoutID = -1;
      try {
          if (mNotificationManager != null) {
              mNotificationManager.cancel(TRACKING_NOTIFICATION_ID);
          }
      } catch (Throwable ignored) {
      }
      notifyTrackingStateChanged();
  }
  ```

### Component 2: [`TrackerService.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java)
* Add intent constant `public static final String TRACKING_ABORTED_INTENT = "com.atrainingtracker.trainingtracker.tracker.TrackerService.TRACKING_ABORTED_INTENT";`.
* Add field `private volatile CompletableFuture<Void> mFinalizationFuture = CompletableFuture.completedFuture(null);` and `@VisibleForTesting @NonNull public CompletableFuture<Void> getFinalizationFuture()`, `@VisibleForTesting boolean isSchedulerShutdownForTesting()`, and `@VisibleForTesting boolean isDbExecutorShutdownForTesting()`.
* Add `runOnMainThread(@NonNull Runnable action)` and `handleTrackingAborted()`:
  * In `START_NORMAL`'s `mDbExecutor.submit(...)` `catch (Exception e)` block:
    ```java
    Log.e(TAG, "Failed to initialize workout in database", e);
    mTableInitializationFuture.completeExceptionally(e);
    mTrackingInterrupted = true;
    runOnMainThread(this::handleTrackingAborted);
    stopSelf();
    ```
  * `handleTrackingAborted()` resets `TrainingApplication` (`app.onTrackingAborted()` when `getApplication() instanceof TrainingApplication`, and always `TrainingApplication.resetTrackingStateOnAbort()`), shows `showTrackingInterruptedNotification(this)`, and broadcasts `TRACKING_ABORTED_INTENT` (wrapped in null-safe guards for unmocked unit tests).
* Refactor `onDestroy()`:
  1. Cancel `mTableInitializationFuture` if not done.
  2. Cancel `mTrackerHandle` (`handle.cancel(true)`) and invoke `mScheduler.shutdownNow()`.
  3. Release `mAutoPauseManager.release()`, unregister broadcast receivers via `LifecycleUtils.safeUnregisterReceiver(...)`, call `unbindService(mBanalConnection)` (retaining `mBanalService` snapshot until `finalizationTask` finishes), and call `stopForeground(true)` (guarded against unmocked base context in unit tests).
  4. Capture `final boolean interrupted = mTrackingInterrupted;` and `final CompletableFuture<Void> finalizationFuture = new CompletableFuture<>(); mFinalizationFuture = finalizationFuture;`.
  5. Construct `Runnable finalizationTask`:
     ```java
     Runnable finalizationTask = () -> {
         try {
             if (!interrupted) {
                 endWorkout();
             }
         } catch (Throwable t) {
             Log.e(TAG, "Unexpected error during async workout finalization in onDestroy", t);
         } finally {
             mBanalService = null;
             releaseWakeLock();
             finalizationFuture.complete(null);
         }
     };
     ```
  6. Enqueue `mDbExecutor.execute(finalizationTask)` (with fallback `catch (RejectedExecutionException e) { finalizationTask.run(); }`) and call `mDbExecutor.shutdown()`.

### Component 3: Unit Test Suite Updates (`TrackerServiceFinalizationTest.kt`, `TrackerServiceResilienceTest.kt`, `TrackerServiceAsyncInitTest.kt`)
* **`TrackerServiceFinalizationTest.kt`**:
  * Configure `executorMock` in `setUp()` so `every { executorMock.execute(any()) } answers { firstArg<Runnable>().run() }`, ensuring existing synchronous finalization tests run `finalizationTask` inline when `service.onDestroy()` is called.
  * Add `TST-TRK-025.1` (`onDestroy shuts down mScheduler, does not block main thread on slow mDbExecutor task, and executes endWorkout in FIFO order`) and `TST-TRK-025.2` (`onDestroy with mTrackingInterrupted true skips endWorkout while completing scheduler and dbExecutor shutdown`).
* **`TrackerServiceResilienceTest.kt`**:
  * In existing `onDestroy` tests that use the real single-threaded `mDbExecutor`, await `service.finalizationFuture.get(2, TimeUnit.SECONDS)` after `service.onDestroy()` before asserting `service.endWorkoutCalled`.
* **`TrackerServiceAsyncInitTest.kt`**:
  * Add `TST-TRK-025.3` verifying that when `createNewWorkout` throws during `START_NORMAL`, `TrainingApplication.getTrackingMode()` resets from `TrackingMode.TRACKING` to `TrackingMode.READY`, `isManualPauseLocked()` and `isAutoPaused()` reset to `false`, `mTrackingInterrupted` is `true`, and `TRACKING_ABORTED_INTENT` is broadcast.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* No UI changes. (Pure service lifecycle, executor concurrency, and `TrainingApplication` state recovery fix.)

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Mandatory Gate 3 Pre-Check (Rule 4)
* Command: `python3 tools/jira_util.py check-gate <STAGE3_KEY>`
* Confirm exit code `0` (`GATE_PASSED`) before modifying any file under `app/src/...`.

### Step 2: Update `TrainingApplication.java` (`REQ-TRK-033.3`)
* File: [`app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java)
* Changes: Make `cTrackingMode` `volatile` and add `resetTrackingStateOnAbort()` and `onTrackingAborted()` with JavaDoc headers.

### Step 3: Update `TrackerService.java` (`REQ-TRK-033.1`, `REQ-TRK-033.2`, `REQ-TRK-033.3`)
* File: [`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java)
* Changes:
  * Add `TRACKING_ABORTED_INTENT`, `mFinalizationFuture`, `getFinalizationFuture()`, `isSchedulerShutdownForTesting()`, `isDbExecutorShutdownForTesting()`.
  * Add `runOnMainThread(Runnable)` and `handleTrackingAborted()`, and wire `runOnMainThread(this::handleTrackingAborted)` into the `START_NORMAL` initialization `catch (Exception e)` block.
  * Refactor `onDestroy()` to shut down `mScheduler` via `mScheduler.shutdownNow()`, execute main-thread receiver/service/foreground teardown, enqueue `finalizationTask` onto `mDbExecutor`, and call `mDbExecutor.shutdown()` without blocking the main thread.

### Step 4: Update & Add Unit Tests (`TST-TRK-025.1` – `TST-TRK-025.3`)
* Files:
  * [`app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceFinalizationTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceFinalizationTest.kt)
  * [`app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResilienceTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResilienceTest.kt)
  * [`app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceAsyncInitTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceAsyncInitTest.kt)
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.*"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted `com.atrainingtracker.trainingtracker.tracker.*` unit tests in Stage 4, followed by full clean-room regression suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: All changes are isolated on `feature/ATT-4006` and can be reverted cleanly without affecting `sprint/2026-42.9` or `develop`.
