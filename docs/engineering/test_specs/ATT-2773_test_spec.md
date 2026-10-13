# Stage 2: Requirement & Test Specification - ATT-2773: Bluetooth LE devices are not discovered during sensor scanning in ControlTrackingScreen

**Ticket**: [ATT-2773](https://atrainingtracker.atlassian.net/browse/ATT-2773)  
**Sub-task**: [ATT-2782](https://atrainingtracker.atlassian.net/browse/ATT-2782) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Sensor Connectivity & Protocols*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.4`  
**Requirement ID**: `REQ-CON-019`  
**Test Spec ID**: `TST-CON-011`  
**Branch**: `bugfix/ATT-2773`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Requirement Specification (REQ-CON-019)

### 1.1 Problem Statement & Rationale
During workout setup on modern Android versions (Android 14 through Android 17 / API 37, tested on Google Pixel 10), triggering sensor scanning from `ControlTrackingScreen.kt` ("Suchen" / `ResearchButton`) or from `DevicesTabbedScreen.kt` failed to discover and connect to Bluetooth Low Energy (BLE) sensors (e.g. Polar H10, Garmin HRM-Dual, cadence/power meters):
1. Omitting `BluetoothDevice.TRANSPORT_LE` in `device.connectGatt(...)` (`MyBTLEDevice.java` and `BTSearchForNewDevicesEngine.java`) defaulted to `TRANSPORT_AUTO` (`0`), triggering fatal status 133 (`GATT_ERROR`) or timeouts for single-mode BLE peripherals.
2. In `AndroidManifest.xml`, declaring `android:usesPermissionFlags="neverForLocation"` on `BLUETOOTH_SCAN` caused the Android 12+ Bluetooth stack to silently drop scan results when `ScanFilter` is empty or null, and suppressed advertisements where UUIDs reside in Scan Response data.
3. In `BTSearchForNewDevicesEngine.java`, `startAsyncSearch()` only checked `BLUETOOTH_CONNECT`, omitting the required `BLUETOOTH_SCAN` check on API 31+ and `ACCESS_FINE_LOCATION` check on API < 31.
4. `ScanSettings` omitted aggressive match modes (`CALLBACK_TYPE_ALL_MATCHES`, `MATCH_MODE_AGGRESSIVE`, `MATCH_NUM_ONE_ADVERTISEMENT`), allowing the OS to delay or batch callbacks.
5. Android 14+ foreground service connected device requirements (`FOREGROUND_SERVICE_CONNECTED_DEVICE`) were omitted from `AndroidManifest.xml` and service declarations.

### 1.2 Functional & Architectural Requirements
The system SHALL guarantee direct Low Energy physical transport connections, unsuppressed BLE scan result delivery, modern scan settings, robust runtime permission verification, and Android 14+ foreground service compliance across all Bluetooth LE discovery and communication workflows (ATT-2773):

1. *Direct Low Energy Transport Specification (`TRANSPORT_LE`)*:
   - In `MyBTLEDevice.java` (`startSearching()`) and `BTSearchForNewDevicesEngine.java` (`mScanCallback.onScanResult`), on Android 6.0+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.M`), the system SHALL invoke `device.connectGatt(mContext, false, mGattCallback, BluetoothDevice.TRANSPORT_LE)`.
   - On legacy platforms (`Build.VERSION.SDK_INT < Build.VERSION_CODES.M`), the system SHALL fall back cleanly to `device.connectGatt(mContext, false, mGattCallback)`.

2. *Unsuppressed BLE Scan Result Delivery (`AndroidManifest.xml`)*:
   - The system SHALL remove `android:usesPermissionFlags="neverForLocation"` from `<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />` in `AndroidManifest.xml`.
   - The application SHALL utilize its already-granted `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` permissions to permit unrestricted reception of BLE advertisements across all filter configurations (including `DeviceType.ALL` where `ScanFilter` is null).

3. *Robust Runtime Permission Verification (`BTSearchForNewDevicesEngine.java`)*:
   - In `startAsyncSearch()`:
     - On Android 12+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`), the engine SHALL verify `ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_SCAN) == PERMISSION_GRANTED` before invoking `scanner.startScan()`. If denied, it SHALL log an advisory warning and return cleanly without throwing `SecurityException`.
     - On Android < 12 (`Build.VERSION.SDK_INT < Build.VERSION_CODES.S`), the engine SHALL verify `ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PERMISSION_GRANTED`.
   - In `stopAsyncSearch()`:
     - On Android 12+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`), the engine SHALL verify `BLUETOOTH_SCAN` before invoking `scanner.stopScan()`.
   - In `mScanCallback.onScanResult`:
     - On Android 12+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`), the engine SHALL verify `BLUETOOTH_CONNECT` before invoking `device.connectGatt()`.

4. *Low-Latency Aggressive Scan Configuration (`BTSearchForNewDevicesEngine.java`)*:
   - On Android 6.0+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.M`), `ScanSettings.Builder` SHALL configure:
     - `.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)`
     - `.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)`
     - `.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)`
     - `.setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)`
   - On Android < 6.0, `ScanSettings.Builder` SHALL configure `.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)`.

5. *Android 14+ Foreground Service Compliance (`AndroidManifest.xml` & `TrackerService.java`)*:
   - `AndroidManifest.xml` SHALL declare `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />`.
   - `BANALService` declaration in `AndroidManifest.xml` SHALL include `android:foregroundServiceType="location|health|connectedDevice"`.
   - `TrackerService` declaration in `AndroidManifest.xml` SHALL include `android:foregroundServiceType="location|health|connectedDevice"`.
   - In `TrackerService.java`, `performStartForeground` SHALL include `ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH` and `ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` in its service type bitmask on API 34+.

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (TRANSPORT_LE Selection in MyBTLEDevice)**:
  * *Given* an active paired BLE sensor instance in `MyBTLEDevice` running on Android 6.0+ (API 23+),
  * *When* `startSearching()` is executed,
  * *Then* the device SHALL invoke `device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)`, forcing the connection over Low Energy transport.

* **Criterion 2 (TRANSPORT_LE Selection in BTSearchForNewDevicesEngine)**:
  * *Given* a discovered BLE peripheral in `BTSearchForNewDevicesEngine` running on Android 6.0+ (API 23+),
  * *When* `mScanCallback.onScanResult()` handles the advertisement,
  * *Then* the engine SHALL invoke `device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)`.

* **Criterion 3 (Unrestricted Scan Results via neverForLocation Removal)**:
  * *Given* an Android 12+ device running `aTrainingTracker` with granted fine location permissions,
  * *When* scanning for new devices with `deviceType == DeviceType.ALL` (`ScanFilter == null`),
  * *Then* the OS Bluetooth stack SHALL NOT discard incoming advertisements and all nearby BLE peripherals SHALL be delivered to `mScanCallback`.

* **Criterion 4 (Runtime Permission Guarding in startAsyncSearch)**:
  * *Given* an Android 12+ device where `BLUETOOTH_SCAN` permission is not granted,
  * *When* `startAsyncSearch()` is invoked,
  * *Then* the engine SHALL abort scan initialization gracefully without throwing `SecurityException`.

* **Criterion 5 (Aggressive ScanSettings Configuration)**:
  * *Given* `startAsyncSearch()` running on API 23+,
  * *When* `ScanSettings` is constructed,
  * *Then* the settings SHALL incorporate `CALLBACK_TYPE_ALL_MATCHES`, `MATCH_MODE_AGGRESSIVE`, and `MATCH_NUM_ONE_ADVERTISEMENT`.

* **Criterion 6 (Foreground Service Compliance on Android 14+)**:
  * *Given* tracking active on Android 14+ with connected BLE peripherals,
  * *When* `TrackerService` or `BANALService` runs in the foreground,
  * *Then* the service declarations and runtime invocations SHALL include `connectedDevice` and `health`, avoiding framework termination or permission revocation.

### 1.4 System Invariants
1. **Thread Synchronization & TOCTOU Immunity (`REQ-CON-012`)**: All operations on `mBluetoothGatt` MUST remain guarded by `mGattLock`.
2. **GATT Queue Deadlock Prevention (`REQ-CON-018`)**: Null-safe enqueueing and fail-forward queue progression in `BTSearchForNewDevicesEngine` MUST be strictly preserved.
3. **Telemetry Measurement Parsing (`REQ-CON-014`)**: Parsing of HR, bike cadence/speed/power, and run speed MUST NOT be modified.
4. **Clean-Room Test Pass Rate**: 100% full-suite unit test pass rate MUST be preserved with zero regressions.

---

## 2. Test Specification (TST-CON-011)

### 2.1 Test Cases & Verification Procedures

#### Test Case 1: `testStartSearching_onApi23Plus_connectsGattWithTransportLe` (`MyBTLEDeviceLifecycleTest.kt`)
* **Objective**: Verify that `MyBTLEDevice.startSearching()` passes `BluetoothDevice.TRANSPORT_LE` on API 23+.
* **Input**: Mocked `BluetoothDevice`, `BluetoothManager`, `BluetoothAdapter`, `Context` with `BLUETOOTH_CONNECT` granted.
* **Execution**: Invoke `device.startSearching()`, drain main Looper.
* **Verification**: Verify `device.connectGatt(mockContext, false, any(), BluetoothDevice.TRANSPORT_LE)` was called.

#### Test Case 2: `testStartSearching_onLegacyApi_connectsGattWithoutTransportParam` (`MyBTLEDeviceLifecycleTest.kt`)
* **Objective**: Verify fallback to 3-argument `connectGatt` on API < 23.
* **Input**: Mocked `Build.VERSION.SDK_INT = 21`.
* **Execution**: Invoke `device.startSearching()`, drain main Looper.
* **Verification**: Verify `device.connectGatt(mockContext, false, any())` was called.

#### Test Case 3: `testScanResult_onApi23Plus_connectsGattWithTransportLe` (`BTSearchForNewDevicesEngineTest.kt`)
* **Objective**: Verify that `BTSearchForNewDevicesEngine` uses `TRANSPORT_LE` when connecting to discovered devices.
* **Input**: Discovered `ScanResult` with mock `BluetoothDevice`.
* **Execution**: Trigger `mScanCallback.onScanResult(0, scanResult)`, drain Looper.
* **Verification**: Verify `device.connectGatt(mockContext, false, any(), BluetoothDevice.TRANSPORT_LE)` was called.

#### Test Case 4: `testStartAsyncSearch_missingBluetoothScanPermission_returnsWithoutCrash` (`BTSearchForNewDevicesEngineTest.kt`)
* **Objective**: Verify that missing `BLUETOOTH_SCAN` on API 31+ does not throw `SecurityException`.
* **Input**: Mock `ContextCompat.checkSelfPermission(..., Manifest.permission.BLUETOOTH_SCAN)` returning `PERMISSION_DENIED`.
* **Execution**: Call `startAsyncSearch()`.
* **Verification**: Verify `scanner.startScan(...)` is never called and method returns cleanly.

#### Test Case 5: `testStartAsyncSearch_grantedBluetoothScanPermission_configuresAggressiveSettings` (`BTSearchForNewDevicesEngineTest.kt`)
* **Objective**: Verify `ScanSettings` has `SCAN_MODE_LOW_LATENCY`, `CALLBACK_TYPE_ALL_MATCHES`, and `MATCH_MODE_AGGRESSIVE`.
* **Input**: `BLUETOOTH_SCAN` granted, mock `BluetoothLeScanner`.
* **Execution**: Call `startAsyncSearch()`.
* **Verification**: Capture `ScanSettings` passed to `scanner.startScan(...)` and verify scan mode and API 23+ settings.

#### Test Case 6: Manifest & Configuration Integrity Audit
* **Objective**: Verify `AndroidManifest.xml` lacks `neverForLocation` on `BLUETOOTH_SCAN` and includes `FOREGROUND_SERVICE_CONNECTED_DEVICE`.
* **Verification**: XML structure analysis confirming attribute removal and permission presence.

---

## 3. Traceability Matrix

| Requirement Clause | Description | Verification Test Case | Target Class / File | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-019.1` | Direct Low Energy Transport Selection (`TRANSPORT_LE`) | `testStartSearching_onApi23Plus_connectsGattWithTransportLe`<br>`testScanResult_onApi23Plus_connectsGattWithTransportLe` | `MyBTLEDevice.java`<br>`BTSearchForNewDevicesEngine.java` | Planned |
| `REQ-CON-019.2` | Unsuppressed BLE Scan Result Delivery | Manifest Integrity Audit | `AndroidManifest.xml` | Planned |
| `REQ-CON-019.3` | Robust Runtime Permission Verification | `testStartAsyncSearch_missingBluetoothScanPermission_returnsWithoutCrash` | `BTSearchForNewDevicesEngine.java` | Planned |
| `REQ-CON-019.4` | Low-Latency Aggressive Scan Configuration | `testStartAsyncSearch_grantedBluetoothScanPermission_configuresAggressiveSettings` | `BTSearchForNewDevicesEngine.java` | Planned |
| `REQ-CON-019.5` | Android 14+ Foreground Service Compliance | Manifest Integrity Audit | `AndroidManifest.xml`<br>`TrackerService.java` | Planned |
