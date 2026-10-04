# Stage 2: Requirement & Test Specification - ATT-2224: Robust Bluetooth LE Sensor Discovery: Prevent GATT Queue Deadlock on Missing Optional Characteristics and Reset Stale Connections

**Ticket**: [ATT-2224](https://atrainingtracker.atlassian.net/browse/ATT-2224)  
**Sub-task**: [ATT-2314](https://atrainingtracker.atlassian.net/browse/ATT-2314) (`[Test-Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-40.15`  
**Requirement Mapping**: `REQ-CON-018` (*Robust Bluetooth LE Sensor Discovery: GATT Queue Deadlock Prevention and State Reset in BTSearchForNewDevicesEngine*)  
**Test Spec ID**: `TST-CON-009`  
**Branch**: `feature/ATT-2224`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Requirement Specification (REQ-CON-018)

### 1.1 Problem Statement & Rationale
During Bluetooth LE sensor pairing scans, `BTSearchForNewDevicesEngine` deadlocks when discovering peripherals that omit optional GATT characteristics (e.g. Manufacturer Name `0x2A29` or Battery Level `0x2A19`). Polling `null` from the read queue or encountering failed `gatt.readCharacteristic()` calls halts the execution chain, causing device discovery to fail completely. Furthermore, connection and notification maps are not cleared across searches, permanently blocking rediscovery in subsequent scans.

### 1.2 Functional & Architectural Requirements
The system SHALL guarantee robust, non-blocking GATT characteristic queue management and state reset in `BTSearchForNewDevicesEngine`:
1. **Defensive Queue Enqueueing**:
   - In `onServicesDiscovered()`, only non-null `BluetoothGattCharacteristic` references returned by `service.getCharacteristic(...)` SHALL be added to `mReadCharacteristicQueue`.
2. **Deadlock-Free Queue Progression**:
   - In `readNextCharacteristic(String address)`:
     - If the polled characteristic is `null`, the engine SHALL immediately advance by invoking `readNextCharacteristic(address)`.
     - When dispatching `gatt.readCharacteristic(characteristic)`, the engine SHALL evaluate the boolean return value. If `readCharacteristic(...)` returns `false` (indicating failure to enqueue in the Android Bluetooth stack), the engine SHALL immediately advance by invoking `readNextCharacteristic(address)`.
3. **Resilient Device Notification (`newDeviceFound`)**:
   - When all characteristics in the queue are read (or skipped), the engine SHALL invoke `newDeviceFound(address)` to notify the listener, even if optional characteristics (Manufacturer Name, Battery Level) were not present or failed to read.
4. **Comprehensive Lifecycle & State Reset**:
   - In `stopAsyncSearch()` and `startAsyncSearch()`, the engine SHALL invoke a synchronized state cleanup method (`resetTrackingState()`) that clears `mBTGatts`, `mReadCharacteristicQueue`, `mInformedDevices`, `mNameMap`, `mManufacturerMap`, and `mBatteryPercentage`.
   - On `stopAsyncSearch()`, all active `BluetoothGatt` connections SHALL be disconnected and closed prior to clearing the map.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Null-Safe Characteristic Queueing)**:
  * *Given* a discovered BLE peripheral whose Device Information or Battery service does not contain the requested characteristic (returns `null`),
  * *When* `onServicesDiscovered` executes,
  * *Then* no `null` objects SHALL be added to `mReadCharacteristicQueue`.
* **Criterion 2 (Deadlock-Free Read Progression on Null or Failure)**:
  * *Given* a characteristic queue containing a `null` entry or where `gatt.readCharacteristic()` returns `false`,
  * *When* `readNextCharacteristic()` is executed,
  * *Then* the engine SHALL skip to the next characteristic without stalling or terminating the discovery loop.
* **Criterion 3 (Successful Discovery with Missing Optional Descriptors)**:
  * *Given* a Bike Power or Heart Rate sensor lacking Device Information (Manufacturer Name) and Battery services,
  * *When* discovery finishes reading available characteristics,
  * *Then* `newDeviceFound(address)` SHALL be invoked and the device SHALL be presented to the user.
* **Criterion 4 (Search State Reset & Rediscovery)**:
  * *Given* an active search where devices were detected and connected,
  * *When* `stopAsyncSearch()` is called and a new search is started,
  * *Then* previously connected devices SHALL be redisoverable and reconnectable.

### 1.4 System Invariants
1. `Manifest.permission.BLUETOOTH_CONNECT` permission checks remain intact on all Bluetooth operations.
2. Runtime sensor operation in `MyBTLEDevice.java` (`REQ-CON-012`, `REQ-CON-014`) remains unaltered.
3. 100% clean-room unit test pass rate across the workspace.

---

## 2. Test Specification (TST-CON-009)

### Test Case 1: `testEnqueue_omitsNullCharacteristics` (`TST-CON-009.1`)
* **Scope**: Unit Test (`BTSearchForNewDevicesEngineTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngineTest.kt`
* **Preconditions**: Mock `BluetoothGattService` returning `null` for `UUID_CHARACTERISTIC_MANUFACTURER_NAME`.
* **Action**: Enqueue characteristics for the service.
* **Expected Result**: The characteristic queue for the device remains empty and contains zero null entries.

### Test Case 2: `testReadNextCharacteristic_handlesNullAndReadFailureGracefully` (`TST-CON-009.2`)
* **Scope**: Unit Test (`BTSearchForNewDevicesEngineTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngineTest.kt`
* **Preconditions**: Queue containing a null characteristic followed by a valid characteristic where `readCharacteristic()` returns `false`.
* **Action**: Invoke `readNextCharacteristic(address)`.
* **Expected Result**: The queue drains completely to empty; `newDeviceFound` is triggered for non-bike device types without hanging.

### Test Case 3: `testResetTrackingState_clearsAllMapsAndEnablesRediscovery` (`TST-CON-009.3`)
* **Scope**: Unit Test (`BTSearchForNewDevicesEngineTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngineTest.kt`
* **Preconditions**: Engine with populated `mBTGatts`, `mInformedDevices`, `mReadCharacteristicQueue`, `mNameMap`, `mManufacturerMap`, `mBatteryPercentage`.
* **Action**: Invoke `stopAsyncSearch()` / `resetTrackingState()`.
* **Expected Result**: All tracking maps are empty; subsequent scan results for the same MAC address successfully trigger connection logic.

### Test Case 4: Clean-Room Regression Suite (`TST-CON-009.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-009.1` | Unit | `BTSearchForNewDevicesEngine.enqueueCharacteristicIfPresent` | `REQ-CON-018` | Specified |
| `TST-CON-009.2` | Unit | `BTSearchForNewDevicesEngine.readNextCharacteristic` | `REQ-CON-018` | Specified |
| `TST-CON-009.3` | Unit | `BTSearchForNewDevicesEngine.resetTrackingState` / `stopAsyncSearch` | `REQ-CON-018` | Specified |
| `TST-CON-009.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
