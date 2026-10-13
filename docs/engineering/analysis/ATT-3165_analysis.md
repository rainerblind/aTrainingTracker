# Stage 1: Problem Domain & Root Cause Analysis - ATT-3165

## 1. Executive Summary & Problem Domain
* **Issue**: [ATT-3165](https://atrainingtracker.atlassian.net/browse/ATT-3165): `[Bug] Restore workout recovery dialog visibility and guard unfinalized sessions on tracking start`
* **Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355): *Good and consistent UI*
* **Target Requirements**: `REQ-UI-345` (amending `REQ-UI-329`, `REQ-STB-003`, and `REQ-STB-012`)
* **Target Test Specs**: `TST-UI-305`
* **Sprint**: `2026-41.9`

### Problem Description
When an unfinalized workout exists in SQLite (for example, following an unexpected process termination, crash, or battery optimization kill such as workout session 5078), reopening the application fails to visibly present the recovery dialog (`StartOrResumeDialog`) to the user.
Furthermore:
1. When an athlete taps the 'Play' (Start Tracking) button on `ControlTrackingScreen`, the application unconditionally commences a brand new workout (e.g., workout 5079) without checking whether an unfinalized workout is pending, permanently masking the crashed session and causing data loss or confusion.
2. If `TrackerService` is recreated in the background by the Android OS via `START_STICKY` (`RESUME_SERVICE_RECREATION`) while tracking is ongoing, the UI in Control Tracking desynchronizes and displays the 'Play' (Start Tracking) button instead of the 'Pause' button. In this active sticky state, the recovery dialog must not be presented, and the UI must immediately reflect active tracking.

---

## 2. Forensic Investigation & Technical Root Cause

Forensic investigation revealed four interlocking defects across the UI, service lifecycle, and persistence layers:

### 2.1 Invisible Dialog Window in `StartOrResumeDialog.kt` (ATT-2948 / `fe4e24f1`)
* When `StartOrResumeDialog` was migrated to Material 3 Jetpack Compose (`ATT-2948`), it configured a transparent window background:
  ```kotlin
  dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
  ```
* However, because it relies on standard `DialogFragment` with `STYLE_NO_TITLE` and Android's default floating dialog theme (`android:windowIsFloating=true`), the window dimensions default to `WRAP_CONTENT` on both axes.
* Inside `onCreateView`, the root `ComposeView` hosts a `Surface` with `Modifier.fillMaxWidth()`. In an unconstrained `WRAP_CONTENT` dialog window, Compose `fillMaxWidth()` measures as 0 width or collapses into an invisible layout hierarchy. The fragment attaches to the window manager, but nothing is rendered visibly on screen.
* **Remedy**: Implement `onStart()` in `StartOrResumeDialog`:
  ```kotlin
  override fun onStart() {
      super.onStart()
      dialog?.window?.setLayout(
          WindowManager.LayoutParams.MATCH_PARENT,
          WindowManager.LayoutParams.WRAP_CONTENT
      )
  }
  ```

### 2.2 Unguarded Tracking Start in `ControlTrackingViewModel.kt`
* `ControlTrackingViewModel.onStartTracking()` unconditionally dispatches:
  ```kotlin
  fun onStartTracking() {
      sendBroadcast(TrainingApplication.REQUEST_START_TRACKING)
  }
  ```
* When an unfinalized workout exists in the database and tracking is not active, tapping 'Start' directly starts a new workout, abandoning the recoverable session.
* **Remedy**:
  - Add navigation event `data object ShowRecoveryDialog : ControlNavigation()` to `ControlNavigation`.
  - In `ControlTrackingViewModel.onStartTracking()`:
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
  - In `TrackingTabsScreen.kt`, handle `ControlNavigation.ShowRecoveryDialog`:
    ```kotlin
    is ControlNavigation.ShowRecoveryDialog -> {
        (activity as? StartOrResumeInterface)?.showStartOrResumeDialog()
            ?: if (activity.supportFragmentManager.findFragmentByTag(StartOrResumeDialog.TAG) == null) {
                StartOrResumeDialog().show(activity.supportFragmentManager, StartOrResumeDialog.TAG)
            }
    }
    ```

### 2.3 SQL Predicate Flaws in `WorkoutSummariesDatabaseManager.java` & `TrackerService.java`
* In `TrackerService.createNewWorkout()`:
  `WorkoutSummaries.FINISHED` is not populated in the initial `ContentValues` insert and defaults to SQLite `NULL`.
* In `WorkoutSummariesDatabaseManager.hasUnfinishedWorkout()`:
  The query executes:
  ```java
  db.query(WorkoutSummaries.TABLE, new String[]{WorkoutSummaries.C_ID, WorkoutSummaries.FINISHED},
      null, null, null, null, WorkoutSummaries.C_ID + " DESC", "1")
  ```
  Because there is no `selection` filter, it only inspects the most recent single record (`ORDER BY _id DESC LIMIT 1`). If a newer workout was created and finished, any previous unfinalized workout is completely ignored and unrecoverable.
* In `WorkoutSummariesDatabaseManager.discardOrFinishUnfinishedWorkout()`:
  The update executes:
  ```java
  db.update(WorkoutSummaries.TABLE, values, WorkoutSummaries.FINISHED + " = 0", null);
  ```
  In SQL three-valued logic, `NULL = 0` evaluates to `UNKNOWN` (false). Therefore, records with `finished IS NULL` are never finalized!
* **Remedy**:
  1. In `TrackerService.createNewWorkout()`, explicitly insert `values.put(WorkoutSummaries.FINISHED, 0);`.
  2. In `hasUnfinishedWorkout()`, use selection: `"(" + WorkoutSummaries.FINISHED + " IS NULL OR " + WorkoutSummaries.FINISHED + " = 0)"`.
  3. In `discardOrFinishUnfinishedWorkout()`, use selection: `"(" + WorkoutSummaries.FINISHED + " IS NULL OR " + WorkoutSummaries.FINISHED + " = 0)"`.

### 2.4 UI State Desynchronization on Sticky Service Recreation
* When `TrackerService` is recreated by the Android OS with `START_STICKY` (`RESUME_SERVICE_RECREATION`), it calls `mTrainingApplication.setTracking()`.
* In `TrainingApplication.java`:
  ```java
  // TODO: Note that here notifyTrackingStateChanged is not called!
  public void setTracking() {
      cTrackingMode = TrackingMode.TRACKING;
  }
  ```
  `notifyTrackingStateChanged()` is not called, so `TRACKING_STATE_CHANGED` is never broadcast.
* In `BANALServiceRepository.kt`:
  ```kotlin
  init {
      _trackingMode.postValue(TrackingMode.READY)
      ...
  }
  ```
  The repository initialization hardcodes `READY`. Because `setTracking()` never dispatched a broadcast, `_trackingMode` remains at `READY`, causing `ControlTrackingScreen` to show the 'Start Tracking' button while the service is actively tracking.
* **Remedy**:
  1. In `TrainingApplication.setTracking()`, invoke `notifyTrackingStateChanged()`.
  2. In `BANALServiceRepository.init`, initialize `_trackingMode` with `TrainingApplication.getTrackingMode()`:
     ```kotlin
     _trackingMode.postValue(TrainingApplication.getTrackingMode())
     ```

---

## 3. Chesterton's Fence & Requirement Archaeology

1. **Original Requirement IDs & Targets**:
   - `REQ-STB-003` (*Interrupted Workout Resumption & Unfinished Workout Recovery*): Mandates that when an unfinalized workout exists, the system presents `StartOrResumeDialog` to resume or start fresh.
   - `REQ-STB-012` (*Forensic Process Kill Diagnosis*): Mandates diagnosis of unexpected background terminations.
   - `REQ-UI-329` (*Material 3 Jetpack Compose Workout Recovery Dialog Architecture*): Migrated `StartOrResumeDialog` to Compose while maintaining `DialogFragment` and `StartOrResumeInterface`.
   - `REQ-TRK-016` (*Guaranteed Workout Finalization*): Mandates `FINISHED = 1` upon clean completion.
2. **Historical Origin & Commit Trace**:
   - `ATT-2948` (commit `fe4e24f1`): Migrated dialog to Compose. Replaced `AlertDialog.Builder` with `ComposeView` but omitted `onStart()` window layout dimensions.
   - `ATT-635` / `ATT-987`: Introduced `hasUnfinishedWorkout()` and `discardOrFinishUnfinishedWorkout()`.
3. **Reason for Refinement (Chesterton's Fence Forensics)**:
   - Floating Compose DialogFragments on Android require explicit window bounds (`MATCH_PARENT`, `WRAP_CONTENT`) to prevent layout collapse.
   - Guarding `onStartTracking()` ensures the user is warned even if the auto-popup was dismissed or prevented.
   - Robust SQL predicates (`IS NULL OR = 0`) prevent data loss from legacy NULL column states.
   - Sticky service state propagation guarantees the athlete sees the Pause/Stop buttons when tracking is active.
4. **Preservation of Core Invariants**:
   - `StartOrResumeInterface` callbacks (`chooseStart()`, `chooseResume()`) remain 100% intact.
   - Clean-stop workouts (`FINISHED = 1`) never trigger the recovery dialog.
   - Sticky resurrection continues without user interruption while keeping the UI in sync.

---

## 4. Scope Bounding & Invariants (Rule 26 / ATT-1250)

* **Bounded Scope**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/dialogs/StartOrResumeDialog.kt`:
    - Add `onStart()` overriding window layout to `MATCH_PARENT, WRAP_CONTENT`.
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingViewModel.kt`:
    - Add `ControlNavigation.ShowRecoveryDialog`.
    - Guard `onStartTracking()` to check `hasUnfinishedWorkout() && !isTracking()`.
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`:
    - Observe `ShowRecoveryDialog` event and invoke recovery dialog.
  - `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java`:
    - Update `hasUnfinishedWorkout()` and `discardOrFinishUnfinishedWorkout()` with `(finished IS NULL OR finished = 0)`.
  - `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`:
    - Insert `WorkoutSummaries.FINISHED = 0` in `createNewWorkout()`.
  - `app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`:
    - Call `notifyTrackingStateChanged()` in `setTracking()`.
  - `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepository.kt`:
    - Initialize `_trackingMode` with `TrainingApplication.getTrackingMode()`.
  - Unit tests in `StartOrResumeDialogLayoutTest.kt`, `ControlTrackingViewModelTest.kt`, and `WorkoutSummariesDatabaseManagerTest.kt`.
* **Zero Out-of-Scope Modifications**:
  - No changes to BLE/ANT+ communication drivers.
  - No changes to database schema versions (no table migrations).
  - No changes to notification channel configurations.

---

## 5. ASPICE Traceability Matrix & Impact Analysis

| Component | Target File | Impact |
| :--- | :--- | :--- |
| **Dialog Layout** | `StartOrResumeDialog.kt` | Implement `onStart()` setting explicit window layout bounds. |
| **Tracking Start Guard** | `ControlTrackingViewModel.kt`, `TrackingTabsScreen.kt` | Intercept `onStartTracking()` when unfinalized session exists. |
| **Database Queries** | `WorkoutSummariesDatabaseManager.java`, `TrackerService.java` | Check and update unfinalized records with `(finished IS NULL OR finished = 0)`. |
| **Sticky State Sync** | `TrainingApplication.java`, `BANALServiceRepository.kt` | Broadcast tracking state in `setTracking()` and initialize repository with active mode. |
| **Verification Tests** | `StartOrResumeDialogLayoutTest.kt`, `ControlTrackingViewModelTest.kt`, `WorkoutSummariesDatabaseManagerTest.kt` | Validate window layout, ViewModel interception, and SQL semantics. |
| **Living Docs** | `docs/requirements.md` (`REQ-UI-345`), `docs/tests.md` (`TST-UI-305`) | Document requirements and test criteria. |
