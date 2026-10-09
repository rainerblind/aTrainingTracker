# Stage 1 Analysis: ATT-2864 - Render climb overlay as solid polyline above route line and segment overlay as dashed above climbs

**Ticket**: [ATT-2864](https://rainerblind.atlassian.net/browse/ATT-2864)  
**Sub-task**: [ATT-2910](https://rainerblind.atlassian.net/browse/ATT-2910) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.5`  
**Branch**: `improvement/ATT-2864`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

During on-device physical testing of `ATT-2763` on Google Pixel 10 (Sprint 2026-41.4 Review), sprint review feedback highlighted visual occlusion issues when inspecting complex routes featuring both climbs and segments:
1. Climbs must be rendered as a continuous line (solid polyline, not dashed) positioned directly above the base route line.
2. When a Strava segment coincides with a climb along the route, the segment polyline must be rendered as a **dashed polyline** positioned above the climb polyline.

### Expected Behavior
A clear, non-occluding three-tier polyline layering hierarchy on the route map canvas:
* **Bottom Tier ($z = 20.0\text{f}$)**: Selected route base line (`TTColor.RouteSelected`, Royal Blue `#1565C0`, solid, width = 10f).
* **Middle Tier ($z = 28.0\text{f}$)**: Climb span highlight in its UCI category color (`getClimbCategoryColors`), rendered as a continuous solid polyline (width = 10f), above the route line.
* **Top Tier ($z = 30.0\text{f}$)**: Segment overlay in Strava Orange (`TTColor.StravaOrange`), rendered as a **dashed polyline** (pattern = Dash(20f) + Gap(15f), width = 10f), positioned above the climb line. Because the segment is dashed with transparent gaps and no solid under-base, the underlying climb category color is clearly visible through the gaps, enabling simultaneous recognition of both entities.

### Current Behavior
In `MapContentScope.kt`, `ClimbHighlightData` was configured with `zIndex = 25f`, which could collide with active route base z-indexes (`ROUTE_ACTIVE_BASE_Z_INDEX = 25f`). Furthermore, `MapSegment` defined `pattern = null` and had no dashed styling capability. In `MapLayers.kt`, `XRayPolyline` rendered a mandatory solid base under any patterned overlay, meaning even if a pattern had been applied, the solid base would continue to occlude the underlying climb line.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic inspection of [MapContentScope.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapContentScope.kt), [MapModels.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapModels.kt), and [MapLayers.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapLayers.kt) revealed:

### 1. Solid Segment Polyline Rendering
In `MapModels.kt`:
```kotlin
@Immutable
data class MapSegment(
    ...
) : MappablePath {
    override val color: Color get() = TTColor.StravaOrange
    override val width: Float get() = MapVisualization.SEGMENT_WIDTH
    override val zIndex: Float get() = MapVisualization.SEGMENT_Z_INDEX // 30.0f
    override val overlayZIndex: Float? get() = null
    override val pattern: List<PatternItem>? get() = null
}
```
* `MapSegment` always evaluated `pattern` to `null`.
* When drawn over a climb on `RouteOnMapScreen`, the solid Strava Orange ribbon completely covered the climb polyline beneath it, obscuring whether the climb was Cat 1, Cat 2, Cat 3, Cat 4, HC, or UC.

### 2. Mandatory Solid Base in `XRayPolyline`
In `MapLayers.kt` (lines 404–425):
```kotlin
Polyline(
    points = points,
    color = color,
    width = width,
    zIndex = baseZIndex,
    clickable = clickable,
    onClick = { if (clickable) onClick() },
    jointType = jointType
)
if (pattern != null) {
    Polyline(
        points = points,
        color = overlayColor,
        width = overlayWidth,
        zIndex = overlayZIndex,
        pattern = pattern,
        jointType = jointType
    )
}
```
* `XRayPolyline` unconditionally rendered a solid polyline at `baseZIndex` before drawing any patterned overlay.
* For a route or telemetry track, this solid under-layer creates a colored base ribbon for dark dashed centerlines. But for a transparent-gap dashed overlay (like a segment over a climb), this mandatory solid base would paint over the climb underneath.

### 3. Ambiguous Climb Z-Index Hierarchy
In `MapContentScope.kt`:
```kotlin
internal data class ClimbHighlightData(
    val path: List<LatLng>,
    val color: Color,
    val zIndex: Float = 25f,
    val width: Float = 10f
)
```
* `zIndex = 25f` matched `ROUTE_ACTIVE_BASE_Z_INDEX = 25f`.
* To enforce a strict, unambiguous z-ordering across all route states:
  * Selected Route: `zIndex = 20.0f`
  * Active Navigation Base: `zIndex = 25.0f`
  * **Climb Polyline**: `zIndex = MapVisualization.CLIMB_Z_INDEX = 28.0f`
  * **Segment Polyline**: `zIndex = MapVisualization.SEGMENT_Z_INDEX = 30.0f`

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Add `isDashed: Boolean = false` to `MapSegment` and `SegmentWithPath.toMapSegment()`.
  2. Define `SEGMENT_DASH_LENGTH = 20f` and `SEGMENT_GAP_LENGTH = 15f` in `MapVisualization`.
  3. Define `CLIMB_Z_INDEX = 28.0f` and `CLIMB_WIDTH = 10f` in `MapVisualization`.
  4. Update `ClimbHighlightData` in `MapContentScope.kt` to default to `MapVisualization.CLIMB_Z_INDEX` (28f) and `CLIMB_WIDTH` (10f), rendering a continuous solid polyline above the route line.
  5. Support `hasSolidBase` in `XRayPolyline` / `MappablePathLayer` so that dashed segments render exclusively the patterned polyline without a solid orange background, allowing underlying climbs to show through the gaps.
  6. Configure `RouteOnMapScreen.kt` to render both matched route segments and background contextual segments with `isDashed = true`.
  7. Maintain solid rendering (`isDashed = false`) when inspecting a segment as the standalone primary entity in `SegmentOnMapScreen.kt`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do not change climb category color values (`getClimbCategoryColors`).
  * Do not change route selection colors (`TTColor.RouteSelected`).
  * Do not alter Strava segment matching algorithms in `RouteSegmentMatcher.kt`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Refines and amends `REQ-UI-298` (*Route Map Climb Span Polyline Highlighting by Climb Category Classification*) and `REQ-UI-302` (*Route Starred Segments Spatial Overlap Matching*), complemented by `REQ-UI-308`.
* **Historical Origin & Commit Trace**: Tickets `ATT-2509` (Sprint 2026-41.3), `ATT-2583` (Sprint 2026-41.3), and `ATT-2763` (Sprint 2026-41.4).
* **Root Reason for Existing Formulation**: Both climb spans and matched segments were originally implemented independently as solid polylines. Combining them on dense routes produced complete occlusion of the lower entity.
* **Preservation of Core Invariants**:
  - Selected route polyline (`TTColor.RouteSelected`) remains permanently anchored at the base (`REQ-UI-308.1`).
  - Climbs render as continuous solid polylines in their authentic UCI category colors.
  - Segments retain `TTColor.StravaOrange` branding.
  - All existing unit and contract tests must continue to pass.

---

## 5. Architectural Strategy & High-Level Solution

1. **`MapModels.kt`**:
   - Add constants to `MapVisualization`:
     ```kotlin
     const val CLIMB_WIDTH = 10f
     const val CLIMB_Z_INDEX = 28.0f
     const val SEGMENT_DASH_LENGTH = 20f
     const val SEGMENT_GAP_LENGTH = 15f
     ```
   - Update `MapSegment`:
     ```kotlin
     @Immutable
     data class MapSegment(
         ...
         val isDashed: Boolean = false,
         override val onClick: ((Long) -> Unit)? = null
     ) : MappablePath {
         ...
         override val pattern: List<com.google.android.gms.maps.model.PatternItem>?
             get() = if (isDashed) {
                 listOf(
                     com.google.android.gms.maps.model.Dash(MapVisualization.SEGMENT_DASH_LENGTH),
                     com.google.android.gms.maps.model.Gap(MapVisualization.SEGMENT_GAP_LENGTH)
                 )
             } else null
     }
     ```
   - Update `toMapSegment(showStartAndFinishText: Boolean = true, isDashed: Boolean = false)`.

2. **`MapContentScope.kt`**:
   - In `ClimbHighlightData`:
     ```kotlin
     internal data class ClimbHighlightData(
         val path: List<LatLng>,
         val color: Color,
         val zIndex: Float = MapVisualization.CLIMB_Z_INDEX,
         val width: Float = MapVisualization.CLIMB_WIDTH
     )
     ```

3. **`MapLayers.kt`**:
   - Update `MappablePathLayer` and `XRayPolyline` so that if `path is MapSegment && path.isDashed`, `hasSolidBase` evaluates to `false`, omitting the solid under-layer.

4. **`RouteOnMapScreen.kt`**:
   - Render `segmentPaths` with `isDashed = true`.
   - Ensure `visibleBackgroundPaths` transforms `MapSegment` with `path.copy(isDashed = true)`.
