# Stage 3: Implementation Plan - ATT-5074: Disable Lieblingsstrecken by Default & Optimize Loading Latency

**Ticket**: [ATT-5074](https://rainerblind.atlassian.net/browse/ATT-5074)  
**Sub-task**: [[ATT-5161]](https://rainerblind.atlassian.net/browse/ATT-5161) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*)  
**Test Mapping**: `TST-UI-282` (*General Map Layer Visibility Controls & Cluster Gating Contract*)  
**Branch**: `feature/ATT-5074`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Problem Description & Background
In ATT-4847, `GeneralMapLayer.CLUSTERS` (*Lieblingsstrecken*) was added to the general and cockpit maps. Because default enabled layers fell back to `entries.toSet()`, clusters were enabled by default, causing canvas clutter. Furthermore, loading clusters incurred high latency due to coupling with heavy `clusterStats` SQL queries, un-gated decoding, UI-thread execution, and missing fast asynchronous repository hydration.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*), Sections 1, 2, 5
* **Test Mapping**: `TST-UI-282` (`TST-UI-282.1`, `TST-UI-282.2`, `TST-UI-282.9`, `TST-UI-282.10`, `TST-UI-282.11`)

---

## 3. System Invariants & Preserved Behavior
1. **Default Layer Invariants**: Primary layers (`ROUTES`, `SEGMENTS`, `KNOWN_LOCATIONS`, `TRACK`) remain enabled by default.
2. **User Setting Stability**: Existing user selections in `SharedPreferences` (`PREF_GENERAL_MAP_ENABLED_LAYERS`) remain valid.
3. **Threading & Non-Blocking UI**: Polyline decoding runs on `Dispatchers.Default`; SQLite IO runs on `Dispatchers.IO`.
4. **Referential Stability**: Polyline memoization in `sClusterLatLngCache` (`REQ-MAP-051.2`) remains preserved.

---

## 4. Proposed Architectural Changes

### Component 1: `GeneralMapLayer` & ViewModels Layer Gating
* Define `GeneralMapLayer.DEFAULT_ENABLED = setOf(ROUTES, SEGMENTS, KNOWN_LOCATIONS, TRACK)`.
* `MapFragmentWithTrackViewModel.loadEnabledLayers()` defaults to `DEFAULT_ENABLED`.
* `mapClustersFlow` in `MapFragmentWithTrackViewModel` transforms dynamically via `enabledLayers`: emits `emptyList()` when `CLUSTERS !in enabledLayers`, offloading polyline mapping to `Dispatchers.Default`.
* `TrackingViewModel` reads `PREF_GENERAL_MAP_ENABLED_LAYERS` (defaulting to `DEFAULT_ENABLED`) to gate `mapClusters`.

### Component 2: `WorkoutClusterRepository` Fast Asynchronous Hydration
* Decouple map cluster loading from heavy `clusterStats` aggregation.
* Launch fast background load in `init` (`Dispatchers.IO`) populating `_allClusters` directly from SQLite `RouteClusters` table without blocking on `WorkoutSummaries` scans or member hydration.

### UI Consistency (Rule 23)
* **Reference screen**: `MapScreenWithTrack.kt` floating layers dropdown.
* **Reused components / tokens**: Material 3 `DropdownMenu`, `DropdownMenuItem`, `Checkbox`, `TTColor.Cluster` swatch.
* **New one-off styles**: No UI styling changes; purely visibility gating and latency reduction.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Default Layer Set Definition & Repository Fast Hydration
* Files: `GeneralMapLayer.kt`, `WorkoutClusterRepository.kt`
* Define `DEFAULT_ENABLED`. Add non-blocking `init` load in `WorkoutClusterRepository` via `repositoryScope.launch(Dispatchers.IO)`.

### Step 2: ViewModel Layer Gating & Background Decoding
* Files: `MapFragmentWithTrackViewModel.kt`, `TrackingViewModel.kt`
* Gate `mapClustersFlow` on `GeneralMapLayer.CLUSTERS in enabledLayers`. Apply `.flowOn(Dispatchers.Default)` to decoding pipeline.

### Step 3: Contract & Unit Test Updates
* Files: `GeneralMapLayersContractTest.kt`, `MapFragmentWithTrackViewModelTest.kt`, `TrackingViewModelMapStateTest.kt`, `WorkoutClusterRepositoryTest.kt`
* Update existing tests for `DEFAULT_ENABLED` and add tests for cluster layer gating and fast hydration.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.database.WorkoutClusterRepositoryTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests (`GeneralMapLayersContractTest`, `MapFragmentWithTrackViewModelTest`, `TrackingViewModelMapStateTest`, `WorkoutClusterRepositoryTest`), followed by Tier 1 governance check.
* **Rollback**: Clean git branch isolation (`feature/ATT-5074`); changes revertable via single branch drop.
