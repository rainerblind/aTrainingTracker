# Stage 2: Requirement & Test Specification - ATT-4007: Improve BLE and ANT+ reconnect and notification handling robustness

**Ticket**: [ATT-4007](https://rainerblind.atlassian.net/browse/ATT-4007)  
**Sub-task**: [ATT-4207](https://rainerblind.atlassian.net/browse/ATT-4207) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-CON-020` (*Resilient Bluetooth LE & ANT+ Reconnection, Immutable Telemetry Dispatch, and Queue Integrity Architecture*, extending `REQ-CON-019`)  
**Test Spec ID**: `TST-CON-012`  
**Branch**: `feature/ATT-4007`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Requirement Specification (`REQ-CON-020`)

### 1.1 Problem Statement & Rationale
Athletic workout telemetry requires continuous, resilient peripheral sensor connections. Prior to this requirement:
1. BLE notifications arriving on Android Bluetooth Binder threads posted raw `BluetoothGattCharacteristic` references to the main Looper. Under high notification frequency, the native Bluetooth stack mutated characteristic byte buffers in place, causing cumulative crank/wheel counters or sensor metrics to be corrupted.
2. `MyBTLEDevice` scheduled untracked anonymous 10-second timeout runnables on every search, causing stale timeouts from previous searches to kill newly established connections. Immediate reconnects (0 ms) on disconnect caused GATT 133/257 storms.
3. `DeviceManager` advanced the search queue unconditionally when any peripheral emitted a stop event, prematurely preempting searches for the active device.
4. `MyANTDevice` leaked `pccReleaseHandle` across search invocations and failed to release channels on `DeviceState.DEAD`, locking ANT+ channels with `DEVICE_ALREADY_IN_USE`.

### 1.2 Functional & Architectural Requirements

1. **Immutable BLE Notification Payload Dispatch (`REQ-CON-020.1`)**:
   - In `MyBTLEDevice`, the `BluetoothGattCallback` SHALL implement the Android 13+ (API 33) signature `onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value)` alongside the legacy fallback.
   - When a notification arrives, the raw payload bytes SHALL immediately be defensively cloned on the Binder callback thread (`(value != null ? value : characteristic.getValue()).clone()`).
   - A decoupled snapshot characteristic (`new BluetoothGattCharacteristic(uuid, properties, permissions)`) populated with the cloned byte buffer SHALL be dispatched to `mHandler`, guaranteeing that subsequent incoming BLE packets cannot mutate payload data before or during main-thread processing.
2. **Deterministic Search Timeout Lifecycle & Reconnect Backoff (`REQ-CON-020.2`)**:
   - `MyBTLEDevice` SHALL maintain a single, reusable `mSearchTimeoutRunnable`.
   - All pending search timeouts SHALL be explicitly cancelled via `mHandler.removeCallbacks(mSearchTimeoutRunnable)` upon:
     - Starting a new search (`startSearching()`);
     - Disconnecting from GATT (`disconnectFromGatt()`);
     - Explicit teardown (`shutDown()`);
     - Successful service discovery and connection establishment (`notifyStopSearching(true)`).
   - `MyBTLEDevice` SHALL maintain an atomic/volatile `mIsShutDown` guard; disconnections arriving during or after `shutDown()` SHALL be ignored.
   - When an active connection drops unexpectedly (`STATE_DISCONNECTED`), `MyBTLEDevice` SHALL NOT reconnect immediately with 0 ms delay. It SHALL schedule `startSearching()` via `mReconnectRunnable` with exponential backoff: base delay 1,000 ms, doubling upon consecutive search failures up to 16,000 ms (`MAX_RECONNECT_DELAY_MS`), and resetting to 1,000 ms upon successful service discovery.
3. **Search Queue Device Integrity in `DeviceManager` (`REQ-CON-020.3`)**:
   - In `DeviceManager.mSearchingStoppedForOneReceiver`, advancing the search queue (`searchForNextRemoteDevice()`) and modifying retry counters SHALL strictly execute ONLY if the event's `deviceID` matches the active target `cMyRemoteDeviceCurrentlySearchingFor.getDeviceId()`.
   - Before invoking `searchForNextRemoteDevice()`, `cMyRemoteDeviceCurrentlySearchingFor` SHALL be cleared to `null`.
   - Any `SEARCHING_STOPPED_FOR_ONE_INTENT` from a device ID not currently matching `cMyRemoteDeviceCurrentlySearchingFor` SHALL be logged and discarded without modifying `mSearchQueue` or popping the next device.
4. **ANT+ Handle Hygiene & DEAD State Recovery (`REQ-CON-020.4`)**:
   - In `MyANTDevice.startSearching()`, `releaseAccess()` SHALL be invoked before requesting new access (`requestAccess()`), ensuring any stale `pccReleaseHandle` is cleanly closed.
   - In `MyANTDevice.onDeviceStateChangeHandler(DeviceState.DEAD)`, `releaseAccess()` SHALL be invoked, `pccReleaseHandle` SHALL be set to `null`, `notifyStopSearching(false)` SHALL be ensured, and sensors unregistered without leaving dangling channel allocations.

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (BLE Payload Immutability & Decoupled Dispatch)**:
  * *Given* a `MyBTLEDevice` receiving rapid BLE notifications,
  * *When* the Bluetooth callback thread receives consecutive packets where the underlying characteristic byte array is mutated in place,
  * *Then* the main-thread handler receives independent immutable byte snapshots matching each exact packet payload without data corruption.
* **Criterion 2 (Deterministic Search Timeout & Reconnect Backoff)**:
  * *Given* a connected BLE sensor that disconnects unexpectedly,
  * *When* `onConnectionStateChange` receives `STATE_DISCONNECTED`,
  * *Then* it cancels any prior timeout, schedules reconnection with initial 1,000 ms backoff, doubles backoff on successive disconnects up to 16,000 ms, and cancels all pending timeouts when shut down.
* **Criterion 3 (Search Queue Isolation on Alien Events)**:
  * *Given* `DeviceManager` actively searching for Device A with Device B queued in `mSearchQueue`,
  * *When* a `SEARCHING_STOPPED_FOR_ONE_INTENT` arrives with `deviceID = C` (alien device),
  * *Then* the event is discarded, `cMyRemoteDeviceCurrentlySearchingFor` remains Device A, and Device B is NOT popped from `mSearchQueue`.
* **Criterion 4 (ANT+ Handle Release & DEAD State Cleanup)**:
  * *Given* an active or failing ANT+ device transition to `DeviceState.DEAD`,
  * *When* `onDeviceStateChangeHandler(DEAD)` executes,
  * *Then* `pccReleaseHandle` is closed and nulled, preventing `DEVICE_ALREADY_IN_USE` errors on subsequent search.

### 1.4 System Invariants
1. Zero regression in existing peripheral drivers (`BTLEHeartRateDevice`, `BTLEBikePowerDevice`, `BTLEBikeDevice`, `BTLERunSpeedDevice`).
2. Main-thread execution guarantees for `measurementCharacteristicUpdate` preserved.
3. Zero UI modifications or localization impact.

---

## 2. Test Specification (`TST-CON-012`)

### Test Case 1: `testBleNotificationPayloadClonedAndDecoupled` (`TST-CON-012.1`)
* **Scope**: Unit Test (`app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BLEPayloadDispatchTest.kt`)
* **Preconditions**: Concrete `MyBTLEDevice` test implementation initialized with mocked handler and sensors.
* **Action**: Invoke `onCharacteristicChanged` with packet 1 (`[0x01, 0x50]`), immediately mutate underlying array to packet 2 (`[0x02, 0x60]`), and execute queued main-thread runnables.
* **Expected Result**: Consumer receives exact packet 1 payload (`[0x01, 0x50]`) unaffected by subsequent mutation.

### Test Case 2: `testSearchTimeoutLifecycleAndReconnectBackoff` (`TST-CON-012.2`)
* **Scope**: Unit Test (`app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BLEReconnectRobustnessTest.kt`)
* **Preconditions**: `MyBTLEDevice` instance in `SEARCHING` or `CONNECTED` state.
* **Action**:
  - Verify `startSearching()` cancels prior timeout.
  - Verify successful connection cancels timeout runnable.
  - Simulate `STATE_DISCONNECTED` and assert backoff delay sequence: 1000ms -> 2000ms -> 4000ms -> 8000ms -> 16000ms.
  - Call `shutDown()` and verify all callbacks removed and subsequent disconnect events ignored.
* **Expected Result**: Zero stale timeouts, deterministic exponential backoff, and clean teardown.

### Test Case 3: `testDeviceManagerSearchQueueIgnoresAlienDeviceEvents` (`TST-CON-012.3`)
* **Scope**: Unit Test (`app/src/test/java/com/atrainingtracker/banalservice/devices/DeviceManagerSearchQueueTest.kt`)
* **Preconditions**: `DeviceManager` initialized with `cMyRemoteDeviceCurrentlySearchingFor = Device A (id=101)`, `mSearchQueue = [Device B (id=102)]`.
* **Action**: Broadcast `SEARCHING_STOPPED_FOR_ONE_INTENT` with `DEVICE_ID = 999` (alien device).
* **Expected Result**: `cMyRemoteDeviceCurrentlySearchingFor` remains Device A (101); `mSearchQueue` remains `[Device B (102)]` without preemption.

### Test Case 4: `testAntDeviceHandleReleaseOnDeadAndSearch` (`TST-CON-012.4`)
* **Scope**: Unit Test (`app/src/test/java/com/atrainingtracker/banalservice/devices/ant_plus/ANTDeviceLifecycleTest.kt`)
* **Preconditions**: `MyANTDevice` subclass with mock `PccReleaseHandle`.
* **Action**:
  - Set active handle and trigger `onDeviceStateChangeHandler(DeviceState.DEAD)`.
  - Verify handle is closed and nulled.
  - Call `startSearching()` while handle exists; verify existing handle closed before new request.
* **Expected Result**: Handle closed cleanly, zero leaks, and graceful dead-state cleanup.

### Test Case 5: Clean-Room Full Suite Regression (`TST-CON-012.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% test pass rate across all ~3,300+ unit and Robolectric tests.

---

## 3. Localization Parity
* **Scope**: Localization Parity: N/A.
* **Justification**: Purely background connectivity, peripheral management, and concurrency hardening. Zero UI strings or layouts added or modified.

---

## 4. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-012.1` | Unit | `MyBTLEDevice.onCharacteristicChanged` | `REQ-CON-020.1` | Specified |
| `TST-CON-012.2` | Unit | `MyBTLEDevice.startSearching`, `disconnectFromGatt`, backoff | `REQ-CON-020.2` | Specified |
| `TST-CON-012.3` | Unit | `DeviceManager.mSearchingStoppedForOneReceiver` | `REQ-CON-020.3` | Specified |
| `TST-CON-012.4` | Unit | `MyANTDevice.startSearching`, `onDeviceStateChangeHandler` | `REQ-CON-020.4` | Specified |
| `TST-CON-012.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001`, `REQ-CON-020` | Specified |
