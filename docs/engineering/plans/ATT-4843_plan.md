# Stage 3: Implementation Plan - ATT-4843: [Bug] Analyse Georgenfelder Hochmoor

**Ticket**: [ATT-4843](https://rainerblind.atlassian.net/browse/ATT-4843)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.1`  
**Requirement Mapping**: `REQ-TRK-041`  
**Test Mapping**: `TST-TRK-041`  
**Branch**: `feature/ATT-4843`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Problem Description & Background

In workout 434 (*Georgenfelder Hochmoor*), `distanceTotal_m` accumulated 13,377.31 m instead of ~4.37 km due to:
1. `SpeedAndLocationDevice.java`: Unconditional distance accumulation without displacement velocity plausibility gating during multipath cell-tower teleportation jumps.
2. `TrackerService.java:sampleAndWriteToDb()`: Secondary device data (`Google Fused`) overwrote `summaryValues` because `sensorData.getDeviceName() == null` was not checked.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TRK-041` (*Geodesic Displacement Plausibility Gating & Canonical Summary Filtering*)
  * `REQ-TRK-041.1`: Displacement speed clamping (`calcSpeed <= 70.0 m/s`).
  * `REQ-TRK-041.2`: Canonical summary filtering (`sensorData.getDeviceName() == null`).
  * `REQ-TRK-041.3`: Authoritative average speed calculation.
* **Test Mapping**: `TST-TRK-041` (`TST-TRK-041.1`, `TST-TRK-041.2`, `TST-TRK-041.3`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing location, distance, and tracker tests pass cleanly.
2. **Raw Multi-Sensor Telemetry Unaffected**: Secondary devices continue logging to `WorkoutSamples.db` with qualified column names (`sensorName (deviceName)`).
3. **Plausible Speed Preservation**: Legitimate displacements ($v \le 70.0\text{ m/s}$) accumulate full distance without attenuation.
4. **Thread Safety & Dispatcher Affinity**: Database writes remain serialized on `mDbExecutor`.
5. **Human Gate Invariance**: Terminal completion of parent ticket remains reserved for human in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `SpeedAndLocationDevice` (Service Layer / Sensor HAL)
* In `onNewLocation(Location location)`:
  * When `mPrevLocation != null` and `dt_ms = location.getTime() - mPrevLocation.getTime()` satisfies `0 < dt_ms < 30000`:
  * Compute `calcSpeed = delta_distance / (dt_ms / 1000.0)`.
  * If `calcSpeed > 70.0`, clamp `delta_distance = 0.0` before passing to `mDistanceSensor.increment(...)` and `mLapDistanceSensor.increment(...)`.

### Component 2: `TrackerService` (Service Layer / Session Aggregator)
* In `sampleAndWriteToDb()`:
  * Wrap `switch (sensorType)` for `summaryValues.put(...)` and session state variables (`mTimeTotal_s`, `mTimeActive_s`, `mDistanceTotal_m`) with `if (deviceName == null)`.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Clamp Implausible Displacement in `SpeedAndLocationDevice.java`
* File: `app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice.java`
* Action: Guard `delta_distance` accumulation against `calcSpeed > 70.0 m/s` when `0 < dt_ms < 30000`.

### Step 2: Gate Summary Updates in `TrackerService.java`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
* Action: Restrict `summaryValues` and cumulative state mutations to `deviceName == null`.

### Step 3: Targeted Unit Tests
* Files:
  * `app/src/test/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDeviceDisplacementTest.kt` (`TST-TRK-041.1`)
  * `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceMultiSensorSummaryTest.kt` (`TST-TRK-041.2`)
* Command:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDeviceDisplacementTest" --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceMultiSensorSummaryTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests during Stage 4 construction, followed by full tracker suite in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-4843` allows instant reset via `git checkout -- .` or branch deletion.
