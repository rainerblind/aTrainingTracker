# Stage 5 Verification & Walkthrough: ATT-2940 - Increase selected route polyline thickness and remove dashed overlay at standard zoom levels

**Ticket**: [ATT-2940](https://atrainingtracker.atlassian.net/browse/ATT-2940)  
**Sub-task**: [ATT-2983](https://atrainingtracker.atlassian.net/browse/ATT-2983) (`[Verification]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2940`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Summary

Ticket `ATT-2940` addresses route polyline glanceability and visual clarity during active navigation and map inspection:
1. **Scaled Polyline Widths**:
   - Actively navigated route line width increased from `16f` to `26f` (`MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH` and `MapStyle.routeActiveNavigationWidth`).
   - Selected passive route line width increased from `10f` to `18f` (`MapVisualization.ROUTE_WIDTH` and `MapStyle.routeWidth`).
   - Unselected background route line width preserved at `6f` (`MapVisualization.ROUTE_UNSELECTED_WIDTH`) to maintain distinct visual hierarchy.
2. **Eliminated Navy Dashed Overlay Clutter**:
   - In `MapRoute`, `pattern` now evaluates to `null`.
   - `XRayPolyline` in `MapLayers.kt` bypasses the secondary overlay when `pattern == null`, rendering actively navigated and selected routes as smooth, solid, authoritative ribbons without distracting stippled midnight navy dashes.
   - Directional forward travel chevrons (`ActiveRouteDecorations`) continue to indicate course bearing on top of the prominent solid 26f ribbon.
3. **Multi-Tier Layering Invariant Preservation**:
   - Z-index stacking hierarchy is strictly preserved:
     $$\text{ROUTE\_BASE\_Z\_INDEX (20f)} < \text{ROUTE\_ACTIVE\_BASE\_Z\_INDEX (24f/26f)} < \text{CLIMB\_Z\_INDEX (28f)} < \text{SEGMENT\_Z\_INDEX (30f)} < \text{USER\_LOCATION\_Z\_INDEX (100f)}$$
   - Climbs and Strava Live Segments remain elevated above route polylines.

---

## 2. Changes Implemented

### 2.1 Map Dimension & Style Tokens (`MapModels.kt`)
* [MapModels.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapModels.kt):
  - Updated `MapStyle.routeWidth` from `10f` to `18f`.
  - Updated `MapStyle.routeActiveNavigationWidth` from `16f` to `26f`.
  - Updated `MapVisualization.ROUTE_WIDTH` from `10f` to `18f`.
  - Updated `MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH` from `16f` to `26f`.
  - Set `MapRoute.pattern` to `null`.

### 2.2 Test Suite Realignment (`MapRouteActiveNavigationTest.kt`)
* [MapRouteActiveNavigationTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapRouteActiveNavigationTest.kt):
  - Updated `testMapRoute_whenActiveNavigation_usesProminentStyling()` asserting width `26f` and `pattern == null`.
  - Updated `testMapRoute_whenSelectedPassive_usesStandardStyling()` asserting width `18f` and `pattern == null`.
  - Updated `testMapRoute_whenUnselected_usesSubordinateStyling()` asserting `pattern == null`.
  - Updated `testToMapRoute_propagatesIsActiveNavigationFlag()` asserting `18f` (passive) and `26f` (active).

---

## 3. Verification & Test Evidence

### 3.1 Targeted Unit Tests
* `MapRouteActiveNavigationTest`:
  - `testMapRoute_whenActiveNavigation_usesProminentStyling`: **PASSED**
  - `testMapRoute_whenSelectedPassive_usesStandardStyling`: **PASSED**
  - `testMapRoute_whenUnselected_usesSubordinateStyling`: **PASSED**
  - `testXRayPolylineHierarchy_preservesSegmentInterleavingInvariants`: **PASSED**
  - `testToMapRoute_propagatesIsActiveNavigationFlag`: **PASSED**

* `RouteClimbSegmentLayeringContractTest`:
  - `testMapVisualizationAndStyleConstants_zIndexHierarchy`: **PASSED**
  - `testMapSegment_patternAndIsDashedContract`: **PASSED**
  - `testClimbHighlightData_zIndexAndMapContentScopeContract`: **PASSED**
  - `testMapLayersAndRouteOnMapScreen_dashedSegmentTransparentGapContract`: **PASSED**

### 3.2 Full Regression Suite
* Executed `./gradlew testDebugUnitTest` across the entire application codebase: **100% PASS RATE** (0 regressions).

---

## 4. Requirement & Test Specification Traceability

| Requirement | Test Specification | Target File | Status |
| :--- | :--- | :--- | :--- |
| `REQ-MAP-040` | `TST-MAP-042` | `MapModels.kt`, `MapVisualization.kt` | **Verified** |

---

## 5. Invariants Maintained

* **Glanceability Under Sun**: Actively navigated routes pop with high-visibility 26f thickness.
* **No Broken Polylines**: Solid ribbon geometry replaces confusing dashed patterns.
* **Z-Ordering Integrity**: Route lines never obscure Strava segments or climb category highlights.
* **Directional Cues**: Active chevrons remain rendered seamlessly.
