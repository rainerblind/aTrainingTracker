# Stage 3: Implementation Plan - ATT-4001: [Bug] Launch intent re-applied on activity recreation can auto-resume tracking

**Ticket**: [ATT-4001](https://rainerblind.atlassian.net/browse/ATT-4001)  
**Sub-task**: [ATT-4454](https://rainerblind.atlassian.net/browse/ATT-4454) (`[Impl-Plan]`)  
**Parent Epic**: None  
**Target Release**: Next Sprint  
**Active Sprint**: `2026-43.3`  
**Requirement Mapping**: `REQ-STB-017` (*MainActivity Launch Intent Lifecycle Sanitization, Transient Extra Consumption & Safe Resumption Guards*)  
**Test Mapping**: `TST-STB-017` (`TST-STB-017.1` .. `TST-STB-017.6`)  
**Branch**: `feature/ATT-4001`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

During an independent whole-app architecture review (`feature/ATT-3921` @ `0234bfc0`, label `code-review-2026-10`), a critical Android framework lifecycle defect was discovered in `MainActivityWithNavigation`:

1. **Unintended Tracking Auto-Resumption After Process Death & Resurrection**:
   - When an active workout is interrupted by the system, `TrackerService.showTrackingInterruptedNotification()` posts a notification with `PendingIntent` extras `EXTRA_RESUME_INTERRUPTED_WORKOUT = true` and `SELECTED_FRAGMENT = START_OR_TRACKING`.
   - When the athlete taps the notification, `MainActivityWithNavigation.onCreate()` executes `handleIntent(intent)`. This invokes `chooseResume()`, sets `TrainingApplication.setResumeFromCrash(true)`, and broadcasts `REQUEST_START_TRACKING`, resuming tracking (`REQ-STB-003.2`).
   - If the athlete later completes or stops the workout and the app process is terminated in the background by the Android OS under memory pressure (or developer option *"Don't keep activities"*), Android's `ActivityManagerService` persists the base launch intent for the task.
   - When the athlete returns to the app from the Recent Tasks list, Android launches a new process and recreates `MainActivityWithNavigation`, passing the original launch intent (`intent`) with `savedInstanceState != null`.
   - Because `onCreate()` calls `handleIntent(intent)` unconditionally, `handleIntent()` executes again, sees `EXTRA_RESUME_INTERRUPTED_WORKOUT == true`, calls `chooseResume()`, and dispatches `REQUEST_START_TRACKING`.
   - `TrainingApplication.startTracking()` launches `TrackerService` with `RESUME_BY_USER`, which queries the last workout summary (`ORDER BY c_id DESC LIMIT 1` without filtering `FINISHED == 0`) and resumes tracking without athlete knowledge or consent, modifying previously finalized records.

2. **Screen Navigation Reset & Filter Wipeout on Activity Configuration Change**:
   - When launched from a deep link or notification (e.g. tracking notification with `SELECTED_FRAGMENT = START_OR_TRACKING`, or sensor battery notification with `EXTRA_DRAWER_ITEM_ID = R.id.drawer_my_sensors` and `EXTRA_FILTER_CRITICAL_BATTERY = true`), the athlete frequently navigates away to another hub (e.g. `drawer_workouts` or Settings).
   - When an activity configuration change occurs (dark mode toggle, screen rotation, multi-window resize, or locale change), `MainActivityWithNavigation` is recreated with `savedInstanceState != null`.
   - `savedInstanceState` correctly restores `mSelectedFragmentId` to the active screen (e.g. `R.id.drawer_workouts`).
   - However, `onCreate()` immediately calls `handleIntent(intent)` unconditionally. Because `EXTRA_DRAWER_ITEM_ID`, `SELECTED_FRAGMENT`, and `EXTRA_FILTER_CRITICAL_BATTERY` were never removed from the intent (and even for `EXTRA_RESUME_INTERRUPTED_WORKOUT`, its companion `SELECTED_FRAGMENT` was not removed), `handleIntent()` re-evaluates the stale extras and overrides `mSelectedFragmentId` back to the original notification target (e.g. `drawer_start_tracking`).
   - In addition, calling `navigateToDrawerItem(R.id.drawer_start_tracking)` triggers `MyPreferenceManager.clearWorkoutFilterCriteria()`, `clearRouteFilterCriteria()`, and `clearClusterFilterCriteria()`, wiping out active filters.

3. **Absence of Defensive State Guards in `chooseResume()`**:
   - `chooseResume()` currently executes `TrainingApplication.setResumeFromCrash(true)` and sends `REQUEST_START_TRACKING` without verifying if tracking is already active or whether an unfinished workout actually exists in SQLite (`WorkoutSummariesDatabaseManager.getInstance(this).hasUnfinishedWorkout()`).

Under `REQ-STB-017`, this implementation plan formalizes the exact atomic steps to isolate launch intent processing to `savedInstanceState == null`, systematically strip handled extras and synchronize `setIntent(intent)`, and fortify `chooseResume()` with defensive state guards.

---

## 2. Traceability & Requirements Mapping

* **Requirements**:
  * `REQ-STB-017.1`: Activity Recreation Intent Isolation (`MainActivityWithNavigation.onCreate()` invokes `handleIntent(intent)` if and only if `savedInstanceState == null`; `onNewIntent()` handles runtime intents).
  * `REQ-STB-017.2`: Transient Extra Consumption & Intent Synchronization (handled extras are stripped from `intent` and synced via `setIntent(intent)`).
  * `REQ-STB-017.3`: Defensive Tracking Resumption Guards in `chooseResume()` (guards against `isTracking() == true` and missing unfinished workouts).
* **Test Specifications**:
  * `TST-STB-017.1`: Cold start intent handling and extra consumption unit tests.
  * `TST-STB-017.2`: Activity recreation isolation unit tests (`savedInstanceState != null`).
  * `TST-STB-017.3`: Runtime `onNewIntent` delivery and extra consumption unit tests.
  * `TST-STB-017.4`: Defensive state guards in `chooseResume()` unit tests.
  * `TST-STB-017.5`: 9-language localization & string specifier parity audit.
  * `TST-STB-017.6`: Clean-room full regression test suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Legitimate Cold Start Notification Intent Fidelity (`REQ-STB-003.2`)**:
   - Tapping an interrupted workout notification on cold start continues to launch `MainActivityWithNavigation`, dismiss the notification, set `cResumeFromCrash = true`, and broadcast `REQUEST_START_TRACKING` to resume tracking seamlessly.
2. **Normal Cold Start Recovery Dialog Invariant (`REQ-STB-003.3`, `REQ-UI-345`)**:
   - Launching the app normally on cold start when an unfinished workout exists in SQLite continues to prompt `StartOrResumeDialog` via `onResume()` / `checkUnfinishedWorkout()`.
3. **Battery Notification Deep-Link Parity**:
   - Tapping a low-battery notification continues to open `drawer_my_sensors` and preselect the critical battery filter on initial entry.
4. **Runtime `onNewIntent` Delivery**:
   - Incoming intents delivered while the activity is alive continue to route through `onNewIntent(intent)` and execute properly.
5. **Java-to-Kotlin Companion Interop Compliance**:
   - Public constants (`EXTRA_RESUME_INTERRUPTED_WORKOUT`, `SELECTED_FRAGMENT_ID`, `SELECTED_FRAGMENT`, etc.) and enum `SelectedFragment` maintain 100% bytecode and Java caller compatibility (`MainActivityWithNavigationInteropTest`).
6. **Subtask & Parent Decision Gate Invariance**:
   - Subtasks transition directly to `Erledigt` upon passing Gate audit; the parent ticket stops at `Final Review (Human)`.
7. **Clean-Room Test Pass Rate**:
   - Full test suite pass rate under `./gradlew testDebugUnitTest` MUST remain 100%.

---

## 4. Proposed Architectural Changes

### Component 1: `MainActivityWithNavigation.kt` (`activities` Layer)

#### 1. Lifecycle Intent Isolation in `onCreate(savedInstanceState: Bundle?)`
At line 431 of `MainActivityWithNavigation.kt`:
```kotlin
// BEFORE
handleIntent(intent)

// AFTER
if (savedInstanceState == null) {
    handleIntent(intent)
}
```
*Rationale*: When `savedInstanceState != null`, the activity is being recreated due to a configuration change (rotation, dark mode, multi-window) or restored from process death. The user's screen state was already saved in `onSaveInstanceState()` and restored at lines 417–420. Re-running `handleIntent(intent)` clobbers the restored navigation state and can re-trigger one-off launch actions.

#### 2. Transient Extra Consumption & Intent Synchronization in `handleIntent(intent: Intent?)`
In `handleIntent()` (lines 306–355):
```kotlin
private fun handleIntent(intent: Intent?) {
    if (intent == null) {
        return
    }
    if (intent.getBooleanExtra(EXTRA_RESUME_INTERRUPTED_WORKOUT, false)) {
        intent.removeExtra(EXTRA_RESUME_INTERRUPTED_WORKOUT)
        intent.removeExtra(SELECTED_FRAGMENT)
        intent.removeExtra(EXTRA_DRAWER_ITEM_ID)
        mResumingFromInterruptedNotification = true
        try {
            NotificationManagerCompat.from(this).cancel(TrackerService.TRACKING_INTERRUPTED_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling tracking interrupted notification: " + e.message, e)
        }
        mSelectedFragmentId = R.id.drawer_start_tracking
        navigateToDrawerItem(mSelectedFragmentId)
        chooseResume()
    } else if (intent.hasExtra(EXTRA_DRAWER_ITEM_ID)) {
        val drawerItemId = intent.getIntExtra(EXTRA_DRAWER_ITEM_ID, -1)
        intent.removeExtra(EXTRA_DRAWER_ITEM_ID)
        if (drawerItemId != -1) {
            mSelectedFragmentId = drawerItemId
            navigateToDrawerItem(mSelectedFragmentId)
        }
    } else if (intent.hasExtra(SELECTED_FRAGMENT)) {
        val selectedName = intent.getStringExtra(SELECTED_FRAGMENT)
        intent.removeExtra(SELECTED_FRAGMENT)
        if (selectedName != null) {
            try {
                val selected = SelectedFragment.valueOf(selectedName)
                when (selected) {
                    SelectedFragment.WORKOUT_LIST -> {
                        mSelectedFragmentId = R.id.drawer_workouts
                        navigateToDrawerItem(mSelectedFragmentId)
                    }
                    SelectedFragment.START_OR_TRACKING -> {
                        mSelectedFragmentId = R.id.drawer_start_tracking
                        navigateToDrawerItem(mSelectedFragmentId)
                    }
                    SelectedFragment.SENSORS -> {
                        mSelectedFragmentId = R.id.drawer_my_sensors
                        navigateToDrawerItem(mSelectedFragmentId)
                    }
                }
            } catch (ignored: IllegalArgumentException) {
            }
        }
    }

    if (intent.getBooleanExtra(EXTRA_FILTER_CRITICAL_BATTERY, false)) {
        intent.removeExtra(EXTRA_FILTER_CRITICAL_BATTERY)
        try {
            val tabViewModel = ViewModelProvider(this)[DevicesTabbedViewModel::class.java]
            tabViewModel.setCriticalBatteryFilter()
        } catch (e: Exception) {
            Log.e(TAG, "Error applying critical battery filter from intent: " + e.message, e)
        }
    }

    setIntent(intent)
}
```
*Rationale*:
- When `TrackerService` posts the interrupted notification, it attaches both `EXTRA_RESUME_INTERRUPTED_WORKOUT` and `SELECTED_FRAGMENT`. Removing both ensures no stale extras linger.
- Removing `EXTRA_DRAWER_ITEM_ID`, `SELECTED_FRAGMENT`, and `EXTRA_FILTER_CRITICAL_BATTERY` immediately upon consumption prevents accidental re-evaluation.
- Calling `setIntent(intent)` synchronizes the sanitized intent back to the activity so any future call to `getIntent()` reflects the stripped intent.

#### 3. Defensive State Guards in `chooseResume()`
In `chooseResume()` (lines 1049–1057):
```kotlin
override fun chooseResume() {
    try {
        NotificationManagerCompat.from(this).cancel(TrackerService.TRACKING_INTERRUPTED_NOTIFICATION_ID)
    } catch (e: Exception) {
        Log.e(TAG, "Error cancelling tracking interrupted notification: " + e.message, e)
    }
    if (TrainingApplication.isTracking()) {
        Log.w(TAG, "chooseResume: already tracking, ignoring duplicate resume request")
        return
    }
    if (!WorkoutSummariesDatabaseManager.getInstance(this).hasUnfinishedWorkout()) {
        Log.w(TAG, "chooseResume: no unfinished workout found in database, ignoring resume request")
        return
    }
    TrainingApplication.setResumeFromCrash(true)
    sendBroadcast(Intent(TrainingApplication.REQUEST_START_TRACKING).setPackage(packageName))
}
```
*Rationale*: If tracking is already active, sending `REQUEST_START_TRACKING` is redundant and can cause lifecycle churn. If no unfinished workout exists in SQLite, resuming would cause `TrackerService` to resurrect an already finalized workout (`ORDER BY c_id DESC LIMIT 1`), corrupting historical records.

### Component 2: `MainActivityIntentLifecycleTest.kt` (`Unit Test` Layer)
* **Location**: `app/src/test/java/com/atrainingtracker/trainingtracker/activities/MainActivityIntentLifecycleTest.kt`
* **Test Cases**:
  1. `testColdStart_resumeInterruptedWorkout_triggersChooseResumeAndStripsExtras()` (`TST-STB-017.1`)
  2. `testColdStart_drawerItemIdExtra_updatesFragmentAndStripsExtra()` (`TST-STB-017.1`)
  3. `testColdStart_selectedFragmentExtra_updatesFragmentAndStripsExtra()` (`TST-STB-017.1`)
  4. `testColdStart_filterCriticalBatteryExtra_appliesFilterAndStripsExtra()` (`TST-STB-017.1`)
  5. `testRecreationWithSavedInstanceState_doesNotInvokeHandleIntentOrClobberScreen()` (`TST-STB-017.2`)
  6. `testOnNewIntent_processesAndConsumesExtrasWhileRunning()` (`TST-STB-017.3`)
  7. `testChooseResume_whenAlreadyTracking_doesNotBroadcastRequestStartTracking()` (`TST-STB-017.4`)
  8. `testChooseResume_whenNoUnfinishedWorkout_doesNotBroadcastRequestStartTracking()` (`TST-STB-017.4`)
  9. `testChooseResume_whenNotTrackingAndUnfinishedWorkoutExists_broadcastsRequestStartTracking()` (`TST-STB-017.4`)

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
No UI changes. Pure Android Activity lifecycle, intent routing, and defensive state verification.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Add Defensive State Guards to `MainActivityWithNavigation.chooseResume()`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt`
* **Changes**:
  - In `chooseResume()`, check `TrainingApplication.isTracking()`. If true, log warning and return.
  - Check `WorkoutSummariesDatabaseManager.getInstance(this).hasUnfinishedWorkout()`. If false, log warning and return.
  - Otherwise, set `TrainingApplication.setResumeFromCrash(true)` and dispatch `REQUEST_START_TRACKING`.

### Step 2: Implement Transient Extra Consumption and `setIntent(intent)` in `MainActivityWithNavigation.handleIntent()`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt`
* **Changes**:
  - In `handleIntent()`, systematically remove handled extras (`EXTRA_RESUME_INTERRUPTED_WORKOUT`, `SELECTED_FRAGMENT`, `EXTRA_DRAWER_ITEM_ID`, `EXTRA_FILTER_CRITICAL_BATTERY`).
  - Invoke `setIntent(intent)` to store the sanitized intent on the activity.

### Step 3: Enforce `savedInstanceState == null` Guard on `handleIntent(intent)` in `MainActivityWithNavigation.onCreate()`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt`
* **Changes**:
  - Wrap `handleIntent(intent)` in `if (savedInstanceState == null)`.

### Step 4: Author Unit Test Suite `MainActivityIntentLifecycleTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/activities/MainActivityIntentLifecycleTest.kt`
* **Changes**:
  - Implement unit tests for `TST-STB-017.1` through `TST-STB-017.4` covering cold start extra consumption, activity recreation isolation, `onNewIntent` delivery, and `chooseResume()` guards.

### Step 5: Execute Targeted Unit Tests & Living Documentation Check
* **Targeted Test Execution**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.activities.*" --tests "com.atrainingtracker.trainingtracker.ui.navigation.*"
  ```
* **Governance Verification**:
  ```bash
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Fast-loop targeted testing during Stage 4 construction across activity lifecycle and navigation tests.
  - Full clean-room test execution (`./gradlew testDebugUnitTest`) during Stage 5 verification.
  - Verification of living documentation status transitions to `Verified` in Stage 5.
* **Rollback Plan**:
  - All changes are isolated on git branch `feature/ATT-4001`.
  - In the event of an unexpected regression or gate failure, `git restore` / branch reset cleanly restores the repository to the pre-ticket state without affecting `develop` or the active sprint branch.
