# Stage 1 Analysis: ATT-6677 - Integrate BLE HRV synthesis and Garmin Varia Radar simulation into replay_workout.py

**Ticket**: [ATT-6677](https://rainerblind.atlassian.net/browse/ATT-6677)  
**Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V5.0.0` | **Active Sprint**: Backlog | **Branch**: `feature/ATT-6677`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  
**Requirement Mapping**: `REQ-SIM-003` (`REQ-TST-007.1`..`REQ-TST-007.5`)  
**Test Mapping**: `TST-SIM-003` (`TST-TST-007.1`..`TST-TST-007.5`)  

---

## 1. Problem Statement & Motivation
* **Context**: `tools/replay_workout.py` replays TCX workouts with synchronized ADB mock GPS, BLE Cycling Power (`0x1818`), and BLE Heart Rate (`0x180D`).
* **Gap**:
  1. `build_hr_payload(bpm)` emits `flags = 0x00` (8-bit BPM only, zero Beat-to-Beat RR intervals). `BTLEHeartRateDevice` receives no RR stream, blocking desk verification of real-time HRV (`rMSSD`, `DFA a1`, `HRVT1`/`HRVT2`), artifact filtering, and post-workout scatter plots.
  2. Lacks Garmin Varia BLE Radar (`6a4e3200-667b-11e3-949a-0800200c9a66`) service support, preventing simultaneous verification of GPS tracks, power, HR/HRV, radar approaching targets (V1/V2), close-pass map hotspots (`TrackOnMapScreen.kt`), and tail light writes (`6a4e3202`) in a single replay session.
* **Objective**: Integrate pure-stdlib physiological HRV beat-to-beat RR synthesis (intensity-coupled or manual regimes/artifacts) and Garmin Varia Radar simulation (V1/V2 streams, tail light control, traffic presets) into `replay_workout.py` GUI/CLI with unit tests in `tools/test_replay_workout.py`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * `tools/replay_workout.py:426`: `build_hr_payload(bpm)` hardcodes `[0x00, bpm]`. No phase accumulator or RR synthesis exists.
  * `BleReplayServer`: Advertises and registers only Cycling Power (`0x1818`) and Heart Rate (`0x180D`); lacks `RadarService` (`6a4e3200`), target kinematic loop, and `6a4e3202` write handler.
  * `ReplayGuiWindow` & CLI: Lack HRV telemetry badges/controls and Radar target/tail-light UI and key bindings.
* **Independent Peer Review & Verification (ATT-3534, Rules 68 & 70)**:
  * *Intensity Coupling*: In `Auto-HRV` mode, `DFA a1` is continuously derived from TCX HR: $\alpha_1 \approx 1.05$ at $\le 120\text{ BPM}$, crossing $0.75$ (aerobic HRVT1) at $145\text{ BPM}$, down to $0.50$ (anaerobic HRVT2) at $165\text{ BPM}$ ($\alpha_1 = 0.50$ for $\ge 165\text{ BPM}$), while `rMSSD` scales inversely ($60\text{ ms} \rightarrow 10\text{ ms}$). This faithfully exercises threshold detection (`ATT-3554`) during ramp TCX replays.
  * *Host BLE Controller Advertisement Capacity*: BlueZ controllers may have limited concurrent `LEAdvertisement1` slots. Registering a 3rd advertisement (`VariaSim`) will be handled gracefully (try-except logging without crashing) alongside a `--radar` CLI flag and GUI toggle.
  * *Side-Effect & Signature Parity*: `build_hr_payload(bpm, rr_raw_list=None, enable_rr=True)` and `BleReplayServer.update_telemetry` preserve default parameters for backwards compatibility.

---

## 3. Scope Grounding & Non-Goals (ATT-1250)
* **In-Scope Goals**:
  * Upgrade `build_hr_payload` and `HeartRateMeasurementCharacteristic` with `0x2A37` Bit 4 (`0x10`) RR packing and phase-locked accumulator reusing `RrSynthesizer` from `tools/ble_hr_simulator.py`.
  * Intensity-coupled `Auto-HRV` mode plus manual regime overrides (`Aerobic`, `Threshold`, `Anaerobic`) and artifact injection (`Ectopic`, `Missed`, `Gap`).
  * Add `RadarService` (`6a4e3200`, `6a4e3204`, `6a4e3203`, `6a4e3202`) and optional `VariaSim` advertisement to `BleReplayServer`, with 5 Hz target kinematics and presets (`Car`, `Truck`, `Close Pass`, `Auto Loop`).
  * Add HRV & Radar/Tail Light telemetry widgets to PyQt6 GUI and hotkeys/status to headless CLI.
  * Comprehensive unit tests in `tools/test_replay_workout.py` for payload packing, intensity mapping, and radar/light state callbacks.
* **Out-of-Scope Non-Goals**:
  * Zero modifications to Android production code in `app/src/main/`.
  * Zero modifications to standalone scripts `ble_hr_simulator.py` and `ble_radar_simulator.py`.
  * Pure Python 3 standard library (+ existing PyQt6/dbus desktop stack); zero new pip dependencies (`TST-PRO-005.6`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022, Rule 68)
* **Original Requirement ID & Target**: Net-new requirement `REQ-SIM-003` (`REQ-TST-007`) under Epic `ATT-2466` (*Developer Testing Tools*), extending `REQ-TRK-024` and `REQ-TRK-025`. Targets `tools/replay_workout.py` and `tools/test_replay_workout.py`.
* **Historical Origin & Commit Trace**: `ATT-2969` / `ATT-3178` (replay architecture), `ATT-6675` (`314b5f16`, RR synthesis), and `ATT-6676` (`f9016b86`, Radar simulation).
* **Root Reason for Existing Formulation**: Replay tool initially focused on power/GPS/basic BPM. Simulators were developed standalone first to validate protocols in isolation before unifying them.
* **Preservation of Core Invariants**: Existing CLI flags and GUI transport controls remain untouched; `--no-rr` and `--no-radar` flags allow running in legacy low-overhead mode.

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Python 3 standard library invariant (`TST-PRO-005.6`): Zero external pip dependencies.
  2. Protocol fidelity: BLE `0x2A37` Bit 4 uint16 LE RR in $1/1024\text{ s}$ units; Garmin Varia V1/V2 threat layouts; `6a4e3202` mode bytes (`0x00`, `0x01`, `0x05`, `0x06`, `0x07`).
  3. Timing & Phase Lock: 60s cumulative RR drift $\le 50\text{ ms}$, preventing false gap/drift alarms.
  4. Advertisement Budget: `VariaSim` legacy advertisement $\le 31$ bytes.
  5. Two-Tier Device Policy: Enforce emulator precedence (`emulator-5554`) under `--agent` / `ATT_AGENT_MODE`.
* **Risk Rating**: **LOW** — Confined purely to developer desktop testing scripts in `tools/`; zero Android production code changes.
