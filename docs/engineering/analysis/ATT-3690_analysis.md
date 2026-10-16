# Stage 1 Analysis: ATT-3690 - OutOfMemoryError in Google Maps Polyline.setPoints (m140.dsa.b) due to 1Hz MapRoute/MapSegment reallocation and unthrottled polyline updates

**Ticket**: [ATT-3690](https://rainerblind.atlassian.net/browse/ATT-3690)  
**Sub-task**: [ATT-3780](https://rainerblind.atlassian.net/browse/ATT-3780) (`[Analysis]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.6`  
**Branch**: `feature/ATT-3690`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Problem Statement & Motivation

In production release `V4.9.38.4 (267)`, Firebase Crashlytics reported a fatal `java.lang.OutOfMemoryError` exhausting the 512 MB JVM heap inside the Google Maps SDK polyline tessellation engine (`com.google.maps.api.android.lib6.phoenix.ee` / `m140.dsa.b`), triggered via Binder IPC from Jetpack Compose's `Polyline.setPoints`:

```text
Fatal Exception: java.lang.OutOfMemoryError: Failed to allocate a 80 byte allocation with 5148320 free bytes and 5027KB until OOM, target footprint 536870912, growth limit 536870912; giving up on allocation because <1% of heap free after GC.
       at m140.dsa.b(:com.google.android.gms.policy_maps_core_dynamite@260830213:293)
       at com.google.maps.api.android.lib6.phoenix.ee.e(:com.google.android.gms.policy_maps_core_dynamite@260830213:760)
       at com.google.maps.api.android.lib6.phoenix.ee.a(:com.google.android.gms.policy_maps_core_dynamite@260830213:104)
       at com.google.maps.api.android.lib6.impl.fu.U(:com.google.android.gms.policy_maps_core_dynamite@260830213:13)
       at com.google.maps.api.android.lib6.impl.fu.x(:com.google.android.gms.policy_maps_core_dynamite@260830213:29)
       at com.google.android.gms.maps.model.internal.ab.br(:com.google.android.gms.policy_maps_core_dynamite@260830213:436)
       at m140.azc.onTransact(:com.google.android.gms.policy_maps_core_dynamite@260830213:21)
       at android.os.Binder.transact(Binder.java:1269)
       at com.google.android.gms.internal.maps.zza.zzc(com.google.android.gms:play-services-maps@@20.0.0:2)
       at com.google.android.gms.internal.maps.zzap.zzw(com.google.android.gms:play-services-maps@@20.0.0:3)
       at com.google.android.gms.maps.model.Polyline.setPoints(com.google.android.gms:play-services-maps@@20.0.0:2)
       at com.google.maps.android.compose.PolylineKt.PolylineImpl_LjegJe0$lambda$2$1(Polyline.kt:201)
```

 Static map layers (`MapRoute` and `MapSegment` polylines) and interactive workout crop previews must not reallocate `List<LatLng>` and `List<PatternItem>` instances on every 1-Hz live GPS tick or 60–120 Hz slider drag frame.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic inspection of the map rendering pipeline revealed four compounding mechanisms that flood `Polyline.setPoints` and `Polyline.setPattern` with newly allocated lists:

1. **1-Hz Reallocation of All `MapRoute` & `MapSegment` Wrappers (`TrackingViewModel.kt:241-314`, `MapModels.kt:206, 233, 263`)**:
   - In `TrackingViewModel.observeMapState()`, `liveSegmentsRepository.liveSegments` (which emits every 1 second as `live.liveData` updates) and `banalServiceRepository.currentTrack` (which emits every 1 second as GPS points arrive) are combined inside the same lambda that executes `allLiveSegments.map { MapSegment(...) }` and `allRoutes.map { it.toMapRoute(...) }`.
   - Even though `MapSegment` depends exclusively on `live.staticData` (`SegmentWithPath`) and `MapRoute` depends exclusively on `RouteWithPath` and `activeNavigatedRouteId`, every 1-second tick instantiates brand-new `MapSegment` and `MapRoute` objects for every stored segment and route in the database.
   - Because `override val latLngs: List<LatLng> by lazy { path.map { it.latLng } }` is defined in the class body of `MapTrack`, `MapSegment`, and `MapRoute` (rather than as a constructor parameter or precomputed on `RouteWithPath` / `SegmentWithPath`), every new wrapper instance receives a fresh `Lazy` delegate and allocates a new `ArrayList<LatLng>` on every 1-second tick.
2. **Per-Tick Recreation of `MapContentScopeImpl` & Unkeyed Layer Loops (`SensorGridScreen.kt:859-874`, `ATrainingTrackerMap.kt:163-166`, `MapContentScope.kt:193-205, 280-338`)**:
   - In `SensorGridScreen.kt`, `mapContent` includes `mapState.currentTrack` in its `remember` keys, producing a new lambda instance every 1 second.
   - In `ATrainingTrackerMap.kt:163`, `remember(content, zoomFocus, ...)` keys on `content`, so `MapContentScopeImpl` is discarded and recreated from scratch every 1 second.
   - Furthermore, `scope.collect(content)` executes directly during composition and clears/repopulates 10 `SnapshotStateList` containers on every recomposition (including camera movement frames).
   - In `MapContentScope.Render`, `contextualPaths.forEach`, `segmentData.forEach`, `routeData.forEach`, and `trackData.forEach` omit `key(data.path.id)`, causing Compose to re-evaluate `Polyline.setPoints` across dual `XRayPolyline` layers (2 `Polyline` nodes per route/track).
3. **Transient `PatternItem` List Allocations on Every Read (`MapModels.kt:212-215, 238-244, 295-303`, `MapLayers.kt:269`)**:
   - `MapTrack.pattern`, `MapSegment.pattern`, `MapRoute.pattern`, and `LiveTrackLayer` construct a new `listOf(Dash(...), Gap(...))` or `listOf(Dot(), Gap(...))` on every property access, repeatedly triggering native pattern re-tessellation in `lib6.phoenix`.
4. **Unthrottled 60–120 Hz Full-List Reallocation in `CropWorkoutScreen` (`CropWorkoutScreen.kt:161-204`)**:
   - Dragging the `RangeSlider` in `CropWorkoutScreen` slices `uiState.points`, maps every retained point into a new `PathPoint`, creates a new `MapTrack`, and allocates a fresh `latLngs` list on every drag frame without downsampling, flooding the 200dp `liteMode` `PathPreviewMap` with full-resolution `Polyline.setPoints` calls.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Decouple static `mapSegments` and `mapRoutes` flows in `TrackingViewModel.observeMapState()` from 1-Hz `currentTrack` and 1-Hz `liveData` emissions (using `.map { list -> list.map { it.staticData } }.distinctUntilChanged()` for segments and `combine(allRoutes, activeNavigatedRouteId).distinctUntilChanged()` for routes), while ensuring live `currentSpeed` and `currentBearing` remain reactively subscribed per Rule 39.
  2. Memoize `latLngs: List<LatLng>` on `RouteWithPath`, `SegmentWithPath`, `MapRoute`, `MapSegment`, and `MapTrack` and hoist `PatternItem` lists into immutable constants so `points` and `pattern` references remain referentially identical (`===`) across recompositions.
  3. Stabilize `MapContentScopeImpl` in `ATrainingTrackerMap.kt` (removing `content` from `remember` keys), avoid redundant `SnapshotStateList` mutations when list contents are unchanged, and wrap `MappablePathLayer` calls in `MapContentScope.Render` with `key(data.path.id)`.
  4. Pre-extract `PathPoint`s once in `CropWorkoutScreen.kt` and downsample the preview path to at most `MAX_PREVIEW_POINTS = 500` points so slider dragging does not flood `Polyline.setPoints`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying database schemas for routes, segments, or workouts.
  * Altering visual styling (colors, widths, z-indices, or dash lengths) of polylines on the map.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-MAP-051` (Net-new requirement for Google Maps polyline memory stability, referential identity of static map layers, and preview downsampling). Adjacent existing requirements `REQ-UI-326` (isolated `TrackingMapContainer` recomposition barrier) and `REQ-MAP-046` (Unified Viewport Anchor Authority) are preserved intact.
* **Historical Origin & Commit Trace**: `ATT-2944` (`REQ-UI-326`) isolated `TrackingMapState` from sensor grid updates, and `ATT-3274` introduced `CropWorkoutScreen` with `PathPreviewMap`.
* **Root Reason for Existing Formulation**: `MapRoute`, `MapSegment`, and `MapTrack` originally used `by lazy { path.map { it.latLng } }` assuming wrapper instances were long-lived; however, `TrackingViewModel` reconstructed wrapper instances inside the 1-Hz `currentTrack` combine loop.
* **Preservation of Core Invariants**: Precomputing/sharing `latLngs` and decoupling static flows preserves all existing route/segment rendering and live segment activation behavior while guaranteeing $O(1)$ referential stability (`===`) of `List<LatLng>` and `List<PatternItem>` across 1-Hz tracking ticks.

---

## 5. Architectural Strategy & High-Level Solution

1. **Static Layer Flow Decoupling (`TrackingViewModel.kt`)**:
   - Create a dedicated `staticMapSegmentsFlow` from `liveSegmentsRepository.liveSegments.map { list -> list.map { it.staticData } }.distinctUntilChanged().map { staticList -> staticList.map { staticData -> MapSegment(..., latLngs = staticData.latLngs) } }`.
   - Create a dedicated `mapRoutesFlow` from `combine(routesRepository.allRoutes, routesRepository.activeNavigatedRouteId) { allRoutes, activeId -> allRoutes.map { it.toMapRoute(isActiveNavigation = (it.summary.id == activeId)) } }.distinctUntilChanged()`.
   - Create `activeLiveSegmentIdsFlow` from `activeLiveSegments.map { list -> list.map { it.staticData.summary.stravaId }.toSet() }.distinctUntilChanged()`.
   - Combine these pre-mapped static flows with live `currentTrack`, `bSportType`, `currentSpeed`, and `currentBearing` so static `MapSegment` and `MapRoute` instances (and their `latLngs` lists) are never reallocated on 1-Hz ticks.
2. **Referential Memoization of `latLngs` & Static `PatternItem` Constants (`MapModels.kt`, `MapLayers.kt`, `RoutesDatabaseManager.kt`, `SegmentsRepository.kt`)**:
   - Add `val latLngs: List<LatLng> = path.map { it.latLng }` to `RouteWithPath` and `SegmentWithPath`, and pass `override val latLngs: List<LatLng> = path.map { it.latLng }` directly into `MapTrack`, `MapSegment`, and `MapRoute` primary constructors.
   - Hoist `TRACK_BEST_PATTERN`, `SEGMENT_DASH_PATTERN`, and `ROUTE_DASH_PATTERN` into `MapVisualization` constants in `MapModels.kt`, and `remember(style.trackDotGap)` the live track pattern in `MapLayers.kt`.
3. **Stable `MapContentScopeImpl` & Keyed `MappablePathLayer` Composition (`ATrainingTrackerMap.kt`, `MapContentScope.kt`)**:
   - Remove `content` from `remember(...)` keys of `MapContentScopeImpl` in `ATrainingTrackerMap.kt` so the scope instance survives `content` lambda updates.
   - Update `SnapshotStateList` helper in `MapContentScopeImpl` to only mutate state lists when content actually differs, and wrap `contextualPaths`, `segmentData`, `routeData`, and `trackData` loop items in `MapContentScope.Render` with `key(data.path.id)`.
4. **Bounded Preview Downsampling (`CropWorkoutScreen.kt`)**:
   - Pre-extract all valid `PathPoint`s once via `remember(uiState.points)`.
   - Slice the retained sub-range and downsample via `CropWorkoutPreviewMath.downsamplePathPoints(points, maxPoints = 500)` (always preserving exact first and last points) before creating `MapTrack`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features and unit tests.
  2. Referential identity (`===`) of `MapRoute.latLngs`, `MapSegment.latLngs`, and `PatternItem` lists across 1-Hz GPS updates.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  All changes are localized to map model memoization, ViewModel flow decoupling, Compose key stabilization, and preview downsampling, verifiable via deterministic unit tests.
