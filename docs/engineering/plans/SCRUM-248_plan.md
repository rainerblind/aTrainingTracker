# Implementation Plan: Direct Export Settings Dialog (ATT-248)

## 1. Requirement Traceability

| ID | Description | Component | Test ID |
|:---|:---|:---|:---|
| **REQ-SET-054** | Show direct Export Settings dialog from Navigation Drawer. | `MainActivityWithNavigation.java`, `ExportSettingsDialogFragment.kt` | `TST-NAV-006` |

## 2. Proposed Changes

### `ExportSettingsDialog.kt` (New)
- Create a Compose-based dialog using `AlertDialog`.
- List the available export formats (TCX, GPX, Golden Cheetah JSON, CSV).
- Use `Switch` or `Checkbox` for toggles.
- Use the same styling as `UnitsSettingsDialog` (compact width).
- Directly persist values to `SharedPreferences`.

### `ExportSettingsDialogFragment.kt` (New)
- A `DialogFragment` wrapper to host `ExportSettingsDialog`.

### `MainActivityWithNavigation.java`
- Update `case R.id.drawer_export:` to show `ExportSettingsDialogFragment` instead of replacing the main fragment.
- Close the drawer before showing the dialog.
- Return `false` to maintain current context.

## 3. Impact Analysis
- **Navigation**: Reduces disruption during navigation.
- **Consistency**: Matches the behavior of Units and Display settings.

## 4. Verification Plan (TST-NAV-006)
1. Open the Navigation Drawer.
2. Click 'Export'.
3. **Expected Result**: A dialog with export format options appears.
4. Toggle an option and click 'Done'.
5. **Expected Result**: Settings are saved and dialog closes.
