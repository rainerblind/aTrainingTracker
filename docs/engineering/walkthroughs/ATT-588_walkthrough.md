# Walkthrough - ATT-588: Strava Equipment is not synhronized

## Problem & Background
When synchronizing Strava equipment via `StravaEquipmentSynchronizeThread` (triggered automatically upon completing the Strava connection in `StravaUploadFragment` or manually via the "Update Strava Equipment" setting), no bikes or shoes were added to `EquipmentDbHelper` (`Equipment.db`).
The root cause was identified in `StravaHelper.kt`: during the SCRUM-153 modernization, the `profile:read_all` OAuth scope was accidentally dropped from `getAuthorizationUrl()`. According to the Strava API v3 specification, `GET /api/v3/athlete` strictly requires `profile:read_all` to return `bikes` and `shoes`. Without it, Strava strips the gear arrays, resulting in an empty response payload and silent synchronization failure.

## Changes Implemented

### 1. OAuth Scope Restoration
- In [StravaHelper.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaHelper.kt):
  - Defined `const val PROFILE_READ_ALL = "profile:read_all"`.
  - Added `profile:read_all` to the requested OAuth scope in `getAuthorizationUrl()`:
    `"read,read_all,profile:read_all,activity:read_all,activity:write"`
  - Added JVM test fallback for `getAuthorizationUrl()`.

### 2. Synchronization Thread Defensive Handling
- In [StravaEquipmentSynchronizeThread.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaEquipmentSynchronizeThread.java):
  - Added null/empty access token guard in `getStravaEquipment()`, returning `"Not connected to Strava"` instead of constructing an invalid `Authorization: Bearer null` header.
  - In `fillDbFromJsonObject()`, added check for payload missing both `shoes` and `bikes` arrays, returning `"No gear returned (missing profile:read_all permission)"`.
  - Implemented duplicate avoidance: before inserting new gear on `updates < 1`, check if an unlinked local item with matching name exists and update its `STRAVA_ID` and `STRAVA_NAME` to link it.
  - Made UI dialog and handler initialization safe for decoupled background/JVM execution.

### 3. Equipment UI Auto-Refresh
- In [EquipmentFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentFragment.kt):
  - Registered a `BroadcastReceiver` in `onStart()` / `onStop()` listening for `SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED`.
  - Triggers `viewModel.loadEquipment()` automatically upon broadcast reception so the UI refreshes when background sync finishes.

### 4. Verification & Testing
- Created unit test suite [StravaEquipmentSyncTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaEquipmentSyncTest.kt):
  - Validated that `getAuthorizationUrl()` requests `profile:read_all` along with all standard scopes.
  - Validated `PROFILE_READ_ALL` constant matching API specification.
  - Validated missing gear detection when `shoes` and `bikes` are stripped.
- Ran `./gradlew testDebugUnitTest`: All 32 tasks passed (`BUILD SUCCESSFUL in 7s`).
- Marked `REQ-EXT-006` in [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md) as **Verified**.
- Marked `TST-EXT-003` in [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md) as **Verified**.
