# Implementation Plan: Altitude Extrema-Aware 3D Route Clustering & Database Migration (ATT-502)

* **Parent Ticket**: [ATT-502](https://rainerblind.atlassian.net/browse/ATT-502) ([Verbesserung] Clustering: optionally, take min and max altitude pos into account)
* **Sub-Task**: [ATT-787](https://rainerblind.atlassian.net/browse/ATT-787) ([Impl-Plan] Clustering: optionally, take min and max altitude pos into account)
* **Target Lösungsversion (Fix Version/s)**: `V4.9.36`
* **Requirement**: `REQ-SET-065` (*Altitude Extrema-Aware 3D Route Clustering & Database Migration*)
* **Test Specifications**: `TST-SET-052` (*Altitude Extrema-Aware Cluster Matching, Migration & Fingerprint Relocation*), `TST-UNT-015` (*Automated Unit Tests*)
* **Branch**: `feature/ATT-502`

---

## 1. Executive Summary & Topographical Motivation

### 1.1 Problem Statement
Currently, [`WorkoutClusterEngine.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt) matches incoming routes against existing clusters using a 4-dimensional spatial fingerprint (`REQ-SET-006`):
1. **Start Point** (`startLat, startLng`)
2. **End Point** (`endLat, endLng`)
3. **Apex / Max Line Distance Point** (`maxDispLat, maxDispLng`)
4. **Total Distance** (`refDistance`)

Each parameter contributes equally with weight `0.25`:
$$\text{Score} = 0.25 \cdot s_{\text{start}} + 0.25 \cdot s_{\text{end}} + 0.25 \cdot s_{\text{apex}} + 0.25 \cdot s_{\text{dist}}$$
A match is accepted if $\text{Score} < 1.0$.

### 1.2 Topographical Limitation
In hilly, mountainous, or undulating environments, two distinct routes often share identical horizontal start, end, apex, and total distance, yet follow completely different physical paths:
* *Mountain Peak vs. Lowland Valley*: Route A ascends a mountain peak at coordinate $(X_1, Y_1)$ before descending. Route B stays in a river valley at coordinate $(X_2, Y_2)$. Both routes have matching start/end points and distance, but are completely different outdoor experiences.
* *Directional Inversion on Loops*: On out-and-back or looped courses, the coordinates of minimum and maximum elevation provide essential topological anchors.

By incorporating the **geographic coordinates of the minimum altitude point** (`minAltitudePos`) and **maximum altitude point** (`maxAltitudePos`), the engine gains a 3D spatial signature that reliably differentiates routes with distinct vertical profiles.

---

## 2. Impact Analysis (SWE.1.BP.5)

| Impact Area | Evaluation & Mitigation Strategy |
| :--- | :--- |
| **Existing Requirements** | Must strictly preserve `REQ-SET-006` (Agnostic Clustering), `REQ-SET-010` (Tuning UI), `REQ-SET-021` (Fingerprint Editing), `REQ-SET-060` (Cluster Markers), `REQ-SET-063` (Apex Placement), and `REQ-SET-064` (Speed Multi-Sport Clustering). |
| **Database Schema** | Upgrading `RouteClusters.db` from version 9 to 10. Added columns (`min_alt_lat`, `min_alt_lng`, `max_alt_lat`, `max_alt_lng`) are nullable `REAL` fields, ensuring 100% backward compatibility. |
| **Historical Data Migration** | Existing clusters must not remain empty when the feature is enabled by default. Migration backfills existing clusters by computing the mean minimum and maximum altitude coordinates across member workouts. |
| **Flat & Sensorless Workouts** | Workouts without altitude data (indoor, flat walk, altimeter sensor failure) must gracefully fall back to 4-term spatial scoring (0.25 weight each) with zero penalty. |
| **TCX File Imports** | TCX trackpoints with elevation must capture extrema coordinates in $O(N)$ and pass them to `suggestCluster()`. |
| **UI & Fingerprint Editing** | Cluster Heatmap must display Min/Max Altitude markers and allow interactive dragging in edit mode (`isEditingFingerprint = true`). |

---

## 3. Technical Implementation Architecture

### 3.1 Component 1: `TrainingApplication.java`
Add preference keys, defaults, and getters:
* `SP_CLUSTER_USE_ALTITUDE_POS = "use_altitude_pos_for_clustering"` (default: `true`)
* `SP_CLUSTER_TOL_ALTITUDE_POS = "cluster_tol_altitude_pos"` (default: `400f`)
* Methods:
  ```java
  public static boolean useAltitudePosForClustering() {
      return cSharedPreferences.getBoolean(SP_CLUSTER_USE_ALTITUDE_POS, true);
  }
  public static float getClusterTolAltitudePos() {
      return cSharedPreferences.getFloat(SP_CLUSTER_TOL_ALTITUDE_POS, 400f);
  }
  ```

### 3.2 Component 2: `WorkoutClusterDatabaseManager.kt`
1. **Data Class Extension**:
   ```kotlin
   data class WorkoutCluster(
       ...
       val minAltLat: Double? = null,
       val minAltLng: Double? = null,
       val maxAltLat: Double? = null,
       val maxAltLng: Double? = null
   ) {
       val minAltLatLng: LatLng? get() = if (minAltLat != null && minAltLng != null) LatLng(minAltLat, minAltLng) else null
       val maxAltLatLng: LatLng? get() = if (maxAltLat != null && maxAltLng != null) LatLng(maxAltLat, maxAltLng) else null
   }
   ```
2. **Contract Columns & SQLite Upgrade (Version 9 -> 10)**:
   Add constants `COLUMN_MIN_ALT_LAT`, `COLUMN_MIN_ALT_LNG`, `COLUMN_MAX_ALT_LAT`, `COLUMN_MAX_ALT_LNG`.
   In `WorkoutClusterDbHelper.onUpgrade(db, oldVersion, newVersion)`:
   ```kotlin
   if (oldVersion < 10) {
       db.execSQL("ALTER TABLE ${WorkoutClusterContract.TABLE_NAME} ADD COLUMN ${WorkoutClusterContract.COLUMN_MIN_ALT_LAT} REAL")
       db.execSQL("ALTER TABLE ${WorkoutClusterContract.TABLE_NAME} ADD COLUMN ${WorkoutClusterContract.COLUMN_MIN_ALT_LNG} REAL")
       db.execSQL("ALTER TABLE ${WorkoutClusterContract.TABLE_NAME} ADD COLUMN ${WorkoutClusterContract.COLUMN_MAX_ALT_LAT} REAL")
       db.execSQL("ALTER TABLE ${WorkoutClusterContract.TABLE_NAME} ADD COLUMN ${WorkoutClusterContract.COLUMN_MAX_ALT_LNG} REAL")
       backfillAltitudeExtrema(context, db)
   }
   ```
3. **Automated Historical Backfill**:
   Iterate existing clusters in `RouteClusters.db`. Query associated workouts from `WorkoutSummaries` (`CLUSTER_ID = ?`). Query `TABLE_EXTREMA_VALUES` for `SensorType.ALTITUDE` (`MIN` and `MAX`). Compute the average `minAltLat, minAltLng` and `maxAltLat, maxAltLng` and update the cluster.

### 3.3 Component 3: `WorkoutClusterEngine.kt`
1. **Similarity Scoring Formulation**:
   Overload / expand `calculateSimilarity`:
   ```kotlin
   fun calculateSimilarity(
       start: LatLng, end: LatLng, apex: LatLng, distance: Double,
       cluster: WorkoutCluster,
       workoutName: String? = null,
       candidateSportTypes: Set<BSportType> = emptySet(),
       minAltPos: LatLng? = null,
       maxAltPos: LatLng? = null
   ): Double {
       val useAlt = TrainingApplication.useAltitudePosForClustering() &&
                    minAltPos != null && maxAltPos != null &&
                    cluster.minAltLatLng != null && cluster.maxAltLatLng != null

       var totalScore = if (useAlt) {
           val s1 = (distanceBetween(start, LatLng(cluster.startLat, cluster.startLng)) / TrainingApplication.getClusterTolEndpoints()) * 0.20
           val s2 = (distanceBetween(end, LatLng(cluster.endLat, cluster.endLng)) / TrainingApplication.getClusterTolEndpoints()) * 0.20
           val s3 = (distanceBetween(apex, LatLng(cluster.maxDispLat, cluster.maxDispLng)) / TrainingApplication.getClusterTolApex()) * 0.20
           val s4 = (Math.abs(distance - cluster.refDistance) / cluster.refDistance / TrainingApplication.getClusterTolDistance()) * 0.20
           val sMinAlt = (distanceBetween(minAltPos!!, cluster.minAltLatLng!!) / TrainingApplication.getClusterTolAltitudePos()) * 0.10
           val sMaxAlt = (distanceBetween(maxAltPos!!, cluster.maxAltLatLng!!) / TrainingApplication.getClusterTolAltitudePos()) * 0.10
           s1 + s2 + s3 + s4 + sMinAlt + sMaxAlt
       } else {
           val s1 = (distanceBetween(start, LatLng(cluster.startLat, cluster.startLng)) / TrainingApplication.getClusterTolEndpoints()) * 0.25
           val s2 = (distanceBetween(end, LatLng(cluster.endLat, cluster.endLng)) / TrainingApplication.getClusterTolEndpoints()) * 0.25
           val s3 = (distanceBetween(apex, LatLng(cluster.maxDispLat, cluster.maxDispLng)) / TrainingApplication.getClusterTolApex()) * 0.25
           val s4 = (Math.abs(distance - cluster.refDistance) / cluster.refDistance / TrainingApplication.getClusterTolDistance()) * 0.25
           s1 + s2 + s3 + s4
       }

       // Sport type penalties and name bonus preserved as in REQ-SET-064
       ...
       return totalScore
   }
   ```
2. **Moving Average Updates**:
   In `onWorkoutFinished`, `learnFromWorkout`, and `createNewClusterFromWorkout`:
   When a workout has `minAltPos` and `maxAltPos`, calculate the updated running weighted centroid:
   $$\text{centroid}_{\text{new}} = \frac{\text{centroid}_{\text{old}} \cdot \text{hitCount} + \text{pos}_{\text{workout}}}{\text{hitCount} + 1}$$

### 3.4 Component 4: Live Tracking Integration (`TrackerService.java`)
In `finalizeLiveSession()`:
```java
LatLng minAltPos = summariesManager.getExtremaPosition(mWorkoutID, SensorType.ALTITUDE, ExtremaType.MIN);
LatLng maxAltPos = summariesManager.getExtremaPosition(mWorkoutID, SensorType.ALTITUDE, ExtremaType.MAX);
WorkoutCluster suggestion = engine.suggestCluster(startPosRaw, endPosRaw, maxDispPos, mDistanceTotal_m, null, candidateSports, minAltPos, maxAltPos);
```

### 3.5 Component 5: TCX File Import Integration (`LegacyImportEngine.kt` & `ImportEngine.kt`)
During `<Trackpoint>` stream parsing:
1. Track running extrema points: `minAltPoint: LatLng?` and `maxAltPoint: LatLng?`.
2. Update `TABLE_EXTREMA_VALUES` with the exact coordinate points for `SensorType.ALTITUDE` `MIN` and `MAX`.
3. Supply `minAltPoint` and `maxAltPoint` to `clusterEngine.suggestCluster(...)`.

### 3.6 Component 6: Cluster Tuning UI (`ClusterTuningScreen.kt`)
1. Add toggle switch for `useAltitudePosForClustering` (`R.string.cluster_tuning_use_altitude_label`).
2. In Detailed Controls, add `TuningSlider` for `altitudePositionTolerance` (range: 10m–500m, default: 400m).
3. Connect state in `WorkoutClustersViewModel.kt` and `BackupRestoreViewModel.kt`.

### 3.7 Component 7: Cluster Detail Map Visualization & Interactive Relocation (`WorkoutClusterHeatmapScreen.kt`)
1. Extend `ClusterMarkerType` with `ALTITUDE_MIN` and `ALTITUDE_MAX`.
2. Add `editMinAlt` and `editMaxAlt` state variables initialized to cluster signature values.
3. Render `LocationMarker`s for Min Altitude (`R.drawable.ic_altitude`, teal) and Max Altitude (`R.drawable.ic_altitude`, amber).
4. When `isEditingFingerprint = true`, markers are draggable (`draggable = true`, `onDragEnd = { ... }`).
5. On save: call `viewModel.updateClusterFingerprint(cluster, editStart, editEnd, editApex, editMinAlt, editMaxAlt)` to persist coordinates to SQLite.

---

## 4. Verification Plan

### 4.1 Automated Unit Tests (`AltitudeAwareClusterMatchingTest.kt`)
Implement comprehensive test cases verifying:
* **TC-UNT-01**: 6-term scoring rejection when altitude extrema differ (> 400m apart).
* **TC-UNT-02**: 6-term matching when altitude extrema align (< 400m tolerance).
* **TC-UNT-03**: Clean fallback to 4-term scoring when altitude data is null.
* **TC-UNT-04**: Clean fallback to 4-term scoring when preference toggle is disabled.
* **TC-UNT-05**: Mathematical precision of the running weighted centroid calculation.

### 4.2 Regression Verification
Execute full clean-room test suite:
```bash
./gradlew testDebugUnitTest
```
Verify all 207 existing tests continue to pass without regression.
