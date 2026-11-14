# Stage 1 Analysis: ATT-7754 - Add option to unassign Lieblingsstrecke in Move Workout cluster dialog and refresh inspected workout state

**Ticket**: [ATT-7754](https://rainerblind.atlassian.net/browse/ATT-7754)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.6` | **Branch**: `feat/ATT-7754`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
In `WorkoutClustersScreen.kt` (lines 181–193, 388–401) and `WorkoutClusterHeatmapScreen.kt` (lines 319–333), tapping the $\leftrightarrows$ ("Einheit verschieben" / Move Workout) action button opens `WorkoutClusterSelectionDialog`. Currently, this dialog only lists candidate route clusters (`Vorgeschlagene Trainingseinheiten`). It lacks the "Keiner Route zuordnen" (`R.string.cluster_naming__leave_unclustered`) action (and `onCreateNew` is unused), forcing athletes to open the full `EditWorkoutScreen` via the pencil icon merely to unassign a route cluster. Furthermore, unassigning a workout does not refresh `WorkoutClusterRepository`, and `inspectedWorkout` in `WorkoutClustersScreen.kt` retains stale workout data when returning from `EditWorkoutScreen`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Selection Dialog Gap**: `WorkoutClusterSelectionDialog` in `WorkoutClusterComponents.kt` accepts `candidates` and `onCreateNew`, but lacks an `onUnassign: (() -> Unit)? = null` parameter and card UI.
* **Repository & ViewModel Gap**:
  * `WorkoutClustersViewModel.kt` provides `moveWorkout(...)`, but lacks `unassignWorkout(workout: WorkoutData)` to unassign, refresh `WorkoutClusterRepository`, and update `_clusterWorkouts` and `_unclusteredWorkouts`.
  * In `WorkoutRepository.kt` (line 4450), `unassignClusterFromWorkout(workoutId)` invokes `WorkoutClusterEngine.unassignClusterFromWorkout()`, but fails to call `WorkoutClusterRepository.getInstance(application).refreshClusters()`, unlike `assignClusterToWorkout(...)` (line 4436).
* **Inspected Workout Reactivity Gap**: `WorkoutClustersScreen.kt` stores a static `inspectedWorkout` state (`mutableStateOf<WorkoutData?>`) that is not updated when `EditWorkoutScreen` dismisses or when the workout's cluster changes.
* **Independent Hypothesis Verification**:
  * Verified: `WorkoutClusterSelectionDialog` is also used for cluster merging (`clusterToMerge`, lines 335–345). `onUnassign` must remain optional (`null` default) so merge sheets never render "Leave Unclustered".
  * Verified: String `R.string.cluster_naming__leave_unclustered` and icon `Icons.Default.Clear` already exist in all 9 locales.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Add `onUnassign: (() -> Unit)? = null` to `WorkoutClusterSelectionDialog` (rendering "Keiner Route zuordnen" card when non-null).
  * Wire `onUnassign` and `onCreateNew` in `WorkoutClustersScreen.kt` (inspected workout & cluster list) and `WorkoutClusterHeatmapScreen.kt`.
  * Add `unassignWorkout(workout: WorkoutData)` and `createNewClusterFromWorkout(workout: WorkoutData)` to `WorkoutClustersViewModel`.
  * Ensure `WorkoutRepository.unassignClusterFromWorkout(...)` calls `refreshClusters()`.
  * Reactively sync `inspectedWorkout` in `WorkoutClustersScreen.kt` on edit return and state flow changes.
* **Out-of-Scope Non-Goals**:
  * Do not modify `clusterToMerge` merge dialog logic.
  * No database schema migrations or clustering algorithm adjustments.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SET-059` (*Explicit Workout Cluster Management & Assignment in Editor*) and `REQ-UI-149` (*Modernized Modal Bottom Sheet Dialogs & Selection Flows*).
* **Historical Origin & Commit Trace**: Commit `bcfd499e` (ATT-318) introduced explicit cluster assignment/unassignment in `EditWorkoutScreen`.
* **Root Reason for Existing Formulation**: `WorkoutClusterSelectionDialog` was created as a lightweight picker for move/merge operations, omitting unassign because unassignment was initially only modeled in the full workout editor.
* **Preservation of Core Invariants**: Reassigning or clearing a cluster must atomically decrement the previous cluster's hit count ($\ge 0$), maintain `clusterLock` mutual exclusion, and preserve 100% test pass rate.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-SET-059` (amended to cover quick move/unassign in cluster views), `REQ-UI-149`.
* **Affected Architecture Elements**: `WorkoutClusterComponents.kt`, `WorkoutClustersScreen.kt`, `WorkoutClusterHeatmapScreen.kt`, `WorkoutClustersViewModel.kt`, `WorkoutRepository.kt`.
* **Verification Impact & Test Surface**: `WorkoutClustersViewModelTest`, `EditWorkoutClusterTest`, `WorkoutClusterSelectionDialog` UI contract assertions.
* **Risk Assessment & Invariants**: **LOW** risk. Backwards-compatible composable signature, reuses existing string resources and database methods.

---

## 6. Architectural Strategy & High-Level Solution
* Add `onUnassign: (() -> Unit)? = null` to `WorkoutClusterSelectionDialog`. Render `R.string.cluster_naming__leave_unclustered` card at the top when non-null.
* In `WorkoutClustersViewModel`: add `unassignWorkout(workout: WorkoutData)` and `createNewClusterFromWorkout(workout: WorkoutData)` dispatching on `ioDispatcher`, calling repository methods and refreshing cluster/unclustered flows.
* In `WorkoutClustersScreen.kt` & `WorkoutClusterHeatmapScreen.kt`: pass `onUnassign = if (workout.clusterId > 0) { ... } else null` and `onCreateNew = { ... }`.
* In `WorkoutRepository.kt`: add `WorkoutClusterRepository.getInstance(application).refreshClusters()` to `unassignClusterFromWorkout(workoutId)`.
* In `WorkoutClustersScreen.kt`: observe `summariesViewModel.workouts` to automatically refresh `inspectedWorkout` when updated.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `clusterToMerge` dialog MUST NOT show "Keiner Route zuordnen".
  2. Hit count of old cluster is atomically decremented without dropping below 0.
  3. No snapshot state writes during composition (`Rule 66`).
* **Risk Rating**: **LOW** — UI sheet enhancement and reactive flow synchronization.
