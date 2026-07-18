# Implementation Plan: Direct Units Settings Dialog (ATT-246)

## 1. Requirement Traceability

| ID | Description | Component | Test ID |
|:---|:---|:---|:---|
| **REQ-SET-053** | Show direct Units Settings dialog from Navigation Drawer. | `MainActivityWithNavigation.java`, `UnitsSettingsDialogFragment.kt` | `TST-NAV-005` |

## 2. Proposed Changes

### `UnitsSettingsDialog.kt` (New)
- Create a Compose-based dialog using `AlertDialog`.
- List the available units (Metric/Imperial) using radio buttons.
- Use `MyUnits` enum for values and labels.
- Persist selection to `SharedPreferences` using key `TrainingApplication.SP_UNITS`.

### `UnitsSettingsDialogFragment.kt` (New)
- A `DialogFragment` wrapper to host `UnitsSettingsDialog`.

### `MainActivityWithNavigation.java`
- Update `case R.id.drawer_units:` to show `UnitsSettingsDialogFragment` instead of replacing the main fragment.
- Close the drawer before showing the dialog.
- Return `false` to maintain current context.

## 3. Impact Analysis
- **Navigation**: Faster access to unit settings.
- **Persistence**: Reuses existing `SharedPreferences` logic.

## 4. Verification Plan (TST-NAV-005)
1. Open the Navigation Drawer.
2. Click 'Units'.
3. **Expected Result**: A dialog with Metric and Imperial options appears.
4. Select a different unit and click 'Done'.
5. **Expected Result**: Settings are saved and dialog closes.
