# Stage 2: Requirement & Test Specification - ATT-1645: Calibrate and Optimize Initial Height & Peek Baselines for Popups and Bottom Sheets (Flush Header-to-NavBar Baseline)

**Ticket**: [ATT-1645](https://atrainingtracker.atlassian.net/browse/ATT-1645)  
**Sub-task**: [ATT-2053](https://atrainingtracker.atlassian.net/browse/ATT-2053) (`[Test-Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.12`  
**Requirement Mapping**: `REQ-UI-221` (*UI/Sheets: Standardized Bottom Sheet Peek Height Baselines and Information Footprint Framing*)  
**Test Spec ID**: `TST-UI-175`  
**Branch**: `feature/ATT-1645`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Requirement Specification (REQ-UI-221)

### 1.1 Problem Statement & Rationale
During physical device evaluation on Google Pixel 10 hardware in Sprint `2026-40.11`, the resting peek behavior of persistent bottom sheets was evaluated as "still not ideal". During Sprint-Start Ceremony 1 for Sprint `2026-40.12`, the user formulated the exact visual and geometric invariant:
> *"There is the line between the white heading and the green map. This line must be exactly on top of the navigation bar."*

When a bottom sheet is in collapsed peek mode, static approximations and uncalibrated initial baselines either:
1. Overshoot the header height, exposing an unsightly band of the green map tiles underneath the header directly above the system navigation bar; or
2. Undershoot the header height, partially obscuring the altitude row in segments or description text in routes behind the navigation bar.

To achieve pixel-perfect presentation on all devices and navigation bar styles (3-button vs gesture navigation), the system establishes **Dynamic Self-Measuring Header Peek Baselines** coupled with a strict **Flush Header-to-NavBar Baseline Invariant**.

### 1.2 Functional & Architectural Requirements
The system SHALL standardize, centralize, and calibrate all persistent bottom sheet and map peek baselines across the application, ensuring essential header content is fully visible above system navigation bars without vertical clipping or awkward container overshoots (`REQ-UI-221`):

1. **Flush Geometric Invariant**:
   * In collapsed peek state, the dividing line between the bottom of the white heading card (`MinimumDragHandle() + Surface(header)`) and the top of the underlying map canvas SHALL sit **flush with the top edge of the system navigation bar**.
   * Zero pixels of the underlying green map SHALL peek out above the navigation bar in peek state.
   * Zero pixels of the white header content (titles, badges, 3-row stats, descriptions) SHALL be occluded behind the navigation bar.

2. **Centralized Design Token Definition (`BottomSheetDesign.kt`)**:
   `BottomSheetDesign` SHALL define canonical, standardized Dp baseline tokens:
   * `val PeekHeightWorkout: Dp = 140.dp`: Calibrated baseline for single workout detail peeks in analytical map screens (`WorkoutClusterHeatmapScreen`, `PeriodMapScreen`), cleanly framing `WorkoutHeader` (Sport Icon, Title, Cluster button, Sport Type/Equipment, Date/Time).
   * `val PeekHeightRoute: Dp = 112.dp`: Calibrated baseline for route detail peeks without description (`MapScreenWithTrack.kt` / `RouteOnMapScreen.kt`), cleanly framing `RouteSummaryHeader` (Sport Icon, Route Name, Source, Distance, Ascent, and Visibility Switch).
   * `val PeekHeightRouteWithDescription: Dp = 152.dp`: Calibrated baseline for route detail peeks with an active description string, ensuring complete visibility of description text above navigation bars.
   * `val PeekHeightSegment: Dp = 192.dp`: Calibrated baseline for segment detail peeks (`MapScreenWithTrack.kt` / `SegmentOnMapScreen.kt`), cleanly framing `SegmentHeader` and the full 3-row `SegmentDetails` card (including the third altitude gain and extrema row) above navigation bars.
   * `val PeekHeightKnownLocation: Dp = 108.dp`: Calibrated baseline for favorite start location (*Lieblingsorte*) peeks (`MapScreenWithTrack.kt` / `KnownLocationOnMapSheet`), cleanly framing Name, Edit button, Altitude, and Starts count.
   * `val PeekHeightLiveSegment: Dp = 126.dp`: Calibrated baseline for active live segment tracking peek (`SensorGridScreen.kt`), cleanly framing live progress, status, and delta metrics without prematurely revealing the underlying elevation profile graph.

3. **System Navigation Bar Inset Factoring**:
   * Every persistent sheet peek height SHALL strictly incorporate the bottom system navigation bar insets:
     `sheetPeekHeight = (measuredHeight ?: fallbackBaseline) + navBarHeight` where `navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()`. When collapsed or unselected, peek height SHALL be `0.dp`.
   * For routes, `MapScreenWithTrack.kt` SHALL dynamically evaluate `routeSummary?.description.isNullOrEmpty()` to select `PeekHeightRoute` or `PeekHeightRouteWithDescription` as the fallback baseline.

4. **Dynamic Self-Measuring Header Peek Architecture (`MapDetailLayout.kt` / `MapScreenWithTrack.kt`)**:
   * In `MapDetailLayout.kt`, when `useStatusBarsPadding == false`, the header container (`MinimumDragHandle() + Surface(header)`) SHALL measure its rendered layout height in pixels via `Modifier.onGloballyPositioned` and convert it to Dp via `LocalDensity.current`.
   * The measured height SHALL be reported via an optional callback parameter: `onHeaderHeightMeasured: ((Dp) -> Unit)? = null`.
   * `SegmentOnMapScreen.kt` and `RouteOnMapScreen.kt` SHALL accept `onHeaderHeightMeasured` and forward it to `MapDetailLayout`.
   * In `MapScreenWithTrack.kt`, `sheetPeekHeight` SHALL dynamically evaluate `(measuredHeight ?: fallbackBaseline) + navBarHeight`.
   * When dynamic measurement completes, the bottom sheet resting offset SHALL align flush with the top edge of the navigation bar without exposing green map tiles.

5. **Preservation of Core Invariants**:
   * Maximum expanded height constraints (`maxSheetHeight = maxHeight - statusBarHeight`) and status bar boundary protections (`REQ-SET-069`) remain strictly preserved.
   * Minimum drag handle pill dimensions (`MinimumDragHandle`, 32dp x 3dp, 15dp total vertical footprint) remain strictly preserved.
   * Fallback tokens in `BottomSheetDesign.kt` are 100% preserved.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-221` (*UI/Sheets: Standardized Bottom Sheet Peek Height Baselines and Information Footprint Framing*), targeting `BottomSheetDesign.kt`, `MapDetailLayout.kt`, `MapScreenWithTrack.kt`, `SegmentOnMapScreen.kt`, `RouteOnMapScreen.kt`, `SensorGridScreen.kt`, `WorkoutClusterHeatmapScreen.kt`, and `PeriodMapScreen.kt`.
* **Historical Origin & Commit Trace**: Sprint `2026-40.8` (Commit `9c3dd09e`, ATT-1645) and Sprint `2026-40.11` (Dynamic Self-Measuring Peeks).
* **Root Reason for Existing Formulation**: Static baseline tokens failed under system font scaling, display zoom, and multi-line titles on physical Pixel 10 hardware, occluding the altitude row or descriptions behind the navigation bar.
* **Preservation of Core Invariants**: Status bar boundary constraints (`REQ-SET-069`), navigation bar padding additions, fallback tokens, and sheet gesture handling are 100% strictly preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Segments Detail Card Complete Visibility & Flush Alignment)**:
  * *Given* an athlete selecting a segment on `MapScreenWithTrack`,
  * *When* the segment bottom sheet peeks above the system navigation bar,
  * *Then* the entire 3-row `SegmentDetails` card SHALL be 100% visible, and the dividing line between the white header surface and green map SHALL sit flush on the top edge of the navigation bar with 0% map exposed.
* **Criterion 2 (Route Description Complete Visibility & Flush Alignment)**:
  * *Given* an athlete selecting a route that has a non-empty description on `MapScreenWithTrack`,
  * *When* the route bottom sheet peeks,
  * *Then* the description text row SHALL be 100% visible, and the bottom of the header card SHALL align flush with the top edge of the navigation bar.
* **Criterion 3 (Live Segment Elevation Chart Shielding)**:
  * *Given* an active live segment in `SensorGridScreen`,
  * *When* the live segment bottom sheet peeks,
  * *Then* live progress metrics SHALL sit cleanly framed and the underlying elevation profile graph SHALL remain hidden below the fold until the athlete drags the sheet upward.
* **Criterion 4 (Dynamic Measurement Architecture & Forwarding)**:
  * *Given* `MapDetailLayout.kt`, `SegmentOnMapScreen.kt`, and `RouteOnMapScreen.kt`,
  * *When* inspected in code and tests,
  * *Then* all three components SHALL declare `onHeaderHeightMeasured` callback support and measure header height using `Modifier.onGloballyPositioned`.
* **Criterion 5 (Defensive Fallback & Token Parity)**:
  * *Given* `MapScreenWithTrack.kt` before measurement completion or in preview mode,
  * *When* evaluating peek heights,
  * *Then* it SHALL safely fall back to `BottomSheetDesign.PeekHeightSegment`, `PeekHeightRoute`, etc., with zero raw numeric literals.

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
* **Preconditions**: Screens compiled.
* **Action**: Verify:
  * `MapScreenWithTrack.kt` references `BottomSheetDesign.PeekHeightSegment`, `PeekHeightRoute`, `PeekHeightRouteWithDescription`, and `PeekHeightKnownLocation`.
  * `SensorGridScreen.kt` references `BottomSheetDesign.PeekHeightLiveSegment`.
  * `WorkoutClusterHeatmapScreen.kt` and `PeriodMapScreen.kt` reference `BottomSheetDesign.PeekHeightWorkout`.
  * Zero raw literal peek heights remain across map detail screens.
* **Expected Result**: All structural assertions pass.

### Test Case 3: `testDynamicHeaderMeasurement_wiringAndForwarding` (`TST-UI-175.3`)
* **Scope**: Structural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetVisualContractTest.kt`
* **Preconditions**: `MapDetailLayout.kt`, `SegmentOnMapScreen.kt`, `RouteOnMapScreen.kt`, `MapScreenWithTrack.kt`.
* **Action**: Verify:
  * `MapDetailLayout.kt` accepts `onHeaderHeightMeasured: ((Dp) -> Unit)? = null`.
  * `MapDetailLayout.kt` uses `onGloballyPositioned` around the header container.
  * `SegmentOnMapScreen.kt` accepts and forwards `onHeaderHeightMeasured`.
  * `RouteOnMapScreen.kt` accepts and forwards `onHeaderHeightMeasured`.
  * `MapScreenWithTrack.kt` tracks dynamic header measurement state and adds `navBarHeight`.
* **Expected Result**: All structural wiring checks pass.

### Test Case 4: Full Test Suite Regression (`TST-UI-175.4`)
* **Scope**: Clean-Room Full Suite Regression
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% test pass rate with 0 failures and 0 regressions across all modules.

---

## 3. Bidirectional Traceability Matrix

| Requirement ID | Test Case ID | Test Type | Target File | Verification Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-221` (Design Tokens) | `TST-UI-175.1` | Unit Test | `BottomSheetDesignTest.kt` | Specified |
| `REQ-UI-221` (Screen Adoption) | `TST-UI-175.2` | Visual Contract | `BottomSheetVisualContractTest.kt` | Specified |
| `REQ-UI-221` (Dynamic Measurement) | `TST-UI-175.3` | Structural Contract | `BottomSheetVisualContractTest.kt` | Specified |
| `REQ-UI-221` (Full Regression) | `TST-UI-175.4` | Full-Suite Regression | Full Test Suite | Specified |
