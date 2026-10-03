# Stage 1 Analysis: ATT-1812 - Restore map preview visibility on detailed workout inspection screen

**Ticket**: [ATT-1812](https://rainerblind.atlassian.net/browse/ATT-1812)  
**Sub-task**: [ATT-1842](https://rainerblind.atlassian.net/browse/ATT-1842) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Branch**: `feature/ATT-1812`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-40.6 Review on connected hardware (Pixel 10, Android 17), navigating to the detailed workout inspection screen (`TrackOnMapScreen` / `MapDetailLayout`) revealed that the map preview is no longer visible. Instead, the screen renders only the workout header, followed immediately by the elevation profile, continuous telemetry graphs (Heart Rate, Pace/Speed, Power), and the top portion of the heart rate zone distribution card, cutting off without scrollability.

The map preview is a foundational visual pillar of the Aftermath experience: athletes expect to immediately see their route footprint, start/stop pins, and live cursor position during synchronized multi-chart scrubbing. Furthermore, sharing workout digests (`combineWorkoutAndShare`) relies on a measured map bitmap; when the map preview collapses, map snapshots cannot be captured.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic examination of `MapDetailLayout.kt` and its commit history (`git log -p app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`) revealed the exact failure mechanism:

1. **Root Layout Contract**:
   ```kotlin
   Column(
       modifier = modifier
           .then(
               if (showMap) Modifier.fillMaxSize() else Modifier.wrapContentHeight()
           )
           ...
   ) {
       // 1. Header (Surface)
       // 2. Map: Box(modifier = Modifier.weight(1f).fillMaxWidth())
       // 3. Elevation Profile & Continuous Telemetry Graphs (ATT-1740)
       // 4. Analytics: HR/Power Zones & Splits
   }
   ```
2. **Measurement Mechanics in Compose `Column`**:
   In Jetpack Compose, an unscrollable `Column` with incoming finite constraints (e.g., viewport height ~800–850 dp on Pixel 10) measures all non-weighted children *first*. The remaining vertical space after subtracting the sum of non-weighted heights is then distributed among weighted children (`Modifier.weight(...)`).
3. **Cumulative Vertical Growth of Non-Weighted Children**:
   - In commit `e7a6a273` (`ATT-1389`), Section 4 (`analyticsContent`) was added to `MapDetailLayout` for HR zones, Power zones, and lap splits (~300–350 dp).
   - In commit `061a4b35` (`ATT-1740`, `REQ-UI-206`), Section 3 was expanded to render three continuous telemetry line charts (Elevation Profile ~160 dp, HR chart ~160 dp, Pace/Speed chart ~160 dp, Power chart ~160 dp, plus headings ~30 dp each).
   - Together with the `WorkoutHeader` (~140–160 dp), the non-weighted children demand **over 950–1100 dp** of vertical height.
4. **Starvation of `Modifier.weight(1f)`**:
   Because `nonWeightedHeight > availableViewportHeight`, the remaining space for `Modifier.weight(1f)` is clamped to **`0 dp`**. As a consequence:
   - The map container receives `height = 0 dp`, completely disappearing from the screen.
   - The root `Column` does not have vertical scrolling, causing the lower cards (zones and splits) to overflow below the bottom navigation bar without the ability to scroll down.
   - The map share button overlay receives no hit-testable area or captures an empty 0x0 map bitmap.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Restore map preview visibility on the detailed workout inspection screen (`TrackOnMapScreen` / `MapDetailLayout`) with a guaranteed minimum/proportional height (e.g., 240–300 dp in portrait).
  * Introduce vertical scrollability for the lower analytics and charts section (Elevation, Heart Rate, Pace/Speed, Power, HR/Power Zones, Lap Splits) so that all visual pillars of `ATT-111` are accessible without crowding out the map.
  * Preserve native, unhindered Google Map pan and zoom gesture handling by keeping the Map outside of the nested vertical scroll container.
  * Ensure full-screen map expansion is preserved for route inspection (`RouteOnMapScreen`), segment inspection (`SegmentOnMapScreen`), and heatmap inspection (`WorkoutClusterHeatmapScreen`) where telemetry charts and analytics cards are absent.
  * Ensure bottom sheet mode (`LIveSegmentSheet.kt`, `showMap = false`) preserves compact `wrapContentHeight` layout without regressions.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do not alter the calculation math of `ElevationProfileZoomMath`, `TelemetryMetricUtils`, or `ZoneDistributionCalculator`.
  * Do not redesign individual chart cards or reorder the Aftermath layout sections.
  * Do not modify database entities, Room DAOs, or workout repository logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: `REQ-UI-206` (*Aftermath: Continuous Telemetry Metric Graphs with Headings & Synchronized Scrubbing*), `REQ-UI-201` (*Multi-Metric Scrubbing & Configurable X-Axis Domain*), `REQ-UI-205` (*Heart Rate & Power Zone Distribution*), extending Epic `ATT-111`.
* **Historical Origin & Commit Trace**:
  - `MapDetailLayout.kt` was unified in `ATT-1389` / `ATT-1391` to serve both map-centric and sheet-centric views.
  - Commit `061a4b35` (`ATT-1740`) added continuous telemetry graphs within Section 3 directly inside the non-scrollable column.
* **Root Reason for Existing Formulation**:
  The map originally used `Modifier.weight(1f)` under the assumption that only a single compact elevation profile (~160 dp) existed below it. Subsequent sprint enhancements incrementally added zone distribution cards, lap split cards, and three telemetry line charts without adapting the parent container's layout constraints.
* **Preservation of Core Invariants**:
  - **Synchronized Scrubbing**: Scrubbing any visible chart in the lower scrollable section must continue to update `selectedDistance` and move the pin on the visible map above it simultaneously.
  - **Zero Route/Segment Regression**: In `RouteOnMapScreen` and `SegmentOnMapScreen`, where `hasScrollableContent` is false, the map must retain its full-weight expansion (`Modifier.weight(1f)`), filling the entire viewport down to the compact elevation profile.
  - **Unified Surface & BottomSheet Contracts**: The background surface handling for `LiveSegmentSheet` (`!useStatusBarsPadding`) must remain 100% compliant with `REQ-UI-196` and `ATT-1735`.
  - **Snapshot Sharing**: `combineWorkoutAndShare` must receive a non-zero measured map bitmap and stitched graphics layers.

---

## 5. Architectural Strategy & High-Level Solution

1. **Adaptive Split-Pane / Scrollable Architecture in `MapDetailLayout.kt`**:
   - Determine whether the layout has overflow-inducing content:
     ```kotlin
     val hasTelemetryGraphs = showZoomControls && activeScrubPath != null && (
         TelemetryMetricUtils.hasHeartRateData(activeScrubPath) ||
         TelemetryMetricUtils.hasSpeedData(activeScrubPath) ||
         TelemetryMetricUtils.hasPowerData(activeScrubPath)
     )
     val hasScrollableContent = analyticsContent != null || hasTelemetryGraphs
     ```
   - Make `analyticsContent` nullable with default `null`:
     ```kotlin
     analyticsContent: (@Composable ColumnScope.() -> Unit)? = null
     ```
   - Sizing strategy when `showMap == true`:
     - **When `hasScrollableContent == true` (Detailed Workout Inspection)**:
       - Allocate the Map a dedicated, stable viewport: `Modifier.weight(1f).heightIn(min = 240.dp).fillMaxWidth()`.
       - Wrap Section 3 (Elevation & Telemetry Graphs) and Section 4 (Analytics) in a dedicated scrollable container:
         `Column(modifier = Modifier.weight(1.2f).fillMaxWidth().verticalScroll(rememberScrollState()))`.
       - This guarantees the Map is never squeezed below 240 dp (typically ~300 dp on 800 dp viewports), while the athlete can scroll smoothly through all charts and cards below it.
     - **When `hasScrollableContent == false` (Routes & Segments)**:
       - Keep the classic full-height map layout: Map gets `Modifier.weight(1f).fillMaxWidth()`, and the lower elevation profile wraps its content at the bottom with zero whitespace waste.
     - **When `showMap == false` (LiveSegment popup)**:
       - Retain unweighted `wrapContentHeight()` on the root column with no scroll state.
2. **Gesture Isolation**:
   - Because the Map resides outside the lower `verticalScroll` column, all Google Map gestures (pan, pinch-to-zoom, rotate) remain completely free from touch interception conflicts.
3. **Graphics Layer Integrity**:
   - Both `elevationLayer` and `analyticsLayer` wrap their respective `Surface`s inside the scrollable column, ensuring that when `combineWorkoutAndShare` is triggered, the full unclipped content is recorded into bitmaps.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Map preview MUST be clearly visible and interactive on detailed workout inspection.
  2. Zero regression in `RouteOnMapScreen`, `SegmentOnMapScreen`, `WorkoutClusterHeatmapScreen`, and `LIveSegmentSheet`.
  3. Continuous multi-chart synchronized scrubbing (`REQ-UI-206`) across Elevation, HR, Pace/Speed, Power, and the Map marker remains pixel-perfect.
  4. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  - Refactoring is confined to Compose layout container modifiers and scroll state in `MapDetailLayout.kt`.
  - No database schemas, data repositories, or math calculators are touched.
