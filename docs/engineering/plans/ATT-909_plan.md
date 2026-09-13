# ASPICE Stage 3: Implementation Plan (SWE.2 / SWE.3)
## ATT-909: Workouts imported from TCX were not added to cluster and missing from workout periods

**Parent Epic**: ATT-529 (Import TCX Files)  
**Target Release**: V4.9.36  
**Requirement Mapping**: `REQ-MIG-026`, `REQ-MIG-027`  
**Verification Tests**: `TST-MIG-023`, `TST-MIG-024`  
**Status**: In Bearbeitung  

---

### 1. Architectural Overview & Design Intent

Historical workouts imported from TCX files (single file import or cloud bulk recovery) currently fail to appear in workout periods (Day/Week/Month/Year cards and heatmaps) and are systematically rejected from matching existing route clusters. 

Root Cause Analysis ([ATT-909_analysis.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/analysis/ATT-909_analysis.md)) identified the following systemic breakdowns:
1. **Unbuffered Broadcast Drop**: `LegacyImportEngine.kt` relied strictly on `LocalBroadcastManager.sendBroadcast(WORKOUT_UPDATED_INTENT)` to signal completion. When `WorkoutRepository` is not yet instantiated (as in the Backup/Import flow), the broadcast is silently dropped. Neither `WorkoutRepository.reloadWorkoutData()` nor `PeriodsRepository.onWorkoutFinished()` ever executes.
2. **Missing Periodic Self-Healing**: `PeriodsRepository` only ran `performHierarchicalMigration()` if `!isSyncFinished()`. If initial migration already occurred in the past, `PeriodSummaries.db` never incorporated newly imported workout dates.
3. **Punitive +5.0 Cluster Penalty**: `LegacyImportEngine` passed `setOf(BSportType.UNKNOWN)` to `suggestCluster()`, incurring a catastrophic `+5.0` distance penalty in `calculateSimilarity()`, completely preventing geometric matches.
4. **Omission of Workout Name Discount**: `LegacyImportEngine` passed `null` for `workoutName`, omitting the 50% similarity bonus (`totalScore *= 0.5`).
5. **Missing B_SPORT Persistence**: `LegacyImportEngine` omitted writing `WorkoutSummaries.B_SPORT` into SQLite.
6. **Stale In-Memory Cluster Cache**: `WorkoutClusterRepository.refreshClusters()` was never notified after import.

This implementation plan establishes a resilient, multi-tiered pipeline guaranteeing that imported workouts immediately appear in temporal periods and accurately associate with route clusters.

---

### 2. Component Modifications & Detailed File Plan

#### 2.1 Direct Notification Pipeline & Heuristic Fixes: `LegacyImportEngine.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`
* **Modifications**:
  1. **Speed-Based Candidate Sport Inference & Name Bonus**:
     In `recalculateStats()`:
     ```kotlin
     val avgSpeed = if (activeTime > 0) totalDistance / activeTime else 0.0
     val candidateSports = if (bSportType != BSportType.UNKNOWN) {
         setOf(bSportType)
     } else {
         EquipmentAndSportTypeDiscoveryManager.getInstance(context)
             .getCandidateBSportTypes(BSportType.UNKNOWN, avgSpeed)
     }
     val matchingCluster = clusterEngine.suggestCluster(
         start = start,
         end = end,
         apex = apex,
         distance = totalDistance,
         workoutName = workoutName,
         candidateSportTypes = candidateSports,
         minAltPos = minAltPos,
         maxAltPos = maxAltPos
     )
     ```
  2. **Cluster Sport Adoption & B_SPORT Persistence**:
     - If `bSportType == BSportType.UNKNOWN` and `matchingCluster != null` and `matchingCluster.bSportType != BSportType.UNKNOWN`, adopt the cluster's base sport.
     - Persist `values.put(WorkoutSummaries.B_SPORT, bSportType.name)` into `WorkoutSummaries`.
  3. **Direct Pipeline Notification**:
     Replace exclusive `LocalBroadcastManager` broadcast with direct synchronous repository invocation:
     ```kotlin
     val app = context.applicationContext as? Application
     if (app != null) {
         val workoutRepo = WorkoutRepository.getInstance(app)
         workoutRepo.reloadWorkoutData(workoutId)
         WorkoutClusterRepository.getInstance(app).refreshClusters()
     }
     // Keep broadcast for legacy listeners
     val intent = Intent(TrackerService.WORKOUT_UPDATED_INTENT)
     intent.putExtra(TrackerService.WORKOUT_ID, workoutId)
     LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
     ```

#### 2.2 Self-Healing Period Synchronization: `PeriodSummariesDatabaseManager.kt` & `WorkoutSummariesDatabaseManager.java`
* **Files**: 
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummariesDatabaseManager.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java`
* **Modifications**:
  1. In `PeriodSummariesDatabaseManager.kt`:
     Add fast $O(1)$ query:
     ```kotlin
     fun getTotalDayWorkoutsCount(): Int {
         val db = getDatabase()
         db.rawQuery(
             "SELECT SUM(${PeriodSummariesContract.COLUMN_TOTAL_WORKOUTS}) FROM ${PeriodSummariesContract.TABLE_NAME} WHERE ${PeriodSummariesContract.COLUMN_PERIOD_TYPE} = ?",
             arrayOf(PeriodType.DAY.name)
         ).use { cursor ->
             return if (cursor.moveToFirst()) cursor.getInt(0) else 0
         }
     }
     ```
  2. In `WorkoutSummariesDatabaseManager.java`:
     Add fast $O(1)$ finished workout count:
     ```java
     public int getFinishedWorkoutCount() {
         try (Cursor c = getDatabase().rawQuery(
                 "SELECT COUNT(*) FROM " + WorkoutSummaries.TABLE + " WHERE " + WorkoutSummaries.TIME_ACTIVE_s + " > 0", null)) {
             return c.moveToFirst() ? c.getInt(0) : 0;
         }
     }
     ```

#### 2.3 Reactive Integrity Verification: `PeriodsRepository.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt`
* **Modifications**:
  1. Implement `checkIntegrityAndSync()`:
     ```kotlin
     suspend fun checkIntegrityAndSync(): Boolean = withContext(Dispatchers.IO) {
         val finishedWorkouts = workoutSummariesManager.getFinishedWorkoutCount()
         val periodWorkouts = dbManager.getTotalDayWorkoutsCount()
         if (finishedWorkouts > periodWorkouts) {
             Log.i(TAG, "Integrity discrepancy detected: $finishedWorkouts workouts in SQLite vs $periodWorkouts in PeriodSummaries. Triggering migration.")
             performHierarchicalMigration()
             true
         } else {
             false
         }
     }
     ```
  2. In `init`:
     When `isSyncFinished()` is true, trigger `checkIntegrityAndSync()`.
  3. Expose public helper `fun syncPeriodsIfDiscrepancy()` for external post-import invocation.

#### 2.4 Post-Import Batch Reconciliation: `BackupRestoreViewModel.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt`
* **Modifications**:
  1. In `importLegacyFile`:
     Upon successful import, explicitly trigger:
     - `WorkoutRepository.getInstance(app).loadAllWorkouts()`
     - `PeriodsRepository.getInstance(app).syncPeriodsIfDiscrepancy()`
     - `WorkoutClusterRepository.getInstance(app).refreshClusters()`
  2. In `bulkRecoverLegacyData`:
     If `result.importedCount > 0`, trigger the same repository refreshes.

---

### 3. Impact Analysis & System Invariants

| Invariant | Protection Mechanism |
| :--- | :--- |
| **User Decision Sovereignty (`REQ-MIG-025`)** | "Leave Unclustered" remains `clusterId = -1L`. No automatic cluster generation for unclustered imports. |
| **Spatial Cluster Tolerances** | Endpoint, apex, distance, and altitude tolerance formulas remain unchanged. |
| **Live Tracking Completion** | Live tracking finish flow in `TrackerService.java` and `WorkoutRepository` remains untouched. |
| **Performance Overhead** | Integrity check runs a single fast $O(1)$ index query (`SELECT SUM(...)` vs `SELECT COUNT(...)`), incurring sub-millisecond overhead. |

---

### 4. Verification Plan

1. **Unit Testing**:
   - `LegacyImportEngineWorkoutNameTest.kt` / `TcxHighFidelityImportTest.kt`:
     - Validate unknown sport cluster candidate evaluation with speed heuristics (`TST-MIG-024`).
     - Validate workout name passing to `suggestCluster` (`TST-MIG-024`).
     - Validate `WorkoutSummaries.B_SPORT` persistence (`TST-MIG-024`).
     - Validate direct repository invocation without unbuffered drop (`TST-MIG-023`).
   - `PeriodCacheResyncTest.kt`:
     - Validate `checkIntegrityAndSync` triggers migration when finished workout count > Day period count (`TST-MIG-023`).
2. **Regression Suite**:
   - Run `./gradlew testDebugUnitTest` across all unit test suites.
