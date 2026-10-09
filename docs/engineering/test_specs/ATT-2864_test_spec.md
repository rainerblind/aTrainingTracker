# Stage 2: Requirement & Test Specification - ATT-2864: Render climb overlay as solid polyline above route line and segment overlay as dashed above climbs

**Ticket**: [ATT-2864](https://rainerblind.atlassian.net/browse/ATT-2864)  
**Sub-task**: [ATT-2911](https://rainerblind.atlassian.net/browse/ATT-2911) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.5`  
**Requirement Mapping**: `REQ-UI-319` (*Multi-Tier Route Map Polyline Layering: Solid Climb Overlay and Dashed Segment Overlay Synergy*)  
**Test Spec ID**: `TST-UI-279`  
**Branch**: `improvement/ATT-2864`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-UI-319)

### 1.1 Problem Statement & Rationale
During Sprint 2026-41.4 review of `ATT-2763` on Google Pixel 10, sprint review feedback highlighted visual occlusion issues when inspecting complex routes featuring both climbs and segments. Previously, both climb spans and matched segments were rendered as solid lines. When a segment coincided with a climb on a route, whichever solid line was on top completely covered the one beneath, obscuring either the climb category classification or the segment identity. The athlete needs climbs rendered as a continuous solid polyline above the route line, and coincident segments rendered as a dashed polyline above the climb polyline with transparent gaps so that the underlying climb category color shows through.

### 1.2 Functional & Architectural Requirements
The system SHALL establish an unambiguous multi-tier polyline layering hierarchy on the route map canvas in `RouteOnMapScreen.kt`, rendering climbs as a continuous solid polyline above the selected route line and rendering segments as a dashed polyline above the climb polyline with transparent gaps (ATT-2864, amending `REQ-UI-298` and `REQ-UI-302`):
1. **Strict Z-Ordering Hierarchy (`MapVisualization`, `MapModels.kt`, `MapContentScope.kt`)**:
   * Selected Route Base Line: `zIndex = MapVisualization.ROUTE_BASE_Z_INDEX = 20.0f` (or `25.0f` for active navigation), rendered as a solid royal blue ribbon (`TTColor.RouteSelected`, width = 10f).
   * Climb Span Overlay: `zIndex = MapVisualization.CLIMB_Z_INDEX = 28.0f`, rendered as a continuous solid polyline (width = 10f, `jointType = JointType.ROUND`) in its UCI category color (`getClimbCategoryColors`), positioned strictly above the route line.
   * Segment Overlay: `zIndex = MapVisualization.SEGMENT_Z_INDEX = 30.0f`, rendered as a dashed polyline above the climb polyline.
2. **Dashed Segment Overlay Pattern & Transparent Gap Synergy**:
   * `MapVisualization` SHALL declare `SEGMENT_DASH_LENGTH = 20f` and `SEGMENT_GAP_LENGTH = 15f`.
   * `MapSegment` SHALL declare property `val isDashed: Boolean = false`. When `isDashed == true`, `pattern` SHALL evaluate to `listOf(Dash(SEGMENT_DASH_LENGTH), Gap(SEGMENT_GAP_LENGTH))`.
   * `XRayPolyline` in `MapLayers.kt` SHALL support `hasSolidBase: Boolean = !isDashedSegment`, ensuring that when `isDashed == true`, no solid orange base polyline is drawn beneath the dashes. The gaps between dashes SHALL be completely transparent, allowing the underlying solid climb category polyline (or base route ribbon) to be clearly visible without occlusion.
3. **Route On Map Screen Synergy (`RouteOnMapScreen.kt`)**:
   * In `RouteOnMapScreen.kt`, all matched segments rendered via `segments(...)` SHALL set `isDashed = true`.
   * All background segments rendered via `contextualPaths(...)` SHALL be mapped with `isDashed = true`.
   * Standalone segment inspection in `SegmentOnMapScreen.kt` SHALL preserve solid rendering (`isDashed = false`).
4. **Preservation of System Invariants**:
   * Selected route line (`TTColor.RouteSelected`) remains permanently anchored at the base (`REQ-UI-308.1`).
   * Start (`control_start`) and End (`control_stop`) navigation markers remain visible at all times (`REQ-UI-308.3`).
   * UCI climb category colors, Strava Orange, and 100% test pass rate MUST NOT be broken.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Climb Continuous Solid Overlay Above Route)**:
  * *Given* an athlete viewing a route with recognized climbs on `RouteOnMapScreen`,
  * *When* inspecting the map canvas,
  * *Then* each climb span SHALL render as a continuous solid polyline in its category color positioned above the route polyline ($z = 28.0\text{f} > 20.0\text{f}$).
* **Criterion 2 (Segment Dashed Overlay Above Climb)**:
  * *Given* a route where a Strava segment coincides with a climb span,
  * *When* inspecting the coincident section,
  * *Then* the segment SHALL render as a dashed Strava Orange polyline positioned above the climb line ($z = 30.0\text{f} > 28.0\text{f}$),
  * *And* the solid climb category color SHALL remain clearly visible through the transparent gaps of the segment dashes.
* **Criterion 3 (Standalone Segment Solid Preservation)**:
  * *Given* an athlete inspecting an isolated segment on `SegmentOnMapScreen`,
  * *When* the map is displayed,
  * *Then* the segment SHALL render as a solid polyline (`isDashed = false`).

---

## 2. Test Specification (TST-UI-279)

### Test Case 1: MapVisualization Z-Index Hierarchy Contract (`TST-UI-279.1`)
* **Scope**: Unit & Contract Test (`RouteClimbSegmentLayeringContractTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteClimbSegmentLayeringContractTest.kt`
* **Checks**:
  * Assert `MapVisualization.ROUTE_BASE_Z_INDEX` ($20.0\text{f}$) < `MapVisualization.CLIMB_Z_INDEX` ($28.0\text{f}$).
  * Assert `MapVisualization.CLIMB_Z_INDEX` ($28.0\text{f}$) < `MapVisualization.SEGMENT_Z_INDEX` ($30.0\text{f}$).
  * Assert `MapVisualization.SEGMENT_DASH_LENGTH` is 20f and `SEGMENT_GAP_LENGTH` is 15f.
  * Assert `MapVisualization.CLIMB_WIDTH` is 10f.

### Test Case 2: MapSegment Pattern & isDashed Contract (`TST-UI-279.2`)
* **Scope**: Unit & Contract Test (`RouteClimbSegmentLayeringContractTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteClimbSegmentLayeringContractTest.kt`
* **Checks**:
  * Assert `MapSegment.isDashed` defaults to `false`.
  * Assert when `isDashed == false`, `pattern` is `null` (solid polyline).
  * Assert when `isDashed == true`, `pattern` contains `Dash(20f)` and `Gap(15f)`.

### Test Case 3: ClimbHighlightData Z-Index Contract (`TST-UI-279.3`)
* **Scope**: Unit & Contract Test (`RouteClimbSegmentLayeringContractTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteClimbSegmentLayeringContractTest.kt`
* **Checks**:
  * Assert `MapContentScope.ClimbHighlightData` default `zIndex` equals `MapVisualization.CLIMB_Z_INDEX` (28f).
  * Assert `MapContentScope.climbs(...)` renders solid polylines with `JointType.ROUND`.

### Test Case 4: MappablePathLayer Transparent Gap Base Omission (`TST-UI-279.4`)
* **Scope**: Unit & Contract Test (`RouteClimbSegmentLayeringContractTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteClimbSegmentLayeringContractTest.kt`
* **Checks**:
  * Assert `MappablePathLayer` sets `hasSolidBase = false` when `path is MapSegment && path.isDashed`.
  * Assert `XRayPolyline` skips solid base rendering when `hasSolidBase == false`.

### Test Case 5: Clean-Room Full Suite Regression Execution (`TST-UI-279.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% test pass rate across the full test suite with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-279.1` | Contract | `MapVisualization` z-index constants | `REQ-UI-319.1` | Specified |
| `TST-UI-279.2` | Contract | `MapSegment.pattern` when `isDashed` | `REQ-UI-319.2` | Specified |
| `TST-UI-279.3` | Contract | `MapContentScope.ClimbHighlightData` z-index | `REQ-UI-319.1` | Specified |
| `TST-UI-279.4` | Contract | `MappablePathLayer` / `XRayPolyline` base omission | `REQ-UI-319.2`, `REQ-UI-319.3` | Specified |
| `TST-UI-279.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001`, `REQ-UI-319.4` | Specified |
