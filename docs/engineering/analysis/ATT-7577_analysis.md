# Stage 1 Analysis: ATT-7577 - Fix ANTBikeLightDevice wildcard fallback trigger and verify Bike Lights mode bytes

**Ticket**: [ATT-7577](https://issue-tracker/ATT-7577)  
**Parent Epic**: [ATT-1286](https://issue-tracker/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.6`  
**Branch**: `feat/ATT-7577`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
In `ANTBikeLightDevice.kt`, review findings M6/m8 revealed three defects:
1. Lines 121–124 trigger `fallbackToWildcardSearch()` on *any* `CHANNEL_EVENT` (including `TX`, `TRANSFER_RX_FAILED`, `TRANSFER_TX_COMPLETED`), and attempt `setChannelId()`/`open()` without closing the active channel first.
2. Wildcard pairing blindly connects to any nearby ANT+ light (`deviceNumber=0`), risking hijacking third-party lights during group rides.
3. `sendLightSettingsCommand()` marks the mode applied upon queueing rather than radio ACK (`TRANSFER_TX_COMPLETED`), and mode byte constants align incorrectly with the ANT+ Bike Lights profile (`DAY_FLASH` was `0x05` [low steady] instead of `0x07` [fast flash]; `PELOTON` was `0x02` instead of `0x05`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (Channel Event Decoding)**: `IAntChannelEventHandler.onReceiveMessage` receives `MessageFromAntType.CHANNEL_EVENT`, but never parsed `ChannelEventMessage(messageParcel).eventCode`. It treated all channel events as search timeouts. Reconfiguring `setChannelId` on an open ANT channel throws `ChannelNotAvailableException` or `AntCommandFailedException`.
* **RCA 2 (Wildcard Group Collision)**: Wildcard search accepted any Page `0x01` broadcast without cross-checking the radar's `antDeviceNumber` or `manufacturerId` (Page `0x50`).
* **RCA 3 (Mode Byte & ACK Desync)**: In ANT+ Bike Lights Device Profile: `0x00`=Off, `0x01`=Steady High, `0x05`=Steady Low (Peloton), `0x06`=Slow Flash (Night Flash), `0x07`=Fast Flash (Day Flash). `ANTBikeLightDevice` mapped `DAY_FLASH` to `0x05` and updated `currentLightMode` synchronously upon queueing rather than awaiting `EventCode.TRANSFER_TX_COMPLETED`.
* **Independent Peer Review & Verification**:
  * Verified against `ANTBikeLightDevice.kt:121-124`, `AntChannelHelper.extractExtendedDeviceAndRssi()`, and `RadarLightMode.kt:36-50`.
  * Confirmed `EventCode.RX_SEARCH_TIMEOUT` and `EventCode.CHANNEL_CLOSED` are the only valid trigger codes.

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals**:
  * Parse `ChannelEventMessage` and filter fallback strictly to `RX_SEARCH_TIMEOUT` / `CHANNEL_CLOSED`.
  * Ensure channel is closed before `setChannelId(ChannelId(0, 35, 0))`.
  * Validate sender `deviceNumber` (matching `antDeviceNumber`) and/or `expectedManufacturerId` before pairing/connecting on wildcard.
  * Correct ANT+ mode byte constants (`0x01`=Solid, `0x05`=Peloton, `0x06`=Night Flash, `0x07`=Day Flash).
  * Await ACK (`EventCode.TRANSFER_TX_COMPLETED`) or Page `0x01` confirmation before confirming mode applied.
* **Out-of-Scope Non-Goals**:
  * Modifying BLE GATT radar light protocols (`BTLERadarDevice`).
  * Changing SQLite database schema or multi-light network topology ($>1$ light index).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-CON-033.1`, `REQ-CON-033.2`, `REQ-CON-033.3`.
* **Historical Origin & Commit Trace**: Introduced in Sprint `2026-46.3` (commit `ec62e15`, ticket `ATT-5103`).
* **Root Reason for Existing Formulation**: Allowed quick wildcard pairing and immediate UI feedback on mode dispatch without awaiting multi-turn ANT channel events.
* **Preservation of Core Invariants**: Retains single companion channel lifecycle and non-blocking radar telemetry while eliminating channel state exceptions and group ride pairing collisions.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-CON-033.1`, `REQ-CON-033.2`, `REQ-CON-033.3`.
* **Affected Architecture Elements**: `ANTBikeLightDevice.kt`, `ANTBikeRadarDevice.kt`, `ANTBikeLightDeviceTest.kt`.
* **Verification Impact & Test Surface**: Unit tests in `ANTBikeLightDeviceTest` covering channel event filtering, safe closing on fallback, device/manufacturer validation, ACK-gated mode transitions, and corrected mode byte serialization.
* **Risk Assessment & Invariants**: Low risk. ANT channel isolation guarantees radar target acquisition remains unaffected.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero SQLite schema changes; radar telemetry (0x28) and 3.5s watchdog independence; seamless `RadarLightController` UI parity.
* **Risk Rating**: `LOW` — Localized to ANT+ companion light channel handling with full unit test coverage.
