# Stage 2: Requirement & Test Specification - ATT-7874: Fix OOM from leaked DataStore scopes and offscreen maps

**Ticket**: [ATT-7874](https://rainerblind.atlassian.net/browse/ATT-7874) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No Crashes & App Stability (Crash-Free Resilience)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8` | **Branch**: `feature/ATT-7874`  
**Requirement Mapping**: `REQ-TRK-079` (`REQ-TRK-079.1`..`REQ-TRK-079.3`)  
**Test Spec ID**: `TST-TRK-079` (`TST-TRK-079.1`..`TST-TRK-079.5`)  
**Author**: AI Agent 2 (`subagent_flash`) | **Date**: 2026-11-16  

---

## 1. Requirement Specification (REQ-TRK-079)

### 1.1 Problem Statement & Rationale
During multi-hour tracking, 19 direct `TuningPreferencesDataStore(context)` constructor calls leaked Activity contexts and spawned duplicate unmanaged coroutine scopes. Simultaneously, `HorizontalPager.beyondViewportPageCount = trackingViews.size + 1` kept all Cockpit Google Maps active offscreen while `TrackingViewModel.observeMapState()` emitted 1-Hz polyline/map allocations even with the screen locked, exhausting the 512 MB Java heap (OOM / SIGABRT).

### 1.2 Attributes
- **Priority**: High | **Type**: Non-Functional | **Verification Method**: Unit

### 1.3 Functional Requirements
1. `TuningPreferencesDataStore` primary constructor SHALL be scoped to internal / `@VisibleForTesting`. Production code SHALL exclusively obtain instances via `TuningPreferencesDataStore.getInstance(context.applicationContext)` (`REQ-TRK-079.1`). Startup purge maintenance SHALL run within `appScope`. All 19 call sites SHALL be migrated.
2. `TrackingTabsScreen` `HorizontalPager` SHALL bound `beyondViewportPageCount = 0` (or 1) (`REQ-TRK-079.2`). `TrackingMapContainer` SHALL dispose underlying `GoogleMap` composables when `!isTabActive`.
3. `TrackingViewModel` 1-Hz map telemetry pipeline (`observeMapState`) and Compose collectors SHALL gate emissions on `Lifecycle.State.STARTED` (`REQ-TRK-079.3`). `TrackerService` 1-Hz background recording remains 100% uninterrupted.

### 1.4 Acceptance Criteria (Given-When-Then)
* *Given* navigation across Cockpit, Maps, and Settings, *When* `TuningPreferencesDataStore` is resolved, *Then* exactly one application-scoped instance exists and zero `Activity` contexts or orphaned coroutine scopes are retained.
* *Given* active workout tracking with map tabs, *When* an athlete switches to a non-map tab or locks the screen (`STOPPED`), *Then* offscreen `GoogleMap` composables are disposed and `TrackingViewModel.observeMapState()` pauses live polyline/marker emissions.
* *Given* screen locked during tracking, *When* `TrackerService` executes, *Then* 1-Hz background sample insertion into `WorkoutSamples.db` continues without data loss.

### 1.5 System Invariants
`TrackerService` 1-Hz background GPS tracking and SQLite persistence 100% unaffected; immediate persistence of tuning preferences preserved; zero leak of `Activity` references; 100% clean-room test pass rate.

---

## 2. Test Specification (TST-TRK-079)

### Test Case 1: `testTuningPreferencesDataStore_singletonAndApplicationContextNormalization` (`TST-TRK-079.1`)
* **Scope**: Unit Test (`TuningPreferencesDataStoreLifecycleTest.kt`)
* **Expected Result**: `getInstance(activity)` returns identical instance across repeated calls and normalizes to `applicationContext`.

### Test Case 2: `testHorizontalPager_boundedViewportAndInactiveTabMapDisposal` (`TST-TRK-079.2`)
* **Scope**: Unit Test (`TrackingTabsScreenLayoutTest.kt`)
* **Expected Result**: `beyondViewportPageCount` bounded (`<= 1`); `TrackingMapContainer` renders placeholder or defers `GoogleMap` when `!isTabActive`.

### Test Case 3: `testTrackingViewModel_observeMapState_gatedOnStartedLifecycle` (`TST-TRK-079.3`)
* **Scope**: Unit Test (`TrackingViewModelMapStateTest.kt`)
* **Expected Result**: Map telemetry collection pauses in `STOPPED` and resumes in `STARTED`.

### Test Case 4: `testTrackerService_backgroundPersistenceNeutrality` (`TST-TRK-079.4`)
* **Scope**: Regression / Integration Test (`TrackerServiceResumeTest.kt`)
* **Expected Result**: 1-Hz background database writes continue while UI telemetry is paused.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-TRK-079.5`)
* **Scope**: Unit Test Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"`

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-079.1` | Unit | `TuningPreferencesDataStore` | `REQ-TRK-079.1` | Specified |
| `TST-TRK-079.2` | Unit | `TrackingTabsScreen` | `REQ-TRK-079.2` | Specified |
| `TST-TRK-079.3` | Unit | `TrackingViewModel.observeMapState` | `REQ-TRK-079.3` | Specified |
| `TST-TRK-079.4` | Integration | `TrackerService` / persistence | `REQ-TRK-079.3` | Specified |
| `TST-TRK-079.5` | Tier 1 | Settings & Tracking UI subsystems | `REQ-PRO-001` | Specified |
