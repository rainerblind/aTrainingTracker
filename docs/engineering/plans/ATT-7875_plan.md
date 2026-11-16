# Stage 3: Implementation Plan - ATT-7875: Preserve accumulators on TrackerService crash recovery

**Ticket**: [ATT-7875](https://rainerblind.atlassian.net/browse/ATT-7875) | **Sub-task**: [ATT-8249](https://rainerblind.atlassian.net/browse/ATT-8249) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*Crash-Free Resilience*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8` | **Branch**: `feature/ATT-7875`  
**Requirement Mapping**: `REQ-TRK-078` (`REQ-TRK-078.1`..`REQ-TRK-078.4`)  
**Test Mapping**: `TST-TRK-078` (`TST-TRK-078.1`..`TST-TRK-078.6`)  
**Risk Classification**: `COMPLEX` (TrackerService crash recovery runtime path & accumulator continuity)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-15  

---

## 1. Problem Description & Background
When Android restarts `START_STICKY` `TrackerService` mid-workout (`StartType.RESUME_SERVICE_RECREATION`), `TrackerService.mBanalConnection.onServiceConnected()` unconditionally broadcasts `BANALService.RESET_ACCUMULATORS_INTENT`, resetting all sensor accumulators to 0 after `recreateValuesWhenResuming()` has already run. Furthermore, `recreateValuesWhenResuming()` omits `ASCENT`, `DESCENT`, `TIME_LAP`, and `DISTANCE_m_LAP`. This wipes workout totals mid-ride, truncates exports (`.fit`/`.tcx`/`.gpx`), and causes accordion folds (backward diagonal lines) in Aftermath elevation/telemetry charts.

---

## 2. Traceability & Invariants
* **Requirements**: `REQ-TRK-078.1` (no reset intent on resume), `REQ-TRK-078.2` (rehydrate ascent/descent/laps), `REQ-TRK-078.3` (seed 9 sensor accumulators), `REQ-TRK-078.4` (late-binding sensor baseline inheritance).
* **Test Mapping**: `TST-TRK-078.1`..`TST-TRK-078.6`.
* **System Invariants**: Monotonic non-decreasing accumulator progression across recovery; `START_NORMAL` fresh workout start resets via `TrainingApplication.startTracking()` 100% preserved; zero SQLite schema migrations; Rule 4 Code Lock enforced until Gate 3 passes.

---

## 3. Proposed Architectural Changes
* **Service Layer (`TrackerService.java`)**:
  - Delete `sendBroadcast(RESET_ACCUMULATORS_INTENT)` from `mBanalConnection.onServiceConnected()`.
  - In `applyResumedState(@NonNull ResumedWorkoutState state)`, invoke `BANALService.setInitialSensorValue` for `ASCENT`, `DESCENT`, `TIME_LAP`, and `DISTANCE_m_LAP`.
* **Data Layer (`ResumedWorkoutState` & `WorkoutRepository.kt`)**:
  - Expand `ResumedWorkoutState` with `ascent: Double`, `descent: Double`, `timeLap_s: Int`, and `distanceLap_m: Double`, preserving backward-compatible 7-arg constructor.
  - In `WorkoutRepository.getResumedWorkoutState()`, add `ASCENDING` and `DESCENDING` to query projection; derive active lap metrics `timeLap_s = max(0, timeActive_s - sum(completedLapTime))` and `distanceLap_m = max(0.0, distanceTotal_m - sum(completedLapDist))` via `LapsDatabaseManager.getLaps(workoutId)`.
* **Sensor Layer (`MyAccumulatorSensor.java`)**:
  - In constructor, seed `mInitialValue` from `mMySensorManager.getInitialValue(sensorType)` when present, ensuring late-attaching providers inherit the active workout baseline.
* **UI Consistency**: No UI changes.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: ResumedWorkoutState & WorkoutRepository Data Rehydration
* **Files**: `TrackerService.java`, `WorkoutRepository.kt`
* **Changes**: Add 4 fields + 11-param constructor to `ResumedWorkoutState` (preserving 7-param delegating overload). Query `ASCENDING`/`DESCENDING` and compute active lap metrics in `WorkoutRepository.getResumedWorkoutState()`.

### Step 2: TrackerService Accumulator Preservation & Baseline Seeding
* **Files**: `TrackerService.java`
* **Changes**: Remove `RESET_ACCUMULATORS_INTENT` broadcast from `mBanalConnection.onServiceConnected()`. In `applyResumedState()`, invoke `BANALService.setInitialSensorValue` for `ASCENT`, `DESCENT`, `TIME_LAP`, and `DISTANCE_m_LAP`.

### Step 3: MyAccumulatorSensor Baseline Inheritance
* **Files**: `MyAccumulatorSensor.java`
* **Changes**: Read `mMySensorManager.getInitialValue(sensorType)` in constructor if registered. Notify listeners on `setInitialValue()` for reactive UI sync.

### Step 4: Targeted Unit & Characterisation Tests
* **Files**: `TrackerServiceResumeTest.kt`, `TrackerServiceCrashRecoveryCharacterisationTest.kt`, `MyAccumulatorSensorTest.kt`
* **Changes**: Assert no `RESET_ACCUMULATORS_INTENT` on resume, verify 9-sensor baseline seeding, assert lap metric rehydration, and test monotonic sample progression across recovery boundaries.

---

## 5. Verification Command (Tier 1 Targeted Unit Tests)
```bash
./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceResumeTest" --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceCrashRecoveryCharacterisationTest" --tests "com.atrainingtracker.banalservice.sensor.MyAccumulatorSensorTest"
```
