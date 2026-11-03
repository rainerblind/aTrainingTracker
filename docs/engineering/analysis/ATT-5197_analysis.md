# Stage 1 Analysis: ATT-5197 - [Bug] Tracking cockpit map: never show or compute Lieblingsstrecken (clusters)

**Ticket**: [ATT-5197](https://rainerblind.atlassian.net/browse/ATT-5197)  
**Sub-task**: [ATT-5224](https://rainerblind.atlassian.net/browse/ATT-5224) (`[Analysis]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `Sprint 2026-45.2`  
**Branch**: `feature/ATT-5197`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Statement & Motivation
During live tracking, the cockpit embedded map (`SensorGridScreen.kt` / `TrackingViewModel.kt`) renders favorite route clusters (*Lieblingsstrecken* / `GeneralMapLayer.CLUSTERS`) whenever the general map's cluster layer is enabled. Live tracking requires high focus on real-time activity: active track, selected navigation routes, and live segments. Historical route clusters cause visual crowding and incur unnecessary repository collection and decoding overhead during tracking. As codified in `docs/coding_standards.md` Section 5.8 (Rule 68), Lieblingsstrecken must never be computed or displayed in the tracking cockpit.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: In ATT-4847, `CLUSTERS` was added as an overlay layer across both the general map and cockpit map. In `TrackingViewModel.kt`, `observeMapState()` checks `isClusterLayerEnabled()`, subscribes to `workoutClusterRepository.allClusters`, and passes decoded `mapClusters` into `TrackingMapState`. In `SensorGridScreen.kt` (line 1327), `TrackingMapContainer` renders `clusters(mapState.mapClusters)`.
* **Independent Peer Review & Verification**:
  * Confirmed `MapFragmentWithTrackViewModel` / `MapScreenWithTrack.kt` (general map) manages layer toggles and bottom sheet inspection (`ClusterOnMapSheet`).
  * Confirmed `TrackingViewModel` has no layer toggle controls and no interactive cluster click handlers. Excising `workoutClusterRepository` and `mapClusters` completely isolates the cockpit without affecting general map functionality.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Excise `clusters(mapState.mapClusters)` and `mapState.mapClusters` key from `TrackingMapContainer` in `SensorGridScreen.kt`.
  * Excise `workoutClusterRepository`, `isClusterLayerEnabled()`, and `mapClustersFlow` from `TrackingViewModel.kt`.
  * Remove `mapClusters` property from `TrackingMapState.kt`, `TrackingScreenState`, and `StaticMapLayerData`.
  * Update `TrackingMapIsolationContractTest` and `TrackingViewModelMapStateTest` to assert cluster excision.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying `GeneralMapLayer.CLUSTERS` behavior or rendering on `MapFragmentWithTrackViewModel` / `MapScreenWithTrack.kt`.
  * Altering `WorkoutClusterRepository` SQLite storage, clustering algorithms, or lightweight loading logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*), Section 5.
* **Historical Origin & Commit Trace**: Commit `4d3f24f2` (Ticket `ATT-4847`, Sprint 2026-44.2), amended in Commit `c020fa5c` (Ticket `ATT-5074`, Sprint 2026-45.1).
* **Root Reason for Existing Formulation**: ATT-4847 originally unified all map overlays across both screens. However, Sprint 2026-45.1 review of ATT-5074 identified that historical discovery overlays clutter live tracking tabs.
* **Preservation of Core Invariants**: Section 5.8 / Rule 68 explicitly restricts `CLUSTERS` to the general map. Decoupling cockpit map preserves 1-Hz telemetry performance and prevents memory churn.

---

## 5. Architectural Strategy & High-Level Solution
* **`SensorGridScreen.kt`**: Remove `clusters(mapState.mapClusters)` call and `mapState.mapClusters` from `remember` dependencies in `TrackingMapContainer`.
* **`TrackingViewModel.kt`**: Remove `workoutClusterRepository` parameter from constructor, delete `isClusterLayerEnabled()` and `mapClustersFlow`, and simplify `staticMapLayerFlow` to combine only segments and routes.
* **`TrackingMapState.kt`**: Remove `val mapClusters: List<MapCluster>` and unused import `MapCluster`.
* **Test Suite**: Adapt `TrackingMapIsolationContractTest` and `TrackingViewModelMapStateTest` to assert cockpit cluster excision.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Full `CLUSTERS` layer functionality preserved on general map (`MapScreenWithTrack`).
  2. Zero recomposition churn on 1-Hz telemetry emissions (`REQ-MAP-051`, `REQ-UI-326`).
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**. Purely subtractive/decoupling refactoring with zero database or concurrency impact.
