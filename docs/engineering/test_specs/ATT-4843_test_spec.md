# Stage 2: Requirement & Test Specification - ATT-4843: [Bug] Analyse Georgenfelder Hochmoor

**Ticket**: [ATT-4843](https://rainerblind.atlassian.net/browse/ATT-4843)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V4.9.41` | **Active Sprint**: `2026-44.1` | **Branch**: `feature/ATT-4843`  
**Requirement Mapping**: `REQ-TRK-041` (*Geodesic Displacement Plausibility Gating & Canonical Summary Filtering*)  
**Test Spec ID**: `TST-TRK-041` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-01  

---

## 1. Requirement Specification (`REQ-TRK-041`)

### 1.1 Problem Statement & Rationale
In workout 434, `distanceTotal_m` accumulated 13.38 km instead of ~4.37 km because: (1) `SpeedAndLocationDevice.java` accumulated an 8.5 km indoor cell-tower triangulation spike in `mDistanceSensor` without displacement velocity gating, and (2) `TrackerService.java:sampleAndWriteToDb()` iterated `getAllSensorData()` and allowed secondary device `Google Fused` to overwrite `summaryValues` because `getDeviceName() == null` was not checked.

### 1.2 Functional & Architectural Requirements
1. **Displacement Plausibility Clamping (`REQ-TRK-041.1`)**: In `SpeedAndLocationDevice.onNewLocation()`, when `mPrevLocation != null` and `dt_ms = location.getTime() - mPrevLocation.getTime()` satisfies `0 < dt_ms < 30000`, the system SHALL compute `calcSpeed = delta_distance / (dt_ms / 1000.0)`. If `calcSpeed > 70.0 m/s` (252 km/h, matching `REQ-TRK-014`), `delta_distance` SHALL be clamped to `0.0` for distance accumulators (`mDistanceSensor`, `mLapDistanceSensor`).
2. **Canonical Multi-Sensor Summary Filtering (`REQ-TRK-041.2`)**: In `TrackerService.sampleAndWriteToDb()`, `summaryValues.put(...)` for `DISTANCE_TOTAL_m`, `TIME_TOTAL_s`, `TIME_ACTIVE_s`, `CALORIES`, `LAPS`, `ASCENDING`, and `DESCENDING` SHALL strictly execute only when `sensorData.getDeviceName() == null` (the canonical/primary proxy sensor). Secondary qualified sensors (`deviceName != null`) SHALL continue logging to raw `samplingValues` only.
3. **Average Speed Harmonization (`REQ-TRK-041.3`)**: `WorkoutSummaries.SPEED_AVERAGE_mps` SHALL strictly evaluate canonical `mDistanceTotal_m / mTimeActive_s`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Teleportation Rejection)**:
  * *Given* two location fixes arriving 1000 ms apart with $\Delta s = 4,245.6\text{ m}$ ($v > 70.0\text{ m/s}$),
  * *When* `onNewLocation` processes the fix,
  * *Then* `mDistanceSensor` and `mLapDistanceSensor` values SHALL NOT increment ($+0.0\text{ m}$).
* **Criterion 2 (Canonical Summary Isolation)**:
  * *Given* an active tracking session with canonical `ProxySensor` (distance = 4,370 m, `deviceName == null`) and secondary device (distance = 13,377 m, `deviceName == "Google Fused"`),
  * *When* `sampleAndWriteToDb()` executes,
  * *Then* `summaryValues.getAsDouble(DISTANCE_TOTAL_m)` SHALL equal 4,370 m, while `samplingValues` records both `DISTANCE_m` (4,370 m) and `"DISTANCE_m (Google Fused)"` (13,377 m).

### 1.4 System Invariants
1. Secondary device columns in `WorkoutSamples.db` must continue logging.
2. Legitimate speeds ($\le 70.0\text{ m/s}$) accumulate full distance without attenuation.
3. Single-threaded serialization on `mDbExecutor` is preserved.

---

## 2. Test Specification (`TST-TRK-041`)

### Test Case 1: Displacement Plausibility Clamping (`TST-TRK-041.1`)
* **Scope**: Unit Test | **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDeviceDisplacementTest.kt`
* **Preconditions**: `SpeedAndLocationDevice` instance with initial location `(50.73199, 13.74682)` at $t=1000$.
* **Action**: Inject location `(50.70461, 13.78871)` at $t=2000$ ($\Delta s = 4245.6\text{ m}, v = 4245.6\text{ m/s}$).
* **Expected Result**: `mDistanceSensor.getValue()` remains `0.0`. Subsequent normal fix ($\Delta s = 15.0\text{ m}, v = 15\text{ m/s}$) increments distance to `15.0`.

### Test Case 2: Multi-Sensor Summary Isolation (`TST-TRK-041.2`)
* **Scope**: Unit Test | **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceMultiSensorSummaryTest.kt`
* **Preconditions**: Mock `BANALServiceComm` returning `ProxySensor` (`deviceName = null`, dist = 4370.0) and `Google Fused` (`deviceName = "Google Fused"`, dist = 13377.0).
* **Action**: Invoke `sampleAndWriteToDb()`.
* **Expected Result**: `WorkoutSummaries.DISTANCE_TOTAL_m` equals `4370.0`. `samplingValues` contains both keys.

### Test Case 3: Targeted & Clean-Room Regression Suite (`TST-TRK-041.3`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.trainingtracker.tracker.*"`
* **Goal**: 100% pass rate across affected packages and zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-041.1` | Unit | `SpeedAndLocationDevice.onNewLocation` | `REQ-TRK-041.1` | Specified |
| `TST-TRK-041.2` | Unit | `TrackerService.sampleAndWriteToDb` | `REQ-TRK-041.2`, `REQ-TRK-041.3` | Specified |
| `TST-TRK-041.3` | Regression | Full tracker & device test suites | `REQ-TRK-041` | Specified |
