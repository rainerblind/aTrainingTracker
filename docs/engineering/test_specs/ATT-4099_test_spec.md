# Stage 2: Requirement & Test Specification - ATT-4099: Keep BANALService bound or snapshot BANAL state until async endWorkout() finalization completes in TrackerService

**Ticket**: [ATT-4099](https://rainerblind.atlassian.net/browse/ATT-4099)  
**Sub-task**: [ATT-4127](https://rainerblind.atlassian.net/browse/ATT-4127) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Stability & Resource Leaks*)  
**Target Release**: `None` (Assigned upon Sprint Review completion per Rule 14)  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-TRK-035` (*Deterministic BANAL State Snapshotting and Bound Lifecycle Preservation During Asynchronous Workout Finalization*)  
**Test Spec ID**: `TST-TRK-027`  
**Branch**: `feature/ATT-4099`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Requirement Specification (`REQ-TRK-035`)

### 1.1 Problem Statement & Rationale
When a workout is stopped by the user or an external control while running in the background, `TrackerService` may be the sole bound client of `BANALService`. If `BANALService` is unbound synchronously during teardown on the Android main thread while `endWorkout()` executes asynchronously on `mDbExecutor`, Android immediately triggers `BANALService.onDestroy()`, shutting down `DeviceManager`, `MySensorManager`, and `FilterManager`.
When `endWorkout()` subsequently runs on `mDbExecutor`:
1. `createNewLap()` reads 0 or null for `LAP_NR`, `TIME_LAP`, and `DISTANCE_m_LAP`. Because `lapTime_s <= 0 && lapDistance <= 0.0` evaluates to true, the final workout lap is discarded from `LapsTable`.
2. `getAccumulatedSensorTypeSet()` returns an empty set, causing `WorkoutSummariesDatabaseManager.saveAccumulatedSensorTypes()` to drop accumulated sensor flags.
3. `getAccumulatedGCDataString()` returns an empty string or null, corrupting GoldenCheetah metadata strings.
4. `getSportTypeId()`, `getDatabaseIdsOfActiveRemoteDevices()`, and `getBSportType()` return default `UNKNOWN` values and empty device sets, breaking hardware identity resolution, favorite route clustering (`WorkoutClusterEngine`), and automated naming (`ATT-1398`).
5. In-flight 1 Hz ticks executing during `shutdownNow(mScheduler)` may attempt to enqueue sample writes onto `mDbExecutor` after finalization has started.

### 1.2 Functional & Architectural Requirements
`TrackerService` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`) SHALL guarantee that all `BANALService` telemetry and state required for final workout database persistence are captured as an immutable snapshot on the Android main thread at workout stop time, that in-flight 1 Hz sampling ticks are immediately suppressed, and that `safeUnbindBanalService()` is universally deferred until after asynchronous `endWorkout()` finalization completes on `mDbExecutor` (`ATT-4099`, extending `REQ-TRK-015`, `REQ-TRK-030`, `REQ-TRK-033`, and `REQ-TRK-034`):

1. **Immutable Main-Thread BANAL Snapshot Capture (`REQ-TRK-035.1`)**:
   - `TrackerService` SHALL define an immutable `BanalFinalizationSnapshot` record (or static data class) encapsulating all BANAL telemetry required by `endWorkout()`:
     - `int lapNr`, `int lapTime_s`, `double lapDistance_m`
     - `Set<Long> activeRemoteDeviceIds`
     - `Set<SensorType> accumulatedSensorTypes`
     - `String accumulatedGCDataString`
     - `BSportType bSportType`
     - `BSportType userSelectedSportType`
     - `long sportTypeId`
   - When workout stopping begins on the Android main thread (inside `handleActionStop()` or `onDestroy()`), `TrackerService` SHALL capture `BanalFinalizationSnapshot` synchronously before enqueuing `finalizationTask` onto `mDbExecutor`.
   - If `mBanalService == null`, `BanalFinalizationSnapshot` SHALL provide safe fallback defaults (lap 0, 0s, 0.0m, empty device set, empty sensor set, null GC string, `BSportType.UNKNOWN`, and default sport type ID).

2. **In-Flight 1 Hz Tick Suppression (`REQ-TRK-035.2`)**:
   - In `TrackerService.java`, the 1 Hz `tracker` runnable and `sampleAndWriteToDb()` SHALL inspect `volatile boolean mIsStopping`.
   - If `mIsStopping == true`, `tracker.run()` and `sampleAndWriteToDb()` SHALL immediately abort execution without evaluating auto-pause, updating notifications, or submitting tasks to `mDbExecutor`.

3. **Snapshot-Driven Asynchronous `endWorkout()` Execution (`REQ-TRK-035.3`)**:
   - `TrackerService.endWorkout(@Nullable BanalFinalizationSnapshot snapshot)` SHALL consume the snapshot to persist:
     - Final lap in `createNewLap(snapshot)` using `snapshot.lapNr`, `snapshot.lapTime_s`, and `snapshot.lapDistance_m` without relying on live BANAL queries,
     - Active devices in `activeDevicesDbHelper.addActiveDevice(mWorkoutID, deviceDbId)` from `snapshot.activeRemoteDeviceIds`,
     - Accumulated sensor types in `saveAccumulatedSensorTypes(mWorkoutID, snapshot.accumulatedSensorTypes)`,
     - Auxiliary summary metadata (`GC_DATA` from `snapshot.accumulatedGCDataString`, `SPORT_ID` and `B_SPORT` from `snapshot.sportTypeId` and `snapshot.bSportType`),
     - Session identity and auto-naming in `finalizeLiveSession(snapshot)` using `snapshot.userSelectedSportType`, `snapshot.bSportType`, and `snapshot.activeRemoteDeviceIds`.

4. **Universal Deferred Service Unbinding (`REQ-TRK-035.4`)**:
   - In both `handleActionStop()` and `onDestroy()` fallback paths, `TrackerService` SHALL defer `safeUnbindBanalService()` until the terminal `finalizationTask` completes on `mDbExecutor`, dispatching unbinding to the main thread in the `finally` block of `finalizationTask`.
   - `safeUnbindBanalService()` SHALL be idempotent and protected by `mBanalBound`, ensuring `unbindService(mBanalConnection)` is invoked at most once per service lifecycle.

---

## 2. Test Specification (`TST-TRK-027`)

### 2.1 Test Cases

#### `TST-TRK-027.1`: Main-Thread `BanalFinalizationSnapshot` Capture Test
- **Target**: `TrackerServiceFinalizationTest.kt`
- **Methodology**:
  1. Instantiate a test `TrackerService` with a mocked `BANALServiceComm` returning known active devices (`listOf(101L, 102L)`), accumulated sensor types (`setOf(HR, CADENCE, SPEED_mps)`), GC data string (`"GC_DATA_TEST"`), `BSportType.BICYCLING`, and current lap metrics (`lapNr = 3`, `lapTime_s = 180`, `lapDistance_m = 1250.0`).
  2. Invoke snapshot capture helper on main thread.
  3. Assert that all snapshot fields match the values provided by `mBanalService`.
  4. Test fallback: pass `null` service reference; assert snapshot returns empty collections, 0s, and `BSportType.UNKNOWN` without throwing NPE.

#### `TST-TRK-027.2`: In-Flight 1 Hz Tick Suppression When Stopping Test
- **Target**: `TrackerServiceFinalizationTest.kt`
- **Methodology**:
  1. Instantiate `TrackerService` and set `mIsStopping = true`.
  2. Call `tracker.run()` and `sampleAndWriteToDb(banalService)`.
  3. Verify that `mDbExecutor` receives zero tasks, auto-pause manager is not queried, and notification is not updated.

#### `TST-TRK-027.3`: Final Lap & Sensor Retention When BANAL Unbound Prior to `endWorkout()` Test
- **Target**: `TrackerServiceFinalizationTest.kt`
- **Methodology**:
  1. Configure `TrackerService` with mock database managers.
  2. Capture `BanalFinalizationSnapshot` with `lapNr = 2`, `lapTime_s = 450`, `lapDistance_m = 3200.0`, `accumulatedSensorTypes = setOf(HR, POWER)`.
  3. Simulate BANAL unbinding/destruction by setting `mBanalService = null`.
  4. Invoke `endWorkout(snapshot)`.
  5. Verify that `LapsTable` contains an inserted row with `lapNr = 2`, `lapTime_s = 450`, `lapDistance = 3200.0`.
  6. Verify `WorkoutSummariesDatabaseManager.saveAccumulatedSensorTypes` receives `setOf(HR, POWER)`.
  7. Verify summary row has `SPORT_ID` and `B_SPORT` populated accurately.

#### `TST-TRK-027.4`: `onDestroy()` Deferred Service Unbinding Test
- **Target**: `TrackerServiceFinalizationTest.kt`
- **Methodology**:
  1. Set up `TrackerService` with `mIsStopping = false` and an active mock `mDbExecutor` running a delayed task.
  2. Call `onDestroy()`.
  3. Verify `unbindServiceCalled` is `false` immediately after `onDestroy()` returns.
  4. Allow `finalizationTask` to finish.
  5. Verify `unbindServiceCalled` becomes `true` on the main thread after `finalizationTask` completes.

#### `TST-TRK-027.5`: Clean-Room Full Suite Regression Execution
- **Target**: Clean-room environment
- **Methodology**:
  1. Execute `./gradlew testDebugUnitTest`.
  2. Assert 100% test pass rate across all 3,189+ tests with zero failures or regressions.

---

## 3. Requirement to Test Traceability Matrix

| Requirement Clause | Test Case | Target Test Class / File | Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-035.1` (Immutable Main-Thread Snapshot) | `TST-TRK-027.1` | `TrackerServiceFinalizationTest.kt` | Unit test of `BanalFinalizationSnapshot.capture()` and fallback |
| `REQ-TRK-035.2` (In-Flight Tick Suppression) | `TST-TRK-027.2` | `TrackerServiceFinalizationTest.kt` | Unit test verifying abort on `mIsStopping == true` |
| `REQ-TRK-035.3` (Snapshot-Driven `endWorkout()`) | `TST-TRK-027.3` | `TrackerServiceFinalizationTest.kt` | Unit test verifying final lap & sensors persist when `mBanalService == null` |
| `REQ-TRK-035.4` (Universal Deferred Unbinding) | `TST-TRK-027.4` | `TrackerServiceFinalizationTest.kt` | Unit test verifying `onDestroy()` defers `unbindService()` |
| `REQ-PRO-001` (Clean-Room Full Suite) | `TST-TRK-027.5` | `./gradlew testDebugUnitTest` | 100% clean-room unit test suite pass rate |
