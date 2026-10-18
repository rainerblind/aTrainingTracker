# Stage 2: Requirement & Test Specification - ATT-3986: [High] Workout finalization can leave FINISHED unset and skip map and streams

**Ticket**: [ATT-3986](https://rainerblind.atlassian.net/browse/ATT-3986)  
**Sub-task**: [ATT-4070](https://rainerblind.atlassian.net/browse/ATT-4070) (`[Req & Test Spec]`)  
**Parent Epic**: None (*Standalone Bug*)  
**Target Release**: `None` (Assigned upon Sprint Review completion per Rule 14)  
**Active Sprint**: `2026-42.9`  
**Requirement Mapping**: `REQ-TRK-030` (*Fault-Isolated Workout Finalization, Dedicated `FINISHED=1` Commit, Defensive `AccumulatedSensorTypeSet` Copy, and Zero-Duration Average Speed Guard*)  
**Test Spec ID**: `TST-TRK-022`  
**Branch**: `feature/ATT-3986`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Requirement Specification (`REQ-TRK-030`)

### 1.1 Problem Statement & Rationale
In `TrackerService.endWorkout()`, persisting `WorkoutSummaries.FINISHED = 1` currently shares a single SQLite transaction with `saveAccumulatedSensorTypes()`, `getAccumulatedGCDataString()`, and `getSportTypeId()`. If any of those auxiliary calls throws an exception—such as a `ConcurrentModificationException` because `MySensorManager.getAccumulatedSensorTypeSet()` returns its live internal `HashSet` while a sensor registers concurrently—the entire transaction rolls back and leaves `FINISHED = 0`, causing the stopped workout to be repeatedly offered for resume. Furthermore, in `TrackerService.finalizeLiveSession()`, `mBanalService` is read without a single local snapshot, `getAverageSpeed()` divides by zero (`NaN` / `Infinity`) when `mTimeActive_s == 0`, and map polyline and stream persistence sits at the very end of an unguarded method body, so any exception in extrema, physiology, commute/trainer heuristics, clustering, or auto-naming skips persisting the workout's map polyline and streams.

### 1.2 Functional & Architectural Requirements
`TrackerService` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`) and `MySensorManager` (`app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensorManager.java`) SHALL guarantee fault-isolated workout finalization (ATT-3986, complementing `REQ-TRK-015`, `REQ-TRK-016`, and `REQ-TRK-027`):

1. **Dedicated, Minimal `FINISHED = 1` Transaction First in `endWorkout()` (`REQ-TRK-030.1`)**:
   * In `TrackerService.endWorkout()`, `WorkoutSummaries.FINISHED = 1` SHALL be persisted first in its own dedicated, minimal SQLite transaction on `WorkoutSummaries.TABLE` (`WHERE C_ID = mWorkoutID`) before attempting auxiliary sensor-type or metadata updates.
   * Subsequent auxiliary operations in `endWorkout()`—(a) persisting accumulated sensor types via `summariesDatabaseManager.saveAccumulatedSensorTypes(mWorkoutID, banalService.getAccumulatedSensorTypeSet())` and (b) updating `WorkoutSummaries.GC_DATA`, `WorkoutSummaries.SPORT_ID`, and `WorkoutSummaries.B_SPORT`—SHALL execute in independent `try / catch (Throwable t)` blocks so that a failure in any auxiliary operation never rolls back or prevents `FINISHED = 1`.
2. **Defensive Copy & Monitor Synchronization for `MySensorManager.getAccumulatedSensorTypeSet()` (`REQ-TRK-030.2`)**:
   * `MySensorManager.getAccumulatedSensorTypeSet()` SHALL return a defensive snapshot copy (`new HashSet<>(mAccumulatedSensorTypeSet)`) while holding the `MySensorManager` monitor (`synchronized`).
   * `MySensorManager.notifyNewSensor(MySensor mySensor)` SHALL be `synchronized` so that all mutations and reads of `mAccumulatedSensorTypeSet` and `mAccumulatedGCData` are serialized under the `MySensorManager` monitor, eliminating `ConcurrentModificationException` when callers iterate over the returned `Set` during concurrent sensor registration.
3. **Zero-Duration & Non-Finite Average Speed Guard (`REQ-TRK-030.3`)**:
   * `TrackerService.averageSpeedCalculateable()` SHALL return `true` if and only if `mTimeActive_s > 0 && Double.isFinite(mDistanceTotal_m) && mDistanceTotal_m >= 0.0`.
   * `TrackerService.getAverageSpeed()` SHALL guard against `mTimeActive_s <= 0` and non-finite operands: if `!averageSpeedCalculateable()`, it SHALL return `0.0`; otherwise it SHALL compute `double speed = mDistanceTotal_m / mTimeActive_s;` and return `Double.isFinite(speed) ? speed : 0.0`, guaranteeing `getAverageSpeed()` never returns `Double.NaN` or `Double.POSITIVE_INFINITY`/`NEGATIVE_INFINITY`.
4. **Local `BANALServiceComm` Snapshot & Reordered, Independently Guarded Steps in `finalizeLiveSession()` (`REQ-TRK-030.4`)**:
   * `TrackerService.finalizeLiveSession()` SHALL capture `final BANALServiceComm banalService = mBanalService;` once at method entry and use that local reference with null checks across all sub-steps.
   * `TrackerService.finalizeLiveSession()` SHALL be partitioned into independently `try / catch (Throwable t)`-guarded steps executed in the following priority order:
     - **Step 1 (Map Polyline & Elevation/Distance Streams First)**: Simplify and encode track coordinates (`PolyUtil.simplify`, `PolyUtil.encode`), encode altitude and distance streams (`NumericalEncodingUtils.INSTANCE.encodeDoubles`), and persist to `summariesManager.updateMapAndStreams(...)`, `repository.setMapPolyline(...)`, and `repository.setElevationStreams(...)`.
     - **Step 2 (Start/End Coordinates, Sensor Extrema & Max Line Distance)**: Persist `START` and `END` latitude/longitude extrema, sensor running stats (`MIN`, `MAX`, `AVG`, `W_PRIME_BAL END`) with finite-value guards (`Double.isFinite`), and `LINE_DISTANCE_m MAX` apex coordinates.
     - **Step 3 (Aerobic Efficiency / Decoupling & Power Training Metrics)**: Compute and persist `EFFICIENCY_FACTOR`, `AEROBIC_DECOUPLING`, `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`, and `POWER_TO_WEIGHT` using the local `banalService` snapshot.
     - **Step 4 (Commute & Trainer Heuristics)**: Execute `guessCommuteAndTrainer()` inside its own `try / catch (Throwable t)` guard.
     - **Step 5 (Identity Arbitration, Route Clustering & Auto-Naming)**: Resolve equipment/sport identity, evaluate `WorkoutClusterEngine.suggestCluster(...)` / `assignClusterToWorkout(...)`, and fall back to `WorkoutAutoNamingHelper.generateWorkoutName(...)` inside its own `try / catch (Throwable t)` guard.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (0 s Active Time Stop Without `NaN`/`Infinity`)**:
  * *Given* an active workout session with `mTimeActive_s == 0` (and `mDistanceTotal_m == 0.0` or `> 0.0`),
  * *When* the user stops the workout (`endWorkout()` / `finalizeLiveSession()`),
  * *Then* `getAverageSpeed()` returns `0.0` (never `NaN` or `Infinity`), `WorkoutSummaries.FINISHED` is persisted as `1`, and no `NaN` or `Infinity` speed values are written to `WorkoutSummaries` or `WorkoutExtrema` or passed to `EquipmentAndSportTypeDiscoveryManager`.
* **Criterion 2 (Fault Isolation Across `endWorkout()` and `finalizeLiveSession()`)**:
  * *Given* a workout being finalized where `mBanalService == null` or where an auxiliary step throws an exception (e.g., `saveAccumulatedSensorTypes()`, `getSportTypeId()`, `guessCommuteAndTrainer()`, `EquipmentAndSportTypeDiscoveryManager`, or `WorkoutClusterEngine`),
  * *When* `endWorkout()` executes,
  * *Then* `WorkoutSummaries.FINISHED = 1`, the encoded map polyline, and the altitude/distance streams are still persisted in SQLite and `WorkoutRepository`, and `TRACKING_FINISHED_INTENT` is still broadcast.
* **Criterion 3 (Thread-Safe Defensive Copy in `MySensorManager.getAccumulatedSensorTypeSet()`)**:
  * *Given* a `MySensorManager` instance with active sensors,
  * *When* one thread repeatedly calls and iterates over `getAccumulatedSensorTypeSet()` (or mutates the returned set) while a concurrent thread registers new sensors via `registerSensor()`,
  * *Then* zero `ConcurrentModificationException`s are thrown and external modifications to the returned set do not mutate `MySensorManager`'s internal `mAccumulatedSensorTypeSet`.

### 1.4 System Invariants
* `REQ-TRK-015`, `REQ-TRK-016`, `REQ-TRK-026`, `REQ-TRK-027`, `REQ-TRK-028`, and `REQ-TRK-029` MUST remain 100% intact.
* Terminal `TRACKING_FINISHED_INTENT` broadcasts (system and `LocalBroadcastManager`) MUST always fire in `endWorkout()`'s `finally` block (Rule 29).
* Zero UI string resources are added or modified (9-language localization parity remains 100% intact).

---

## 2. Test Specification (`TST-TRK-022`)

### Test Case 1: `testEndWorkout_zeroActiveTime_marksFinishedWithoutNaNOrInfinity` (`TST-TRK-022.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceFinalizationTest.kt`
* **Preconditions**: `TrackerService` configured with `mWorkoutID = 42L`, `mTimeActive_s = 0`, `mDistanceTotal_m = 0.0` (and tested with `mDistanceTotal_m = 25.0`), active `LiveWorkoutSession` containing `SPEED_mps` stats and GPS track points.
* **Action**: Invoke `service.getAverageSpeed()`, `service.averageSpeedCalculateable()`, and `service.endWorkout()`.
* **Expected Result**:
  1. `service.averageSpeedCalculateable()` returns `false` and `service.getAverageSpeed()` returns `0.0` (finite, neither `NaN` nor `Infinity`).
  2. `WorkoutSummaries.FINISHED = 1` is persisted for `workoutId = 42L`.
  3. All speed values passed to `WorkoutSummariesDatabaseManager`, `WorkoutRepository`, and `EquipmentAndSportTypeDiscoveryManager` are finite (`!Double.isNaN(v) && Double.isFinite(v)`).

### Test Case 2: `testEndWorkout_whenAuxiliaryStepsThrowOrBanalNull_stillPersistsFinishedPolylineAndStreams` (`TST-TRK-022.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceFinalizationTest.kt`
* **Preconditions**: `TrackerService` configured with `mWorkoutID = 77L`, recorded GPS points and altitude/distance samples in `mLiveSession`, tested under:
  - Scenario A: `saveAccumulatedSensorTypes(...)` and `getSportTypeId()` / `EquipmentAndSportTypeDiscoveryManager` / `WorkoutClusterEngine` throw `RuntimeException`.
  - Scenario B: `mBanalService == null` (or concurrently nulled during `finalizeLiveSession()`).
* **Action**: Invoke `service.endWorkout()`.
* **Expected Result**:
  1. `WorkoutSummaries.FINISHED = 1` is committed in its own transaction before auxiliary failures occur.
  2. `summariesManager.updateMapAndStreams(77L, nonEmptyPolyline, nonEmptyAltStream, nonEmptyDistStream)`, `repository.setMapPolyline(77L, ...)`, and `repository.setElevationStreams(77L, ...)` are all invoked with the encoded track and streams despite later finalization steps throwing.
  3. `TRACKING_FINISHED_INTENT` is broadcast.

### Test Case 3: `testMySensorManager_getAccumulatedSensorTypeSet_returnsDefensiveCopyImmuneToCME` (`TST-TRK-022.3`)
* **Scope**: Concurrency & Defensive Copy Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceFinalizationTest.kt`
* **Preconditions**: `MySensorManager` initialized with mock context and test `MyDevice` sensors across `SensorType.values()`.
* **Action**:
  1. Register a sensor, obtain `val snapshot = sensorManager.accumulatedSensorTypeSet`, mutate `snapshot.clear()`, and verify `sensorManager.accumulatedSensorTypeSet` still contains the registered sensor.
  2. Run concurrent writer and reader threads where writer registers sensors across `SensorType.values()` while reader iterates over `sensorManager.accumulatedSensorTypeSet` with `Thread.yield()`.
* **Expected Result**: Zero `ConcurrentModificationException`s thrown; defensive copy isolation verified.

### Test Case 4: 9-Language Localization & Specifier Audit (`TST-TRK-022.4`)
* **Scope**: Localization Parity Audit
* **Goal**: Verify zero string resource regressions across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
* **Expected Result**: 100% parity.

### Test Case 5: Clean-Room Regression Suite (`TST-TRK-022.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-022.1` | Unit | `TrackerService.getAverageSpeed`, `TrackerService.endWorkout` | `REQ-TRK-030.1`, `REQ-TRK-030.3` | Specified |
| `TST-TRK-022.2` | Unit | `TrackerService.endWorkout`, `TrackerService.finalizeLiveSession` | `REQ-TRK-030.1`, `REQ-TRK-030.4` | Specified |
| `TST-TRK-022.3` | Concurrency Unit | `MySensorManager.getAccumulatedSensorTypeSet`, `MySensorManager.notifyNewSensor` | `REQ-TRK-030.2` | Specified |
| `TST-TRK-022.4` | Localization | `TranslationParityTest` | `REQ-UI-106` | Specified |
| `TST-TRK-022.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
