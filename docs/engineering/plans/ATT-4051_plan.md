# Stage 3: Implementation Plan - ATT-4051: Show "+ Pair Sensor" onboarding action when no sensors are paired, add empty-state search action, and auto-pair newly discovered sensors by default

**Ticket**: [ATT-4051](https://rainerblind.atlassian.net/browse/ATT-4051)  
**Sub-task**: [ATT-4118](https://rainerblind.atlassian.net/browse/ATT-4118) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2617](https://rainerblind.atlassian.net/browse/ATT-2617) (*Optimize first impression*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-SEN-020` (*Streamlined Sensor Onboarding, Empty-State Action & Discovery Auto-Pairing*)  
**Test Mapping**: `TST-SEN-020` (`TST-SEN-020.1` to `TST-SEN-020.11`)  
**Branch**: `feature/ATT-4051`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Background

On a fresh installation (or whenever no remote sensors are paired, `hasPairedRemoteDevices == false`), new users face three critical usability hurdles:
1. The start tracking cockpit (`ControlTrackingScreen.kt`) renders a completely blank sensor header slot when no devices are paired, leaving no visual clue or direct path to pair a sensor.
2. In `AppNavigationDrawer.kt`, "My Sensors" (`R.id.drawer_my_sensors`) is tucked away inside the "My Stuff" section, which is collapsed by default.
3. Once in "My Sensors", the empty state provides no explanation or action button to start scanning, and when a user explicitly initiates a scan and discovers a sensor, it is stored as `paired = false` by default, requiring an unintuitive second step (manually locating and flipping a switch) before the sensor can actually be used.

This plan details the atomic implementation steps to deliver a prominent *"+ Pair Sensor"* onboarding action on the Start screen, an actionable empty state in *"My Sensors"* with robust lifecycle reset handling, and default auto-pairing for discovered sensors with immediate unpair safety.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-SEN-020` (*Streamlined Sensor Onboarding, Empty-State Action & Discovery Auto-Pairing*)
* **Test Mapping**: `TST-SEN-020` (`TST-SEN-020.1` to `TST-SEN-020.11`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: All 3,169 existing clean-room unit tests continue to pass cleanly.
2. **Drawer Collapse Invariant**: The *"My Stuff"* group in `AppNavigationDrawer.kt` remains `collapsible = true` and collapsed by default.
3. **Control Cockpit Cleanliness & Layout Balance**: `ControlTrackingScreen` renders the "+ Pair Sensor" onboarding button in a dedicated centered `Row` slot strictly when `devices.isEmpty() && !showResearchButton && searchingFor == null`. When `devices.isNotEmpty()` or `showResearchButton == true`, the sensor header layout structure, `ResearchButton`, and invisible symmetrical balancing anchor (`alpha(0f)`) remain 100% unaltered.
4. **Symmetrical Layout Invariant**: Symmetrical balancing in `ControlTrackingScreen` is strictly preserved when devices are present. Contract test `ControlTrackingSensorHeaderContractTest` and snapshot test verify zero off-center drift.
5. **User Reversal & Immediate Reactive State Invariant**: Every auto-paired device displays its discovery notification via the existing discovery snackbar (`sendNewDeviceFoundBroadcast`) and presents an immediate unpair toggle switch (`DeviceItem.kt` `onPairClick`) and delete action (`onLongClick`). `DeviceListViewModel` observes `GetMergedDevicesUseCase` (`DeviceDataRepository.allDevices` + `BANALServiceRepository.allActiveDevicesTelemetry` + `newlyFoundDevicesIds`), ensuring that when `sendNewDeviceFoundBroadcast` triggers `DeviceDataRepository.handleNewDeviceFound()`, the UI list reactively reflects the newly paired state before user navigation.
6. **Lock Hierarchy & Concurrency Safety Invariant**: In `DeviceManager.java`, to avoid re-entrancy deadlocks, the lock hierarchy is strictly defined:
   - Level 1: `DeviceManager` internal lock (no synchronization on `mDevicesDatabaseManager` while holding other locks).
   - Level 2: `DevicesDatabaseManager` lock (`synchronized (mDevicesDatabaseManager)`).
   - `mDevicesDatabaseManager` methods execute SQLite operations without callbacks into `DeviceManager`.
   - `pairingChanged(deviceId, true)` is called OUTSIDE the `synchronized (mDevicesDatabaseManager)` block. This completely eliminates lock inversion and deadlocks.
7. **Active Workout Immunity Invariant**: In `DeviceManager.java`, before calling `pairingChanged(deviceId, true)`, verify whether a workout is currently active via `TrainingApplication.getTrackingMode() == TrackingMode.TRACKING`. If a workout is actively recording, the device is inserted into the database as `paired = true`, but `pairingChanged(deviceId, true)` (which attempts to queue or start immediate search for the new device) is deferred, preventing connection churn, sensor re-allocation, or interruptions to ongoing workout telemetry.
8. **Race Condition Mitigation & Direct State Check**: In addition to observing `TrainingApplication.TRACKING_STATE_CHANGED` via broadcast receiver, `DeviceManager` explicitly checks `TrainingApplication.getTrackingMode()` directly before initiating connection search (`startSearchForPairedDevices()`) to avoid any asynchronous broadcast delivery latency race.
9. **Database Insertion Failure Resilience**: If `insertNewBluetoothDeviceIntoDB` or `insertNewAntDeviceIntoDB` returns `deviceId <= 0` (e.g. SQLite insertion failure or constraint violation), the device ID MUST NOT be added to `mNewlyFoundDevices`, `pairingChanged(deviceId, true)` MUST NOT be invoked, and no broadcast shall be sent, preventing phantom/ghost sensor entries.
10. **Manual Override Invariant (Conflict Resolution)**: If a user explicitly unpairs a sensor during an active workout (e.g. flipping the toggle in "My Sensors"), the database updates `paired = 0`. When tracking terminates and `startSearchForPairedDevices()` runs post-workout, `createPairedRemoteDevices` queries `Devices WHERE paired > 0`, so the explicitly unpaired sensor is NOT re-queued or connected. Manual unpairing strictly overrides auto-pairing re-queueing logic.
11. **9-Language Localization Parity**: All 3 new UI strings exist across `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, and `values-pt`.

---

## 4. UI Consistency (Rule 23)

* **Reference Screens**:
  - `ControlTrackingScreen.kt` (Start tracking cockpit)
  - `EmptyStatePlaceholder.kt` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/EmptyStatePlaceholder.kt`)
  - `DevicesTabbedScreen.kt` (`app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreen.kt`)
* **Reused Components**:
  - `EmptyStatePlaceholder`: standard component with `icon`, `message`, `hint`, and `content: @Composable ColumnScope.() -> Unit`.
  - `OutlinedButton`: Material 3 standard button with icon and label for high-visibility primary actions without overwhelming background canvas.
  - `Icons.Default.Add`: standard AndroidX Compose Material icon for addition/creation affordances.
* **Theme Tokens & Spacing**:
  - Border and icon: `MaterialTheme.colorScheme.outline` and `MaterialTheme.colorScheme.primary`.
  - Typography: `MaterialTheme.typography.labelLarge`.
  - Spacing: `horizontal = 16.dp`, `padding(bottom = 8.dp)`, button icon size `18.dp`, icon-to-text spacer `8.dp`.

---

## 5. Proposed Architectural Changes (SWE.2)

### Component 1: Localization Layer (9 Locales)
Add 3 new strings to all 9 strings.xml files:
1. `onboarding_pair_sensor_action`:
   - `values`: `+ Pair Sensor`
   - `values-de`: `+ Sensor koppeln`
   - `values-es`: `+ Vincular sensor`
   - `values-fr`: `+ Associer un capteur`
   - `values-it`: `+ Associa sensore`
   - `values-ja`: `+ センサーをペアリング`
   - `values-nl`: `+ Sensor koppelen`
   - `values-pl`: `+ Sparuj czujnik`
   - `values-pt`: `+ Emparelhar sensor`
2. `devices_empty_state_search_hint`:
   - `values`: `Scan for nearby Bluetooth LE or ANT+ sensors`
   - `values-de`: `Suche nach Bluetooth-LE- oder ANT+-Sensoren in der Nähe`
   - `values-es`: `Buscar sensores Bluetooth LE o ANT+ cercanos`
   - `values-fr`: `Rechercher des capteurs Bluetooth LE ou ANT+ à proximité`
   - `values-it`: `Cerca sensori Bluetooth LE o ANT+ nelle vicinanze`
   - `values-ja`: `近くのBluetooth LEまたはANT+センサーをスキャン`
   - `values-nl`: `Zoeken naar Bluetooth LE- of ANT+-sensoren in de buurt`
   - `values-pl`: `Skanuj w poszukiwaniu czujników Bluetooth LE lub ANT+ w pobliżu`
   - `values-pt`: `Procurar sensores Bluetooth LE ou ANT+ próximos`
3. `devices_action_search_sensors`:
   - `values`: `+ Search for Sensors`
   - `values-de`: `+ Sensoren suchen`
   - `values-es`: `+ Buscar sensores`
   - `values-fr`: `+ Rechercher des capteurs`
   - `values-it`: `+ Cerca sensori`
   - `values-ja`: `+ センサーを検索`
   - `values-nl`: `+ Sensoren zoeken`
   - `values-pl`: `+ Szukaj czujników`
   - `values-pt`: `+ Procurar sensores`

### Component 2: Start Screen Onboarding (`ControlTrackingScreen.kt`, `TrackingTabsScreen.kt`)
- In `ControlTrackingScreen.kt`:
  - Add parameter `onPairSensor: () -> Unit = {}`.
  - In sensor header area, strictly preserve the `if (devices.isNotEmpty())` and `else if (showResearchButton)` branches:
    ```kotlin
    if (devices.isNotEmpty()) {
        // existing RemoteDevices + ResearchButton + invisible balancing anchor
    } else if (showResearchButton) {
        // existing centered ResearchButton
    } else if (searchingFor == null) {
        // Onboarding button when 0 sensors paired and not searching
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onPairSensor,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.onboarding_pair_sensor_action))
            }
        }
    }
    ```
  - This ensures zero shifting of existing `ResearchButton` or `RemoteDevices` elements when devices are present or when researching.
- In `TrackingTabsScreen.kt`:
  - Pass `onPairSensor = { (activity as? MainActivityWithNavigation)?.navigateToDrawerItem(R.id.drawer_my_sensors) }`.

### Component 3: "My Sensors" Actionable Empty State & Lifecycle Guard (`DeviceListScreen.kt`, `DevicesTabbedScreen.kt`)
- In `DeviceListScreen.kt`:
  - Add parameter `onScanForSensors: (() -> Unit)? = null`.
  - When `devices.isEmpty()`:
    ```kotlin
    val showSearchAction = !isSearching && filterCriteria.isEmpty
    val hintText = if (showSearchAction) {
        stringResource(R.string.devices_empty_state_search_hint)
    } else {
        ""
    }
    EmptyStatePlaceholder(
        modifier = Modifier
            .padding(top = placeholderPadding)
            .navigationBarsPadding()
            .weight(1f),
        icon = Icons.Default.Devices,
        message = emptyMessage,
        hint = hintText
    ) {
        if (showSearchAction && onScanForSensors != null) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onScanForSensors
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.devices_action_search_sensors))
            }
        }
    }
    ```
- In `DevicesTabbedScreen.kt`:
  - Pass `onScanForSensors = { showProtocolSheet = true }` to `DeviceListScreen` across tabs 0, 1, and 2.
  - Lifecycle Binding Guard:
    - `DisposableEffect(Unit)` ensures `tabViewModel.startSearching()` starts when tabs are displayed and `onDispose { tabViewModel.stopSearching(); showProtocolSheet = false; selectedPairingProtocol = null }` completely stops searching and cleans up any open sheet state upon navigation away, preventing any zombie scan state.

### Component 4: Discovery Auto-Pairing, Concurrency Synchronization, Workout Immunity & Post-Workout Re-queueing (`DeviceManager.java`, `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`)
- Lock Hierarchy & Execution Sequence:
  1. Atomically evaluate tracking mode `isWorkoutActive = (TrainingApplication.getTrackingMode() == TrackingMode.TRACKING)` INSIDE the `synchronized (mDevicesDatabaseManager)` block together with existence check, database insertion (`paired = true`), and registration in `mNewlyFoundDevices.add(deviceId)`.
  2. Release `mDevicesDatabaseManager` monitor before calling `pairingChanged(deviceId, true)`.
  3. When `!isWorkoutActive`, invoke `pairingChanged(deviceId, true)` to queue connection search. If `isWorkoutActive`, suppress immediate `pairingChanged(deviceId, true)` to protect active workout recording from telemetry disruption or reconnect churn (the device is safely persisted with `paired = true` in SQLite).
  4. Post-Workout Re-queueing: `DeviceManager` registers a `BroadcastReceiver` for `TrainingApplication.TRACKING_STATE_CHANGED`. When tracking finishes (transitions from `TRACKING` to `STOPPED` or `READY`), `DeviceManager` checks `startSearchForPairedDevices()`, seamlessly initiating connection search for any newly auto-paired devices once workout recording has terminated.
  5. Dispatch `sendNewDeviceFoundBroadcast(deviceId)`. The broadcast triggers `DeviceDataRepository.handleNewDeviceFound()`, which reloads `allDevices` `StateFlow` so `DeviceListViewModel` immediately emits the paired device to the UI.
- In `DeviceManager.java`:
  - In `mBTSearchInterface.onNewDeviceFound`:
    ```java
    long deviceId = -1;
    boolean isNew = false;
    boolean isWorkoutActive = false;
    synchronized (mDevicesDatabaseManager) {
        isWorkoutActive = (TrainingApplication.getTrackingMode() == TrackingMode.TRACKING);
        if (!mDevicesDatabaseManager.isBluetoothDeviceInDB(deviceType, BluetoothMACAddress)) {
            if (DEBUG) Log.d(TAG, "device is not yet in the database, so we have to add it");
            deviceId = mDevicesDatabaseManager.insertNewBluetoothDeviceIntoDB(deviceType, BluetoothMACAddress, name, manufacturer, batteryPercentage, true);
            isNew = true;
        } else {
            if (DEBUG) Log.d(TAG, "Device already in database, so we do not add it");
            deviceId = mDevicesDatabaseManager.getDeviceId(deviceType, BluetoothMACAddress);
        }
        if (deviceId > 0) {
            mNewlyFoundDevices.add(deviceId);
        } else {
            Log.w(TAG, "Failed to insert or locate Bluetooth device in DB: " + BluetoothMACAddress);
        }
    }
    if (deviceId > 0) {
        if (isNew && !isWorkoutActive) {
            pairingChanged(deviceId, true);
        }
        sendNewDeviceFoundBroadcast(deviceId);
    }
    ```
  - In `mANTAsyncSearchEngineInterface.onNewDeviceFound`:
    ```java
    long deviceId = -1;
    boolean isNew = false;
    boolean isWorkoutActive = false;
    synchronized (mDevicesDatabaseManager) {
        isWorkoutActive = (TrainingApplication.getTrackingMode() == TrackingMode.TRACKING);
        if (!mDevicesDatabaseManager.isANTDeviceInDB(deviceType, deviceFound.getAntDeviceNumber())) {
            if (DEBUG) Log.d(TAG, "device is not yet in the database, so we have to add it to the database");
            deviceId = mDevicesDatabaseManager.insertNewAntDeviceIntoDB(deviceType, deviceFound.getDeviceDisplayName(), deviceFound.getAntDeviceNumber(), true, manufacturer, batteryPercentage);
            isNew = true;
        } else {
            if (DEBUG) Log.i(TAG, "Device already in database, so we do not add it to the database");
            deviceId = mDevicesDatabaseManager.getDeviceId(deviceType, deviceFound.getAntDeviceNumber());
            if (manufacturer != null) {
                mDevicesDatabaseManager.setManufacturerName(deviceId, manufacturer);
            }
            if (batteryPercentage >= 0) {
                mDevicesDatabaseManager.setBatteryPercentage(deviceId, batteryPercentage);
            }
        }
        if (deviceId > 0) {
            mNewlyFoundDevices.add(deviceId);
        } else {
            Log.w(TAG, "Failed to insert or locate ANT device in DB: " + deviceFound.getAntDeviceNumber());
        }
    }
    if (deviceId > 0) {
        if (isNew && !isWorkoutActive) {
            pairingChanged(deviceId, true);
        }
        sendNewDeviceFoundBroadcast(deviceId);
    }
    ```
  - Tracking State Receiver for Post-Workout Re-queueing with Direct State Check:
    ```java
    private final BroadcastReceiver mTrackingStateChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            TrackingMode mode = TrainingApplication.getTrackingMode();
            if (mode == TrackingMode.READY || mode == TrackingMode.STOPPED) {
                // Direct state check protects against broadcast timing delays
                startSearchForPairedDevices();
            }
        }
    };
    ```
- In `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`:
  - In `myOnDeviceFound`:
    ```java
    boolean pairingRecommendation = true;
    ```

---

## 6. Step-by-Step Construction Plan (Atomic Steps)

### Step 1: Localization Parity across 9 Languages
* Target: `app/src/main/res/values*/strings.xml`.
* Actions: Add `onboarding_pair_sensor_action`, `devices_empty_state_search_hint`, `devices_action_search_sensors` across all 9 directories.
* Verification: Create and execute `SensorOnboardingLocalizationTest.kt` (`TST-SEN-020.5`).
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.SensorOnboardingLocalizationTest"`

### Step 2: Auto-Pairing on Discovery, Concurrency Lock Hierarchy, Workout Immunity & Post-Workout Re-queueing in DeviceManager
* Target: `DeviceManager.java`, `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`.
* Actions: Wrap discovery insertions and `mNewlyFoundDevices.add` in `synchronized (mDevicesDatabaseManager)`, query `isWorkoutActive` inside lock, set `paired = true`, release lock before calling `pairingChanged(deviceId, true)` when `deviceId > 0 && !isWorkoutActive`, register tracking state receiver with direct state check to re-queue search upon workout completion, guard `deviceId <= 0` insertion failures, and ensure manual unpair overrides post-workout search.
* Verification: Unit tests in `DevicesDatabaseManagerTest.kt` (`TST-SEN-020.1`), `DeviceManagerDiscoveryTest.kt` (`TST-SEN-020.2`), active workout immunity test (`TST-SEN-020.9`), database insertion failure test (`TST-SEN-020.10`), and manual unpair conflict resolution test (`TST-SEN-020.11`).
* Command: `./gradlew testDebugUnitTest --tests "*DevicesDatabaseManagerTest*" --tests "*DeviceManagerDiscoveryTest*"`

### Step 3: Actionable Empty State & Scan Lifecycle Guard in "My Sensors"
* Target: `DeviceListScreen.kt`, `DevicesTabbedScreen.kt`.
* Actions: Add `onScanForSensors` parameter, render hint and `OutlinedButton` when `!isSearching && filterCriteria.isEmpty`, wire `{ showProtocolSheet = true }`, and ensure sheet state teardown on dispose.
* Verification: Unit test in `DeviceListScreenTest.kt` (`TST-SEN-020.4`), `DeviceListViewModelTest.kt` (`TST-SEN-020.7`), and `DevicesTabbedViewModelTest.kt` (`TST-SEN-020.8`).
* Command: `./gradlew testDebugUnitTest --tests "*DeviceList*"`

### Step 4: Start Screen Onboarding Entry Point in Control Cockpit
* Target: `ControlTrackingScreen.kt`, `TrackingTabsScreen.kt`.
* Actions: Add `onPairSensor` parameter, render "+ Pair Sensor" button when 0 sensors paired and not searching, wire drawer navigation.
* Verification: Unit and layout contract test in `ControlTrackingScreenTest.kt` and `ControlTrackingSensorHeaderContractTest.kt` (`TST-SEN-020.3`).
* Command: `./gradlew testDebugUnitTest --tests "*ControlTracking*"`

### Step 5: Clean-Room Full Suite Regression
* Target: Full project unit test suite.
* Actions: Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero failures (`TST-SEN-020.6`).
* Command: `./gradlew testDebugUnitTest`
