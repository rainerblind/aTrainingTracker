# Stage 3: Implementation Plan - ATT-7576: Fix ANT+ Bike Radar Shutdown Stale State, Binder-Thread Races, and Channel-Close Reconnect

**Ticket**: [ATT-7576](https://rainerblind.atlassian.net/browse/ATT-7576)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-CON-039` (`REQ-CON-039.1`..`REQ-CON-039.4`)  
**Test Mapping**: `TST-CON-039` (`TST-CON-039.1`..`TST-CON-039.4`)  
**Branch**: `feat/ATT-7576`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Description & Background
In `ANTBikeRadarDevice.kt`:
1. `shutDown()` purges the watchdog callback but never resets `RadarStateRepository` or clears `activeTargets`, leaving ghost vehicles and `CONNECTED` state lingering in the UI.
2. `onReceiveMessage()` runs on the ANT IPC binder thread while `onWatchdogTimeout()` runs on the main thread, creating thread concurrency races on `activeTargets`, `RadarStateRepository`, and `MySensor.newValue()`.
3. `channelEventHandler` previously only re-opened channels when `isSearching == true`. In tracking mode, an RF drop or 25s slave search timeout leaves the channel permanently closed.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-CON-039` (*ANT+ Bike Radar Shutdown Cleanup, MainLooper Thread Serialization & Channel-Close Reconnect*)
* **Test Mapping**: `TST-CON-039` (*ANT+ Bike Radar Shutdown Cleanup, Thread Serialization & Channel-Close Reconnect Test Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Predefined Network Slot 1 Isolation**: Channel parameters (RF 57, period 4084, device type 40) remain unchanged.
2. **MainLooper Affinity**: All state mutations, sensor dispatches, and repository updates execute sequentially on `Looper.getMainLooper()`.
3. **Shutdown Finality**: Once `shutDown()` is invoked, no late-arriving binder callbacks or channel events may re-open channels or modify state.
4. **Parent Human Gate Invariance**: Terminal ticket progression remains reserved for human approval.

---

## 4. Proposed Architectural Changes

### Component 1: `ANTBikeRadarDevice.kt`
* Add lifecycle guard `private var isShutDown = false`.
* In `startSearching()`: reset `isShutDown = false`.
* In `shutDown()`:
  - Set `isShutDown = true`.
  - Purge pending handler execution via `handler.removeCallbacksAndMessages(null)`.
  - Clear `activeTargets.clear()`.
  - Update `RadarStateRepository` to `RadarConnectionState.DISCONNECTED` with `ThreatLevel.NONE` and `emptyList()`.
  - Reset sensors via `resetSensorsToClear()`.
* In `channelEventHandler`:
  - On `BROADCAST_DATA` / `ACKNOWLEDGED_DATA`: clone payload bytes (`payload.clone()`) and dispatch `handler.post { if (!isShutDown) { if (isSearching) notifyStopSearching(true); decodeBroadcastData(clonedPayload) } }`.
  - On `CHANNEL_EVENT`: if `eventCode == RX_SEARCH_TIMEOUT || eventCode == CHANNEL_CLOSED`, dispatch `handler.post { if (!isShutDown) antChannel?.open() }`.
  - On `onChannelDeath()`: dispatch `handler.post { if (!isShutDown) onWatchdogTimeout() }`.

### UI Consistency (Rule 23)
* No UI components modified.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Thread-Safe Ingestion & Shutdown Cleanup in `ANTBikeRadarDevice.kt` (`REQ-CON-039.1`, `REQ-CON-039.2`, `REQ-CON-039.4`)
* Add `isShutDown` flag.
* Update `channelEventHandler` to clone payload and post message decoding to `handler`.
* Update `shutDown()` to clear targets, update `RadarStateRepository` to `DISCONNECTED`, and cancel handler callbacks.

### Step 2: Channel-Close & Search Timeout Automatic Reconnect (`REQ-CON-039.3`)
* In `channelEventHandler`, trigger channel re-open on `RX_SEARCH_TIMEOUT` / `CHANNEL_CLOSED` whenever `!isShutDown` (removing the `isSearching` restriction).

### Step 3: Unit Test Suite Construction in `ANTBikeRadarDeviceTest.kt` (`TST-CON-039.1`..`TST-CON-039.4`)
* Test `shutDown()` state reset and repository transition to `DISCONNECTED`.
* Test main thread dispatch and serialization for incoming data messages and channel death.
* Test channel re-open on `CHANNEL_CLOSED` / `RX_SEARCH_TIMEOUT` during tracking mode (`isSearching == false`).
* Test suppression of post-shutdown events.
* Verification Command:
  ```bash
  python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.ant_plus.*" --max-workers=2
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted unit tests (`ant_plus.*`) and `verify_requirement_governance.py`.
* **Rollback**: Clean branch isolation on `feat/ATT-7576` allows full git revert.
