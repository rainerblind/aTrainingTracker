# Stage 1 Analysis: ATT-3981 - [Highest] Single unchecked exception permanently stops 1 Hz sampling loop and clock timer

**Ticket**: [ATT-3981](https://rainerblind.atlassian.net/browse/ATT-3981)  
**Sub-task**: [ATT-4052](https://rainerblind.atlassian.net/browse/ATT-4052) (`[Analysis]`)  
**Parent Epic**: None (Standalone Defect from `code-review-2026-10`)  
**Target Release**: `TBD (Set on completion per Rule 14)`  
**Active Sprint**: `Sprint 2026-42.9`  
**Branch**: `feature/ATT-3981`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Statement & Motivation

During an active workout recording session in **aTrainingTracker**, two periodic 1 Hz background tasks drive all timekeeping and sensor telemetry persistence:
1. **`TrackerService.tracker` (`TrackerService.java:351–370`)**: Scheduled via `mScheduler.scheduleAtFixedRate(tracker, 0, 1, TimeUnit.SECONDS)` (`TrackerService.java:634–636`) on a single-threaded `ScheduledExecutorService`. Each tick samples active sensors from `BANALService`, updates `LiveWorkoutSession` extrema and streams, enqueues SQLite persistence to `WorkoutSamples` and `WorkoutSummaries`, evaluates auto-pause transitions, and refreshes the foreground service notification.
2. **`ClockDevice.ClockTimeTask` (`ClockDevice.java:156–170`)**: Scheduled via `timer.scheduleAtFixedRate(new ClockTimeTask(), 0, 1000)` (`ClockDevice.java:121`) on a `java.util.Timer` background thread. Each tick increments `TIME_TOTAL`, `TIME_ACTIVE`, and `TIME_LAP`, formats `TIME_OF_DAY`, and broadcasts `BANALService.NEW_TIME_EVENT_INTENT`.

Neither `TrackerService.tracker.run()` nor `ClockDevice.ClockTimeTask.run()` wraps its body in a `try / catch (Throwable t)` guard:
- Per the `ScheduledExecutorService.scheduleAtFixedRate` contract, *"If any execution of the task encounters an exception, subsequent executions are suppressed."* A single uncaught exception in `tracker.run()` permanently halts the 1 Hz sampling loop for the remainder of the workout while the foreground notification still displays "tracking" and the CPU `WakeLock` remains held.
- Per the `java.util.Timer` contract, *"If a timer task's run method throws an uncaught exception, the Timer thread terminates abruptly, behaving as if cancel() had been called on the Timer."* A single uncaught exception in `ClockTimeTask.run()` kills the timer thread permanently, freezing `TIME_TOTAL`, `TIME_ACTIVE`, `TIME_LAP`, `TIME_OF_DAY`, and `NEW_TIME_EVENT_INTENT` broadcasts.
- Compounding this fragility, `mBanalService` in `TrackerService.java:178` is declared without `volatile` and is mutated on the Android main thread (`mBanalConnection.onServiceConnected` at line 374, `onServiceDisconnected` at line 408, and `onDestroy` at line 776) while being dereferenced repeatedly without local snapshotting on the `mScheduler` thread (`tracker.run()`, `sampleAndWriteToDb()`, `updateActiveSessionDevices()`, `evaluateAutoPauseTick()`) and `mDbExecutor` thread (`getSportTypeId()`). A mid-tick service disconnect or teardown race can therefore trigger a `NullPointerException` inside `tracker.run()`, permanently killing the 1 Hz recording loop.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Uncaught Exceptions in `TrackerService.tracker.run()` (`TrackerService.java:351–370`)
```java
final Runnable tracker = new Runnable() {
    public void run() {
        if (DEBUG) {
            Log.d(TAG, "sampling/tracking");
        }

        if (mBanalService != null) {

            sampleAndWriteToDb();

            // update notification
            if (DEBUG) Log.i(TAG, "updating notification");
            mTrainingApplication.updateTimeAndDistanceToNotification(mBanalService.getBestSensorData(SensorType.TIME_ACTIVE),
                    mBanalService.getBestSensorData(SensorType.DISTANCE_m),
                    SportTypeDatabaseManager.getInstance(TrackerService.this).getUIName(mBanalService.getSportTypeId()));
            if (DEBUG) Log.i(TAG, "updated notification");

        }
    }
};
```
Forensic trace of throw sources reachable from `tracker.run()`:
1. **Cross-Thread Race on Non-Volatile `mBanalService`**:
   - `mBanalService` (line 178) is not `volatile`.
   - Even after `if (mBanalService != null)` succeeds at line 357, `mBanalConnection.onServiceDisconnected()` (line 408) or `onDestroy()` (line 776) on the main thread can set `mBanalService = null` before lines 363–365 or inside `sampleAndWriteToDb()` (lines 1278, 1323, 1324, 1348, 1385, 1452, 1453) or `evaluateAutoPauseTick()` (lines 1552, 1557).
   - Dereferencing `mBanalService.getBestSensorData(...)`, `mBanalService.getAllSensorData()`, or `mBanalService.getSportTypeId()` after `mBanalService` becomes `null` throws an uncaught `NullPointerException`.
2. **Downstream Collaborator Exceptions**:
   - `mBanalService.getAllSensorData()` and `getBestSensorData(...)` iterate over live sensor collections managed across device threads (`DeviceManager` / `MySensorManager`).
   - `evaluateAutoPauseTick()` calls `getAutoPauseConfig()` -> `SettingsDataStoreJavaHelper.getAutoPauseConfig(this)` and `mAutoPauseManager.evaluateTick(...)`.
   - `mLiveSession.addSample(...)`, `mWorkoutRepository.updateExtremaValue(...)`, and `mLiveSession.recordStreamPoint(...)` execute on each active tick.
   - `mTrainingApplication.updateTimeAndDistanceToNotification(...)` and `SportTypeDatabaseManager.getInstance(...).getUIName(...)` query SQLite and Android `NotificationManager`.
   - Because `ScheduledThreadPoolExecutor.ScheduledFutureTask.run()` catches `Throwable` internally only to call `setException(ex)` and cancel future periodic runs, any transient runtime failure in any of these calls silently and permanently stops `tracker`.

### 2.2 Uncaught Exceptions in `ClockDevice.ClockTimeTask.run()` (`ClockDevice.java:156–170`)
```java
private class ClockTimeTask extends TimerTask {
    @Override
    public void run() {
        mTotalTimeSensor_s.increment(1);
        mActiveTimeSensor_s.increment(1);
        mLapTimeSensor_s.increment(1);

        mTimeOfDaySensor.newValue(df.format(Calendar.getInstance().getTime()));

        // send broadcast
        if (DEBUG) Log.d(TAG, "sending new time event broadcast");
        mContext.sendBroadcast(new Intent(BANALService.NEW_TIME_EVENT_INTENT)
                .setPackage(mContext.getPackageName()));
    }
}
```
Forensic trace of throw sources reachable from `ClockTimeTask.run()`:
1. `mTotalTimeSensor_s.increment(1)`, `mActiveTimeSensor_s.increment(1)`, `mLapTimeSensor_s.increment(1)`, and `mTimeOfDaySensor.newValue(...)` invoke `MySensor.newValue(...)`, which notifies registered `SensorListener` instances. If any listener or formatter throws a runtime exception, it propagates out of `ClockTimeTask.run()`.
2. `mContext.sendBroadcast(...)` interacts with the Android system server / context. Any transient runtime exception propagating out of `ClockTimeTask.run()` causes `java.util.TimerThread.mainLoop()` to exit its `while (true)` loop, permanently killing the clock timer.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Wrap the body of `TrackerService.tracker.run()` in `try { ... } catch (Throwable t)` so that any uncaught exception/throwable during a 1 Hz sampling tick is logged (`Log.e(TAG, ...)`) and counted (`mSamplingErrorCount`) without suppressing subsequent scheduled executions of the 1 Hz loop.
  2. Wrap the body of `ClockDevice.ClockTimeTask.run()` in `try { ... } catch (Throwable t)` so that any uncaught exception/throwable during a 1 Hz clock tick is logged (`Log.e(TAG, ...)`) and counted (`mClockTimerErrorCount`) without terminating the `java.util.Timer` thread.
  3. Declare `mBanalService` as `volatile` in `TrackerService.java` and snapshot it into a local variable (`final BANALServiceComm banalService = mBanalService;`) once per tick in `tracker.run()` (passing it into `sampleAndWriteToDb`, `updateActiveSessionDevices`, and `evaluateAutoPauseTick`) and in all other methods (`createNewLap`, `getSportTypeId`, `onSearchingFinished`, `endWorkout`, `finalizeLiveSession`) where `mBanalService` is dereferenced.
  4. Write unit tests proving that when one tick throws an unchecked exception in `tracker.run()` and in `ClockTimeTask.run()`, subsequent ticks continue executing and advancing time/sampling, and verifying `mBanalService` local-snapshot null-safety under concurrent disconnection.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Replacing `java.util.Timer` in `ClockDevice` with a different scheduling architecture (preserving the existing `Timer` lifecycle while making `ClockTimeTask.run()` exception-safe).
  * Modifying `DeviceManager` internal map data structures (tracked separately if applicable).
  * Altering UI screens, Compose components, or localization files.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-TRK-027` (*Fault-Tolerant 1 Hz Sampling Loop, Clock Timer Exception Guard, and Volatile `BANALServiceComm` Snapshot Concurrency Safety*), complementing `REQ-TRK-015` (`ATT-2641`) and `REQ-TRK-026` (`ATT-3980`). Targets `TrackerService.java`, `ClockDevice.java`, `TrackerServiceResilienceTest.kt`, and `ClockDeviceResilienceTest.kt`.
* **Historical Origin & Commit Trace**:
  * `TrackerService.tracker` and `ClockDevice.ClockTimeTask` originate from the initial service architecture (`mScheduler.scheduleAtFixedRate(tracker, 0, 1, TimeUnit.SECONDS)` and `timer.scheduleAtFixedRate(new ClockTimeTask(), 0, 1000)`), which assumed that `tracker.run()` and `ClockTimeTask.run()` would never throw unchecked exceptions.
* **Root Reason for Existing Formulation**:
  * Early versions of `TrackerService` and `ClockDevice` relied on single-process assumptions and lacked defensive exception barriers around periodic `Runnable` / `TimerTask` entry points.
* **Preservation of Core Invariants**:
  * Catching `Throwable` at the top-level periodic task boundary in `tracker.run()` and `ClockTimeTask.run()` and snapshotting `volatile BANALServiceComm mBanalService` into a local variable preserves 100% of existing normal-path sampling, notification updates, auto-pause evaluation, and time sensor increments while guaranteeing that a transient error on tick $k$ never suppresses tick $k+1$.

---

## 5. Architectural Strategy & High-Level Solution

1. **Volatile Reference & Local Snapshot in `TrackerService.java`**:
   - Change `@Nullable BANALServiceComm mBanalService;` (line 178) to `@Nullable volatile BANALServiceComm mBanalService;`.
   - In `tracker.run()`, read `final BANALServiceComm banalService = mBanalService;` once at the start of the tick. If `banalService != null`, pass `banalService` to `sampleAndWriteToDb(banalService)` (keep a parameterless `sampleAndWriteToDb()` delegating to `sampleAndWriteToDb(mBanalService)` for backward compatibility with existing reflection-based tests in `TrackerServiceResumeTest` and `TrackerServiceWorkoutStartTest`).
   - Inside `sampleAndWriteToDb(@Nullable BANALServiceComm banalService)`, `updateActiveSessionDevices(@Nullable BANALServiceComm banalService)`, and `evaluateAutoPauseTick(@Nullable BANALServiceComm banalService)`, use only the non-null local `banalService` reference.
   - Similarly snapshot `final BANALServiceComm banalService = mBanalService;` in `createNewLap()`, `getSportTypeId()`, `onSearchingFinished()`, `endWorkout()`, and `finalizeLiveSession()`.
2. **Top-Level `try / catch (Throwable t)` Guard in `TrackerService.tracker.run()`**:
   - Wrap the body of `tracker.run()` in `try { ... } catch (Throwable t) { mSamplingErrorCount.incrementAndGet(); Log.e(TAG, "Unhandled exception in 1 Hz tracker sampling loop (count=" + mSamplingErrorCount.get() + ")", t); }`.
   - Expose `@VisibleForTesting int getSamplingErrorCount()` for deterministic verification.
3. **Top-Level `try / catch (Throwable t)` Guard in `ClockDevice.ClockTimeTask.run()`**:
   - Wrap the body of `ClockTimeTask.run()` in `try { ... } catch (Throwable t) { mClockTimerErrorCount.incrementAndGet(); Log.e(TAG, "Unhandled exception in 1 Hz ClockTimeTask (count=" + mClockTimerErrorCount.get() + ")", t); }`.
   - Expose `@VisibleForTesting int getClockTimerErrorCount()` and package-private `@VisibleForTesting void runClockTickForTesting()` (or package-private `ClockTimeTask`) for deterministic unit testing.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing `TrackerService` and `BANALService` unit tests (`TrackerServiceResumeTest`, `TrackerServiceResilienceTest`, `TrackerServiceAsyncInitTest`, `TrackerServiceFinalizationTest`, `TrackerServiceFGSTypeTest`, `TrackerServiceLapTest`, `TrackerServiceWorkoutStartTest`, `FreshInstallLocationInitTest`).
  2. Thread safety on database and sensor single-thread dispatchers is strengthened via `volatile` visibility and local reference confinement.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  * The changes are purely defensive concurrency and exception-boundary guards in `TrackerService.java` and `ClockDevice.java` with zero changes to business logic on healthy ticks.
