# Walkthrough - ATT-498: Favorite routes: max line distance marker is not at the correct position

## Problem & Background
In the Favorite Routes (Workout Cluster) view (`WorkoutClusterHeatmapScreen`), the cluster fingerprint Max Line Distance (Apex) marker (`ic_distance`) was placed in Dachswald, ~400m east of the route "Run to work (Uni)" and completely off the physical track network.
Individual recorded member workouts correctly located their apex at Campus Vaihingen (the true end of the run), but the cluster fingerprint record itself was displaced because:
1. `WorkoutClusterEngine` computed apex coordinates via an unconstrained arithmetic running average, causing the centroid on curved tracks to drift into the interior of the curve (eastward into Dachswald).
2. In `WorkoutClusterEngine.onWorkoutFinished()`, lines 169 & 194 incorrectly passed `w.startLatLng` as the apex parameter instead of `w.maxDisplacementLatLng`.
3. In `SpeedAndLocationDevice.java`, `mStartLocation` was not reset upon accumulator reset, allowing locations from prior sessions to distort subsequent displacement readings.

## Changes Implemented

### 1. Speed & Location Baseline Reset
- In [MyDevice.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/MyDevice.java):
  - Added `onAccumulatorsReset()` lifecycle hook invoked when `RESET_ACCUMULATORS_INTENT` is received.
- In [SpeedAndLocationDevice.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice.java):
  - Overrode `onAccumulatorsReset()` to invoke `resetStartLocation()`, clearing `mStartLocation = null` and `mPrevLocation = null`.

### 2. Geometric Apex Resolution & Parameter Correction
- In [WorkoutClusterEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt):
  - Implemented `findApexFromPoints(start, points)` to find the coordinate along the actual route/track points maximizing geodesic distance to `start`.
  - Added graceful Haversine fallback in `distanceBetween` for standalone JVM testing environments.
  - Corrected `onWorkoutFinished()` to pass `w.maxDisplacementLatLng ?: w.endLatLng ?: w.startLatLng` as the apex parameter to `suggestCluster()` and `learnFromWorkout()`.
  - Updated `learnFromRoute()` so that an authoritative linked route anchors the cluster apex to the true maximum point on the route.

### 3. Self-Healing Apex Synchronization in ViewModel
- In [WorkoutClustersViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt):
  - In `selectCluster()`, check candidate points from the linked route or member heatmap polylines. If the cluster's stored apex has drifted off-route (> 100m) or is unset, automatically recalculate the true apex along the track and update the cluster fingerprint in the database and state.

### 4. Verification & Testing
- Created unit test suite [WorkoutClusterApexTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterApexTest.kt):
  - Validated C-shaped route topology (Rohr -> Rohrer Höhe -> Rosental -> Lauchäcker -> Nobelstraße -> Campus Vaihingen). Confirmed `campusVaihingen` is selected as the apex, rejecting Dachswald and ensuring the coordinate exists on the track.
  - Validated out-and-back route turnaround selection.
  - Validated single-point / empty list fallback.
- Ran `./gradlew testDebugUnitTest`: All 32 tasks passed (`BUILD SUCCESSFUL in 10s`).
- Marked `REQ-SET-063` in [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md) as **Verified**.
- Marked `TST-SET-049` in [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md) as **Verified**.
