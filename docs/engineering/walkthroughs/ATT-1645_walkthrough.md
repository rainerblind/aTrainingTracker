# Stage 5: Walkthrough & Verification - ATT-1645: Calibrate and Optimize Initial Height & Peek Baselines for Popups and Bottom Sheets

**Ticket**: [ATT-1645](https://rainerblind.atlassian.net/browse/ATT-1645)  
**Sub-task**: [ATT-1965](https://rainerblind.atlassian.net/browse/ATT-1965) (`[Test]`)  
**Parent Epic**: [ATT-180](https://rainerblind.atlassian.net/browse/ATT-180) (*Design System: Global Consistency, Typography & Tokens*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.9`  
**Requirement Mapping**: `REQ-UI-221` (*UI/Sheets: Standardized Bottom Sheet Peek Height Baselines and Information Footprint Framing*)  
**Test Mapping**: `TST-UI-175`  
**Branch**: `feature/ATT-1645`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Executive Summary & Verification Overview

Ticket ATT-1645 establishes a centralized, standardized token hierarchy for modal and persistent bottom sheet peek heights and initial display baselines in `BottomSheetDesign.kt`, eliminating fragmented hardcoded values (such as `120.dp`, `150.dp`, `180.dp`) across map and tracking screens. In this revision cycle for Sprint `2026-40.9`, tokens were recalibrated following physical device testing on the Pixel 10:

1. **Recalibrated Centralized Baseline Tokens in `BottomSheetDesign.kt`**:
   - `PeekHeightWorkout = 140.dp`: Calibrated baseline for single workout detail peeks in analytical map screens (`WorkoutClusterHeatmapScreen`, `PeriodMapScreen`), cleanly framing `WorkoutHeader` (Sport Icon, Title, Cluster button, Sport Type/Equipment, Date/Time).
   - `PeekHeightRoute = 112.dp`: Calibrated baseline for route detail peeks without description (`MapScreenWithTrack.kt` / `RouteOnMapScreen.kt`), cleanly framing `RouteSummaryHeader` (Sport Icon, Route Name, Source, Distance, Ascent, Visibility Switch).
   - `PeekHeightRouteWithDescription = 152.dp`: Calibrated baseline for route detail peeks with an active description string, ensuring complete visibility of the description text above the system navigation bar without cut-off.
   - `PeekHeightSegment = 192.dp`: Recalibrated from 156.dp to 192.dp for segment detail peeks (`MapScreenWithTrack.kt` / `SegmentOnMapScreen.kt`), ensuring the entire 3-row `SegmentDetails` card (including the third altitude gain and extrema row) sits fully above the navigation bar.
   - `PeekHeightKnownLocation = 108.dp`: Calibrated baseline for favorite start location (*Lieblingsorte*) peeks (`MapScreenWithTrack.kt` / `KnownLocationOnMapSheet`), cleanly framing Name, Edit button, Altitude, and Starts count.
   - `PeekHeightLiveSegment = 126.dp`: Recalibrated from 140.dp to 126.dp for active live segment tracking (`SensorGridScreen.kt`), framing live progress, status, and delta metrics cleanly while preventing the elevation profile chart from peeking prematurely.

2. **Dynamic Inset Factoring & Screen Standardization**:
   - `MapScreenWithTrack.kt`: Dynamically checks `routeSummary?.description.isNullOrEmpty()` to select `PeekHeightRoute` (112.dp) or `PeekHeightRouteWithDescription` (152.dp), combined with `WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()`.
   - `MapScreenWithTrack.kt`: Replaced segment peek with `PeekHeightSegment` (192.dp) and known locations with `PeekHeightKnownLocation` (108.dp).
   - `SensorGridScreen.kt`: Binds `PeekHeightLiveSegment` (126.dp).
   - `WorkoutClusterHeatmapScreen.kt` & `PeriodMapScreen.kt`: Bind `PeekHeightWorkout` (140.dp).

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-221`), refining and centralizing peek height specifications across `REQ-SET-069` and `REQ-UI-180` under Epic `ATT-355` (*Good and consistent UI*).
2. *Historical Origin & Commit Trace*: Sprint `2026-40.8` (Commit `9c3dd09e`, ATT-1645).
3. *Root Reason for Existing Formulation*: Baseline tokens (156dp for segments, 112dp for routes, 140dp for live segments) were calibrated before physical Pixel 10 hardware testing revealed that 156dp clipped the altitude metrics row of SegmentDetails, 112dp obscured route descriptions behind the navigation bar, and 140dp on live segments exposed an unwanted top slice of the elevation profile graph.
4. *Preservation of Core Invariants*: Status bar boundary constraints (`REQ-SET-069`), navigation bar padding additions, and sheet gesture handling are 100% strictly preserved.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-221` (item 1) | `[TST-UI-175.1]` | Unit Test (`BottomSheetDesignTest.testBottomSheetDesign_peekHeightBaselineConstants`) | **PASSED** | `Verified` |
| `REQ-UI-221` (item 2, 3) | `[TST-UI-175.2]` | Visual Contract Test (`BottomSheetVisualContractTest.testScreens_consumeStandardizedPeekHeightTokens`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `[TST-UI-175.3]` | Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit Tests
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.core.BottomSheet*"
BUILD SUCCESSFUL in 8s
12 actionable tasks: 12 executed
```
- `BottomSheetDesignTest.testBottomSheetDesign_peekHeightBaselineConstants`: PASSED
- `BottomSheetVisualContractTest.testScreens_consumeStandardizedPeekHeightTokens`: PASSED

### Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`)
```text
./gradlew testDebugUnitTest
BUILD SUCCESSFUL in 3m 1s
32 actionable tasks: 32 executed
0 failures, 0 regressions across all project modules.
```

---

## 4. Hardware / Physical Verification (Pixel 10)

1. **Segments Bottom Sheet**:
   - Open Map Screen with Track and select a segment marker.
   - Verify that the bottom sheet opens to its recalibrated peek height (`192.dp + navBarPadding`).
   - Confirm that the entire 3-row `SegmentDetails` card (including the third altitude gain and extrema row) is completely visible above the system navigation bar without cut-off.
2. **Route Bottom Sheet (with and without Description)**:
   - Select a route without description: Sheet peeks at `112.dp + navBarPadding` framing the header card cleanly.
   - Select a route with description: Sheet peeks at `152.dp + navBarPadding` cleanly framing both header card and description text above the system navigation bar.
3. **Live Segment Sheet**:
   - Start live tracking with an active segment.
   - Verify that the bottom sheet peeks at `126.dp + navBarPadding`.
   - Confirm that live progress and delta metrics are fully framed, while the elevation profile graph stays cleanly below the fold.

---

## 5. Invariant & Governance Verification

1. **Design System Consistency**: All persistent bottom sheet peek heights reference centralized `BottomSheetDesign` tokens.
2. **Status Bar & Gesture Safety**: `maxSheetHeight = maxHeight - statusBarHeight` (`REQ-SET-069`) and minimum drag handle pill dimensions (`MinimumDragHandle`) are preserved.
3. **9-Language Parity**: All labels and dynamic strings retain 100% localization parity across EN, DE, ES, FR, IT, JA, NL, PL, and PT.
4. **Fix Version Audit**: Parent ticket `ATT-1645` correctly specifies Fix Version `V4.9.38`.
5. **Clean-Room Test Suite**: Full test suite passes 100% (32 actionable tasks, 0 failures).
