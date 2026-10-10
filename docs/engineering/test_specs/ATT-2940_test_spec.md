# Stage 2: Requirement & Test Specification - ATT-2940: Increase selected route polyline thickness and remove dashed overlay at standard zoom levels

**Ticket**: [ATT-2940](https://atrainingtracker.atlassian.net/browse/ATT-2940)  
**Sub-task**: [ATT-3058](https://atrainingtracker.atlassian.net/browse/ATT-3058) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: None (Unassigned per Rule 19)  
**Active Sprint**: `2026-41.7`  
**Requirement Mapping**: `REQ-MAP-040` (*High-Glanceability Route Polyline Scaling and Solid Line Rendering for Actively Navigated Route*)  
**Test Spec ID**: `TST-MAP-042`  
**Branch**: `feature/ATT-2940`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Requirement Specification (REQ-MAP-040)

### 1.1 Problem Statement & Rationale
During Sprint 2026-41.6 review, the human user identified that the previous implementation mistakenly applied line thickness increases and pattern nullification across all active routes instead of strictly to the single selected route the athlete is following:
> *"Unfortunately, there was a misunderstanding. We have active routes and one selected route. The selected one is the one the user wants to follow. Only this one must be highlighted as requested by this ticket. The active routes should not have been touched."*

Active/background routes visible on the map must retain their standard route width (`10f`) and standard dashed pattern (`Dash(15f)`, `Gap(15f)`). Only the route being actively navigated (`isActiveNavigation == true`) must be highlighted with prominent width (`26f`) and solid polyline rendering (`pattern = null`).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-MAP-040` (and `REQ-MAP-023`).
* **Historical Origin & Commit Trace**: Ticket `ATT-2940` (commit `61218f1a`, Sprint 2026-41.6).
* **Root Reason for Existing Formulation**: Ticket ATT-2940 in Sprint 2026-41.6 inadvertently increased polyline width across all active routes and nullified patterns globally, contrary to the user's intent to only highlight the actively navigated route.
* **Preservation of Core Invariants**: Z-index stacking hierarchy, directional chevrons, and 100% clean-room test pass rate remain strictly preserved.

### 1.2 Functional & Architectural Requirements
1. **Actively Navigated Polyline Width & Solid Rendering (`MapModels.kt`)**:
   * When `isActiveNavigation == true`:
     * Polyline width SHALL be `26f` (`MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH` and `MapStyle.routeActiveNavigationWidth`).
     * Polyline pattern SHALL be `null` (`MapRoute.pattern = null`), rendering a clean, solid high-contrast ribbon without dashed overlay fragmentation.
2. **Active Non-Navigated Route Baseline Preservation (`MapModels.kt`)**:
   * When `isActiveNavigation == false` and `isSelected == true`:
     * Polyline width SHALL be `10f` (`MapVisualization.ROUTE_WIDTH` and `MapStyle.routeWidth`).
     * Polyline pattern SHALL be preserved as `listOf(Dash(ROUTE_DASH_LENGTH), Gap(ROUTE_GAP_LENGTH))`.
3. **Unselected Route Baseline Preservation (`MapModels.kt`)**:
   * When `isActiveNavigation == false` and `isSelected == false`:
     * Polyline width SHALL be `6f` (`MapVisualization.ROUTE_UNSELECTED_WIDTH` and `MapStyle.routeUnselectedWidth`).
     * Polyline pattern SHALL be preserved as `listOf(Dash(ROUTE_DASH_LENGTH), Gap(ROUTE_GAP_LENGTH))`.
4. **Stacking & Decoration Invariants**:
   * Z-index stacking hierarchy SHALL strictly satisfy:
     `ROUTE_BASE_Z_INDEX (20f) < ROUTE_ACTIVE_BASE_Z_INDEX (24f) < CLIMB_Z_INDEX (28f) < SEGMENT_Z_INDEX (30f) < USER_LOCATION_Z_INDEX (100f)`.
   * Directional forward chevrons (`ActiveRouteDecorations`) SHALL remain rendered on actively navigated routes.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Actively Navigated Route Highlight)**:
  * *Given* a route actively navigated (`isActiveNavigation == true`)
  * *When* querying polyline width and pattern from `MapRoute`
  * *Then* `width` is `26f` and `pattern` is `null`.
* **Criterion 2 (Active Non-Navigated Route Untouched)**:
  * *Given* a route visible/active on the map (`isSelected == true`, `isActiveNavigation == false`)
  * *When* querying polyline width and pattern from `MapRoute`
  * *Then* `width` is `10f` and `pattern` contains `Dash(15f)` and `Gap(15f)`.
* **Criterion 3 (Unselected Route Subordinated)**:
  * *Given* an unselected route (`isSelected == false`, `isActiveNavigation == false`)
  * *When* querying polyline width and pattern from `MapRoute`
  * *Then* `width` is `6f` and `pattern` contains `Dash(15f)` and `Gap(15f)`.
* **Criterion 4 (Z-Index Layering Integrity)**:
  * *Given* the map layering constants in `MapVisualization` and `MapStyle`
  * *When* checking Z-index relationships
  * *Then* `ROUTE_BASE_Z_INDEX < ROUTE_ACTIVE_BASE_Z_INDEX < CLIMB_Z_INDEX < SEGMENT_Z_INDEX < USER_LOCATION_Z_INDEX`.

### 1.4 System Invariants
* Zero regression in Strava Live Segment or Climb Category rendering.
* 100% clean-room test suite pass rate.
* Parent ticket Human Decision Gate strictly guarded.

---

## 2. Test Specification (TST-MAP-042)

### Test Case 1: Active Navigation Route Styling (`TST-MAP-042.1`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapRouteActiveNavigationTest.kt`
* **Preconditions**: `MapRoute` instantiated with `isActiveNavigation = true`.
* **Action**: Read `width`, `color`, and `pattern`.
* **Expected Result**:
  * `width == 26f` (`MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH`).
  * `color == TTColor.RouteActiveNavigation`.
  * `pattern == null`.

### Test Case 2: Active Non-Navigated Route Styling (`TST-MAP-042.2`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapRouteActiveNavigationTest.kt`
* **Preconditions**: `MapRoute` instantiated with `isSelected = true`, `isActiveNavigation = false`.
* **Action**: Read `width`, `color`, and `pattern`.
* **Expected Result**:
  * `width == 10f` (`MapVisualization.ROUTE_WIDTH`).
  * `color == TTColor.RouteSelected`.
  * `pattern != null` matching `Dash(15f)` and `Gap(15f)`.

### Test Case 3: Unselected Route Styling (`TST-MAP-042.3`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapRouteActiveNavigationTest.kt`
* **Preconditions**: `MapRoute` instantiated with `isSelected = false`, `isActiveNavigation = false`.
* **Action**: Read `width`, `color`, and `pattern`.
* **Expected Result**:
  * `width == 6f` (`MapVisualization.ROUTE_UNSELECTED_WIDTH`).
  * `color == TTColor.RouteUnselected`.
  * `pattern != null` matching `Dash(15f)` and `Gap(15f)`.

### Test Case 4: Z-Index Stacking Hierarchy (`TST-MAP-042.4`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapRouteActiveNavigationTest.kt`
* **Preconditions**: `MapVisualization` and `MapStyle` initialized.
* **Action**: Assert numerical ordering.
* **Expected Result**:
  * `ROUTE_BASE_Z_INDEX (20f) < ROUTE_ACTIVE_BASE_Z_INDEX (24f) < CLIMB_Z_INDEX (28f) < SEGMENT_Z_INDEX (30f) < USER_LOCATION_Z_INDEX (100f)`.

### Test Case 5: Clean-Room Regression Suite (`TST-MAP-042.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MAP-042.1` | Unit | `MapRoute.width`, `MapRoute.pattern` | `REQ-MAP-040` (Criterion 1) | Specified |
| `TST-MAP-042.2` | Unit | `MapRoute.width`, `MapRoute.pattern` | `REQ-MAP-040` (Criterion 2) | Specified |
| `TST-MAP-042.3` | Unit | `MapRoute.width`, `MapRoute.pattern` | `REQ-MAP-040` (Criterion 3) | Specified |
| `TST-MAP-042.4` | Unit | `MapVisualization` Z-indexes | `REQ-MAP-040` (Criterion 4) | Specified |
| `TST-MAP-042.5` | Regression | Full Test Suite | `REQ-PRO-001` | Specified |
