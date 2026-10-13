# Stage 2 Test Specification: ATT-1841 - Prominent High-Contrast Rendering for Actively Navigated Routes Preserving Multi-Layer X-Ray Segment Synergy

**Ticket**: [ATT-1841](https://rainerblind.atlassian.net/browse/ATT-1841)  
**Sub-task**: [ATT-2264](https://rainerblind.atlassian.net/browse/ATT-2264) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-1841`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Overview & Verification Strategy

This test specification defines the verification procedures for `REQ-MAP-023` under ticket [ATT-1841](https://rainerblind.atlassian.net/browse/ATT-1841).

The verification strategy ensures that actively followed / navigated routes are visually prominent (width 16f, vibrant `#00E676` Electric Emerald, directional chevrons), clearly differentiated from passive background routes, and strictly maintain the multi-layer X-Ray sandwich with Strava Live Segments (`baseZIndex < SEGMENT_Z_INDEX < overlayZIndex`).

---

## 2. Requirement Traceability Matrix

| Requirement ID | Test Specification ID | Test Classes / Suites | Verification Method | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-MAP-023` (1: State & Properties) | `TST-MAP-025` (Group 1) | `MapRouteActiveNavigationTest.kt` | JUnit 4 Unit Test | Defined |
| `REQ-MAP-023` (2: High-Contrast Prominence) | `TST-MAP-025` (Group 1) | `MapRouteActiveNavigationTest.kt` | JUnit 4 Unit Test | Defined |
| `REQ-MAP-023` (3: Multi-Layer X-Ray Synergy) | `TST-MAP-025` (Group 2) | `MapRouteActiveNavigationTest.kt` | JUnit 4 Invariant Assertion | Defined |
| `REQ-MAP-023` (1: RoutesRepository State) | `TST-MAP-025` (Group 3) | `RoutesRepositoryActiveNavigationTest.kt` | Robolectric / Coroutine Test | Defined |
| `REQ-MAP-023` (4: Directional Chevrons) | `TST-MAP-025` (Group 4) | `MapRouteChevronsTest.kt` | JUnit 4 Geometry Test | Defined |
| `REQ-ALL` (Regression Invariant) | `TST-MAP-025` (Group 5) | Full `./gradlew testDebugUnitTest` | Clean-Room CI Suite | Defined |

---

## 3. Concrete Test Cases (`TST-MAP-025`)

### Group 1: `MapRoute` Visual Hierarchy & Active Navigation Prominence
* **Test Class**: `com.atrainingtracker.trainingtracker.ui.map.MapRouteActiveNavigationTest`
* **Test Cases**:
  1. `testMapRoute_whenActiveNavigation_usesProminentStyling`:
     - Given a `MapRoute` with `isActiveNavigation = true`, `isSelected = true`.
     - Asserts:
       - `route.color == TTColor.RouteActiveNavigation` (`Color(0xFF00E676)`).
       - `route.width == MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH` (`16f`).
       - `route.zIndex == MapVisualization.ROUTE_ACTIVE_BASE_Z_INDEX` (`25.0f`).
       - `route.overlayZIndex == MapVisualization.ROUTE_ACTIVE_OVERLAY_Z_INDEX` (`45.0f`).
  2. `testMapRoute_whenSelectedPassive_usesStandardStyling`:
     - Given a `MapRoute` with `isActiveNavigation = false`, `isSelected = true`.
     - Asserts:
       - `route.color == TTColor.RouteSelected` (`Color(0xFF228B22)` ForestGreen).
       - `route.width == MapVisualization.ROUTE_WIDTH` (`10f`).
       - `route.zIndex == MapVisualization.ROUTE_BASE_Z_INDEX` (`20.0f`).
       - `route.overlayZIndex == MapVisualization.ROUTE_OVERLAY_Z_INDEX` (`40.0f`).
  3. `testMapRoute_whenUnselected_usesSubordinateStyling`:
     - Given a `MapRoute` with `isActiveNavigation = false`, `isSelected = false`.
     - Asserts:
       - `route.color == TTColor.RouteUnselected`.
       - `route.width == MapVisualization.ROUTE_UNSELECTED_WIDTH` (`6f`).
       - `route.zIndex == MapVisualization.ROUTE_UNSELECTED_Z_INDEX` (`5.0f`).

### Group 2: Mathematical Multi-Layer X-Ray Polyline Synergy Invariant
* **Test Class**: `com.atrainingtracker.trainingtracker.ui.map.MapRouteActiveNavigationTest`
* **Test Case**: `testXRayPolylineHierarchy_preservesSegmentInterleavingInvariants`:
  - Asserts mathematically:
    1. Passive Route Base ($20.0\text{f}$) $<$ Active Route Base ($25.0\text{f}$) $<$ Segment Z-Index ($30.0\text{f}$).
    2. Segment Z-Index ($30.0\text{f}$) $<$ Passive Route Overlay ($40.0\text{f}$) $<$ Active Route Overlay ($45.0\text{f}$).
    3. User Location Z-Index ($100.0\text{f}$) $>$ All Route and Segment layers.
    4. Active overlay pattern is non-null and contains alternating `Dash` and `Gap` items.

### Group 3: `RoutesRepository` Active Navigation Reactive State
* **Test Class**: `com.atrainingtracker.trainingtracker.repositories.RoutesRepositoryActiveNavigationTest`
* **Test Cases**:
  1. `testActiveNavigatedRouteId_defaultsToNull`:
     - Asserts `repository.activeNavigatedRouteId.value == null`.
  2. `testSetActiveNavigatedRoute_emitsUpdatedRouteId`:
     - Invoke `repository.setActiveNavigatedRoute(42L)`.
     - Asserts `repository.activeNavigatedRouteId.value == 42L`.
     - Invoke `repository.setActiveNavigatedRoute(null)`.
     - Asserts `repository.activeNavigatedRouteId.value == null`.

### Group 4: Directional Chevrons Geometry & Rotation
* **Test Class**: `com.atrainingtracker.trainingtracker.ui.map.MapRouteChevronsTest`
* **Test Cases**:
  1. `testCalculateBearing_computesAccurateSegmentHeading`:
     - Northbound vector $(0,0) \rightarrow (1,0)$ computes bearing $0^\circ$.
     - Eastbound vector $(0,0) \rightarrow (0,1)$ computes bearing $90^\circ$.
     - Southbound vector $(1,0) \rightarrow (0,0)$ computes bearing $180^\circ$.
     - Westbound vector $(0,1) \rightarrow (0,0)$ computes bearing $270^\circ$.
  2. `testChevronSampling_generatesEvenlySpacedMidpoints`:
     - Given a 100-point route path, windowed sampling generates valid midpoints along the route polyline without out-of-bounds errors.

### Group 5: Full Clean-Room Regression Invariant
* **Execution**: `./gradlew testDebugUnitTest`
* **Success Criteria**: 100% of all existing and new unit tests pass with zero regressions.

---

## 4. Next Steps & Stage 3 Transition
1. Register `TST-MAP-025` in `docs/tests.md`.
2. Post this Stage 2 Test Specification to subtask `ATT-2264`.
3. Move `ATT-2264` to `In Überprüfung`.
4. Run Gate 2 audit (`python3 tools/review_agent.py audit ATT-2264`).
5. Upon Gate 2 approval, advance to Stage 3 (`[Impl-Plan]`).
