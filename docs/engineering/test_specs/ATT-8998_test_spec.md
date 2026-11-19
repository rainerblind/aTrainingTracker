# Stage 2: Requirement & Test Specification - ATT-8998

**DOC-ID**: `DOC-TST-ATT-8998` | **Ticket**: [ATT-8998](https://rainerblind.atlassian.net/browse/ATT-8998) (`2 SP`) | **Parent**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-18_03_Nacht` | **Branch**: `feature/ATT-8998`  
**Requirement Mapping**: `REQ-CON-051` | **Test Spec ID**: `TST-CON-051` | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-CON-051` `[Verified]`)
* **Attributes**: Priority: `Critical` | Type: `Functional` | Verification Method: `Unit` | System Req: `SYSREQ-TRK-002` (no new `SYS.2`/`SYS.3` interfaces)
* **Implementation**: `MyBTLEDevice.java`, `MyRemoteDevice.java`, `DeviceManager.java`, `my_btle_device.md`, `banal_service.md`
* **`REQ-CON-051.1` (Immediate Sequential Search-Queue Release on Timeout)**: When `MyBTLEDevice.mSearchTimeoutRunnable` expires with `!isReceivingData()`, `MyBTLEDevice` SHALL always transition `mState` out of `State.SEARCHING` (`State.DISCONNECTED`) and invoke `notifyStopSearching(false)` (clearing `MyRemoteDevice.mSearching = false` and broadcasting `SEARCHING_STOPPED_FOR_ONE_INTENT`), regardless of `TrackingMode` (`READY`, `TRACKING`, `PAUSED`) or `mHasConnectedInSession`. `DeviceManager.mSearchingStoppedForOneReceiver` SHALL clear `cMyRemoteDeviceCurrentlySearchingFor = null` and advance `searchForNextRemoteDevice()` to subsequent queued sensors (e.g., 2nd HR sensor after offline BLE Radar) so `BANALService.isSearching()` becomes `false` once `mSearchQueue` drains.
* **`REQ-CON-051.2` (Decoupled Background Reconnect Without Queue Blocking or `tries <= 1` Teardown)**: Background mid-session / active-workout reconnection (`mReconnectRunnable` `1s -> 2s -> 4s -> 8s -> 16s` and persistent `autoConnect = true`) SHALL execute without setting `MyRemoteDevice.mSearching = true`, without blocking `cMyRemoteDeviceCurrentlySearchingFor`, and without stalling concurrent `startSearchForPairedDevices()` / `searchForNextRemoteDevice()` (`next.isSearching()`), while preserving `triggerGattConnect(dev, true)` / `onScanResult()` attachment and clean `shutDown()` cancellation. `DeviceManager.mSearchingStoppedForOneReceiver` (`tries <= 1`) MUST NOT `shutDown()` or remove from `mMyRemoteDevices` any device persisting active background reconnect, while never-connected offline devices in `TrackingMode.READY` MUST still shut down and be removed.
* **`REQ-CON-051.3` (Monolith LoC Ceiling, Design Parity & Localization)**: `DeviceManager.java` MUST remain `<= 1,708` LoC (`ArchitectureConformanceContractTest`); `my_btle_device.md` and `banal_service.md` SHALL stay synchronized (`DetailedDesignParityContractTest`); `Localization Parity: N/A - no UI strings`; unit tests SHALL pass offline with `0` new `src/main` reads.

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-CON-051` refining `REQ-CON-020.2`, `REQ-CON-020.3`, `REQ-CON-036.1`, and `REQ-CON-036.2` across `MyBTLEDevice.java`, `MyRemoteDevice.java`, and `DeviceManager.java`.
* **Historical Origin & Commit Trace**: `e84383ee` (`ATT-4007`), `22d8c54d` (`ATT-5489`), `3dc19303` (`ATT-7755`, `isMidSessionOrWorkoutActive()`).
* **Root Reason for Existing Formulation**: `ATT-7755` omitted `notifyStopSearching(false)` on timeout under `isMidSessionOrWorkoutActive()` assuming `cMyRemoteDeviceCurrentlySearchingFor == null` during mid-workout disconnects, overlooking workout-resume and mid-workout searches through `mSearchQueue` and `tries <= 1` teardown in `DeviceManager`.
* **Preservation of Core Invariants**: Preserves offline startup battery bounding (`REQ-CON-036.1`), mid-workout exponential backoff + `autoConnect = true`, `DeviceManager` alien-broadcast isolation (`REQ-CON-020.3`), `mGattLock` / `mSearchQueue` lock ordering, `MyANTDevice` & `PolarOnlineStreamingBridge`, `REQ-PRO-049.1` (`0` new `src/main` reads), and `DeviceManager.java <= 1,708` LoC.

## 3. Acceptance Criteria (Given-When-Then)
* *Given* `TrackingMode.TRACKING` (or `PAUSED`) with `[Offline BLE Radar, Online BLE HRM]` queued in `DeviceManager`, *When* `mSearchTimeoutRunnable` expires on the Radar, *Then* `MyBTLEDevice` clears `State.SEARCHING` and calls `notifyStopSearching(false)`, `DeviceManager` immediately searches the HRM, and `BANALService.isSearching()` becomes `false` once `mSearchQueue` drains.
* *Given* `DeviceManager.mSearchingStoppedForOneReceiver` (`success = false`, `tries <= 1`) on a device persisting active-workout/mid-session reconnect vs. an offline device in `TrackingMode.READY`, *When* processed, *Then* the persisting device is NOT shut down or removed and continues background `mReconnectRunnable` (`2s -> 4s -> 8s -> 16s`) + `autoConnect = true` without `mSearching = true` or stalling `startSearchForPairedDevices()`, whereas the `READY` offline device IS shut down and removed.

## 4. Test Specification (`TST-CON-051`, Offline Unit — `Unit`)
* **`TST-CON-051.1` (`BLEReconnectRobustnessTest`, `DeviceManagerSearchQueueTest`)**: Under `TrackingMode.TRACKING` and `PAUSED`, fire `mSearchTimeoutRunnable` on offline BLE Radar; assert `State.SEARCHING` cleared, `!isSearching()`, `SEARCHING_STOPPED_FOR_ONE_INTENT` (`success=false`), immediate advance to queued 2nd HR sensor, and `BANALService.isSearching() == false` after queue drain.
* **`TST-CON-051.2` (`DeviceManagerSearchQueueTest`, `BLEReconnectRobustnessTest`)**: Assert `tries <= 1` preserves actively reconnecting device (no `shutDown()`/`remove()`, `mReconnectRunnable` `2s..16s` + `autoConnect = true` intact) while `TrackingMode.READY` offline device shuts down; assert background `mReconnectRunnable` preserves `triggerGattConnect(dev, true)` / `onScanResult()` without `mSearching = true` or stalling subsequent `startSearchForPairedDevices()`.
* **`TST-CON-051.3` (`MyBTLEDeviceAddressAndSearchTest`, `MyBTLEDeviceLifecycleTest`, `ArchitectureConformanceContractTest`, `DetailedDesignParityContractTest`)**: Assert all BLE search/lifecycle tests pass, `DeviceManager.java <= 1,708` LoC, detailed-design parity passes, `0` new `src/main` reads, and `Localization Parity: N/A - no UI strings`.

## 5. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-051.1` | Unit (Mocked HAL) | `MyBTLEDevice`, `DeviceManager`, `BANALService` | `REQ-CON-051.1` | Verified |
| `TST-CON-051.2` | Unit (Mocked HAL) | `DeviceManager`, `MyBTLEDevice`, `MyRemoteDevice` | `REQ-CON-051.2` | Verified |
| `TST-CON-051.3` | Unit & Contract | `MyBTLEDevice*Test`, `ArchitectureConformanceContractTest`, `DetailedDesignParityContractTest` | `REQ-CON-051.3` | Verified |
