# Stage 1 Analysis: ATT-4847 - Render Lieblingsstrecken (Workout Clusters) as Toggleable Layer on General Map & Cockpit Map

**Ticket**: [ATT-4847](https://rainerblind.atlassian.net/browse/ATT-4847)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Branch**: `feature/ATT-4847`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & Motivation
Athletes have automated recurring routes (*Lieblingsstrecken* / `WorkoutCluster`) generated from historical activities by `WorkoutClusterEngine`. Currently, while the General Map (`MapScreenWithTrack`) allows toggling `ROUTES`, `SEGMENTS`, `KNOWN_LOCATIONS`, and `TRACK`, and the Cockpit live tracking map (`TrackingMapContainer`) renders routes and segments, favorite route clusters remain invisible on both maps. Exposing *Lieblingsstrecken* as a first-class map layer provides athletes with spatial discovery of recurring routes, seamless inspection of cluster metadata, and route awareness during tracking.

---

## 2. Architectural Gap Analysis & Forensic Call-Site Audit
* **General Map Layer (`GeneralMapLayer.kt:25-30`)**: `enum class GeneralMapLayer` only enumerates `ROUTES`, `SEGMENTS`, `KNOWN_LOCATIONS`, and `TRACK`. Lacks `CLUSTERS`.
* **General Map UI State Pipeline (`MapFragmentWithTrackViewModel.kt:39-135`)**: Does not inject or subscribe to `WorkoutClusterRepository.allClusters`. `uiState` lacks a `clusters: List<MapCluster>` field, and the 5-flow `combine` ignores cluster emissions.
* **Map Content Orchestration Scope (`MapContentScope.kt:45-135, 144-350`)**: `MapContentScope` lacks a `clusters(clusters: List<MapCluster>, onClusterClick: (Long) -> Unit)` DSL entry, and `MapContentScopeImpl` has no `clusterData` state backing or render pass in `Render()`.
* **Map Presentation Model (`MapModels.kt:183-312`)**: No `MapCluster` model implementing `MappablePath`. Needs referentially stable `latLngs` caching per `REQ-MAP-051.2` to prevent repetitive decoding from `WorkoutCluster.routePolyline ?: previewPaths.firstOrNull()`.
* **General Map Screen & BottomSheet (`MapScreenWithTrack.kt:280-385`)**: Dropdown menu lacks `CLUSTERS` toggle with legend swatch; `ATrainingTrackerMap` body omits conditional `clusters()` invocation; bottom sheet scaffold lacks cluster detail peek handling.
* **Cockpit Map State Pipeline (`TrackingViewModel.kt:360-475`, `TrackingMapState.kt:34-47`)**: `TrackingMapState` does not expose `mapClusters`. `staticMapLayerFlow` does not combine `WorkoutClusterRepository.allClusters`, risking missing or 1-Hz recomputed cluster polylines.
* **Cockpit Map Rendering Barrier (`SensorGridScreen.kt:1201-1230`)**: `TrackingMapContainer` does not include `mapClusters` in its `remember` barrier or DSL pass.

---

## 3. Independent Peer Review & Verification (4-Augen-Prinzip — ATT-3534)
* **Ticket Hypothesis Audit**: The ticket correctly identifies the missing `CLUSTERS` layer in both `MapScreenWithTrack` and `TrackingMapContainer`.
* **Blind Spot 1 — Contract & Localization Tests**: `GeneralMapLayersContractTest.kt:105` explicitly asserts `GeneralMapLayer.entries.size == 4`, and `GeneralMapLayersLocalizationTest.kt:36-43` asserts parity across an explicit key list. Adding `CLUSTERS` will break these tests unless updated to 5 entries and `map_layer_clusters`.
* **Blind Spot 2 — Referential Identity & OOM Prevention (`REQ-MAP-051`)**: A `WorkoutCluster` stores polylines as encoded strings (`routePolyline`, `previewPaths`). Naive decoding on every Flow emission or recomposition will violate `REQ-MAP-051.2`, allocating new `List<LatLng>` instances and triggering Maps SDK memory pressure. A thread-safe memoization cache (`ConcurrentHashMap<String, List<LatLng>>`) is required.
* **Blind Spot 3 — Static Flow Decoupling in TrackingViewModel**: `allClusters` must join `staticMapLayerFlow` via `distinctUntilChanged()`, strictly avoiding any cluster re-mapping inside `liveTelemetryFlow` (1-Hz ticks).

---

## 4. User Scope Grounding (ATT-1250)
* **In-Scope Objectives**:
  1. Add `GeneralMapLayer.CLUSTERS` with 9-language localized strings (`map_layer_clusters`) and purple legend swatch.
  2. Define `@Immutable data class MapCluster(...) : MappablePath` with memoized `latLngs` decoding.
  3. Wire `allClusters` into `MapFragmentWithTrackViewModel.uiState` and render conditionally in `MapScreenWithTrack` with peekable bottom sheet.
  4. Integrate `allClusters` into `TrackingViewModel.staticMapLayerFlow` and render in `TrackingMapContainer`.
  5. Update contract and localization tests to enforce 5 layers.
* **Out-of-Scope Non-Goals**:
  1. Modifying cluster generation, centroid calculation, or spatial clustering thresholds (`WorkoutClusterEngine`).
  2. Linking or unlinking routes/workouts to clusters from the map screen (handled in `WorkoutClustersScreen`).

---

## 5. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Target Requirements**: Amending `REQ-UI-322` (*General Map Layer Visibility Controls*) and complementing `REQ-MAP-051` (*Referential Map Polyline Allocation & Static Layer Decoupling*).
* **Historical Origin**: Commit `cc879818` (2026-10-09, `ATT-2931`) introduced `GeneralMapLayer` with 4 initial layers. Commit `ATT-3690` (2026-10-18) established `REQ-MAP-051` decoupling.
* **Root Reason for Existing Formulation**: `GeneralMapLayer` was constrained to 4 layers because favorite route clusters (`ATT-176`) were not yet integrated into spatial map presentation layers.
* **Preservation of Core Invariants**: Existing layer persistence (`SharedPreferences`), default all-enabled state, follow-me camera kinematics, and 100% test suite pass rate remain strictly invariant.

---

## 6. Architectural Strategy & High-Level Solution
* **Data Flow & Memoization**:
  * Extension `WorkoutCluster.toMapCluster(cache: ConcurrentMap<String, List<LatLng>>): MapCluster?` extracts representative polyline (`routePolyline ?: previewPaths.firstOrNull()`), decodes once, and caches `List<LatLng>`.
  * `MapFragmentWithTrackViewModel` combines static layers (`segments`, `routes`, `clusters`, `knownLocations`) decoupled from 1-Hz `currentTrack`.
  * `TrackingViewModel.staticMapLayerFlow` includes `mapClustersFlow.distinctUntilChanged()`.
* **Map Presentation & DSL**:
  * `MapContentScope` adds `fun clusters(clusters: List<MapCluster>, onClusterClick: (Long) -> Unit = {})`.
  * `MappablePathLayer` renders `MapCluster` using `TTColor.Cluster` (e.g., `#8E24AA`) with zIndex `12.0f`.
  * `MapScreenWithTrack` renders cluster item in dropdown menu and displays `ClusterOnMapSheet` when selected.

---

## 7. System Invariants & Risk Assessment
* **Invariants**: `GeneralMapLayer` string set persistence backward compatibility; zero 1-Hz allocation in `TrackingViewModel`; non-blocking map rendering; 100% clean-room test pass rate across all 9 locales.
* **Risk Rating**: **LOW-MEDIUM** — Touches core map layer enums, viewmodels, and Compose map containers, but follows established `REQ-MAP-051` / `REQ-UI-322` patterns. Fully verifiable via synthetic unit tests and ADB screenshot verification.
