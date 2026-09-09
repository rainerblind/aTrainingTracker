# Implementation Plan: Fix TCX Import "Leave Unclustered" Auto-Clustering Bug (ATT-741)

* **Parent Ticket**: [ATT-741](https://rainerblind.atlassian.net/browse/ATT-741) ([Bug] When importing from TCX and selecting to not cluster a workout, it is still clusterd and thus gives a strange name.)
* **Sub-Task**: [ATT-776](https://rainerblind.atlassian.net/browse/ATT-776) ([Impl-Plan] When importing from TCX and selecting to not cluster a workout...)
* **Target Lösungsversion (Fix Version/s)**: `V4.9.36`
* **Requirement**: `REQ-MIG-025` (*TCX Import Cluster Decision Integrity & Unclustered Workout Preservation*)
* **Test Specification**: `TST-MIG-022` (*TCX Import Leave Unclustered Integrity Verification*)
* **Branch**: `bugfix/ATT-741`

---

## 1. Executive Summary & Root Cause Analysis

### 1.1 Problem Statement
When importing a TCX workout file via `LegacyImportEngine` and choosing "Leave Unclustered" (`@string/cluster_naming__leave_unclustered`) in `ClusterNamingDialog`, the user expects the workout to remain completely unclustered (`WorkoutSummaries.CLUSTER_ID = -1L`) and keep its original name.
However, the workout was unexpectedly auto-clustered into a newly generated cluster named "Workout at [date]" (or localized e.g. "Einheit bei [date]") and renamed accordingly (e.g. "Workout at 2026-09-08 #1").

### 1.2 Root Cause Analysis
1. In `LegacyImportEngine.kt` (lines 780–808): When the user chooses "Leave Unclustered", `listener?.onNewClusterCandidate(...)` returns `Pair(null, null)`. `LegacyImportEngine` correctly leaves `WorkoutSummaries.CLUSTER_ID = -1L` in SQLite and preserves the workout's original name.
2. In `LegacyImportEngine.kt` (line 813): Step 6 sends `TrackerService.WORKOUT_UPDATED_INTENT` via `LocalBroadcastManager` to signal that database writes have completed and downstream modules should refresh.
3. In `WorkoutRepository.kt` (line 760): `reloadWorkoutData(workoutId)` handles the broadcast. It checks:
   ```kotlin
   val existing = allWorkouts.value.find { it.id == workoutId }
   val isNewFinish = (existing == null || !existing.finished) && freshWorkoutData.finished
   ```
   Because the imported workout was not yet in the in-memory `allWorkouts.value` list, `existing == null` evaluated to `true`, mistakenly treating the imported historical workout as a brand new live-tracking completion!
4. In `WorkoutRepository.kt` (line 765): `isNewFinish` triggered:
   ```kotlin
   WorkoutClusterEngine.getInstance(application).onWorkoutFinished(application, freshWorkoutData)
   ```
5. In `WorkoutClusterEngine.kt` (lines 196–235): `onWorkoutFinished` executed `suggestCluster()`. When no cluster was matched, the fallback `else` branch executed:
   ```kotlin
   val clusterName = normalizedName ?: context.getString(R.string.cluster_default_name_format, w.fileBaseName?.take(10) ?: "Workout")
   val newId = learnFromWorkout(w.startLatLng, w.endLatLng, apex, w.totalDistance, clusterName, w.sportId, ...)
   assignClusterToWorkout(context, w.id, newId, false)
   ```
   This created an unwanted cluster with the fallback format string (`R.string.cluster_default_name_format`), assigned it to the workout, and altered the workout's name, overriding the user's explicit decision to leave the workout unclustered.

---

## 2. Technical Implementation Architecture

We implement a defense-in-depth architectural fix across two layers:

### 2.1 Layer 1: Guarding `WorkoutClusterEngine.onWorkoutFinished`
* **Unclustered Invariant**: If a workout has `clusterId == -1L`, it is unclustered. Under `REQ-MIG-025`, the system must never auto-create a fallback cluster for unclustered workouts.
* **Immediate Exit**:
  ```kotlin
  fun onWorkoutFinished(context: Context, w: WorkoutData) {
      // REQ-MIG-025: Strictly respect unclustered status and never auto-create fallback clusters
      if (w.clusterId == -1L) return
      if (w.startLatLng == null || w.endLatLng == null) return
      ...
  ```
* **Cluster Update Disambiguation**: When `w.clusterId != -1L`, the workout is already bound to `w.clusterId`. Update the bounds and centroids for that specific cluster (`dbManager.getClusterById(w.clusterId)`) without invoking fallback cluster creation.
* **Elimination of Fallback Auto-Creation**: Remove the fallback `else` branch that called `learnFromWorkout` with `cluster_default_name_format` and `assignClusterToWorkout`.

### 2.2 Layer 2: Disambiguating Import vs. Live Tracking in `WorkoutRepository.reloadWorkoutData`
* Disambiguate live tracking completion from newly loaded/imported workouts:
  ```kotlin
  val existing = allWorkouts.value.find { it.id == workoutId }
  val isLiveSessionFinish = (existing != null && !existing.finished) && freshWorkoutData.finished
  val isNewImportOrFinish = (existing == null || !existing.finished) && freshWorkoutData.finished

  if (isNewImportOrFinish) {
      PeriodsRepository.getInstance(application).onWorkoutFinished(freshWorkoutData)
  }

  if (isLiveSessionFinish && freshWorkoutData.clusterId != -1L) {
      WorkoutClusterEngine.getInstance(application).onWorkoutFinished(application, freshWorkoutData)
  }
  ```
* `PeriodsRepository.onWorkoutFinished` continues to receive newly imported and finished sessions (`isNewImportOrFinish`) to maintain accurate weekly/monthly aggregation caches.
* `WorkoutClusterEngine.onWorkoutFinished` is only triggered for actual live session completions that possess an active cluster assignment (`isLiveSessionFinish && freshWorkoutData.clusterId != -1L`), preventing duplicate cluster learning passes for imports where clustering was already resolved in `LegacyImportEngine`.

---

## 3. Impact Analysis (ASPICE SWE.1.BP.5 Phase)

### 3.1 Mandatory `find_usages` Audit
1. **`WorkoutClusterEngine.onWorkoutFinished`**:
   - `WorkoutRepository.kt:765`: Updated to respect `isLiveSessionFinish && freshWorkoutData.clusterId != -1L`.
   - `WorkoutClusterEngine.kt:278` (`onWorkoutSportChanged`): Benefits directly from `w.clusterId == -1L` guard (prevents inadvertent auto-clustering when an unclustered workout's sport is modified).
2. **`WorkoutRepository.reloadWorkoutData`**:
   - Triggered by `workoutUpdateReceiver` for `WORKOUT_UPDATED_INTENT` and `TRACKING_FINISHED_INTENT`. Preserved with enhanced safety.
3. **`R.string.cluster_default_name_format`**:
   - Retained for manual/fallback naming in dialogs where explicitly requested, but removed from silent background auto-creation on unclustered workouts.

### 3.2 Mapped Requirements Cross-Check (`docs/requirements.md`)
* `REQ-MIG-025`: Directly fulfilled.
* `REQ-SET-063` (Geometric Route Cluster Apex Integrity): Fully preserved; live session completions with an assigned cluster continue to update apex displacement and spatial boundaries.
* `REQ-SET-059` (Favorite Tracks Editing & Reassignment): Fully preserved; manual cluster assignment, reassignment, and unassignment via `EditWorkoutActivity` remain untouched.
* `REQ-DAT-011` (Analytical Period Cache Resync): Fully preserved; `PeriodsRepository` continues to receive `onWorkoutFinished` for all new imports and session completions.
* `REQ-UI-136` (Favorite Tracks Sorting): Fully preserved; sorting logic operates on the cluster list and unclustered tab without regression.

### 3.3 System & Non-Functional Constraints
* **Battery & Background**: Prevents redundant SQLite writes and unnecessary cluster insertions.
* **Component Interfaces**: No public signature changes; zero breaking changes.
* **Data Integrity**: Database schema remains unchanged; unclustered workouts consistently preserve `clusterId = -1L`.

---

## 4. Test Strategy & Verification (`TST-MIG-022`)

### 4.1 Unit Tests
Create unit test in `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterImportIntegrityTest.kt` verifying:
1. `onWorkoutFinished` with `clusterId = -1L` does NOT call `learnFromWorkout`, does NOT call `assignClusterToWorkout`, and leaves cluster database unmutated.
2. `reloadWorkoutData` for an imported workout (`existing == null`) does NOT invoke `WorkoutClusterEngine.onWorkoutFinished`.
3. An unclustered workout retains its original name and `clusterId = -1L`.

### 4.2 Automated Test Execution
Run unit tests via Gradle:
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutClusterImportIntegrityTest"
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.EditWorkoutClusterTest"
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutClusterApexTest"
```

---

## 5. Implementation Steps Summary
1. Update `WorkoutClusterEngine.kt` to guard `onWorkoutFinished` against `w.clusterId == -1L` and remove fallback cluster auto-creation.
2. Update `WorkoutRepository.kt` to disambiguate live tracking finish vs. import in `reloadWorkoutData`.
3. Implement `WorkoutClusterImportIntegrityTest.kt` verifying `REQ-MIG-025` and `TST-MIG-022`.
4. Run unit test suite to verify zero regressions.
5. Create Conventional Commit referencing `ATT-741` / `ATT-777` (Implementation).
