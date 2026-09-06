# Walkthrough - ATT-318: Explicit Workout Cluster Assignment

## Overview
Ticket: **ATT-318** (`[Feature] Edit Workout: No Cluster, New Cluster selection`)  
Epic: **ATT-176**  
Requirements: **REQ-SET-059**  
Tests: **TST-SET-050**  
Implementation Sub-task: **ATT-669**

This ticket replaces the subtle trailing-icon cluster selector inside the workout name text field with an explicit, dedicated cluster selector field directly below the workout name in `EditWorkoutScreen`. It harmonizes the cluster selection UI with the TCX import dialog (`RouteClusterNamingDialog`), adding explicit support for:
1. **Leave Unclustered** (`cluster_naming__leave_unclustered` / "Ungruppiert lassen")
2. **Create New...** (`cluster_naming__create_new` / "Neu erstellen...") with inline custom name field
3. **Candidate Routes list** sorted by spatial similarity score
4. **Quick Clear (✕)** button in the selector field when a cluster is assigned

---

## Key Changes Made

### 1. Database & Domain Layer
- [`WorkoutClusterEngine.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt):
  - Added `unassignClusterFromWorkout(context, workoutId)` to cleanly decrement cluster hit count (clearing preview paths if hit count reaches 0) and set `CLUSTER_ID = -1L`.
  - Added `createNewClusterFromWorkout(context, workout, customName)` to create a new `WorkoutCluster` from the workout's spatial fingerprint, insert it, and assign the workout.
  - Guarded `assignClusterToWorkout` to delegate to `unassignClusterFromWorkout` if `clusterId <= 0L`.
  - Safely parsed `BSportType` enum strings.
  - Added test helpers `resetForTesting()` to `WorkoutClusterEngine`, `WorkoutClusterDatabaseManager`, and `EquipmentAndSportTypeDiscoveryManager`.

### 2. Repository & ViewModel
- [`WorkoutRepository.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt):
  - Added `unassignClusterFromWorkout` and `createNewClusterFromWorkout` coroutine dispatchers running on `Dispatchers.IO`.
- [`EditWorkoutViewModel.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt):
  - Preserved `clusterName` across UI merge passes.
  - Added `unassignCluster()` and `createNewCluster(customName)` ViewModel methods.

### 3. UI Layer
- [`WorkoutClusterComponents.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt):
  - Implemented `EditWorkoutClusterDialog` composable styled and structured identically to `RouteClusterNamingDialog`:
    - Leave unclustered option (with check/cross icon and selected highlighting).
    - Create new cluster option with expand/collapse and inline `OutlinedTextField`.
    - Horizontal divider with title "Vorgeschlagene Trainingseinheiten (niedriger Score ist besser)".
    - Scrollable list of candidate routes showing sport icon, route name, score, and hit count description.
    - Cancel button ("Abbrechen").
- [`EditWorkoutScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt):
  - Removed subtle trailing icon inside the workout name text field.
  - Added dedicated read-only `OutlinedTextField` labeled "Lieblingsstrecke" (Favorite Route / Cluster) positioned prominently below the workout name.
  - Added leading route pin icon.
  - Added quick clear button ("✕") when a cluster is currently assigned.
  - Added trailing route selection pin icon.
  - Tapping the field or trailing pin opens `EditWorkoutClusterDialog`.

---

## Verification & Screenshots

### 1. Automated Tests
- Ran dedicated unit test suite:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.EditWorkoutClusterTest"
  ```
  Result: **Passed (4/4 tests)**.
- Ran full unit test suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```
  Result: **Passed (100% build & test success, 0 regressions)**.

### 2. Physical Device Verification (Google Pixel 10)
Captured the user experience flow on Google Pixel 10:
- Dedicated cluster selector field beneath workout name
- Cluster assignment dialog with Leave Unclustered, Create New..., and candidate routes
- Quick clear (✕) button instantly unassigning the cluster
- Re-assignment updating workout name with count suffix and cluster identity
