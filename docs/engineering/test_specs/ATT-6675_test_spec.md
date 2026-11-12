# Stage 2: Requirement & Test Specification - ATT-6675: Upgrade ble_hr_simulator with RR intervals, DFA a1/rMSSD synthesis, and artifact injection

**Ticket**: [ATT-6675](https://rainerblind.atlassian.net/browse/ATT-6675) | **Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3` | **Branch**: `feature/ATT-6675`  
**Requirement Mapping**: `REQ-SIM-001` (`REQ-TST-005.1`..`REQ-TST-005.4`) | **Test Spec ID**: `TST-SIM-001` (`TST-TST-005.1`..`TST-TST-005.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-SIM-001 / REQ-TST-005)

### 1.1 Problem Statement & Rationale
`tools/ble_hr_simulator.py` currently emits `flags = 0x00` without Beat-to-Beat RR intervals, preventing desk verification of real-time HRV metrics (`HRV_RMSSD`, `HRV_DFA_A1`), Lipponen & Tarvainen artifact filtering (`RrArtifactFilter`), and morning readiness checks (`MorningHrvCheckScreen`). The simulator must broadcast phase-locked BLE `0x2A37` Bit 4 (`0x10`) RR intervals, pure stdlib physiological RR series with controllable DFA $\alpha_1$ regimes and `rMSSD`, and interactive artifact/gap injection without external Python dependencies.

### 1.2 Functional & Architectural Requirements
* **REQ-SIM-001.1** (*BLE 0x2A37 Bit 4 RR Packing & Legacy Mode*): `HeartRateMeasurementCharacteristic.build_payload()` SHALL set Flag Bit 4 (`0x10`) and append 1 to 3 little-endian `uint16` RR values in units of $1/1024\text{ s}$ (`round(rr_ms * 1024.0 / 1000.0)`) per 1 Hz tick. When launched with `--no-rr`, it SHALL emit legacy `flags = 0x00` (8-bit BPM only).
* **REQ-SIM-001.2** (*Phase-Locked Beat Accumulator & Drift Invariant*): The emission loop SHALL maintain a fractional beat phase accumulator across 1 Hz ticks such that cumulative emitted RR duration remains strictly phase-locked to elapsed wall-clock time with drift $\le 50\text{ ms}$ over 60 seconds ($\ll 2000\text{ ms}$ `BTLEHeartRateDevice.DRIFT_THRESHOLD_MS`).
* **REQ-SIM-001.3** (*Pure Stdlib Physiological RR & DFA $\alpha_1$ / rMSSD Synthesis*): The generator SHALL execute purely within Python 3 standard library (`math`, `random`, zero pip dependencies per `TST-PRO-005.6`), modeling RR fluctuations via AR(1) pink/white noise: $\Delta RR_t = \phi \cdot \Delta RR_{t-1} + \sigma_\epsilon \cdot \mathcal{N}(0, 1)$ with $\sigma_\epsilon = \text{rMSSD} \sqrt{(1+\phi)/2}$, supporting regimes `aerobic` ($\phi \approx 0.70, \alpha_1 \approx 1.00$), `threshold` ($\phi \approx 0.35, \alpha_1 \approx 0.70$), and `anaerobic` ($\phi \approx -0.20, \alpha_1 \approx 0.45$).
* **REQ-SIM-001.4** (*Interactive Hotkeys & Artifact/Dropout Injection*): Terminal UI SHALL support live hotkeys: regimes `a1`, `a2`, `a3`; target BPM `+`/`-` or `b<bpm>`; rMSSD `r<ms>`; ectopic beat pair `e` ($\sim 70\%$ then $\sim 130\%$ baseline RR); missed beat `m` ($2\times$ baseline RR); extra beat `x` ($0.5\times$ baseline RR); and packet dropout `g` (suppressing notifications for $> 2.5\text{ s}$).

### 1.3 Acceptance Criteria (Given-When-Then) & System Invariants
* *Given* default simulator run, *When* connected via BLE GATT `0x2A37`, *Then* packet contains Bit 4 (`0x10`), 8-bit BPM, and 1–3 little-endian 1/1024s RR intervals parsed cleanly by `BTLEHeartRateDevice`.
* *Given* 60s continuous generation across 40–180 BPM, *When* cumulative RR sum is evaluated against wall-clock time, *Then* absolute drift is $\le 50\text{ ms}$ (zero false `ARTIFACT_GAP`).
* *Given* regime `a1`, `a2`, `a3` or target rMSSD, *When* 300 beats are synthesized, *Then* empirical rMSSD matches target within $\pm 20\%$ and short-term autocorrelation $\rho_1$ monotonically orders $\rho_1(\text{aerobic}) > \rho_1(\text{threshold}) > \rho_1(\text{anaerobic})$.
* *Given* hotkey `e`, `m`, `x`, or `g`, *When* triggered, *Then* next payload injects anomaly signature or pauses transmission for $\ge 2.5\text{ s}$.
* *Invariants*: Pure Python 3 stdlib (`TST-PRO-005.6`), zero Android runtime source code changes (`app/src/main/`), backward compatibility with `--no-rr`.

---

## 2. Test Specification (TST-SIM-001 / TST-TST-005)

### Test Case 1: BLE Byte Packing & `--no-rr` Contract Test (`TST-SIM-001.1`)
* **Scope**: Unit Test (`tools/test_ble_hr_simulator.py`)
* **Assertion**: Verify Bit 4 flag, 8-bit BPM, and 1/1024s little-endian uint16 encoding; verify `--no-rr` produces 2-byte payload `[0x00, bpm]`.

### Test Case 2: Phase-Locked Beat Accumulator Drift Test (`TST-SIM-001.2`)
* **Scope**: Simulation Unit Test (`tools/test_ble_hr_simulator.py`)
* **Assertion**: Simulate 60 seconds (60 ticks) of 1 Hz emission across 40 to 180 BPM; assert $|\sum \text{RR} - 60000\text{ ms}| \le 50\text{ ms}$.

### Test Case 3: AR(1) DFA $\alpha_1$ Regimes & rMSSD Scaling Test (`TST-SIM-001.3`)
* **Scope**: Statistical Unit Test (`tools/test_ble_hr_simulator.py`)
* **Assertion**: Synthesize 300 beats per regime in pure Python stdlib; assert empirical rMSSD matches target within $\pm 20\%$; assert $\rho_1(\text{aerobic}) > \rho_1(\text{threshold}) > \rho_1(\text{anaerobic})$.

### Test Case 4: Artifact & Gap Injection Behavior Test (`TST-SIM-001.4`)
* **Scope**: Unit Test (`tools/test_ble_hr_simulator.py`)
* **Assertion**: Trigger `e`, `m`, `x`, `g`; verify emitted RR series injects ectopic pair, missed beat, extra beat; verify gap suppresses notifications for $\ge 2500\text{ ms}$.

### Test Case 5: Python Standard Library Compliance Test (`TST-SIM-001.5`)
* **Scope**: Static AST Audit (`tools/test_ble_hr_simulator.py`)
* **Assertion**: Parse `tools/ble_hr_simulator.py` and `tools/test_ble_hr_simulator.py` via `ast` enforcing zero third-party dependencies (`TST-PRO-005.6`).

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SIM-001.1` | Unit | `HeartRateMeasurementCharacteristic.build_payload` | `REQ-SIM-001.1` | Specified |
| `TST-SIM-001.2` | Simulation | `RrSynthesizer.tick_1hz` / Phase Accumulator | `REQ-SIM-001.2` | Specified |
| `TST-SIM-001.3` | Statistical | `RrSynthesizer` DFA $\alpha_1$ & rMSSD Generator | `REQ-SIM-001.3` | Specified |
| `TST-SIM-001.4` | Unit | `RrSynthesizer.inject_artifact` & Gap Controller | `REQ-SIM-001.4` | Specified |
| `TST-SIM-001.5` | Static AST | `tools/ble_hr_simulator.py`, `test_ble_hr_simulator.py` | `REQ-SIM-001.3`, `TST-PRO-005.6` | Specified |
