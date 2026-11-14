# Stage 1 Analysis: ATT-7547 - Fix ANT+ Bike Radar Discovery Scan Timeout, Channel Event Handling, and ExtendedData Parsing

**Ticket**: [ATT-7547](https://rainerblind.atlassian.net/browse/ATT-7547)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Branch**: `feat/ATT-7547`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
Even after acquiring an ANT+ channel on predefined network slot 1, ANT+ Bike Radar discovery and tracking channels suffer from three protocol/lifecycle defects:
1. **Silent Search Death After 25 Seconds**: Slave channels set with `LowPrioritySearchTimeout.TWENTY_FIVE_SECONDS` generate `EVENT_RX_SEARCH_TIMEOUT` and `EVENT_CHANNEL_CLOSED` after 25s. Because `CHANNEL_EVENT` messages are ignored, channels close silently while the UI remains in active search.
2. **Missing Background Scanning & Auto-Reopen**: Radar discovery uses basic `BIDIRECTIONAL_SLAVE` without `ExtendedAssignment(BACKGROUND_SCANNING)` or automatic re-opening on `EVENT_CHANNEL_CLOSED`.
3. **Unprotected LibConfig & Fragile Data Parsing**: `setAdapterWideLibConfig()` fails fatally if another open channel already configured the adapter (`AntCommandFailedException`). Furthermore, extended data parsing only handled `BROADCAST_DATA` and hardcoded RSSI to `-1`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * Inspected `ANTSearchForNewDevicesEngineMultiDeviceSearch.java:105`: Sets 25s search timeout and ignores `CHANNEL_EVENT` messages in `onReceiveMessage()`. After 25s, slave channel closes permanently without notification.
  * Inspected `ANTBikeRadarDevice.kt:235`: Sets 25s search timeout in `configureChannel()` and ignores `CHANNEL_EVENT`. If a paired radar is switched on $>25\text{ s}$ after search begins, the tracking channel has already closed.
  * Inspected `setAdapterWideLibConfig()` in both classes: Neither catches `AntCommandFailedException`. If an HRM or Power sensor is open on the USB stick, configuring radar channel throws an unhandled exception.
  * Inspected message demuxing: Only `BroadcastDataMessage` was instantiated; `AcknowledgedDataMessage` was treated as raw broadcast or ignored, and `ExtendedData.getRssi()` was not propagated.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Continuous radar discovery: Support `ExtendedAssignment` with background scanning when supported by adapter `Capabilities`, and handle `CHANNEL_EVENT` (`RX_SEARCH_TIMEOUT` / `CHANNEL_CLOSED`) by re-opening the channel while search is active.
  * Re-open tracking channel in `ANTBikeRadarDevice` when searching for paired radar upon 25s timeout.
  * Gracefully handle `AntCommandFailedException` on `setAdapterWideLibConfig()` across both classes.
  * Standardized `DataMessage` extended data extraction (`ChannelId`, `Rssi`) in shared `AntChannelHelper`.
* **Out-of-Scope Non-Goals**:
  * Modifying BLE radar or non-radar ANT PCC sensors (`HRM`, `BIKE_POWER`, etc.).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-CON-032.2` & `REQ-CON-032.5` (`docs/requirements/req_tracking_cockpit.md`).
* **Historical Origin & Commit Trace**: Sprint 2026-46.3 (`ATT-5084`).
* **Root Reason for Existing Formulation**: Assumed wildcard slave channel remains open indefinitely and `setAdapterWideLibConfig()` always succeeds on an exclusive adapter.
* **Preservation of Core Invariants**: Predefined Network Slot 1 (`ANT_PLUS`) isolation, channel parameters (4084 period, RF 57, device type 40), and 3.5s starvation watchdog remain 100% preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-CON-038` (Continuous discovery, channel events & extended data parsing).
* **Affected Architecture Elements**: `AntChannelHelper.kt`, `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`, `ANTBikeRadarDevice.kt`.
* **Verification Impact & Test Surface**: `AntChannelHelperTest.kt`, `ANTSearchForNewDevicesEngineMultiDeviceSearchTest.kt`, `ANTBikeRadarDeviceTest.kt`.
* **Risk Assessment & Invariants**: Low risk. Thread-safe execution via existing handlers; robust fallback for adapters lacking background scanning; zero disruption to open channels.

---

## 6. Architectural Strategy & High-Level Solution
* Add helper methods in `AntChannelHelper`:
  - `safeSetAdapterWideLibConfig(channel, libConfig)`
  - `assignScanChannel(channel): Boolean` (attempts background scanning assignment, falls back to basic slave)
  - `extractExtendedDeviceAndRssi(messageType, messageParcel): Pair<Int, Int>?`
* In `ANTSearchForNewDevicesEngineMultiDeviceSearch`: assign scan channel, handle `CHANNEL_EVENT` by re-opening channel if active, extract RSSI and device number from `DataMessage`.
* In `ANTBikeRadarDevice`: catch `AntCommandFailedException` in `setAdapterWideLibConfig()`, handle `CHANNEL_EVENT` by re-opening channel while searching for paired device.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression on existing ANT+ sensors or BLE radar.
  2. Bounded search lifecycle (search stops cleanly when user taps stop or search times out globally).
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** — Self-contained ANT+ channel event and configuration resilience enhancements.
