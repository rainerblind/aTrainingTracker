# Stage 2 Test Specification: ATT-7546

**Ticket:** ATT-7546 (`[Bug] Fix ANT+ Bike Radar Cold-Start Channel Acquisition Race with AntRadioService and UsbAntService`)  
**Parent Epic:** ATT-1286 | **Sprint:** 2026-46.6 | **Target Release:** V5.0.0 | **Story Points:** 2 SP  
**Role:** `agent1` (Subagent Flash) / Review: `agent2` (Subagent Pro) | **Status:** In Progress (Stage 2)

---

## 1. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Method | Target Component | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-037.1` | `TST-CON-037.1` | Unit | `AntChannelHelper.kt` | Proposed |
| `REQ-CON-037.2` | `TST-CON-037.2`, `TST-CON-037.3` | Unit | `AntChannelHelper.kt` | Proposed |
| `REQ-CON-037.3` | `TST-CON-037.5` | Unit | `ANTBikeRadarDevice.kt`, `ANTSearchForNewDevicesEngineMultiDeviceSearch.java` | Proposed |
| `REQ-CON-037.4` | `TST-CON-037.2` | Unit | `AntChannelHelper.kt` | Proposed |

---

## 2. Requirement Specification (`REQ-CON-037`)

### REQ-CON-037: ANT+ Bike Radar Cold-Start Channel Acquisition and UsbAntService Race Resilience [Proposed]
- **Tests**: `TST-CON-037`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AntChannelHelper.kt`, `ANTBikeRadarDevice.kt`, `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`
- **Rationale**: When an external USB ANT+ stick is plugged in, `AntRadioService` cold-starts `UsbAntService` asynchronously on a background executor. Initial channel acquisition attempts via `AntChannelHelper.acquireAntPlusChannel()` immediately throw `ChannelNotAvailableException` (`SERVICE_INITIALIZING` / `NO_ADAPTERS_EXIST` or zero available channels). The system must register a broadcast receiver for `AntChannelProvider.ACTION_CHANNEL_PROVIDER_STATE_CHANGED` with `ContextCompat.RECEIVER_EXPORTED`, execute bounded interval retry backoff (up to 5 retries spaced 1000ms apart with 10s overall timeout), and automatically acquire the channel once channels become available, ensuring clean unregistration upon teardown (ATT-7546, Parent Epic ATT-1286, extending `REQ-CON-032`):
- **Description**:
  1. *Shared Asynchronous Channel Acquisition Engine (`REQ-CON-037.1`)*:
     - `AntChannelHelper` SHALL provide an asynchronous channel acquisition facility (`acquireAntPlusChannelAsync`) returning an `AcquisitionCancellationHandle`.
     - Upon invocation, it SHALL attempt immediate channel acquisition. If channels are available (`provider.getNumChannelsAvailable() > 0`), it SHALL acquire the channel and deliver it via `onChannelAcquired(channel)`.
  2. *Cold-Start State Observation & Bounded Backoff (`REQ-CON-037.2`)*:
     - If initial acquisition fails with `ChannelNotAvailableException` indicating `SERVICE_INITIALIZING` or `NO_ADAPTERS_EXIST` (or `getNumChannelsAvailable() == 0`), the engine SHALL register a `BroadcastReceiver` for `AntChannelProvider.ACTION_CHANNEL_PROVIDER_STATE_CHANGED` using `ContextCompat.RECEIVER_EXPORTED`.
     - The engine SHALL schedule periodic retry attempts (1000ms interval, up to 5 retries) and enforce a 10-second overall timeout.
     - When `ACTION_CHANNEL_PROVIDER_STATE_CHANGED` is received or a retry timer fires, the engine SHALL verify `provider.getNumChannelsAvailable() > 0` before attempting `acquireAntPlusChannel`.
     - Upon successful acquisition, the receiver and retry timers SHALL be unregistered and `onChannelAcquired(channel)` SHALL be invoked.
     - If the 10-second timeout expires or an unrecoverable exception occurs, the receiver and timers SHALL be unregistered and `onAcquisitionFailed(exception)` SHALL be invoked.
  3. *Clean Lifecycle Teardown & Unregistration (`REQ-CON-037.3`)*:
     - In `ANTSearchForNewDevicesEngineMultiDeviceSearch`, invocation of `stopAsyncSearch()` or `onServiceDisconnected()` SHALL invoke `cancel()` on any pending channel acquisition handle.
     - In `ANTBikeRadarDevice`, invocation of `shutDown()`, `onWatchdogTimeout()`, or `onServiceDisconnected()` SHALL invoke `cancel()` on any pending channel acquisition handle.
  4. *Security & Broadcast Spoofing Guard (`REQ-CON-037.4`)*:
     - The broadcast receiver SHALL treat `ACTION_CHANNEL_PROVIDER_STATE_CHANGED` solely as an event notification and SHALL query the authentic bound `AntChannelProvider` IPC interface for channel availability, never relying on untrusted intent extras.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an external USB ANT+ stick plugged in and `AntRadioService` cold-starting,
• *When* `ANTBikeRadarDevice` or `ANTSearchForNewDevicesEngineMultiDeviceSearch` attempts channel acquisition and encounters `SERVICE_INITIALIZING` or `NO_ADAPTERS_EXIST`,
• *Then* the acquisition engine registers a receiver for `ACTION_CHANNEL_PROVIDER_STATE_CHANGED` with `ContextCompat.RECEIVER_EXPORTED` and schedules 1000ms retry backoff.
• *Given* an active waiting acquisition session,
• *When* `UsbAntService` completes initialization and broadcasts `ACTION_CHANNEL_PROVIDER_STATE_CHANGED` or a retry fires with available channels,
• *Then* `AntChannel` is acquired on Predefined Network slot 1, the receiver is unregistered, and `onChannelAcquired(channel)` is invoked.
• *Given* channels remain unavailable for 10 seconds,
• *When* the overall timeout expires,
• *Then* the receiver is cleanly unregistered and `onAcquisitionFailed(exception)` is invoked without unhandled crashes.
• *Given* an ongoing acquisition attempt,
• *When* the user stops search (`stopAsyncSearch()`) or the device is shut down (`shutDown()`),
• *Then* `cancel()` unregisters the receiver and removes all pending handler callbacks immediately.

**Invariants**: Predefined Network Slot 1 isolation; zero regression on BLE radar or standard ANT PCC devices; memory leak prevention via deterministic unregistration; thread safety on MainLooper.

---

## 3. Test Specification (`TST-CON-037`)

### TST-CON-037: ANT+ Bike Radar Cold-Start Channel Acquisition and Race Resilience Test Suite [Proposed]
- **Verifies**: `REQ-CON-037`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Component**: `AntChannelHelperTest.kt`, `ANTBikeRadarDeviceTest.kt`, `ANTSearchForNewDevicesEngineMultiDeviceSearchTest.kt`
- **Jira**: `ATT-7546`
- **Test Procedures**:
  1. *Immediate Synchronous Channel Acquisition Test (`TST-CON-037.1`)*:
     - Execute `AntChannelHelper.acquireAntPlusChannelAsync` when `provider.getNumChannelsAvailable() > 0`. Assert immediate invocation of `onChannelAcquired(channel)` without registering a `BroadcastReceiver`.
  2. *Cold-Start Broadcast-Triggered Channel Acquisition Test (`TST-CON-037.2`)*:
     - Simulate initial `acquireAntPlusChannel` throwing `ChannelNotAvailableException(SERVICE_INITIALIZING)`.
     - Assert `BroadcastReceiver` registered for `ACTION_CHANNEL_PROVIDER_STATE_CHANGED` with `ContextCompat.RECEIVER_EXPORTED`.
     - Dispatch mock intent with `NUM_CHANNELS_AVAILABLE = 8`.
     - Assert `onChannelAcquired(channel)` invoked and `BroadcastReceiver` unregistered.
  3. *Bounded Retry Backoff Fallback Test (`TST-CON-037.3`)*:
     - Simulate `SERVICE_INITIALIZING` with delayed broadcast.
     - Advance simulated timer by 1000ms. Assert retry attempts channel acquisition.
  4. *Acquisition Timeout & User-Visible Failure Test (`TST-CON-037.4`)*:
     - Simulate persistent channel unavailability for 10,000ms (5 retries exhausted).
     - Assert `onAcquisitionFailed(exception)` invoked with timeout message, and `BroadcastReceiver` unregistered.
  5. *Teardown & Unregistration Contract Tests (`TST-CON-037.5`)*:
     - Verify calling `cancel()` unregisters receiver and removes all pending handler callbacks.
     - Verify `ANTSearchForNewDevicesEngineMultiDeviceSearch.stopAsyncSearch()` cancels pending acquisition handle.
     - Verify `ANTBikeRadarDevice.shutDown()` and `onServiceDisconnected()` cancel pending acquisition handle.
