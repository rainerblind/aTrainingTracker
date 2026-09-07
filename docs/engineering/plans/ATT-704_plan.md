# Implementation Plan: Bulk Workout Deletion Confirmation Dialog (ATT-704)

## Overview
During bulk workout deletion ([ATT-296](https://rainerblind.atlassian.net/browse/ATT-296), [ATT-705](https://rainerblind.atlassian.net/browse/ATT-705)), tapping the proceed action in `DeleteOldWorkoutsDialog` immediately initiates the irreversible database purge without secondary user confirmation. Because bulk deletion permanently removes historical workouts, GPS samples, laps, extrema, and sensor data from SQLite, accidental confirmation or numeric typos (e.g. entering 30 instead of 300) can result in catastrophic, irreversible data loss. Single workout deletions already require explicit confirmation via `WorkoutDeleteDialog` and `DeleteConfirmationDialog`.

This plan implements an explicit two-step confirmation flow ([`REQ-UI-129`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L253), [`TST-UI-082`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L283)) prior to executing bulk deletion.

---

## User Review Required
> [!NOTE]
> A new localized format string `@string/really_delete_old_workouts_format` ("Do you really want to delete all workouts older than %1$d days?") will be added across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) to guarantee complete localization parity.

---

## Proposed Changes

### Component 1: Localization & Resource Bundles
#### [MODIFY] [strings.xml (all 9 locales)](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values/strings.xml)
* Add `@string/really_delete_old_workouts_format` across:
  * `values/strings.xml`: `Do you really want to delete all workouts older than %1$d days?`
  * `values-de/strings.xml`: `Möchtest du wirklich alle Einheiten löschen, die älter als %1$d Tage sind?`
  * `values-es/strings.xml`: `¿Realmente quieres eliminar todos los entrenamientos de más de %1$d días?`
  * `values-fr/strings.xml`: `Voulez-vous vraiment supprimer tous les entraînements de plus de %1$d jours ?`
  * `values-it/strings.xml`: `Vuoi davvero eliminare tutti gli allenamenti più vecchi di %1$d giorni?`
  * `values-nl/strings.xml`: `Wil je echt alle trainingen ouder dan %1$d dagen verwijderen?`
  * `values-pl/strings.xml`: `Czy na pewno chcesz usunąć wszystkie treningi starsze niż %1$d dni?`
  * `values-pt/strings.xml`: `Deseja realmente excluir todos os treinos com mais de %1$d dias?`
  * `values-ja/strings.xml`: `%1$d日より古いすべてのトレーニングを本当に削除しますか？`

---

### Component 2: Presentation & Jetpack Compose UI
#### [MODIFY] [WorkoutTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutTabsScreen.kt)
* Add state `var daysToConfirmDelete by rememberSaveable { mutableStateOf<Int?>(null) }`.
* In `DeleteOldWorkoutsDialog.onConfirm(daysToKeep)`:
  * Dismiss `showDeleteOldWorkoutsDialog = false`.
  * Set `daysToConfirmDelete = daysToKeep`.
* Render `DeleteConfirmationDialog` when `daysToConfirmDelete != null`:
  * `title = stringResource(R.string.deleteOldWorkouts)`
  * `message = stringResource(R.string.really_delete_old_workouts_format, days)`
  * `onConfirm`: invokes `onDeleteOldWorkouts(days)` and resets `daysToConfirmDelete = null`.
  * `onDismiss`: resets `daysToConfirmDelete = null` without triggering deletion.

---

### Component 3: Automated Unit Testing
#### [NEW] [DeleteOldWorkoutsConfirmationTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/DeleteOldWorkoutsConfirmationTest.kt)
* Automated unit tests verifying:
  1. String resource formatting and parameter interpolation.
  2. Sequential dialog hoisting: `DeleteOldWorkoutsDialog` -> `DeleteConfirmationDialog` -> `onDeleteOldWorkouts`.
  3. Cancellation safety: `DeleteConfirmationDialog` cancellation clears state without calling `onDeleteOldWorkouts`.

---

## Verification Plan

### Automated Tests
1. Run target unit tests:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.DeleteOldWorkoutsConfirmationTest"
   ```
2. Run full regression test suite:
   ```bash
   ./gradlew testDebugUnitTest
   ```

### Manual Verification
1. Open the Workouts tabbed screen.
2. Click the delete old workouts sweep action icon in the header.
3. In `DeleteOldWorkoutsDialog`, change retention days to `180` and tap "Delete".
4. Verify that `DeleteOldWorkoutsDialog` closes and the confirmation dialog appears asking *"Do you really want to delete all workouts older than 180 days?"*.
5. Tap "Cancel" -> verify the confirmation dialog closes and no workouts are deleted.
6. Re-open, proceed to confirmation, and tap "Delete" -> verify that deletion executes, showing the progress modal dialog and ongoing notification from ATT-705.
