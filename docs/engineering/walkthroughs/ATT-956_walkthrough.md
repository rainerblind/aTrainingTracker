# Walkthrough: Edit Workout New Cluster Creation & Name Synchronization (ATT-956)

* **Parent Ticket**: [ATT-956](https://rainerblind.atlassian.net/browse/ATT-956) (*[Fehler] When creating a cluster from edit workout, the cluster gets the name of the workout including the #n instead of stripping that, and the workout is not named as the cluster*)
* **Sub-Tasks**:
  * [ATT-957](https://rainerblind.atlassian.net/browse/ATT-957) (*[SWE.1] System & Software Requirements Analysis*) - `Erledigt`
  * [ATT-958](https://rainerblind.atlassian.net/browse/ATT-958) (*[SWE.4] Verification Specification*) - `Erledigt`
  * [ATT-960](https://rainerblind.atlassian.net/browse/ATT-960) (*[SWE.2 / SWE.3] Architecture, Detailed Design & Implementation Plan*) - `Erledigt`
  * [ATT-959](https://rainerblind.atlassian.net/browse/ATT-959) (*[Implementation] Implement cluster name stripping and workout renaming*) - `In Review`
* **Target Version**: `V4.9.36`
* **Requirement**: [`REQ-SET-072`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L114) (*Edit Workout New Cluster Creation & Name Synchronization*)
* **Test Specification**: [`TST-SET-061`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L232) (*Edit Workout New Cluster Creation & Name Synchronization Verification*)
* **Branch**: `bugfix/ATT-956`

---

## 1. Overview & Root Cause Analysis

When an athlete opens the **Edit Workout** dialog for a workout matched to an existing cluster (e.g. `"Hausrunde #3"`) and decides to fork or create a new route cluster from it:
1. **Unwanted Numerical Suffix Pre-fill**: The text field for the new cluster name in [`EditWorkoutClusterDialog`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt) directly pre-filled the raw `initialWorkoutName` (`"Hausrunde #3"`), forcing the user to manually backspace and delete the ` #3` suffix.
2. **Workout Not Renamed to New Cluster**: When saving the new cluster, [`WorkoutClusterEngine.createNewClusterFromWorkout`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt) called `assignClusterToWorkout(context, workout.id, newClusterId, false)` with `forceIdentity = false`. Because the workout had an existing name, SQLite's `WorkoutSummaries.WORKOUT_NAME` was never updated to the new cluster name.
3. **Loss of User Intent & Race Conditions**: In [`EditWorkoutViewModel`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt):
   - `createNewCluster` only updated `clusterName`, leaving the in-memory `workoutName` as the stale old name.
   - If the user had intentionally typed a custom workout name, subsequent background emissions from `WorkoutRepository.workout` could overwrite it.

---

## 2. Summary of Implementation Changes

### 2.1 Pre-fill Ergonomics with Suffix Stripping ([`WorkoutClusterComponents.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt))
* In `EditWorkoutClusterDialog`, the `newClusterName` state now strips any numerical counter suffix from `initialWorkoutName`:
  ```kotlin
  var newClusterName by remember(initialWorkoutName) {
      mutableStateOf(WorkoutClusterEngine.stripHitCount(initialWorkoutName))
  }
  ```
* Tapping "Create New Route" on a workout named `"Hausrunde #3"` now instantly pre-fills `"Hausrunde"`.

### 2.2 Deterministic Database Synchronization with `forceIdentity = true` ([`WorkoutClusterEngine.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt))
* Updated `createNewClusterFromWorkout`:
  - Added optional parameter `customWorkoutName: String? = null`.
  - Invokes `assignClusterToWorkout(context, workout.id, newClusterId, forceIdentity = true)`.
  - When `customWorkoutName` is provided (user explicitly typed a custom name), that custom name is written to `WorkoutSummaries.WORKOUT_NAME`.
  - When `customWorkoutName == null`, the workout name is formatted via `formatClusterWorkoutName(cluster.name, 1, cluster.hasCounter)` (e.g. `"Neue Hausrunde"` without `#1` per `REQ-SET-066`).
  - Decoupled `WorkoutRepository.reloadWorkoutData` trigger to a non-blocking background coroutine with `Dispatchers.IO`.

### 2.3 Smart Name Preservation vs Auto-Naming in ViewModel ([`EditWorkoutViewModel.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt))
* Tracks `userManuallyChangedWorkoutName: Boolean`:
  - Flag is set to `true` when user edits the workout name field in `updateWorkoutName()`.
* In `createNewCluster(name: String, hasCounter: Boolean)`:
  - If user did not manually edit the name (`!userManuallyChangedWorkoutName`), the formatted new cluster name (e.g. `"Neue Runde"`) is applied to `_workoutData` and persisted to the database.
  - If user manually entered a custom name, that typed name is passed to `customWorkoutName` and preserved.
  - `_workoutData` is updated in memory synchronously before launching background persistence, ensuring immediate UI reactivity upon dialog dismissal.
* In repository collector:
  - Preserves user-typed name across any repository reload emissions:
    ```kotlin
    if (userManuallyChangedWorkoutName) {
        val userTypedName = _workoutData.value?.workoutName
        it.copy(workoutName = userTypedName ?: it.workoutName)
    }
    ```

### 2.4 Re-entrant Update Protection in Repository ([`WorkoutRepository.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt))
* In `updateWorkoutCluster(workoutId, clusterId, clusterName)`:
  - Added re-entrancy guard preventing duplicate emissions if the workout in `_workout.value` already possesses the updated `clusterId`.

---

## 3. Verification & Test Evidence

### 3.1 Unit Test Suite (`EditWorkoutClusterTest.kt`)
Added automated unit test coverage for `REQ-SET-072` / `TST-SET-061`:
1. `testStripHitCount_removesSequentialCounters`:
   - Validates stripping of ` #4`, ` #12`, etc., and unchanged strings for unnumbered names.
2. `testCreateNewClusterFromWorkout_formatsClusterWorkoutName_forcesIdentity`:
   - Validates that `createNewClusterFromWorkout` invokes `assignClusterToWorkout` with `forceIdentity = true`, updating SQLite `WORKOUT_NAME` to the new cluster name (`"Forest Loop"`) and setting `CLUSTER_ID`.
3. `testCreateNewClusterFromWorkout_withCustomWorkoutName_preservesCustomName`:
   - Validates that passing `customWorkoutName = "My Custom Typed Name"` preserves the user-typed name in SQLite `WORKOUT_NAME` alongside the new `CLUSTER_ID`.

**Command**:
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.EditWorkoutClusterTest"
```
**Result**: `BUILD SUCCESSFUL` (12 tests passed, 0 failures).

### 3.2 Full Project Regression Suite
Executed the entire project unit test suite:
```bash
./gradlew testDebugUnitTest
```
**Result**: `BUILD SUCCESSFUL in 1m 35s` (32 actionable tasks, 1 executed, 31 up-to-date, 0 failures across the entire application).

---

## 4. Traceability & Stage Status

| Artifact / Entity | ID | Status | Notes |
|:---|:---|:---|:---|
| Requirement | `REQ-SET-072` | **Verified** | Traceability updated in `docs/requirements.md` |
| Test Case | `TST-SET-061` | **Verified** | Traceability updated in `docs/tests.md` |
| Parent Ticket | `ATT-956` | **In Review** | Ready for Gate 5 audit and human approval |
| SWE.1 Sub-Task | `ATT-957` | **Erledigt** | Stage 1 approved |
| SWE.4 Sub-Task | `ATT-958` | **Erledigt** | Stage 2 approved |
| SWE.2/3 Sub-Task | `ATT-960` | **Erledigt** | Stage 3 approved |
| SWE.3 Sub-Task | `ATT-959` | **In Review** | Code and unit tests verified |
