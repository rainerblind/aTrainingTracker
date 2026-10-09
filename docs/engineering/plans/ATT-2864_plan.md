# Stage 3: Implementation Plan - ATT-2864: Render climb overlay as solid polyline above route line and segment overlay as dashed above climbs

**Ticket**: [ATT-2864](https://atrainingtracker.atlassian.net/browse/ATT-2864)  
**Sub-task**: [ATT-2956](https://atrainingtracker.atlassian.net/browse/ATT-2956) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Requirement Mapping**: `REQ-UI-319` (*Multi-Tier Route Map Polyline Layering: Solid Climb Overlay and Dashed Segment Overlay Synergy*)  
**Test Mapping**: `TST-UI-279` (*Multi-Tier Route Map Polyline Layering Contract & Full Suite Regression Verification*)  
**Branch**: `feature/ATT-2864`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Description & Background

During Sprint 2026-41.4 & 41.5 reviews of `ATT-2763` on Google Pixel 10, sprint review feedback highlighted visual occlusion issues when inspecting complex routes featuring both climbs and segments:
> *"Revision needed: Human user during Sprint Review: Unfortunately, this does not work. Moved back to Zu erledigen. Note that the root cause might be that we draw the routes in two layers: within one layer it is solid, in the other it is dashed. Please check in more detail."*

`MapRoute` is rendered using `XRayPolyline` in two layers (a solid base and a dashed overlay). Because `ROUTE_OVERLAY_Z_INDEX` was set to `40.0f` and `ROUTE_ACTIVE_OVERLAY_Z_INDEX` was set to `45.0f`, the route's upper dashed layer was drawn above both the climb (28.0f) and the segment (30.0f), covering them with route dashes.

The solution requires establishing an unambiguous multi-tier polyline layering hierarchy on the route map canvas:
1. Route Base Line: 20.0f (or 24.0f for active navigation)
2. Route Patterned Overlay: 22.0f (or 26.0f for active navigation)
3. Climb Span Overlay: 28.0f (continuous solid polyline in UCI category color)
4. Segment Overlay: 30.0f (dashed polyline without solid base, exposing climb color through transparent gaps)
5. Track / Markers / User Location: 50.0f - 100.0f

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-319` (*Multi-Tier Route Map Polyline Layering: Solid Climb Overlay and Dashed Segment Overlay Synergy*)
* **Test Mapping**: `TST-UI-279` (*Multi-Tier Route Map Polyline Layering Contract & Full Suite Regression Verification*)
  * `TST-UI-279.1`: Unit & contract test verifying `MapVisualization` z-index hierarchy (`ROUTE_BASE_Z_INDEX` = 20f < `ROUTE_OVERLAY_Z_INDEX` = 22f < `ROUTE_ACTIVE_BASE_Z_INDEX` = 24f < `ROUTE_ACTIVE_OVERLAY_Z_INDEX` = 26f < `CLIMB_Z_INDEX` = 28f < `SEGMENT_Z_INDEX` = 30f), `SEGMENT_DASH_LENGTH` (20f), `SEGMENT_GAP_LENGTH` (15f), and `CLIMB_WIDTH` (10f).
  * `TST-UI-279.2`: Unit & contract test verifying `MapSegment` property `isDashed` defaults to `false`, produces `null` pattern when `false`, and produces `listOf(Dash(20f), Gap(15f))` when `true`.
  * `TST-UI-279.3`: Contract test verifying `ClimbHighlightData` default `zIndex` equals `MapVisualization.CLIMB_Z_INDEX` (28f) and `width` equals `MapVisualization.CLIMB_WIDTH` (10f).
  * `TST-UI-279.4`: Contract test verifying `MappablePathLayer` sets `hasSolidBase = false` when `path is MapSegment && path.isDashed` and passes `pattern` even when alpha is contextual, ensuring transparent gaps.
  * `TST-UI-279.5`: Clean-room full regression test suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Permanent Selected Route Anchoring (`REQ-UI-308.1`)**: The selected route polyline (`TTColor.RouteSelected`) remains permanently anchored at the base of the polyline stack (base z-index 20.0f, overlay 22.0f, or active base 24.0f, overlay 26.0f).
2. **Permanent Start and End Markers (`REQ-UI-308.3`)**: Start (`control_start`) and End (`control_stop`) navigation markers remain visible on the map canvas at all times (z-index >= 50f).
3. **UCI Climb Classification Category Colors**: Climb category colors and widths remain strictly intact (`getClimbCategoryColors`, width = 10f, jointType = ROUND).
4. **Strava Orange Identity**: Segment polylines preserve authentic `TTColor.StravaOrange`.
5. **Standalone Segment Screen Preservation**: In `SegmentOnMapScreen.kt`, standalone segments continue to render as solid lines (`isDashed = false`).
6. **Subtask Direct Completion**: Sub-task transitions directly to `Erledigt` via transition `freigabe` upon review agent audit pass.
7. **Parent Human Decision Gate**: Parent ticket ATT-2864 terminal transition is strictly `Final Review (Human)`.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: `MapModels.kt` (`com.atrainingtracker.trainingtracker.ui.map`)
1. In `MapStyle`:
   * Update `val routeOverlayZIndex: Float = 22f` (was 40f)
   * Update `val routeActiveBaseZIndex: Float = 24f` (was 25f)
   * Update `val routeActiveOverlayZIndex: Float = 26f` (was 45f)
   * Verify `val climbWidth: Float = 10f`
   * Verify `val climbZIndex: Float = 28f`
   * Verify `val segmentWidth: Float = 10f`
   * Verify `val segmentZIndex: Float = 30f`
   * Verify `val segmentDashLength: Float = 20f`
   * Verify `val segmentGapLength: Float = 15f`
2. In `MapVisualization`:
   * Update `const val ROUTE_OVERLAY_Z_INDEX = 22.0f` (was 40.0f)
   * Update `const val ROUTE_ACTIVE_BASE_Z_INDEX = 24.0f` (was 25.0f)
   * Update `const val ROUTE_ACTIVE_OVERLAY_Z_INDEX = 26.0f` (was 45.0f)
   * Verify `const val CLIMB_WIDTH = 10f`
   * Verify `const val CLIMB_Z_INDEX = 28.0f`
   * Verify `const val SEGMENT_WIDTH = 10f`
   * Verify `const val SEGMENT_Z_INDEX = 30.0f`
   * Verify `const val SEGMENT_DASH_LENGTH = 20f`
   * Verify `const val SEGMENT_GAP_LENGTH = 15f`
3. In `MapSegment`:
   * Ensure property `val isDashed: Boolean = false`
   * Property `pattern`:
     ```kotlin
     override val pattern: List<com.google.android.gms.maps.model.PatternItem>?
         get() = if (isDashed) {
             listOf(
                 com.google.android.gms.maps.model.Dash(MapVisualization.SEGMENT_DASH_LENGTH),
                 com.google.android.gms.maps.model.Gap(MapVisualization.SEGMENT_GAP_LENGTH)
             )
         } else null
     ```
4. In `SegmentWithPath.toMapSegment`:
   * Ensure parameter `isDashed: Boolean = false`.

### Component 2: `MapContentScope.kt` (`com.atrainingtracker.trainingtracker.ui.map`)
1. In `ClimbHighlightData`:
   ```kotlin
   internal data class ClimbHighlightData(
       val path: List<LatLng>,
       val color: Color,
       val zIndex: Float = MapVisualization.CLIMB_Z_INDEX,
       val width: Float = MapVisualization.CLIMB_WIDTH
   )
   ```
2. In `climbs(...)`:
   Ensure `JointType.ROUND` is used for smooth polyline joining.

### Component 3: `MapLayers.kt` (`com.atrainingtracker.trainingtracker.ui.map`)
1. In `XRayPolyline`:
   * Add parameter `hasSolidBase: Boolean = true`.
   * Only render base polyline if `hasSolidBase == true`.
   * For the patterned overlay, set `clickable = !hasSolidBase && clickable` so click events still trigger when there is no base polyline.
2. In `MappablePathLayer`:
   * Calculate `val isDashedSegment = path is MapSegment && path.isDashed`.
   * Compute `pattern = if (isDashedSegment || alpha >= 1.0f) path.pattern else null`.
   * Pass `hasSolidBase = !isDashedSegment` to `XRayPolyline`.

### Component 4: `RouteOnMapScreen.kt` (`com.atrainingtracker.trainingtracker.ui.routes`)
1. In `matchedSegments.map`:
   * Pass `isDashed = true` to `MapSegment(...)`.
2. In `visibleBackgroundPaths`:
   * When path is `MapSegment`, map with `path.copy(isDashed = true)`.

### Component 5: Tests Update
1. In `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapRouteActiveNavigationTest.kt`:
   * Update `testMapRoute_whenSelectedPassive_usesStandardStyling()` assertion: `assertEquals(MapVisualization.ROUTE_OVERLAY_Z_INDEX, passiveRoute.overlayZIndex, 0.001f)` -> asserts `22.0f`.
   * Update `testXRayPolylineHierarchy_preservesSegmentInterleavingInvariants()`:
     Assert `passiveRoute.zIndex` (20f) < `passiveRoute.overlayZIndex` (22f) < `activeRoute.zIndex` (24f) < `activeRoute.overlayZIndex` (26f) < `CLIMB_Z_INDEX` (28f) < `segmentZIndex` (30f) < `userLocationZIndex` (100f).
2. In `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteClimbSegmentLayeringContractTest.kt`:
   * Author comprehensive contract assertions covering all 4 TST-UI-279 sub-specifications.

### UI Consistency (Rule 23)
* **Closest Reference Screen**: `RouteOnMapScreen.kt` and `SegmentOnMapScreen.kt`.
* **Reused Components**: Existing `MappablePathLayer`, `XRayPolyline`, and Google Maps `Polyline` with `Dash` and `Gap`.
* **Theme Tokens**: `TTColor.StravaOrange`, `TTAlpha.Medium`, UCI climb category colors.
* **One-Off Styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Gate 3 Audit Sign-Off
* Ensure Gate 3 passes and ATT-2956 is transitioned to `Erledigt`.
* Manually advance parent `ATT-2864` to `Implementation`.

### Step 2: Update `MapModels.kt`
* Update `ROUTE_OVERLAY_Z_INDEX = 22.0f`, `ROUTE_ACTIVE_BASE_Z_INDEX = 24.0f`, `ROUTE_ACTIVE_OVERLAY_Z_INDEX = 26.0f` in `MapStyle` and `MapVisualization`.
* Verify `CLIMB_WIDTH = 10f`, `CLIMB_Z_INDEX = 28.0f`, `SEGMENT_DASH_LENGTH = 20f`, `SEGMENT_GAP_LENGTH = 15f`.
* Verify `MapSegment.isDashed` property and pattern.

### Step 3: Update `MapRouteActiveNavigationTest.kt`
* Update overlay z-index assertions and layer hierarchy assertions to match the new layering order.

### Step 4: Verify `MapContentScope.kt` and `MapLayers.kt`
* Verify `ClimbHighlightData` uses `CLIMB_Z_INDEX` and `CLIMB_WIDTH`.
* Verify `MappablePathLayer` sets `hasSolidBase = !isDashedSegment`.

### Step 5: Verify `RouteOnMapScreen.kt`
* Verify matched segments and background segments set `isDashed = true`.

### Step 6: Create `RouteClimbSegmentLayeringContractTest.kt`
* Add comprehensive contract assertions for `TST-UI-279.1` through `TST-UI-279.4`.

### Step 7: Execute Targeted Tests
* Run:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteClimbSegmentLayeringContractTest" --tests "com.atrainingtracker.trainingtracker.ui.map.MapRouteActiveNavigationTest"
  ```

---

## 6. Clean-Room Regression Verification (Stage 5)
* Execute full clean-room suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* Author walkthrough: `docs/engineering/walkthroughs/ATT-2864_walkthrough.md`.
* Mark `REQ-UI-319` and `TST-UI-279` as `Verified`.
