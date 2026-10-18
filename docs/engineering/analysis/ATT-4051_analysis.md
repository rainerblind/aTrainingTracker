# Stage 1 Analysis: ATT-4051 - Show "+ Pair Sensor" onboarding action when no sensors are paired, add empty-state search action, and auto-pair newly discovered sensors by default

**Ticket**: [ATT-4051](https://rainerblind.atlassian.net/browse/ATT-4051)  
**Sub-task**: [ATT-4116](https://rainerblind.atlassian.net/browse/ATT-4116) (`[Analysis]`)  
**Parent Epic**: [ATT-2617](https://rainerblind.atlassian.net/browse/ATT-2617) (*Optimize first impression*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Branch**: `feature/ATT-4051`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Statement & Motivation

On a fresh installation (or whenever no remote sensors are paired, `hasPairedRemoteDevices == false`), a user experiences significant usability friction across the sensor setup lifecycle:

1. **Blank Sensor Area on Start Screen**:
   In `ControlTrackingScreen.kt:287-332` (invoked by `TrackingTabsScreen.kt:756` with `showResearchButton = hasPairedRemoteDevices`), when no remote sensors are paired and `devices.isEmpty()`, the entire top sensor section is completely blank. The user receives no visual cue, call-to-action, or direct onboarding affordance to pair their first sensor.
2. **Hidden Drawer Navigation**:
   In `AppNavigationDrawer.kt:205-213`, *"My Sensors"* (`R.id.drawer_my_sensors`) is situated inside the *"My Stuff"* drawer section, which is collapsed by default. A new user must manually discover and expand this drawer group to find sensor setup.
3. **Passive Empty State in "My Sensors"**:
   Once inside *"My Sensors"* (`DevicesTabbedScreen.kt` / `DeviceListScreen.kt:103-122`), if no sensors are listed in the Available or Paired tabs, `EmptyStatePlaceholder` renders a static message with no hint explaining how to scan and no actionable button. The user must notice the floating action button (FAB) in the lower corner.
4. **Unpaired Default on Sensor Discovery**:
   When a scan discovers a new Bluetooth LE or ANT+ sensor, `DeviceManager.java` inserts the device with `paired = false` by default (`insertNewBluetoothDeviceIntoDB(..., false)` at line 232, and `insertNewAntDeviceIntoDB(..., pairingRecommendation, ...)` at line 185 where `pairingRecommendation` is false for new devices). The user is required to realize they must manually flip the pairing switch before the newly found sensor will connect and provide telemetry during workouts.

---

## 2. Root Cause Analysis (Forensic Investigation)

1. **Start Screen Sensor Header Branching (`ControlTrackingScreen.kt`)**:
   ```kotlin
   if (devices.isNotEmpty()) {
       // renders RemoteDevices and optional ResearchButton
   } else if (showResearchButton) {
       // renders centered ResearchButton
   }
   ```
   When `devices.isEmpty()` and `showResearchButton == false` (derived from `hasPairedRemoteDevices`), execution falls through both branches, leaving the top slot completely empty and devoid of any onboarding affordance.
2. **Navigation Drawer Layout (`AppNavigationDrawer.kt`)**:
   The *"My Stuff"* drawer group is explicitly configured with `collapsible = true` (and defaults to collapsed state). While intentional to avoid overwhelming the drawer, it prevents new users from discovering sensor management without an explicit entry point on the primary Start screen.
3. **EmptyStatePlaceholder Usage in `DeviceListScreen.kt`**:
   `DeviceListScreen.kt:114-122` renders:
   ```kotlin
   EmptyStatePlaceholder(
       modifier = Modifier...,
       icon = Icons.Default.Devices,
       message = emptyMessage,
       hint = ""
   )
   ```
   `EmptyStatePlaceholder` supports an actionable `content: @Composable ColumnScope.() -> Unit`, but `DeviceListScreen` passes empty content and leaves `hint = ""` empty, leaving the screen passive.
4. **Discovery Auto-Pairing Flag & Concurrency (`DeviceManager.java`)**:
   - For Bluetooth LE (`DeviceManager.java:232`):
     `deviceId = mDevicesDatabaseManager.insertNewBluetoothDeviceIntoDB(deviceType, BluetoothMACAddress, name, manufacturer, batteryPercentage, false);`
     The boolean argument `isPaired` is hardcoded to `false`, and `pairingChanged(deviceId, true)` is never called.
   - For ANT+ (`DeviceManager.java:185` and `ANTSearchForNewDevicesEngineMultiDeviceSearch.java:87`):
     `pairingRecommendation` is computed as `deviceFound.isPreferredDevice() || deviceFound.isAlreadyConnected()`. For previously unseen devices during a discovery scan, both conditions are `false`, causing the device to be stored as unpaired (`paired = false`).
   - *Database Insertion and State Propagation Order*:
     When `insertNewBluetoothDeviceIntoDB` / `insertNewAntDeviceIntoDB` is executed, SQLite insertion occurs synchronously on the database helper. However, to guarantee absolute read-consistency for the downstream `pairingChanged(deviceId, true)` callback (which immediately queries `devicesDatabaseManager.getDeviceData(deviceId)` in `createRemoteDevice`), the insertion must be fully committed and confirmed before `pairingChanged` and `sendNewDeviceFoundBroadcast` are dispatched.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * **Start Screen Onboarding Entry Point (`ControlTrackingScreen.kt`, `TrackingTabsScreen.kt`)**:
    When `!hasPairedRemoteDevices && devices.isEmpty() && searchingFor == null`, display a compact, prominent `OutlinedButton` (*"+ Pair Sensor"* / *"+ Sensor koppeln"*) in the top sensor header slot. Tapping this button invokes `onPairSensor`, which navigates to *"My Sensors"* via `(activity as? MainActivityWithNavigation)?.navigateToDrawerItem(R.id.drawer_my_sensors)`.
    Once at least one sensor is paired (`hasPairedRemoteDevices == true`), this onboarding button automatically disappears and the standard `RemoteDevices` / `ResearchButton` row takes over.
  * **Actionable Empty State in "My Sensors" (`DeviceListScreen.kt`, `DevicesTabbedScreen.kt`)**:
    When `devices.isEmpty()`, `!isSearching`, and `filterCriteria.isEmpty`, display a localized hint on `EmptyStatePlaceholder` explaining how to scan for sensors, and render a direct action button (*"+ Search for Sensors"* / *"+ Sensoren suchen"*) that opens `PairingProtocolBottomSheet` (matching the FAB action).
  * **State Guard for Scan Lifecycle (Timeout & Failure Handling)**:
    Define explicit state transitions:
    - *IDLE (empty)*: `isSearching == false`, `devices.isEmpty()` $\rightarrow$ Show EmptyStatePlaceholder with "+ Search for Sensors" action button.
    - *SCANNING*: `isSearching == true` $\rightarrow$ Show scanning indicator / progress card; hide empty-state button to prevent duplicate scan launches.
    - *SCAN TIMEOUT / NO DEVICES FOUND*: `isSearching` transitions from `true` back to `false` via `onSearchStopped()`. Because `devices.isEmpty()` remains true, the screen immediately reverts to the actionable empty state, keeping the "+ Search for Sensors" button accessible.
    - *SCAN FOUND DEVICES*: `devices.isNotEmpty()` $\rightarrow$ Transitions to device list with items automatically paired.
  * **Safe Auto-Pairing for Newly Discovered Sensors (`DeviceManager.java`, `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`)**:
    * In `DeviceManager.mBTSearchInterface.onNewDeviceFound`: insert new Bluetooth LE devices with `paired = true` (`insertNewBluetoothDeviceIntoDB(..., true)`). When `deviceId > 0` is returned from SQLite, invoke `pairingChanged(deviceId, true)` strictly after ensuring the device record is committed, followed by `sendNewDeviceFoundBroadcast(deviceId)`.
    * In `DeviceManager.mANTAsyncSearchEngineInterface.onNewDeviceFound`: insert new ANT+ devices with `paired = true` and call `pairingChanged(deviceId, true)` when `deviceId > 0`.
    * In `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`: default `pairingRecommendation = true` for newly discovered sensors.
  * **9-Language Localization Parity**:
    Provide localized strings across all 9 languages (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) for:
    - `onboarding_pair_sensor_action`: "+ Pair Sensor" / "+ Sensor koppeln"
    - `devices_empty_state_search_hint`: "Scan for nearby Bluetooth LE or ANT+ sensors" / "Suche nach Bluetooth-LE- oder ANT+-Sensoren in der Nähe"
    - `devices_action_search_sensors`: "+ Search for Sensors" / "+ Sensoren suchen"
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying the collapsed state of *"My Stuff"* in `AppNavigationDrawer.kt` (remains collapsed by default).
  * Altering pairing states of already known/existing devices in `DevicesDatabaseManager`.
  * Modifying sensor disconnect or reconnect policies in `BANALService`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

1. **Requirement ID & Target**: Net-new requirement `REQ-SEN-020` (*Streamlined Sensor Onboarding, Empty-State Action & Discovery Auto-Pairing*), verified by `TST-SEN-020`.
2. **Historical Origin & Commit Trace**:
   - `REQ-UI-301` / `REQ-UI-393` (Sprint `2026-41.4`, `ATT-3692`): Cleaned up dead pairing dialogs from `ControlTrackingScreen` to keep it clean and focused on workout tracking, routing pairing workflows through *"My Sensors"*.
   - `ATT-4051` (Sprint `2026-42.10`): Bridges the first-run onboarding gap by providing a direct navigation affordance from `ControlTrackingScreen` to *"My Sensors"* and making sensor discovery instantly actionable.
3. **Preservation of Core Invariants**:
   - Preserves `REQ-UI-301` / `REQ-UI-393`: `ControlTrackingScreen` remains clean and free of redundant protocol dialogs; it merely delegates navigation to `MainActivityWithNavigation.navigateToDrawerItem(R.id.drawer_my_sensors)`.
   - Preserves symmetrical balancing in `ControlTrackingScreen` when devices are present.
   - Preserves manual unpairing capability via `DeviceItem` switch and `onPairedChanged`.

---

## 5. Architectural Strategy & High-Level Solution

1. **`ControlTrackingScreen.kt` & `TrackingTabsScreen.kt`**:
   - Add parameter `onPairSensor: () -> Unit = {}` to `ControlTrackingScreen`.
   - In `ControlTrackingScreen`, when `devices.isEmpty()`, `!showResearchButton`, and `searchingFor == null`: render `OutlinedButton` with `Icons.Default.Add` and `stringResource(R.string.onboarding_pair_sensor_action)`.
   - In `TrackingTabsScreen.kt`, pass `onPairSensor = { (activity as? MainActivityWithNavigation)?.navigateToDrawerItem(R.id.drawer_my_sensors) }`.
2. **`DeviceListScreen.kt` & `DevicesTabbedScreen.kt`**:
   - Add parameter `onScanForSensors: (() -> Unit)? = null` to `DeviceListScreen`.
   - In `DeviceListScreen`, when `devices.isEmpty() && !isSearching && filterCriteria.isEmpty`, pass `hint = stringResource(R.string.devices_empty_state_search_hint)` and a content block rendering an `OutlinedButton` with `Icons.Default.Add` and `stringResource(R.string.devices_action_search_sensors)` invoking `onScanForSensors`.
   - When scan is in progress or filters are active, the empty-state action button is suppressed. When scan completes or times out without finding devices, `isSearching` becomes `false`, gracefully restoring the button.
   - In `DevicesTabbedScreen.kt`, wire `onScanForSensors = { showProtocolSheet = true }` for tabs 0, 1, and 2.
3. **`DeviceManager.java` & `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`**:
   - In `mBTSearchInterface.onNewDeviceFound`:
     ```java
     synchronized (mDevicesDatabaseManager) {
         if (!mDevicesDatabaseManager.isBluetoothDeviceInDB(deviceType, BluetoothMACAddress)) {
             deviceId = mDevicesDatabaseManager.insertNewBluetoothDeviceIntoDB(deviceType, BluetoothMACAddress, name, manufacturer, batteryPercentage, true);
             if (deviceId > 0) {
                 pairingChanged(deviceId, true);
             }
         } else {
             deviceId = mDevicesDatabaseManager.getDeviceId(deviceType, BluetoothMACAddress);
         }
         mNewlyFoundDevices.add(deviceId);
         sendNewDeviceFoundBroadcast(deviceId);
     }
     ```
   - In `mANTAsyncSearchEngineInterface.onNewDeviceFound`:
     ```java
     synchronized (mDevicesDatabaseManager) {
         if (!mDevicesDatabaseManager.isANTDeviceInDB(deviceType, deviceFound.getAntDeviceNumber())) {
             deviceId = mDevicesDatabaseManager.insertNewAntDeviceIntoDB(deviceType, deviceFound.getDeviceDisplayName(), deviceFound.getAntDeviceNumber(), true, manufacturer, batteryPercentage);
             if (deviceId > 0) {
                 pairingChanged(deviceId, true);
             }
         } else {
             ...
         }
         mNewlyFoundDevices.add(deviceId);
         sendNewDeviceFoundBroadcast(deviceId);
     }
     ```
   - In `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`:
     `boolean pairingRecommendation = true;`
4. **Targeted Automated Tests**:
   - In `DevicesDatabaseManagerTest.kt`: verify that `insertNewBluetoothDeviceIntoDB(..., paired = true)` persists `paired == 1` in the database, and that newly inserted ANT devices default to `paired == 1`.
   - In `ControlTrackingScreenLayoutTest.kt` / `SingleActivityNavigationTest.kt`: verify that when `hasPairedRemoteDevices == false` and `devices.isEmpty()`, `onboarding_pair_sensor_action` is displayed and dispatches navigation to `R.id.drawer_my_sensors`.
   - In `DeviceListScreenTest.kt` / `DevicesTabbedScreenTest.kt`: verify empty state displays search hint and button when idle, and reverts cleanly after scan completion.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. 100% test pass rate across all 3,169 clean-room unit tests.
  2. "My Stuff" drawer group remains collapsed by default.
  3. Existing paired devices and pairing toggle functionality in `DeviceItem.kt` remain 100% intact.
  4. Symmetrical visual centering and layout stability of `ControlTrackingScreen` preserved.
  5. Parent ticket Human Decision Gate remains strictly enforced (`Analysis` -> `Test Spec` -> `Impl-Plan` -> `Implementation` -> `Final Review (Human)`).
* **Risk Rating**: **LOW**
  - All changes are additive UI conveniences and sensor state initialization improvements.
  - Zero database schema migrations required (`DevicesDatabaseManager` already supports `paired = true/false`).
