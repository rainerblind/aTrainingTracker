# Stage 2: Requirement & Test Specification - ATT-6676: Create tools/ble_radar_simulator.py for Garmin Varia BLE streaming

**Ticket**: [ATT-6676](https://rainerblind.atlassian.net/browse/ATT-6676) | **Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3` | **Branch**: `feature/ATT-6676`  
**Requirement Mapping**: `REQ-SIM-002` (`REQ-TST-006.1`..`REQ-TST-006.4`) | **Test Spec ID**: `TST-SIM-002` (`TST-TST-006.1`..`TST-TST-006.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-SIM-002 / REQ-TST-006)

### 1.1 Problem Statement & Rationale
`RadarTrafficSimulator` provides in-app UI preview but bypasses the physical BLE stack (`BTLERadarDevice`, serialized GATT queues, packet demuxers, and tail light writes). Testing radar discovery, V1/V2 packet decoding, 3.5s watchdog starvation, and tail light mode control currently requires real Garmin Varia hardware. `tools/ble_radar_simulator.py` must emulate Garmin Varia GATT (`6a4e3200`) with V1/V2 threat streams, light control (`6a4e3202`), traffic presets, and 31-byte legacy PDU compliance without external Python dependencies.

### 1.2 Functional & Architectural Requirements
* **REQ-SIM-002.1** (*V2 High-Resolution 2D & V1 Legacy Threat Streaming*): `tools/ble_radar_simulator.py` SHALL implement GATT service `6a4e3200-667b-11e3-949a-0800200c9a66` and emit 5 Hz notifications on V2 characteristic `6a4e3204` (`0x01` + $N \times 8$ bytes: targetId, range 0.1m uint16 LE, speed 0.1m/s int16 LE, lateral 0.1m int16 LE, size/lane/threat uint8). When `--v1` is set, it SHALL emit on V1 characteristic `6a4e3203` (`0x00` + $N \times 3$ bytes: targetId, range uint8, `(threat << 4) | speed`).
* **REQ-SIM-002.2** (*Bidirectional Tail Light Control & DIS StVZO Mode*): Characteristic `6a4e3202` SHALL accept 1-byte GATT writes for light modes (`0x00` OFF, `0x01` SOLID, `0x05` PELOTON, `0x06` NIGHT_FLASH, `0x07` DAY_FLASH) and reflect state on GATT read. Device Information `0x2A24` SHALL support `--stvzo` returning `"RTL516"` / `"010-03180-10"` (or default `"RTL515"`).
* **REQ-SIM-002.3** (*Kinematics Engine, Presets & Watchdog Starvation*): Target physics SHALL update at 5 Hz ($r_{t+1} = r_t - v \cdot dt$) supporting presets: car (`c`), fast car (`f`), truck/bus (`t`), close pass (`p`), convoy (`m`), loop (`l`), all-clear (`0`). Watchdog command (`w`) SHALL starve notifications for $\ge 4.5\text{ s}$ to trigger client `DISCONNECTED_ERROR` ($> 3.5\text{ s}$).
* **REQ-SIM-002.4** (*31-Byte Legacy Advertisement Budget & Pure Stdlib Invariant*): BLE advertising packet SHALL not exceed 31 bytes (18-byte 128-bit Service UUID + 3-byte Flags + $\le 10$-byte Local Name e.g. `'VariaSim'`). Simulator and test suite SHALL strictly execute within Python 3 standard library (`TST-PRO-005.6`).

### 1.3 Acceptance Criteria (Given-When-Then) & System Invariants
* *Given* connected BLE client, *When* vehicle approaches, *Then* V2 notifications broadcast $1 + 8 \times N$ bytes decoded identically by `BTLERadarDevice`.
* *Given* `--v1` switch, *When* target approaches, *Then* V1 notifications broadcast $1 + 3 \times N$ bytes with matching threat and range.
* *Given* client writes `0x01` to `6a4e3202`, *When* read is issued, *Then* value returns `[0x01]` and light state updates.
* *Given* hotkey `w`, *When* triggered, *Then* notifications cease for 4.5s triggering client watchdog disconnect.
* *Invariants*: Pure Python 3 stdlib (`TST-PRO-005.6`), zero Android runtime changes (`app/src/main/`), 31-byte legacy PDU budget.

---

## 2. Test Specification (TST-SIM-002 / TST-TST-006)

### Test Case 1: V2 Multi-Target Binary Encoding Contract Test (`TST-SIM-002.1`)
* **Scope**: Unit Test (`tools/test_ble_radar_simulator.py`)
* **Assertion**: Verify header `0x01`, 8-byte target stride, little-endian encodings, and size/lane/threat bitfield packing matching `BTLERadarDevice`.

### Test Case 2: V1 Legacy Binary Encoding Fallback Test (`TST-SIM-002.2`)
* **Scope**: Unit Test (`tools/test_ble_radar_simulator.py`)
* **Assertion**: Verify header `0x00`, 3-byte target stride, and `(threat << 4) | (speed & 0x0F)` nibble packing.

### Test Case 3: Tail Light Control 6a4e3202 Read/Write Contract Test (`TST-SIM-002.3`)
* **Scope**: Unit Test (`tools/test_ble_radar_simulator.py`)
* **Assertion**: Verify valid writes update light mode state; verify invalid values rejected; verify `ReadValue` returns current mode byte.

### Test Case 4: Kinematics Engine Physics & Traffic Presets Test (`TST-SIM-002.4`)
* **Scope**: Simulation Unit Test (`tools/test_ble_radar_simulator.py`)
* **Assertion**: Verify $r_{t+1} = r_t - v \cdot dt$ closes distance; verify target removed upon pass completion; verify presets `c`, `f`, `t`, `p`, `m`, `0`.

### Test Case 5: Watchdog Starvation & 31-Byte PDU Budget Audit Test (`TST-SIM-002.5`)
* **Scope**: Protocol Test (`tools/test_ble_radar_simulator.py`)
* **Assertion**: Verify watchdog suppresses notifications for 4.5s; verify total advertising PDU length $\le 31$ bytes.

### Test Case 6: Pure Python Standard Library AST Audit (`TST-SIM-002.6`)
* **Scope**: Static AST Audit (`tools/test_ble_radar_simulator.py`)
* **Assertion**: Parse `tools/ble_radar_simulator.py` enforcing zero third-party pip dependencies (`TST-PRO-005.6`).

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SIM-002.1` | Unit | `RadarPayloadBuilder.build_v2_payload` | `REQ-SIM-002.1` | Specified |
| `TST-SIM-002.2` | Unit | `RadarPayloadBuilder.build_v1_payload` | `REQ-SIM-002.1` | Specified |
| `TST-SIM-002.3` | Unit | `RadarLightControlCharacteristic` Read/Write | `REQ-SIM-002.2` | Specified |
| `TST-SIM-002.4` | Simulation | `RadarKinematicsEngine` presets & tick | `REQ-SIM-002.3` | Specified |
| `TST-SIM-002.5` | Protocol | Watchdog timer & Advertisement PDU | `REQ-SIM-002.3`, `REQ-SIM-002.4` | Specified |
| `TST-SIM-002.6` | Static AST | `tools/ble_radar_simulator.py` | `REQ-SIM-002.4`, `TST-PRO-005.6` | Specified |
