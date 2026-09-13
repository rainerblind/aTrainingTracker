# Engineering Analysis: Cluster Creation Lifecycle & Name Synchronization in Edit Workout (ATT-956)

## 1. Executive Summary & Problem Statement
* **Parent Ticket**: [ATT-956](https://rainerblind.atlassian.net/browse/ATT-956) (`[Bug] Creating a new cluster did not work properly`)
* **Analysis Sub-Task**: [ATT-958](https://rainerblind.atlassian.net/browse/ATT-958) (`[Analysis] Creating a new cluster did not work properly`)
* **Target Release**: Fix Version `V4.9.36`
* **Affected Area**: `EditWorkoutScreen.kt`, `EditWorkoutViewModel.kt`, `WorkoutClusterEngine.kt`, `WorkoutClusterComponents.kt`, `WorkoutRepository.kt`

### Defect Description
When a workout has matched an existing cluster (e.g., "Hausrunde #3"), and the user opens the Edit Workout dialog to create a new cluster:
1. In `EditWorkoutClusterDialog`, the text field for the new route name is prefilled with the full name including counter (`"Hausrunde #3"`), requiring manual stripping.
2. If the user specifies a new cluster name (e.g., `"Neue Hausrunde"`), the cluster is created, but upon returning to `EditWorkoutScreen`:
   - The Workout Name field still displays the old cluster's name (`"Hausrunde #3"`).
   - In SQLite (`WorkoutSummaries`), `WORKOUT_NAME` was never updated to the new cluster name because `createNewClusterFromWorkout` invoked `assignClusterToWorkout` with `forceIdentity = false`, which refused to overwrite `currentName` because it wasn't empty or equal to `fileBaseName`.
   - In `EditWorkoutViewModel`, `_workoutData` was not updated synchronously, leaving stale route and name state in the UI.
   - Furthermore, `repository.workout.collect` in `EditWorkoutViewModel` indiscriminately overwrote any user-typed custom workout name whenever a database reload occurred.

---

## 2. Root Cause Analysis (RCA)

### RCA-1: `forceIdentity = false` in `WorkoutClusterEngine.createNewClusterFromWorkout`
* **Location**: [WorkoutClusterEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt#L738) & [L784](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt#L784)
* **Mechanism**:
  ```kotlin
  val newClusterId = dbManager.insertCluster(newCluster)
  assignClusterToWorkout(context, workout.id, newClusterId, forceIdentity = false)
  ```
  In `assignClusterToWorkout`:
  ```kotlin
  if (forceIdentity || currentName.isNullOrEmpty() || currentName == fileBaseName) {
      val displayCount = if (previousClusterId == clusterId) cluster.hitCount else cluster.hitCount + 1
      put(WorkoutSummaries.WORKOUT_NAME, formatClusterWorkoutName(context, cluster.name, displayCount, cluster.hasCounter))
  }
  ```
  Because `forceIdentity == false` and `currentName` was `"Hausrunde #3"` (neither null/empty nor equal to `fileBaseName`), `assignClusterToWorkout` bypassed updating `WorkoutSummaries.WORKOUT_NAME`. The database row permanently retained the old matched cluster's name.

### RCA-2: Missing Synchronous State Update in `EditWorkoutViewModel.createNewCluster`
* **Location**: [EditWorkoutViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt#L447-L454)
* **Mechanism**: Unlike `applyClusterIdentity(cluster)` which immediately updates `_workoutData` with the new `clusterId`, `clusterName`, and `workoutName`, `createNewCluster` only dispatched an asynchronous coroutine via `repository.createNewClusterFromWorkout`. When `EditWorkoutClusterDialog` dismissed, `_workoutData` on `EditWorkoutScreen` still held the stale cluster and workout name.

### RCA-3: Unconditional Repository State Overwrite of User-Typed Workout Name
* **Location**: [EditWorkoutViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt#L91-L100)
* **Mechanism**: When `loadWorkout(workoutId)` executes in `WorkoutRepository`, `repository.workout` emits a new `WorkoutData`. The collector in `EditWorkoutViewModel` did not differentiate between an unmodified workout name and a user-customized workout name, causing any in-progress or typed edits on `EditWorkoutScreen` to be reverted to the database value.

### RCA-4: Counter Suffix Not Stripped in Create Cluster Dialog Pre-fill
* **Location**: [WorkoutClusterComponents.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt#L689)
* **Mechanism**: `var newClusterName by remember { mutableStateOf(initialWorkoutName) }` passed `"Hausrunde #3"` directly into the route name field instead of `WorkoutClusterEngine.stripHitCount(initialWorkoutName)` (`"Hausrunde"`).

---

## 3. Requirements Specification (REQ-SET-067)

### REQ-SET-067: Edit Workout New Cluster Creation & Name Synchronization
The system SHALL ensure that creating a new cluster from within the Edit Workout dialog cleanly updates both the cluster assignment and the workout name in accordance with user intent:
1. **Dialog Pre-fill Ergonomics**: When opening the "Create New Route" section in `EditWorkoutClusterDialog`, the new route name field SHALL pre-fill with the base name stripped of any numerical counter suffix (`WorkoutClusterEngine.stripHitCount(initialWorkoutName)`).
2. **Deterministic Database Synchronization (`forceIdentity = true`)**: `WorkoutClusterEngine.createNewClusterFromWorkout` SHALL invoke `assignClusterToWorkout` with `forceIdentity = true`, ensuring `WorkoutSummaries.WORKOUT_NAME` and `CLUSTER_ID` are updated in SQLite to the newly created cluster's identity.
3. **Smart Workout Name Preservation vs. Auto-Naming**:
   - If the user has *manually typed* a custom workout name on `EditWorkoutScreen` (`userManuallyChangedWorkoutName == true`), that typed name SHALL be preserved and MUST NOT be overwritten by the new cluster's name.
   - If the workout name was *not manually typed* (i.e., it still reflected the old cluster's name or a default title), creating a new cluster SHALL automatically update the workout name to the new cluster's formatted name (e.g. `"Neue Hausrunde"` without `#1` suffix per `REQ-SET-066`).
4. **Reactive UI State Consistency**: `EditWorkoutViewModel.createNewCluster` SHALL update `_workoutData` in memory synchronously so that `currentClusterName` and `workoutName` immediately reflect the new route upon dialog dismissal.

---

## 4. Impact Analysis & Invariants
* **Existing Cluster Integrity**: Atomic decrement of the previous cluster's `hitCount` (`previousClusterId != -1L`) is preserved by `assignClusterToWorkout`.
* **Zero-Counter Initial Suffix (`REQ-SET-066`)**: For count = 1, `formatClusterWorkoutName` outputs the clean base name without `#1`.
* **User Decision Sovereignty (`REQ-MIG-025`)**: "Leave Unclustered" (`unassignCluster()`) continues to set `clusterId = -1L` without regression.
* **No Database Schema Changes**: Leverages existing columns in `WorkoutSummaries` and `WorkoutCluster`.
