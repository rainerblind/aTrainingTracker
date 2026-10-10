# Stage 4 Implementation: ATT-2944 - Isolate tracking map composable from sensor grid telemetry recomposition churn

**Ticket**: [ATT-2944](https://atrainingtracker.atlassian.net/browse/ATT-2944)  
**Sub-task**: [ATT-3002](https://atrainingtracker.atlassian.net/browse/ATT-3002) (`[Implementation]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2944`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Summary of Code Changes

1. **`TrackingMapState.kt` (`com.atrainingtracker.trainingtracker.ui.tracking.tracking`)**:
   - Created `@Immutable data class TrackingMapState` encapsulating all 11 map-specific rendering parameters:
     `showMap`, `zoomFocus`, `userBearing`, `userSpeed`, `bSportType`, `currentTrack`, `mapTracks`, `mapSegments`, `activeLiveSegmentIds`, `mapRoutes`, `mapMarkers`.

2. **`TrackingViewModel.kt` (`com.atrainingtracker.trainingtracker.ui.tracking.tracking`)**:
   - Updated `TrackingScreenState` to incorporate `val mapState: TrackingMapState = TrackingMapState(showMap = showMap)`.
   - Preserved backward-compatible property defaults and delegates (`zoomFocus`, `userBearing`, `userSpeed`, `bSportType`, `currentTrack`, `mapTracks`, `mapSegments`, `activeLiveSegmentIds`, `mapRoutes`, `mapMarkers`).
   - Exposed `val mapState: StateFlow<TrackingMapState> = _mapState.asStateFlow()`.
   - Decoupled map state generation into dedicated `observeMapState()` flow driven solely by map inputs (tracking view info, live segments, routes, recorded track, sport type).
   - In `loadSensorFieldStates()`, high-frequency emissions from `allFilteredSensorData` (HR, power, cadence, 1 Hz timer tick) reuse `_mapState.value` directly without re-mapping routes or recreating segment lists.

3. **`SensorGridScreen.kt` (`com.atrainingtracker.trainingtracker.ui.tracking.tracking`)**:
   - Implemented `@Composable fun TrackingMapContainer(mapState: TrackingMapState, currentLocationFlow: StateFlow<LatLng?>, modifier: Modifier = Modifier)`.
   - Inside `TrackingMapContainer`, memoized the `MapContentScope.() -> Unit` lambda using `remember(mapState.mapTracks, mapState.mapSegments, mapState.activeLiveSegmentIds, mapState.mapRoutes, mapState.mapMarkers, mapState.currentTrack)`.
   - In `SensorGridScreen`, replaced direct `ATrainingTrackerMap` composition with `TrackingMapContainer(mapState = state.mapState, currentLocationFlow = currentLocationFlow, modifier = Modifier.fillMaxWidth().weight(1f))`.
   - Because `mapState` is `@Immutable` and `oldMapState == newMapState`, the Compose compiler skips `TrackingMapContainer` during sensor metric recompositions.

4. **`TrackingMapIsolationContractTest.kt` (`com.atrainingtracker.trainingtracker.ui.tracking.tracking`)**:
   - Added architectural contract tests verifying `@Immutable` annotation, property encapsulation, backward compatibility in `TrackingScreenState`, and memoized lambda delegation in `SensorGridScreen.kt`.

---

## 2. Targeted Verification Results

Executed targeted unit tests:
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingMapIsolationContractTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingViewModelGridTest"
```
* **`TrackingMapIsolationContractTest`**: 3/3 tests PASSED (`TST-UI-286`).
* **`TrackingViewModelGridTest`**: 5/5 tests PASSED.
* Result: BUILD SUCCESSFUL in 9s (0 failures, 100% pass rate).
