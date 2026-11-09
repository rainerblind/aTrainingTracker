# Stage 1 Analysis: ATT-6245 - Public IEEE MAC addresses with 0xC0..0xFC OUI misclassified as Random Static

**Ticket**: [ATT-6245](https://rainerblind.atlassian.net/browse/ATT-6245)  
**Sub-task**: [ATT-6301](https://rainerblind.atlassian.net/browse/ATT-6301) (`[Analysis]`)  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile (Gear, ANT+/BLE & PMC)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Branch**: `feature/ATT-6245`  
**Requirement Mapping**: `REQ-SET-099` (`docs/requirements/req_settings_profiles.md`)  
**Test Mapping**: `TST-SET-099` (`docs/tests/tst_settings_profiles.md`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-09  

---

## 1. Problem Statement & Motivation
Following ATT-5489 (commit `22d8c54d`), paired BLE sensors using Public IEEE MAC-48 addresses with first byte in `0xC0..0xFC` (e.g. Intel controller `D0:C6:37:33:67:AA` emulating `ATT-HRM` / `ATT-Pwr` via `tools/replay_workout.py`) fail to connect during `MyBTLEDevice.startSearching()`. Connection attempts consistently time out after the configured search period.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
Two interacting defects in `MyBTLEDevice.java`:
1. **Flawed Address Type Heuristic (`isRandomStaticAddress`)**:
   - Per Bluetooth Core Spec Vol 6, Part B, Sec 1.3 & 2.3, address type (`Public` vs `Random`) is transmitted out-of-band in the 1-bit `TxAdd` Link Layer PDU header flag, not within MAC octets.
   - The `(firstOctet & 0xC0) == 0xC0` rule identifies Static Random addresses *only* when `TxAdd = 1`. In IEEE 802 Public MAC space (`TxAdd = 0`), ~25% of all manufacturer OUIs (`0xC0..0xFC`, such as Intel `D0:C6:37`) have bits 7..6 set.
   - On API 33+, `startSearching()` passes `ADDRESS_TYPE_RANDOM` to `btAdapter.getRemoteLeDevice()`, causing the controller's Filter Accept List to filter for `[random]`. Because the peripheral broadcasts with `TxAdd = 0 (Public)`, the BLE controller ignores its advertisements.
2. **Scan Assistance Handle Dropped by Stale Non-Null `mBluetoothGatt` Guard**:
   - `startSearching()` immediately calls `mHandler.post(...)` which assigns `mBluetoothGatt = device.connectGatt(...)`.
   - When active scan assistance discovers the authentic peripheral handle via `ScanResult.getDevice()` (~1.7s later), `mScanCallback.onScanResult()` checks `if (mState == State.SEARCHING && mBluetoothGatt == null)`. Because `mBluetoothGatt != null`, the scan-resolved handle is discarded and `stopScanAssistance()` is called, leaving the wrong-address-type connection to time out.
* **Independent Peer Review & Verification**:
   - Verified live code in `MyBTLEDevice.java`: `startSearching()` starts both scan assist and immediate direct connect; `onScanResult()` requires `mBluetoothGatt == null`.
   - In `MyBTLEDeviceAddressAndSearchTest.kt` (L245), the test manually forced `device.setGatt(null)`, which masked the bug in production.
   - Furthermore, `mGattCallback.onConnectionStateChange()` lacks an instance identity check (`gatt != mBluetoothGatt`), so closing a speculative connection could inadvertently trigger premature disconnection cleanup if not guarded.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Defer direct `connectGatt` when scan assistance successfully starts (`mIsScanning == true`), falling back to direct connect only when scan assistance cannot be started (e.g. missing scan permissions).
  * In `onScanResult()`, safely connect via `result.getDevice().connectGatt(...)` during `State.SEARCHING`, even if a speculative/fallback `mBluetoothGatt` exists (safely closing any prior instance).
  * Guard `onConnectionStateChange` against stale callbacks (`if (gatt != mBluetoothGatt) return;`).
  * Add unit tests in `MyBTLEDeviceAddressAndSearchTest.kt` verifying connection handoff for both Public (`0xC0..0xFC` OUI) and Random Static peripherals without artificial `setGatt(null)`.
  * Shard isolation: map requirement to `REQ-SET-099` and test to `TST-SET-099` (Rule 9).
* **Out-of-Scope Non-Goals**:
  * Altering BLE service/characteristic discovery or GATT notification parsers.
  * Modifying ANT+ device connections or UI sensor scan screens.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-CON-023` behavior, mapped to `REQ-SET-099` (`docs/requirements/req_settings_profiles.md`) for sprint shard isolation.
* **Historical Origin & Commit Trace**: `ATT-5489` (commit `22d8c54d`), which introduced `isRandomStaticAddress()` and `startScanAssistance()`.
* **Root Reason for Existing Formulation**: ATT-5489 aimed to fix Random Static BLE connections by adding scan assist and API 33 `getRemoteLeDevice(RANDOM)`, but concurrently ran blind direct `connectGatt()`, which clobbered the scan assist handoff and misclassified Public OUIs.
* **Preservation of Core Invariants**: Scan assistance lifecycle, configurable timeout, clean teardown on disconnect/shutdown, and bonding compatibility remain strictly intact.

---

## 5. Architectural Strategy & High-Level Solution
1. **Authoritative Scan Handle Connection**:
   - In `startSearching()`, start scan assistance. If `mIsScanning == true`, do not trigger immediate speculative direct connect; allow `onScanResult` to connect with hardware-verified `result.getDevice()`.
   - If `!mIsScanning` (e.g. permission denied), fall back to direct `device.connectGatt()`.
2. **Defensive GATT Handoff & Stale Callback Guard**:
   - In `onScanResult()`, if `mState == State.SEARCHING`, close any existing speculative GATT handle and establish connection via `result.getDevice().connectGatt(...)`.
   - In `onConnectionStateChange()`, ignore events if `gatt != mBluetoothGatt`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Both Public IEEE and Random Static BLE peripherals connect reliably.
  2. Zero regression in sensor streaming, reconnect backoff, or search timeout.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** (Targeted fix confined to `MyBTLEDevice.java` connection initialization; high testability with Robolectric/MockK).
