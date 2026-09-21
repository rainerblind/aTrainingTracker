# Walkthrough - ATT-1043: Use Bottom Popup for Edit Workout

## 1. Summary of Changes
Refactored the workout editing experience per `REQ-UI-151` and `TST-UI-104` to eliminate the full-screen `Scaffold` outlier, standardize modal bottom popup ergonomics, and optimize caller viewport composition:

1. **Modal Bottom Sheet Modernization (`EditWorkoutScreen.kt`)**:
   - Replaced full-screen `Scaffold` and `TopAppBar` with `AppModalBottomSheet`.
   - Equipped with leading edit icon (`Icons.Default.Edit`), localized title (`@string/edit_workout`), and standard dismiss callbacks (`onDismissRequest`).
   - Standardized action bar with `AppDialogActions.SaveCancel` ("Abbrechen" / `@string/Cancel` and "Speichern" / `@string/save`).
   - "Speichern" triggers `viewModel.saveChanges()` and dismisses the bottom popup; "Abbrechen", backdrop taps, drag gestures, and close button discard changes without saving.
   - Preserved backward compatibility by providing `EditWorkoutDialog` as an alias for `EditWorkoutScreen`.
   - All 8 form sections (Workout Name, Route/Cluster selection, Sport & Equipment spinners, Commute & Trainer flags, Strava sync toggle, Description, Goal, Method) and nested `EditWorkoutClusterDialog` sheet invocation remain fully functional.

2. **Caller Viewport Layering Optimization**:
   - In all 4 fragment callers, updated conditional composition so the active underlying content remains composed beneath the modal scrim instead of being replaced by a blank background:
     - `WorkoutSummariesListFragment`: Active workout list or map remains composed while `selectedWorkoutIdForEdit != null`.
     - `WorkoutSummariesTabbedFragment`: Active tabs or summary map remains composed while `selectedWorkoutIdForEdit != null`.
     - `PeriodsFragment`: Active period list or period map remains composed while `selectedWorkoutIdForEdit != null`.
     - `WorkoutClustersFragment`: Active clusters screen remains composed while `editedWorkoutId != null`.

3. **Architecture & Contract Integrity Testing**:
   - Extended `ModalBottomSheetDialogsIntegrityTest` with `testEditWorkoutDialog_existsAndExposesComposableContract()` validating that `EditWorkoutDialog` and `EditWorkoutScreen` are exposed with required `@Composable` contracts.

---

## 2. Modified & Created Files
- `[MODIFY]` `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt`
- `[MODIFY]` `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesListFragment.kt`
- `[MODIFY]` `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesTabbedFragment.kt`
- `[MODIFY]` `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsFragment.kt`
- `[MODIFY]` `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersFragment.kt`
- `[MODIFY]` `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/ModalBottomSheetDialogsIntegrityTest.kt`
- `[MODIFY]` `docs/requirements.md`
- `[MODIFY]` `docs/tests.md`
- `[NEW]` `docs/engineering/walkthroughs/ATT-1043_walkthrough.md`

---

## 3. Verification & Validation Evidence
* **SWE.4 Clean-Room Automated Test Suite**:
  - `./gradlew testDebugUnitTest`: BUILD SUCCESSFUL across all 32 actionable tasks (0 failures, 0 regressions).
  - `ModalBottomSheetDialogsIntegrityTest`: PASS (Validating contract and layout compliance for modal bottom sheets including `EditWorkoutDialog`).
* **Physical Hardware Deployment**:
  - Successfully built debug APK and deployed to Google Pixel 10 (`66020DLCR002FL`) via `./gradlew installDebug`.
  - Started `MainActivityWithNavigation` via `adb shell am start`.
* **Living Documentation Verification**:
  - `REQ-UI-151`: Transitioned to `Verified` in `docs/requirements.md`.
  - `TST-UI-104`: Transitioned to `Verified` in `docs/tests.md`.
