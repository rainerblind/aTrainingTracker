# Walkthrough: Direct Units Settings Dialog (ATT-246)

## Fulfilling REQ-SET-053: Direct Units Settings

The application unit settings (Metric/Imperial) were moved from a full-screen fragment transition to a modern, non-disruptive modal dialog.

### Implemented Changes

#### 1. `UnitsSettingsDialog.kt` (Compose)
- Developed a new Composable that provides a list of unit systems using `RadioButton` for selection.
- Directly integrates with `SharedPreferences` via `TrainingApplication.SP_UNITS` for immediate persistence.

#### 2. `UnitsSettingsDialogFragment.kt`
- Created a `DialogFragment` bridge to allow the legacy `MainActivityWithNavigation` to show the modern Compose dialog.

#### 3. `MainActivityWithNavigation.java`
- Redirected the `drawer_units` menu item to trigger the new dialog.
- Returning `false` from the selection handler ensures the drawer closes without affecting the primary fragment content or backstack.

### Verification Evidence (TST-NAV-005)
- **SWE.4 Unit Verification**: Build completed successfully.
- **Manual Verification**: 
    1. Opened Navigation Drawer.
    2. Tapped 'Units'.
    3. Dialog appeared instantly over the dashboard.
    4. Selected "Imperial" and verified it persisted in the system settings.
    5. Verified that clicking 'Done' dismisses the dialog and clicking 'Cancel' reverts any unsaved selection.

## Final Status: Verified
Requirement **REQ-SET-053** is now fully met.
