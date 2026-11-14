# Stage 1 Analysis: ATT-7576 - Fix ANT+ Bike Radar Shutdown Stale State, Binder-Thread Races, and Channel-Close Reconnect

**Ticket**: [ATT-7576](https://rainerblind.atlassian.net/browse/ATT-7576)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Branch**: `feat/ATT-7576`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
Findings M3/M4/M5 from technical review of `ANTBikeRadarDevice.kt`:
1. **Shutdown Leaves Stale Target State (M3)**: `shutDown()` removes the watchdog callback and unbinds the service, but never clears `activeTargets` or publishes `RadarConnectionState.DISCONNECTED` to `RadarStateRepository`. Downstream UI and Cockpit overlays remain stuck in `CONNECTED` with stale ghost vehicles.
2. **Binder-Thread Concurrency Races (M4)**: `channelEventHandler.onReceiveMessage()` runs on the ANT IPC binder thread while `onWatchdogTimeout()` runs on `Handler(Looper.getMainLooper())`. State mutations (`activeTargets`, `RadarStateRepository`, `windowAggregator`, and `MySensor.newValue()`) run off-main without synchronization, risking state corruption and Android UI crashes.
3. **Channel-Close Reconnect Blocked in Tracking Mode (M5)**: `channelEventHandler` previously only inspected `CHANNEL_EVENT` when `isSearching == true`. Once connected (`isSearching == false`), any 25s search timeout (`RX_SEARCH_TIMEOUT`) or radio drop (`CHANNEL_CLOSED`) permanently abandons the channel without re-opening.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Codebase Verification**:
  * Inspected `ANTBikeRadarDevice.kt:491-519` (`shutDown()`): Purges `watchdogRunnable`, closes channel, and unbinds service, but omits `activeTargets.clear()` and `RadarStateRepository.updateState()`. `RadarState` remains unchanged.
  * Inspected `ANTBikeRadarDevice.kt:161-171` (`onReceiveMessage()`): Directly invokes `decodeBroadcastData()` on the calling binder thread. `parseTargetsPage()` mutates `activeTargets`, calls `RadarStateRepository.updateState()`, and triggers `threatCountSensor.newValue()` on the background IPC binder thread.
  * Inspected `ANTBikeRadarDevice.kt:172-183`: Guards `CHANNEL_EVENT` re-open behind `if (isSearching)`. In tracking mode (`isSearching == false`), `EVENT_RX_SEARCH_TIMEOUT` and `EVENT_CHANNEL_CLOSED` are silently ignored; channel remains closed forever.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * In `shutDown()`: Set `isShutDown = true`, cancel pending runnables via `handler.removeCallbacksAndMessages(null)`, clear `activeTargets`, publish `RadarConnectionState.DISCONNECTED` with empty targets to `RadarStateRepository`, and reset sensors.
  * In `channelEventHandler`: Clone incoming packet payload and post `decodeBroadcastData` onto `handler` (`Looper.getMainLooper()`). Post `onWatchdogTimeout` to `handler` from `onChannelDeath()`.
  * In `channelEventHandler`: Re-open `antChannel` upon `RX_SEARCH_TIMEOUT` or `CHANNEL_CLOSED` whenever `!isShutDown` (both in search and tracking modes).
* **Out-of-Scope Non-Goals**:
  * Modifying BLE radar (`BTLERadarDevice.java`) or other ANT PCC devices.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-CON-032.2` & `REQ-CON-038.2` in `docs/requirements/req_tracking_cockpit.md`.
* **Historical Origin & Commit Trace**: Sprints 2026-46.3 (`ATT-5084`) and 2026-46.6 (`ATT-7547`).
* **Root Reason for Existing Formulation**: Scoped channel re-open strictly to initial discovery search (`isSearching == true`) to avoid interference with tracking, overlooking that a 25s tracking loss also closes the slave channel.
* **Preservation of Core Invariants**: Predefined Network Slot 1 isolation, 3.5s starvation watchdog, and strict sensor decoupling remain intact.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-CON-039` (ANT+ Bike Radar shutdown cleanup, main-thread serialization & channel-close reconnect).
* **Affected Architecture Elements**: `ANTBikeRadarDevice.kt`.
* **Verification Impact & Test Surface**: `ANTBikeRadarDeviceTest.kt`.
* **Risk Assessment & Invariants**: Low risk. Thread serialization via existing `Handler(Looper.getMainLooper())` eliminates concurrency races without adding locking overhead.

---

## 6. Architectural Strategy & High-Level Solution
1. Introduce `private var isShutDown = false`.
2. In `shutDown()`: set `isShutDown = true`, call `handler.removeCallbacksAndMessages(null)`, clear `activeTargets`, emit `RadarState(DISCONNECTED, NONE, emptyList())`, and reset sensors.
3. In `onReceiveMessage`: clone payload bytes and dispatch `handler.post { if (!isShutDown) decodeBroadcastData(clonedPayload) }`.
4. In `CHANNEL_EVENT`: dispatch `handler.post { if (!isShutDown) antChannel?.open() }` on `RX_SEARCH_TIMEOUT` / `CHANNEL_CLOSED`.
5. In `onChannelDeath`: dispatch `handler.post { if (!isShutDown) onWatchdogTimeout() }`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. MainLooper thread affinity: All state mutations and sensor broadcasts execute strictly on `Looper.getMainLooper()`.
  2. Immediate disconnect propagation upon `shutDown()`.
  3. Seamless auto-recovery from transient RF dropouts in tracking mode.
* **Risk Rating**: **LOW** — Localized to `ANTBikeRadarDevice.kt` lifecycle and handler synchronization.
