# Stage 1 Analysis: ATT-4099 - Keep BANALService bound or snapshot BANAL state until async endWorkout() finalization completes in TrackerService

**Ticket**: [ATT-4099](https://rainerblind.atlassian.net/browse/ATT-4099)  
**Sub-task**: [ATT-4126](https://rainerblind.atlassian.net/browse/ATT-4126) (`[Analysis]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Stability & Resource Leaks*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-TRK-035` (*Deterministic BANAL State Snapshotting and Bound Lifecycle Preservation During Asynchronous Workout Finalization*)  
**Branch**: `feature/ATT-4099`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Statement & Motivation

During the Sprint `2026-42.9` code review of `ATT-4006` (commit `814919cd`, Finding 1), a critical race condition between `TrackerService` workout finalization and `BANALService` lifecycle was identified:

1. **Premature `BANALService` Destruction on Main Thread**:
   In `TrackerService.onDestroy()`, `safeUnbindBanalService()` (`unbindService(mBanalConnection)`) is invoked synchronously on the Android main thread, whereas `endWorkout()` executes asynchronously on the single-threaded `mDbExecutor`.
   When `TrackerService` is the sole bound client of `BANALService`—which is the standard real-world scenario when workouts are stopped via the Android notification action while running in the background with the screen turned off or the UI closed—Android's Service Manager immediately initiates `BANALService.onDestroy()` on the main thread.

2. **Cascade Teardown of Sensor Subsystems**:
   `BANALService.onDestroy()` systematically shuts down its core subsystems:
   - `DeviceManager.shutDown()` unregisters Bluetooth LE and ANT+ device channels,
   - `MySensorManager.shutDown()` calls `unregisterSensors()`, nullifying and resetting active sensor accumulators,
   - `FilterManager.shutDown()` cleans up filtering pipelines.

3. **Silent Data Corruption and Loss in `endWorkout()`**:
   Concurrently executing on `mDbExecutor`, `endWorkout()` reads telemetry and state through `mBanalService`. Because the service has been unbound or partially/fully torn down:
   - `createNewLap()` queries `LAP_NR`, `TIME_LAP`, and `DISTANCE_m_LAP`. When `BANALService` has been reset, these return 0 or null. `TrackerService.java:1485` explicitly discards zero-duration and zero-distance laps (`if (lapTime_s <= 0 && lapDistance <= 0.0) return;`), completely losing the final workout lap!
   - `getAccumulatedSensorTypeSet()` returns an empty `EnumSet`, omitting recorded sensor capabilities from `WorkoutSummariesDatabaseManager.saveAccumulatedSensorTypes()`.
   - `getAccumulatedGCDataString()` returns an empty string or null, corrupting GoldenCheetah metadata strings (`WorkoutSummaries.GC_DATA`).
   - `getSportTypeId()`, `getDatabaseIdsOfActiveRemoteDevices()`, `getBSportType()`, and `getUserSelectedBSportType()` return default `UNKNOWN` sport type and empty device sets. As a consequence, hardware identity determination, automated workout naming (`ATT-1398`), and favorite route clustering (`WorkoutClusterEngine`, `ATT-773`) fail or misclassify the completed workout.

4. **In-Flight 1 Hz Tick Race**:
   When `mScheduler.shutdownNow()` is invoked at stop time, any 1 Hz tick already in-flight in `tracker.run()` can proceed through `sampleAndWriteToDb()`. Without checking an explicit volatile stopping barrier, this in-flight tick can enqueue a sample write onto `mDbExecutor` or attempt to update UI notifications and auto-pause state after finalization has started.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Teardown Concurrency in `TrackerService.java`

Inspecting `TrackerService.java`:
```java
// In onDestroy() fallback (lines 1054-1065):
safeUnbindBanalService();   // <--- Unbinds BANALService immediately on main thread!
performStopForeground();

final boolean interrupted = mTrackingInterrupted;
final CompletableFuture<Void> finalizationFuture = new CompletableFuture<>();
mFinalizationFuture = finalizationFuture;

Runnable finalizationTask = () -> {
    try {
        if (!interrupted) {
            endWorkout();  // <--- Runs asynchronously on mDbExecutor, reading mBanalService!
        }
```
If `onDestroy()` is triggered without `ACTION_STOP` (e.g. system termination, crash recovery, or direct `stopService`), `safeUnbindBanalService()` runs *before* `finalizationTask` is submitted to `mDbExecutor`.

### 2.2 Cross-Thread Volatility and Race in `handleActionStop()`

While `ATT-4098` moved `safeUnbindBanalService()` inside `handleActionStop()` to the `finally` block of `finalizationTask`, `endWorkout()` still directly dereferences `final BANALServiceComm banalService = mBanalService;` from the worker thread `mDbExecutor`.
Because `BANALServiceComm` methods (`getBestSensorData`, `getAccumulatedSensorTypeSet`, `getDatabaseIdsOfActiveRemoteDevices`) touch in-memory manager state that is concurrently susceptible to transient disconnection, receiver unregistration, or pause resets, relying on live cross-thread service calls during database writes is inherently brittle.

### 2.3 `createNewLap()` Zero-Lap Discard Logic

In `TrackerService.java:1454-1488`:
```java
protected void createNewLap() {
    final BANALServiceComm banalService = mBanalService;
    if (banalService == null) {
        mCreateNewLapWhenConnectedToBanalService = true;
    } else {
        ...
        int lapTime_s = 0;
        sensorData = banalService.getBestSensorData(SensorType.TIME_LAP);
        if (sensorData != null && sensorData.getValue() != null) {
            lapTime_s = (Integer) sensorData.getValue();
        }
        ...
        if (lapTime_s <= 0 && lapDistance <= 0.0) {
            return; // <--- The entire final lap is lost!
        }
```
If `banalService` has been disconnected or reset by the time `mDbExecutor` reaches `createNewLap()`, `lapTime_s` and `lapDistance` evaluate to `0`, discarding the final lap from `LapsTable`.

---

## 3. Chesterton's Fence Requirement Archaeology

In accordance with `REQ-PRO-022`, forensic analysis was conducted on previous requirements:

1. **Original Requirement ID & Target**:
   - `REQ-TRK-015` (`ATT-2641`): Introduced single-threaded FIFO `mDbExecutor` for database operations.
   - `REQ-TRK-030` (`ATT-3986`): Fault-isolated `endWorkout()` stages and immediate `FINISHED = 1` transaction.
   - `REQ-TRK-033` (`ATT-4006`): Moved `endWorkout()` off the Android main thread onto `mDbExecutor`.
   - `REQ-TRK-034` (`ATT-4098`): Introduced `ACTION_STOP` foreground lifecycle protection and deferred unbinding.
   - Targets: `TrackerService.java`, `BanalFinalizationSnapshot.java` (or static inner class), `TrackerServiceFinalizationTest.kt`.

2. **Historical Origin & Commit Trace**:
   - `ATT-4006` (`814919cd`, Oct 17, 2026): Moved `endWorkout()` to `mDbExecutor`.
   - Code Review Finding 1 flagged that `unbindService(mBanalConnection)` in `onDestroy()` unbinds BANAL prematurely before `mDbExecutor` completes.
   - Ticket `ATT-4099` was logged to resolve this finding.

3. **Root Reason for Existing Formulation**:
   - `unbindService(mBanalConnection)` was originally in `onDestroy()` because in standard Android lifecycle templates, service connections are unbound when the host service destroys.
   - However, moving `endWorkout()` to a background worker thread (`mDbExecutor`) decoupled the lifecycle: the database work outlives the service's active sampling window.

4. **Preservation of Core Invariants**:
   - Non-blocking main thread (< 5 ms) MUST be preserved.
   - Strict FIFO execution on `mDbExecutor` MUST be preserved.
   - Dedicated `FINISHED = 1` transaction (Rule 29) MUST be preserved.
   - `BANALService` MUST NOT leak: `unbindService(mBanalConnection)` MUST always be invoked exactly once on the main thread after `finalizationTask` completes.

---

## 4. Proposed Architectural Solution

We implement a dual-protection strategy combining an **Immutable Main-Thread BANAL Snapshot** with **Universal Deferred BANAL Unbinding**:

```
[Main Thread: stopTracking / ACTION_STOP / onDestroy]
   │
   ├─► 1. Set volatile mIsStopping = true
   ├─► 2. Cancel mTrackerHandle & shutdownNow(mScheduler)
   ├─► 3. Capture BanalFinalizationSnapshot(mBanalService) [SYNCHRONOUS on Main Thread]
   │       • lapNr, lapTime_s, lapDistance_m
   │       • activeRemoteDeviceIds
   │       • accumulatedSensorTypes
   │       • accumulatedGCDataString
   │       • bSportType, userSelectedSportType, sportTypeId
   │
   ├─► 4. Enqueue finalizationTask(snapshot) onto mDbExecutor (DO NOT unbind BANAL yet!)
   │
   ▼
[Worker Thread: mDbExecutor]
   ├─► 5. Execute endWorkout(snapshot):
   │       • createNewLap(snapshot) -> saves final lap reliably from snapshot
   │       • saveActiveDevices(snapshot.activeRemoteDeviceIds)
   │       • summariesDb.update(FINISHED = 1) [Dedicated Transaction]
   │       • saveAccumulatedSensorTypes(snapshot.accumulatedSensorTypes)
   │       • updateSummaryMetadata(snapshot.gcData, snapshot.sportTypeId, snapshot.bSportType)
   │       • finalizeLiveSession(snapshot) -> hardware identity & auto-naming
   │       • exportManager.workoutFinished()
   │
   ▼
[Completion Barrier: finally on mDbExecutor -> runOnMainThread]
   ├─► 6. Dispatch to Main Thread:
   │       • safeUnbindBanalService()  [Guaranteed exactly once on Main Thread]
   │       • performStopForeground()
   │       • performStopSelf()
   │       • complete(finalizationFuture)
```

### 4.1 Key Implementation Elements

1. **`BanalFinalizationSnapshot` Data Class**:
   Create a clean, immutable snapshot capturing all BANAL telemetry required by `endWorkout()`:
   - `int lapNr`, `int lapTime_s`, `double lapDistance_m`
   - `Set<Long> activeRemoteDeviceIds`
   - `Set<SensorType> accumulatedSensorTypes`
   - `String accumulatedGCDataString`
   - `BSportType bSportType`
   - `BSportType userSelectedSportType`
   - `long sportTypeId`
   If `mBanalService` is null (e.g. unmocked unit tests or standalone service), provide safe fallback defaults.

2. **In-Flight 1 Hz Tick Guard**:
   In `TrackerService.java`, add `if (mIsStopping) return;` at the beginning of `tracker.run()` and `sampleAndWriteToDb()`. This prevents any tick that started before `shutdownNow()` from enqueueing sample writes or posting notifications after finalization has begun.

3. **Refactored `endWorkout(@Nullable BanalFinalizationSnapshot snapshot)`**:
   Pass the snapshot into `endWorkout()`, `createNewLap(snapshot)`, and `finalizeLiveSession(snapshot)`. All reads of lap metrics, sensor sets, device IDs, and sport types read directly from the snapshot, eliminating thread-safety issues and dependency on live service binding during database writes.

4. **Uniform Teardown in `onDestroy()` Fallback**:
   In `TrackerService.onDestroy()`, remove the early call to `safeUnbindBanalService()`. In both `handleActionStop()` and `onDestroy()`, `safeUnbindBanalService()` is deferred to the completion runnable dispatched to the main thread in `finally`.

---

## 5. Scope Bounding & Invariants

- **In Scope**:
  - `TrackerService.java`: `BanalFinalizationSnapshot` capture on main thread, `mIsStopping` in-flight tick checks, updating `endWorkout`, `createNewLap`, `finalizeLiveSession` to consume snapshot, and deferring `safeUnbindBanalService()` in `onDestroy()`.
  - `TrackerServiceFinalizationTest.kt`: Unit tests verifying snapshot capture, preservation of final lap, sensor accumulations, and sport type when BANAL is unbound or destroyed immediately after stop initiation.
  - Living docs update (`docs/requirements.md` -> `REQ-TRK-035`, `docs/tests.md` -> `TST-TRK-027`).

- **Out of Scope**:
  - Modifying `BANALService` internal device discovery or BLE connection logic (`ATT-4007`).
  - Modifying `LiveWorkoutSession` rehydration or resume from crash (`ATT-4095`).
  - Altering `WorkoutClusterEngine` clustering heuristics or database schemas.

- **Invariants**:
  - Non-blocking UI: Main thread teardown latency remains `< 5 ms`.
  - Rule 29: `FINISHED = 1` transaction committed first.
  - Zero BANALService leaks: Service is always unbound exactly once on the main thread.
  - 100% clean-room unit test suite pass rate.

---

## 6. Risk Assessment & Mitigation

| Risk | Impact | Likelihood | Mitigation Strategy |
| :--- | :--- | :--- | :--- |
| `BanalFinalizationSnapshot` captures nulls if BANAL not yet connected | Low | Low | Snapshot factory defensively checks nulls and provides sensible defaults (e.g. `UNKNOWN` sport, empty sets, lap 0). |
| Race between `mScheduler.shutdownNow()` and running `tracker.run()` | Medium | Low | `if (mIsStopping) return;` at entry of `tracker.run()` and `sampleAndWriteToDb()`. |
| Double unbind if `onDestroy()` runs after `handleActionStop()` | High | Medium | `mBanalBound` tracking flag in `safeUnbindBanalService()` ensures idempotent unbinding. |
| Regression in existing finalization tests | Medium | Low | Run full clean-room suite (`./gradlew testDebugUnitTest`). |

---

## 7. Verification Strategy

1. **Unit Test Suite (`TrackerServiceFinalizationTest.kt`)**:
   - `testBanalFinalizationSnapshot_capturesAllTelemetryCorrectly`: Verify snapshot captures lap metrics, devices, sport type, and sensor accumulations.
   - `testEndWorkout_withSnapshot_persistsFinalLapAndAccumulatedSensorsEvenIfBanalServiceDestroyed`: Simulate BANAL unbinding/destruction before `mDbExecutor` runs; verify final lap, sensor types, and GC string are persisted.
   - `testInFlightTick_whenIsStoppingTrue_abortsWithoutSampleWrite`: Verify `tracker.run()` and `sampleAndWriteToDb()` abort when `mIsStopping == true`.
   - `testOnDestroy_defersUnbindBanalUntilAfterFinalization`: Verify `onDestroy()` defers BANAL unbind until after `endWorkout()` completes on `mDbExecutor`.
2. **Clean-Room Regression Suite**:
   - Execute `./gradlew testDebugUnitTest` verifying 100% pass rate.
