# Stage 2: Requirement & Test Specification - ATT-7754: Add option to unassign Lieblingsstrecke in Move Workout cluster dialog and refresh inspected workout state

**Ticket**: [ATT-7754](https://rainerblind.atlassian.net/browse/ATT-7754)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-SET-059` (*Explicit Workout Cluster Management & Assignment*)  
**Test Spec ID**: `TST-SET-104` (*Move Workout Dialog Unassign and State Synchronization*)  
**Branch**: `feat/ATT-7754`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-SET-059 Amendment)

### 1.1 Problem Statement & Rationale
Athletes managing workouts in `WorkoutClustersScreen` or `WorkoutClusterHeatmapScreen` need the ability to remove a workout from its current favorite route without opening the full edit workout screen. Providing an explicit "Keiner Route zuordnen" action in `WorkoutClusterSelectionDialog` aligns the move experience with `EditWorkoutClusterDialog`, ensures immediate reactive state updates across repositories and UI models, and keeps inspected workout metadata synchronized upon returning from editing.

### 1.2 Attributes
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration

### 1.3 Functional & Architectural Requirements
1. **Move Dialog Unassign Option**:
   - `WorkoutClusterSelectionDialog` SHALL accept an optional `onUnassign: (() -> Unit)? = null` parameter.
   - When `onUnassign != null`, the dialog SHALL render an explicit "Keiner Route zuordnen" card using `Icons.Default.Clear` and `R.string.cluster_naming__leave_unclustered` above candidate routes.
   - Tapping the unassign card SHALL invoke `onUnassign()` and dismiss the dialog.
2. **Move Dialog Escape Hatch & Action Bounding**:
   - In `WorkoutClustersScreen.kt` and `WorkoutClusterHeatmapScreen.kt`, `onUnassign` SHALL only be passed when the targeted workout is currently clustered (`workout.clusterId > 0`).
   - `WorkoutClusterSelectionDialog` when invoked for cluster merging (`clusterToMerge`) SHALL NOT supply `onUnassign`.
3. **Repository Reactive Refresh**:
   - `WorkoutRepository.unassignClusterFromWorkout(workoutId)` SHALL invoke `WorkoutClusterRepository.getInstance(application).refreshClusters()` to ensure cluster counts and unclustered lists are refreshed reactively.
   - `WorkoutClustersViewModel.unassignWorkout(workout)` SHALL execute the unassign operation, refresh cluster flows, and reload UI list state.
4. **Inspected Workout Reactivity**:
   - In `WorkoutClustersScreen.kt`, returning from `EditWorkoutScreen` or modifying a workout's cluster SHALL immediately refresh `inspectedWorkout` to reflect updated workout metadata.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Unassign in Move Dialog)**:
  * *Given* a workout assigned to a favorite route (`clusterId > 0`) in `WorkoutClustersScreen` or `WorkoutClusterHeatmapScreen`,
  * *When* the athlete taps the $\leftrightarrows$ ("Einheit verschieben") action,
  * *Then* the bottom sheet displays "Keiner Route zuordnen" at the top, and selecting it unassigns the workout (`clusterId = -1L`), decrements the old cluster's hit count ($\ge 0$), and refreshes the list reactively.
* **Criterion 2 (Unclustered Workout Move Dialog)**:
  * *Given* an unclustered workout (`clusterId <= 0`),
  * *When* the athlete taps the $\leftrightarrows$ action,
  * *Then* "Keiner Route zuordnen" is NOT displayed.
* **Criterion 3 (Inspected Workout Synchronization)**:
  * *Given* the athlete is inspecting a workout in `WorkoutClustersScreen` and navigates to `EditWorkoutScreen`,
  * *When* the athlete updates or unassigns the cluster and returns,
  * *Then* `inspectedWorkout` immediately displays the updated cluster assignment and name.

### 1.5 System Invariants
- `clusterToMerge` dialog MUST NEVER display "Keiner Route zuordnen".
- Atomicity: previous cluster hit counts must never drop below 0 (`coerceAtLeast(0)`).
- Mutual exclusion on `clusterLock` and IO dispatchers must be maintained.

---

## 2. Test Specification (TST-SET-104)

### Test Case 1: `testWorkoutClusterSelectionDialog_whenOnUnassignProvided_rendersCard` (`TST-SET-104.1`)
* **Scope**: Unit / Composable Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterSelectionDialogTest.kt`
* **Preconditions**: Dialog launched with non-null `onUnassign` callback.
* **Action**: Render dialog composable and verify presence of unassign action.
* **Expected Result**: Unassign card is present and invokes callback when clicked.

### Test Case 2: `testWorkoutClustersViewModel_unassignWorkout_updatesRepository` (`TST-SET-104.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModelTest.kt`
* **Preconditions**: Seeded workout with `clusterId = 10L`.
* **Action**: Invoke `viewModel.unassignWorkout(workout)`.
* **Expected Result**: `WorkoutRepository.unassignClusterFromWorkout` is invoked, `refreshClusters()` is triggered, and `clusterWorkouts` / `unclusteredWorkouts` reflect unassigned state.

### Test Case 3: `testWorkoutRepository_unassignClusterFromWorkout_triggersRefreshClusters` (`TST-SET-104.3`)
* **Scope**: Integration / Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepositoryClusteringTest.kt`
* **Preconditions**: Seeded workout in a cluster.
* **Action**: Call `WorkoutRepository.unassignClusterFromWorkout(workoutId)`.
* **Expected Result**: Cluster engine unassigns workout, and `WorkoutClusterRepository.refreshClusters()` is verified to have been called.

### Test Case 4: 9-Language Localization & Specifier Audit (`TST-SET-104.4`)
* **Scope**: Localization Parity Test
* **Target File**: `TranslationParityTest.kt`
* **Goal**: Verify string presence and matching `%s`/`%d` tokens for `cluster_naming__leave_unclustered` and `cluster_naming__create_new` across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
* **Expected Result**: 100% parity, 0 missing strings.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-SET-104.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.database.EditWorkoutClusterTest"`
* **Goal**: Verify 100% pass rate across cluster UI and database components.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-104.1` | UI Contract | `WorkoutClusterSelectionDialog` | `REQ-SET-059`, `REQ-UI-149` | Specified |
| `TST-SET-104.2` | ViewModel | `WorkoutClustersViewModel.unassignWorkout` | `REQ-SET-059` | Specified |
| `TST-SET-104.3` | Repository | `WorkoutRepository.unassignClusterFromWorkout` | `REQ-SET-059` | Specified |
| `TST-SET-104.4` | Localization | `TranslationParityTest` | `REQ-SET-059`, `REQ-UI-106` | Specified |
| `TST-SET-104.5` | Regression | Tier 1 Package Test Suite | `REQ-PRO-001` | Specified |
