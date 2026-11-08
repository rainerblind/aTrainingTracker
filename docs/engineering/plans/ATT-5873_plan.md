# Stage 3: Implementation Plan - ATT-5873: Modal Bottom Sheet Close Button Elimination

**Ticket**: [ATT-5873](https://atrainingtracker.atlassian.net/browse/ATT-5873)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-UI-473` (`REQ-UI-473.1`..`3`)  
**Test Mapping**: `TST-UI-473` (`TST-UI-473.1`..`4`)  
**Branch**: `feature/ATT-5873`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-08  

---

## 1. Problem Description & Background
In `AppModalBottomSheet.kt`, `showCloseButton: Boolean = true` is hardcoded regardless of whether a bottom action bar (`actions != null`) is provided. This renders redundant dismissal controls (both a top-right `✕` and bottom `Abbrechen` button) across settings and sync dialogs (`Dropbox`, `Google Drive`, `Export`, `Display`, `Units`, `Strava`, `EditEquipmentDialog`). In `ActivityTypeSelectionDialog.kt`, single-tap sport selection redundantly displays a bottom `CancelOnly` action bar alongside the top `✕` button.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-473` (*Modal Bottom Sheet Close Button Elimination for Bottom Action Bar Dialogs & Activity Type Selection Polish*)
* **Test Mapping**: `TST-UI-473` (*Modal Bottom Sheet Close Button Default Contract & Activity Type Selection Test Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Header-Only Invariance**: Sheets with `actions == null` (`PairingProtocolBottomSheet`, `AntServicesStatusSheet`, `ClusterInfoDialog`) retain top-right `✕` by default.
2. **Explicit Override Support**: Callers explicitly passing `showCloseButton = true/false` remain respected regardless of `actions`.
3. **Source Compatibility**: Reordering `actions` before `showCloseButton` preserves 100% source compatibility across all named-argument call sites.
4. **Window Insets & Geometry**: `navigationBarsPadding()`, `imePadding()`, drag handles, and bottom sheet elevation tokens remain untouched.

---

## 4. Proposed Architectural Changes

### Component 1: `AppModalBottomSheet.kt` (Core UI Layer)
* Reorder parameter `actions` before `showCloseButton` in `AppModalBottomSheet`, `AppBottomSheetContent`, and private `AppBottomSheetBody`.
* Change parameter default to `showCloseButton: Boolean = (actions == null)`.
* Forward `actions` and `showCloseButton` from outer composables to `AppBottomSheetBody`.

### Component 2: `ActivityTypeSelectionDialog.kt` (Settings UI Layer)
* Remove `actions = { AppDialogActions.CancelOnly(onCancel = onDismiss) }`.
* Remove unused `AppDialogActions` import.
* With `actions == null`, the dialog cleanly displays the header `✕` button without a bottom action bar.

### UI Consistency (Rule 23)
* **Reference screen / component**: Existing `AppModalBottomSheet` (`ATT-900`, `ATT-1072`).
* **Reused components**: `AppModalBottomSheet`, `AppBottomSheetContent`, `IconButton`, `Icons.Default.Close`.
* **Theme tokens**: `BottomSheetDesign.SheetShape` (20.dp), `MaterialTheme.colorScheme.surface`.
* **New one-off styles & justification**: None. Retains existing design tokens 1:1.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Reorder Parameters & Set Dynamic Default in `AppModalBottomSheet.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/AppModalBottomSheet.kt`
* Changes: Move `actions` before `showCloseButton`, default `showCloseButton = (actions == null)` in `AppModalBottomSheet`, `AppBottomSheetContent`, and `AppBottomSheetBody`.

### Step 2: Simplify `ActivityTypeSelectionDialog.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/trackingtabs/ActivityTypeSelectionDialog.kt`
* Changes: Remove `actions` slot and clean up unused `AppDialogActions` import (`REQ-UI-473.2`).

### Step 3: Implement Unit & Contract Tests in `AppModalBottomSheetTest.kt`
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/AppModalBottomSheetTest.kt`
* Changes: Add reflection/contract test verifying `actions` precedes `showCloseButton` in parameter signatures, dynamic default behavior, and `ActivityTypeSelectionDialog` clean contract (`TST-UI-473.1`, `TST-UI-473.2`).

### Step 4: Targeted Unit Tests & Verification
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.core.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.trackingtabs.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit test suite and verify living docs parity with `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Branch isolation (`feature/ATT-5873`) allows clean rollback via `git restore .`.
