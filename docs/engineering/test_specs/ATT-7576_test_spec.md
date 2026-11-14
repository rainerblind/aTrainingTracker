# Stage 2 Test Specification: ATT-7576

**Ticket:** ATT-7576 (`[Bug] Fix ANT+ Bike Radar shutdown stale state, binder-thread races, and channel-close reconnect`)  
**Parent Epic:** ATT-1286 | **Sprint:** 2026-46.6 | **Target Release:** V5.0.0 | **Story Points:** 2 SP  
**Role:** `agent1` (Subagent Flash) / Review: `agent2` (Subagent Pro) | **Status:** In Progress (Stage 2)

---

## 1. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Method | Target Component | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-039.1` | `TST-CON-039.1` | Unit | `ANTBikeRadarDevice.kt` | Proposed |
| `REQ-CON-039.2` | `TST-CON-039.2` | Unit | `ANTBikeRadarDevice.kt` | Proposed |
| `REQ-CON-039.3` | `TST-CON-039.3` | Unit | `ANTBikeRadarDevice.kt` | Proposed |
| `REQ-CON-039.4` | `TST-CON-039.4` | Unit | `ANTBikeRadarDevice.kt` | Proposed |

---

## 2. Requirement Specification (`REQ-CON-039`)

### REQ-CON-039: ANT+ Bike Radar Shutdown Cleanup, MainLooper Thread Serialization & Channel-Close Reconnect [Proposed]
- **Tests**: `TST-CON-039`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ANTBikeRadarDevice.kt`
- **Rationale**: When an ANT+ Bike Radar device is shut down, all active target maps, sensor values, and `RadarStateRepository` states must be cleanly reset to prevent ghost vehicles from lingering in downstream Cockpit overlays. Concurrently, ANT message ingestion runs on the IPC binder thread while starvation watchdog timers and shutdown routines run on the main thread; message processing must be serialized onto `Handler(Looper.getMainLooper())` to avoid race conditions. Furthermore, if tracking channel connection drops or the 25-second slave search timeout expires, the device must re-open the channel automatically to resume tracking (ATT-7576, Parent Epic ATT-1286, extending `REQ-CON-032`, `REQ-CON-037`, and `REQ-CON-038`):
- **Description**:
  1. *Clean Shutdown State & Repository Disconnect (`REQ-CON-039.1`)*:
     - In `ANTBikeRadarDevice.shutDown()`, the device SHALL mark `isShutDown = true`, invoke `handler.removeCallbacksAndMessages(null)` to cancel pending runnables and watchdogs, clear `activeTargets`, and publish `RadarState` with `RadarConnectionState.DISCONNECTED`, `ThreatLevel.NONE`, and empty targets to `RadarStateRepository`.
     - `resetSensorsToClear()` SHALL be invoked to clear all scalar sensor values.
  2. *MainLooper Thread Serialization for ANT Message Ingestion (`REQ-CON-039.2`)*:
     - In `ANTBikeRadarDevice.channelEventHandler`, incoming `BROADCAST_DATA` and `ACKNOWLEDGED_DATA` messages received on the ANT binder thread SHALL clone their payload (`payload.clone()`) and dispatch `decodeBroadcastData` onto `handler` (`Looper.getMainLooper()`).
     - State mutations (`activeTargets`, `RadarStateRepository.updateState()`, `windowAggregator`, and `MySensor.newValue()`) SHALL execute exclusively on the main thread, eliminating thread concurrency races with `onWatchdogTimeout()` and `shutDown()`.
     - In `channelEventHandler.onChannelDeath()`, `onWatchdogTimeout()` SHALL also be posted to `handler`.
  3. *Tracking Mode Channel-Close Reconnect (`REQ-CON-039.3`)*:
     - In `ANTBikeRadarDevice.channelEventHandler`, when `MessageFromAntType.CHANNEL_EVENT` reports `EventCode.RX_SEARCH_TIMEOUT` or `EventCode.CHANNEL_CLOSED`, if `!isShutDown` and `antChannel != null`, the event handler SHALL dispatch a channel re-open request (`antChannel.open()`) via `handler`.
     - This re-open SHALL occur regardless of whether `isSearching` is true or false, allowing the radar channel to automatically recover and resume tracking after a 25-second slave search timeout or transient radio drop.
  4. *Shutdown Guard on Late-Arriving Messages (`REQ-CON-039.4`)*:
     - Once `shutDown()` has been invoked (`isShutDown == true`), all callbacks posted to `handler` and any late-arriving ANT messages SHALL be dropped immediately without modifying state, touching sensors, or re-opening `antChannel`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `ANTBikeRadarDevice.shutDown()` is called while targets are active,
• *When* shutdown completes,
• *Then* `activeTargets` is cleared and `RadarStateRepository` transitions to `DISCONNECTED` with empty targets.
• *Given* concurrent ANT+ broadcast packets on the binder thread and watchdog timeout on main,
• *When* messages are received,
• *Then* payload bytes are cloned and decoded sequentially on `Handler(Looper.getMainLooper())` without race conditions.
• *Given* an active tracking session (`isSearching == false`),
• *When* `EVENT_RX_SEARCH_TIMEOUT` or `EVENT_CHANNEL_CLOSED` is received,
• *Then* the channel event handler dispatches `antChannel.open()` to re-arm tracking.
• *Given* `shutDown()` has executed,
• *When* late ANT events arrive,
• *Then* they are ignored without re-opening channels or emitting state changes.

**Invariants**: Predefined Network Slot 1 isolation; thread safety via single-threaded MainLooper serialization; zero memory leaks or unhandled exceptions on shutdown.

---

## 3. Test Specification (`TST-CON-039`)

### TST-CON-039: ANT+ Bike Radar Shutdown Cleanup, Thread Serialization & Channel-Close Reconnect Test Suite [Proposed]
- **Verifies**: `REQ-CON-039`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Component**: `ANTBikeRadarDeviceTest.kt`
- **Jira**: `ATT-7576`
- **Test Procedures**:
  1. *Shutdown State Cleanup & Repository Disconnect Test (`TST-CON-039.1`)*:
     - Populate `activeTargets` with mock radar targets and set `RadarStateRepository` to `CONNECTED`.
     - Invoke `shutDown()`.
     - Verify `activeTargets.isEmpty()` is true and `RadarStateRepository.getState().connectionState == RadarConnectionState.DISCONNECTED`.
  2. *Main Thread Ingestion & Concurrency Serialization Test (`TST-CON-039.2`)*:
     - Deliver `BROADCAST_DATA` parcel on a mock binder thread.
     - Verify `handler.post` is invoked with cloned payload, and state mutations execute on the Handler thread.
     - Deliver `onChannelDeath()` and verify `onWatchdogTimeout()` is posted to `handler`.
  3. *Tracking Mode Channel-Close Reconnect Test (`TST-CON-039.3`)*:
     - Transition device out of search mode (`isSearching = false`).
     - Deliver `CHANNEL_EVENT` with `EventCode.RX_SEARCH_TIMEOUT` and `EventCode.CHANNEL_CLOSED`.
     - Verify `antChannel.open()` is invoked to re-arm the channel for automatic reconnection.
  4. *Post-Shutdown Message Ingestion Suppression Test (`TST-CON-039.4`)*:
     - Call `shutDown()`.
     - Deliver subsequent `BROADCAST_DATA` or `CHANNEL_EVENT` parcels.
     - Verify no channel re-open is attempted and `RadarStateRepository` remains in `DISCONNECTED`.
