# Stage 1 Analysis: ATT-2863 - Fix segment unselection toggle in route map layers menu to hide segment polylines

**Ticket**: [ATT-2863](https://rainerblind.atlassian.net/browse/ATT-2863)  
**Sub-task**: [ATT-2905](https://rainerblind.atlassian.net/browse/ATT-2905) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.5`  
**Branch**: `improvement/ATT-2863`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

During on-device physical testing of `ATT-2763` on Google Pixel 10 (Sprint 2026-41.4 Review), unselecting "Segments" in the `RouteOnMapScreen` top app bar layers menu had no visual effect: segment polylines remained visible on the map canvas.

### Expected Behavior
When an athlete unchecks the "Segments" layer in the Route Map Layers menu (`Icons.Default.Layers`), all segment polylines—whether rendered as matched route segments or as background contextual segment paths—must immediately disappear from the map canvas. Furthermore, when an athlete toggles off an individual segment via its eye icon in the segments breakdown list, that specific segment's polyline must be hidden from both the matched segment layer and background segment paths.

### Current Behavior
In `RouteOnMapScreen.kt`, unchecking "Segments" sets `RouteOverlayLayer.SEGMENTS !in enabledOverlayLayers`, which skips the `segments(...)` composable call for matched segments. However, background segments passed via `backgroundPaths` (from `RoutesScreen.kt`) continue to be rendered unconditionally via `contextualPaths(backgroundPaths, sameSportAlpha = TTAlpha.Medium)` at the bottom of `mapContent`. Because `backgroundPaths` contains `MapSegment` instances for all segments matching the route's sport type, segment polylines remain fully drawn on the map canvas despite the toggle being unchecked.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic examination of [RouteOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt) and [RoutesScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreen.kt) revealed three architectural gaps:

### 1. Unconditional Rendering of `backgroundPaths` in `mapContent`
In `RouteOnMapScreen.kt` (lines 260–296 and line 323):
```kotlin
// Segment filtering: check layer toggle and individual hidden segments
if (RouteOverlayLayer.SEGMENTS in enabledOverlayLayers && matchedSegments.isNotEmpty()) {
    val visibleSegments = matchedSegments.filter { matched ->
        matched.segment.summary.stravaId !in hiddenSegmentIds
    }
    if (visibleSegments.isNotEmpty()) {
        ...
        segments(
            segments = segmentPaths,
            activeLiveSegmentIds = highlightedSegmentId?.let { setOf(it) } ?: emptySet(),
            onSegmentClick = { id -> ... }
        )
    }
}
...
contextualPaths(backgroundPaths, sameSportAlpha = TTAlpha.Medium)
```
* In `RoutesScreen.kt` (lines 126–136):
```kotlin
val backgroundPaths = remember(selectedRoute, allSegments) {
    allSegments
        .filter { it.summary.bSportType == selectedRoute.summary.bSportType }
        .map { it.toMapSegment(showStartAndFinishText = false) }
}
RouteOnMapScreen(
    route = selectedRoute.toMapRoute(),
    routeSummary = selectedRoute.summary,
    backgroundPaths = backgroundPaths,
    allSegments = allSegments,
    ...
)
```
* **Failure Mechanism**: `RoutesScreen` passes all segments of the route's sport type to `RouteOnMapScreen` as `backgroundPaths` (as `MapSegment` instances). `RouteOnMapScreen` renders `contextualPaths(backgroundPaths, ...)` unconditionally at the end of `mapContent`. When the user unchecks `RouteOverlayLayer.SEGMENTS` in `enabledOverlayLayers`, `segments(...)` is skipped, but `contextualPaths(backgroundPaths)` continues executing, drawing all segment paths.

### 2. Leakage of Individual Hidden Segments into `backgroundPaths`
* In `RouteSegmentsBreakdownSection.kt`, individual segments can be hidden using the eye icon toggle (`hiddenSegmentIds: Set<Long>`).
* Line 261 filters `matchedSegments` against `hiddenSegmentIds`.
* However, because `contextualPaths(backgroundPaths)` does not filter against `hiddenSegmentIds`, any segment that is also present in `backgroundPaths` remains rendered on the map canvas as a contextual path even if the user explicitly clicked "Hide" on that segment card in the breakdown list.

### 3. Incomplete Availability Check for `RouteOverlayLayer.SEGMENTS` in Layers Menu
* In `RouteOnMapScreen.kt` (lines 327 and 363):
```kotlin
val hasSegments = matchedSegments.isNotEmpty()
...
RouteOverlayLayer.SEGMENTS -> hasSegments
```
* If a route has no matched segments (e.g., none meeting the route corridor overlap criteria in `RouteSegmentMatcher`), but `backgroundPaths` contains nearby segments for the sport, `hasSegments` evaluates to `false`.
* As a result, the "Segments" checkbox in the Layers dropdown is disabled even though segment polylines from `backgroundPaths` are visibly drawn on the map canvas, preventing the user from toggling them off.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Update `RouteOnMapScreen.kt` to filter `backgroundPaths` based on `RouteOverlayLayer.SEGMENTS in enabledOverlayLayers` and `hiddenSegmentIds`.
  2. Suppress all `MapSegment` instances from `backgroundPaths` when `RouteOverlayLayer.SEGMENTS` is not in `enabledOverlayLayers`.
  3. Filter out any `MapSegment` whose `stravaId` is in `hiddenSegmentIds`.
  4. Ensure matched segments are not duplicated between `segments(...)` and `contextualPaths(backgroundPaths)` when both layers are enabled.
  5. Expand `hasSegments` check in `RouteOnMapScreen` to `matchedSegments.isNotEmpty() || backgroundPaths.any { it is MapSegment }`, allowing the athlete to toggle segment visibility even on routes with only background segments.
  6. Expand `RouteOverlayLayersContractTest.kt` to verify that disabling `RouteOverlayLayer.SEGMENTS` suppresses all segment paths (both matched and background) and that individual hidden segment IDs filter background segment paths.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do not modify track or route rendering in `TrackOnMapScreen.kt` or `SegmentOnMapScreen.kt`.
  * Do not alter `RouteSegmentMatcher.kt` matching algorithms (completed in `ATT-2862`).
  * Do not alter database schemas, DAOs, or repositories.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-308` (*Route Map Overlay Layer Visibility Controls, Layers Menu, and Item-Level Breakdown Toggles with Permanent Route Line Anchoring*).
* **Historical Origin & Commit Trace**: Ticket `ATT-2763` (Sprint 2026-41.4), Epic `ATT-66` (*Improve Routes*).
* **Root Reason for Existing Formulation**: `ATT-2763` implemented layer filtering for matched segments in `segments(...)`, but overlooked that `RoutesScreen.kt` also supplies segments via `backgroundPaths`, which bypassed the layer visibility state in `mapContent`.
* **Preservation of Core Invariants**:
  - Selected route polyline (`TTColor.RouteSelected`) remains permanently anchored and unhideable (`REQ-UI-308.1`).
  - Start and End navigation markers remain visible at all times (`REQ-UI-308.3`).
  - Climbs and Waypoints toggling remain completely unaffected.
  - All existing unit tests in `RouteOverlayLayersContractTest.kt` and across the codebase must continue to pass.

---

## 5. Architectural Strategy & High-Level Solution

In `RouteOnMapScreen.kt`:
1. Compute `matchedSegmentIds`:
   ```kotlin
   val matchedSegmentIds = remember(matchedSegments) {
       matchedSegments.map { it.segment.summary.stravaId }.toSet()
   }
   ```
2. Filter `backgroundPaths` dynamically within `mapContent`:
   ```kotlin
   val isSegmentsLayerEnabled = RouteOverlayLayer.SEGMENTS in enabledOverlayLayers
   val visibleBackgroundPaths = backgroundPaths.filter { path ->
       if (path is MapSegment) {
           isSegmentsLayerEnabled && path.stravaId !in hiddenSegmentIds && path.stravaId !in matchedSegmentIds
       } else {
           true
       }
   }
   if (visibleBackgroundPaths.isNotEmpty()) {
       contextualPaths(visibleBackgroundPaths, sameSportAlpha = TTAlpha.Medium)
   }
   ```
3. Update `hasSegments` availability in `overlay`:
   ```kotlin
   val hasSegments = matchedSegments.isNotEmpty() || backgroundPaths.any { it is MapSegment }
   ```

With this change:
- When "Segments" is unchecked in the Layers menu, `isSegmentsLayerEnabled` is `false`: both `segments(...)` and all `MapSegment` instances in `backgroundPaths` are suppressed. The map canvas displays zero segment polylines.
- When an individual segment is hidden via its eye icon in the breakdown list, `path.stravaId !in hiddenSegmentIds` ensures it is hidden from both `segments(...)` and `contextualPaths(...)`.
- Non-segment background paths (e.g. adjacent tracks or routes, if any) continue to render unaffected.
