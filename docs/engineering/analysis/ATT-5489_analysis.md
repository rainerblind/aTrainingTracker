# Stage 1 Analysis: ATT-5489 - MyBTLEDevice: Paired BLE sensors with Random Static MAC addresses fail to connect (addr_type=public) + configurable BLE search timeout in Expert Settings

**Ticket**: [ATT-5489](https://rainerblind.atlassian.net/browse/ATT-5489)  
**Parent Epic**: None  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.7`  
**Branch**: `feature/ATT-5489`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-05  

---

## 1. Problem Statement & Motivation
Paired BLE sensors with Random Static MAC addresses (e.g. Magene Cadence `C6:CC:E3:F5:F0:37`, Speed `E8:4D:74:07:F4:DD`) fail to connect during paired device search (`DeviceManager.startSearchForPairedDevices()`), repeatedly timing out after 10s. Furthermore, `SEARCH_PERIOD` is hardcoded to 10s in `MyBTLEDevice.java`, causing up to 30s+ head-of-line blocking in sequential device search when paired peripherals are offline.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Address Type Defaulting to Public (`addr_type=public`)**: `MyBTLEDevice.java:355` calls `BluetoothAdapter.getRemoteDevice(mAddress)`. Because BLE sensors are paired in SQLite without OS PIN bonding (`Bonded devices: 0`), Android's `BluetoothRemoteDevices` cache is empty after reboot/BT toggle (`device_type=Unknown`). Calling `getRemoteDevice()` defaults to `ADDRESS_TYPE_PUBLIC` (`RxAdd = 0`), which peripherals advertising with Random Static Addresses (`TxAdd = 1`, bits 47..46 = `11b`, `(firstOctet & 0xC0) == 0xC0`) ignore per Bluetooth Core Spec Vol 6 Part B §1.3.2.1.
2. **Hardcoded Search Period & Head-of-Line Blocking**: `MyBTLEDevice.SEARCH_PERIOD = 10000L` is hardcoded. Offline sensors consume full 10s per try before `DeviceManager.searchForNextRemoteDevice()` moves forward, putting motion-activated sensors to sleep.
* **Independent Peer Review & Verification**:
  - Confirmed `MyBTLEDevice.java:355-368` calls `getRemoteDevice(mAddress)` directly without address type resolution.
  - Confirmed `BluetoothAdapter.getRemoteLeDevice(address, addressType)` exists on API 33+ (`android-34.jar`), supporting `BluetoothDevice.ADDRESS_TYPE_RANDOM` (1) and `ADDRESS_TYPE_PUBLIC` (0).
  - Confirmed `SensorSearchPreferences` and `SearchSettingsDialog` lack search timeout slider; `TrainingApplication` lacks `getBtleSearchPeriodMs()`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Implement `isRandomStaticAddress(String)` and API 33+ `getRemoteLeDevice(address, addressType)` in `MyBTLEDevice`.
  - Add filtered `BluetoothLeScanner` scan assistance in `MyBTLEDevice` to warm Android's BLE cache on API < 33 and accelerate connection, with robust cleanup on connect/timeout/shutdown.
  - Introduce `SP_BTLE_SEARCH_PERIOD_SEC` (`"btleSearchPeriodSec"`, 3..15s, default 5s) across `TrainingApplication`, `SensorSearchPreferences`, `SensorSearchTuningSection`, `SearchSettingsDialog`, and `prefs_search.xml` with 9-language strings.
* **Out-of-Scope Non-Goals**:
  - Modifying ANT+ device search timing or `BTSearchForNewDevicesEngine` discovery logic.
  - Changing OS bonding or pairing persistence in `Devices.db`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-CON-019` (GATT Connection & BLE Discovery) and `REQ-UI-154` (Search Settings Dialog).
* **Historical Origin & Commit Trace**: `REQ-CON-019` added in Sprint 2026-41.4 (`ATT-2773`), refined in Sprint 2026-43.4 (`ATT-4449`). `REQ-UI-154` added in Sprint 2026-41.2 (`ATT-2780`).
* **Root Reason for Existing Formulation**: `REQ-CON-019` mandated `device.connectGatt(..., TRANSPORT_LE)` to fix auto-transport failures on Android 12+ but relied on `getRemoteDevice()`. `REQ-UI-154` exposed search retries (1..5) but omitted duration.
* **Preservation of Core Invariants**: `TRANSPORT_LE`, `mGattLock` synchronization, sequential queue search in `DeviceManager`, and 7 existing `SharedPreferences` keys remain intact.

---

## 5. Architectural Strategy & High-Level Solution
1. **Address Type & Scan Assist (`MyBTLEDevice`)**: Use `getRemoteLeDevice(mAddress, ADDRESS_TYPE_RANDOM)` on API 33+ if `isRandomStaticAddress(mAddress)`; start filtered `BluetoothLeScanner` scan if `BLUETOOTH_SCAN` permitted; guarantee `stopScan()` on connect, timeout, and `shutDown()`.
2. **Configurable Timeout**: Add `TrainingApplication.getBtleSearchPeriodMs()` (default 5s, range 3..15s); pass to `mSearchTimeoutRunnable`; expose in `SensorSearchTuningSection` and `SearchSettingsDialog`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression in ANT+ and bonded/public BLE sensor connections (`TRANSPORT_LE` preserved).
  2. Strict scan cleanup: zero leaked `BluetoothLeScanner` scans on timeout or service shutdown.
  3. Complete 9-language translation parity for new string resources.
* **Risk Rating**: **LOW** — targeted BLE device resolution fix and non-breaking preference addition with safe fallbacks.
