# Stage 3: Implementation Plan - ATT-3690: OutOfMemoryError in Google Maps Polyline.setPoints (m140.dsa.b) due to 1Hz MapRoute/MapSegment reallocation and unthrottled polyline updates

**Ticket**: [ATT-3690](https://rainerblind.atlassian.net/browse/ATT-3690)  
**Sub-task**: [ATT-3782](https://rainerblind.atlassian.net/browse/ATT-3782) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.6`  
**Requirement Mapping**: `REQ-MAP-051`  
**Test Mapping**: `TST-MAP-053`  
**Branch**: `feature/ATT-3690`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Problem Description & Background

In production release `V4.9.38.4 (267)`, Firebase Crashlytics recorded a fatal `java.lang.OutOfMemoryError` in Google Maps SDK's polyline tessellation engine (`m140.dsa.b` / `com.google.maps.api.android.lib6.phoenix.ee`) triggered via `Polyline.setPoints`. Four compounding allocation sites flood `Polyline.setPoints` and `Polyline.setPattern` with newly allocated `List<LatLng>` and `List<PatternItem>` objects:
1. `TrackingViewModel.observeMapState()` combines 1-Hz `currentTrack` and 1-Hz `liveSegments` updates inside the same lambda that instantiates `MapSegment` and `MapRoute` wrappers for every route and segment in the database, resetting their `by lazy { path.map { it.latLng } }` delegates every second.
2. `MapTrack.pattern`, `MapSegment.pattern`, `MapRoute.pattern`, and `LiveTrackLayer` allocate new `List<PatternItem>` instances on every property read/recomposition.
3. `ATrainingTrackerMap.kt` includes `content` in `remember(content, ...)` keys for `MapContentScopeImpl`, recreating `MapContentScopeImpl` every second, while `MapContentScope.Render` lacks `key(data.path.id)` around `MappablePathLayer` calls.
4. `CropWorkoutScreen.kt` slices and maps thousands of `PathPoint` and `LatLng` instances on every 60–120 Hz `RangeSlider` drag frame without downsampling.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-051` (*Referentially Stable Map Polyline Allocation, Static Layer Flow Decoupling, Keyed Layer Composition & Preview Downsampling*)
* **Test Mapping**: `TST-MAP-053` (*Map Polyline Memory Stability, Referential Identity & Preview Downsampling Verification Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites and map rendering behaviors continue to pass cleanly.
2. **Thread Safety & Dispatcher Affinity**: Database operations remain isolated to designated single-thread dispatchers; ViewModel flows run on `viewModelScope`.
3. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
4. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Domain & Presentation Path Models (`RoutesDatabaseManager.kt`, `SegmentsRepository.kt`, `MapModels.kt`, `MapLayers.kt`)
* In `RouteWithPath` (`RoutesDatabaseManager.kt`) and `SegmentWithPath` (`SegmentsRepository.kt`), precompute `val latLngs: List<LatLng> = path.map { it.latLng }`.
* In `MapTrack`, `MapSegment`, and `MapRoute` (`MapModels.kt`), accept `override val latLngs: List<LatLng> = path.map { it.latLng }` in the primary constructor instead of an unshared class-body `by lazy` delegate. Update `RouteWithPath.toMapRoute(...)` to pass `latLngs = this.latLngs`.
* In `MapVisualization` (`MapModels.kt`), define immutable `TRACK_BEST_PATTERN`, `SEGMENT_DASH_PATTERN`, and `ROUTE_DASH_PATTERN` constants (`List<PatternItem>`), and return them from `MapTrack.pattern`, `MapSegment.pattern`, and `MapRoute.pattern`.
* In `LiveTrackLayer` (`MapLayers.kt`), memoize the live track pattern via `val livePattern = remember(style.trackDotGap) { listOf(Dot(), Gap(style.trackDotGap)) }`.

### Component 2: ViewModel Static Map Layer Decoupling (`TrackingViewModel.kt`)
* In `TrackingViewModel.observeMapState()`:
  * Create `staticMapSegmentsFlow` from `liveSegmentsRepository.liveSegments.map { list -> list.map { it.staticData } }.distinctUntilChanged().map { staticList -> staticList.map { staticData -> MapSegment(stravaId = staticData.summary.stravaId, name = staticData.summary.name, bSportType = staticData.summary.bSportType, path = staticData.path, latLngs = staticData.latLngs) } }`.
  * Create `activeLiveSegmentIdsFlow` from `activeLiveSegments.map { list -> list.map { it.staticData.summary.stravaId }.toSet() }.distinctUntilChanged()`.
  * Create `mapRoutesFlow` from `combine(routesRepository.allRoutes, routesRepository.activeNavigatedRouteId) { allRoutes, activeId -> allRoutes.map { it.toMapRoute(isActiveNavigation = (it.summary.id == activeId)) } }.distinctUntilChanged()`.
  * Combine the static layer state with `trackingViewsRepository.getTrackingViewInfoFlow(viewId)`, `banalServiceRepository.currentTrack`, `banalServiceRepository.bSportType`, `banalServiceRepository.currentSpeed`, and `banalServiceRepository.currentBearing` (maintaining Rule 39 reactive telemetry subscription) so static `MapSegment` and `MapRoute` instances are never recreated on 1-Hz GPS or telemetry ticks.

### Component 3: Map Scope Stabilization & Keyed Layer Loops (`ATrainingTrackerMap.kt`, `MapContentScope.kt`)
* In `ATrainingTrackerMap.kt`, remove `content` from `remember(zoomFocus, primaryColor, context, directionIcons, bSportType)` when instantiating `MapContentScopeImpl`.
* In `MapContentScopeImpl` (`MapContentScope.kt`), update `collect(block)` to gather entries into temporary staging lists and only mutate each `SnapshotStateList` (`trackData`, `segmentData`, `routeData`, `markers`, `currentTracks`, `heatmaps`, `contextualPaths`, `locationData`, `lapHighlights`, `climbHighlights`) when the staged list differs from the current state list.
* In `MapContentScope.Render` (`MapContentScope.kt`), wrap `contextualPaths.forEach`, `segmentData.forEach`, `routeData.forEach`, and `trackData.forEach` bodies in `androidx.compose.runtime.key(data.path.id) { ... }`.

### Component 4: Bounded Preview Downsampling (`CropWorkoutScreen.kt`)
* Introduce `object CropWorkoutPreviewMath` in `CropWorkoutScreen.kt` with `const val MAX_PREVIEW_POINTS = 500` and `fun downsamplePathPoints(points: List<PathPoint>, maxPoints: Int = MAX_PREVIEW_POINTS): List<PathPoint>` which returns `points` unchanged when `points.size <= maxPoints`, and otherwise samples `maxPoints` evenly spaced indices across `0..points.lastIndex` (always preserving `points.first()` at index `0` and `points.last()` at index `maxPoints - 1`).
* In `CropWorkoutBottomSheet`, pre-extract `allValidPathPoints` once per workout load (`remember(uiState.points)`), slice the retained window, and pass `CropWorkoutPreviewMath.downsamplePathPoints(slicedPoints)` to `MapTrack`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `ATrainingTrackerMap.kt`, `MapLayers.kt`, `CropWorkoutScreen.kt`
* **Reused components**: `XRayPolyline`, `MappablePathLayer`, `PathPreviewMap`
* **Theme tokens**: Unchanged (`TTColor.RouteActiveNavigation`, `TTColor.RouteSelected`, `TTColor.StravaOrange`, `TTColor.DarkMapTrackCyan`)
* **New one-off styles & justification**: None (visual styling, colors, widths, and dash lengths remain 100% identical).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Memoize `latLngs` and Hoist Static `PatternItem` Constants
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/database/RoutesDatabaseManager.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/segments/SegmentsRepository.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapModels.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapLayers.kt`
* Changes: Add `val latLngs: List<LatLng> = path.map { it.latLng }` to `RouteWithPath` and `SegmentWithPath`; move `latLngs` to primary constructors of `MapTrack`, `MapSegment`, `MapRoute`; hoist `TRACK_BEST_PATTERN`, `SEGMENT_DASH_PATTERN`, `ROUTE_DASH_PATTERN` into `MapVisualization`; memoize `livePattern` in `LiveTrackLayer`.

### Step 2: Decouple Static Map Layer Flows in `TrackingViewModel`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt`
* Changes: Extract `staticMapSegmentsFlow`, `activeLiveSegmentIdsFlow`, and `mapRoutesFlow` with `.distinctUntilChanged()` so static `MapSegment` and `MapRoute` wrappers are never reallocated on 1-Hz ticks, while subscribing `currentSpeed` and `currentBearing` reactively.

### Step 3: Stabilize `MapContentScopeImpl` & Add Compose Keys in `MapContentScope.Render`
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapContentScope.kt`
* Changes: Remove `content` from `remember` keys of `MapContentScopeImpl`, diff-update `SnapshotStateList` containers in `collect(...)`, and wrap `MappablePathLayer` calls in `key(data.path.id)`.

### Step 4: Pre-Extract & Downsample Preview Path in `CropWorkoutScreen`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/crop/CropWorkoutScreen.kt`
* Changes: Add `CropWorkoutPreviewMath.downsamplePathPoints(points, maxPoints = 500)` and pre-extract `PathPoint`s once per `uiState.points`.

### Step 5: Targeted Unit & Architectural Contract Tests
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapPolylineMemoryStabilityTest.kt`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.MapPolylineMemoryStabilityTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Execute `MapPolylineMemoryStabilityTest` during Stage 4 construction, followed by full clean-room regression suite `./gradlew testDebugUnitTest` in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-3690` allows full revert without affecting `sprint/2026-42.6` or `develop`.
