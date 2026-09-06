# Implementation Plan: ATT-318 - Explicit Workout Cluster Selection (No Cluster, New Cluster) in Edit Workout

## 1. Executive Summary & Objective
The objective of **ATT-318** is to replace the subtle trailing-icon cluster selection mechanism in `EditWorkoutScreen.kt` with an explicit, prominent cluster management interface positioned directly below the workout name input field. The interface and selection dialog will mirror the look and feel of the TCX import naming dialog (`RouteClusterNamingDialog` in `ImportBackupTabsScreen.kt`), providing full support for:
1. **Current Cluster Visibility**: Explicitly showing the assigned cluster name or "Unclustered" / "Kein Cluster".
2. **"Leave Unclustered" / "Kein Cluster"**: Explicitly unassigning the workout from any cluster (`clusterId = -1L`) and decrementing the previous cluster's hit count.
3. **"Create New..." / "Neuer Cluster..."**: Creating a brand new `WorkoutCluster` from the workout session's spatial fingerprint and linking it.
4. **Candidate Clusters Selection**: Choosing from spatially matching cluster candidates with similarity scores, hit counts, and sport types.
5. **Quick-Clear Option**: When a cluster is currently assigned, an inline clear action allows immediate unclustering.

---

## 2. Traceability & Mapped Entities

| Artifact | Identifier | Details |
|:---|:---|:---|
| **Requirement** | `REQ-SET-059` | Explicit Workout Cluster Management & Assignment in Editor (`docs/requirements.md`) |
| **Test Specification** | `TST-SET-050` | Explicit Cluster Selection in Edit Workout (`docs/tests.md`) |
| **Parent Ticket** | `ATT-318` | `[Feature] Edit Workout: No Cluster, New Cluster selection` (Epic: `ATT-176`) |
| **Test Sub-task** | `ATT-666` | `[Test] ATT-318: TST-SET-050 - Explicit Cluster Selection (No Cluster, New Cluster) in Edit Workout` |
| **Analysis Sub-task** | `ATT-667` | `[Analysis] ATT-318: Technical Analysis & Dual-Gate Audit` (`Erledigt` - Gate 1 Approved) |
| **Plan Sub-task** | `ATT-668` | `[Plan] ATT-318: Implementation Plan & Dual-Gate Audit` (`In Bearbeitung` - Stage 2) |

---

## 3. System Invariants & Safety Audit (SWE.1.BP.5)

* **REQ-SET-034 & REQ-SET-044 (Cluster Name Isolation)**: Renaming a workout session in `EditWorkoutScreen.kt` MUST NOT mutate the permanent cluster name in `WorkoutClusterDatabaseManager`.
* **REQ-SET-037 (Atomic Cluster Linking & Hit Counts)**: Unassigning a workout MUST decrement the old cluster's hit count (and clear previews if count drops to 0). Assigning or creating a cluster MUST update hit counts atomically in SQLite.
* **Sport & Equipment Safety**: Unclustering or creating a new cluster MUST NOT overwrite user-selected sport or equipment dropdown values unless explicitly confirming a cluster identity change.
* **Localization & Format Safety (`REQ-LOC-001`)**: All required UI strings (`cluster_naming__selected_route_label`, `cluster_naming__leave_unclustered`, `cluster_naming__create_new`, `cluster_naming__title`, `unclustered`) exist and are verified across all 9 supported languages (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 4. Technical Architecture & File Changes

### 4.1 Storage & Engine Layer (`WorkoutClusterEngine.kt`)
* **`unassignClusterFromWorkout(context: Context, workoutId: Long)`**:
  * Reads previous `CLUSTER_ID` from `WorkoutSummariesDatabaseManager`.
  * If `previousClusterId > 0`, fetches `WorkoutCluster`, decrements `hitCount` (coerced at least to 0), clears `previewPaths` if `hitCount == 0`, and updates the cluster in `WorkoutClusterDatabaseManager`.
  * Sets `WorkoutSummaries.CLUSTER_ID = -1L` in SQLite.
* **`createNewClusterFromWorkout(context: Context, workoutId: Long, customName: String?): Long`**:
  * Extracts spatial coordinates (Start, End, Max Displacement / Apex, Distance) and Sport ID from `WorkoutSummariesDatabaseManager`.
  * Instantiates a new `WorkoutCluster` with unique name (via `findUniqueClusterName`) and inserts into `WorkoutClusterDatabaseManager`.
  * Calls `assignClusterToWorkout(context, workoutId, newClusterId, forceIdentity = false)`.
* **Defensive guard in `assignClusterToWorkout`**:
  * If `clusterId <= 0L`, safely delegates to `unassignClusterFromWorkout(context, workoutId)`.

### 4.2 Repository Layer (`WorkoutRepository.kt`)
* Expose `unassignClusterFromWorkout(workoutId: Long)`: executes engine unassign in `scope.launch(Dispatchers.IO)` and calls `loadWorkout(workoutId)`.
* Expose `createNewClusterFromWorkout(workoutId: Long, customName: String?)`: executes engine creation in `scope.launch(Dispatchers.IO)` and calls `loadWorkout(workoutId)`.

### 4.3 ViewModel Layer (`EditWorkoutViewModel.kt`)
* In `loadWorkoutData()`: ensure `clusterName = data.clusterName` is retained during data merge updates.
* Expose `fun unassignCluster()`: sets local `clusterId = -1L, clusterName = null` and calls `repository.unassignClusterFromWorkout(workoutId)`.
* Expose `fun createNewCluster(clusterName: String)`: calls `repository.createNewClusterFromWorkout(workoutId, clusterName)`.
* Expose `fun applyClusterIdentity(cluster: WorkoutCluster)`: preserves existing assignment logic.

### 4.4 Presentation Layer (`EditWorkoutScreen.kt` & Dialog Component)
* Remove the subtle trailing icon from the workout name `OutlinedTextField`.
* Add explicit **Cluster Assignment Selector** directly below the workout name input:
  * Read-only `OutlinedTextField` / Container styled identically to `ImportBackupTabsScreen.kt`:
    * Leading icon: `R.drawable.my_locations` (tinted primary if clustered, onSurfaceVariant if unclustered).
    * Label: `stringResource(R.string.cluster_naming__selected_route_label)` ("Selected Route").
    * Text: Assigned cluster name, or `stringResource(R.string.unclustered)` ("Unclustered").
    * Trailing icons: If clustered, an inline "✕" button (`Icons.Default.Clear`) to uncluster with one tap; a chevron or dropdown button to open the assignment dialog.
    * Clickable surface: Tapping anywhere on the field opens `EditWorkoutClusterDialog`.
* **`EditWorkoutClusterDialog`** (harmonized with `RouteClusterNamingDialog`):
  * **Title**: `stringResource(R.string.cluster_naming__title)` ("Assign Workout to Route").
  * **Action 1 (Leave Unclustered)**: Option to unassign (`cluster_naming__leave_unclustered`).
  * **Action 2 (Create New)**: Option to create a new cluster (`cluster_naming__create_new`), presenting a text field for custom route name (defaulting to current workout name) and a confirm button.
  * **Section (Candidate Routes)**: List of candidate clusters from `clusterSuggestions`, displaying similarity scores, probable sport icons, and hit counts.
  * Dismiss / Cancel button.

---

## 5. Test & Verification Plan

### 5.1 Automated Unit Tests
* Implement unit tests in `EditWorkoutClusterTest.kt` verifying:
  * Unassigning cluster: decrements old cluster hit count, sets `clusterId = -1L` and `clusterName = null`.
  * Creating new cluster: generates unique cluster, calculates fingerprint, sets `hitCount = 1`, and links workout.
  * Reassigning cluster: atomic transition between source and target cluster hit counts.
* Full unit test suite regression check: `./gradlew testDebugUnitTest`.

### 5.2 Device Verification
* Deploy build to connected Pixel 10 (`66020DLCR002FL`).
* Test manual workflow:
  1. Open a clustered workout in Edit Workout -> verify cluster selector displays name.
  2. Tap "✕" or select "Leave Unclustered" in dialog -> verify it displays "Unclustered" and saves with `clusterId = -1L`.
  3. Tap cluster selector, select "Create New..." with name "Morning Ride Test" -> verify new cluster created and assigned.
  4. Tap cluster selector, choose a suggested candidate -> verify cluster reassigned and hit counts updated.
  5. Verify workout header in list view displays matching cluster name.

---

## 6. Review Gates & Sign-Off

* **Gate 2 Sub-task (`ATT-668`)**:
  * Agent 1 drafts plan deliverable.
  * Agent 2 conducts independent review and posts audit comment.
  * Agent 2 moves `ATT-668` to `Freigabe (Human)`.
  * User approves and moves `ATT-668` to `Erledigt`.
