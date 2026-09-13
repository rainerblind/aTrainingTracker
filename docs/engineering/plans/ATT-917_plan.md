# Implementation Plan: UI Deletion of Old Unfinished Workouts (ATT-917)

## 1. Executive Summary & Objective
The objective of **ATT-917** is to resolve the user interface limitation that prevented users from deleting old unfinished/crashed workouts, while strictly preserving unfinished sessions in the database until user deletion, maintaining their visual alpha (`0.5f`), and preventing deletion of any workout currently being actively tracked:
1. **Database Retention Invariant (User Directive)**: Crashed and unfinished workouts (`finished == false` / SQLite `FINISHED == 0`) **MUST remain in the database**. They are never automatically purged in the background or upon starting new workouts.
2. **Visual Differentiation**: In `WorkoutSummary.kt` and `WorkoutSummaryCompact.kt`, unfinished workouts continue to render with `alpha = 0.5f` to visually indicate their incomplete status.
3. **Decoupled Deletion in `WorkoutHeader.kt`**: Previously, `WorkoutHeader` grouped both the 3-dots export menu and the long-press deletion context menu under `if (menuEnabled)`. In `WorkoutSummary.kt`, `menuEnabled = workoutData.headerData.finished` was passed, causing `combinedClickable(onClick, onLongClick = { showContextMenu = true })` to be completely bypassed for unfinished workouts. Deletion capability (`canDelete`) is now decoupled from export enablement.
4. **Active Tracking Protection Invariant**: If an unfinished workout is actively being tracked by `TrackerService` (`TrainingApplication.isActivelyTracked(workoutId)`), the UI strictly disallows its deletion in both full and compact views.
5. **Clean Cascading Deletion**: When confirmed by the user, deletion delegates to `WorkoutDeletionHelper.deleteWorkout(workoutId)`, which safely purges summaries, laps, extrema, export status, and drops high-frequency sample tables (`workout_samples_<fileBaseName>`) in the verified ATT-296 sequence.

---

## 2. Traceability & Mapped Entities

| Artifact | Identifier | Details |
| :--- | :--- | :--- |
| **Requirement** | `REQ-UI-145` | UI Deletion of Old Unfinished Workouts & Active Tracking Deletion Protection (`docs/requirements.md`) |
| **Test Specification** | `TST-UI-098` | UI Deletion of Old Unfinished Workouts & Active Workout Protection Verification (`docs/tests.md`) |
| **Parent Ticket** | `ATT-917` | `[Verbesserung] Delete crashed workouts (when it is not the one that is currently tracked)` |
| **Analysis Sub-task** | `ATT-978` | `[Analysis] Delete crashed workouts (when it is not the one that is currently tracked)` (`Erledigt` - Gate 1 Approved) |
| **Test Sub-task** | `ATT-983` | `[Test-Spec] Delete crashed workouts (when it is not the one that is currently tracked)` (`Erledigt` - Gate 2 Approved) |
| **Plan Sub-task** | `ATT-984` | `[Impl-Plan] Delete crashed workouts (when it is not the one that is currently tracked)` (`In Bearbeitung` - Stage 3) |
| **Target Version** | `V4.9.36` | Target Release Version |
| **Target Branch** | `feature/ATT-917` | Git Working Branch off `develop` |

---

## 3. Detailed Software Design & File Changes (SWE.2 / SWE.3)

### 3.1 Application State & Active Workout Guard ([TrainingApplication.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java))
* **Static Active Workout State**:
  Add a thread-safe static field `sActiveWorkoutId`:
  ```java
  private static volatile long sActiveWorkoutId = -1;
  ```
* **Synchronization in Lifecycle Methods**:
  - In `setWorkoutID(long workoutID)`:
    ```java
    mWorkoutID = workoutID;
    sActiveWorkoutId = workoutID;
    ```
  - In `stopTracking()`:
    ```java
    cTrackingMode = TrackingMode.READY;
    sActiveWorkoutId = -1;
    notifyTrackingStateChanged();
    ```
* **Static Accessors & Guards**:
  ```java
  public static long getActiveWorkoutID() {
      return sActiveWorkoutId;
  }

  public static boolean isActivelyTracked(long workoutId) {
      return isTracking() && workoutId > 0 && workoutId == sActiveWorkoutId;
  }

  public static void setActiveWorkoutIdForTesting(long workoutId) {
      sActiveWorkoutId = workoutId;
  }
  ```

---

### 3.2 Component Header Layer ([WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt))
* **Parameter Decoupling**:
  Add parameter `canDelete: Boolean = true`:
  ```kotlin
  @Composable
  fun WorkoutHeader(
      data: WorkoutHeaderData,
      onClicked: (() -> Unit)? = null,
      onExport: (FileFormat) -> Unit,
      onSaveAsRoute: () -> Unit,
      onDeleteRequest: () -> Unit,
      modifier: Modifier = Modifier,
      menuEnabled: Boolean = true,
      canDelete: Boolean = true,
      onClusterClick: ((Long) -> Unit)? = null,
      onEditWorkout: (() -> Unit)? = null,
      actions: @Composable RowScope.() -> Unit = {}
  )
  ```
* **Interactive Surface Click Modifier**:
  Decouple the long-click gesture from `menuEnabled` so that `onLongClick = { showContextMenu = true }` is enabled whenever `canDelete` is true:
  ```kotlin
  Surface(
      modifier = if (canDelete || onClicked != null) {
          modifier.fillMaxWidth()
              .combinedClickable(
                  onClick = { onClicked?.invoke() },
                  onLongClick = if (canDelete) { { showContextMenu = true } } else null
              )
      } else {
          modifier.fillMaxWidth()
      },
      color = Color.Transparent
  )
  ```
* **Export Action Menu**:
  The 3-dots overflow button and its dropdown menu remain governed by `menuEnabled`, ensuring unfinished workouts are not offered for export:
  ```kotlin
  if (menuEnabled) {
      Box {
          IconButton(onClick = { showMenu = true }, ...) { ... }
          DropdownMenu(expanded = showMenu, ...) { ... }
      }
  }
  ```
* **Deletion Context Menu**:
  Anchored context menu remains triggered by `showContextMenu` and executes `onDeleteRequest()`.

---

### 3.3 Aftermath Summary Card Layer ([WorkoutSummary.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt))
* **Pass Decoupled Parameters to `WorkoutHeader`**:
  - Calculate `canDelete = !TrainingApplication.isActivelyTracked(workoutData.id)`.
  - Pass `canDelete` to `WorkoutHeader`.
  - Maintain `menuEnabled = workoutData.headerData.finished` for export options.
  - Retain `val contentAlpha = if (workoutData.headerData.finished) TTAlpha.High else 0.5f`.
  - Retain body section click behavior: `if (workoutData.headerData.finished) onMapClick()`.

---

### 3.4 Compact Summary Card Layer ([WorkoutSummaryCompact.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummaryCompact.kt))
* **Active Tracking Deletion Guard**:
  - Calculate `val canDelete = !TrainingApplication.isActivelyTracked(workoutData.id)`.
  - In `MappableListItem`:
    ```kotlin
    MappableListItem(
        modifier = modifier,
        onClick = onMapClick,
        onLongClick = if (canDelete || onEditWorkout != null) { { showContextMenu = true } } else null,
        alpha = contentAlpha
    )
    ```
  - In context `DropdownMenu`:
    Only render the "Delete" item when `canDelete == true`:
    ```kotlin
    if (canDelete) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.delete)) },
            onClick = {
                showContextMenu = false
                onDeleteRequest()
            },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
        )
    }
    ```

---

## 4. Verification Plan (SWE.4 / TST-UI-098)

### 4.1 Automated Unit Tests
1. **`TrainingApplicationTrackedTest.kt`**:
   - Verify `isActivelyTracked` returns `false` when idle (`cTrackingMode == TrackingMode.READY`).
   - Verify `isActivelyTracked(100L)` returns `true` and `isActivelyTracked(101L)` returns `false` when `cTrackingMode == TrackingMode.TRACKING` and `sActiveWorkoutId == 100L`.
   - Verify `stopTracking()` resets `sActiveWorkoutId` to `-1` and `isActivelyTracked` returns `false`.
2. **`WorkoutHeaderDeleteTest.kt`**:
   - Verify `WorkoutHeader` with `canDelete = true` and `menuEnabled = false` (old unfinished workout):
     - Long-press triggers `showContextMenu = true` and invoking Delete fires `onDeleteRequest()`.
     - 3-dots export button is not rendered.
   - Verify `WorkoutHeader` with `canDelete = false` (actively tracked workout):
     - Long-press does not open context menu; `onDeleteRequest` is never fired.
   - Verify `WorkoutHeader` with `canDelete = true` and `menuEnabled = true` (finished workout):
     - Both 3-dots export menu and long-press deletion context menu are fully functional.
3. **`WorkoutSummaryCompactDeleteTest.kt`**:
   - Verify compact view allows deletion when `canDelete == true`.
   - Verify compact view hides/disables deletion when `canDelete == false`.

### 4.2 Clean-Room Repository Regression Pass
Execute full unit test suite:
```bash
./gradlew testDebugUnitTest
```
Ensuring 100% build success and zero test regressions across all application modules.

---

## 5. Implementation Sequence & Next Sub-tasks

1. **Gate 3 Review**:
   - Audit `ATT-984` and advance to `Freigabe (Human)` for user approval.
2. **Implementation (SWE.3 / SWE.4 - ATT-985)**:
   - Implement `sActiveWorkoutId` and `isActivelyTracked` in `TrainingApplication.java`.
   - Update `WorkoutHeader.kt` with decoupled `canDelete`.
   - Update `WorkoutSummary.kt` and `WorkoutSummaryCompact.kt`.
   - Implement unit tests for `TST-UI-098`.
   - Run `./gradlew testDebugUnitTest`.
3. **Verification & Quality Gate (SWE.5 - ATT-986)**:
   - Update `docs/requirements.md` and `docs/tests.md` to `Verified`.
   - Merge `feature/ATT-917` into `develop` via `--no-ff`.
