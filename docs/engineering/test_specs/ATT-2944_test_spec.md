# Stage 2 Requirement & Test Specification: ATT-2944 - Isolate tracking map composable from sensor grid telemetry recomposition churn

**Ticket**: [ATT-2944](https://atrainingtracker.atlassian.net/browse/ATT-2944)  
**Sub-task**: [ATT-3000](https://atrainingtracker.atlassian.net/browse/ATT-3000) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2944`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-UI-326)

### 1.1 Requirement Definition
* **Requirement ID**: `REQ-UI-326`
* **Title**: Tracking Map Recomposition Boundary Isolation and Telemetry Decoupling
* **Type**: Non-Functional Performance & Architectural UI Specification
* **Target Release**: `V4.9.40`
* **Status**: Specified
* **Amends/Complements**: Complements `REQ-UI-275` (*Unified WYSIWYG Tracking Tab Configuration*, ATT-2360) and `REQ-UI-295` (*Unified Scrollable Container Architecture*, ATT-2620)
* **Parent Ticket**: ATT-2944

### 1.2 Description
The system shall isolate the live Google Map composable in `SensorGridScreen.kt` from high-frequency sensor telemetry recomposition churn by establishing an `@Immutable` sub-state model, extracting a dedicated `TrackingMapContainer` recomposition barrier with memoized layer DSL content, and decoupling map data computation from volatile sensor telemetry in `TrackingViewModel.kt`:
1. *Sub-State Model Isolation (`TrackingMapState`)*:
   - The system shall define `@Immutable data class TrackingMapState` in `com.atrainingtracker.trainingtracker.ui.tracking.tracking` encapsulating all map-specific rendering state:
     `showMap: Boolean = false`, `zoomFocus: MapZoomFocus = MapZoomFocus.TRACK_AND_MARKERS`, `userBearing: Float = 0f`, `userSpeed: Float = 0f`, `bSportType: BSportType = BSportType.UNKNOWN`, `currentTrack: List<LatLng> = emptyList()`, `mapTracks: List<MapTrack> = emptyList()`, `mapSegments: List<MapSegment> = emptyList()`, `activeLiveSegmentIds: Set<Long> = emptySet()`, `mapRoutes: List<MapRoute> = emptyList()`, `mapMarkers: List<LocationMarker> = emptyList()`.
   - `TrackingScreenState` shall incorporate `val mapState: TrackingMapState = TrackingMapState()` and expose backward-compatible accessors for all existing map properties.
2. *Dedicated Recomposition Barrier (`TrackingMapContainer`)*:
   - The system shall declare `@Composable fun TrackingMapContainer(mapState: TrackingMapState, currentLocationFlow: StateFlow<LatLng?>, modifier: Modifier = Modifier)` in `SensorGridScreen.kt`.
   - Inside `TrackingMapContainer`, the `MapContentScope.() -> Unit` lambda supplied to `ATrainingTrackerMap` shall be memoized using `remember(mapState.mapTracks, mapState.mapSegments, mapState.activeLiveSegmentIds, mapState.mapRoutes, mapState.mapMarkers, mapState.currentTrack)`.
   - `SensorGridScreen.kt` shall delegate map composition exclusively to `TrackingMapContainer`.
   - When `SensorGridScreen` recomposes solely due to high-frequency sensor metric updates (`state.fields`), the Compose compiler shall skip `TrackingMapContainer`, eliminating all map DSL re-allocations and bounds calculations.
3. *ViewModel State Decoupling (`TrackingViewModel.kt`)*:
   - `TrackingViewModel` shall decouple map state generation from `allFilteredSensorData`.
   - Telemetry emissions (Heart Rate, Cycling Power, Cadence, Speed, 1 Hz timer ticks) shall reuse the existing `TrackingMapState` instance, avoiding redundant route mapping (`allRoutes.map { it.toMapRoute(...) }`) and list allocations.
4. *Preservation of System Invariants*:
   - User location arrow streaming via `currentLocationFlow` and recorded live track updates via `currentTrack` must remain responsive.
   - Dynamic map visibility (`showMap`), weighted expansion (`Modifier.weight(1f)` in tracking/preview mode), and configuration mode toggle cards (`REQ-UI-295`) must remain strictly preserved.
   - 100% full-suite unit test pass rate across all tracking and map test suites.

### 1.3 Acceptance Criteria (Given-When-Then)

#### Scenario 1: Compose Skipping During High-Frequency Telemetry Updates
* **Given** active workout tracking on `SensorGridScreen`,
* **When** sensor telemetry updates (e.g. Heart Rate changes, 1 Hz duration timer tick),
* **Then** `SensorGridScreen` recomposes to update sensor metric tiles,
* **And** `TrackingMapContainer` is skipped by the Compose runtime,
* **And** zero layer lambda allocations or bounds recalculations occur.

#### Scenario 2: Responsive Map Updates on Real Map Events
* **Given** active workout tracking on `SensorGridScreen`,
* **When** an active route is selected/deselected, a map layer is toggled, or a new track point is recorded,
* **Then** `TrackingMapState` updates,
* **And** `TrackingMapContainer` recomposes smoothly with the updated map content.

#### Scenario 3: Continuous Location Streaming & Follow-Me Camera
* **Given** active workout tracking with follow-me mode enabled,
* **When** GPS location fixes arrive on `currentLocationFlow`,
* **Then** the user location arrow and follow-me camera smoothly follow the athlete without stutter or frame drops.

---

## 2. Test Specification (TST-UI-286)

### 2.1 Test Definition
* **Test ID**: `TST-UI-286`
* **Title**: Tracking Map Recomposition Boundary and State Decoupling Verification
* **Target Requirement**: `REQ-UI-326`
* **Test Type**: Architectural Contract & Unit Tests (`TrackingMapIsolationContractTest.kt`, `TrackingViewModelGridTest.kt`)
* **Status**: Specified

### 2.2 Test Cases

#### Case 1: `testTrackingMapState_isImmutableAndEncapsulatesMapProperties` (`TrackingMapIsolationContractTest.kt`)
* **Verification**:
  - Assert that `TrackingMapState` is defined and annotated with `@Immutable`.
  - Assert that `TrackingMapState` contains all 11 map-specific rendering properties.
  - Assert that `TrackingScreenState` includes `val mapState: TrackingMapState`.

#### Case 2: `testSensorGridScreen_definesTrackingMapContainerWithMemoizedContent` (`TrackingMapIsolationContractTest.kt`)
* **Verification**:
  - Assert that `SensorGridScreen.kt` defines `fun TrackingMapContainer(`.
  - Assert that `TrackingMapContainer` memoizes the `MapContentScope` lambda with `remember(`.
  - Assert that `SensorGridScreen` does NOT compose `ATrainingTrackerMap` directly with an un-remembered trailing lambda.

#### Case 3: `testTrackingViewModel_reusesMapStateInstanceOnSensorUpdates` (`TrackingViewModelGridTest.kt`)
* **Verification**:
  - Instantiate `TrackingViewModel`.
  - Emit an initial state with mock routes. Capture `initialMapState = viewModel.uiState.value.mapState`.
  - Emit a sensor telemetry update (e.g. updated heart rate sample in `allFilteredSensorData`).
  - Assert that `viewModel.uiState.value.mapState` is identical / equal to `initialMapState` without re-allocating new route or segment collections.

#### Case 4: Clean-Room Full Regression Suite
* **Verification**:
  - Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across the full suite.

---

## 3. Traceability Matrix

| Requirement ID | Test Case ID | Test Class | Verification Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-326` | `TST-UI-286` | `TrackingMapIsolationContractTest` | Map isolation composable & @Immutable state | Specified |
| `REQ-UI-326` | `TST-UI-286` | `TrackingViewModelGridTest` | Recomposition avoidance & state reuse | Specified |
| `REQ-UI-275` | `TST-UI-235` | `TrackingTabWysiwygContractTest` | WYSIWYG cockpit configuration & per-tab toggles | Verified |
| `REQ-UI-295` | `TST-UI-255` | `TrackingTabWysiwygContractTest` | Full-width spatial toggles & scrollable container | Verified |
