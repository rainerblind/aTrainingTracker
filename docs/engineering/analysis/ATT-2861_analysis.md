# Stage 1 Analysis: ATT-2861 - Display route details popup instead of full route navigation when tapping route card in segment view

**Ticket**: [ATT-2861](https://atrainingtracker.atlassian.net/browse/ATT-2861)  
**Sub-task**: [ATT-2895](https://atrainingtracker.atlassian.net/browse/ATT-2895) (`[Analysis]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.5`)  
**Active Sprint**: `2026-41.5`  
**Branch**: `improvement/ATT-2861`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

During the review of ATT-2585 (*Show saved routes containing segment*), the human user observed that tapping a containing route card in `SegmentRoutesSection` (within `SegmentOnMapScreen`) navigates away to the full `RouteOnMapScreen`:
* **Observed Disruption**: The athlete loses context of the segment they are currently inspecting. Back navigation is cumbersome and disorients the user.
* **Target Behavior**: Tapping a containing route item should open a lightweight modal bottom sheet (`RouteDetailSheet`) directly over the segment view, displaying the route's key metrics, focused map overview, and elevation profile, while preserving the underlying segment inspection session.

---

## 2. Root Cause Analysis (Forensic Investigation)

In `StarredSegmentsScreen.kt` (lines 65–85):
```kotlin
if (inspectedRouteId != null) {
    val inspectedRoute = allRoutes.find { it.summary.id == inspectedRouteId }
    if (inspectedRoute != null) {
        ...
        RouteOnMapScreen(
            route = inspectedRoute.toMapRoute(),
            routeSummary = inspectedRoute.summary,
            ...
        )
    }
}
```
* `inspectedRouteId` replaced the entire view tree with `RouteOnMapScreen` instead of rendering a modal overlay.
* In `RouteOnMapScreen`, full route controls, navigation buttons, and sub-views are loaded, which is unnecessarily heavy for simply previewing how a segment sits inside a containing route.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Create `RouteDetailSheet.kt` using `AppModalBottomSheet` following the design language established in `ClimbDetailSheet.kt` and `SegmentDetailSheet.kt` (16.dp rounded cards, `surfaceContainer` colors, vector icons).
  2. Display route header with route name, sport type badge, and close button.
  3. Display route metrics HUD (distance, elevation gain, estimated duration/speed).
  4. Display focused map card with route path polyline, start/stop markers, and explicit bounds fitting.
  5. Display route elevation profile.
  6. In `StarredSegmentsScreen.kt`, keep `SegmentOnMapScreen` rendered and display `RouteDetailSheet` as an overlay whenever `inspectedRouteId != null`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Editing or modifying route geometry.
  * Altering the main `RouteOnMapScreen` navigation flow from the Routes tab.
  * Adding interactive segment selection inside the route detail sheet.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Audit**: Net-new requirement only (`REQ-UI-316`). No existing requirements modified.
* **Invariant**: The route preview retains read-only isolation and does not alter the athlete's active segment state or route database records.

---

## 5. Architectural Strategy & High-Level Solution

1. **New Component**: `com.atrainingtracker.trainingtracker.ui.routes.RouteDetailSheet`:
   - Wraps content in `AppModalBottomSheet(onDismissRequest = onDismiss)`.
   - Structural sub-composables:
     - `RouteDetailHeader`: title, sport icon, close button.
     - `RouteDetailMetricsCard`: distance, ascent, altitude range.
     - `RouteDetailMapCard`: `ATrainingTrackerMap` with `MapZoomFocus.EXPLICIT_BOUNDS` and single route polyline.
     - `RouteDetailElevationProfile`: isolated route elevation profile.
2. **Integration in `StarredSegmentsScreen.kt`**:
   - Maintain `inspectedRouteId` state.
   - When `inspectedRouteId != null`, fetch `inspectedRoute = allRoutes.find { it.summary.id == inspectedRouteId }`.
   - Render `RouteDetailSheet(route = inspectedRoute, onDismiss = { inspectedRouteId = null })` over `SegmentOnMapScreen`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing segment or route browsing flows.
  2. 100% 9-language translation parity for any new strings.
  3. Seamless back handler support (back press dismisses the modal sheet first).
  4. 100% unit and contract test coverage with clean-room regression pass.
