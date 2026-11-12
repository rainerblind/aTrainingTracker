# Stage 5 Verification & Walkthrough: Integrate BLE HRV & Varia Radar into tools/replay_workout.py (ATT-6677)

* **Ticket**: [ATT-6677](https://rainerblind.atlassian.net/browse/ATT-6677) (*Integrate BLE HRV beat-to-beat synthesis and Garmin Varia Radar simulation into tools/replay_workout.py GUI and CLI*)
* **Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*) | **Target Release**: `V5.0.0` | **Sprint**: Backlog
* **Requirement Mapping**: `REQ-SIM-003` (`REQ-SIM-003.1`..`REQ-SIM-003.5`) | **Test Spec**: `TST-SIM-003` (`TST-SIM-003.1`..`TST-SIM-003.5`)
* **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12

---

## 1. Verification Scope & Test Results

### 1.1 Automated Developer Tool Unit Tests
* **Tier 1 Replay & Sensor Simulator Suite**: `python3 -m unittest -v tools/test_replay_workout.py tools/test_ble_hr_simulator.py tools/test_ble_radar_simulator.py` (**PASS: 75/75 tests in 0.434s**)
  - `TestHrPayloadPackingAndHrvCurve` (`TST-SIM-003.1`, `TST-SIM-003.2`): Validated BLE 0x2A37 Bit 4 (`0x10`) RR packing in 1/1024s uint16 LE units, `--no-rr` legacy fallback, Auto-HRV intensity curve ($\alpha_1 = 1.05 \rightarrow 0.75 \rightarrow 0.50$, $\text{rMSSD} = 60.0 \rightarrow 10.0\text{ ms}$), monotonic decrease, and 60s cumulative phase drift $\le 50\text{ ms}$. (PASS)
  - `TestRadarProtocolAndService` (`TST-SIM-003.3`): Validated Primary Radar Service UUID `6a4e3200`, `RadarLightMode` enum validation/names, V2 high-res 8-byte stride serialization (`6a4e3204`), V1 legacy 3-byte stride serialization (`6a4e3203`), and empty payload framing. (PASS)
  - `TestRadarKinematicsEngine` (`TST-SIM-003.4`): Validated 5 Hz target physics ($r_{t+1} = r_t - v \cdot dt$), overtake removal at $r \le 0$, presets `c`, `f`, `t`, `p` ($<1.5\text{ m}$), and 4.5s watchdog starvation. (PASS)
  - `TestBleReplayServerRadarAndHrv` (`REQ-SIM-003.2`, `REQ-SIM-003.3`): Validated server defaults, radar kill/revive/toggle, scheduled radar triggers, regime switching, and artifact triggers (`ectopic`, `missed`, `extra`, `gap`). (PASS)
  - `TestCliParserAndStdLibCompliance` (`TST-SIM-003.5`, `TST-PRO-005.6`): Validated CLI options (`--no-rr`, `--no-radar`, `--radar`, `--hrv-regime`, `--kill-radar-at`, `--revive-radar-at`) and AST audit verifying zero external pip dependencies. (PASS)

### 1.2 Governance Verification
* `python3 tools/verify_requirement_governance.py`: **PASS** (100% parity across all 9 living document shards, zero regressions).

---

## 2. Implemented Architecture & Deliverables

### 2.1 Core Replay & Sensor Integration (`tools/replay_workout.py`)
* **Intensity-Coupled HRV**: Integrated `RrSynthesizer` from `ble_hr_simulator.py` into `HeartRateMeasurementCharacteristic` with dynamic Auto-HRV scaling curve. Added regime dropdown/CLI flags and artifact injection.
* **Garmin Varia BLE Radar (`6a4e3200`)**: Added `RadarService` with V2 2D target stream (`6a4e3204`), V1 threat stream (`6a4e3203`), and bidirectional Tail Light Control (`6a4e3202`) with 31-byte legacy advertisement PDU budgeting (`VariaSim`) and graceful BlueZ slot error handling.
* **5 Hz Kinematics Engine**: Synchronized target physics with ADB mock GPS advancement along the TCX track to generate close-pass hotspots on `TrackOnMapScreen.kt`.
* **Dual UI Parity**: Added PyQt6 Obsidian Dark GUI cards (gauges, badges, 1-click preset buttons) and matching headless CLI status lines with interactive hotkeys.

### 2.2 Test Suite (`tools/test_replay_workout.py`)
* Expanded from 44 to 64 tests covering all 5 requirement and test specs under `TST-SIM-003`.

---

## 3. Acceptance Criteria Verification

| Requirement | Acceptance Criteria Summary | Status | Evidence |
|---|---|---|---|
| `REQ-SIM-003.1` | BLE 0x2A37 Bit 4 RR packing & intensity-coupled Auto-HRV curve | **Verified** | `TestHrPayloadPackingAndHrvCurve` (`TST-SIM-003.1`, `TST-SIM-003.2`) |
| `REQ-SIM-003.2` | Manual HRV regimes (`auto`, `aerobic`, `threshold`, `anaerobic`) & artifact injection | **Verified** | `TestBleReplayServerRadarAndHrv` (`REQ-SIM-003.2`) |
| `REQ-SIM-003.3` | Garmin Varia Radar Primary Service `6a4e3200` & Tail Light Control `6a4e3202` | **Verified** | `TestRadarProtocolAndService` (`TST-SIM-003.3`) |
| `REQ-SIM-003.4` | 5 Hz kinematics physics ($r_{t+1} = r_t - v \cdot dt$) & traffic presets | **Verified** | `TestRadarKinematicsEngine` (`TST-SIM-003.4`) |
| `REQ-SIM-003.5` | PyQt6 GUI & Headless CLI parity; pure Python 3 stdlib (`TST-PRO-005.6`) | **Verified** | `TestCliParserAndStdLibCompliance` (`TST-SIM-003.5`) |

---

## 4. Hardware / Physical Verification
* **Verification Scope**: Pure developer testing tool enhancement in `tools/`; zero Android runtime bytecode changes (`app/src/main/`). Desk verified via BlueZ D-Bus and automated unit test suite.
