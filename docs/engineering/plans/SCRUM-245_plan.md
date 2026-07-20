# Implementation Plan: Move TrackingTabs and Show Direct Dialog (ATT-245)

## 1. Requirement Traceability

| ID | Description | Component | Test ID |
|:---|:---|:---|:---|
| **REQ-SET-051** | Show direct Activity Type selection dialog from Navigation Drawer. | `MainActivityWithNavigation.java`, `ActivityTypeSelectionHelper.kt` | `TST-NAV-003` |
| **REQ-SET-050** | Move 'Tracking Tabs' to the Settings section. | `main_navigation_drawer.xml` | `TST-NAV-003` |

## 2. Proposed Changes

### `main_navigation_drawer.xml`
- Move `@+id/drawer_tracking_layouts` from the `drawer__training` group to the `drawer__settings` group.
- Position it as the first item in the Settings group.

### `ActivityTypeSelectionHelper.kt` (New)
- Create a reusable helper function `showActivityTypeSelectionDialog(context: Context, onTypeSelected: (ActivityType) -> Unit)`.
- Extract the dialog logic from `ConfigTrackingTabsFragment.kt` and `ConfigTrackingTabsActivity.kt`.
- Use the professional primary-color header style.

### `MainActivityWithNavigation.java`
- Implement `case R.id.drawer_tracking_layouts:` in `onNavigationItemSelected`.
- Call `ActivityTypeSelectionHelper.showActivityTypeSelectionDialog`.
- In the callback, perform a fragment transaction to `TrackingTabsFragment.newInstance(selectedType)`.

### `ConfigTrackingTabsFragment.kt` & `ConfigTrackingTabsActivity.kt`
- Refactor to use the new `ActivityTypeSelectionHelper`.

## 3. Impact Analysis
- **Navigation**: Reduces clicks for users wanting to customize their cockpit.
- **Code Quality**: Eliminates duplicated dialog logic.
- **Consistency**: Ensures the configuration dialog looks the same regardless of where it's triggered.

## 4. Verification Plan (TST-NAV-003)
1. Open the Navigation Drawer.
2. Verify 'Tracking Tabs' is now the first item under 'Settings'.
3. Click 'Tracking Tabs'.
4. **Expected Result**: A dialog titled "Choose Activity Type" appears immediately over the current screen.
5. Select "Running".
6. **Expected Result**: The screen changes to the Tracking cockpit in configuration mode for Running.
