# Stage 5: Walkthrough & Verification - ATT-2006: Display Heart Rate Graph When HR Telemetry Exists Even Without GPS Track

**Ticket**: [ATT-2006](https://rainerblind.atlassian.net/browse/ATT-2006)  
**Sub-task**: [ATT-2021](https://rainerblind.atlassian.net/browse/ATT-2021) (`[Test]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.11`  
**Requirement Mapping**: `REQ-UI-235` (*Aftermath/Telemetry: Continuous Sensor Telemetry Graph Ingestion, Time-Domain Scrubbing, and Map Collapse for Trackless Workouts*)  
**Test Spec ID**: `TST-UI-194`  
**Branch**: `feature/ATT-2006`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Executive Summary & Verification Overview

Athletes frequently record training sessions without GPS coordinates (e.g. indoor treadmill runs, stationary cycling trainers, indoor rowing, or outdoor sessions where GPS fix was unavailable). While spatial map coordinates do not exist, these sessions contain rich sensor telemetry—specifically continuous Heart Rate and Cycling Power streams recorded at 1 Hz.

Historically, `MapDetailLayout` and `TelemetryMetricGraph` coupled continuous graph rendering to the geospatial `activeScrubPath` from GPS tracks. When GPS points were absent, `activeScrubPath` was null, completely suppressing the continuous Heart Rate curve even though the Heart Rate Zone card was fully populated. Simultaneously, `ATrainingTrackerMap` rendered an empty Google Map centered on (0.0, 0.0) off the coast of Africa.

Ticket ATT-2006 (`REQ-UI-235`) decouples sensor telemetry ingestion from GPS coordinates, renders continuous telemetry curves along the Time domain across workout duration, enables synchronized touch scrubbing with instantaneous metric readouts, and cleanly collapses the Google Map container when no GPS coordinates exist:

1. **Decoupled Telemetry Extraction (`WorkoutRepository.kt`)**:
   - Implemented `suspend fun getWorkoutTelemetryPoints(workoutId: Long): List<PathPoint>`.
   - Extracts recorded samples from `WorkoutSamples.db` with valid Heart Rate or Power readings, returning synthetic `PathPoint` objects with elapsed time (`timeSec`), `hr`, `power`, and `speedMps`, with `distance = 0.0` and `latLng = LatLng(0.0, 0.0)`.
   - Applies uniform stride decimation when sample count exceeds 800 while strictly preserving initial (`index == 0`) and terminal (`index == lastIndex`) boundary samples for 120fps fluid rendering.

2. **ViewModel Telemetry Ingestion (`TrackOnMapAftermathViewModel.kt`)**:
   - Exposed `val telemetryPath: List<PathPoint> = emptyList()` in `AftermathMapUIState`.
   - In `loadAftermathData()`, when `fullTracks` is empty (no GPS tracks available), queries `workoutRepository.getWorkoutTelemetryPoints(workoutId)` and populates `_uiState.value.copy(telemetryPath = telemetryPoints)`.
   - Added `@JvmOverloads constructor(application: Application, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO)` for deterministic coroutine testing.

3. **TrackOnMapScreen Scrubber & Layout Wiring (`TrackOnMapScreen.kt`)**:
   - Evaluates `val hasGpsTrack = tracks.any { it.path.isNotEmpty() && it.latLngs.any { p -> p.latitude != 0.0 || p.longitude != 0.0 } }`.
   - Binds `activeScrubPath = if (hasGpsTrack) (bestTrack?.path) else aftermathUIState.telemetryPath.ifEmpty { null }`.
   - Forwards `showMap = showMap && hasGpsTrack` and `showElevationProfile = hasGpsTrack && ...` to `MapDetailLayout`.

4. **Map Collapse & Full-Screen Scrollable Layout (`MapDetailLayout.kt`)**:
   - Completely suppresses `ATrainingTrackerMap` and `SplitPaneDivider` when `showMap == false`, eliminating the Null Island (0.0, 0.0) Atlantic Ocean map tile artifact.
   - Expands the outer layout with `Modifier.fillMaxSize()` and the lower content column with `Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())` when `!showMap && hasScrollableContent`.
   - Automatically computes `isTrackless` and enforces `ProfileXAxisDomain.TIME` for telemetry graphs.
   - Computes active instantaneous metric readout during scrubbing (e.g. `148 bpm • Z3`).

5. **Synchronized Time-Domain Scrubbing (`TelemetryMetricGraph.kt`)**:
   - In trackless mode (`(pathPoints.lastOrNull()?.distance ?: 0.0) == 0.0`), drag and tap gestures dispatch `nearest?.timeSec?.toDouble()` directly without 0.0 distance fallback.
   - Cursor calculation evaluates `cursorDistSpan = currentDistance` directly against elapsed time span in seconds.
   - Marker dot lookup compares `p.timeSec` directly against `currentDistance`.

---

## 2. Requirement & Test Verification Matrix

| Requirement Clause | Test Case ID | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-235` (Clause 1: Decoupled Extraction) | `[TST-UI-194.1]` | Unit Test (`WorkoutRepositoryTelemetryTest`) | **PASSED** | `Verified` |
| `REQ-UI-235` (Clause 2: ViewModel Ingestion) | `[TST-UI-194.2]` | ViewModel Test (`TrackOnMapViewModelTelemetryTest`) | **PASSED** | `Verified` |
| `REQ-UI-235` (Clause 3, 4: Map Collapse & Scroll) | `[TST-UI-194.3]` | Visual Contract Test (`TracklessAftermathVisualContractTest`) | **PASSED** | `Verified` |
| `REQ-UI-235` (Clause 5: Time Scrubbing) | `[TST-UI-194.4]` | Composable Logic Test (`TelemetryMetricGraphTracklessScrubbingTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` (Regression Safety) | `[TST-UI-194.5]` | Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Contract Tests
```text
./gradlew testDebugUnitTest \
  --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryTelemetryTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.map.TrackOnMapViewModelTelemetryTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.map.TracklessAftermathVisualContractTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphTracklessScrubbingTest"

BUILD SUCCESSFUL in 18s
32 actionable tasks: 2 executed, 30 up-to-date
```
- `WorkoutRepositoryTelemetryTest.testExtractTelemetryPoints_whenNoGpsCoordinates_returnsValidTimeDomainPoints`: PASSED
- `WorkoutRepositoryTelemetryTest.testExtractTelemetryPoints_populatesHeartRateAndPower`: PASSED
- `WorkoutRepositoryTelemetryTest.testExtractTelemetryPoints_decimatesWhenOver800Points`: PASSED
- `TrackOnMapViewModelTelemetryTest.testAftermathMapUIState_containsTelemetryPath_defaultEmpty`: PASSED
- `TrackOnMapViewModelTelemetryTest.testLoadAftermathData_whenTracksEmpty_populatesTelemetryPath`: PASSED
- `TracklessAftermathVisualContractTest.testTrackOnMapScreen_evaluatesHasGpsTrack_andSuppressesMap`: PASSED
- `TracklessAftermathVisualContractTest.testMapDetailLayout_suppressesMap_andAppliesVerticalScroll`: PASSED
- `TracklessAftermathVisualContractTest.testMapDetailLayout_evaluatesHasTelemetryGraphs_fromActiveScrubPath`: PASSED
- `TracklessAftermathVisualContractTest.testMapDetailLayout_rendersScrubbingReadoutInHeader`: PASSED
- `TelemetryMetricGraphTracklessScrubbingTest.testTracklessScrubbing_contractEnforcesTimeDomainAndDirectTimeDispatch`: PASSED
- `TelemetryMetricGraphTracklessScrubbingTest.testTracklessCursor_contractEvaluatesDirectlyAgainstTimeSpan`: PASSED
- `TelemetryMetricGraphTracklessScrubbingTest.testTracklessTelemetryScrubbingMath`: PASSED

---

## 4. Hardware / Physical Verification (Google Pixel 10)

1. **Trackless Indoor Workout Rendering**:
   - Open indoor treadmill run / trainer workout recorded with HR monitor and zero GPS points.
   - Verify that Google Map viewport is completely collapsed with zero Null Island Atlantic Ocean tile rendering.
   - Verify that continuous Heart Rate curve is displayed along the Time domain across workout duration.
2. **Interactive Time-Domain Scrubbing**:
   - Drag finger across Heart Rate graph from start to end.
   - Verify that vertical cursor line and highlight dot track finger movement smoothly.
   - Verify that section header readout displays active instantaneous heart rate and zone (e.g. `148 bpm • Z3`).
3. **Full-Screen Unified Scrolling**:
   - Scroll through the Aftermath screen.
   - Confirm that Workout Header, Zoom Toolbar, Heart Rate Graph, Heart Rate Zone Distribution Card, and splits scroll smoothly in a single vertical container without layout clipping.
4. **GPS Workouts Invariance**:
   - Open standard outdoor run / ride with GPS track.
   - Verify that Google Map, SplitPaneDivider, Elevation Profile, and spatial scrubbing continue to operate with 100% fidelity.

---

## 5. Invariant & Governance Verification

1. **Design System & Layout Safety**: Map suppression and full-height scroll column maintain clean Material 3 padding and token consistency.
2. **GPS Workouts Invariance**: Standard GPS workouts retain full map, elevation profile, and spatial scrubbing capabilities (`REQ-UI-201`, `REQ-UI-206`, `REQ-UI-233`).
3. **9-Language Parity**: All string resources retain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, and PT.
4. **Fix Version Audit**: Parent ticket `ATT-2006` specifies Fix Version `V4.9.38`.
5. **Clean-Room Test Suite**: Full test suite passes 100% with zero regressions across all modules.
