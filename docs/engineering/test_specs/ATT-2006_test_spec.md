# Stage 2: Requirement & Test Specification - ATT-2006: Display Heart Rate Graph When HR Telemetry Exists Even Without GPS Track

**Ticket**: [ATT-2006](https://atrainingtracker.atlassian.net/browse/ATT-2006)  
**Sub-task**: [ATT-2061](https://atrainingtracker.atlassian.net/browse/ATT-2061) (`[Test-Spec]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.12`  
**Requirement Mapping**: `REQ-UI-235` (*Aftermath/Telemetry: Continuous Sensor Telemetry Graph Ingestion, Time-Domain Scrubbing, and Map Collapse for Trackless Workouts*)  
**Test Spec ID**: `TST-UI-194`  
**Branch**: `feature/ATT-2006`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Requirement Specification (`REQ-UI-235`)

### 1.1 Problem Statement & Rationale
Athletes frequently record training sessions without GPS coordinates (e.g. indoor treadmill runs, stationary cycling trainers, indoor rowing, or outdoor sessions where GPS fix was unavailable). While spatial map coordinates do not exist, these sessions contain rich sensor telemetry—specifically continuous Heart Rate and Cycling Power streams recorded at 1 Hz. 

Historically, `MapDetailLayout` and `TelemetryMetricGraph` coupled continuous graph rendering to the geospatial `activeScrubPath` from GPS tracks and wrapped all telemetry graphs inside `if (showElevationProfile)`. When GPS points were absent, `showElevationProfile` evaluated to `false`, completely suppressing the continuous Heart Rate curve even though the Heart Rate Zone card was fully populated. Simultaneously, `ATrainingTrackerMap` rendered an empty Google Map centered on (0.0, 0.0) off the coast of Africa.

`REQ-UI-235` decouples sensor telemetry ingestion and rendering from GPS coordinates and elevation profiles, renders continuous telemetry curves along the Time domain across workout duration, enables synchronized touch scrubbing with instantaneous metric readouts, and cleanly collapses the Google Map container when no GPS coordinates exist.

### 1.2 Functional & Architectural Requirements
The system SHALL extract, ingest, and render continuous sensor telemetry (Heart Rate and Cycling Power) along the Time domain across workout duration for trackless workouts (sessions without GPS coordinates), support interactive time-domain scrubbing, decouple telemetry graph rendering from elevation profiles, and automatically collapse the Google Map viewport (ATT-2006):

1. **Decoupled Telemetry Extraction (`WorkoutRepository`)**:
   - The system SHALL provide `suspend fun getWorkoutTelemetryPoints(workoutId: Long): List<PathPoint>` in `WorkoutRepository.kt`.
   - When querying `WorkoutSamples.db`, the method SHALL extract recorded samples having valid sensor data (Heart Rate `SensorType.HR` or Power `SensorType.POWER`), capturing `timeSec`, `hr`, `power`, and `speedMps`, with `distance = 0.0` and `latLng = LatLng(0.0, 0.0)`.
   - When sample count exceeds 800, the method SHALL apply uniform stride decimation preserving initial and terminal samples (`index == 0 || index == lastIndex`), guaranteeing high-performance 120fps rendering on physical hardware.

2. **ViewModel Telemetry Ingestion (`TrackOnMapAftermathViewModel`)**:
   - In `AftermathMapUIState`, the system SHALL expose `val telemetryPath: List<PathPoint> = emptyList()`.
   - In `loadAftermathData()`, when `fullTracks` is empty (no GPS tracks available), the ViewModel SHALL query `workoutRepository.getWorkoutTelemetryPoints(workoutId)` and populate `_uiState.value.copy(telemetryPath = telemetryPoints)`.

3. **TrackOnMapScreen Scrubber & Layout Wiring (`TrackOnMapScreen.kt`)**:
   - The screen SHALL evaluate `val hasGpsTrack = tracks.any { it.path.isNotEmpty() && it.latLngs.any { p -> p.latitude != 0.0 || p.longitude != 0.0 } }`.
   - `activeScrubPath` SHALL be bound to `if (hasGpsTrack) (bestTrack?.path) else aftermathUIState.telemetryPath.ifEmpty { null }`.
   - The screen SHALL forward `showMap = hasGpsTrack` and `showElevationProfile = hasGpsTrack && (workoutData.minAltitude != null || (activeScrubPath?.any { it.altitude != 0.0 } == true))` to `MapDetailLayout`.

4. **Decoupled Telemetry Rendering, Map Collapse & Full-Screen Layout (`MapDetailLayout.kt`)**:
   - `MapDetailLayout` SHALL evaluate `hasTelemetryGraphs = showZoomControls && activeScrubPath != null && (TelemetryMetricUtils.hasHeartRateData(activeScrubPath) || TelemetryMetricUtils.hasSpeedData(activeScrubPath) || TelemetryMetricUtils.hasPowerData(activeScrubPath))`.
   - In `lowerColumn`, the chart wrapper `Surface` SHALL be rendered if `showElevationProfile || hasTelemetryGraphs`.
   - `ElevationProfile` SHALL be rendered only if `showElevationProfile` is true.
   - `TelemetryMetricGraph` instances (Speed/Pace, Heart Rate, Power) SHALL be rendered whenever `showZoomControls && activeScrubPath != null` and the corresponding data exists, **independent of whether `showElevationProfile` is true or false**.
   - When `showMap == false`, `MapDetailLayout` SHALL completely suppress `ATrainingTrackerMap`, eliminating the default Null Island (0.0, 0.0) Atlantic Ocean map tile artifact.
   - When `showMap == false`, the outer layout SHALL occupy `Modifier.fillMaxSize()`, and the lower content column SHALL take `Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())`, ensuring all stacked graphs, zone cards, and analytics cards scroll smoothly.
   - When `activeScrubPath` contains zero distance (`(path.lastOrNull()?.distance ?: 0.0) == 0.0`) and valid duration (`timeSec > 0`), the layout SHALL enforce `ProfileXAxisDomain.TIME` for telemetry graphs.
   - When scrubbing (`selectedDistance != null`), the header row above each graph SHALL render the active metric value (e.g. `148 bpm • Z3`).

5. **Synchronized Time-Domain Scrubbing (`TelemetryMetricGraph.kt`)**:
   - In `TelemetryMetricGraph.kt`, when operating on trackless data in the Time domain (`(pathPoints.lastOrNull()?.distance ?: 0.0) == 0.0`), touch dragging and tapping SHALL dispatch `selectedVal` directly as elapsed time in seconds (`onDistanceSelected(nearest?.timeSec?.toDouble())`).
   - The scrubbing cursor and marker dot SHALL evaluate `cursorDistSpan = currentDistance` directly against `totalSpan` in seconds without round-tripping through distance, allowing smooth scrubbing across `[0L, totalDurationSec]`.

6. **Preservation of Core Invariants**:
   - Standard GPS workouts continue to render map, elevation profile, and telemetry graphs with spatial scrubbing (`REQ-UI-201`, `REQ-UI-206`, `REQ-UI-233`).
   - Zone distribution cards, histogram tabs, and lap visualizers remain 100% operational.
   - 100% 9-language localization parity across all strings.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-235`), extending `REQ-UI-206` under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.5 (`ATT-1740`) and Sprint 2026-40.10 (`ATT-1986`).
3. *Root Reason for Existing Formulation*: `TelemetryMetricGraph` previously assumed all workouts had GPS tracks and derived data and scrubbing strictly from GPS-bound `activeScrubPath` inside `if (showElevationProfile)`.
4. *Preservation of Core Invariants*: Standard GPS workouts, zoom toolbar, synchronized scrubbing, and 9-language localization parity are 100% strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Continuous HR Graph for Trackless Workout)**:
  - *Given* an athlete viewing a workout recorded with Heart Rate sensor data but zero GPS track points (e.g. treadmill run or trainer ride),
  - *When* the workout is opened in the Aftermath detail screen (`TrackOnMapScreen`),
  - *Then* the continuous Heart Rate curve SHALL be displayed along the Time domain across workout duration even when `showElevationProfile` is false.
* **Criterion 2 (Map Suppression / Collapse)**:
  - *Given* a trackless workout without GPS coordinates,
  - *When* viewed in `TrackOnMapScreen`,
  - *Then* the Google Map viewport SHALL be collapsed/hidden, with zero display of the Atlantic Ocean (0.0, 0.0) map tile.
* **Criterion 3 (Interactive Time-Domain Scrubbing)**:
  - *Given* an athlete touching and scrubbing across the continuous Heart Rate curve of a trackless workout,
  - *When* dragging across the graph from start to end,
  - *Then* the vertical cursor line and highlight dot SHALL follow the finger smoothly, and the header readout SHALL display the instantaneous heart rate and zone (e.g. `148 bpm • Z3`).
* **Criterion 4 (Full-Screen Fluid Scrolling)**:
  - *Given* a trackless workout,
  - *When* scrolling the Aftermath view,
  - *Then* the entire content—including Workout Header, Telemetry Zoom Toolbar, Heart Rate Graph, Heart Rate Zone Distribution Card, and splits—SHALL scroll smoothly in a single vertical scroll container without clipping.
* **Criterion 5 (GPS Workouts Invariance)**:
  - *Given* a workout with valid GPS track coordinates,
  - *When* viewed in `TrackOnMapScreen`,
  - *Then* the Google Map, SplitPaneDivider, Elevation Profile, and synchronized map marker scrubbing SHALL continue to operate identically to existing behavior.

---

## 2. Test Specification (`TST-UI-194`)

### Test Case 1: `WorkoutRepositoryTelemetryTest` (`[TST-UI-194.1]`)
* **Scope**: Unit / Repository Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepositoryTelemetryTest.kt`
* **Test Procedures**:
  - `testExtractTelemetryPoints_whenNoGpsCoordinates_returnsValidTimeDomainPoints`: Verifies that `WorkoutRepository.getWorkoutTelemetryPoints` extracts samples from SQLite database when latitude and longitude columns are null.
  - `testExtractTelemetryPoints_populatesHeartRateAndPower`: Asserts that `timeSec`, `hr`, and `power` are correctly extracted while `distance` is 0.0 and `latLng` is `(0.0, 0.0)`.
  - `testExtractTelemetryPoints_decimatesWhenOver800Points`: Verifies that datasets $> 800$ samples are downsampled, with first (`timeSec = 0`) and last (`timeSec = totalDurationSec`) points strictly preserved.

### Test Case 2: `TrackOnMapViewModelTelemetryTest` (`[TST-UI-194.2]`)
* **Scope**: ViewModel State Ingestion Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TrackOnMapViewModelTelemetryTest.kt`
* **Test Procedures**:
  - `testAftermathMapUIState_containsTelemetryPath`: Verifies `AftermathMapUIState` exposes `telemetryPath: List<PathPoint>`.
  - `testLoadAftermathData_whenTracksEmpty_populatesTelemetryPath`: Verifies that when `fullTracks` is empty, `telemetryPath` receives the extracted sensor telemetry points.

### Test Case 3: `TracklessAftermathVisualContractTest` (`[TST-UI-194.3]`)
* **Scope**: Visual & Structural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TracklessAftermathVisualContractTest.kt`
* **Test Procedures**:
  - `testTrackOnMapScreen_evaluatesHasGpsTrack_andSuppressesMap`: Verifies `TrackOnMapScreen.kt` checks for non-zero GPS coordinates and sets `showMap = false` when no GPS points exist.
  - `testMapDetailLayout_suppressesMap_andAppliesVerticalScroll`: Verifies that when `showMap == false`, `ATrainingTrackerMap` is omitted, and the content container applies `Modifier.fillMaxSize().verticalScroll(...)`.
  - `testMapDetailLayout_evaluatesHasTelemetryGraphs_fromActiveScrubPath`: Verifies `hasTelemetryGraphs` evaluates to `true` when `activeScrubPath` contains heart rate data without GPS.
  - `testMapDetailLayout_rendersTelemetryGraphs_evenWhenShowElevationProfileIsFalse`: Verifies that `TelemetryMetricGraph` (HR, Power, Speed) is rendered inside `lowerColumn` whenever `hasTelemetryGraphs == true` even if `showElevationProfile == false`.
  - `testMapDetailLayout_rendersScrubbingReadoutInHeader`: Verifies that instantaneous metric values (e.g. `148 bpm • Z3`) are rendered in the section heading row during active scrubbing.

### Test Case 4: `TelemetryMetricGraphTracklessScrubbingTest` (`[TST-UI-194.4]`)
* **Scope**: Composable Logic & Gesture Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraphTracklessScrubbingTest.kt`
* **Test Procedures**:
  - `testTracklessScrubbing_dispatchesTimeInSeconds`: Verifies that when `(pathPoints.lastOrNull()?.distance ?: 0.0) == 0.0`, dragging dispatches `nearest.timeSec` directly without falling back to 0.0.
  - `testTracklessCursor_evaluatesDirectlyAgainstTimeSpan`: Verifies that cursor position and marker dot calculate their canvas X coordinate from `currentDistance` directly as time seconds.

### Test Case 5: 9-Language Localization Audit (`[TST-UI-194.5]`)
* **Scope**: Static Resource Audit
* **Target Directories**: `app/src/main/res/values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
* **Verified String Resources**:
  - `graph_heading_heart_rate`, `graph_heading_power`, `graph_heading_speed`, `graph_heading_pace`, `graph_heading_elevation`
  - Assert 100% presence and parity across all 9 languages.

### Test Case 6: Clean-Room Full Suite Regression (`[TST-UI-194.6]`)
* **Scope**: Full-Suite Clean-Room Regression Execution
* **Procedure**: Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

---

## 3. Traceability Matrix

| Requirement Clause | Test Case ID | Test Type | Target File | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-235` (Clause 1: Decoupled Extraction) | `[TST-UI-194.1]` | Repository Unit Test | `WorkoutRepositoryTelemetryTest.kt` | Specified |
| `REQ-UI-235` (Clause 2: ViewModel Ingestion) | `[TST-UI-194.2]` | ViewModel Test | `TrackOnMapViewModelTelemetryTest.kt` | Specified |
| `REQ-UI-235` (Clause 3, 4: Decoupled Rendering & Collapse) | `[TST-UI-194.3]` | Visual Contract Test | `TracklessAftermathVisualContractTest.kt` | Specified |
| `REQ-UI-235` (Clause 5: Time Scrubbing) | `[TST-UI-194.4]` | Composable Logic Test | `TelemetryMetricGraphTracklessScrubbingTest.kt` | Specified |
| `REQ-UI-235` (Clause 6: Localization Parity) | `[TST-UI-194.5]` | Resource Audit | `res/values*/strings.xml` | Specified |
| `REQ-PRO-001` (Regression Safety) | `[TST-UI-194.6]` | Clean-Room Full Suite | Full Test Suite | Specified |
