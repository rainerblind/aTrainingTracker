# Stage 2: Requirement & Test Specification - ATT-1391: Aftermath: Synchronized Multi-Metric Scrubbing on Elevation Profile

**Ticket**: [ATT-1391](https://atrainingtracker.atlassian.net/browse/ATT-1391)  
**Sub-task**: [ATT-1705](https://atrainingtracker.atlassian.net/browse/ATT-1705) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Requirement Mapping**: `REQ-UI-201` (*Aftermath: Synchronized Multi-Metric Scrubbing on Elevation Profile with Configurable X-Axis Domain Architecture*)  
**Test Spec ID**: `TST-UI-155`  
**Branch**: `feature/ATT-1391`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Requirement Specification (REQ-UI-201)

### 1.1 Problem Statement & Rationale
Currently, interactive scrubbing on the elevation profile in the Aftermath inspection view (`MapDetailLayout.kt` / `ElevationProfile.kt`) only displays distance and altitude in a plain text label and highlights a dot on the Google Map. Athletes cannot correlate terrain topography (climbs, descents, pitch transitions) with physiological response (heart rate) or mechanical effort (power, speed/pace). Furthermore, athletes analyzing interval or track workouts lack the ability to view the profile across elapsed time rather than distance.

### 1.2 Functional & Architectural Requirements
The system SHALL enhance the Aftermath inspection layout and elevation profile with synchronized multi-metric scrubbing, telemetry-enriched trackpoints, and a configurable X-axis domain:

1. **Enriched `PathPoint` Telemetry Model (`MapModels.kt`)**:
   - `PathPoint` SHALL encapsulate distance, latitude/longitude, altitude, and optional telemetry fields:
     ```kotlin
     data class PathPoint(
         val distance: Double,
         val latLng: LatLng,
         val altitude: Double,
         val timeSec: Long = 0L,
         val hr: Int? = null,
         val power: Int? = null,
         val speedMps: Double? = null,
         val slope: Double? = null
     )
     ```
   - Default arguments SHALL ensure 100% backward compatibility for all existing call sites.

2. **Full-Fidelity Telemetry Extraction (`WorkoutRepository.kt`)**:
   - `WorkoutRepository.getWorkoutTrackPoints(workoutId, trackType)` SHALL query the workout's samples database table and resolve column indices for `TIME_ACTIVE`, `TIME_TOTAL`, `HR`, `POWER`, `SPEED_mps`, and `SLOPE`.
   - When available, cursor data SHALL be extracted safely and mapped to `PathPoint`.
   - Cursor operations SHALL remain strictly confined to `Dispatchers.IO`.

3. **Configurable Profile X-Axis Domain Preference (`TuningPreferencesDataStore.kt` & `AdvancedTuningDialog.kt`)**:
   - The system SHALL define `enum class ProfileXAxisDomain { DISTANCE, TIME }`.
   - `TuningConfig` and `TuningPreferencesDataStore` SHALL manage `profileXAxisDomain` with default `ProfileXAxisDomain.DISTANCE`.
   - `AdvancedTuningDialog.kt` SHALL expose an Aftermath Tuning section allowing athletes to switch between *Distance* and *Elapsed Time*.
   - Factory reset (`resetToDefaults()`) SHALL restore the domain preference to `DISTANCE`.

4. **Adaptive Time Ticks & Viewport Scaling (`ElevationProfileZoomMath.kt`)**:
   - When `xAxisDomain == ProfileXAxisDomain.TIME`, the horizontal chart domain SHALL scale from $0 .. T_{\text{total}}$ seconds.
   - `ElevationProfileZoomMath.calculateAdaptiveTimeStep(visibleTimeSec: Double): Long` SHALL calculate readable tick intervals (e.g. 30s, 60s, 120s, 300s, 600s, 1800s, 3600s).
   - Time ticks SHALL format as `m:ss` or `h:mm:ss`.

5. **Synchronized Multi-Metric Telemetry Scrubbing & Floating Overlay (`ElevationProfile.kt` & `MapDetailLayout.kt`)**:
   - While scrubbing, `ElevationProfile` SHALL locate the nearest `PathPoint` along the active horizontal axis and invoke `onPointSelected: ((PathPoint?) -> Unit)?`.
   - `ElevationProfile` SHALL render a floating Compose telemetry card (`ScrubbingTelemetryBadge`):
     - Distance (`km`/`mi`) & Elapsed Time (`m:ss` or `h:mm:ss`).
     - Altitude (`m`/`ft`) & Slope (`%`).
     - Heart Rate (`bpm`).
     - Power (`W`).
     - Speed / Pace (`km/h` or `min/km` formatted per sport type).
   - Unrecorded sensors (`null`) SHALL be cleanly omitted without leaving empty gaps or placeholders.

6. **100% 9-Language Localization Parity**:
   - All preference strings and telemetry labels SHALL be defined across all 9 application locales (values, values-de, values-es, values-fr, values-it, values-ja, values-nl, values-pl, values-pt).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1: Configurable X-Axis Domain Preference**:
  * *Given* an athlete opens `Experten-Einstellungen` (`AdvancedTuningDialog`),
  * *When* selecting *Elapsed Time* (`Verstrichene Zeit`) for the Profile X-Axis domain,
  * *Then* the preference SHALL be persisted in DataStore, and the elevation profile chart in Aftermath SHALL plot altitude against elapsed time with time-based ticks (`m:ss` / `h:mm:ss`).
* **Criterion 2: Multi-Metric Telemetry Scrubbing**:
  * *Given* a workout recorded with Heart Rate, Power, and Speed sensors,
  * *When* the athlete drags their finger across the elevation profile in `MapDetailLayout`,
  * *Then* the floating telemetry badge SHALL display instantaneous Distance, Time, Altitude, Slope, HR (`bpm`), Power (`W`), and Speed/Pace synchronized to the scrubbed trackpoint.
* **Criterion 3: Graceful Absent Metric Fallback**:
  * *Given* a workout without Power meter telemetry (`power == null`),
  * *When* scrubbing along the elevation profile,
  * *Then* the Power metric SHALL be omitted cleanly from the badge without displaying `null` or 0W.
* **Criterion 4: Backward Compatibility**:
  * *Given* existing callers of `ElevationProfile` (such as list previews or routes),
  * *When* rendered without telemetry or with `showZoomControls = false`,
  * *Then* they SHALL render normally without regression.

### 1.4 System Invariants
- Viewport zooming mathematics (`ElevationProfileZoomMath`) centroid scaling and clamping invariants remain strictly intact.
- SQLite query execution remains strictly confined to `Dispatchers.IO`.
- No database schema migration or alter-table statements are introduced.
- Existing `onDistanceSelected: (Double?) -> Unit` callback is preserved.

---

## 2. Test Specification (TST-UI-155)

### Test Case 1: `PathPoint` Telemetry & Extraction Unit Tests (`[TST-UI-155.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/PathPointTelemetryTest.kt`
* **Preconditions**: `PathPoint` class with extended telemetry attributes and sample cursor rows.
* **Action**:
  1. Instantiate `PathPoint` with 3 arguments (legacy signature); assert default telemetry values (`timeSec == 0L`, `hr == null`, `power == null`, `speedMps == null`, `slope == null`).
  2. Instantiate `PathPoint` with full telemetry; assert all fields match.
* **Expected Result**: Complete backward compatibility and accurate telemetry encapsulation.

### Test Case 2: `ProfileXAxisDomain` Preference & DataStore Unit Tests (`[TST-UI-155.2]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/settings/ProfileXAxisDomainTest.kt`
* **Preconditions**: `TuningPreferencesDefaults`, `TuningConfig`, and mock `TuningPreferencesDataStore`.
* **Action**:
  1. Verify default domain is `ProfileXAxisDomain.DISTANCE`.
  2. Update domain to `ProfileXAxisDomain.TIME`; verify config serialization and deserialization.
  3. Verify factory reset reverts domain to `ProfileXAxisDomain.DISTANCE`.
* **Expected Result**: Clean DataStore persistence and atomic reset.

### Test Case 3: `ElevationProfileZoomMath` Adaptive Time Step Tests (`[TST-UI-155.3]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileZoomMathTimeTest.kt`
* **Preconditions**: `ElevationProfileZoomMath` object.
* **Action**:
  1. Test visible time spans: 120s (2 min), 600s (10 min), 3600s (1 hour), 14400s (4 hours).
  2. Verify returned time steps produce readable grid ticks (e.g. 30s, 60s, 300s, 1800s).
* **Expected Result**: Adaptive time step calculation generates pleasant, readable grid divisions.

### Test Case 4: Elevation Profile Telemetry Scrubbing & Graceful Fallback (`[TST-UI-155.4]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileScrubbingTest.kt`
* **Preconditions**: Track with multiple `PathPoint` items containing varying sensor presence.
* **Action**:
  1. Interpolate / resolve `PathPoint` at intermediate distance.
  2. Verify metrics are extracted accurately.
  3. Verify absent sensors return null.
* **Expected Result**: Accurate trackpoint matching and robust null handling.

### Test Case 5: 9-Language Localization & Specifier Audit (`[TST-UI-155.5]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/AftermathTuningLocalizationTest.kt`
* **Goal**: Verify string presence and matching `%s`/`%d` tokens across all 9 locales:
  * `tuning_cat_aftermath`
  * `tuning_profile_x_axis_title`
  * `tuning_profile_x_axis_desc`
  * `tuning_profile_x_axis_distance`
  * `tuning_profile_x_axis_time`
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 6: Clean-Room Full Suite Regression Execution (`[TST-UI-155.6]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-155.1]` | Unit | `PathPoint` constructor & defaults | `REQ-UI-201` | Specified |
| `[TST-UI-155.2]` | Unit | `TuningPreferencesDataStore` & `TuningConfig` | `REQ-UI-201` | Specified |
| `[TST-UI-155.3]` | Unit | `ElevationProfileZoomMath.calculateAdaptiveTimeStep` | `REQ-UI-201`, `REQ-UI-192` | Specified |
| `[TST-UI-155.4]` | Unit | `ElevationProfile` telemetry resolution | `REQ-UI-201` | Specified |
| `[TST-UI-155.5]` | Localization | `AftermathTuningLocalizationTest` | `REQ-UI-201`, `REQ-UI-106` | Specified |
| `[TST-UI-155.6]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
