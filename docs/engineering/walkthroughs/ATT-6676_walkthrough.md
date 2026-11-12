# Stage 5 Verification & Walkthrough: Create tools/ble_radar_simulator.py (ATT-6676)

* **Ticket**: [ATT-6676](https://rainerblind.atlassian.net/browse/ATT-6676) (*Create tools/ble_radar_simulator.py for Garmin Varia BLE V1/V2 target streaming, bidirectional tail light control, and traffic presets*)
* **Sub-task**: ATT-7210 (*[Implementation] ble_radar_simulator.py*)
* **Sprint**: `2026-46.3` | **Target Release**: `V5.0.0` | **Branch**: `feature/ATT-6676`
* **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12

---

## 1. Verification Scope & Test Results

### 1.1 Automated Developer Tool Unit Tests
* **Test Suite**: `python3 -m unittest -v tools/test_ble_radar_simulator.py` (**PASS: 6/6 tests in 0.028s**)
  - `test_v2_payload_encoding` (`TST-SIM-002.1`): Validated 8-byte stride V2 target encoding (`0x01` header, uint16 LE `rangeRaw` 0.1m, int16 LE `speedRaw` 0.1m/s, int16 LE `lateralRaw` 0.1m, `sizeAndThreat` bitfield) and empty all-clear frame (`[0x01]`). (PASS)
  - `test_v1_payload_encoding` (`TST-SIM-002.2`): Validated 3-byte stride V1 legacy target encoding (`0x00` header, uint8 `rangeMeters`, `(threat << 4) | (speed & 0x0F)`), range/speed clamping, and empty all-clear frame (`[0x00]`). (PASS)
  - `test_light_control_read_write` (`TST-SIM-002.3`): Validated `6a4e3202` state machine for modes `OFF` (0x00), `SOLID` (0x01), `PELOTON` (0x05), `NIGHT_FLASH` (0x06), `DAY_FLASH` (0x07), invalid mode rejection, and StVZO flashing mode restriction to `SOLID`. (PASS)
  - `test_kinematics_engine_and_presets` (`TST-SIM-002.4`): Validated 5 Hz physics update ($r_{t+1} = r_t - v \cdot dt$), target removal upon overtake ($r \le 0.0$), and presets `c` (car), `f` (fast car), `t` (truck/bus), `p` (close pass < 1.5m), `m` (convoy), `0` (all-clear). (PASS)
  - `test_watchdog_and_pdu_budget` (`TST-SIM-002.5`): Validated 4.5s notification starvation suppressing transmission for 23 ticks to trigger client 3500ms `DISCONNECTED_ERROR` timeout, and verified legacy advertising PDU budget equals exactly 31 bytes (`VariaSim` + 128-bit UUID). (PASS)
  - `test_python_stdlib_compliance_ast` (`TST-SIM-002.6`): AST static audit verifying zero external pip dependencies (no numpy, scipy) per `TST-PRO-005.6`. (PASS)

### 1.2 Requirement Governance Verification
* `python3 tools/verify_requirement_governance.py`: **PASS** (zero requirement regressions, master table parity maintained).

---

## 2. Implemented Architecture & Deliverables

### 2.1 Standalone Simulator (`tools/ble_radar_simulator.py`)
* **Primary Radar GATT Service (`6a4e3200`)**:
  - `RadarV2Characteristic` (`6a4e3204`): High-resolution 2D target notifications at 5 Hz.
  - `RadarV1Characteristic` (`6a4e3203`): Legacy 1D target notifications at 5 Hz.
  - `RadarLightControlCharacteristic` (`6a4e3202`): Read/Write receiver for tail light commands with StVZO clamp.
  - Battery Service (`0x180F` / `0x2A19`) and DIS (`0x180A` / `0x2A24` model `"RTL515"` / `"RTL516"`).
* **Kinematics Engine (`RadarKinematicsEngine`)**:
  - Independent pure Python physics loop updating targets at 5 Hz.
  - Presets: `c`, `f`, `t`, `p`, `m`, `l` (country loop toggle), `0`, and `w` (watchdog starvation).
* **31-Byte Legacy PDU Budget**: Local name `'VariaSim'` (8B) + 128-bit UUID (18B) + Flags (3B) = 31 bytes $\le 31$ bytes.

### 2.2 Comprehensive Unit Test Suite (`tools/test_ble_radar_simulator.py`)
* 6 standalone unit test cases covering all protocol bitfields, light control transitions, kinematics progression, starvation timing, PDU budget, and AST stdlib hygiene.

---

## 3. Acceptance Criteria Verification

| Requirement | Acceptance Criteria Summary | Status | Evidence |
|---|---|---|---|
| `REQ-SIM-002.1` | V2 (0x3204) & V1 (0x3203) binary payload encoding matching `BTLERadarDevice` | **Verified** | `test_v2_payload_encoding`, `test_v1_payload_encoding` (`TST-SIM-002.1`, `TST-SIM-002.2`) |
| `REQ-SIM-002.2` | Bidirectional Tail Light Control 6a4e3202 read/write receiver with StVZO modes | **Verified** | `test_light_control_read_write` (`TST-SIM-002.3`) |
| `REQ-SIM-002.3` | Kinematics engine physics ($r_{t+1} = r_t - v \cdot dt$) & interactive traffic presets | **Verified** | `test_kinematics_engine_and_presets` (`TST-SIM-002.4`) |
| `REQ-SIM-002.4` | 4.5s watchdog starvation & 31-byte advertising PDU budget compliance | **Verified** | `test_watchdog_and_pdu_budget` (`TST-SIM-002.5`) |
| `TST-PRO-005.6` | Zero non-stdlib external dependencies (AST audit) | **Verified** | `test_python_stdlib_compliance_ast` (`TST-SIM-002.6`) |
