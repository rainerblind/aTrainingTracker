# Stage 1 Analysis: ATT-4001 - Launch intent re-applied on activity recreation can auto-resume tracking

**Ticket**: [ATT-4001](https://rainerblind.atlassian.net/browse/ATT-4001)  
**Sub-task**: [ATT-4451](https://rainerblind.atlassian.net/browse/ATT-4451) (`[Analysis]`)  
**Parent Epic**: None  
**Target Release**: Next Sprint  
**Active Sprint**: `2026-43.3`  
**Branch**: `feature/ATT-4001`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

During an independent whole-app architecture review (`feature/ATT-3921` @ `0234bfc0`, label `code-review-2026-10`), a critical Android lifecycle defect was identified in `MainActivityWithNavigation`:

1. **Unintended Tracking Auto-Resumption After Process Death & Resurrection**:
   - When an active workout is interrupted (e.g. system termination, memory pressure, or permission interruption), `TrackerService.showTrackingInterruptedNotification()` attaches `EXTRA_RESUME_INTERRUPTED_WORKOUT = true` and `SELECTED_FRAGMENT = START_OR_TRACKING` to its `PendingIntent`.
   - When the athlete taps this notification, `MainActivityWithNavigation` launches with that intent. `handleIntent(intent)` invokes `chooseResume()`, which sets `TrainingApplication.setResumeFromCrash(true)` and broadcasts `TrainingApplication.REQUEST_START_TRACKING`. Tracking resumes as expected (`REQ-STB-003`).
   - If the athlete later completes or stops the workout and the app process is terminated in the background by the Android OS under memory pressure (or developer option *"Don't keep activities"*), Android's `ActivityManagerService` retains the base launch intent for the task.
   - When the athlete returns to the app from the Recent Tasks list, Android launches a new process and recreates `MainActivityWithNavigation`, passing the original launch intent (`intent`) and a non-null `savedInstanceState`.
   - Because `onCreate()` calls `handleIntent(intent)` unconditionally without checking `savedInstanceState == null`, `handleIntent()` executes again, finds `EXTRA_RESUME_INTERRUPTED_WORKOUT == true`, calls `chooseResume()`, and dispatches `REQUEST_START_TRACKING`.
   - `TrainingApplication.startTracking()` launches `TrackerService` with `RESUME_BY_USER`, which queries SQLite for the most recent workout (`ORDER BY c_id DESC LIMIT 1` without filtering `FINISHED == 0`) and resumes tracking without athlete knowledge or consent.

2. **Screen Navigation Reset & Filter Wipeout on Activity Configuration Change**:
   - When launched from a deep link or notification (such as a sensor low-battery notification with `EXTRA_DRAWER_ITEM_ID = R.id.drawer_my_sensors` and `EXTRA_FILTER_CRITICAL_BATTERY = true`, or a tracking notification with `SELECTED_FRAGMENT = START_OR_TRACKING`), the athlete frequently navigates to another hub (e.g. `drawer_workouts` or Settings).
   - When a configuration change occurs (dark mode toggle, screen rotation, multi-window resize, or locale change), `MainActivityWithNavigation` is recreated with `savedInstanceState != null`.
   - `savedInstanceState` correctly restores `mSelectedFragmentId` to the active screen (e.g. `R.id.drawer_workouts`).
   - However, `onCreate()` immediately calls `handleIntent(intent)`. Because `EXTRA_DRAWER_ITEM_ID`, `SELECTED_FRAGMENT`, and `EXTRA_FILTER_CRITICAL_BATTERY` were never removed from the intent (and even for `EXTRA_RESUME_INTERRUPTED_WORKOUT`, its companion `SELECTED_FRAGMENT` remained unremoved), `handleIntent()` re-processes the stale extras and overrides `mSelectedFragmentId` back to the original notification target (e.g. `drawer_start_tracking` or `drawer_my_sensors`).
   - In addition, calling `navigateToDrawerItem(R.id.drawer_start_tracking)` triggers `MyPreferenceManager.clearWorkoutFilterCriteria()`, `clearRouteFilterCriteria()`, and `clearClusterFilterCriteria()`, wiping out active filters.

**Expected Behavior**:
- Launch intent extras must only be processed on initial task launch (`savedInstanceState == null`) or when a fresh intent arrives via `onNewIntent(intent)`.
- On configuration change or process death resurrection (`savedInstanceState != null`), the UI state restored from `savedInstanceState` must be respected, and launch intent extras must not be re-executed.
- Handled intent extras must be consumed/removed from the intent and synchronized via `setIntent(intent)`.
- As a defensive guard, `chooseResume()` must verify that tracking is not already active and that an unfinished workout (`FINISHED == 0` or `FINISHED IS NULL`) actually exists in SQLite before broadcasting `REQUEST_START_TRACKING`.

---

## 2. Root Cause Analysis (Forensic Investigation)

A forensic investigation of `MainActivityWithNavigation.kt`, `TrainingApplication.java`, and `TrackerService.java` confirms the reviewer's findings:

1. **Unconditional `handleIntent(intent)` in `MainActivityWithNavigation.onCreate`**:
   - `MainActivityWithNavigation.kt` lines 417–432:
     ```kotlin
     if (savedInstanceState != null) {
         mSelectedFragmentId = savedInstanceState.getInt(SELECTED_FRAGMENT_ID, DEFAULT_SELECTED_FRAGMENT_ID)
         mDrawerController.selectedItemId = mSelectedFragmentId
     }

     setContent {
         ATrainingTrackerTheme {
             ATrainingTrackerApp(
                 activity = this,
                 drawerController = mDrawerController
             )
         }
     }

     handleIntent(intent) // Unconditional execution!
     ```
   - When `savedInstanceState != null`, the activity is being recreated. The restored `mSelectedFragmentId` is clobbered by `handleIntent(intent)`.

2. **Unconsumed Extras & Asymmetric Removal in `handleIntent`**:
   - `MainActivityWithNavigation.kt` lines 306–355:
     ```kotlin
     if (intent.getBooleanExtra(EXTRA_RESUME_INTERRUPTED_WORKOUT, false)) {
         intent.removeExtra(EXTRA_RESUME_INTERRUPTED_WORKOUT)
         mResumingFromInterruptedNotification = true
         ...
         chooseResume()
     } else if (intent.hasExtra(EXTRA_DRAWER_ITEM_ID)) {
         val drawerItemId = intent.getIntExtra(EXTRA_DRAWER_ITEM_ID, -1)
         if (drawerItemId != -1) {
             mSelectedFragmentId = drawerItemId
             navigateToDrawerItem(mSelectedFragmentId)
         }
     } else if (intent.hasExtra(SELECTED_FRAGMENT)) {
         ...
     }
     ```
   - In `TrackerService.java:1092-1093`, the interrupted workout notification attaches both:
     ```java
     resumeIntent.putExtra(MainActivityWithNavigation.SELECTED_FRAGMENT, MainActivityWithNavigation.SelectedFragment.START_OR_TRACKING.name());
     resumeIntent.putExtra(MainActivityWithNavigation.EXTRA_RESUME_INTERRUPTED_WORKOUT, true);
     ```
   - On initial launch, `EXTRA_RESUME_INTERRUPTED_WORKOUT` is removed from the in-memory `intent`, but `SELECTED_FRAGMENT` is **NOT** removed.
   - On a simple in-process configuration change, `EXTRA_RESUME_INTERRUPTED_WORKOUT` evaluates to false, but `intent.hasExtra(SELECTED_FRAGMENT)` evaluates to **true**! It jumps into the `else if (intent.hasExtra(SELECTED_FRAGMENT))` branch, setting `mSelectedFragmentId = R.id.drawer_start_tracking` and calling `navigateToDrawerItem(...)`.
   - `EXTRA_DRAWER_ITEM_ID`, `SELECTED_FRAGMENT`, and `EXTRA_FILTER_CRITICAL_BATTERY` are never removed anywhere.
   - `setIntent(intent)` is never called in `handleIntent()`.

3. **Absence of Defensive State Guards in `chooseResume()`**:
   - `MainActivityWithNavigation.kt` lines 1049–1057:
     ```kotlin
     override fun chooseResume() {
         try {
             NotificationManagerCompat.from(this).cancel(TrackerService.TRACKING_INTERRUPTED_NOTIFICATION_ID)
         } catch (e: Exception) {
             Log.e(TAG, "Error cancelling tracking interrupted notification: " + e.message, e)
         }
         TrainingApplication.setResumeFromCrash(true)
         sendBroadcast(Intent(TrainingApplication.REQUEST_START_TRACKING).setPackage(packageName))
     }
     ```
   - `chooseResume()` does not check:
     - `TrainingApplication.isTracking()` (whether tracking is already active)
     - `WorkoutSummariesDatabaseManager.getInstance(this).hasUnfinishedWorkout()` (whether an unfinalized workout exists)
   - When combined with `TrackerService.recreateValuesWhenResuming()` (lines 1303–1310), which queries the most recent workout `ORDER BY c_id DESC LIMIT 1` without filtering for `FINISHED == 0`, re-triggering `chooseResume()` can reopen and resume a previously finished workout.

**Reproduction Conclusion**:
The reported finding is **CONFIRMED 100%**.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Guard `handleIntent(intent)` in `MainActivityWithNavigation.onCreate()` to only run when `savedInstanceState == null`.
  * In `handleIntent(intent)`, consume and remove all handled extras:
    - `EXTRA_RESUME_INTERRUPTED_WORKOUT`
    - `SELECTED_FRAGMENT`
    - `EXTRA_DRAWER_ITEM_ID`
    - `EXTRA_FILTER_CRITICAL_BATTERY`
  * Synchronize the stripped intent via `setIntent(intent)`.
  * Add defensive guards in `chooseResume()` to verify `!TrainingApplication.isTracking()` and `WorkoutSummariesDatabaseManager.getInstance(this).hasUnfinishedWorkout()`.
  * Author comprehensive unit test coverage verifying cold start intent dispatch, configuration change recreation preservation, process-death restoration immunity, and `chooseResume()` guards.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do NOT refactor Compose NavHost routing or top-level navigation controllers (`ATT-657`).
  * Do NOT alter `TrackerService` notification generation contracts or pending intent flags (`REQ-STB-003`).
  * Do NOT modify `StartOrResumeDialog` or `ControlTrackingViewModel` recovery logic (`REQ-UI-345`).
  * Do NOT modify background service binding lifecycle or coroutine scopes (`ATT-4020`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:
  - `REQ-STB-003`: *Interrupted Workout Resumption & Unfinished Workout Recovery*
  - `REQ-UI-124`: *MainActivityWithNavigation Kotlin Migration & Navigation Hubs*
  - `REQ-UI-345`: *Workout Recovery Dialog Layout Visibility, Start Interception Guard & Sticky Tracking State Synchronization*

* **Historical Origin & Commit Trace**:
  - Commit `0668dda6` (`ATT-657` / `ATT-658`): Kotlin migration of `MainActivityWithNavigation`. The legacy Java implementation called `handleIntent(getIntent())` unconditionally in `onCreate()`. During the Kotlin migration, this unconditional invocation was preserved without guarding against activity recreation.
  - Commit `fe4e24f1` (`ATT-3165` / `REQ-UI-345`): Introduced robust unfinalized workout detection (`hasUnfinishedWorkout()`) to prevent silent data loss and ensure dialog visibility.

* **Root Reason for Existing Formulation**:
  - `handleIntent` was originally designed to process intents dispatched when the user taps notifications while the activity is not running. In single-instance/singleTask activities, Android delivers new intents to `onNewIntent()`, while initial launch intents are delivered to `onCreate()`. Calling `handleIntent` in `onCreate` was intended only for the initial start, but omitting the `savedInstanceState == null` check caused it to re-execute on every configuration change and process recreation.
  - Intent extras were treated as immutable read-only parameters rather than one-off transient event payloads, leaving them attached to the activity across subsequent lifecycle callbacks.

* **Preservation of Core Invariants**:
  - Tapping an interrupted workout notification on cold start continues to immediately resume tracking (`REQ-STB-003.2`).
  - Tapping a low-battery notification continues to open Sensors and preselect the critical battery filter.
  - Delivering new intents while the activity is alive continues to route through `onNewIntent(intent)` and execute properly.
  - When an unfinished workout exists in SQLite, launching normally continues to prompt `StartOrResumeDialog` via `onResume()` / `checkUnfinishedWorkout()` (`REQ-STB-003.3`).

---

## 5. Architectural Strategy & High-Level Solution

### Component Changes

1. **`MainActivityWithNavigation.kt` (`onCreate`)**:
   Wrap `handleIntent(intent)` in `if (savedInstanceState == null)`:
   ```kotlin
   if (savedInstanceState == null) {
       handleIntent(intent)
   }
   ```

2. **`MainActivityWithNavigation.kt` (`handleIntent`)**:
   Consume all handled extras upon processing and synchronize via `setIntent(intent)`:
   - When handling `EXTRA_RESUME_INTERRUPTED_WORKOUT`:
     - Remove `EXTRA_RESUME_INTERRUPTED_WORKOUT`
     - Remove `SELECTED_FRAGMENT`
     - Remove `EXTRA_DRAWER_ITEM_ID`
   - When handling `EXTRA_DRAWER_ITEM_ID`:
     - Remove `EXTRA_DRAWER_ITEM_ID`
   - When handling `SELECTED_FRAGMENT`:
     - Remove `SELECTED_FRAGMENT`
   - When handling `EXTRA_FILTER_CRITICAL_BATTERY`:
     - Remove `EXTRA_FILTER_CRITICAL_BATTERY`
   - Call `setIntent(intent)` at the conclusion of `handleIntent`.

3. **`MainActivityWithNavigation.kt` (`chooseResume`)**:
   Add defense-in-depth safety checks:
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

4. **Testing Strategy**:
   Author unit test suite `MainActivityIntentLifecycleTest.kt`:
   - `onCreate_coldStartWithResumeInterruptedWorkout_invokesChooseResumeAndRemovesExtras`: verifies initial launch consumes extras and initiates resume.
   - `onCreate_recreationWithSavedInstanceState_ignoresStaleIntentExtras`: verifies that when `savedInstanceState != null`, restored screen is preserved and intent extras are not re-processed.
   - `onNewIntent_whileRunning_processesNewIntentAndRemovesExtras`: verifies `onNewIntent` updates navigation and strips extras.
   - `chooseResume_whenAlreadyTracking_doesNotBroadcastRequestStartTracking`: verifies guard when tracking is active.
   - `chooseResume_whenNoUnfinishedWorkout_doesNotBroadcastRequestStartTracking`: verifies guard when database has no unfinished workout.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Cold start from notifications (interrupted tracking, low battery, export completion) must navigate to the expected screen and trigger the expected action.
  2. Normal cold start with unfinished workout in SQLite must prompt `StartOrResumeDialog` (`REQ-STB-003.3`, `REQ-UI-345`).
  3. Activity recreation (screen rotation, dark mode toggle) must preserve the user's active screen and filters.
  4. Java-to-Kotlin interop contracts (`MainActivityWithNavigationInteropTest`) must remain 100% compliant.
  5. Clean-room unit test suite pass rate must remain 100%.

* **Risk Rating**: **LOW**
  - **Justification**: The modification is purely architectural lifecycle sanitization within `MainActivityWithNavigation.kt`. It enforces canonical Android framework lifecycle contracts (`savedInstanceState == null` check for launch intent processing, intent extra consumption, and defensive state guarding in `chooseResume()`). Blast radius is strictly confined to activity intent routing.
