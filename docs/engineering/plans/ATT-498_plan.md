# Implementation Plan - ATT-498: Favorite routes: max line distance marker is not at the correct position

## 1. Problem Summary & Motivation
In the Favorite Routes (Workout Cluster) view (`WorkoutClusterHeatmapScreen`), the cluster fingerprint Max Line Distance (Apex) marker (`ic_distance`) is displayed at Dachswald for the route "Run to work (Uni)". Dachswald is ~400m east of the route and completely off the physical road/trail network. The Start marker is at Steigstraße in Rohr and the End marker is at Campus Vaihingen. The individual member workouts (50 recordings) correctly placed their individual apex markers at Campus Vaihingen.
The defect occurred because:
1. `WorkoutClusterEngine` updated apex coordinates using an unconstrained arithmetic running average (`(lat1 + lat2) / 2`). For curved paths, this centroid naturally drifts into the interior of the curve (off-track into Dachswald).
2. In `WorkoutClusterEngine.onWorkoutFinished()`, lines 169 & 194 incorrectly passed `w.startLatLng` as the apex parameter instead of `w.maxDisplacementLatLng`.
3. In `SpeedAndLocationDevice.java`, `mStartLocation` was never reset upon accumulator reset, allowing prior sessions to bias displacement measurements.
4. Cluster signatures lacked geometric validation/re-anchoring against the physical route/path geometry.

## 2. Requirements & Verification Traceability
- **Requirement**: `REQ-SET-063` (*Geometric Route Cluster Apex Integrity & Live-Session Tracking Synchronization*) in `docs/requirements.md`.
- **Test Case**: `TST-SET-049` (*Route Cluster Apex Placement & Live Tracking Apex Synchronization*) in `docs/tests.md`.

## 3. Proposed Changes

### Component 1: `com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice`
- **[MODIFY] `SpeedAndLocationDevice.java`**:
  - In `SpeedAndLocationDevice`, listen for accumulator reset / lap reset or provide a clean reset for `mStartLocation` and `mPrevLocation`.
  - When `BANALService.RESET_ACCUMULATORS_INTENT` is received (or `reset()` is called), clear `mStartLocation = null` and `mPrevLocation = null` so that subsequent workouts start with a clean spatial baseline.

### Component 2: `com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine`
- **[MODIFY] `WorkoutClusterEngine.kt`**:
  - In `onWorkoutFinished(context, w)`:
    - Replace `suggestCluster(w.startLatLng, w.endLatLng, w.startLatLng, ...)` with `suggestCluster(w.startLatLng, w.endLatLng, w.maxDisplacementLatLng ?: w.endLatLng ?: w.startLatLng, ...)`.
    - Replace `learnFromWorkout(w.startLatLng, w.endLatLng, w.startLatLng, ...)` with `learnFromWorkout(w.startLatLng, w.endLatLng, w.maxDisplacementLatLng ?: w.endLatLng ?: w.startLatLng, ...)`.
  - Add helper function:
    ```kotlin
    fun findApexFromPoints(start: LatLng, points: List<LatLng>): LatLng
    ```
    Iterates through route coordinates and returns the exact coordinate maximizing geodesic distance to `start`.
  - In `learnFromRoute(route)`:
    - If learning into an existing cluster, ensure the authoritative route's apex updates the cluster's apex rather than being diluted by prior drifted running averages.

### Component 3: `com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClustersViewModel`
- **[MODIFY] `WorkoutClustersViewModel.kt`**:
  - In `selectCluster(cluster)`:
    - When `linkedRoute` or `workouts` are retrieved, evaluate whether the cluster's stored apex (`cluster.maxDispLat, cluster.maxDispLng`) is off-route or diverges significantly (> 100m) from the true apex calculated along the route/member tracks.
    - If so, update the cluster fingerprint atomically via `updateClusterFingerprint` so the marker immediately snaps onto the physical route at the true max distance point (Campus Vaihingen).

### Component 4: Unit Test Suite
- **[NEW] `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterApexTest.kt`**:
  - Test apex calculation along curved route points: verifies that the apex for a C-shaped route starting at Rohr and ending at Campus Vaihingen selects Campus Vaihingen and does not pick Dachswald or an off-track point.
  - Test `onWorkoutFinished` parameter propagation: verifies `maxDisplacementLatLng` is passed as the apex.
  - Test self-healing apex re-anchoring when a cluster has a linked route or member polylines.

## 4. Verification Plan
- Execute `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.database.WorkoutClusterApexTest`
- Execute full test suite: `./gradlew testDebugUnitTest`
- Verify documentation consistency in `docs/requirements.md` and `docs/tests.md`.
