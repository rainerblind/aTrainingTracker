# Stage 1: Problem Domain & Root Cause Analysis - ATT-3310: Configure default sensor data filtering on initial install

**Ticket**: [ATT-3310](https://rainerblind.atlassian.net/browse/ATT-3310)  
**Sub-task**: [ATT-3378](https://rainerblind.atlassian.net/browse/ATT-3378) (`[Analysis]`)  
**Parent Epic**: [ATT-2561](https://rainerblind.atlassian.net/browse/ATT-2561) (*Sensors: BLE / ANT+ Connectivity, Calibration & Telemetry*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-198` (Amending Clause 1)  
**Test Mapping**: `TST-UI-152` (Amending Clause 1 & 2)  
**Branch**: `feature/ATT-3310`  
**Author**: AI Agent 1 (Forensic & Systems Analyst)  
**Date**: 2026-10-13  

---

## 1. Problem Statement & User Impact

When athletes perform a clean installation of the application or configure new sensor fields in their cockpit tracking views, sensor fields for **Speed** (`SensorType.SPEED_mps`) and **Cadence** (`SensorType.CADENCE`) currently default to instantaneous (raw) telemetry (`FilterType.INSTANTANEOUS`, constant `1.0s`).

In real-world athletic conditions:
- **Instantaneous Speed**: Subject to 1 Hz GPS satellite quantization noise, multipath distortion under tree canopies, and minor BLE wheel revolution timestamp jitter, causing displayed speed to fluctuate noticeably (e.g. oscillating between 27.2 km/h and 29.8 km/h while riding at a perfectly steady cadence and effort).
- **Instantaneous Cadence**: Crank-based cadence sensors and pedal power meters emit discrete crank revolution events. Slight variations in stroke timing or momentary freewheeling produce rapid cadence display flicker.

While power already defaults to 3-second moving average (`FilterType.MOVING_AVERAGE_TIME, 3.0s`) and pace to 5-second moving average (`5.0s`), speed and cadence were left unbuffered. Athletes had to manually open field settings and navigate into the filter configuration dialog for each field to configure smoothing.

Configuring a gentle **2-second moving average** (`FilterType.MOVING_AVERAGE_TIME, 2.0s`) out-of-the-box for Speed and Cadence stabilizes cockpit readouts, eliminates distracting micro-jitter, and preserves near-instantaneous responsiveness during accelerations or braking, without requiring manual athlete intervention.

---

## 2. Root Cause Analysis & Architectural Trace

### 2.1 Centralized Preset Definition: `SensorFilterDefaults.kt`
In `app/src/main/java/com/atrainingtracker/banalservice/filters/SensorFilterDefaults.kt`:
```kotlin
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
```
`SPEED_mps` and `CADENCE` fall through to the `else` branch, returning `INSTANTANEOUS, 1.0s`.

### 2.2 Consumers of `SensorFilterDefaults`
1. **Initial Installation / View Seeding (`TrackingViewsDatabaseManager.java`)**:
   - In `addDefaultTab(...)` (invoked during `onCreate()` for fresh databases), line 894:
     ```java
     DefaultFilterConfig filterConfig = SensorFilterDefaults.getDefaultFilterConfig(rowData.sensorType);
     values.put(FILTER_TYPE, filterConfig.getFilterType().name());
     values.put(FILTER_CONSTANT, filterConfig.getFilterConstant());
     db.insert(ROWS_TABLE, null, values);
     ```
   - New database installations automatically inherit `SensorFilterDefaults`.
   - **Crucial Architectural Invariant**: Existing databases (`onUpgrade`) must **not** run an automated migration rewriting existing user rows. Existing users' custom filter preferences must remain completely untouched.
2. **Field Editing (`EditSensorFieldViewModel.kt`)**:
   - In `init`, `onSensorTypeChanged`, and `onFilterConfigDismissed`, queries `SensorFilterDefaults.getDefaultFilterConfig(sensorType)`.
   - Adding a new speed or cadence field or changing a field's sensor type to speed/cadence will immediately prefill with the 2-second moving average.
3. **Heart Rate Metric Protection**:
   - `SensorType.HR` MUST remain instantaneous (`INSTANTANEOUS, 1.0s`) because athletes need immediate detection of cardiac spikes, threshold transitions, and recovery kinetics.

---

## 3. Chesterton's Fence Archaeology (`REQ-PRO-022`)

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-UI-198` (*Cockpit Sensor Field Domain-Specific Default Filter Presets Architecture*), targeting `SensorFilterDefaults.kt`, `TrackingViewsDatabaseManager.java`, and `EditSensorFieldViewModel.kt`.
2. **Historical Origin & Commit Trace**:
   - Ticket `ATT-1624` (Sprint `2026-40.10`), which established centralized domain-specific presets for Power (3s), Pace (5s), Vertical Speed (15s), and Slope (5s), while leaving other continuous metrics in the `else` block (`INSTANTANEOUS`).
3. **Root Reason for Existing Formulation**:
   - In ATT-1624, the primary focus was dampening extreme high-variance metrics (power dead-spots and 1Hz barometric VAM/slope noise). Speed and cadence were left raw under the assumption that bike speed and cadence sensors were sufficiently steady. In practice, GPS-derived speed and discrete reed switch cadence exhibit distracting display flutter on high-resolution screens.
4. **Preservation of Core Invariants**:
   - Heart Rate (`SensorType.HR`) remains strictly `INSTANTANEOUS, 1.0s`.
   - Accumulator and discrete counter metrics (`DISTANCE_m`, `TIME_ACTIVE`, `CALORIES`, `LAP_NR`, `BATTERY_*`) remain strictly `INSTANTANEOUS, 1.0s`.
   - Existing user database rows are NOT migrated or altered (zero schema migration).
   - User sovereignty is fully preserved: athletes can freely modify or disable filtering in `ConfigureFilterDialog`.

---

## 4. User Scope Grounding (`ATT-1250`)

### In Scope
- Update `SensorFilterDefaults.kt`:
  - `SensorType.SPEED_mps -> DefaultFilterConfig(FilterType.MOVING_AVERAGE_TIME, 2.0, "sec")`
  - `SensorType.CADENCE -> DefaultFilterConfig(FilterType.MOVING_AVERAGE_TIME, 2.0, "sec")`
- Update unit tests in `SensorFilterDefaultsTest.kt` to verify `SPEED_mps` and `CADENCE` default to 2.0s moving average, while `HR` and accumulators remain instantaneous.
- Update living requirements (`REQ-UI-198`) and test specifications (`TST-UI-152`).

### Out of Scope
- No SQLite database schema bump or upgrade migration in `TrackingViewsDatabaseManager.java`.
- No modification of `FilterType.kt` mathematical calculation engines in `BANALService`.
- No alterations to `ConfigureFilterDialog.kt` UI layout.
