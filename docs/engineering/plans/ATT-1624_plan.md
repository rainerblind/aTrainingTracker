# Stage 3 Implementation Plan: ATT-1624

**Ticket**: [ATT-1624](https://atrainingtracker.atlassian.net/browse/ATT-1624)  
**Sub-task**: [ATT-1686](https://atrainingtracker.atlassian.net/browse/ATT-1686) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1624`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Traceability & Scope Matrix
* **Requirements Traced**: `REQ-UI-198` (*Cockpit Sensor Field Domain-Specific Default Filter Presets Architecture*)
* **Tests Traced**: `TST-UI-152` (*Cockpit Sensor Field Domain-Specific Default Filter Presets Verification*)
* **Scope Definition**: Implement centralized `SensorFilterDefaults.kt`, refactor `EditSensorFieldViewModel.kt` and `TrackingViewsDatabaseManager.java` to consume it, and author comprehensive unit tests in `SensorFilterDefaultsTest.kt` and `EditSensorFieldViewModelTest.kt`.

---

## 2. Step-by-Step Atomic Implementation Tasks

### Task 1: Implement `SensorFilterDefaults.kt`
- File: `app/src/main/java/com/atrainingtracker/banalservice/filters/SensorFilterDefaults.kt`
- Create `DefaultFilterConfig`:
  ```kotlin
  data class DefaultFilterConfig(
      val filterType: FilterType,
      val filterConstant: Double,
      val unit: String = "sec"
  )
  ```
- Create `object SensorFilterDefaults`:
  - `getDefaultFilterConfig(sensorType: SensorType?): DefaultFilterConfig`
    - `POWER`: `MOVING_AVERAGE_TIME`, `3.0`, `"sec"`
    - `PACE_spm`: `MOVING_AVERAGE_TIME`, `5.0`, `"sec"`
    - `VERTICAL_SPEED`: `MOVING_AVERAGE_TIME`, `15.0`, `"sec"`
    - `SLOPE`: `MOVING_AVERAGE_TIME`, `5.0`, `"sec"`
    - Accumulators (`DISTANCE_m`, `TIME_ACTIVE`, `CALORIES`, `LAP_NR`, `DISTANCE_m_LAP`, `TIME_LAP`, `TIME_TOTAL`, `PHONE_BATTERY`, `BATTERY_REMAINING_TIME`): strictly `INSTANTANEOUS`, `1.0`, `"sec"`
    - Other continuous metrics: `INSTANTANEOUS`, `1.0`, `"sec"`
    - Null / unknown: `INSTANTANEOUS`, `1.0`, `"sec"`
  - `@JvmStatic fun getDefaultFilterType(sensorType: SensorType?): FilterType`
  - `@JvmStatic fun getDefaultFilterConstant(sensorType: SensorType?): Double`
  - `@JvmStatic fun getDefaultUnit(sensorType: SensorType?): String`
  - Top-level extension: `fun SensorType.getDefaultFilterConfig(): DefaultFilterConfig`

### Task 2: Refactor `EditSensorFieldViewModel.kt`
- File: [EditSensorFieldViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldViewModel.kt)
- In `init`:
  - Replace lines 142-143:
    ```kotlin
    val defaultConfig = SensorFilterDefaults.getDefaultFilterConfig(defaultSensor)
    val defaultFilterType = defaultConfig.filterType
    val defaultConstant = defaultConfig.filterConstant
    val defaultUnit = defaultConfig.unit
    val initialPreset = resolveFilterPreset(defaultFilterType, defaultConstant, defaultUnit)
    ```
- In `onSensorTypeChanged(newSensorType)`:
  - Replace lines 219-220:
    ```kotlin
    val defaultConfig = SensorFilterDefaults.getDefaultFilterConfig(newSensorType)
    val defaultFilterType = defaultConfig.filterType
    val defaultConstant = defaultConfig.filterConstant
    val defaultUnit = defaultConfig.unit
    val initialPreset = resolveFilterPreset(defaultFilterType, defaultConstant, defaultUnit)
    ```
  - Update `filterSummary`, `selectedFilterType`, `filterConstant`, `movingAverageUnit`, and `isCustomFilterExpanded = (initialPreset == FilterPreset.CUSTOM)`.
- In `onFilterConfigDismissed()`:
  - Replace lines 422-423:
    ```kotlin
    val defaultConfig = SensorFilterDefaults.getDefaultFilterConfig(it.selectedSensorType)
    val defaultFilterType = defaultConfig.filterType
    val defaultConstant = defaultConfig.filterConstant
    val defaultUnit = defaultConfig.unit
    val initialPreset = resolveFilterPreset(defaultFilterType, defaultConstant, defaultUnit)
    ```

### Task 3: Refactor `TrackingViewsDatabaseManager.java` Default Seeding
- File: [TrackingViewsDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManager.java)
- In `addDefaultTab(...)` (around line 576):
  - Replace hardcoded `POWER` branching with:
    ```java
    DefaultFilterConfig filterConfig = SensorFilterDefaults.getDefaultFilterConfig(rowData.sensorType);
    values.put(FILTER_TYPE, filterConfig.getFilterType().name());
    values.put(FILTER_CONSTANT, filterConfig.getFilterConstant());
    ```

### Task 4: Author Unit Tests in `SensorFilterDefaultsTest.kt`
- File: `app/src/test/java/com/atrainingtracker/banalservice/filters/SensorFilterDefaultsTest.kt`
- Author comprehensive unit tests:
  1. `testSensorFilterDefaults_power_returns3sMovingAverage`
  2. `testSensorFilterDefaults_pace_returns5sMovingAverage`
  3. `testSensorFilterDefaults_verticalSpeed_returns15sMovingAverage`
  4. `testSensorFilterDefaults_slope_returns5sMovingAverage`
  5. `testSensorFilterDefaults_accumulators_returnInstantaneous1s`
  6. `testSensorFilterDefaults_otherContinuousMetrics_returnInstantaneous1s`
  7. `testSensorFilterDefaults_nullSensor_returnsInstantaneous1s`
  8. `testSensorFilterDefaults_exhaustiveCoverage_allSensorsReturnValidConfig`
  9. `testSensorFilterDefaults_staticHelpersAndExtension_functionIdentically`

### Task 5: Update Unit Tests in `ConfigureFilterDialogTest.kt` & `EditSensorFieldViewModelTest.kt`
- Files:
  - [ConfigureFilterDialogTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/ConfigureFilterDialogTest.kt)
  - [EditSensorFieldViewModelTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldViewModelTest.kt)
- Verify smart athletic defaults for Pace (5s), VAM (15s), Slope (5s), Power (3s), and accumulators (1s).

### Task 6: Verification & Clean-Room Regression
- Execute targeted unit tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.filters.SensorFilterDefaultsTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.*"
  ```
- Execute full regression test suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## 3. Invariant & Regression Guards
1. **Accumulator Protection**: Accumulators (`DISTANCE_m`, `TIME_ACTIVE`, `CALORIES`, `LAP_NR`, etc.) strictly map to `INSTANTANEOUS` (1.0).
2. **User Customization Immutability**: Existing saved views in `TrackingViewsDatabaseManager.ROWS_TABLE` are never overwritten; defaults only apply to new fields, sensor type changes, or default tab generation.
3. **Java Interoperability**: `@JvmStatic` static methods guarantee clean Java access without reflection.
4. **Zero Migration**: No SQLite schema bump required; existing database schemas remain 100% backward-compatible.
