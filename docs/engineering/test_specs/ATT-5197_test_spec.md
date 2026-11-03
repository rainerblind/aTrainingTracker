# Stage 2: Requirement & Test Specification - ATT-5197: [Bug] Tracking cockpit map: never show or compute Lieblingsstrecken (clusters)

**Ticket**: [ATT-5197](https://rainerblind.atlassian.net/browse/ATT-5197)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `Sprint 2026-45.2`  
**Requirement Mapping**: `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*)  
**Test Spec ID**: `TST-UI-282`  
**Branch**: `feature/ATT-5197`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-UI-322)
### 1.1 Problem Statement & Rationale
Historical discovery layers (*Lieblingsstrecken* / `GeneralMapLayer.CLUSTERS`) were previously composed across both the general inspection map and live tracking cockpit map (`SensorGridScreen.kt`). Live tracking requires strict focus on real-time activity (active track, selected routes, live segments) without visual clutter or background repository collection and decoding overhead. As codified in Section 5.8 (Rule 68), cluster overlays must never be computed or displayed in the tracking cockpit.

### 1.2 Functional & Architectural Requirements
* `REQ-UI-322.6` (Cockpit Map Excision): `SensorGridScreen.kt` (`TrackingMapContainer`) SHALL NOT compose `clusters()` or pass cluster data to `ATrainingTrackerMap`.
* `REQ-UI-322.7` (TrackingViewModel Decoupling): `TrackingViewModel` SHALL NOT inject `WorkoutClusterRepository`, SHALL NOT check `isClusterLayerEnabled()`, and SHALL NOT collect or emit `mapClustersFlow`.
* `REQ-UI-322.8` (Model Simplification): `TrackingMapState` and `TrackingScreenState` SHALL eliminate `mapClusters` dependencies.
* `REQ-UI-322.9` (General Map Preservation): `MapFragmentWithTrackViewModel` and `MapScreenWithTrack.kt` SHALL retain full `CLUSTERS` layer toggle, polyline decoding, and bottom-sheet inspection.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Cockpit Rendering)**:
  * *Given* the athlete tracking an activity on `SensorGridScreen.kt`,
  * *When* viewing the embedded cockpit map (regardless of general map layer toggles),
  * *Then* favorite route cluster polylines SHALL NEVER be rendered on `ATrainingTrackerMap`.
* **Criterion 2 (ViewModel Decoupling)**:
  * *Given* `TrackingViewModel` instantiating and observing map state,
  * *When* `workoutClusterRepository.allClusters` emits,
  * *Then* `TrackingViewModel` does not collect the emission or trigger recomposition.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*), Section 5.
* `Historical Origin & Commit Trace`: Ticket `ATT-1449` (Sprint 2026-40.4), Ticket `ATT-4847` (Sprint 2026-44.2), Ticket `ATT-5074` (Sprint 2026-45.1), and Ticket `ATT-5197` (Sprint 2026-45.2).
* `Root Reason for Existing Formulation`: In ATT-4847, `CLUSTERS` was added as a shared overlay layer across both maps. However, live tracking requires focus on real-time activity; historical discovery overlays cause visual clutter and unnecessary background collection during tracking (Rule 68 / Section 5.8).
* `Preservation of Core Invariants`: General map `CLUSTERS` layer visibility toggle, polyline rendering, and `ClusterOnMapSheet` inspection remain 100% intact. Live GPS tracking and 1-Hz telemetry performance remain strictly preserved.

### 1.5 System Invariants
* General map Lieblingsstrecken display, toggle menu, and `ClusterOnMapSheet` remain 100% operational.
* Zero 1-Hz telemetry allocations or recomposition churn on `TrackingMapState` (`REQ-MAP-051`, `REQ-UI-326`).
* Parent ticket Human Decision Gate remains strictly enforced (`Rule 3`).

---
## 2. Test Specification (TST-UI-282)
### Test Case 1: Cockpit Map Cluster Excision Contract (`TST-UI-282.11`)
* **Scope**: Unit & Compose Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingMapIsolationContractTest.kt`
* **Preconditions**: `SensorGridScreen.kt` and `TrackingMapState.kt` source files loaded.
* **Action**: Assert `TrackingMapContainer` does not call `clusters()`, and `TrackingMapState` does not contain `mapClusters`.
* **Expected Result**: Static AST / reflection contract asserts 0 cluster bindings in tracking map.

### Test Case 2: TrackingViewModel Map State Isolation (`TST-UI-282.12`)
* **Scope**: ViewModel Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModelMapStateTest.kt`
* **Preconditions**: `TrackingViewModel` initialized with mocked dependencies.
* **Action**: Advance scheduler and observe `TrackingViewModel.mapState`.
* **Expected Result**: `TrackingViewModel` does not query `WorkoutClusterRepository` or `PREF_GENERAL_MAP_ENABLED_LAYERS` for clusters.

### Test Case 3: 9-Language Localization Audit (`TST-UI-282.2`)
* **Target**: `GeneralMapLayersLocalizationTest.kt`
* **Expected Result**: 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Test Case 4: Targeted Unit Test Suite (`TST-UI-282.3`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.*"`
* **Expected Result**: 100% pass rate with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Test Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-282.11` | Contract | `TrackingMapIsolationContractTest` | `REQ-UI-322.6`, `REQ-UI-322.8` | Specified |
| `TST-UI-282.12` | Unit | `TrackingViewModelMapStateTest` | `REQ-UI-322.7` | Specified |
| `TST-UI-282.2` | Localization | `GeneralMapLayersLocalizationTest` | `REQ-UI-106`, `REQ-UI-322` | Specified |
| `TST-UI-282.3` | Regression | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.*"` | `REQ-PRO-001` | Specified |
