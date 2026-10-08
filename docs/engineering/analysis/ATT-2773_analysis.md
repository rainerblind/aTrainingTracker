# Stage 1 Analysis: ATT-2773 - Bluetooth LE devices are not discovered during sensor scanning in ControlTrackingScreen

**Ticket**: [ATT-2773](https://atrainingtracker.atlassian.net/browse/ATT-2773)  
**Sub-task**: [ATT-2781](https://atrainingtracker.atlassian.net/browse/ATT-2781) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Sensor Connectivity & Protocols*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.4`  
**Branch**: `bugfix/ATT-2773`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-41.3 physical device evaluation on Google Pixel 10 (Android 17 / SDK 37), sensor scanning triggered from the workout tracking control screen (`ControlTrackingScreen.kt`, "Suchen" / `ResearchButton`) failed to discover and connect to nearby active Bluetooth Low Energy (BLE) peripherals (e.g. Polar H10, Garmin HRM-Dual, cadence and power meters).

Physical testing and forensic inspection revealed the following:
* Tapping "Suchen" initiates `banalServiceRepository.startSearchingForPairedDevices()`, which routes via `BANALService` to `DeviceManager.startSearchForPairedDevices()`.
* In `DeviceManager`, paired devices are queued and `myRemoteDevice.startSearching()` is executed. For BLE peripherals (`MyBTLEDevice.java`), `device.connectGatt(mContext, false, mGattCallback)` is dispatched.
* Under modern Android (API 23+, and especially Android 14/15/16/17), omitting the transport parameter defaults to `TRANSPORT_AUTO` (`0`), which attempts dual-mode BR/EDR negotiation or immediately fails with GATT status 133 (`GATT_ERROR`) or times out for single-mode BLE peripherals.
* Simultaneously, during discovery of new devices via `BTSearchForNewDevicesEngine.java`:
  - `startAsyncSearch()` only verifies `BLUETOOTH_CONNECT`, omitting the required `BLUETOOTH_SCAN` runtime permission check on Android 12+ (API 31+).
  - In `AndroidManifest.xml`, `BLUETOOTH_SCAN` asserted `android:usesPermissionFlags="neverForLocation"`. Under Android 12+ Bluetooth security policies, declaring `neverForLocation` causes the OS Bluetooth stack to silently drop all scan results when `ScanFilter` is empty or null, and suppresses beacons/advertisements where service UUIDs reside in Scan Response packets. Because `aTrainingTracker` is an outdoor GPS fitness tracker that already requests and possesses `ACCESS_FINE_LOCATION`, asserting `neverForLocation` is both technically inaccurate and actively destructive to BLE discovery.
  - `ScanSettings` omitted aggressive match modes and callback types on API 23+, allowing the OS to delay or batch advertisement callbacks.
  - Newly detected peripherals in `mScanCallback` also invoked `connectGatt` without `TRANSPORT_LE`.
  - Android 14+ foreground service connected device requirements (`FOREGROUND_SERVICE_CONNECTED_DEVICE`) were not declared for `BANALService` and `TrackerService`.

The objective of **ATT-2773** is to restore instantaneous, robust BLE sensor scanning, pairing, and reconnection on modern Android devices (API 23 through Android 17 / API 37) across both `ControlTrackingScreen` (paired device search) and `DevicesTabbedScreen` (new sensor pairing discovery), while preserving full backwards compatibility and architectural invariants.

---

## 2. Root Cause Analysis (Forensic Investigation)

### Root Cause 1: Omission of `BluetoothDevice.TRANSPORT_LE` in `connectGatt` (Critical)
* **Locations**: 
  - `MyBTLEDevice.java` (line 201): `mBluetoothGatt = device.connectGatt(mContext, false, mGattCallback);`
  - `BTSearchForNewDevicesEngine.java` (line 244): `mBTGatts.put(device.getAddress(), device.connectGatt(mContext, false, mGattCallback));`
* **Mechanism**: 
  - Android 6.0 (API 23) introduced `BluetoothDevice.connectGatt(Context, boolean, BluetoothGattCallback, int transport)`.
  - When the 3-argument overload is used, the framework assigns `transport = TRANSPORT_AUTO` (`0`).
  - On modern Android platforms (API 26+ up to Android 17), `TRANSPORT_AUTO` attempts classic Bluetooth BR/EDR SDP discovery before falling back to BLE. For BLE-only peripherals (such as modern fitness heart rate straps, power meters, speed/cadence sensors), this causes connection timeouts, unhandled disconnects, or fatal status 133 (`GATT_ERROR`) in `onConnectionStateChange`.
  - Specifying `BluetoothDevice.TRANSPORT_LE` (`2`) forces the Bluetooth controller to connect directly over the Low Energy physical transport, establishing GATT connections in milliseconds.

### Root Cause 2: `android:usesPermissionFlags="neverForLocation"` Suppressing BLE Scan Results (Critical)
* **Location**: `app/src/main/AndroidManifest.xml` (line 41):
  ```xml
  <uses-permission
      android:name="android.permission.BLUETOOTH_SCAN"
      android:usesPermissionFlags="neverForLocation" />
  ```
* **Mechanism**:
  - The `neverForLocation` flag was introduced in Android 12 (API 31) for apps that only communicate with paired beacons/devices and swear to never use Bluetooth for positioning, exempting them from location permissions.
  - Under Android 12+ framework enforcement, when `neverForLocation` is present:
    1. Scan results are automatically dropped by the system if `ScanFilter` is empty or null (e.g. `DeviceType.ALL` discovery in `BTSearchForNewDevicesEngine`).
    2. Advertisements with 16-bit or 128-bit UUIDs located in scan response data or non-standard advertisement headers are dropped.
  - `aTrainingTracker` is a dedicated GPS sports tracking application with explicit user consent for `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`. Asserting `neverForLocation` is architecturally contradictory and cripples general BLE scanning.
  - Removing `neverForLocation` allows `BluetoothLeScanner` to deliver all incoming BLE advertisements to `ScanCallback` regardless of filter configuration.

### Root Cause 3: Inadequate Permission Verification in `BTSearchForNewDevicesEngine`
* **Location**: `BTSearchForNewDevicesEngine.java` (lines 326, 359):
  ```java
  if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
      return;
  }
  ```
* **Mechanism**:
  - `scanner.startScan(...)` and `scanner.stopScan(...)` require `Manifest.permission.BLUETOOTH_SCAN` on Android 12+ (API 31+). Calling `startScan` without `BLUETOOTH_SCAN` throws a runtime `SecurityException`.
  - On Android < 31, `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` do not exist; checking them returns `PERMISSION_DENIED` unless guarded by SDK version checks, while `ACCESS_FINE_LOCATION` is the required permission on legacy Android.
  - Fix: Check `BLUETOOTH_SCAN` on API 31+, and `ACCESS_FINE_LOCATION` on API < 31 before scanning. Check `BLUETOOTH_CONNECT` on API 31+ before calling `connectGatt` or GATT operations.

### Root Cause 4: Suboptimal `ScanSettings` and `ScanFilter` Configuration
* **Location**: `BTSearchForNewDevicesEngine.java` (lines 346-350):
  ```java
  ScanSettings settings = new ScanSettings.Builder()
          .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
          .build();
  ```
* **Mechanism**:
  - On API 23+, `ScanSettings.Builder` supports:
    - `.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)`
    - `.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)`
    - `.setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)`
  - Without these parameters, Android OS power management batches scan results or waits for multiple advertisements before notifying `mScanCallback`, causing significant latency or apparent failure during interactive device pairing.

### Root Cause 5: Missing Android 14+ Foreground Service Connected Device Declaration
* **Location**: `app/src/main/AndroidManifest.xml` and `TrackerService.java`:
  - Android 14 (API 34) mandates that foreground services communicating with external peripherals declare `android:foregroundServiceType="...|connectedDevice"` and hold `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />`.
  - `BANALService` had no `foregroundServiceType` specified, and `TrackerService` only declared `"location"`.
  - While tracking or searching in the background, omitting `connectedDevice` risks service termination or Bluetooth resource revocation by Android 14/15/16/17 process management.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Add `BluetoothDevice.TRANSPORT_LE` to `connectGatt` calls in `MyBTLEDevice.java` and `BTSearchForNewDevicesEngine.java` on API 23+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.M`), with graceful fallback on legacy platforms.
  2. Remove `android:usesPermissionFlags="neverForLocation"` from `BLUETOOTH_SCAN` in `AndroidManifest.xml`.
  3. Declare `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE` in `AndroidManifest.xml` and update `BANALService` and `TrackerService` foreground service types to include `connectedDevice` and `health`.
  4. Modernize `ScanSettings` in `BTSearchForNewDevicesEngine.java` with `CALLBACK_TYPE_ALL_MATCHES`, `MATCH_MODE_AGGRESSIVE`, and `MATCH_NUM_ONE_ADVERTISEMENT` on API 23+.
  5. Correct runtime permission checks in `BTSearchForNewDevicesEngine.java` to check `BLUETOOTH_SCAN` on API 31+ / `ACCESS_FINE_LOCATION` on API < 31 for scanning, and `BLUETOOTH_CONNECT` for GATT operations.
  6. Expand unit test suites (`BTSearchForNewDevicesEngineTest.kt` and `MyBTLEDeviceLifecycleTest.kt`) to rigorously test `TRANSPORT_LE` routing, permission validation, and scan setting configuration.
  7. Verify on connected Google Pixel 10 hardware (Android 17) via ADB.
  8. Maintain 100% full clean-room unit test suite pass rate.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Refactoring ANT+ protocol or dongle drivers (`BANALService` ANT+ pipelines remain untouched).
  2. Modifying UI layout or Compose styling of `ControlTrackingScreen.kt` or `DevicesTabbedScreen.kt`.
  3. Modifying database tables or schemas in `Devices.db`.
  4. Modifying sensor telemetry parsing algorithms in `BTLEBikePowerDevice`, `BTLEHeartRateDevice`, etc.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: 
  - Extends `REQ-CON-018` (*Robust Bluetooth LE Sensor Discovery: GATT Queue Deadlock Prevention and State Reset in BTSearchForNewDevicesEngine*).
  - Extends `REQ-CON-012` (*Asynchronous BLE GATT Lifecycle, Thread-Safe Concurrency & Read Queue Null-Safety*).
* **Historical Origin & Commit Trace**: 
  - `REQ-CON-012`: Sprint 2026-40.10 (`ATT-1348`), commit `b09b533e`.
  - `REQ-CON-018`: Sprint 2026-40.15 (`ATT-2224`), commit `79854589`.
  - Manifest `neverForLocation`: Added during Android 12 migration (Sprint 2021 / commit `67451a9`).
* **Root Reason for Existing Formulation**: 
  - In commit `67451a9`, `neverForLocation` was added following Google's initial Android 12 migration sample snippets to avoid requesting location permission solely for Bluetooth. However, `aTrainingTracker` is fundamentally a GPS tracking application that already requests fine location. Retaining `neverForLocation` caused unintended scan result drops under modern Android OS updates.
  - The legacy 3-argument `connectGatt` was authored in Android 4.3 (API 18) when BLE was first introduced to Android, prior to the introduction of `TRANSPORT_LE` in Android 6.0 (API 23).
* **Preservation of Core Invariants**:
  - Thread safety and null safety guaranteed by `REQ-CON-012` and `REQ-CON-018` remain 100% intact.
  - TOCTOU immunity with `mGattLock` remains intact.
  - Telemetry measurement parsing (`REQ-CON-014`) remains untouched.
  - 100% clean-room test pass rate preserved.

---

## 5. Architectural Strategy & High-Level Solution

### Component Changes
1. **`app/src/main/AndroidManifest.xml`**:
   - Update `BLUETOOTH_SCAN` declaration: remove `android:usesPermissionFlags="neverForLocation"`.
   - Add `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />`.
   - Update `BANALService`: `android:foregroundServiceType="location|health|connectedDevice"`.
   - Update `TrackerService`: `android:foregroundServiceType="location|health|connectedDevice"`.

2. **`MyBTLEDevice.java`**:
   - In `startSearching()`, update `device.connectGatt(...)`:
     ```java
     synchronized (mGattLock) {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
             mBluetoothGatt = device.connectGatt(mContext, false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
         } else {
             mBluetoothGatt = device.connectGatt(mContext, false, mGattCallback);
         }
     }
     ```

3. **`BTSearchForNewDevicesEngine.java`**:
   - In `mScanCallback.onScanResult(...)`:
     ```java
     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
         mBTGatts.put(device.getAddress(), device.connectGatt(mContext, false, mGattCallback, BluetoothDevice.TRANSPORT_LE));
     } else {
         mBTGatts.put(device.getAddress(), device.connectGatt(mContext, false, mGattCallback));
     }
     ```
   - In `startAsyncSearch()`:
     - Check `BLUETOOTH_SCAN` on API 31+, and `ACCESS_FINE_LOCATION` on API < 31.
     - Modernize `ScanSettings`:
       ```java
       ScanSettings.Builder builder = new ScanSettings.Builder()
               .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
           builder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                  .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                  .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT);
       }
       ScanSettings settings = builder.build();
       ```
   - In `stopAsyncSearch()`:
     - Check `BLUETOOTH_SCAN` on API 31+.

4. **`TrackerService.java`**:
   - Update `performStartForeground` to include `ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH` and `ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` on API 34+ (or API 30+).

5. **Unit Tests**:
   - Expand `BTSearchForNewDevicesEngineTest.kt` with tests for permission gating, `ScanSettings` generation, and `TRANSPORT_LE` GATT connection.
   - Expand `MyBTLEDeviceLifecycleTest.kt` with `startSearching` tests verifying `TRANSPORT_LE` is supplied on API 23+.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. **Dual Transport Robustness**: Works seamlessly on Android 6.0 through Android 17.
  2. **Security & Permission Immunity**: No `SecurityException` crashes under any permission denial state.
  3. **Thread Safety & GATT State**: Preserves `mGattLock` synchronization and clean disconnection lifecycle.
  4. **Zero Test Regressions**: 100% full clean-room unit test suite pass rate.
  5. **Human Decision Gate**: Parent ticket `ATT-2773` completion is strictly reserved for human review.

* **Risk Rating**: **LOW-MEDIUM**
  - Well-defined Android Bluetooth LE APIs and standard permission model.
  - Minimal risk of regressions to existing telemetry or database operations.
