# Stage 5: Walkthrough & Verification - ATT-2774: Open existing Segment details popup when clicking a segment in Route Segments breakdown

**Ticket**: [ATT-2774](https://atrainingtracker.atlassian.net/browse/ATT-2774)  
**Sub-task**: [ATT-2796](https://atrainingtracker.atlassian.net/browse/ATT-2796) (`[Test]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-303` (*Dedicated Route Matched Segment Detail View with Focused Map and Zoomed Isolated Elevation Profile*)  
**Test Mapping**: `TST-UI-263` (*Route Matched Segment Detail Bottom Sheet, Focused Map, Isolated Elevation Profile & Interaction Parity Verification*)  
**Branch**: `improvement/ATT-2774`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Executive Summary & Verification Overview

This improvement ticket resolves an interaction asymmetry between route climbs and route segments within `RouteOnMapScreen`:
1. **Interactive Tap Integration**: In `RouteOnMapScreen`, clicking any matched segment card in `RouteSegmentsBreakdownSection` now triggers `onSegmentClick(matched)`, updating `selectedSegmentForDetail` state, setting `highlightedSegmentId`, and scrubbing the elevation profile cursor to the segment start distance.
2. **Dedicated Segment Detail Sheet (`SegmentDetailSheet.kt`)**: Implemented a Material 3 `AppModalBottomSheet` following the exact architectural pattern and visual language of `ClimbDetailSheet.kt`:
   - **Header Bar**: Segment name, route counter badge (`routes_segment_counter`, e.g., "1 of 3 segments"), UCI climb category chip (`ClimbCategoryChip`), athlete PR badge (`routes_segment_pr`), and close dismiss button.
   - **Metrics HUD Card**: Start distance along route (`routes_segment_start_at`), city label, and canonical `SegmentDetails` component rendering distance, Strava attribution, average/max grade, elevation gain, and min/max altitude.
   - **Focused Map Viewport**: Embedded `ATrainingTrackerMap` with `zoomFocus = MapZoomFocus.EXPLICIT_BOUNDS` mapped strictly to the segment path coordinates with start (`control_start`) and stop (`control_stop`) markers.
   - **Isolated Elevation Profile**: `ElevationProfile` rendering the segment's isolated profile and min/max altitude bounds.
3. **100% 9-Language Localization Parity**: Externalized and formatted `routes_segment_counter` ("%1$d of %2$d segments") across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
4. **Automated Verification**: Created contract tests in `SegmentDetailSheetContractTest.kt`, updated `RouteSegmentsBreakdownContractTest.kt`, verified localization in `RouteSegmentsLocalizationTest.kt`, and executed the full clean-room regression test suite (2,146 tests, 0 failures).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-303` | `TST-UI-263.1` | `RouteSegmentsBreakdownContractTest.kt` (tap interaction, state binding, sheet invocation) | **PASSED** | `Verified` |
| `REQ-UI-303` | `TST-UI-263.2` | `SegmentDetailSheetContractTest.kt` (modal structure, bounds, metrics HUD, map viewport) | **PASSED** | `Verified` |
| `REQ-UI-303` | `TST-UI-263.3` | `RouteSegmentsLocalizationTest.kt` (9-locale parity for `routes_segment_counter`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-263.4` | Clean-Room Suite `./gradlew testDebugUnitTest` (2,146 tests, 0 failures) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 2m 54s
32 actionable tasks: 12 executed, 20 up-to-date

Test Execution Verification:
- Total Test Classes: 438
- Total Tests Executed: 2,146
- Total Failures: 0
- Total Skipped: 0
- Pass Rate: 100.0%
```

### Targeted Unit & Integration Tests
```text
SegmentDetailSheetContractTest > segmentDetailSheet_declaresAppModalBottomSheet_andStructuralComponents PASSED
SegmentDetailSheetContractTest > calculateSegmentBounds_computesValidBoundingBox PASSED
SegmentDetailSheetContractTest > segmentDetailSheet_rendersClimbCategoryChip_whenCategorized PASSED
SegmentDetailSheetContractTest > segmentDetailCards_renderExpectedContent PASSED

RouteSegmentsBreakdownContractTest > routeOnMapScreen_declaresSelectedSegmentForDetail_andInvokesSegmentDetailSheet PASSED
RouteSegmentsBreakdownContractTest > routeSegmentsBreakdownSection_providesOnSegmentClickCallback PASSED
RouteSegmentsBreakdownContractTest > routeSegmentsBreakdownSection_rendersCardsWithExpectedData PASSED
RouteSegmentsBreakdownContractTest > routeSegmentsBreakdownSection_handlesClimbsAndSegmentsCoexistence PASSED

RouteSegmentsLocalizationTest > testRouteSegmentsStrings_haveCompleteParityAcrossAll9Locales PASSED

BUILD SUCCESSFUL in 13s
```

---

## 4. Hardware / Physical Verification (Pixel 10)

- Deployed debug APK to Pixel 10 via `./gradlew installDebug` (Installed on `Pixel 10 - 17`).
- Verified app launch via `adb shell monkey -p com.atrainingtracker.debug -c android.intent.category.LAUNCHER 1` (Process PID: 9205).
- Inspected Logcat: zero crashes, zero ANRs, zero layout inflation exceptions.

### Visual Consistency (Rule 23)
- **Reference Screen**: `ClimbDetailSheet.kt` (referenced in Stage 3 Implementation Plan `ATT-2774_plan.md` §3).
- **Parity Audit**:
  - Modal container: Both use Material 3 `AppModalBottomSheet` with drag handle.
  - Header: Counter chip ("1 of 3 segments" vs "Climb 1 of 4"), category chip (`ClimbCategoryChip`), and close button match identically in shape, spacing (16 dp horizontal, 8 dp bottom), and typography (`titleMedium`).
  - Cards: `ElevatedCard` containers with 12 dp rounded corners and `surfaceVariant` styling.
  - Map Viewport: `ATrainingTrackerMap` with `MapZoomFocus.EXPLICIT_BOUNDS` and start/stop markers (`control_start` and `control_stop`).
  - Checked against `docs/design_guidelines.md` §5: shapes [x] spacing [x] colors/themes [x] typography/icons [x] placement [x].
  - Deviations & justification: None; full visual and behavioral parity achieved.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Full test suite passed with 2,146 tests executed and 0 failures.
2. **Living Documentation Synchronized**: Status of `REQ-UI-303` in `docs/requirements.md` and `TST-UI-263` in `docs/tests.md` updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask `ATT-2796` submitted for Gate 5 audit and transitioned to `Erledigt`.
4. **Parent Ticket Final Review**: Parent ticket `ATT-2774` transitioned to `Final Review (Human)` for final release sign-off.
5. **Continuous Sprint Branch Integration**: Feature branch `improvement/ATT-2774` cleanly merged into `sprint/2026-41.4`.
