# Stage 2 Requirement & Test Specification: ATT-1740

**Ticket**: [ATT-1740](https://atrainingtracker.atlassian.net/browse/ATT-1740)  
**Sub-task**: [ATT-1776](https://atrainingtracker.atlassian.net/browse/ATT-1776) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Branch**: `feature/ATT-1740`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Requirement Specification (REQ-UI-206)

### REQ-UI-206: Aftermath: Continuous Telemetry Metric Graphs (Heart Rate, Speed/Pace, Power) with Section Headings & Synchronized Multi-Chart Scrubbing Architecture
The system SHALL provide dedicated continuous time-series and distance-series metric graphs with distinct section headings and synchronized scrubbing within the Aftermath inspection layout (`MapDetailLayout.kt` / `TrackOnMapScreen.kt`) (ATT-1740):

1. **Continuous Telemetry Graph Composable (`TelemetryMetricGraph.kt`)**:
   - The system SHALL provide a lightweight, high-performance continuous line chart composable (`TelemetryMetricGraph.kt`) to render scalar telemetry curves matching the active horizontal axis domain (`ProfileXAxisDomain.DISTANCE` or `ProfileXAxisDomain.TIME`).
   - Horizontal layout geometry SHALL match `ElevationProfile` padding invariants: `start = 50.dp, end = 25.dp, bottom = 24.dp`, guaranteeing pixel-perfect vertical alignment of the plot areas and X-axis ticks across all stacked charts.
   - Y-axis minimum and maximum value labels SHALL be rendered on the left baseline-aligned to the chart area bounds.
   - Adaptive X-axis ticks (distance milestones or formatted elapsed timestamps) SHALL be rendered along the bottom axis adapting via `ElevationProfileZoomMath`.
   - When scrubbing (`currentDistance != null`), the component SHALL render a vertical dashed cursor line at `canvasX` and a circular highlight marker dot on the curve at `(canvasX, canvasY)`.
   - Touch and horizontal drag gestures on the chart surface SHALL translate touch coordinates to distance/time and invoke `onDistanceSelected(distance)`.

2. **Dedicated Metric Visualizations & Conditional Rendering**:
   - **Heart Rate Graph**: Rendered if and only if `path.any { it.hr != null && it.hr > 0 }`. Y-axis bounded to minimum and maximum HR in `bpm`. Highlight accent line and subtle area fill using Red / Coral (`TTColor.Zone4`).
   - **Speed / Pace Graph**: Rendered if and only if `path.any { it.speedMps != null && it.speedMps > 0.0 }`. For running sports (`bSportType == BSportType.RUN`), displayed as Pace in `min/km` or `min/mi`; for other sports displayed as Speed in `km/h` or `mph`. Highlight accent line and subtle area fill using Primary Blue (`MaterialTheme.colorScheme.primary`).
   - **Cycling Power Graph**: Rendered if and only if `path.any { it.power != null && it.power > 0 }`. Y-axis bounded to 0..max Power in `Watts`. Highlight accent line and subtle area fill using Purple (`TTColor.Zone5`).
   - Sessions lacking a specific sensor metric SHALL omit the corresponding graph cleanly with zero vertical whitespace consumption.

3. **Distinct Localized Section Headings**:
   - In detailed inspection views where `showZoomControls == true`, each displayed graph SHALL feature a distinct section header title:
     - Elevation: `R.string.graph_heading_elevation` ("Elevation Profile" / "Höhenprofil")
     - Heart Rate: `R.string.graph_heading_heart_rate` ("Heart Rate" / "Herzfrequenz")
     - Speed: `R.string.graph_heading_speed` ("Speed" / "Geschwindigkeit")
     - Pace: `R.string.graph_heading_pace` ("Pace" / "Tempo")
     - Power: `R.string.graph_heading_power` ("Power" / "Leistung")

4. **Synchronized Multi-Chart Scrubbing Architecture**:
   - In `MapDetailLayout.kt`, all displayed continuous graphs (`ElevationProfile`, Heart Rate, Speed/Pace, Power) and the interactive map SHALL share `selectedDistance: Double?`.
   - Scrubbing coordinates on any graph or the map SHALL synchronously update `selectedDistance`, positioning the vertical cursor line and instantaneous value across all visible graphs simultaneously.

5. **100% 9-Language Localization Parity**:
   - String resources `graph_heading_elevation`, `graph_heading_heart_rate`, `graph_heading_speed`, `graph_heading_pace`, and `graph_heading_power` SHALL be defined across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero missing entries.

6. **Preservation of Core Invariants**:
   - List previews (`WorkoutSummary.kt`, `RouteItem.kt`, `SegmentItem.kt`) continue calling `ElevationProfile` with `showZoomControls = false` without instantiating telemetry graphs.
   - Ambient tracking popups (`LIveSegmentSheet.kt`) retain compact elevation profile without extra telemetry graphs.
   - Snapshot sharing (`combineWorkoutAndShare`) records all graphs within `elevationLayer` seamlessly.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**:
  - Net-new requirement only (`REQ-UI-206`), extending Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*), `REQ-UI-197` (*Elevation Profile Contextual Zoom Controls & Scrubber Layout*), and `REQ-UI-201` (*Multi-Metric Scrubbing & Configurable X-Axis Domain*).
* **Historical Origin & Commit Trace**:
  - Ticket `ATT-1391` (Sprint `2026-40.5`) introduced multi-metric scrubbing on `ElevationProfile`. The user explicitly reviewed this feature and requested full graphical curves with headings for Heart Rate, Speed, and Power (ATT-1740).
* **Root Reason for Existing Formulation**:
  - `ElevationProfile` previously served as the sole profile chart. Sensor telemetry was only displayed as text within the floating `ScrubbingTelemetryBadge` rather than plotted as continuous analytical curves.
* **Preservation of Core Invariants**:
  - Viewport mathematics (`ElevationProfileZoomMath`), `ProfileXAxisDomain` preference handling, existing `ElevationProfile` zoom/pan controls, single-thread SQLite query confinement, and 9-language localization parity are 100% preserved.

---

## 3. Acceptance Criteria (Given-When-Then)

* **AC-1 (Dedicated Continuous Telemetry Curves)**:
  - *Given* an athlete viewing a completed workout in Aftermath (`TrackOnMapScreen`),
  - *When* the workout contains recorded Heart Rate, Speed, and Power data,
  - *Then* the screen SHALL display distinct continuous graphs for Elevation Profile, Heart Rate, Speed/Pace, and Power, each headed by its localized section title.
* **AC-2 (Conditional Rendering & Zero Blank Overhead)**:
  - *Given* a workout recorded with GPS and Heart Rate but without a Power meter (`power == null`),
  - *When* viewed in Aftermath,
  - *Then* Elevation, Heart Rate, and Speed/Pace graphs SHALL render, while the Power graph SHALL be completely omitted with 0dp blank space.
* **AC-3 (Synchronized Multi-Chart Scrubbing)**:
  - *Given* an athlete scrubbing horizontally on any graph or the map,
  - *When* the touch position updates,
  - *Then* the vertical cursor line and scrubbed metric values SHALL update synchronously across all displayed graphs.
* **AC-4 (Horizontal Axis Visual Alignment)**:
  - *Given* stacked continuous graphs in `MapDetailLayout`,
  - *When* rendered on screen,
  - *Then* the plot area horizontal margins SHALL match exactly (`start = 50.dp, end = 25.dp`), ensuring vertical cursor lines align across charts.
* **AC-5 (List Previews & Ambient Cockpits Invariant)**:
  - *Given* list item previews (`WorkoutSummary`) or ambient cockpit popups (`LiveSegmentSheet`),
  - *When* rendered,
  - *Then* additional telemetry graphs SHALL NOT be rendered, preserving compact performance.
* **AC-6 (9-Language Parity)**:
  - *Given* an athlete using any of the 9 supported languages,
  - *When* viewing graph section headings,
  - *Then* headings SHALL display accurate, native terminology without missing keys or English fallbacks.

---

## 4. Test Case Specification (TST-UI-160)

### TST-UI-160: Aftermath Continuous Telemetry Metric Graphs & Synchronized Scrubbing Verification
- **Target Components**: [TelemetryMetricGraph.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt), [ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt), [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt), [strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values/strings.xml)
- **Test Files**: `TelemetryMetricGraphTest.kt`, `TelemetryMetricLocalizationTest.kt`, `MapDetailLayoutTest.kt`
- **Scenarios**:
  1. `testTelemetryMetricGraph_dataNormalizationAndBounds`: Verify dynamic scaling of metric values to canvas coordinates (min/max scaling, zero-range guard, outlier clamping).
  2. `testTelemetryMetricGraph_sportTypeSelection_paceVsSpeed`: Verify running activities select Pace formatting (`min/km` or `min/mi`) while cycling/other activities select Speed (`km/h` or `mph`).
  3. `testTelemetryMetricGraph_conditionalRendering_presenceCheck`: Verify presence detection: `hasHeartRateData(path)` requires `it.hr != null && it.hr > 0`, `hasSpeedData(path)` requires `it.speedMps != null && it.speedMps > 0.0`, `hasPowerData(path)` requires `it.power != null && it.power > 0`.
  4. `testTelemetryMetricGraph_cursorAlignmentAndInterpolation`: Verify touch coordinate to distance/time mapping and instantaneous value interpolation at `currentDistance`.
  5. `testHorizontalLayoutInvariants_matchesElevationProfile`: Verify padding invariants (`start = 50.dp, end = 25.dp, bottom = 24.dp`) guaranteeing pixel-perfect alignment.
  6. `testLocalizationParity_telemetryGraphHeadings`: Verify all 5 graph heading strings (`graph_heading_elevation`, `graph_heading_heart_rate`, `graph_heading_speed`, `graph_heading_pace`, `graph_heading_power`) are fully defined across all 9 localized resource files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  7. `testFullRegression_cleanRoom`: Execute `./gradlew testDebugUnitTest` verifying 0 test failures.
