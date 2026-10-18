# Stage 2: Requirement & Test Specification - ATT-3981: [Highest] Single unchecked exception permanently stops 1 Hz sampling loop and clock timer

**Ticket**: [ATT-3981](https://rainerblind.atlassian.net/browse/ATT-3981)  
**Sub-task**: [ATT-4053](https://rainerblind.atlassian.net/browse/ATT-4053) (`[Req & Test Spec]`)  
**Parent Epic**: None (Standalone Defect from `code-review-2026-10`)  
**Target Release**: `TBD (Set on completion per Rule 14)`  
**Active Sprint**: `Sprint 2026-42.9`  
**Requirement Mapping**: `REQ-TRK-027` (*Fault-Tolerant 1 Hz Sampling Loop, Clock Timer Exception Guard, and Volatile `BANALServiceComm` Snapshot Concurrency Safety*)  
**Test Spec ID**: `TST-TRK-019`  
**Branch**: `feature/ATT-3981`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Requirement Specification (`REQ-TRK-027`)

### 1.1 Problem Statement & Rationale
`ScheduledExecutorService.scheduleAtFixedRate` (`TrackerService.java:634`) suppresses all subsequent executions if `tracker.run()` throws an unchecked exception, and `java.util.Timer` (`ClockDevice.java:121`) terminates its background timer thread permanently if `ClockTimeTask.run()` throws an unchecked exception. Neither `Runnable` / `TimerTask` previously wrapped its execution in a `try / catch (Throwable t)` barrier. Additionally, `mBanalService` in `TrackerService.java:178` was non-`volatile` and dereferenced multiple times across threads (`mScheduler` and `mDbExecutor`) without local reference snapshotting while `mBanalConnection.onServiceDisconnected()` or `onDestroy()` could null it on the main thread, creating a direct `NullPointerException` race inside the 1 Hz loop.

### 1.2 Functional & Architectural Requirements
1. **Fault-Tolerant 1 Hz Sampling Loop in `TrackerService` (`REQ-TRK-027.1`)**:
   - `TrackerService.tracker.run()` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`) SHALL wrap its entire execution body in a `try { ... } catch (Throwable t)` guard so that any runtime exception or throwable thrown during `sampleAndWriteToDb(...)` or foreground notification updates is caught, increments an atomic error counter (`mSamplingErrorCount`), and logs a diagnostic error (`Log.e(TAG, ...)`) without escaping to `ScheduledExecutorService.scheduleAtFixedRate` or suppressing subsequent 1 Hz ticks.
   - `TrackerService` SHALL expose a package-private `@VisibleForTesting int getSamplingErrorCount()` accessor returning the accumulated error count for deterministic test verification.
2. **Volatile `mBanalService` Visibility & Local Reference Snapshotting (`REQ-TRK-027.2`)**:
   - `TrackerService.mBanalService` SHALL be declared `volatile` (`@Nullable volatile BANALServiceComm mBanalService;`) to guarantee happens-before memory visibility between main-thread binder callbacks (`onServiceConnected`, `onServiceDisconnected`, `onDestroy`) and background worker threads (`mScheduler`, `mDbExecutor`).
   - `TrackerService.tracker.run()` SHALL snapshot `final BANALServiceComm banalService = mBanalService;` into a local variable once at the start of each tick and pass that non-null snapshot through `sampleAndWriteToDb(@Nullable BANALServiceComm banalService)`, `updateActiveSessionDevices(@Nullable BANALServiceComm banalService)`, and `evaluateAutoPauseTick(@Nullable BANALServiceComm banalService)` (while retaining the parameterless `sampleAndWriteToDb()` overload delegating to `sampleAndWriteToDb(mBanalService)` for reflection test compatibility).
   - All other methods in `TrackerService` that dereference `mBanalService` (`createNewLap()`, `getSportTypeId()`, `onSearchingFinished()`, `endWorkout()`, `finalizeLiveSession()`) SHALL snapshot `final BANALServiceComm banalService = mBanalService;` into a local variable before null-checking and invoking methods on it, preventing mid-method `NullPointerException` races when `onServiceDisconnected()` nulls `mBanalService` concurrently.
3. **Fault-Tolerant 1 Hz Clock Timer in `ClockDevice` (`REQ-TRK-027.3`)**:
   - `ClockDevice.ClockTimeTask.run()` (`app/src/main/java/com/atrainingtracker/banalservice/devices/ClockDevice.java`) SHALL wrap its entire execution body in a `try { ... } catch (Throwable t)` guard so that any runtime exception or throwable thrown during time sensor increments (`TIME_TOTAL`, `TIME_ACTIVE`, `TIME_LAP`), `TIME_OF_DAY` formatting, or `NEW_TIME_EVENT_INTENT` broadcasting is caught, increments an atomic error counter (`mClockTimerErrorCount`), and logs a diagnostic error (`Log.e(TAG, ...)`) without terminating the `java.util.Timer` thread.
   - `ClockDevice` SHALL expose package-private `@VisibleForTesting int getClockTimerErrorCount()` and `@VisibleForTesting void runClockTickForTesting()` accessors for deterministic unit testing.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Sampling Loop Survival Across Unchecked Exception)**:
  * *Given* the 1 Hz sampling loop (`TrackerService.tracker`) is running (either invoked directly across sequential ticks or scheduled via `ScheduledExecutorService.scheduleAtFixedRate`),
  * *When* one tick throws an unchecked exception (e.g., `RuntimeException` / `NullPointerException` from `mBanalService` or notification update),
  * *Then* the exception is caught and logged via `Log.e`, `getSamplingErrorCount()` increments by `1`, and the subsequent ticks continue to execute, sample sensor data, and write to the database.
* **Criterion 2 (Concurrent `mBanalService` Disconnection Snapshot Safety)**:
  * *Given* `TrackerService.tracker.run()` or `sampleAndWriteToDb()` starts executing with a non-null `mBanalService`,
  * *When* `mBanalConnection.onServiceDisconnected()` concurrently sets `mBanalService = null` on the main thread mid-tick,
  * *Then* the in-flight tick completes using its local `banalService` snapshot without throwing a `NullPointerException`, and the subsequent tick safely observes `mBanalService == null` via `volatile` visibility.
* **Criterion 3 (Clock Timer Survival Across Unchecked Exception)**:
  * *Given* `ClockDevice` and its `ClockTimeTask` periodic timer are running,
  * *When* one tick throws an unchecked exception (e.g., `RuntimeException` during broadcast or sensor listener callback),
  * *Then* the exception is caught and logged via `Log.e`, `getClockTimerErrorCount()` increments by `1`, and on the next elapsed period `TIME_TOTAL`, `TIME_ACTIVE`, and `TIME_LAP` sensors continue to advance.

### 1.4 System Invariants
1. Zero regression in existing `TrackerService` and `BANALService` unit tests (`TrackerServiceResumeTest`, `TrackerServiceResilienceTest`, `TrackerServiceAsyncInitTest`, `TrackerServiceFinalizationTest`, `TrackerServiceFGSTypeTest`, `TrackerServiceLapTest`, `TrackerServiceWorkoutStartTest`, `FreshInstallLocationInitTest`).
2. Parameterless `sampleAndWriteToDb()` method signature in `TrackerService.java` remains intact for reflection-based unit tests.
3. Zero changes to user-facing UI strings (`strings.xml`).

---

## 2. Test Specification (`TST-TRK-019`)

### Test Case 1: `testTrackerLoop_whenOneTickThrowsUncheckedException_nextTickStillRunsAndSamples` (`TST-TRK-019.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResilienceTest.kt`
* **Preconditions**: `TrackerService` initialized with a mock `BANALServiceComm` and `TrainingApplication`; tick 1 configured to throw `RuntimeException("Transient sensor/binder glitch")` while tick 2 and tick 3 return valid telemetry (`TIME_TOTAL = 2, 3`, `HR = 150`).
* **Action**: Execute `tracker` across 3 ticks on a real `ScheduledExecutorService.scheduleAtFixedRate` (as well as synchronous invocation).
* **Expected Result**: Tick 1 catches and logs the exception via `Log.e`, increments `samplingErrorCount` to `1`, does not cancel the scheduled task, and ticks 2 and 3 execute normally, advancing `timeTotal_s` and updating the notification.

### Test Case 2: `testTrackerLoop_whenBanalServiceDisconnectedMidTick_localSnapshotPreventsNpe` (`TST-TRK-019.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResilienceTest.kt`
* **Preconditions**: `TrackerService` has `mBanalService` bound; during `getBestSensorData(SensorType.TIME_TOTAL)` inside `sampleAndWriteToDb()`, a side effect invokes `mBanalConnection.onServiceDisconnected(...)` which nulls `mBanalService`.
* **Action**: Invoke `service.tracker.run()` followed by a second `service.tracker.run()`.
* **Expected Result**: The first tick completes without `NullPointerException` (`samplingErrorCount == 0`) because `banalService` is snapshotted locally, `mBanalService` is declared `volatile`, and the second tick cleanly no-ops on `banalService == null`.

### Test Case 3: `testClockDeviceTimer_whenOneTickThrowsUncheckedException_nextTickContinuesAdvancingTimeSensors` (`TST-TRK-019.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/ClockDeviceResilienceTest.kt`
* **Preconditions**: `ClockDevice` initialized with mock `Context` and `MySensorManager`; `context.sendBroadcast(...)` (or sensor listener) throws `RuntimeException("Transient broadcast failure")` on tick 1 and succeeds on subsequent ticks.
* **Action**: Execute `ClockTimeTask` across multiple ticks (both directly via `runClockTickForTesting()` and via `java.util.Timer.scheduleAtFixedRate`).
* **Expected Result**: Tick 1 catches the exception, logs via `Log.e`, and increments `clockTimerErrorCount` to `1`; the `Timer` thread remains alive and subsequent ticks continue advancing `TIME_TOTAL`, `TIME_ACTIVE`, and `TIME_LAP` sensors (`2`, `3`, ...).

### Test Case 4: 9-Language Localization & Specifier Audit (`TST-TRK-019.4`)
* **Scope**: Localization Parity Audit
* **Goal**: Verify zero string modifications or regressions across all 9 locales (`EN`, `DE`, `ES`, `FR`, `IT`, `JA`, `NL`, `PL`, `PT`).
* **Expected Result**: 100% parity maintained.

### Test Case 5: Clean-Room Regression Suite (`TST-TRK-019.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-019.1` | Unit | `TrackerService.tracker.run()` | `REQ-TRK-027.1` | Specified |
| `TST-TRK-019.2` | Unit | `TrackerService.sampleAndWriteToDb(BANALServiceComm)` & `mBanalService` volatile snapshot | `REQ-TRK-027.2` | Specified |
| `TST-TRK-019.3` | Unit | `ClockDevice.ClockTimeTask.run()` | `REQ-TRK-027.3` | Specified |
| `TST-TRK-019.4` | Localization | `N/A (No UI strings modified)` | `REQ-TRK-027` | Specified |
| `TST-TRK-019.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001`, `REQ-TRK-027` | Specified |
