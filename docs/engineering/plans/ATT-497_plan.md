# Implementation & Verification Plan: ATT-497

## 1. Goal & Context
Implement **ATT-497**: Ensure athlete routes created in Strava are automatically imported into `RoutesDatabaseManager` (`Routes.db`) when establishing a Strava connection, and can be updated on-demand from the Strava settings screen, fully integrated with the spatial route family engine (`WorkoutClusterEngine`).

## 2. Requirement & Test Traceability
* **Requirement**: `REQ-EXT-007` (*Automated & On-Demand Strava Route Synchronization*) in `docs/requirements.md`.
* **Test Case**: `TST-EXT-004` (*Strava Route Synchronization & Connection Trigger Flow*) in `docs/tests.md` (Jira Sub-task `ATT-607`).

## 3. Impact Analysis (SWE.1.BP.5)
* **Mapped Requirements Invariant Check**:
  * `REQ-EXT-002` (Official Strava Assets): Preserved without modification.
  * `REQ-EXT-004` (Integrated Strava Authorization): Preserved; Custom Tabs flow triggers route sync upon success.
  * `REQ-UI-105` (Edge-to-Edge Layout): Insets and layout handling in `StravaUploadFragment` remain untouched.
  * `REQ-SET-031` (Route-to-Cluster Synchronization): Each imported Strava route is passed to `WorkoutClusterEngine.learnFromRoute()`.
* **Component Interfaces & Threading**:
  * Route network and database operations execute strictly off the main thread on `Dispatchers.IO`.
  * `RoutesRepository.allRoutes` StateFlow is automatically refreshed, ensuring immediate UI updates in `RoutesFragment`.
* **Database & Data Hygiene**:
  * `RouteSource.STRAVA` already exists in `RoutesDatabaseManager.kt`. No schema migration required.
  * Duplicate avoidance via `existingExtIds` ensures existing routes and user edits are preserved.

## 4. Proposed Changes

### Component 1: `TrainingApplication.java`
* Add preference key constants:
  * `SP_LAST_UPDATE_TIME_OF_STRAVA_ROUTES = "lastUpdateTimeOfStravaRoutes"`
  * `UPDATE_STRAVA_ROUTES = "updateStravaRoutes"`
* Add getter `getLastUpdateTimeOfStravaRoutes()` defaulting to `@string/lastUpdateOfRoutesNever`.
* Add setter `setLastUpdateTimeOfStravaRoutes(String)`.

### Component 2: `RoutesRepository.kt`
* Add `syncRoutesFromStravaAsync()` to allow non-blocking invocation from Fragment lifecycle / click listeners.
* Enhance `syncRoutesFromStrava()`:
  * Self-healing Athlete ID recovery: if `athleteId == 0`, fetch authenticated athlete profile to retrieve ID before querying `/athletes/{id}/routes`.
  * Persist formatted timestamp upon successful sync via `TrainingApplication.setLastUpdateTimeOfStravaRoutes()`.
  * Return `Boolean` to indicate sync success/failure.

### Component 3: `prefs_strava.xml` & `StravaUploadFragment.kt`
* In `res/xml/prefs_strava.xml`:
  * Add `<Preference android:key="updateStravaRoutes" android:title="@string/updateStravaRoutes" android:summary="@string/lastUpdateOfRoutesNever" android:layout="@layout/preference_slim"/>`.
* In `StravaUploadFragment.kt`:
  * In `LaunchedEffect(authState)`: on `StravaAuthState.Success`, call `RoutesRepository.getInstance(requireContext()).syncRoutesFromStravaAsync()`.
  * In `onResume()`: bind `mUpdateStravaRoutes` click listener to trigger `syncRoutesFromStravaAsync()`.
  * In `updateSelectiveUploadVisibility()`: toggle visibility of `updateStravaRoutes` based on `isConnected`.
  * In `onSharedPreferenceChanged()`: update `mUpdateStravaRoutes?.summary` when `SP_LAST_UPDATE_TIME_OF_STRAVA_ROUTES` changes.

### Component 4: Localization (9 Languages)
* Add strings `updateStravaRoutes` and `lastUpdateOfRoutesNever` to all 9 resource files:
  * `values/strings.xml` (EN)
  * `values-de/strings.xml` (DE)
  * `values-es/strings.xml` (ES)
  * `values-fr/strings.xml` (FR)
  * `values-it/strings.xml` (IT)
  * `values-ja/strings.xml` (JA)
  * `values-nl/strings.xml` (NL)
  * `values-pl/strings.xml` (PL)
  * `values-pt/strings.xml` (PT)

### Component 5: Verification & Automated Tests
* Create unit test suite `StravaRouteSyncTest.kt`:
  * Validate JSON parsing for Strava route objects and polyline/stream point extraction.
  * Validate null token and missing athlete ID handling.
  * Validate timestamp storage and preference key integrity.
* Run full test suite `./gradlew testDebugUnitTest`.
