# Stage 3: Implementation Plan - ATT-4007: Improve BLE and ANT+ reconnect and notification handling robustness

**Ticket**: [ATT-4007](https://rainerblind.atlassian.net/browse/ATT-4007)  
**Sub-task**: [ATT-4208](https://rainerblind.atlassian.net/browse/ATT-4208) (`[Plan]`)  
**Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-CON-020` (*Resilient Bluetooth LE & ANT+ Reconnection, Immutable Telemetry Dispatch, and Queue Integrity Architecture*, extending `REQ-CON-019`)  
**Test Mapping**: `TST-CON-012`  
**Branch**: `feature/ATT-4007`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Description & Background

Athletic workout recording depends on reliable, uninterrupted telemetry from BLE and ANT+ peripheral sensors (heart rate monitors, bike power meters, cadence/speed sensors, run footpods). Forensic analysis (Stage 1) and requirement specification (Stage 2) confirmed four architectural vulnerabilities in peripheral connection lifecycle and data dispatch:

1. **BLE Notification In-Place Mutation Race**: `MyBTLEDevice` posts raw `BluetoothGattCharacteristic` references to the Android main Looper thread. Under high notification frequency, Android Bluetooth Binder threads reuse and mutate characteristic byte buffers in place before or during main-thread metric decoding, skewing cumulative crank and wheel revolution counters.
2. **Search Timeout Leak & GATT Storming**: `MyBTLEDevice.startSearching()` posts an anonymous, unreferenced 10-second timeout runnable on every search invocation without cancellation tracking. Stale timeouts fire asynchronously and kill subsequent valid connections. Disconnections trigger immediate reconnects with 0 ms delay, storming Android Bluetooth HAL and triggering GATT 133/257 disconnect loops.
3. **Search Queue Preemption on Alien Events**: In `DeviceManager.java:150`, `searchForNextRemoteDevice()` was unconditionally executed outside the `current != null && current.getDeviceId() == deviceID` verification block. If an alien peripheral emitted a stop event, the search queue popped the next target device prematurely.
4. **ANT+ Channel Locking & Permanent DEAD State**: `MyANTDevice.startSearching()` overwrote `pccReleaseHandle` without closing the prior handle, triggering `DEVICE_ALREADY_IN_USE` errors. Upon transitioning to `DeviceState.DEAD`, channel resources were never released and `pccReleaseHandle` was never nulled, permanently preventing reconnection.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-CON-020` (*Resilient Bluetooth LE & ANT+ Reconnection, Immutable Telemetry Dispatch, and Queue Integrity Architecture*)
  * `REQ-CON-020.1`: Immutable BLE Notification Payload Dispatch (API 33 `onCharacteristicChanged` + defensive buffer cloning into decoupled snapshot characteristic).
  * `REQ-CON-020.2`: Deterministic Search Timeout Lifecycle & Exponential Reconnect Backoff (1s -> 2s -> 4s -> 8s -> 16s with `mIsShutDown` guard).
  * `REQ-CON-020.3`: Search Queue Device Integrity in `DeviceManager` (conditional advancement strictly matching active device).
  * `REQ-CON-020.4`: ANT+ Handle Hygiene & DEAD State Teardown (`releaseAccess()` on search and `DeviceState.DEAD`).
* **Test Mapping**: `TST-CON-012` (*Resilient Bluetooth LE & ANT+ Reconnection, Immutable Telemetry Dispatch, and Queue Integrity Verification Suite*)
  * `TST-CON-012.1`: `BLEPayloadDispatchTest.kt` (defensive clone & decoupled dispatch under byte mutation).
  * `TST-CON-012.2`: `BLEReconnectRobustnessTest.kt` (timeout cancellation, backoff sequence, shutdown guard).
  * `TST-CON-012.3`: `DeviceManagerSearchQueueTest.kt` (queue isolation against alien stop intents).
  * `TST-CON-012.4`: `ANTDeviceLifecycleTest.kt` (handle release and DEAD state cleanup).
  * `TST-CON-012.5`: Full clean-room regression suite execution (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Subclass Compatibility**: Existing peripheral subclasses (`BTLEHeartRateDevice`, `BTLEBikePowerDevice`, `BTLEBikeDevice`, `BTLERunSpeedDevice`) continue to utilize their standard `measurementCharacteristicUpdate(BluetoothGatt, BluetoothGattCharacteristic)` contract without modifying subclass parsing logic.
2. **Main-Thread Dispatch Integrity**: All measurement characteristic updates and battery status notifications remain dispatched strictly to `mHandler` (main Looper).
3. **Zero UI / Localization Impact**: The changes are strictly localized to background service peripheral communication (`banalservice.devices`). Zero UI screens, layouts, or strings are modified.
4. **Clean-Room Test Pass Rate**: All existing ~3,300 unit and Robolectric tests must pass with 100% success rate.
5. **Single Atomic Commit per Ticket (Rule 44)**: Stage 3 and Stage 4 changes remain uncommitted until Stage 5 clean-room verification is signed off.

---

## 4. Proposed Architectural Changes

### Component 1: `MyBTLEDevice.java` Concurrency & Lifecycle Hardening
- **API 33 & API < 33 Notification Dispatch**:
  - Implement `@Override public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value)`.
  - Retain `@Override public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic)`.
  - Forward both to unified `handleCharacteristicChanged(gatt, characteristic, value)` which defensively clones payload bytes `(value != null ? value : characteristic.getValue()).clone()` and constructs an independent `BluetoothGattCharacteristic` snapshot populated with the cloned byte array before posting to `mHandler`.
- **Search Timeout Tracking**:
  - Replace anonymous `Runnable` with single reusable field `mSearchTimeoutRunnable`.
  - Guard timeout execution with `!mIsShutDown && !isReceivingData()`.
  - Explicitly cancel `mSearchTimeoutRunnable` via `mHandler.removeCallbacks(...)` in:
    - `startSearching()` (prior to scheduling new timeout);
    - `disconnectFromGatt()`;
    - `notifyStopSearching(true)` (successful discovery);
    - `shutDown()`.
- **Exponential Reconnect Backoff & Shutdown Guard**:
  - Add `private volatile boolean mIsShutDown = false;`.
  - Add `INITIAL_RECONNECT_DELAY_MS = 1000L` and `MAX_RECONNECT_DELAY_MS = 16000L`.
  - Track `mCurrentReconnectDelayMs`.
  - Replace direct `startSearching()` call in `onConnectionStateChange(STATE_DISCONNECTED)` with `mReconnectRunnable` scheduled at `mCurrentReconnectDelayMs`.
  - Double delay on each consecutive disconnect up to `MAX_RECONNECT_DELAY_MS`.
  - Reset delay to `INITIAL_RECONNECT_DELAY_MS` upon successful connection (`notifyStopSearching(true)`).
  - Check `mIsShutDown` at top of `startSearching()`, `mReconnectRunnable`, and `mSearchTimeoutRunnable`.

### Component 2: `DeviceManager.java` Queue Progression Isolation
- In `mSearchingStoppedForOneReceiver.onReceive()`:
  - Move `searchForNextRemoteDevice()` strictly inside the `if (current != null && current.getDeviceId() == deviceID)` branch.
  - Set `cMyRemoteDeviceCurrentlySearchingFor = null;` before invoking `searchForNextRemoteDevice()`.
  - In the `else` branch (alien device event), log advisory warning and discard event without mutating `mSearchQueue` or popping the next device.

### Component 3: `MyANTDevice.java` Handle Lifecycle & DEAD Teardown
- In `releaseAccess()`:
  - Null out `pccReleaseHandle = null;` after calling `pccReleaseHandle.close()`.
  - Null out `mLegacyCommonPcc = null;` and `mCommonPcc = null;` after releasing access.
- In `startSearching()`:
  - Invoke `releaseAccess()` immediately prior to calling `requestAccess()`.
- In `onDeviceStateChangeHandler(DeviceState newDeviceState)`:
  - Under `case DEAD`:
    - Call `unregisterSensors()`;
    - Call `releaseAccess()`;
    - Call `notifyStopSearching(false)`.

---

## 5. UI Consistency (Rule 23)
* **Scope**: UI Consistency: N/A.
* **Justification**: Purely background sensor communication, peripheral connection management, and concurrency hardening in `banalservice.devices`. Zero user interface components or views are touched.

---

## 6. Atomic Implementation Steps

### Step 1: Immutable BLE Telemetry Dispatch in `MyBTLEDevice.java`
- Implement API 33 3-parameter `onCharacteristicChanged` and unify with 2-parameter fallback in `handleCharacteristicChanged`.
- Defensively clone raw payload bytes and instantiate decoupled snapshot `BluetoothGattCharacteristic`.
- Update `onCharacteristicRead` to also clone payload bytes on successful read.

### Step 2: Deterministic Search Timeout & Exponential Backoff in `MyBTLEDevice.java`
- Introduce `mSearchTimeoutRunnable`, `mReconnectRunnable`, `mCurrentReconnectDelayMs`, and `mIsShutDown`.
- Implement timeout cancellation in `startSearching()`, `disconnectFromGatt()`, `notifyStopSearching()`, and `shutDown()`.
- Implement exponential backoff (1s -> 2s -> 4s -> 8s -> 16s) in `STATE_DISCONNECTED`.
- Enforce `mIsShutDown` checks.

### Step 3: Search Queue Integrity Guard in `DeviceManager.java`
- Relocate `searchForNextRemoteDevice()` inside `current != null && current.getDeviceId() == deviceID`.
- Clear `cMyRemoteDeviceCurrentlySearchingFor = null` before advancing.

### Step 4: ANT+ Channel & DEAD State Cleanup in `MyANTDevice.java`
- Update `releaseAccess()` to null out handles after closing.
- Call `releaseAccess()` in `startSearching()` before `requestAccess()`.
- Call `releaseAccess()` and `notifyStopSearching(false)` in `onDeviceStateChangeHandler(DEAD)`.

### Step 5: Author Unit Test Suites
- Create `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BLEPayloadDispatchTest.kt` (`TST-CON-012.1`).
- Create `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BLEReconnectRobustnessTest.kt` (`TST-CON-012.2`).
- Create `app/src/test/java/com/atrainingtracker/banalservice/devices/DeviceManagerSearchQueueTest.kt` (`TST-CON-012.3`).
- Create `app/src/test/java/com/atrainingtracker/banalservice/devices/ant_plus/ANTDeviceLifecycleTest.kt` (`TST-CON-012.4`).

### Step 6: Targeted Test Verification
- Run `./gradlew testDebugUnitTest --tests "*BLEPayloadDispatchTest*" --tests "*BLEReconnectRobustnessTest*" --tests "*DeviceManagerSearchQueueTest*" --tests "*ANTDeviceLifecycleTest*"`

---

## 7. Review and Gate Audit Preparation
- Update subtask `ATT-4208` description with this document.
- Transition `ATT-4208` to `In Überprüfung`.
- Run Gate 3 review audit via `python3 tools/review_agent.py audit ATT-4208`.
- Upon passing Gate 3, execute `python3 tools/jira_util.py move ATT-4208 freigabe` to advance parent `ATT-4007` to `Implementation`.
