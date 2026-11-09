# Stage 5 Walkthrough: ATT-6245 — Paired BLE Peripheral Scan-Assisted Connection & Public/Random MAC Address Classification

**Ticket**: [ATT-6245](https://rainerblind.atlassian.net/browse/ATT-6245)  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile (Gear, ANT+/BLE & PMC)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Requirement Mapping**: `REQ-SET-099` (*Paired BLE Peripheral Scan-Assisted Connection & Public/Random MAC Address Classification*)  
**Test Spec ID**: `TST-SET-099`  
**Branch**: `feature/ATT-6245`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-09  

---

## 1. Problem & Root Cause Resolution

Following ATT-5489, paired BLE peripherals using Public IEEE MAC addresses with first byte in `0xC0..0xFC` (e.g. Intel controller `D0:C6:37:33:67:AA` emulating `ATT-HRM`/`ATT-Pwr` via `tools/replay_workout.py`) failed to connect during `MyBTLEDevice.startSearching()`.
1. **Flawed Address Heuristic**: `isRandomStaticAddress()` bitwise check `(firstOctet & 0xC0) == 0xC0` misclassified 25% of IEEE Public MACs as `ADDRESS_TYPE_RANDOM`. Per Bluetooth Core Spec Vol 6 Part B §1.3 & §2.3, address type is transmitted out-of-band in the 1-bit `TxAdd` link layer header.
2. **Scan Assistance Handle Drop**: `startSearching()` immediately opened a speculative direct `connectGatt(...)`. When active scan assistance discovered the authentic peripheral handle with true hardware address type, `onScanResult()` checked `if (mBluetoothGatt == null)` and discarded the scan-resolved handle, leaving the misconfigured connection to time out.

---

## 2. Construction Summary

1. **Deferred Direct Connect (`REQ-SET-099.1`, `REQ-SET-099.2`)**:
   - In `MyBTLEDevice.startSearching()`, deferred speculative direct `connectGatt` when scan assistance successfully starts (`mIsScanning == true`), allowing `onScanResult()` to connect via the hardware-resolved `BluetoothDevice`.
   - Preserved immediate direct connect as guaranteed fallback when scan assistance cannot start (`!mIsScanning`, e.g. missing scan permission).
2. **Authoritative Scan Handoff & Superseded GATT Teardown (`REQ-SET-099.3`)**:
   - In `MyBTLEDevice.onScanResult()`, safely disconnects and closes any pre-existing speculative GATT handle under `mGattLock` before establishing connection via `dev.connectGatt(...)`.
3. **Stale Callback Defense (`REQ-SET-099.4`)**:
   - In `MyBTLEDevice.onConnectionStateChange()`, added guard `if (mBluetoothGatt != null && gatt != mBluetoothGatt) return;` under `mGattLock`, preventing closed/superseded speculative instances from clobbering active state or scheduling reconnects.
4. **Unit & Contract Test Suite (`TST-SET-099.1`–`.4`)**:
   - In `MyBTLEDeviceAddressAndSearchTest.kt`, added test cases covering Public IEEE MACs with `0xC0..0xFC` OUI, Random Static MACs, speculative GATT replacement with stale callback defense, and fallback direct connect. Removed artificial `device.setGatt(null)` in existing scan discovery test.

---

## 3. Verification & Test Evidence

### 3.1 Tier 1 Targeted Test Scope
* **Scope**: `com.atrainingtracker.banalservice.devices.bluetooth_le.*` + `com.atrainingtracker.trainingtracker.build.*`
* **Command**:
  ```bash
  python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --max-workers=2 \
      --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" \
      --tests "com.atrainingtracker.trainingtracker.build.*"
  ```
* **Result**: **122 tests completed, 0 failures (100% PASS)** in 35s.

### 3.2 Requirement Governance & Parity
* **Command**: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-45.13`
* **Result**: **PASS** (100% parity across living doc master index tables, `REQ-SET-099` & `TST-SET-099` transitioned to `[Verified]`).

---

## 4. Traceability Matrix

| Requirement | Description | Test Case | Status |
| :--- | :--- | :--- | :--- |
| `REQ-SET-099.1` | Deferred connect & scan-resolved connection | `TST-SET-099.1`, `TST-SET-099.2` | **Verified** |
| `REQ-SET-099.2` | Fallback direct connect without scan | `TST-SET-099.4` | **Verified** |
| `REQ-SET-099.3` | Authoritative scan handoff & GATT teardown | `TST-SET-099.3` | **Verified** |
| `REQ-SET-099.4` | Stale GATT callback isolation guard | `TST-SET-099.3` | **Verified** |
| `REQ-SET-099.5` | Public & Random Static interoperability | `TST-SET-099.1`, `TST-SET-099.2` | **Verified** |
