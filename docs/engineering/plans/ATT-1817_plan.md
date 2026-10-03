# Stage 3: Implementation Plan - ATT-1817: Standardize popup and bottom sheet surface background across LiveSegment, Routes, Segments, and Settings

**Ticket**: [ATT-1817](https://rainerblind.atlassian.net/browse/ATT-1817)  
**Sub-task**: [ATT-1875](https://rainerblind.atlassian.net/browse/ATT-1875) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-218` (*UI/Theme: Standardized Surface Background Tokens and Zero Tonal Elevation across Popups, Bottom Sheets, and Dialogs*)  
**Test Mapping**: `TST-UI-172`  
**Branch**: `feature/ATT-1817`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Description & Background

In `aTrainingTracker`, popups, bottom sheets, and dialogs are used extensively for live telemetry inspection (LiveSegment in the cockpit), route and segment inspection in map views, settings sheets, and user confirmation prompts.

Photographic evidence and forensic investigation revealed that sheets exhibit visual contrast seams, contrasting grey backgrounds (`#EDEDED`, RGB 237, 237, 237) against pure white elements, or non-standard container colors:
1. **LiveSegment Popup (`SensorGridScreen` / `LiveSegmentSheet`)**: The header card is pure white (`#FFFFFF`), but the elevation container below renders as light grey (`#EDEDED`) due to Material 3's 2dp tonal elevation formula ($6.94\%$ grey overlay).
2. **Route & Segment Bottom Sheets (`MapScreenWithTrack.kt`)**: The bottom sheet scaffold omitted `sheetContainerColor`, defaulting to `surfaceContainerLow` with 2dp tonal elevation.
3. **Modal Sheets & Settings Dialogs (`AppModalBottomSheet.kt`, `FilterBottomSheetScaffold.kt`)**: Modal sheets defaulted to `surfaceContainerLow` and applied 2dp tonal elevation.
4. **Confirmation Dialogs**: `AlertDialog` instances in `StravaSettingsDialog.kt`, `WorkoutClusterHeatmapScreen.kt`, etc., omitted `containerColor` and `tonalElevation`, defaulting to `surfaceContainerHigh` with 6dp tonal elevation.

This implementation plan outlines the construction steps to standardize all bottom sheets, modal popups, and dialogs to use clean, consistent `MaterialTheme.colorScheme.surface` tokens with zero tonal elevation (`SheetTonalElevation = 0.dp`), eliminating artificial grey overlays and maintaining unified, seamless surfaces across the application.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-218` (*UI/Theme: Standardized Surface Background Tokens and Zero Tonal Elevation across Popups, Bottom Sheets, and Dialogs*)
* **Test Mapping**: `TST-UI-172` (*Popups, Bottom Sheets & Dialogs Surface Background Standardization Verification*)
* **Governing Requirements**:
  - `REQ-UI-189`: *UI/Sheets: Standardized Boundaries, Contours, Drop Shadow Elevation & Drag Affordances* (boundary tokens preserved).
  - `REQ-UI-196`: *LiveSegment & Bottom Sheets: Refined Subtle Drag Handle, Harmonized Popup Spacing & Unified Surface Background* (surface background unified).
  - `REQ-UI-148`: *Edge-to-Edge System Bar Insets Guarantee* (insets preserved).
  - `REQ-PRO-001`: *Clean-Room Full Suite Regression Execution* (full test suite pass).

---

## 3. System Invariants & Preserved Behavior

1. **Design Boundary Token Invariant**: Top curvature (`SheetCornerRadius = 20.dp`, `SheetShape`), boundary contour stroke (`1.dp` `outlineVariant(0.6f)`), drop shadow (`SheetShadowElevation = 8.dp`), and drag handle pill ($32\text{dp} \times 3\text{dp}$) SHALL remain 100% preserved.
2. **System Bars Insets Invariant**: System bars edge-to-edge insets (`navigationBarsPadding()`, `imePadding()`, `statusBarsPadding()`) per `REQ-UI-148` SHALL NOT be altered.
3. **Cockpit & Map Interaction Invariant**: Zero regressions in telemetry graphs, map snapshot sharing, scrub synchronization, or sensor grid layouts.
4. **Subtask & Gate Governance**: Subtasks transition directly to `Erledigt` upon passing review audit via `freigabe`. Parent ticket `ATT-1817` must be transitioned to `Final Review (Human)` assigned to `rainer`, preserving the human gate mandate.

---

## 4. Proposed Architectural Changes

### Component 1: `BottomSheetDesign.kt` (Centralized Token Refinement)
- Update `SheetTonalElevation = 0.dp` (reduced from `2.dp`).
- Preserve `SheetCornerRadius = 20.dp`, `SheetShape`, `SheetShadowElevation = 8.dp`, `BorderWidth = 1.dp`, `DragHandleWidth = 32.dp`, `DragHandleHeight = 3.dp`.

### Component 2: Modal Bottom Sheets (`AppModalBottomSheet.kt`, `FilterBottomSheetScaffold.kt`)
- In `AppModalBottomSheet.kt`:
  - `ModalBottomSheet` explicitly declares `containerColor = MaterialTheme.colorScheme.surface` and `tonalElevation = BottomSheetDesign.SheetTonalElevation` (`0.dp`).
  - `AppBottomSheetContent` declares `color = MaterialTheme.colorScheme.surface` and `tonalElevation = BottomSheetDesign.SheetTonalElevation` (`0.dp`).
- In `FilterBottomSheetScaffold.kt`:
  - `ModalBottomSheet` explicitly declares `containerColor = MaterialTheme.colorScheme.surface` and `tonalElevation = BottomSheetDesign.SheetTonalElevation` (`0.dp`).

### Component 3: Persistent Scaffolds (`MapScreenWithTrack.kt`, `SensorGridScreen.kt`, `PeriodMapScreen.kt`, `WorkoutClusterHeatmapScreen.kt`)
- Uniformly declare on `BottomSheetScaffold`:
  - `sheetContainerColor = MaterialTheme.colorScheme.surface`
  - `sheetTonalElevation = BottomSheetDesign.SheetTonalElevation` (`0.dp`)
  - `sheetShape = BottomSheetDesign.SheetShape`
  - `sheetShadowElevation = BottomSheetDesign.SheetShadowElevation` (`8.dp`)

### Component 4: Layout Seamlessness (`MapDetailLayout.kt`, `LIveSegmentSheet.kt`)
- In `MapDetailLayout.kt`:
  - Apply `.background(MaterialTheme.colorScheme.surface)` on the root `Column` when `!useStatusBarsPadding`.
  - Slotted header `Surface`, elevation profile `Surface`, and analytics `Surface` bind `color = MaterialTheme.colorScheme.surface` with zero tonal elevation.
- In `LIveSegmentSheet.kt`:
  - Ensure header and metrics columns seamlessly share `MaterialTheme.colorScheme.surface`.

### Component 5: Confirmation Dialogs Standardization (`StravaSettingsDialog.kt`, `WorkoutClusterHeatmapScreen.kt`, `WorkoutClustersScreen.kt`, `DevicesTabbedScreen.kt`, `ImportBackupTabsScreen.kt`)
- Set `containerColor = MaterialTheme.colorScheme.surface` and `tonalElevation = 0.dp` on confirmation `AlertDialog` instances, matching `DeleteConfirmationDialog.kt` and `DeleteOldWorkoutsDialog.kt`.

### Component 6: Test Suite Alignment (`BottomSheetDesignTest.kt`, `BottomSheetVisualContractTest.kt`)
- Update `BottomSheetDesignTest.kt` to assert `SheetTonalElevation == 0.dp`.
- Update `BottomSheetVisualContractTest.kt` with tests for:
  - Modal sheets (`AppModalBottomSheet.kt`, `FilterBottomSheetScaffold.kt`)
  - Persistent scaffolds (`MapScreenWithTrack.kt`, `SensorGridScreen.kt`, `PeriodMapScreen.kt`, `WorkoutClusterHeatmapScreen.kt`)
  - Layout surfaces (`MapDetailLayout.kt`, `LIveSegmentSheet.kt`)
  - Confirmation dialogs (`StravaSettingsDialog.kt`, etc.)

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `BottomSheetDesign.kt` Tokens
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetDesign.kt`
* **Changes**: Change `SheetTonalElevation = 2.dp` to `SheetTonalElevation = 0.dp`.

### Step 2: Standardize Modal Bottom Sheets
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/AppModalBottomSheet.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/filter/FilterBottomSheetScaffold.kt`
* **Changes**: Add `containerColor = MaterialTheme.colorScheme.surface` and `tonalElevation = BottomSheetDesign.SheetTonalElevation`.

### Step 3: Standardize Persistent Bottom Sheet Scaffolds
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/sensorgrid/SensorGridScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/periodmap/PeriodMapScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/heatmap/WorkoutClusterHeatmapScreen.kt`
* **Changes**: Ensure `sheetContainerColor = MaterialTheme.colorScheme.surface`, `sheetTonalElevation = BottomSheetDesign.SheetTonalElevation`, `sheetShape = BottomSheetDesign.SheetShape`, `sheetShadowElevation = BottomSheetDesign.SheetShadowElevation`.

### Step 4: Refine MapDetailLayout & LiveSegmentSheet Layout Seamlessness
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/sensorgrid/LIveSegmentSheet.kt`
* **Changes**: Apply `Modifier.background(MaterialTheme.colorScheme.surface)` when `!useStatusBarsPadding` and ensure surface bindings have zero tonal elevation.

### Step 5: Standardize Confirmation Dialogs
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/StravaSettingsDialog.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/heatmap/WorkoutClusterHeatmapScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/workoutclusters/WorkoutClustersScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/devices/DevicesTabbedScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/ImportBackupTabsScreen.kt`
* **Changes**: Explicitly set `containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp`.

### Step 6: Update Tests
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetDesignTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetVisualContractTest.kt`
* **Changes**: Update token assertions and add contract tests for modal sheets, scaffolds, and dialogs.

### Step 7: Run Targeted Unit Tests
* **Command**: `./gradlew testDebugUnitTest --tests "*BottomSheet*"`
* **Expected Result**: 100% pass across all bottom sheet unit tests.

---

## 6. Verification & Rollback Plan

* **Verification**: Run targeted tests during construction, followed by the complete clean-room test suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback Plan**: In the event of an irrecoverable issue, git branch isolation on `feature/ATT-1817` allows clean reset to `sprint/2026-40.7` without impacting production or sprint branches.
