# Stage 3: Implementation Plan - ATT-2006: [Aftermath/Telemetry] Display Heart Rate Graph When HR Telemetry Exists Even Without GPS Track

**Ticket**: [ATT-2006](https://atrainingtracker.atlassian.net/browse/ATT-2006)  
**Sub-task**: [ATT-2062](https://atrainingtracker.atlassian.net/browse/ATT-2062) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.12`  
**Requirement Mapping**: `REQ-UI-235`  
**Test Mapping**: `TST-UI-194`  
**Branch**: `feature/ATT-2006`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Description & Background

During Ceremony 2 physical device testing on Google Pixel 10 hardware (Sprint 2026-40.11 Review), indoor running workout `'2014-12-03_145500'` (duration: 39:38, sport: Laufen) was inspected in the post-workout Aftermath detailed view (`TrackOnMapScreen` / `MapDetailLayout`).

While the Google Map correctly collapsed and the Heart Rate Zone card rendered completely, the continuous Heart Rate curve was missing, displaying only the sticky zoom toolbar above the zone card.

Forensic analysis confirmed that:
1. `WorkoutRepository.getWorkoutTelemetryPoints` and `TrackOnMapAftermathViewModel` already populate `activeScrubPath` with time-domain `telemetryPath` (`distance = 0.0`, `latLng = LatLng(0.0, 0.0)`, `timeSec = timeSec`, `hr = hr`).
2. When a workout lacks GPS track points, `TrackOnMapScreen.kt` passes `showElevationProfile = false`.
3. In `MapDetailLayout.kt`, all `TelemetryMetricGraph` instances (Pace/Speed, Heart Rate, Power) and their container `Surface` were nested directly inside `if (showElevationProfile) { ... }`.
4. When `showElevationProfile` evaluates to `false`, `MapDetailLayout.kt` completely bypassed the chart container, suppressing all continuous telemetry graphs!

`REQ-UI-235` decouples `TelemetryMetricGraph` rendering from `showElevationProfile`, rendering continuous sensor curves whenever `hasTelemetryGraphs` is true, even when elevation profiles and GPS maps are absent.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-235` (*Aftermath/Telemetry: Continuous Sensor Telemetry Graph Ingestion, Time-Domain Scrubbing, and Map Collapse for Trackless Workouts*)
  - Clause 1: Decoupled Telemetry Extraction (`WorkoutRepository.kt`).
  - Clause 2: ViewModel Telemetry Ingestion (`TrackOnMapAftermathViewModel.kt`).
  - Clause 3: TrackOnMapScreen Scrubber & Layout Wiring (`TrackOnMapScreen.kt`).
  - Clause 4: Decoupled Telemetry Rendering, Map Collapse & Full-Screen Layout (`MapDetailLayout.kt`).
  - Clause 5: Synchronized Time-Domain Scrubbing (`TelemetryMetricGraph.kt`).
  - Clause 6: Preservation of Core Invariants (GPS workouts, 9-language localization parity, SQLite single-thread confinement).
* **Test Mapping**: `TST-UI-194` (*Aftermath/Telemetry: Trackless Continuous Telemetry Graph Ingestion, Time-Domain Scrubbing, and Map Collapse Verification*)
  - `[TST-UI-194.1]`: `WorkoutRepositoryTelemetryTest.kt` (Extraction, data integrity, decimation).
  - `[TST-UI-194.2]`: `TrackOnMapViewModelTelemetryTest.kt` (State exposure, ingestion when tracks empty).
  - `[TST-UI-194.3]`: `TracklessAftermathVisualContractTest.kt` (GPS detection, map collapse, vertical scrolling, header readout, decoupled graph rendering).
  - `[TST-UI-194.4]`: `TelemetryMetricGraphTracklessScrubbingTest.kt` (Time-domain dispatch, cursor positioning).
  - `[TST-UI-194.5]`: 9-language localization parity audit (`res/values*/strings.xml`).
  - `[TST-UI-194.6]`: Clean-room full test suite regression (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Standard GPS workouts continue to render map, elevation profile, and telemetry graphs with spatial scrubbing (`REQ-UI-201`, `REQ-UI-206`, `REQ-UI-233`).
2. **Padding & Alignment Invariant**: Horizontal padding parity (`startPaddingPx = 50.dp, endPaddingPx = 25.dp`) remains strictly preserved across `ElevationProfile` and `TelemetryMetricGraph`.
3. **Thread Safety & Dispatcher Affinity**: SQLite database extraction in `WorkoutRepository` remains isolated to `Dispatchers.IO`. UI mutations remain on `Dispatchers.Main`.
4. **9-Language Localization Parity**: Zero hardcoded strings. All UI text tokens exist across all 9 supported application locales.
5. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`. Never advance parent to `Erledigt`.
7. **Programmatic Pre-Check Gate**: Gate 3 verification via `tools/jira_util.py check-gate ATT-2062` must pass before any production code edits.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component: `MapDetailLayout.kt` (`com.atrainingtracker.trainingtracker.ui.map`)
In `lowerColumn`:
Refactor chart container from gating on `if (showElevationProfile)` to `if (showElevationProfile || hasTelemetryGraphs)`:
```kotlin
val lowerColumn: @Composable (Modifier) -> Unit = { colModifier ->
    Column(modifier = colModifier) {
        if (showElevationProfile || hasTelemetryGraphs) {
            activeScrubPath?.let { path ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (analyticsContent == null) Modifier.navigationBarsPadding() else Modifier
                        )
                ) {
                    Box(modifier = Modifier.drawWithContent {
                        elevationLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(elevationLayer)
                    }) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (showElevationProfile) {
                                if (showZoomControls) {
                                    Text(
                                        text = stringResource(R.string.graph_heading_elevation),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                                    )
                                }
                                ElevationProfile(
                                    pathPoints = path,
                                    currentDistance = selectedDistance,
                                    minAltitudeOverride = minAltitudeOverride,
                                    maxAltitudeOverride = maxAltitudeOverride,
                                    onDistanceSelected = { selectedDistance = it },
                                    showZoomControls = showZoomControls,
                                    xAxisDomain = tuningConfig.elevationXAxisDomain,
                                    bSportType = bSportType,
                                    zoomScale = profileZoomScale,
                                    startDist = MapDetailViewportMath.fractionToDomain(viewportStartFraction, elevationTotalSpan, profileZoomScale),
                                    onZoomChanged = { z, s ->
                                        profileZoomScale = z
                                        viewportStartFraction = MapDetailViewportMath.domainToFraction(s, elevationTotalSpan, z)
                                    },
                                    isPanMode = isPanMode,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Telemetry Metric Graphs in detailed inspection view
                            if (showZoomControls) {
                                // Speed / Pace Graph
                                if (TelemetryMetricUtils.hasSpeedData(path)) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val isRunning = bSportType == BSportType.RUN
                                    Text(
                                        text = stringResource(if (isRunning) R.string.graph_heading_pace else R.string.graph_heading_speed),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                                    )
                                    TelemetryMetricGraph(...)
                                }

                                // HR Graph
                                if (TelemetryMetricUtils.hasHeartRateData(path)) {
                                    ...
                                    TelemetryMetricGraph(
                                        pathPoints = path,
                                        metricType = TelemetryMetricType.HEART_RATE,
                                        ...
                                    )
                                }

                                // Power Graph
                                if (TelemetryMetricUtils.hasPowerData(path)) {
                                    ...
                                    TelemetryMetricGraph(
                                        pathPoints = path,
                                        metricType = TelemetryMetricType.POWER,
                                        ...
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (analyticsContent == null && !useStatusBarsPadding) {
            Spacer(modifier = Modifier.navigationBarsPadding())
        }

        // 4. ANALYTICS
        analyticsContent?.let { content ->
            ...
        }
    }
}
```

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Programmatic Gate 3 Pre-Check
* Verify Gate 3 status of `ATT-2062` via `python3 tools/jira_util.py check-gate ATT-2062`.

### Step 2: Decouple Telemetry Graph Rendering in `MapDetailLayout.kt`
* In `MapDetailLayout.kt`, change container conditional to `if (showElevationProfile || hasTelemetryGraphs)`.
* Wrap `ElevationProfile` block in `if (showElevationProfile)`.
* Maintain independent `if (showZoomControls)` block for `TelemetryMetricGraph` instances.

### Step 3: Update & Extend Visual Contract Tests (`TracklessAftermathVisualContractTest.kt`)
* Verify `MapDetailLayout.kt` file structure confirms:
  1. `showElevationProfile || hasTelemetryGraphs` condition.
  2. `TelemetryMetricGraph` instances are not gated inside `if (showElevationProfile)`.
* Execute unit tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.TracklessAftermathVisualContractTest"
  ```

### Step 4: Run Complete Telemetry Test Suite
* Execute:
  ```bash
  ./gradlew testDebugUnitTest \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryTelemetryTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.map.TrackOnMapViewModelTelemetryTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphTracklessScrubbingTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.map.TracklessAftermathVisualContractTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests during construction, followed by clean-room full test suite regression (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-2006` allows complete rollback via `git reset --hard origin/sprint/2026-40.12` without impacting integration branches.
