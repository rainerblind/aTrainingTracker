# Implementation Plan: Direct Display Settings Dialog (ATT-247)

## 1. Requirement Traceability

| ID | Description | Component | Test ID |
|:---|:---|:---|:---|
| **REQ-SET-052** | Show direct Display Settings dialog from Navigation Drawer. | `MainActivityWithNavigation.java`, `DisplaySettingsDialogFragment.kt` | `TST-NAV-004` |

## 2. Proposed Changes

### `DisplaySettingsDialogFragment.kt` (New)
- Create a new `DialogFragment` that acts as a container for the `DisplaySettingsFragment`.
- Use a full-screen or large dialog style to accommodate the preference list.
- In `onCreateView`, provide a simple container (e.g., a `FrameLayout`).
- In `onViewCreated`, use `childFragmentManager` to host `DisplaySettingsFragment`.

### `MainActivityWithNavigation.java`
- Update `case R.id.drawer_display_settings:` to show the `DisplaySettingsDialogFragment` instead of replacing the main fragment.
- Close the drawer before showing the dialog.
- Return `false` from `onNavigationItemSelected` for this item to preserve the background state and avoid redundant backstack entries.

## 3. Impact Analysis
- **Navigation**: Allows users to quickly toggle display options (like "Keep Screen On") without losing their current place in the app.
- **UI Consistency**: Maintains the use of existing `PreferenceFragmentCompat` but presents it in a more convenient modal way.

## 4. Verification Plan (TST-NAV-004)
1. Open the Navigation Drawer.
2. Click 'Display'.
3. **Expected Result**: A dialog containing the display settings appears.
4. Change a setting (e.g., toggle an option).
5. Dismiss the dialog.
6. **Expected Result**: The app returns to the previous screen, and settings are applied.
