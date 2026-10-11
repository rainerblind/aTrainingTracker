# ATT-3165 Walkthrough: Restore Workout Recovery Dialog Visibility and Guard Unfinalized Sessions on Tracking Start

## Overview
- **Issue**: [ATT-3165](https://atrainingtracker.atlassian.net/browse/ATT-3165)
- **Requirement**: `REQ-UI-345`
- **Test Specification**: `TST-UI-305`
- **Sprint**: `2026-41.9`
- **Status**: Verified

## Problem Addressed
During field and regression testing under Sprint 2026-41.9, athletes observed that if the app process terminated unexpectedly while tracking a workout:
1. **Invisible Recovery Dialog**: On app restart, `StartOrResumeDialog` was attached as a `DialogFragment` hosting a Jetpack Compose `ComposeView`, but rendered with zero dimensions or clipped bounds on certain device window managers because explicit window layout parameters were not specified in `onStart()`.
2. **Missing Start Tracking Guard**: In `ControlTrackingViewModel`, tapping "Start Tracking" immediately dispatched `REQUEST_START_TRACKING` without verifying if an unfinished session was pending in SQLite (`WorkoutSummariesDatabaseManager.hasUnfinishedWorkout()`), silently overwriting or isolating unfinalized workout data.
3. **Database Predicate Fragility**: In `WorkoutSummariesDatabaseManager.java`, `hasUnfinishedWorkout()` and `discardOrFinishUnfinishedWorkout()` queried and updated `finished = 0`, but did not match `finished IS NULL` records created by legacy session starts or crashed writes. Furthermore, `TrackerService.createNewWorkout()` did not explicitly write `finished = 0`.
4. **Sticky Service State Desynchronization**: When `TrackerService` resumed after OS process reclamation (`RESUME_SERVICE_RECREATION`), `TrainingApplication.setTracking()` mutated internal tracking state without calling `notifyTrackingStateChanged()`. Furthermore, `BANALServiceRepository.init` hardcoded `_trackingMode.postValue(TrackingMode.READY)`, causing UI controls to desynchronize from the active sticky background service.

## Architectural Changes & Solution

### 1. Window Bounds Configuration (`StartOrResumeDialog.kt`)
Overrode `onStart()` in `StartOrResumeDialog.kt` to explicitly set dialog window layout dimensions:
```kotlin
override fun onStart() {
    super.onStart()
    dialog?.window?.setLayout(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT
    )
}
```
This guarantees that Compose elements expand to the full dialog width and measure dynamically with `WRAP_CONTENT` height.

### 2. Start Tracking Interception & Unfinished Session Guard (`ControlTrackingViewModel.kt` & `TrackingTabsScreen.kt`)
- Added navigation action `ControlNavigation.ShowRecoveryDialog`:
  ```kotlin
  data object ShowRecoveryDialog : ControlNavigation()
  ```
- In `ControlTrackingViewModel.onStartTracking()`:
  ```kotlin
  val hasUnfinished = try {
      WorkoutSummariesDatabaseManager.getInstance(getApplication()).hasUnfinishedWorkout()
  } catch (_: Exception) {
      false
  }

  if (hasUnfinished && !TrainingApplication.isTracking()) {
      _navigationEvent.value = ControlNavigation.ShowRecoveryDialog
      return
  }
  ```
- In `TrackingTabsScreen.kt`, intercepted `ControlNavigation.ShowRecoveryDialog` and displayed `StartOrResumeDialog` via `StartOrResumeInterface.showStartOrResumeDialog()` or direct dialog instantiation.

### 3. Robust SQLite Predicates (`WorkoutSummariesDatabaseManager.java` & `TrackerService.java`)
- In `WorkoutSummariesDatabaseManager.hasUnfinishedWorkout()`:
  ```java
  String selection = "(" + WorkoutSummaries.FINISHED + " IS NULL OR " + WorkoutSummaries.FINISHED + " = 0)";
  try (Cursor cursor = db.query(
          WorkoutSummaries.TABLE,
          new String[]{WorkoutSummaries.C_ID, WorkoutSummaries.FINISHED},
          selection,
          null, null, null,
          WorkoutSummaries.C_ID + " DESC",
          "1"
  )) {
      return cursor != null && cursor.moveToFirst();
  }
  ```
- In `discardOrFinishUnfinishedWorkout()`:
  ```java
  ContentValues values = new ContentValues();
  values.put(WorkoutSummaries.FINISHED, 1);
  String whereClause = "(" + WorkoutSummaries.FINISHED + " IS NULL OR " + WorkoutSummaries.FINISHED + " = 0)";
  db.update(WorkoutSummaries.TABLE, values, whereClause, null);
  ```
- In `TrackerService.createNewWorkout()`:
  ```java
  values.put(WorkoutSummaries.FINISHED, 0);
  ```

### 4. Sticky Service Tracking State Synchronization (`TrainingApplication.java` & `BANALServiceRepository.kt`)
- In `TrainingApplication.java`:
  ```java
  public void setTracking() {
      cTrackingMode = TrackingMode.TRACKING;
      notifyTrackingStateChanged();
  }
  ```
- In `BANALServiceRepository.kt`:
  ```kotlin
  init {
      _trackingMode.postValue(TrainingApplication.getTrackingMode())
      ...
  }
  ```

## Verification & Test Results

### 1. Targeted Unit Tests
- `StartOrResumeDialogLayoutTest.kt`:
  - `testStartOrResumeDialog_overridesOnStartWithExplicitWindowBounds`: PASSED (100%).
- `ControlTrackingViewModelTest.kt`:
  - `testOnStartTracking_whenUnfinishedWorkoutExists_emitsShowRecoveryDialog`: PASSED (100%).
  - `testOnStartTracking_whenNoUnfinishedWorkout_dispatchesStartTrackingBroadcast`: PASSED (100%).
  - `testOnStartTracking_whenAlreadyTracking_dispatchesStartTrackingBroadcast`: PASSED (100%).
- `WorkoutSummariesDatabaseManagerTest.kt`:
  - `testHasUnfinishedWorkout_appliesNullAndZeroFinishedFilter`: PASSED (100%).
  - `testHasUnfinishedWorkout_returnsFalseWhenNoMatchingRows`: PASSED (100%).
  - `testDiscardOrFinishUnfinishedWorkout_appliesNullAndZeroWhereClause`: PASSED (100%).
- `TrainingApplicationTrackingTest.kt`:
  - `testSetTracking_callsNotifyTrackingStateChanged`: PASSED (100%).
- `BANALServiceRepositoryTrackingModeTest.kt`:
  - `testInit_initializesTrackingModeFromTrainingApplication`: PASSED (100%).
  - `testTrackingModeReceiver_updatesTrackingModeOnBroadcast`: PASSED (100%).
- `WorkoutResumptionTest.kt`:
  - All 4 database queries & updates aligned with `(finished IS NULL OR finished = 0)`: PASSED (100%).
- `ControlTrackingPermissionTest.kt`:
  - `testOnStartTracking_dispatchesStartTrackingBroadcast`: PASSED (100%).

### 2. Full Clean-Room Unit Test Suite Execution
- **Command**: `./gradlew testDebugUnitTest`
- **Result**: `BUILD SUCCESSFUL in 2m 12s` (100% pass rate across 2376+ unit tests).
- Zero regressions.

### 3. Requirement Governance Verification
- **Command**: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-41.9`
- **Result**: `Net-new requirement(s) detected: REQ-UI-345. Bypassing archaeology check cleanly.` (Exit code 0).

## Invariants Preserved
- User finalization options (`Start` vs `Resume`) and battery optimization dialog callbacks preserved.
- Existing workout database records and schema version remain unchanged.
- Clean stop finalization (`finished = 1`) preserved.
- No regression across existing tracking, elevation, sensor, and map subsystems.
