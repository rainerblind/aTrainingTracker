# Stage 5 Walkthrough: ATT-7875 - Preserve accumulators on TrackerService crash recovery to prevent mid-workout time and distance reset

**Ticket**: [ATT-7875](https://rainerblind.atlassian.net/browse/ATT-7875)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No Crashes & App Stability (Crash-Free Resilience)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8`  
**Requirement Mapping**: `REQ-TRK-078` (`REQ-TRK-078.1`..`REQ-TRK-078.4`) [Verified]  
**Test Spec Mapping**: `TST-TRK-078` (`TST-TRK-078.1`..`TST-TRK-078.6`) [Verified]  
**Author**: AI Agent 2 (`subagent_flash`) | **Date**: 2026-11-15  

---

## 1. Executive Summary & Verification Walkthrough
This ticket resolves mid-workout accumulator reset (`TIME_TOTAL`, `TIME_ACTIVE`, `DISTANCE_m`, `CALORIES`, `LAP_NR`, `ASCENT`, `DESCENT`, `TIME_LAP`, `DISTANCE_m_LAP`) caused by `START_STICKY` service recovery after process kill:
1. **Omission of Reset Intent on Resume (`REQ-TRK-078.1`)**:
   - Removed `sendBroadcast(RESET_ACCUMULATORS_INTENT)` from `TrackerService.mBanalConnection.onServiceConnected()`. `RESET_ACCUMULATORS_INTENT` is strictly reserved for fresh workout starts (`START_NORMAL`).
2. **Extended Resumed Workout State Rehydration (`REQ-TRK-078.2`)**:
   - `ResumedWorkoutState` carries `ascent`, `descent`, `timeLap_s`, and `distanceLap_m` with backward-compatible 7-arg constructor delegating defaults.
   - `WorkoutRepository.getResumedWorkoutState()` queries `ASCENDING` and `DESCENDING` and calculates active lap metrics from `LapsDatabaseManager.getLaps(workoutId)`.
3. **9-Sensor Baseline Seeding on Resume (`REQ-TRK-078.3`)**:
   - `TrackerService.applyResumedState()` invokes `BANALService.setInitialSensorValue` for `ASCENT`, `DESCENT`, `TIME_LAP`, and `DISTANCE_m_LAP` in addition to session totals.
4. **Late-Binding Sensor Initial Value Inheritance (`REQ-TRK-078.4`)**:
   - `MyAccumulatorSensor` constructor seeds `mInitialValue` from `mMySensorManager.getInitialValue(sensorType)` when registered, protecting late-attaching providers.

---

## 2. Verification Evidence & Test Execution

### Targeted Tier 1 Unit Tests (Stage 4 Commit SHA: `01d59f45`)
* **Test Command**:
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceResumeTest" --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceCrashRecoveryCharacterisationTest" --tests "com.atrainingtracker.banalservice.sensor.MyAccumulatorSensorTest"
  ```
* **Execution Results**: **27 / 27 unit tests PASSED** in 8.35s (exit code 0).
  - `TrackerServiceResumeTest` (18 tests): Verified no `RESET_ACCUMULATORS_INTENT` broadcast on resume, correct 9-sensor baseline seeding in `applyResumedState()`, and active lap time/distance computation in `WorkoutRepository.getResumedWorkoutState()`.
  - `TrackerServiceCrashRecoveryCharacterisationTest` (5 tests): Verified strict monotonic continuity of `TIME_TOTAL`, `DISTANCE_m`, and `ASCENT` across process recreation boundary.
  - `MyAccumulatorSensorTest` (4 tests): Verified initial value inheritance from `MySensorManager`, listener dispatch on `setInitialValue()`, and relative reset behavior.

### Living Documentation Governance & Traceability
* **Command**: `python3 tools/verify_requirement_governance.py`
* **Result**: **11 / 11 checks PASSED** (exit code 0), 100% master table parity (`REQ-TRK-078` & `TST-TRK-078` marked `[Verified]`), 0 dangling references.

---

## 3. Requirement Verification & Acceptance Matrix

| Requirement | Test Spec | Verification Vector | Result | Living Doc Status |
| :--- | :--- | :--- | :---: | :---: |
| `REQ-TRK-078.1` | `TST-TRK-078.1` | Unit test (`testResumeTracking_doesNotBroadcastResetAccumulatorsIntent_onServiceConnected`) | **PASSED** | `[Verified]` |
| `REQ-TRK-078.2` | `TST-TRK-078.2` | Unit test (`testGetResumedWorkoutState_rehydratesAscentDescentAndCalculatesActiveLapMetrics`) | **PASSED** | `[Verified]` |
| `REQ-TRK-078.3` | `TST-TRK-078.3` | Unit test (`testApplyResumedState_seedsAscentDescentAndLapAccumulatorSensors`) | **PASSED** | `[Verified]` |
| `REQ-TRK-078.4` | `TST-TRK-078.4` | Unit test (`testMyAccumulatorSensor_inheritsInitialValueFromManager`) | **PASSED** | `[Verified]` |
| `REQ-TRK-078` | `TST-TRK-078.5` | Characterisation test (`testEndToEndMonotonicAccumulatorContinuityAcrossProcessRecreation`) | **PASSED** | `[Verified]` |
| `REQ-PRO-001` | `TST-TRK-078.6` | Tier 1 unit test regression suite (27/27 tests) | **PASSED** | `[Verified]` |
