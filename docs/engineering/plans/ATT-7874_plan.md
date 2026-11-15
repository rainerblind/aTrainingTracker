# Stage 3: Implementation Plan - ATT-7874: Fix OOM from leaked DataStore scopes and offscreen Cockpit maps

**Ticket**: [ATT-7874](https://rainerblind.atlassian.net/browse/ATT-7874) | **Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No Crashes & App Stability (Crash-Free Resilience)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8`  
**Requirement Mapping**: `REQ-TRK-079` (`REQ-TRK-079.1`..`REQ-TRK-079.3`)  
**Test Mapping**: `TST-TRK-079` (`TST-TRK-079.1`..`TST-TRK-079.5`)  
**Risk Classification**: `COMPLEX` (Core Cockpit UI & DataStore Singleton Lifecycle)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-16  

---

## 1. Problem Description & Background
Multi-hour workout tracking caused fatal 512 MB Java heap `OutOfMemoryError` and native JNI `SIGABRT` crashes due to 19 unmanaged `TuningPreferencesDataStore(context)` constructor calls leaking Activity contexts and worker threads, unbounded `beyondViewportPageCount` in `HorizontalPager` keeping offscreen Google Maps active, and ungated 1-Hz map polyline emissions while the screen is off.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-079` (*Cockpit Map & DataStore Lifecycle Scoping and Memory Bounds*)
* **Test Mapping**: `TST-TRK-079` (*Cockpit Map & DataStore Lifecycle Scoping and Memory Bounds Verification Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Uninterrupted Background Recording**: `TrackerService` 1-Hz sensor polling, database inserts, and crash recovery remain 100% active and untouched.
2. **Immediate Persistence**: All tuning preferences persist reactively without data loss or schema changes.
3. **Smooth Tab Swiping**: Sensor grid state is retained while heavy GoogleMap composables are disposed when offscreen.
4. **Clean-Room Test Pass Rate**: 100% of targeted and regression unit tests pass cleanly.

---

## 4. Proposed Architectural Changes
* **`TuningPreferencesDataStore.kt`**: Make primary constructor `@VisibleForTesting internal`, force `context.applicationContext`, and launch startup key purge within `appScope.launch(Dispatchers.IO)`. Expose thread-safe `getInstance(context)`.
* **19 Production Call Sites**: Replace direct constructor calls with `TuningPreferencesDataStore.getInstance(context)`.
* **`TrackingTabsScreen.kt` & `SensorGridScreen.kt`**: Bound `HorizontalPager.beyondViewportPageCount = 0` (or 1) and pass `isTabActive` to `TrackingMapContainer` to dispose `GoogleMap` when `!isTabActive`.
* **`TrackingTabGridContent.kt` & `TrackingViewModel.kt`**: Collect state via `collectAsStateWithLifecycle(minActiveState = STARTED)` and gate `observeMapState()` map telemetry on subscriber lifecycle (`WhileSubscribed(5000)`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: `TuningPreferencesDataStore` Scoping & Normalization
* Files: `TuningPreferencesDataStore.kt`
* Scopes constructor to `@VisibleForTesting internal`, binds `appContext = context.applicationContext ?: context`, and runs init purge on `appScope`.

### Step 2: Migrate All 19 Production Call Sites
* Files: `MainActivityWithNavigation.kt`, `ZonesSettingsActivity.kt`, `ForkNavigationRepository.kt`, `TurnByTurnNavigationRepository.kt`, `TrackOnMapScreen.kt`, `WorkoutList.kt`, `WorkoutSummary.kt`, `AthleteProfileViewModel.kt`, `HrvRampTestViewModel.kt`, `ATrainingTrackerMap.kt`, `ElevationProfile.kt`, `MapDetailLayout.kt`, `ATrainingTrackerApp.kt`, `RoutesViewModel.kt`, `AutoPauseViewModel.kt`, `EditSensorFieldViewModel.kt`, `SensorGridScreen.kt`, `TrackingViewModel.kt`, `TrackingTabsScreen.kt`.
* Replaces all direct constructor calls with `TuningPreferencesDataStore.getInstance(context)`.

### Step 3: Bounded Viewport & Offscreen Map Disposal
* Files: `TrackingTabsScreen.kt`, `SensorGridScreen.kt`
* Sets `beyondViewportPageCount = 0` (or 1) in `HorizontalPager`; passes `isTabActive` to `TrackingMapContainer` to guard `ATrainingTrackerMap` composition.

### Step 4: Lifecycle-Gated Map Telemetry
* Files: `TrackingTabGridContent.kt`, `TrackingViewModel.kt`
* Uses `collectAsStateWithLifecycle(STARTED)` and converts `observeMapState()` to pause live emissions during `STOPPED`.

### Step 5: Unit & Regression Verification
* Files: `TuningPreferencesDataStoreLifecycleTest.kt`, `TrackingViewModelTest.kt`
* **Tier 1 Targeted Unit Test Command**:
  `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Run Tier 1 unit test command; verify `verify_requirement_governance.py` passes with 100% parity; test zero Activity context leaks via unit tests.
* **Rollback**: Clean git commit rollback on branch `feature/ATT-7874`.
