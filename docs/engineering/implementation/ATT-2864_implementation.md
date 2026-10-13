# Stage 4: Implementation Report - ATT-2864: Render climb overlay as solid polyline above route line and segment overlay as dashed above climbs

**Ticket**: [ATT-2864](https://rainerblind.atlassian.net/browse/ATT-2864)  
**Sub-task**: [ATT-2913](https://rainerblind.atlassian.net/browse/ATT-2913) (`[Implementation]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.5`  
**Requirement Mapping**: `REQ-UI-319` (*Multi-Tier Route Map Polyline Layering: Solid Climb Overlay and Dashed Segment Overlay Synergy*)  
**Test Mapping**: `TST-UI-279` (*Multi-Tier Route Map Polyline Layering Contract & Full Suite Regression Verification*)  
**Branch**: `improvement/ATT-2864`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Summary of Changes

1. **`MapModels.kt` (`com.atrainingtracker.trainingtracker.ui.map`)**:
   * Added `CLIMB_WIDTH = 10f`, `CLIMB_Z_INDEX = 28.0f`, `SEGMENT_DASH_LENGTH = 20f`, `SEGMENT_GAP_LENGTH = 15f` to `MapVisualization`.
   * Added `climbWidth = 10f`, `climbZIndex = 28f`, `segmentDashLength = 20f`, `segmentGapLength = 15f` to `MapStyle`.
   * Added property `val isDashed: Boolean = false` to `MapSegment`.
   * In `MapSegment.pattern`: dynamically returns `listOf(Dash(20f), Gap(15f))` when `isDashed == true`, and `null` when `isDashed == false`.
   * Updated `SegmentWithPath.toMapSegment(showStartAndFinishText = true, isDashed = false)` to support dash configuration.

2. **`MapContentScope.kt` (`com.atrainingtracker.trainingtracker.ui.map`)**:
   * Updated `ClimbHighlightData` default `zIndex` to `MapVisualization.CLIMB_Z_INDEX` (28f) and `width` to `MapVisualization.CLIMB_WIDTH` (10f).

3. **`MapLayers.kt` (`com.atrainingtracker.trainingtracker.ui.map`)**:
   * Added `hasSolidBase: Boolean = true` parameter to `XRayPolyline`.
   * When `hasSolidBase == false`, the base solid polyline is suppressed, rendering only the patterned overlay with transparent gaps and maintaining clickability.
   * In `MappablePathLayer`, computed `val isDashedSegment = path is MapSegment && path.isDashed`, passing `hasSolidBase = !isDashedSegment` and preserving the dash pattern across all alpha levels.

4. **`RouteOnMapScreen.kt` (`com.atrainingtracker.trainingtracker.ui.routes`)**:
   * Matched segments mapped in `mapContent` explicitly set `isDashed = true`.
   * Background segments in `visibleBackgroundPaths` mapped with `path.copy(isDashed = true)`.
   * Standalone segment screen (`SegmentOnMapScreen.kt`) preserves solid rendering (`isDashed = false`).

5. **`RouteClimbSegmentLayeringContractTest.kt` (`com.atrainingtracker.trainingtracker.ui.routes`)**:
   * Implemented contract tests verifying `MapVisualization` and `MapStyle` constants, `MapSegment` pattern and `isDashed` behavior, `ClimbHighlightData` z-index, and `MapLayers.kt` / `RouteOnMapScreen.kt` transparent gap synergy (`TST-UI-279.1` - `TST-UI-279.4`).

---

## 2. Verification & Test Results

* Targeted Contract Tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteClimbSegmentLayeringContractTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteOverlayLayersContractTest"
  ```
  Result: **BUILD SUCCESSFUL** (100% tests passed).
