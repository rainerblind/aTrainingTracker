# Stage 3: Implementation Plan - ATT-2860: Visual and UX styling polish for Climb and Segment detail bottom sheets

**Ticket**: [ATT-2860](https://atrainingtracker.atlassian.net/browse/ATT-2860)  
**Sub-task**: [ATT-2951](https://atrainingtracker.atlassian.net/browse/ATT-2951) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.6`  
**Requirement Mapping**: `REQ-UI-315`  
**Test Mapping**: `TST-UI-275`  
**Branch**: `feature/ATT-2860`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Description & Background

During Sprint 2026-41.5 review, the human user rejected the initial card-stack polish for `SegmentDetailSheet` and `ClimbDetailSheet`:
> *"The segment popup must look identical to the one that pops up in the map. We should use the same code here. The route popup should follow this layout."*

Currently:
1. Tapping a segment on the general map (`MapScreenWithTrack.kt`) displays `SegmentOnMapScreen`, which leverages `MapDetailLayout` with `SegmentHeader`, `HorizontalDivider`, `SegmentDetails`, an interactive Google Map (`ATrainingTrackerMap`) with elevation scrubbing, and containing routes breakdown.
2. Tapping a segment in the route breakdown list (`RouteOnMapScreen.kt`) opened `SegmentDetailSheet.kt`, which rendered an ad-hoc 3-card stack (`SegmentDetailMetricsCard`, `SegmentDetailMapCard`, `SegmentDetailElevationProfileCard`) inside a scrolling `AppModalBottomSheet`.
3. Tapping a route in the segment routes list opened `RouteDetailSheet.kt`, which copied this separate 3-card column instead of reusing `RouteOnMapScreen.kt`.
4. `ClimbDetailSheet.kt` also duplicated this disconnected 3-card column pattern.

This plan details the SWE.2 architecture and atomic construction steps to unify modal entity inspection across segments, routes, and climbs by directly reusing `SegmentOnMapScreen` and `RouteOnMapScreen` via `MapDetailLayout`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-315` (*Harmonized Visual & UX Architecture for Climb, Segment, and Route Detail Bottom Sheets via MapDetailLayout*)
* **Test Mapping**: `TST-UI-275` (*Harmonized Visual & UX Architecture for Climb, Segment, and Route Detail Bottom Sheets via MapDetailLayout Verification*)
  * `TST-UI-275.1`: `SegmentDetailSheetContractTest` (SegmentDetailSheet reuses SegmentOnMapScreen / MapDetailLayout)
  * `TST-UI-275.2`: `RouteDetailSheetContractTest` (RouteDetailSheet reuses RouteOnMapScreen / MapDetailLayout)
  * `TST-UI-275.3`: `ClimbDetailSheetContractTest` (ClimbDetailSheet layout alignment)
  * `TST-UI-275.4`: `TranslationParityTest` (9-language localization audit)
  * `TST-UI-275.5`: `./gradlew testDebugUnitTest` (Clean-room full-suite regression)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing route management, climb detection, and segment matching algorithms remain completely untouched.
2. **Gesture & Touch Decoupling**: Placing `MapDetailLayout` in modal sheets with non-scrolling containers prevents vertical scroll gesture collisions with map pan/zoom and elevation scrub bars.
3. **Seamless Non-Destructive Dismissal**: Dismissing a modal detail sheet via close button, drag handle gesture, or scrim tap restores the caller screen with scroll position and selection state intact.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent ticket ATT-2860 remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `SegmentDetailSheet.kt`
* Refactor `SegmentDetailSheet` to wrap `SegmentOnMapScreen` within a full-height non-scrolling modal bottom sheet (`ModalBottomSheet(sheetState = ..., dragHandle = null)` or `AppModalBottomSheet(scrollable = false, dragHandle = null)`).
* Map `matchedSegment` to:
  * `segmentSummary = matchedSegment.segment.summary`
  * `segment = MapSegment(matchedSegment.segment)`
* Provide an overlay close/dismiss button so the athlete can dismiss the modal sheet with one tap.
* Delete obsolete card composables (`SegmentDetailMetricsCard`, `SegmentDetailMapCard`, `SegmentDetailElevationProfileCard`, `SegmentDetailMetricItem`).

### Component 2: `RouteDetailSheet.kt`
* Refactor `RouteDetailSheet` to wrap `RouteOnMapScreen` within a full-height non-scrolling modal bottom sheet.
* Pass `route = routeWithPath`, `routeSummary = routeWithPath.summary`, and provide an overlay close/dismiss button.
* Delete obsolete card composables (`RouteDetailMetricsCard`, `RouteDetailMapCard`, `RouteDetailElevationProfileCard`, `RouteDetailMetricItem`).

### Component 3: `ClimbDetailSheet.kt`
* Align `ClimbDetailSheet` with this unified architecture, implementing `ClimbHeader` and `ClimbDetails` rows over `MapDetailLayout` with climb category coloring and elevation profile.
* Delete obsolete isolated card composables.

### UI Consistency (Rule 23 — mandatory)
* **Reference screen / component**: `SegmentOnMapScreen.kt` and `RouteOnMapScreen.kt` (using `MapDetailLayout.kt`).
* **Reused components**: `SegmentOnMapScreen`, `RouteOnMapScreen`, `SegmentHeader`, `SegmentDetails`, `RouteHeader`, `RouteDetails`, `MapDetailLayout`, `ATrainingTrackerMap`, `ElevationProfile`.
* **Theme tokens**: `BottomSheetDesign.SheetShape`, `BottomSheetDesign.SheetShadowElevation`, `BottomSheetDesign.SheetTonalElevation`, `MaterialTheme.colorScheme.surface`, `TTColor.StravaOrange`, `TTColor.RouteSelected`.
* **New one-off styles & justification**: None. 100% direct reuse of existing canonical map popup components, fulfilling the user's explicit mandate for identical layout and code reuse.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Refactor `SegmentDetailSheet.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentDetailSheet.kt`
* Changes:
  1. Remove obsolete card composables (`SegmentDetailMetricsCard`, `SegmentDetailMapCard`, `SegmentDetailElevationProfileCard`).
  2. Implement `SegmentDetailSheet` hosting `SegmentOnMapScreen` with close button and system window insets.
* Targeted Test: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.segments.SegmentDetailSheetContractTest"`

### Step 2: Refactor `RouteDetailSheet.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteDetailSheet.kt`
* Changes:
  1. Remove obsolete card composables (`RouteDetailMetricsCard`, `RouteDetailMapCard`, `RouteDetailElevationProfileCard`).
  2. Implement `RouteDetailSheet` hosting `RouteOnMapScreen` with close button and system window insets.
* Targeted Test: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteDetailSheetContractTest"`

### Step 3: Align `ClimbDetailSheet.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/ClimbDetailSheet.kt`
* Changes:
  1. Refactor `ClimbDetailSheet` to present structured `ClimbHeader` + `ClimbDetails` over `MapDetailLayout`.
* Targeted Test: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.climbs.ClimbDetailSheetContractTest"`

### Step 4: Update Contract Tests
* Files:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentDetailSheetContractTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteDetailSheetContractTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/climbs/ClimbDetailSheetContractTest.kt`
* Changes:
  1. Update structural assertions to assert usage of `SegmentOnMapScreen`, `RouteOnMapScreen`, and `MapDetailLayout`.
  2. Assert absence of obsolete card composables.
* Targeted Test: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.segments.*" --tests "com.atrainingtracker.trainingtracker.ui.routes.*" --tests "com.atrainingtracker.trainingtracker.ui.climbs.*"`

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted tests after each atomic step, followed by `./gradlew testDebugUnitTest` in Stage 5 clean-room regression.
* **Rollback**: Work is isolated on `feature/ATT-2860`. In case of blockers, `git checkout sprint/2026-41.6` allows clean rollback.
