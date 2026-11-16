# Stage 2: Requirement & Test Specification — ATT-8451: Polar Online Streaming Bridge Search Timeout & GATT Contention Recovery

**Ticket**: [ATT-8451](https://issue-tracker/ATT-8451) (`2 SP`) | **Parent Epic**: `ATT-5910` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-16_03_Nacht` | **Branch**: `feature/ATT-8451` | **Author**: `agent1` (`subagent_flash`)  
**Requirement Mapping**: `REQ-CON-045` (`REQ-CON-045.1`..`REQ-CON-045.4`) | **Test Spec ID**: `TST-CON-045` (`TST-CON-045.1`..`TST-CON-045.4`)  

---

## 1. Requirement Specification (REQ-CON-045)

### 1.1 Problem Statement & Rationale
When a Polar sensor (H10 or Verity Sense with live IMU enabled) is turned off, out of range, or held in an open OS GATT connection by an external application (e.g. `fi.polar.polarflow`), Polar SDK's internal advertisement scan never receives connectable advertisements. `PolarOnlineStreamingBridge.startSearching()` called `notifyStartSearching()` without scheduling a search timeout watchdog. Without `notifyStopSearching(false)`, `DeviceManager.mSearchQueue` halts indefinitely, starving all subsequent sensors (e.g. radar, powermeters). Furthermore, `connectPersistentSession()` returned silently on `polarApi != null`, leaving repeated search triggers and post-disconnect / post-BT-restart attempts permanently stuck in "searching" state (`ATT-8451`, extending `REQ-SET-112.2` and `REQ-CON-004`).

### 1.2 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- **System Requirement(s)**: `SYSREQ-TRK-001`, `SYSREQ-TRK-002`
- **Implementation**: `PolarOnlineStreamingBridge.kt`, `DeviceManager.java`

### 1.3 Functional & Architectural Requirements
1. **Deterministic Search Timeout & Queue Unblocking (`REQ-CON-045.1`)**: `PolarOnlineStreamingBridge.startSearching()` SHALL schedule a deterministic search timeout matching `TrainingApplication.getBtleSearchPeriodMs()`. If the timeout expires before `deviceConnected`, the bridge SHALL cancel the scan attempt, broadcast `notifyStopSearching(false)` to advance `DeviceManager.mSearchQueue`, and call `polarApi.disconnectFromDevice(polarId)` if `!isReceivingData()`.
2. **Idempotent Search & Session State Recovery on Cached `polarApi` (`REQ-CON-045.2`)**: Invoking `startSearching()` when `polarApi != null` SHALL NOT silently exit without attempting connection. If already connected and receiving data, it SHALL immediately emit `notifyStopSearching(true)`. If disconnected, it SHALL re-initiate `api.connectToDevice(polarId)` (or re-instantiate `polarApi` if the session is closed) and arm the search timeout watchdog.
3. **Search Watchdog Cancellation on Connection (`REQ-CON-045.3`)**: When `deviceConnected(polarDeviceInfo)` arrives during an active search, `PolarOnlineStreamingBridge` SHALL immediately cancel the search timeout watchdog, broadcast `notifyStopSearching(true)`, register sensors, and synchronize active streaming sessions.
4. **Bluetooth Adapter Power Cycle & Lifecycle Teardown (`REQ-CON-045.4`)**: `PolarOnlineStreamingBridge.sdkCallback` SHALL implement `blePowerStateChanged(powered: Boolean)`. When `powered == false`, the bridge SHALL clear `isConnectedFlag`, cancel active streaming coroutines (`hrJob`, `accJob`, `gyroJob`), release the `ACC` channel lock in `PolarOfflineGattCoordinator`, unregister sensors, and (if searching) cancel the search timeout and broadcast `notifyStopSearching(false)`. `shutDown()` SHALL cancel all timeout jobs, coroutine scopes, and receivers.

### 1.4 Acceptance Criteria (Given-When-Then)
* *Criterion 1 (Deterministic Search Timeout & Queue Progression)*:
  * *Given* a paired Polar H10 or Verity Sense with live IMU enabled that is out of range or advertisement-starved by an existing Polar Flow GATT connection,
  * *When* `startSearching()` is invoked by `DeviceManager.searchForNextRemoteDevice()`,
  * *Then* after `TrainingApplication.getBtleSearchPeriodMs()` elapses without connection, `notifyStopSearching(false)` is broadcast, allowing `DeviceManager.mSearchQueue` to advance to the next sensor.
* *Criterion 2 (Idempotent Repeated Search with Existing `polarApi`)*:
  * *Given* a `PolarOnlineStreamingBridge` with an initialized but disconnected `polarApi`,
  * *When* `startSearching()` is invoked again,
  * *Then* connection is re-triggered via `api.connectToDevice(polarId)`, the search timeout watchdog is armed, and the search queue does not deadlock.
* *Criterion 3 (Connection Success Timeout Cancellation)*:
  * *Given* an active search with the search timeout watchdog armed,
  * *When* the Polar sensor connects and `deviceConnected` is received,
  * *Then* the search timeout watchdog is immediately cancelled and `notifyStopSearching(true)` is broadcast.
* *Criterion 4 (Bluetooth Adapter Power Cycle & Teardown)*:
  * *Given* an active search or connected stream on `PolarOnlineStreamingBridge`,
  * *When* `blePowerStateChanged(false)` is received or `shutDown()` is called,
  * *Then* active timeout jobs and streaming jobs are cancelled, connection state is cleared, and if searching, `notifyStopSearching(false)` is dispatched.

### 1.5 System Invariants & Localization Impact
- Preserves standard `BTLEHeartRateDevice` fallback when live IMU is disabled or `< API 33`.
- Preserves `MySensorManager.CADENCE_PRIORITY` ranking (`RUN_SPEED` > `BIKE_*` > `HRM`, `REQ-SET-112.4`).
- Preserves `PolarOfflineGattCoordinator` session sharing (`REQ-SET-112.3`) and `activeBridges` registry (`ATT-8019`).
- **Localization Impact**: Zero modified or added string resources; 100% string-parity preserved across all 9 UI locales.

---

## 2. Test Specification (TST-CON-045)

### Test Case 1: Search Timeout & Queue Unblocking (`TST-CON-045.1`)
* **Scope**: Unit Test (`PolarOnlineStreamingBridgeTest.kt`).
* **Assertions**: `startSearching()` broadcasts `notifyStartSearching()`; upon timeout expiry without connection, `notifyStopSearching(false)` is broadcast, `isSearching == false`, and `api.disconnectFromDevice(polarId)` is invoked.

### Test Case 2: Repeated Search with Existing `polarApi` (`TST-CON-045.2`)
* **Scope**: Unit Test (`PolarOnlineStreamingBridgeTest.kt`).
* **Assertions**: With an existing disconnected `polarApi`, `startSearching()` invokes `api.connectToDevice` and arms timeout. When already connected, `notifyStopSearching(true)` is immediately broadcast without duplicate connection calls.

### Test Case 3: Connection Success Timeout Cancellation (`TST-CON-045.3`)
* **Scope**: Unit Test (`PolarOnlineStreamingBridgeTest.kt`).
* **Assertions**: `deviceConnected` cancels the search watchdog; advancing time beyond timeout period does not trigger disconnect or `notifyStopSearching(false)`.

### Test Case 4: Bluetooth Power Cycle & Teardown (`TST-CON-045.4`)
* **Scope**: Unit Test (`PolarOnlineStreamingBridgeTest.kt`).
* **Assertions**: `blePowerStateChanged(false)` halts streaming jobs, cancels search timeout, and broadcasts `notifyStopSearching(false)` if searching. `shutDown()` cleanly tears down all jobs and registry entries.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-CON-045.1` | Deterministic search timeout & queue unblocking | `TST-CON-045.1` | Unit Test |
| `REQ-CON-045.2` | Idempotent search & session state recovery on cached `polarApi` | `TST-CON-045.2` | Unit Test |
| `REQ-CON-045.3` | Search watchdog cancellation on connection | `TST-CON-045.3` | Unit Test |
| `REQ-CON-045.4` | Bluetooth adapter power cycle & lifecycle teardown | `TST-CON-045.4` | Unit Test |
