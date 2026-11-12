# Stage 1 Analysis: ATT-6676 - Create tools/ble_radar_simulator.py for Garmin Varia BLE V1/V2 streaming, tail light control, and traffic presets

**Ticket**: [ATT-6676](https://rainerblind.atlassian.net/browse/ATT-6676)  
**Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3` | **Branch**: `feature/ATT-6676`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  
**Requirement Mapping**: `REQ-SIM-002` (`REQ-TST-006.1`..`REQ-TST-006.4`)  
**Test Mapping**: `TST-SIM-002` (`TST-TST-006.1`..`TST-TST-006.5`)  

---

## 1. Problem Statement & Motivation
* **Context**: While `RadarTrafficSimulator` (`ATT-5087`) provides in-app preview by injecting `RadarState` directly into `RadarStateRepository`, it bypasses the entire physical Bluetooth LE radio stack (`BTSearchForNewDevicesEngine`, serialized GATT queues, `BTLERadarDevice` packet demuxers, and tail light GATT write commands).
* **Gap**: Verifying BLE radar discovery, V1 (`6a4e3203`) vs. V2 (`6a4e3204`) packet decoding, 3.5s watchdog starvation (`DISCONNECTED_ERROR`), and bidirectional tail light mode control (`6a4e3202`) currently requires real Garmin Varia hardware and outdoor road vehicles.
* **Objective**: Create `tools/ble_radar_simulator.py`—a standalone BlueZ D-Bus GATT peripheral emulating Garmin Varia Radar (`6a4e3200-667b-11e3-949a-0800200c9a66`) with V1/V2 target streaming, tail light `6a4e3202` write handling, interactive traffic presets, and unit test suite `tools/test_ble_radar_simulator.py`.

---

## 2. Root Cause Analysis & Protocol Investigation
* **BLE Service Architecture & GATT Hierarchy**:
  * Service `6a4e3200-667b-11e3-949a-0800200c9a66` alongside Battery (`0x180F`) and Device Information (`0x180A`).
  * V2 Characteristic `6a4e3204`: 4–8 Hz notification stream (`200 ms` default). Header byte `0x01` followed by $N \times 8$-byte target blocks (`targetId`, uint16 LE `rangeRaw` in 0.1m, int16 LE `speedRaw` in 0.1m/s, int16 LE `lateralRaw` in 0.1m, uint8 `sizeAndThreat`).
  * V1 Characteristic `6a4e3203`: Header byte `0x00` followed by $N \times 3$-byte target blocks (`targetId`, uint8 `rangeMeters`, `(threatBits << 4) | (speedMps & 0x0F)`).
  * Light Control Characteristic `6a4e3202`: Receives 1-byte writes (`0x00` OFF, `0x01` SOLID, `0x05` PELOTON, `0x06` NIGHT_FLASH, `0x07` DAY_FLASH) and responds to reads.
  * Model Number Characteristic `0x2A24`: Returns `"RTL515"` (default) or `"RTL516"` / `"010-03180-10"` (StVZO mode) to test `BTLERadarDevice.isStvzo` detection.
* **31-Byte Legacy Advertisement Budget Constraint**:
  * 128-bit Service UUID consumes 18 bytes (1 byte length + 1 byte type `0x07` + 16 bytes UUID). Flags consume 3 bytes. Remaining budget: $31 - 21 = 10$ bytes.
  * Local name AD record (type `0x08` or `0x09`) requires 2 bytes overhead. Therefore, local name string must be $\le 8$ ASCII characters (e.g. `'VariaSim'` = 8 bytes $\rightarrow 21 + 10 = 31$ bytes exact).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * BlueZ D-Bus GATT peripheral exposing `6a4e3200`, `6a4e3204` (V2), `6a4e3203` (V1), `6a4e3202` (Light Control), `0x180F` (Battery), and `0x180A` (DIS Model Number).
  * Kinematic target physics ($r_{t+1} = r_t - v \cdot dt$) and interactive presets: car (`c`), fast car (`f`), truck/bus (`t`), close pass (`p`), convoy (`m`), country loop (`l`), all-clear (`0`), and 4.5s watchdog starvation (`w`).
  * Pure stdlib implementation (`TST-PRO-005.6`) and test suite `tools/test_ble_radar_simulator.py`.
* **Out-of-Scope**: Modifications to Android production code (`BTLERadarDevice.java` is complete).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022, Rule 68)
* **Status**: Net-new requirement `REQ-SIM-002` / `REQ-TST-006` under Epic `ATT-2466`.
* **Chesterton's Fence**: Reuses proven BlueZ D-Bus GATT skeleton from `tools/ble_hr_simulator.py`, ensuring consistent CLI ergonomics and zero third-party pip dependencies.

---

## 5. Architectural Strategy & High-Level Solution
* **`RadarKinematicsEngine`**: Updates target distances and speeds at 5 Hz (`200 ms`), handles vehicle lifetime and pass completion, and supports traffic preset spawning.
* **`RadarPayloadBuilder`**: Pure stdlib binary packer generating byte arrays for V1 (`1 + 3*N`) and V2 (`1 + 8*N`) matching `BTLERadarDevice` bitfields.
* **`RadarLightControlCharacteristic`**: Implements D-Bus `WriteValue` and `ReadValue`, updating state and logging light mode transitions.

---

## 6. System Invariants & Risk Assessment
* **Invariants**: Pure Python standard library (`TST-PRO-005.6`), exact bit-level encoding matching `BTLERadarDevice`, legacy 31-byte advertisement budget compliance, and non-blocking watchdog starvation.
* **Risk Rating**: **LOW** — Self-contained PC developer simulation tool in `tools/` with zero Android runtime changes.
