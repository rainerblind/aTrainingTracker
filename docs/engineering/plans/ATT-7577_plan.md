# Stage 3: Implementation Plan - ATT-7577: Fix ANTBikeLightDevice wildcard fallback trigger and verify Bike Lights mode bytes

**Ticket**: [ATT-7577](https://issue-tracker/ATT-7577)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-1286](https://issue-tracker/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.6`  
**Requirement Mapping**: `REQ-CON-033` (`REQ-CON-033.1`, `REQ-CON-033.2`, `REQ-CON-033.3`)  
**Test Mapping**: `TST-CON-033` (`TST-CON-033.6`, `TST-CON-033.7`)  
**Branch**: `feat/ATT-7577`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Description & Background
`ANTBikeLightDevice` triggers wildcard fallback on any channel event and configures open channels without closing them. In group rides, wildcard search blindly pairs with unrelated lights. Furthermore, mode commands update state on queueing rather than radio ACK, and mode byte mappings disagree with the ANT+ Bike Lights profile.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-CON-033` (*ANT+ Bike Lights Companion Channel*)
  - `REQ-CON-033.1`: Safe channel event decoding, channel closure on fallback, group ride device/manufacturer matching.
  - `REQ-CON-033.2`: Profile-aligned mode decoding (`0x00`, `0x01`, `0x05`, `0x06`, `0x07`).
  - `REQ-CON-033.3`: Profile-aligned mode serialization and ACK-gated (`TRANSFER_TX_COMPLETED`) mode application.
* **Test Mapping**: `TST-CON-033.6`, `TST-CON-033.7` in `ANTBikeLightDeviceTest.kt`.

---

## 3. System Invariants & Preserved Behavior
1. **Radar Isolation**: Radar telemetry (0x28) and the 3.5s starvation watchdog remain completely independent.
2. **Schema Invariance**: Zero SQLite database migrations or duplicate device rows.
3. **Controller Parity**: `RadarLightController` and `RadarStateRepository` state updates remain seamless.
4. **Code Lock**: Production code modifications strictly locked until Gate 3 passes.

---

## Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-CON-033.1`, `REQ-CON-033.2`, `REQ-CON-033.3`.
* **Historical Origin & Commit Trace**: Introduced in Sprint `2026-46.3` (commit `ec62e15`, ticket `ATT-5103`).
* **Root Reason for Existing Formulation**: Allowed quick wildcard pairing and immediate UI feedback on mode dispatch without awaiting multi-turn ANT channel events.
* **Preservation of Core Invariants**: Retains single companion channel lifecycle and non-blocking radar telemetry while eliminating channel state exceptions and group ride pairing collisions.

---

## 4. Proposed Architectural Changes
### Component 1: `ANTBikeLightDevice.kt`
* In `IAntChannelEventHandler.onReceiveMessage`, unpack `ChannelEventMessage(messageParcel).eventCode`.
* Gating wildcard fallback strictly on `EventCode.RX_SEARCH_TIMEOUT` and `EventCode.CHANNEL_CLOSED`.
* In `fallbackToWildcardSearch()`, explicitly call `channel.close()` before `channel.setChannelId()`.
* Add `expectedManufacturerId: Int? = null` parameter/property, decode Page `0x50` (`PAGE_MANUFACTURER_ID`), and check `extInfo?.deviceNumber` / manufacturer against `antDeviceNumber` and `expectedManufacturerId` before pairing/connecting on wildcard.
* Update mode constants: `ANT_LIGHT_MODE_SOLID_HIGH = 0x01`, `ANT_LIGHT_MODE_SOLID_LOW = 0x05` (Peloton), `ANT_LIGHT_MODE_NIGHT_FLASH = 0x06`, `ANT_LIGHT_MODE_DAY_FLASH = 0x07`.
* Store `pendingModeCommand: RadarLightMode?`; do not update `currentLightMode` or notify `RadarLightController` upon queueing. Update upon `EventCode.TRANSFER_TX_COMPLETED` or Page `0x01` broadcast. Clear on `TRANSFER_TX_FAILED`.

### Component 2: `ANTBikeRadarDevice.kt`
* Pass `manufacturerId` to `companionLightDevice?.expectedManufacturerId` when Page `0x50` is parsed.

### Component 3: `ANTBikeLightDeviceTest.kt`
* Update existing tests for new mode byte constants and ACK-gated state transitions.
* Add `testChannelEventFiltering_nonTimeoutEventsDoNotTriggerFallback_tstCon033_6()`.
* Add `testWildcardFallback_closesChannelBeforeSetChannelId_tstCon033_6()`.
* Add `testWildcardSearch_rejectsUnrelatedDeviceNumberOrManufacturer_tstCon033_7()`.
* Add `testModeByteMappingAndAckConfirmation_tstCon033_7()`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1**: In `ANTBikeLightDevice.kt`, update `channelEventHandler` to parse `ChannelEventMessage` and filter fallback to `RX_SEARCH_TIMEOUT`/`CHANNEL_CLOSED`. Add safe `channel.close()` in `fallbackToWildcardSearch()`.
2. **Step 2**: Add `expectedManufacturerId` and device/manufacturer validation in `ANTBikeLightDevice.kt` and wire in `ANTBikeRadarDevice.kt:parseManufacturerPage()`.
3. **Step 3**: Fix mode byte constants and ACK handling (`TRANSFER_TX_COMPLETED`/`TRANSFER_TX_FAILED`) in `ANTBikeLightDevice.kt`.
4. **Step 4**: Implement test cases in `ANTBikeLightDeviceTest.kt`.
5. **Step 5**: Run targeted unit tests: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.ant_plus.*"`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests in `ANTBikeLightDeviceTest` and requirement governance verification.
* **Rollback**: Clean git branch revert on `feat/ATT-7577`.
