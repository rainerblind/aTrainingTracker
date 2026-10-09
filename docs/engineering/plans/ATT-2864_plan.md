# Stage 3: Implementation Plan - ATT-2864: Render climb overlay as solid polyline above route line and segment overlay as dashed above climbs

**Ticket**: [ATT-2864](https://rainerblind.atlassian.net/browse/ATT-2864)  
**Sub-task**: [ATT-2912](https://rainerblind.atlassian.net/browse/ATT-2912) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.5`  
**Requirement Mapping**: `REQ-UI-319` (*Multi-Tier Route Map Polyline Layering: Solid Climb Overlay and Dashed Segment Overlay Synergy*)  
**Test Mapping**: `TST-UI-279` (*Multi-Tier Route Map Polyline Layering Contract & Full Suite Regression Verification*)  
**Branch**: `improvement/ATT-2864`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Description & Background

During Sprint 2026-41.4 review of `ATT-2763` on Google Pixel 10, sprint review feedback highlighted visual occlusion issues when inspecting complex routes featuring both climbs and segments. Previously, both climb spans and matched segments were rendered as solid lines. When a segment coincided with a climb on a route, whichever solid line was on top completely covered the one beneath, obscuring either the climb category classification or the segment identity. The athlete needs climbs rendered as a continuous solid polyline above the route line, and coincident segments rendered as a dashed polyline above the climb polyline with transparent gaps so that the underlying climb category color shows through.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-319` (*Multi-Tier Route Map Polyline Layering: Solid Climb Overlay and Dashed Segment Overlay Synergy*)
* **Test Mapping**: `TST-UI-279` (*Multi-Tier Route Map Polyline Layering Contract & Full Suite Regression Verification*)
  * `TST-UI-279.1`: Unit & contract test verifying `MapVisualization` z-index hierarchy (`ROUTE_BASE_Z_INDEX` = 20f < `CLIMB_Z_INDEX` = 28f < `SEGMENT_Z_INDEX` = 30f), `SEGMENT_DASH_LENGTH` (20f), `SEGMENT_GAP_LENGTH` (15f), and `CLIMB_WIDTH` (10f).
  * `TST-UI-279.2`: Unit & contract test verifying `MapSegment` property `isDashed` defaults to `false`, produces `null` pattern when `false`, and produces `listOf(Dash(20f), Gap(15f))` when `true`.
  * `TST-UI-279.3`: Contract test verifying `ClimbHighlightData` default `zIndex` equals `MapVisualization.CLIMB_Z_INDEX` (28f) and `width` equals `MapVisualization.CLIMB_WIDTH` (10f).
  * `TST-UI-279.4`: Contract test verifying `MappablePathLayer` sets `hasSolidBase = false` when `path is MapSegment && path.isDashed` and passes `pattern` even when alpha is contextual, ensuring transparent gaps.
  * `TST-UI-279.5`: Clean-room full regression test suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Permanent Selected Route Anchoring (`REQ-UI-308.1`)**: The selected route polyline (`TTColor.RouteSelected`) remains permanently anchored at base z-index 20.0f (or 25.0f for active navigation).
2. **Permanent Start and End Markers (`REQ-UI-308.3`)**: Start (`control_start`) and End (`control_stop`) navigation markers remain visible on the map canvas at all times.
3. **UCI Climb Classification Category Colors**: Climb category colors and widths remain strictly intact (`getClimbCategoryColors`).
4. **Strava Orange Identity**: Segment polylines preserve authentic `TTColor.StravaOrange`.
5. **Standalone Segment Screen Preservation**: In `SegmentOnMapScreen.kt`, standalone segments continue to render as solid lines (`isDashed = false`).
6. **Subtask Direct Completion**: Sub-task transitions directly to `Erledigt` via transition `freigabe` upon review agent audit pass.
7. **Parent Human Decision Gate**: Parent ticket ATT-2864 terminal transition is strictly `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `MapModels.kt` (`com.atrainingtracker.trainingtracker.ui.map`)
1. In `MapStyle`:
   * Add `val climbWidth: Float = 10f`
   * Add `val climbZIndex: Float = 28f`
   * Add `val segmentDashLength: Float = 20f`
   * Add `val segmentGapLength: Float = 15f`
2. In `MapVisualization`:
   * Add `const val CLIMB_WIDTH = 10f`
   * Add `const val CLIMB_Z_INDEX = 28.0f`
   * Add `const val SEGMENT_DASH_LENGTH = 20f`
   * Add `const val SEGMENT_GAP_LENGTH = 15f`
3. In `MapSegment`:
   * Add `val isDashed: Boolean = false`
   * Update `pattern`:
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
   * Add parameter `isDashed: Boolean = false`.

### Component 2: `MapContentScope.kt` (`com.atrainingtracker.trainingtracker.ui.map`)
1. Update `ClimbHighlightData`:
   ```kotlin
   internal data class ClimbHighlightData(
       val path: List<LatLng>,
       val color: Color,
       val zIndex: Float = MapVisualization.CLIMB_Z_INDEX,
       val width: Float = MapVisualization.CLIMB_WIDTH
   )
   ```

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
   * When path is `MapSegment`, pass `path.copy(isDashed = true)`.

### UI Consistency (Rule 23)
* **Closest Reference Screen**: `RouteOnMapScreen.kt` and `SegmentOnMapScreen.kt`.
* **Reused Components**: Existing `MappablePathLayer`, `XRayPolyline`, and Google Maps `Polyline` with `Dash` and `Gap`.
* **Theme Tokens**: `TTColor.StravaOrange`, `TTAlpha.Medium`, UCI climb category colors.
* **One-Off Styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Gate 3 Audit Sign-Off
* Ensure Gate 3 passes and ATT-2912 is `Erledigt`.
* Run mandatory check: `python3 tools/jira_util.py check-gate ATT-2912`.

### Step 2: Update `MapModels.kt`
* Add climb and segment dash constants to `MapStyle` and `MapVisualization`.
* Add `isDashed` property and `pattern` computation to `MapSegment`.
* Update `toMapSegment` signature with default `isDashed = false`.

### Step 3: Update `MapContentScope.kt`
* Set default `zIndex = MapVisualization.CLIMB_Z_INDEX` and `width = MapVisualization.CLIMB_WIDTH` in `ClimbHighlightData`.

### Step 4: Update `MapLayers.kt`
* Add `hasSolidBase` parameter to `XRayPolyline`.
* Update `MappablePathLayer` to omit solid base when rendering dashed segments and preserve dash pattern.

### Step 5: Update `RouteOnMapScreen.kt`
* Set `isDashed = true` for matched segments and background segments.

### Step 6: Author Contract Tests in `RouteClimbSegmentLayeringContractTest.kt`
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteClimbSegmentLayeringContractTest.kt`.
* Implement `TST-UI-279.1` through `TST-UI-279.4`.

### Step 7: Execute Targeted Tests
* Run:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteClimbSegmentLayeringContractTest"
  ```

---

## 6. Clean-Room Regression Verification (Stage 5)
* Execute full clean-room suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* Author walkthrough: `docs/engineering/walkthroughs/ATT-2864_walkthrough.md`.
* Mark `REQ-UI-319` and `TST-UI-279` as `Verified`.
