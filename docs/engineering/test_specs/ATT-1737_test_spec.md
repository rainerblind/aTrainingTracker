# Stage 2 Requirement & Test Specification: ATT-1737

**Ticket**: [ATT-1737](https://atrainingtracker.atlassian.net/browse/ATT-1737)  
**Sub-task**: [ATT-1771](https://atrainingtracker.atlassian.net/browse/ATT-1771) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Branch**: `feature/ATT-1737`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Requirement Specification (REQ-UI-197)

### REQ-UI-197: Elevation Profile Contextual Zoom Controls Visibility & Non-Overlapping Multi-Metric Scrubbing Badge Architecture
The system SHALL restrict interactive zoom and pan controls on the elevation profile chart strictly to detailed inspection views, suppress them in compact list item previews, and enforce a non-overlapping vertical layout between the overlay control buttons and the multi-metric telemetry badge (ATT-1647, ATT-1736, ATT-1737):

1. **Contextual Zoom Controls Parameterization (`ElevationProfile.kt`, `MapDetailLayout.kt`, `LIveSegmentSheet.kt`)**:
   - `ElevationProfile` composable overloads SHALL accept `showZoomControls: Boolean = false`.
   - When `showZoomControls == false`:
     - (a) The zoom controls row (`+`, `-`, Pan/Scrub mode toggle, and zoom scale reset badge) and the legend info icon button SHALL NOT be rendered;
     - (b) The `Canvas` gesture handler `Modifier.pointerInput(...)` SHALL NOT be attached, enabling smooth list scrolling and instantaneous parent card click handling (`clickable { onMapClick() }`);
     - (c) Top padding of the chart canvas SHALL be reduced to compact `16.dp` without visual overhead.
   - When `showZoomControls == true`:
     - Full interactive zooming, panning, reset badge, and legend info button are rendered.
   - Detailed inspection layouts (`MapDetailLayout.kt`) SHALL accept `showZoomControls: Boolean = true` defaulting to `true`, forwarding the parameter directly to `ElevationProfile`.
   - Full-screen detail inspection screens ([TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt), [RouteOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt), [SegmentOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentOnMapScreen.kt), [WorkoutClusterHeatmapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt)) SHALL rely on the default `true`, while ambient/compact bottom sheet popups ([LIveSegmentSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LIveSegmentSheet.kt)) SHALL explicitly pass `showZoomControls = false`, completely suppressing zoom controls and legend button in the LiveSegment popup during tracking cockpit view (ATT-1736).

2. **Three-Layer Non-Overlapping Vertical Layout Architecture (`ElevationProfile.kt`)**:
   - In detailed inspection views where `showZoomControls == true`:
     - **Layer 1 (Controls & Legend Row)**: The controls row height SHALL be constrained to `24.dp` at `top = 2.dp` (occupying vertical interval `2.dp .. 26.dp`); The legend info button is placed at `Alignment.TopEnd` at `top = 2.dp`.
     - **Clearance 1**: A guaranteed vertical clearance of at least `2.0.dp` exists between the bottom of the controls row (`26.dp`) and the top of the telemetry badge (`28.dp`).
     - **Layer 2 (Multi-Metric Telemetry Badge)**: When scrubbing (`currentDistance != null`), `ScrubbingTelemetryBadge` SHALL be positioned at `top = 28.dp` (occupying vertical interval `28.dp .. 70.dp`), guaranteeing at least `2.dp` clear vertical separation below the controls row across all scrubbing positions and eliminating overlap with zoom buttons unconditionally (ATT-1737).
     - **Clearance 2**: A guaranteed vertical clearance of at least `2.0.dp` exists between the bottom of the telemetry badge (`70.dp`) and the start of the chart curve canvas (`72.dp`).
     - **Layer 3 (Canvas Chart Plotting)**: The `Canvas` top padding SHALL be set to `72.dp` (with total canvas height = `cachedData.adaptiveHeight + 48.dp` to strictly preserve the exact drawable plotting height: `adaptiveHeight - 48.dp`); The elevation curve begins plotting from `y = 0` (which begins at `72.dp` in Box space), guaranteeing a minimum `2.dp` clear separation below the telemetry badge across all horizontal coordinates.
   - In compact previews or ambient popups where `showZoomControls == false`: Top padding remains compact at `16.dp`, total canvas height equals `cachedData.adaptiveHeight`, and single-line scrubber text baseline is anchored at `y = -4.dp.toPx()`.

3. **Preservation of Core Invariants**:
   - Viewport zooming calculations, centroid scaling, pan clamping, adaptive distance ticks, and unit formatting remain 100% intact per `REQ-UI-192`.
   - Existing call sites in list previews (`WorkoutSummary.kt`, `RouteItem.kt`, `SegmentItem.kt`) and ambient live tracking (`SensorGridScreen.kt`) automatically inherit `showZoomControls = false` cleanly.
   - Exact chart plotting height (`cachedData.adaptiveHeight - 48.dp`) is strictly invariant.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**:
  - `REQ-UI-192` (*Interactive Elevation Profile Zoom, Pan & Centered Scaling Navigation*), targeting `ElevationProfile.kt` and `MapDetailLayout.kt`.
* **Historical Origin & Commit Trace**:
  - Commit `69286d4e` (Ticket `ATT-527`, Sprint `2026-40.4`), commit for `ATT-1647` (Sprint `2026-40.5`), `ATT-1736` (Sprint `2026-40.6`), and `ATT-1737` (Sprint `2026-40.6`).
* **Root Reason for Existing Formulation**:
  - In `ATT-1647`, `topPadding = 44.dp` separated controls from the old single-line text label. In `ATT-1391`, `ScrubbingTelemetryBadge` (~42dp tall) was introduced with hardcoded `top = 2.dp`, placing it directly over the zoom buttons on the left half of the chart.
* **Refinement Reason (ATT-1737)**:
  - During on-device review of Sprint `2026-40.5`, the user demonstrated via screenshot that scrubbing on the left side caused `ScrubbingTelemetryBadge` to collide with and occlude the zoom controls. Adjusting the vertical geometry to 3 disjoint layers (Controls at 2..26dp, Badge at 28..70dp, Canvas at 72dp) completely resolves the visual collision.
* **Preservation of Core Invariants**:
  - Viewport mathematics (`ElevationProfileZoomMath`), synchronized coordinate scrubbing, exact chart plotting height (`cachedData.adaptiveHeight - 48.dp`), unit formatting, and full-screen detail view interactivity are fully preserved.

---

## 3. Acceptance Criteria (Given-When-Then)

* **AC-1 (List Preview Cards)**:
  - *Given* an athlete viewing workout history, route list, or segment list,
  - *When* inspecting the elevation profile preview,
  - *Then* zero zoom controls (`+`, `-`, Pan mode, reset badge) or legend buttons SHALL be displayed, and tapping the profile opens the detailed map view.
* **AC-2 (Detailed Inspection Views)**:
  - *Given* an athlete viewing a workout, route, or segment detail sheet in `MapDetailLayout`,
  - *When* inspecting the elevation profile,
  - *Then* zoom controls and the legend button SHALL be visible and fully interactive (`showZoomControls == true`).
* **AC-3 (Non-Overlapping Multi-Metric Scrubbing Badge)**:
  - *Given* an athlete scrubbing on the left edge or any position of the elevation profile in `MapDetailLayout`,
  - *When* `ScrubbingTelemetryBadge` is displayed,
  - *Then* `ScrubbingTelemetryBadge` SHALL render completely below the zoom control buttons (`top = 28.dp`) with a minimum 2dp vertical clearance, ensuring the `+`, `-`, Pan mode toggle, and zoom reset badge remain 100% visible and accessible without any visual collision.
* **AC-4 (Canvas Plotting Height Invariant)**:
  - *Given* `showZoomControls == true`,
  - *When* calculating total canvas height and canvas top padding,
  - *Then* `topPadding` SHALL be `72.dp` and `totalCanvasHeight` SHALL be `cachedData.adaptiveHeight + 48.dp`, strictly preserving the exact drawable chart height `cachedData.adaptiveHeight - 48.dp`.

---

## 4. Test Case Specification (TST-UI-151)

### TST-UI-151: Elevation Profile Contextual Zoom Controls Visibility and Non-Overlapping Multi-Metric Badge Layout Verification
- **Target Components**: [ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt), [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt), [LIveSegmentSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LIveSegmentSheet.kt)
- **Test Files**: `ElevationProfileLayoutTest.kt`, `LiveSegmentSheetLayoutTest.kt`
- **Scenarios**:
  1. `testElevationProfile_parameterDefaults_suppressZoomControls`: Verify `showZoomControls` defaults to `false` in both `ElevationProfile` overloads.
  2. `testMapDetailLayout_parameterDefaults_showZoomControlsTrue`: Verify `MapDetailLayout.kt` defines `showZoomControls: Boolean = true` as default and forwards `showZoomControls = showZoomControls` to `ElevationProfile`.
  3. `testLiveSegmentSheet_suppressesZoomControls`: Verify `LIveSegmentSheet.kt` passes `showZoomControls = false` to `MapDetailLayout`.
  4. `testElevationProfile_sourceCodeInspection_layoutSeparation`: Verify `ElevationProfile.kt` source code sets:
     - `val topPadding = if (showZoomControls) 72.dp else 16.dp`
     - `val totalCanvasHeight = if (showZoomControls) cachedData.adaptiveHeight + 48.dp else cachedData.adaptiveHeight`
     - `padding(top = 28.dp)` on `ScrubbingTelemetryBadge`
  5. `testVerticalLayoutGeometry_guaranteesNonOverlappingBounds`: Verify 3-layer geometry calculations:
     - Layer 1 (Controls Row): `top = 2.0.dp`, height = `24.0.dp`, bottom = `26.0.dp`.
     - Layer 2 (Telemetry Badge): `top = 28.0.dp`, height = `42.0.dp`, bottom = `70.0.dp`.
     - Layer 3 (Canvas Chart Curve): `top = 72.0.dp`.
     - Clearance 1 (`28.0 - 26.0 = 2.0.dp`) and Clearance 2 (`72.0 - 70.0 = 2.0.dp`) are both $\ge 2.0\text{dp}$.
  6. Clean-room full regression execution: `./gradlew testDebugUnitTest`.
