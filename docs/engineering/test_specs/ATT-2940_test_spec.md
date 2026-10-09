# Stage 2 Requirement & Test Specification: ATT-2940 - Increase selected route polyline thickness and remove dashed overlay at standard zoom levels

**Ticket**: [ATT-2940](https://atrainingtracker.atlassian.net/browse/ATT-2940)  
**Sub-task**: [ATT-2980](https://atrainingtracker.atlassian.net/browse/ATT-2980) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2940`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-MAP-040)

### 1.1 Requirement Definition
* **Requirement ID**: `REQ-MAP-040`
* **Title**: High-Glanceability Route Polyline Scaling and Elimination of Dashed Overlay for Actively Navigated and Selected Routes
* **Type**: Functional / Visual Rendering Specification
* **Target Release**: `V4.9.40`
* **Status**: Specified
* **Amends**: `REQ-MAP-023` (*Prominent High-Contrast Rendering for Actively Navigated Routes*, ATT-1841) and `REQ-UI-306` (*Royal Blue Route Palette Transition*, ATT-2761)
* **Parent Ticket**: ATT-2940

### 1.2 Description
The map visualization system shall render actively navigated routes and selected passive routes with increased polyline stroke widths to ensure effortless glanceability under bright outdoor sunlight and at bicycle handlebar distance. Furthermore, the map visualization shall remove the secondary midnight navy dashed overlay pattern from routes, rendering them as clean, solid polyline ribbons while preserving forward travel directional chevrons (`ActiveRouteDecorations`) on actively navigated routes:
1. **Actively Navigated Polyline Width**: `MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH` and `MapStyle.routeActiveNavigationWidth` shall be scaled to `26f`.
2. **Selected Passive Route Polyline Width**: `MapVisualization.ROUTE_WIDTH` and `MapStyle.routeWidth` shall be scaled to `18f`.
3. **Unselected Background Route Polyline Width**: `MapVisualization.ROUTE_UNSELECTED_WIDTH` shall remain preserved at `6f` to maintain visual subordination.
4. **Solid Polyline Rendering**: `MapRoute.pattern` shall return `null`, causing `XRayPolyline` to omit secondary dashed overlay lines and render clean, solid ribbons for all route states.
5. **Directional Chevrons & Layering Invariants**: Forward travel chevrons (`ActiveRouteDecorations`) shall remain active on top of the solid polyline. Stacking order `ROUTE_BASE_Z_INDEX < ROUTE_ACTIVE_BASE_Z_INDEX < CLIMB_Z_INDEX < SEGMENT_Z_INDEX < USER_LOCATION_Z_INDEX` shall be strictly preserved.

### 1.3 Acceptance Criteria (Given-When-Then)

#### Scenario 1: Active Route Navigation Thickness and Solid Ribbon
* **Given** an active route navigation session is in progress with an actively navigated route (`isActiveNavigation = true`),
* **When** the route polyline visual properties are queried from `MapRoute`,
* **Then** the polyline width shall be exactly `26f` (`MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH`),
* **And** the polyline pattern shall be `null` to eliminate dashed overlay clutter and render a clean, solid ribbon,
* **And** the polyline base color shall be `TTColor.RouteActiveNavigation`.

#### Scenario 2: Selected Passive Route Navigation Thickness
* **Given** a route is selected on the map but not in active navigation mode (`isSelected = true`, `isActiveNavigation = false`),
* **When** the route polyline visual properties are queried from `MapRoute`,
* **Then** the polyline width shall be exactly `18f` (`MapVisualization.ROUTE_WIDTH`),
* **And** the polyline pattern shall be `null`,
* **And** the polyline color shall be `TTColor.RouteSelected`.

#### Scenario 3: Unselected Background Route Preservation
* **Given** multiple routes are displayed on the map and one route is unselected (`isSelected = false`, `isActiveNavigation = false`),
* **When** the unselected route polyline visual properties are queried from `MapRoute`,
* **Then** the polyline width shall remain `6f` (`MapVisualization.ROUTE_UNSELECTED_WIDTH`),
* **And** the polyline pattern shall be `null`, preserving clear visual hierarchy below the selected route.

#### Scenario 4: Z-Index Layering Order Preservation
* **Given** an active route, climb categories, Strava segments, and GPS user location rendered on the map canvas,
* **When** the respective Z-indices are evaluated,
* **Then** `ROUTE_BASE_Z_INDEX` (20f) shall be strictly less than `ROUTE_ACTIVE_BASE_Z_INDEX` (26f),
* **And** `ROUTE_ACTIVE_BASE_Z_INDEX` (26f) shall be strictly less than `CLIMB_Z_INDEX` (28f),
* **And** `CLIMB_Z_INDEX` (28f) shall be strictly less than `SEGMENT_Z_INDEX` (30f),
* **And** `SEGMENT_Z_INDEX` (30f) shall be strictly less than `USER_LOCATION_Z_INDEX` (100f).

---

## 2. Test Specification (TST-MAP-042)

### 2.1 Test Definition
* **Test ID**: `TST-MAP-042`
* **Title**: High-Glanceability Route Polyline Scaling and Solid Line Verification
* **Target Requirement**: `REQ-MAP-040`
* **Test Type**: Automated Unit Test (`MapRouteActiveNavigationTest.kt`)
* **Status**: Specified

### 2.2 Test Cases

#### Case 1: `testActiveRoutePolylineDimensionsAndSolidPattern`
* **Target**: `MapRoute` with `isActiveNavigation = true`.
* **Verification Steps**:
  1. Instantiate a `MapRoute` with `isActiveNavigation = true` and `isSelected = true`.
  2. Verify that `mapRoute.width` equals `26f` (`MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH`).
  3. Verify that `mapRoute.pattern` is `null`.
  4. Verify that `mapRoute.color` equals `TTColor.RouteActiveNavigation`.
  5. Verify that `mapRoute.zIndex` equals `MapVisualization.ROUTE_ACTIVE_BASE_Z_INDEX`.

#### Case 2: `testPassiveRoutePolylineDimensionsAndSolidPattern`
* **Target**: `MapRoute` with `isActiveNavigation = false` and `isSelected = true`.
* **Verification Steps**:
  1. Instantiate a `MapRoute` with `isActiveNavigation = false` and `isSelected = true`.
  2. Verify that `mapRoute.width` equals `18f` (`MapVisualization.ROUTE_WIDTH`).
  3. Verify that `mapRoute.pattern` is `null`.
  4. Verify that `mapRoute.color` equals `TTColor.RouteSelected`.
  5. Verify that `mapRoute.zIndex` equals `MapVisualization.ROUTE_BASE_Z_INDEX`.

#### Case 3: `testUnselectedRoutePolylineDimensions`
* **Target**: `MapRoute` with `isActiveNavigation = false` and `isSelected = false`.
* **Verification Steps**:
  1. Instantiate a `MapRoute` with `isActiveNavigation = false` and `isSelected = false`.
  2. Verify that `mapRoute.width` equals `6f` (`MapVisualization.ROUTE_UNSELECTED_WIDTH`).
  3. Verify that `mapRoute.pattern` is `null`.
  4. Verify that `mapRoute.color` equals `TTColor.RouteUnselected`.

#### Case 4: `testZIndexLayeringIntegrity`
* **Target**: `MapVisualization` Z-Index constants.
* **Verification Steps**:
  1. Assert `ROUTE_BASE_Z_INDEX < ROUTE_ACTIVE_BASE_Z_INDEX`.
  2. Assert `ROUTE_ACTIVE_BASE_Z_INDEX < CLIMB_Z_INDEX`.
  3. Assert `CLIMB_Z_INDEX < SEGMENT_Z_INDEX`.
  4. Assert `SEGMENT_Z_INDEX < USER_LOCATION_Z_INDEX`.

---

## 3. Localization Parity (REQ-LOC-001)

No new user-facing strings or UI text changes are introduced in this ticket. The modification is strictly focused on map layer geometry scaling and pattern nullification. Existing localized strings remain untouched and 100% compliant across all 9 supported locales.
