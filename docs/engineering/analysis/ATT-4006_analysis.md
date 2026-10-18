# Stage 1 Analysis: ATT-4006 - [Medium] TrackerService onDestroy blocks main thread and leaks scheduler thread

**Ticket**: [ATT-4006](https://rainerblind.atlassian.net/browse/ATT-4006)  
**Sub-task**: [ATT-4089](https://rainerblind.atlassian.net/browse/ATT-4089) (`[Analysis]`)  
**Parent Epic**: None (*Standalone Bug*)  
**Target Release**: `None` (Assigned upon Sprint Review completion per Rule 14)  
**Active Sprint**: `2026-42.9`  
**Branch**: `feature/ATT-4006`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Statement & Motivation

When an athlete stops a workout—particularly after a multi-hour ride with tens of thousands of recorded 1 Hz rows—`TrackerService.onDestroy()` executes on the Android main (UI) thread and performs two heavy synchronous operations:
1. It calls `mDbExecutor.awaitTermination(5, TimeUnit.SECONDS)` directly on the main thread, blocking UI rendering and input dispatch for up to 5 seconds while pending sample writes drain.
2. It then invokes `endWorkout()` synchronously on the main thread, which performs SQLite transactions (`LapsDatabaseManager`, `ActiveDevicesDbHelper`, `WorkoutSummariesDatabaseManager`), Ramer-Douglas-Peucker polyline simplification (`PolyUtil.simplify`), full-table `SELECT *` cursor iteration over `WorkoutSamples` (`finalizeLiveSession()` Step 3 computing Aerobic Efficiency, Decoupling, Normalized Power, IF, TSS, Work kJ, and W/kg), route clustering (`WorkoutClusterEngine`), auto-naming (`KnownLocationsDatabaseManager`), and file export (`ExportManager.workoutFinished()`).

Additionally, two lifecycle defects compound this problem:
* **Leaked Scheduler Thread & Tick Race**: `TrackerService` allocates `private final ScheduledExecutorService mScheduler = Executors.newScheduledThreadPool(1)` at field initialization (`TrackerService.java:171`), but `onDestroy()` only cancels `mTrackerHandle` (`mTrackerHandle.cancel(true)`) and **never shuts down `mScheduler`**. Every workout leaks one live scheduler thread for the lifetime of the OS process, and an in-flight tick can race with `endWorkout()` or submit tasks to `mDbExecutor` after `mDbExecutor.shutdown()`.
* **Stuck `TrackingMode.TRACKING` on Async Initialization Failure**: In `TrainingApplication.startTracking()` (`TrainingApplication.java:1512-1516`), `cTrackingMode` is immediately set to `TrackingMode.TRACKING` when `ContextCompat.startForegroundService(this, intent)` is called. If `TrackerService.onStartCommand` subsequently fails asynchronously on `mDbExecutor` during `START_NORMAL` database/table initialization (`TrackerService.java:596-606`) or during FGS promotion/resumption, `TrackerService` sets `mTrackingInterrupted = true`, shows the interrupted notification, and calls `performStopSelf()`. Because `onDestroy()` skips `endWorkout()` when `mTrackingInterrupted == true`, `TRACKING_FINISHED_INTENT` is never broadcast and `TrainingApplication.cTrackingMode` remains permanently stuck in `TrackingMode.TRACKING` even though `TrackerService` is dead.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic inspection of [`TrackerService.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java) and [`TrainingApplication.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java) confirms all three findings reported in the code review:

### 2.1 Main-Thread Blocking in `TrackerService.onDestroy()` (`TrackerService.java:780-820`)
```java
        // first of all, stop the trackerHandle
        if (mTrackerHandle != null) {
            mTrackerHandle.cancel(true);
        }

        mDbExecutor.shutdown();
        try {
            if (!mDbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                Log.w(TAG, "Database executor did not terminate in time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!mTrackingInterrupted) {
            endWorkout();
        }

        unbindService(mBanalConnection);
        mBanalService = null;
        ...
        releaseWakeLock();
```
* **Failure Mechanism**:
  * `Service.onDestroy()` runs on the Android main thread.
  * `mDbExecutor.awaitTermination(5, TimeUnit.SECONDS)` blocks the main thread for up to 5,000 ms if sample writes or altitude corrections are queued on `mDbExecutor`.
  * Immediately afterward, `endWorkout()` -> `finalizeLiveSession()` executes synchronously on the main thread. For a 6-hour workout (21,600 rows), `finalizeLiveSession()` queries the entire `WorkoutSamples` table (`SELECT *`), computes 30-second rolling fourth-power Normalized Power and aerobic decoupling, simplifies up to 21,600 GPS coordinates via `PolyUtil.simplify`, executes multiple SQLite transactions across `WorkoutSummaries`, `WorkoutExtrema`, `ActiveDevices`, and `Laps`, and triggers `ExportManager.workoutFinished()`.
  * Why was `awaitTermination(5, TimeUnit.SECONDS)` placed before `endWorkout()` originally? Because `endWorkout()` needed all queued 1 Hz sample writes on `mDbExecutor` to complete before computing final summary statistics. However, because `mDbExecutor` is a single-threaded FIFO executor (`Executors.newSingleThreadExecutor()`), queuing the terminal `endWorkout()` + `releaseWakeLock()` task directly onto `mDbExecutor` before calling `mDbExecutor.shutdown()` guarantees the exact same FIFO ordering (all pending sample writes finish before `endWorkout()` begins) with **zero main-thread blocking**.

### 2.2 Unclosed `mScheduler` Thread Leak & In-Flight Tick Race (`TrackerService.java:171, 668-670, 791-795`)
```java
    private final ScheduledExecutorService mScheduler = Executors.newScheduledThreadPool(1);
```
* **Failure Mechanism**:
  * `onDestroy()` calls `mTrackerHandle.cancel(true)` (lines 792-794) but never invokes `mScheduler.shutdown()` or `mScheduler.shutdownNow()`.
  * `Executors.newScheduledThreadPool(1)` keeps its core worker thread alive indefinitely until `shutdown()` / `shutdownNow()` is called. Across $N$ workouts in a single app session, $N$ `pool-X-thread-1` scheduler threads accumulate in the process.
  * Furthermore, if a 1 Hz tick is mid-execution on `mScheduler` when `onDestroy()` runs on the main thread, it can call `mDbExecutor.execute(...)` (`TrackerService.java:1594, 1813`) after `mDbExecutor.shutdown()`, throwing an unhandled `RejectedExecutionException`, or mutate `mLiveSession` concurrently while `endWorkout()` -> `finalizeLiveSession()` reads `mLiveSession`.

### 2.3 Stuck `TrainingApplication.cTrackingMode = TRACKING` on Async Init Failure (`TrackerService.java:584-607`, `TrainingApplication.java:1512-1516`)
```java
        // TrainingApplication.java:1512-1516
        try {
            ContextCompat.startForegroundService(this, intent);
            startPebbleWatchapp();

            cTrackingMode = TrackingMode.TRACKING;
            notifyTrackingStateChanged();
        } catch (IllegalStateException | SecurityException e) { ... }
```
```java
        // TrackerService.java:596-606
        } catch (Throwable t) {
            Log.e(TAG, "Fatal error initializing workout database asynchronously", t);
            initFuture.completeExceptionally(t);
            mTrackingInterrupted = true;
            if (mTrackerHandle != null) {
                mTrackerHandle.cancel(true);
                mTrackerHandle = null;
            }
            showTrackingInterruptedNotification();
            performStopSelf();
        }
```
* **Failure Mechanism**:
  * `TrainingApplication.startTracking()` optimistically transitions `cTrackingMode` to `TrackingMode.TRACKING` and broadcasts `TRACKING_STATE_CHANGED` as soon as `startForegroundService` returns.
  * When `createNewWorkout()` or `WorkoutSamplesDatabaseManager.createNewTable(...)` throws inside `mDbExecutor.submit(...)` (or when `performStartForeground` / `applyResumedState` aborts), `TrackerService` sets `mTrackingInterrupted = true` and calls `performStopSelf()`.
  * Neither the catch block nor `onDestroy()` resets `TrainingApplication`'s tracking state (`cTrackingMode = TrackingMode.READY`, `sActiveWorkoutId = -1`, `setManualPauseLock(false)`, `setAutoPaused(false)`, `stopPebbleWatchapp()`, cancelling `TRACKING_NOTIFICATION_ID`, and broadcasting `TRACKING_STATE_CHANGED` / `TRACKING_ABORTED_INTENT`) on the main thread. Consequently, the UI and `TrainingApplication.isTracking()` remain stuck in `TrackingMode.TRACKING`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Non-Blocking `onDestroy()` & Off-Main-Thread Finalization**: Remove `mDbExecutor.awaitTermination(5, TimeUnit.SECONDS)` and synchronous `endWorkout()` from the main thread in `TrackerService.onDestroy()`. Enqueue the terminal workout finalization (`endWorkout()` when `!mTrackingInterrupted`) and `releaseWakeLock()` onto `mDbExecutor` before calling `mDbExecutor.shutdown()`, ensuring main-thread `onDestroy()` completes in `< 100 ms` (typically `< 5 ms`) while preserving strict FIFO ordering after pending sample writes.
  2. **Deterministic `mScheduler` Teardown & Zero Thread Leak**: Name the executor threads (`TrackerService-Scheduler`, `TrackerService-DbExecutor`), cancel `mTrackerHandle`, call `mScheduler.shutdownNow()` with a bounded micro-wait (`awaitTermination(20, TimeUnit.MILLISECONDS)`) in `onDestroy()` before queuing terminal finalization on `mDbExecutor`, and guard all `mDbExecutor` task submissions against post-shutdown `RejectedExecutionException`.
  3. **Main-Thread State Reset & Broadcast on Init/Startup Failure**: On async `START_NORMAL` initialization failure (and FGS/resume abort paths), dispatch state recovery to the main thread (`runOnMainThread`) to reset `TrainingApplication` (`onTrackingAborted()` -> `cTrackingMode = TrackingMode.READY`, `sActiveWorkoutId = -1`, pause flags cleared, Pebble watchapp stopped, ongoing notification cancelled, `TRACKING_STATE_CHANGED` and `TRACKING_ABORTED_INTENT` broadcast).
  4. **Unit Test Determinism & Verification**: Expose `getFinalizationFuture()` (`@VisibleForTesting`) on `TrackerService` for deterministic test synchronization and add unit tests verifying non-blocking `onDestroy()` (`< 100 ms`), zero leaked threads across sequential workouts, guaranteed background finalization + WakeLock release, and `TrainingApplication` state reset on async initialization failure.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Migrating `TrackerService` to Kotlin coroutines or redesigning `ExportManager`'s internal file writers.
  * Altering SQLite schemas or UI composable layouts.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:
  * **Modified Requirement**: `REQ-STB-009` (*Asynchronous Non-Blocking Workout Initialization and Sample Buffering in TrackerService*, Clause 6) and **Net-New Requirement**: `REQ-TRK-033` (*Non-Blocking Asynchronous Workout Teardown, Scheduler Thread Lifecycle Hygiene, and Main-Thread Initialization Abort Recovery*), complementing `REQ-TRK-016` (`ATT-2972`) and `REQ-TRK-030` (`ATT-3986`).
* **Historical Origin & Commit Trace**:
  * `REQ-STB-009` was introduced in commit `3d033495` (`ATT-1245` / `ATT-1262`: *Offload TrackerService Workout Initialization from Main Thread*) to move `createNewWorkout()` and `createNewTable()` off the main thread onto `mDbExecutor` using `mTableInitializationFuture`.
  * `REQ-TRK-016` (`ATT-2641` / `ATT-2972`) and `REQ-TRK-030` (`dc61fdfb`, `ATT-3986`) hardened `endWorkout()` transaction isolation and `TRACKING_FINISHED_INTENT` dispatch in `finally`.
* **Root Reason for Existing Formulation**:
  * When `ATT-1245` introduced `mDbExecutor` for async sample writes, `onDestroy()` called `mDbExecutor.shutdown(); mDbExecutor.awaitTermination(5, TimeUnit.SECONDS);` on the main thread so that all queued sample writes would finish before `endWorkout()` ran on the main thread.
  * Similarly, `REQ-STB-009` Clause 6 specified setting `mTrackingInterrupted = true`, showing the interrupted notification, and calling `performStopSelf()` on async DB init failure, overlooking that `TrainingApplication.startTracking()` had already set `cTrackingMode = TrackingMode.TRACKING`.
* **Preservation of Core Invariants**:
  * Submitting the terminal finalization task (`if (!mTrackingInterrupted) endWorkout();` + `finally { mBanalService = null; releaseWakeLock(); }`) to the single-threaded `mDbExecutor` **before** `mDbExecutor.shutdown()` preserves the exact FIFO invariant that `awaitTermination(5s)` was trying to achieve—every queued sample write completes before `endWorkout()` starts—without blocking the Android main thread.
  * Holding the `PARTIAL_WAKE_LOCK` until the terminal task's `finally` block executes on `mDbExecutor` guarantees the CPU cannot suspend mid-finalization.
  * Resetting `TrainingApplication` to `TrackingMode.READY` on the main thread when initialization aborts preserves `REQ-STB-009` Clause 6 (`mTrackingInterrupted = true`, interrupted notification shown, `performStopSelf()`) while eliminating the stuck `TRACKING` state.

---

## 5. Architectural Strategy & High-Level Solution

1. **Named Single-Thread Executors & Scheduler Teardown (`TrackerService.java`)**:
   * Initialize `mScheduler` and `mDbExecutor` with descriptive daemon-safe thread names (`"TrackerService-Scheduler"` and `"TrackerService-DbExecutor"`).
   * In `onDestroy()`:
     1. Cancel `mTableInitializationFuture` if incomplete.
     2. Cancel `mTrackerHandle` (`cancel(true)`) and immediately invoke `mScheduler.shutdownNow()`, followed by a bounded micro-wait (`mScheduler.awaitTermination(20, TimeUnit.MILLISECONDS)`) so any in-flight tick exits cleanly before finalization is queued and no scheduler thread is leaked.
     3. Unregister broadcast receivers, release `mAutoPauseManager`, and call `unbindService(mBanalConnection)` on the main thread (keeping the local `mBanalService` reference valid until `finalizationTask` finishes so `endWorkout()` can read final sensor/lap state).
     4. Submit `finalizationTask` to `mDbExecutor` and call `mDbExecutor.shutdown()` without blocking the main thread. Inside `finalizationTask`:
        ```java
        try {
            if (!interrupted) {
                endWorkout();
            }
        } catch (Throwable t) {
            Log.e(TAG, "Unexpected error during workout finalization in onDestroy", t);
        } finally {
            mBanalService = null;
            releaseWakeLock();
            finalizationFuture.complete(null);
        }
        ```
   * Guard all `mDbExecutor` task submissions with a helper `executeOnDbExecutor(Runnable)` that checks `!mDbExecutor.isShutdown()` and catches `RejectedExecutionException`.
2. **Main-Thread Recovery on Async Init / Startup Abort (`TrackerService.java` & `TrainingApplication.java`)**:
   * Add `public void onTrackingAborted()` and static fallback state reset in `TrainingApplication.java` (`cTrackingMode = TrackingMode.READY`, `sActiveWorkoutId = -1`, `mWorkoutID = -1`, clear manual/auto pause flags, stop Pebble watchapp, cancel `TRACKING_NOTIFICATION_ID`, and call `notifyTrackingStateChanged()`). Also mark `cTrackingMode` as `volatile` for cross-thread visibility.
   * Add `public static final String TRACKING_ABORTED_INTENT = "de.rainerblind.trainingtracker.TrackerService.TRACKING_ABORTED_INTENT";` and `protected void handleTrackingAborted()` in `TrackerService.java`, which uses `runOnMainThread(...)` to reset `TrainingApplication` state, broadcast `TRACKING_ABORTED_INTENT`, show the interrupted notification, and call `performStopSelf()`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features and unit tests (`TrackerServiceFinalizationTest`, `TrackerServiceResilienceTest`, `TrackerServiceAsyncInitTest`, `TrackerServiceResumeTest`, `TrackerServiceWorkoutStartTest`).
  2. Strict FIFO ordering on `mDbExecutor`: all queued 1 Hz sample writes execute before `endWorkout()`.
  3. Guaranteed `TRACKING_FINISHED_INTENT` broadcast and `WakeLock` release in `finally` blocks (Rule 29).
  4. Parent ticket Human Decision Gate remains strictly enforced (Rule 3).
* **Risk Rating**: **LOW**
  * The refactoring preserves single-thread sequential execution of all database writes and finalization steps on `mDbExecutor` while eliminating main-thread blocking and closing the `mScheduler` thread leak.
