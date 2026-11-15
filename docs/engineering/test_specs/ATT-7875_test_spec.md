# Stage 2: Requirement & Test Specification - ATT-7875: Preserve accumulators on TrackerService crash recovery

**Ticket**: [ATT-7875](https://rainerblind.atlassian.net/browse/ATT-7875) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No Crashes & App Stability (Crash-Free Resilience)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8` | **Branch**: `feature/ATT-7875`  
**Requirement Mapping**: `REQ-TRK-078` (`REQ-TRK-078.1`..`REQ-TRK-078.4`)  
**Test Spec ID**: `TST-TRK-078` (`TST-TRK-078.1`..`TST-TRK-078.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-15  

---

## 1. Requirement Specification (REQ-TRK-078)

### 1.1 Problem Statement & Rationale
When Android restarts `START_STICKY` `TrackerService` after an unexpected process kill or crash (`StartType.RESUME_SERVICE_RECREATION` or `StartType.RESUME_BY_USER`), `TrackerService` unconditionally broadcasts `BANALService.RESET_ACCUMULATORS_INTENT` upon binding to `BANALService` (`TrackerService.java:964-965`), wiping session accumulators back to 0. Furthermore, `recreateValuesWhenResuming()` omits `ASCENT`, `DESCENT`, `TIME_LAP`, and `DISTANCE_m_LAP`. This resets workout metrics mid-ride, truncates exports (`.fit`/`.tcx`/`.gpx`), and causes fold-back artifacts in Aftermath elevation/telemetry charts.

### 1.2 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit

### 1.3 Functional Requirements
1. `TrackerService.mBanalConnection.onServiceConnected()` SHALL NOT broadcast `RESET_ACCUMULATORS_INTENT` on resume (`REQ-TRK-078.1`). `RESET_ACCUMULATORS_INTENT` is strictly reserved for `StartType.START_NORMAL`.
2. `ResumedWorkoutState` and `WorkoutRepository.getResumedWorkoutState()` SHALL extract `ascent`, `descent`, and active lap metrics (`timeLap_s`, `distanceLap_m` calculated across `LapsDatabaseManager`) (`REQ-TRK-078.2`).
3. `TrackerService.applyResumedState()` SHALL invoke `BANALService.setInitialSensorValue` for `ASCENT`, `DESCENT`, `TIME_LAP`, and `DISTANCE_m_LAP` alongside `TIME_TOTAL`, `TIME_ACTIVE`, `CALORIES`, `LAP_NR`, and `DISTANCE_m` (`REQ-TRK-078.3`).
4. `MyAccumulatorSensor` constructor SHALL initialize `mInitialValue` from `MySensorManager.getInitialValue(sensorType)` to support late-binding sensor providers (`REQ-TRK-078.4`).

### 1.4 Acceptance Criteria (Given-When-Then)
* *Given* an active workout with persisted totals (`timeTotal = 643s`, `distance = 1098.8m`, `ascent = 21.0m`, `descent = 1.7m`), *When* `TrackerService` restarts via `StartType.RESUME_SERVICE_RECREATION`, *Then* `RESET_ACCUMULATORS_INTENT` is not broadcast and subsequent samples advance monotonically without reset.
* *Given* an active workout on `lapNr = 2` with lap 1 completed (`300s`, `500m`), *When* crash recovery occurs, *Then* `TIME_LAP` and `DISTANCE_m_LAP` are initialized to active lap values (`timeActive - 300`, `distanceTotal - 500`).
* *Given* late-binding sensors instantiated after resume, *When* constructed, *Then* initial values inherit the pre-crash baseline registered in `MySensorManager`.

### 1.5 System Invariants
Monotonic non-decreasing accumulator values across recovery boundaries; `START_NORMAL` fresh start reset behavior 100% preserved; zero SQLite schema alterations; 100% clean-room test pass rate.

---

## 2. Test Specification (TST-TRK-078)

### Test Case 1: `testResumeTracking_doesNotBroadcastResetAccumulatorsIntent` (`TST-TRK-078.1`)
* **Scope**: Unit Test (`TrackerServiceResumeTest.kt`)
* **Expected Result**: `onServiceConnected()` with `mResumeTrackingWhenConnectedToBanalService` does not send `RESET_ACCUMULATORS_INTENT`.

### Test Case 2: `testGetResumedWorkoutState_loadsAscentDescentAndLapMetrics` (`TST-TRK-078.2`)
* **Scope**: Unit Test (`TrackerServiceResumeTest.kt` / `WorkoutRepositoryTest.kt`)
* **Expected Result**: `getResumedWorkoutState()` returns populated `ascent`, `descent`, `timeLap_s`, and `distanceLap_m`.

### Test Case 3: `testApplyResumedState_seedsAllAccumulatorSensors` (`TST-TRK-078.3`)
* **Scope**: Unit Test (`TrackerServiceResumeTest.kt`)
* **Expected Result**: `BANALService.setInitialSensorValue` invoked for all 9 accumulator sensor types.

### Test Case 4: `testMyAccumulatorSensor_inheritsInitialValueFromManager` (`TST-TRK-078.4`)
* **Scope**: Unit Test (`MyAccumulatorSensorTest.kt`)
* **Expected Result**: `getValue()` reflects baseline registered in `MySensorManager`.

### Test Case 5: `testEndToEndMonotonicContinuityAcrossCrashRecovery` (`TST-TRK-078.5`)
* **Scope**: Characterisation Test (`TrackerServiceCrashRecoveryCharacterisationTest.kt`)
* **Expected Result**: Simulated post-crash tick samples strictly non-decreasing across recovery boundary.

### Test Case 6: Tier 1 Targeted Regression Suite (`TST-TRK-078.6`)
* **Scope**: Unit Test Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.*" --tests "com.atrainingtracker.banalservice.sensor.*"`

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-078.1` | Unit | `TrackerService.mBanalConnection` | `REQ-TRK-078.1` | Specified |
| `TST-TRK-078.2` | Unit | `WorkoutRepository.getResumedWorkoutState` | `REQ-TRK-078.2` | Specified |
| `TST-TRK-078.3` | Unit | `TrackerService.applyResumedState` | `REQ-TRK-078.3` | Specified |
| `TST-TRK-078.4` | Unit | `MyAccumulatorSensor.<init>` | `REQ-TRK-078.4` | Specified |
| `TST-TRK-078.5` | Characterisation | `TrackerService` / Crash Recovery | `REQ-TRK-078.1`..`4` | Specified |
| `TST-TRK-078.6` | Tier 1 Regression | Tracker & Sensor subsystems | `REQ-PRO-001` | Specified |
