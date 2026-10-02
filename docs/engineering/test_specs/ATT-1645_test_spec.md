# Stage 2: Requirement & Test Specification - ATT-1645: Calibrate and Optimize Initial Height & Peek Baselines for Popups and Bottom Sheets

**Ticket**: [ATT-1645](https://atrainingtracker.atlassian.net/browse/ATT-1645)  
**Sub-task**: [ATT-1898](https://atrainingtracker.atlassian.net/browse/ATT-1898) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Requirement Mapping**: `REQ-UI-221` (*Standardized Bottom Sheet Peek Height Baselines & Information Footprint Framing*)  
**Test Spec ID**: `TST-UI-175`  
**Branch**: `feature/ATT-1645`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Requirement Specification (REQ-UI-221)

### 1.1 Problem Statement & Rationale
Various bottom sheets and persistent scaffolds across the application currently rely on scattered, hardcoded initial peek heights (`100.dp`, `120.dp`, `140.dp`, `185.dp`). On physical devices (e.g. Google Pixel 10), several of these heights produce visual truncation or awkward whitespace:
* In `WorkoutClusterHeatmapScreen` and `PeriodMapScreen`, a peek height of `120.dp` cuts right through the Date/Time row of the `WorkoutHeader`.
* In `RouteOnMapScreen`, `100.dp` pinches the visibility switch and elevation gain metric tightly against the bottom edge.
* In `SegmentOnMapScreen`, `185.dp` overshoots the natural 3-row metric card by ~33.5dp, exposing an awkward empty gap.
* Sheet peek heights are not governed by centralized design tokens in `BottomSheetDesign.kt`.

### 1.2 Functional & Architectural Requirements
The system SHALL standardize, centralize, and calibrate all persistent bottom sheet and map peek baselines across the application:

1. **Centralized Design Token Definition (`BottomSheetDesign.kt`)**:
   `BottomSheetDesign` SHALL define canonical, standardized Dp baseline tokens:
   * `val PeekHeightWorkout: Dp = 140.dp`: Calibrated baseline for single workout detail peeks in analytical map screens (`WorkoutClusterHeatmapScreen`, `PeriodMapScreen`), cleanly framing `WorkoutHeader` (Sport Icon, Title, Cluster button, Sport Type/Equipment, Date/Time).
   * `val PeekHeightRoute: Dp = 112.dp`: Calibrated baseline for route detail peeks (`MapScreenWithTrack.kt` / `RouteOnMapScreen.kt`), cleanly framing `RouteSummaryHeader` (Sport Icon, Route Name, Source, Distance, Ascent, and Visibility Switch).
   * `val PeekHeightSegment: Dp = 156.dp`: Calibrated baseline for segment detail peeks (`MapScreenWithTrack.kt` / `SegmentOnMapScreen.kt`), cleanly framing `SegmentHeader` and the 3-row `SegmentDetails` card without trailing empty container gaps.
   * `val PeekHeightKnownLocation: Dp = 108.dp`: Calibrated baseline for favorite start location (*Lieblingsorte*) peeks (`MapScreenWithTrack.kt` / `KnownLocationOnMapSheet`), cleanly framing Name, Edit button, Altitude, and Starts count.
   * `val PeekHeightLiveSegment: Dp = 140.dp`: Standardized baseline for active live segment tracking peek (`SensorGridScreen.kt`), cleanly framing live progress, status, and delta metrics.

2. **System Navigation Bar Inset Factoring**:
   Every persistent sheet peek height SHALL strictly incorporate the bottom system navigation bar insets:
   $$\text{sheetPeekHeight} = \text{BottomSheetDesign.PeekHeight*} + \text{navBarHeight}$$
   where $\text{navBarHeight} = \text{WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()}$.
   When collapsed or unselected, peek height SHALL be `0.dp`.

3. **Screen-Level Token Adoption**:
   * `MapScreenWithTrack.kt`: SHALL reference `BottomSheetDesign.PeekHeightSegment`, `BottomSheetDesign.PeekHeightRoute`, and `BottomSheetDesign.PeekHeightKnownLocation`.
   * `SensorGridScreen.kt`: SHALL reference `BottomSheetDesign.PeekHeightLiveSegment`.
   * `WorkoutClusterHeatmapScreen.kt` & `PeriodMapScreen.kt`: SHALL reference `BottomSheetDesign.PeekHeightWorkout`.

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Workout Header Unclipped Initial Footprint)**:
  * *Given* an athlete peeking a workout in `WorkoutClusterHeatmapScreen` or `PeriodMapScreen`
  * *When* the bottom sheet is in its initial collapsed peek state
  * *Then* the entire `WorkoutHeader`—including sport icon, workout title, cluster badge, sport name, equipment, and start date/time—SHALL be 100% visible above the system navigation bar without vertical clipping.

* **Criterion 2 (Route Header & Switch Framing)**:
  * *Given* an athlete selecting a route on the central navigation map (`MapScreenWithTrack`)
  * *When* the route bottom sheet peeks
  * *Then* the sport icon, name, source label, distance, ascent, and visibility switch SHALL render with balanced top and bottom padding above the navigation bar.

* **Criterion 3 (Segment Card Clean Bounding)**:
  * *Given* an athlete selecting a segment on `MapScreenWithTrack`
  * *When* the segment bottom sheet peeks
  * *Then* the 3-row `SegmentDetails` card SHALL be fully contained within the peek height without an excessive trailing blank margin or cut-off elevation metrics.

* **Criterion 4 (Centralized Design System Governance)**:
  * *Given* static AST / code inspection across `MapScreenWithTrack.kt`, `SensorGridScreen.kt`, `WorkoutClusterHeatmapScreen.kt`, and `PeriodMapScreen.kt`
  * *When* evaluating sheet peek height assignments
  * *Then* zero hardcoded raw literals (`100.dp`, `120.dp`, `185.dp`) SHALL remain, and all sheets SHALL consume `BottomSheetDesign.PeekHeight*` tokens.

### 1.4 System Invariants
1. Maximum expanded height constraints (`maxSheetHeight = maxHeight - statusBarHeight`) and status bar boundary protections (`REQ-SET-069`) MUST NOT be altered.
2. The drag handle pill (`MinimumDragHandle`) dimensions (32dp x 3dp, 15dp total vertical footprint) MUST NOT be altered.
3. Single-threaded database dispatchers, background repository flows, and live tracking sensor pipelines MUST NOT be touched.

---

## 2. Test Specification (TST-UI-175)

### Test Case 1: `testBottomSheetDesign_peekHeightBaselineConstants` (`TST-UI-175.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetDesignTest.kt`
* **Preconditions**: `BottomSheetDesign` loaded.
* **Action**: Assert all 5 peek height baseline constants:
  * `PeekHeightWorkout == 140.dp`
  * `PeekHeightRoute == 112.dp`
  * `PeekHeightSegment == 156.dp`
  * `PeekHeightKnownLocation == 108.dp`
  * `PeekHeightLiveSegment == 140.dp`
* **Expected Result**: All assertions pass.

### Test Case 2: `testScreens_consumeStandardizedPeekHeightTokens` (`TST-UI-175.2`)
* **Scope**: Visual Contract / AST Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetVisualContractTest.kt`
* **Preconditions**: Source files accessible.
* **Action**: Inspect `MapScreenWithTrack.kt`, `SensorGridScreen.kt`, `WorkoutClusterHeatmapScreen.kt`, and `PeriodMapScreen.kt` for `BottomSheetDesign.PeekHeight*` token consumption.
* **Expected Result**: All screens reference the canonical design tokens; zero raw literal peek heights remain.

### Test Case 3: 9-Language Localization & Specifier Audit (`TST-UI-175.3`)
* **Scope**: Localization Parity Test
* **Target File**: `TranslationParityTest.kt`
* **Preconditions**: Android resource directories across all 9 locales.
* **Action**: Run existing translation parity tests.
* **Expected Result**: 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT. Zero regressions.

### Test Case 4: Clean-Room Regression Suite (`TST-UI-175.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Full suite validation ensuring zero regressions across all modules.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-175.1` | Unit | `BottomSheetDesignTest.testBottomSheetDesign_peekHeightBaselineConstants` | `REQ-UI-221` | Specified |
| `TST-UI-175.2` | Visual Contract | `BottomSheetVisualContractTest.testScreens_consumeStandardizedPeekHeightTokens` | `REQ-UI-221` | Specified |
| `TST-UI-175.3` | Localization | `TranslationParityTest` | `REQ-UI-221`, `REQ-UI-106` | Specified |
| `TST-UI-175.4` | Clean-Room Suite | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
