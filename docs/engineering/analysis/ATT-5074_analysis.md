# Stage 1 Analysis: ATT-5074 - Lieblingsstrecken map layer: disable by default and significantly reduce loading/rendering latency

**Ticket**: [ATT-5074](https://rainerblind.atlassian.net/browse/ATT-5074)  
**Sub-task**: [[ATT-5159]](https://rainerblind.atlassian.net/browse/ATT-5159) (`[Analysis]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Branch**: `feature/ATT-5074`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Problem Statement & Motivation
Following the introduction of the *Lieblingsstrecken* (`GeneralMapLayer.CLUSTERS`) map layer in ATT-4847:
1. Favorite route clusters are enabled by default on both the General Map and live Cockpit Tracking Map via `GeneralMapLayer.entries.toSet()`, cluttering the map canvas on first launch.
2. When the layer is loaded or toggled, users experience substantial latency before cluster polylines appear, caused by coupling to heavy workout aggregation queries, un-gated decoding, Main-thread execution, and lack of lazy initialization in `WorkoutClusterRepository`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Default Enabled Inadvertence**: In ATT-4847, `CLUSTERS` was added to `GeneralMapLayer` enum while `MapFragmentWithTrackViewModel.loadEnabledLayers()` and `GeneralMapLayer.entries.toSet()` defaulted to all entries, unintentionally enabling `CLUSTERS`. In `TrackingViewModel`, clusters were piped unconditionally without evaluating layer preferences.
* **Latency Bottleneck 1 (Heavy Stats & Member Hydration)**: `MapFragmentWithTrackViewModel.mapClustersFlow` combined `allClusters` with `clusterStats`. `refreshClusters()` and `clusterStats` execute expensive aggregation queries (`getWorkoutClusterStatsForAllClusters()`) and member workout polyline scans (`WorkoutSummaries`), stalling polyline emission.
* **Latency Bottleneck 2 (Main-Thread Polyline Decoding)**: `WorkoutCluster.toMapCluster()` decodes encoded polyline strings (`PolyUtil.decode`) inside the ViewModel's Flow combination without `flowOn(Dispatchers.Default)`, blocking the Main/UI thread for multiple clusters.
* **Latency Bottleneck 3 (Unconditional Decoding & Missing Repository Initialization)**: ViewModels decoded cluster polylines even when `GeneralMapLayer.CLUSTERS` was disabled. Conversely, `WorkoutClusterRepository` initialized `_allClusters` to `emptyList()` with no lazy DB load, requiring manual screen visits to trigger retrieval.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Verified: `MapFragmentWithTrackViewModel.kt:71` returns `GeneralMapLayer.entries.toSet()` when preferences are empty.
  * Verified: `TrackingViewModel.kt:414-429` combined `allClusters` unconditionally without layer gating.
  * Verified: `MapScreenWithTrack.kt:327` only uses `cluster.stats` for `ClusterOnMapSheet` popup clicks, never for rendering polyline strokes on canvas.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Define `GeneralMapLayer.DEFAULT_ENABLED` excluding `CLUSTERS`; update `MapFragmentWithTrackViewModel` and `TrackingViewModel` to disable `CLUSTERS` by default.
  2. Gate cluster polyline decoding and flow emissions in `MapFragmentWithTrackViewModel` and `TrackingViewModel` so they only execute when `CLUSTERS` is enabled.
  3. Ensure lightweight background initialization in `WorkoutClusterRepository` (reading directly from `RouteClusters` table without blocking on heavy `clusterStats` or member workout hydration) and offload `PolyUtil.decode` to `Dispatchers.Default`.
* **Out-of-Scope Non-Goals**:
  1. Altering spatial clustering thresholds, algorithms, or distance tolerances in `WorkoutClusterEngine`.
  2. Modifying `ClusterOnMapSheet` layout or typography (harmonized in ATT-5075).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*), Sections 2 & 5.
* **Historical Origin & Commit Trace**: Introduced in commit `cc879818` (ATT-2931, 2026-10-09), amended in commit `4d3f24f2` (ATT-4847, 2026-11-01), and refined in commit `95cb0048` (ATT-5075, 2026-11-02).
* **Root Reason for Existing Formulation**: ATT-2931 set all 4 original primary layers as default (`entries.toSet()`). ATT-4847 added `CLUSTERS` as the 5th layer without defining an explicit default set, inadvertently enabling it.
* **Preservation of Core Invariants**: Primary layers (`ROUTES`, `SEGMENTS`, `KNOWN_LOCATIONS`, `TRACK`) remain default-enabled; existing custom user preferences in `SharedPreferences` are preserved; stroke colors (`TTColor.Cluster`) and z-indexes (`12f`) remain intact.

---

## 5. Architectural Strategy & High-Level Solution
* **Default Layer Set**: Declare `GeneralMapLayer.DEFAULT_ENABLED = setOf(ROUTES, SEGMENTS, KNOWN_LOCATIONS, TRACK)`. `MapFragmentWithTrackViewModel.loadEnabledLayers()` falls back to `DEFAULT_ENABLED`.
* **Cockpit Map Gating**: `TrackingViewModel` evaluates `PREF_GENERAL_MAP_ENABLED_LAYERS` (falling back to `DEFAULT_ENABLED`); if `CLUSTERS !in enabledLayers`, `mapClusters` emits `emptyList()`.
* **Conditional & Asynchronous Pipeline**: In `MapFragmentWithTrackViewModel`, derive `mapClustersFlow` conditionally on `enabledLayers`: when disabled, emit `emptyList()` immediately. Offload polyline decoding to `Dispatchers.Default`.
* **Lightweight Repository Hydration**: `WorkoutClusterRepository` provides fast asynchronous loading from SQLite `RouteClusters` table on `Dispatchers.IO`, decoupling map polyline display from heavy `clusterStats` aggregation.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression in existing map layer persistence, toggles, and unit tests (`GeneralMapLayersContractTest`).
  2. No 1-Hz polyline allocations or UI thread stalling (`REQ-MAP-051`).
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** — Localized to map layer defaults and ViewModels flow gating; fully verifiable with fast unit tests and zero risk to database integrity.
