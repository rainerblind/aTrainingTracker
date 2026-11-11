# Stage 3: Implementation Plan - ATT-6701: Align BIKE_RADAR discovery in BTSearchForNewDevicesEngine, unify MyBTLEDevice GATT read queue, and scope RADAR_* SensorTypes to BSportType.BIKE

**Ticket**: [ATT-6701](https://rainerblind.atlassian.net/browse/ATT-6701)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-CON-028` (`REQ-CON-028.2`, `REQ-CON-028.3`, `REQ-TRK-070.3`)  
**Test Mapping**: `TST-CON-028` (`TST-CON-028.1`, `TST-CON-028.5`, `TST-CON-028.6`, `TST-CON-028.7`, `TST-CON-028.8`)  
**Branch**: `feature/ATT-6701`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background

In sprint review of `ATT-5083`, two BLE GATT queuing/discovery issues were identified:
1. `BTSearchForNewDevicesEngine.java`: An ad-hoc early return check in `onServicesDiscovered` bypassed standard metadata enqueuing (`UUID_CHARACTERISTIC_MANUFACTURER_NAME`, `UUID_CHARACTERISTIC_BATTERY_LEVEL`) for `DeviceType.BIKE_RADAR`, while `matchesScanRecord(ScanResult, UUID)` remained as dead code with 0 callers (Rule 5.3).
2. `MyBTLEDevice.java`: Split GATT read queues occurred because legacy `mReadCharacteristicQueue` and `mBatteryReadRunnable` called `gatt.readCharacteristic(...)` directly outside `mGattQueue`, allowing `onCharacteristicRead` to prematurely complete `mGattOperationPending` and collide with in-flight descriptor/characteristic writes (`GATT_BUSY` 132/0x84).
3. `ActivityType.java`: Radar scalar `SensorType`s were previously unscoped across activity types; this was independently audited and verified as already resolved under `BSportType.BIKE` in commit `e9db6552` (ATT-6726) with passing tests in `ActivityTypeTest.kt`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-CON-028` (*BLE Cycling Radar Protocol Driver, Serialized GATT Queue, Scalar Telemetry & Bike Equipment Integration*)
  * `REQ-CON-028.2`: Unified GATT Operation Queue (FIFO serialization of descriptor writes, characteristic reads/writes, MTU requests).
  * `REQ-CON-028.3`: Aligned Radar Peripheral Discovery & Scan Filtering.
  * `REQ-TRK-070.3`: Radar Sensor Scoping to `BSportType.BIKE`.
* **Test Mapping**: `TST-CON-028`
  * `TST-CON-028.1`: Thread-safe serialized GATT read/write execution in `MyBTLEDeviceQueueTest.kt`.
  * `TST-CON-028.7`: Aligned `BIKE_RADAR` discovery in `BTSearchForNewDevicesEngineTest.kt`.
  * `TST-CON-028.8`: Discovery rejection on missing radar service in `BTSearchForNewDevicesEngineTest.kt`.
  * `TST-CON-028.5`: 9-language translation parity in `TranslationParityTest.kt`.
  * `TST-CON-028.6`: Tier 1 BLE & BANAL unit test regression suite.

---

## 3. System Invariants & Preserved Behavior

1. **Strict Single-GATT Concurrency**: Exactly one GATT operation executes at any time per connected device (`mGattOperationPending` guard).
2. **Backward-Compatible Service Registration**: Transition to `State.CONNECTED_WITH_SERVICE` and invocation of `registerSensors()` remain intact.
3. **Peripheral Discovery Contract**: Legitimate radar peripherals (Garmin Varia V1/V2 and compatible ANT+/BLE devices) are reliably discovered; non-radar peripherals are rejected.
4. **Subtask & Human Gate Governance**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`; parent ticket terminal completion remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `BTSearchForNewDevicesEngine.java`
* Remove ad-hoc early return check `if (getDeviceType() == DeviceType.BIKE_RADAR)` from `onServicesDiscovered`.
* In `readNextCharacteristic(String address)`: When characteristic queue is empty, include `DeviceType.BIKE_RADAR` alongside `DeviceType.BIKE_POWER`, verifying `gatt.getService(BluetoothConstants.UUID_SERVICE_RADAR) != null` before invoking `newDeviceFound(address)`.
* In `startAsyncSearch()`: Encapsulate scan filter construction in helper `buildScanFilters(DeviceType)`.
* Remove dead method `matchesScanRecord(ScanResult, UUID)`.

### Component 2: `MyBTLEDevice.java`
* In `mBatteryReadRunnable`: Route battery reads through `enqueueReadCharacteristic(batteryChar)` instead of bypassing the queue.
* In `readNextCharacteristic()`: Drain any items from `mReadCharacteristicQueue` into `mGattQueue` via `enqueueReadCharacteristic(gattChar)`, preserving compatibility for legacy callers while maintaining single-queue FIFO ordering, then transition to `State.CONNECTED_WITH_SERVICE` and invoke `registerSensors()`.
* In `onCharacteristicRead`: Advance queue solely via `onGattOperationCompleted()`, removing redundant `readNextCharacteristic()` invocation.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Aligned Discovery & Scan Filters in `BTSearchForNewDevicesEngine.java`
* Target: `app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngine.java`
* Changes:
  1. Remove early-return check at lines 130–136 in `onServicesDiscovered`.
  2. In `readNextCharacteristic(String address)`: Add `DeviceType.BIKE_RADAR` check verifying `UUID_SERVICE_RADAR`.
  3. Extract `buildScanFilters(DeviceType)` helper in `startAsyncSearch()`.
  4. Delete unused `matchesScanRecord(ScanResult, UUID)` method.

### Step 2: Unified GATT Read Queue in `MyBTLEDevice.java`
* Target: `app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/MyBTLEDevice.java`
* Changes:
  1. Update `mBatteryReadRunnable` to invoke `enqueueReadCharacteristic(batteryChar)`.
  2. Update `readNextCharacteristic()` to forward queued characteristics to `enqueueReadCharacteristic(...)` and register sensors when complete.
  3. Update `onCharacteristicRead` to remove redundant `readNextCharacteristic()` call.

### Step 3: Unit Test Suite Expansion
* Targets:
  1. `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngineTest.kt` (`TST-CON-028.7`, `TST-CON-028.8`).
  2. `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/MyBTLEDeviceQueueTest.kt` (`TST-CON-028.1`).
* Verification: Run targeted unit tests under parallel sprint lock:
  ```bash
  python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.banalservice.ActivityTypeTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Run Tier 1 unit tests (`MyBTLEDeviceQueueTest`, `BTSearchForNewDevicesEngineTest`, `ActivityTypeTest`, `MyBTLEDeviceLifecycleTest`).
  - Run `TranslationParityTest` to confirm 100% 9-language parity.
* **Rollback**:
  - Branch isolation (`feature/ATT-6701`) allows clean rollback via `git reset --hard origin/sprint/2026-46.2`.
