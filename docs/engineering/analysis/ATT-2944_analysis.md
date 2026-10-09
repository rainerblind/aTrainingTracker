# Stage 1 Analysis: ATT-2944 - Isolate tracking map composable from sensor grid telemetry recomposition churn

**Ticket**: [ATT-2944](https://atrainingtracker.atlassian.net/browse/ATT-2944)  
**Sub-task**: [ATT-2999](https://atrainingtracker.atlassian.net/browse/ATT-2999) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2944`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Problem Summary

During active workout tracking on `SensorGridScreen.kt`, the live Google Map (`ATrainingTrackerMap`) is directly composed inside the main composable body of `SensorGridScreen` alongside the sensor metric fields (`SensorFieldView`).

High-frequency sensor telemetry—such as continuous Heart Rate fluctuations, Cycling Power updates, Cadence sensor events, and the 1 Hz workout elapsed duration timer—triggers continuous updates to `TrackingScreenState.fields` via `TrackingViewModel`.

Because `SensorGridScreen` accepts `state: TrackingScreenState`, every telemetry update causes `SensorGridScreen` to recompose. In the existing implementation:
1. `ATrainingTrackerMap` was directly invoked with an un-remembered trailing lambda:
   ```kotlin
   ATrainingTrackerMap(...) {
       tracks(state.mapTracks)
       segments(state.mapSegments, state.activeLiveSegmentIds)
       routes(state.mapRoutes)
       markers(state.mapMarkers)
       liveTrack(state.currentTrack)
   }
   ```
   Every recomposition allocated a new `content: MapContentScope.() -> Unit` lambda instance.
2. Inside `ATrainingTrackerMap.kt:155`, `scope = remember(content, ...) { MapContentScopeImpl(...) }` invalidated on every frame because `content` changed.
3. This repeatedly triggered `scope.collect(content)`, rebuilding all tracks, segments, routes, and markers, and re-invoking `MapBoundsController` every second.
4. Furthermore, in `TrackingViewModel.kt:238-325`, `allFilteredSensorData` was combined with `mapDataFlow`. Every sensor tick re-mapped all routes (`allRoutes.map { it.toMapRoute(...) }`), segments, and markers in memory.

This continuous recomposition churn consumes unnecessary CPU cycles, induces micro-stutter on low-to-mid-tier devices, and drains battery life during extended 3–6 hour endurance rides.

---

## 2. Forensic Investigation & Root Cause Analysis

### 2.1 The Compose Recomposition Chain
The churn occurs across three architectural layers:
```
[BANALServiceRepository.allFilteredSensorData] (HR / Power / 1Hz Timer)
   │
   ▼
[TrackingViewModel.loadSensorFieldStates]
   ├── Re-maps allRoutes to MapRoute list (O(N) object allocations)
   ├── Re-maps segments & markers
   └── Emits new TrackingScreenState to _uiState
         │
         ▼
[TrackingTabGridContent]
   └── Collects uiState -> calls SensorGridScreen(state = uiState)
         │
         ▼
[SensorGridScreen] Recomposes
   ├── Recomposes SensorFieldView rows (expected for metrics)
   └── Recomposes ATrainingTrackerMap with NEW inline trailing lambda:
         { tracks(...); segments(...); routes(...); markers(...); liveTrack(...) }
               │
               ▼
[ATrainingTrackerMap] Cannot be skipped by Compose compiler
   ├── remember(content, ...) key changed -> allocates new MapContentScopeImpl
   ├── scope.collect(content) re-executes all layer registration DSL blocks
   └── MapBoundsController re-evaluates spatial bounds
```

### 2.2 Why Compose Could Not Skip `ATrainingTrackerMap`
Under Jetpack Compose rules, a composable function can only be skipped during recomposition if:
1. All its parameters are `@Stable` or `@Immutable`.
2. All parameter values are equal (`equals`) to their values from the previous recomposition.

Because `content: MapContentScope.() -> Unit` was passed as an inline lambda capturing `state`, each recomposition created a brand new lambda reference. Therefore, Compose was forced to execute `ATrainingTrackerMap` on every single sensor tick, even though not a single route, track, or segment coordinate changed.

---

## 3. Chesterton's Fence Requirement Archaeology

* **Original Requirements & Targets**:
  - `REQ-UI-275` (*Unified WYSIWYG Tracking Tab Configuration with Spatial Overlays and Per-Tab Popup Toggles*, ATT-2360).
  - `REQ-UI-295` (*Unified Scrollable Container Architecture and Viewport Slotting for Tracking Tab Configuration Mode*, ATT-2620).
  - `REQ-MAP-021` (*Dark Theme & Map Styling*, ATT-1841).
* **Historical Origin & Commit Trace**:
  - Commit `81b77864` (*fix(navigation): streamline tracking tab config and isolate UI state*).
  - Commit `ec2b94b9` (*feat(tracking): unified WYSIWYG cockpit configuration and per-tab gating*).
* **Root Reason for Existing Formulation**:
  - `ATrainingTrackerMap` was embedded directly in `SensorGridScreen` during the rapid development of the WYSIWYG tracking cockpit in Sprint 2026-41.1 (`ATT-2360`). At the time, all tab state was consolidated into `TrackingScreenState` for convenient unified collection. The performance impact of continuous recomposition on physical devices under 1 Hz telemetry streaming was recognized in retrospective testing.
* **Preservation of Core Invariants**:
  - Dynamic map visibility (`showMap`), weighted expansion (`Modifier.weight(1f)` in tracking/preview mode), and configuration mode toggle cards (`REQ-UI-295`) remain fully preserved.
  - Live track rendering (`liveTrack`), user location arrow streaming (`currentLocationFlow`), follow-me tracking, and spatial layer visibility remain 100% operational.
  - 100% full-suite unit test pass rate across tracking and map test suites.

---

## 4. Scope Grounding & Proposed Solution

### 4.1 In-Scope Deliverables
1. **Dedicated Map Sub-State Model (`TrackingMapState`)**:
   - Declare `@Immutable data class TrackingMapState(...)` containing strictly map-related properties:
     `showMap`, `zoomFocus`, `userBearing`, `userSpeed`, `bSportType`, `currentTrack`, `mapTracks`, `mapSegments`, `activeLiveSegmentIds`, `mapRoutes`, `mapMarkers`.
   - Embed `mapState: TrackingMapState` inside `TrackingScreenState`, preserving backward-compatible property accessors.
2. **Dedicated Isolated Map Composable (`TrackingMapContainer`)**:
   - Extract map rendering into `TrackingMapContainer`:
     ```kotlin
     @Composable
     fun TrackingMapContainer(
         mapState: TrackingMapState,
         currentLocationFlow: StateFlow<LatLng?>,
         modifier: Modifier = Modifier
     )
     ```
   - Inside `TrackingMapContainer`, memoize the `MapContentScope.() -> Unit` lambda using:
     ```kotlin
     val mapContent: MapContentScope.() -> Unit = remember(
         mapState.mapTracks,
         mapState.mapSegments,
         mapState.activeLiveSegmentIds,
         mapState.mapRoutes,
         mapState.mapMarkers,
         mapState.currentTrack
     ) {
         {
             tracks(mapState.mapTracks)
             segments(mapState.mapSegments, mapState.activeLiveSegmentIds)
             routes(mapState.mapRoutes)
             markers(mapState.mapMarkers)
             liveTrack(mapState.currentTrack)
         }
     }
     ```
3. **Decoupled Map Computation in `TrackingViewModel`**:
   - Memoize or isolate `TrackingMapState` in `TrackingViewModel` so that high-frequency updates from `allFilteredSensorData` reuse the existing `mapState` instance when routes, tracks, and segments have not changed.
4. **Compose Skipping Guarantees**:
   - Because `mapState` is `@Immutable` and `oldMapState == newMapState`, Compose compiler skips `TrackingMapContainer` entirely when `SensorGridScreen` recomposes on sensor telemetry updates.
   - Zero lambda allocations, zero map layer DSL rebuilds, and zero bounds calculations on 1 Hz telemetry ticks.

### 4.2 Out-of-Scope Items
* Altering the internal implementation of `ATrainingTrackerMap` or Google Maps SDK.
* Changing sensor field layout, typography, or styling.
* Modifying navigation prompt overlays or live climb sheet logic.

---

## 5. Acceptance Criteria

* `TrackingMapContainer` is skipped by Compose when only sensor telemetry (`state.fields`) updates.
* Map content DSL lambda is remembered and only re-evaluated when map inputs change.
* Live track points and user location updates continue streaming without latency or regression.
* 100% clean-room test pass rate across all unit tests.
