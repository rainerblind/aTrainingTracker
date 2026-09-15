# Walkthrough - ATT-1034: Unify Bottom Popups and Dialog Action Paradigms

## 1. Summary of Changes
Implemented comprehensive bottom sheet and popup unification per `REQ-UI-150` and `TST-UI-103`:
1. **Core Design System (`AppDialogActions`)**:
   - Introduced `com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions` establishing standard action button layouts:
     - `SaveCancel`: TextButton ("Abbrechen" / `R.string.Cancel`) on left, Button ("Speichern" / `R.string.save`) on right with `saveEnabled` support.
     - `Confirm`: Full-width Button ("OK" / `android.R.string.ok`).
     - `CancelOnly`: Full-width TextButton ("Abbrechen" / `R.string.Cancel`).
2. **Outlier Dialog Elimination (`EditRouteScreen`)**:
   - Refactored `EditRouteScreen.kt` from a full-screen `Scaffold` with `TopAppBar` into an `AppModalBottomSheet` using `AppDialogActions.SaveCancel`. Preserved existing caller API for `RoutesFragment` and `GpxImportActivity`.
3. **Raw BottomSheet Modernization (`ImportBackupTabsScreen`)**:
   - Modernized `PreImportTuningBottomSheet` to `AppModalBottomSheet` with `headerActions` slot for the info dialog and `AppDialogActions.SaveCancel` with "Speichern".
4. **Semantic Labeling & Spacing Standardization**:
   - **Data-Persisting Dialogs**: `EditDeviceDialog`, `EditSensorFieldDialog`, `ConfigureFilterDialog`, and `UnitsSettingsDialog` standardized to `AppDialogActions.SaveCancel` and updated from "OK"/"Done" to "Speichern" (`R.string.save`).
   - `EditEquipmentDialog`, `EditSportTypeDialog`, and `WorkoutClusterHeatmapScreen` (`EditWorkoutClusterIdentityDialog`) adopted `AppDialogActions.SaveCancel`.
   - **Immediate / Informational Sheets**: `ExportSettingsDialog` and `DisplaySettingsDialog` updated from untranslated English "Done" (`R.string.Done`) to "OK" (`android.R.string.ok`).
   - `SensorSourceDialog`, `ClusterInfoDialog`, and `ExportDetailsDialog` adopted `AppDialogActions.Confirm`.
   - `ActivityTypeSelectionDialog`, `WorkoutClusterSelectionDialog`, and `EditWorkoutClusterDialog` adopted `AppDialogActions.CancelOnly`.
5. **Testing**:
   - Implemented `AppDialogActionsIntegrityTest` verifying component contracts and reflections.

---

## 2. Modified & Created Files
- `[NEW]` `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/AppDialogActions.kt`
- `[NEW]` `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/AppDialogActionsIntegrityTest.kt`
- `[MODIFY]` `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/AppModalBottomSheet.kt`
- `[MODIFY]` `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/EditRouteScreen.kt`
- `[MODIFY]` `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt`
- `[MODIFY]` `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceDialog.kt`
- `[MODIFY]` `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt`
- `[MODIFY]` `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/ConfigureFilterDialog.kt`
- `[MODIFY]` `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/units/UnitsSettingsDialog.kt`
- `[MODIFY]` `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/export/ExportSettingsDialog.kt`
- `[MODIFY]` `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsDialog.kt`
- `[MODIFY]` `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt`
- `[MODIFY]` `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/trackingtabs/ActivityTypeSelectionDialog.kt`
- `[MODIFY]` `app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EditEquipmentDialog.kt`
- `[MODIFY]` `app/src/main/java/com/atrainingtracker/banalservice/ui/sporttype/EditSportTypeDialog.kt`
- `[MODIFY]` `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt`
- `[MODIFY]` `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt`
- `[MODIFY]` `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterInfoDialog.kt`
- `[MODIFY]` `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/export/ExportStatusGroup.kt`

---

## 3. Verification & Validation Evidence
* **SWE.4 Unit Tests**:
  - `AppDialogActionsIntegrityTest`: PASS (Contract compliance for SaveCancel, Confirm, CancelOnly, EditRouteScreen, PreImportTuningBottomSheet, AppModalBottomSheet).
  - `ModalBottomSheetDialogsIntegrityTest`: PASS (All bottom sheet dialogs and invariant centered dialogs intact).
  - Full debug unit test suite for core UI components: PASS (0 failures, 0 regressions).
