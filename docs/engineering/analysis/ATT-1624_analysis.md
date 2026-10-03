# Stage 1 Analysis: ATT-1624

**Ticket**: [ATT-1624](https://atrainingtracker.atlassian.net/browse/ATT-1624)  
**Sub-task**: [ATT-1684](https://atrainingtracker.atlassian.net/browse/ATT-1684) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1624`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Executive Summary & Problem Domain

### 1.1 Context
In `aTrainingTracker`, cockpit display tiles (`SensorFieldView.kt`) allow athletes to visualize live sensor metrics with customizable smoothing filters (`FilterType`: Instantaneous, Moving Average Time, Moving Average Number, Exponential Smoothing, Session Average, Session Max). When an athlete adds a new sensor field or modifies an existing tile's sensor type in `EditSensorFieldDialog` (`EditSensorFieldViewModel.kt`), the system assigns a default filter configuration. Furthermore, when new default tracking views are initialized in `TrackingViewsDatabaseManager.java`, initial rows are seeded with default filter settings.

### 1.2 Problem Statement & Athletic Impact
Currently, both `EditSensorFieldViewModel.kt` and `TrackingViewsDatabaseManager.java` contain hardcoded binary branching that only recognizes `SensorType.POWER` for non-instantaneous default smoothing:
```kotlin
val defaultFilterType = if (newSensorType == SensorType.POWER) FilterType.MOVING_AVERAGE_TIME else FilterType.INSTANTANEOUS
val defaultConstant = if (newSensorType == SensorType.POWER) 3.0 else 1.0
```
For all other sensor types, the system defaults unconditionally to `FilterType.INSTANTANEOUS` (1.0).

This creates significant athletic UX deficiencies:
1. **Pace (`PACE_spm`)**: Instantaneous GPS pace fluctuates erratically second-by-second due to stride dynamics, multi-path reflections, and discrete coordinate updates. Raw instantaneous pace readouts swing wildly (e.g., oscillating between 4:15 min/km and 5:45 min/km during steady running), rendering the metric unusable without manual configuration. An industry-standard 5-second moving average provides immediately stable, responsive pacing.
2. **Vertical Speed / VAM (`VERTICAL_SPEED`)**: Vertical ascent rate ($dh/dt$) computed from barometric pressure or GPS altitude suffers from substantial instantaneous quantization noise. At 1Hz sampling, a minute 10 cm pressure fluctuation translates to an instantaneous vertical speed fluctuation of $\pm 360\text{ m/h}$. A 15-second moving average is essential for climbers to read meaningful, stabilized VAM.
3. **Slope / Incline (`SLOPE`)**: Grade calculation ($dh/dx$) divides altitude change by horizontal distance change. Instantaneous measurements at low speeds or brief stops result in division-by-small-delta spikes ($\pm 25\%$). A 5-second moving average provides smooth, realistic slope readouts (e.g., 7%).
4. **Hardcoded Anti-Pattern**: The scattered `if (sensorType == SensorType.POWER)` checks across `init`, `onSensorTypeChanged`, and `onFilterConfigDismissed` in `EditSensorFieldViewModel.kt`, as well as in `TrackingViewsDatabaseManager.java`, violate DRY principles and make adding or refining sensor filter defaults fragile.

Conversely, discrete counters and accumulator metrics (`DISTANCE_m`, `TIME_ACTIVE`, `CALORIES`, `LAP_NR`, `DISTANCE_m_LAP`, etc.) must strictly remain `INSTANTANEOUS` (constant 1.0), as smoothing cumulative values distorts total session and split tracking.

---

## 2. Chesterton's Fence & Requirement Archaeology

### 2.1 Archaeological Investigation
* **Requirement Origin**: `REQ-UI-167` (*Intuitive Smoothing Presets, Simplified Filter Configuration & Smart Athletic Defaults*), validated by `TST-UI-119`.
* **Historical Implementation**: Ticket `ATT-1276` (Sprint `2026-40.4`).
* **Design Rationale at Creation**: Prior to `ATT-1276`, all sensors including cycling power defaulted to raw instantaneous 1-second values. `ATT-1276` introduced the Compose `ConfigureFilterDialog`, 1-tap quick presets, and introduced the first smart athletic default: 3-second moving average for `SensorType.POWER`, because 1s power was notoriously noisy due to pedal stroke dead-spots.
* **Why Only Power was Handled**: Power was the most egregious complaint from cyclists using smart trainers and power meters. The requirement scoped power smoothing specifically, leaving other sensors at `INSTANTANEOUS`.
* **Chesterton's Fence Evaluation**:
  - The fence was built to address erratic 1s cycling power while keeping other sensors unchanged as a conservative baseline.
  - Expanding domain-optimized defaults to Pace (5s), VAM (15s), and Slope (5s) directly aligns with the athletic intent of `REQ-UI-167` without violating any invariants.
  - Existing user configurations already saved in SQLite (`ROWS_TABLE`) must remain untouched; defaults only apply to newly added tiles, sensor type changes, or default database tab generation.

---

## 3. Proposed Architectural Solution

### 3.1 Centralized Sensor Filter Defaults (`SensorFilterDefaults.kt`)
Establish a single source of truth in package `com.atrainingtracker.banalservice.filters`:
```kotlin
package com.atrainingtracker.banalservice.filters

import com.atrainingtracker.banalservice.sensor.SensorType

data class DefaultFilterConfig(
    val filterType: FilterType,
    val filterConstant: Double,
    val unit: String = "sec"
)

object SensorFilterDefaults {

    @JvmStatic
    fun getDefaultFilterConfig(sensorType: SensorType?): DefaultFilterConfig {
        if (sensorType == null) {
            return DefaultFilterConfig(FilterType.INSTANTANEOUS, 1.0, "sec")
        }
        return when (sensorType) {
            SensorType.POWER -> DefaultFilterConfig(FilterType.MOVING_AVERAGE_TIME, 3.0, "sec")
            SensorType.PACE_spm -> DefaultFilterConfig(FilterType.MOVING_AVERAGE_TIME, 5.0, "sec")
            SensorType.VERTICAL_SPEED -> DefaultFilterConfig(FilterType.MOVING_AVERAGE_TIME, 15.0, "sec")
            SensorType.SLOPE -> DefaultFilterConfig(FilterType.MOVING_AVERAGE_TIME, 5.0, "sec")
            else -> DefaultFilterConfig(FilterType.INSTANTANEOUS, 1.0, "sec")
        }
    }

    @JvmStatic
    fun getDefaultFilterType(sensorType: SensorType?): FilterType =
        getDefaultFilterConfig(sensorType).filterType

    @JvmStatic
    fun getDefaultFilterConstant(sensorType: SensorType?): Double =
        getDefaultFilterConfig(sensorType).filterConstant

    @JvmStatic
    fun getDefaultUnit(sensorType: SensorType?): String =
        getDefaultFilterConfig(sensorType).unit
}

fun SensorType.getDefaultFilterConfig(): DefaultFilterConfig =
    SensorFilterDefaults.getDefaultFilterConfig(this)
```

### 3.2 Refactoring `EditSensorFieldViewModel.kt`
Replace all occurrences of `if (sensorType == SensorType.POWER)` with calls to `SensorFilterDefaults.getDefaultFilterConfig(sensorType)`:
1. `init` block (fallback/default initialization).
2. `onSensorTypeChanged(newSensorType)` (when user changes the sensor type in the dropdown).
3. `onFilterConfigDismissed()` (when restoring defaults on sensor change).

### 3.3 Database Seeding Alignment (`TrackingViewsDatabaseManager.java`)
In `TrackingViewsDatabaseManager.addDefaultTab(...)`:
Replace:
```java
if (rowData.sensorType == SensorType.POWER) {
    values.put(FILTER_TYPE, FilterType.MOVING_AVERAGE_TIME.name());
    values.put(FILTER_CONSTANT, 3);
} else {
    values.put(FILTER_TYPE, FilterType.INSTANTANEOUS.name());
    values.put(FILTER_CONSTANT, 1);
}
```
with:
```java
DefaultFilterConfig filterConfig = SensorFilterDefaults.getDefaultFilterConfig(rowData.sensorType);
values.put(FILTER_TYPE, filterConfig.getFilterType().name());
values.put(FILTER_CONSTANT, filterConfig.getFilterConstant());
```
This ensures default seeded cockpit views (e.g. Run default view which includes `PACE_spm`) automatically start with 5-second smoothed pace instead of raw jitter.

---

## 4. Scope Bounding

### 4.1 In Scope
1. Creation of `com.atrainingtracker.banalservice.filters.SensorFilterDefaults.kt` with `DefaultFilterConfig` and `@JvmStatic` helper methods.
2. Refactoring `EditSensorFieldViewModel.kt` to consume `SensorFilterDefaults`.
3. Updating `TrackingViewsDatabaseManager.java` default row generation to use `SensorFilterDefaults`.
4. Comprehensive unit test coverage in `SensorFilterDefaultsTest.kt` and `EditSensorFieldViewModelTest.kt`.
5. Living documentation updates (`docs/requirements.md`, `docs/tests.md`).

### 4.2 Out of Scope
1. Modifying SQLite schema version or table structures in `TrackingViewsDatabaseManager.java` (no database migration needed; existing user rows remain immutable).
2. Modifying underlying filter mathematical execution in `BANALService` (`TimedMovingAverageFilter.java`, `InstantaneousFilter.java`).
3. Modifying `FilterPreset` enum options in `ConfigureFilterDialog.kt` (quick presets direct, 3s, 10s, 30s remain intact).

---

## 5. Invariants & Risk Assessment

| Risk / Invariant | Impact | Mitigation Strategy |
|:---|:---|:---|
| **Accumulator Distortion** | High | Accumulators (`DISTANCE_m`, `TIME_ACTIVE`, `CALORIES`, `LAP_NR`, etc.) strictly map to `FilterType.INSTANTANEOUS` (1.0). Unit tests verify all accumulator sensors return `INSTANTANEOUS`. |
| **User Customization Immutability** | High | Existing saved views in `TrackingViewsDatabaseManager.ROWS_TABLE` are never overwritten. Defaults only apply during initial tab generation or when the user explicitly selects a new sensor type in the editor dialog. |
| **Java / Kotlin Interoperability** | Medium | `SensorFilterDefaults` exposes `@JvmStatic` static methods so Java callers (`TrackingViewsDatabaseManager`) can seamlessly access typed defaults without reflection or Kotlin runtime boilerplate. |
| **No Database Migration Needed** | Low | New default rows write standard `FILTER_TYPE` and `FILTER_CONSTANT` columns already present since database version 6. |
