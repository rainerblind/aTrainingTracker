# Stage 2: Requirement & Test Specification - ATT-4847: Render Lieblingsstrecken as Map Layer

**Ticket**: [ATT-4847](https://rainerblind.atlassian.net/browse/ATT-4847)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*)  
**Test Spec ID**: `TST-UI-282`  
**Branch**: `feature/ATT-4847`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Requirement Specification (REQ-UI-322)

### 1.1 Scope & Functional Criteria
* `GeneralMapLayer` SHALL declare 5 entries: `ROUTES`, `SEGMENTS`, `KNOWN_LOCATIONS`, `TRACK`, and `CLUSTERS`.
* `MapFragmentWithTrackViewModel` SHALL subscribe to `WorkoutClusterRepository.allClusters`, decode/memoize representative polylines into `MapCluster` models with referential identity (`REQ-MAP-051`), and expose `clusters: List<MapCluster>` in `uiState`.
* `MapScreenWithTrack` SHALL render a 5th menu option for `CLUSTERS` with legend swatch (`TTColor.Cluster` / `#8E24AA`) and conditionally compose `clusters(uiState.clusters)` on `ATrainingTrackerMap`.
* `TrackingViewModel` SHALL incorporate `WorkoutClusterRepository.allClusters` into `staticMapLayerFlow` via `distinctUntilChanged()`, exposed as `TrackingMapState.mapClusters` (decoupled from 1-Hz GPS/telemetry).
* `SensorGridScreen` (`TrackingMapContainer`) SHALL render `clusters(mapState.mapClusters)` on `ATrainingTrackerMap`.
* String resource `map_layer_clusters` SHALL have 100% parity across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

### 1.2 Acceptance Criteria (Given-When-Then)
* *Given* saved *Lieblingsstrecken* in `WorkoutClusterRepository`, *When* viewing `MapScreenWithTrack`, *Then* the layers menu displays *Lieblingsstrecken* (`CLUSTERS`), and when toggled on, renders their polylines.
* *Given* an active or preview Cockpit map, *When* favorite clusters exist, *Then* polylines render via `TrackingMapContainer` with zero 1-Hz reallocation.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-322` (amending `REQ-UI-180` and `REQ-UI-308`, complementing `REQ-MAP-051`).
* **Historical Origin & Commit Trace**: `cc879818` (Sprint 2026-40.4, `ATT-2931`) and `ATT-3690` (Sprint 2026-41.6).
* **Root Reason for Existing Formulation**: Four layers were initially defined when clusters were exclusive to post-workout list views (`ATT-176`).
* **Preservation of Core Invariants**: Layer persistence in `SharedPreferences`, all-enabled default, referential polyline stability (`REQ-MAP-051`), and 100% test pass rate are preserved.

---

## 2. Test Specification (TST-UI-282)

* **`TST-UI-282.1` (GeneralMapLayer Enum & Default State)**: `GeneralMapLayersContractTest.kt` asserts `GeneralMapLayer.entries.size == 5` and contains `CLUSTERS`.
* **`TST-UI-282.2` (ViewModel State & Cluster Flow)**: `GeneralMapLayersContractTest.kt` verifies toggling `CLUSTERS` updates flow and persists to prefs; verifies `uiState.clusters` populates.
* **`TST-UI-282.3` (Cockpit Map Decoupling & Stability)**: `TrackingViewModelTest.kt` verifies `mapClusters` in `TrackingMapState` updates only when repository emits, not on 1-Hz telemetry.
* **`TST-UI-282.4` (9-Language Parity Audit)**: `GeneralMapLayersLocalizationTest.kt` asserts `map_layer_clusters` exists and is non-empty across all 9 locales.
* **`TST-UI-282.5` (Regression Gate)**: Targeted test suites pass cleanly.

---

## 3. Traceability Matrix

| Test ID | Scope | Target Test Class / Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-282.1` | Contract | `GeneralMapLayersContractTest.testGeneralMapLayer_enumEntries` | `REQ-UI-322.1` | Specified |
| `TST-UI-282.2` | Contract | `GeneralMapLayersContractTest.testToggleClusterLayer` | `REQ-UI-322.2` | Specified |
| `TST-UI-282.3` | Unit | `TrackingViewModelTest.testStaticMapLayers_includesClusters` | `REQ-UI-322.5` | Specified |
| `TST-UI-282.4` | Localization | `GeneralMapLayersLocalizationTest.testGeneralMapLayersStringsParity` | `REQ-UI-322.6` | Specified |
| `TST-UI-282.5` | Regression | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*"` | `REQ-PRO-001` | Specified |
