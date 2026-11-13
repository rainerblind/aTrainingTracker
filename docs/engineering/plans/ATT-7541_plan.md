# Stage 3: Implementation Plan - ATT-7541: Authentic Garmin Varia Wire Protocol Simulator

**Ticket**: [ATT-7541](https://rainerblind.atlassian.net/browse/ATT-7541) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7541`  
**Requirement Mapping**: `REQ-SIM-002` (`REQ-SIM-002.1`..`REQ-SIM-002.4`)  
**Test Mapping**: `TST-SIM-002` (`TST-SIM-002.1`..`TST-SIM-002.6`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-13  

---

## 1. Problem Description & Background
`tools/ble_radar_simulator.py` (`REQ-SIM-002`) emulates Garmin Varia peripherals using synthetic packet formats (`0x00` + $3\times N$ for V1; `0x01` + $8\times N$ for V2) and skips heartbeats when 0 targets are active. Following physical Garmin Varia RearVue 820 captures (ATT-7542, ATT-7543, ATT-7545), the simulator must emit authentic wire protocol packets (V1 sequence headers/heartbeats, V2 2-byte idle frames, 24-bit coordinate packing) and expose dedicated Control Service `6a4e2f00` with characteristics `6a4e2f11` and `6a4e2f14`.

---

## 2. Traceability & Requirements Mapping
* **REQ-SIM-002.1**: V1 characteristic `6a4e3203` sequence-framed headers (`0x02..0xF2`), 1-byte idle heartbeats, and target triplets with vehicle valid bit (`0x80 | (tid & 0x7F)`) (`TST-SIM-002.2`).
* **REQ-SIM-002.2**: V2 characteristic `6a4e3204` 2-byte idle heartbeats (`02 00`) and $2 + 9\times N$ target frames (`02 38`) with 24-bit LE packed coordinates (11-bit lateral $X$, 13-bit range $Y$), vehicle classes (36 TRUCK, 23 CAR, 16 UNKNOWN), and velocities (`TST-SIM-002.1`).
* **REQ-SIM-002.3**: Control Service `6a4e2f00` exposing `6a4e2f11` (write `06 09 01 TT`) and `6a4e2f14` (notify `01 00 ff TT`) synchronized with `RadarKinematicsEngine` and legacy `6a4e3202` (`TST-SIM-002.3`).
* **REQ-SIM-002.4**: 31-byte legacy BLE advertisement budget (`6a4e2f00` unadvertised) and Python 3 standard library AST compliance (`TST-SIM-002.5`, `TST-SIM-002.6`).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Production Code Changes**: Only `tools/ble_radar_simulator.py` and `tools/test_ble_radar_simulator.py` modified.
2. **Pure Python Standard Library (`TST-PRO-005.6`)**: Zero external dependencies added to tooling.
3. **31-Byte Legacy Advertising PDU Budget**: Service `6a4e2f00` registered in GATT without advertising, preserving 31-byte limit.
4. **Decoder Interoperability**: 100% compatibility with `BTLERadarDevice.java` decoders in `aTrainingTracker`.

---

## 4. Proposed Architectural Changes
* **tools/ble_radar_simulator.py**:
  - Add constants: `RADAR_CONTROL_SERVICE_UUID`, `RADAR_CONTROL_WRITE_UUID`, `RADAR_CONTROL_NOTIFY_UUID`.
  - Add RearVue mode mapping: SOLID (`0x11`), PELOTON (`0x12`), DAY_FLASH (`0x13`), NIGHT_FLASH (`0x14`), OFF (`0x1F`).
  - Upgrade `RadarPayloadBuilder`:
    - `build_v1_payload(targets, seq_counter=0, authentic=True)`: Emit `[seq]` when empty; emit `[seq, 0x80 | (tid & 0x7F), range, flag]` when active.
    - `build_v2_payload(targets, authentic=True)`: Emit `[0x02, 0x00]` when empty; emit `[0x02, 0x38]` + 9-byte target structs when active.
    - `unpack_v2_target_range(b0, b1, b2)`: Helper with 11-bit / 13-bit sign extension for verification.
  - Implement `RadarControlWriteCharacteristic` (`6a4e2f11`) handling `06 09 01 TT`.
  - Implement `RadarControlNotifyCharacteristic` (`6a4e2f14`) emitting `01 00 ff TT`.
  - Implement `RadarControlService` (`6a4e2f00`) and wire into GATT application in `main()`.
  - Synchronize mode state between `6a4e2f11`, `6a4e2f14`, `6a4e3202`, and `RadarKinematicsEngine`.
* **tools/test_ble_radar_simulator.py**:
  - Update unit tests covering authentic V1 sequence/heartbeats, V2 24-bit packing round-trips, and RearVue 820 control write/notify operations.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: V1 Sequence Framing & V2 24-Bit Coordinate Packing in `RadarPayloadBuilder`
* Files: `tools/ble_radar_simulator.py`
* Implement sequence counter cycling, 1-byte idle heartbeat, `0x80` vehicle valid bit, 2-byte idle V2 frame (`02 00`), 24-bit LE packed range word encoder/decoder, and authentic 9-byte target strides.

### Step 2: RearVue 820 Tail Light Control Service `6a4e2f00`
* Files: `tools/ble_radar_simulator.py`
* Implement `RadarControlWriteCharacteristic` (`6a4e2f11`), `RadarControlNotifyCharacteristic` (`6a4e2f14`), `RadarControlService`, and bidirectional synchronization with `RadarKinematicsEngine` and `RadarLightControlCharacteristic` (`6a4e3202`).

### Step 3: GATT Application Wiring & Advertising PDU Guard
* Files: `tools/ble_radar_simulator.py`
* Add `RadarControlService` to GATT application in `main()`; preserve 31-byte advertising PDU budget (`6a4e2f00` unadvertised).

### Step 4: Unit Test Suite Expansion & Clean-Room Verification
* Files: `tools/test_ble_radar_simulator.py`
* Add tests for V1 sequence headers/heartbeats, V2 24-bit coordinate round-trips, RearVue 820 control write/notify commands, and AST compliance.
* Command: `python3 tools/test_ble_radar_simulator.py`

---

## 6. Verification & Rollback Plan
* **Verification**: `python3 tools/test_ble_radar_simulator.py` executes all 6 unit test cases covering REQ-SIM-002.1..4.
* **Rollback**: Tooling-only modifications; `git checkout feature/ATT-7541` allows clean revert without impacting Android build.
