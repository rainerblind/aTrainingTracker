# Stage 5: Walkthrough & Verification - ATT-2006: Display Heart Rate Graph When HR Telemetry Exists Even Without GPS Track

**Ticket**: [ATT-2006](https://atrainingtracker.atlassian.net/browse/ATT-2006)  
**Sub-task**: [ATT-2064](https://atrainingtracker.atlassian.net/browse/ATT-2064) (`[Test]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.12`  
**Requirement Mapping**: `REQ-UI-235` (*Aftermath/Telemetry: Continuous Sensor Telemetry Graph Ingestion, Time-Domain Scrubbing, and Map Collapse for Trackless Workouts*)  
**Test Spec ID**: `TST-UI-194`  
**Branch**: `feature/ATT-2006`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Executive Summary & Verification Overview

Athletes frequently record training sessions without GPS coordinates (e.g. indoor treadmill runs, stationary cycling trainers, indoor rowing, or outdoor sessions where GPS fix was unavailable). While spatial map coordinates do not exist, these sessions contain rich sensor telemetry—specifically continuous Heart Rate and Cycling Power streams recorded at 1 Hz.

During physical verification on Google Pixel 10 (Sprint 2026-40.11 review), indoor workout `'2014-12-03_145500'` (duration: 39:38, sport: Laufen) displayed the Heart Rate Zone card and the sticky zoom toolbar, but the continuous Heart Rate curve was omitted. Forensic analysis revealed that in `MapDetailLayout.kt`, all telemetry graphs were nested inside `if (showElevationProfile)`. Because trackless workouts have `showElevationProfile == false`, all continuous telemetry graphs were inadvertently bypassed.

Ticket ATT-2006 (`REQ-UI-235`) decouples continuous sensor telemetry rendering from elevation profiles and GPS coordinates, rendering continuous telemetry curves along the Time domain across workout duration:

1. **Decoupled Telemetry Graph Rendering (`MapDetailLayout.kt`)**:
   - Refactored chart container conditional from `if (showElevationProfile)` to `if (showElevationProfile || hasTelemetryGraphs)`.
   - Placed `ElevationProfile` inside `if (showElevationProfile)`, while allowing `TelemetryMetricGraph` (Speed/Pace, Heart Rate, Power) to render whenever `showZoomControls && activeScrubPath != null` and valid sensor data exists.
   - Preserved `GlobalTelemetryZoomToolbar` sticky pinning at the top of the scrollable section when `!showMap && hasScrollableContent`.

2. **Decoupled Telemetry Extraction (`WorkoutRepository.kt`)**:
   - `getWorkoutTelemetryPoints(workoutId: Long): List<PathPoint>` extracts sensor samples from `WorkoutSamples.db` with valid Heart Rate or Power readings, returning synthetic `PathPoint` objects with elapsed time (`timeSec`), `hr`, `power`, and `speedMps`, with `distance = 0.0` and `latLng = LatLng(0.0, 0.0)`.
   - Applies uniform stride decimation when sample count exceeds 800 while strictly preserving initial and terminal boundary samples for 120fps fluid rendering.

3. **ViewModel Telemetry Ingestion (`TrackOnMapAftermathViewModel.kt`)**:
   - In `loadAftermathData()`, when `fullTracks` is empty (no GPS tracks available), queries `workoutRepository.getWorkoutTelemetryPoints(workoutId)` and populates `_uiState.value.copy(telemetryPath = telemetryPoints)`.

4. **TrackOnMapScreen Scrubber & Layout Wiring (`TrackOnMapScreen.kt`)**:
   - Evaluates `val hasGpsTrack = tracks.any { it.path.isNotEmpty() && it.latLngs.any { p -> p.latitude != 0.0 || p.longitude != 0.0 } }`.
   - Binds `activeScrubPath = if (hasGpsTrack) (bestTrack?.path) else aftermathUIState.telemetryPath.ifEmpty { null }`.
   - Forwards `showMap = showMap && hasGpsTrack` and `showElevationProfile = hasGpsTrack && ...` to `MapDetailLayout`.

5. **Map Collapse & Full-Screen Scrollable Layout (`MapDetailLayout.kt`)**:
   - Completely suppresses `ATrainingTrackerMap` and `SplitPaneDivider` when `showMap == false`, eliminating the Null Island (0.0, 0.0) Atlantic Ocean map tile artifact.
   - Expands the outer layout with `Modifier.fillMaxSize()` and the lower content column with `Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())` when `!showMap && hasScrollableContent`.
   - Automatically computes `isTrackless` and enforces `ProfileXAxisDomain.TIME` for telemetry graphs.
   - Computes active instantaneous metric readout during scrubbing (e.g. `148 bpm • Z3`).

6. **Synchronized Time-Domain Scrubbing (`TelemetryMetricGraph.kt`)**:
   - In trackless mode (`(pathPoints.lastOrNull()?.distance ?: 0.0) == 0.0`), drag and tap gestures dispatch `nearest?.timeSec?.toDouble()` directly without 0.0 distance fallback.
   - Cursor calculation evaluates `cursorDistSpan = currentDistance` directly against elapsed time span in seconds.
   - Marker dot lookup compares `p.timeSec` directly against `currentDistance`.

---

## 2. Requirement & Test Verification Matrix

| Requirement Clause | Test Case ID | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-235` (Clause 1: Decoupled Extraction) | `[TST-UI-194.1]` | Unit Test (`WorkoutRepositoryTelemetryTest`) | **PASSED** | `Verified` |
| `REQ-UI-235` (Clause 2: ViewModel Ingestion) | `[TST-UI-194.2]` | ViewModel Test (`TrackOnMapViewModelTelemetryTest`) | **PASSED** | `Verified` |
| `REQ-UI-235` (Clause 3, 4: Decoupled Rendering & Collapse) | `[TST-UI-194.3]` | Visual Contract Test (`TracklessAftermathVisualContractTest`) | **PASSED** | `Verified` |
| `REQ-UI-235` (Clause 5: Time Scrubbing) | `[TST-UI-194.4]` | Composable Logic Test (`TelemetryMetricGraphTracklessScrubbingTest`) | **PASSED** | `Verified` |
| `REQ-UI-235` (Clause 6: Localization Parity) | `[TST-UI-194.5]` | Resource Audit across 9 locales | **PASSED** | `Verified` |
| `REQ-PRO-001` (Regression Safety) | `[TST-UI-194.6]` | Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Contract Tests
- `WorkoutRepositoryTelemetryTest`: 100% PASSED (3/3 tests).
- `TrackOnMapViewModelTelemetryTest`: 100% PASSED (2/2 tests).
- `TracklessAftermathVisualContractTest`: 100% PASSED (5/5 tests, including `testMapDetailLayout_rendersTelemetryGraphs_evenWhenShowElevationProfileIsFalse`).
- `TelemetryMetricGraphTracklessScrubbingTest`: 100% PASSED (3/3 tests).

---

## 4. Hardware / Physical Verification (Google Pixel 10)

1. **Trackless Indoor Workout Rendering**:
   - Open indoor treadmill run / trainer workout recorded with HR monitor and zero GPS points.
   - Verify that Google Map viewport is completely collapsed with zero Null Island Atlantic Ocean tile rendering.
   - Verify that continuous Heart Rate curve is displayed along the Time domain across workout duration.
2. **Interactive Time-Domain Scrubbing**:
   - Drag finger across Heart Rate graph from start to end.
   - Verify that vertical cursor line and highlight dot track finger movement smoothly.
   - Verify that the graph header displays the instantaneous heart rate and zone (e.g. `148 bpm • Z3`).
3. **Full-Screen Fluid Scrolling**:
   - Verify that Header, Zoom Toolbar, Heart Rate Graph, and Heart Rate Zone Distribution Card scroll together seamlessly in a single vertical container without clipping.
