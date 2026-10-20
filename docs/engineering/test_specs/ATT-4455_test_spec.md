# Stage 2: Requirement & Test Specification - ATT-4455: [Feature] Add abortable first-install permission onboarding flow on initial app launch

**Ticket**: [ATT-4455](https://rainerblind.atlassian.net/browse/ATT-4455)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-2617](https://rainerblind.atlassian.net/browse/ATT-2617) (*Optimize first impression*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Requirement Mapping**: `REQ-PRI-007` (*Abortable First-Install Permission Onboarding Flow and Upgrade Auto-Skip Architecture*)  
**Test Spec ID**: `TST-PRI-006`  
**Branch**: `feature/ATT-4455`  
**Author**: AI Agent 1 (Implementer - subagent_flash)  
**Date**: 2026-10-21  

---

## 1. Requirement Specification (`REQ-PRI-007`)

### 1.1 Problem Statement & Rationale
In **aTrainingTracker**, tracking workouts with GPS tracks, BLE sensor telemetry, and background survival requires several Android runtime permissions and system exemptions:
- Location (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`) and Background Location (`ACCESS_BACKGROUND_LOCATION`) for continuous GPS track recording with the screen locked.
- Nearby Devices / Bluetooth (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` on Android 12+) for pairing and streaming HRM, cycling power, and cadence sensors.
- Notifications (`POST_NOTIFICATIONS` on Android 13+) for active foreground service status, lap splits, and battery alerts.
- Battery Optimization Exemption (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) for preventing Android Doze process kills.

Currently, permissions are requested purely Just-in-Time (JIT) when the athlete expresses tracking intent (tapping Start on `ControlTrackingScreen` or pairing on `DevicesTabbedScreen`). On a fresh clean install, tapping Start triggers an overwhelming 4-step permission rationale barrage (Foreground Location -> Notifications -> Background Location in OS Settings -> Battery Optimization dialog) right when the athlete wants to begin moving.

`REQ-PRI-007` resolves this friction by introducing an upfront, educational, and completely abortable/skippable **First-Install Permission Onboarding Flow** on initial launch. This flow walks the athlete through setup categories with athletic explanations, allows skipping any step or aborting setup entirely, persists completion in `TuningPreferencesDataStore`, automatically auto-skips for upgraded installs where permissions are already held, and strictly retains existing JIT checks as safety nets.

---

### 1.2 Functional & Architectural Requirements

#### REQ-PRI-007.1: First-Launch Detection & Upgraded Install Auto-Skip (`ATrainingTrackerApp.kt`, `TuningPreferencesDataStore.kt`)
1. `TuningPreferencesDataStore` SHALL define `KEY_HAS_COMPLETED_PERMISSION_ONBOARDING: Preferences.Key<Boolean>`, defaulting to `false` (`DEFAULT_HAS_COMPLETED_PERMISSION_ONBOARDING`), with reactive flow `hasCompletedPermissionOnboardingFlow: Flow<Boolean>` and persistence mutator `suspend fun setHasCompletedPermissionOnboarding(completed: Boolean)`.
2. In `ATrainingTrackerApp.kt`, `hasCompletedPermissionOnboardingFlow` SHALL be collected safely with initial value `true` to avoid transient UI flashes while DataStore initializes.
3. Startup helper `checkAreAllPermissionsGranted(context)` SHALL evaluate:
   - `ACCESS_FINE_LOCATION` granted;
   - On Android 12+ (API 31+), `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` granted;
   - On Android 13+ (API 33+), `POST_NOTIFICATIONS` granted;
   - `PowerManager.isIgnoringBatteryOptimizations` granted.
4. If `hasCompletedPermissionOnboardingFlow` emits `false` and `checkAreAllPermissionsGranted` evaluates to `true` (upgraded install or backup restore), the system SHALL immediately invoke `setHasCompletedPermissionOnboarding(true)` without presenting the onboarding UI.
5. If `hasCompletedPermissionOnboardingFlow` emits `false` and permissions are missing, the system SHALL present the `PermissionOnboardingSheet` overlay.

#### REQ-PRI-007.2: Step-by-Step Educational Onboarding Wizard (`PermissionOnboardingSheet.kt`)
1. `PermissionOnboardingSheet.kt` SHALL be implemented as a modular Material 3 `ModalBottomSheet` strictly `< 400` lines.
2. The wizard SHALL guide the athlete across 4 discrete setup categories:
   - **Step 1** (`OnboardingStep.LOCATION`): Location and background GPS tracking (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`).
   - **Step 2** (`OnboardingStep.BLUETOOTH`): Nearby Bluetooth sensors (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` on Android 12+; cleanly auto-advanced or omitted on API < 31).
   - **Step 3** (`OnboardingStep.NOTIFICATION`): Workout foreground service status, lap splits, and battery alerts (`POST_NOTIFICATIONS` on Android 13+; cleanly auto-advanced or omitted on API < 33).
   - **Step 4** (`OnboardingStep.BATTERY_OPTIMIZATION`): Doze mode background survival exemption (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
3. The header SHALL render a step progress indicator ("Step %1$d of %2$d" / `permission_onboarding_step_format`) and an abort action button ("Skip Setup" / `permission_onboarding_skip_setup`).

#### REQ-PRI-007.3: Total Abortability & Non-Blocking Navigation
1. The athlete SHALL be able to skip any individual step via a secondary "Skip this step" action (`permission_onboarding_skip_step`), advancing immediately to the subsequent step without requesting or granting the permission.
2. Tapping "Skip Setup" (`permission_onboarding_skip_setup`) or dismissing the bottom sheet scrim at any point SHALL dismiss the flow immediately and persist `setHasCompletedPermissionOnboarding(true)`, allowing immediate access to the app cockpit without modal locks or unskippable walls.

#### REQ-PRI-007.4: Preservation of Just-in-Time Fallback Invariants
1. All existing Just-in-Time permission checks in `ControlTrackingScreen.kt` (`handleStartClick`, `proceedAfterPermissions`) and `DevicesTabbedScreen.kt` (`checkHasBluetoothScan`) SHALL remain completely intact.
2. If an athlete skips any permission during onboarding, the corresponding contextual JIT rationale sheet SHALL still be presented when they subsequently trigger an action requiring that capability (e.g. tapping Start or scanning for sensors).

#### REQ-PRI-007.5: 100% 9-Language Localization Parity
1. All user-facing onboarding strings (`permission_onboarding_title`, `permission_onboarding_step_format`, `permission_onboarding_skip_setup`, `permission_onboarding_skip_step`, `permission_onboarding_grant`, `permission_onboarding_continue`, `permission_onboarding_finish`, `permission_onboarding_location_title`, `permission_onboarding_location_desc`, `permission_onboarding_bluetooth_title`, `permission_onboarding_bluetooth_desc`, `permission_onboarding_notification_title`, `permission_onboarding_notification_desc`, `permission_onboarding_battery_title`, `permission_onboarding_battery_desc`) SHALL exist across all 9 canonical locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with indexed positional format specifiers (`%1$d`, `%2$d`) and zero literal `"+ "` prefixes.

---

### Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**:
   - Refines and amends `REQ-PRI-003` (*Contextual Just-in-Time Permission Flow, Zero-Friction Cold Start, Background Location Escalation, Battery Optimization Exemption & Graceful Settings Return Handling*) and `REQ-PRI-006` (*Decoupled Multi-Permission Cascade for Workout Start*).
   - Targets `ATrainingTrackerApp.kt`, `TuningPreferencesDataStore.kt`, `PermissionOnboardingSheet.kt`, `ControlTrackingScreen.kt`, and `DevicesTabbedScreen.kt`.
2. **Historical Origin & Commit Trace**:
   - `ATT-2075` (Sprint 2026-40.16) retired eager cold-start dialogs in `MainActivityWithNavigation` in favor of JIT rationale sheets.
   - `ATT-2621` (Sprint 2026-41.2) decoupled battery optimization warnings.
   - `ATT-3998` (Sprint 2026-43.3) decoupled the workout start cascade for notifications and Bluetooth.
   - Usability friction identified during clean-install verification of `ATT-3387` where first-time athletes experienced 4 consecutive educational rationale sheets and 3 system dialogs upon first tapping Start.
3. **Root Reason for Existing Formulation**:
   - Pure JIT moved 100% of permission requests to user actions (tapping Start or searching sensors), which successfully prevented cold-start modal popups for returning athletes, but concentrated the cumulative setup burden at the moment the athlete was ready to start recording.
4. **Preservation of Core Invariants**:
   - Returning athletes experience zero cold-start popups (`REQ-PRI-003`).
   - Strict fine location requirement (`REQ-PRI-004`).
   - Android 11+ two-step background location cascade.
   - Fail-safe platform intent handling (`REQ-PRI-005`).
   - JIT fallback safety preserved for skipped permissions (`REQ-PRI-006`).
   - Non-blocking app exploration (total abortability).
   - 100% full test suite pass rate.

---

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Fresh Clean Install Trigger)**:
  * *Given* a clean app install where `hasCompletedPermissionOnboardingFlow` is `false` and permissions are missing,
  * *When* the app is launched on cold start (`savedInstanceState == null`),
  * *Then* `PermissionOnboardingSheet` is presented, displaying Step 1 (Location & GPS Tracking) with a "Skip Setup" button.

* **Criterion 2 (Skip Setup / Scrim Abort)**:
  * *Given* an active `PermissionOnboardingSheet`,
  * *When* the user taps "Skip Setup" or dismisses the bottom sheet,
  * *Then* the sheet closes, `setHasCompletedPermissionOnboarding(true)` is persisted, and the main cockpit is accessible immediately without granting permissions.

* **Criterion 3 (Per-Step Skipping)**:
  * *Given* an active `PermissionOnboardingSheet` on Step 1,
  * *When* the user taps "Skip this step",
  * *Then* the wizard advances to Step 2 without requesting location permission.

* **Criterion 4 (Upgraded Install Auto-Skip)**:
  * *Given* an upgraded install or backup restore where all essential permissions are already granted,
  * *When* the app is launched and DataStore emits `hasCompletedPermissionOnboardingFlow == false`,
  * *Then* `checkAreAllPermissionsGranted()` evaluates to `true`, `setHasCompletedPermissionOnboarding(true)` is persisted, and no onboarding sheet is displayed.

* **Criterion 5 (Returning User Zero Popups)**:
  * *Given* a returning user where `hasCompletedPermissionOnboardingFlow` is `true`,
  * *When* the app is launched on cold start,
  * *Then* no onboarding sheet or modal dialogs appear (`REQ-PRI-003`).

* **Criterion 6 (JIT Fallback Intact)**:
  * *Given* a user who completed or skipped the onboarding flow without granting location permission,
  * *When* they subsequently tap Start on `ControlTrackingScreen`,
  * *Then* the standard JIT rationale cascade (`RationaleStep.FOREGROUND`) is presented.

* **Criterion 7 (Localization & Specifier Parity)**:
  * *Given* all 15 onboarding string keys,
  * *When* verified by `TranslationParityTest`,
  * *Then* all 9 locales pass with 100% translation parity and matching format specifiers.

---

### 1.5 System Invariants
1. **Zero Cold-Start Regressions for Returning Users**: Subsequent app launches must NEVER re-prompt users who already completed or dismissed the onboarding flow (`REQ-PRI-003`).
2. **Total Abortability & Non-Blocking Exploration**: The athlete must never be trapped in an unskippable modal wall; every step and the entire wizard must be skippable.
3. **Preservation of JIT Fallbacks**: Existing JIT permission checks in `ControlTrackingScreen.kt` and `DevicesTabbedScreen.kt` remain active as robust safety guards (`REQ-PRI-006`).
4. **Strict Fine Location Requirement**: Coarse location alone must never satisfy workout tracking requirements (`REQ-PRI-004`).
5. **Fail-Safe Platform Intent Safety**: Catching `ActivityNotFoundException` when invoking external battery optimization or application details intents (`REQ-PRI-005`).
6. **UI File Size Constraint**: `PermissionOnboardingSheet.kt` MUST remain strictly `< 400` lines.
7. **9-Language Localization Parity**: 100% parity across all 9 supported locales with positional format specifiers (`%1$d`, `%2$d`).
8. **Clean-Room Test Pass Rate**: Zero regressions across the full unit test suite.

---

## 2. Test Specification (`TST-PRI-006`)

### Test Case 1: DataStore Preference Flow & Persistence Unit Tests (`TST-PRI-006.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStoreOnboardingTest.kt`
* **Preconditions**: Fresh in-memory DataStore instance.
* **Action**:
  1. Read `hasCompletedPermissionOnboardingFlow.first()`.
  2. Invoke `setHasCompletedPermissionOnboarding(true)`.
  3. Read `hasCompletedPermissionOnboardingFlow.first()`.
  4. Invoke `setHasCompletedPermissionOnboarding(false)`.
  5. Read `hasCompletedPermissionOnboardingFlow.first()`.
* **Expected Result**:
  - Initial default value is `false` (`DEFAULT_HAS_COMPLETED_PERMISSION_ONBOARDING`).
  - After setting `true`, flow emits `true`.
  - After setting `false`, flow emits `false`.

### Test Case 2: Host App Launch & Upgrade Auto-Skip Integration Tests (`TST-PRI-006.2`)
* **Scope**: Integration / Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/activities/PermissionOnboardingLaunchContractTest.kt`
* **Preconditions**: Mocked Android Context, PackageManager, and PowerManager.
* **Scenarios**:
  - *Clean Install*: `hasCompleted = false`, fine location denied -> `checkAreAllPermissionsGranted` returns `false`, onboarding sheet presented.
  - *Upgraded Install*: `hasCompleted = false`, all permissions granted (Location, BT API 31+, Notifications API 33+, Battery) -> `checkAreAllPermissionsGranted` returns `true`, `setHasCompletedPermissionOnboarding(true)` persisted, onboarding sheet suppressed.
  - *Returning User*: `hasCompleted = true` -> onboarding sheet suppressed, zero cold-start popups.
* **Expected Result**: State evaluation matches expected branching without regression.

### Test Case 3: PermissionOnboardingSheet Step Navigation & Abortability Unit Tests (`TST-PRI-006.3`)
* **Scope**: UI Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/onboarding/permission/PermissionOnboardingSheetContractTest.kt`
* **Preconditions**: Sheet state initialized with `OnboardingStep.LOCATION`.
* **Action**:
  1. Verify step progression: `LOCATION` -> `BLUETOOTH` -> `NOTIFICATION` -> `BATTERY_OPTIMIZATION`.
  2. Verify per-step skipping: tapping "Skip this step" advances to subsequent step without permission requests.
  3. Verify total abortability: tapping "Skip Setup" or dismissing scrim invokes `onFinish` with `completed = true`.
  4. Verify UI file constraint: `PermissionOnboardingSheet.kt` line count is strictly `< 400`.
* **Expected Result**: Clean step navigation, complete abortability, file size constraint verified.

### Test Case 4: Preservation of Just-in-Time Fallback Invariants (`TST-PRI-006.4`)
* **Scope**: Integration / Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingPermissionTest.kt`
* **Preconditions**: Athlete holding zero permissions (skipped onboarding).
* **Action**: Athlete taps Start on `ControlTrackingScreen`.
* **Expected Result**: Existing JIT cascade (`RationaleStep.FOREGROUND`) triggers contextually as specified in `REQ-PRI-006`.

### Test Case 5: 9-Language Localization & Positional Specifier Audit (`TST-PRI-006.5`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Goal**: Verify string presence and matching `%1$d`, `%2$d` tokens across all 9 locales:
  - EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches, zero literal `"+ "` prefixes.

### Test Case 6: Clean-Room Regression Suite (`TST-PRI-006.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.
* **Expected Result**: Zero test failures or regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRI-006.1` | Unit | `TuningPreferencesDataStore.hasCompletedPermissionOnboardingFlow` | `REQ-PRI-007.1` | Specified |
| `TST-PRI-006.2` | Contract | `checkAreAllPermissionsGranted` & launch branching | `REQ-PRI-007.1`, `REQ-PRI-003` | Specified |
| `TST-PRI-006.3` | Contract | `PermissionOnboardingSheet` step navigation & abort | `REQ-PRI-007.2`, `REQ-PRI-007.3` | Specified |
| `TST-PRI-006.4` | Integration | `ControlTrackingScreen` JIT permission cascade | `REQ-PRI-007.4`, `REQ-PRI-006` | Specified |
| `TST-PRI-006.5` | Localization | `TranslationParityTest` across 9 canonical locales | `REQ-PRI-007.5`, `REQ-UI-106` | Specified |
| `TST-PRI-006.6` | Regression | `./gradlew testDebugUnitTest` full test suite | `REQ-PRO-001` | Specified |
