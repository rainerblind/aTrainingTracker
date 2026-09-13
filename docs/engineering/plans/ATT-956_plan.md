# Implementation Plan: Cluster Creation Lifecycle & Name Synchronization in Edit Workout (ATT-956)

## 1. Executive Summary & Objective
The objective of **ATT-956** is to fix the lifecycle and state synchronization when creating a new cluster from within `EditWorkoutScreen`:
1. **Pre-fill Ergonomics**: Pre-fill the "New Route Name" field in `EditWorkoutClusterDialog` with `WorkoutClusterEngine.stripHitCount(initialWorkoutName)`.
2. **Deterministic Database Synchronization (`forceIdentity = true`)**: Ensure `WorkoutClusterEngine.createNewClusterFromWorkout` updates `WorkoutSummaries.WORKOUT_NAME` and `CLUSTER_ID` in SQLite.
3. **Smart Workout Name Preservation vs. Auto-Naming**:
   - If the athlete manually typed a custom workout name (`userManuallyChangedWorkoutName == true`), preserve it and do not overwrite it.
   - If the workout name was not manually customized, update it to the newly created cluster's name.
4. **Synchronous UI State**: Update `EditWorkoutViewModel._workoutData` synchronously upon cluster creation so both "Workout Name" and "Selected Route" update immediately upon dialog dismissal.
5. **Reload Resistance**: Protect user-typed custom workout names from being overwritten by asynchronous `repository.workout` emissions during background reloads.

---

## 2. Traceability & Mapped Entities

| Artifact | Identifier | Details |
|:---|:---|:---|
| **Requirement** | `REQ-SET-072` | Edit Workout New Cluster Creation & Name Synchronization (`docs/requirements.md`) |
| **Test Specification** | `TST-SET-061` | Edit Workout New Cluster Creation & Name Synchronization Verification (`docs/tests.md`) |
| **Parent Ticket** | `ATT-956` | `[Bug] Creating a new cluster did not work properly` (Epic: `ATT-176`) |
| **Analysis Sub-task** | `ATT-958` | `[Analysis] Creating a new cluster did not work properly` (`Erledigt` - Gate 1 Approved) |
| **Test Sub-task** | `ATT-959` | `[Test-Spec] Creating a new cluster did not work properly` (`Erledigt` - Gate 2 Approved) |
| **Plan Sub-task** | `ATT-960` | `[Impl-Plan] Creating a new cluster did not work properly` (`In Bearbeitung` - Stage 3) |

---

## 3. Detailed Software Design & File Changes (SWE.2 / SWE.3)

### 3.1 Dialog Pre-Fill Component ([WorkoutClusterComponents.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt))
* In `EditWorkoutClusterDialog`:
  ```kotlin
  var newClusterName by remember(initialWorkoutName) {
      mutableStateOf(WorkoutClusterEngine.stripHitCount(initialWorkoutName))
  }
  ```
  Ensures that when editing a workout named `"Hausrunde #3"`, opening the create route section pre-fills `"Hausrunde"` rather than requiring manual erasure of `#3`.

### 3.2 Storage & Engine Layer ([WorkoutClusterEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt))
* **Enhance `assignClusterToWorkout`**:
  ```kotlin
  fun assignClusterToWorkout(
      context: Context,
      workoutId: Long,
      clusterId: Long,
      forceIdentity: Boolean = true,
      customWorkoutName: String? = null
  )
  ```
  - If `customWorkoutName != null`, persist `values.put(WorkoutSummaries.WORKOUT_NAME, customWorkoutName)`.
  - Else if `forceIdentity || currentName.isNullOrEmpty() || currentName == fileBaseName`:
    Persist `formatClusterWorkoutName(context, cluster.name, displayCount, cluster.hasCounter)`.
* **Update `createNewClusterFromWorkout`**:
  Accept optional parameter `customWorkoutName: String? = null`.
  Pass `forceIdentity = true` and `customWorkoutName` into `assignClusterToWorkout`.

### 3.3 Repository Layer ([WorkoutRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt))
* Expose `customWorkoutName: String? = null` in:
  - `fun createNewClusterFromWorkout(workout: WorkoutData, customName: String? = null, hasCounter: Boolean = true, customWorkoutName: String? = null)`
  - `fun createNewClusterFromWorkout(workoutId: Long, customName: String? = null, hasCounter: Boolean = true, customWorkoutName: String? = null)`
  Propagating the custom workout name to `WorkoutClusterEngine`.

### 3.4 ViewModel Layer ([EditWorkoutViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt))
* Track custom name edits:
  Add `var userManuallyChangedWorkoutName: Boolean = false; private set`.
  In `updateWorkoutName(newName: String)`: set `userManuallyChangedWorkoutName = true`.
* In `repository.workout.collect`:
  When a database reload arrives, if `userManuallyChangedWorkoutName == true`, retain `workoutName = _workoutData.value?.workoutName ?: data.workoutName` so the user's input is never wiped.
* In `createNewCluster(customName: String, hasCounter: Boolean = true)`:
  - If `userManuallyChangedWorkoutName`:
    - `customWorkoutName = _workoutData.value?.workoutName`
    - `resolvedWorkoutName = current.workoutName`
  - Else:
    - `resolvedWorkoutName = WorkoutClusterEngine.formatClusterWorkoutName(application, customName.trim(), 1, hasCounter)`
    - `customWorkoutName = null`
  - Update `_workoutData` in memory synchronously:
    `_workoutData.update { current -> current?.copy(clusterName = customName.trim(), workoutName = resolvedWorkoutName) }`
  - Invoke `repository.createNewClusterFromWorkout(current, customName, hasCounter, customWorkoutName)`.

---

## 4. Test & Verification Plan

### 4.1 Unit Tests (`EditWorkoutClusterTest.kt`)
* **Test 1: Dialog Pre-Fill**: Verify `stripHitCount("Hausrunde #3")` yields `"Hausrunde"`.
* **Test 2: `WorkoutClusterEngine.createNewClusterFromWorkout`**:
  - Non-customized: Existing name `"Hausrunde #3"`, `customWorkoutName = null` -> updates SQLite `WORKOUT_NAME` to `"Neue Runde"`, `CLUSTER_ID` to new ID.
  - Customized: Existing name `"Hausrunde #3"`, `customWorkoutName = "Mein Lauf"` -> updates SQLite `WORKOUT_NAME` to `"Mein Lauf"`, `CLUSTER_ID` to new ID.
  - Verify previous cluster hit count is decremented and new cluster hit count is 1.
* **Test 3: `EditWorkoutViewModel` State & Reload Resistance**:
  - Verify `_workoutData` updates synchronously on `createNewCluster`.
  - Verify `repository.workout.collect` preserves typed workout name when `userManuallyChangedWorkoutName == true`.
* **Full Unit Test Suite**: `./gradlew testDebugUnitTest`.

### 4.2 Device Verification (Pixel 10)
* Deploy build to connected device (`66020DLCR002FL`).
* Test manual workflow:
  1. Open a workout clustered to `"Hausrunde #3"`.
  2. Tap Route field -> verify create new route field pre-fills `"Hausrunde"`.
  3. Enter `"Neue Runde"` -> tap OK.
  4. Verify screen immediately displays `"Neue Runde"` in Workout Name and Route Name.
  5. Tap Save -> verify list displays `"Neue Runde"` linked to the new cluster.
  6. Repeat with manual workout name entered before opening dialog -> verify manual name is retained while route updates.

---

## 5. Review Gates & Sign-Off
* Sub-task `ATT-960`:
  * Draft plan deliverable.
  * Agent 2 conducts independent review and posts audit comment.
  * Agent 2 moves `ATT-960` to `Freigabe (Human)`.
  * User approves and moves `ATT-960` to `Erledigt`.
