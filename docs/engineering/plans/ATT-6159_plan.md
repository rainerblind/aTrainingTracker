# Stage 3: Implementation Plan - ATT-6159: Fix Stale-Copy Overwrite in moveWorkoutToCluster

**Ticket**: [ATT-6159](https://rainerblind.atlassian.net/browse/ATT-6159)  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6159`  
**Requirement Mapping**: `REQ-REV-007` (`REQ-REV-007.1`..`REQ-REV-007.3`)  
**Test Mapping**: `TST-REV-008` (`TST-REV-008.1`..`TST-REV-008.3`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
In `WorkoutClusterEngine.kt:1166-1208` (`moveWorkoutToCluster`), two sequential bugs corrupt cluster records:
1. `dbManager.updateCluster(oldCluster.copy(...))` computes and writes updated centroids. Directly after, `dbManager.updateCluster(oldCluster.copy(probableSportId = ...))` uses the stale initial `oldCluster` reference, overwriting the new centroids. The same bug affects `newCluster`.
2. `summariesManager.getMostFrequentSportIdForCluster` runs before `assignClusterToWorkout(context, workoutId, newClusterId)` updates `WorkoutSummaries.CLUSTER_ID`, computing majority sports against the pre-move workout distribution.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-REV-007` (*Workout Cluster Reassignment Centroid Integrity & Sport Frequency Synchronization*)
  * `REQ-REV-007.1`: Centroid preservation using pre-move `hitCount`; subsequent sport updates must not overwrite centroids.
  * `REQ-REV-007.2`: `assignClusterToWorkout` executes before sport frequency recalculation.
  * `REQ-REV-007.3`: `getMostFrequentSportIdForCluster` evaluated after SQLite reassignment; sport fields applied to freshly queried cluster instances.
* **Test Mapping**: `TST-REV-008` (`TST-REV-008.1`..`TST-REV-008.3`)

---

## 3. System Invariants & Preserved Behavior
1. **Centroid Mathematical Correctness**: Weighted centroid formulas un-weight points from `oldCluster` and add points to `newCluster` using pre-move `hitCount`.
2. **Single Authority for Assignment**: `assignClusterToWorkout` retains sole authority for updating `WorkoutSummaries.CLUSTER_ID` and cluster `hitCount`.
3. **Database Concurrency & Integrity**: All cluster mutations remain enclosed within `synchronized(clusterLock)`. Zero schema changes.

---

## 4. Proposed Architectural Changes
* In `WorkoutClusterEngine.kt` (`moveWorkoutToCluster`):
  * Capture pre-move `oldHitCount` and `newHitCount`.
  * Compute and persist updated centroid coordinates for `oldCluster` (if `oldHitCount > 1`) and `newCluster`.
  * Call `assignClusterToWorkout(context, workoutId, newClusterId)` to update `WorkoutSummaries.CLUSTER_ID` and reconcile hit counts.
  * Recalculate `mostFrequentSport` for both clusters and apply to fresh cluster records re-queried via `dbManager.getClusterById()`.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Engine Reordering & Fresh Cluster Copies)**: Refactor `WorkoutClusterEngine.moveWorkoutToCluster` to compute centroids first, invoke `assignClusterToWorkout`, and apply sport updates to `dbManager.getClusterById()`.
2. **Step 2 (Targeted Unit Tests `TST-REV-008`)**:
   - In `EditWorkoutClusterTest.kt`, add unit test `testMoveWorkoutToCluster_preservesUpdatedCentroidsAndSynchronizesSport()` verifying centroids are not overwritten and `probableSportId` reflects the moved workout.
   - Run targeted tests:
     ```bash
     ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.EditWorkoutClusterTest" --tests "com.atrainingtracker.trainingtracker.build.*"
     ```

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted tests in `EditWorkoutClusterTest.kt` followed by clean-room suite in Stage 5.
* **Rollback**: Branch isolation (`feature/ATT-6159`) allows revert via `git reset --hard` before merge.
