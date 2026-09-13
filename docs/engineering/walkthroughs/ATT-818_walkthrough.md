# Walkthrough: Restore Authentic Altitude Profiles and Technical Tracks on Favorite Tracks Detailed View (ATT-818)

* **Parent Ticket**: [ATT-818](https://rainerblind.atlassian.net/browse/ATT-818) (*[Fehler] Altitude values (and more) is missing when we navigate from the Lieblingsstrecken to a detailed workout view*)
* **Sub-Task**: [ATT-823](https://rainerblind.atlassian.net/browse/ATT-823) (*[Implementation] Altitude values (and more) is missing when we navigate from the Lieblingsstrecken to a detailed workout view*)
* **Target Version**: `V4.9.36`
* **Requirement**: `REQ-UI-138` (*Full-Fidelity Track and Sensor Extrema Restoration on Favorite Tracks Inspection*)
* **Test Specification**: `TST-UI-091` (*Favorite Tracks Detailed Inspection Track and Elevation Verification*)
* **Branch**: `bugfix/ATT-818`

---

## 1. Root Cause Summary

When navigating from Favorite Tracks (*Lieblingsstrecken*) into a detailed workout view:
1. **Destructive Simplification & Missing Altitude Streams**: In [`MapModels.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapModels.kt), `WorkoutData.toMapTrack()` previously executed `PolyUtil.simplify()` with a 50-meter tolerance, discarding fine track resolution. Crucially, it populated `altitude = 0.0` for all points, ignoring the database fields `encodedAltitudes` and `encodedDistances`.
2. **Missing Technical Tracks & Aftermath Data**: In [`WorkoutClustersFragment.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersFragment.kt), navigating to workout detail directly passed a synthetic single-track list derived from `toMapTrack()` without invoking `trackOnMapViewModel.loadAftermathData(workout)`. Consequently, GPS/Fused/Network track toggles, segment markers, route overlays, and sensor extrema were missing.
3. **Empty Cluster Workouts Flow**: In [`WorkoutClustersViewModel.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt), `selectCluster(cluster)` queried cluster workouts but omitted updating `_clusterWorkouts.value`, preventing workout lookups for peek selection.

---

## 2. Summary of Changes

### 2.1 High-Fidelity Track & Elevation Decoding in `MapModels.kt`
* Eliminated destructive polyline decimation (`PolyUtil.simplify()`).
* Decoded `workout.encodedAltitudes` and `workout.encodedDistances` using `NumericalEncodingUtils.decodeDoubles()`.
* Mapped decoded altitudes and cumulative distances 1-to-1 with decoded polyline coordinates.
* Wrapped decoding in defensive `try/catch` shielding against malformed/corrupted encoding strings, safely falling back to coordinate-only points (`altitude = 0.0`) without crashing.

### 2.2 Aftermath Data Loading & Technical Tracks in `WorkoutClustersFragment.kt`
* Triggered `trackOnMapViewModel.loadAftermathData(workout)` via `LaunchedEffect(workout.id)` when inspecting a workout from Favorite Tracks.
* Connected `aftermathUIState` (tracks, markers, segments, routes, availableTrackTypes, enabledTrackTypes) to `TrackOnMapScreen`.
* Enabled `showTechnicalTracks = true` to restore technical track layer toggles (GPS, Fused, Network).

### 2.3 State Synchronization in `WorkoutClustersViewModel.kt`
* Updated `_clusterWorkouts.value = workouts` when `cluster != null` in `selectCluster(cluster)`.
* Cleared `_clusterWorkouts.value = emptyList()` when `cluster == null`.

### 2.4 Automated Unit Tests
* [`MapModelsTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapModelsTest.kt):
  * `toMapTrack_decodesEncodedAltitudesAndDistances`: Verifies accurate elevation and distance stream decoding.
  * `toMapTrack_whenEncodedStreamsEmpty_fallsBackToZeroElevation`: Verifies fallback to 0.0 altitude when streams are empty.
  * `toMapTrack_whenMapPolylineEmpty_returnsEmptyPoints`: Verifies empty polyline safety.
  * `toMapTrack_whenStreamLengthsMismatch_handlesDefensivelyWithoutCrashing`: Verifies mismatched stream lengths.
  * `toMapTrack_whenCorruptEncodingStrings_recoversDefensively`: Verifies corrupted string resilience.
* [`WorkoutClustersViewModelTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModelTest.kt):
  * `selectCluster_whenClusterSelected_updatesClusterWorkoutsState`: Verifies cluster workouts state propagation.
  * `selectWorkoutForPeek_afterSelectCluster_resolvesWorkoutAndLoadsTrackAndMarkers`: Verifies peek selection, track points, and marker resolution.
  * `selectCluster_whenNull_clearsClusterWorkoutsAndPeekSelection`: Verifies state reset when cluster is deselected.

---

## 3. Verification & Test Evidence

### 3.1 Automated Unit Tests
Executed dedicated test suite:
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClustersViewModelTest" --tests "com.atrainingtracker.trainingtracker.ui.map.MapModelsTest"
```

**Results**:
* `MapModelsTest`: **5/5 PASSED**
* `WorkoutClustersViewModelTest`: **3/3 PASSED**
* Full compilation & test execution: **BUILD SUCCESSFUL in 32s**
