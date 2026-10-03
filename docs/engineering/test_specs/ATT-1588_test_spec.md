# Stage 2: Requirement & Test Specification - ATT-1588: Distinct Visual Boundaries, Contours & Elevation for Bottom Popups & Live Segment

**Ticket**: [ATT-1588](https://atrainingtracker.atlassian.net/browse/ATT-1588)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.4`  
**Requirement Mapping**: `REQ-UI-189` (*UI/Sheets: Standardized Boundaries, Contours, Drop Shadow Elevation & Drag Affordances*)  
**Test Mapping**: `TST-UI-143` (`TST-UI-143.1`, `TST-UI-143.2`, `TST-UI-143.3`, `TST-UI-143.4`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Formal Requirement Specification

### REQ-UI-189: UI/Sheets: Standardized Boundaries, Contours, Drop Shadow Elevation & Drag Affordances
The system SHALL standardize visual boundaries, contours, elevation drop shadows, and interactive drag affordances across all bottom sheet popups and persistent scaffold sheets without introducing contrasting background colors or multi-color bars (ATT-1588):

1. **Design Tokens & Core Bottom Sheet Architecture (`BottomSheetDesign.kt`)**:
   - The system SHALL provide a centralized design tokens object `BottomSheetDesign` in package `com.atrainingtracker.trainingtracker.ui.components.core` defining:
     - Standardized corner radius: `SheetCornerRadius = 20.dp`
     - Standardized top curvature shape: `SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)`
     - Standardized drop shadow elevation: `SheetShadowElevation = 8.dp`
     - Standardized tonal elevation: `SheetTonalElevation = 2.dp`
     - Standardized contour border: `BorderWidth = 1.dp`, `BorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)`
     - Extension modifier `Modifier.sheetContour(borderColor, shape, borderWidth)` applying border stroke and top corner clipping.

2. **Live Segment & Scaffold Bottom Sheets (`SensorGridScreen.kt`, `LIveSegmentSheet.kt`, `MapDetailLayout.kt`)**:
   - In `SensorGridScreen.kt`, `BottomSheetScaffold` SHALL specify:
     - `sheetShape = BottomSheetDesign.SheetShape`,
     - `sheetShadowElevation = BottomSheetDesign.SheetShadowElevation` (8.dp),
     - `sheetTonalElevation = BottomSheetDesign.SheetTonalElevation` (2.dp).
   - The Live Segment sheet content container SHALL apply `sheetContour` to cleanly delineate its top edge from the sensor grid and map canvas.
   - In `MapDetailLayout.kt` and `LIveSegmentSheet.kt`, header surfaces SHALL NOT override the top curvature with `RectangleShape` when hosted within a bottom sheet (`!useStatusBarsPadding`).

3. **Modal Bottom Sheet Harmonization (`AppModalBottomSheet.kt`, `FilterBottomSheetScaffold.kt`)**:
   - All modal bottom sheets (`AppModalBottomSheet`, `AppBottomSheetContent`, `FilterBottomSheetScaffold`) SHALL use `BottomSheetDesign.SheetShape`, `BottomSheetDesign.SheetTonalElevation`, and apply the 1.dp `outlineVariant` top contour stroke.

4. **Prominent Drag Handle Affordance (`MinimumDragHandle.kt`)**:
   - `MinimumDragHandle` SHALL provide a standardized, prominent drag handle pill of dimension $36\text{dp} \times 4\text{dp}$ with `MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)` and padded bounds ($12\text{dp}$ top, $6\text{dp}$ bottom) ensuring unambiguous interactivity affordance.

5. **Persistent Map Bottom Sheets (`MapScreenWithTrack.kt`, `PeriodMapScreen.kt`, `WorkoutClusterHeatmapScreen.kt`)**:
   - Persistent map scaffolds SHALL uniformly specify `sheetShape = BottomSheetDesign.SheetShape` and `sheetShadowElevation = 8.dp`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-189`), complementing `REQ-UI-148` (*Standardized Edge-to-Edge Bottom Sheet Insets & Core UI Design Components*).
2. **Historical Origin & Commit Trace**: Ticket `ATT-939` (commit `9b7405e3`) under Epic `ATT-355` (*Good and consistent UI*).
3. **Root Reason for Existing Formulation**: Previous work focused strictly on system window insets, navigation bar padding, and basic keyboard handling. Visual boundary delineation, top border strokes, and elevation drop shadows were omitted, causing sheets in dark mode to blend into the underlying canvas.
4. **Preservation of Core Invariants**:
   - Window insets handling (`navigationBarsPadding()`, `imePadding()`) remains 100% intact across all sheets.
   - Zero arbitrary background color shifts (user-mandated constraint).
   - Map underlay drawing behavior in `SensorGridScreen` and `MapScreenWithTrack` preserved.
   - Single-thread SQLite concurrency unaffected.

---

## 3. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Live Segment Sheet Boundary)**:
  * *Given* an active live segment is peeking or expanded in `SensorGridScreen`,
  * *When* viewing the cockpit in either Light or Dark mode,
  * *Then* the bottom sheet SHALL be delimited by 20dp top rounded corners, a subtle 1dp `outlineVariant` contour stroke, an 8dp drop shadow, and a prominent drag handle pill without arbitrary background color shifts.

* **Criterion 2 (Modal Bottom Sheet Consistency)**:
  * *Given* any modal bottom sheet (`AppModalBottomSheet`, `FilterBottomSheetScaffold`),
  * *When* presented against the application background,
  * *Then* the sheet SHALL exhibit 20dp top rounded curvature and a 1dp `outlineVariant` contour border.

* **Criterion 3 (Persistent Map Scaffold Sheets)**:
  * *Given* persistent map bottom sheets (`MapScreenWithTrack`, `PeriodMapScreen`, `WorkoutClusterHeatmapScreen`),
  * *When* the bottom sheet peeks or expands,
  * *Then* it SHALL cast an 8dp drop shadow elevation with 20dp top corner curvature.

---

## 4. Verification & Test Specification (TST-UI-143)

### TST-UI-143.1: Design Tokens & Modifier Unit Tests (`BottomSheetDesignTest.kt`)
* **Objective**: Verify design tokens constants, dimensions, and shape metrics.
* **Assertions**:
  - `BottomSheetDesign.SheetCornerRadius == 20.dp`
  - `BottomSheetDesign.SheetShadowElevation == 8.dp`
  - `BottomSheetDesign.SheetTonalElevation == 2.dp`
  - `BottomSheetDesign.BorderWidth == 1.dp`
  - `BottomSheetDesign.SheetShape` topStart and topEnd corner radii equal 20dp, bottomStart and bottomEnd equal 0dp.

### TST-UI-143.2: Scaffold & Modal Bottom Sheet Visual Contract Tests (`BottomSheetVisualContractTest.kt`)
* **Objective**: Verify that sheet containers consume `BottomSheetDesign` shape, elevation, and contouring.
* **Assertions**:
  - `SensorGridScreen` `BottomSheetScaffold` uses `sheetShape = SheetShape`, `sheetShadowElevation = 8.dp`, `sheetTonalElevation = 2.dp`.
  - `MapDetailLayout` when `useStatusBarsPadding == false` does not force `shape = RectangleShape`.
  - `AppModalBottomSheet` and `FilterBottomSheetScaffold` use 20dp top curvature and 1dp border contour stroke.

### TST-UI-143.3: MinimumDragHandle Pill Affordance Tests (`MinimumDragHandleTest.kt`)
* **Objective**: Verify drag handle dimensions and vertical padding.
* **Assertions**:
  - Drag handle pill width is 36dp and height is 4dp.
  - Padding bounds are 12dp top and 6dp bottom.

### TST-UI-143.4: Clean-Room Full Suite Regression Execution
* **Objective**: Verify zero regressions across the complete unit test suite.
* **Command**: `./gradlew testDebugUnitTest`
* **Success Criteria**: 100% pass rate with 0 failures across all test suites.

---

## 5. Traceability Matrix

| Requirement | Test ID | Verification Target | Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-189.1` | `TST-UI-143.1` | `BottomSheetDesign` constants, corner radii, and shape dimensions | Proposed |
| `REQ-UI-189.2` | `TST-UI-143.2` | `SensorGridScreen` scaffold elevation, contouring, and `MapDetailLayout` curvature | Proposed |
| `REQ-UI-189.3` | `TST-UI-143.2` | Modal sheets (`AppModalBottomSheet`, `FilterBottomSheetScaffold`) curvature & border | Proposed |
| `REQ-UI-189.4` | `TST-UI-143.3` | `MinimumDragHandle` pill proportions and padding bounds | Proposed |
| `REQ-UI-189.5` | `TST-UI-143.2` | Persistent map scaffold sheets (`MapScreenWithTrack`, `PeriodMapScreen`, `WorkoutClusterHeatmapScreen`) | Proposed |
| `REQ-UI-189` | `TST-UI-143.4` | Full clean-room unit test regression suite (`./gradlew testDebugUnitTest`) | Proposed |
