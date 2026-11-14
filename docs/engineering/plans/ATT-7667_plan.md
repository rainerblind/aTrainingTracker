# Stage 3: Implementation Plan - ATT-7667: Immediate Workout Name Update on Route Cluster Change

**Ticket**: [ATT-7667](https://rainerblind.atlassian.net/browse/ATT-7667)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-SET-059` (*Explicit Workout Cluster Management & Assignment in Editor*)  
**Test Mapping**: `TST-SET-105` (*Immediate Workout Name Synchronization on Route Cluster Change*)  
**Branch**: `feat/ATT-7667`  
**Author**: AI Agent 2 (Pro)  
**Date**: 2026-11-14  

---

## 1. Problem Description & Background
In `EditWorkoutScreen`, changing the route cluster (Lieblingsstrecke) or unassigning it via `EditWorkoutClusterDialog` does not reliably update the workout name input field. In `EditWorkoutViewModel.kt`, `userManuallyChangedWorkoutName` was never cleared when the user explicitly selected or created a route cluster, causing asynchronous repository flow merges in `loadWorkoutData()` to ignore the newly selected route's auto-generated name. Furthermore, `unassignCluster()` left the previous cluster's name intact instead of restoring the fallback unclustered workout name.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SET-059` Clause 5 (*Immediate Workout Name Update on Cluster Assignment & Reassignment*)
* **Test Mapping**: `TST-SET-105` (`TST-SET-105.1` Cluster Selection Name Sync, `TST-SET-105.2` Repo Flow Merge, `TST-SET-105.3` Unassign Reversion, `TST-SET-105.4` Post-Selection Manual Override Protection, `TST-SET-105.5` Create New Cluster Name Sync)

---

## 3. System Invariants & Preserved Behavior
1. **Manual Override Invariance**: When an athlete types directly into the name field (`onWorkoutNameChanged`), `userManuallyChangedWorkoutName` is set to `true` and subsequent background repository flow emissions MUST NOT clobber the user's manual input.
2. **Explicit Action Intent**: Selecting a route cluster or creating a new cluster expresses clear user intent to adopt that cluster's identity; thus `userManuallyChangedWorkoutName` MUST reset to `false` so the generated formatted name takes precedence.
3. **Unassign Name Fallback**: When unassigning a cluster, `clusterId` resets to `-1L`, `clusterName` to `null`, `userManuallyChangedWorkoutName` to `false`, and the workout name reverts to the unclustered default fallback.
4. **Thread Safety & State Reactivity**: All mutations on `_workoutData` are performed atomically via `MutableStateFlow.update`.

---

## 4. Proposed Architectural Changes

### Component 1: `EditWorkoutViewModel.kt`
* **`applyClusterIdentity(cluster: WorkoutCluster)`**:
  * Reset `userManuallyChangedWorkoutName = false`.
  * Update `_workoutData` with `formattedName`, `clusterId = cluster.id`, `clusterName = cluster.name`, `sportId = cluster.probableSportId`, `bSportType = cluster.bSportType`.
  * Persist via `repository.assignClusterToWorkout(workoutId, cluster.id)`.
* **`createNewCluster(customName: String, hasCounter: Boolean)`**:
  * Reset `userManuallyChangedWorkoutName = false`.
  * Update `_workoutData` with `resolvedWorkoutName`.
* **`unassignCluster()`**:
  * Reset `userManuallyChangedWorkoutName = false`.
  * Revert `workoutName` in `_workoutData` to default unclustered name: derived from `current.fileBaseName` or sport name.
  * Clear `clusterId = -1L` and `clusterName = null`.
  * Call `repository.unassignClusterFromWorkout(workoutId)`.
* **`onWorkoutNameChanged(newName: String)`**:
  * Ensure `userManuallyChangedWorkoutName = true` remains active when athlete manually edits text.

### UI Consistency (Rule 23)
* **Reference component**: `EditWorkoutScreen.kt` workout name `OutlinedTextField` (lines 200–220).
* **Changes**: Presentation and ViewModel state flow synchronization only; no UI component layout alterations required.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction Blueprint)

### Step 1: Update `EditWorkoutViewModel.kt` State Synchronization
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt`
* Changes:
  1. In `applyClusterIdentity(cluster)`: insert `userManuallyChangedWorkoutName = false`.
  2. In `createNewCluster(...)`: insert `userManuallyChangedWorkoutName = false`.
  3. In `unassignCluster()`: insert `userManuallyChangedWorkoutName = false`, and update `workoutName` to fallback unclustered name.

### Step 2: Implement Unit Tests in `EditWorkoutViewModelTest.kt`
* Target: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModelTest.kt`
* Implementations:
  - `testApplyClusterIdentity_updatesWorkoutName_andResetsManualFlag()` (`TST-SET-105.1`)
  - `testRepositoryEmission_whenManualFlagFalse_preservesNewClusterName()` (`TST-SET-105.2`)
  - `testUnassignCluster_resetsManualFlag_andRevertsToFallbackName()` (`TST-SET-105.3`)
  - `testOnWorkoutNameChanged_setsManualFlag_andProtectsAgainstRepoEmissions()` (`TST-SET-105.4`)
  - `testCreateNewCluster_resetsManualFlag_andUpdatesWorkoutName()` (`TST-SET-105.5`)

### Step 3: Test Execution & Verification
* Run command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.*"`
* Ensure 100% test pass rate with zero regressions.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests in `EditWorkoutViewModelTest.kt` and full regression audit.
* **Rollback**: Changes isolated on branch `feat/ATT-7667`; can be reverted cleanly via git checkout.
