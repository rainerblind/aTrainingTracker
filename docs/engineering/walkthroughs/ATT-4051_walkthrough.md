# Walkthrough - ATT-4051: Sensor Onboarding, Empty-State Action & Discovery Auto-Pairing

Implemented first-run sensor pairing onboarding on the workout start screen, actionable empty-state search triggers in the sensors view with lifecycle safety guards, and default discovery auto-pairing in `DeviceManager` with concurrency locks, active workout immunity, database failure resilience, and manual override precedence.

## 1. Requirements Fulfilled
- **REQ-SEN-020**: Default sensor discovery auto-pairing, workout start screen onboarding action, and "My Sensors" actionable empty state.
  - Start screen (`ControlTrackingScreen`) displays a centered `+ Pair Sensor` onboarding button when 0 sensors are paired and the app is not actively searching, routing directly to "My Sensors".
  - "My Sensors" screen (`DeviceListScreen`) presents an actionable empty-state search hint and `+ Search for Sensors` button when the device list is empty, !isSearching, and no active filters are applied.
  - `DevicesTabbedScreen` lifecycle guard (`DisposableEffect`) terminates discovery scans and cleans up open modal sheets upon navigation disposal.
  - `DeviceManager` (`BTSearchInterface`, `ANTAsyncSearchEngineInterface`) auto-pairs newly discovered sensors (`paired = true`), respects lock ordering with database monitor, provides active workout auto-pair immunity (`TrackingMode.TRACKING`), handles SQLite insertion failures gracefully, and honors manual unpair precedence post-workout.
  - 9-language localization parity across all strings (`onboarding_pair_sensor_action`, `devices_empty_state_search_hint`, `devices_action_search_sensors`).

## 2. Verification Results
- **TST-SEN-020.1 (Database Auto-Pairing Flag)**: **PASS** (`DevicesDatabaseManagerTest.kt`)
  - Verified `insertNewBluetoothDeviceIntoDB` and `insertNewAntDeviceIntoDB` persist `PAIRED = 1` in SQLite.
- **TST-SEN-020.2 (Synchronized Discovery & Notification)**: **PASS** (`DeviceManagerDiscoveryTest.kt`)
  - Verified BTLE and ANT+ discovery engines insert with `paired = true`, trigger `pairingChanged`, and dispatch `NEW_DEVICE_FOUND_INTENT`.
- **TST-SEN-020.3 (Start Screen Onboarding Button Contract)**: **PASS** (`ControlTrackingSensorHeaderContractTest.kt`)
  - Verified `+ Pair Sensor` button displays when 0 sensors paired and idle, triggers `onPairSensor`, and preserves existing layout when devices/research buttons are present.
- **TST-SEN-020.4 (Empty-State Action & Timeout Guard Contract)**: **PASS** (`DeviceListScreenContractTest.kt`)
  - Verified `+ Search for Sensors` button and hint display in empty state, trigger `onScanForSensors`, and hide during active scan.
- **TST-SEN-020.5 (9-Language Localization Parity)**: **PASS** (`SensorOnboardingLocalizationTest.kt`)
  - Verified all 3 new strings are non-blank and translated across EN, DE, ES, FR, IT, JA, NL, PL, PT.
- **TST-SEN-020.7 (Immediate Unpair Reversal Safety)**: **PASS** (`DeviceListViewModelPairingTest.kt`)
  - Verified unpairing an auto-paired device updates database with `isPaired = false` (`PAIRED = 0`).
- **TST-SEN-020.8 (Navigation Disposal Scan Reset)**: **PASS** (`DevicesTabbedViewModelTest.kt`, `DevicesTabbedScreenContractTest.kt`)
  - Verified navigation disposal stops searching and cleans up sheet states without zombie actions.
- **TST-SEN-020.9 (Active Workout Auto-Pairing Immunity)**: **PASS** (`DeviceManagerDiscoveryTest.kt`)
  - Verified discovery during `TrackingMode.TRACKING` persists sensor with `paired = true` but suppresses immediate connection attempt to protect telemetry recording.
- **TST-SEN-020.10 (DB Insertion Failure Resilience)**: **PASS** (`DeviceManagerDiscoveryTest.kt`)
  - Verified `deviceId <= 0` aborts notification dispatch and avoids phantom newly found entries.
- **TST-SEN-020.11 (Manual Unpair vs. Auto-Pairing Conflict Resolution)**: **PASS** (`DeviceManagerDiscoveryTest.kt`)
  - Verified manual unpair during workout persists `paired = 0`, preventing post-workout auto-reconnect.

## 3. Technical Changes
- **Database**:
  - Tested `DevicesDatabaseManager.insertNewBluetoothDeviceIntoDB` and `insertNewAntDeviceIntoDB` with `paired = true`.
- **Device Management**:
  - `DeviceManager.java`: discovery handlers wrapped in `synchronized (mDevicesDatabaseManager)` lock; post-workout `mTrackingStateChangedReceiver` triggers `startSearchForPairedDevices()`; safe null handling in `MyRemoteDevice.java`.
  - `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`: enabled `pairingRecommendation = true`.
- **UI & Navigation**:
  - `ControlTrackingScreen.kt`: added centered `+ Pair Sensor` onboarding button when 0 sensors paired and idle.
  - `TrackingTabsScreen.kt`: wired `onPairSensor` to navigate to drawer item `R.id.drawer_my_sensors`.
  - `DeviceListScreen.kt`: added `onScanForSensors` parameter and actionable empty-state button.
  - `DevicesTabbedScreen.kt`: wired `onScanForSensors` to trigger protocol selection bottom sheet across tabs 0, 1, 2; added comprehensive disposal cleanup.
- **Localization**:
  - Added translations in `res/values*/strings.xml` for all 9 locales.
