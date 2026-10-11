# Stage 3: Implementation Plan - ATT-3165

## 1. Executive Summary & Architectural Invariants (SWE.2)
* **Issue**: [ATT-3165](https://atrainingtracker.atlassian.net/browse/ATT-3165): `[Bug] Restore workout recovery dialog visibility and guard unfinalized sessions on tracking start`
* **Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355): *Good and consistent UI*
* **Requirement**: `REQ-UI-345` (amending `REQ-UI-329`, `REQ-STB-003`, and `REQ-STB-012`)
* **Test Specification**: `TST-UI-305`
* **Sprint**: `2026-41.9`

### Architectural Invariants
1. **Dialog Window Bounds & Visibility**: Floating `DialogFragment` windows on Android require explicit `MATCH_PARENT` width and `WRAP_CONTENT` height in `onStart()` to ensure Compose `fillMaxWidth()` surfaces properly measure and render across all devices and screen densities.
2. **Start Tracking Interception**: Unfinalized workouts in SQLite must never be silently masked or abandoned by starting a new workout. If `hasUnfinishedWorkout() == true` while not tracking, `ControlTrackingViewModel` intercepts `onStartTracking()` and presents `StartOrResumeDialog`.
3. **Database Query Predicates**: SQLite rows where `finished IS NULL` or `finished = 0` must both be treated as unfinalized across query (`hasUnfinishedWorkout`) and update (`discardOrFinishUnfinishedWorkout`). Initial row creation in `TrackerService.createNewWorkout()` must explicitly populate `finished = 0`.
4. **Sticky Tracking State Synchronization**: When `TrackerService` resumes from an OS kill via `START_STICKY` (`RESUME_SERVICE_RECREATION`), `TrainingApplication.setTracking()` must broadcast `TRACKING_STATE_CHANGED`, and `BANALServiceRepository` must initialize its tracking mode directly from `TrainingApplication.getTrackingMode()`, ensuring `ControlTrackingScreen` immediately presents the Pause button without showing the recovery dialog.
5. **UI Consistency (Rule 23)**:
   - Closest reference: `StartOrResumeDialog.kt` (Material 3 Compose dialog surface).
   - Reused components: `Button` (primary resume), `OutlinedButton` (secondary start fresh), `FilledTonalButton` (battery optimization).
   - Tokens: `AlertDialogDefaults.shape` (`28.dp`), `AlertDialogDefaults.containerColor`, `AlertDialogDefaults.TonalElevation`.
   - Zero one-off styles; 100% adherence to `docs/design_guidelines.md`.
6. **Scope Bounding (Rule 26 / ATT-1250)**: Confined strictly to `StartOrResumeDialog.kt`, `ControlTrackingViewModel.kt`, `TrackingTabsScreen.kt`, `WorkoutSummariesDatabaseManager.java`, `TrackerService.java`, `TrainingApplication.java`, `BANALServiceRepository.kt`, and corresponding test suites.

---

## 2. Component Architecture & Data Flow

```
User Taps "Start Tracking" (ControlTrackingScreen)
       │
       ▼
ControlTrackingViewModel.onStartTracking()
       │
       ├──► Is TrainingApplication.isTracking()?
       │         └── YES: Send REQUEST_START_TRACKING broadcast
       │
       └──► Is WorkoutSummaries.hasUnfinishedWorkout()?
                 ├── YES: Emit ControlNavigation.ShowRecoveryDialog
                 │         │
                 │         ▼
                 │    TrackingTabsScreen
                 │         └── (activity as? StartOrResumeInterface)?.showStartOrResumeDialog()
                 │                   └── StartOrResumeDialog.onStart() enforces MATCH_PARENT x WRAP_CONTENT
                 │
                 └── NO: Send REQUEST_START_TRACKING broadcast (Starts clean workout 5080)

OS Kills Service & Revives via START_STICKY (RESUME_SERVICE_RECREATION)
       │
       ▼
TrackerService.onStartCommand()
       │
       └──► TrainingApplication.setTracking()
                 ├── cTrackingMode = TrackingMode.TRACKING
                 └── notifyTrackingStateChanged() ──► Broadcast TRACKING_STATE_CHANGED
                           │
                           ▼
                 BANALServiceRepository
                      - init { _trackingMode.postValue(TrainingApplication.getTrackingMode()) }
                      - receiver { _trackingMode.postValue(newTrackingMode) }
                           │
                           ▼
                 ControlTrackingScreen displays "Pause" button (In-sync!)
```

---

## 3. Step-by-Step Construction Plan (Atomic Steps)

### Step 1: Enforce Dialog Window Dimensions in `StartOrResumeDialog.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/dialogs/StartOrResumeDialog.kt`
* **Action**:
  Add `onStart()` method override:
  ```kotlin
  override fun onStart() {
      super.onStart()
      dialog?.window?.setLayout(
          android.view.WindowManager.LayoutParams.MATCH_PARENT,
          android.view.WindowManager.LayoutParams.WRAP_CONTENT
      )
  }
  ```
* **Test Verification**:
  Add `testStartOrResumeDialog_overridesOnStartWithExplicitWindowBounds` in `app/src/test/java/com/atrainingtracker/trainingtracker/dialogs/StartOrResumeDialogLayoutTest.kt`.

### Step 2: Implement Start Tracking Guard in `ControlTrackingViewModel.kt` & `TrackingTabsScreen.kt`
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingViewModel.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`
* **Action**:
  1. In `ControlTrackingViewModel.kt`:
     - Add `data object ShowRecoveryDialog : ControlNavigation()` to sealed class `ControlNavigation`.
     - In `onStartTracking()`:
       ```kotlin
       fun onStartTracking() {
           val dbManager = WorkoutSummariesDatabaseManager.getInstance(application)
           if (!TrainingApplication.isTracking() && dbManager.hasUnfinishedWorkout()) {
               viewModelScope.launch {
                   _navigationEvent.emit(ControlNavigation.ShowRecoveryDialog)
               }
           } else {
               sendBroadcast(TrainingApplication.REQUEST_START_TRACKING)
           }
       }
       ```
  2. In `TrackingTabsScreen.kt`:
     - In `LaunchedEffect(Unit)` navigation event collector:
       ```kotlin
       is ControlNavigation.ShowRecoveryDialog -> {
           (activity as? StartOrResumeInterface)?.showStartOrResumeDialog()
               ?: if (activity.supportFragmentManager.findFragmentByTag(StartOrResumeDialog.TAG) == null) {
                   StartOrResumeDialog().show(activity.supportFragmentManager, StartOrResumeDialog.TAG)
               }
       }
       ```
* **Test Verification**:
  Add unit tests in `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingViewModelTest.kt` validating navigation emission when unfinished workout exists and broadcast dispatch when clean or tracking.

### Step 3: Fix SQLite Predicates in `WorkoutSummariesDatabaseManager.java` & `TrackerService.java`
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
* **Action**:
  1. In `TrackerService.createNewWorkout()`:
     Add:
     ```java
     values.put(WorkoutSummaries.FINISHED, 0);
     ```
  2. In `WorkoutSummariesDatabaseManager.hasUnfinishedWorkout()`:
     Update query selection:
     ```java
     String selection = "(" + WorkoutSummaries.FINISHED + " IS NULL OR " + WorkoutSummaries.FINISHED + " = 0)";
     try (Cursor cursor = db.query(
             WorkoutSummaries.TABLE,
             new String[]{WorkoutSummaries.C_ID, WorkoutSummaries.FINISHED},
             selection,
             null,
             null,
             null,
             WorkoutSummaries.C_ID + " DESC",
             "1"
     )) {
         return cursor != null && cursor.moveToFirst();
     }
     ```
  3. In `WorkoutSummariesDatabaseManager.discardOrFinishUnfinishedWorkout()`:
     Update whereClause:
     ```java
     ContentValues values = new ContentValues();
     values.put(WorkoutSummaries.FINISHED, 1);
     String whereClause = "(" + WorkoutSummaries.FINISHED + " IS NULL OR " + WorkoutSummaries.FINISHED + " = 0)";
     db.update(WorkoutSummaries.TABLE, values, whereClause, null);
     ```
* **Test Verification**:
  Add unit tests in `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManagerTest.kt` verifying detection of `NULL` and `0` finished workouts, resistance to masking by newer finished workouts, and correct bulk finalization.

### Step 4: Synchronize Tracking Mode on Sticky Recreation in `TrainingApplication.java` & `BANALServiceRepository.kt`
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepository.kt`
* **Action**:
  1. In `TrainingApplication.java`:
     Update `setTracking()`:
     ```java
     public void setTracking() {
         cTrackingMode = TrackingMode.TRACKING;
         notifyTrackingStateChanged();
     }
     ```
  2. In `BANALServiceRepository.kt`:
     In `init`:
     ```kotlin
     _trackingMode.postValue(TrainingApplication.getTrackingMode())
     ```
* **Test Verification**:
  Add unit tests in `app/src/test/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepositoryTest.kt` / `TrainingApplicationTest.kt`.

### Step 5: Full Clean-Room Regression Verification
* Execute `./gradlew testDebugUnitTest` ensuring 100% test pass rate with zero regressions.

---

## 4. Verification & Testing Strategy
* **Targeted Test 1**: `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.dialogs.StartOrResumeDialogLayoutTest`
* **Targeted Test 2**: `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingViewModelTest`
* **Targeted Test 3**: `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManagerTest`
* **Targeted Test 4**: `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.repositories.BANALServiceRepositoryTest`
* **Clean-Room Test**: `./gradlew testDebugUnitTest`
