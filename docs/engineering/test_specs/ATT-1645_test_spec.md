# Stage 2: Requirement & Test Specification - ATT-1645: Calibrate and Optimize Initial Height & Peek Baselines for Popups and Bottom Sheets

**Ticket**: [ATT-1645](https://atrainingtracker.atlassian.net/browse/ATT-1645)  
**Sub-task**: [ATT-1962](https://atrainingtracker.atlassian.net/browse/ATT-1962) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.9`  
**Requirement Mapping**: `REQ-UI-221` (*UI/Sheets: Standardized Bottom Sheet Peek Height Baselines and Information Footprint Framing*)  
**Test Spec ID**: `TST-UI-175`  
**Branch**: `feature/ATT-1645`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Requirement Specification (REQ-UI-221)

### 1.1 Problem Statement & Rationale
During Sprint `2026-40.8` review on physical Google Pixel 10 hardware, initial bottom sheet peek baselines exhibited three visual truncation or premature exposure defects:
1. In `SegmentOnMapScreen.kt`, the third row of the `SegmentDetails` card (altitude icon, elevation gain, min altitude, max altitude) was obscured behind the gesture navigation bar because `PeekHeightSegment = 156.dp` was $20\text{dp}$ to $28\text{dp}$ too short for the full $180\text{dp}$ content footprint.
2. In `RouteOnMapScreen.kt`, when a route contains a description string, the description row was obscured behind the navigation bar because `PeekHeightRoute = 112.dp` only accommodates routes without description.
3. In `SensorGridScreen.kt` / `LiveSegmentSheet.kt`, `PeekHeightLiveSegment = 140.dp` was ~$14\text{dp}$ taller than the live header footprint, causing the top slice of the underlying elevation profile chart to prematurely peek out above the navigation bar.

### 1.2 Functional & Architectural Requirements
The system SHALL standardize, centralize, and calibrate all persistent bottom sheet and map peek baselines across the application, ensuring essential header content is fully visible above system navigation bars without vertical clipping or awkward container overshoots (`REQ-UI-221`):

1. **Centralized Design Token Definition (`BottomSheetDesign.kt`)**:
   `BottomSheetDesign` SHALL define canonical, standardized Dp baseline tokens:
   * `val PeekHeightWorkout: Dp = 140.dp`: Calibrated baseline for single workout detail peeks in analytical map screens (`WorkoutClusterHeatmapScreen`, `PeriodMapScreen`), cleanly framing `WorkoutHeader` (Sport Icon, Title, Cluster button, Sport Type/Equipment, Date/Time).
   * `val PeekHeightRoute: Dp = 112.dp`: Calibrated baseline for route detail peeks without description (`MapScreenWithTrack.kt` / `RouteOnMapScreen.kt`), cleanly framing `RouteSummaryHeader` (Sport Icon, Route Name, Source, Distance, Ascent, and Visibility Switch).
   * `val PeekHeightRouteWithDescription: Dp = 152.dp`: Calibrated baseline for route detail peeks with an active description string, ensuring complete visibility of description text above navigation bars.
   * `val PeekHeightSegment: Dp = 192.dp`: Calibrated baseline for segment detail peeks (`MapScreenWithTrack.kt` / `SegmentOnMapScreen.kt`), cleanly framing `SegmentHeader` and the full 3-row `SegmentDetails` card (including the third altitude gain and extrema row) above navigation bars.
   * `val PeekHeightKnownLocation: Dp = 108.dp`: Calibrated baseline for favorite start location (*Lieblingsorte*) peeks (`MapScreenWithTrack.kt` / `KnownLocationOnMapSheet`), cleanly framing Name, Edit button, Altitude, and Starts count.
   * `val PeekHeightLiveSegment: Dp = 126.dp`: Calibrated baseline for active live segment tracking peek (`SensorGridScreen.kt`), cleanly framing live progress, status, and delta metrics without prematurely revealing the underlying elevation profile graph.

2. **System Navigation Bar Inset Factoring**:
   * Every persistent sheet peek height SHALL strictly incorporate the bottom system navigation bar insets:
     `sheetPeekHeight = BottomSheetDesign.PeekHeight* + navBarHeight` where `navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()`. When collapsed or unselected, peek height SHALL be `0.dp`.
   * For routes, `MapScreenWithTrack.kt` SHALL dynamically evaluate `routeSummary?.description.isNullOrEmpty()` to select `PeekHeightRoute` or `PeekHeightRouteWithDescription`.

3. **Screen-Level Token Adoption**:
   * `MapScreenWithTrack.kt` SHALL consume `PeekHeightSegment`, `PeekHeightRoute`, `PeekHeightRouteWithDescription`, and `PeekHeightKnownLocation`.
   * `SensorGridScreen.kt` SHALL consume `PeekHeightLiveSegment`.
   * `WorkoutClusterHeatmapScreen.kt` and `PeriodMapScreen.kt` SHALL consume `PeekHeightWorkout`.
   * Zero hardcoded raw peek height literals SHALL remain in screen composables.

4. **Preservation of Core Invariants**:
   * Maximum expanded height constraints (`maxSheetHeight = maxHeight - statusBarHeight`) and status bar boundary protections (`REQ-SET-069`) remain strictly preserved.
   * Minimum drag handle pill dimensions (`MinimumDragHandle`, 32dp x 3dp, 15dp total vertical footprint) remain strictly preserved.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-221` (*UI/Sheets: Standardized Bottom Sheet Peek Height Baselines and Information Footprint Framing*), targeting `BottomSheetDesign.kt`, `MapScreenWithTrack.kt`, `SensorGridScreen.kt`, `WorkoutClusterHeatmapScreen.kt`, and `PeriodMapScreen.kt`.
* **Historical Origin & Commit Trace**: Sprint `2026-40.8` (Commit `9c3dd09e`, ATT-1645).
* **Root Reason for Existing Formulation**: Baseline tokens (156dp for segments, 112dp for routes, 140dp for live segments) were calibrated before physical Pixel 10 hardware testing revealed that 156dp clipped the altitude metrics row of SegmentDetails, 112dp obscured route descriptions behind the navigation bar, and 140dp on live segments exposed an unwanted top slice of the elevation profile graph.
* **Preservation of Core Invariants**: Status bar boundary constraints (`REQ-SET-069`), navigation bar padding additions, and sheet gesture handling are 100% strictly preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Segments Detail Card Complete Visibility)**:
  * *Given* an athlete selecting a segment on `MapScreenWithTrack`,
  * *When* the segment bottom sheet peeks above the system navigation bar,
  * *Then* the entire 3-row `SegmentDetails` card (including Row 3: altitude icon, elevation gain, min altitude, and max altitude) SHALL be 100% visible without clipping behind the navigation bar.
* **Criterion 2 (Route Description Complete Visibility)**:
  * *Given* an athlete selecting a route that has a non-empty description on `MapScreenWithTrack`,
  * *When* the route bottom sheet peeks,
  * *Then* the description text row SHALL be 100% visible above the navigation bar.
* **Criterion 3 (Live Segment Elevation Chart Shielding)**:
  * *Given* an active live segment in `SensorGridScreen`,
  * *When* the live segment bottom sheet peeks,
  * *Then* live progress metrics SHALL sit cleanly framed and the underlying elevation profile graph SHALL remain hidden below the fold until the athlete drags the sheet upward.
* **Criterion 4 (Token Governance & Zero Magic Numbers)**:
  * *Given* static code inspection across `MapScreenWithTrack.kt`, `SensorGridScreen.kt`, `WorkoutClusterHeatmapScreen.kt`, and `PeriodMapScreen.kt`,
  * *When* evaluating sheet peek height assignments,
  * *Then* all screens SHALL reference `BottomSheetDesign.PeekHeight*` tokens with zero raw literals.

### 1.4 System Invariants
* Status bar boundary constraints (`REQ-SET-069`, `maxSheetHeight = maxHeight - statusBarHeight`) MUST NOT be altered.
* Minimum drag handle dimensions (`MinimumDragHandle`, 32dp x 3dp, 15dp vertical touch footprint) MUST NOT be altered.
* `WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()` MUST be dynamically added to every active peek baseline.
* Zero changes to background tracking, database schemas, or repository threading.

---

## 2. Test Specification (TST-UI-175)

### Test Case 1: `testBottomSheetDesign_peekHeightBaselineConstants` (`TST-UI-175.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetDesignTest.kt`
* **Preconditions**: `BottomSheetDesign` constants declared.
* **Action**: Assert equality of baseline tokens:
  * `PeekHeightWorkout == 140.dp`
  * `PeekHeightRoute == 112.dp`
  * `PeekHeightRouteWithDescription == 152.dp`
  * `PeekHeightSegment == 192.dp`
  * `PeekHeightKnownLocation == 108.dp`
  * `PeekHeightLiveSegment == 126.dp`
* **Expected Result**: All assertions evaluate to `true`.

### Test Case 2: `testScreens_consumeStandardizedPeekHeightTokens` (`TST-UI-175.2`)
* **Scope**: Visual Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetVisualContractTest.kt`
* **Preconditions**: Screen composables in `app/src/main/java/...`.
* **Action**: Read source files and assert token references:
  * `MapScreenWithTrack.kt` references `BottomSheetDesign.PeekHeightSegment`, `PeekHeightRoute`, `PeekHeightRouteWithDescription`, and `PeekHeightKnownLocation`.
  * `SensorGridScreen.kt` references `BottomSheetDesign.PeekHeightLiveSegment`.
  * `WorkoutClusterHeatmapScreen.kt` and `PeriodMapScreen.kt` reference `BottomSheetDesign.PeekHeightWorkout`.
  * No raw hardcoded numeric literals (`185.dp`, `156.dp`, `140.dp`, `100.dp`, `120.dp`) in peek assignments.
* **Expected Result**: 100% token adoption, zero violations.

### Test Case 3: Clean-Room Regression Suite (`TST-UI-175.3`)
* **Scope**: Full Clean-Room Test Suite
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate with zero regressions across all modules.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-175.1]` | Unit | `BottomSheetDesignTest.testBottomSheetDesign_peekHeightBaselineConstants` | `REQ-UI-221` (Item 1) | Specified |
| `[TST-UI-175.2]` | Visual Contract | `BottomSheetVisualContractTest.testScreens_consumeStandardizedPeekHeightTokens` | `REQ-UI-221` (Items 2 & 3) | Specified |
| `[TST-UI-175.3]` | Clean-Room Suite | `./gradlew testDebugUnitTest` | `REQ-UI-221` (Item 4) | Specified |
