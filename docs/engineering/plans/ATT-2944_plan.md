# Stage 3 Implementation Plan: ATT-2944 - Isolate tracking map composable from sensor grid telemetry recomposition churn

**Ticket**: [ATT-2944](https://atrainingtracker.atlassian.net/browse/ATT-2944)  
**Sub-task**: [ATT-3001](https://atrainingtracker.atlassian.net/browse/ATT-3001) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2944`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Architectural Design & SWE.2 Boundaries

### 1.1 Dedicated Sub-State Model (`TrackingMapState.kt`)
* **Layer**: UI State Model (`com.atrainingtracker.trainingtracker.ui.tracking.tracking`).
* **Design**:
  - Create `@Immutable data class TrackingMapState` containing strictly map-related rendering state:
    ```kotlin
    @Immutable
    data class TrackingMapState(
        val showMap: Boolean = false,
        val zoomFocus: MapZoomFocus = MapZoomFocus.TRACK_AND_MARKERS,
        val userBearing: Float = 0f,
        val userSpeed: Float = 0f,
        val bSportType: BSportType = BSportType.UNKNOWN,
        val currentTrack: List<LatLng> = emptyList(),
        val mapTracks: List<MapTrack> = emptyList(),
        val mapSegments: List<MapSegment> = emptyList(),
        val activeLiveSegmentIds: Set<Long> = emptySet(),
        val mapRoutes: List<MapRoute> = emptyList(),
        val mapMarkers: List<LocationMarker> = emptyList()
    )
    ```
  - Integrate `val mapState: TrackingMapState = TrackingMapState(showMap = showMap)` into `TrackingScreenState`.
  - Maintain backward-compatible accessors/delegates on `TrackingScreenState` for `mapRoutes`, `mapSegments`, `currentTrack`, etc., ensuring existing call-sites and tests compile seamlessly.

### 1.2 Dedicated Recomposition Barrier (`TrackingMapContainer`) in `SensorGridScreen.kt`
* **Layer**: Jetpack Compose Presentation Layer (`com.atrainingtracker.trainingtracker.ui.tracking.tracking`).
* **Problem**: In `SensorGridScreen.kt:508-525`, `ATrainingTrackerMap` was directly invoked with an un-remembered trailing lambda `{ tracks(...); segments(...); ... }`. Any sensor update (HR, power, cadence, 1-sec timer) forced `SensorGridScreen` to recompose, reallocating the lambda and invalidating `ATrainingTrackerMap`'s internal `scope = remember(content, ...)`.
* **Solution**:
  - Extract the map composition into `TrackingMapContainer`:
    ```kotlin
    @Composable
    fun TrackingMapContainer(
        mapState: TrackingMapState,
        currentLocationFlow: StateFlow<LatLng?>,
        modifier: Modifier = Modifier
    ) {
        if (mapState.showMap) {
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

            ATrainingTrackerMap(
                zoomFocus = mapState.zoomFocus,
                userBearing = mapState.userBearing,
                userSpeed = mapState.userSpeed,
                bSportType = mapState.bSportType,
                currentLocationFlow = currentLocationFlow,
                modifier = modifier,
                content = mapContent
            )
        }
    }
    ```
  - In `SensorGridScreen.kt`, invoke `TrackingMapContainer(mapState = state.mapState, currentLocationFlow = currentLocationFlow, modifier = Modifier.fillMaxWidth().weight(1f))`.
  - Because `TrackingMapContainer` receives an `@Immutable` model and stable flows, the Compose compiler **skips** `TrackingMapContainer` entirely when `SensorGridScreen` recomposes on metric changes.

### 1.3 State Decoupling in `TrackingViewModel.kt`
* **Layer**: Presentation ViewModel (`com.atrainingtracker.trainingtracker.ui.tracking.tracking`).
* **Design**:
  - Decouple map data generation from `banalServiceRepository.allFilteredSensorData`.
  - In `TrackingViewModel`, compute `TrackingMapState` in a dedicated flow or memoize it such that emissions from sensor data (HR, power, cadence, timer ticks) preserve the existing `mapState` instance reference without re-mapping routes (`allRoutes.map { it.toMapRoute(...) }`) or creating new segment lists.
  - Expose `val mapState: StateFlow<TrackingMapState>`.

---

## 2. UI Consistency (Governance Rule 23)

* **Reference Screen**: `SensorGridScreen.kt` (Tracking Cockpit).
* **Reused Components**:
  - `ATrainingTrackerMap` (unchanged API).
  - Existing layouts: weighted `Modifier.weight(1f)`, scrollable sensor grid column, top-level spatial navigation overlays.
* **Visual Parity**: 100% pixel-identical visual presentation. No colors, fonts, shapes, or margins are changed.
* **Justification for New Elements**: None. Purely an internal architectural recomposition barrier to isolate the Google Map from high-frequency metric churn.

---

## 3. Atomic Implementation Steps

### Step 1: Create `TrackingMapState.kt`
* Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingMapState.kt`.
* Define `@Immutable data class TrackingMapState(...)`.

### Step 2: Update `TrackingScreenState` in `TrackingViewModel.kt`
* Add `mapState: TrackingMapState` parameter to `TrackingScreenState`.
* Keep backward-compatible property defaults and delegates.
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt`.

### Step 3: Decouple Map Computation in `TrackingViewModel.kt`
* Maintain and expose `mapState: StateFlow<TrackingMapState>` derived from map-only sources.
* Attach the current `mapState` to `TrackingScreenState` without re-mapping routes on sensor ticks.
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt`.

### Step 4: Extract `TrackingMapContainer` in `SensorGridScreen.kt`
* Implement `TrackingMapContainer` with memoized `mapContent` lambda.
* Replace direct `ATrainingTrackerMap` call in `SensorGridScreen` with `TrackingMapContainer`.
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`.

### Step 5: Author Architectural Contract Tests
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingMapIsolationContractTest.kt`.
* Test `@Immutable` annotation, properties, `TrackingMapContainer` presence, and lambda memoization.
* Add state-reuse unit test in `TrackingViewModelGridTest.kt`.

### Step 6: Targeted Unit Test Verification
* Run:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingMapIsolationContractTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingViewModelGridTest"
  ```

### Step 7: Clean-Room Full Regression Verification
* Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across the full suite.

---

## 4. Invariants & Risk Mitigation

* **100% Backward Compatibility**: `TrackingScreenState` preserves all existing properties, ensuring zero compilation or runtime breakage for existing consumers and previews.
* **No UI Regression**: Map rendering, follow-me camera, user location arrow, and spatial layers remain identical.
* **CPU & Battery Preservation**: Continuous 1 Hz recomposition and lambda churn for Google Maps is eliminated during endurance tracking.
* **100% Test Pass Rate**: Full regression suite must pass with 0 failures.
