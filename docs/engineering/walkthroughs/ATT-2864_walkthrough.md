# Stage 5 Walkthrough: ATT-2864 - Render climb overlay as solid polyline above route line and segment overlay as dashed above climbs

**Ticket**: [ATT-2864](https://atrainingtracker.atlassian.net/browse/ATT-2864)  
**Sub-task**: [ATT-2958](https://atrainingtracker.atlassian.net/browse/ATT-2958) (`[Test]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Requirement Mapping**: `REQ-UI-319` (*Multi-Tier Route Map Polyline Layering: Solid Climb Overlay and Dashed Segment Overlay Synergy*)  
**Test Spec ID**: `TST-UI-279`  
**Branch**: `feature/ATT-2864`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Summary & Verification Outcome

During Sprint 2026-41.4 & 41.5 reviews of `ATT-2763` on Google Pixel 10, sprint review feedback highlighted visual occlusion issues when inspecting complex routes featuring both climbs and segments:
> *"Revision needed: Human user during Sprint Review: Unfortunately, this does not work. Moved back to Zu erledigen. Note that the root cause might be that we draw the routes in two layers: within one layer it is solid, in the other it is dashed. Please check in more detail."*

Investigation confirmed that `MapRoute` uses dual-layer X-Ray rendering (a solid base and a dashed overlay). Previously, `ROUTE_OVERLAY_Z_INDEX` was set to `40.0f` and `ROUTE_ACTIVE_OVERLAY_Z_INDEX` was set to `45.0f`. Because `CLIMB_Z_INDEX = 28.0f` and `SEGMENT_Z_INDEX = 30.0f`, the route's upper dashed layer was drawn above both the climb and the segment, covering them with route dashes.

Ticket `ATT-2864` established requirement `REQ-UI-319`, designed an architectural multi-tier layering hierarchy, and resolved the issue across `MapModels.kt`, `MapRouteActiveNavigationTest.kt`, `RouteClimbSegmentLayeringContractTest.kt`, `MapLayers.kt`, and `RouteOnMapScreen.kt`.

### Key Enhancements
1. **Strict Z-Ordering Hierarchy Across Dual-Layer Routes, Climbs, and Segments**:
   * *Selected Route Base Line*: `zIndex = MapVisualization.ROUTE_BASE_Z_INDEX = 20.0f` (or `24.0f` for active navigation), rendered as a solid royal blue ribbon (`TTColor.RouteSelected`, width = 10f).
   * *Selected Route Patterned Overlay*: `overlayZIndex = MapVisualization.ROUTE_OVERLAY_Z_INDEX = 22.0f` (or `26.0f` for active navigation), positioned strictly below the climb tier ($22.0\text{f} < 28.0\text{f}$).
   * *Climb Span Overlay*: `zIndex = MapVisualization.CLIMB_Z_INDEX = 28.0f`, rendered as a continuous solid polyline (width = 10f, `jointType = JointType.ROUND`) in its UCI category color (`getClimbCategoryColors`), positioned strictly above both route layers.
   * *Segment Overlay*: `zIndex = MapVisualization.SEGMENT_Z_INDEX = 30.0f`, rendered as a dashed polyline above the climb polyline.
2. **Dashed Segment Overlay with Transparent Gaps**:
   * `MapVisualization` declared `SEGMENT_DASH_LENGTH = 20f` and `SEGMENT_GAP_LENGTH = 15f`.
   * `MapSegment` declared property `val isDashed: Boolean = false`. When `isDashed == true`, `pattern` evaluates to `listOf(Dash(20f), Gap(15f))`.
   * `XRayPolyline` in `MapLayers.kt` supports `hasSolidBase: Boolean = !isDashedSegment`, ensuring that when `isDashed == true`, no solid orange base polyline is drawn beneath the dashes. The gaps between dashes are completely transparent, allowing the underlying solid climb category polyline (or base route ribbon) to be clearly visible without occlusion.
3. **Route On Map Screen Synergy**:
   * In `RouteOnMapScreen.kt`, all matched segments rendered via `segments(...)` set `isDashed = true`.
   * All background segments rendered via `contextualPaths(...)` are mapped with `isDashed = true`.
   * Standalone segment inspection in `SegmentOnMapScreen.kt` preserves solid rendering (`isDashed = false`).
4. **Preservation of System Invariants**:
   * Selected route line (`TTColor.RouteSelected`) remains permanently anchored at the base (`REQ-UI-308.1`).
   * Start (`control_start`) and End (`control_stop`) navigation markers remain visible at all times (`REQ-UI-308.3`).
   * UCI climb category colors, Strava Orange, and 100% test pass rate are strictly preserved.
5. **Clean-Room Verification**: 100% test pass rate across the full unit test suite (2,228 tests executed, 0 failures, 2m 37s runtime).

---

## 2. Requirement & Test Traceability Matrix

| Requirement | Test Spec | Scope | Test Target | Result | Status |
| :--- | :--- | :--- | :--- | :---: | :---: |
| `REQ-UI-319` | `TST-UI-279.1` | Contract | `RouteClimbSegmentLayeringContractTest` (MapVisualization & MapStyle constants) | **PASSED** | `Verified` |
| `REQ-UI-319` | `TST-UI-279.2` | Contract | `RouteClimbSegmentLayeringContractTest` (MapSegment pattern & isDashed contract) | **PASSED** | `Verified` |
| `REQ-UI-319` | `TST-UI-279.3` | Contract | `RouteClimbSegmentLayeringContractTest` (ClimbHighlightData z-index & climb rendering) | **PASSED** | `Verified` |
| `REQ-UI-319` | `TST-UI-279.4` | Contract | `RouteClimbSegmentLayeringContractTest` (MappablePathLayer & RouteOnMapScreen transparent gaps) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-279.5` | Regression | Full test suite (`./gradlew testDebugUnitTest`) | **PASSED** | `Verified` |

---

## 3. Test Execution Results

```text
RouteClimbSegmentLayeringContractTest > testMapVisualizationAndStyleConstants_zIndexHierarchy PASSED
RouteClimbSegmentLayeringContractTest > testMapSegment_patternAndIsDashedContract PASSED
RouteClimbSegmentLayeringContractTest > testClimbHighlightData_zIndexAndMapContentScopeContract PASSED
RouteClimbSegmentLayeringContractTest > testMapLayersAndRouteOnMapScreen_dashedSegmentTransparentGapContract PASSED
MapRouteActiveNavigationTest > testMapRoute_whenSelectedPassive_usesStandardStyling PASSED
MapRouteActiveNavigationTest > testMapRoute_whenActiveNavigation_usesProminentStyling PASSED
MapRouteActiveNavigationTest > testXRayPolylineHierarchy_preservesSegmentInterleavingInvariants PASSED

BUILD SUCCESSFUL in 2m 37s
32 actionable tasks: 1 executed, 31 up-to-date
Total tests executed: 2,228, Failures: 0, Errors: 0, Skipped: 0
```

---

## 4. Modified Files

* [MapModels.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapModels.kt): Updated `routeOverlayZIndex` to 22f, `routeActiveBaseZIndex` to 24f, and `routeActiveOverlayZIndex` to 26f in `MapStyle` and `MapVisualization`, placing dual-layer routes strictly below `climbZIndex` (28f) and `segmentZIndex` (30f).
* [MapRouteActiveNavigationTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapRouteActiveNavigationTest.kt): Updated assertions to verify the 22f, 24f, 26f z-index hierarchy and updated multi-tier layering invariant assertions.
* [RouteClimbSegmentLayeringContractTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteClimbSegmentLayeringContractTest.kt): Added contract assertions for the full z-index sequence (20f < 22f < 24f < 26f < 28f < 30f) and styling constants.
* [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md): Updated `REQ-UI-319` with dual-layer z-index details, marked `Verified`.
* [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md): Added `TST-UI-279` in status `Verified`.
* [docs/engineering/analysis/ATT-2864_analysis.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/analysis/ATT-2864_analysis.md): Stage 1 analysis deliverable.
* [docs/engineering/test_specs/ATT-2864_test_spec.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/test_specs/ATT-2864_test_spec.md): Stage 2 test spec deliverable.
* [docs/engineering/plans/ATT-2864_plan.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/plans/ATT-2864_plan.md): Stage 3 implementation plan deliverable.
* [docs/engineering/walkthroughs/ATT-2864_walkthrough.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/walkthroughs/ATT-2864_walkthrough.md): Stage 5 walkthrough deliverable.
