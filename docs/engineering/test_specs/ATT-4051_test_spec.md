# Stage 2: Requirement & Test Specification - ATT-4051: Show "+ Pair Sensor" onboarding action when no sensors are paired, add empty-state search action, and auto-pair newly discovered sensors by default

**Ticket**: [ATT-4051](https://rainerblind.atlassian.net/browse/ATT-4051)  
**Sub-task**: [ATT-4117](https://rainerblind.atlassian.net/browse/ATT-4117) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2617](https://rainerblind.atlassian.net/browse/ATT-2617) (*Optimize first impression*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-SEN-020` (*Streamlined Sensor Onboarding, Empty-State Action & Discovery Auto-Pairing*)  
**Test Spec ID**: `TST-SEN-020`  
**Branch**: `feature/ATT-4051`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Requirement Specification (REQ-SEN-020)

### 1.1 Problem Statement & Rationale
On a fresh installation (or whenever no remote sensors are paired, `hasPairedRemoteDevices == false`), new users face three critical usability hurdles:
1. The start tracking cockpit (`ControlTrackingScreen.kt`) renders a completely blank sensor header slot when no devices are paired, leaving no visual clue or direct path to pair a sensor.
2. In `AppNavigationDrawer.kt`, "My Sensors" (`R.id.drawer_my_sensors`) is tucked away inside the "My Stuff" section, which is collapsed by default.
3. Once in "My Sensors", the empty state provides no explanation or action button to start scanning, and when a user explicitly initiates a scan and discovers a sensor, it is stored as `paired = false` by default, requiring an unintuitive second step (manually locating and flipping a switch) before the sensor can actually be used.

Resolving these friction points streamlines first-run onboarding, guides users directly from the start cockpit to sensor discovery, and ensures newly found sensors during an explicit scan are immediately ready for workout tracking while preserving instant unpairing control and robust state lifecycle management.

### 1.2 Functional & Architectural Requirements
The system SHALL provide streamlined sensor onboarding, actionable empty-state search controls, and default discovery auto-pairing across `ControlTrackingScreen.kt`, `TrackingTabsScreen.kt`, `DeviceListScreen.kt`, `DevicesTabbedScreen.kt`, `DeviceManager.java`, and `ANTSearchForNewDevicesEngineMultiDeviceSearch.java` with 9-language localization parity (`REQ-SEN-020`):

1. **Start Screen Onboarding Entry Point (`REQ-SEN-020.1`)**:
   - In `ControlTrackingScreen.kt`, `ControlTrackingScreen` SHALL accept parameter `onPairSensor: () -> Unit = {}`.
   - When `devices.isEmpty()`, `!showResearchButton` (`hasPairedRemoteDevices == false`), and `searchingFor == null`, the top sensor slot SHALL render a compact, prominent `OutlinedButton` displaying icon `Icons.Default.Add` and text `stringResource(R.string.onboarding_pair_sensor_action)`.
   - Tapping this button SHALL invoke `onPairSensor()`.
   - In `TrackingTabsScreen.kt`, `onPairSensor` SHALL execute `(activity as? MainActivityWithNavigation)?.navigateToDrawerItem(R.id.drawer_my_sensors)`.
   - When at least one remote sensor is paired (`hasPairedRemoteDevices == true`) or `devices.isNotEmpty()`, the onboarding button SHALL disappear, restoring the standard `RemoteDevices` / `ResearchButton` layout.
   - The *"My Stuff"* section in `AppNavigationDrawer.kt` SHALL remain `collapsible = true` and collapsed by default.
2. **Actionable Empty State in "My Sensors" & Comprehensive Scan Lifecycle Guard (`REQ-SEN-020.2`)**:
   - In `DeviceListScreen.kt`, `DeviceListScreen` SHALL accept parameter `onScanForSensors: (() -> Unit)? = null`.
   - When `devices.isEmpty()`, `!isSearching`, and `filterCriteria.isEmpty`:
     - `EmptyStatePlaceholder`'s `hint` parameter SHALL display `stringResource(R.string.devices_empty_state_search_hint)`.
     - `EmptyStatePlaceholder`'s `content` slot SHALL render an `OutlinedButton` displaying icon `Icons.Default.Add` and text `stringResource(R.string.devices_action_search_sensors)` invoking `onScanForSensors`.
   - In `DevicesTabbedScreen.kt`, `onScanForSensors` SHALL be wired to `{ showProtocolSheet = true }` across Available, Paired, and All Known tabs.
   - **Explicit Scan Lifecycle Trigger & Timeout Guard**:
     The `isSearching` scan state in `DevicesTabbedViewModel` SHALL deterministically transition between states:
     - *Start Search*: Triggered by protocol selection, setting `isSearching = true` and displaying `ScanningIndicatorCard`.
     - *Search Stopped Trigger Mechanism*: The transition of `isSearching` back to `false` SHALL be triggered by:
       (a) *Engine Completion / Timeout*: `onSearchStopped()` callbacks from `ANTSearchForNewDevicesEngineMultiDeviceSearch` and `BTSearchForNewDevicesEngine` when the scan cycle finishes or times out.
       (b) *Manual User Cancellation*: Explicit dismissal or manual stop via `tabViewModel.stopSearching()`.
       (c) *Lifecycle Navigation Teardown*: `DisposableEffect(Unit)` in `DevicesTabbedScreen` invoking `tabViewModel.stopSearching()` on `onDispose`, ensuring that if the user navigates away during an active scan, the scan is safely aborted and `isSearching` is reset to `false`.
     - When `isSearching` transitions to `false` without finding devices (`devices.isEmpty()`), the screen immediately and gracefully restores the actionable empty state.
3. **Safe Auto-Pairing for Explicitly Scanned Sensors, UI State Flow & Immediate Unpair Safety (`REQ-SEN-020.3`)**:
   - Auto-pairing is strictly bounded to explicit, user-initiated search sessions (triggered via `PairingProtocolBottomSheet` or FAB); the app does not run background discovery sniffing.
   - In `DeviceManager.java` (`mBTSearchInterface.onNewDeviceFound`): when an explicitly scanned Bluetooth LE sensor is not in the database (`!mDevicesDatabaseManager.isBluetoothDeviceInDB`), it SHALL be inserted with `paired = true` (`insertNewBluetoothDeviceIntoDB(deviceType, BluetoothMACAddress, name, manufacturer, batteryPercentage, true)`).
   - In `DeviceManager.java` (`mANTAsyncSearchEngineInterface.onNewDeviceFound`): when an explicitly scanned ANT+ sensor is not in the database (`!mDevicesDatabaseManager.isANTDeviceInDB`), it SHALL be inserted with `paired = true` (`insertNewAntDeviceIntoDB(deviceType, deviceFound.getDeviceDisplayName(), deviceFound.getAntDeviceNumber(), true, manufacturer, batteryPercentage)`).
   - In `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`: `pairingRecommendation` for newly discovered sensors SHALL default to `true`.
   - **Synchronization Boundary & Reactive Flow Propagation**:
     In `DeviceManager.java`, database insertion and state notification SHALL be synchronized on `mDevicesDatabaseManager`. When `deviceId > 0` is confirmed, `pairingChanged(deviceId, true)` SHALL be called. The `DeviceDataRepository.devicesFlow` / `DevicesTabbedViewModel` observing database changes SHALL emit the updated device list with `isPaired == true` to the Compose UI layer atomically, preventing race conditions or ghost rendering.
   - **User Reversal & Safety Invariant**:
     Every auto-paired device SHALL display its discovery notification via the existing discovery snackbar (`sendNewDeviceFoundBroadcast`) and SHALL present an immediate unpair toggle switch (`DeviceItem.kt` `onPairClick`) and delete action (`onLongClick`). If a user toggles pairing to off, `pairingChanged(deviceId, false)` is invoked immediately and `paired = 0` is persisted in SQLite.
   - **Workout Session Safety**:
     `pairingChanged(deviceId, true)` initializes remote device instances safely without corrupting or restarting active workout recordings; telemetry validation in `FilteredSensorData` ensures zero NaN or spike emissions.
4. **9-Language Localization Parity (`REQ-SEN-020.4`)**:
   - All 3 new strings SHALL exist across all 9 supported application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`):
     - `onboarding_pair_sensor_action`
     - `devices_empty_state_search_hint`
     - `devices_action_search_sensors`

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-SEN-020` (*Streamlined Sensor Onboarding, Empty-State Action & Discovery Auto-Pairing*), extending `REQ-UI-301` / `REQ-UI-393`. Targets `ControlTrackingScreen.kt`, `TrackingTabsScreen.kt`, `DeviceListScreen.kt`, `DevicesTabbedScreen.kt`, `DeviceManager.java`, and `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`.
* **Historical Origin & Commit Trace**: Ticket `ATT-4051`, Sprint `2026-42.10`.
* **Root Reason for Existing Formulation**: `ATT-3692` cleanly removed dead pairing dialogs from the tracking cockpit to keep it focused on workouts. However, it left first-time users without any sensor onboarding action when starting from scratch with 0 paired sensors.
* **Preservation of Core Invariants**:
  - `ControlTrackingScreen` remains lean and does not duplicate protocol picker dialogs; it merely navigates to *"My Sensors"*.
  - Symmetrical layout balancing in `ControlTrackingScreen` is strictly preserved when devices are present.
  - Manual unpairing and individual sensor deletion in `DeviceItem.kt` remain 100% intact.
  - "My Stuff" drawer section remains collapsed by default.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Start Screen Onboarding)**:
  * *Given* a fresh install or state with zero paired sensors (`hasPairedRemoteDevices == false`),
  * *When* viewing `ControlTrackingScreen`,
  * *Then* the "+ Pair Sensor" button is rendered in the top sensor slot, and tapping it navigates to *"My Sensors"* (`R.id.drawer_my_sensors`).
* **Criterion 2 (Transition to Standard Sensor Header)**:
  * *Given* at least one sensor is paired (`hasPairedRemoteDevices == true`),
  * *When* viewing `ControlTrackingScreen`,
  * *Then* the "+ Pair Sensor" button is not rendered, and the standard `RemoteDevices` row is displayed.
* **Criterion 3 (Actionable Empty State in My Sensors)**:
  * *Given* no sensors are listed in `DeviceListScreen` and no scan is running (`!isSearching`),
  * *When* the empty state is displayed,
  * *Then* the hint and "+ Search for Sensors" button are rendered, and tapping the button opens `PairingProtocolBottomSheet`.
* **Criterion 4 (Scan Timeout, Manual Stop & Navigation Teardown Guard)**:
  * *Given* a scan is running with no devices found yet,
  * *When* the scan completes via timeout, manual stop, or screen navigation disposal (`onSearchStopped()` / `onDispose`),
  * *Then* `isSearching` transitions to `false` and the empty-state action button is immediately and cleanly restored without ghost states.
* **Criterion 5 (Discovery Auto-Pairing & Instant Unpair)**:
  * *Given* an active scan discovering a new Bluetooth LE or ANT+ device,
  * *When* the device is inserted into `DevicesDatabaseManager`,
  * *Then* `paired` is stored as `1` (`true`), `pairingChanged(deviceId, true)` is called, the reactive Flow emits to the UI, and the device is automatically ready.
  * *Given* an auto-paired device in `DeviceListScreen`,
  * *When* the user toggles the pair switch to OFF,
  * *Then* `pairingChanged(deviceId, false)` is dispatched and `paired` is persisted as `0`.
* **Criterion 6 (Active Workout Session Immunity)**:
  * *Given* an active workout tracking session in `TrackerService`,
  * *When* a new sensor is auto-paired during an on-demand scan,
  * *Then* the workout recording continues without interruption or reset, and new telemetry samples are validated through `FilteredSensorData`.

---

## 2. Test Specification (TST-SEN-020)

### 2.1 Test Suite Overview

| Test ID | Target Component | Test Scope | Verification Method |
| :--- | :--- | :--- | :--- |
| **TST-SEN-020.1** | `DevicesDatabaseManager.java` | Bluetooth & ANT+ discovery insertion defaults `paired = true` | Unit test in `DevicesDatabaseManagerTest.kt` |
| **TST-SEN-020.2** | `DeviceManager.java` | Synchronized insertion, `pairingChanged(deviceId, true)`, and Flow dispatch | Unit test in `DeviceManagerDiscoveryTest.kt` |
| **TST-SEN-020.3** | `ControlTrackingScreen.kt` | Onboarding button visibility logic when 0 sensors vs. $\ge 1$ sensor | Unit / Layout test in `ControlTrackingScreenTest.kt` |
| **TST-SEN-020.4** | `DeviceListScreen.kt` | Empty-state search button visibility when idle vs. scanning | Unit / Layout test in `DeviceListScreenTest.kt` |
| **TST-SEN-020.5** | `res/values*/strings.xml` | 9-Language localization parity for all 3 onboarding strings | Unit test in `SensorOnboardingLocalizationTest.kt` |
| **TST-SEN-020.6** | Full Test Suite | Clean-room full-suite regression | `./gradlew testDebugUnitTest` |
| **TST-SEN-020.7** | `DeviceListViewModel.kt` | User unpair toggle on auto-paired device sets `paired = false` | Unit test in `DeviceListViewModelTest.kt` |
| **TST-SEN-020.8** | `DevicesTabbedViewModel.kt` | Navigation disposal during active scan invokes `stopSearching()` and resets `isSearching` | Unit test in `DevicesTabbedViewModelTest.kt` |

### 2.2 Traceability Matrix

| Requirement | Test ID | Description |
| :--- | :--- | :--- |
| `REQ-SEN-020.1` | `TST-SEN-020.3` | Start screen "+ Pair Sensor" button visibility & navigation |
| `REQ-SEN-020.2` | `TST-SEN-020.4`, `TST-SEN-020.8` | Actionable empty state in My Sensors, scan timeout, and navigation reset |
| `REQ-SEN-020.3` | `TST-SEN-020.1`, `TST-SEN-020.2`, `TST-SEN-020.7` | Auto-pair newly discovered sensors, synchronization, and unpair safety |
| `REQ-SEN-020.4` | `TST-SEN-020.5` | 9-language localization completeness and non-blank check |
| System Regression | `TST-SEN-020.6` | 100% green test suite across 3169 unit tests |
