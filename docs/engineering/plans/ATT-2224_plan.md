# Stage 3: Implementation Plan - ATT-2224: Robust Bluetooth LE Sensor Discovery: Prevent GATT Queue Deadlock on Missing Optional Characteristics and Reset Stale Connections

**Ticket**: [ATT-2224](https://atrainingtracker.atlassian.net/browse/ATT-2224)  
**Sub-task**: [ATT-2315](https://atrainingtracker.atlassian.net/browse/ATT-2315) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-40.15`  
**Requirement Mapping**: `REQ-CON-018` (*Robust Bluetooth LE Sensor Discovery: GATT Queue Deadlock Prevention and State Reset in BTSearchForNewDevicesEngine*)  
**Test Mapping**: `TST-CON-009` (*Robust Bluetooth LE Sensor Discovery: GATT Queue Deadlock Prevention and State Reset Test*)  
**Branch**: `feature/ATT-2224`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Description & Background

In `BTSearchForNewDevicesEngine.java`, discovering Bluetooth LE peripherals currently fails and deadlocks if a device omits optional GATT characteristics (e.g. Manufacturer Name `0x2A29` in `0x180A` Device Information or Battery Level `0x2A19` in `0x180F` Battery Service).

Forensic analysis confirmed:
1. `service.getCharacteristic(...)` returning `null` is appended directly to `mReadCharacteristicQueue`.
2. Polling `null` from the queue or encountering a `false` return from `gatt.readCharacteristic(characteristic)` halts queue progression without an `else` branch, leaving the discovery process permanently stalled.
3. In `stopAsyncSearch()`, internal maps (`mBTGatts`, `mReadCharacteristicQueue`, `mInformedDevices`, etc.) are never cleared, preventing subsequent search attempts from discovering or reconnecting to previously detected devices.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-CON-018` (*Robust Bluetooth LE Sensor Discovery: GATT Queue Deadlock Prevention and State Reset in BTSearchForNewDevicesEngine*)
* **Test Mapping**: `TST-CON-009` (`TST-CON-009.1` to `TST-CON-009.4`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: The 1,600+ passing unit tests must continue to pass cleanly.
2. **Permission Guarding**: `Manifest.permission.BLUETOOTH_CONNECT` checks must remain on all framework Bluetooth interactions.
3. **Runtime BLE Isolation**: `MyBTLEDevice.java` (`REQ-CON-012`, `REQ-CON-014`) operates independently and remains completely unmodified.
4. **Mandatory Programmatic Pre-Check**: Before modifying production code in Stage 4, `python3 tools/jira_util.py check-gate ATT-2315` must exit with code 0 (`GATE_PASSED: ATT-2315 is Erledigt`).

---

## 4. Proposed Architectural Changes

### Component 1: `BTSearchForNewDevicesEngine.java`
* **Defensive Enqueueing**:
  Introduce `protected void enqueueCharacteristicIfPresent(String address, BluetoothGattService service, UUID charUuid)`:
  - Validates that `service != null`.
  - Queries `BluetoothGattCharacteristic c = service.getCharacteristic(charUuid)`.
  - Appends `c` to `mReadCharacteristicQueue.get(address)` **if and only if** `c != null`.
* **Fail-Forward Queue Progression in `readNextCharacteristic(String address)`**:
  - If `mReadCharacteristicQueue.get(address).poll()` yields `null`, immediately invoke `readNextCharacteristic(address)`.
  - Inside the UI thread runnable:
    ```java
    boolean enqueued = gatt.readCharacteristic(characteristic);
    if (!enqueued) {
        if (DEBUG) Log.w(TAG, "readCharacteristic failed to enqueue for: " + characteristic.getUuid());
        readNextCharacteristic(address);
    }
    ```
* **State Reset Method `resetTrackingState()`**:
  - Centralize cleanup:
    ```java
    protected void resetTrackingState() {
        mBTGatts.clear();
        mReadCharacteristicQueue.clear();
        mInformedDevices.clear();
        mNameMap.clear();
        mManufacturerMap.clear();
        mBatteryPercentage.clear();
    }
    ```
  - Call `resetTrackingState()` in `stopAsyncSearch()` (after disconnecting/closing GATTs) and in `startAsyncSearch()`.

### Component 2: `BTSearchForNewDevicesEngineTest.kt`
* Targeted unit test suite validating:
  - Null-safe enqueueing (`testEnqueue_omitsNullCharacteristics`).
  - Graceful progression across null characteristics and `readCharacteristic() == false` failures (`testReadNextCharacteristic_handlesNullAndReadFailureGracefully`).
  - Complete map reset on search stop enabling rediscovery (`testResetTrackingState_clearsAllMapsAndEnablesRediscovery`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Programmatic Gate 3 Pre-Check
* Verify Gate 3 sign-off via:
  ```bash
  python3 tools/jira_util.py check-gate ATT-2315
  ```

### Step 2: Refactor `BTSearchForNewDevicesEngine.java`
* File: `app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngine.java`
* Implement `enqueueCharacteristicIfPresent`.
* Refactor `onServicesDiscovered` to use `enqueueCharacteristicIfPresent`.
* Refactor `readNextCharacteristic` to advance on null or false read results.
* Implement and wire `resetTrackingState` in `stopAsyncSearch` and `startAsyncSearch`.

### Step 3: Author Unit Tests in `BTSearchForNewDevicesEngineTest.kt`
* File: `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngineTest.kt`
* Implement test cases covering `TST-CON-009.1`, `TST-CON-009.2`, and `TST-CON-009.3`.

### Step 4: Execute Targeted Unit Tests
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.search_new.BTSearchForNewDevicesEngineTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests pass with 100% success rate, followed by full clean-room suite regression in Stage 5.
* **Rollback**: Work is isolated on `feature/ATT-2224`. If unexpected regressions occur, branch can be cleanly reverted prior to sprint merge.
