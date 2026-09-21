# Implementation Plan - ATT-1034: Unification of Bottom Popups and Dialog Paradigms

## 1. Overview & Problem Statement
In accordance with Stage 1 Analysis (`docs/engineering/analysis/ATT-1034_analysis.md`) and requirements defined in `REQ-UI-150`, this implementation plan unifies bottom popup sheets and modal dialogs across the application. 

Currently:
1. **Full-screen outlier**: `EditRouteScreen.kt` renders a full-screen `Scaffold` with `TopAppBar` rather than a modal bottom sheet.
2. **Raw component divergence**: `ImportBackupTabsScreen.kt` re-invents a raw `ModalBottomSheet` with custom `OutlinedButton` rather than adhering to `AppModalBottomSheet`.
3. **Bottom action layout disparities**: Action buttons vary arbitrarily across dialogs (left-right with spacer, right-packed with spacer, 50/50 split width).
4. **Semantic action wording inconsistencies**: Data-mutating dialogs (`EditDeviceDialog`, `EditSensorFieldDialog`, `ConfigureFilterDialog`, `UnitsSettingsDialog`, `ImportBackupTabsScreen`) display "OK" or "Done" instead of "Speichern" (`R.string.save`). Immediate/informational sheets (`ExportSettingsDialog`, `DisplaySettingsDialog`) display untranslated English "Done" (`R.string.Done`) instead of "OK" (`android.R.string.ok`).

---

## 2. Requirement & Test Traceability
* **Requirement**: `REQ-UI-150` (*Bottom Sheet Presentation & Action Paradigm Unification*)
* **Test Case**: `TST-UI-103` (*Verification of Unified Bottom Sheet Dialogs & Semantic Action Paradigms*)
* **Analysis**: `docs/engineering/analysis/ATT-1034_analysis.md`

---

## 3. Architecture & Core Component Design

### Standardized Action Bar (`AppDialogActions`)
A new design system component will be established in:
`app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/AppDialogActions.kt`

Providing standardized action composables:
1. **`AppDialogActions.SaveCancel`**:
   ```kotlin
   @Composable
   fun SaveCancel(
       onSave: () -> Unit,
       onCancel: () -> Unit,
       modifier: Modifier = Modifier,
       saveText: String = stringResource(R.string.save),
       cancelText: String = stringResource(R.string.Cancel),
       saveEnabled: Boolean = true
   ) {
       Row(
           modifier = modifier.fillMaxWidth(),
           verticalAlignment = Alignment.CenterVertically
       ) {
           TextButton(onClick = onCancel) {
               Text(cancelText)
           }
           Spacer(modifier = Modifier.weight(1f))
           Button(
               onClick = onSave,
               enabled = saveEnabled
           ) {
               Text(saveText)
           }
       }
   }
   ```
2. **`AppDialogActions.Confirm`**:
   ```kotlin
   @Composable
   fun Confirm(
       onConfirm: () -> Unit,
       modifier: Modifier = Modifier,
       confirmText: String = stringResource(android.R.string.ok)
   ) {
       Row(
           modifier = modifier.fillMaxWidth(),
           verticalAlignment = Alignment.CenterVertically
       ) {
           Button(
               onClick = onConfirm,
               modifier = Modifier.fillMaxWidth()
           ) {
               Text(confirmText)
           }
       }
   }
   ```
3. **`AppDialogActions.CancelOnly`**:
   ```kotlin
   @Composable
   fun CancelOnly(
       onCancel: () -> Unit,
       modifier: Modifier = Modifier,
       cancelText: String = stringResource(R.string.Cancel)
   ) {
       Row(
           modifier = modifier.fillMaxWidth(),
           verticalAlignment = Alignment.CenterVertically
       ) {
           TextButton(
               onClick = onCancel,
               modifier = Modifier.fillMaxWidth()
           ) {
               Text(cancelText)
           }
       }
   }
   ```

---

## 4. Detailed Component Modifications

### A. Full-Screen Outlier Migration: `EditRouteScreen.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/EditRouteScreen.kt`
* **Action**:
  * Replace full-screen `Scaffold` + `TopAppBar` with `AppModalBottomSheet`.
  * Title: `stringResource(R.string.route_edit)`.
  * Icon: `Icons.Default.Edit` (or route icon).
  * Insets & scroll handling: Managed by `AppModalBottomSheet` with internal vertical scroll.
  * Bottom action: `AppDialogActions.SaveCancel(onSave = { onSave(...) }, onCancel = onCancel)`.
  * Call site compatibility: Signature `EditRouteScreen(routeSummary: RouteSummary, onSave: (RouteSummary) -> Unit, onCancel: () -> Unit)` is preserved, ensuring zero breakage in `RoutesFragment.kt` and `GpxImportActivity.kt`.

### B. Cluster Parameters Migration: `ImportBackupTabsScreen.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/importbackup/ImportBackupTabsScreen.kt`
* **Action**:
  * Replace raw `ModalBottomSheet` with `AppModalBottomSheet`.
  * Replace custom `OutlinedButton` + `Button` layout with `AppDialogActions.SaveCancel`.
  * Semantic label: Update action button from "OK" to `R.string.save` ("Speichern").

### C. Semantic Label & Layout Standardization on Data-Storing Dialogs
* **`EditDeviceDialog.kt`**:
  * Replace custom right-packed row with `AppDialogActions.SaveCancel`.
  * Action button wording: Change `R.string.OK` to `R.string.save` ("Speichern").
* **`EditSensorFieldDialog.kt`**:
  * Replace custom right-packed row with `AppDialogActions.SaveCancel`.
  * Action button wording: Change `R.string.OK` to `R.string.save` ("Speichern").
* **`ConfigureFilterDialog.kt`**:
  * Replace custom right-packed row with `AppDialogActions.SaveCancel`.
  * Action button wording: Change `R.string.OK` to `R.string.save` ("Speichern").
* **`UnitsSettingsDialog.kt`**:
  * Replace 50/50 split width row with `AppDialogActions.SaveCancel`.
  * Action button wording: Change `R.string.Done` to `R.string.save` ("Speichern").
* **`EditEquipmentDialog.kt`**:
  * Replace custom right-packed row with `AppDialogActions.SaveCancel`.
* **`EditSportTypeDialog.kt`**:
  * Replace custom right-packed row with `AppDialogActions.SaveCancel`.
* **`WorkoutClusterHeatmapScreen.kt` (`EditWorkoutClusterIdentityDialog`)**:
  * Replace custom layout with `AppDialogActions.SaveCancel`.

### D. Semantic Label & Layout Standardization on Immediate/Informational Dialogs
* **`ExportSettingsDialog.kt`**:
  * Replace untranslated `R.string.Done` with `AppDialogActions.Confirm(onConfirm = onDismiss, confirmText = stringResource(android.R.string.ok))`.
* **`DisplaySettingsDialog.kt`**:
  * Replace untranslated `R.string.Done` with `AppDialogActions.Confirm(onConfirm = onDismiss, confirmText = stringResource(android.R.string.ok))`.
* **`SensorSourceDialog.kt`**:
  * Replace manual button layout with `AppDialogActions.Confirm(onConfirm = onDismiss, confirmText = stringResource(android.R.string.ok))`.
* **`ActivityTypeSelectionDialog.kt`**:
  * Replace manual cancel button with `AppDialogActions.CancelOnly(onCancel = onDismiss)`.

---

## 5. Invariant Protection & Non-Functional Requirements
1. **Zero Data Loss**: Form validations (e.g. valid route name, sensor smoothing parameters, wheel circumference) remain strictly enforced.
2. **Localization Parity**: All button strings use standardized Android and app resources (`R.string.save`, `R.string.Cancel`, `android.R.string.ok`) which exist in all 9 supported languages.
3. **System Insets & Ergonomics**: Dialogs inherit safe IME padding and navigation bar insets from `AppModalBottomSheet`.
4. **Preserved Invariants**: Centered dialogs specified in ATT-900 (`LapSummaryDialog`, `DeleteConfirmationDialog`, `WorkoutDeleteDialog`) remain untouched.

---

## 6. Verification Plan
* **Unit Tests**:
  * `AppDialogActionsIntegrityTest.kt`: Unit tests verifying that `AppDialogActions` exposes `SaveCancel`, `Confirm`, and `CancelOnly` with expected parameters, default values, and semantic labels.
  * Update `ModalBottomSheetDialogsIntegrityTest.kt` to assert compliance for `EditRouteScreen` and updated dialogs.
* **Regression Test**:
  * Execute `./gradlew testDebugUnitTest` to confirm 100% clean test execution.
