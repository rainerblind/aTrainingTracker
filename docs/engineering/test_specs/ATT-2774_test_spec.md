# Stage 2: Requirement & Test Specification - ATT-2774: Open Existing Segment Details Popup When Clicking a Segment in Route Segments Breakdown

**Ticket**: [ATT-2774](https://atrainingtracker.atlassian.net/browse/ATT-2774)  
**Sub-task**: [ATT-2793](https://atrainingtracker.atlassian.net/browse/ATT-2793) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-303` (*Dedicated Route Matched Segment Detail View with Focused Map and Zoomed Isolated Elevation Profile*)  
**Test Spec ID**: `TST-UI-263` (*Route Matched Segment Detail Bottom Sheet, Focused Map, Isolated Elevation Profile & Interaction Parity Verification*)  
**Branch**: `improvement/ATT-2774`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  
**Status**: Ready for Gate 2 Review  

---

## 1. Requirement Specification (REQ-UI-303)

### 1.1 Problem Statement & Rationale
In the current implementation of `RouteOnMapScreen` (introduced in `ATT-2583`), tapping a climb item in `RouteClimbsBreakdownSection` opens a dedicated, rich modal detail sheet (`ClimbDetailSheet`) showcasing the climb's counter, category chip, telemetry HUD, focused map, and isolated elevation profile.

In contrast, tapping a matched segment card in `RouteSegmentsBreakdownSection` only toggles polyline highlighting and scrubs the parent elevation profile, completely omitting any detail presentation. Athletes cannot inspect segment metrics (such as min/max altitude, average and max gradients, Strava source branding, or detailed geographic start/stop locations) from within the route view.

`REQ-UI-303` eliminates this interaction asymmetry by establishing full visual and functional parity between route climbs and route segments.

---

### 1.2 Functional & Architectural Requirements

1. **Interactive Segment Item Tap & State Management (`RouteOnMapScreen.kt`)**:
   - `RouteOnMapScreen` SHALL maintain a mutable state variable `var selectedSegmentForDetail by remember { mutableStateOf<MatchedRouteSegment?>(null) }`.
   - In `RouteSegmentsBreakdownSection`, tapping any matched segment card SHALL invoke `onSegmentClick(matched)`, which:
     - Sets `selectedSegmentForDetail = matched`.
     - Sets `highlightedSegmentId = matched.segment.summary.stravaId`.
     - Sets `externalScrubDistance = matched.startDistanceMeters`.

2. **Dedicated Segment Detail Sheet Architecture (`SegmentDetailSheet.kt`)**:
   - When `selectedSegmentForDetail != null`, `RouteOnMapScreen` SHALL present `SegmentDetailSheet` as a Material 3 `AppModalBottomSheet`.
   - `SegmentDetailSheet` SHALL provide:
     - **Header Bar**:
       - Segment name as primary title (`summary.name.ifBlank { stringResource(R.string.strava_segments_title) }`).
       - Close/dismiss action button (`showCloseButton = true`).
       - Route counter badge (e.g., `Surface` displaying `stringResource(R.string.routes_segment_counter, routeIndex, totalRouteSegments)`).
       - Climb category badge (`ClimbCategoryChip`) if the segment is categorized as a climb.
       - Athlete PR badge (`MetricBadge` displaying `stringResource(R.string.routes_segment_pr, summary.prTime)`) if `prTime` is populated.
     - **Key Metrics HUD Card**:
       - An `ElevatedCard` (12.dp rounded corners, `surfaceVariant`) rendering:
         - Route start distance badge (`stringResource(R.string.routes_segment_start_at, ...)`).
         - City/location label (`summary.city`).
         - Horizontal divider (`0.5.dp`, `outlineVariant`).
         - Canonical `SegmentDetails` component (`SegmentDetails(summary = summary, showStravaLogo = true)`), exposing distance, Strava branding, average/max grade, elevation gain, and min/max altitude.
     - **Focused Map Viewport Card**:
       - An `ElevatedCard` embedding `ATrainingTrackerMap` with `zoomFocus = MapZoomFocus.EXPLICIT_BOUNDS` set to the segment's geographical bounding box.
       - Renders the segment path with distinct start marker (`R.drawable.control_start`, `TTColor.StartPoint`) and finish marker (`R.drawable.control_stop`, `TTColor.EndPoint`).
     - **Isolated Elevation Profile Card**:
       - An `ElevatedCard` rendering `ElevationProfile(pathPoints = segment.path, currentDistance = null)` with min and max altitude annotations.

3. **State Isolation & Dismissal Parity**:
   - Dismissing `SegmentDetailSheet` (via close button, drag handle gesture, or system back press) SHALL reset `selectedSegmentForDetail = null`.
   - Dismissal SHALL NOT reset route selection, map center, route bounds, or breakdown scroll position in `RouteOnMapScreen`.

4. **100% 9-Language Localization Parity**:
   - The new string resource `routes_segment_counter` SHALL exist and declare valid format specifiers across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

---

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Segment Tap Opens Detail Sheet)**:
  * *Given* an athlete viewing a route with matched segments in `RouteOnMapScreen`,
  * *When* tapping any matched segment card in `RouteSegmentsBreakdownSection`,
  * *Then* `SegmentDetailSheet` SHALL open as a modal bottom sheet, displaying the segment title, route counter, telemetry metrics, focused map, and isolated elevation profile.

* **Criterion 2 (Visual Parity with Climb Detail Sheet)**:
  * *Given* `SegmentDetailSheet` open for a matched segment,
  * *When* observing the rendered layout,
  * *Then* the sheet SHALL match the architectural structure of `ClimbDetailSheet`, including an `AppModalBottomSheet` container, header counter badge, metrics card, map card with start/end markers, and isolated elevation profile card.

* **Criterion 3 (Safe Dismissal & State Retention)**:
  * *Given* `SegmentDetailSheet` open in `RouteOnMapScreen`,
  * *When* tapping the close button or swiping down to dismiss,
  * *Then* the sheet SHALL close cleanly, and `RouteOnMapScreen` SHALL retain its route polyline, highlighted segment, and breakdown tab selection.

* **Criterion 4 (9-Language Parity)**:
  * *Given* all 9 supported application locales,
  * *When* evaluating `routes_segment_counter`,
  * *Then* the string SHALL be present, non-empty, and contain `%1$d` and `%2$d` tokens in all 9 XML resources.

---

### 1.4 System Invariants
- `RouteClimbsBreakdownSection` and `ClimbDetailSheet` (REQ-UI-300) remain 100% untouched and functional.
- Route selection toggle and GPX export invariants remain intact.
- Zero regressions across the full clean-room unit test suite (`./gradlew testDebugUnitTest`).

---

## 2. Test Specification (TST-UI-263)

### Test Case 1: `RouteSegmentsBreakdownContractTest` (`TST-UI-263.1`)
* **Scope**: Architectural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSegmentsBreakdownContractTest.kt`
* **Preconditions**: Project source tree available.
* **Assertions**:
  - `RouteOnMapScreen.kt` declares `selectedSegmentForDetail` state variable.
  - `RouteSegmentsBreakdownSection` invocation in `RouteOnMapScreen.kt` assigns `selectedSegmentForDetail = matched`.
  - `RouteOnMapScreen.kt` invokes `SegmentDetailSheet` when `selectedSegmentForDetail != null`.

### Test Case 2: `SegmentDetailSheetContractTest` (`TST-UI-263.2`)
* **Scope**: Unit & Architectural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentDetailSheetContractTest.kt`
* **Preconditions**: `SegmentDetailSheet.kt` exists.
* **Assertions**:
  - `SegmentDetailSheet` composable exists and is public.
  - Declares parameters: `matchedSegment: MatchedRouteSegment`, `routeIndex: Int?`, `totalRouteSegments: Int?`, `bSportType: BSportType`, `onDismiss: () -> Unit`.
  - Embeds `AppModalBottomSheet`, `SegmentDetails`, `ATrainingTrackerMap`, and `ElevationProfile`.
  - References `R.string.routes_segment_counter`, `R.string.routes_segment_start_at`, `R.string.routes_segment_pr`, `R.string.strava_segments_title`.

### Test Case 3: 9-Language Localization Parity Audit (`TST-UI-263.3`)
* **Scope**: Automated Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSegmentsLocalizationTest.kt`
* **Preconditions**: All 9 `strings.xml` files populated.
* **Assertions**:
  - `routes_segment_counter` exists in all 9 locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  - Contains valid positional formatting specifiers `%1$d` and `%2$d`.
  - Immutability and cached access verified via `LocalizationTestCache`.

### Test Case 4: Full Clean-Room Regression Suite (`TST-UI-263.4`)
* **Scope**: Full Regression
* **Command**: `./gradlew testDebugUnitTest`
* **Assertions**: 100% pass rate across all 438+ test classes, 0 failures, 0 skipped.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-263.1` | Contract | `RouteOnMapScreen.kt` | `REQ-UI-303.1` | Specified |
| `TST-UI-263.2` | Contract | `SegmentDetailSheet.kt` | `REQ-UI-303.2`, `REQ-UI-303.3` | Specified |
| `TST-UI-263.3` | Localization | `strings.xml` (9 locales) | `REQ-UI-303.4` | Specified |
| `TST-UI-263.4` | Clean-Room Suite | Entire application | `REQ-UI-303.5` | Specified |
