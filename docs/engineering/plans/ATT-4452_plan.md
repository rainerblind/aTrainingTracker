# Stage 3: Implementation Plan - ATT-4452: [Verbesserung] Prompt athlete to configure HR or Power zones via dialog/action when a newly discovered sensor is auto-paired

**Ticket**: [ATT-4452](https://rainerblind.atlassian.net/browse/ATT-4452)  
**Sub-task**: [ATT-4505](https://rainerblind.atlassian.net/browse/ATT-4505) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Requirement Mapping**: `REQ-UI-374` (*Contextual Zone Settings Navigation Upon Physiological Sensor Pairing*)  
**Test Mapping**: `TST-UI-337` (*Contextual Zone Settings Navigation Upon Physiological Sensor Pairing Verification*)  
**Branch**: `feature/ATT-4452`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-21  

---

## 1. Problem Description & Background

In Sprint 2026-42.4 (`ATT-3189`, commit `4033efb7`), contextual training zone onboarding (`REQ-UI-374`) was implemented by intercepting manual pairing transitions in `DeviceListViewModel.onPairedChanged(deviceId, isPaired)` (`if (!wasPaired && newPaired)`).

However, during active wireless sensor discovery outside workouts, `DeviceManager.java` (lines 211 & 267) automatically inserts brand-new sensors into SQLite with `paired = true` immediately and broadcasts `BANALService.NEW_DEVICE_FOUND_INTENT`. Because newly discovered sensors are auto-paired upon insertion, `DeviceListViewModel.onPairedChanged` is never invoked, leaving athletes with unconfigured default zones.

During sprint planning, **Option C** was selected:
When `BANALService.NEW_DEVICE_FOUND_INTENT` is received on `DevicesTabbedScreen` / `DevicesTabbedViewModel` for a newly discovered and auto-paired `DeviceType.HRM` (while `isHrZonesConfigured == false`) or `DeviceType.BIKE_POWER` (while `isPwrZonesConfigured == false`):
1. **Retain Athlete on Sensors Screen**: The app keeps the athlete on `DevicesTabbedScreen` so they see the newly discovered sensor appear in the list with standard tactile and Snackbar feedback.
2. **Confirmation Prompt Dialog**: The screen presents a Material 3 `AlertDialog` (with `containerColor = surface`, `tonalElevation = 0.dp`, and `AppDialogActions` button ergonomics):
   - **Title / Message**: Localized copy informing the athlete that their heart rate strap or power meter has been paired and asking if they wish to configure zones now.
   - **[Configure Zones]** (`confirmButton`): Navigates to `NavRoutes.trainingZones(initialTab, promptReason)` (tab 0 for HR, tab 2 for Cycling Power).
   - **[Not Now / Later]** (`dismissButton`): Dismisses the dialog and remains on the Sensors screen without modifying navigation state.
3. **Preserve Manual Pairing Transition**: The existing manual `isPaired: false -> true` check in `DeviceListViewModel.onPairedChanged(...)` is 100% preserved for known unpaired sensors.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-374` (*Contextual Zone Settings Navigation Upon Physiological Sensor Pairing*)
  - Clause 1: Pairing Detection & Sensor Discrimination (`DeviceListViewModel.kt`)
  - Clause 2: Zone Configuration Status Guard (`SettingsDataStore.kt`)
  - Clause 3: One-Time Deep-Link Navigation & Parameterized Route (`NavRoutes.kt`, `ATrainingTrackerApp.kt`)
  - Clause 4: Contextual Guidance Banner UI (`ZoneOnboardingBanner.kt`, `ZonesSettingsActivity.kt`)
  - Clause 5: 9-Language Localization Parity (`strings.xml`)
  - Clause 6: Auto-Pairing Discovery Confirmation Prompt Dialog on Sensors Screen (`DevicesTabbedViewModel.kt`, `DevicesTabbedScreen.kt`)
* **Test Mapping**: `TST-UI-337` (*Contextual Zone Settings Navigation Upon Physiological Sensor Pairing Verification*)
  - `TST-UI-337.1`: `DevicesTabbedViewModelTest` Discovery & Zone Prompt Emission
  - `TST-UI-337.2`: `DevicesTabbedScreen` Dialog Rendering & Action Callbacks Contract
  - `TST-UI-337.3`: 9-Language Localization & Format Token Parity Audit
  - `TST-UI-337.4`: Manual Pairing Non-Regression Suite (`DeviceListViewModelPairingTest.kt`)
  - `TST-UI-337.5`: Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing manual pairing tests in `DeviceListViewModelPairingTest.kt` continue to pass 100% without regression.
2. **Silent Discovery for Configured & Non-Physiological Sensors**: No prompt is emitted if zones are configured (`isHrZonesConfigured == true` / `isPwrZonesConfigured == true`) or for non-physiological devices (`BIKE_SPEED`, `BIKE_CADENCE`, `RUN_SPEED`, `ENVIRONMENT`, `SPEED_AND_LOCATION_GPS`).
3. **Modal Overlay Composition Retention (Rule 47 - ATT-4285)**: The dialog is rendered as an overlay *alongside* the underlying host screen in the Compose tree, never in a mutually exclusive `if/else` block that unmounts `HorizontalPager` or tab rows.
4. **Visual & Contract Consistency**: Dialog adheres to `BottomSheetVisualContractTest` (`containerColor = MaterialTheme.colorScheme.surface`, `tonalElevation = 0.dp`) and `AppDialogActions` ergonomics (cancel on dismiss/left, confirm on right).
5. **Single-Activity Navigation Invariant**: Navigating to `NavRoutes.TRAINING_ZONES` and returning via system back gesture or app bar arrow returns cleanly to `NavRoutes.SENSORS` with zero back-stack corruption.
6. **Code Lock Until Gate 3 Passes (Rule 4)**: No production source code in `app/src/main/...` will be edited until `check-gate` exits code `0`.
7. **One Atomic Commit per Ticket (Rule 8)**: No intermediate git commits during Stages 1–4; squashed atomic commit only upon verified completion in Stage 5.

---

## 4. Proposed Architectural Changes

### Component 1: `DevicesTabbedViewModel.kt` (State Management & Broadcast Interception)
* **File**: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedViewModel.kt`
* **Changes**:
  1. Define `ZoneOnboardingPrompt` data model:
     ```kotlin
     data class ZoneOnboardingPrompt(
         val deviceType: DeviceType,
         val deviceName: String
     )
     ```
  2. Inject `SettingsDataStore` with `@JvmOverloads`:
     ```kotlin
     class DevicesTabbedViewModel @JvmOverloads constructor(
         application: Application,
         private val savedStateHandle: SavedStateHandle,
         private val settingsDataStore: SettingsDataStore = SettingsDataStore(application),
         private val deviceDBRepository: DeviceDataRepository = DeviceDataRepository.getInstance(application),
         private val banalServiceRepository: BANALServiceRepository = BANALServiceRepository.getInstance(application)
     ) : AndroidViewModel(application)
     ```
  3. Expose reactive prompt state and dismissal method:
     ```kotlin
     private val _zonePromptState = MutableStateFlow<ZoneOnboardingPrompt?>(null)
     val zonePromptState: StateFlow<ZoneOnboardingPrompt?> = _zonePromptState.asStateFlow()

     fun dismissZonePrompt() {
         _zonePromptState.value = null
     }
     ```
  4. In `deviceDiscoveryReceiver`, upon `BANALService.NEW_DEVICE_FOUND_INTENT`:
     - Query device snapshot from `deviceDBRepository` or fallback to IO `DevicesDatabaseManager` to resolve both `deviceName` and `deviceType`.
     - Emit resolved name to existing `_newDeviceDiscoveredEvent`.
     - Check `deviceType`:
       - If `DeviceType.HRM`: query `settingsDataStore.isHrZonesConfigured.first()`. If `false`, emit `ZoneOnboardingPrompt(DeviceType.HRM, resolvedName)` to `_zonePromptState`.
       - If `DeviceType.BIKE_POWER`: query `settingsDataStore.isPwrZonesConfigured.first()`. If `false`, emit `ZoneOnboardingPrompt(DeviceType.BIKE_POWER, resolvedName)` to `_zonePromptState`.
       - For other types or when configured: do not set prompt state.

### Component 2: `DevicesTabbedScreen.kt` (UI Layer / Dialog Presentation)
* **File**: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreen.kt`
* **Changes**:
  1. Collect `tabViewModel.zonePromptState`:
     ```kotlin
     val zonePrompt by tabViewModel.zonePromptState.collectAsState()
     ```
  2. Render `AlertDialog` when `zonePrompt != null`:
     ```kotlin
     zonePrompt?.let { prompt ->
         val isHr = prompt.deviceType == DeviceType.HRM
         val titleRes = if (isHr) R.string.zone_onboarding_prompt_hr_title else R.string.zone_onboarding_prompt_pwr_title
         val messageRes = if (isHr) R.string.zone_onboarding_prompt_hr_message else R.string.zone_onboarding_prompt_pwr_message
         val initialTab = if (isHr) 0 else 2
         val promptReason = if (isHr) DeviceListViewModel.PROMPT_REASON_HR_PAIRED else DeviceListViewModel.PROMPT_REASON_PWR_PAIRED

         AlertDialog(
             onDismissRequest = { tabViewModel.dismissZonePrompt() },
             containerColor = MaterialTheme.colorScheme.surface,
             tonalElevation = 0.dp,
             title = { Text(stringResource(titleRes)) },
             text = { Text(stringResource(messageRes)) },
             confirmButton = {
                 Button(onClick = {
                     tabViewModel.dismissZonePrompt()
                     onNavigateToZones?.invoke(initialTab, promptReason)
                 }) {
                     Text(stringResource(R.string.zone_onboarding_action_configure))
                 }
             },
             dismissButton = {
                 TextButton(onClick = { tabViewModel.dismissZonePrompt() }) {
                     Text(stringResource(R.string.zone_onboarding_action_later))
                 }
             }
         )
     }
     ```
  3. Ensure overlay is positioned alongside the host screen content (Rule 47) and add `@Preview` composable covering Light and Dark themes.

### Component 3: String Resources & Localization
* **Files**: `app/src/main/res/values*/strings.xml`
* **Status**: Completed in Stage 2 across all 9 locales:
  - `zone_onboarding_prompt_hr_title`
  - `zone_onboarding_prompt_hr_message`
  - `zone_onboarding_prompt_pwr_title`
  - `zone_onboarding_prompt_pwr_message`
  - `zone_onboarding_action_configure`
  - `zone_onboarding_action_later`

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `DevicesTabbedScreen.kt` (lines 577–598, `showDeleteConfirmFor` AlertDialog) and `DeleteConfirmationDialog.kt`.
* **Reused components**: Material 3 `AlertDialog`, `Button`, `TextButton`, `Text`, `stringResource`.
* **Theme tokens**:
  - Shapes: Material 3 default dialog shape (`MaterialTheme.shapes.extraLarge`).
  - Colors: `containerColor = MaterialTheme.colorScheme.surface`, `tonalElevation = 0.dp` (required by `BottomSheetVisualContractTest`).
  - Button Ergonomics: Secondary text button on dismiss/left (`zone_onboarding_action_later`), primary filled button on confirm/right (`zone_onboarding_action_configure`).
* **New one-off styles & justification**: None. Standard Material 3 `AlertDialog` tokens reused 100%.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Code Lock Check & Verification
* Command: `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>`
* Ensure exit code `0` (`GATE_PASSED`) before modifying any files in `app/src/...`.

### Step 2: Implement State & Discovery Logic in `DevicesTabbedViewModel.kt`
* Target: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedViewModel.kt`
* Action:
  1. Define `data class ZoneOnboardingPrompt(val deviceType: DeviceType, val deviceName: String)`.
  2. Add `settingsDataStore: SettingsDataStore = SettingsDataStore(application)` to `DevicesTabbedViewModel` constructor.
  3. Declare `_zonePromptState = MutableStateFlow<ZoneOnboardingPrompt?>(null)` and `val zonePromptState: StateFlow<ZoneOnboardingPrompt?>`.
  4. Implement `fun dismissZonePrompt() { _zonePromptState.value = null }`.
  5. In `deviceDiscoveryReceiver`, resolve `DeviceType` and check `isHrZonesConfigured` / `isPwrZonesConfigured`, updating `_zonePromptState` when unconfigured.

### Step 3: Implement Confirmation Prompt Dialog in `DevicesTabbedScreen.kt`
* Target: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreen.kt`
* Action:
  1. Collect `tabViewModel.zonePromptState.collectAsState()`.
  2. Render `AlertDialog` when `zonePrompt != null` with `containerColor = MaterialTheme.colorScheme.surface`, `tonalElevation = 0.dp`, localized title/message, and `onNavigateToZones` invocation on confirm.
  3. Add multi-state `@Preview` composables for `ZoneOnboardingPromptDialog`.

### Step 4: Unit Test Verification in `DevicesTabbedViewModelTest.kt`
* Target: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedViewModelTest.kt`
* Action: Add unit test cases for `[TST-UI-337.1]`:
  - `testDiscovery_unconfiguredHrm_emitsZonePrompt`
  - `testDiscovery_unconfiguredPower_emitsZonePrompt`
  - `testDiscovery_configuredHrm_doesNotEmitZonePrompt`
  - `testDiscovery_configuredPower_doesNotEmitZonePrompt`
  - `testDiscovery_nonPhysiologicalSensor_doesNotEmitZonePrompt`
  - `testDismissZonePrompt_resetsPromptStateToNull`
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedViewModelTest"
  ```

### Step 5: Compose Contract Test Verification
* Target: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreenPromptContractTest.kt`
* Action: Add contract tests for `[TST-UI-337.2]`:
  - Verify `DevicesTabbedScreen.kt` collects `zonePromptState`.
  - Verify `DevicesTabbedScreen.kt` sets `containerColor = MaterialTheme.colorScheme.surface` and `tonalElevation = 0.dp`.
  - Verify `DevicesTabbedScreen.kt` references string keys `zone_onboarding_prompt_hr_title`, `zone_onboarding_prompt_pwr_title`, `zone_onboarding_action_configure`, and `zone_onboarding_action_later`.
  - Verify `DevicesTabbedScreen.kt` invokes `onNavigateToZones` and `dismissZonePrompt()`.
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedScreenPromptContractTest"
  ```

### Step 6: Non-Regression & Clean-Room Verification
* Commands:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.devicelist.DeviceListViewModelPairingTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
  ./gradlew testDebugUnitTest
  ```

---

## 6. Verification & Rollback Plan

* **Verification Strategy**:
  1. Fast targeted test loop during Stage 4 on `DevicesTabbedViewModelTest`, `DevicesTabbedScreenPromptContractTest`, and `DeviceListViewModelPairingTest`.
  2. Full clean-room test suite run in Stage 5: `./gradlew testDebugUnitTest`.
* **Rollback Strategy**:
  All changes reside on isolated feature branch `feature/ATT-4452`. If unrecoverable regressions occur, `git checkout sprint/2026-43.4` provides immediate rollback without impacting active sprint code.
