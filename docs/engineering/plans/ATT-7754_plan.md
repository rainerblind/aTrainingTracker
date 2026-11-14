# Stage 3: Implementation Plan - ATT-7754: Add option to unassign Lieblingsstrecke in Move Workout cluster dialog and refresh inspected workout state

**Ticket**: [ATT-7754](https://rainerblind.atlassian.net/browse/ATT-7754)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-SET-059` (*Explicit Workout Cluster Management & Assignment*)  
**Test Mapping**: `TST-SET-104` (*Move Workout Dialog Unassign and State Synchronization*)  
**Branch**: `feat/ATT-7754`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Description & Background
In `WorkoutClustersScreen` and `WorkoutClusterHeatmapScreen`, tapping $\leftrightarrows$ ("Einheit verschieben") opens `WorkoutClusterSelectionDialog`. It lists candidate clusters, but lacks the "Keiner Route zuordnen" (`cluster_naming__leave_unclustered`) action, forcing users to open `EditWorkoutScreen` to unassign routes. Additionally, `WorkoutRepository.unassignClusterFromWorkout()` fails to trigger `WorkoutClusterRepository.refreshClusters()`, and `inspectedWorkout` in `WorkoutClustersScreen` retains stale data when returning from `EditWorkoutScreen`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SET-059` Clause 4 (*Move Workout Dialog Unassignment & Reactive State Refresh*)
* **Test Mapping**: `TST-SET-104` (`TST-SET-104.1` UI Contract, `TST-SET-104.2` ViewModel, `TST-SET-104.3` Repository, `TST-SET-104.4` Localization, `TST-SET-104.5` Regression)

---

## 3. System Invariants & Preserved Behavior
1. **Cluster Merge Dialog Invariance**: `clusterToMerge` (lines 335–345) merges clusters; `onUnassign` defaults to `null` and MUST NOT appear during merge.
2. **Hit Count Atomic Decrement**: Decrementing cluster hit counts must remain clamped to $\ge 0$ without corrupting `previewPaths`.
3. **Dispatcher & Lock Affinity**: All database and repository operations remain on `Dispatchers.IO` under `clusterLock`.
4. **No Snapshot State Writes During Composition**: `inspectedWorkout` state updates occur strictly within `LaunchedEffect` or callback handlers (`Rule 66`).

---

## 4. Proposed Architectural Changes

### Component 1: `WorkoutClusterComponents.kt`
* Extend `WorkoutClusterSelectionDialog` signature with `onUnassign: (() -> Unit)? = null`.
* If `onUnassign != null`, render "Keiner Route zuordnen" `Card` with `Icons.Default.Clear` and `R.string.cluster_naming__leave_unclustered` matching `EditWorkoutClusterDialog` visual styling.

### Component 2: Repository & Engine Layer
* `WorkoutClusterRepository.kt`: Add `fun refreshClustersAsync()` launching `refreshClusters()` on `repositoryScope` (`Dispatchers.IO`).
* `WorkoutClusterEngine.kt`: In `unassignClusterFromWorkout(context, workoutId)`, trigger `WorkoutClusterRepository.getInstance(context).refreshClustersAsync()`.
* `WorkoutRepository.kt`: In `unassignClusterFromWorkout(workoutId)`, invoke `WorkoutClusterRepository.getInstance(application).refreshClusters()`.

### Component 3: `WorkoutClustersViewModel.kt`
* Add `fun unassignWorkout(workout: WorkoutData)`: calls `WorkoutRepository.unassignClusterFromWorkout`, `repository.refreshClusters()`, and refreshes `_clusterWorkouts` / `_unclusteredWorkouts`.
* Add `fun createNewClusterFromWorkout(workout: WorkoutData)`: creates cluster via engine, refreshes repository and workout lists.

### Component 4: `WorkoutClustersScreen.kt` & `WorkoutClusterHeatmapScreen.kt`
* In `WorkoutClustersScreen`: wire `onUnassign` and `onCreateNew` for `inspectedWorkout` (lines 181–193) and cluster list (lines 388–401).
* In `WorkoutClusterHeatmapScreen`: wire `onUnassign` and `onCreateNew` for `workoutToMove` (lines 319–333).
* In `WorkoutClustersScreen`: update `inspectedWorkout` on `EditWorkoutScreen` `onBack` and via `LaunchedEffect(allWorkouts, inspectedWorkout?.id)`.

### UI Consistency (Rule 23)
* **Reference screen / component**: `EditWorkoutClusterDialog` in `WorkoutClusterComponents.kt` (lines 858–891).
* **Reused components**: Material 3 `Card`, `Icons.Default.Clear`, `Icons.Default.Add`, `cluster_naming__leave_unclustered`, `cluster_naming__create_new`.
* **Theme tokens**: `MaterialTheme.colorScheme.surfaceVariant`, `onSurfaceVariant`, `primary`.
* **New one-off styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Repository & Engine Unassign Refresh
* Files: `WorkoutClusterRepository.kt`, `WorkoutClusterEngine.kt`, `WorkoutRepository.kt`
* Changes: Add `refreshClustersAsync()` to `WorkoutClusterRepository`; call `refreshClusters()` in `WorkoutRepository.unassignClusterFromWorkout` and `refreshClustersAsync()` in `WorkoutClusterEngine.unassignClusterFromWorkout`.

### Step 2: ViewModel Operations
* Files: `WorkoutClustersViewModel.kt`
* Changes: Implement `unassignWorkout(workout: WorkoutData)` and `createNewClusterFromWorkout(workout: WorkoutData)`.

### Step 3: Selection Dialog UI Extension
* Files: `WorkoutClusterComponents.kt`
* Changes: Add `onUnassign: (() -> Unit)? = null` to `WorkoutClusterSelectionDialog` and render unassign card.

### Step 4: Screen Wiring & Reactive State Sync
* Files: `WorkoutClustersScreen.kt`, `WorkoutClusterHeatmapScreen.kt`
* Changes: Pass `onUnassign` and `onCreateNew` callbacks; implement reactive `inspectedWorkout` synchronization in `WorkoutClustersScreen.kt`.

### Step 5: Unit & Contract Tests
* Files: `EditWorkoutClusterTest.kt`, `WorkoutClustersViewModelTest.kt`, `WorkoutClusterSelectionDialogTest.kt`
* Verification: Run Tier 1 test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.database.EditWorkoutClusterTest"`.

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit and UI tests during construction. Validate living doc governance via `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Changes isolated on branch `feat/ATT-7754`; clean revert via Git branch reset if needed.
