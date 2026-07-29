# Walkthrough: Fix Navigation ActivityNotFoundException (ATT-409)

## Goal
The goal was to resolve a fatal `ActivityNotFoundException` when selecting "Tracking Layouts" from the navigation drawer. This also involved aligning the navigation flow with the project's architectural standards (**REQ-SET-051**).

## Changes Made

### UI & Navigation Layer
#### [MainActivityWithNavigation.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.java)
- Refactored the `drawer_tracking_layouts` case to trigger a modal `ActivityTypeSelectionDialog` directly.
- Implemented an asynchronous callback that replaces the current fragment with `TrackingTabsFragment` in configuration mode.
- Added `@JvmOverloads` to `ActivityTypeSelectionHelper.showSelectionDialog` in [ActivityTypeSelectionHelper.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/trackingtabs/ActivityTypeSelectionHelper.kt) to support simplified Java calls.

### Cleanup
- **Deleted** redundant `ConfigTrackingTabsActivity.kt` and `ConfigTrackingTabsFragment.kt`.
- **Removed** the `<activity>` registration from [AndroidManifest.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/AndroidManifest.xml).

## Verification Results

### Automated Tests
- **Build**: Successfully executed `:app:assembleDebug`.
- **Static Audit**: Confirmed the removal of explicit intents and middle-man Activities.

### Manual Verification
1.  **Drawer Tap**: Tapping "Tracking Layouts" now opens the "Select Activity Type" dialog immediately.
2.  **Selection**: Selecting a sport type correctly transitions the activity's main content area to the tracking cockpit in configuration mode.
3.  **Back Stack**: Pressing the system "Back" button correctly pops the configuration fragment and returns the user to their previous top-level view.

### Jira Tracking
- **RCA Sub-task**: [ATT-478](https://atrainingtracker.atlassian.net/browse/ATT-478)
- **Plan Sub-task**: [ATT-479](https://atrainingtracker.atlassian.net/browse/ATT-479)
- **Test Sub-task**: [ATT-480](https://atrainingtracker.atlassian.net/browse/ATT-480)
- **Verification Result**: PASS (TST-NAV-003) posted to [ATT-409](https://atrainingtracker.atlassian.net/browse/ATT-409).
