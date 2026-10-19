# Stage 3: Implementation Plan - ATT-4098: [Medium] Protect async workout finalization with foreground service lifecycle or expedited WorkManager job

**Ticket**: [ATT-4098](https://rainerblind.atlassian.net/browse/ATT-4098)  
**Sub-task**: [ATT-4123](https://rainerblind.atlassian.net/browse/ATT-4123) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Stability & Resource Leaks*)  
**Target Release**: `None` (Assigned upon Sprint Review completion per Rule 14)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-TRK-034` (*Foreground-Protected Asynchronous Workout Finalization and Stop Lifecycle Isolation*)  
**Test Mapping**: `TST-TRK-026` (*Foreground-Protected Asynchronous Workout Finalization and Stop Lifecycle Isolation Verification Suite*)  
**Branch**: `feature/ATT-4098`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Background

In Sprint `2026-42.9`, `ATT-4006` moved `endWorkout()` off the Android main thread onto `mDbExecutor`. However, during review, Finding 2 identified a critical lifecycle vulnerability:
1. `TrainingApplication.stopTracking()` calls `stopService(new Intent(this, TrackerService.class))` directly.
2. Android immediately executes `TrackerService.onDestroy()`, which unbinds `mBanalConnection` and calls `stopForeground(true)` on the main thread.
3. Once `onDestroy()` returns, the service is destroyed and the hosting process is demoted by Android to cached/background priority (`oom_score_adj >= 900`).
4. Multi-hour workouts running intensive `endWorkout()` operations (e.g. SQLite sample queries, route clustering, stream encoding) risk termination by the Low Memory Killer (LMK).
5. Concurrently, unbinding `mBanalConnection` in `onDestroy()` causes `BANALService` to terminate before `endWorkout()` can read sensor accumulations, lap counts, or active device states (`ATT-4099`).
6. Additionally, `TrackerService.java:633` contains a duplicate call to `TrainingApplication.resetTrackingStateOnAbort()` prior to `handleTrackingAborted()`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TRK-034` (*Foreground-Protected Asynchronous Workout Finalization and Stop Lifecycle Isolation*)
  * `REQ-TRK-034.1`: Graceful stop dispatch via `ACTION_STOP` and `TrackerService.isRunning()` in `TrainingApplication.stopTracking()`.
  * `REQ-TRK-034.2`: Retention of Foreground Service status and notification throughout `mDbExecutor` finalization in `TrackerService.handleActionStop()`.
  * `REQ-TRK-034.3`: Deferred `unbindService(mBanalConnection)`, `performStopForeground()`, and `performStopSelf()` on main-thread completion callback after `endWorkout()` completes.
  * `REQ-TRK-034.4`: `onDestroy()` fallback safety net and consolidation of duplicate `resetTrackingStateOnAbort()` call in async init failure path.
* **Test Mapping**: `TST-TRK-026` (`TST-TRK-026.1`, `TST-TRK-026.2`, `TST-TRK-026.3`, `TST-TRK-026.4`, `TST-TRK-026.5`, `TST-TRK-026.6`)

---

## 3. System Invariants & Preserved Behavior

1. **Non-Blocking Main Thread UI**: `ACTION_STOP` and `onDestroy()` must return immediately on the calling thread without blocking on database execution.
2. **Strict FIFO Execution on `mDbExecutor` (`REQ-TRK-015`)**: `mScheduler` is shut down before `finalizationTask` is queued, guaranteeing that all in-flight 1 Hz ticks drain prior to `endWorkout()`.
3. **Dedicated `FINISHED = 1` Transaction First (`REQ-TRK-030`)**: `endWorkout()` continues to commit `FINISHED = 1` before auxiliary finalization steps.
4. **`BANALService` Binding Invariance**: `mBanalConnection` remains bound and `mBanalService` remains accessible throughout `endWorkout()`.
5. **Human Gate & Versioning Invariance (Rule 3 & Rule 14)**: Sub-tasks transition directly to `Erledigt` via `freigabe` upon passing Gate audit without `fixVersion`; parent ticket transitions to `Final Review (Human)`.
6. **One Commit per Ticket (Rule 44)**: Changes remain on `feature/ATT-4098` without intermediate per-stage commits until squashed into `sprint/2026-42.10` in Stage 5.

---

## 4. Proposed Architectural Changes

### Component 1: `TrackerService.java`
* Define action constant and state flags:
  ```java
  public static final String ACTION_STOP = "com.atrainingtracker.trainingtracker.tracker.TrackerService.ACTION_STOP";
  private volatile boolean mIsStopping = false;
  ```
* Expose running check:
  ```java
  public static boolean isRunning() {
      return sInstance != null;
  }
  @VisibleForTesting
  public boolean isStoppingForTesting() {
      return mIsStopping;
  }
  ```
* Implement `handleActionStop()` in `onStartCommand()`:
  - Check for duplicate or idle stop:
    ```java
    if (mIsStopping) {
        Log.w(TAG, "ACTION_STOP received while already stopping; ignoring duplicate");
        return Service.START_NOT_STICKY;
    }
    if (mWorkoutID <= 0 && mBaseFileName == null) {
        Log.w(TAG, "ACTION_STOP received while not tracking; stopping self");
        performStopSelf();
        return Service.START_NOT_STICKY;
    }
    mIsStopping = true;
    ```
  - Shut down `mScheduler` and unregister transient receivers:
    ```java
    ScheduledFuture<?> handle = mTrackerHandle;
    if (handle != null) {
        handle.cancel(true);
        mTrackerHandle = null;
    }
    mScheduler.shutdownNow();
    mAutoPauseManager.release();
    LifecycleUtils.safeUnregisterReceiver(this, mSearchingFinishedReceiver, TAG);
    LifecycleUtils.safeUnregisterReceiver(this, mAltitudeCorrectionReceiver, TAG);
    LifecycleUtils.safeUnregisterReceiver(this, mLapSummaryReceiver, TAG);
    LifecycleUtils.safeUnregisterReceiver(this, mUserSelectedSportTypeChangedReceiver, TAG);
    ```
  - Retain foreground notification and enqueue `finalizationTask` on `mDbExecutor`:
    ```java
    final boolean interrupted = mTrackingInterrupted;
    final CompletableFuture<Void> finalizationFuture = new CompletableFuture<>();
    mFinalizationFuture = finalizationFuture;

    Runnable finalizationTask = () -> {
        try {
            if (!interrupted) {
                endWorkout();
            }
        } catch (Throwable t) {
            Log.e(TAG, "Unexpected error during async workout finalization", t);
        } finally {
            mBanalService = null;
            releaseWakeLock();
            finalizationFuture.complete(null);
            runOnMainThread(() -> {
                unbindService(mBanalConnection);
                performStopForeground();
                performStopSelf();
            });
        }
    };
    try {
        mDbExecutor.execute(finalizationTask);
    } catch (RejectedExecutionException e) {
        Log.w(TAG, "mDbExecutor rejected finalizationTask; running inline", e);
        finalizationTask.run();
    } finally {
        mDbExecutor.shutdown();
    }
    return Service.START_NOT_STICKY;
    ```
* Guard `onDestroy()` with `mFinalizationFuture` safety net:
  - If `mFinalizationFuture != null`: finalization is already in flight or finished. Wait on `mFinalizationFuture` with bounded timeout if needed, but do not enqueue duplicate finalization.
  - If `mFinalizationFuture == null`: execute existing fallback teardown.
* Consolidate duplicate call in `START_NORMAL` async DB initialization abort path:
  - Remove line 633 `TrainingApplication.resetTrackingStateOnAbort();`, as line 634 `runOnMainThread(this::handleTrackingAborted)` calls it on the main thread.

### Component 2: `TrainingApplication.java`
* In `stopTracking()`:
  ```java
  protected void stopTracking() {
      setManualPauseLock(false, this);
      setAutoPaused(false, this);
      stopPebbleWatchapp();

      if (TrackerService.isRunning()) {
          Intent stopIntent = new Intent(this, TrackerService.class);
          stopIntent.setAction(TrackerService.ACTION_STOP);
          startService(stopIntent);
      } else {
          stopService(new Intent(this, TrackerService.class));
      }

      cTrackingMode = TrackingMode.READY;
      sActiveWorkoutId = -1;
      notifyTrackingStateChanged();
  }
  ```

---

## 5. Atomic Implementation Steps

### Step 1: Add `ACTION_STOP` Constants & Stop Handler to `TrackerService.java`
- Define `ACTION_STOP`, `mIsStopping`, `isRunning()`, and `isStoppingForTesting()`.
- Route `ACTION_STOP` in `onStartCommand()`.
- Implement `handleActionStop()` with foreground retention and deferred stopSelf.
- Targeted Test: Verify compilation and basic action routing in `TrackerServiceFinalizationTest.kt`.

### Step 2: Update `onDestroy()` Fallback & Consolidate Abort Call in `TrackerService.java`
- Add safety net check in `onDestroy()` for `mFinalizationFuture != null`.
- Remove redundant line 633 `TrainingApplication.resetTrackingStateOnAbort()`.
- Targeted Test: Run `TrackerServiceAsyncInitTest` and `TrackerServiceFinalizationTest`.

### Step 3: Update `stopTracking()` in `TrainingApplication.java`
- Replace direct `stopService` with `ACTION_STOP` dispatch when `TrackerService.isRunning()`.
- Retain `stopService` fallback when not running.
- Targeted Test: Run `TrainingApplicationTest`.

### Step 4: Author Comprehensive Verification Unit Tests
- In `TrackerServiceFinalizationTest.kt`:
  - `testActionStop_MaintainsForegroundStatusDuringEndWorkoutAndStopsSelfAfterCompletion`: verifies foreground isn't dropped while `endWorkout()` runs.
  - `testActionStop_KeepsBanalServiceBoundUntilEndWorkoutCompletes`: verifies `mBanalService` non-null and unbind deferred.
  - `testActionStop_WhenDuplicateStopReceived_IgnoresSecondStopSafely`: verifies idempotency.
  - `testTrainingApplication_StopTracking_DispatchesActionStopWhenServiceRunning`: verifies dispatch contract.
- In `TrackerServiceAsyncInitTest.kt`:
  - Verify abort path calls `handleTrackingAborted()` cleanly on main thread.

### Step 5: Clean-Room Regression Suite
- Run `./gradlew testDebugUnitTest` to guarantee 100% test pass rate with zero regressions.

---

## 6. UI Consistency (Rule 23)
* Non-applicable: This ticket modifies service lifecycle and background/foreground threading architecture. No visual UI layouts or composables are altered.
