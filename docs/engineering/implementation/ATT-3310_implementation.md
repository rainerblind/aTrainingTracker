# Stage 4: Implementation Report - ATT-3310: Configure default sensor data filtering on initial install

**Ticket**: [ATT-3310](https://rainerblind.atlassian.net/browse/ATT-3310)  
**Sub-task**: [ATT-3381](https://rainerblind.atlassian.net/browse/ATT-3381) (`[Implementation]`)  
**Parent Epic**: [ATT-2561](https://rainerblind.atlassian.net/browse/ATT-2561) (*Sensors: BLE / ANT+ Connectivity, Calibration & Telemetry*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-198` (Amending Clause 1)  
**Test Mapping**: `TST-UI-152` (Amending Clause 1 & 2)  
**Branch**: `feature/ATT-3310`  
**Author**: AI Agent 1 (Software Construction Engineer)  
**Date**: 2026-10-13  

---

## 1. Summary of Changes

In accordance with the approved Stage 3 Implementation Plan, the following software construction activities were completed:

### 1.1 `SensorFilterDefaults.kt` Update
- **File**: `app/src/main/java/com/atrainingtracker/banalservice/filters/SensorFilterDefaults.kt`
- Added explicit mapping branches in `getDefaultFilterConfig(sensorType: SensorType?)`:
  ```kotlin
  SensorType.SPEED_mps -> DefaultFilterConfig(FilterType.MOVING_AVERAGE_TIME, 2.0, "sec")
  SensorType.CADENCE -> DefaultFilterConfig(FilterType.MOVING_AVERAGE_TIME, 2.0, "sec")
  ```
- Updated KDoc documentation explaining that Speed and Cadence default to a 2-second moving average to eliminate 1Hz GPS quantization jitter and discrete crank rotation timing flutter.
- Verified that `SensorType.HR`, environmental metrics (`ALTITUDE`, `TEMPERATURE`), and all accumulators (`DISTANCE_m`, `TIME_ACTIVE`, `CALORIES`, etc.) strictly retain `FilterType.INSTANTANEOUS, 1.0s`.

### 1.2 `SensorFilterDefaultsTest.kt` Update
- **File**: `app/src/test/java/com/atrainingtracker/banalservice/filters/SensorFilterDefaultsTest.kt`
- Added `@Test fun testSpeedSensor_defaultsToTwoSecondMovingAverage()` verifying `SensorType.SPEED_mps` resolves `MOVING_AVERAGE_TIME, 2.0s`.
- Added `@Test fun testCadenceSensor_defaultsToTwoSecondMovingAverage()` verifying `SensorType.CADENCE` resolves `MOVING_AVERAGE_TIME, 2.0s`.
- Updated `testContinuousMetrics_defaultToInstantaneous()` by removing `SPEED_mps` and `CADENCE` while verifying `HR`, `ALTITUDE`, and `TEMPERATURE` strictly return `INSTANTANEOUS, 1.0s`.

---

## 2. Targeted Verification Results

1. **`SensorFilterDefaultsTest`**:
   - `testPowerSensor_defaultsToThreeSecondMovingAverage`: **PASSED**
   - `testSpeedSensor_defaultsToTwoSecondMovingAverage`: **PASSED**
   - `testCadenceSensor_defaultsToTwoSecondMovingAverage`: **PASSED**
   - `testPaceSensor_defaultsToFiveSecondMovingAverage`: **PASSED**
   - `testVerticalSpeedSensor_defaultsToFifteenSecondMovingAverage`: **PASSED**
   - `testSlopeSensor_defaultsToFiveSecondMovingAverage`: **PASSED**
   - `testAccumulators_defaultToInstantaneous`: **PASSED**
   - `testContinuousMetrics_defaultToInstantaneous`: **PASSED**
   - `testNullSensorType_returnsInstantaneousFallback`: **PASSED**
   - `testAllSensorTypes_returnNonNullValidConfigurations`: **PASSED**
2. **`EditSensorFieldViewModelTest`**:
   - All tests passed with 100% success rate (11s runtime).

---

## 3. Invariants & Backward Compatibility Verification

- **User Sovereignty**: Athletes can modify or disable filtering at any time in `ConfigureFilterDialog`.
- **Database Immutability**: Existing user database records are completely untouched (zero upgrade migration).
- **Metric Integrity**: Heart Rate (`HR`) remains strictly instantaneous for immediate threshold detection.
