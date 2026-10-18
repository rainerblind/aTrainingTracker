# Stage 3: Implementation Plan - ATT-3981: [Highest] Single unchecked exception permanently stops 1 Hz sampling loop and clock timer

**Ticket**: [ATT-3981](https://rainerblind.atlassian.net/browse/ATT-3981)  
**Sub-task**: [ATT-4054](https://rainerblind.atlassian.net/browse/ATT-4054) (`[Impl-Plan]`)  
**Parent Epic**: None (Standalone Defect from `code-review-2026-10`)  
**Target Release**: `TBD (Set on completion per Rule 14)`  
**Active Sprint**: `Sprint 2026-42.9`  
**Requirement Mapping**: `REQ-TRK-027`  
**Test Mapping**: `TST-TRK-019`  
**Branch**: `feature/ATT-3981`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Background

`TrackerService` runs its 1 Hz workout sampling loop via `mScheduler.scheduleAtFixedRate(tracker, 0, 1, TimeUnit.SECONDS)` (`TrackerService.java:634`), and `ClockDevice` runs its 1 Hz time-sensor loop via `timer.scheduleAtFixedRate(new ClockTimeTask(), 0, 1000)` (`ClockDevice.java:121`). Neither `TrackerService.tracker.run()` nor `ClockDevice.ClockTimeTask.run()` wraps its body in a `try / catch (Throwable t)` exception barrier. If a single tick throws an unchecked exception, `ScheduledExecutorService.scheduleAtFixedRate` suppresses all future executions of `tracker` and `java.util.Timer` terminates its background timer thread permanently. Furthermore, `mBanalService` in `TrackerService.java:178` is non-`volatile` and dereferenced multiple times per tick without local snapshotting while `onServiceDisconnected()` or `onDestroy()` can null it on the main thread, exposing a cross-thread `NullPointerException` race.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TRK-027` (*Fault-Tolerant 1 Hz Sampling Loop, Clock Timer Exception Guard, and Volatile `BANALServiceComm` Snapshot Concurrency Safety*)
* **Test Mapping**: `TST-TRK-019` (*Fault-Tolerant 1 Hz Sampling Loop, Clock Timer Exception Guard & Volatile `mBanalService` Snapshot Concurrency Verification Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing `TrackerService` and `BANALService` unit test suites (`TrackerServiceResumeTest`, `TrackerServiceResilienceTest`, `TrackerServiceAsyncInitTest`, `TrackerServiceFinalizationTest`, `TrackerServiceFGSTypeTest`, `TrackerServiceLapTest`, `TrackerServiceWorkoutStartTest`, `FreshInstallLocationInitTest`) continue to pass cleanly.
2. **Reflection Compatibility**: The parameterless `private void sampleAndWriteToDb()` method in `TrackerService.java` is retained (delegating to `sampleAndWriteToDb(mBanalService)`) so reflection-based tests in `TrackerServiceResumeTest` and `TrackerServiceWorkoutStartTest` continue to work without modification.
3. **Thread Safety & Dispatcher Affinity**: Database operations remain isolated to `mDbExecutor`, and `mBanalService` gains `volatile` memory visibility and local reference confinement across `mScheduler`, `mDbExecutor`, and main-thread callbacks.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `TrackerService` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`)
1. **Volatile `mBanalService` & Atomic Error Counter**:
   - Declare `@Nullable volatile BANALServiceComm mBanalService;` (line 178).
   - Add `private final java.util.concurrent.atomic.AtomicInteger mSamplingErrorCount = new java.util.concurrent.atomic.AtomicInteger(0);` and package-private `@VisibleForTesting int getSamplingErrorCount() { return mSamplingErrorCount.get(); }`.
2. **Fault-Tolerant `tracker.run()` with Local `banalService` Snapshot (`REQ-TRK-027.1`, `REQ-TRK-027.2`)**:
   ```java
   final Runnable tracker = new Runnable() {
       @Override
       public void run() {
           try {
               if (DEBUG) {
                   Log.d(TAG, "sampling/tracking");
               }

               final BANALServiceComm banalService = mBanalService;
               if (banalService != null) {
                   sampleAndWriteToDb(banalService);

                   // update notification
                   if (DEBUG) Log.i(TAG, "updating notification");
                   if (mTrainingApplication != null) {
                       mTrainingApplication.updateTimeAndDistanceToNotification(
                               banalService.getBestSensorData(SensorType.TIME_ACTIVE),
                               banalService.getBestSensorData(SensorType.DISTANCE_m),
                               SportTypeDatabaseManager.getInstance(TrackerService.this).getUIName(banalService.getSportTypeId()));
                   }
                   if (DEBUG) Log.i(TAG, "updated notification");
               }
           } catch (Throwable t) {
               int errCount = mSamplingErrorCount.incrementAndGet();
               Log.e(TAG, "Unhandled exception in 1 Hz tracker sampling loop (count=" + errCount + ")", t);
           }
       }
   };
   ```
3. **Local Reference Snapshotting Across `TrackerService` Methods (`REQ-TRK-027.2`)**:
   - Keep `private void sampleAndWriteToDb() { sampleAndWriteToDb(mBanalService); }` and implement `private void sampleAndWriteToDb(@Nullable final BANALServiceComm banalService)` using only `banalService` (passing `banalService` into `updateActiveSessionDevices(banalService)` and `evaluateAutoPauseTick(banalService)`).
   - Snapshot `final BANALServiceComm banalService = mBanalService;` in `createNewLap()`, `getSportTypeId()`, `onSearchingFinished()`, `endWorkout()`, and `finalizeLiveSession()`.

### Component 2: `ClockDevice` (`app/src/main/java/com/atrainingtracker/banalservice/devices/ClockDevice.java`)
1. **Atomic Error Counter & Fault-Tolerant `ClockTimeTask.run()` (`REQ-TRK-027.3`)**:
   - Add `private final java.util.concurrent.atomic.AtomicInteger mClockTimerErrorCount = new java.util.concurrent.atomic.AtomicInteger(0);`.
   - Wrap `ClockTimeTask.run()` in `try { ... } catch (Throwable t)`:
     ```java
     private class ClockTimeTask extends TimerTask {
         @Override
         public void run() {
             try {
                 mTotalTimeSensor_s.increment(1);
                 mActiveTimeSensor_s.increment(1);
                 mLapTimeSensor_s.increment(1);

                 mTimeOfDaySensor.newValue(df.format(Calendar.getInstance().getTime()));

                 // send broadcast
                 if (DEBUG) Log.d(TAG, "sending new time event broadcast");
                 mContext.sendBroadcast(new Intent(BANALService.NEW_TIME_EVENT_INTENT)
                         .setPackage(mContext.getPackageName()));
             } catch (Throwable t) {
                 int errCount = mClockTimerErrorCount.incrementAndGet();
                 Log.e(TAG, "Unhandled exception in 1 Hz ClockTimeTask (count=" + errCount + ")", t);
             }
         }
     }
     ```
   - Expose `@VisibleForTesting public int getClockTimerErrorCount() { return mClockTimerErrorCount.get(); }` and `@VisibleForTesting public void runClockTickForTesting() { new ClockTimeTask().run(); }` for deterministic unit testing.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Gate 3 Pre-Check (Rule 4)
* Command: `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>`
* Verify exit code `0` (`GATE_PASSED`) before modifying any file under `app/src/...`.

### Step 2: Harden `TrackerService.java` (`REQ-TRK-027.1`, `REQ-TRK-027.2`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
* Changes:
  * Make `mBanalService` `volatile`.
  * Add `mSamplingErrorCount` and `@VisibleForTesting int getSamplingErrorCount()`.
  * Wrap `tracker.run()` in `try / catch (Throwable t)` with `Log.e` and error counting, and snapshot `final BANALServiceComm banalService = mBanalService;` across `tracker.run()`, `sampleAndWriteToDb`, `updateActiveSessionDevices`, `evaluateAutoPauseTick`, `createNewLap`, `getSportTypeId`, `onSearchingFinished`, `endWorkout`, and `finalizeLiveSession`.

### Step 3: Harden `ClockDevice.java` (`REQ-TRK-027.3`)
* Files: `app/src/main/java/com/atrainingtracker/banalservice/devices/ClockDevice.java`
* Changes:
  * Add `mClockTimerErrorCount`, `@VisibleForTesting public int getClockTimerErrorCount()`, and `@VisibleForTesting public void runClockTickForTesting()`.
  * Wrap `ClockTimeTask.run()` in `try / catch (Throwable t)` with `Log.e` and error counting.

### Step 4: Author Unit Tests & Execute Fast Targeted Test Loop (`TST-TRK-019`)
* Files:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResilienceTest.kt`
  * `app/src/test/java/com/atrainingtracker/banalservice/devices/ClockDeviceResilienceTest.kt`
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceResilienceTest" --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceResumeTest" --tests "com.atrainingtracker.banalservice.devices.ClockDeviceResilienceTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests (`TrackerServiceResilienceTest`, `TrackerServiceResumeTest`, `ClockDeviceResilienceTest`) during Stage 4 construction, followed by full clean-room regression suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-3981` allows full revert without affecting `sprint/2026-42.9` or `develop`.
