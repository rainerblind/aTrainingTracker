# Stage 3: Implementation Plan - ATT-3310: Configure default sensor data filtering on initial install

**Ticket**: [ATT-3310](https://rainerblind.atlassian.net/browse/ATT-3310)  
**Sub-task**: [ATT-3380](https://rainerblind.atlassian.net/browse/ATT-3380) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2561](https://rainerblind.atlassian.net/browse/ATT-2561) (*Sensors: BLE / ANT+ Connectivity, Calibration & Telemetry*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-198` (Amending Clause 1)  
**Test Mapping**: `TST-UI-152` (Amending Clause 1 & 2)  
**Branch**: `feature/ATT-3310`  
**Author**: AI Agent 1 (Software Architect & Planner)  
**Date**: 2026-10-13  

---

## 1. Architectural Overview (SWE.2)

### 1.1 Architectural Layering & Components
- **Central Defaults Registry (`BANALService` layer)**:
  `com.atrainingtracker.banalservice.filters.SensorFilterDefaults.kt` provides domain-optimized filter configuration presets for sensor types.
- **Database Seeding Layer (`TrackingViewsDatabaseManager.java`)**:
  During initial installation `onCreate()`, `addDefaultTab(...)` seeds initial view rows by querying `SensorFilterDefaults.getDefaultFilterConfig(rowData.sensorType)` and populates `FILTER_TYPE` and `FILTER_CONSTANT`. No database schema bump or table migration is executed for existing databases, preserving existing user configurations completely untouched.
- **Presentation / ViewModel Layer (`EditSensorFieldViewModel.kt`)**:
  When users configure or change a sensor type in the cockpit editor, `EditSensorFieldViewModel` queries `SensorFilterDefaults.getDefaultFilterConfig(sensorType)` to initialize `selectedFilterType` and `filterConstant`.

### 1.2 Threading & Concurrency Model
- `SensorFilterDefaults` is an immutable, pure domain mapping object (`object SensorFilterDefaults`). All evaluations are synchronous, non-blocking, and thread-safe across any calling dispatcher (`Dispatchers.Main`, `Dispatchers.IO`, `dbDispatcher`).

---

## 2. Atomic Implementation Steps

### Step 1: Update `SensorFilterDefaults.kt`
- **File**: `app/src/main/java/com/atrainingtracker/banalservice/filters/SensorFilterDefaults.kt`
- **Action**:
  - Add explicit branches to `when (sensorType)`:
    - `SensorType.SPEED_mps -> DefaultFilterConfig(FilterType.MOVING_AVERAGE_TIME, 2.0, "sec")`
    - `SensorType.CADENCE -> DefaultFilterConfig(FilterType.MOVING_AVERAGE_TIME, 2.0, "sec")`
  - Update KDoc to explain the rationale: Speed and Cadence default to a 2-second moving average to dampen 1Hz GPS quantization noise and crank rotation timing flutter.
  - Verify that `SensorType.HR` and all accumulators remain instantaneous (`FilterType.INSTANTANEOUS, 1.0s`).

### Step 2: Update Unit Tests in `SensorFilterDefaultsTest.kt`
- **File**: `app/src/test/java/com/atrainingtracker/banalservice/filters/SensorFilterDefaultsTest.kt`
- **Action**:
  - Add `@Test fun testSpeedSensor_defaultsToTwoSecondMovingAverage()`:
    - Assert `SensorFilterDefaults.getDefaultFilterConfig(SensorType.SPEED_mps)` returns `FilterType.MOVING_AVERAGE_TIME`, `2.0`, `"sec"`.
    - Assert `SensorType.SPEED_mps.getDefaultFilterConfig()` returns equivalent configuration.
    - Assert helper methods `getDefaultFilterType`, `getDefaultFilterConstant`, and `getDefaultUnit`.
  - Add `@Test fun testCadenceSensor_defaultsToTwoSecondMovingAverage()`:
    - Assert `SensorFilterDefaults.getDefaultFilterConfig(SensorType.CADENCE)` returns `FilterType.MOVING_AVERAGE_TIME`, `2.0`, `"sec"`.
    - Assert `SensorType.CADENCE.getDefaultFilterConfig()` returns equivalent configuration.
    - Assert helper methods `getDefaultFilterType`, `getDefaultFilterConstant`, and `getDefaultUnit`.
  - In `@Test fun testContinuousMetrics_defaultToInstantaneous()`:
    - Remove `SensorType.SPEED_mps` and `SensorType.CADENCE` from the list.
    - Retain `SensorType.HR`, `SensorType.ALTITUDE`, and `SensorType.TEMPERATURE` to assert they strictly default to `FilterType.INSTANTANEOUS`, `1.0s`.

### Step 3: Targeted Verification & Regression Suite
- **Commands**:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.filters.SensorFilterDefaultsTest"`
  - `./gradlew testDebugUnitTest --tests "*EditSensorFieldViewModel*"`

---

## 3. Invariant Protection & Verification

1. **User Sovereignty**: Athletes retain 100% freedom to modify or disable filtering in `ConfigureFilterDialog`.
2. **Database Immutability**: Existing user database records are never modified via upgrade migrations.
3. **Heart Rate Metric Protection**: Heart rate (`HR`) remains strictly instantaneous (`FilterType.INSTANTANEOUS, 1.0s`) to ensure immediate detection of cardiac threshold transitions.
4. **Accumulator Protection**: Total distance, lap distance, active time, total time, calories, and lap counts strictly remain instantaneous.
5. **Clean Architecture Separation**: `SensorFilterDefaults` remains pure Kotlin domain logic without Android framework dependencies.
