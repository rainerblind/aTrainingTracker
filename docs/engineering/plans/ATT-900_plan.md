# Implementation Plan - ATT-900: Modernize Dialogs to Bottom Popups (AppModalBottomSheet)

## 1. Problem Domain & Background
Following the standardization of edge-to-edge system insets and core design components in ATT-939, modern mobile ergonomics favor bottom-anchored modal sheets over floating, centered dialogs for interactive configuration, settings, and option selection flows. Centered modal dialogs on large mobile displays introduce thumb strain, awkward reachability, and inconsistent scrolling bounds.

However, as explicitly constrained in ATT-900 (*"But not for all. E.g., the Lap summary might be kept."*), not all dialogs should become bottom sheets. Transient active tracking HUD overlays (like `LapSummaryDialog`, which auto-dismisses after 3 seconds) and high-stakes destructive alerts (such as deletion confirmations) must remain centered dialogs to ensure immediate focal visibility and prevent accidental touches.

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

### B. Retained as Centered Dialogs (Invariants):
- `LapSummaryDialog.kt`: 3-second temporary heads-up display overlaying the active tracking map/HUD.
- `DeleteConfirmationDialog.kt`, `WorkoutDeleteDialog.kt`, `DeleteOldWorkoutsDialog.kt`: Explicit destructive confirmation modals.
- `GPSDisabledDialog.java`, `StartOrResumeDialog.java`: System and binary startup choices.

## 3. Core Design System Enhancements
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/AppModalBottomSheet.kt`:
- Add `iconPainter: Painter? = null` alongside `icon: ImageVector? = null` to support drawable resource icons.
- Add `iconTint: Color = MaterialTheme.colorScheme.primary` to allow un-tinted icon rendering (`Color.Unspecified`) for sport logos and multi-color assets.

## 4. Invariant Protection Strategy
- **SharedPreferences Keys**: `SP_UNITS`, `pref_display_options`, and export flags remain unmodified.
- **Orientation & Window Flags**: `MainActivityWithNavigation` orientation lock and `keepScreenOn` dynamic updates remain unchanged.
- **Sport Brand Colors**: Sport logos rendered with `Color.Unspecified` per `REQ-UI-114`.
- **Zero Regressions**: All existing 394 unit tests must continue to pass cleanly.

## 5. File Modification Plan
1. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/AppModalBottomSheet.kt`:
   - Extend signature with `iconPainter: Painter? = null` and `iconTint: Color = MaterialTheme.colorScheme.primary`.
2. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/export/ExportSettingsDialog.kt`:
   - Refactor to compose `AppModalBottomSheet`.
3. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/export/ExportSettingsDialogFragment.kt`:
   - Set transparent theme (`Theme_Translucent_NoTitleBar`).
4. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/units/UnitsSettingsDialog.kt`:
   - Refactor to compose `AppModalBottomSheet`.
5. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/units/UnitsSettingsDialogFragment.kt`:
   - Set transparent theme (`Theme_Translucent_NoTitleBar`).
6. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsDialog.kt`:
   - Refactor to compose `AppModalBottomSheet`.
7. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsDialogFragment.kt`:
   - Set transparent theme (`Theme_Translucent_NoTitleBar`).
8. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/trackingtabs/ActivityTypeSelectionDialog.kt`:
   - Refactor to compose `AppModalBottomSheet`.
9. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/trackingtabs/ActivityTypeSelectionDialogFragment.kt`:
   - Set transparent theme (`Theme_Translucent_NoTitleBar`).
10. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt`:
    - Refactor to compose `AppModalBottomSheet`.
11. `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/DeviceTypeSelectionDialog.kt`:
    - Refactor to compose `AppModalBottomSheet`.
12. `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/ModalBottomSheetDialogsIntegrityTest.kt` (NEW):
    - Unit tests validating bottom sheet composable signatures, parameters, and contract compliance.

## 6. Verification Plan
- Unit tests: Run `ModalBottomSheetDialogsIntegrityTest`, `DisplaySettingsTest`, and core UI tests.
- Full suite: Run `./gradlew testDebugUnitTest --no-daemon` to confirm zero regressions.
