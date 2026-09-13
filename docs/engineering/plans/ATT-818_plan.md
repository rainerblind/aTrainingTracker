# Implementation Plan: Detailed Workout Inspection from Favorite Tracks (ATT-818)

## 1. Context & Root Cause Analysis Summary
When inspecting a workout session from the Favorite Tracks (*Lieblingsstrecken*) screen (`WorkoutClustersFragment`), the user observed:
* Elevation profile was flatlined at dummy bounds `[-10m, +10m]` centered around `0m` with zero elevation gain.
* Extrema pins (Altitude Min/Max, Speed Max, Heart Rate Max) were completely missing from the map.
* Technical track layer selection (GPS, Google Fused, Network) was unavailable.

### Forensic Root Causes
1. **`WorkoutClustersViewModel.selectCluster(cluster)`**:
   `repository.getWorkoutsForCluster(cluster.id)` retrieved the workouts into a local variable, but never assigned them to `_clusterWorkouts.value`. When `WorkoutClustersFragment` triggered `viewModel.selectWorkoutForPeek(workout.id)`, the lookup `_clusterWorkouts.value.find { it.id == id }` failed (returned `null`), leaving `_peekedWorkoutDataWithTrack.value` as `null` and `isDataLoaded` as `false`.
2. **`WorkoutData.toMapTrack()` Zero-Altitude Fallback**:
   Because `isDataLoaded` remained `false`, `WorkoutClustersFragment` fell back to `workout.toMapTrack()`. In `MapModels.kt`, `toMapTrack()` simplified the polyline and hardcoded `PathPoint(0.0, it, 0.0)`, discarding `encodedAltitudes` and `encodedDistances`. `ElevationProfile.calculateElevationBounds` received all `0.0` altitudes, clamping overrides and expanding around `0.0` to produce the `[-10m, +10m]` flatline.
3. **Unused `TrackOnMapAftermathViewModel`**:
   `WorkoutClustersFragment` had `trackOnMapViewModel: TrackOnMapAftermathViewModel by viewModels()` injected, but never called `loadAftermathData(workout)` for `inspectedWorkout`. Consequently, aftermath high-fidelity database tracks, technical tracks (GPS, FUSED, NETWORK), extrema pins, segments, and routes were never loaded.

---

## 2. Impact Analysis (ASPICE SWE.1.BP.5)

### Target Files Slated for Modification
1. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt`
2. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersFragment.kt`
3. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapModels.kt`

### Mapped Requirements Cross-Check
* `REQ-PER-001` (Cluster load performance): Assigning `_clusterWorkouts.value` occurs alongside existing background map processing without blocking UI.
* `REQ-PER-002` (Rapid navigation consistency): `selectionJob?.cancel()` already guards `selectCluster`.
* `REQ-UI-136` (Cluster sorting): Preserved; sorting operates on `allClusters`.
* `REQ-UI-135` (Cluster filtering): Preserved; filter criteria unaffected.
* `REQ-MAP-018` (Heatmap rendering): Preserved; cluster heatmap paths remain untouched.
* `REQ-UI-138` (Full-fidelity workout inspection): DIRECT TARGET of this implementation.

### Potential Side Effects & Mitigations
* **Android System**: Zero background service, WakeLock, or battery impacts. High-fidelity track loading is already asynchronous via `Dispatchers.IO` in `TrackOnMapAftermathViewModel`.
* **Component Interfaces**: `WorkoutData.toMapTrack()` preserves existing return type `MapTrack`. Decoded `altitude` and `distance` enrich `PathPoint` without altering `latLng` coordinates.
* **Data Integrity**: Zero SQLite schema modifications; read-only telemetry decoding.

---

## 3. Proposed Architectural Changes

### Component 1: `WorkoutClustersViewModel.kt`
* In `selectCluster(cluster: WorkoutCluster?)`:
  When `cluster != null`, assign `_clusterWorkouts.value = workouts` immediately after fetching from `repository.getWorkoutsForCluster(cluster.id)`.
  This allows `selectWorkoutForPeek(id)` to find the workout and also benefits the bottom-sheet peek in `WorkoutClusterHeatmapScreen`.

### Component 2: `MapModels.kt`
* In `WorkoutData.toMapTrack()`:
  * Remove destructive polyline simplification that misaligns point counts with altitude/distance streams.
  * Decode `this.mapPolyline` via `PolyUtil.decode()`.
  * Decode `this.encodedAltitudes` via `NumericalEncodingUtils.decodeDoubles()`.
  * Decode `this.encodedDistances` via `NumericalEncodingUtils.decodeDoubles()`.
  * Construct `PathPoint` elements using the decoded altitudes and distances with safe fallback (`getOrElse(index) { 0.0 }`).
  * If `mapPolyline` is empty, return an empty `path`.

### Component 3: `WorkoutClustersFragment.kt`
* In `inspectedWorkout != null`:
  * Collect `aftermathUIState` from `trackOnMapViewModel.uiState.collectAsStateWithLifecycle()`.
  * Collect `enabledTrackTypes` from `trackOnMapViewModel.enabledTrackTypes.collectAsStateWithLifecycle()`.
  * In `LaunchedEffect(workout.id)`, invoke `trackOnMapViewModel.loadAftermathData(workout)`.
  * In `TrackOnMapScreen`:
    * Pass `tracks = aftermathUIState.tracks.ifEmpty { listOf(workout.toMapTrack()) }`.
    * Pass `availableTrackTypes = aftermathUIState.availableTrackTypes`.
    * Pass `segments = aftermathUIState.segments`.
    * Pass `routes = aftermathUIState.routes`.
    * Pass `markers = aftermathUIState.markers`.
    * Pass `enabledTrackTypes = enabledTrackTypes`.
    * Pass `onToggleTrackType = { trackOnMapViewModel.toggleTrackTypeEnabled(it) }`.
    * Set `showTechnicalTracks = true`.
    * Retain `headerActions` with `IconButton` for moving the workout to another cluster.

---

## 4. Verification Plan (TST-UI-091)

### Automated Unit Tests
1. **`MapModelsTest.kt`** (New Unit Test Suite):
   * `toMapTrack_withValidStreams_decodesAltitudesAndDistancesCorrectly()`: Verify `PathPoint` instances contain the expected altitude and distance values.
   * `toMapTrack_withEmptyStreams_fallsBackToZeroGracefully()`: Verify no exceptions when `encodedAltitudes` or `encodedDistances` are empty strings.
   * `toMapTrack_withEmptyPolyline_returnsEmptyPath()`: Verify empty polyline returns empty track.
2. **`WorkoutClustersViewModelTest.kt`** (New Unit Test Suite):
   * Verify `selectCluster()` sets `_clusterWorkouts.value`.
   * Verify `selectWorkoutForPeek(id)` sets `_peekedWorkoutDataWithTrack.value`.

### Regression Verification
* Execute full test suite: `./gradlew testDebugUnitTest`.

### Human Manual Verification
* Deploy debug build, navigate to *Lieblingsstrecken*, open a cluster, tap a workout to inspect:
  * Verify authentic elevation profile curve with correct ascent, min, and max altitude values.
  * Verify extrema pins (Min Altitude, Max Altitude, Max Speed, Max HR) are visible on map.
  * Verify technical track layers menu (GPS, FUSED, NETWORK) is available and functional.
