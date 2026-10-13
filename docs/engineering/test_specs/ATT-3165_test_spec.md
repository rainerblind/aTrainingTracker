# Stage 2: Requirement & Test Specification - ATT-3165

## 1. Overview & Test Objectives
* **Issue**: [ATT-3165](https://atrainingtracker.atlassian.net/browse/ATT-3165): `[Bug] Restore workout recovery dialog visibility and guard unfinalized sessions on tracking start`
* **Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355): *Good and consistent UI*
* **Requirement**: `REQ-UI-345` (amending `REQ-UI-329`, `REQ-STB-003`, and `REQ-STB-012`)
* **Verification Specification**: `TST-UI-305`
* **Sprint**: `2026-41.9`
* **Primary Objective**: Specify formal requirements, acceptance criteria, and targeted unit test verifications ensuring:
  1. `StartOrResumeDialog` reliably renders with visible window bounds (`MATCH_PARENT, WRAP_CONTENT`).
  2. `ControlTrackingViewModel` intercepts tracking start when unfinalized workouts exist, preventing silent session abandonment.
  3. `WorkoutSummariesDatabaseManager` robustly identifies and finalizes workouts with both `finished = 0` and legacy `finished IS NULL`.
  4. Sticky service resurrection in `TrackerService` broadcasts tracking state changes and initializes `BANALServiceRepository` with active `TRACKING` mode, eliminating UI desynchronization.

---

## 2. Formal Requirement Specification (`REQ-UI-345`)

### REQ-UI-345: Workout Recovery Dialog Layout Visibility, Start Interception Guard & Sticky Tracking State Synchronization

1. **Dialog Window Bounds & Visibility (`StartOrResumeDialog.kt`)**:
   - `StartOrResumeDialog` SHALL override `onStart()` to explicitly configure its window layout dimensions:
     ```kotlin
     dialog?.window?.setLayout(
         WindowManager.LayoutParams.MATCH_PARENT,
         WindowManager.LayoutParams.WRAP_CONTENT
     )
     ```
   - This ensures the Material 3 Compose content hosted inside `ComposeView` expands across the available dialog window without collapsing or measuring as zero width.

2. **Start Tracking Interception & Unfinished Session Guard (`ControlTrackingViewModel.kt` & `TrackingTabsScreen.kt`)**:
   - `ControlNavigation` SHALL declare:
     ```kotlin
     data object ShowRecoveryDialog : ControlNavigation()
     ```
   - When `onStartTracking()` is invoked in `ControlTrackingViewModel`:
     - If `WorkoutSummariesDatabaseManager.hasUnfinishedWorkout() == true` AND `TrainingApplication.isTracking() == false`:
       - The ViewModel SHALL NOT dispatch `REQUEST_START_TRACKING`.
       - The ViewModel SHALL emit `ControlNavigation.ShowRecoveryDialog` via `navigationEvent`.
     - Otherwise:
       - The ViewModel SHALL dispatch `REQUEST_START_TRACKING` via broadcast as normal.
   - When `TrackingTabsScreen` observes `ControlNavigation.ShowRecoveryDialog`:
     - It SHALL invoke `(activity as? StartOrResumeInterface)?.showStartOrResumeDialog()` or instantiate `StartOrResumeDialog().show(...)` if not already visible.

3. **Database Query & Finalization Predicate Robustness (`WorkoutSummariesDatabaseManager.java` & `TrackerService.java`)**:
   - In `WorkoutSummariesDatabaseManager.hasUnfinishedWorkout()`:
     - The SQLite query SHALL filter rows matching `(finished IS NULL OR finished = 0)`.
     - It SHALL return `true` if any unfinalized workout exists in SQLite.
   - In `WorkoutSummariesDatabaseManager.discardOrFinishUnfinishedWorkout()`:
     - The update SHALL match rows with `(finished IS NULL OR finished = 0)`.
   - In `TrackerService.createNewWorkout()`:
     - The initial workout insert values SHALL explicitly include `values.put(WorkoutSummaries.FINISHED, 0)`.

4. **Sticky Service Tracking State UI Synchronization (`TrainingApplication.java` & `BANALServiceRepository.kt`)**:
   - In `TrainingApplication.setTracking()`:
     - The method SHALL invoke `notifyTrackingStateChanged()`, dispatching the `TRACKING_STATE_CHANGED` broadcast intent and updating notifications.
   - In `BANALServiceRepository.init`:
     - `_trackingMode` LiveData SHALL be initialized with `TrainingApplication.getTrackingMode()` rather than hardcoded `TrackingMode.READY`.
   - When `TrackerService` resumes via `RESUME_SERVICE_RECREATION`, `ControlTrackingScreen` SHALL display the active tracking controls (e.g. Pause button) and `StartOrResumeDialog` SHALL NOT be shown.

---

## 3. Traceability Matrix

| Requirement Clause | Test Case ID | Test Class / Implementation | Test Method / Verification Target |
| :--- | :--- | :--- | :--- |
| `REQ-UI-345.1` (Dialog Window Bounds) | `TST-UI-305.1` | `StartOrResumeDialogLayoutTest.kt` | `testStartOrResumeDialog_overridesOnStartWithExplicitWindowBounds` |
| `REQ-UI-345.2` (Start Interception Guard) | `TST-UI-305.2` | `ControlTrackingViewModelTest.kt` | `testOnStartTracking_emitsShowRecoveryDialog_whenUnfinishedWorkoutExistsAndNotTracking`<br>`testOnStartTracking_sendsBroadcast_whenNoUnfinishedWorkout`<br>`testOnStartTracking_sendsBroadcast_whenAlreadyTracking` |
| `REQ-UI-345.3` (Database SQL Predicates) | `TST-UI-305.3` | `WorkoutSummariesDatabaseManagerTest.kt` | `testHasUnfinishedWorkout_detectsNullAndZeroFinished`<br>`testDiscardOrFinishUnfinishedWorkout_updatesNullAndZeroFinished` |
| `REQ-UI-345.4` (Sticky State Sync) | `TST-UI-305.4` | `BANALServiceRepositoryTest.kt` / `TrainingApplicationTest.kt` | `testSetTracking_invokesNotifyTrackingStateChanged`<br>`testBanalServiceRepository_initializesTrackingModeFromApplication` |
| Full Regression | `TST-UI-305.5` | `./gradlew testDebugUnitTest` | 100% full clean-room unit test suite pass rate |

---

## 4. Detailed Test Specification

### 4.1 Dialog Window Layout Contract Verification (`TST-UI-305.1`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/dialogs/StartOrResumeDialogLayoutTest.kt`
* **Assertions**:
  - Verify `StartOrResumeDialog.kt` declares an `onStart()` method override.
  - Verify `onStart()` contains calls to `dialog?.window?.setLayout(` with `WindowManager.LayoutParams.MATCH_PARENT` and `WindowManager.LayoutParams.WRAP_CONTENT`.

### 4.2 Start Tracking Guard Unit Tests (`TST-UI-305.2`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingViewModelTest.kt`
* **Test Cases**:
  1. *Unfinished Workout & Not Tracking*:
     - Mock `WorkoutSummariesDatabaseManager.hasUnfinishedWorkout() == true`.
     - Set `TrainingApplication.isTracking() == false`.
     - Invoke `viewModel.onStartTracking()`.
     - Assert `navigationEvent` emits `ControlNavigation.ShowRecoveryDialog`.
     - Assert `sendBroadcast(REQUEST_START_TRACKING)` was NOT sent.
  2. *No Unfinished Workout*:
     - Mock `WorkoutSummariesDatabaseManager.hasUnfinishedWorkout() == false`.
     - Invoke `viewModel.onStartTracking()`.
     - Assert `sendBroadcast(REQUEST_START_TRACKING)` was dispatched.
     - Assert `navigationEvent` did not emit `ShowRecoveryDialog`.
  3. *Already Tracking*:
     - Set `TrainingApplication.isTracking() == true`.
     - Mock `WorkoutSummariesDatabaseManager.hasUnfinishedWorkout() == true`.
     - Invoke `viewModel.onStartTracking()`.
     - Assert `sendBroadcast(REQUEST_START_TRACKING)` was dispatched.

### 4.3 Database Query Predicate Unit Tests (`TST-UI-305.3`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManagerTest.kt`
* **Test Cases**:
  1. *NULL Finished Identification*:
     - Insert a workout with `finished = NULL`.
     - Assert `hasUnfinishedWorkout() == true`.
  2. *Zero Finished Identification Behind Finished Workout*:
     - Insert workout A with `finished = 0`.
     - Insert newer workout B with `finished = 1`.
     - Assert `hasUnfinishedWorkout() == true` (not masked by newer finished workout).
  3. *NULL and Zero Finalization*:
     - Insert workout with `finished = NULL`.
     - Call `discardOrFinishUnfinishedWorkout()`.
     - Assert query for `finished IS NULL OR finished = 0` returns 0 rows.
     - Assert `hasUnfinishedWorkout() == false`.

### 4.4 Sticky Service State Sync Contract Tests (`TST-UI-305.4`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepositoryTest.kt`
* **Test Cases**:
  1. Verify `TrainingApplication.setTracking()` invokes `notifyTrackingStateChanged()`.
  2. Verify `BANALServiceRepository` initializes `_trackingMode` with current `TrainingApplication.getTrackingMode()` (e.g. `TrackingMode.TRACKING` when initialized during active tracking).

---

## 5. Localization Audit
* **Target Resources**:
  - `R.string.unfinished_workout_title`
  - `R.string.unfinished_workout_title_battery`
  - `R.string.unfinished_workout_title_memory`
  - `R.string.unfinished_workout_title_permission`
  - `R.string.start_or_resume_dialog_message`
  - `R.string.resume_workout`
  - `R.string.start_new_workout`
  - `R.string.action_disable_battery_optimization`
* **Status**: 100% existing string resources verified across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
* Zero new strings required.

---

## 6. Acceptance Criteria (Given-When-Then)

1. *Dialog Window Sizing*:
   - **Given** `StartOrResumeDialog` is attached and displayed,
   - **When** `onStart()` executes,
   - **Then** the dialog window layout parameters are explicitly set to `MATCH_PARENT` width and `WRAP_CONTENT` height.
2. *Start Tracking Interception*:
   - **Given** an unfinalized workout exists in SQLite and the app is not tracking,
   - **When** the athlete taps the Start Tracking button,
   - **Then** tracking start is intercepted and `ShowRecoveryDialog` is emitted.
3. *Database SQL Robustness*:
   - **Given** an unfinalized workout with `finished IS NULL` or `finished = 0`,
   - **When** `hasUnfinishedWorkout()` or `discardOrFinishUnfinishedWorkout()` is executed,
   - **Then** the record is matched and updated to `finished = 1`.
4. *Sticky Service UI Synchronization*:
   - **Given** `TrackerService` resumes via `RESUME_SERVICE_RECREATION`,
   - **When** the UI loads `ControlTrackingScreen`,
   - **Then** `trackingMode` evaluates to `TrackingMode.TRACKING`, displaying the Pause button without showing the recovery dialog.
