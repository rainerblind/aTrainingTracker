# Stage 1 Analysis: ATT-4098 - Protect async workout finalization with foreground service lifecycle or expedited WorkManager job

**Ticket**: [ATT-4098](https://rainerblind.atlassian.net/browse/ATT-4098)  
**Sub-task**: [ATT-4121](https://rainerblind.atlassian.net/browse/ATT-4121) (`[Analysis]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Stability & Resource Leaks*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Branch**: `feature/ATT-4098`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Statement & Motivation

During the Sprint `2026-42.9` code review of `ATT-4006` (commit `814919cd`), a critical lifecycle vulnerability was discovered regarding post-workout asynchronous finalization:

1. **Premature Foreground Teardown and Process Priority Demotion**:
   When a workout is stopped by the user or an external control (e.g., notification action, Pebble watchapp, or UI stop button), `TrainingApplication.stopTracking()` immediately issues:
   ```java
   stopService(new Intent(this, TrackerService.class));
   ```
   Android immediately executes `TrackerService.onDestroy()` on the main thread. In compliance with `REQ-TRK-033`, `onDestroy()` unbinds `mBanalConnection`, invokes `performStopForeground()` (`stopForeground(true)`), and enqueues `finalizationTask` (`endWorkout()`) onto `mDbExecutor`.
   However, once `onDestroy()` returns, Android considers `TrackerService` stopped and destroyed. If the user navigates away or the app is in the background, Android immediately demotes the host application process to a cached or background priority (`PROCESS_STATE_CACHED_EMPTY` / low oom_adj score).

2. **Vulnerability to Low Memory Killer (LMK)**:
   While `mWakeLock` keeps the CPU from sleeping, a `WakeLock` does **not** protect process priority against Android's Low Memory Killer (LMK). For multi-hour workouts with tens of thousands of sensor samples, `endWorkout()` performs intensive operations:
   - Full-table sensor sample queries and aggregation in `WorkoutSummariesDatabaseManager.updateWorkoutSummary()`,
   - High-resolution GPS route clustering and polygon simplification via `WorkoutClusterEngine`,
   - GPX/TCX/CSV file stream generation via `ExportManager`,
   - Reverse geocoding and naming heuristics.
   If system memory pressure occurs during this multi-second asynchronous window, the LMK can kill the demoted background process mid-finalization. Although `ATT-3986` ensures `WorkoutSummaries.FINISHED = 1` is committed early, map polylines, elevation streams, clustering, and exported files are left corrupted or missing.

3. **Concurrent BANALService Teardown Race (`ATT-4099`)**:
   Because `unbindService(mBanalConnection)` is invoked in `onDestroy()` before `finalizationTask` runs on `mDbExecutor`, if `TrackerService` was the sole bound client of `BANALService`, Android tears down `BANALService` concurrently. Consequently, when `endWorkout()` attempts to read `mBanalService.getLapNumber()`, query active device configurations, or snapshot sensor accumulations, it encounters an unbound or destroyed service.

4. **Redundant Invalidation Call**:
   In `TrackerService.java:633-634`, the async database initialization abort handler executes:
   ```java
   TrainingApplication.resetTrackingStateOnAbort();
   runOnMainThread(this::handleTrackingAborted);
   ```
   Inside `handleTrackingAborted()`, line 775 calls `TrainingApplication.resetTrackingStateOnAbort()` a second time. This redundancy should be cleanly consolidated.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Teardown Sequence in `TrackerService.onDestroy()` and `TrainingApplication.stopTracking()`

Examining `TrainingApplication.java:1579-1590`:
```java
protected void stopTracking() {
    setManualPauseLock(false, this);
    setAutoPaused(false, this);
    stopPebbleWatchapp();

    stopService(new Intent(this, TrackerService.class)); // <--- Triggers immediate onDestroy()

    cTrackingMode = TrackingMode.READY;
    sActiveWorkoutId = -1;
    notifyTrackingStateChanged();
}
```

Examining `TrackerService.java:908-944`:
```java
@Override
public void onDestroy() {
    performSuperOnDestroy();
    ...
    mScheduler.shutdownNow(); // REQ-TRK-033.1
    mAutoPauseManager.release();

    unbindService(mBanalConnection); // BANAL unbound here!
    performStopForeground();         // Demoted from foreground here!

    Runnable finalizationTask = () -> {
        try {
            if (!interrupted) {
                endWorkout();        // Runs asynchronously in background/cached process!
            }
        } finally {
            mBanalService = null;
            releaseWakeLock();
            finalizationFuture.complete(null);
        }
    };
    mDbExecutor.execute(finalizationTask);
    mDbExecutor.shutdown();
}
```

### 2.2 Forensic Analysis of Android Process Lifecycle
Under the Android runtime:
1. An active Foreground Service keeps its hosting process at `PROCESS_STATE_FOREGROUND_SERVICE` (`oom_score_adj = 200` or lower), making it virtually immune to LMK termination under standard operating conditions.
2. The instant `stopForeground()` is called and the service lifecycle terminates (`onDestroy()`), if no foreground activity is in the window manager focus, the process drops to background priority (`oom_score_adj >= 900`).
3. An active `PowerManager.WakeLock` holds the device CPU clock active but does **not** raise or retain Linux `oom_score_adj`. When system RAM drops below low-memory watermarks, Linux LMK kills processes starting from the highest `oom_score_adj` values regardless of `WakeLock` possession.

---

## 3. Architectural Decision & Solution Options

We evaluated three architectural candidates:

| Criterion | Option A: Graceful Foreground Finalization via `ACTION_STOP` (Selected) | Option B: Expedited WorkManager Job (`setExpedited`) | Option C: Independent Bound Service Process |
| :--- | :--- | :--- | :--- |
| **Process Priority Protection** | **Optimal**: Process remains at `PROCESS_STATE_FOREGROUND_SERVICE` until `endWorkout()` completes. | **Good**: Runs with expedited job priority, but subject to quota limits. | **Complex**: Requires IPC and multi-process architecture. |
| **Quota Restrictions** | **None**: No runtime quota or OS throttling. | **High Risk**: Android 12+ limits expedited jobs under App Standby Buckets; drops to regular worker if quota exhausted. | **None**. |
| **BANALService Binding** | **Guaranteed**: `mBanalConnection` remains bound until after `endWorkout()` finishes; solves `ATT-4099` at root. | **Broken**: `BANALService` is destroyed before worker starts; cannot access live sensor buffers. | **Complex**: Cross-process binding required. |
| **Implementation Complexity** | **Low / Minimal**: Refactors stop command sequence within existing `TrackerService` and `TrainingApplication`. | **High**: Requires `Worker`, data serialization of workout IDs, handling missing in-memory sessions. | **Prohibitive**: Multi-process database locking and AIDL synchronization. |
| **Risk of Regression** | **Very Low**: Preserves `REQ-TRK-033` FIFO order and non-blocking invariants. | **High**: Race conditions between worker startup and DB helper singletons. | **High**. |

### Selected Architecture: Graceful Foreground Finalization (`ACTION_STOP`)
Instead of `TrainingApplication.stopTracking()` issuing `stopService()`, it commands `TrackerService` via:
```java
Intent stopIntent = new Intent(this, TrackerService.class);
stopIntent.setAction(TrackerService.ACTION_STOP);
startService(stopIntent);
```
In `TrackerService`:
1. `onStartCommand()` receives `ACTION_STOP`:
   - Sets `mIsStopping = true` to reject new incoming tick tasks.
   - Cancels `mTrackerHandle` and immediately calls `mScheduler.shutdownNow()`.
   - Releases ephemeral receivers (`mAltitudeCorrectionReceiver`, `mSearchingFinishedReceiver`, `mUserSelectedSportTypeChangedReceiver`, `mLapSummaryReceiver`).
   - Updates foreground notification to a finalization state (or maintains the foreground notification).
   - Enqueues `finalizationTask` on `mDbExecutor`.
2. Inside `finalizationTask` (on `mDbExecutor`):
   - Executes `endWorkout()`. Note: `BANALService` remains actively bound and valid!
   - In the `finally` block:
     - Clears `mBanalService = null` and releases `mWakeLock`.
     - Completes `mFinalizationFuture`.
     - Dispatches to the Android main thread via `runOnMainThread()`:
       - `unbindService(mBanalConnection);`
       - `performStopForeground();`
       - `performStopSelf();`
3. Fallback in `onDestroy()`:
   - If `onDestroy()` is invoked unexpectedly by the OS (e.g. task swiped from Recents), it inspects `mFinalizationFuture`. If finalization has not yet been scheduled, it executes the fallback finalization as guaranteed by `REQ-TRK-033`.

---

## 4. User Scope Grounding (ATT-1250)

### In-Scope Objectives:
1. **`TrackerService.ACTION_STOP` Implementation**:
   - Define `public static final String ACTION_STOP = "com.atrainingtracker.trainingtracker.tracker.TrackerService.ACTION_STOP"`.
   - Implement `handleActionStop()` in `TrackerService.onStartCommand()` to initiate foreground-protected finalization on `mDbExecutor`.
   - Maintain foreground status and notification during `mDbExecutor` task execution.
   - Defer `unbindService(mBanalConnection)`, `performStopForeground()`, and `performStopSelf()` to main-thread completion callback after `endWorkout()` succeeds or fails.
2. **`TrainingApplication.stopTracking()` Migration**:
   - Replace direct `stopService(...)` call with `startService(new Intent(this, TrackerService.class).setAction(ACTION_STOP))`.
   - Guard against calling `startService` if `TrackerService` is not running.
3. **Consolidation of Duplicate Reset Call**:
   - Remove redundant `TrainingApplication.resetTrackingStateOnAbort();` from `TrackerService.java:633`.
4. **Resilience & Safety Guards**:
   - Guard against duplicate `ACTION_STOP` delivery (`mIsStopping` flag).
   - Retain `onDestroy()` fallback behavior for system-initiated kills.

### Out-of-Scope Objectives:
- Refactoring `BANALService` internal sensor drivers.
- Introducing WorkManager dependencies or Room migrations.
- Altering the SQLite table schema for `WorkoutSummaries` or `WorkoutSamples`.

---

## 5. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

1. **Original Requirement ID & Target**:
   `REQ-TRK-034` (*Foreground-Protected Asynchronous Workout Finalization and Stop Lifecycle Isolation*), extending `REQ-TRK-033` (ATT-4006), `REQ-TRK-030` (ATT-3986), and `REQ-TRK-015` (ATT-2641).
   Targets: `TrackerService.java`, `TrainingApplication.java`, `TrackerServiceFinalizationTest.kt`, `TrackerServiceResilienceTest.kt`.

2. **Historical Origin & Commit Trace**:
   - `ATT-2641` (`d497f85b`): Introduced `mDbExecutor.awaitTermination(5, TimeUnit.SECONDS)` in `onDestroy()`.
   - `ATT-4006` (`814919cd`, `REQ-TRK-033`): Eliminated main-thread blocking in `onDestroy()` by enqueuing `endWorkout()` to `mDbExecutor` and terminating `mScheduler`. Finding 2 in review noted the LMK vulnerability from calling `stopForeground()` before `mDbExecutor` completes.

3. **Root Reason for Existing Formulation**:
   `stopForeground()` and `unbindService()` were placed in `onDestroy()` under the assumption that `onDestroy()` was the only place to release service resources when `stopService()` was called.

4. **Preservation of Core Invariants**:
   - Non-blocking main thread: `ACTION_STOP` dispatches `endWorkout()` to `mDbExecutor` without blocking the main thread.
   - Strict FIFO ordering: `mScheduler` is shut down before `finalizationTask` is queued, ensuring no extra ticks are enqueued.
   - `BANALService` integrity: `mBanalConnection` is maintained until `endWorkout()` finishes.

---

## 6. Affected Files & Artifacts

- Production Code:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`
- Living Documentation:
  - `docs/requirements.md` (`REQ-TRK-034`)
  - `docs/tests.md` (`TST-TRK-026`)
- Test Code:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceFinalizationTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResilienceTest.kt`

---

## 7. Verification Strategy & Edge Cases

1. **Unit Test Scenarios**:
   - Verify `ACTION_STOP` keeps service in foreground while `endWorkout()` is running on `mDbExecutor`.
   - Verify `performStopForeground()` and `stopSelf()` are only invoked after `finalizationTask` completes.
   - Verify `unbindService(mBanalConnection)` is called after `endWorkout()` finishes, not before.
   - Verify duplicate `ACTION_STOP` calls are safely ignored.
   - Verify redundant `TrainingApplication.resetTrackingStateOnAbort()` removal does not break `handleTrackingAborted()`.
   - Verify `onDestroy()` fallback still works if service is destroyed without `ACTION_STOP`.
2. **Clean-room Regression**:
   - Run `./gradlew testDebugUnitTest` across the complete unit test suite.
