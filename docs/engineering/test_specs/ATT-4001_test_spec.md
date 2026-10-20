# Stage 2: Requirement & Test Specification - ATT-4001: Launch intent re-applied on activity recreation can auto-resume tracking

**Ticket**: [ATT-4001](https://rainerblind.atlassian.net/browse/ATT-4001)  
**Sub-task**: [ATT-4453](https://rainerblind.atlassian.net/browse/ATT-4453) (`[Req & Test Spec]`)  
**Parent Epic**: None  
**Target Release**: Next Sprint  
**Active Sprint**: `2026-43.3`  
**Requirement Mapping**: `REQ-STB-017` (*MainActivity Launch Intent Lifecycle Sanitization, Transient Extra Consumption & Safe Resumption Guards*)  
**Test Spec ID**: `TST-STB-017`  
**Branch**: `feature/ATT-4001`  
**Author**: AI Agent 1 (`subagent_flash`)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification (REQ-STB-017)

### 1.1 Problem Statement & Rationale
During an independent whole-app architecture review (`feature/ATT-3921` @ `0234bfc0`, label `code-review-2026-10`), a critical lifecycle and intent-routing vulnerability was discovered in `MainActivityWithNavigation`:
1. `onCreate()` calls `handleIntent(intent)` unconditionally, ignoring whether `savedInstanceState != null`. On activity recreation (configuration changes like dark mode toggle, screen rotation, or multi-window resize), stale extras stored in the activity's base launch intent override the user's navigated screen restored by `savedInstanceState`.
2. Handled intent extras (`EXTRA_RESUME_INTERRUPTED_WORKOUT`, `SELECTED_FRAGMENT`, `EXTRA_DRAWER_ITEM_ID`, `EXTRA_FILTER_CRITICAL_BATTERY`) are not systematically stripped from the intent, nor is `setIntent(intent)` synchronized. In particular, `TrackerService.showTrackingInterruptedNotification()` attaches both `EXTRA_RESUME_INTERRUPTED_WORKOUT` and `SELECTED_FRAGMENT = START_OR_TRACKING`. Even when `EXTRA_RESUME_INTERRUPTED_WORKOUT` was removed from the in-memory intent, `SELECTED_FRAGMENT` remained unconsumed, causing subsequent recreations to reset the active screen to `drawer_start_tracking`.
3. When the application process is terminated in the background by the Android OS (memory pressure, LMK, or "Don't keep activities") and resurrected when the user returns from Recent Tasks, Android's `ActivityManagerService` redelivers the original launch intent with `EXTRA_RESUME_INTERRUPTED_WORKOUT = true`. Because `onCreate()` runs `handleIntent(intent)` unconditionally, it calls `chooseResume()`.
4. `chooseResume()` lacked defensive state guards, failing to check `!TrainingApplication.isTracking()` and `WorkoutSummariesDatabaseManager.getInstance(this).hasUnfinishedWorkout()`. As a result, process death resurrection can auto-resume tracking without user confirmation and reopen previously completed workouts.

### 1.2 Functional & Architectural Requirements
The system SHALL satisfy the following formal requirements under `REQ-STB-017`:
1. **Activity Recreation Intent Isolation (`MainActivityWithNavigation.kt`)**:
   - In `MainActivityWithNavigation.onCreate()`, `handleIntent(intent)` SHALL be invoked if and only if `savedInstanceState == null`.
   - On activity recreation (`savedInstanceState != null`), `savedInstanceState` SHALL retain full authority over active screen restoration (`mSelectedFragmentId`), and stale launch intent extras from `ActivityManagerService` SHALL NOT be re-evaluated.
   - Intent delivery while the activity is alive SHALL route through `onNewIntent(intent)`, which SHALL call `setIntent(intent)` and invoke `handleIntent(intent)`.
2. **Transient Extra Consumption & Intent Synchronization (`MainActivityWithNavigation.kt`)**:
   - In `handleIntent(intent)`, once intent extras are processed, they SHALL be immediately consumed and stripped from the active intent:
     - Handling `EXTRA_RESUME_INTERRUPTED_WORKOUT` SHALL remove `EXTRA_RESUME_INTERRUPTED_WORKOUT`, `SELECTED_FRAGMENT`, and `EXTRA_DRAWER_ITEM_ID`.
     - Handling `EXTRA_DRAWER_ITEM_ID` SHALL remove `EXTRA_DRAWER_ITEM_ID`.
     - Handling `SELECTED_FRAGMENT` SHALL remove `SELECTED_FRAGMENT`.
     - Handling `EXTRA_FILTER_CRITICAL_BATTERY` SHALL remove `EXTRA_FILTER_CRITICAL_BATTERY`.
   - At the conclusion of `handleIntent(intent)`, `MainActivityWithNavigation` SHALL invoke `setIntent(intent)` to ensure the sanitized intent state is synchronized with the activity.
3. **Defensive Tracking Resumption Guards (`MainActivityWithNavigation.kt`)**:
   - In `MainActivityWithNavigation.chooseResume()`, before setting `TrainingApplication.setResumeFromCrash(true)` or broadcasting `TrainingApplication.REQUEST_START_TRACKING`:
     - The system SHALL verify `!TrainingApplication.isTracking()`. If tracking is already active, `chooseResume()` SHALL log a warning and return immediately without broadcasting `REQUEST_START_TRACKING`.
     - The system SHALL verify `WorkoutSummariesDatabaseManager.getInstance(this).hasUnfinishedWorkout()`. If no unfinalized workout exists in SQLite, `chooseResume()` SHALL log a warning and return immediately without broadcasting `REQUEST_START_TRACKING`.

### 1.3. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-STB-017` under Stability Epic `ATT-235`, amending `REQ-STB-003` (*Interrupted Workout Resumption & Unfinished Workout Recovery*), `REQ-UI-124` (*MainActivity Kotlin Migration*), and `REQ-UI-345` (*Workout Recovery Dialog & Start Interception Guard*). Targets `MainActivityWithNavigation.kt`.
* **Historical Origin & Commit Trace**: Independent whole-app code review (branch `feature/ATT-3921` @ `0234bfc0`). Legacy Java implementation called `handleIntent(getIntent())` unconditionally in `onCreate()`; preserved during Kotlin migration in commit `0668dda6` (`ATT-657`/`ATT-658`). `hasUnfinishedWorkout()` was introduced in `fe4e24f1` (`ATT-3165` / `REQ-UI-345`).
* **Root Reason for Existing Formulation**: Historical `handleIntent` assumed launch intents represented immediate one-time user actions without considering Android framework task intent persistence across activity recreations (`savedInstanceState != null`) and process resurrecting from Recent Tasks. Extras were treated as read-only rather than consumed event payloads.
* **Preservation of Core Invariants**: Legitimate cold start from interrupted workout notifications (`REQ-STB-003.2`), low-battery notifications, dynamic drawer deep-links, `StartOrResumeDialog` prompting on normal cold starts (`REQ-STB-003.3`), and 100% clean-room test pass rate remain strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Cold Start Interrupted Notification Resumption)**:
  * *Given* an interrupted tracking notification tapped on cold start (`savedInstanceState == null`),
  * *When* `onCreate()` executes,
  * *Then* `handleIntent()` consumes the extras, cancels the notification, navigates to `drawer_start_tracking`, and initiates resume.
* **Criterion 2 (Configuration Change & Recreation Screen Preservation)**:
  * *Given* the app was opened from a notification/deep link and the user navigated to another drawer screen (e.g. `drawer_workouts`),
  * *When* the activity is recreated due to configuration change (dark mode toggle, rotation, resize) with `savedInstanceState != null`,
  * *Then* `handleIntent()` is bypassed, the restored screen (`drawer_workouts`) is retained, and active filter criteria are preserved.
* **Criterion 3 (Process Death Resurrection Immunity)**:
  * *Given* the app was opened with `EXTRA_RESUME_INTERRUPTED_WORKOUT` and the process is killed in the background and resurrected via Recent Tasks,
  * *When* `onCreate()` executes with `savedInstanceState != null`,
  * *Then* stale launch extras are ignored and tracking is NOT restarted without user confirmation.
* **Criterion 4 (Defensive Already-Tracking Guard in `chooseResume()`)**:
  * *Given* tracking is already active (`TrainingApplication.isTracking() == true`),
  * *When* `chooseResume()` is invoked,
  * *Then* `chooseResume()` logs a warning and does NOT broadcast `REQUEST_START_TRACKING`.
* **Criterion 5 (Defensive Missing-Unfinished-Workout Guard in `chooseResume()`)**:
  * *Given* no unfinished workout exists in SQLite (`hasUnfinishedWorkout() == false`),
  * *When* `chooseResume()` is invoked,
  * *Then* `chooseResume()` logs a warning and does NOT broadcast `REQUEST_START_TRACKING`.
* **Criterion 6 (Live Runtime `onNewIntent` Delivery)**:
  * *Given* `MainActivityWithNavigation` is currently running,
  * *When* a new intent is delivered via `onNewIntent(intent)`,
  * *Then* `setIntent(intent)` is called and `handleIntent(intent)` processes and consumes the new extras.

### 1.5 System Invariants
1. **Normal Workout Start & Recovery Invariant**: Normal cold starts with an unfinished workout in SQLite continue to trigger `StartOrResumeDialog` via `checkUnfinishedWorkout()` (`REQ-STB-003.3`, `REQ-UI-345`).
2. **Notification Intent Fidelity**: Tapping an interrupted workout notification on cold start continues to immediately resume tracking without manual prompts (`REQ-STB-003.2`).
3. **Direct Platform & NavHost Decoupling**: Bounded strictly to `MainActivityWithNavigation.kt` intent handling; zero modifications to Compose `NavHost` routing or background service architectures.
4. **Clean-Room Test Pass Rate**: Full test suite pass rate under `./gradlew testDebugUnitTest` MUST remain 100%.

---

## 2. Test Specification (TST-STB-017)

### Test Case 1: Cold Start Intent Handling & Extra Consumption (`TST-STB-017.1`)
* **Scope**: Unit Test (`MainActivityIntentLifecycleTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/activities/MainActivityIntentLifecycleTest.kt`
* **Preconditions**: Activity created with `savedInstanceState == null`.
* **Action & Assertions**:
  - Provide intent with `EXTRA_RESUME_INTERRUPTED_WORKOUT = true`, `SELECTED_FRAGMENT = START_OR_TRACKING`, and `EXTRA_DRAWER_ITEM_ID = R.id.drawer_start_tracking`.
  - Invoke `handleIntent(intent)`.
  - Assert `chooseResume()` is triggered.
  - Assert `EXTRA_RESUME_INTERRUPTED_WORKOUT`, `SELECTED_FRAGMENT`, and `EXTRA_DRAWER_ITEM_ID` are removed from `intent`.
  - Repeat for `EXTRA_DRAWER_ITEM_ID` standalone: assert `mSelectedFragmentId` updated and extra removed.
  - Repeat for `SELECTED_FRAGMENT` standalone: assert `mSelectedFragmentId` updated and extra removed.
  - Repeat for `EXTRA_FILTER_CRITICAL_BATTERY`: assert filter applied and extra removed.

### Test Case 2: Activity Recreation Isolation (`TST-STB-017.2`)
* **Scope**: Unit Test (`MainActivityIntentLifecycleTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/activities/MainActivityIntentLifecycleTest.kt`
* **Preconditions**: Activity recreation scenario where `savedInstanceState != null` (bundle contains `SELECTED_FRAGMENT_ID = R.id.drawer_workouts`), while `intent` contains stale `EXTRA_RESUME_INTERRUPTED_WORKOUT = true` or `EXTRA_DRAWER_ITEM_ID = R.id.drawer_start_tracking`.
* **Action**: Simulate `onCreate(savedInstanceState)`.
* **Expected Result**:
  - `handleIntent(intent)` is NOT invoked.
  - `mSelectedFragmentId` remains `R.id.drawer_workouts`.
  - `chooseResume()` is NOT called.

### Test Case 3: Runtime New Intent Handling (`TST-STB-017.3`)
* **Scope**: Unit Test (`MainActivityIntentLifecycleTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/activities/MainActivityIntentLifecycleTest.kt`
* **Preconditions**: Activity is active (`savedInstanceState` already evaluated).
* **Action**: Invoke `onNewIntent(newIntent)` with `EXTRA_DRAWER_ITEM_ID = R.id.drawer_my_sensors`.
* **Expected Result**:
  - `setIntent(newIntent)` is called.
  - `handleIntent(newIntent)` processes the intent.
  - `mSelectedFragmentId` updates to `R.id.drawer_my_sensors`.
  - `EXTRA_DRAWER_ITEM_ID` is removed from `newIntent`.

### Test Case 4: Defensive State Guards in `chooseResume()` (`TST-STB-017.4`)
* **Scope**: Unit Test (`MainActivityIntentLifecycleTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/activities/MainActivityIntentLifecycleTest.kt`
* **Preconditions**: Mock `TrainingApplication` and `WorkoutSummariesDatabaseManager`.
* **Action & Assertions**:
  - Case A (Already Tracking): Set `TrainingApplication.isTracking() == true`. Call `chooseResume()`. Assert `sendBroadcast(REQUEST_START_TRACKING)` is NOT invoked.
  - Case B (No Unfinished Workout): Set `TrainingApplication.isTracking() == false` and `hasUnfinishedWorkout() == false`. Call `chooseResume()`. Assert `sendBroadcast(REQUEST_START_TRACKING)` is NOT invoked.
  - Case C (Valid Resume): Set `TrainingApplication.isTracking() == false` and `hasUnfinishedWorkout() == true`. Call `chooseResume()`. Assert `setResumeFromCrash(true)` is set and `sendBroadcast(REQUEST_START_TRACKING)` is broadcast.

### Test Case 5: 9-Language Localization & Specifier Audit (`TST-STB-017.5`)
* **Scope**: Localization Parity Test
* **Goal**: Confirm that no user-facing strings are modified or added for this lifecycle fix, and verify 100% translation parity and format specifier consistency across all 9 canonical locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) via `TranslationParityTest`.
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 6: Clean-Room Regression Suite (`TST-STB-017.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.
* **Expected Result**: 0 failures, 0 errors across all test classes.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-STB-017.1` | Unit | `MainActivityWithNavigation.handleIntent` | `REQ-STB-017.2`, `REQ-STB-003.2` | Specified |
| `TST-STB-017.2` | Unit | `MainActivityWithNavigation.onCreate` | `REQ-STB-017.1`, `REQ-UI-124` | Specified |
| `TST-STB-017.3` | Unit | `MainActivityWithNavigation.onNewIntent` | `REQ-STB-017.1` | Specified |
| `TST-STB-017.4` | Unit | `MainActivityWithNavigation.chooseResume` | `REQ-STB-017.3`, `REQ-UI-345` | Specified |
| `TST-STB-017.5` | Localization | `TranslationParityTest` | `REQ-UI-106`, `REQ-LOC-001` | Specified |
| `TST-STB-017.6` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
