# Stage 5: Walkthrough & Verification - ATT-2861: Display route details popup instead of full route navigation when tapping route card in segment view

**Ticket**: [ATT-2861](https://atrainingtracker.atlassian.net/browse/ATT-2861)  
**Sub-task**: [ATT-2899](https://atrainingtracker.atlassian.net/browse/ATT-2899) (`[Test]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.5`)  
**Active Sprint**: `2026-41.5`  
**Requirement Mapping**: `REQ-UI-316` (*Dedicated Route Detail Bottom Sheet from Matched Segment Containing Routes View*)  
**Test Mapping**: `TST-UI-276` (*Dedicated Route Detail Bottom Sheet from Segment View Verification*)  
**Branch**: `improvement/ATT-2861`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Summary & Verification Overview

This improvement ticket replaces the previous disruptive full-screen navigation from the segment view with a lightweight, non-destructive modal bottom sheet (`RouteDetailSheet`):
1. **Lightweight Modal Sheet Overlay (`RouteDetailSheet.kt`)**:
   - Implemented `RouteDetailSheet` using `AppModalBottomSheet` following the Material 3 design patterns established in `ClimbDetailSheet.kt` and `SegmentDetailSheet.kt`.
   - Header displays the route name, sport type indicator icon (`summary.bSportType.iconResId`), and dedicated close button.
   - Key Route Metrics HUD displays total distance (`ic_distance`), total ascent (`ic_ascent`), and altitude range in 16.dp rounded elevated cards (`surfaceVariant` / `surfaceContainer`).
   - Focused Map Viewport embeds `ATrainingTrackerMap` tightly framed via `MapZoomFocus.EXPLICIT_BOUNDS` and `calculateRouteBounds(route)`.
   - Route Elevation Profile embeds `ElevationProfile` with route altitude points and climbs.
2. **Non-Destructive Screen Navigation (`StarredSegmentsScreen.kt`)**:
   - Refactored `StarredSegmentsScreen` so `SegmentOnMapScreen` remains mounted and intact in the background.
   - Tapping a containing route card in `SegmentRoutesSection` sets `inspectedRouteId`, rendering `RouteDetailSheet` as a modal overlay on top of the segment view.
   - Back handling dismisses the route detail sheet first (`inspectedRouteId = null`) without resetting or reloading the segment map screen.
3. **Automated Verification**:
   - Created `RouteDetailSheetContractTest.kt` verifying composable existence, `AppModalBottomSheet` usage, 16.dp cards, explicit bounds, and geodesic bounds boundary cases.
   - Updated `SegmentRoutesSectionContractTest.kt` asserting `RouteDetailSheet` modal overlay presentation.
   - Verified 100% 9-language translation parity in `TranslationParityTest.kt`.
   - Clean-room regression suite (`./gradlew testDebugUnitTest`): 100% pass rate with 0 regressions.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-316.1` | `TST-UI-276.1` | `RouteDetailSheetContractTest.kt` (Structural tokens & contracts) | **PASSED** | `Verified` |
| `REQ-UI-316.2` | `TST-UI-276.1` | `RouteDetailSheetContractTest.kt` (Header & sport icon) | **PASSED** | `Verified` |
| `REQ-UI-316.3` | `TST-UI-276.1` | `RouteDetailSheetContractTest.kt` (Metrics HUD & 16.dp cards) | **PASSED** | `Verified` |
| `REQ-UI-316.4` | `TST-UI-276.1` | `RouteDetailSheetContractTest.kt` (Map card & `calculateRouteBounds`) | **PASSED** | `Verified` |
| `REQ-UI-316.5` | `TST-UI-276.1` | `RouteDetailSheetContractTest.kt` (Elevation profile card) | **PASSED** | `Verified` |
| `REQ-UI-316.6` | `TST-UI-276.2` | `SegmentRoutesSectionContractTest.kt` (`StarredSegmentsScreen` overlay) | **PASSED** | `Verified` |
| `REQ-UI-316` | `TST-UI-276.3` | `TranslationParityTest.kt` (9-language translation parity) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-276.4` | Clean-room suite `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
BUILD SUCCESSFUL in 2m 45s
32 actionable tasks: 12 executed, 20 up-to-date
```

### Targeted Unit & Contract Tests
```text
RouteDetailSheetContractTest > testRouteDetailSheet_composablesExistAndArePublic PASSED
RouteDetailSheetContractTest > testRouteDetailSheet_structuralTokensAndContracts PASSED
RouteDetailSheetContractTest > testCalculateRouteBounds_validRoute_computesCorrectBounds PASSED
RouteDetailSheetContractTest > testCalculateRouteBounds_emptyPath_fallbackToSummary PASSED
RouteDetailSheetContractTest > testCalculateRouteBounds_singlePoint_expandsBounds PASSED
RouteDetailSheetContractTest > testCalculateRouteBounds_emptyEverything_returnsNull PASSED

SegmentRoutesSectionContractTest > testStarredSegmentsScreen_routeDetailSheetOverlay PASSED
SegmentRoutesSectionContractTest > testSegmentOnMapScreen_integratesCandidateRoutesAndAnalyticsContent PASSED
SegmentRoutesSectionContractTest > testSegmentRoutesSection_structuralTokensAndContracts PASSED

TranslationParityTest > testAllTranslationsComplete PASSED
```

---

## 4. UI Consistency & Verification (Rule 23)

* **Reference Screen**: `SegmentDetailSheet.kt` (`com.atrainingtracker.trainingtracker.ui.segments.SegmentDetailSheet`) and `ClimbDetailSheet.kt` (`com.atrainingtracker.trainingtracker.ui.climbs.ClimbDetailSheet`).
* **Consistency Check against `docs/design_guidelines.md` §5**:
  - Shapes: `RoundedCornerShape(16.dp)` applied to all elevated cards (`MetricsCard`, `MapCard`, `ElevationProfileCard`).
  - Container colors: `MaterialTheme.colorScheme.surfaceVariant` for elevated cards, matching `ClimbDetailSheet`.
  - Typography: `MaterialTheme.typography.titleMedium` for values, `MaterialTheme.typography.labelSmall` for secondary labels.
  - Iconography: Vector drawables `R.drawable.ic_distance` and `R.drawable.ic_ascent` with primary color tinting.
  - Map Viewport: `MapZoomFocus.EXPLICIT_BOUNDS` with clean Start/Finish markers.
  - Bottom Sheet Container: `AppModalBottomSheet` with header close button and title.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Full clean-room test suite passed with 100% success rate.
2. **Living Documentation Synchronized**: `REQ-UI-316` in `docs/requirements.md` and `TST-UI-276` in `docs/tests.md` updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask `ATT-2899` transitioned directly to `Erledigt` via `freigabe`.
4. **Parent Ticket Handover**: Parent ticket `ATT-2861` transitioned to `Final Review (Human)`.
5. **Continuous Sprint Integration (Strategy A)**: Branch `improvement/ATT-2861` merged into `sprint/2026-41.5` via `--no-ff`.
