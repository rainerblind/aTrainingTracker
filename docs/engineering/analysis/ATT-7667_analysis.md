# Stage 1 Analysis: ATT-7667 - Immediate Workout Name Update on Route Cluster Change

**Ticket**: [ATT-7667](https://rainerblind.atlassian.net/browse/ATT-7667)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Track Your Running & Cycling Workouts (Activity Recording)*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.6` | **Branch**: `feat/ATT-7667`  
**Author**: AI Agent 2 (Pro) | **Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
When editing a workout in `EditWorkoutScreen`, changing the route cluster (Lieblingsstrecke) or unassigning it via `EditWorkoutClusterDialog` does not immediately update the workout name consistently in the UI, or preserves stale manual override flags that suppress the new cluster's formatted name during asynchronous repository loads.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA Gap 1 (Flag Persistence)**: In `EditWorkoutViewModel.kt`, `userManuallyChangedWorkoutName` tracks whether the user manually typed in the name field. When `applyClusterIdentity(cluster)` or `createNewCluster(...)` is invoked, `userManuallyChangedWorkoutName` is not reset to `false`. If the user previously touched or typed in the name field, subsequent cluster selections retain the stale custom name during asynchronous `WorkoutData` flow merges in `loadWorkoutData()`.
* **RCA Gap 2 (Unassign Name Handling)**: In `EditWorkoutViewModel.unassignCluster()`, only `clusterId = -1L` and `clusterName = null` are updated in `_workoutData`. The existing cluster-derived name is left intact without resetting to the default unclustered seed name, leaving stale cluster naming on unassigned workouts.
* **RCA Gap 3 (Identity Sync Order)**: `repository.assignClusterToWorkout(workoutId, cluster.id)` reloads data asynchronously. If `userManuallyChangedWorkoutName` remains true, `loadWorkoutData()` keeps `current.workoutName` rather than adopting the freshly assigned cluster's name.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * In `EditWorkoutViewModel.applyClusterIdentity(cluster)`: immediately reset `userManuallyChangedWorkoutName = false`, update `_workoutData` with `formattedName`, and persist to repository.
  * In `EditWorkoutViewModel.createNewCluster(...)`: immediately reset `userManuallyChangedWorkoutName = false`, update `_workoutData` with `resolvedWorkoutName`.
  * In `EditWorkoutViewModel.unassignCluster()`: reset `userManuallyChangedWorkoutName = false`, update `_workoutData` to clear cluster identity and restore unclustered fallback seed name.
  * Unit test coverage in `EditWorkoutViewModelTest.kt` verifying immediate name update on cluster selection, unassign, and manual typing override preservation.
* **Out-of-Scope Non-Goals**:
  * No changes to SQLite cluster clustering algorithms or database schemas.
  * No UI layout alterations in `EditWorkoutScreen.kt`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SET-059` (*Explicit Workout Cluster Management & Assignment in Editor*).
* **Historical Origin & Commit Trace**: `ATT-7754` introduced unassign capability (`clusterId = -1L`). `ATT-388` introduced immediate persistence of cluster identity.
* **Root Reason for Existing Formulation**: `userManuallyChangedWorkoutName` was introduced to prevent background repo loads from clobbering in-progress user text input.
* **Preservation of Core Invariants**: An explicit user action to select or create a cluster in the dialog expresses intent to adopt that cluster's identity; thus `userManuallyChangedWorkoutName` must reset to `false`. Explicit text input typed *after* selecting a cluster sets `userManuallyChangedWorkoutName = true` and must be preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-SET-059` Clause 5 (immediate workout name synchronization upon cluster selection or unassignment).
* **Affected Architecture Elements**: `EditWorkoutViewModel.kt`, `EditWorkoutScreen.kt`, `WorkoutClusterEngine.kt`.
* **Verification Impact & Test Surface**: `EditWorkoutViewModelTest.kt`, `WorkoutClusterEngineTest.kt`, living test specification `TST-SET-105`.
* **Risk Assessment & Invariants**: Low risk. Isolated to ViewModel state management; preserves user manual edits when typed after cluster selection.

---

## 6. Architectural Strategy & High-Level Solution
* In `EditWorkoutViewModel.kt`:
  * Add reset of `userManuallyChangedWorkoutName = false` inside `applyClusterIdentity(cluster)` and `createNewCluster(...)`.
  * In `unassignCluster()`: reset `userManuallyChangedWorkoutName = false`, clear cluster fields, and update `workoutName` to fallback unclustered name (`fileBaseName` or sport name).
  * Ensure `onWorkoutNameChanged` sets `userManuallyChangedWorkoutName = true` only when user actively edits the text field.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Selecting a cluster immediately updates `workoutName` in `_workoutData` and clears `userManuallyChangedWorkoutName`.
  2. Manual typing in the name field sets `userManuallyChangedWorkoutName = true`.
  3. Subsequent repository reloads do not overwrite custom user-typed names if `userManuallyChangedWorkoutName == true`.
* **Risk Rating**: **LOW** — Pure state management in ViewModel.
