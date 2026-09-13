# Implementation Plan: Option to Mark Old Unfinished Workouts as Finished (ATT-987)

## 1. Executive Summary & Objective
The objective of **ATT-987** is to provide athletes with an explicit user interface option to mark old unfinished or crashed workouts as finished:
1. **User Empowerment**: When an activity recording is interrupted by device battery depletion, OS kill, or application crash, the recorded GPS track and sensor data in SQLite are preserved (`REQ-UI-145`). Athletes need the ability to transition these old workouts from the unfinished/dimmed state (`alpha = 0.5f`) to fully finished activities (`alpha = 1.0f`).
2. **Long-Click Context Menu Access**: The long-press context menu on both expanded (`WorkoutSummary` via `WorkoutHeader`) and compact (`WorkoutSummaryCompact`) aftermath cards will provide the action **"Als abgeschlossen markieren"** / **"Mark as finished"** (`@string/mark_as_finished`).
3. **Atomic State & Visual Transition**:
   - Updates SQLite column `FINISHED = 1` in `WorkoutSummaries.TABLE` for the specified `workoutId`.
   - Updates in-memory `WorkoutData` cache with `finished = true`.
   - Re-renders the card with full opacity (`alpha = TTAlpha.High` / `1.0f`).
   - Enables the 3-dots export menu (`menuEnabled = true`), unlocking TCX/GPX export and Strava upload.
   - Omits the "Mark as finished" action from subsequent context menus once completed.
4. **Historical Period Rollup Integration**:
   - `WorkoutRepository.markWorkoutFinished` directly triggers `PeriodsRepository.getInstance(app).onWorkoutFinished(updatedWorkout)`, immediately aggregating the newly finished activity into its Day period and rolling up metrics (workout count, duration, distance, ascent) to Week, Month, and Year periods.
5. **Active Tracking Protection Invariant**:
   - If an unfinished workout is currently being recorded by `TrackerService` (`TrainingApplication.isActivelyTracked(workoutId) == true`), the "Mark as finished" option is strictly prohibited from the aftermath context menu. Live recordings must only be finalized through the standard tracking stop lifecycle.

---

## 2. Traceability & Mapped Entities

| Artifact | Identifier | Details |
| :--- | :--- | :--- |
| **Requirement** | `REQ-UI-146` | Option to Mark Old Unfinished Workouts as Finished & Period Integration (`docs/requirements.md`) |
| **Test Specification** | `TST-UI-099` | Mark Old Unfinished Workout Finished & Active Workout Protection Verification (`docs/tests.md`) |
| **Parent Ticket** | `ATT-987` | `[Verbesserung] Option to mark old unfinished workouts as finished` |
| **Analysis Sub-task** | `ATT-988` | `[Analysis] Option to mark old unfinished workouts as finished` (`Erledigt` - Gate 1 Approved) |
| **Test Sub-task** | `ATT-989` | `[Test-Spec] Option to mark old unfinished workouts as finished` (`Erledigt` - Gate 2 Approved) |
| **Plan Sub-task** | `ATT-990` | `[Impl-Plan] Option to mark old unfinished workouts as finished` (`In Bearbeitung` - Stage 3) |
| **Target Version** | `V4.9.36` | Target Release Version |
| **Target Branch** | `feature/ATT-987` | Git Working Branch off `develop` |

---

## 3. Detailed Software Design & File Changes (SWE.2 / SWE.3)

### 3.1 Database Layer ([WorkoutSummariesDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java))
* **Add `setWorkoutFinished(long workoutId)`**:
  ```java
  public void setWorkoutFinished(long workoutId) {
      SQLiteDatabase db = getDatabase();
      if (db == null || !db.isOpen()) {
          return;
      }
      try {
          ContentValues values = new ContentValues();
          values.put(WorkoutSummaries.FINISHED, 1);
          db.update(WorkoutSummaries.TABLE, values, WorkoutSummaries.C_ID + " = ?", new String[]{String.valueOf(workoutId)});
      } catch (Exception e) {
          Log.e(TAG, "Error setting workout " + workoutId + " to finished: " + e.getMessage(), e);
      }
  }
  ```

### 3.2 Repository Layer ([WorkoutRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt))
* **Add `markWorkoutFinished(workoutId: Long)`**:
  ```kotlin
  fun markWorkoutFinished(workoutId: Long) {
      scope.launch {
          withContext(Dispatchers.IO) {
              summariesManager.setWorkoutFinished(workoutId)
          }
          updateWorkoutInMemory(workoutId) { current ->
              current.copy(finished = true)
          }
          val updatedWorkout = _workoutList.value.firstOrNull { it.id == workoutId }
          if (updatedWorkout != null) {
              PeriodsRepository.getInstance(application).onWorkoutFinished(updatedWorkout)
          }
      }
  }
  ```

### 3.3 ViewModel Layer ([WorkoutSummariesViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesViewModel.kt))
* **Expose `markWorkoutFinished`**:
  ```kotlin
  fun markWorkoutFinished(workoutId: Long) {
      workoutRepository.markWorkoutFinished(workoutId)
  }
  ```

### 3.4 UI Components
#### 3.4.1 [WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt)
* Add `onMarkFinished: (() -> Unit)? = null` parameter.
* Evaluate combined clickable:
  ```kotlin
  val hasLongClick = canDelete || (!data.finished && onMarkFinished != null)
  ```
* In `DropdownMenu`:
  ```kotlin
  if (!data.finished && onMarkFinished != null) {
      DropdownMenuItem(
          text = { Text(stringResource(R.string.mark_as_finished)) },
          onClick = {
              showContextMenu = false
              onMarkFinished()
          },
          leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
      )
  }
  if (canDelete) {
      DropdownMenuItem(
          text = { Text(stringResource(R.string.delete)) },
          onClick = { showContextMenu = false; onDeleteRequest() },
          leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
      )
  }
  ```

#### 3.4.2 [WorkoutSummary.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt)
* Add `onMarkFinished: ((Long) -> Unit)? = null` parameter.
* Forward to `WorkoutHeader`:
  ```kotlin
  val canMarkFinished = !workoutData.headerData.finished && !TrainingApplication.isActivelyTracked(workoutData.id)
  onMarkFinished = if (canMarkFinished && onMarkFinished != null) { { onMarkFinished(workoutData.id) } } else null
  ```

#### 3.4.3 [WorkoutSummaryCompact.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummaryCompact.kt)
* Add `onMarkFinished: (() -> Unit)? = null` parameter.
* Evaluate context menu availability:
  ```kotlin
  val canMarkFinished = !workoutData.headerData.finished && !TrainingApplication.isActivelyTracked(workoutData.id) && onMarkFinished != null
  val hasContextMenu = canDelete || onEditWorkout != null || canMarkFinished
  ```
* In `DropdownMenu`:
  ```kotlin
  if (canMarkFinished) {
      DropdownMenuItem(
          text = { Text(stringResource(R.string.mark_as_finished)) },
          onClick = {
              showContextMenu = false
              onMarkFinished?.invoke()
          },
          leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
      )
  }
  ```

#### 3.4.4 [WorkoutList.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutList.kt)
* Add `onMarkFinished: (Long) -> Unit = {}` parameter.
* Forward to both `WorkoutSummaryCompact` and `WorkoutSummary`.

#### 3.4.5 Screens Wiring
* `WorkoutTabsScreen.kt`: Pass `onMarkFinished = { id -> summariesViewModel.markWorkoutFinished(id) }`.
* `WorkoutSummariesListFragment.kt`: Pass `onMarkFinished = { id -> summariesViewModel.markWorkoutFinished(id) }`.
* `WorkoutClustersFragment.kt`: Pass `onMarkFinished = { id -> summariesViewModel.markWorkoutFinished(id) }`.

### 3.5 Multilingual String Resources (All 9 Locales)
* `values/strings.xml`: `<string name="mark_as_finished">Mark as finished</string>`
* `values-de/strings.xml`: `<string name="mark_as_finished">Als abgeschlossen markieren</string>`
* `values-es/strings.xml`: `<string name="mark_as_finished">Marcar como finalizado</string>`
* `values-fr/strings.xml`: `<string name="mark_as_finished">Marquer comme terminé</string>`
* `values-it/strings.xml`: `<string name="mark_as_finished">Contrassegna come completato</string>`
* `values-ja/strings.xml`: `<string name="mark_as_finished">完了としてマーク</string>`
* `values-nl/strings.xml`: `<string name="mark_as_finished">Markeren als voltooid</string>`
* `values-pl/strings.xml`: `<string name="mark_as_finished">Oznacz jako zakończony</string>`
* `values-pt/strings.xml`: `<string name="mark_as_finished">Marcar como concluído</string>`

---

## 4. Test Strategy & Verification Plan (SWE.4 / SWE.5)

### 4.1 Automated Unit Tests ([WorkoutMarkFinishedTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutMarkFinishedTest.kt))
* **Test 1 (`testUnfinishedWorkout_whenIdle_canMarkFinishedAndTransitionsToFinished`)**:
  - Unfinished workout (`finished == false`): verify `canMarkFinished == true`, `contentAlpha == 0.5f`, and `menuEnabled == false`.
  - Simulate mark finished: verify `finished == true`, `contentAlpha == TTAlpha.High` (1.0f), `menuEnabled == true`, and "Mark as finished" option is omitted.
* **Test 2 (`testActiveLiveWorkout_whenTracking_cannotBeMarkedFinishedViaUI`)**:
  - Unfinished workout with ID $W$: set `isTracking = true` and `activeWorkoutId = W`.
  - Verify `canMarkFinished == false`, strictly preventing context menu from showing the action.
* **Test 3 (`testAlreadyFinishedWorkout_omitsMarkFinishedAction`)**:
  - Finished workout (`finished == true`): verify `canMarkFinished == false` (or action omitted).

### 4.2 Translation Parity Test
* Verify string `mark_as_finished` exists in all 9 `strings.xml` files without missing tags or formatting defects.

### 4.3 Clean-Room Regression Verification
* Execute `./gradlew testDebugUnitTest` to ensure 100% test pass rate with 0 regressions.
