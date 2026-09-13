# Implementation Walkthrough: Bulk Workout Deletion Confirmation Dialog (ATT-704)

## 1. Overview & Objectives
* **Parent Ticket**: [ATT-704](https://rainerblind.atlassian.net/browse/ATT-704) (`[Verbesserung] When deleting old workouts there must be a confirmation`)
* **Implementation Sub-Task**: [ATT-721](https://rainerblind.atlassian.net/browse/ATT-721) (`[Implementation] When deleting old workouts there must be a confirmation`)
* **Associated Requirements & Tests**:
  * Requirement: `REQ-UI-129` ([docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L253))
  * Test Specification: `TST-UI-082` ([docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L283))
  * FixVersion: `V4.9.36`

Previously, tapping the proceed action in `DeleteOldWorkoutsDialog` immediately executed the bulk deletion without an explicit confirmation dialog. Because bulk workout deletion permanently deletes historical workouts, GPS samples, laps, extrema, and sensor data from SQLite, accidental confirmation or numeric typos (e.g. entering 30 instead of 300) risked irreversible data loss.

This implementation introduces a two-step confirmation flow:
1. **Retention Input**: The user specifies retention days in `DeleteOldWorkoutsDialog`.
2. **Explicit Confirmation**: The input dialog closes and `DeleteConfirmationDialog` appears, explicitly displaying the number of days and asking for confirmation before any database deletion is initiated.

---

## 2. Changes Implemented

### A. Localization & Resource Bundles
* Added `@string/really_delete_old_workouts_format` across all 9 application locales in `res/values*/strings.xml`:
  * `values/strings.xml`: `Do you really want to delete all workouts older than %1$d days?`
  * `values-de/strings.xml`: `Möchtest du wirklich alle Einheiten löschen, die älter als %1$d Tage sind?`
  * `values-es/strings.xml`: `¿Realmente quieres eliminar todos los entrenamientos de más de %1$d días?`
  * `values-fr/strings.xml`: `Voulez-vous vraiment supprimer tous les entraînements de plus de %1$d jours ?`
  * `values-it/strings.xml`: `Vuoi davvero eliminare tutti gli allenamenti più vecchi di %1$d giorni?`
  * `values-nl/strings.xml`: `Wil je echt alle trainingen ouder dan %1$d dagen verwijderen?`
  * `values-pl/strings.xml`: `Czy na pewno chcesz usunąć wszystkie treningi starsze niż %1$d dni?`
  * `values-pt/strings.xml`: `Deseja realmente excluir todos os treinos com mais de %1$d dias?`
  * `values-ja/strings.xml`: `%1$d日より古いすべてのトレーニングを本当に削除しますか？`

### B. Presentation Layer (`WorkoutTabsScreen.kt`)
* **[WorkoutTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutTabsScreen.kt)**:
  * Hoisted confirmation state: `var daysToConfirmDelete by rememberSaveable { mutableStateOf<Int?>(null) }`.
  * Updated `DeleteOldWorkoutsDialog.onConfirm(daysToKeep)` to close the input dialog (`showDeleteOldWorkoutsDialog = false`) and open the confirmation dialog (`daysToConfirmDelete = daysToKeep`).
  * Rendered `DeleteConfirmationDialog` when `daysToConfirmDelete != null`:
    * Title: `stringResource(R.string.deleteOldWorkouts)`
    * Message: formatted string `really_delete_old_workouts_format` with the chosen retention days.
    * On Confirm: dispatches `onDeleteOldWorkouts(days)` and resets `daysToConfirmDelete = null`.
    * On Dismiss: resets `daysToConfirmDelete = null` without modifying SQLite.

### C. Automated Unit Testing (`DeleteOldWorkoutsConfirmationTest.kt`)
* **[DeleteOldWorkoutsConfirmationTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/DeleteOldWorkoutsConfirmationTest.kt)**:
  * `testReallyDeleteOldWorkoutsFormat_interpolatesDaysCorrectly`: Verifies parameter interpolation across thresholds (180, 365, 30 days).
  * `testTwoStepConfirmation_cancellationSafety_doesNotTriggerDeletion`: Verifies that canceling the confirmation dialog resets state without executing deletion.
  * `testTwoStepConfirmation_confirmedExecution_triggersDeletion`: Verifies that confirming dispatches deletion with the exact specified retention parameter.

---

## 3. Verification & Evidence
* **Automated Unit Tests**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.DeleteOldWorkoutsConfirmationTest"`: PASSED.
  * Full debug unit test suite executed cleanly.
