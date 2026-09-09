# Implementation Plan: Optional Route Cluster Activity Counter (ATT-714)

* **Parent Ticket**: [ATT-714](https://rainerblind.atlassian.net/browse/ATT-714) ([Feature] Counter for cluster shoud be optional)
* **Sub-Task**: [ATT-809](https://rainerblind.atlassian.net/browse/ATT-809) ([Impl-Plan] Counter for cluster shoud be optional)
* **Requirement**: `REQ-SET-067` (*Optional Route Cluster Activity Counter / Auto-Numbering*)
* **Test Specification**: `TST-SET-054` (*Optional Route Cluster Counter Verification*)
* **Target Version**: `V4.9.36`
* **Branch**: `feature/ATT-714`

---

## 1. Architectural Motivation & Scope

When recurring workouts are categorized under a `WorkoutCluster`, a sequential numeric suffix (`#<count>`) is appended to automated workout names by default (`REQ-SET-066`). While desired for recurring training runs, for distinct types of clusters (e.g. daily commutes, competitions, or named trail circuits), users prefer static naming without counter suffixes (e.g. `"Daily Commute"` rather than `"Daily Commute #14"`).

This plan introduces a per-cluster boolean flag `hasCounter` with full persistence, migration support, centralized formatting control, interactive UI toggles, and localization across all 9 supported languages.

---

## 2. Detailed Technical Design & Code Modifications

### 2.1 Database Layer: `WorkoutClusterDatabaseManager.kt`
* **File**: [`app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterDatabaseManager.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterDatabaseManager.kt)
* **Modifications**:
  1. `WorkoutCluster` data class:
     - Add `val hasCounter: Boolean = true` as constructor parameter.
  2. `WorkoutClusterContract`:
     - Add `const val COLUMN_HAS_COUNTER = "has_counter"`.
     - Add `$COLUMN_HAS_COUNTER INTEGER DEFAULT 1` to `CREATE_TABLE` query.
  3. `WorkoutClusterDbHelper`:
     - Increment `DATABASE_VERSION` from `10` to `11`.
     - In `onUpgrade`:
       ```kotlin
       if (oldVersion < 11) {
           try {
               db.execSQL("ALTER TABLE ${WorkoutClusterContract.TABLE_NAME} ADD COLUMN ${WorkoutClusterContract.COLUMN_HAS_COUNTER} INTEGER DEFAULT 1")
           } catch (e: Exception) {
               Log.w("WorkoutClusterDbHelper", "Failed to add has_counter column", e)
           }
       }
       ```
  4. Cursor Mapping & Content Values:
     - In `mapCursorToCluster`:
       ```kotlin
       val hasCounterIdx = cursor.getColumnIndex(WorkoutClusterContract.COLUMN_HAS_COUNTER)
       // pass to constructor:
       hasCounter = if (hasCounterIdx != -1 && !cursor.isNull(hasCounterIdx)) cursor.getInt(hasCounterIdx) == 1 else true
       ```
     - In `createContentValues`:
       ```kotlin
       put(WorkoutClusterContract.COLUMN_HAS_COUNTER, if (cluster.hasCounter) 1 else 0)
       ```

### 2.2 Core Logic Engine: `WorkoutClusterEngine.kt`
* **File**: [`app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt)
* **Modifications**:
  1. Update `formatClusterWorkoutName`:
     ```kotlin
     @JvmStatic
     @JvmOverloads
     fun formatClusterWorkoutName(
         context: Context,
         clusterName: String,
         hitCount: Int,
         hasCounter: Boolean = true
     ): String {
         return if (!hasCounter || hitCount <= 1) {
             clusterName
         } else {
             context.getString(R.string.cluster_autoname_format, clusterName, hitCount)
         }
     }
     ```
  2. In `assignClusterToWorkout`:
     Pass `cluster.hasCounter`:
     `formatClusterWorkoutName(context, cluster.name, displayCount, cluster.hasCounter)`
  3. In `createNewClusterFromWorkout`:
     Add `hasCounter: Boolean = true` parameter and pass to new `WorkoutCluster(...)`.

### 2.3 Tracker Service: `TrackerService.java`
* **File**: [`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java)
* **Modifications**:
  - In line 1201:
    ```java
    int displayCount = suggestion.getHitCount() + 1;
    String autoName = WorkoutClusterEngine.formatClusterWorkoutName(this, suggestion.getName(), displayCount, suggestion.getHasCounter());
    ```

### 2.4 Edit Workout ViewModel: `EditWorkoutViewModel.kt`
* **File**: [`app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt)
* **Modifications**:
  - In `applyClusterIdentity(cluster)`:
    ```kotlin
    val displayCount = cluster.hitCount + 1
    val autoName = WorkoutClusterEngine.formatClusterWorkoutName(
        getApplication(),
        cluster.name,
        displayCount,
        cluster.hasCounter
    )
    ```

### 2.5 ViewModel & UI Dialogs: `WorkoutClustersViewModel.kt` & `WorkoutClusterHeatmapScreen.kt`
* **File**: [`app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt)
  - Update `updateClusterIdentity`:
    ```kotlin
    fun updateClusterIdentity(cluster: WorkoutCluster, newName: String, newSportId: Long, hasCounter: Boolean = true) {
        viewModelScope.launch {
            val updated = cluster.copy(name = newName, probableSportId = newSportId, hasCounter = hasCounter)
            repository.updateCluster(updated)
            if (_selectedCluster.value?.id == cluster.id) {
                _selectedCluster.value = updated
            }
        }
    }
    ```
* **File**: [`app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt)
  - In `EditWorkoutClusterIdentityDialog`:
    - Signature: `onConfirm: (String, Long, Boolean) -> Unit`
    - Add state: `var hasCounter by remember { mutableStateOf(cluster.hasCounter) }`
    - Add interactive Switch row for enabling/disabling the counter.
    - Confirm button passes `hasCounter`.
  - In caller: `viewModel.updateClusterIdentity(cluster, newName, newSportId, hasCounter)`.

### 2.6 Cluster Components: `WorkoutClusterComponents.kt`
* **File**: [`app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt)
  - In `EditWorkoutClusterDialog` ("Create New Route..."):
    - Add state: `var newClusterHasCounter by remember { mutableStateOf(true) }`
    - Add Switch/Checkbox row.
    - Update `onCreateNewCluster(name, hasCounter)`.

### 2.7 String Resources & Localization
* Add strings to all 9 locales:
  - `cluster_counter_enabled_label`
  - `cluster_counter_enabled_description`

---

## 3. Invariants & Risk Assessment

| Aspect | Invariant Guarantee |
|:---|:---|
| **Database Integrity** | Non-destructive `ALTER TABLE ... ADD COLUMN has_counter INTEGER DEFAULT 1`. Existing databases migrate seamlessly with zero data alteration. |
| **Backward Compatibility** | Existing clusters and default creation retain `hasCounter = true` (defaulting to existing behavior). |
| **Clustering Algorithms** | Spatial scoring, 3D elevation terms, and fingerprint dragging remain 100% untouched. |
| **Hit Count Accuracy** | `cluster.hitCount` continues to accurately reflect the true number of member sessions. |
