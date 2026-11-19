# Stage 3: Implementation Plan - ATT-8998: Non-Blocking BLE Search-Queue & Background Reconnect

**DOC-ID**: `DOC-PLN-ATT-8998` | **Ticket**: `ATT-8998` (`2 SP`, Parent `ATT-5910`) | **Target Release**: `V5.0.0`  
**Sprint**: `2026-11-18_03_Nacht` | **Requirement Mapping**: `REQ-CON-051` (`.1`..`.3`) | **Test Mapping**: `TST-CON-051` (`.1`..`.3`)  
**Branch**: `feature/ATT-8998` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-18

---

## 1. Problem Summary & Traceability Matrix
`MyBTLEDevice.mSearchTimeoutRunnable` skips `notifyStopSearching(false)` and leaves `mState = State.SEARCHING` whenever `isMidSessionOrWorkoutActive()` is `true` (including never-connected offline sensors during `TrackingMode.TRACKING`/`PAUSED`). `DeviceManager.cMyRemoteDeviceCurrentlySearchingFor` stalls indefinitely on the first offline BLE device (`BANALService.isSearching() == true`), starving subsequent sensors in `mSearchQueue`.

| Requirement | Atomic Step(s) | Test Spec | Target Test File(s) |
| :--- | :--- | :--- | :--- |
| `REQ-CON-051.1` | Steps 2, 4, 5 | `TST-CON-051.1` | `BLEReconnectRobustnessTest`, `DeviceManagerSearchQueueTest` |
| `REQ-CON-051.2` | Steps 1, 2, 3, 4, 5 | `TST-CON-051.2` | `DeviceManagerSearchQueueTest`, `BLEReconnectRobustnessTest` |
| `REQ-CON-051.3` | Steps 3, 4, 6 | `TST-CON-051.3` | `MyBTLEDeviceAddressAndSearchTest`, `MyBTLEDeviceLifecycleTest`, `ArchitectureConformanceContractTest`, `DetailedDesignParityContractTest` |

## 2. System Invariants & Constraints
1. **Monolith LoC Hard Ceiling**: `DeviceManager.java` is `1,708 / 1,708` LoC (`ArchitectureConformanceContractTest`); zero net line growth (`<= 1,708` LoC) allowed.
2. **Legacy & Hardware Invariants**: Preserve offline startup battery bounding (`TrackingMode.READY && !mHasConnectedInSession` shuts down on `tries <= 1`), `REQ-CON-036` exponential backoff (`1s..16s`) + `autoConnect = true`, `mGattLock`/`mSearchQueue` lock ordering, `MyANTDevice`, and `PolarOnlineStreamingBridge`.
3. **UI/String & Code-Lock Rules**: `Localization Parity: N/A - no UI strings`; `0` new `src/main` reads in tests (`REQ-PRO-049.1`); `Rule 4.1` code lock enforced until Gate 3 `PASS`.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new `REQ-CON-051`, refining `REQ-CON-020.2`, `REQ-CON-020.3`, `REQ-CON-036.1`, `REQ-CON-036.2` (`MyBTLEDevice.java`, `MyRemoteDevice.java`, `DeviceManager.java`).
2. *Historical Origin & Commit Trace*: `e84383ee` (`ATT-4007`), `22d8c54d` (`ATT-5489`), `3dc19303` (`ATT-7755`).
3. *Root Reason for Existing Formulation*: `ATT-7755` skipped `notifyStopSearching(false)` on timeout under `isMidSessionOrWorkoutActive()` assuming `cMyRemoteDeviceCurrentlySearchingFor == null`, overlooking active `mSearchQueue` scans in `TRACKING`/`PAUSED` and `tries <= 1` teardown.
4. *Preservation of Core Invariants*: Offline startup battery bound (`TrackingMode.READY`), mid-workout backoff + `autoConnect = true`, `mGattLock`/`mSearchQueue` safety, and `DeviceManager.java <= 1,708` LoC preserved.

## 3. SWE.2 / SWE.3 Architectural Design & Threading
* **`MyRemoteDevice` Teardown Exemption Hook**: Add `public boolean shouldKeepAliveOnSearchTimeout()` (default `false`), overridden in `MyBTLEDevice` to return `isMidSessionOrWorkoutActive()`.
* **Decoupled `MyBTLEDevice` Timeout & Background Reconnect (`mGattLock` + `mHandler`)**:
  - Introduce `private volatile boolean mIsBackgroundReconnecting = false;` (cleared under `mGattLock` in `startSearching()`, `onConnectionStateChange(STATE_CONNECTED)`, `disconnectFromGatt()`, and `performShutDown()`).
  - In `mSearchTimeoutRunnable` (`!isReceivingData()`): always stop scan assistance; if `isMidSessionOrWorkoutActive()`, under `mGattLock` transition `mState` out of `State.SEARCHING` to `State.DISCONNECTED`, set `mIsBackgroundReconnecting = true`, compute exponential backoff, schedule `mReconnectRunnable`, and ensure `triggerGattConnect(dev, true)` when `mBluetoothGatt == null`; ALWAYS call `notifyStopSearching(false)` when `isSearching()` so `DeviceManager.mSearchQueue` advances immediately.
  - Extract `attemptBackgroundReconnect()` called by `mReconnectRunnable` (`!mIsShutDown && !mIsGattPaused && mState != CONNECTED_TO_GATT && mState != CONNECTED_WITH_SERVICE`): under `mGattLock`, sets `mIsBackgroundReconnecting = true` (re-arming after `onConnectionStateChange(STATE_DISCONNECTED) -> disconnectFromGatt()` cleared it) while keeping `mState = State.DISCONNECTED` and `MyRemoteDevice.mSearching = false`; schedules `mSearchTimeoutRunnable`, starts scan assistance, and calls `triggerGattConnect(dev, isMidSessionOrWorkoutActive())` if `!mIsScanning || isMidSessionOrWorkoutActive()`. Update `triggerGattConnect` / `onScanResult` guards to `(mState == State.SEARCHING || (mIsBackgroundReconnecting && mState == State.DISCONNECTED))`.
* **`DeviceManager` Queue & `tries <= 1` Guard (`<= 1,708` LoC)**: In `mSearchingStoppedForOneReceiver`, inside `if (tries == null || tries <= 1)` (preserving finite try exhaustion without re-queuing), guard `detachIfRrBeatSource`/`shutDown()`/`mMyRemoteDevices.remove(deviceID)` with `if (!current.shouldKeepAliveOnSearchTimeout())`, trimming 1–2 redundant blank/comment lines in `DeviceManager.java` to stay `<= 1,708` LoC.

## 4. Atomic Stage 4 Implementation Sequence
1. **Step 1 (`REQ-CON-051.2`) — `MyRemoteDevice.java`**: Add `public boolean shouldKeepAliveOnSearchTimeout() { return false; }`.
2. **Step 2 (`REQ-CON-051.1`, `.2`) — `MyBTLEDevice.java`**: Add `mIsBackgroundReconnecting` (cleared in `startSearching()`, `onConnectionStateChange(STATE_CONNECTED)`, `disconnectFromGatt()`, `performShutDown()`; set in `attemptBackgroundReconnect()` and `mSearchTimeoutRunnable`), override `shouldKeepAliveOnSearchTimeout()`, update `mSearchTimeoutRunnable` to clear `State.SEARCHING -> DISCONNECTED` and call `notifyStopSearching(false)` when `isSearching()`, add `attemptBackgroundReconnect()` for `mReconnectRunnable` preserving `triggerGattConnect(dev, isMidSessionOrWorkoutActive())`, and update `triggerGattConnect` / `onScanResult` guards under `mGattLock`.
3. **Step 3 (`REQ-CON-051.2`, `.3`) — `DeviceManager.java` (`<= 1,708` LoC)**: Guard `tries <= 1` teardown with `!current.shouldKeepAliveOnSearchTimeout()` and consolidate redundant blank/comment lines so `wc -l DeviceManager.java <= 1708`.
4. **Step 4 (`REQ-CON-051.3`, `REQ-PRO-114`) — SWE.3 Detailed Design Sync**: Verify `docs/architecture/detailed_design/my_btle_device.md` matches final Stage 4 code symbols and line counts.
5. **Step 5 (`TST-CON-051.1`, `.2`) — Multi-Sensor Queue & Reconnect Tests**:
   - Extend `DeviceManagerSearchQueueTest.kt`: test `[HR1 (found) -> BLE Radar (offline timeout in TRACKING & PAUSED) -> HR2 (searched)]`, verify `BANALService.isSearching()` becomes `false` after queue drain, verify `tries <= 1` preserves `shouldKeepAliveOnSearchTimeout()` device while shutting down `TrackingMode.READY` device, and verify concurrent `startSearchForPairedDevices()` does not stall on background-reconnecting device.
   - Extend `BLEReconnectRobustnessTest.kt`: verify `mSearchTimeoutRunnable` clears `State.SEARCHING` + calls `notifyStopSearching(false)` in `TRACKING`/`PAUSED`, `mReconnectRunnable` (`STATE_DISCONNECTED -> disconnectFromGatt() -> attemptBackgroundReconnect()`) re-arms `mIsBackgroundReconnecting`, backs off (`2s..16s`), and executes `triggerGattConnect(dev, isMidSessionOrWorkoutActive())` without setting `mSearching=true`, and `TrackingMode.READY` offline termination remains intact.
6. **Step 6 (`TST-CON-051.3`) — Full Targeted & Contract Verification**: Run `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.build.DetailedDesignParityContractTest"` (under `parallel_sprint_lock.py`) + `verify_requirement_governance.py`.

## 5. Verification & Rollback Plan
* **Verification**: All targeted unit & contract tests (`0` new `src/main` reads) + `verify_requirement_governance.py` (`exit 0`).
* **Rollback**: Uncommitted/single-branch `feature/ATT-8998`; clean revert restores prior state.
