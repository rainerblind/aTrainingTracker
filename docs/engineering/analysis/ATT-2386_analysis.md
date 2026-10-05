# Stage 1 Analysis: ATT-2386 - Anchor Elevation Profile to Bottom Navigation Bar and Dynamically Expand Upper Map in Route and Segment Details

**Ticket**: [ATT-2386](https://rainerblind.atlassian.net/browse/ATT-2386)  
**Sub-task**: [ATT-2489](https://rainerblind.atlassian.net/browse/ATT-2489) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Branch**: `feature/ATT-2386`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-05  

---

## 1. Problem Statement & Motivation

In the route and segment detailed views (`RouteOnMapScreen` / `SegmentOnMapScreen` / `MapDetailLayout`), the screen viewport is split into two panes using a proportional split (`splitFraction`, defaulting to 50/50).

Because route and segment details contain only an elevation profile (and zoom toolbar) without any additional telemetry charts (HR, Speed/Pace, Power), lap splits, or workout analytics, the lower section only requires its intrinsic height (~100–220 dp depending on elevation range + 40 dp toolbar).

By enforcing a 50/50 split on a typical smartphone screen (~600–700 dp available viewport height below the header):
1. **Unnecessary Dead Space**: The lower viewport allocates ~300–350 dp, leaving 100–180 dp of blank, empty space between the bottom of the elevation profile and the system Navigation Bar.
2. **Artificial Map Squashing**: The upper map viewport is needlessly constrained to 50% of the screen, even though it could dynamically expand to fill the entire remaining vertical space.

Expected behavior:
* The lower elevation profile (including zoom toolbar and x-axis labels) must size dynamically to its intrinsic content height (`wrapContentHeight()`), with its bottom edge sitting flush with the top of the Navigation Bar (`navigationBarsPadding()`).
* The upper map must dynamically expand to occupy all remaining vertical space (`weight(1f)`), maximizing map visibility for compact profiles while keeping taller profiles fully visible and unclipped.

---

## 2. Root Cause Analysis (Forensic Investigation)

1. **Evolution of `MapDetailLayout.kt`**:
   - In Sprint `2026-40.7` (`ATT-1890` / `REQ-UI-223`), the interactive `SplitPaneDivider` was introduced for post-workout inspection (`TrackOnMapScreen`), gated on `hasScrollableContent`.
   - In Sprint `2026-40.15` (`ATT-2311` / `REQ-UI-267`), the elevation profile was repositioned below the map in Routes & Segments. To achieve this, the split layout condition in `MapDetailLayout.kt` was generalized from:
     ```kotlin
     if (showMap && hasScrollableContent)
     ```
     to:
     ```kotlin
     if (showMap && hasLowerSection)
     ```
     where `val hasLowerSection = (showElevationProfile || hasTelemetryGraphs) && !activeScrubPath.isNullOrEmpty() || metadataContent != null || analyticsContent != null`.
   - Because routes and segments have `showElevationProfile == true` and non-empty `activeScrubPath`, `hasLowerSection` evaluates to `true`.
2. **Side Effect on Route and Segment Screens**:
   - As a result of this generalization, `RouteOnMapScreen` and `SegmentOnMapScreen` were funneled into the proportional 50/50 split layout (`Modifier.weight(splitFraction)` vs `Modifier.weight(1f - splitFraction)`), complete with the draggable `SplitPaneDivider` and scrollable lower container (`verticalScroll(rememberScrollState())`).
   - But in routes and segments, `hasScrollableContent` is `false` (`metadataContent == null && analyticsContent == null && !hasTelemetryGraphs`). There are no continuous telemetry graphs or analytics cards to scroll through.
   - The lower section only needs the space occupied by `GlobalTelemetryZoomToolbar` (40 dp), elevation profile title/canvas (~100–228 dp), and navigation bar padding.
   - Forcing a 50% height allocation produces dead space at the bottom and unnecessarily chokes the map viewport.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Differentiate `hasScrollableContent` within the `showMap && hasLowerSection` branch in `MapDetailLayout.kt`.
  2. For screens where `hasScrollableContent == false` (e.g. `RouteOnMapScreen` and `SegmentOnMapScreen`):
     - Assign `Modifier.weight(1f).heightIn(min = SplitPaneMath.MIN_MAP_HEIGHT).fillMaxWidth()` to `mapBox`.
     - Assign `Modifier.fillMaxWidth().wrapContentHeight()` to the lower container holding `GlobalTelemetryZoomToolbar`, `lowerColumn`, and `scrubbingOverlay`.
     - Ensure the lower container sits flush against the bottom Navigation Bar via `navigationBarsPadding()`.
     - Remove the unnecessary `SplitPaneDivider` from `!hasScrollableContent` viewports.
  3. Ensure `TrackOnMapScreen` (detailed workout inspection with telemetry charts and analytics) continues to use the interactive resizable `SplitPaneDivider` with full fidelity.
  4. Ensure `LiveSegmentSheet` (`showMap == false`) and `WorkoutClusterHeatmapScreen` (`hasLowerSection == false`) remain 100% unaffected.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying route polyline rendering or waypoint logic (`RouteOnMapScreen.kt`).
  - Altering `ElevationProfileZoomMath` or canvas rendering routines (`ElevationProfile.kt`).
  - Changing `SplitPaneMath` clamping boundaries or formulas.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Refines `REQ-UI-267` (*Routes & Segments: Position Elevation Profile Below Map with Interactive Zooming, Panning, and Resizable Split-Pane Viewport in MapDetailLayout*) and restores the intent of Clause 4 in `REQ-UI-223` (*Aftermath/Map: Interactive Draggable Splitter and Dynamic Viewport Resizing in MapDetailLayout*).
* **Historical Origin & Commit Trace**:
  - `ATT-1890` (Sprint `2026-40.7`, commit `4e6224aa`): Introduced `REQ-UI-223`. Clause 4 stated: *"When `hasScrollableContent == false` (Routes & Segments), Map Box SHALL retain full `Modifier.weight(1f)` and lower section SHALL wrap its content (`wrapContentHeight()`), with zero divider displayed."*
  - `ATT-2311` (Sprint `2026-40.15`, commit `f129a0de`): Introduced `REQ-UI-267`, which moved elevation profile below the map in routes/segments by switching the split layout condition to `showMap && hasLowerSection`, unintentionally subjecting routes and segments to the 50/50 proportional split.
* **Root Reason for Existing Formulation**:
  `ATT-2311` sought to place the elevation profile below the map instead of floating over it. Reusing the existing split layout block was the quickest path, but it applied a proportional 50% height budget that was designed for multi-chart workout aftermath, rather than an intrinsic-height single profile.
* **Preservation of Core Invariants**:
  - `TrackOnMapScreen` retains full 2-pane interactive resizing with `SplitPaneDivider`.
  - In Routes & Segments, `GlobalTelemetryZoomToolbar` remains fully interactive (+, -, reset, pan toggle), 2-finger pinch zoom remains active, and scrubbing markers remain 100% synchronized with the map polyline.
  - Minimum map height (`SplitPaneMath.MIN_MAP_HEIGHT` = 120 dp) is strictly enforced.

---

## 5. Architectural Strategy & High-Level Solution

In `MapDetailLayout.kt`:
When `showMap && hasLowerSection`:
```kotlin
if (hasScrollableContent) {
    // Detailed Workout Inspection (TrackOnMapScreen): Interactive SplitPaneDivider with proportional resizing
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        ... (existing SplitPaneDivider and weight(splitFraction) / weight(1f - splitFraction) logic)
    }
} else {
    // Route & Segment Details (RouteOnMapScreen / SegmentOnMapScreen):
    // Upper map dynamically maximized (weight 1f), lower elevation profile intrinsically sized and flush with Navigation Bar (ATT-2386)
    Column(modifier = Modifier.fillMaxSize()) {
        mapBox(
            Modifier
                .weight(1f)
                .heightIn(min = SplitPaneMath.MIN_MAP_HEIGHT)
                .fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                if (hasZoomToolbar) {
                    GlobalTelemetryZoomToolbar(
                        zoomScale = profileZoomScale,
                        startDist = MapDetailViewportMath.fractionToDomain(viewportStartFraction, totalSpan, profileZoomScale),
                        totalSpan = totalSpan,
                        onZoomChanged = { z, s ->
                            profileZoomScale = z
                            viewportStartFraction = MapDetailViewportMath.domainToFraction(s, totalSpan, z)
                        },
                        isPanMode = isPanMode,
                        onPanModeToggle = { isPanMode = !isPanMode },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                lowerColumn(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                )
            }
            scrubbingOverlay()
        }
    }
}
```

Benefits:
1. **Dynamic Map Maximization**: On a typical screen, the map expands from ~300 dp to ~500–600 dp, providing a significantly superior navigation and overview experience.
2. **Zero Dead Space**: The elevation profile and zoom toolbar take only their necessary vertical space (~160–260 dp) and anchor cleanly to the top of the Navigation Bar.
3. **Full Functionality Intact**: Zoom toolbar buttons, pinch-to-zoom gestures, distance scrubbing, map marker tracking, and telemetry badges continue to function identically.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. `TrackOnMapScreen` resizable split pane and multi-chart vertical scrolling remain 100% unchanged.
  2. `LiveSegmentSheet` wrapped bottom sheet behavior (`showMap == false`) remains 100% unchanged.
  3. Interactive zoom toolbar (+, -, 1.0x, pan toggle) and elevation scrubbing markers remain fully active in routes and segments.
  4. 100% clean-room test suite pass rate (`./gradlew testDebugUnitTest`).
  5. Parent ticket Human Decision Gate remains strictly guarded.
* **Risk Rating**: **LOW**
  - Well-isolated Compose layout refinement in `MapDetailLayout.kt`.
  - Conditioned on `hasScrollableContent`, which cleanly separates routes/segments from workout aftermath.
