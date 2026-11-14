# Stage 1 Analysis: ATT-7755 - MyBTLEDevice Mid-Workout BLE Disconnect Reconnection Abandonment

**Ticket**: [ATT-7755](https://rainerblind.atlassian.net/browse/ATT-7755)  
**Sub-task**: None (`[Analysis]`)  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Branch**: `feature/ATT-7755`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
Mid-workout BLE disconnect (e.g. Polar H10 HRM, outdoor run #2175, RF body-shadowing LSTO `HCI reason = 8`) permanently abandons reconnection if BLE advertisement is not detected during a single 5s search window. The peripheral remains stranded in `State.SEARCHING` with `mBluetoothGatt = null`, no active scanner, and zero scheduled retry timers for the remainder of the workout (38+ minutes lost).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- On disconnect, `onConnectionStateChange` posts `mReconnectRunnable`, which invokes `startSearching()`.
- `startSearching()` cancels `mReconnectRunnable`, schedules `mSearchTimeoutRunnable` (5s), and starts scan assistance (`mIsScanning = true`), which suppresses direct GATT connection (`if (!mIsScanning)`).
- If no advertisement is received within 5s, `mSearchTimeoutRunnable` calls `stopScanAssistance()`, `disconnectFromGatt()` (clearing `mBluetoothGatt = null`), and `notifyStopSearching(false)`.
- `DeviceManager.mSearchingStoppedForOneReceiver` ignores `notifyStopSearching(false)` during active workouts (`cMyRemoteDeviceCurrentlySearchingFor == null`). No runnable is re-scheduled, leaving the device dead.
- *Independent Peer Review & Verification*: Verified against `MyBTLEDevice.java` (lines 249–343, 403–436, 456–506, 644–704) and `DeviceManager.java` (lines 133–157). Confirmed single timeout clears all reconnect logic. Confirmed Android BLE scan quota (5 scans / 30s) can silently disable `BluetoothLeScanner` (`onError(): Unregistering the app`).

---

## 3. User Scope Grounding (ATT-1250)
- **In-Scope Goals**:
  1. Continuous mid-session / active-workout reconnection in `MyBTLEDevice` (exponential backoff across timeouts).
  2. Hardware-offloaded `autoConnect = true` for known/session-connected peripherals.
  3. Scan rate quota limiter (max 4 scans / 30s) and immediate `connectGatt` fallback on throttle / `onScanFailed`.
  4. Robustness regression test suite in `BLEReconnectRobustnessTest.kt`.
- **Out-of-Scope Non-Goals**: Startup search queue redesign in `DeviceManager`; ANT+ reconnect changes; UI/settings modifications.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: `REQ-CON-020` & `REQ-CON-023` in `MyBTLEDevice.java`.
- **Historical Origin & Commit Trace**: `ATT-4007` (`e84383ee`), `ATT-5489` (`22d8c54d`), `ATT-6245` (`99164ce7`).
- **Root Reason for Existing Formulation**: `mSearchTimeoutRunnable` called `notifyStopSearching(false)` so `DeviceManager` could bound startup discovery tries (`mMyRemoteDeviceTries`) for offline sensors to prevent battery drain.
- **Preservation of Core Invariants**: Initial startup search for never-seen offline sensors continues to terminate cleanly after configured retries; only session-connected / active-workout devices persist reconnection.

---

## 5. Change-Request Impact Analysis (SUP.10)
- **Affected Requirements**: Refines `REQ-CON-020` & `REQ-CON-023`; introduces `REQ-CON-036`.
- **Affected Architecture Elements**: `MyBTLEDevice.java` (`mHasConnectedInSession`, `mSearchTimeoutRunnable`, scan quota limiter), `BLEReconnectRobustnessTest.kt`.
- **Verification Impact & Test Surface**: `BLEReconnectRobustnessTest.kt`, `MyBTLEDeviceAddressAndSearchTest.kt`, targeted package test `--tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*"`.
- **Risk Assessment & Invariants**: Low risk; localized to `MyBTLEDevice` state machine. Invariant: offline sensor startup battery conservation strictly preserved.

---

## 6. Architectural Strategy & High-Level Solution
- Track `mHasConnectedInSession` (set `true` on `CONNECTED_WITH_SERVICE`, reset on `shutDown()`) and workout state (`TrackingMode.TRACKING` / `PAUSED`).
- In `mSearchTimeoutRunnable`: if `!isReceivingData()` and device was connected in session / workout active, do NOT abandon. Stop active scan, re-schedule `mReconnectRunnable` with exponential backoff (1s..16s), and maintain `autoConnect = true`.
- Implement sliding-window scan quota limiter (max 4 scans / 30s); fallback directly to `connectGatt` on quota exhaustion or `onScanFailed`.

---

## 7. System Invariants & Risk Assessment
- **Core Invariants**:
  1. Startup search for offline sensors terminates after configured tries without infinite retry loops.
  2. Thread safety under `mGattLock` and Handler looper execution preserved.
  3. Zero battery regression when app is idle or workout is inactive.
- **Risk Rating**: **LOW** (Targeted state-machine and fallback recovery in `MyBTLEDevice`).
