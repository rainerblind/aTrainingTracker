# Implementation & Verification Plan: ATT-588

## 1. Goal & Context
Resolve **ATT-588**: Ensure Strava equipment (bikes and shoes) synchronizes reliably between Strava API v3 and `EquipmentDbHelper` (`Equipment.db`), ensuring gear appears in the Equipment management view and avoiding duplicate entries or silent permission failures.

## 2. Requirement & Test Mapping
* **Requirement**: `REQ-EXT-006` (*Strava Profile Scope & Equipment Synchronization Integrity*) in `docs/requirements.md`.
* **Test Case**: `TST-EXT-003` (*Strava Profile Scope & Equipment Synchronization Flow*) in `docs/tests.md`.

## 3. Impact Analysis & Architecture
* **OAuth Scope Integrity**: Adding `profile:read_all` to `StravaHelper.getAuthorizationUrl()` restores access to the protected athlete gear endpoint per Strava API v3 guidelines without breaking existing tokens.
* **Database & Data Hygiene**: Ensuring unlinked local equipment with matching names is linked to Strava IDs rather than duplicated preserves user workout history and statistics associated with existing equipment IDs.
* **UI Responsiveness**: Enabling `EquipmentFragment` to react to `SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED` guarantees real-time UI updates without requiring screen navigation or app restarts.

## 4. Proposed Changes

### Component 1: `StravaHelper.kt`
* Add `PROFILE_READ_ALL = "profile:read_all"` constant.
* In `getAuthorizationUrl()`, update requested scope to include `profile:read_all`:
  `"read,read_all,profile:read_all,activity:read_all,activity:write"`

### Component 2: `StravaEquipmentSynchronizeThread.java`
* In `getStravaEquipment()`:
  * Validate `accessToken != null` before making network requests; return localized "not connected" status on failure.
  * In `fillDbFromJsonObject()`:
    * Check if both `SHOES` and `BIKES` are missing; log a warning indicating missing `profile:read_all` permission.
    * Prior to raw `insert` on `updates < 1`, check if an unlinked equipment with matching name and sport type exists, and update its `STRAVA_ID` to prevent duplicate rows.

### Component 3: `EquipmentFragment.kt`
* In `onStart()` / `onStop()`:
  * Register a `BroadcastReceiver` listening for `SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED`.
  * Trigger `viewModel.loadEquipment()` upon broadcast reception to immediately refresh the list.

### Component 4: Verification & Automated Tests
* Create unit test suite `app/src/test/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaEquipmentSyncTest.kt`:
  * Verify `getAuthorizationUrl()` generates a valid URL containing `profile:read_all`.
  * Verify payload parsing for `shoes` and `bikes` with frame types and sport mapping.
  * Verify null access token handling.
* Run `./gradlew testDebugUnitTest`.
* Update `REQ-EXT-006` and `TST-EXT-003` to `Verified` after execution.
