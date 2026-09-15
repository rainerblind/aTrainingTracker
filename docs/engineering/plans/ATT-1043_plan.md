# ASPICE Stage 3: Technical Implementation Plan (ATT-1043)

## 1. Goal Description
Eliminate the full-screen `Scaffold` outlier in `EditWorkoutScreen.kt` by refactoring it to a modal bottom sheet (`AppModalBottomSheet`) featuring the standard `AppDialogActions.SaveCancel` action bar per `REQ-UI-151` and `TST-UI-104`. Refactor all 4 fragment caller sites to layer the modal sheet above the active screen content so background lists and maps remain visible behind the translucent scrim.

---

## 2. Proposed Changes

### Component 1: Workout Editor Bottom Sheet
#### [MODIFY] [EditWorkoutScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt)
- Remove `Scaffold` and `TopAppBar`.
- Compose `AppModalBottomSheet` with:
  - `onDismissRequest = onBack`
  - `title = stringResource(R.string.edit_workout)`
  - `icon = Icons.Default.Edit`
  - `onCloseClick = onBack`
- Retain all 8 form sections within scrollable `Column`:
  1. Workout Name (`OutlinedTextField`)
  2. Route / Cluster Assignment (`OutlinedTextField` + clear icon button + tap opens nested `EditWorkoutClusterDialog`)
  3. Spinners (Sport & Equipment via `DropdownSelector`)
  4. Checkboxes (Commute / Trainer)
  5. Strava individual upload toggle
  6. Description (`OutlinedTextField`, multi-line)
  7. Goal (`OutlinedTextField`, single-line)
  8. Method (`OutlinedTextField`, single-line)
- Bottom action bar:
  - `AppDialogActions.SaveCancel(onSave = { viewModel.saveChanges(); onBack() }, onCancel = onBack)`
- Provide `EditWorkoutDialog` composable function (aliased to or wrapping `EditWorkoutScreen` for clean naming parity).

---

### Component 2: Fragment Viewport Layering
#### [MODIFY] [WorkoutSummariesListFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesListFragment.kt)
- Hoist `if (selectedWorkoutIdForEdit != null)` out of the exclusive `if-else` block.
- Render `TrackOnMapScreen` (if `selectedWorkoutForDetailsData != null`) or `WorkoutList` in the background.
- Render `EditWorkoutScreen` as an overlay modal bottom sheet above the active screen when `selectedWorkoutIdForEdit != null`.

#### [MODIFY] [WorkoutSummariesTabbedFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesTabbedFragment.kt)
- Hoist `if (selectedWorkoutIdForEdit != null)` out of the exclusive `if-else` block.
- Render `TrackOnMapScreen` (if `selectedWorkoutForDetails != null`) or `WorkoutTabsScreen` in the background.
- Render `EditWorkoutScreen` as an overlay modal bottom sheet above the active screen when `selectedWorkoutIdForEdit != null`.

#### [MODIFY] [PeriodsFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsFragment.kt)
- Hoist `if (editedWorkoutId != null)` out of the exclusive `if-else` block.
- Render `PeriodMapScreen` or the periods LazyColumn in the background.
- Render `EditWorkoutScreen` as an overlay modal bottom sheet above the active screen when `editedWorkoutId != null`.

#### [MODIFY] [WorkoutClustersFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersFragment.kt)
- Hoist `if (editedWorkoutId != null)` out of the exclusive `when` block.
- Render `ClusterDetailScreen`, `ManualClusterScreen`, `TrackOnMapScreen`, or `WorkoutClustersScreen` in the background.
- Render `EditWorkoutScreen` as an overlay modal bottom sheet above the active screen when `editedWorkoutId != null`.

---

### Component 3: Test Verification
#### [MODIFY] [ModalBottomSheetDialogsIntegrityTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/ModalBottomSheetDialogsIntegrityTest.kt)
- Add `testEditWorkoutDialog_existsAndExposesComposableContract` verifying reflection contracts for `EditWorkoutScreen` and `EditWorkoutDialog`.

---

## 3. Verification Plan
### Automated Tests
- `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.core.ModalBottomSheetDialogsIntegrityTest"`
- `./gradlew testDebugUnitTest` (full suite clean-room regression check, 32 tasks).

### Device Verification
- `./gradlew installDebug` on Google Pixel 10.
- Verify opening workout edit from list, editing details, saving, and verifying underlying screen stays visible under scrim.
