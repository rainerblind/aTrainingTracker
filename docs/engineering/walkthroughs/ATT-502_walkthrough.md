# Implementation Walkthrough: Altitude Extrema-Aware 3D Route Clustering & Database Migration (ATT-502)

## 1. Overview & Objectives
* **Parent Ticket**: [ATT-502](https://rainerblind.atlassian.net/browse/ATT-502) (`[Verbesserung] Clustering: optionally, take min and max altitude pos into account`)
* **Sub-Task**: [ATT-788](https://rainerblind.atlassian.net/browse/ATT-788) (`[Implementation] Clustering: optionally, take min and max altitude pos into account`)
* **Associated Requirements & Tests**:
  * Requirement: `REQ-SET-065` ([docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L107))
  * Test Specifications: `TST-SET-052`, `TST-UNT-015` ([docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L220))
  * Target Lösungsversion (Fix Version/s): `V4.9.36`
  * Branch: `feature/ATT-502`

This feature upgrades route clustering from a 2D planar fingerprint to a 3D altitude extrema-aware signature. In undulating or mountainous terrain, routes that share identical horizontal endpoints, apex displacement, and distance often diverge vertically (e.g. mountain summit trail vs valley road, or directional elevation reversals). Incorporating the geographic coordinates of the lowest (`minAltitudePos`) and highest (`maxAltitudePos`) elevation points provides distinct spatial anchors that reliably differentiate these routes.

---

## 2. Changes Implemented

### A. Preferences & Configuration
* **[TrainingApplication.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java)**:
  * Added `SP_CLUSTER_USE_ALTITUDE_POS = "use_altitude_pos_for_clustering"` (default: `true`).
  * Added `SP_CLUSTER_TOL_ALTITUDE_POS = "cluster_tol_altitude_pos"` (default: `400f`).
  * Implemented public static getters `useAltitudePosForClustering()` and `getClusterTolAltitudePos()`.

### B. Database Schema & Migration (v9 -> v10)
* **[WorkoutClusterDatabaseManager.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterDatabaseManager.kt)**:
  * Extended `WorkoutCluster` data class with nullable `minAltLat, minAltLng, maxAltLat, maxAltLng` fields and helper getters `minAltLatLng` and `maxAltLatLng`.
  * Added columns `COLUMN_MIN_ALT_LAT`, `COLUMN_MIN_ALT_LNG`, `COLUMN_MAX_ALT_LAT`, and `COLUMN_MAX_ALT_LNG` to `WorkoutClusterContract`.
  * Incremented `DATABASE_VERSION` from `9` to `10`.
  * Implemented automated schema migration `ALTER TABLE` in `onUpgrade` with historical data backfill (`backfillAltitudeExtrema`), which computes the running member workout centroids from `TABLE_EXTREMA_VALUES` for existing clusters.
  * Updated cursor mapping and content values mapping.

### C. Similarity Scoring Engine & Mathematical Centroids
* **[WorkoutClusterEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt)**:
  * **6-Term Balanced Similarity Model**:
    $$\text{Score} = 0.20 \cdot s_{\text{start}} + 0.20 \cdot s_{\text{end}} + 0.20 \cdot s_{\text{apex}} + 0.20 \cdot s_{\text{dist}} + 0.10 \cdot s_{\text{minAlt}} + 0.10 \cdot s_{\text{maxAlt}}$$
  * **Fallback Invariant**: If preference is disabled, or either candidate cluster or workout lacks altitude coordinates, scoring cleanly falls back to the 4-term model with equal `0.25` weights and zero penalty.
  * **Running Weighted Centroids**:
    $$\text{centroid}_{\text{new}} = \frac{\text{centroid}_{\text{old}} \cdot \text{hitCount} + \text{pos}_{\text{workout}}}{\text{hitCount} + 1}$$
    Applied in `learnFromWorkout`, `onWorkoutFinished`, `createNewClusterFromWorkout`, and `migrateHistory`.

### D. Live Tracking & File Imports
* **[TrackerService.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java)**:
  * Queries `minAltPos` and `maxAltPos` from `TABLE_EXTREMA_VALUES` for `SensorType.ALTITUDE` and passes them to `suggestCluster(...)`.
* **[LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt)**:
  * Tracks minimum and maximum elevation coordinates in $O(N)$ during stream parsing.
  * Writes exact coordinates to `TABLE_EXTREMA_VALUES` for `SensorType.ALTITUDE`.
  * Passes extrema points to `suggestCluster` and `learnFromWorkout`.

### E. User Interface, Tuning & Heatmap Relocation
* **[WorkoutClusterHeatmapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt)**:
  * Renders distinct markers for Min Altitude (teal) and Max Altitude (amber) using unified icons [`ic_altitude_min.xml`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/drawable/ic_altitude_min.xml) (mountain silhouette + downward triangle `▼`) and [`ic_altitude_max.xml`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/drawable/ic_altitude_max.xml) (mountain silhouette + upward triangle `▲`).
  * Harmonized altitude icons across both Workout Details and Cluster Heatmap screens.
  * In fingerprint edit mode (`isEditingFingerprint = true`), markers are draggable; on save, new positions are persisted via `updateClusterFingerprint`.
* **[ClusterTuningScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values/strings.xml)** & **[ImportBackupTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt)**:
  * Added switch toggle for `useAltitudePosForClustering`.
  * Added dedicated slider in Detailed Controls for `altitudePositionTolerance` (10m–500m, default: 400m).
  * Connected sensitivity master slider with altitude position tolerance scaling.
* **[WorkoutClustersViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt)** & **[BackupRestoreViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt)**:
  * Added state flows, preference synchronization, and updated `updateClusterFingerprint`.
  * Added peak markers referencing distinct `ic_altitude_min` and `ic_altitude_max` drawables.
* **[ClusterData.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterData.kt)**, **[Color.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt)**, **[MyPreferenceManager.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/MyPreferenceManager.kt)**:
  * Added `ALTITUDE_MIN` and `ALTITUDE_MAX` marker types and colors.

### F. Multi-Language Localization
* Updated strings in all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) for complete translation parity.

### G. Automated Unit Tests
* **[AltitudeAwareClusterMatchingTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/database/AltitudeAwareClusterMatchingTest.kt)**:
  * `TC-UNT-01`: 6-term scoring rejection when altitude extrema differ (> 400m apart).
  * `TC-UNT-02`: 6-term matching when altitude extrema align (< 400m tolerance).
  * `TC-UNT-03`: Clean fallback to 4-term scoring when altitude data is null.
  * `TC-UNT-04`: Clean fallback to 4-term scoring when preference toggle is disabled.
  * `TC-UNT-05`: Mathematical precision of running weighted centroid calculation and database update.

---

## 3. Verification & Evidence

### Automated Unit Test Matrix
1. **Targeted Test Suite**:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.AltitudeAwareClusterMatchingTest"
   ```
   * Result: **PASSED (All 5 test cases succeeded)**.
2. **Cluster & Localization Regression Matrix**:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
   ```
   * Result: **PASSED (All tests succeeded)**.
3. **Full Clean-Room Unit Test Suite**:
   ```bash
   ./gradlew testDebugUnitTest
   ```
   * Result: **`BUILD SUCCESSFUL in 43s` (All 213 unit tests passed with zero errors)**.

---

## 4. Adversarial Self-Review ("Red Team" Pass)
1. **Can flat or indoor workouts without barometric/GPS elevation data break the clustering engine?**
   * *No*: The engine evaluates `minAltPos != null && maxAltPos != null && cluster.minAltLatLng != null && cluster.maxAltLatLng != null`. If any coordinate is missing, it cleanly falls back to 4-term scoring (`0.25` weight each) with zero penalty.
2. **Does database migration v10 block or wipe user clusters on update?**
   * *No*: SQLite `ALTER TABLE` adds nullable REAL columns non-destructively, preserving existing cluster records. The backfill iterates member workouts and gracefully assigns centroids only when elevation data is available.
3. **What if a user drags altitude markers during fingerprint editing?**
   * *Handled*: `WorkoutClusterHeatmapScreen` allows dragging Min and Max altitude markers identically to Start, End, and Apex markers, and persists new coordinates via `dbManager.updateCluster`.
4. **Are format specifiers and translations intact across all languages?**
   * *Verified*: `TranslationParityTest` confirmed string parity and format specifiers across all 9 supported locales.
