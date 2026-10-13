# Stage 2: Requirement & Test Specification - ATT-2861: Display route details popup instead of full route navigation when tapping route card in segment view

**Ticket**: [ATT-2861](https://atrainingtracker.atlassian.net/browse/ATT-2861)  
**Sub-task**: [ATT-2896](https://atrainingtracker.atlassian.net/browse/ATT-2896) (`[Test-Spec]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.5`)  
**Active Sprint**: `2026-41.5`  
**Requirement Mapping**: `REQ-UI-316`  
**Test Mapping**: `TST-UI-276`  
**Branch**: `improvement/ATT-2861`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Formal Requirement Specification (`REQ-UI-316`)

### `REQ-UI-316`: Dedicated Route Detail Bottom Sheet from Matched Segment Containing Routes View
* **Clause 1 (Modal Presentation)**: When an athlete taps any containing route card inside `SegmentRoutesSection` (within `SegmentOnMapScreen`), the application SHALL display a modal `RouteDetailSheet` bottom sheet overlay using `AppModalBottomSheet`. It SHALL NOT navigate away from or unmount the underlying `SegmentOnMapScreen`.
* **Clause 2 (Header & Sport Identification)**: The `RouteDetailSheet` SHALL render a header bar displaying the route name, sport type indicator icon (`SportItem` / sport icon), and a dedicated close button.
* **Clause 3 (Key Route Metrics HUD)**: The sheet SHALL render a metrics card with `RoundedCornerShape(16.dp)` and `surfaceContainer` styling, displaying total distance, total elevation gain, and altitude bounds (min/max elevation).
* **Clause 4 (Focused Map Viewport)**: The sheet SHALL render a focused map card displaying `ATrainingTrackerMap` configured with `zoomFocus = MapZoomFocus.EXPLICIT_BOUNDS` bounding the entire route polyline, rendered in `TTColor.RouteSelected` with start and finish markers.
* **Clause 5 (Route Elevation Profile)**: The sheet SHALL render the route's elevation profile card with min/max elevation indicators and total distance axis.
* **Clause 6 (Non-Destructive Back & Dismiss Behavior)**: Dismissing the bottom sheet (via drag down, scrim tap, close icon, or system Back press) SHALL return the athlete immediately to the active segment map view without resetting segment state or reloading the segment list.

---

## 2. Given-When-Then Acceptance Criteria

* **Scenario 1: Tapping a containing route opens RouteDetailSheet modal**
  * **Given** an athlete is viewing a segment on `SegmentOnMapScreen` that is contained within one or more saved routes.
  * **When** the athlete taps a route card in `SegmentRoutesSection`.
  * **Then** `RouteDetailSheet` appears as a bottom sheet modal overlay.
  * **And** `SegmentOnMapScreen` remains active in the background.

* **Scenario 2: RouteDetailSheet renders metrics, map, and profile**
  * **Given** `RouteDetailSheet` is displayed for a route with distance 42.5 km and ascent 520 m.
  * **Then** the header shows the route name and sport icon.
  * **And** the metrics card displays formatted distance and ascent.
  * **And** the map card renders the route polyline within explicit bounds.
  * **And** the elevation profile displays the route altitude graph.

* **Scenario 3: Dismissing sheet returns to Segment view**
  * **Given** `RouteDetailSheet` is open.
  * **When** the user taps the close button, drags the sheet down, or triggers system Back.
  * **Then** `RouteDetailSheet` dismisses.
  * **And** the user remains on the segment map screen with all state intact.

---

## 3. Test Specification (`TST-UI-276`)

### TST-UI-276.1: RouteDetailSheet Contract & Structural Verification (`RouteDetailSheetContractTest.kt`)
* Verify `RouteDetailSheet` composable exists and accepts `RouteWithPath`, `onDismiss`, and `modifier`.
* Verify `RouteDetailSheet` declares `AppModalBottomSheet`.
* Verify Header contains title, close icon button, and sport representation.
* Verify Metrics card declares `RoundedCornerShape(16.dp)`.
* Verify Map card declares `MapZoomFocus.EXPLICIT_BOUNDS` and embeds route path polyline.
* Verify Elevation profile card is rendered.

### TST-UI-276.2: StarredSegmentsScreen Overlay Navigation Verification
* Verify `inspectedRouteId` state opens `RouteDetailSheet` as a modal overlay instead of switching screen root to `RouteOnMapScreen`.
* Verify back handling dismisses `RouteDetailSheet` when open (`inspectedRouteId = null`).

### TST-UI-276.3: 9-Language Localization Audit (`TranslationParityTest.kt`)
* Verify any newly introduced string keys exist across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with non-empty translations and identical format specifiers.

### TST-UI-276.4: Full Clean-Room Regression Test Suite
* Execute `./gradlew testDebugUnitTest` and assert 100% test pass rate with 0 regressions.

---

## 4. Traceability Matrix

| Requirement | Test Spec | Target Test Class / File | Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-316.1` | `TST-UI-276.1` | `RouteDetailSheetContractTest.kt` | Planned |
| `REQ-UI-316.2` | `TST-UI-276.1` | `RouteDetailSheetContractTest.kt` | Planned |
| `REQ-UI-316.3` | `TST-UI-276.1` | `RouteDetailSheetContractTest.kt` | Planned |
| `REQ-UI-316.4` | `TST-UI-276.1` | `RouteDetailSheetContractTest.kt` | Planned |
| `REQ-UI-316.5` | `TST-UI-276.1` | `RouteDetailSheetContractTest.kt` | Planned |
| `REQ-UI-316.6` | `TST-UI-276.2` | `StarredSegmentsScreenContractTest.kt` | Planned |
| `REQ-UI-316` | `TST-UI-276.3` | `TranslationParityTest.kt` | Planned |
| `REQ-PRO-001` | `TST-UI-276.4` | Full Clean-Room Suite (`testDebugUnitTest`) | Planned |
