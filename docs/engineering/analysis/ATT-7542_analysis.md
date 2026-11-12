# Stage 1 Analysis: ATT-7542 - Garmin Varia RearVue 820 BLE V2 24-Bit 2D Target Decoder & Pre-Handshake Fallback

**Ticket**: [ATT-7542](https://rainerblind.atlassian.net/browse/ATT-7542)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7542`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
On physical Garmin Varia RearVue 820 hardware, BLE V2 2D telemetry (`6a4e3204`) fails to stream:
1. **Pre-Handshake Battery Ordering & Silence Deadlock**: RearVue 820 requires Battery (`0x2a19`) read and CCCD subscription prior to V2. The peripheral accepts CCCD writes on `6a4e3204` with `GATT_SUCCESS` even when V2 streaming is inactive, emitting 0 notifications. Because `onDescriptorWriteCompleted()` only falls back on `status != GATT_SUCCESS`, bonded devices remain silent indefinitely without falling back to V1 (`6a4e3203`). Subscribing V1 prematurely pins firmware into V1 mode across reconnects.
2. **V2 Wire Format Mismatch**: Current `parseV2ThreatPacket()` expects `0x01` + `8*N` bytes with `sizeAndThreat` bitfields. Real hardware uses 2-byte LE headers (`len == 2` idle heartbeats; bit 2 `0x0004` status frames) and `(len - 2) % 9 == 0` payloads with 24-bit packed little-endian words (11-bit signed lateral $x$, 13-bit signed longitudinal $y$ range), explicit vehicle classes, and $0.5\text{ m/s}$ resolution velocities.

---

## 2. Root Cause Analysis & Independent Verification
* **Independent Codebase Verification**:
  - `BTLERadarDevice.java:208-230`: V2 CCCD write is queued before Battery CCCD (lines 280-285). `onDescriptorWriteCompleted` (line 318) only invokes fallback on `status != GATT_SUCCESS`. No stream-activity watchdog exists.
  - `BTLERadarDevice.java:713-725`: `parseV2ThreatPacket` rejects all frames where `payload[0] != 0x01` or `(len - 1) % 8 != 0`, causing 100% rejection of authentic RearVue 820 V2 frames (`02 00`, `02 90`, and `2 + 9*N` threat frames).
  - Scope cleanup: Tail light control (`6a4e2f00`) is owned by ATT-7545 and excluded from ATT-7542.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Reorder GATT setup in `BTLERadarDevice.java` to enqueue Battery `0x2a19` read + CCCD subscription before V2.
  - Arm a 1500 ms V2 silence-timeout timer after V2 CCCD completion; fallback to V1 (`6a4e3203`) if 0 notifications arrive.
  - Reset V2 fallback state on disconnect so reconnects re-attempt V2 negotiation cleanly.
  - Parse `2 + 9*N` V2 frames: unpack 24-bit words (signed 11-bit $x$, signed 13-bit $y$ range), decode class byte, derive closing speed ($v = -speedY$) and `ThreatLevel` / `LaneState`, and handle idle (`len == 2`) and status frames (`0x0004`).
  - Unit tests in `BTLERadarDeviceTest.kt` verifying 24-bit packed decoding, idle/status packets, and 1500 ms fallback.
* **Out-of-Scope Non-Goals**:
  - `6a4e2f00` Tail Light Control (owned by ATT-7545).
  - Proprietary AMV diagnostic handshake on `6a4e2821`/`6a4e2811` (unneeded for standard telemetry).
  - UI / Edge overlay changes (`TrackingTabsScreen.kt` consumes `RadarState` unchanged).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-CON-030` (`REQ-CON-030.1`, `REQ-CON-030.2`, `REQ-CON-030.3`). Targets `BTLERadarDevice.java`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-5095` (commit `524e937d`) with synthetic 8-byte structs.
* **Root Reason for Existing Formulation**: Simulator initial implementation assumed synthetic `0x01` header and 8-byte structs with embedded threat bits.
* **Preservation of Core Invariants**: 3.5s starvation watchdog, V1 fallback compatibility, 0 SQLite migrations, 9-language translation parity preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-CON-030` (`REQ-CON-030.1`..`REQ-CON-030.3`) refined in `docs/requirements/req_tracking_cockpit.md`.
* **Affected Architecture Elements**: `BTLERadarDevice.java`, `BTLERadarDeviceTest.kt`.
* **Verification Impact & Test Surface**: `TST-CON-030.2`, `com.atrainingtracker.banalservice.devices.bluetooth_le.*` test suite.
* **Risk Assessment & Invariants**: High-Risk BLE protocol change (Rule 7). Mitigated via 1500 ms V2-silence fallback to V1, robust packet header demuxing, and strict unit test coverage of capture frames.
