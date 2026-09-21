# Implementation Plan - ATT-900: Modernize Dialogs to Bottom Popups (AppModalBottomSheet)

## 1. Problem Domain & Background
Following the standardization of edge-to-edge system insets and core design components in ATT-939, modern mobile ergonomics favor bottom-anchored modal sheets over floating, centered dialogs for interactive configuration, settings, and option selection flows. Centered modal dialogs on large mobile displays introduce thumb strain, awkward reachability, and inconsistent scrolling bounds.

However, as explicitly constrained in ATT-900 (*"But not for all. E.g., the Lap summary might be kept."*), not all dialogs should become bottom sheets. Transient active tracking HUD overlays (like `LapSummaryDialog`, which auto-dismisses after 3 seconds), high-stakes destructive alerts (such as deletion confirmations), and system lifecycle prompts must remain centered dialogs to ensure immediate focal visibility and prevent accidental touches.

Following user alignment, all interactive configuration, settings, editing, and selection dialogs across all modules are migrated comprehensively to `AppModalBottomSheet`.

## 2. Scope & Categorization
### A. Converted to AppModalBottomSheet:
1. **Drawer Settings Popups**:
   - `ExportSettingsDialog.kt`: Modernized to `AppModalBottomSheet` with `Upload` icon, toggles for TCX/GPX/GC/CSV, and "Done" action.
   - `UnitsSettingsDialog.kt`: Modernized to `AppModalBottomSheet` with `SquareFoot` icon, Metric/Imperial radio options, and "Done"/"Cancel" actions.
   - `DisplaySettingsDialog.kt`: Modernized to `AppModalBottomSheet` with `DisplaySettings` icon, toggles for orientation and screen wake locks, and "Done" action.
   - `ActivityTypeSelectionDialog.kt`: Modernized to `AppModalBottomSheet` with `ic_table_edit` icon, un-tinted authentic sport logos (`REQ-UI-114`), and scrollable activity type list.
2. **DialogFragment Bridge Hosts**:
   - `ExportSettingsDialogFragment.kt`, `UnitsSettingsDialogFragment.kt`, `DisplaySettingsDialogFragment.kt`, `ActivityTypeSelectionDialogFragment.kt`:
   - Configured with `setStyle(STYLE_NORMAL, android.R.style.Theme_Translucent_NoTitleBar)` to provide a transparent edge-to-edge window where `AppModalBottomSheet` renders natively.
3. **Tracking & Device Popups**:
   - `SensorSourceDialog.kt`: Modernized to `AppModalBottomSheet` with sensor icon, 3-tier telemetry status (Source, Backups, Not Connected), and device navigation.
   - `DeviceTypeSelectionDialog.kt`: Modernized to `AppModalBottomSheet` with protocol device list and "All Devices" bottom action button.
4. **Tracking Grid & Sensor Field Popups**:
   - `EditSensorFieldDialog.kt`: Modernized to `AppModalBottomSheet` with sensor field icon, sensor type, source device, and text size selection.
   - `ConfigureFilterDialog.kt`: Modernized to `AppModalBottomSheet` with filter icon, filter type, and smoothing factor controls.
5. **Sport Types & Equipment Management**:
   - `EditSportTypeDialog.kt`: Modernized to `AppModalBottomSheet` with sport icon, base sport selector, speed thresholds, and sensor requirements.
   - `EditEquipmentDialog.kt`: Modernized to `AppModalBottomSheet` with equipment icon, name, bike type selector, and linked sport types/sensors.
   - `EditDeviceDialog.kt`: Modernized to `AppModalBottomSheet` with hardware status LED, protocol, calibration, wheel circumference, and unpairing action.
6. **Workout Clusters & Spatial Details**:
   - `WorkoutClusterSelectionDialog` (`WorkoutClusterComponents.kt`): Modernized to `AppModalBottomSheet` for selecting cluster candidates.
   - `EditWorkoutClusterDialog` / `EditWorkoutClusterIdentityDialog` (`WorkoutClusterComponents.kt`, `WorkoutClusterHeatmapScreen.kt`): Modernized to `AppModalBottomSheet` for editing cluster name and primary sport type.
   - `ClusterInfoDialog.kt`: Modernized to `AppModalBottomSheet` for cluster tuning guidance.
   - `ExportDetailsDialog` (`ExportStatusGroup.kt`): Modernized to `AppModalBottomSheet` for export status breakdown.

### B. Retained as Centered Dialogs (Invariants):
- `LapSummaryDialog.kt`: 3-second temporary heads-up display overlaying the active tracking map/HUD.
- `DeleteConfirmationDialog.kt`, `WorkoutDeleteDialog.kt`, `DeleteOldWorkoutsDialog.kt`: Explicit destructive confirmation modals.
- `WorkoutDeletionProgressDialog.kt`: System deletion progress indicator.
- `GPSDisabledDialog.java`, `StartOrResumeDialog.java`: System and binary startup choices.

## 3. Core Design System Enhancements
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/AppModalBottomSheet.kt`:
- Add `iconPainter: Painter? = null` alongside `icon: ImageVector? = null` to support drawable resource icons.
- Add `iconTint: Color = MaterialTheme.colorScheme.primary` to allow un-tinted icon rendering (`Color.Unspecified`) for sport logos and multi-color assets.

## 4. Invariant Protection Strategy
- **SharedPreferences Keys**: `SP_UNITS`, `pref_display_options`, and export flags remain unmodified.
- **Orientation & Window Flags**: `MainActivityWithNavigation` orientation lock and `keepScreenOn` dynamic updates remain unchanged.
- **Sport Brand Colors**: Sport logos rendered with `Color.Unspecified` per `REQ-UI-114`.
- **Zero Regressions**: All existing unit tests must continue to pass cleanly.

## 5. File Modification Plan
1. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/AppModalBottomSheet.kt`:
   - Extend signature with `iconPainter: Painter? = null` and `iconTint: Color = MaterialTheme.colorScheme.primary`.
2. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/export/ExportSettingsDialog.kt` & `ExportSettingsDialogFragment.kt`:
   - Refactor to compose `AppModalBottomSheet`.
3. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/units/UnitsSettingsDialog.kt` & `UnitsSettingsDialogFragment.kt`:
   - Refactor to compose `AppModalBottomSheet`.
4. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsDialog.kt` & `DisplaySettingsDialogFragment.kt`:
   - Refactor to compose `AppModalBottomSheet`.
5. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/trackingtabs/ActivityTypeSelectionDialog.kt` & `ActivityTypeSelectionDialogFragment.kt`:
   - Refactor to compose `AppModalBottomSheet`.
6. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt`:
   - Refactor to compose `AppModalBottomSheet`.
7. `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/DeviceTypeSelectionDialog.kt`:
   - Refactor to compose `AppModalBottomSheet`.
8. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt`:
   - Refactor to compose `AppModalBottomSheet`.
9. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/ConfigureFilterDialog.kt`:
   - Refactor to compose `AppModalBottomSheet`.
10. `app/src/main/java/com/atrainingtracker/banalservice/ui/sporttype/EditSportTypeDialog.kt`:
    - Refactor to compose `AppModalBottomSheet`.
11. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EditEquipmentDialog.kt`:
    - Refactor to compose `AppModalBottomSheet`.
12. `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceDialog.kt`:
    - Refactor to compose `AppModalBottomSheet`.
13. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt`:
    - Refactor `WorkoutClusterSelectionDialog` and `EditWorkoutClusterDialog` to compose `AppModalBottomSheet`.
14. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt`:
    - Refactor `EditWorkoutClusterIdentityDialog` to compose `AppModalBottomSheet`.
15. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterInfoDialog.kt`:
    - Refactor to compose `AppModalBottomSheet`.
16. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/export/ExportStatusGroup.kt`:
    - Refactor `ExportDetailsDialog` to compose `AppModalBottomSheet`.
17. `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/ModalBottomSheetDialogsIntegrityTest.kt`:
    - Unit tests validating bottom sheet composable signatures, parameters, and contract compliance.

## 6. Verification Plan
- Unit tests: Run `ModalBottomSheetDialogsIntegrityTest`, `DisplaySettingsTest`, and all affected component unit tests.
- Full suite: Run `./gradlew testDebugUnitTest --no-daemon` to confirm zero regressions.
