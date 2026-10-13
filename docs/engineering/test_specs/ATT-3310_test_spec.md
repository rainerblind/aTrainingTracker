# Stage 2: Requirement & Test Specification - ATT-3310: Configure default sensor data filtering on initial install

**Ticket**: [ATT-3310](https://rainerblind.atlassian.net/browse/ATT-3310)  
**Sub-task**: [ATT-3379](https://rainerblind.atlassian.net/browse/ATT-3379) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2561](https://rainerblind.atlassian.net/browse/ATT-2561) (*Sensors: BLE / ANT+ Connectivity, Calibration & Telemetry*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-198` (Amending Clause 1)  
**Test Mapping**: `TST-UI-152` (Amending Clause 1 & 2)  
**Branch**: `feature/ATT-3310`  
**Author**: AI Agent 1 (Specification Engineer)  
**Date**: 2026-10-13  

---

## 1. Requirement Specification (`REQ-UI-198`)

The system SHALL provide domain-optimized default filter presets for sensor metrics upon field creation, sensor type switching, and initial tracking view database seeding, eliminating raw telemetry jitter while preserving accumulator integrity (ATT-1624, ATT-3310):

1. **Centralized Sensor Filter Defaults Registry (`SensorFilterDefaults.kt`)**:
   - `DefaultFilterConfig(val filterType: FilterType, val filterConstant: Double, val unit: String = "sec")`.
   - `SensorFilterDefaults.getDefaultFilterConfig(sensorType: SensorType?)` SHALL evaluate:
     - `SensorType.POWER`: `FilterType.MOVING_AVERAGE_TIME`, constant `3.0`, unit `"sec"`.
     - `SensorType.SPEED_mps`: `FilterType.MOVING_AVERAGE_TIME`, constant `2.0`, unit `"sec"` (ATT-3310).
     - `SensorType.CADENCE`: `FilterType.MOVING_AVERAGE_TIME`, constant `2.0`, unit `"sec"` (ATT-3310).
     - `SensorType.PACE_spm`: `FilterType.MOVING_AVERAGE_TIME`, constant `5.0`, unit `"sec"`.
     - `SensorType.VERTICAL_SPEED`: `FilterType.MOVING_AVERAGE_TIME`, constant `15.0`, unit `"sec"`.
     - `SensorType.SLOPE`: `FilterType.MOVING_AVERAGE_TIME`, constant `5.0`, unit `"sec"`.
     - All accumulator and discrete counter metrics (`DISTANCE_m`, `TIME_ACTIVE`, `CALORIES`, `LAP_NR`, `DISTANCE_m_LAP`, `TIME_LAP`, `TIME_TOTAL`, `PHONE_BATTERY`, `BATTERY_REMAINING_TIME`): strictly `FilterType.INSTANTANEOUS`, constant `1.0`.
     - Continuous physiological and environmental metrics (`HR`, `ALTITUDE`, `TEMPERATURE`): strictly `FilterType.INSTANTANEOUS`, constant `1.0`.
     - Null or unknown sensor types: `FilterType.INSTANTANEOUS`, constant `1.0`.
   - `SensorFilterDefaults` SHALL expose static `@JvmStatic` helper methods (`getDefaultFilterConfig`, `getDefaultFilterType`, `getDefaultFilterConstant`, `getDefaultUnit`) and Kotlin extension method `SensorType.getDefaultFilterConfig()`.
2. **Sensor Field Editor Dynamic State Synchronization (`EditSensorFieldViewModel.kt`)**:
   - In `EditSensorFieldViewModel.init`, fallback field initialization SHALL resolve `SensorFilterDefaults.getDefaultFilterConfig(defaultSensor)`.
   - In `onSensorTypeChanged(newSensorType)`, the system SHALL update `selectedFilterType`, `filterConstant`, `movingAverageUnit`, and `filterSummary` dynamically using `newSensorType.getDefaultFilterConfig()`.
3. **Default Cockpit View Database Seeding (`TrackingViewsDatabaseManager.java`)**:
   - When generating default tracking view rows during `onCreate()` via `addDefaultTab(...)`, the system SHALL query `SensorFilterDefaults.getDefaultFilterConfig(rowData.sensorType)` to assign `FILTER_TYPE` and `FILTER_CONSTANT`, ensuring newly installed databases inherit 2.0s smoothing for Speed and Cadence.
4. **User Sovereignty & Immutability Invariants**:
   - Existing saved views in `TrackingViewsDatabaseManager.ROWS_TABLE` SHALL remain completely immutable across upgrades (zero automated migration).
   - Athletes retain unrestricted ability to customize, alter, or disable filters in `ConfigureFilterDialog`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-UI-198` (*Cockpit Sensor Field Domain-Specific Default Filter Presets Architecture*), targeting `SensorFilterDefaults.kt`, `TrackingViewsDatabaseManager.java`, and `EditSensorFieldViewModel.kt`.
2. **Historical Origin & Commit Trace**:
   - Ticket `ATT-1624` (Sprint `2026-40.10`), which established centralized domain-specific presets for Power (3s), Pace (5s), Vertical Speed (15s), and Slope (5s), while leaving other continuous metrics in the `else` block (`INSTANTANEOUS`).
3. **Root Reason for Existing Formulation**:
   - In ATT-1624, the primary focus was dampening extreme high-variance metrics (pedal stroke dead-spots and 1Hz barometric VAM/slope noise). Speed and cadence were left raw under the assumption that bike speed and cadence sensors were sufficiently steady. In practice, GPS-derived speed and discrete reed switch cadence exhibit distracting display flutter on high-resolution screens. A gentle 2-second moving average stabilizes cockpit readouts while preserving real-time responsiveness.
4. **Preservation of Core Invariants**:
   - Heart Rate (`SensorType.HR`) remains strictly `INSTANTANEOUS, 1.0s`.
   - Accumulator and discrete counter metrics (`DISTANCE_m`, `TIME_ACTIVE`, `CALORIES`, `LAP_NR`, `BATTERY_*`) remain strictly `INSTANTANEOUS, 1.0s`.
   - Existing user database rows are NOT migrated or altered (zero schema migration).
   - User sovereignty is fully preserved: athletes can freely modify or disable filtering in `ConfigureFilterDialog`.

---

## 3. Test Specification (`TST-UI-152`)

### 3.1 Unit Test Cases (`SensorFilterDefaultsTest.kt`)
1. **`testSpeedSensor_defaultsToTwoSecondMovingAverage()`**:
   - Assert `SensorFilterDefaults.getDefaultFilterConfig(SensorType.SPEED_mps)` returns `FilterType.MOVING_AVERAGE_TIME`, `filterConstant = 2.0`, `unit = "sec"`.
   - Assert `SensorType.SPEED_mps.getDefaultFilterConfig()` matches.
2. **`testCadenceSensor_defaultsToTwoSecondMovingAverage()`**:
   - Assert `SensorFilterDefaults.getDefaultFilterConfig(SensorType.CADENCE)` returns `FilterType.MOVING_AVERAGE_TIME`, `filterConstant = 2.0`, `unit = "sec"`.
   - Assert `SensorType.CADENCE.getDefaultFilterConfig()` matches.
3. **`testContinuousMetrics_defaultToInstantaneous()`**:
   - Assert `SensorType.HR`, `SensorType.ALTITUDE`, and `SensorType.TEMPERATURE` strictly return `FilterType.INSTANTANEOUS`, `filterConstant = 1.0`, `unit = "sec"`.
4. **`testPowerSensor_defaultsToThreeSecondMovingAverage()`**:
   - Assert `SensorType.POWER` retains `FilterType.MOVING_AVERAGE_TIME, 3.0s`.
5. **`testPaceSensor_defaultsToFiveSecondMovingAverage()`**:
   - Assert `SensorType.PACE_spm` retains `FilterType.MOVING_AVERAGE_TIME, 5.0s`.
6. **`testAccumulators_defaultToInstantaneous()`**:
   - Assert all accumulator metrics (`DISTANCE_m`, `TIME_ACTIVE`, `CALORIES`, etc.) strictly return `INSTANTANEOUS, 1.0s`.

### 3.2 Regression Suite
- Clean-room test suite execution: `./gradlew testDebugUnitTest` must pass with 100% success rate.

---

## 4. Traceability Matrix

| Requirement Clause | Test Case | Target Component |
| :--- | :--- | :--- |
| `REQ-UI-198` (Clause 1: Speed 2.0s MA) | `SensorFilterDefaultsTest.testSpeedSensor_defaultsToTwoSecondMovingAverage` | `SensorFilterDefaults.kt` |
| `REQ-UI-198` (Clause 1: Cadence 2.0s MA) | `SensorFilterDefaultsTest.testCadenceSensor_defaultsToTwoSecondMovingAverage` | `SensorFilterDefaults.kt` |
| `REQ-UI-198` (Clause 1: HR Instantaneous) | `SensorFilterDefaultsTest.testContinuousMetrics_defaultToInstantaneous` | `SensorFilterDefaults.kt` |
| `REQ-UI-198` (Clause 2: Field Editor Prefill) | `EditSensorFieldViewModelTest` | `EditSensorFieldViewModel.kt` |
| `REQ-UI-198` (Clause 3: Fresh DB Seeding) | `TrackingViewsDatabaseManager` (Default Table Init) | `TrackingViewsDatabaseManager.java` |
| `REQ-UI-198` (Clause 4: Invariants) | Full test suite clean-room run | All modules |
