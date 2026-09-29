# Stage 3: Implementation Plan - ATT-1588: Distinct Visual Boundaries, Contours & Elevation for Bottom Popups & Live Segment

**Ticket**: [ATT-1588](https://atrainingtracker.atlassian.net/browse/ATT-1588)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.4`  
**Requirement Mapping**: `REQ-UI-189` (*UI/Sheets: Standardized Boundaries, Contours, Drop Shadow Elevation & Drag Affordances*)  
**Test Mapping**: `TST-UI-143` (`TST-UI-143.1`, `TST-UI-143.2`, `TST-UI-143.3`, `TST-UI-143.4`)  
**Branch**: `feature/ATT-1588`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Description & Background

In the active tracking cockpit (`SensorGridScreen.kt`), the live segment bottom sheet (`LiveSegmentSheet.kt` / `MapDetailLayout.kt`) blends directly into the sensor grid and map background. Material 3 `BottomSheetScaffold` applies no drop shadow by default (`sheetShadowElevation = 0.dp`), the inner header forces `shape = RectangleShape`, and no contour border exists. This results in the live segment card visually bleeding into surrounding tiles as unstructured floating text.

Furthermore, across the application, modal bottom sheets (`AppModalBottomSheet.kt`, `FilterBottomSheetScaffold.kt`) and map scaffold sheets (`MapScreenWithTrack.kt`, `PeriodMapScreen.kt`, `WorkoutClusterHeatmapScreen.kt`) lack standardized top boundary contours and consistent drop shadow elevation.

Per user directive, visual separation must **NOT** be achieved through arbitrary color shifts or multi-color bars. Structural separation and clarity must be achieved purely through form, contour lines, elevation drop shadows, and clear interactive drag affordances.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-189` (*UI/Sheets: Standardized Boundaries, Contours, Drop Shadow Elevation & Drag Affordances*)
* **Test Mapping**: `TST-UI-143`
  - `TST-UI-143.1`: Unit test verifying `BottomSheetDesign` design tokens, dimensions, and shape metrics (`BottomSheetDesignTest.kt`).
  - `TST-UI-143.2`: Component and contract tests verifying scaffold and modal bottom sheet shape, elevation, and contouring (`BottomSheetVisualContractTest.kt`).
  - `TST-UI-143.3`: Drag handle pill dimensions and padding tests (`MinimumDragHandleTest.kt`).
  - `TST-UI-143.4`: Clean-room full regression unit test suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Color Shifts / No Multi-Color Bars**:
   - Preserves semantic theme colors (`surface`, `surfaceContainer`, `outlineVariant`). No arbitrary tinted headers or multi-color status bars.
2. **Edge-to-Edge Window Insets (`REQ-UI-148`)**:
   - `navigationBarsPadding()` and `imePadding()` remain strictly intact across all sheets and scaffolds.
3. **Map Underlay Behavior**:
   - The map in `SensorGridScreen` and `MapScreenWithTrack` must continue to draw under the sheet for seamless panning.
4. **Interactive Drag & Touch Targets**:
   - Bottom sheet gesture interactions, fling velocity, and peek heights must remain smooth and unhindered.
   - Accessible touch targets are preserved.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: Design Tokens Layer (`BottomSheetDesign.kt`)
* Location: `com.atrainingtracker.trainingtracker.ui.components.core.BottomSheetDesign`
* Provide standardized constants:
  - `SheetCornerRadius = 20.dp`
  - `SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)`
  - `SheetShadowElevation = 8.dp`
  - `SheetTonalElevation = 2.dp`
  - `BorderWidth = 1.dp`
  - `DragHandleWidth = 36.dp`
  - `DragHandleHeight = 4.dp`
* Provide reusable `Modifier.sheetContour(borderColor, shape, borderWidth)` clipping to `shape` and applying `border` stroke with `outlineVariant.copy(alpha = 0.6f)`.

### Component 2: Drag Handle Affordance (`MinimumDragHandle.kt`)
* Update pill dimensions: `width = 36.dp, height = 4.dp`.
* Update pill color: `MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)`.
* Standardize padding bounds: `padding(top = 12.dp, bottom = 6.dp)`.

### Component 3: Live Tracking Cockpit & Live Segment (`SensorGridScreen.kt`, `MapDetailLayout.kt`)
* In `SensorGridScreen.kt`:
  - Set `sheetShape = BottomSheetDesign.SheetShape`.
  - Set `sheetShadowElevation = BottomSheetDesign.SheetShadowElevation` (8.dp).
  - Set `sheetTonalElevation = BottomSheetDesign.SheetTonalElevation` (2.dp).
  - Wrap live segment sheet content with `Modifier.sheetContour()`.
* In `MapDetailLayout.kt`:
  - In header `Surface`, set `shape = if (useStatusBarsPadding) RectangleShape else BottomSheetDesign.SheetShape`.

### Component 4: Modal Sheets Harmonization (`AppModalBottomSheet.kt`, `FilterBottomSheetScaffold.kt`)
* In `AppModalBottomSheet.kt`:
  - In `ModalBottomSheet`: specify `shape = BottomSheetDesign.SheetShape`, `tonalElevation = BottomSheetDesign.SheetTonalElevation`.
  - In `AppBottomSheetContent`: specify `shape = BottomSheetDesign.SheetShape`, `tonalElevation = BottomSheetDesign.SheetTonalElevation`, and `border = BorderStroke(BottomSheetDesign.BorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))`.
* In `FilterBottomSheetScaffold.kt`:
  - In `ModalBottomSheet`: specify `shape = BottomSheetDesign.SheetShape`, `tonalElevation = BottomSheetDesign.SheetTonalElevation`, and `dragHandle = { MinimumDragHandle() }`.

### Component 5: Persistent Map Scaffold Sheets (`MapScreenWithTrack.kt`, `PeriodMapScreen.kt`, `WorkoutClusterHeatmapScreen.kt`)
* In `MapScreenWithTrack.kt`, `PeriodMapScreen.kt`, and `WorkoutClusterHeatmapScreen.kt`:
  - Add `sheetShape = BottomSheetDesign.SheetShape`.
  - Add `sheetShadowElevation = BottomSheetDesign.SheetShadowElevation`.
  - Add `sheetTonalElevation = BottomSheetDesign.SheetTonalElevation`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Create `BottomSheetDesign.kt`
* Implement tokens object and `Modifier.sheetContour()` extension.

### Step 2: Refine `MinimumDragHandle.kt`
* Update pill dimensions, color alpha (0.5f), and padding (12dp top / 6dp bottom).

### Step 3: Update `SensorGridScreen.kt` & `MapDetailLayout.kt`
* Apply `sheetShape`, `sheetShadowElevation`, `sheetTonalElevation`, and `sheetContour` to `SensorGridScreen.kt`.
* Update header surface shape in `MapDetailLayout.kt` to inherit `BottomSheetDesign.SheetShape` when `!useStatusBarsPadding`.

### Step 4: Harmonize `AppModalBottomSheet.kt` & `FilterBottomSheetScaffold.kt`
* Apply `BottomSheetDesign.SheetShape`, `tonalElevation`, and contour border.

### Step 5: Update Scaffold Map Sheets
* In `MapScreenWithTrack.kt`, `PeriodMapScreen.kt`, and `WorkoutClusterHeatmapScreen.kt`, configure `sheetShape`, `sheetShadowElevation`, and `sheetTonalElevation`.

### Step 6: Author Unit Tests & Full Regression
* Author `BottomSheetDesignTest.kt`, `MinimumDragHandleTest.kt`, and `BottomSheetVisualContractTest.kt`.
* Run targeted tests: `./gradlew testDebugUnitTest --tests "*BottomSheet*"` and `./gradlew testDebugUnitTest --tests "*MinimumDragHandle*"`.
* Run full suite: `./gradlew testDebugUnitTest`.

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Unit tests covering token values, modifier application, and visual contract conformity.
  - Full clean-room test suite execution.
* **Rollback Strategy**:
  - Changes are isolated on `feature/ATT-1588`. In case of unexpected regressions, reverting the branch leaves `sprint/2026-40.4` intact.
