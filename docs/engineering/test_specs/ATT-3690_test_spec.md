# Stage 2: Requirement & Test Specification - ATT-3690: OutOfMemoryError in Google Maps Polyline.setPoints (m140.dsa.b) due to 1Hz MapRoute/MapSegment reallocation and unthrottled polyline updates

**Ticket**: [ATT-3690](https://rainerblind.atlassian.net/browse/ATT-3690)  
**Sub-task**: [ATT-3781](https://rainerblind.atlassian.net/browse/ATT-3781) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.6`  
**Requirement Mapping**: `REQ-MAP-051` (*Referentially Stable Map Polyline Allocation, Static Layer Flow Decoupling, Keyed Layer Composition & Preview Downsampling*)  
**Test Spec ID**: `TST-MAP-053`  
**Branch**: `feature/ATT-3690`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Requirement Specification (REQ-MAP-051)

### 1.1 Problem Statement & Rationale
In production release `V4.9.38.4 (267)`, a fatal `java.lang.OutOfMemoryError` exhausted the 512 MB JVM heap inside Google Maps SDK's polyline tessellation engine (`com.google.maps.api.android.lib6.phoenix.ee` / `m140.dsa.b`) called via `Polyline.setPoints`. Four compounding allocation loops flooded `Polyline.setPoints` and `Polyline.setPattern`:
1. `TrackingViewModel.observeMapState()` combined 1-Hz `currentTrack` and 1-Hz `liveSegments` emissions directly with static `allRoutes` and `allLiveSegments` mapping, reallocating all `MapRoute` and `MapSegment` wrappers and their `by lazy { path.map { it.latLng } }` lists every second.
2. `MapTrack.pattern`, `MapSegment.pattern`, `MapRoute.pattern`, and `LiveTrackLayer` allocated fresh `List<PatternItem>` instances on every property read/recomposition.
3. `ATrainingTrackerMap` included `content` in `remember(content, ...)` keys for `MapContentScopeImpl`, recreating `MapContentScopeImpl` every second, while `MapContentScope.Render` omitted `key(data.path.id)` around `MappablePathLayer` calls.
4. `CropWorkoutScreen` sliced and re-mapped thousands of `PathPoint` and `LatLng` instances on every 60–120 Hz `RangeSlider` drag frame without downsampling.

### 1.2 Functional & Architectural Requirements
1. **Decoupled Static Map Layer Flows (`REQ-MAP-051.1`)**:
   - The system SHALL decouple static segment and route polyline layer streams from 1-Hz live GPS track and live telemetry updates so that static route and segment map models are only recomputed when underlying static segment paths, stored routes, or active navigation targets change.
   - Live athlete speed and bearing streams SHALL remain reactively subscribed for camera state computation in compliance with Rule 39 (`docs/coding_standards.md` Sec 1.5).
2. **Referential Memoization of Coordinate Lists and Stroke Patterns (`REQ-MAP-051.2`)**:
   - The system SHALL precompute and share immutable `List<LatLng>` coordinate lists across domain path entities and map presentation models so that coordinate list references remain referentially identical (`===`) across wrapper updates and recompositions.
   - The system SHALL reuse immutable singleton `List<PatternItem>` stroke pattern definitions for dashed and dotted polylines instead of allocating new pattern lists on each property access or recomposition.
3. **Stable Map Content Scope & Keyed Layer Rendering (`REQ-MAP-051.3`)**:
   - The map container SHALL preserve its content orchestration scope across content lambda updates without discarding and recreating internal state containers on 1-Hz live track emissions.
   - The map rendering loop SHALL key each rendered path layer by its stable path identifier (`id`) and avoid mutating state lists when path collections are unchanged.
4. **Bounded Preview Downsampling in Workout Cropping (`REQ-MAP-051.4`)**:
   - The workout crop preview map SHALL pre-extract valid path coordinates once per loaded workout and downsample the active preview slice to at most 500 points (strictly preserving the exact start and end coordinates of the cropped range) so that interactive slider adjustments operate in bounded memory without flooding `Polyline.setPoints`.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
* **Original Requirement ID & Target**: `REQ-MAP-051` (Net-new requirement complementing `REQ-UI-326` *TrackingMapContainer Recomposition Barrier* and `REQ-MAP-046` *Unified Viewport Anchor Authority*). Targets `TrackingViewModel.kt`, `MapModels.kt`, `MapLayers.kt`, `MapContentScope.kt`, `ATrainingTrackerMap.kt`, and `CropWorkoutScreen.kt`.
* **Historical Origin & Commit Trace**: `ATT-2944` (`REQ-UI-326`, Sprint 2026-41.6) isolated `TrackingMapContainer` from sensor grid updates; `ATT-3274` (Sprint 2026-42.5) introduced `CropWorkoutScreen` with interactive `RangeSlider` previewing on `PathPreviewMap`.
* **Root Reason for Existing Formulation**: `MapRoute`, `MapSegment`, and `MapTrack` originally declared `override val latLngs: List<LatLng> by lazy { path.map { it.latLng } }` inside their class bodies under the assumption that wrapper instances were long-lived, while `PatternItem` lists were written as inline getters (`get() = listOf(...)`) for brevity. However, `TrackingViewModel.observeMapState()` combined 1-Hz `currentTrack` and `liveSegments` inside the same lambda that instantiated `MapSegment` and `MapRoute`, defeating the `by lazy` delegate every second.
* **Preservation of Core Invariants**: Moving `latLngs` to constructor-backed shared references, hoisting `PatternItem` lists into immutable constants, keying `MappablePathLayer` by `data.path.id`, and capping the 200dp `liteMode` crop preview path to 500 points preserves 100% of existing visual styling, dash patterns, live segment activation, and crop index precision while eliminating unbounded heap allocations in `lib6.phoenix`.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Referential Identity of `latLngs` & `pattern`)**:
  * *Given* a `RouteWithPath`, `SegmentWithPath`, `MapRoute`, `MapSegment`, or `MapTrack` instance,
  * *When* `toMapRoute(...)` is invoked or `.pattern` is read multiple times,
  * *Then* the returned `latLngs` and `pattern` references are referentially identical (`===`) with zero transient list allocations.
* **Criterion 2 (Static Map Layer Stability Across 1-Hz Ticks)**:
  * *Given* `TrackingViewModel` observing live `currentTrack` and `liveSegments` updates at 1 Hz while static segment paths and routes remain unchanged,
  * *When* 1-Hz GPS track or live segment telemetry ticks occur,
  * *Then* `mapState.mapRoutes` and `mapState.mapSegments` retain referentially identical `latLngs` lists without reallocating static `MapRoute` or `MapSegment` wrappers.
* **Criterion 3 (Bounded Preview Downsampling in Workout Cropping)**:
  * *Given* a recorded workout with $10,000$ GPS points in the workout crop screen,
  * *When* the preview path is evaluated for any slider range `[startIndex, endIndex]`,
  * *Then* the returned preview path contains at most $500$ points and preserves the exact first and last `PathPoint` of the retained slice.

### 1.5 System Invariants
* Zero visual change to map polylines, dash patterns, or route/segment colors.
* 100% pass rate on `./gradlew testDebugUnitTest`.

---

## 2. Test Specification (TST-MAP-053)

### Test Case 1: `MapPolylineMemoryStabilityTest` (`TST-MAP-053.1`)
* **Scope**: Unit & Architectural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapPolylineMemoryStabilityTest.kt`
* **Preconditions**: Sample `PathPoint` lists, `RouteWithPath`, `SegmentWithPath`, `MapTrack`, `MapSegment`, `MapRoute`, and source files `TrackingViewModel.kt`, `MapContentScope.kt`, `ATrainingTrackerMap.kt`, `CropWorkoutScreen.kt`.
* **Action & Expected Result**:
  1. Assert `MapTrack(type = TrackType.BEST).pattern === MapTrack(type = TrackType.BEST).pattern`, `MapSegment(isDashed = true).pattern === MapSegment(isDashed = true).pattern`, and `MapRoute(isActiveNavigation = false).pattern === MapRoute(isActiveNavigation = false).pattern`.
  2. Assert `routeWithPath.toMapRoute(false).latLngs === routeWithPath.toMapRoute(true).latLngs` and `segmentWithPath.latLngs` referential identity.
  3. Assert preview downsampling caps a $10,000$-point list to $\le 500$ points while preserving exact first and last points, and returns lists $\le 500$ points unchanged.
  4. Verify via source contract assertions that `MapContentScope.kt` wraps `MappablePathLayer` calls in `key(data.path.id)`, `ATrainingTrackerMap.kt` does not key `MapContentScopeImpl` on `content`, and `TrackingViewModel.kt` decouples `staticMapSegmentsFlow` and `mapRoutesFlow` from `currentTrack`.

### Test Case 2: 9-Language Localization & Specifier Audit (`TST-MAP-053.2`)
* **Scope**: Localization Parity Test
* **Goal**: Verify zero regressions in `TranslationParityTest` across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 3: Clean-Room Regression Suite (`TST-MAP-053.3`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MAP-053.1` | Unit / Contract | `MapPolylineMemoryStabilityTest` | `REQ-MAP-051` | Specified |
| `TST-MAP-053.2` | Localization | `TranslationParityTest` | `REQ-MAP-051`, `REQ-UI-106` | Specified |
| `TST-MAP-053.3` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
