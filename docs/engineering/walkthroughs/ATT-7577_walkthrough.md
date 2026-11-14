# Stage 5: Walkthrough & Verification - ATT-7577: Fix ANTBikeLightDevice wildcard fallback trigger and verify Bike Lights mode bytes

**Ticket**: [ATT-7577](https://issue-tracker/ATT-7577)  
**Parent Epic**: [ATT-1286](https://issue-tracker/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.6`  
**Requirement Mapping**: `REQ-CON-033` (`REQ-CON-033.1`, `REQ-CON-033.2`, `REQ-CON-033.3`)  
**Test Mapping**: `TST-CON-033` (`TST-CON-033.6`, `TST-CON-033.7`)  
**Branch**: `feat/ATT-7577`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Executive Summary & Verification Overview

This walkthrough documents the construction and verification of **ATT-7577**:
1. **Safe Channel Event Filtering & Safe Fallback (`REQ-CON-033.1`)**:
   - Decoded `ChannelEventMessage(messageParcel).eventCode` in `ANTBikeLightDevice.channelEventHandler`.
   - Wildcard fallback is gated strictly on `EventCode.RX_SEARCH_TIMEOUT` and `EventCode.CHANNEL_CLOSED`. Non-timeout events (`TX`, `TRANSFER_RX_FAILED`, `TRANSFER_TX_COMPLETED`) no longer trigger wildcard search.
   - In `fallbackToWildcardSearch()`, explicitly invoked `channel.close()` prior to calling `channel.setChannelId(ChannelId(0, 35, 0))`, eliminating open-channel reconfiguration exceptions.
2. **Group Ride Disambiguation & Device/Manufacturer Matching (`REQ-CON-033.1`)**:
   - Added `expectedManufacturerId` property to `ANTBikeLightDevice`, wired automatically from `ANTBikeRadarDevice` when Page `0x50` (`PAGE_MANUFACTURER_ID`) is decoded.
   - `ANTBikeLightDevice` decodes Page `0x50` and extracts sender device number from extended channel info.
   - In wildcard fallback mode (`triedWildcardFallback == true`), `isDeviceAcceptable()` rejects devices with non-matching `antDeviceNumber` or non-matching `manufacturerId`, preventing third-party light hijacking during group rides.
3. **Profile-Aligned Mode Byte Mapping & ACK-Gated State Transitions (`REQ-CON-033.2`, `REQ-CON-033.3`)**:
   - Updated ANT+ Bike Lights mode constants: `OFF = 0x00`, `SOLID = 0x01`, `PELOTON = 0x05`, `NIGHT_FLASH = 0x06`, `DAY_FLASH = 0x07`.
   - `sendLightSettingsCommand()` now tracks `pendingModeCommand` without prematurely mutating `currentLightMode` or notifying `RadarLightController`.
   - State transition confirmation is strictly gated on radio ACK (`EventCode.TRANSFER_TX_COMPLETED`) or incoming Page `0x01` broadcast. On `EventCode.TRANSFER_TX_FAILED`, pending state is discarded cleanly.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Scope & Test Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-033.1` | `TST-CON-033.6` | Channel event filtering & safe close before setChannelId (`ANTBikeLightDeviceTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-033.1` | `TST-CON-033.7` | Group ride device/manufacturer disambiguation on wildcard search (`ANTBikeLightDeviceTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-033.2` | `TST-CON-033.7` | ANT+ Bike Lights profile mode byte decoding (`ANTBikeLightDeviceTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-033.3` | `TST-CON-033.7` | Mode serialization & radio ACK-gated confirmation (`ANTBikeLightDeviceTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-033.5` | `TST-CON-033.5` | Channel isolation & radar telemetry non-interference (`ANTBikeRadarDeviceTest.kt`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-PRO-001` | Living requirement governance audit (`verify_requirement_governance.py`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Regression Test Suite
* **Command**: `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.ant_plus.*" --tests "com.atrainingtracker.banalservice.devices.radar.*" --max-workers=2`
* **Result**: `BUILD SUCCESSFUL` (153 tests completed: 68 `ant_plus` + 85 `radar`, 0 failures, 0 errors, 0 skipped).

### Living Requirement Governance Audit
* **Command**: `python3 tools/verify_requirement_governance.py`
* **Result**: `PASS` (100% parity across living doc shards, 100% REQ-TST bidirectional traceability, zero Composable islands, zero architecture/system violations, Chesterton's fence audit verified).

---

## 4. Invariant & Governance Verification

1. **Radar Isolation**: ANT+ radar target acquisition (`0x28`) and the 3.5s starvation watchdog remain completely independent of companion light events.
2. **Schema Invariance**: Zero SQLite database migrations; companion light operates without duplicate database entries.
3. **Controller Parity**: `RadarLightController` unified interface functions identically across BLE and ANT+ devices.
4. **Group Ride Protection**: Wildcard fallback strictly ignores unrelated devices and competing manufacturers.
