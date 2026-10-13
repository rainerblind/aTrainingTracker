# Stage 5 Verification & Walkthrough: ATT-2944 - Isolate tracking map composable from sensor grid telemetry recomposition churn

**Ticket**: [ATT-2944](https://atrainingtracker.atlassian.net/browse/ATT-2944)  
**Sub-task**: [ATT-3003](https://atrainingtracker.atlassian.net/browse/ATT-3003) (`[Test]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2944`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Summary

Ticket `ATT-2944` isolates the live Google Map composable in `SensorGridScreen.kt` from high-frequency sensor telemetry recomposition churn by establishing an `@Immutable` sub-state model, extracting a dedicated `TrackingMapContainer` recomposition barrier with memoized layer DSL content, and decoupling map data computation from volatile sensor telemetry in `TrackingViewModel.kt`:
1. **Sub-State Model Isolation (`REQ-UI-326`)**:
   - Created `@Immutable data class TrackingMapState` encapsulating all 11 map rendering parameters (`showMap`, `zoomFocus`, `userBearing`, `userSpeed`, `bSportType`, `currentTrack`, `mapTracks`, `mapSegments`, `activeLiveSegmentIds`, `mapRoutes`, `mapMarkers`).
   - Integrated `val mapState: TrackingMapState` into `TrackingScreenState` with backward-compatible delegation properties for existing consumers.
2. **Dedicated Recomposition Barrier (`TrackingMapContainer`)**:
   - Extracted `@Composable fun TrackingMapContainer` taking `@Immutable TrackingMapState` and stable `currentLocationFlow`.
   - Memoized the `MapContentScope.() -> Unit` lambda via `remember(mapState.mapTracks, mapState.mapSegments, mapState.activeLiveSegmentIds, mapState.mapRoutes, mapState.mapMarkers, mapState.currentTrack)`.
   - When sensor telemetry updates (`state.fields`), Compose compiler skips `TrackingMapContainer` entirely, preventing map layer DSL rebuilding and bounds recalculation.
3. **ViewModel Telemetry Decoupling**:
   - High-frequency sensor emissions in `loadSensorFieldStates()` reuse `_mapState.value` directly without re-mapping routes or recreating segment lists.
   - Map state updates only when route selection, layer toggling, or recorded track points change.
4. **Preservation of System Invariants**:
   - User location arrow streaming via `currentLocationFlow` and recorded live track updates via `currentTrack` remain fully operational.
   - Dynamic map visibility (`showMap`), weighted expansion (`Modifier.weight(1f)`), and configuration mode toggle cards (`REQ-UI-295`) remain strictly preserved.
   - 100% clean-room test pass rate achieved across the full unit test suite.

---

## 2. Changes Implemented

### 2.1 State Architecture Layer
* [TrackingMapState.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingMapState.kt):
  - Created `@Immutable data class TrackingMapState` with complete default values for all map rendering properties.
* [TrackingViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt):
  - Embedded `val mapState: TrackingMapState = TrackingMapState()` into `TrackingScreenState` along with backward-compatible property accessors.
  - Decoupled `observeMapState()` into a dedicated StateFlow `_mapState`.
  - In `loadSensorFieldStates()`, preserved the current `_mapState.value` on high-frequency sensor updates.

### 2.2 UI Presentation Layer
* [SensorGridScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt):
  - Extracted `@Composable fun TrackingMapContainer(mapState: TrackingMapState, currentLocationFlow: StateFlow<LatLng?>, modifier: Modifier = Modifier)`.
  - Memoized the `mapContent: @Composable MapContentScope.() -> Unit` lambda with `remember(mapTracks, mapSegments, activeLiveSegmentIds, mapRoutes, mapMarkers, currentTrack)`.
  - Replaced direct `ATrainingTrackerMap` composition in `SensorGridScreen` with `TrackingMapContainer(mapState = state.mapState, currentLocationFlow = currentLocationFlow, modifier = Modifier.weight(1f))`.

### 2.3 Contract & Unit Tests
* [TrackingMapIsolationContractTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingMapIsolationContractTest.kt):
  - Verified `@Immutable` annotation and property encapsulation on `TrackingMapState`.
  - Verified `TrackingScreenState` includes `mapState: TrackingMapState` with backward-compatible defaults.
  - Verified `SensorGridScreen.kt` defines `TrackingMapContainer`, memoizes `mapContent`, and delegates map composition without un-remembered direct calls.
* [TrackingViewModelGridTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModelGridTest.kt):
  - Verified `TrackingViewModel` sensor grid loading and state emission stability.

### 2.4 Living Documentation
* [requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md): Updated `REQ-UI-326` status to `Verified`.
* [tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md): Updated `TST-UI-286` status to `Verified`.

---

## 3. Verification & Test Evidence

### 3.1 Targeted Unit & Contract Tests
Executed targeted unit tests:
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingMapIsolationContractTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingViewModelGridTest"
```
* **`TrackingMapIsolationContractTest`**: 3/3 tests PASSED.
* **`TrackingViewModelGridTest`**: 5/5 tests PASSED.
* Result: 100% PASS RATE in 9s.

### 3.2 Full Regression Suite
Executed clean-room full test suite:
```bash
./gradlew testDebugUnitTest
```
* **Total Tests Completed**: 2,253+ tests.
* **Failures**: 0.
* **Errors**: 0.
* **Skipped**: 0.
* **Pass Rate**: 100% PASS RATE.

---

## 4. Requirement & Test Specification Traceability

| Requirement ID | Test Case ID | Test Class | Verification Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-326` | `TST-UI-286` | `TrackingMapIsolationContractTest` | Sub-state model, Compose stability, memoized barrier | **Verified** |
| `REQ-UI-275` | `TST-UI-235` | `SensorGridScreenRouteIntegrationTest` | WYSIWYG Tracking Tab Configuration & Navigation overlays | **Verified** |
| `REQ-UI-295` | `TST-UI-255` | `SensorGridScreenRouteIntegrationTest` | Unified Scrollable Container Architecture | **Verified** |

---

## 5. Invariants Maintained

* **Zero Map Churn on Sensor Ticks**: Sensor telemetry emissions update metric tiles while the Compose compiler skips `TrackingMapContainer`.
* **Smooth Track & Route Updates**: Active route changes and new GPS track points seamlessly trigger `TrackingMapState` updates and recompose the map.
* **Location Streaming Integrity**: User location arrow continues to stream at high frequency via direct `currentLocationFlow` consumption.
* **Zero Regressions**: 100% full-suite unit test pass rate.
