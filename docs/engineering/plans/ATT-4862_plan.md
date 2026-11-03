# Implementation Plan - ATT-4862

## 1. Overview & Architectural Design (SWE.2)
- **Ticket**: ATT-4862 ([Bug] Lieblingsstrecke wird erstellt, obwohl 'Offen' ausgewählt wurde)
- **Requirements**: `REQ-SET-081` (`REQ-SET-081.1`..`REQ-SET-081.3`)
- **Living Test Spec**: `TST-SET-081` in `docs/tests/tst_settings_profiles.md`
- **Architectural Scope**:
  - `WorkoutRepository.kt`: Gating the learning loop strictly behind `clusterId > 0`, removing implicit cluster assignment in `saveWorkout()`.
  - `EditWorkoutClusteringTest.kt`: Verification of unclustered preservation and clustered centroid learning.

## 2. Step-by-Step Construction Plan

### Step 1: Remove Implicit Cluster Creation & Assignment from `WorkoutRepository.saveWorkout()`
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`
- **Action**:
  - Modify the learning loop in `saveWorkout()`:
    - Change condition to `if (userEditedWorkout.clusterId > 0 && userEditedWorkout.startLatLng != null && userEditedWorkout.endLatLng != null && userEditedWorkout.maxDisplacementLatLng != null)`.
    - Invoke `engine.learnFromWorkout(..., clusterIdOverride = userEditedWorkout.clusterId)`.
    - Remove `if (userEditedWorkout.clusterId != learnedId) { engine.assignClusterToWorkout(...) }` override block.
  - When `userEditedWorkout.clusterId <= 0` (e.g. `-1L` / "Offen"), bypass cluster operations entirely.

### Step 2: Implement Unit Tests in `EditWorkoutClusteringTest.kt`
- **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutClusteringTest.kt`
- **Action**:
  - `testSaveWorkout_whenUnclustered_preservesUnclusteredStatusWithoutCreatingCluster`:
    - Mock `WorkoutClusterEngine.learnFromWorkout` and `assignClusterToWorkout`.
    - Call `saveWorkout()` on an unclustered workout (`clusterId = -1L`) with a custom title.
    - Verify `learnFromWorkout` and `assignClusterToWorkout` are never invoked.
  - `testSaveWorkout_whenClustered_updatesClusterCentroidWithoutReassigning`:
    - Call `saveWorkout()` on a clustered workout (`clusterId = 10L`) with an edited name.
    - Verify `learnFromWorkout(..., clusterIdOverride = 10L)` is invoked.
    - Verify `assignClusterToWorkout` is not invoked.

### Step 3: Verification & Clean-Room Regression
- **Test Commands**:
  - `./gradlew testDebugUnitTest --tests "*EditWorkoutClusteringTest*" --tests "*EditWorkoutClusterTest*"`
  - `./gradlew testDebugUnitTest --tests "*ThemeColorCentralizationAuditTest*"`
  - `python3 tools/verify_requirement_governance.py`

## 3. Invariants & Guardrails
- **Zero Schema Migrations**: `WorkoutClusters.db` and `WorkoutSummaries.db` schemas remain unchanged.
- **Explicit Cluster Management**: `EditWorkoutClusterDialog` actions (`createNewCluster`, `applyClusterIdentity`, `unassignCluster`) remain the single authority for creating and assigning clusters.
