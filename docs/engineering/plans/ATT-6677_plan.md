# Stage 3: Implementation Plan - ATT-6677: Integrate BLE HRV synthesis and Garmin Varia Radar simulation into tools/replay_workout.py

**Ticket**: [ATT-6677](https://rainerblind.atlassian.net/browse/ATT-6677) | **Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V5.0.0` | **Active Sprint**: Backlog | **Branch**: `feature/ATT-6677`  
**Requirement Mapping**: `REQ-SIM-003` (`REQ-TST-007.1`..`REQ-TST-007.5`)  
**Test Mapping**: `TST-SIM-003` (`TST-TST-007.1`..`TST-TST-007.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Architectural Strategy
* **Problem**: `tools/replay_workout.py` replays TCX workouts with Cycling Power and mock GPS, but lacks Beat-to-Beat RR intervals in its BLE HR stream (`flags = 0x00`) and has no Garmin Varia Radar GATT service (`6a4e3200`). This blocks simultaneous desk verification of HRV metrics (`rMSSD`, $\alpha_1$, `HRVT1`/`HRVT2`), radar close-pass route overlays (`TrackOnMapScreen.kt`), and tail light control.
* **Architecture**:
  * Reuse pure stdlib components from `tools.ble_hr_simulator` (`RrSynthesizer`) and `tools.ble_radar_simulator` (`RadarKinematicsEngine`, `RadarPayloadBuilder`, `RadarLightMode`, `RadarTargetState`).
  * Integrate into `BleReplayServer`: add `RadarService` (`6a4e3204` V2, `6a4e3203` V1, `6a4e3202` light control) with graceful BlueZ advertisement slot handling (`VariaSim` $\le 31$ B).
  * Auto-HRV intensity engine: continuously map replayed TCX BPM to target DFA $\alpha_1$ and `rMSSD`.
  * Dual UI Parity: Add Obsidian Dark HRV and Radar cards to PyQt6 `ReplayGuiWindow` and matching terminal status/hotkeys to `run_headless_cli`.

---

## 2. Traceability & Requirements Mapping
* **REQ-SIM-003.1** $\rightarrow$ `TST-SIM-003.1`: Bit 4 RR packing (`0x10`) & Auto-HRV intensity curve ($\alpha_1: 1.05 \rightarrow 0.75 \rightarrow 0.50$).
* **REQ-SIM-003.2** $\rightarrow$ `TST-SIM-003.2`: Manual regimes (`Aerobic`, `Threshold`, `Anaerobic`) and artifact injection (`e`, `m`, `x`, `g`).
* **REQ-SIM-003.3** $\rightarrow$ `TST-SIM-003.3`: `RadarService` GATT registration, V1/V2 threat packets, and `6a4e3202` tail light read/write.
* **REQ-SIM-003.4** $\rightarrow$ `TST-SIM-003.4`: 5 Hz target kinematics, close pass detection ($<1.5\text{ m}$), and synchronized traffic loops.
* **REQ-SIM-003.5** $\rightarrow$ `TST-SIM-003.5`: PyQt6 GUI telemetry cards, headless CLI dashboard/hotkeys, AST stdlib compliance (`TST-PRO-005.6`).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Android Production Modifications**: Strictly confined to `tools/replay_workout.py` and `tools/test_replay_workout.py`.
2. **Pure Python Standard Library (`TST-PRO-005.6`)**: Core math/serialization uses stdlib only; D-Bus and PyQt6 remain optional desktop layers.
3. **Cumulative RR Drift Invariant**: Phase-locked beat accumulation maintains $\le 50\text{ ms}$ drift over 60s replay.
4. **Backward Compatibility**: `--no-rr` and `--no-radar` flags allow running legacy lightweight simulation mode.
5. **Two-Tier Device Policy (`REQ-TST-004.4`)**: Preserves `--agent` emulator protection for ADB GPS injection.

---

## 4. UI Consistency & Theming (Section 5)
* **Design Language**: Obsidian Dark theme (`#121212` background, `#1E1E1E` cards, `#03DAC6` teal HR/GPS badges, `#BB86FC` purple power badges, `#FFA726` radar amber/warning badges, `#CF6679` critical red).
* **HRV Widget**: Displays live `rMSSD (ms)` and target $\alpha_1$ gauges, regime selector combo, and artifact trigger buttons.
* **Radar Widget**: Displays vehicle count, nearest distance (`m`), live `RadarLightMode` badge, and 1-click preset buttons (`+ Car`, `+ Truck`, `+ Close Pass`, `Auto Loop`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

| Step | Target File | Component / Task | Verification |
|:---|:---|:---|:---|
| **1** | `tools/replay_workout.py` | Upgrade `build_hr_payload` with Bit 4 (`0x10`) RR packing; wire `RrSynthesizer` into `HeartRateMeasurementCharacteristic` with phase accumulator. | `python3 -m unittest tools/test_replay_workout.py -k TestHrPayloadPacking` |
| **2** | `tools/replay_workout.py` | Implement `compute_auto_hrv_params(bpm)` intensity curve and manual regime/artifact injection queue in `BleReplayServer`. | `python3 -m unittest tools/test_replay_workout.py -k TestAutoHrvIntensityMapping` |
| **3** | `tools/replay_workout.py` | Add `RadarService` (`6a4e3200`, `6a4e3204`, `6a4e3203`, `6a4e3202`) and optional `VariaSim` advertisement with graceful slot error handling. | `python3 -m unittest tools/test_replay_workout.py -k TestRadarService` |
| **4** | `tools/replay_workout.py` | Integrate 5 Hz `RadarKinematicsEngine`, interactive traffic presets, and close pass detection synchronized with TCX progression. | `python3 -m unittest tools/test_replay_workout.py -k TestRadarKinematics` |
| **5** | `tools/replay_workout.py` | Mount HRV & Radar/Tail Light telemetry cards in PyQt6 `ReplayGuiWindow`; mirror status and hotkeys in `run_headless_cli`. | `python3 -m unittest tools/test_replay_workout.py -k TestCliArguments` |
| **6** | `tools/test_replay_workout.py` | Author comprehensive unit test suite covering RR byte layouts, Auto-HRV curves, radar V1/V2 threat frames, light writes, and AST stdlib hygiene. | `python3 -m unittest tools/test_replay_workout.py` |

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `python3 -m unittest tools/test_replay_workout.py` asserting 100% pass rate with zero regressions.
* **Rollback**: Clean git checkout of `tools/replay_workout.py` and `tools/test_replay_workout.py`.
