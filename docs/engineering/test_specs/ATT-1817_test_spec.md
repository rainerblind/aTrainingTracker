# Stage 2: Requirement & Test Specification - ATT-1817: Standardize popup and bottom sheet surface background across LiveSegment, Routes, Segments, and Settings

**Ticket**: [ATT-1817](https://rainerblind.atlassian.net/browse/ATT-1817)  
**Sub-task**: [ATT-1874](https://rainerblind.atlassian.net/browse/ATT-1874) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-218` (*UI/Theme: Standardized Surface Background Tokens and Zero Tonal Elevation across Popups, Bottom Sheets, and Dialogs*)  
**Test Spec ID**: `TST-UI-172`  
**Branch**: `feature/ATT-1817`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Requirement Specification (REQ-UI-218)

### 1.1 Problem Statement & Rationale

In `aTrainingTracker`, popups, bottom sheets, and dialogs are used extensively for live telemetry inspection (LiveSegment in the cockpit), route and segment inspection in map views, settings sheets, and user confirmation prompts.

Photographic evidence and forensic investigation revealed that sheets exhibit visual contrast seams, contrasting grey backgrounds (`#EDEDED`, RGB 237, 237, 237) against pure white elements, or non-standard container colors:
1. **LiveSegment Popup (`SensorGridScreen` / `LIveSegmentSheet`)**: The header card is pure white (`#FFFFFF`), but the elevation container below renders as light grey (`#EDEDED`) due to Material 3's 2dp tonal elevation formula ($6.94\%$ grey overlay).
2. **Route & Segment Bottom Sheets (`MapScreenWithTrack.kt`)**: The bottom sheet scaffold omitted `sheetContainerColor`, defaulting to `surfaceContainerLow` with 2dp tonal elevation.
3. **Modal Sheets & Settings Dialogs (`AppModalBottomSheet.kt`, `FilterBottomSheetScaffold.kt`)**: Modal sheets defaulted to `surfaceContainerLow` and applied 2dp tonal elevation.
4. **Confirmation Dialogs**: `AlertDialog` instances in `StravaSettingsDialog.kt`, `WorkoutClusterHeatmapScreen.kt`, etc., omitted `containerColor` and `tonalElevation`, defaulting to `surfaceContainerHigh` with 6dp tonal elevation.

The objective of `REQ-UI-218` is to standardize all bottom sheets, modal popups, and dialogs to use clean, consistent `MaterialTheme.colorScheme.surface` tokens with zero tonal elevation (`SheetTonalElevation = 0.dp`), eliminating artificial grey overlays and maintaining unified, seamless surfaces across the application.

---

### 1.2 Functional & Architectural Requirements

1. **Centralized Design Token Refinement (`BottomSheetDesign.kt`)**:
   - `SheetTonalElevation` SHALL be standardized at `0.dp` (reduced from `2.dp`) to eliminate Material 3's automatic 6.94% grey tint overlay over white surfaces in Light mode.
   - All physical boundary tokens (`SheetCornerRadius = 20.dp`, `SheetShape`, `SheetShadowElevation = 8.dp`, `BorderWidth = 1.dp`, `DragHandleWidth = 32.dp`, `DragHandleHeight = 3.dp`) SHALL remain strictly preserved.

2. **Modal Bottom Sheets (`AppModalBottomSheet.kt`, `FilterBottomSheetScaffold.kt`)**:
   - In `AppModalBottomSheet.kt`, `ModalBottomSheet` SHALL explicitly declare `containerColor = MaterialTheme.colorScheme.surface` and `tonalElevation = BottomSheetDesign.SheetTonalElevation` (`0.dp`).
   - In `AppModalBottomSheet.kt`, `AppBottomSheetContent` SHALL declare `color = MaterialTheme.colorScheme.surface` and `tonalElevation = BottomSheetDesign.SheetTonalElevation` (`0.dp`).
   - In `FilterBottomSheetScaffold.kt`, `ModalBottomSheet` SHALL explicitly declare `containerColor = MaterialTheme.colorScheme.surface` and `tonalElevation = BottomSheetDesign.SheetTonalElevation` (`0.dp`).

3. **Persistent Bottom Sheet Scaffolds (`MapScreenWithTrack.kt`, `SensorGridScreen.kt`, `PeriodMapScreen.kt`, `WorkoutClusterHeatmapScreen.kt`)**:
   - `BottomSheetScaffold` instances in `MapScreenWithTrack.kt`, `SensorGridScreen.kt`, `PeriodMapScreen.kt`, and `WorkoutClusterHeatmapScreen.kt` SHALL uniformly declare:
     - `sheetContainerColor = MaterialTheme.colorScheme.surface`
     - `sheetTonalElevation = BottomSheetDesign.SheetTonalElevation` (`0.dp`)
     - `sheetShape = BottomSheetDesign.SheetShape`
     - `sheetShadowElevation = BottomSheetDesign.SheetShadowElevation` (`8.dp`)

4. **LiveSegment & Map Detail Layout Seamlessness (`MapDetailLayout.kt`, `LIveSegmentSheet.kt`)**:
   - In `MapDetailLayout.kt`, when operating in bottom sheet mode (`!useStatusBarsPadding`), the root `Column` SHALL apply `.background(MaterialTheme.colorScheme.surface)`.
   - The slotted header `Surface`, the elevation profile `Surface`, and the analytics `Surface` SHALL uniformly bind `color = MaterialTheme.colorScheme.surface` with zero tonal elevation, eliminating visual seams.
   - In `LIveSegmentSheet.kt`, header and metrics columns SHALL seamlessly share `MaterialTheme.colorScheme.surface`.

5. **Confirmation Dialogs Standardization (`StravaSettingsDialog.kt`, `WorkoutClusterHeatmapScreen.kt`, `WorkoutClustersScreen.kt`, `DevicesTabbedScreen.kt`, `ImportBackupTabsScreen.kt`)**:
   - Confirmation `AlertDialog` instances SHALL explicitly declare:
     - `containerColor = MaterialTheme.colorScheme.surface`
     - `tonalElevation = 0.dp`
     matching `DeleteConfirmationDialog.kt` and `DeleteOldWorkoutsDialog.kt`.

6. **Preservation of Core Invariants**:
   - Top curvature (`20.dp`), boundary contour stroke (`1.dp` `outlineVariant(0.6f)`), drop shadow (`8.dp`), and drag handle pill ($32\text{dp} \times 3\text{dp}$) SHALL remain 100% preserved.
   - System bars edge-to-edge insets (`navigationBarsPadding()`, `imePadding()`, `statusBarsPadding()`) per `REQ-UI-148` SHALL NOT be altered.
   - Zero regressions in telemetry graphs, map snapshot sharing, or sensor grid layouts.

---

### 1.3 Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

1. **Original Requirement ID & Target**:
   - `REQ-UI-189` (*UI/Sheets: Standardized Boundaries, Contours, Drop Shadow Elevation & Drag Affordances*), targeting `BottomSheetDesign.kt`, `SensorGridScreen.kt`, `AppModalBottomSheet.kt`.
   - `REQ-UI-196` (*LiveSegment & Bottom Sheets: Refined Subtle Drag Handle, Harmonized Popup Spacing & Unified Surface Background*), targeting `SensorGridScreen.kt`, `MapDetailLayout.kt`, `LIveSegmentSheet.kt`.
2. **Historical Origin & Commit Trace**:
   - Commit `69c84e1b` (ATT-1588): Introduced `BottomSheetDesign.kt` with `SheetTonalElevation = 2.dp`.
   - Commit `e7ae455c` (ATT-1644) & commit `54f90713` (ATT-1735): Harmonized drag handle dimensions and attempted to fix LiveSegment popup contrast by applying `sheetContainerColor = MaterialTheme.colorScheme.surface` in `SensorGridScreen.kt` and `MapDetailLayout.kt`.
3. **Root Reason for Existing Formulation**:
   - `SheetTonalElevation = 2.dp` was chosen under the assumption that subtle tonal elevation was desirable in Material 3. However, M3's mathematical tint formula applies a 6.94% overlay (`#EDEDED`) to white surfaces. Furthermore, while `SensorGridScreen` had `sheetContainerColor = surface`, other persistent scaffolds (`MapScreenWithTrack.kt`, etc.) and modal sheets omitted `containerColor`. Additionally, because `LiveSegmentSheet`'s header used `Modifier.background()` while the elevation container used `Surface()`, the 2.dp tonal elevation produced an unintended contrast split.
4. **Preservation of Core Invariants**:
   - Setting `SheetTonalElevation = 0.dp` and explicitly specifying `containerColor = MaterialTheme.colorScheme.surface` across all scaffolds and sheets eliminates the contrast split while preserving all physical boundary delineations:
     - Top curvature: `SheetCornerRadius = 20.dp` (`SheetShape`).
     - Top boundary stroke: 1.dp `outlineVariant` at 60% opacity (`sheetContour`).
     - Drop shadow elevation: 8.dp (`SheetShadowElevation`).
     - Drag handle pill: 32dp x 3dp with subtle `outlineVariant` color.
     - Full-screen status bars edge-to-edge insets (`REQ-UI-148`).

---

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Design Token Refinement)**:
  * *Given* `BottomSheetDesign` design tokens object,
  * *When* `SheetTonalElevation` is evaluated,
  * *Then* it SHALL equal `0.dp`.

* **Criterion 2 (Modal Bottom Sheet Uniform Surface)**:
  * *Given* an athlete opening any modal bottom sheet (`AppModalBottomSheet`, `FilterBottomSheetScaffold`) or settings sheet (`AppBottomSheetContent`),
  * *When* viewed in Light mode,
  * *Then* the sheet container SHALL render with clean `MaterialTheme.colorScheme.surface` background with 0.dp tonal elevation, free of grey tonal tinting.

* **Criterion 3 (Persistent Bottom Sheet Scaffold Uniform Surface)**:
  * *Given* an athlete viewing map bottom sheets (`MapScreenWithTrack`, `PeriodMapScreen`, `WorkoutClusterHeatmapScreen`) or cockpit live segments (`SensorGridScreen`),
  * *When* inspecting the sheet container,
  * *Then* the scaffold SHALL explicitly bind `sheetContainerColor = MaterialTheme.colorScheme.surface` and `sheetTonalElevation = BottomSheetDesign.SheetTonalElevation` (`0.dp`).

* **Criterion 4 (LiveSegment & Map Detail Layout Seamlessness)**:
  * *Given* an active live segment in `SensorGridScreen` or route/segment sheet in `MapScreenWithTrack`,
  * *When* viewing the boundary between the header card and the elevation profile container,
  * *Then* both regions SHALL render with identical, continuous `MaterialTheme.colorScheme.surface` fill without contrasting grey patches or visible background seams.

* **Criterion 5 (Confirmation Dialogs Standardization)**:
  * *Given* an athlete triggering a confirmation dialog (`StravaSettingsDialog`, `WorkoutClusterHeatmapScreen`, etc.),
  * *When* the `AlertDialog` is displayed,
  * *Then* its container SHALL use `MaterialTheme.colorScheme.surface` with `0.dp` tonal elevation.

---

## 2. Test Specification (TST-UI-172)

### Test Case 1: Design Tokens Verification (`TST-UI-172.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetDesignTest.kt`
* **Preconditions**: `BottomSheetDesign` class loaded.
* **Action**: Assert `BottomSheetDesign.SheetTonalElevation == 0.dp`.
* **Expected Result**: Assertion passes; `SheetTonalElevation` is strictly 0.dp while `SheetCornerRadius` (20.dp), `SheetShadowElevation` (8.dp), `BorderWidth` (1.dp), `DragHandleWidth` (32.dp), and `DragHandleHeight` (3.dp) remain preserved.

### Test Case 2: Modal Bottom Sheet Visual Contract Tests (`TST-UI-172.2`)
* **Scope**: Unit & Source Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetVisualContractTest.kt`
* **Preconditions**: Source files for `AppModalBottomSheet.kt` and `FilterBottomSheetScaffold.kt` loaded.
* **Action**: Parse AST / source content for `ModalBottomSheet` and `AppBottomSheetContent`.
* **Expected Result**:
  * `AppModalBottomSheet.kt` sets `containerColor = MaterialTheme.colorScheme.surface`.
  * `AppModalBottomSheet.kt` sets `tonalElevation = BottomSheetDesign.SheetTonalElevation`.
  * `AppBottomSheetContent` sets `color = MaterialTheme.colorScheme.surface` and `tonalElevation = BottomSheetDesign.SheetTonalElevation`.
  * `FilterBottomSheetScaffold.kt` sets `containerColor = MaterialTheme.colorScheme.surface`.
  * `FilterBottomSheetScaffold.kt` sets `tonalElevation = BottomSheetDesign.SheetTonalElevation`.

### Test Case 3: Persistent Map & Cockpit Scaffolds Contract Tests (`TST-UI-172.3`)
* **Scope**: Unit & Source Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetVisualContractTest.kt`
* **Preconditions**: Source files for `MapScreenWithTrack.kt`, `SensorGridScreen.kt`, `PeriodMapScreen.kt`, and `WorkoutClusterHeatmapScreen.kt` loaded.
* **Action**: Verify `BottomSheetScaffold` parameters in all 4 files.
* **Expected Result**:
  * Every scaffold specifies `sheetContainerColor = MaterialTheme.colorScheme.surface`.
  * Every scaffold specifies `sheetTonalElevation = BottomSheetDesign.SheetTonalElevation`.
  * Every scaffold specifies `sheetShape = BottomSheetDesign.SheetShape`.
  * Every scaffold specifies `sheetShadowElevation = BottomSheetDesign.SheetShadowElevation`.

### Test Case 4: MapDetailLayout & LiveSegment Uniform Surface Contract Tests (`TST-UI-172.4`)
* **Scope**: Unit & Source Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetVisualContractTest.kt`
* **Preconditions**: Source files for `MapDetailLayout.kt` and `LIveSegmentSheet.kt` loaded.
* **Action**: Verify background and surface bindings across header and lower containers.
* **Expected Result**:
  * `MapDetailLayout.kt` applies `.background(MaterialTheme.colorScheme.surface)` when `!useStatusBarsPadding`.
  * Slotted header `Surface`, elevation profile `Surface`, and analytics `Surface` bind `color = MaterialTheme.colorScheme.surface`.
  * `LIveSegmentSheet.kt` header applies `MaterialTheme.colorScheme.surface`.

### Test Case 5: Confirmation Dialogs Container Standardization (`TST-UI-172.5`)
* **Scope**: Unit & Source Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetVisualContractTest.kt`
* **Preconditions**: Source files for `StravaSettingsDialog.kt`, `WorkoutClusterHeatmapScreen.kt`, etc. loaded.
* **Action**: Verify `AlertDialog` invocations specify `containerColor = MaterialTheme.colorScheme.surface` and `tonalElevation = 0.dp`.
* **Expected Result**: All confirmation dialogs adhere to standardized surface container color tokens.

### Test Case 6: Clean-Room Full Suite Regression Execution (`TST-UI-172.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-172.1` | Unit | `BottomSheetDesignTest.testBottomSheetDesign_tokenConstants` | `REQ-UI-218` (item 1) | Specified |
| `TST-UI-172.2` | Contract | `BottomSheetVisualContractTest.testModalSheets_consumeSurfaceAndZeroTonalElevation` | `REQ-UI-218` (item 2) | Specified |
| `TST-UI-172.3` | Contract | `BottomSheetVisualContractTest.testPersistentScaffolds_consumeSurfaceAndZeroTonalElevation` | `REQ-UI-218` (item 3) | Specified |
| `TST-UI-172.4` | Contract | `BottomSheetVisualContractTest.testMapDetailLayoutAndLiveSegment_uniformSurface` | `REQ-UI-218` (item 4) | Specified |
| `TST-UI-172.5` | Contract | `BottomSheetVisualContractTest.testAlertDialogs_consumeSurfaceAndZeroTonalElevation` | `REQ-UI-218` (item 5) | Specified |
| `TST-UI-172.6` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
