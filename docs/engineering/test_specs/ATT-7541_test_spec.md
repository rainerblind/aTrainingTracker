# Stage 2: Requirement & Test Specification - ATT-7541: Align tools/ble_radar_simulator.py with Authentic Garmin Varia Wire Protocol

**Ticket**: [ATT-7541](https://rainerblind.atlassian.net/browse/ATT-7541)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7541`  
**Requirement Mapping**: `REQ-SIM-002` (`REQ-TST-006.1`..`REQ-TST-006.4`)  
**Test Spec ID**: `TST-SIM-002` (`TST-SIM-002.1`..`TST-SIM-002.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-13  

---

## 1. Requirement Specification (REQ-SIM-002)

### 1.1 Problem Statement & Rationale
`tools/ble_radar_simulator.py` currently emits synthetic packet formats (`0x00` + $3\times N$ for V1; `0x01` + $8\times N$ for V2) and omits idle heartbeats when 0 targets are active. Following authentic physical Garmin Varia / RearVue 820 captures (ATT-7542, ATT-7543, ATT-7545), the simulator must emit authentic wire protocol packets and expose dedicated Control Service `6a4e2f00` to ensure 1:1 hardware fidelity during desk testing.

### 1.2 Attributes
- **Priority**: High | **Type**: Functional / Non-Functional | **Verification Method**: Unit & Review

### 1.3 Functional & Architectural Requirements
* **REQ-SIM-002.1** (*Authentic V1 Stream & Heartbeat*): V1 characteristic `6a4e3203` SHALL emit sequence-framed headers `seq = ((counter & 0x0F) << 4) | 0x02` cycling `0x02..0xF2`. When 0 targets are active, it SHALL emit 1-byte heartbeat `[seq]`. When targets are active, it SHALL emit $1 + 3\times N$ bytes with triplets `[0x80 | (targetId & 0x7F), range_m, flag]`.
* **REQ-SIM-002.2** (*Authentic V2 2D Stream & 24-Bit Packing*): V2 characteristic `6a4e3204` SHALL emit 2-byte idle heartbeat `[0x02, 0x00]` when 0 targets are active. When targets are active, it SHALL emit $2 + 9\times N$ bytes with header `[0x02, 0x38]` and 9-byte target structs: uint8 `targetId`, uint8 `targetClass` (36 TRUCK, 23 CAR, 16 UNKNOWN/LIGHT), 24-bit LE packed coordinate word (11-bit signed lateral $X \times 10$, 13-bit signed range $Y \times 10$), uint8 `length` ($0.25\text{ m}$), uint8 `width` ($0.25\text{ m}$), int8 `speedY` (negative closing speed in $0.5\text{ m/s}$), and int8 `speedX` (`0x80` sentinel).
* **REQ-SIM-002.3** (*RearVue 820 Tail Light Control Service 6a4e2f00*): The simulator SHALL expose Control Service `6a4e2f00`. Characteristic `6a4e2f11` SHALL accept 4-byte write override `06 09 01 TT` (`0x11` SOLID, `0x12` PELOTON, `0x13` DAY_FLASH, `0x14` NIGHT_FLASH, `0x1F` OFF). Characteristic `6a4e2f14` SHALL emit 4-byte slot notifications `01 00 ff TT`. Legacy `6a4e3202` on `6a4e3200` SHALL retain synchronized 1-byte read/write support.
* **REQ-SIM-002.4** (*31-Byte Advertisement Budget & Python Stdlib Invariant*): Legacy advertising PDU SHALL strictly not exceed 31 bytes (Service `6a4e2f00` unadvertised). Simulator and unit test suite SHALL strictly execute within Python 3 standard library (`TST-PRO-005.6`).

### 1.4 Acceptance Criteria (Given-When-Then) & Invariants
* *Given* active V1 stream with 0 targets, *When* tick fires, *Then* 1-byte heartbeat `[seq]` emits and resets client 3.5s watchdog.
* *Given* active V2 stream with targets, *When* target is at range $140.0\text{ m}$, lateral $-1.8\text{ m}$, closing $12.0\text{ m/s}$, *Then* $2 + 9\times N$ frame emits with 24-bit packed range decoded losslessly by `BTLERadarDevice.parseV2ThreatPacket`.
* *Given* RearVue 820 client writes `06 09 01 13` to `6a4e2f11`, *When* write completes, *Then* mode updates to DAY_FLASH and `6a4e2f14` notifies `01 00 ff 13`.
* *Chesterton's Fence Audit (`REQ-PRO-022`)*: `REQ-SIM-002` synthetic packet layouts (`0x00` + $3\times N$, `0x01` + $8\times N$) originated in ATT-6676 before physical hardware delivery. Safe to align with authentic RearVue 820 wire format because `BTLERadarDevice.java` decoders (ATT-7542, ATT-7543, ATT-7545) already support both authentic and legacy fallbacks.

---

## 2. Test Specification (TST-SIM-002)

### Test Case 1: V2 Multi-Target Binary Encoding & 24-Bit Packing (`TST-SIM-002.1`)
* **Scope**: Unit Test (`tools/test_ble_radar_simulator.py`)
* **Assertion**: Verify 2-byte idle header `[0x02, 0x00]`, $2 + 9\times N$ stride, vehicle class bytes, 24-bit coordinate packing/unpacking round-trip across boundaries ($-10.0\text{ m} \le X \le +10.0\text{ m}$, $0\text{ m} \le Y \le 175\text{ m}$), and $-speedY$ closing velocity.

### Test Case 2: V1 Sequence Header & Threat Triplet Encoding (`TST-SIM-002.2`)
* **Scope**: Unit Test (`tools/test_ble_radar_simulator.py`)
* **Assertion**: Verify sequence counter `0x02..0xF2`, 1-byte idle heartbeat, and triplets with vehicle valid bit `0x80 | (tid & 0x7F)`.

### Test Case 3: Tail Light Control Service 6a4e2f00 & Legacy 6a4e3202 (`TST-SIM-002.3`)
* **Scope**: Unit Test (`tools/test_ble_radar_simulator.py`)
* **Assertion**: Verify `6a4e2f11` 4-byte command parsing `06 09 01 TT`, `6a4e2f14` 4-byte notification emission `01 00 ff TT`, and legacy `6a4e3202` read/write synchronization.

### Test Case 4: Kinematics Engine, Presets & 4.5s Starvation (`TST-SIM-002.4`)
* **Scope**: Simulation Unit Test (`tools/test_ble_radar_simulator.py`)
* **Assertion**: Verify $r_{t+1} = r_t - v \cdot dt$, presets `c, f, t, p, m, l, 0`, and notification starvation $\ge 4.5\text{ s}$ for client timeout trigger.

### Test Case 5: 31-Byte Legacy BLE Advertisement Budget (`TST-SIM-002.5`)
* **Scope**: Protocol Budget Test (`tools/test_ble_radar_simulator.py`)
* **Assertion**: Enforce total advertising PDU $\le 31$ bytes with `6a4e3200` (service `6a4e2f00` excluded from advertising).

### Test Case 6: Pure Python 3 Standard Library AST Compliance (`TST-SIM-002.6`)
* **Scope**: Static AST Audit (`tools/test_ble_radar_simulator.py`, `TST-PRO-005.6`)
* **Assertion**: Strictly standard library imports only (`sys`, `os`, `math`, `struct`, `threading`, `time`, `dbus`, `gi`).

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SIM-002.1` | Unit | `RadarPayloadBuilder.build_v2_payload` & 24-bit unpack | `REQ-SIM-002.2` | Specified |
| `TST-SIM-002.2` | Unit | `RadarPayloadBuilder.build_v1_payload` | `REQ-SIM-002.1` | Specified |
| `TST-SIM-002.3` | Unit | `RadarControlWriteCharacteristic` & `RadarControlNotifyCharacteristic` | `REQ-SIM-002.3` | Specified |
| `TST-SIM-002.4` | Simulation | `RadarKinematicsEngine` presets & starvation | `REQ-SIM-002.3` | Specified |
| `TST-SIM-002.5` | Protocol | `compute_advertising_pdu_size` | `REQ-SIM-002.4` | Specified |
| `TST-SIM-002.6` | Static AST | AST inspection of `tools/ble_radar_simulator.py` | `REQ-SIM-002.4`, `TST-PRO-005.6` | Specified |
