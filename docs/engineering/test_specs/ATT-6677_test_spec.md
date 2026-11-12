# Stage 2: Requirement & Test Specification - ATT-6677: Integrate BLE HRV beat-to-beat synthesis and Garmin Varia Radar simulation into tools/replay_workout.py GUI and CLI

**Ticket**: [ATT-6677](https://rainerblind.atlassian.net/browse/ATT-6677) | **Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V5.0.0` | **Active Sprint**: Backlog | **Branch**: `feature/ATT-6677`  
**Requirement Mapping**: `REQ-SIM-003` (`REQ-TST-007.1`..`REQ-TST-007.5`) | **Test Spec ID**: `TST-SIM-003` (`TST-TST-007.1`..`TST-TST-007.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-SIM-003 / REQ-TST-007)

### 1.1 Problem Statement & Rationale
`tools/replay_workout.py` replays TCX workouts with ADB mock GPS, BLE Power (`0x1818`), and BLE HR (`0x180D`), but emits `flags = 0x00` (integer BPM only, zero Beat-to-Beat RR intervals) and lacks Garmin Varia BLE Radar (`6a4e3200`). This blocks desk verification of real-time HRV (`rMSSD`, `DFA a1`, `HRVT1`/`HRVT2`), post-workout scatter plots, radar traffic overlays, close-pass map hotspots (`TrackOnMapScreen.kt`), and tail light control.

### 1.2 Functional & Architectural Requirements
* **REQ-SIM-003.1** (*Intensity-Coupled Beat-to-Beat RR Synthesis & Auto-HRV Curve*): `HeartRateMeasurementCharacteristic` and `build_hr_payload` in `tools/replay_workout.py` SHALL set Flag Bit 4 (`0x10`) and pack 1 to 3 little-endian `uint16` RR values in units of $1/1024\text{ s}$ using `RrSynthesizer` from `tools.ble_hr_simulator`. In `Auto-HRV` mode, target DFA $\alpha_1$ and $\text{rMSSD}$ SHALL dynamically scale with replayed TCX Heart Rate: $\alpha_1 = 1.05$ for $\text{BPM} \le 120$, linearly decreasing to $0.75$ at $145\text{ BPM}$, reaching $0.50$ at $165\text{ BPM}$ ($\alpha_1 = 0.50$ for $\text{BPM} \ge 165$), with $\text{rMSSD}$ smoothly scaling inversely ($60.0\text{ ms} \rightarrow 10.0\text{ ms}$). When `--no-rr` is specified, it SHALL emit legacy `flags = 0x00`.
* **REQ-SIM-003.2** (*Manual HRV Regimes & Interactive Artifact Injection*): Replay tool SHALL support manual regime overrides (`Auto`, `Aerobic`, `Threshold`, `Anaerobic`) and artifact injection triggers: ectopic PVC pair (`e`), missed beat (`m`), extra beat (`x`), and RF notification gap dropout (`g`, $> 2.5\text{ s}$ suppression) across both GUI buttons and CLI hotkeys.
* **REQ-SIM-003.3** (*Garmin Varia BLE Radar Service & Advertisement Integration*): `BleReplayServer` SHALL host Garmin Varia Radar Primary Service `6a4e3200-667b-11e3-949a-0800200c9a66`, supporting V2 2D target stream `6a4e3204`, V1 legacy threat stream `6a4e3203`, and bidirectional Tail Light Control `6a4e3202` (receiving 1-byte GATT writes for `OFF`, `SOLID`, `PELOTON`, `NIGHT_FLASH`, `DAY_FLASH` and reflecting state). If enabled via CLI (`--radar`, default True) or GUI toggle, it SHALL register a legacy advertisement `VariaSim` ($\le 31$ bytes) handling host adapter slot constraints gracefully.
* **REQ-SIM-003.4** (*5 Hz Target Kinematics & Synchronized Traffic Loop*): Target physics SHALL tick at 5 Hz ($r_{t+1} = r_t - v \cdot dt$) updating approaching vehicle distance, speed, and lateral position synchronized with ADB mock GPS advancement along the TCX track. Traffic triggers SHALL support single car (`c`), fast car (`f`), truck/bus (`t`), close pass $<1.5\text{ m}$ (`p`), and Auto Traffic Loop (`l`) to produce verified close-pass hotspots on `TrackOnMapScreen.kt`.
* **REQ-SIM-003.5** (*PyQt6 Obsidian GUI & Headless CLI Dual-Interface Parity*): PyQt6 GUI SHALL display real-time HRV telemetry badges (current `rMSSD`, target $\alpha_1$, regime selector, artifact buttons) and Radar/Tail-Light status badges (approaching vehicle count, closest distance, live phone-commanded `RadarLightMode` badge, preset buttons). Headless CLI SHALL mirror telemetry and keyboard hotkeys. Pure Python 3 stdlib/PyQt6/dbus without external pip dependencies (`TST-PRO-005.6`).

### 1.3 Acceptance Criteria (Given-When-Then) & System Invariants
* *Given* TCX replay with HR data, *When* 1 Hz BLE HR notifications (`0x2A37`) are broadcast, *Then* payload sets Bit 4 (`0x10`) with phase-locked $1/1024\text{ s}$ RR intervals whose DFA $\alpha_1$ and $\text{rMSSD}$ scale dynamically with replayed BPM.
* *Given* radar enabled, *When* traffic preset triggered and phone writes light mode byte to `6a4e3202`, *Then* 5 Hz radar target notifications (`6a4e3204`/`6a4e3203`) stream while ADB mock GPS advances, and GUI/CLI displays live target distance and phone-commanded light mode.
* *Invariants*: Pure Python 3 stdlib + PyQt6/dbus (`TST-PRO-005.6`), zero Android runtime source code changes (`app/src/main/`), 60s cumulative RR drift $\le 50\text{ ms}$, 31-byte legacy advertisement PDU budget.

---

## 2. Test Specification (TST-SIM-003 / TST-TST-007)

### Test Case 1: BLE 0x2A37 Bit 4 RR Packing & Legacy Compatibility Contract (`TST-SIM-003.1`)
* **Scope**: Unit Test (`tools/test_replay_workout.py`)
* **Assertion**: Verify `build_hr_payload` sets Bit 4 (`0x10`) and encodes little-endian `uint16` $1/1024\text{ s}$ RR intervals; verify `enable_rr=False` produces `[0x00, bpm]`.

### Test Case 2: Auto-HRV Intensity Coupling Curve & Phase-Lock Drift (`TST-SIM-003.2`)
* **Scope**: Statistical & Simulation Test (`tools/test_replay_workout.py`)
* **Assertion**: Verify BPM-to-HRV mapping: $\alpha_1 \approx 1.05$ at $\le 120\text{ BPM}$, $\approx 0.75$ at $145\text{ BPM}$, $\approx 0.50$ at $\ge 165\text{ BPM}$; verify 60s simulated replay phase accumulator drift $\le 50\text{ ms}$.

### Test Case 3: Garmin Varia BLE Radar Service & Tail Light State Machine (`TST-SIM-003.3`)
* **Scope**: Protocol Unit Test (`tools/test_replay_workout.py`)
* **Assertion**: Verify `RadarService` initialization, V2/V1 binary serialization compatibility, `6a4e3202` write handler updating light mode, and `RadarLightMode` enum.

### Test Case 4: Kinematics Engine & Traffic Presets Synchronization (`TST-SIM-003.4`)
* **Scope**: Simulation Unit Test (`tools/test_replay_workout.py`)
* **Assertion**: Verify target kinematics closing range at 5 Hz, overtake removal, close pass detection ($<1.5\text{ m}$), and traffic loop presets.

### Test Case 5: Python Standard Library Compliance & CLI Parser Contract (`TST-SIM-003.5`)
* **Scope**: Static AST Audit & CLI Test (`tools/test_replay_workout.py`)
* **Assertion**: Parse `tools/replay_workout.py` via `ast` enforcing zero external pip dependencies (`TST-PRO-005.6`); test CLI parser for `--radar`, `--no-radar`, `--no-rr`, and `--hrv-regime`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SIM-003.1` | Unit | `build_hr_payload`, `HeartRateMeasurementCharacteristic` | `REQ-SIM-003.1` | Specified |
| `TST-SIM-003.2` | Simulation | Auto-HRV mapping & Phase Accumulator | `REQ-SIM-003.1`, `REQ-SIM-003.2` | Specified |
| `TST-SIM-003.3` | Protocol | `RadarService`, `RadarLightControlCharacteristic` | `REQ-SIM-003.3` | Specified |
| `TST-SIM-003.4` | Simulation | `RadarKinematicsEngine` presets & TCX GPS sync | `REQ-SIM-003.4` | Specified |
| `TST-SIM-003.5` | Static AST | `tools/replay_workout.py`, CLI argument parser | `REQ-SIM-003.5`, `TST-PRO-005.6` | Specified |
