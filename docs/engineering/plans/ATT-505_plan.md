# Implementation Plan: ATT-505 - Live Tracking Period Creation & Update

## 1. Goal Description
Resolve the defect where newly recorded workouts tracked live via `TrackerService` do not create or update period summaries (Day, Week, Month, Year). While TCX imports (via `LegacyImportEngine`) properly broadcast `WORKOUT_UPDATED_INTENT` with `WORKOUT_ID` over `LocalBroadcastManager`, `TrackerService.endWorkout()` currently emits only a package-targeted system broadcast of `TRACKING_FINISHED_INTENT` without `WORKOUT_ID` and without `LocalBroadcastManager`.

Because `WorkoutRepository` listens strictly on `LocalBroadcastManager` and requires `WORKOUT_ID` to trigger `reloadWorkoutData(workoutId)`, it is starved of completion notifications for live-tracked sessions. This blocks the surgical execution of `PeriodsRepository.onWorkoutFinished(freshWorkoutData)` (and `WorkoutClusterEngine.onWorkoutFinished`), leaving `PeriodSummariesDatabaseManager` without a record of the newly completed session.

---

## 2. User Review Required
> [!NOTE]
> No breaking changes to database schemas or external API contracts. Existing system-level broadcast listeners (`TrainingApplication`, `MainActivityWithNavigation`) continue to receive `TRACKING_FINISHED_INTENT` uninterrupted.

---

## 3. Proposed Changes

### Component 1: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
#### [MODIFY] [TrackerService.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java)
- In `endWorkout()`:
  - Add `putExtra(WORKOUT_ID, mWorkoutID)` to the system-level `TRACKING_FINISHED_INTENT` broadcast for complete diagnostic context.
  - Dispatch `WORKOUT_UPDATED_INTENT` with `putExtra(WORKOUT_ID, mWorkoutID)` via `LocalBroadcastManager.getInstance(this).sendBroadcast(...)`, matching the proven pattern in `LegacyImportEngine.kt`.
  - Dispatch `TRACKING_FINISHED_INTENT` with `putExtra(WORKOUT_ID, mWorkoutID)` via `LocalBroadcastManager.getInstance(this).sendBroadcast(...)` to fulfill the intent filter registered by `WorkoutRepository`.

### Component 2: `docs/requirements.md`
#### [MODIFY] [requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md)
- Formalize `REQ-TRK-010` ("Live Tracking Finalization Event Dispatch").

### Component 3: `docs/tests.md`
#### [MODIFY] [tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md)
- Formalize `TST-PER-016` ("Live Tracking Period Creation & Update Verification") mapped to sub-task `ATT-590`.

---

## 4. Verification Plan

### Automated Tests
- Execute unit test suite `./gradlew testDebugUnitTest` to verify no regressions in background tracking, repository data flow, or period aggregation calculations.

### Manual Verification (`TST-PER-016`)
1. Start an active tracking session in the application or trigger `TrackerService.endWorkout()`.
2. Stop and finish the tracking session.
3. Verify in logcat that `WorkoutRepository` receives the `LocalBroadcastManager` completion broadcast with the valid `workoutId`.
4. Verify that `WorkoutRepository.reloadWorkoutData` executes and triggers `PeriodsRepository.onWorkoutFinished`.
5. Open the **Periods** tab:
   - Verify a new **Day** card is generated containing the recorded workout.
   - Verify the parent **Week**, **Month**, and **Year** cards reflect the updated duration, distance, ascent, and workout count.
