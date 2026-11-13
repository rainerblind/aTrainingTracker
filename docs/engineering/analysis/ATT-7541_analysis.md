# Stage 1 Analysis: ATT-7541 - Align tools/ble_radar_simulator.py with Authentic Garmin Varia / RearVue 820 BLE V1, V2 & Tail Light Wire Protocol

**Ticket**: [ATT-7541](https://rainerblind.atlassian.net/browse/ATT-7541)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7541`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-13  

---

## 1. Problem Statement & Motivation
`tools/ble_radar_simulator.py` (`REQ-SIM-002`) emulates Garmin Varia BLE peripherals using synthetic packet formats (`0x00` + $3\times N$ for V1; `0x01` + $8\times N$ for V2) and skips heartbeat emissions when 0 targets are present. Following physical Garmin Varia / RearVue 820 hardware captures in ATT-7542, ATT-7543, and ATT-7545, the simulator must emit authentic wire protocol packets to ensure 1:1 hardware fidelity during desk testing:
1. **V1 Stream (`6a4e3203`)**: Emit sequence-framed headers (`((counter & 0x0F) << 4) | 0x02`), 1-byte link-alive heartbeats, and target triplets with vehicle valid bit (`0x80 | (tid & 0x7F)`).
2. **V2 Stream (`6a4e3204`)**: Emit 2-byte idle heartbeats (`02 00`) and authentic $2 + 9\times N$ target frames with 24-bit LE packed coordinate words (11-bit signed lateral $X$, 13-bit signed longitudinal $Y$), vehicle classes, and $0.5\text{ m/s}$ resolution velocities.
3. **Tail Light Control Service (`6a4e2f00`)**: Expose RearVue 820 write characteristic `6a4e2f11` (`06 09 01 TT`) and notify characteristic `6a4e2f14` (`01 00 ff TT`), retaining legacy `6a4e3202` compatibility.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  - `BTLERadarDevice.java:792-824` (ATT-7543): Real V1 frames require `(header & 0x0F) == 0x02`, 1-byte frames are treated as link-alive heartbeats resetting the 3.5s watchdog, and targets are filtered unless `vid & 0x80 != 0`. Verified ticket hypothesis.
  - `BTLERadarDevice.java:929-1043` (ATT-7542): Real V2 frames emit 2-byte idle heartbeats (`02 00` / `02 90`, matched by `payload[0] == 0x02`). The ticket hypothesis of `[01 00]` was audited: while `(header & 0x0001) != 0` also demuxes, emitting `[0x02, 0x00]` 1:1 matches physical hardware captures. Multi-target frames use 2-byte LE header `[0x02, 0x38]` and 9-byte target strides.
  - `BTLERadarDevice.java:284-318, 590-598, 653-667` (ATT-7545): RearVue 820 mode overrides use `6a4e2f11` with `06 09 01 TT` (`0x11` SOLID, `0x12` PELOTON, `0x13` DAY_FLASH, `0x14` NIGHT_FLASH, `0x1F` OFF) and emit 4-byte slot notifications `01 00 ff TT` on `6a4e2f14`.
  - **24-bit Range Packing**: Range word combines 11-bit signed lateral $X$ (`round(lateral_m * 10) & 0x7FF`) and 13-bit signed longitudinal $Y$ (`round(range_m * 10) & 0x1FFF`): `packed = (x & 0x7FF) | ((y & 0x1FFF) << 11)`. Boundary round-trip tests confirm zero bit-drift across negative lateral offsets and $175\text{ m}$ max range.
  - **Advertising PDU Constraint**: `6a4e2f00` service MUST NOT be advertised over BLE; only `6a4e3200` is advertised to strictly preserve the 31-byte legacy advertising budget (3B Flags + 18B UUID + 10B Name = 31B).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Upgrade `tools/ble_radar_simulator.py` to emit authentic V1 sequence headers (`0x02..0xF2`), 1-byte idle heartbeats, and `0x80` vehicle valid triplets.
  - Upgrade V2 stream to emit authentic 2-byte idle heartbeats (`02 00`) and $2 + 9\times N$ target frames with 24-bit packed coordinates and explicit vehicle classes (36 TRUCK, 23 CAR, 16 UNKNOWN/LIGHT).
  - Add `RadarControlService` (`6a4e2f00`) with characteristics `6a4e2f11` (write) and `6a4e2f14` (notify), synchronizing mode state bidirectionally with `RadarKinematicsEngine` and legacy `6a4e3202`.
  - Update `tools/test_ble_radar_simulator.py` (TST-SIM-002) with 100% clean-room unit tests covering V1/V2 encoding, 24-bit boundary round-trips, and light control write/notify operations.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Zero Android production code modifications in `app/src/main/` (`BTLERadarDevice.java` is already complete).
  - AMV proprietary diagnostic protocol on `6a4e2821`/`6a4e2811`.
  - Adding `6a4e2f00` to BLE advertisement packet (violates 31-byte PDU limit).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-SIM-002` (`REQ-TST-006.1`..`REQ-TST-006.4`) in `tools/ble_radar_simulator.py`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-6676` (commit `f9016b86`).
* **Root Reason for Existing Formulation**: Prior to physical Garmin Varia RearVue 820 hardware procurement, simulator assumed synthetic packet layouts (`0x00` + $3\times N$ and `0x01` + $8\times N$) and 1-byte light control.
* **Preservation of Core Invariants**: Retains Python standard library compliance (`TST-PRO-005.6`), 31-byte advertising PDU budget, 5 Hz tick rate, 4.5s watchdog starvation command (`w`), traffic presets (`c`, `f`, `t`, `p`, `m`, `l`, `0`), and backward-compatible synthetic decoder fallback.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-SIM-002.1` (authentic V1/V2 wire protocol) and `REQ-SIM-002.2` (RearVue 820 light control).
* **Affected Architecture Elements**: `tools/ble_radar_simulator.py`, `tools/test_ble_radar_simulator.py`.
* **Verification Impact & Test Surface**: Unit tests `TST-SIM-002.1`..`TST-SIM-002.6` in `tools/test_ble_radar_simulator.py`. Zero impact on Android runtime or test suites.
* **Risk Assessment & Invariants**: Low risk developer tooling change. Preserves pure Python stdlib, 31-byte PDU budget, and full compatibility with `BTLERadarDevice.java`.

---

## 6. Architectural Strategy & High-Level Solution
- **V1 Framing**: Add cycling sequence counter `counter = (counter + 1) & 0x0F` producing `seq = (counter << 4) | 0x02`. Emit `[seq]` when empty; emit `[seq, 0x80 | (tid & 0x7F), range_m, flag]` when active.
- **V2 Framing**: Emit `[0x02, 0x00]` when empty; emit `[0x02, 0x38]` + 9-byte structs when active with 24-bit LE packed range word, vehicle class, dimensions ($0.25\text{ m}$), and closing velocity ($0.5\text{ m/s}$).
- **Control Service**: Add `RadarControlService` on `6a4e2f00` with `6a4e2f11` (write handler parsing `06 09 01 TT`) and `6a4e2f14` (notify handler emitting `01 00 ff TT`).

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero production runtime code changes in `app/src/main/`.
  2. Pure Python 3 standard library only (`TST-PRO-005.6`), zero external dependencies.
  3. 31-byte legacy BLE advertisement budget strictly preserved.
  4. 100% interoperability with `BTLERadarDevice.java` V1, V2, and light control decoders.
* **Risk Rating**: **LOW** (tooling and developer simulation test suite only).
