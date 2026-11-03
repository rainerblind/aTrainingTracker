# Stage 2: Requirement & Test Specification - ATT-5074: Disable Lieblingsstrecken by Default & Optimize Loading Latency

**Ticket**: [ATT-5074](https://rainerblind.atlassian.net/browse/ATT-5074)  
**Sub-task**: [[ATT-5160]](https://rainerblind.atlassian.net/browse/ATT-5160) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*)  
**Test Spec ID**: `TST-UI-282`  
**Branch**: `feature/ATT-5074`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Requirement Specification (REQ-UI-322)

### 1.1 Problem Statement & Rationale
In ATT-4847, `GeneralMapLayer.CLUSTERS` was introduced as the 5th map layer. Because default enabled layers fell back to `GeneralMapLayer.entries.toSet()`, favorite route clusters (*Lieblingsstrecken*) were enabled by default on both the General Map and live Cockpit Tracking Map, causing visual clutter. Furthermore, loading clusters suffered from severe latency due to coupling with heavy `clusterStats` aggregation queries, lack of layer gating in ViewModels, Main-thread polyline decoding, and absence of fast asynchronous initialization in `WorkoutClusterRepository`.

### 1.2 Functional & Architectural Requirements
1. `GeneralMapLayer` SHALL define `DEFAULT_ENABLED = setOf(ROUTES, SEGMENTS, KNOWN_LOCATIONS, TRACK)`, strictly excluding `CLUSTERS`.
2. `MapFragmentWithTrackViewModel.loadEnabledLayers()` and `TrackingViewModel` SHALL default to `DEFAULT_ENABLED` when `SharedPreferences` contains no preference for `PREF_GENERAL_MAP_ENABLED_LAYERS`.
3. `MapFragmentWithTrackViewModel.mapClustersFlow` and `TrackingViewModel.mapClusters` SHALL be conditionally gated on `GeneralMapLayer.CLUSTERS in enabledLayers`, emitting `emptyList()` immediately when disabled.
4. `WorkoutClusterRepository` SHALL provide a fast asynchronous loading pathway from the SQLite `RouteClusters` table on `Dispatchers.IO`, decoupling cluster polyline retrieval from heavy `clusterStats` aggregation.
5. Representative cluster polyline decoding (`PolyUtil.decode`) SHALL execute off the Main/UI thread (on `Dispatchers.Default`) with coordinate memoization per `REQ-MAP-051.2`.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*), Sections 1, 2, and 5.
* **Historical Origin & Commit Trace**: Tickets `ATT-2931` (commit `cc879818`), `ATT-4847` (commit `4d3f24f2`), and `ATT-5075` (commit `95cb0048`).
* **Root Reason for Existing Formulation**: ATT-2931 set all 4 original primary layers as default (`entries.toSet()`). ATT-4847 added `CLUSTERS` without defining a separate default set, inadvertently enabling it on all maps. Decoding was un-gated and coupled to heavy `clusterStats` aggregation.
* **Preservation of Core Invariants**: Primary layers (`ROUTES`, `SEGMENTS`, `KNOWN_LOCATIONS`, `TRACK`) remain default-enabled; existing custom user preferences in `SharedPreferences` are preserved; stroke colors (`TTColor.Cluster`) and z-indexes (`12f`) remain intact.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Default Off)**:
  * *Given* a fresh install or unconfigured map layer preferences,
  * *When* viewing `MapScreenWithTrack` or the Cockpit live tracking map,
  * *Then* `GeneralMapLayer.CLUSTERS` is disabled by default, and cluster polylines are hidden.
* **Criterion 2 (Fast Asynchronous Rendering)**:
  * *Given* `GeneralMapLayer.CLUSTERS` is toggled on,
  * *When* clusters are loaded,
  * *Then* clusters load asynchronously from SQLite without blocking on heavy `clusterStats` aggregation, and polylines decode on `Dispatchers.Default` without UI stutter.

---

## 2. Test Specification (TST-UI-282)

### Test Case 1: `GeneralMapLayersContractTest.testDefaultEnabledLayers_excludesClusters` (TST-UI-282.1)
* **Scope**: Contract Test (`GeneralMapLayersContractTest.kt`)
* **Expected Result**: Verifies `GeneralMapLayer.DEFAULT_ENABLED` contains 4 entries excluding `CLUSTERS`, and `MapFragmentWithTrackViewModel.enabledLayers.value` excludes `CLUSTERS` when prefs are null.

### Test Case 2: `GeneralMapLayersContractTest.testToggleClusterLayer` (TST-UI-282.2)
* **Scope**: Contract Test (`GeneralMapLayersContractTest.kt`)
* **Expected Result**: Verifies toggling `CLUSTERS` enables the layer, updates `enabledLayers.value`, and persists the updated set to `SharedPreferences`.

### Test Case 3: `MapFragmentWithTrackViewModelTest.testMapClustersFlow_gatedByClusterLayer` (TST-UI-282.9)
* **Scope**: ViewModel Unit Test (`MapFragmentWithTrackViewModelTest.kt`)
* **Expected Result**: Verifies `mapClustersFlow` emits `emptyList()` when `CLUSTERS` is disabled, and emits decoded `MapCluster` items only when `CLUSTERS` is enabled.

### Test Case 4: `TrackingViewModelTest.testTrackingMapClusters_gatedByLayer` (TST-UI-282.10)
* **Scope**: ViewModel Unit Test (`TrackingViewModelTest.kt`)
* **Expected Result**: Verifies `TrackingMapState.mapClusters` is empty when `CLUSTERS` is disabled by default.

### Test Case 5: `WorkoutClusterRepositoryTest.testFastLightweightLoading` (TST-UI-282.11)
* **Scope**: Repository Unit Test (`WorkoutClusterRepositoryTest.kt`)
* **Expected Result**: Verifies fast asynchronous loading populates `_allClusters` directly from SQLite on `Dispatchers.IO` without requiring `clusterStats` or member workout hydration.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-282.1` | Contract | `GeneralMapLayer.DEFAULT_ENABLED` | `REQ-UI-322.1`, `REQ-UI-322.2` | Specified |
| `TST-UI-282.2` | Contract | `MapFragmentWithTrackViewModel.toggleLayer` | `REQ-UI-322.2` | Specified |
| `TST-UI-282.9` | ViewModel | `MapFragmentWithTrackViewModel.mapClustersFlow` gating | `REQ-UI-322.2` | Specified |
| `TST-UI-282.10` | ViewModel | `TrackingViewModel.mapClusters` layer gating | `REQ-UI-322.5` | Specified |
| `TST-UI-282.11` | Repository | `WorkoutClusterRepository` fast async hydration | `REQ-UI-322.5` | Specified |
