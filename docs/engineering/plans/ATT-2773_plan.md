# Stage 3: Implementation Plan - ATT-2773: Bluetooth LE devices are not discovered during sensor scanning in ControlTrackingScreen

**Ticket**: [ATT-2773](https://atrainingtracker.atlassian.net/browse/ATT-2773)  
**Sub-task**: [ATT-2783](https://atrainingtracker.atlassian.net/browse/ATT-2783) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Sensor Connectivity & Protocols*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-CON-019` (*Robust Bluetooth LE Sensor Discovery & Direct LE Transport Connection Architecture*)  
**Test Mapping**: `TST-CON-011` (*Bluetooth LE Sensor Discovery, LE Transport Selection & Foreground Service Compliance Test*)  
**Branch**: `bugfix/ATT-2773`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Description & Background

During Sprint 2026-41.3 review on physical device (Google Pixel 10 running Android 17 / SDK 37), Bluetooth Low Energy (BLE) peripherals (e.g. Polar H10, Garmin HRM-Dual, cadence and power sensors) failed to be discovered or reconnected when triggering sensor search ("Suchen") from `ControlTrackingScreen.kt` or during new sensor pairing in `DevicesTabbedScreen.kt`.

Forensic analysis revealed 5 distinct root causes:
1. `device.connectGatt(mContext, false, mGattCallback)` omitted `BluetoothDevice.TRANSPORT_LE` in both `MyBTLEDevice.java` and `BTSearchForNewDevicesEngine.java`, defaulting to `TRANSPORT_AUTO` and failing with GATT status 133 or timeouts on BLE-only peripherals.
2. `AndroidManifest.xml` declared `android:usesPermissionFlags="neverForLocation"` on `BLUETOOTH_SCAN`. Under Android 12+ framework enforcement, this flag caused the OS to drop all scan results for empty/null filters (`DeviceType.ALL`), suppressing BLE peripheral discovery in an outdoor GPS app that already has fine location permissions.
3. In `BTSearchForNewDevicesEngine.java`, `startAsyncSearch()` checked `BLUETOOTH_CONNECT` instead of `BLUETOOTH_SCAN` on API 31+ / `ACCESS_FINE_LOCATION` on legacy Android.
4. `ScanSettings` omitted aggressive match modes (`CALLBACK_TYPE_ALL_MATCHES`, `MATCH_MODE_AGGRESSIVE`, `MATCH_NUM_ONE_ADVERTISEMENT`), allowing the OS to delay or batch callbacks.
5. `AndroidManifest.xml` and `TrackerService.java` / `BANALService.java` lacked Android 14+ foreground service connected device declarations (`FOREGROUND_SERVICE_CONNECTED_DEVICE`).

This implementation plan defines the atomic construction steps to resolve these issues across all layers and verify them with targeted automated tests and physical device checks.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-CON-019` (*Robust Bluetooth LE Sensor Discovery & Direct LE Transport Connection Architecture*)
* **Test Case**: `TST-CON-011` (*Bluetooth LE Sensor Discovery, LE Transport Selection & Foreground Service Compliance Test*)

---

## 3. System Invariants & Preserved Behavior

1. **Thread Synchronization & TOCTOU Immunity (`REQ-CON-012`)**: All operations interacting with `mBluetoothGatt` MUST remain synchronized under `mGattLock`.
2. **GATT Queue Deadlock Prevention & State Purging (`REQ-CON-018`)**: Null-safe enqueueing and fail-forward queue progression in `BTSearchForNewDevicesEngine` MUST be preserved.
3. **Telemetry Packet Parsing (`REQ-CON-014`)**: Calculation and parsing algorithms for HR, bike power/cadence/speed, and run speed MUST NOT be modified.
4. **ANT+ Protocol Stability**: ANT+ sensor pipelines and USB-OTG drivers in `BANALService` MUST NOT be altered.
5. **Full Test Suite Pass Rate**: 100% clean-room test pass rate across all unit test suites.
6. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-2773` remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `AndroidManifest.xml` (Permissions & Foreground Service Types)
* Remove `android:usesPermissionFlags="neverForLocation"` from `BLUETOOTH_SCAN`.
* Add `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />`.
* Update `<service android:name=".banalservice.BANALService" android:foregroundServiceType="location|health|connectedDevice" />`.
* Update `<service android:name=".trainingtracker.tracker.TrackerService" android:foregroundServiceType="location|health|connectedDevice" />`.

### Component 2: `MyBTLEDevice.java` (GATT Transport Parameter)
* In `startSearching()`:
  ```java
  synchronized (mGattLock) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          mBluetoothGatt = device.connectGatt(mContext, false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
      } else {
          mBluetoothGatt = device.connectGatt(mContext, false, mGattCallback);
      }
  }
  ```

### Component 3: `BTSearchForNewDevicesEngine.java` (Scan Settings, Permission Guards, Transport LE)
* In `mScanCallback.onScanResult`:
  ```java
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      mBTGatts.put(device.getAddress(), device.connectGatt(mContext, false, mGattCallback, BluetoothDevice.TRANSPORT_LE));
  } else {
      mBTGatts.put(device.getAddress(), device.connectGatt(mContext, false, mGattCallback));
  }
  ```
* In `startAsyncSearch()`:
  * Check `BLUETOOTH_SCAN` on API 31+, `ACCESS_FINE_LOCATION` on API < 31.
  * Build `ScanSettings` with `SCAN_MODE_LOW_LATENCY`, and on API 23+:
    `.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)`
    `.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)`
    `.setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)`.
* In `stopAsyncSearch()`:
  * Check `BLUETOOTH_SCAN` on API 31+.

### Component 4: `TrackerService.java` (Foreground Service Type Mask)
* In `performStartForeground`:
  * On API 34+ (`Build.VERSION_CODES.UPSIDE_DOWN_CAKE`), pass bitwise OR:
    `ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION | ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH | ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`.

### UI Consistency (Rule 23)
* **Scope**: This ticket contains zero UI layout changes or composable modifications. All changes are in service, transport, and engine layers. Existing UI in `ControlTrackingScreen.kt` and `DevicesTabbedScreen.kt` is strictly preserved.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Implementation Gate Verification (`REQ-PRO-016`)
* Verify Stage 3 Plan subtask `ATT-2783` is in status `Erledigt` via:
  ```bash
  python3 tools/jira_util.py check-gate ATT-2783
  ```
* Halt immediately if exit code is non-zero.

### Step 2: Manifest & Service Configuration Updates
* **File**: `app/src/main/AndroidManifest.xml`
* **Changes**:
  1. Remove `android:usesPermissionFlags="neverForLocation"` from `BLUETOOTH_SCAN`.
  2. Declare `FOREGROUND_SERVICE_CONNECTED_DEVICE` under `<uses-permission>`.
  3. Set `android:foregroundServiceType="location|health|connectedDevice"` on `BANALService` and `TrackerService`.

### Step 3: Implement `TRANSPORT_LE` in `MyBTLEDevice.java`
* **File**: `app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/MyBTLEDevice.java`
* **Changes**:
  - In `startSearching()`, branch on `Build.VERSION.SDK_INT >= Build.VERSION_CODES.M` and pass `BluetoothDevice.TRANSPORT_LE` into `connectGatt`.

### Step 4: Implement `TRANSPORT_LE`, Permission Checks & Aggressive ScanSettings in `BTSearchForNewDevicesEngine.java`
* **File**: `app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngine.java`
* **Changes**:
  1. In `mScanCallback.onScanResult`, branch on `Build.VERSION.SDK_INT >= Build.VERSION_CODES.M` and pass `BluetoothDevice.TRANSPORT_LE`.
  2. In `startAsyncSearch()`, check `BLUETOOTH_SCAN` on API 31+ / `ACCESS_FINE_LOCATION` on API < 31.
  3. In `startAsyncSearch()`, configure aggressive `ScanSettings` on API 23+.
  4. In `stopAsyncSearch()`, check `BLUETOOTH_SCAN` on API 31+.

### Step 5: Update Foreground Service Type in `TrackerService.java`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
* **Changes**:
  - In `performStartForeground`, bitwise OR `LOCATION`, `HEALTH`, and `CONNECTED_DEVICE` when running on API 34+.

### Step 6: Expand Unit Test Suites
* **Files**:
  - `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/MyBTLEDeviceLifecycleTest.kt`
  - `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngineTest.kt`
* **Changes**:
  1. In `MyBTLEDeviceLifecycleTest.kt`:
     - Test `startSearching()` on API 23+ passes `BluetoothDevice.TRANSPORT_LE`.
     - Test `startSearching()` on API < 23 calls 3-arg `connectGatt`.
  2. In `BTSearchForNewDevicesEngineTest.kt`:
     - Test `onScanResult()` connects GATT with `TRANSPORT_LE` on API 23+.
     - Test `startAsyncSearch()` aborts gracefully when `BLUETOOTH_SCAN` is denied.
     - Test `startAsyncSearch()` builds aggressive `ScanSettings` on API 23+.
* **Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "*.BTSearchForNewDevicesEngineTest" --tests "*.MyBTLEDeviceLifecycleTest"
  ```

### Step 7: Clean-Room Full Regression Execution
* Run full suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* Verify 0 failures across all unit test suites.
