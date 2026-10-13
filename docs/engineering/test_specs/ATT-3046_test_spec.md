# Stage 2 Requirement & Test Specification: ATT-3046 - Display elevation profile in workout details for trackless workouts with altitude data

**Ticket**: [ATT-3046](https://atrainingtracker.atlassian.net/browse/ATT-3046)  
**Sub-task**: [ATT-3068](https://atrainingtracker.atlassian.net/browse/ATT-3068) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68) (*Improve WorkoutSummaries*)  
**Target Release**: Unassigned (`None`) per Rule 19 (Lösungsversion assigned only when finished)  
**Active Sprint**: `Sprint 2026-41.7`  
**Branch**: `feature/ATT-3046`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Formal Requirement Specification

### REQ-UI-332: Workout Details Elevation Profile Display & Time-Domain Telemetry Ingestion for Trackless Workouts with Altitude Data

* **Requirement ID**: `REQ-UI-332`
* **Parent Epic**: `ATT-68` (*Improve WorkoutSummaries*)
* **Traceable Specification**:
  The system SHALL extract altitude telemetry for trackless workouts and display the elevation profile chart plotted against the Time domain in the workout details aftermath screen (`TrackOnMapScreen.kt`) when altitude data is present (ATT-3046, amending `REQ-UI-235`):
  1. *Decoupled Visibility Gating (`TrackOnMapScreen.kt`)*:
     • In `TrackOnMapScreen.kt`, the system SHALL compute:
       `val hasTracklessAltitude = !hasGpsTrack && (activeScrubPath?.any { it.altitude != 0.0 } == true)`
       `val hasAltitudeData = workoutData.minAltitude != null || (activeScrubPath?.any { it.altitude != 0.0 } == true)`
     • `showElevationProfile` SHALL be evaluated as:
       `showElevationProfile = (hasGpsTrack || hasTracklessAltitude) && activeDetailPrefs.showElevationProfile && isElevationPostMap && hasAltitudeData`
     • For trackless workouts (`hasGpsTrack == false`), when `activeScrubPath` contains non-zero altitude samples, `showElevationProfile` SHALL resolve to `true`, enabling `MapDetailLayout` to render the elevation profile chart plotted along the Time domain (`ProfileXAxisDomain.TIME`).
     • When neither GPS coordinates nor non-zero altitude telemetry are present, `showElevationProfile` SHALL resolve to `false`, cleanly suppressing the chart card without blank space artifacts.
  2. *Active Scrub Path Resolution for Trackless Sessions (`TrackOnMapScreen.kt`)*:
     • In `TrackOnMapScreen.kt`, `activeScrubPath` SHALL resolve to:
       `telemetryPath.ifEmpty { tracks.firstOrNull { it.path.isNotEmpty() }?.path }`
       when `hasGpsTrack == false`, ensuring altitude samples from both telemetry paths and trackless map tracks are ingested for scrubbing and chart rendering.
  3. *Altitude Telemetry Ingestion (`WorkoutRepository.kt`)*:
     • In `WorkoutRepository.getWorkoutTelemetryPoints(workoutId: Long)`, the system SHALL query `SensorType.ALTITUDE.name` from `WorkoutSamples.db`.
     • When constructing `PathPoint` instances, `altitude` SHALL be populated with the recorded sample altitude (`cursor.getDouble(altIdx)`).
     • The inclusion filter SHALL accept samples having valid altitude (`alt != 0.0`), ensuring sessions recording barometric altitude without heart rate, power, or speed are captured into `telemetryPath`.
  4. *Preservation of System Invariants*:
     • Standard outdoor GPS workouts continue to render distance-domain elevation profiles and maps with spatial scrubbing (`REQ-UI-201`, `REQ-UI-206`, `REQ-UI-235`).
     • User detail preferences (`activeDetailPrefs.showElevationProfile`), dynamic section ordering (`isElevationPostMap`), and 100% full clean-room unit test pass rate MUST be strictly preserved.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Amends `REQ-UI-235` (*Aftermath/Telemetry: Continuous Sensor Telemetry Graph Ingestion, Time-Domain Scrubbing, and Map Collapse for Trackless Workouts*, Sprint 2026-40.10, ATT-2006).
2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-40.10 (`ATT-2006`, commit `b9d73a723c`).
3. *Root Reason for Existing Formulation*: `TrackOnMapScreen` originally assumed trackless workouts were indoor treadmill/trainer activities without barometric sensors, hard-gating `showElevationProfile` behind `hasGpsTrack`. Subsequent architectural upgrades in `MapDetailLayout` (`ATT-2016`, `ATT-2311`) added time-domain elevation support, but `TrackOnMapScreen`'s visibility gate was never decoupled.
4. *Preservation of Core Invariants*: Standard GPS workouts, zero-elevation suppression, detail preferences, and 100% unit test pass rate remain strictly preserved.

---

## 2. Formal Test Specification

### TST-UI-292: Workout Details Elevation Profile Display & Telemetry Ingestion Verification for Trackless Workouts

* **Test ID**: `TST-UI-292`
* **Traceable Requirement**: `REQ-UI-332`
* **Target Test Classes**:
  - `com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryTelemetryTest`
  - `com.atrainingtracker.trainingtracker.ui.map.TracklessAftermathVisualContractTest`
  - `com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreenDetailPreferencesContractTest`

### Test Verification Cases
1. *WorkoutRepository Telemetry Altitude Ingestion Tests (`WorkoutRepositoryTelemetryTest.kt`)*:
   • Verify `getWorkoutTelemetryPoints` queries `SensorType.ALTITUDE.name` from the samples table.
   • Verify `PathPoint.altitude` is populated with the recorded sample altitude.
   • Verify rows containing non-zero altitude without HR, power, or speed are extracted into `telemetryPath`.
2. *TrackOnMapScreen Decoupled Visibility Contract Tests (`TracklessAftermathVisualContractTest.kt`, `TrackOnMapScreenDetailPreferencesContractTest.kt`)*:
   • Verify `TrackOnMapScreen.kt` evaluates `hasTracklessAltitude` and decouples `showElevationProfile` from `hasGpsTrack`.
   • Verify `showElevationProfile` evaluates to `true` when `hasGpsTrack == false` and `hasTracklessAltitude == true`.
   • Verify `showElevationProfile` remains conditioned on `activeDetailPrefs.showElevationProfile` and `isElevationPostMap`.
   • Verify `activeScrubPath` falls back to `tracks.firstOrNull { it.path.isNotEmpty() }?.path` when `telemetryPath` is empty.
3. *Clean-Room Full Suite Regression Execution*:
   • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across the full test suite.

---

## 3. Given-When-Then Acceptance Criteria

* **Criterion 1 (Trackless Workout with Altitude Telemetry)**:
  * *Given* an athlete viewing a trackless workout recorded with altitude telemetry samples (e.g. indoor barometric session or imported TCX file with altitude),
  * *When* opening the workout details screen (`TrackOnMapScreen`),
  * *Then* `showElevationProfile` evaluates to `true`, and the elevation profile chart is rendered below the altitude statistics table plotted along the Time domain.

* **Criterion 2 (Trackless Workout without Altitude Data)**:
  * *Given* a trackless workout without altitude data (all altitude values 0.0 or null),
  * *When* opening the workout details screen,
  * *Then* `showElevationProfile` evaluates to `false`, and no blank space or empty elevation chart is displayed.

* **Criterion 3 (Outdoor GPS Workouts Unchanged)**:
  * *Given* an outdoor workout with valid GPS track coordinates and altitude data,
  * *When* opening the workout details screen,
  * *Then* the elevation profile continues to render along the Distance domain, and the map remains visible.

* **Criterion 4 (Interactive Scrubbing on Trackless Elevation)**:
  * *Given* an athlete interacting with the elevation profile of a trackless workout with altitude data,
  * *When* scrubbing across the profile,
  * *Then* the scrubber indicator smoothly tracks elapsed time and displays the corresponding altitude value.

---

## 4. Traceability Matrix

| Requirement ID | Test Case ID | Test Class / Method | Verification Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-332` (Clause 1) | `TST-UI-292.1` | `TracklessAftermathVisualContractTest.kt` | Specified |
| `REQ-UI-332` (Clause 1) | `TST-UI-292.2` | `TrackOnMapScreenDetailPreferencesContractTest.kt` | Specified |
| `REQ-UI-332` (Clause 2) | `TST-UI-292.3` | `TracklessAftermathVisualContractTest.kt` | Specified |
| `REQ-UI-332` (Clause 3) | `TST-UI-292.4` | `WorkoutRepositoryTelemetryTest.kt` | Specified |
| `REQ-UI-332` (Clause 4) | `TST-UI-292.5` | `./gradlew testDebugUnitTest` | Specified |
