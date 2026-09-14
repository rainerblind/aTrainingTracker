# Walkthrough - ATT-900: Modernize Dialogs to Bottom Popups (AppModalBottomSheet)

## 1. Executive Summary
Under **ATT-900**, interactive configuration, settings, editing, and device/sensor selection dialogs across all modules were modernized into bottom popups backed by `AppModalBottomSheet`. This transition significantly improves one-handed mobile ergonomics and edge-to-edge navigation bar integration. Per explicit ticket requirements, transient heads-up displays (`LapSummaryDialog`) and destructive confirmation alerts (`DeleteConfirmationDialog`, `WorkoutDeleteDialog`, `DeleteOldWorkoutsDialog`) are preserved as centered dialogs.

## 2. Changes Implemented

### A. Core UI Design System (`ui.components.core`)
* **`AppModalBottomSheet.kt`**: Extended with `iconPainter: Painter? = null` and `iconTint: Color = MaterialTheme.colorScheme.primary` (allowing `Color.Unspecified` for multi-colored sport logos per `REQ-UI-114`).

### B. Converted Interactive Dialogs
1. **Drawer Settings Popups**:
   - `ExportSettingsDialog.kt` & `ExportSettingsDialogFragment.kt`: Refactored to `AppModalBottomSheet` with `Icons.Default.Upload`, export toggles (TCX/GPX/GC/CSV), and "Done" action button.
   - `UnitsSettingsDialog.kt` & `UnitsSettingsDialogFragment.kt`: Refactored to `AppModalBottomSheet` with `Icons.Default.SquareFoot`, Metric vs. Imperial radio selection, and "Cancel" / "Done" action buttons.
   - `DisplaySettingsDialog.kt` & `DisplaySettingsDialogFragment.kt`: Refactored to `AppModalBottomSheet` with `Icons.Default.DisplaySettings`, toggles for portrait locking, keep screen on, and unlock behavior, with "Done" action button.
   - `ActivityTypeSelectionDialog.kt` & `ActivityTypeSelectionDialogFragment.kt`: Refactored to `AppModalBottomSheet` with `ic_table_edit` icon, un-tinted authentic sport logos, and "Cancel" action button.
   - Transparent Edge-to-Edge Theme: All 4 bridge `DialogFragment` hosts configured with `setStyle(STYLE_NORMAL, android.R.style.Theme_Translucent_NoTitleBar)`.

2. **Tracking & Hardware Selection**:
   - `SensorSourceDialog.kt`: Modernized to `AppModalBottomSheet` with sensor icon, 3-tier telemetry breakdown (Source Device, Active Backups, Not Connected), and clickable device rows.
   - `DeviceTypeSelectionDialog.kt`: Modernized to `AppModalBottomSheet` with protocol hardware types list and "All Devices" text button.

3. **Tracking Grid & Sensor Field**:
   - `EditSensorFieldDialog.kt`: Modernized to `AppModalBottomSheet` with sensor field type, device source, and text size selection.
   - `ConfigureFilterDialog.kt`: Modernized to `AppModalBottomSheet` with filter type selection, smoothing factor alpha slider, and cancel/OK action buttons.

4. **Sport Types & Equipment**:
   - `EditSportTypeDialog.kt`: Modernized to `AppModalBottomSheet` with sport icon, base sport selector, speed thresholds, and linked equipment.
   - `EditEquipmentDialog.kt`: Modernized to `AppModalBottomSheet` with equipment icon, name field, bike frame type selector, sport types, and sensor linkages.
   - `EditDeviceDialog.kt`: Modernized `EditDeviceDialog`, `EquipmentSection`, and `CorrectCalibrationDialog` to `AppModalBottomSheet` with device status LED, protocol, calibration factor, and wheel circumference.

5. **Workout Clusters & Spatial Details**:
   - `WorkoutClusterSelectionDialog` (`WorkoutClusterComponents.kt`): Modernized to `AppModalBottomSheet` for selecting cluster candidates.
   - `EditWorkoutClusterDialog` (`WorkoutClusterComponents.kt`): Modernized to `AppModalBottomSheet` for naming and cluster unassignment.
   - `EditWorkoutClusterIdentityDialog` (`WorkoutClusterHeatmapScreen.kt`): Modernized to `AppModalBottomSheet` for editing cluster name and primary sport type.
   - `ClusterInfoDialog.kt`: Modernized to `AppModalBottomSheet` for topological guidance.
   - `ExportDetailsDialog` (`ExportStatusGroup.kt`): Modernized to `AppModalBottomSheet` for export status details.

### C. Centered Dialog Invariants Preserved
* `LapSummaryDialog.kt`: 3-second auto-dismissing heads-up display overlaying the live map.
* `DeleteConfirmationDialog.kt`, `WorkoutDeleteDialog.kt`, `DeleteOldWorkoutsDialog.kt`: Centered destructive confirmation modals.
* `WorkoutDeletionProgressDialog.kt`: Deletion progress indicator.
* `GPSDisabledDialog.java`, `StartOrResumeDialog.java`: System and binary startup choices.

## 3. Verification & Evidence
* **Contract & Invariant Test**: `ModalBottomSheetDialogsIntegrityTest` passes green with 8 unit tests validating composable signatures, DialogFragment bridges, and invariant retention.
* **Component Test**: `DisplaySettingsTest` passes green.
* **Clean-Room Regression**: Full clean-room test suite (`./gradlew testDebugUnitTest --no-daemon`) passed with 100% success (0 failures, 0 regressions).
