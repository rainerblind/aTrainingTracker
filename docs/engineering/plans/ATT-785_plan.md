# Implementation Plan: Workout Cluster Sequential Auto-Naming Counter Ergonomics (ATT-785)

* **Parent Ticket**: [ATT-785](https://rainerblind.atlassian.net/browse/ATT-785) ([Verbesserung] First workout of a cluster should not get the #1.)
* **Sub-Task**: [ATT-804](https://rainerblind.atlassian.net/browse/ATT-804) ([Impl-Plan] First workout of a cluster should not get the #1.)
* **Requirement**: `REQ-SET-066` (*Workout Cluster Sequential Auto-Naming Counter Ergonomics*)
* **Test Specification**: `TST-SET-053` (*Workout Cluster Auto-Naming Counter Ergonomics Verification*)
* **Target Version**: `V4.9.36`
* **Branch**: `feature/ATT-785`

---

## 1. Context & Architectural Motivation

In the current route clustering auto-naming implementation, whenever a workout is assigned to a cluster, its title is unconditionally formatted using the pattern `%1$s #%2$d` (`@string/cluster_autoname_format`), yielding names such as `"Morning Run #1"`, `"Lake Tahoe Loop #1"`, or `"Chiemsee-Runde #1"`.

Appending `"#1"` to the solitary or initial activity on a route is unnatural and visually cluttered. A route activity stands on its own without needing a counter until a recurring workout along the same route actually occurs.

### Architectural Solution:
Introduce a centralized, static formatting utility on `WorkoutClusterEngine`:
```kotlin
@JvmStatic
fun formatClusterWorkoutName(context: Context, clusterName: String, hitCount: Int): String {
    return if (hitCount <= 1) {
        clusterName
    } else {
        context.getString(R.string.cluster_autoname_format, clusterName, hitCount)
    }
}
```
All call sites that format workout names from cluster entities will be routed through this helper.

---

## 2. Impact Analysis & Proposed Code Changes

### 2.1 Component: `WorkoutClusterEngine.kt`
* **File**: [`app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt)
* **Changes**:
  1. Add `@JvmStatic fun formatClusterWorkoutName(context: Context, clusterName: String, hitCount: Int): String` companion/object method with complete KDoc documentation.
  2. In `assignClusterToWorkout(context, workoutId, clusterId, forceIdentity)`:
     Replace the direct call to `context.getString(R.string.cluster_autoname_format, cluster.name, displayCount)` with `formatClusterWorkoutName(context, cluster.name, displayCount)`.

### 2.2 Component: `TrackerService.java`
* **File**: [`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java)
* **Changes**:
  * In line 1200:
    Replace:
    ```java
    String autoName = getString(R.string.cluster_autoname_format, suggestion.getName(), suggestion.getHitCount() + 1);
    ```
    with:
    ```java
    int displayCount = suggestion.getHitCount() + 1;
    String autoName = WorkoutClusterEngine.formatClusterWorkoutName(this, suggestion.getName(), displayCount);
    ```

### 2.3 Component: `EditWorkoutViewModel.kt`
* **File**: [`app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt)
* **Changes**:
  * In `applyClusterIdentity(cluster)`:
    Replace:
    ```kotlin
    workoutName = application.getString(R.string.cluster_autoname_format, cluster.name, cluster.hitCount + 1),
    ```
    with:
    ```kotlin
    val displayCount = cluster.hitCount + 1
    val formattedName = WorkoutClusterEngine.formatClusterWorkoutName(application, cluster.name, displayCount)
    workoutName = formattedName,
    ```

---

## 3. System Invariants & Safety Audit

| Subsystem / Layer | Invariant Constraint | Preservation Strategy |
|:---|:---|:---|
| **Clustering Engine** | 3D spatial similarity scoring (`calculateSimilarity`), distance thresholds (Start/End 200m, Apex 400m, Length 20%), and sport type firewall MUST NOT change. | `calculateSimilarity()` and geometric anchors remain untouched. |
| **String Formatting** | For `hitCount >= 2`, naming MUST continue using `cluster_autoname_format` (`%1$s #%2$d`). | Explicit conditional check `if (hitCount <= 1) clusterName else getString(R.string.cluster_autoname_format, clusterName, hitCount)`. |
| **Regex Normalization** | `stripHitCount` (`Regex(" #\\d+$")`) and `normalizeName` (`Regex(" (?:#\|var) \\d+$")`) MUST process names without numerical suffixes cleanly. | For `"Morning Run"`, both functions naturally return the trimmed base name without altering or mangling it. |
| **Database Schemas** | SQLite tables (`RouteClusters.db`, `WorkoutSummaries`) and Room entities MUST remain untouched. | No schema or DAO modifications required. |
| **Localization Parity** | `cluster_autoname_format` continues to be localized across all 9 locales. | Resource key preserved; no new locale string needed since `clusterName` is already provided. |

---

## 4. Verification & Testing Strategy (`TST-SET-053`)

### 4.1 Unit Test Suite: `WorkoutClusterAutoNamingTest.kt`
* Create dedicated test class in `com.atrainingtracker.trainingtracker.database`:
  1. `testFormatClusterWorkoutName_initialHitCount_omitsSuffix`:
     - Assert `formatClusterWorkoutName(context, "Morning Run", 0)` == `"Morning Run"`.
     - Assert `formatClusterWorkoutName(context, "Morning Run", 1)` == `"Morning Run"`.
  2. `testFormatClusterWorkoutName_subsequentHitCount_appendsSuffix`:
     - Assert `formatClusterWorkoutName(context, "Morning Run", 2)` == `"Morning Run #2"`.
     - Assert `formatClusterWorkoutName(context, "Morning Run", 5)` == `"Morning Run #5"`.
  3. `testAssignClusterToWorkout_initialSession_setsBaseNameWithoutSuffix`:
     - Mock `WorkoutSummariesDatabaseManager` and `WorkoutClusterDatabaseManager`.
     - Assign workout to empty cluster (`hitCount = 0`).
     - Verify updated `WORKOUT_NAME` in `WorkoutSummaries` receives `"Morning Run"`.
  4. `testAssignClusterToWorkout_subsequentSession_appendsSuffix`:
     - Assign workout to cluster with `hitCount = 1`.
     - Verify updated `WORKOUT_NAME` in `WorkoutSummaries` receives `"Morning Run #2"`.
  5. `testNormalizationAndHitCountStripping_handlesBaseNameAndNumberedNameIdentically`:
     - Assert `stripHitCount("Morning Run") == "Morning Run"`.
     - Assert `stripHitCount("Morning Run #2") == "Morning Run"`.
     - Assert `normalizeName("Morning Run") == "morning run"`.
     - Assert `normalizeName("Morning Run #2") == "morning run"`.

### 4.2 Automated Build & Regression
* Run `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.database.WorkoutClusterAutoNamingTest`.
* Run `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.database.EditWorkoutClusterTest`.
* Verify `./gradlew assembleDebug` compiles cleanly.
