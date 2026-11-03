# Stage 3: Implementation Plan - ATT-5197: [Bug] Tracking cockpit map: never show or compute Lieblingsstrecken (clusters)

**Ticket**: [ATT-5197](https://rainerblind.atlassian.net/browse/ATT-5197)  
**Sub-task**: [ATT-5226](https://rainerblind.atlassian.net/browse/ATT-5226) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `Sprint 2026-45.2`  
**Requirement Mapping**: `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*)  
**Test Mapping**: `TST-UI-282`  
**Branch**: `feature/ATT-5197`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Description & Background
Historical route cluster overlays (*Lieblingsstrecken*) must never be displayed or computed on the live tracking cockpit map (`SensorGridScreen.kt` / `TrackingViewModel.kt`). Live tracking requires focus on active activity telemetry (track, navigation routes, live segments) without visual clutter or background repository collection and decoding overhead (Section 5.8 / Rule 68).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-322.6` (Cockpit Map Excision), `REQ-UI-322.7` (TrackingViewModel Decoupling), `REQ-UI-322.8` (Model Simplification), `REQ-UI-322.9` (General Map Preservation)
* **Test Mapping**: `TST-UI-282.11` (Cockpit Map Cluster Excision Contract), `TST-UI-282.12` (TrackingViewModel Map State Isolation), `TST-UI-282.2` (9-Language Parity), `TST-UI-282.3` (Targeted Unit Test Suite)

---

## 3. System Invariants & Preserved Behavior
1. **General Map Preservation**: `MapFragmentWithTrackViewModel` / `MapScreenWithTrack.kt` retain full `CLUSTERS` layer toggle, polyline rendering, and `ClusterOnMapSheet` inspection.
2. **Telemetry Performance**: Zero recomposition churn or object allocations on 1-Hz sensor/location updates (`REQ-MAP-051`, `REQ-UI-326`).
3. **Subtask & Gate Governance**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `gate-verdict`; parent ticket terminal completion remains exclusively reserved for the human user (`Rule 3`).

---

## 4. Proposed Architectural Changes
* **`SensorGridScreen.kt`**: In `TrackingMapContainer`, remove `mapState.mapClusters` from `remember(...)` dependencies and excise `clusters(mapState.mapClusters)` call.
* **`TrackingViewModel.kt`**: Remove `workoutClusterRepository` from constructor, delete `isClusterLayerEnabled()` and `mapClustersFlow`, simplify `staticMapLayerFlow` combine to 3 inputs, and remove `mapClusters` from `TrackingScreenState`.
* **`TrackingMapState.kt`**: Remove `val mapClusters: List<MapCluster>` and unused import.
* **UI Consistency**: No UI additions; purely subtractive decluttering of the cockpit map.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Model & Container Excision
* Files: `TrackingMapState.kt`, `SensorGridScreen.kt`
* Changes: Remove `mapClusters` property from `TrackingMapState`; remove `mapState.mapClusters` and `clusters(...)` invocation from `TrackingMapContainer`.

### Step 2: ViewModel Decoupling
* Files: `TrackingViewModel.kt`
* Changes: Remove `workoutClusterRepository` parameter and default instantiation; delete `isClusterLayerEnabled()` and `mapClustersFlow`; simplify `StaticMapLayerData` and `staticMapLayerFlow`; remove `mapClusters` from `TrackingScreenState`.

### Step 3: Contract & Unit Test Alignment
* Files: `TrackingMapIsolationContractTest.kt`, `TrackingViewModelMapStateTest.kt`
* Changes: Update contract assertions verifying `TrackingMapState` and `SensorGridScreen` do not reference clusters; update `TrackingViewModelMapStateTest` to assert `TrackingViewModel` does not query clusters even when `CLUSTERS` layer is enabled.

### Step 4: Verification Suite
* Commands:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.*"`
  `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit and contract tests in Stage 4; Tier 1 package test suite in Stage 5.
* **Rollback**: Branch isolation allows full revert via `git reset --hard HEAD` without affecting `sprint/2026-45.2`.
