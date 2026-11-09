# Stage 2: Requirement & Test Specification - ATT-6245: Public IEEE MAC addresses with 0xC0..0xFC OUI misclassified as Random Static

**Ticket**: [ATT-6245](https://rainerblind.atlassian.net/browse/ATT-6245)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile (Gear, ANT+/BLE & PMC)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Requirement Mapping**: `REQ-SET-099` (*Paired BLE Peripheral Scan-Assisted Connection & Public/Random MAC Address Classification*)  
**Test Spec ID**: `TST-SET-099`  
**Branch**: `feature/ATT-6245`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-09  

---

## 1. Requirement Specification (REQ-SET-099)

### 1.1 Problem Statement & Rationale
Per Bluetooth Core Specification Vol 6 Part B §1.3 & §2.3, BLE address type is transmitted out-of-band via the 1-bit `TxAdd` link layer header flag. Checking `(firstOctet & 0xC0) == 0xC0` misclassifies 25% of IEEE Public MACs (OUI `0xC0..0xFC`, e.g. Intel `D0:C6:37`) as `ADDRESS_TYPE_RANDOM`, causing peripherals to ignore connection requests on API 33+. Concurrently, immediate direct `connectGatt()` populates `mBluetoothGatt`, causing `onScanResult()` to discard the hardware-verified `BluetoothDevice` handle.

### 1.2 Functional & Architectural Requirements
1. When `startSearching()` starts scan assistance successfully (`mIsScanning == true`), direct speculative `connectGatt` SHALL be deferred in favor of scan-resolved connection via `onScanResult()`.
2. When scan assistance cannot start (e.g. missing scan permission), `startSearching()` SHALL fall back to direct `device.connectGatt(...)`.
3. In `onScanResult()`, when discovering target `mAddress` while `mState == State.SEARCHING`, the system SHALL connect using `result.getDevice().connectGatt(...)`, cleanly closing any prior speculative `mBluetoothGatt`.
4. `onConnectionStateChange()` SHALL ignore callbacks from stale GATT instances (`if (gatt != mBluetoothGatt) return;`).
5. Connection pipeline SHALL reliably connect both Public IEEE MACs (`0xC0..0xFC` OUI, e.g. `D0:C6:37:33:67:AA`) and Random Static MACs (`C6:CC:E3:F5:F0:37`).

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* a paired peripheral with Public IEEE MAC with OUI in `0xC0..0xFC` (`D0:C6:37:33:67:AA`),
  *When* `startSearching()` runs with scan permissions,
  *Then* scan assist discovers the device and connects via `result.getDevice().connectGatt(...)` without timing out.
* *Given* a paired peripheral with Random Static MAC (`C6:CC:E3:F5:F0:37`),
  *When* `startSearching()` runs,
  *Then* scan assist discovers and connects the device handle.
* *Given* an active search with an existing speculative `mBluetoothGatt`,
  *When* `onScanResult()` matches target `mAddress`,
  *Then* the speculative GATT is closed, replaced with scan-resolved handle, and stale callbacks are ignored.

### 1.4 System Invariants
Scan lifecycle teardown, configurable search timeout, and sensor streaming remain preserved.

---

## 2. Test Specification (TST-SET-099)

### Test Case 1: `testScanAssistance_connectsPublicAddressWithC0ToFCOui` (`TST-SET-099.1`)
* **Scope**: Unit Test (`MyBTLEDeviceAddressAndSearchTest.kt`)
* **Action**: Start search for Public MAC `D0:C6:37:33:67:AA`; emit scan result.
* **Expected Result**: Scan result connects via `result.getDevice().connectGatt(...)`.

### Test Case 2: `testScanAssistance_connectsRandomStaticAddress` (`TST-SET-099.2`)
* **Scope**: Unit Test (`MyBTLEDeviceAddressAndSearchTest.kt`)
* **Action**: Start search for Random Static MAC `C6:CC:E3:F5:F0:37`; emit scan result.
* **Expected Result**: Connects via scan-resolved handle.

### Test Case 3: `testScanAssistance_replacesExistingGattAndIgnoresStaleCallbacks` (`TST-SET-099.3`)
* **Scope**: Unit Test (`MyBTLEDeviceAddressAndSearchTest.kt`)
* **Action**: Trigger scan result when `mBluetoothGatt` is non-null; emit callback from stale GATT.
* **Expected Result**: Old GATT closed; new GATT assigned; stale callback ignored.

### Test Case 4: `testStartSearching_scanFailsToStart_fallsBackToDirectConnect` (`TST-SET-099.4`)
* **Scope**: Unit Test (`MyBTLEDeviceAddressAndSearchTest.kt`)
* **Action**: Start search when scanner is null or permission denied.
* **Expected Result**: Direct `connectGatt` is invoked as fallback.

### Test Case 5: Tier 1 Targeted BLE Regression Suite (`TST-SET-099.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-099.1` | Unit | `MyBTLEDevice` (Scan Assist) | `REQ-SET-099.1`, `REQ-SET-099.3`, `REQ-SET-099.5` | Specified |
| `TST-SET-099.2` | Unit | `MyBTLEDevice` (Scan Assist) | `REQ-SET-099.1`, `REQ-SET-099.5` | Specified |
| `TST-SET-099.3` | Unit | `MyBTLEDevice` (GATT Handoff) | `REQ-SET-099.3`, `REQ-SET-099.4` | Specified |
| `TST-SET-099.4` | Unit | `MyBTLEDevice` (Fallback) | `REQ-SET-099.2` | Specified |
| `TST-SET-099.5` | Regression | Bluetooth LE Package | `REQ-PRO-001` | Specified |
