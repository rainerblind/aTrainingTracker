# Stage 2: Requirement & Test Specification - ATT-7577: Fix ANTBikeLightDevice wildcard fallback trigger and verify Bike Lights mode bytes

**Ticket**: [ATT-7577](https://issue-tracker/ATT-7577)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-1286](https://issue-tracker/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.6`  
**Requirement Mapping**: `REQ-CON-033` (`REQ-CON-033.1`, `REQ-CON-033.2`, `REQ-CON-033.3`)  
**Test Spec ID**: `TST-CON-033` (`TST-CON-033.6`, `TST-CON-033.7`)  
**Branch**: `feat/ATT-7577`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-CON-033)

### 1.1 Problem Statement & Rationale
`ANTBikeLightDevice` must safely handle channel events, avoid reconfiguring open channels, prevent hijacking third-party ANT+ lights in group rides, accurately serialize mode bytes against the ANT+ Bike Lights profile, and await radio ACK before marking modes applied.

### 1.2 Attributes
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit / Integration

### 1.3 Functional & Architectural Requirements
1. `ANTBikeLightDevice` SHALL parse `ChannelEventMessage` and restrict wildcard fallback strictly to `EventCode.RX_SEARCH_TIMEOUT` and `EventCode.CHANNEL_CLOSED`.
2. Prior to calling `setChannelId(ChannelId(0, 35, 0))`, `ANTBikeLightDevice` SHALL close the active ANT channel.
3. During wildcard search, `ANTBikeLightDevice` SHALL reject pairing and control of devices with non-matching `deviceNumber` (when `antDeviceNumber != 0`) or non-matching `manufacturerId` (Page `0x50`).
4. `ANTBikeLightDevice` SHALL map modes per profile specification: `0x00`=Off, `0x01`=Solid, `0x05`=Peloton, `0x06`=Night Flash, `0x07`=Day Flash.
5. Applied mode state SHALL NOT update upon queueing; confirmation SHALL be gated on `EventCode.TRANSFER_TX_COMPLETED` or Page `0x01` broadcast.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1**: *Given* a non-timeout `CHANNEL_EVENT` (e.g. `TX`, `TRANSFER_RX_FAILED`) *When* received *Then* wildcard fallback is not triggered.
* **Criterion 2**: *Given* multiple ANT+ lights nearby *When* fallback search runs *Then* another rider's unrelated light is not paired.
* **Criterion 3**: *Given* a mode change request *When* transmitted *Then* mode is marked applied only after ACK (`TRANSFER_TX_COMPLETED`) or Page `0x01` confirmation.

### 1.5 System Invariants
Radar telemetry (0x28) and 3.5s watchdog independence; zero SQLite schema mutations.

---

## Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-CON-033.1`, `REQ-CON-033.2`, `REQ-CON-033.3`.
* **Historical Origin & Commit Trace**: Introduced in Sprint `2026-46.3` (commit `ec62e15`, ticket `ATT-5103`).
* **Root Reason for Existing Formulation**: Allowed quick wildcard pairing and immediate UI feedback on mode dispatch without awaiting multi-turn ANT channel events.
* **Preservation of Core Invariants**: Retains single companion channel lifecycle and non-blocking radar telemetry while eliminating channel state exceptions and group ride pairing collisions.

---

## 2. Test Specification (TST-CON-033)

### Test Case 1: Channel Event Filtering & Safe Fallback (`TST-CON-033.6`)
* **Scope**: Unit Test in `ANTBikeLightDeviceTest.kt`
* **Preconditions**: `ANTBikeLightDevice` open with `antDeviceNumber = 12345`.
* **Action**: Inject `MessageFromAntType.CHANNEL_EVENT` with `EventCode.TX`, `TRANSFER_RX_FAILED`, `TRANSFER_TX_COMPLETED` $\rightarrow$ verify `triedWildcardFallback == false`. Inject `RX_SEARCH_TIMEOUT` $\rightarrow$ verify channel is closed before `setChannelId(0)` and `triedWildcardFallback == true`.

### Test Case 2: Group Ride Disambiguation & Verified Mode Bytes (`TST-CON-033.7`)
* **Scope**: Unit Test in `ANTBikeLightDeviceTest.kt`
* **Preconditions**: `ANTBikeLightDevice` in wildcard fallback.
* **Action**: Inject broadcast from unrelated device number / different manufacturer ID $\rightarrow$ assert `sendConnectCommand()` not called. Inject matching device $\rightarrow$ send `DAY_FLASH` (`0x07`), `PELOTON` (`0x05`), `NIGHT_FLASH` (`0x06`) $\rightarrow$ verify byte mapping and assert mode applies only upon `TRANSFER_TX_COMPLETED` or Page `0x01`.

### Test Case 3: Fast Targeted Regression
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.ant_plus.*"`

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-CON-033.1` | Event filtering strictly to timeout/closed & safe channel close | `TST-CON-033.6` | Unit Test |
| `REQ-CON-033.1` | Group ride device/manufacturer matching on wildcard search | `TST-CON-033.7` | Unit Test |
| `REQ-CON-033.2` | Mode decoding alignment (`0x00`, `0x01`, `0x05`, `0x06`, `0x07`) | `TST-CON-033.7` | Unit Test |
| `REQ-CON-033.3` | Mode byte serialization & ACK-gated mode application | `TST-CON-033.7` | Unit Test |
| `REQ-CON-033.5` | Channel isolation & radar non-interference | `TST-CON-033.5` | Unit Test |
