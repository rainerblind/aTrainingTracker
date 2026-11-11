# Stage 2: Requirement & Test Specification - ATT-5103: Support ANT+ Bike Lights profile (Device Type 0x23) for radar tail light mode control and workout auto-switch

**Ticket**: [ATT-5103](https://rainerblind.atlassian.net/browse/ATT-5103)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `v2026.46`  
**Active Sprint**: `sprint/2026-46.2`  
**Requirement Mapping**: `REQ-CON-033`  
**Test Mapping**: `TST-CON-033`  
**Branch**: `feature/ATT-5103`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-11  

---

## 1. Traceability & Requirements Mapping

* **Requirement**: `REQ-CON-033` (*ANT+ Bike Lights Profile [Device Type 0x23] Companion Channel, Connect Handshake & Tail Light Mode Control*)
* **Test Specification**: `TST-CON-033` (*ANT+ Bike Lights Profile [Device Type 0x23] Companion Channel, Handshake & Mode Control Test Suite*)

---

## 2. Requirements & Verification Matrix

| Requirement ID | Description | Test Case | Target Test Class |
| :--- | :--- | :--- | :--- |
| `REQ-CON-033.1` | ANT+ Bike Lights channel constants (`0x23`, RF 57, Period 4084) & companion lifecycle bound to radar | `TST-CON-033.1` | `ANTBikeLightDeviceTest.kt` |
| `REQ-CON-033.2` | Broadcast decoding: Data Page `0x01` (Main Light State, Index, Battery) & Page `0x02` (Capabilities) | `TST-CON-033.2` | `ANTBikeLightDeviceTest.kt` |
| `REQ-CON-033.3` | Stateful 3-step handshake: Connect (Page `0x21`), Mode Command (Page `0x22`) & Disconnect (Page `0x20`) | `TST-CON-033.3` | `ANTBikeLightDeviceTest.kt` |
| `REQ-CON-033.4` | Unified `RadarLightController` generalization via `RadarLightDeviceClient` for BLE & ANT+ parity | `TST-CON-033.4` | `RadarLightControllerTest.kt` |
| `REQ-CON-033.5` | Fail-safe channel isolation: non-blocking ACK dispatch & zero interference with radar telemetry | `TST-CON-033.5` | `ANTBikeRadarDeviceTest.kt` |

---

## 3. Detailed Acceptance Criteria & Verification Procedures

### TST-CON-033.1: ANT+ Channel Acquisition & Companion Binding
* **Given**: `AntChannelHelper` providing constants for `BIKE_LIGHT_DEVICE_TYPE = 35` / `0x23` and Period `4084`.
* **When**: `ANTBikeRadarDevice` connects and acquires an `AntChannel` for its companion `ANTBikeLightDevice`.
* **Then**: The channel is configured as `BIDIRECTIONAL_SLAVE` on Predefined Network slot 1 (`ANT_PLUS`), matching radar device number, without creating duplicate entries in `DevicesDatabaseManager`.

### TST-CON-033.2: Broadcast Decoding (Pages 0x01 & 0x02)
* **Given**: Raw 8-byte ANT+ broadcast frames for Page `0x01` (Light State, Index, Type, Battery) and Page `0x02` (Capabilities).
* **When**: Decoded by `ANTBikeLightDevice`.
* **Then**: Light index, light type (Tail Light = 2), current mode, battery status, and supported mode bitmasks are parsed accurately and updated in `RadarStateRepository`.

### TST-CON-033.3: 3-Step Handshake (Pages 0x21, 0x22, 0x20)
* **Given**: An unassociated companion light (`Light Index == 0` on Page `0x01`).
* **When**: A mode transition (`Day Flash`, `Peloton`, `Solid`, `Off`) is requested.
* **Then**: `ANTBikeLightDevice` transmits acknowledged Page `0x21` assigning `Light Index = 1`, waits for confirmation in Page `0x01`, transmits Page `0x22` with target mode byte, and transmits Page `0x20` upon disconnect.

### TST-CON-033.4: Unified Controller Abstraction & Auto-Switch Parity
* **Given**: `RadarLightController` managing tail light mode switching.
* **When**: `ANTBikeLightDevice` registers as `RadarLightDeviceClient`.
* **Then**: Cockpit quick-switch bottom sheets and workout auto-switch (`ATT-5102`) trigger identical mode transitions across both BLE and ANT+.

### TST-CON-033.5: Fail-Safe Isolation & Radar Telemetry Non-Interference
* **Given**: An active ANT+ radar device streaming target packets (Pages `0x30`/`0x31`).
* **When**: Companion light channel encounters timeouts, packet drops, or is absent (e.g. RVR315).
* **Then**: Target demuxing and 3.5s starvation watchdog execute uninterrupted on the primary channel.

---

## 4. Invariants & Governance

1. **Predefined Network Slot 1**: Bound exclusively to slot 1 (`ANT_PLUS`), avoiding raw key rejection or RF bounds errors.
2. **Zero Duplicate Devices**: Companion channel lifecycle strictly managed via paired radar device; zero database pollution.
3. **Non-Blocking Execution**: Handshake transactions and acknowledged transmissions execute asynchronously on AIDL dispatchers.
4. **Radar Telemetry Immunity**: Radar target stream (0x28) is never stalled by companion light transactions.
5. **Zero SQLite Migration**: Preserves schema and `DB_VERSION` unchanged.
