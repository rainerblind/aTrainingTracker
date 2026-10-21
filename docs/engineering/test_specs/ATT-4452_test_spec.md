# Stage 2: Requirement & Test Specification - ATT-4452: [Verbesserung] Prompt athlete to configure HR or Power zones via dialog/action when a newly discovered sensor is auto-paired

**Ticket**: [ATT-4452](https://rainerblind.atlassian.net/browse/ATT-4452)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Requirement Mapping**: `REQ-UI-374` (*Contextual Zone Settings Navigation Upon Physiological Sensor Pairing*)  
**Test Spec ID**: `TST-UI-337`  
**Branch**: `feature/ATT-4452`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-10-21  

---

## 1. Requirement Specification (REQ-UI-374)

### 1.1 Problem Statement & Rationale
When discovering a brand-new ANT+ or BLE sensor outside an active workout, `DeviceManager.java` (lines 211 & 267) automatically inserts the sensor record into SQLite with `paired = true` immediately and broadcasts `BANALService.NEW_DEVICE_FOUND_INTENT`. Because the new sensor is auto-paired upon insertion, the manual `isPaired: false -> true` toggle hook in `DeviceListViewModel.onPairedChanged(...)` (introduced in `ATT-3189`) is never invoked. As a result, athletes discovering and auto-pairing a heart rate monitor or power meter are never prompted to configure their unconfigured HR or Cycling Power zones.

Sprint planning aligned on **Option C**:
When `BANALService.NEW_DEVICE_FOUND_INTENT` is received on `DevicesTabbedScreen` / `DevicesTabbedViewModel` for a newly discovered and auto-paired `DeviceType.HRM` (while `isHrZonesConfigured == false`) or `DeviceType.BIKE_POWER` (while `isPwrZonesConfigured == false`), the app:
1. Keeps the athlete on `DevicesTabbedScreen` so they see the newly discovered sensor appear in the list with standard tactile and Snackbar feedback.
2. Displays a Material 3 confirmation prompt dialog (`AlertDialog` with `containerColor = MaterialTheme.colorScheme.surface`, `tonalElevation = 0.dp`) offering to navigate to Zone Settings or dismiss.
3. Preserves the manual `isPaired: false -> true` path in `DeviceListViewModel.onPairedChanged(...)` for existing unpaired devices.

### 1.2 Functional & Architectural Requirements
1. **Auto-Pairing Discovery Interception (`REQ-UI-374.6`)**:
   - `DevicesTabbedViewModel` SHALL observe `BANALService.NEW_DEVICE_FOUND_INTENT` within its registered `deviceDiscoveryReceiver`.
   - Upon receiving the broadcast intent, `DevicesTabbedViewModel` SHALL resolve the device's `DeviceType` and `deviceName` via `DeviceDataRepository.getDeviceSnapshotById(deviceId)` (or fallback to `DevicesDatabaseManager`).
   - If `deviceType == DeviceType.HRM`, `DevicesTabbedViewModel` SHALL query `SettingsDataStore.isHrZonesConfigured.first()`. If `false`, it SHALL emit `ZoneOnboardingPrompt(DeviceType.HRM, resolvedName)` to `zonePromptState: StateFlow<ZoneOnboardingPrompt?>`.
   - If `deviceType == DeviceType.BIKE_POWER`, `DevicesTabbedViewModel` SHALL query `SettingsDataStore.isPwrZonesConfigured.first()`. If `false`, it SHALL emit `ZoneOnboardingPrompt(DeviceType.BIKE_POWER, resolvedName)` to `zonePromptState`.
   - For all other sensor types (`BIKE_SPEED`, `BIKE_CADENCE`, `RUN_SPEED`, `ENVIRONMENT`, `SPEED_AND_LOCATION_GPS`, etc.), `zonePromptState` SHALL remain `null`.
   - If zones are already configured (`true`), `zonePromptState` SHALL remain `null`.
   - `DevicesTabbedViewModel` SHALL expose `fun dismissZonePrompt()` resetting `_zonePromptState.value = null`.

2. **Confirmation Prompt Dialog UI (`REQ-UI-374.6`)**:
   - `DevicesTabbedScreen` SHALL collect `tabViewModel.zonePromptState.collectAsState()`.
   - When `zonePromptState` is non-null, `DevicesTabbedScreen` SHALL render a Material 3 `AlertDialog`:
     - Container Color: `MaterialTheme.colorScheme.surface`
     - Tonal Elevation: `0.dp`
     - Title: `R.string.zone_onboarding_prompt_hr_title` ("Heart Rate Monitor paired") or `R.string.zone_onboarding_prompt_pwr_title` ("Power Meter paired").
     - Message: `R.string.zone_onboarding_prompt_hr_message` ("Would you like to configure your Heart Rate zones now?") or `R.string.zone_onboarding_prompt_pwr_message` ("Would you like to configure your Cycling Power zones now?").
     - Confirm Button: `Text(stringResource(R.string.zone_onboarding_action_configure))` ("Configure Zones"). Tapping dismisses prompt dialog via `tabViewModel.dismissZonePrompt()` and invokes `onNavigateToZones?.invoke(initialTab, promptReason)` (`initialTab = 0` and `promptReason = "hr_paired"` for HRM; `initialTab = 2` and `promptReason = "pwr_paired"` for BIKE_POWER).
     - Dismiss Button: `Text(stringResource(R.string.zone_onboarding_action_later))` ("Not Now"). Tapping dismisses prompt dialog via `tabViewModel.dismissZonePrompt()` and remains on `DevicesTabbedScreen`.
     - `onDismissRequest`: Invokes `tabViewModel.dismissZonePrompt()`.

3. **Manual Pairing Preservation Invariant**:
   - The manual `isPaired: false -> true` pairing trigger in `DeviceListViewModel.onPairedChanged(...)` (`REQ-UI-374.1`) SHALL be strictly preserved for previously discovered unpaired sensors.

4. **9-Language Localization Parity (`REQ-UI-374.5`)**:
   - All 6 new prompt strings (`zone_onboarding_prompt_hr_title`, `zone_onboarding_prompt_hr_message`, `zone_onboarding_prompt_pwr_title`, `zone_onboarding_prompt_pwr_message`, `zone_onboarding_action_configure`, `zone_onboarding_action_later`) SHALL be defined across all 9 canonical locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with 100% token parity.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (HRM Auto-Discovery Unconfigured)**:
  * *Given* an athlete with unconfigured HR zones (`isHrZonesConfigured == false`) on `DevicesTabbedScreen`,
  * *When* `BANALService.NEW_DEVICE_FOUND_INTENT` is received for a newly discovered `DeviceType.HRM`,
  * *Then* the athlete remains on `DevicesTabbedScreen`, the sensor appears in the list with haptic/Snackbar feedback, and an `AlertDialog` prompts: *"Heart Rate Monitor paired! Would you like to configure your Heart Rate zones now?"*.
* **Criterion 2 (Power Meter Auto-Discovery Unconfigured)**:
  * *Given* an athlete with unconfigured power zones (`isPwrZonesConfigured == false`) on `DevicesTabbedScreen`,
  * *When* `BANALService.NEW_DEVICE_FOUND_INTENT` is received for a newly discovered `DeviceType.BIKE_POWER`,
  * *Then* the athlete remains on `DevicesTabbedScreen` and an `AlertDialog` prompts: *"Power Meter paired! Would you like to configure your Cycling Power zones now?"*.
* **Criterion 3 (Configure Zones Action)**:
  * *Given* the confirmation prompt dialog displayed for HRM or Power Meter,
  * *When* the athlete taps "Configure Zones",
  * *Then* the dialog is dismissed and the app navigates to `NavRoutes.trainingZones(initialTab, promptReason)` (`0` / `"hr_paired"` for HRM, `2` / `"pwr_paired"` for Power).
* **Criterion 4 (Not Now / Dismiss Action)**:
  * *Given* the confirmation prompt dialog displayed,
  * *When* the athlete taps "Not Now" or taps outside the dialog,
  * *Then* the dialog is dismissed and the athlete remains on `DevicesTabbedScreen` without navigation.
* **Criterion 5 (Configured Athlete Silent Discovery)**:
  * *Given* an athlete with already configured HR zones (`isHrZonesConfigured == true`),
  * *When* a new HRM sensor is discovered and auto-paired,
  * *Then* discovery completes silently on `DevicesTabbedScreen` with standard Snackbar only, and no prompt dialog is shown.
* **Criterion 6 (Non-Physiological Sensor Silent Discovery)**:
  * *Given* `DevicesTabbedScreen` active,
  * *When* a speed, cadence, or GPS sensor is discovered,
  * *Then* discovery completes silently without any zone prompt dialog.

### 1.4 System Invariants
- **Non-Physiological Invariant**: Non-physiological sensors (`BIKE_SPEED`, `BIKE_CADENCE`, `RUN_SPEED`, `ENVIRONMENT`, `SPEED_AND_LOCATION_GPS`) NEVER trigger zone prompt dialogs or zone navigation events.
- **Manual Pairing Invariant**: Existing manual pairing toggle behavior in `DeviceListViewModel.onPairedChanged(...)` remains intact with 100% test pass rate in `DeviceListViewModelPairingTest.kt`.
- **Theme & Elevation Token Invariant**: Dialog styling conforms to Material 3 tokens: `containerColor = MaterialTheme.colorScheme.surface`, `tonalElevation = 0.dp`.
- **Single-Activity Navigation Invariant**: Navigating to `NavRoutes.TRAINING_ZONES` and returning via system back gesture or app bar arrow returns cleanly to `NavRoutes.SENSORS` with zero back-stack corruption.
- **Clean-Room Test Suite Invariant**: `./gradlew testDebugUnitTest` must pass 100% across all unit tests.

### Requirement Archaeology & Chesterton's Fence Audit
* Original Requirement ID & Target: Refines and extends `REQ-UI-374` (*Contextual Zone Settings Navigation Upon Physiological Sensor Pairing*). Targets `DevicesTabbedViewModel.kt`, `DevicesTabbedScreen.kt`, `SettingsDataStore.kt`, `DeviceListViewModel.kt`, `NavRoutes.kt`, `ATrainingTrackerApp.kt`, `ZonesSettingsActivity.kt`, and `values*/strings.xml`.
* Historical Origin & Commit Trace: Introduced in Sprint 2026-40.5 (`ATT-1390`, commit `e3966ebf`), extended in Sprint 2026-42.4 (`ATT-3188`, commit `c800ca58`), and refined in Sprint 2026-42.4 (`ATT-3189`, commit `4033efb7`, integrated via squash `e4145f35`). Active ticket `ATT-4452`.
* Root Reason for Existing Formulation: In `ATT-3189`, zone onboarding redirection was implemented strictly inside `DeviceListViewModel.onPairedChanged(...)` under the assumption that pairing occurs via the manual switch toggle. However, `DeviceManager.java` (lines 211 & 267) automatically inserts newly discovered sensors with `paired = true` outside active workouts, causing newly discovered sensors to completely bypass `DeviceListViewModel.onPairedChanged` and leaving athletes unprompted.
* Preservation of Core Invariants: Manual pairing in `DeviceListViewModel` is preserved, silent discovery for configured athletes is preserved, silent discovery for non-physiological sensors is strictly preserved, single-activity Compose navigation is preserved, dialog surface styling is preserved, and 100% clean-room test pass rate is strictly preserved.

---

## 2. Test Specification (TST-UI-337)

### Test Case 1: `DevicesTabbedViewModelTest` Discovery & Zone Prompt Emission (`TST-UI-337.1`)
* **Scope**: ViewModel Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedViewModelTest.kt`
* **Preconditions**:
  * Mock or in-memory `SettingsDataStore` with `isHrZonesConfigured` and `isPwrZonesConfigured`.
  * Mock `DeviceDataRepository` returning `DeviceType.HRM`, `DeviceType.BIKE_POWER`, or `DeviceType.BIKE_SPEED`.
* **Actions & Assertions**:
  1. `onNewDeviceFound` with `DeviceType.HRM` and `isHrZonesConfigured == false`:
     * Assert `zonePromptState.value` equals `ZoneOnboardingPrompt(DeviceType.HRM, "Polar H10")`.
  2. `onNewDeviceFound` with `DeviceType.BIKE_POWER` and `isPwrZonesConfigured == false`:
     * Assert `zonePromptState.value` equals `ZoneOnboardingPrompt(DeviceType.BIKE_POWER, "Stages Power")`.
  3. `onNewDeviceFound` with `DeviceType.HRM` and `isHrZonesConfigured == true`:
     * Assert `zonePromptState.value` is `null`.
  4. `onNewDeviceFound` with `DeviceType.BIKE_POWER` and `isPwrZonesConfigured == true`:
     * Assert `zonePromptState.value` is `null`.
  5. `onNewDeviceFound` with non-physiological sensor (`DeviceType.BIKE_SPEED`):
     * Assert `zonePromptState.value` is `null` regardless of zone configuration.
  6. `dismissZonePrompt()`:
     * When `zonePromptState.value` is non-null, calling `dismissZonePrompt()` resets `zonePromptState.value` to `null`.

### Test Case 2: `DevicesTabbedScreen` Dialog Rendering & Action Callbacks (`TST-UI-337.2`)
* **Scope**: Compose Contract / Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreenPromptContractTest.kt`
* **Preconditions**: `DevicesTabbedScreen` rendered or dialog composable invoked with active `ZoneOnboardingPrompt`.
* **Actions & Assertions**:
  1. Verify dialog is displayed with title and message corresponding to HRM or Power Meter.
  2. Verify clicking "Configure Zones" invokes `tabViewModel.dismissZonePrompt()` and `onNavigateToZones(initialTab, promptReason)`.
  3. Verify clicking "Not Now" invokes `tabViewModel.dismissZonePrompt()` without calling `onNavigateToZones`.
  4. Verify dialog styling matches `containerColor = surface` and `tonalElevation = 0.dp`.

### Test Case 3: 9-Language Localization & Format Token Parity Audit (`TST-UI-337.3`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Goal**: Verify string presence and 100% format token parity across all 9 supported locales:
  * EN (`values/strings.xml`)
  * DE (`values-de/strings.xml`)
  * ES (`values-es/strings.xml`)
  * FR (`values-fr/strings.xml`)
  * IT (`values-it/strings.xml`)
  * JA (`values-ja/strings.xml`)
  * NL (`values-nl/strings.xml`)
  * PL (`values-pl/strings.xml`)
  * PT (`values-pt/strings.xml`)
* **String Keys**:
  * `zone_onboarding_prompt_hr_title`
  * `zone_onboarding_prompt_hr_message`
  * `zone_onboarding_prompt_pwr_title`
  * `zone_onboarding_prompt_pwr_message`
  * `zone_onboarding_action_configure`
  * `zone_onboarding_action_later`
* **Expected Result**: 100% key parity, non-empty values, and zero token mismatches.

### Test Case 4: Manual Pairing Non-Regression Suite (`TST-UI-337.4`)
* **Scope**: Unit Test Regression
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceListViewModelPairingTest.kt`
* **Goal**: Verify that manual `isPaired: false -> true` pairing transitions in `DeviceListViewModel` continue to pass 100% without regression.

### Test Case 5: Clean-Room Regression Suite (`TST-UI-337.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero test failures.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-337.1]` | ViewModel Unit | `DevicesTabbedViewModel.deviceDiscoveryReceiver` / `dismissZonePrompt` | `REQ-UI-374.6` | Specified |
| `[TST-UI-337.2]` | Compose Contract | `DevicesTabbedScreen` Zone Onboarding `AlertDialog` | `REQ-UI-374.6` | Specified |
| `[TST-UI-337.3]` | Localization Parity | `TranslationParityTest` (9 locales) | `REQ-UI-374.5`, `REQ-LOC-001` | Passed |
| `[TST-UI-337.4]` | Non-Regression | `DeviceListViewModelPairingTest` | `REQ-UI-374.1` | Specified |
| `[TST-UI-337.5]` | Clean-Room Suite | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
