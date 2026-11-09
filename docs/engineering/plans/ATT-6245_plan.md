# Stage 3: Implementation Plan - ATT-6245: Public IEEE MAC addresses with 0xC0..0xFC OUI misclassified as Random Static

**Ticket**: [ATT-6245](https://rainerblind.atlassian.net/browse/ATT-6245)  
**Sub-task**: [ATT-6304](https://rainerblind.atlassian.net/browse/ATT-6304) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile (Gear, ANT+/BLE & PMC)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Requirement Mapping**: `REQ-SET-099`  
**Test Mapping**: `TST-SET-099`  
**Branch**: `feature/ATT-6245`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-09  

---

## 1. Problem Description & Background
BLE peripherals advertising with Public IEEE MAC addresses with 0xC0..0xFC OUIs (e.g. `D0:C6:37:33:67:AA`) are misclassified by `isRandomStaticAddress()` as `ADDRESS_TYPE_RANDOM`. Concurrently, immediate speculative direct `connectGatt` populates `mBluetoothGatt`, causing `onScanResult` to drop the scan-resolved handle and time out.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SET-099` (*Paired BLE Peripheral Scan-Assisted Connection & Public/Random MAC Address Classification*)
* **Test Mapping**: `TST-SET-099` (*Paired BLE Peripheral Scan-Assisted Connection Test Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Universal BLE Connectivity**: Public IEEE and Random Static peripherals connect reliably.
2. **Scan Lifecycle Safety**: Scan stops on connect, timeout, disconnect, or teardown.
3. **Stale Callback Isolation**: Discarded speculative GATT instances cannot trigger disconnection state transitions.
4. **Code Lock**: Production code modification locked until Gate 3 sign-off (Rule 4).

---

## 4. Proposed Architectural Changes

### Component 1: `MyBTLEDevice.java` (SWE.2 Connection Engine)
* **Deferred Direct Connect (`REQ-SET-099.1`, `REQ-SET-099.2`)**:
  * In `startSearching()`, invoke `startScanAssistance(btAdapter)`.
  * If `mIsScanning == true`, defer direct `connectGatt` to let `onScanResult` connect with the authoritative `result.getDevice()`.
  * If `!mIsScanning` (e.g. missing permissions), post fallback direct `device.connectGatt(...)`.
* **Authoritative GATT Handoff (`REQ-SET-099.3`)**:
  * In `mScanCallback.onScanResult()`, when target `mAddress` is matched during `State.SEARCHING`:
    * Stop scan assistance.
    * Under `mGattLock`, safely disconnect/close any pre-existing speculative `mBluetoothGatt`.
    * Assign `mBluetoothGatt = dev.connectGatt(mContext, false, mGattCallback, BluetoothDevice.TRANSPORT_LE)`.
* **Stale Callback Guard (`REQ-SET-099.4`)**:
  * In `mGattCallback.onConnectionStateChange()`, check `if (gatt != mBluetoothGatt) return;` under `mGattLock`.

### UI Consistency (Rule 23)
* No UI components or layouts are modified.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Defer Speculative Connect & Authoritative Scan Handoff (`REQ-SET-099.1`–`.3`)
* Files: `app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/MyBTLEDevice.java`
* Defer direct connect when `mIsScanning == true`; fall back to direct connect only when `!mIsScanning`.
* In `onScanResult()`, safely close existing speculative `mBluetoothGatt` during `State.SEARCHING` and connect via `dev.connectGatt(...)`.

### Step 2: Stale Callback Defense (`REQ-SET-099.4`)
* Files: `app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/MyBTLEDevice.java`
* In `mGattCallback.onConnectionStateChange`, ignore callbacks where `gatt != mBluetoothGatt`.

### Step 3: Unit & Contract Tests (`TST-SET-099.1`–`.4`)
* Files: `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/MyBTLEDeviceAddressAndSearchTest.kt`
* Add test cases:
  * `testScanAssistance_connectsPublicAddressWithC0ToFCOui` (`TST-SET-099.1`)
  * `testScanAssistance_connectsRandomStaticAddress` (`TST-SET-099.2`)
  * `testScanAssistance_replacesExistingGattAndIgnoresStaleCallbacks` (`TST-SET-099.3`)
  * `testStartSearching_scanFailsToStart_fallsBackToDirectConnect` (`TST-SET-099.4`)
* Remove artificial `device.setGatt(null)` in existing test.

### Step 4: Verification Suite (`TST-SET-099.5`)
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
