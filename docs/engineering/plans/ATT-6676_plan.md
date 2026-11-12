# Stage 3 Implementation Plan: ATT-6676 - Create tools/ble_radar_simulator.py for Garmin Varia BLE streaming

**Ticket**: [ATT-6676](https://rainerblind.atlassian.net/browse/ATT-6676) | **Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-46.3` | **Branch**: `feature/ATT-6676`  
**Requirement Mapping**: `REQ-SIM-002` (`REQ-TST-006.1`..`REQ-TST-006.4`)  
**Test Mapping**: `TST-SIM-002` (`TST-TST-006.1`..`TST-TST-006.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Architectural Strategy & Component Allocation
* **`RadarAdvertisement`**: Subclasses BlueZ `Advertisement` with 128-bit UUID `6a4e3200-667b-11e3-949a-0800200c9a66` and local name `'VariaSim'` (8 bytes) ensuring total advertising PDU $\le 31$ bytes.
* **`RadarGattApplication`**: Exposes Garmin Varia primary service `6a4e3200`, Battery Service `0x180F`, and DIS `0x180A`.
* **`RadarThreatCharacteristic` (V2 `6a4e3204` & V1 `6a4e3203`)**: Handles CCCD subscription, notification timer (5 Hz / 200 ms), and delegates binary payload generation to pure-logic serializer.
* **`RadarLightControlCharacteristic` (`6a4e3202`)**: Handles D-Bus `WriteValue` and `ReadValue` for tail light mode commands (`0x00`, `0x01`, `0x05`, `0x06`, `0x07`).
* **`RadarKinematicsEngine` & `RadarPayloadBuilder`**: Standalone pure Python standard library modules (`math`, `struct`, `time`) separated from D-Bus bindings for 100% testability.

---

## 2. Atomic Implementation Steps

| Step | Component / Target | Description | Verification |
|:---|:---|:---|:---|
| **1** | `tools/ble_radar_simulator.py` (GATT & DIS) | Define GATT Service `6a4e3200`, V2 (`6a4e3204`), V1 (`6a4e3203`), Light Control (`6a4e3202`), Battery (`0x180F`), and DIS (`0x180A` with `--stvzo` flag for `0x2A24`). | Static review / AST |
| **2** | `tools/ble_radar_simulator.py` (Advertisement) | Configure `RadarAdvertisement` with 128-bit service UUID and local name `'VariaSim'`, asserting total PDU $\le 31$ bytes. | `test_watchdog_and_pdu_budget` (`TST-SIM-002.5`) |
| **3** | `tools/ble_radar_simulator.py` (Payload Serializers) | Implement `RadarPayloadBuilder`: V2 (`0x01` + $N \times 8$ B) and V1 (`0x00` + $N \times 3$ B) bitfield encoding matching `BTLERadarDevice.java`. | `test_v2_payload_encoding`, `test_v1_payload_encoding` (`TST-SIM-002.1`, `TST-SIM-002.2`) |
| **4** | `tools/ble_radar_simulator.py` (Kinematics & Hotkeys) | Implement `RadarKinematicsEngine` with 5 Hz tick ($r_{t+1} = r_t - v \cdot dt$), presets (`c`, `f`, `t`, `p`, `m`, `l`, `0`), tail light state tracking, and 4.5s watchdog starvation (`w`). | `test_kinematics_engine_and_presets`, `test_light_control_read_write` (`TST-SIM-002.3`, `TST-SIM-002.4`) |
| **5** | `tools/test_ble_radar_simulator.py` (Test Suite) | Implement automated test suite covering V1/V2 binary encoding, light control, kinematics, watchdog, PDU budget, and pure stdlib AST hygiene. | `python3 -m unittest tools/test_ble_radar_simulator.py` (`TST-SIM-002.1`..`6`) |

---

## 3. Invariants & Risk Mitigation
* **Standard Library Invariant (`TST-PRO-005.6`)**: Strictly standard library (`math`, `random`, `struct`, `time`, `threading`) with zero pip dependencies.
* **Code Lock**: Zero modifications in Android runtime code (`app/src/main/`).
* **Protocol Parity**: Payload formats verified bit-for-bit against `BTLERadarDevice.parseV2ThreatPacket` and `parseV1ThreatPacket`.
* **Timing & Starvation**: 4.5s notification starvation guarantees triggering `BTLERadarDevice.WATCHDOG_TIMEOUT_MS` (3500 ms).
