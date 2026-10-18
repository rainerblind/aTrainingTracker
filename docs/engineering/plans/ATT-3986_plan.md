# Stage 3: Implementation Plan - ATT-3986: [High] Workout finalization can leave FINISHED unset and skip map and streams

**Ticket**: [ATT-3986](https://rainerblind.atlassian.net/browse/ATT-3986)  
**Sub-task**: [ATT-4071](https://rainerblind.atlassian.net/browse/ATT-4071) (`[Impl-Plan]`)  
**Parent Epic**: None (*Standalone Bug*)  
**Target Release**: `None` (Assigned upon Sprint Review completion per Rule 14)  
**Active Sprint**: `2026-42.9`  
**Requirement Mapping**: `REQ-TRK-030`  
**Test Mapping**: `TST-TRK-022`  
**Branch**: `feature/ATT-3986`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Background

When an athlete stops a workout (`TrackerService.endWorkout()`), four coupled vulnerabilities can leave `WorkoutSummaries.FINISHED` unset (`0`) or skip persisting the workout's map polyline and elevation/distance streams:
1. `WorkoutSummaries.FINISHED = 1` shares a single SQLite transaction with `saveAccumulatedSensorTypes()`, `getAccumulatedGCDataString()`, and `getSportTypeId()`. Any exception in those auxiliary calls aborts the transaction and rolls back `FINISHED = 1`.
2. `MySensorManager.getAccumulatedSensorTypeSet()` returns its live mutable `mAccumulatedSensorTypeSet` (`HashSet`) rather than a defensive copy, and `notifyNewSensor()` mutates `mAccumulatedSensorTypeSet` without synchronizing on `getAccumulatedSensorTypeSet()`'s monitor, risking `ConcurrentModificationException` when `saveAccumulatedSensorTypes()` iterates over the set during stop.
3. `TrackerService.getAverageSpeed()` divides `mDistanceTotal_m / mTimeActive_s` without guarding `mTimeActive_s <= 0`, producing `Double.NaN` or `Double.POSITIVE_INFINITY` at `0 s` active time when `finalizeLiveSession()` invokes `getAverageSpeed()` unconditionally.
4. `TrackerService.finalizeLiveSession()` reads `mBanalService` directly without a single local snapshot and executes all post-workout steps in a single unguarded sequence with map polyline and stream persistence at the very end, so any exception in extrema, physiology, commute/trainer heuristics, clustering, or auto-naming aborts before map/stream persistence.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TRK-030` (*Fault-Isolated Workout Finalization, Dedicated `FINISHED=1` Commit, Defensive `AccumulatedSensorTypeSet` Copy, and Zero-Duration Average Speed Guard*)
* **Test Mapping**: `TST-TRK-022` (*Fault-Isolated Workout Finalization, Dedicated `FINISHED=1` Commit, Defensive `AccumulatedSensorTypeSet` Copy & Zero-Duration Average Speed Verification Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites continue to pass cleanly.
2. **Thread Safety & Monitor Affinity**: `MySensorManager` serializes all mutations and defensive-copy reads of `mAccumulatedSensorTypeSet` under its `synchronized` instance monitor; `TrackerService` snapshots `volatile mBanalService` locally.
3. **Guaranteed `FINISHED = 1` & Completion Broadcasts (Rule 29)**: `FINISHED = 1` is committed first in its own dedicated minimal transaction, and `TRACKING_FINISHED_INTENT` is always dispatched in `endWorkout()`'s `finally` block.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `MySensorManager` (`app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensorManager.java`)
* **Layer**: Sensor Management (`banalservice.sensor`).
* **Changes**:
  1. In `public synchronized Set<SensorType> getAccumulatedSensorTypeSet()`, return `new HashSet<>(mAccumulatedSensorTypeSet)` instead of the live internal `mAccumulatedSensorTypeSet` reference, adding JavaDoc documenting thread-safety and defensive copying (`REQ-TRK-030.2`).
  2. Mark `private synchronized void notifyNewSensor(MySensor mySensor)` as `synchronized` so that any mutation to `mAccumulatedSensorTypeSet` and `mAccumulatedGCData` is strictly serialized with `getAccumulatedSensorTypeSet()`.

### Component 2: `TrackerService` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`)
* **Layer**: Foreground Tracking Service (`trainingtracker.tracker`).
* **Changes**:
  1. **Zero-Duration & Non-Finite Average Speed Guard (`REQ-TRK-030.3`)**:
     * Update `averageSpeedCalculateable()` to:
       ```java
       protected boolean averageSpeedCalculateable() {
           return mTimeActive_s > 0 && Double.isFinite(mDistanceTotal_m) && mDistanceTotal_m >= 0.0;
       }
       ```
     * Update `getAverageSpeed()` to:
       ```java
       protected double getAverageSpeed() {
           if (!averageSpeedCalculateable()) {
               return 0.0;
           }
           double speed = mDistanceTotal_m / mTimeActive_s;
           return Double.isFinite(speed) ? speed : 0.0;
       }
       ```
  2. **Dedicated Minimal `FINISHED = 1` Transaction & Isolated Auxiliary Updates in `endWorkout()` (`REQ-TRK-030.1`)**:
     * **Step A**: Immediately persist `WorkoutSummaries.FINISHED = 1` in its own dedicated, minimal SQLite transaction on `summariesDb` (`summariesDb.update(WorkoutSummaries.TABLE, finishedValues, WorkoutSummaries.C_ID + "=?", new String[]{String.valueOf(mWorkoutID)})`).
     * **Step B**: In an independent `try / catch (Throwable t)` block, if `banalService != null`, call `summariesDatabaseManager.saveAccumulatedSensorTypes(mWorkoutID, banalService.getAccumulatedSensorTypeSet())`.
     * **Step C**: In an independent `try / catch (Throwable t)` block, build `ContentValues` for `GC_DATA` (guarded in its own inner `try/catch` if `banalService != null`) and `SPORT_ID` / `B_SPORT` (guarded in its own inner `try/catch`), and update `WorkoutSummaries.TABLE` in its own transaction when `summaryValues.size() > 0`.
  3. **Local `BANALServiceComm` Snapshot & Reordered, Independently Guarded Steps in `finalizeLiveSession()` (`REQ-TRK-030.4`)**:
     * Make `finalizeLiveSession()` package-private `@VisibleForTesting void finalizeLiveSession()` so unit tests can also invoke or inspect it directly in addition to `endWorkout()`.
     * Capture `final BANALServiceComm banalService = mBanalService;` once at the start of `finalizeLiveSession()`.
     * **Step 1 (Map Polyline & Streams First — `try / catch (Throwable t)`)**: Simplify and encode track coordinates (`PolyUtil.simplify`, `PolyUtil.encode`) and altitude/distance streams (`NumericalEncodingUtils.INSTANCE.encodeDoubles`), and persist via `summariesManager.updateMapAndStreams(mWorkoutID, polyline, altStream, distStream)`, `repository.setMapPolyline(mWorkoutID, polyline)`, and `repository.setElevationStreams(mWorkoutID, altStream, distStream)`.
     * **Step 2 (Start/End Locations, Sensor Extrema & Max Line Distance — `try / catch (Throwable t)`)**: Persist `START` and `END` positions, iterate over `mLiveSession.getSensorStats().entrySet()` (skipping any non-finite `Double` values with `Double.isFinite(...)` so `NaN`/`Infinity` can never be written to `WorkoutExtrema`), and compute/persist `LINE_DISTANCE_m MAX`.
     * **Step 3 (Aerobic Efficiency / Decoupling & Power Training Metrics — `try / catch (Throwable t)`)**: Compute and persist `EFFICIENCY_FACTOR`, `AEROBIC_DECOUPLING`, `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`, and `POWER_TO_WEIGHT` using the local `banalService` snapshot (`(banalService != null && banalService.getBSportType() != null) ? banalService.getBSportType() : BSportType.UNKNOWN`).
     * **Step 4 (Commute & Trainer Heuristics — `try / catch (Throwable t)`)**: Invoke `guessCommuteAndTrainer()` inside its own `try / catch (Throwable t)` guard.
     * **Step 5 (Identity Arbitration, Route Clustering & Auto-Naming — `try / catch (Throwable t)`)**: Resolve `InferredIdentity` using `banalService` and safe `getAverageSpeed()`, evaluate `WorkoutClusterEngine.suggestCluster(...)` / `assignClusterToWorkout(...)`, and fall back to `WorkoutAutoNamingHelper.generateWorkoutName(...)`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Harden `MySensorManager.getAccumulatedSensorTypeSet()` and `notifyNewSensor()`
* **Files**: `app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensorManager.java`
* **Changes**: Return `new HashSet<>(mAccumulatedSensorTypeSet)` from `synchronized getAccumulatedSensorTypeSet()` and make `notifyNewSensor(MySensor)` `synchronized`.

### Step 2: Harden `TrackerService.averageSpeedCalculateable()`, `getAverageSpeed()`, `endWorkout()`, and `finalizeLiveSession()`
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
* **Changes**:
  1. Guard `averageSpeedCalculateable()` and `getAverageSpeed()` against `mTimeActive_s <= 0` and non-finite values.
  2. Split `endWorkout()` so `FINISHED = 1` is committed first in its own minimal transaction before isolated `try/catch` blocks for `saveAccumulatedSensorTypes` and `GC_DATA`/`SPORT_ID`/`B_SPORT`.
  3. Snapshot `banalService` once in `finalizeLiveSession()` and split `finalizeLiveSession()` into 5 independently `try/catch (Throwable t)`-guarded steps with Map Polyline & Streams persisted first.

### Step 3: Implement Unit Tests in `TrackerServiceFinalizationTest.kt` (`TST-TRK-022`)
* **Files**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceFinalizationTest.kt`
* **Changes**: Add unit tests covering:
  1. Stopping a workout with `0 s` active time marks `FINISHED = 1` without `NaN` or `Infinity` values (`TST-TRK-022.1`).
  2. Stopping with `mBanalService == null` or when auxiliary steps (`saveAccumulatedSensorTypes`, `getSportTypeId`, `guessCommuteAndTrainer`, `EquipmentAndSportTypeDiscoveryManager`, `WorkoutClusterEngine`) throw still persists `FINISHED = 1`, polyline, and streams (`TST-TRK-022.2`).
  3. `MySensorManager.getAccumulatedSensorTypeSet()` returns a defensive copy immune to external mutation and concurrent `registerSensor()` `ConcurrentModificationException` (`TST-TRK-022.3`).
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceFinalizationTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests (`TrackerServiceFinalizationTest`) during Stage 4 construction, followed by the full clean-room regression suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-3986` allows clean revert without affecting `sprint/2026-42.9` or `develop`.
