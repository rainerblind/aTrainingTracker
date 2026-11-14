# Stage 2: Requirement & Test Specification - ATT-7667: Immediate Workout Name Update on Route Cluster Change

**Ticket**: [ATT-7667](https://rainerblind.atlassian.net/browse/ATT-7667)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-SET-059` (*Explicit Workout Cluster Management & Assignment in Editor*)  
**Test Spec ID**: `TST-SET-105` (*Immediate Workout Name Synchronization on Route Cluster Change*)  
**Branch**: `feat/ATT-7667`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-SET-059 Clause 5 Amendment)

### 1.1 Problem Statement & Rationale
When an athlete edits a workout and changes or unassigns the route cluster (Lieblingsstrecke) via `EditWorkoutClusterDialog`, the workout name input field must immediately update to reflect the newly selected route's auto-generated name (or clear cluster naming when unassigned). If the user had previously touched the name field, internal tracking flags must not permanently suppress subsequent explicit route selections, while still protecting user-typed names entered after selecting a route.

### 1.2 Attributes
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration

### 1.3 Functional & Technical Specifications
1. **Cluster Selection Name Synchronization**:
   - In `EditWorkoutViewModel.applyClusterIdentity(cluster: WorkoutCluster)`, the method SHALL update `_workoutData` with `formattedName` and set `userManuallyChangedWorkoutName = false`.
   - Subsequent emissions from `repository.initialWorkoutLoaded` SHALL NOT overwrite the formatted name with stale pre-selection text.
2. **New Cluster Creation Name Synchronization**:
   - In `EditWorkoutViewModel.createNewCluster(customName, hasCounter)`, the method SHALL update `_workoutData` with `resolvedWorkoutName` and reset `userManuallyChangedWorkoutName = false`.
3. **Cluster Unassignment Name Reversion**:
   - In `EditWorkoutViewModel.unassignCluster()`, the method SHALL update `_workoutData` to set `clusterId = -1L`, `clusterName = null`, reset `userManuallyChangedWorkoutName = false`, and revert `workoutName` to the fallback unclustered name pattern (`fileBaseName` or sport name).
4. **Manual Typing Override Protection**:
   - When the user explicitly modifies the text in `onWorkoutNameChanged(newName)`, `userManuallyChangedWorkoutName` SHALL be set to `true`.
   - When `userManuallyChangedWorkoutName == true`, subsequent asynchronous emissions from `repository.initialWorkoutLoaded` SHALL preserve the user's manual text edit.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Immediate Name Update on Route Selection)**:
  * *Given* a workout open in `EditWorkoutScreen` with an existing name,
  * *When* the user opens `EditWorkoutClusterDialog` and selects a different route cluster,
  * *Then* `workoutName` immediately updates to the formatted route name, and background repo loads retain that name.
* **Criterion 2 (Manual Override Preservation Post-Selection)**:
  * *Given* a route cluster selected in `EditWorkoutScreen`,
  * *When* the user types a custom name into the name field,
  * *Then* `userManuallyChangedWorkoutName` becomes `true` and the custom name is preserved across background repo updates.
* **Criterion 3 (Cluster Unassignment Reverts Name)**:
  * *Given* a workout assigned to a cluster in `EditWorkoutScreen`,
  * *When* the user chooses "Leave Unclustered",
  * *Then* `clusterId` becomes `-1L` and the workout name reverts to the unclustered fallback name.

---

## 2. Test Specification (TST-SET-105)

### Test Case 1: `testApplyClusterIdentity_updatesWorkoutName_andResetsManualFlag` (`TST-SET-105.1`)
* **Target**: `EditWorkoutViewModelTest.kt`
* **Preconditions**: `viewModel` loaded with existing workout where `userManuallyChangedWorkoutName == true`.
* **Action**: Invoke `viewModel.applyClusterIdentity(targetCluster)`.
* **Expected Result**: `_workoutData.value.workoutName` equals cluster formatted name, and `userManuallyChangedWorkoutName` is `false`.

### Test Case 2: `testRepositoryEmission_whenManualFlagFalse_preservesNewClusterName` (`TST-SET-105.2`)
* **Target**: `EditWorkoutViewModelTest.kt`
* **Preconditions**: `applyClusterIdentity` was called (`userManuallyChangedWorkoutName == false`).
* **Action**: Emit a `WorkoutData` update from repository flow with the newly saved cluster data.
* **Expected Result**: `_workoutData.value.workoutName` retains the newly assigned cluster formatted name.

### Test Case 3: `testUnassignCluster_resetsManualFlag_andRevertsToFallbackName` (`TST-SET-105.3`)
* **Target**: `EditWorkoutViewModelTest.kt`
* **Preconditions**: Workout with assigned cluster.
* **Action**: Invoke `viewModel.unassignCluster()`.
* **Expected Result**: `clusterId == -1L`, `clusterName == null`, `userManuallyChangedWorkoutName == false`, and `workoutName` reverts to default unclustered seed name.

### Test Case 4: `testOnWorkoutNameChanged_setsManualFlag_andProtectsAgainstRepoEmissions` (`TST-SET-105.4`)
* **Target**: `EditWorkoutViewModelTest.kt`
* **Preconditions**: Cluster assigned.
* **Action**: Call `viewModel.onWorkoutNameChanged("Custom Athlete Title")` followed by repo flow emission.
* **Expected Result**: `_workoutData.value.workoutName` remains `"Custom Athlete Title"`.

### Test Case 5: `testCreateNewCluster_resetsManualFlag_andUpdatesWorkoutName` (`TST-SET-105.5`)
* **Target**: `EditWorkoutViewModelTest.kt`
* **Preconditions**: Workout open in editor.
* **Action**: Call `viewModel.createNewCluster("Morning Loop", hasCounter = true)`.
* **Expected Result**: `_workoutData.value.workoutName` updates to `"Morning Loop #1"` and `userManuallyChangedWorkoutName == false`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-105.1` | Unit | `EditWorkoutViewModel.applyClusterIdentity` | `REQ-SET-059` | Specified |
| `TST-SET-105.2` | Integration | `EditWorkoutViewModel.loadWorkoutData` Flow Merge | `REQ-SET-059` | Specified |
| `TST-SET-105.3` | Unit | `EditWorkoutViewModel.unassignCluster` | `REQ-SET-059` | Specified |
| `TST-SET-105.4` | Unit | `EditWorkoutViewModel.onWorkoutNameChanged` | `REQ-SET-059` | Specified |
| `TST-SET-105.5` | Unit | `EditWorkoutViewModel.createNewCluster` | `REQ-SET-059` | Specified |
