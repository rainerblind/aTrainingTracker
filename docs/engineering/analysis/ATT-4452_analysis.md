# Stage 1 Analysis: ATT-4452 - [Verbesserung] Prompt athlete to configure HR or Power zones via dialog/action when a newly discovered sensor is auto-paired

**Ticket**: [ATT-4452](https://rainerblind.atlassian.net/browse/ATT-4452)  
**Sub-task**: [ATT-4503](https://rainerblind.atlassian.net/browse/ATT-4503) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Branch**: `feature/ATT-4452`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-21  

---

## 1. Problem Statement & Motivation

In Sprint 2026-42.4 (`ATT-3189`, commit `4033efb7`), contextual training zone onboarding navigation (`REQ-UI-374`) was introduced. The goal was to automatically direct athletes to the relevant training zone configuration tab (`NavRoutes.trainingZones(initialTab, promptReason)`) when they pair a physiological sensor (`DeviceType.HRM` or `DeviceType.BIKE_POWER`) if their zones have not yet been customized (`isHrZonesConfigured == false` or `isPwrZonesConfigured == false`).

### Current Behavior & Defect Mechanism
In `ATT-3189`, the zone onboarding interception was implemented strictly within `DeviceListViewModel.onPairedChanged(deviceId, isPaired)`:
```kotlin
// DeviceListViewModel.kt line 119
if (!wasPaired && newPaired) {
    when (currentState.deviceType) {
        DeviceType.HRM -> { /* check isHrZonesConfigured and emit ZoneOnboardingEvent */ }
        DeviceType.BIKE_POWER -> { /* check isPwrZonesConfigured and emit ZoneOnboardingEvent */ }
        else -> { /* silent */ }
    }
}
```
This logic functions as intended when an already known, unpaired sensor (`isPaired == false`) is manually toggled to paired (`isPaired: false -> true`) by the athlete in the sensor list.

However, during active wireless sensor discovery (ANT+ or Bluetooth LE scanning), when a brand-new sensor is detected outside an active workout, `DeviceManager.java` (lines 211 & 267) automatically inserts the sensor record into SQLite with `paired = true` immediately upon creation:
```java
// DeviceManager.java (ANT+) line 211
deviceId = mDevicesDatabaseManager.insertNewAntDeviceIntoDB(deviceType, ..., true, ...);
// DeviceManager.java (BLE) line 267
deviceId = mDevicesDatabaseManager.insertNewBluetoothDeviceIntoDB(deviceType, ..., true);
```
and then executes:
```java
// DeviceManager.java lines 237-241 & 282-286
if (deviceId > 0) {
    if (isNew && !isWorkoutActive) {
        pairingChanged(deviceId, true);
    }
    sendNewDeviceFoundBroadcast(deviceId);
}
```
Because the newly discovered sensor enters SQLite already flagged as `paired = true`, a manual `false -> true` state transition never occurs in `DeviceListViewModel.onPairedChanged`. Consequently, athletes who scan and pair a brand-new heart rate monitor or power meter never receive the zone onboarding prompt, leaving their training zones unconfigured at generic defaults.

### Desired UX (Aligned Sprint Planning Option C)
When `BANALService.NEW_DEVICE_FOUND_INTENT` is received on `DevicesTabbedScreen` / `DevicesTabbedViewModel` for a newly discovered and auto-paired `DeviceType.HRM` (while `isHrZonesConfigured == false`) or `DeviceType.BIKE_POWER` (while `isPwrZonesConfigured == false`):
1. **Retain Athlete on Sensors Screen**: The app does NOT rudely auto-redirect or snatch the user away from the Sensors screen. The athlete remains on `DevicesTabbedScreen` to visually confirm that the newly discovered sensor has appeared in the list with haptic feedback and the standard discovery Snackbar.
2. **Material 3 Confirmation Prompt Dialog**: The screen displays a non-intrusive confirmation dialog (`AlertDialog` themed with `containerColor = surface`, `tonalElevation = 0.dp`, and `AppDialogActions` ergonomics):
   - **Title / Message**: Localized copy informing the athlete that their heart rate strap or power meter has been paired and asking if they wish to configure zones now (e.g. *"Heart Rate Monitor paired! Would you like to configure your Heart Rate zones now?"* or *"Power Meter paired! Would you like to configure your Cycling Power zones now?"*).
   - **[Configure Zones]** (`confirmButton`): Navigates to `NavRoutes.trainingZones(initialTab, promptReason)` (tab 0 for HR, tab 2 for Cycling Power).
   - **[Not Now / Later]** (`dismissButton`): Dismisses the dialog and remains on the Sensors screen without modifying navigation state.
3. **Preserve Manual Pairing Transition**: The existing manual `isPaired: false -> true` check in `DeviceListViewModel.onPairedChanged(...)` is 100% preserved for known unpaired sensors that are later paired manually.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Decoupled Discovery Architecture in `DeviceManager.java`
In `DeviceManager.java`, hardware scanning engines (`mANTSearchInterface` and `mBTSearchInterface`) run in background threads managed by `BANALService`. When an incoming wireless broadcast packet is parsed:
1. `DeviceManager` synchronizes on `mDevicesDatabaseManager`.
2. If the device address/number does not exist in SQLite, `insertNewAntDeviceIntoDB(..., /*paired=*/ true, ...)` or `insertNewBluetoothDeviceIntoDB(..., /*paired=*/ true)` is called with `paired = true`.
3. If not in active tracking mode (`!isWorkoutActive`), `pairingChanged(deviceId, true)` registers the sensor with active hardware filters.
4. `sendNewDeviceFoundBroadcast(deviceId)` dispatches an explicit Android broadcast intent `BANALService.NEW_DEVICE_FOUND_INTENT` containing extra `BANALService.DEVICE_ID = deviceId`.

This design is intentional: newly discovered sensors are immediately ready for use without requiring a redundant second tap to pair.

### 2.2 Event Blind Spot in `DeviceListViewModel.kt`
`DeviceListViewModel` observes device state via `GetMergedDevicesUseCase` and `DeviceDataRepository.allDevices`. It handles list filtering and user-initiated toggle actions (`onPairedChanged`).
Because the newly discovered sensor enters `DeviceDataRepository` with `isPaired == true` already set, the reactive stream emits the device directly in the paired state.
`DeviceListViewModel.onPairedChanged` is only triggered when the athlete taps the pair switch in `DeviceItem.kt`. Thus, auto-paired sensors completely bypass the pairing detection hook added in `ATT-3189`.

### 2.3 Host Receiver & Event Handling in `DevicesTabbedViewModel.kt`
`DevicesTabbedViewModel` is the lifecycle owner of `DevicesTabbedScreen` (`NavRoutes.SENSORS`). It registers a `deviceDiscoveryReceiver` listening for `BANALService.NEW_DEVICE_FOUND_INTENT` (lines 74–94):
```kotlin
private val deviceDiscoveryReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == BANALService.NEW_DEVICE_FOUND_INTENT) {
            deviceDBRepository.handleNewDeviceFound()
            val deviceId = intent.getLongExtra(BANALService.DEVICE_ID, -1L)
            viewModelScope.launch {
                val resolvedName = if (deviceId != -1L) {
                    deviceDBRepository.getDeviceSnapshotById(deviceId)?.deviceName
                        ?: withContext(Dispatchers.IO) {
                            runCatching {
                                DevicesDatabaseManager.getInstance(getApplication()).getDeviceName(deviceId)
                            }.getOrNull()
                        } ?: "Sensor"
                } else "Sensor"
                _newDeviceDiscoveredEvent.emit(resolvedName)
            }
        }
    }
}
```
Currently, `DevicesTabbedViewModel` only extracts `deviceId` to query its display name and emit a String to `_newDeviceDiscoveredEvent` (which `DevicesTabbedScreen` collects to trigger haptic feedback and show a Snackbar).
It does **NOT** query the newly discovered device's `DeviceType`, nor does it inspect `SettingsDataStore.isHrZonesConfigured` or `isPwrZonesConfigured`.

### 2.4 UX Friction Analysis: Auto-Redirection vs. Confirmation Dialog (Option C)
During sprint planning, three options were evaluated:
- **Option A (Do nothing / manual only)**: Leaves new athletes with unconfigured default zones.
- **Option B (Immediate auto-navigation on discovery)**: Jarringly pulls the athlete out of the Sensors screen while they are observing wireless scan results, potentially interrupting multi-sensor pairing.
- **Option C (Retain on screen + Confirmation Dialog)**: Recommended and agreed. The athlete observes their sensor appear in the list, receives reassuring tactile feedback, and is presented with a non-blocking Material 3 confirmation dialog giving them full control over whether to jump to Zone Settings now or stay on the Sensors screen.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Enhance `DevicesTabbedViewModel` to inspect `deviceId` from `NEW_DEVICE_FOUND_INTENT`, resolve its `DeviceType`, query `SettingsDataStore.isHrZonesConfigured` / `isPwrZonesConfigured`, and expose a `zonePromptState: StateFlow<ZoneOnboardingPrompt?>` (with `dismissZonePrompt()` mutator).
  2. Implement a dedicated confirmation prompt dialog (`AlertDialog`) on `DevicesTabbedScreen` compliant with Material 3, `containerColor = MaterialTheme.colorScheme.surface`, `tonalElevation = 0.dp`, and `AppDialogActions` button ergonomics.
  3. Wire dialog actions:
     - **[Configure Zones]**: Invokes `onNavigateToZones?.invoke(initialTab, promptReason)` (initialTab 0 / `"hr_paired"` for HRM; initialTab 2 / `"pwr_paired"` for BIKE_POWER) and dismisses the prompt.
     - **[Not Now / Later]**: Dismisses the prompt and stays on `DevicesTabbedScreen`.
  4. Preserve existing manual `isPaired: false -> true` check in `DeviceListViewModel.onPairedChanged(...)` for already known unpaired sensors.
  5. Add localized string resources across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  6. Add comprehensive unit tests in `DevicesTabbedViewModelTest.kt` verifying prompt state emission for unconfigured HRM and BIKE_POWER sensors, silent completion for configured sensors and non-physiological sensors, and prompt dismissal.
  7. Add contract / UI tests asserting dialog existence and action callbacks.
  8. Maintain 100% clean-room test pass rate under `./gradlew testDebugUnitTest`.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. **No modification to `DeviceManager.java` auto-pairing logic**: Newly discovered sensors outside active workouts must continue to be auto-paired upon insertion.
  2. **No changes to `SettingsDataStore` preference keys or fallback heuristics**: `isHrZonesConfigured` and `isPwrZonesConfigured` keys and migration logic from `ATT-3189` remain intact.
  3. **No changes to `ZoneOnboardingBanner` or `ZoneSettingsScreen`**: The banner displayed inside Zone Settings upon arrival with `promptReason` is preserved.
  4. **No prompts for non-physiological sensors**: Speed, cadence, environment, and GPS sensors must complete discovery silently without prompting for zones.
  5. **No prompts if zones are already configured**: If `isHrZonesConfigured == true` (for HRM) or `isPwrZonesConfigured == true` (for Power), sensor discovery completes with the standard snackbar only.
  6. **No modification to manual pairing flow**: `DeviceListViewModel.onPairedChanged` remains active and tested.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**:
  - Refines and extends `REQ-UI-374` (*Contextual Zone Settings Navigation Upon Physiological Sensor Pairing*).
  - Target files: `DevicesTabbedViewModel.kt`, `DevicesTabbedScreen.kt`, `DeviceListViewModel.kt`, `values*/strings.xml`, `docs/requirements.md`, `docs/tests.md`.

* **Historical Origin & Commit Trace**:
  - Introduced in Sprint 2026-42.4 via ticket `ATT-3189` (commit `4033efb7`, integrated via squash commit `e4145f35`).
  - Added clauses `REQ-UI-374.1` through `REQ-UI-374.5` and test specification `TST-UI-337`.

* **Root Reason for Existing Formulation**:
  - `REQ-UI-374.1` was formulated under the assumption that pairing occurs through the UI switch toggle in `DeviceListViewModel`. The background auto-pairing behavior in `DeviceManager` (which automatically inserts new sensors with `paired = true`) was not connected to `REQ-UI-374`, leaving newly discovered sensors unable to trigger onboarding.

* **Preservation of Core Invariants**:
  - **Manual Pairing Invariant**: Manual `isPaired: false -> true` pairing in `DeviceListViewModel.onPairedChanged` remains completely untouched.
  - **Non-Physiological Sensor Invariant**: Non-physiological sensors (`BIKE_SPEED`, `BIKE_CADENCE`, `RUN_SPEED`, `ENVIRONMENT`, `SPEED_AND_LOCATION_GPS`) remain 100% silent regarding zones.
  - **Configured Athlete Invariant**: If zones are already customized, pairing completes silently without displaying the prompt dialog.
  - **Compose Navigation Invariant**: Single-activity Compose navigation with seamless back navigation to `NavRoutes.SENSORS` remains preserved.
  - **Theme & Dialog Token Invariants**: Dialogs comply with `containerColor = MaterialTheme.colorScheme.surface` and `tonalElevation = 0.dp` (enforced by `BottomSheetVisualContractTest`).
  - **100% Clean-Room Test Suite Pass Rate**: Verified via `./gradlew testDebugUnitTest`.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Data Model
Introduce a lightweight data model representing an active onboarding prompt request:
```kotlin
data class ZoneOnboardingPrompt(
    val deviceType: DeviceType,
    val deviceName: String
)
```

### 5.2 Enhancements to `DevicesTabbedViewModel.kt`
1. Inject `SettingsDataStore` via constructor with `@JvmOverloads`:
   ```kotlin
   class DevicesTabbedViewModel @JvmOverloads constructor(
       application: Application,
       private val savedStateHandle: SavedStateHandle,
       private val settingsDataStore: SettingsDataStore = SettingsDataStore(application),
       private val deviceDBRepository: DeviceDataRepository = DeviceDataRepository.getInstance(application),
       private val banalServiceRepository: BANALServiceRepository = BANALServiceRepository.getInstance(application)
   ) : AndroidViewModel(application)
   ```
2. Declare reactive prompt state and dismissal mutator:
   ```kotlin
   private val _zonePromptState = MutableStateFlow<ZoneOnboardingPrompt?>(null)
   val zonePromptState: StateFlow<ZoneOnboardingPrompt?> = _zonePromptState.asStateFlow()

   fun dismissZonePrompt() {
       _zonePromptState.value = null
   }
   ```
3. In `deviceDiscoveryReceiver`, upon receiving `NEW_DEVICE_FOUND_INTENT`:
   - Refresh DB cache via `deviceDBRepository.handleNewDeviceFound()`.
   - Resolve `deviceId`.
   - Resolve `DeviceType`: Check `deviceDBRepository.getDeviceSnapshotById(deviceId)?.deviceType` with fallback to `withContext(Dispatchers.IO) { DevicesDatabaseManager.getInstance(getApplication()).getDeviceType(deviceId) }`.
   - Resolve `deviceName`: Same cached/IO pattern as currently implemented.
   - Emit `_newDeviceDiscoveredEvent.emit(resolvedName)`.
   - Evaluate zone configuration:
     - If `deviceType == DeviceType.HRM`:
       ```kotlin
       val isConfigured = settingsDataStore.isHrZonesConfigured.first()
       if (!isConfigured) {
           _zonePromptState.value = ZoneOnboardingPrompt(DeviceType.HRM, resolvedName)
       }
       ```
     - If `deviceType == DeviceType.BIKE_POWER`:
       ```kotlin
       val isConfigured = settingsDataStore.isPwrZonesConfigured.first()
       if (!isConfigured) {
           _zonePromptState.value = ZoneOnboardingPrompt(DeviceType.BIKE_POWER, resolvedName)
       }
       ```
     - For other sensor types or when already configured: do not set prompt.

### 5.3 UI Layer in `DevicesTabbedScreen.kt`
Collect `tabViewModel.zonePromptState.collectAsState()` and display a Material 3 `AlertDialog` when non-null:
```kotlin
val zonePrompt by tabViewModel.zonePromptState.collectAsState()

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

### 5.4 String Resources & 9-Locale Parity
Add 6 new string keys across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`:
- `zone_onboarding_prompt_hr_title`: "Heart Rate Monitor paired" (DE: "Herzfrequenz-Sensor gekoppelt")
- `zone_onboarding_prompt_hr_message`: "Would you like to configure your Heart Rate zones now?" (DE: "Möchtest du deine Herzfrequenz-Zonen jetzt konfigurieren?")
- `zone_onboarding_prompt_pwr_title`: "Power Meter paired" (DE: "Leistungsmesser gekoppelt")
- `zone_onboarding_prompt_pwr_message`: "Would you like to configure your Cycling Power zones now?" (DE: "Möchtest du deine Rad-Leistungszonen jetzt konfigurieren?")
- `zone_onboarding_action_configure`: "Configure Zones" (DE: "Zonen konfigurieren")
- `zone_onboarding_action_later`: "Not Now" (DE: "Nicht jetzt")

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. **Zero Regression on Manual Pairing**: Existing `DeviceListViewModelPairingTest` assertions continue to pass 100%.
  2. **Silent Discovery for Configured & Non-Physiological Sensors**: No prompt is emitted if zones are configured or for non-physiological devices.
  3. **Visual & Contract Consistency**: Dialog complies with `BottomSheetVisualContractTest` (`containerColor = surface`, `tonalElevation = 0.dp`) and `AppDialogActions` button ergonomics.
  4. **Single-Activity Navigation**: Back gesture from `NavRoutes.TRAINING_ZONES` returns cleanly to `NavRoutes.SENSORS`.
  5. **Clean-Room Test Pass Rate**: `./gradlew testDebugUnitTest` must remain at 100% passing tests.
  6. **Human Decision Gate**: Parent ticket advances to `Final Review (Human)` assigned to `human`.

* **Risk Rating**: **LOW**
  - **Technical Justification**: The changes are localized to `DevicesTabbedViewModel` (reactive state derivation upon broadcast) and `DevicesTabbedScreen` (`AlertDialog` overlay). No changes are made to low-level SQLite schema, BLE GATT connection lifecycles, ANT+ message routing, or background telemetry recording.
