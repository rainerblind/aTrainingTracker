# Stage 1 Analysis: ATT-2224 - Robust Bluetooth LE Sensor Discovery: Prevent GATT Queue Deadlock on Missing Optional Characteristics and Reset Stale Connections

**Ticket**: [ATT-2224](https://atrainingtracker.atlassian.net/browse/ATT-2224)  
**Sub-task**: [ATT-2313](https://atrainingtracker.atlassian.net/browse/ATT-2313) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-40.15`  
**Branch**: `feature/ATT-2224`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Statement & Motivation

During Bluetooth LE sensor discovery (pairing search for Cycling Power Meters, Heart Rate straps, Cycling Speed & Cadence sensors, etc.), the discovery engine (`BTSearchForNewDevicesEngine.java`) silently hangs and ceases all communication if a peripheral lacks optional GATT characteristics (such as Manufacturer Name `0x2A29` or Battery Level `0x2A19`).

This issue prominently manifests when interacting with Linux/BlueZ-based BLE sensor simulators, smart trainers, or peripherals from third-party manufacturers that implement only mandatory telemetry characteristics without optional metadata descriptors. Once the discovery engine encounters such a device:
1. The characteristic read queue halts indefinitely without notifying the UI or caller.
2. Subsequent critical characteristics (e.g. Cycling Power Feature `0x2A65`) are never read.
3. The callback `newDeviceFound` is never invoked for the peripheral.
4. Furthermore, when the user stops and restarts the search, stale connection maps (`mBTGatts`, `mReadCharacteristicQueue`, `mInformedDevices`) retain cached state, permanently blocking rediscovery of the device.

---

## 2. Root Cause Analysis (Forensic Investigation)

A comprehensive code audit of `BTSearchForNewDevicesEngine.java` identified three critical defects:

### 2.1 Unchecked Null Addition to Read Queue in `onServicesDiscovered`
In `BTSearchForNewDevicesEngine.java` lines 119–141:
```java
btGattService = gatt.getService(BluetoothConstants.UUID_SERVICE_DEVICE_INFORMATION);
if (btGattService != null) {
    mReadCharacteristicQueue.get(address).add(btGattService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_MANUFACTURER_NAME));
}
```
If a peripheral exposes the `0x180A` (Device Information) or `0x180F` (Battery) service, but omits the specific characteristic (`0x2A29` Manufacturer Name or `0x2A19` Battery Level), `btGattService.getCharacteristic(...)` returns `null`. This `null` reference is unconditionally appended to `mReadCharacteristicQueue.get(address)`.

### 2.2 Permanent Queue Deadlock in `readNextCharacteristic`
In `BTSearchForNewDevicesEngine.java` lines 370–385:
```java
if (!mReadCharacteristicQueue.get(address).isEmpty()) {
    final BluetoothGattCharacteristic characteristic = Objects.requireNonNull(mReadCharacteristicQueue.get(address)).poll();
    if (characteristic != null) {
        ...
        gatt.readCharacteristic(characteristic);
    }
}
```
* **Defect A (Polled Null Stall)**: When a `null` element is polled from the queue, `if (characteristic != null)` evaluates to `false`. Because there is no `else` branch, the method exits without initiating a read and without recursing to `readNextCharacteristic(address)`. The queue is left partially processed, and execution deadlocks forever.
* **Defect B (Failed Read Request)**: In Android's BLE framework, `gatt.readCharacteristic(characteristic)` returns a boolean indicating whether the read command was successfully enqueued in the lower-level Bluetooth stack. If it returns `false` (e.g. peripheral busy, connection terminating, or stack congestion), Android's `BluetoothGattCallback.onCharacteristicRead` will **never** be invoked. Since advancing the queue relies on `onCharacteristicRead`, a `false` return value permanently stalls the discovery pipeline.

### 2.3 Stale Connection Map Leaks in `stopAsyncSearch` & `startAsyncSearch`
In `BTSearchForNewDevicesEngine.java` lines 245 and 335–351:
* In `onScanResult`:
  ```java
  if (!mBTGatts.containsKey(device.getAddress())) { ... }
  ```
* In `stopAsyncSearch`: The GATT connections are closed and disconnected asynchronously, but `mBTGatts`, `mReadCharacteristicQueue`, `mInformedDevices`, `mNameMap`, `mManufacturerMap`, and `mBatteryPercentage` are **never cleared**.
* When an athlete triggers a new scan, `mBTGatts.containsKey(device.getAddress())` evaluates to `true` for all previously seen devices, preventing the scanner from connecting to them again.
* Similarly, `mInformedDevices` retains previous addresses, preventing `newDeviceFound` from firing on subsequent scans.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Guard characteristic additions in `onServicesDiscovered` so only non-null `BluetoothGattCharacteristic` instances enter `mReadCharacteristicQueue`.
  2. Implement robust queue progression in `readNextCharacteristic`:
     - If a polled characteristic is `null`, immediately advance to the next item via `readNextCharacteristic(address)`.
     - Check the boolean return value of `gatt.readCharacteristic(...)`; if `false`, immediately advance via `readNextCharacteristic(address)`.
  3. Ensure that when all characteristics are processed, `newDeviceFound` is reliably invoked for devices even if optional characteristics were missing or unreadable.
  4. Ensure `stopAsyncSearch()` and `startAsyncSearch()` cleanly reset and purge all connection and state maps (`mBTGatts`, `mReadCharacteristicQueue`, `mInformedDevices`, `mNameMap`, `mManufacturerMap`, `mBatteryPercentage`).
  5. Provide automated unit tests verifying null-safe queue progression, read failure recovery, and map reset behavior.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Do not alter `MyBTLEDevice.java` or established runtime telemetry parsing (`REQ-CON-012`, `REQ-CON-014`).
  2. Do not modify Bluetooth scan filter settings or Bluetooth LE advertisement packet parsing.
  3. Do not modify ANT+ device search engines (`AntSearchForNewDevicesEngine`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Audit Finding**: Net-new requirement (`REQ-CON-015`). No existing requirements modified.
* **Context**: `REQ-CON-012` established GATT lifecycle guards and queue safety for active runtime devices (`MyBTLEDevice.java`). `BTSearchForNewDevicesEngine.java` serves an analogous role during the pairing/discovery phase and requires equivalent null-safe queue invariants and lifecycle cleanup.

---

## 5. Architectural Strategy & High-Level Solution

1. **Defensive Queue Enqueueing in `onServicesDiscovered`**:
   Create a helper method `enqueueCharacteristicIfPresent(String address, BluetoothGattService service, UUID charUuid)`:
   ```java
   if (service != null) {
       BluetoothGattCharacteristic c = service.getCharacteristic(charUuid);
       if (c != null) {
           mReadCharacteristicQueue.get(address).add(c);
       }
   }
   ```
2. **Resilient Queue Draining in `readNextCharacteristic`**:
   ```java
   if (!mReadCharacteristicQueue.get(address).isEmpty()) {
       final BluetoothGattCharacteristic characteristic = mReadCharacteristicQueue.get(address).poll();
       if (characteristic != null) {
           mHandler.post(new Runnable() {
               @Override
               public void run() {
                   boolean success = gatt.readCharacteristic(characteristic);
                   if (!success) {
                       Log.w(TAG, "readCharacteristic failed to enqueue, skipping characteristic: " + characteristic.getUuid());
                       readNextCharacteristic(address);
                   }
               }
           });
       } else {
           readNextCharacteristic(address);
       }
   }
   ```
3. **State Cleanup on Search Stop & Start**:
   Provide a centralized `resetTrackingState()` method invoked on `stopAsyncSearch()` and `startAsyncSearch()`:
   - Clear `mBTGatts` (after disconnecting/closing).
   - Clear `mReadCharacteristicQueue`.
   - Clear `mInformedDevices`.
   - Clear `mNameMap`, `mManufacturerMap`, `mBatteryPercentage`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Fully compliant with Bluetooth SIG profiles (Bike Power, Bike Speed & Cadence, Heart Rate).
  2. BLE permission checks (`Manifest.permission.BLUETOOTH_CONNECT`) remain strictly guarded.
  3. 100% clean-room test suite pass rate without regressions.
* **Risk Rating**: **LOW**
  - Scope is strictly confined to `BTSearchForNewDevicesEngine.java`.
  - Fixes are defensive and eliminate unhandled deadlock edge cases without modifying protocol logic.
