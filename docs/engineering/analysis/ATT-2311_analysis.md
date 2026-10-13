# Stage 1 Analysis: ATT-2311 - Position Elevation Profile Below Map and Enable Interactive Zooming in Routes and Segments

**Ticket**: [ATT-2311](https://rainerblind.atlassian.net/browse/ATT-2311)  
**Sub-task**: [ATT-2349](https://rainerblind.atlassian.net/browse/ATT-2349) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Branch**: `feature/ATT-2311`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Statement & Motivation

During Sprint Review evaluation of DEM route elevation enrichment (ATT-1502), the athlete observed that the elevation profile in the Route and Segment detail views (`RouteOnMapScreen` and `SegmentOnMapScreen`) suffers from two major usability and layout defects:
1. **Elevation Profile Positioned Above Map**: In `RouteOnMapScreen` and `SegmentOnMapScreen`, the elevation profile is rendered directly on top of / above the map. Across the entire application (including Workout Details / `TrackOnMapScreen`), the established, intuitive layout is that the map occupies the upper viewport, and the elevation profile / telemetry inspection graphs occupy the lower section *below* the map.
2. **Missing Interactive Zooming & Pan Toolbar**: In Workout Details (`TrackOnMapScreen`), athletes can interactively zoom into telemetry and elevation profiles via `GlobalTelemetryZoomToolbar` (with `+`, `-`, 1.0x reset, and pan mode toggle) as well as 2-finger pinch gestures. In Routes and Segments, this zoom capability is absent or occluded, preventing detailed route elevation profile inspection.

---

## 2. Root Cause Analysis (Forensic Investigation)

A forensic investigation into `MapDetailLayout.kt`, `RouteOnMapScreen.kt`, and `SegmentOnMapScreen.kt` revealed the exact mechanism of the defect:

### 2.1 The Gating Condition of `hasScrollableContent`
In `MapDetailLayout.kt` (lines 135–141):
```kotlin
val hasTelemetryGraphs = showZoomControls && showTelemetryCharts && activeScrubPath != null && (
    TelemetryMetricUtils.hasHeartRateData(activeScrubPath) ||
    TelemetryMetricUtils.hasSpeedData(activeScrubPath) ||
    TelemetryMetricUtils.hasPowerData(activeScrubPath)
)
val hasZoomToolbar = showZoomControls && (showElevationProfile || hasTelemetryGraphs) && !activeScrubPath.isNullOrEmpty()
val hasScrollableContent = metadataContent != null || analyticsContent != null || hasTelemetryGraphs
```
* In `RouteOnMapScreen` and `SegmentOnMapScreen`:
  - `metadataContent == null` (no upper metadata slots)
  - `analyticsContent == null` (no zone distribution cards or split interval tables)
  - `hasTelemetryGraphs == false` (route and segment GPX/TCX paths contain spatial lat/lng/altitude, but no sensor streams like Heart Rate, Speed, or Power meters)
  - Consequently, `hasScrollableContent` evaluates strictly to **`false`**!

### 2.2 Broken Layout Stacking in the Fallback `Box`
In `MapDetailLayout.kt` (lines 583–723):
```kotlin
if (showMap && hasScrollableContent) {
    // 1. Resizable split pane with Map on top and Lower Content below
    BoxWithConstraints(...) {
        Column(...) {
            mapBox(Modifier.weight(splitFraction)...)
            SplitPaneDivider(...)
            Box(Modifier.weight(1f - splitFraction)...) {
                Column(...) {
                    if (hasZoomToolbar) GlobalTelemetryZoomToolbar(...)
                    lowerColumn(Modifier.weight(1f)...)
                }
                scrubbingOverlay()
            }
        }
    }
} else {
    // When hasScrollableContent is false (Routes & Segments), or when showMap is false (LiveSegmentSheet)
    if (showMap) {
        mapBox(Modifier.fillMaxSize())
    }

    if (!showMap && hasScrollableContent) {
        ...
    } else {
        if (hasZoomToolbar) {
            GlobalTelemetryZoomToolbar(...)
        }
        Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
            lowerColumn(Modifier.fillMaxWidth().wrapContentHeight())
            scrubbingOverlay()
        }
    }
}
```
* When `hasScrollableContent == false` and `showMap == true` (the exact state for Routes and Segments):
  1. The layout enters the `else` branch.
  2. The parent container is an unaligned `Box(modifier = Modifier.fillMaxSize().padding(top = currentTopPaddingDp))`.
  3. `mapBox(Modifier.fillMaxSize())` is drawn to fill the entire box.
  4. Next, `if (hasZoomToolbar) GlobalTelemetryZoomToolbar(...)` is drawn directly inside the `Box`.
  5. Finally, `Box(...) { lowerColumn(...) }` (which contains `ElevationProfile`) is drawn inside the `Box`.
  6. **Because `Box` stacks children on top of each other at default alignment `TopStart`**, `lowerColumn` and `GlobalTelemetryZoomToolbar` are placed directly **on top of the map**, occluding the top half of the map!
  7. There is zero vertical column division and zero split pane: the elevation profile sits on top of the map, and the zoom toolbar is occluded or colliding.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Position Elevation Profile Strictly Below Map**: In `MapDetailLayout.kt`, ensure that whenever `showMap == true` and lower content exists (specifically `(showElevationProfile || hasTelemetryGraphs) && !activeScrubPath.isNullOrEmpty()`), the layout activates the structured two-pane vertical arrangement:
     - Upper viewport: Map (`mapBox` with `Modifier.weight(splitFraction)`).
     - Divider: Interactive draggable `SplitPaneDivider` with double-tap reset to 50/50 balance.
     - Lower viewport: Sticky lower section containing `GlobalTelemetryZoomToolbar` and `lowerColumn` (`ElevationProfile`).
  2. **Enable Interactive Zooming & Panning in Routes and Segments**: Ensure `GlobalTelemetryZoomToolbar` renders above `ElevationProfile` in the lower viewport, allowing athletes to zoom in (`+`), zoom out (`-`), reset to 1.0x, and toggle pan mode.
  3. **Scrubbing & Map Marker Synchronization**: Ensure that scrubbing along the zoomed elevation profile in `RouteOnMapScreen` and `SegmentOnMapScreen` smoothly moves the map cursor marker along the route/segment path.
  4. **Preserve Specialized Scenarios**:
     - Standalone maps with no path/elevation (e.g. `WorkoutClusterHeatmapScreen`): `mapBox` continues to occupy 100% of the viewport cleanly.
     - Bottom sheet live tracking popups without map (`LiveSegmentSheet.kt`, `showMap == false`): maintains compact `wrapContentHeight()` layout.
     - Workout Details (`TrackOnMapScreen.kt`): preserves existing telemetry charts, metadata, and analytics.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Do NOT modify DEM altitude querying or elevation smoothing algorithms in `ElevationProfile.kt` or `RouteAltitudeEngine.kt`.
  2. Do NOT alter route database schemas or waypoint data structures in `RouteDatabase.kt` / `MapRoute`.
  3. Do NOT modify the live sensor grid or recording services.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: `REQ-UI-223` (*Aftermath/Map: Interactive Draggable Splitter and Dynamic Viewport Resizing in MapDetailLayout*), specifically Clause 4 (*Preservation of Core Invariants*), and `REQ-UI-197` (*Elevation Profile Contextual Zoom Controls Visibility*).
* **Historical Origin & Commit Trace**: Introduced in sprint `2026-40.7` under `ATT-1890` (commit `4e6224aa`) and sprint `2026-40.6` (`ATT-1647`).
* **Root Reason for Existing Formulation**: Clause 4 of `REQ-UI-223` stated: *"When hasScrollableContent == false (Routes & Segments), Map Box SHALL retain full Modifier.weight(1f) and lower section SHALL wrap its content (wrapContentHeight()), with zero divider displayed."* The original developer assumed routes and segments would render a compact non-resizable overlay over the map. However, in Compose, placing them in an unaligned `Box` caused the elevation profile to occlude the map at the top of the screen and eliminated the ability to zoom into elevation features.
* **Preservation of Core Invariants**: 
  - Minimum map height protection (`SplitPaneMath.MIN_MAP_HEIGHT` = 120dp), safe boundary clamping, and double-tap reset (`0.50f`) are 100% strictly preserved.
  - Multi-touch pinch-to-zoom and synchronized map scrubbing marker updates remain fully functional.
  - We define **`REQ-UI-267`** to refine `REQ-UI-223` and `REQ-UI-197`, establishing that screens with elevation profiles (including Routes and Segments) utilize the standard vertical two-pane layout with the elevation profile positioned strictly below the map and equipped with interactive zooming.

---

## 5. Architectural Strategy & High-Level Solution

### Component 1: `MapDetailLayout.kt`
* Define lower section presence explicitly:
  ```kotlin
  val hasLowerSection = (showElevationProfile || hasTelemetryGraphs) && !activeScrubPath.isNullOrEmpty() || metadataContent != null || analyticsContent != null
  ```
* In the viewport layout branch:
  ```kotlin
  if (showMap && hasLowerSection) {
      // Two-pane vertical layout:
      // 1. Upper: mapBox(Modifier.weight(splitFraction).heightIn(min = SplitPaneMath.MIN_MAP_HEIGHT).fillMaxWidth())
      // 2. Middle: SplitPaneDivider(onDelta = ..., onReset = ...)
      // 3. Lower: Box(Modifier.weight(1f - splitFraction).fillMaxWidth()) {
      //       Column(Modifier.fillMaxSize()) {
      //           if (hasZoomToolbar) GlobalTelemetryZoomToolbar(...)
      //           lowerColumn(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()))
      //       }
      //       scrubbingOverlay()
      //    }
  } else if (showMap) {
      // Single full-screen map (e.g. Heatmap or routes with empty paths)
      mapBox(Modifier.fillMaxSize())
  } else if (hasScrollableContent) {
      // Full-screen scrollable telemetry/analytics without map (e.g. indoor/trackless workouts)
      ...
  } else if (hasLowerSection) {
      // Compact wrapped-height bottom sheet without map (e.g. LiveSegmentSheet)
      Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
          if (hasZoomToolbar) GlobalTelemetryZoomToolbar(...)
          Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
              lowerColumn(Modifier.fillMaxWidth().wrapContentHeight())
              scrubbingOverlay()
          }
      }
  }
  ```

### Component 2: `RouteOnMapScreen.kt` & `SegmentOnMapScreen.kt`
* Because `RouteOnMapScreen` and `SegmentOnMapScreen` already pass `activeScrubPath = route?.path` (and `segment?.path`), `showElevationProfile = true`, and `showZoomControls = true`, fixing `MapDetailLayout.kt` immediately restores:
  1. The map in the upper viewport.
  2. The interactive draggable `SplitPaneDivider`.
  3. The `GlobalTelemetryZoomToolbar` with zoom scale (+ / - / reset) and pan toggle.
  4. The `ElevationProfile` positioned strictly below the map in the lower viewport.
  5. Synchronized map marker scrubbing when interacting with the elevation profile.

---

## 6. Verification Strategy

1. **Architectural & Layout Contract Tests**:
   - Update `MapDetailLayoutTest.kt` to verify that `hasLowerSection` correctly encompasses `showElevationProfile` when `activeScrubPath` is non-empty.
   - Verify that when `showMap == true` and `hasLowerSection == true`, the split-pane layout with `SplitPaneDivider` and `GlobalTelemetryZoomToolbar` is activated.
   - Verify that `LiveSegmentSheetLayoutTest` continues to pass, ensuring `wrapContentHeight()` is preserved for `LiveSegmentSheet` (`showMap == false`).
2. **Interactive Zooming & Scrubbing Contract Tests**:
   - Verify `MapDetailLayoutZoomContractTest` passes with zooming enabled on routes and segments.
3. **Clean-Room Regression Suite**:
   - Run `./gradlew testDebugUnitTest` verifying 100% pass rate.
