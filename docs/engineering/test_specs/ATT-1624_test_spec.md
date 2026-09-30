# Stage 2 Requirement & Test Specification: ATT-1624

**Ticket**: [ATT-1624](https://atrainingtracker.atlassian.net/browse/ATT-1624)  
**Sub-task**: [ATT-1685](https://atrainingtracker.atlassian.net/browse/ATT-1685) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1624`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Traceability Matrix

| Requirement ID | Test Case ID | Scope Summary | Verification Method | Status |
|:---|:---|:---|:---|:---|
| **REQ-UI-198** | **TST-UI-152** | Centralized SensorFilterDefaults registry, dynamic ViewModel defaults synchronization, TrackingViewsDatabaseManager seed alignment | Unit Tests (`SensorFilterDefaultsTest.kt`, `EditSensorFieldViewModelTest.kt`) & Clean-Room Regression | Specified |

---

## 2. Formal Requirement Specification

### `REQ-UI-198`: Cockpit Sensor Field Domain-Specific Default Filter Presets Architecture

The system SHALL provide domain-optimized default filter presets for sensor metrics upon field creation, sensor type switching, and initial tracking view database seeding, eliminating raw telemetry jitter while preserving accumulator integrity (ATT-1624):

1. **Centralized Sensor Filter Defaults Registry (`SensorFilterDefaults.kt`)**:
   - The system SHALL define `DefaultFilterConfig(val filterType: FilterType, val filterConstant: Double, val unit: String = "sec")`.
   - `SensorFilterDefaults.getDefaultFilterConfig(sensorType: SensorType?)` SHALL evaluate:
     - `SensorType.POWER`: `FilterType.MOVING_AVERAGE_TIME`, constant `3.0`, unit `"sec"`.
     - `SensorType.PACE_spm`: `FilterType.MOVING_AVERAGE_TIME`, constant `5.0`, unit `"sec"`.
     - `SensorType.VERTICAL_SPEED`: `FilterType.MOVING_AVERAGE_TIME`, constant `15.0`, unit `"sec"`.
     - `SensorType.SLOPE`: `FilterType.MOVING_AVERAGE_TIME`, constant `5.0`, unit `"sec"`.
     - All accumulator and discrete counter metrics (`DISTANCE_m`, `TIME_ACTIVE`, `CALORIES`, `LAP_NR`, `DISTANCE_m_LAP`, `TIME_LAP`, `TIME_TOTAL`, `PHONE_BATTERY`, `BATTERY_REMAINING_TIME`): strictly `FilterType.INSTANTANEOUS`, constant `1.0`.
     - Other continuous metrics (`HR`, `SPEED_mps`, `CADENCE`, `ALTITUDE`, `TEMPERATURE`): `FilterType.INSTANTANEOUS`, constant `1.0`.
     - Null or unknown sensor types: `FilterType.INSTANTANEOUS`, constant `1.0`.
   - `SensorFilterDefaults` SHALL expose `@JvmStatic` static accessor methods (`getDefaultFilterConfig`, `getDefaultFilterType`, `getDefaultFilterConstant`, `getDefaultUnit`) and a Kotlin extension method `SensorType.getDefaultFilterConfig()` for seamless cross-language interoperability.

2. **Sensor Field Editor Dynamic State Synchronization (`EditSensorFieldViewModel.kt`)**:
   - In `EditSensorFieldViewModel.init`, fallback field initialization SHALL resolve `SensorFilterDefaults.getDefaultFilterConfig(defaultSensor)`.
   - In `onSensorTypeChanged(newSensorType)`, the system SHALL update `selectedFilterType`, `filterConstant`, `movingAverageUnit`, and `filterSummary` dynamically using `newSensorType.getDefaultFilterConfig()`, and synchronize `isCustomFilterExpanded = (resolveFilterPreset(...) == FilterPreset.CUSTOM)`.
   - In `onFilterConfigDismissed()`, when reverting unsaved modifications for a newly selected sensor type, the system SHALL restore the domain-specific defaults from `SensorFilterDefaults`.

3. **Default Cockpit View Database Seeding (`TrackingViewsDatabaseManager.java`)**:
   - When generating default tracking view rows in `TrackingViewsDatabaseManager.addDefaultTab(...)`, the system SHALL query `SensorFilterDefaults.getDefaultFilterConfig(rowData.sensorType)` to assign `FILTER_TYPE` and `FILTER_CONSTANT`, ensuring pre-configured tabs (e.g. Run views containing `PACE_spm`) inherit smoothed presets upon clean database creation.

4. **User Sovereignty & Customization Invariants**:
   - Existing saved views in `TrackingViewsDatabaseManager.ROWS_TABLE` SHALL remain completely immutable.
   - The user's ability to manually override, customize, or disable filters in `ConfigureFilterDialog` across all supported `FilterType` options SHALL NOT be restricted.

---

## 3. Detailed Test Specification

### `TST-UI-152`: Cockpit Sensor Field Domain-Specific Default Filter Presets Verification

#### 3.1 Centralized Registry Unit Tests (`SensorFilterDefaultsTest.kt`)
1. **Specific Domain Presets**:
   - `getDefaultFilterConfig(SensorType.POWER)` -> `FilterType.MOVING_AVERAGE_TIME`, `3.0`, `"sec"`.
   - `getDefaultFilterConfig(SensorType.PACE_spm)` -> `FilterType.MOVING_AVERAGE_TIME`, `5.0`, `"sec"`.
   - `getDefaultFilterConfig(SensorType.VERTICAL_SPEED)` -> `FilterType.MOVING_AVERAGE_TIME`, `15.0`, `"sec"`.
   - `getDefaultFilterConfig(SensorType.SLOPE)` -> `FilterType.MOVING_AVERAGE_TIME`, `5.0`, `"sec"`.
2. **Accumulator Protection**:
   - `SensorType.DISTANCE_m`, `TIME_ACTIVE`, `CALORIES`, `LAP_NR`, `DISTANCE_m_LAP`, `TIME_LAP`, `TIME_TOTAL`, `PHONE_BATTERY`, `BATTERY_REMAINING_TIME` -> `FilterType.INSTANTANEOUS`, `1.0`.
3. **Other Continuous Metrics**:
   - `SensorType.HR`, `SPEED_mps`, `CADENCE`, `ALTITUDE`, `TEMPERATURE` -> `FilterType.INSTANTANEOUS`, `1.0`.
4. **Exhaustive Null & Safety Coverage**:
   - For every `type` in `SensorType.values()`, assert `getDefaultFilterConfig(type) != null`, `filterType != null`, and `filterConstant > 0.0`.
   - Assert `getDefaultFilterConfig(null)` returns `INSTANTANEOUS`, `1.0`.
5. **Static Helpers & Extension Property**:
   - Verify `getDefaultFilterType`, `getDefaultFilterConstant`, and `getDefaultUnit`.
   - Verify `SensorType.getDefaultFilterConfig()` extension.

#### 3.2 ViewModel State Integration Unit Tests (`EditSensorFieldViewModelTest.kt`)
1. **Pace Selection**:
   - Call `viewModel.onSensorTypeChanged(SensorType.PACE_spm)`.
   - Assert `state.selectedFilterType == FilterType.MOVING_AVERAGE_TIME`.
   - Assert `state.filterConstant == 5.0`.
   - Assert `state.movingAverageUnit == "sec"`.
2. **VAM Selection**:
   - Call `viewModel.onSensorTypeChanged(SensorType.VERTICAL_SPEED)`.
   - Assert `state.selectedFilterType == FilterType.MOVING_AVERAGE_TIME`.
   - Assert `state.filterConstant == 15.0`.
3. **Slope Selection**:
   - Call `viewModel.onSensorTypeChanged(SensorType.SLOPE)`.
   - Assert `state.selectedFilterType == FilterType.MOVING_AVERAGE_TIME`.
   - Assert `state.filterConstant == 5.0`.
4. **Power Selection**:
   - Call `viewModel.onSensorTypeChanged(SensorType.POWER)`.
   - Assert `state.selectedFilterType == FilterType.MOVING_AVERAGE_TIME`.
   - Assert `state.filterConstant == 3.0`.
5. **Accumulator Selection**:
   - Call `viewModel.onSensorTypeChanged(SensorType.DISTANCE_m)`.
   - Assert `state.selectedFilterType == FilterType.INSTANTANEOUS`.
   - Assert `state.filterConstant == 1.0`.

#### 3.3 Clean-Room Full Suite Regression
- Execute `./gradlew testDebugUnitTest` across all modules to guarantee 100% test pass rate with zero regressions.

---

## 4. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Pace Preset)**:
  - *Given* an athlete configuring a sensor tile in `EditSensorFieldDialog`,
  - *When* selecting `SensorType.PACE_spm` in the sensor dropdown,
  - *Then* `selectedFilterType` SHALL automatically initialize to `FilterType.MOVING_AVERAGE_TIME` with `filterConstant = 5.0` (5s moving average).
* **Criterion 2 (VAM Preset)**:
  - *Given* an athlete selecting `SensorType.VERTICAL_SPEED` (VAM),
  - *When* the sensor is selected,
  - *Then* `selectedFilterType` SHALL automatically initialize to `FilterType.MOVING_AVERAGE_TIME` with `filterConstant = 15.0`.
* **Criterion 3 (Slope Preset)**:
  - *Given* an athlete selecting `SensorType.SLOPE`,
  - *When* selected,
  - *Then* `selectedFilterType` SHALL initialize to `FilterType.MOVING_AVERAGE_TIME` with `filterConstant = 5.0`.
* **Criterion 4 (Accumulator Protection)**:
  - *Given* an athlete selecting an accumulator sensor (`DISTANCE_m`, `TIME_ACTIVE`, `CALORIES`, `LAP_NR`),
  - *When* selected,
  - *Then* `selectedFilterType` SHALL strictly initialize to `FilterType.INSTANTANEOUS` with `filterConstant = 1.0`.
* **Criterion 5 (Database Seeding)**:
  - *Given* default tracking views created in `TrackingViewsDatabaseManager`,
  - *When* views are seeded for `ActivityType.RUN`,
  - *Then* the `PACE_spm` row SHALL be persisted with `FILTER_TYPE = MOVING_AVERAGE_TIME` and `FILTER_CONSTANT = 5.0`.
