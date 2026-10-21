# Stage 3: Implementation Plan - ATT-4455: [Feature] Add abortable first-install permission onboarding flow on initial app launch

**Ticket**: [ATT-4455](https://rainerblind.atlassian.net/browse/ATT-4455)  
**Sub-task**: [ATT-4500](https://rainerblind.atlassian.net/browse/ATT-4500) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2617](https://rainerblind.atlassian.net/browse/ATT-2617) (*Optimize first impression*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Requirement Mapping**: `REQ-PRI-007` (`REQ-PRI-007.1`, `REQ-PRI-007.2`, `REQ-PRI-007.3`, `REQ-PRI-007.4`, `REQ-PRI-007.5`), refining `REQ-PRI-003` and `REQ-PRI-006`  
**Test Mapping**: `TST-PRI-006` (`TST-PRI-006.1`, `TST-PRI-006.2`, `TST-PRI-006.3`, `TST-PRI-006.4`, `TST-PRI-006.5`, `TST-PRI-006.6`)  
**Branch**: `feature/ATT-4455`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-21  

---

## 1. Problem Description & Background

In **aTrainingTracker**, tracking workouts with continuous GPS tracks, BLE sensor telemetry (heart rate, cycling power, cadence), foreground status notifications, and background survival requires several Android runtime permissions and system exemptions:
- **Location** (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`) and **Background Location** (`ACCESS_BACKGROUND_LOCATION`): Required for recording accurate GPS trajectories when the screen is locked or the app is running in the background.
- **Nearby Devices / Bluetooth** (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` on Android 12+ / API 31+): Required for discovering and pairing BLE sensors (heart rate straps, power meters, smart trainers, speed/cadence sensors).
- **Notifications** (`POST_NOTIFICATIONS` on Android 13+ / API 33+): Required for the active workout foreground service notification (`TrackerService`), lock-screen controls, split-lap announcements, and sensor battery alerts.
- **Battery Optimization Exemption** (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`): Required to prevent OEM battery-saving subsystems and Android Doze from killing background tracking during long rides or runs.

### The Problem Mechanism
Currently, permissions and battery optimization exemptions are evaluated purely **Just-in-Time (JIT)** when the athlete expresses tracking intent (tapping *Start* on `ControlTrackingScreen` or initiating BLE pairing on `DevicesTabbedScreen`).

During human verification of `ATT-3387` on a clean initial installation, severe usability friction was identified:
When a new athlete installs **aTrainingTracker** for the first time and taps the prominent **Start** button to begin their very first workout, they are ambushed by an overwhelming, sequential cascade of 4 permission rationale sheets, 3 system permission dialogs, and an external Android OS Settings redirect (`FOREGROUND` $\rightarrow$ `NOTIFICATION` $\rightarrow$ `BACKGROUND_LOCATION` in OS Settings $\rightarrow$ `BATTERY_OPTIMIZATION` dialog). This creates immense cognitive friction, delays workout recording, and produces a jarring first impression.

### Objective
Introduce a dedicated, welcoming, educational, and **abortable/skippable First-Install Permission Onboarding Flow** (`PermissionOnboardingSheet`) on initial application launch:
1. Walk the athlete step-by-step through core setup categories before they head out for a workout.
2. Clearly explain the athletic necessity of each permission.
3. Allow skipping any individual step ("Skip" / "Not Now") or aborting the entire setup flow ("Skip Setup") at any point so the user is never blocked from exploring the app.
4. Persist completion/dismissal in `TuningPreferencesDataStore` so it only triggers once on fresh installs.
5. Automatically auto-skip for upgraded installs or returning users where permissions are already granted.
6. Strictly retain existing Just-in-Time permission checks on `ControlTrackingScreen` and `DevicesTabbedScreen` as robust fallback guards.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PRI-007` (*Abortable First-Install Permission Onboarding Flow and Upgrade Auto-Skip Architecture*)
  * `REQ-PRI-007.1`: First-Launch Detection & Upgraded Install Auto-Skip (`ATrainingTrackerApp.kt`, `TuningPreferencesDataStore.kt`) — define `KEY_HAS_COMPLETED_PERMISSION_ONBOARDING` in DataStore, evaluate `checkAreAllPermissionsGranted(context)`, and automatically auto-skip without rendering UI if all permissions are already held.
  * `REQ-PRI-007.2`: Step-by-Step Educational Onboarding Wizard (`PermissionOnboardingSheet.kt`) — implement modular Compose `ModalBottomSheet` strictly `< 400` lines covering 4 discrete setup steps (Location, Bluetooth on API 31+, Notifications on API 33+, Battery Optimization).
  * `REQ-PRI-007.3`: Total Abortability & Non-Blocking Navigation — provide per-step skip action ("Skip this step") and overall abort action ("Skip Setup") persisting completion immediately without locking the UI.
  * `REQ-PRI-007.4`: Preservation of Just-in-Time Fallback Invariants — retain all existing JIT checks in `ControlTrackingScreen.kt` and `DevicesTabbedScreen.kt` as fallback guards for skipped permissions.
  * `REQ-PRI-007.5`: 100% 9-Language Localization Parity — translate all 15 onboarding string keys across EN, DE, ES, FR, IT, JA, NL, PL, PT with indexed positional format specifiers (`%1$d`, `%2$d`).
* **Test Mapping**: `TST-PRI-006` (`TST-PRI-006.1` – `TST-PRI-006.6`)
  * `TST-PRI-006.1`: DataStore Preference Flow & Persistence Unit Tests (`TuningPreferencesDataStoreOnboardingTest.kt`)
  * `TST-PRI-006.2`: Host App Launch & Upgrade Auto-Skip Integration Tests (`PermissionOnboardingLaunchContractTest.kt`)
  * `TST-PRI-006.3`: PermissionOnboardingSheet Step Navigation & Abortability Unit Tests (`PermissionOnboardingSheetContractTest.kt`)
  * `TST-PRI-006.4`: Preservation of Just-in-Time Fallback Invariants (`ControlTrackingPermissionTest.kt` / `DevicesTabbedPermissionContractTest.kt`)
  * `TST-PRI-006.5`: 9-Language Localization & Positional Specifier Parity Audit (`TranslationParityTest.kt`)
  * `TST-PRI-006.6`: Clean-Room Full Test Suite Regression Execution (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Cold-Start Regressions for Returning Users (`REQ-PRI-003`)**: Returning athletes who have already completed or dismissed the onboarding flow MUST NEVER encounter modal startup popups.
2. **Total Abortability & Non-Blocking Exploration**: The athlete MUST be able to skip any step or dismiss the entire flow at any time; app usage must never be blocked or locked.
3. **Strict Preservation of JIT Fallbacks (`REQ-PRI-006`)**: All existing permission checks on `ControlTrackingScreen` (Start button) and `DevicesTabbedScreen` (Pair Sensor) remain fully functional as safety guards.
4. **Strict Fine Location Requirement (`REQ-PRI-004`)**: Coarse location alone must never satisfy workout tracking requirements.
5. **Fail-Safe Platform Intent Safety (`REQ-PRI-005`)**: Catching `ActivityNotFoundException` when invoking external battery optimization or application details intents.
6. **Modal Overlay Retention Alongside Host Screen (Rule 47 — ATT-4285)**: `PermissionOnboardingSheet` MUST be rendered as an overlay *alongside* the underlying host screen in the Compose tree (`ATrainingTrackerApp.kt`), NEVER inside a mutually exclusive `if / else` branch that would unmount the active cockpit screen.
7. **UI File Size Constraint**: `PermissionOnboardingSheet.kt` MUST remain strictly `< 400` lines.
8. **9-Language Localization Parity**: 100% parity across all 9 supported locales with positional format specifiers (`%1$d`, `%2$d`) and zero literal `"+ "` prefixes.
9. **Code Lock Until Gate 3 Passes (Rule 4)**: No production source code in `app/src/main/...` shall be created or edited until `check-gate` passes on this plan.
10. **One Commit per Ticket at Stage 5 (Rule 8)**: No intermediate git commits during Stages 1–4; changes are squashed onto `sprint/2026-43.4` strictly upon Stage 5 clean-room verification.
11. **Parent Human Decision Gate (Rule 3)**: Terminal completion of parent ticket `ATT-4455` is strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes (SWE.2)

### 4.1 Component Diagram & Layer Boundaries

```
+--------------------------------------------------------------------------------------------------+
| Presentation Layer: ATrainingTrackerApp.kt (Single Activity Compose Host)                        |
+--------------------------------------------------------------------------------------------------+
| - Collects tuningPreferencesDataStore.hasCompletedPermissionOnboardingFlow                       |
| - Evaluates checkAreAllPermissionsGranted(context)                                               |
| - Auto-skips if all granted: setHasCompletedPermissionOnboarding(true)                          |
| - Mounts PermissionOnboardingSheet alongside Scaffold when !hasCompleted && !allGranted         |
+--------------------------------------------------------------------------------------------------+
                                                 |
                                                 v
+--------------------------------------------------------------------------------------------------+
| UI Component: com.atrainingtracker.trainingtracker.ui.onboarding.permission.PermissionOnboardingSheet |
+--------------------------------------------------------------------------------------------------+
| - Strictly < 400 lines                                                                           |
| - Consumes Material 3 ModalBottomSheet & BottomSheetDesign tokens                                |
| - State machine: OnboardingStep (LOCATION, BLUETOOTH, NOTIFICATION, BATTERY_OPTIMIZATION)         |
| - Per-step actions: Grant / Continue, Skip step ("Skip this step")                              |
| - Overall action: Skip Setup ("Skip Setup" / Scrim tap) -> persists completed = true             |
| - Light / Dark / AMOLED Multi-State @Previews                                                    |
+--------------------------------------------------------------------------------------------------+
                                                 |
                                                 v
+--------------------------------------------------------------------------------------------------+
| Settings / Persistence Layer: TuningPreferencesDataStore.kt                                      |
+--------------------------------------------------------------------------------------------------+
| + KEY_HAS_COMPLETED_PERMISSION_ONBOARDING: Preferences.Key<Boolean>                              |
| + hasCompletedPermissionOnboardingFlow: Flow<Boolean>                                            |
| + suspend fun setHasCompletedPermissionOnboarding(completed: Boolean)                           |
+--------------------------------------------------------------------------------------------------+
```

### 4.2 Component 1: `TuningPreferencesDataStore.kt`
- **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`
- **Modifications**:
  1. Add default constant to `TuningPreferencesDefaults`:
     ```kotlin
     const val DEFAULT_HAS_COMPLETED_PERMISSION_ONBOARDING = false
     ```
  2. Add key definition to `TuningPreferencesKeys`:
     ```kotlin
     val KEY_HAS_COMPLETED_PERMISSION_ONBOARDING: Preferences.Key<Boolean> =
         booleanPreferencesKey("tuning_has_completed_permission_onboarding")
     ```
  3. Include `KEY_HAS_COMPLETED_PERMISSION_ONBOARDING` in `DEFAULT_VALUES_MAP`.
  4. Expose reactive flow and persistence mutator:
     ```kotlin
     val hasCompletedPermissionOnboardingFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
         prefs[KEY_HAS_COMPLETED_PERMISSION_ONBOARDING] ?: TuningPreferencesDefaults.DEFAULT_HAS_COMPLETED_PERMISSION_ONBOARDING
     }

     suspend fun setHasCompletedPermissionOnboarding(completed: Boolean) {
         context.dataStore.edit { prefs ->
             prefs[KEY_HAS_COMPLETED_PERMISSION_ONBOARDING] = completed
         }
     }
     ```

### 4.3 Component 2: `PermissionOnboardingSheet.kt`
- **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/onboarding/permission/PermissionOnboardingSheet.kt`
- **Modifications**:
  - Implement step model:
    ```kotlin
    enum class OnboardingStep {
        LOCATION,
        BLUETOOTH,
        NOTIFICATION,
        BATTERY_OPTIMIZATION
    }
    ```
  - Composable signature:
    ```kotlin
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PermissionOnboardingSheet(
        modifier: Modifier = Modifier,
        sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onFinish: () -> Unit
    )
    ```
  - Educational step descriptions and benefit cards reuse visual layout from `PermissionRationaleSheet.kt` (`RationaleValueItem`) and standard icons (`R.drawable.ic_location`, `R.drawable.ic_my_paired_devices`, `R.drawable.ic_lap_timer`, `R.drawable.ic_battery_full`).
  - Permission Launchers:
    - `locationLauncher`: requests `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`.
    - `bluetoothLauncher`: requests `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` (Android 12+).
    - `notificationLauncher`: requests `POST_NOTIFICATIONS` (Android 13+).
    - `batteryOptimization`: triggers `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with package URI inside `try/catch (ActivityNotFoundException)`.
  - Header displays:
    - Step progress text: `stringResource(R.string.permission_onboarding_step_format, currentStepIndex + 1, totalSteps)`
    - Dismiss text button: `stringResource(R.string.permission_onboarding_skip_setup)`
  - Buttons:
    - Primary: `stringResource(R.string.permission_onboarding_grant)` / `continue` / `finish`
    - Secondary: `stringResource(R.string.permission_onboarding_skip_step)`
  - Enforce file size strictly `< 400` lines.
  - Multi-state `@Preview` composables for Light, Dark, and AMOLED themes across all 4 steps.

### 4.4 Component 3: `ATrainingTrackerApp.kt` & `MainActivityWithNavigation.kt`
- **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt`
- **Modifications**:
  - Helper predicate:
    ```kotlin
    fun checkAreAllPermissionsGranted(context: Context): Boolean {
        val hasLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasLocation) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasBtScan = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            val hasBtConnect = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasBtScan || !hasBtConnect) return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotification = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasNotification) return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager?.isIgnoringBatteryOptimizations(context.packageName) != true) {
                return false
            }
        }

        return true
    }
    ```
  - State observation:
    ```kotlin
    val tuningDataStore = remember { TuningPreferencesDataStore(activity.applicationContext) }
    val hasCompletedOnboarding by tuningDataStore.hasCompletedPermissionOnboardingFlow.collectAsState(initial = true)
    var showOnboardingSheet by remember { mutableStateOf(false) }

    LaunchedEffect(hasCompletedOnboarding) {
        if (!hasCompletedOnboarding) {
            if (checkAreAllPermissionsGranted(activity)) {
                tuningDataStore.setHasCompletedPermissionOnboarding(true)
            } else {
                showOnboardingSheet = true
            }
        } else {
            showOnboardingSheet = false
        }
    }
    ```
  - Host integration (Rule 47): Render `PermissionOnboardingSheet` as an overlay alongside host screen:
    ```kotlin
    if (showOnboardingSheet) {
        PermissionOnboardingSheet(
            onFinish = {
                scope.launch {
                    tuningDataStore.setHasCompletedPermissionOnboarding(true)
                    showOnboardingSheet = false
                }
            }
        )
    }
    ```

### 4.5 UI Consistency (Rule 23)
* **Reference screen / component**: `PermissionRationaleSheet.kt` (lines 74–275) — standard Material 3 modal sheet layout with domain icon, title, subtitle, benefit card rows, and dual-action button footer.
* **Reused components**: Material 3 `ModalBottomSheet`, `Card`, `Button`, `TextButton`, `BottomSheetDesign.SheetShape`, standard drawables (`R.drawable.ic_location`, `R.drawable.ic_my_paired_devices`, `R.drawable.ic_lap_timer`, `R.drawable.ic_battery_full`).
* **Theme tokens**:
  - Shapes: `RoundedCornerShape(12.dp)` for benefit cards and buttons.
  - Spacing scale: `8.dp`, `12.dp`, `16.dp`, `20.dp`, `24.dp`.
  - Colors: `MaterialTheme.colorScheme.surface`, `MaterialTheme.colorScheme.surfaceContainerHighest`, `MaterialTheme.colorScheme.primary`, `MaterialTheme.colorScheme.onSurface`, `MaterialTheme.colorScheme.onSurfaceVariant`.
* **New one-off styles & justification**: None. Strictly consumes established theme tokens and shared layout patterns.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: `TuningPreferencesDataStore.kt` Persistence Implementation (`TST-PRI-006.1`)
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStoreOnboardingTest.kt`
* **Changes**:
  1. Add `DEFAULT_HAS_COMPLETED_PERMISSION_ONBOARDING = false` to `TuningPreferencesDefaults`.
  2. Add `KEY_HAS_COMPLETED_PERMISSION_ONBOARDING` to `TuningPreferencesKeys` and register in `DEFAULT_VALUES_MAP`.
  3. Expose `hasCompletedPermissionOnboardingFlow` and `suspend fun setHasCompletedPermissionOnboarding(completed: Boolean)`.
  4. Write `TuningPreferencesDataStoreOnboardingTest.kt` testing initial default emission and reactive updates.
* **Targeted Test Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStoreOnboardingTest"
  ```

### Step 2: `PermissionOnboardingSheet.kt` Modular Composable Construction (`TST-PRI-006.3`)
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/onboarding/permission/PermissionOnboardingSheet.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/onboarding/permission/PermissionOnboardingSheetContractTest.kt`
* **Changes**:
  1. Create `PermissionOnboardingSheet.kt` (< 400 lines) with `OnboardingStep` enum and step wizard logic.
  2. Implement Step 1 (Location), Step 2 (Bluetooth on API 31+), Step 3 (Notifications on API 33+), Step 4 (Battery Optimization).
  3. Wire per-step skipping ("Skip this step") and overall abort ("Skip Setup" / scrim tap) invoking `onFinish`.
  4. Provide Light, Dark, and AMOLED `@Preview` composables.
  5. Write `PermissionOnboardingSheetContractTest.kt` verifying composable existence, step constants, file line count `< 400`, and step transitions.
* **Targeted Test Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.onboarding.permission.PermissionOnboardingSheetContractTest"
  ```

### Step 3: `ATrainingTrackerApp.kt` Host Integration & Upgrade Auto-Skip (`TST-PRI-006.2`)
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/activities/PermissionOnboardingLaunchContractTest.kt`
* **Changes**:
  1. Implement `checkAreAllPermissionsGranted(context)` helper.
  2. In `ATrainingTrackerApp.kt`, collect `hasCompletedPermissionOnboardingFlow`, auto-skip if all granted, and mount `PermissionOnboardingSheet` as an overlay alongside host screen (Rule 47).
  3. Write `PermissionOnboardingLaunchContractTest.kt` verifying branching logic for clean installs, upgraded installs, and returning users.
* **Targeted Test Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.activities.PermissionOnboardingLaunchContractTest"
  ```

### Step 4: Verification of JIT Fallbacks & Localization Parity (`TST-PRI-006.4`, `TST-PRI-006.5`)
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingPermissionTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/localization/PermissionOnboardingLocalizationTest.kt`
* **Changes**:
  1. Add targeted assertions to confirm that an athlete who skipped onboarding receives the standard JIT prompts on `ControlTrackingScreen` when tapping Start.
  2. Implement `PermissionOnboardingLocalizationTest.kt` verifying 100% 9-language localization parity and positional specifiers.
* **Targeted Test Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingPermissionTest" --tests "com.atrainingtracker.trainingtracker.localization.PermissionOnboardingLocalizationTest"
  ```

### Step 5: Full Clean-Room Regression Verification (`TST-PRI-006.6`)
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* **Expected Result**: 100% clean-room test suite pass rate with zero regressions.

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Phase 1 (Stage 4 Construction): Targeted unit test commands running in 5–15 seconds per atomic step.
  - Phase 2 (Stage 5 Verification): Full clean-room test suite (`./gradlew testDebugUnitTest`) across all 430+ test classes.
* **Rollback Plan**:
  - All changes reside on the isolated feature branch `feature/ATT-4455`.
  - In the event of an unresolvable defect or regression, the branch can be cleanly reset (`git checkout feature/ATT-4455 -- .`) or discarded without impacting `sprint/2026-43.4` or `develop`.
