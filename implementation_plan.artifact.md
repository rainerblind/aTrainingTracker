# Implementation Plan: Direct Tracking Tab Selection (ATT-409)

Refactor the navigation drawer to show the Activity Type selection dialog directly, fulfilling REQ-SET-051 and resolving the fatal `ActivityNotFoundException` when launching the configuration cockpit.

## User Review Required

> [!IMPORTANT]
> - **Navigation Change**: This change will remove the intermediate `ConfigTrackingTabsActivity` from the drawer flow. Instead, a modal dialog will appear over the current screen to select the sport type.
> - **In-App Configuration**: Selecting a sport will now replace the current fragment with the `TrackingTabsFragment` in configuration mode, keeping the user within `MainActivityWithNavigation` instead of jumping to a separate Activity.

## Proposed Changes

### 1. UI Layer: Navigation Drawer Refactor
#### [MODIFY] [MainActivityWithNavigation.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.java)
- **Requirement**: `REQ-SET-051` (Direct Tracking Tab Selection)
- **Test**: `TST-NAV-003` (Direct Config Navigation)
- **Changes**:
    - Update the `drawer_tracking_layouts` case in `onNavigationItemSelected` to call `ActivityTypeSelectionHelper.showSelectionDialog()`.
    - In the `onTypeSelected` callback, replace the current fragment with `TrackingTabsFragment.newInstance(activityType)`.

### 2. Cleanup: Remove Redundant Components
#### [DELETE] [ConfigTrackingTabsActivity.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/trackingtabs/ConfigTrackingTabsActivity.kt)
#### [DELETE] [ConfigTrackingTabsFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/trackingtabs/ConfigTrackingTabsFragment.kt)
#### [MODIFY] [AndroidManifest.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/AndroidManifest.xml)
- Remove the `<activity>` registration for `ConfigTrackingTabsActivity`.

## Verification Plan

### Manual Verification
1.  **Drawer Interaction**: Open the navigation drawer and tap "Tracking Layouts".
2.  **Dialog Visibility**: Verify that the "Select Activity Type" dialog appears immediately.
3.  **Selection Flow**: Select "Cycling".
4.  **Fragment Transition**: Verify that the screen switches to the tracking configuration cockpit for Cycling.
5.  **Navigation Consistency**: Verify that pressing "Back" returns to the previous top-level fragment correctly.
