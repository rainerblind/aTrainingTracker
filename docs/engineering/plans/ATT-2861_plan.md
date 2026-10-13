# Stage 3: Implementation Plan - ATT-2861: Display route details popup instead of full route navigation when tapping route card in segment view

**Ticket**: [ATT-2861](https://atrainingtracker.atlassian.net/browse/ATT-2861)  
**Sub-task**: [ATT-2897](https://atrainingtracker.atlassian.net/browse/ATT-2897) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.5`)  
**Active Sprint**: `2026-41.5`  
**Requirement Mapping**: `REQ-UI-316`  
**Test Mapping**: `TST-UI-276`  
**Branch**: `improvement/ATT-2861`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Description & Background

During the Sprint 2026-41.4 review of ATT-2585, the human user observed that tapping a containing route card in `SegmentRoutesSection` (inside `SegmentOnMapScreen`) abruptly navigates away to the full `RouteOnMapScreen`. This causes athletes to lose their inspection context in the segments view and requires cumbersome back navigation.

**User Intent**: When clicking on a route within the segments view, display a lightweight modal route detail bottom sheet (`RouteDetailSheet`) instead of navigating away to the full route screen.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-316` (*Dedicated Route Detail Bottom Sheet from Matched Segment Containing Routes View*)
  - Clause 1: Modal overlay architecture using `AppModalBottomSheet` in `StarredSegmentsScreen.kt` without unmounting `SegmentOnMapScreen`.
  - Clause 2: Header bar with route name, sport type icon indicator, and close button.
  - Clause 3: Key Route Metrics HUD with 16.dp rounded cards and `surfaceContainer` styling.
  - Clause 4: Focused map card using `ATrainingTrackerMap` with `zoomFocus = MapZoomFocus.EXPLICIT_BOUNDS` and `TTColor.RouteSelected`.
  - Clause 5: Route elevation profile card with min/max elevation indicators.
  - Clause 6: Non-destructive back navigation and dismissal.
* **Test Mapping**: `TST-UI-276` (*Dedicated Route Detail Bottom Sheet from Segment View Verification*)
  - Contract & structural verification in `RouteDetailSheetContractTest.kt`.
  - Screen overlay integration in `StarredSegmentsScreen.kt`.
  - 100% 9-language translation parity in `TranslationParityTest.kt`.
  - Clean-room regression suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing route navigation from the main Routes tab (`RouteTabbedScreen`) and segment navigation flows remain completely untouched.
2. **Back Navigation & State Integrity**: Back gestures or close clicks while `RouteDetailSheet` is open dismiss ONLY the sheet, returning the user to the active `SegmentOnMapScreen` with all segment filters, scroll positions, and state intact.
3. **UI Consistency Baseline (Rule 23)**: Follows `ClimbDetailSheet.kt` and `SegmentDetailSheet.kt` visual patterns (`RoundedCornerShape(16.dp)`, `surfaceVariant` / `surfaceContainer` elevated cards, standard typography, vector icons).
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-2861` remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `RouteDetailSheet.kt` (`com.atrainingtracker.trainingtracker.ui.routes.RouteDetailSheet`)
* Implements a reusable modal bottom sheet for route preview using `AppModalBottomSheet`:
  - `calculateRouteBounds(route: RouteWithPath): LatLngBounds?`: Bounds helper calculating tight LatLng bounds for map framing with zero-area expansion fallback.
  - `RouteDetailSheet(routeWithPath: RouteWithPath, modifier: Modifier = Modifier, onDismiss: () -> Unit)`
  - Sub-composables:
    - `RouteDetailMetricsCard`: Total distance, total ascent, and elevation range (min/max altitude) styled in 16.dp elevated card.
    - `RouteDetailMapCard`: Embeds `ATrainingTrackerMap` with `zoomFocus = MapZoomFocus.EXPLICIT_BOUNDS`, bounds fitting, and polyline in `TTColor.RouteSelected`.
    - `RouteDetailElevationProfileCard`: Embeds `ElevationProfile` with route path points, min/max altitude bounds, and climbs.

### Component 2: `StarredSegmentsScreen.kt` (`com.atrainingtracker.trainingtracker.ui.segments.segmentlist.StarredSegmentsScreen`)
* Refactors `Box(modifier = modifier)` navigation structure:
  - When `selectedSegmentId != null`, `SegmentOnMapScreen` is maintained as the persistent base screen.
  - When `inspectedRouteId != null`, `RouteDetailSheet` is rendered as an overlay composable over `SegmentOnMapScreen`.
  - BackHandler prioritizes dismissing `RouteDetailSheet` (`inspectedRouteId = null`) before navigating back to the segment list (`selectedSegmentId = null`).

### UI Consistency (Rule 23 — Mandatory for UI Changes)
* **Reference screen / component**: `SegmentDetailSheet.kt` (`com.atrainingtracker.trainingtracker.ui.segments.SegmentDetailSheet`) and `ClimbDetailSheet.kt` (`com.atrainingtracker.trainingtracker.ui.climbs.ClimbDetailSheet`).
* **Reused components**:
  - `AppModalBottomSheet` (`com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet`)
  - `ATrainingTrackerMap` (`com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMap`)
  - `ElevationProfile` (`com.atrainingtracker.trainingtracker.ui.map.ElevationProfile`)
  - `SportItem` or sport vector drawable (`R.drawable.bsport_bike`, `R.drawable.bsport_run`, `R.drawable.bsport_other`)
  - Metric vector icons (`R.drawable.ic_distance`, `R.drawable.ic_ascent`)
* **Theme tokens**:
  - Shapes: `RoundedCornerShape(16.dp)` for cards, `RoundedCornerShape(8.dp)` for sub-chips.
  - Spacing: 16.dp horizontal padding, 12.dp vertical card gap.
  - Colors: `MaterialTheme.colorScheme.surfaceVariant` for elevated card containers, `TTColor.RouteSelected` for route polyline.
* **New one-off styles & justification**: None. Reuses 100% of established Material 3 design tokens.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Author Architectural Contract Tests (`RouteDetailSheetContractTest.kt`)
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteDetailSheetContractTest.kt`
* Add structural reflection and pattern tests verifying:
  - `RouteDetailSheet` exists, is `@Composable`, and accepts `RouteWithPath` and `onDismiss`.
  - Declares `AppModalBottomSheet`.
  - Declares 16.dp rounded card shapes.
  - Embeds `ATrainingTrackerMap` with `MapZoomFocus.EXPLICIT_BOUNDS`.
  - Embeds `ElevationProfile`.
  - `calculateRouteBounds` handles non-empty points, empty fallback bounds, and zero-span points.

### Step 2: Implement `RouteDetailSheet.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteDetailSheet.kt`
* Implement `calculateRouteBounds` and `RouteDetailSheet` composable with header, metrics HUD, map viewport, and elevation profile.

### Step 3: Refactor `StarredSegmentsScreen.kt` to Render Sheet Overlay
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/segmentlist/StarredSegmentsScreen.kt`
* Remove full-screen replacement `RouteOnMapScreen`.
* Render `RouteDetailSheet` as an `AppModalBottomSheet` overlay on top of `SegmentOnMapScreen`.
* Wire back handler to clear `inspectedRouteId` first.

### Step 4: Run Targeted Unit & Contract Tests
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteDetailSheetContractTest" --tests "com.atrainingtracker.trainingtracker.ui.routes.*"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Targeted unit tests during Stage 4.
  - 9-language translation parity check (`TranslationParityTest.kt`).
  - Full clean-room regression test suite (`./gradlew testDebugUnitTest`).
* **Rollback Plan**:
  - Changes are isolated on `improvement/ATT-2861`. If unexpected regressions emerge, git reset or checkout cleanly restores prior state without affecting `sprint/2026-41.5` or `develop`.
