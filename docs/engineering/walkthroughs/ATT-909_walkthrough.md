# Implementation Walkthrough: TCX Import Period Aggregation, Self-Healing Sync & Cluster Heuristics (ATT-909)

## 1. Overview & Objectives
* **Parent Ticket**: [ATT-909](https://rainerblind.atlassian.net/browse/ATT-909) (`[Bug] Workouts imported from TCX were not added to cluster and missing from workout periods`)
* **Associated Requirements & Tests**:
  * Requirements: `REQ-MIG-026`, `REQ-MIG-027` ([docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L345-L346))
  * Test Specifications: `TST-MIG-023`, `TST-MIG-024` ([docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L135-L136))
  * Fix Version: `V4.9.36`

This defect resolution addresses the issue where historical TCX workouts (imported individually or in bulk via cloud recovery) failed to associate with existing workout clusters and were absent from temporal period summaries (Days, Weeks, Months, Years).

---

## 2. Root Cause Analysis & Technical Solutions

### Issue 1: Missing Period Summaries (Unbuffered Broadcast Drop)
* **Root Cause**: `LegacyImportEngine.recalculateStats()` relied solely on `LocalBroadcastManager.sendBroadcast(WORKOUT_UPDATED_INTENT)`. `WorkoutRepository` registers for this broadcast in its constructor, which in turn calls `PeriodsRepository.onWorkoutFinished()`. If `WorkoutRepository` had not yet been instantiated in memory before the import commenced (e.g. user navigated directly from the navigation drawer into Import & Backup), the broadcast was dropped without buffer. As a result, neither `WorkoutRepository` nor `PeriodsRepository` were informed of the newly imported session, leaving `PeriodSummaries.db` empty for those workouts.
* **Solution**: Implemented a **Direct Repository Notification Pipeline** in `LegacyImportEngine`. The engine now obtains `WorkoutRepository.getInstance(app).reloadWorkoutData(workoutId)` and `WorkoutClusterRepository.getInstance(app).refreshClusters()` directly, ensuring deterministic period and cluster rollups regardless of broadcast timing or prior repository instantiation.

### Issue 2: Lack of Self-Healing Sync in Period Summaries
* **Root Cause**: If any discrepancy ever arose between finished workouts in SQLite (`WorkoutSummaries.db`) and `PeriodSummaries.db` (due to process death, offline database migration, or legacy imports), the periods view remained permanently out-of-sync because no reconciliation mechanism existed.
* **Solution**: Introduced `getTotalDayWorkoutsCount()` in `PeriodSummariesDatabaseManager`, `getFinishedWorkoutCount()` in `WorkoutSummariesDatabaseManager`, and `checkIntegrityAndSync()` / `syncPeriodsIfDiscrepancy()` in `PeriodsRepository`. On repository initialization and post-import reconciliation, the system performs an $O(1)$ count comparison (`COUNT(*) FROM WorkoutSummaries WHERE finished = 1` vs. `SUM(total_workouts) FROM PeriodSummaries WHERE period_type = 'DAY'`). If counts diverge, it automatically triggers asynchronous hierarchical re-synchronization (`performHierarchicalMigration()`).

### Issue 3: False Rejection in Cluster Matching for Unknown Sports
* **Root Cause**: When importing third-party TCX files whose sport name was missing or unmapped (e.g., `Sport="Other"`), `bSportType` resolved to `BSportType.UNKNOWN`. `LegacyImportEngine` previously passed `setOf(bSportType)` into `suggestCluster()`. In `WorkoutClusterEngine.calculateSimilarity()`, `candidateSportTypes.contains(cluster.bSportType)` evaluated to false when the cluster was `RUN` or `BIKE`, applying an insurmountable `+5.0` distance penalty that eliminated the cluster match.
* **Solution**: In `LegacyImportEngine`, when `bSportType == BSportType.UNKNOWN`, the engine now queries speed-based candidates via `EquipmentAndSportTypeDiscoveryManager.getInstance(context).getCandidateBSportTypes(BSportType.UNKNOWN, avgSpeed)` or passes `emptySet()`. This allows route geometry and average speed to match `BIKE` or `RUN` clusters cleanly without the `+5.0` penalty.

### Issue 4: Missing Workout Name Bonus in Cluster Matching
* **Root Cause**: `suggestCluster()` supports a 50% similarity bonus (`totalScore *= 0.5`) when the workout name matches an existing cluster name. However, `LegacyImportEngine` omitted passing `workoutName` into `suggestCluster()`.
* **Solution**: `workoutName` is now passed to `suggestCluster()`, unlocking the 50% similarity discount for matching cluster names (e.g. "Hausrunde").

### Issue 5: Missing `B_SPORT` Column Persistence
* **Root Cause**: When `LegacyImportEngine` matched a cluster and updated `SPORT_ID`, it did not persist the corresponding `B_SPORT` string in `WorkoutSummaries`.
* **Solution**: Added `values.put(WorkoutSummaries.B_SPORT, effectiveBSport.name)` to the database update.

### Issue 6: Stale In-Memory Cluster State
* **Root Cause**: Post-import, `WorkoutClustersViewModel` holds cached `allClusters`, `clusterStats`, and `unclusteredWorkouts` in memory, which were not refreshed after TCX import.
* **Solution**: Added direct invocation of `WorkoutClusterRepository.getInstance(app).refreshClusters()` in `LegacyImportEngine` and post-import batch reconciliation in `BackupRestoreViewModel`.

### Issue 7: Clustering Early Return Bypassing Notifications
* **Root Cause**: In `LegacyImportEngine.kt`, if `existingClusterId != -1L`, the code executed an early `return`, skipping Section 6 (notification pipeline).
* **Solution**: Restructured the guard to skip clustering calculations while allowing Section 6 to execute reliably. Wrapped legacy broadcast in a try-catch to protect against unmocked Intent/LBM crashes in test and background contexts.

---

## 3. Changes Implemented

### A. Database Layer
* **[PeriodSummariesDatabaseManager.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummariesDatabaseManager.kt)**:
  * Added `getTotalDayWorkoutsCount(): Int` querying `SELECT SUM(total_workouts) FROM PeriodSummaries WHERE period_type = 'DAY'`.
  * Added `@VisibleForTesting fun setInstanceForTesting(manager: PeriodSummariesDatabaseManager?)`.
* **[WorkoutSummariesDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java)**:
  * Added `public int getFinishedWorkoutCount()` querying `SELECT COUNT(*) FROM WorkoutSummaries WHERE finished = 1`.

### B. Repository Layer
* **[PeriodsRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt)**:
  * Added `suspend fun checkIntegrityAndSync(): Boolean` comparing SQLite finished workouts vs Day periods in `PeriodSummaries.db`.
  * In `init`, added `checkIntegrityAndSync()` call when `isSyncFinished()` is true.
  * Added `fun syncPeriodsIfDiscrepancy()` triggering integrity check and background re-sync on `repositoryScope`.
  * Added `@VisibleForTesting fun setInstanceForTesting(repo: PeriodsRepository?)`.

### C. Migration & Import Engine
* **[LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt)**:
  * In `recalculateStats`: query speed candidates via `EquipmentAndSportTypeDiscoveryManager.getCandidateBSportTypes(BSportType.UNKNOWN, avgSpeed)` when `bSportType == BSportType.UNKNOWN` (eliminates `+5.0` penalty).
  * Pass `workoutName` to `clusterEngine.suggestCluster()` for 50% score bonus.
  * Persist `values.put(WorkoutSummaries.B_SPORT, bSportType.name)`.
  * Direct repository notification: calls `WorkoutRepository.getInstance(app).reloadWorkoutData(workoutId)` and `WorkoutClusterRepository.getInstance(app).refreshClusters()` directly when `app != null`.
  * Restructured section 5 clustering guard: if `existingClusterId != -1L`, log and skip clustering without returning early, ensuring Section 6 (notification) always runs.
  * Wrapped legacy LocalBroadcastManager broadcast in try-catch.
* **[BackupRestoreViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt)**:
  * Added post-import reactive reconciliation in `importLegacyFile` and `bulkRecoverLegacyData` triggering `loadAllWorkouts()`, `syncPeriodsIfDiscrepancy()`, and `refreshClusters()`.

### D. Verification Suite
* **[TcxImportPeriodAndClusterIntegrationTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/migration/TcxImportPeriodAndClusterIntegrationTest.kt)**:
  * Added comprehensive automated integration test suite covering:
    1. `testTST_MIG_023_directRepositoryNotificationPipelineInvoked`: Verifies direct repository notification pipeline executes without broadcast dependencies.
    2. `testTST_MIG_023_selfHealingPeriodSync_detectsDiscrepancyAndTriggersMigration`: Verifies fast count discrepancy detection triggers hierarchical migration.
    3. `testTST_MIG_024_candidateSportsInferredForUnknownSport_and_nameMatchDiscount_and_bSportPersisted`: Verifies candidate sport inference, workout name similarity discount, and `B_SPORT` column persistence.

---

## 4. Verification & Evidence
* **Automated Unit Tests**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.TcxImportPeriodAndClusterIntegrationTest"`: **3/3 tests PASSED (100%)**.
  * Full project regression suite `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL in 1m 29s (100% pass rate, 0 regressions across all 67 test classes)**.
* **Requirements & Test Status**:
  * `REQ-MIG-026` & `REQ-MIG-027` updated to **Verified** in [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md).
  * `TST-MIG-023` & `TST-MIG-024` updated to **Verified** in [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md).
