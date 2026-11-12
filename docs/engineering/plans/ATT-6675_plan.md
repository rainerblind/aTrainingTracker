# Stage 3: Implementation Plan - ATT-6675: Upgrade ble_hr_simulator with RR intervals, DFA a1/rMSSD synthesis, and artifact injection

**Ticket**: [ATT-6675](https://rainerblind.atlassian.net/browse/ATT-6675) | **Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3` | **Branch**: `feature/ATT-6675`  
**Requirement Mapping**: `REQ-SIM-001` (`REQ-TST-005.1`..`REQ-TST-005.4`) | **Test Mapping**: `TST-SIM-001` (`TST-TST-005.1`..`TST-TST-005.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
`tools/ble_hr_simulator.py` emulates a BLE Heart Rate service (GATT `0x180D`, `0x2A37`) but currently broadcasts only 8-bit BPM (`flags = 0x00`), omitting Beat-to-Beat RR intervals (Bit 4 `0x10`). This prevents desk verification of real-time HRV calculations (`rMSSD`, `DFA a1`), Lipponen & Tarvainen artifact filtering, lossless `WorkoutRrIntervals` persistence, and morning readiness checks without wearing an ECG chest strap.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-SIM-001.1` (BLE 0x2A37 Bit 4 RR Packing & `--no-rr` flag), `REQ-SIM-001.2` (Phase-Locked Beat Accumulator & Drift Invariant), `REQ-SIM-001.3` (Pure Stdlib AR(1) DFA $\alpha_1$ & rMSSD Synthesis), `REQ-SIM-001.4` (Interactive Hotkeys & Artifact/Dropout Injection).
* **Test Mapping**: `TST-SIM-001.1` (BLE byte packing contract), `TST-SIM-001.2` (Accumulator 60s drift test), `TST-SIM-001.3` (Statistical DFA $\alpha_1$ / rMSSD test), `TST-SIM-001.4` (Artifact & gap injection test), `TST-SIM-001.5` (Python stdlib AST compliance).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Android Production Modifications**: `app/src/main/` remains 100% untouched.
2. **Pure Python 3 Standard Library**: Zero external pip dependencies (`TST-PRO-005.6`).
3. **Cumulative Drift Invariant**: Cumulative emitted RR drift $\le 50\text{ ms}$ over 60s ($\ll 2000\text{ ms}$ `DRIFT_THRESHOLD_MS` in `BTLEHeartRateDevice`).
4. **Backward Compatibility**: `--no-rr` switch produces legacy 2-byte `[0x00, bpm]` payload.

---

## 4. Proposed Architectural Changes & UI Consistency
* **Synthesizer Core (`RrSynthesizer`)**: Encapsulates target BPM, AR(1) parameter $\phi$, innovation scale $\sigma_\epsilon = \text{rMSSD}\sqrt{(1+\phi)/2}$, phase accumulator, and artifact queue.
* **GATT Characteristic (`HeartRateMeasurementCharacteristic`)**: Encodes Bit 4 (`0x10`), 8-bit BPM, and little-endian `1/1024 s` RR pairs; supports gap injection by pausing notifications for $\ge 2.5\text{ s}$.
* **CLI & Terminal Controls**: Hotkeys (`a1`, `a2`, `a3`, `r<ms>`, `e`, `m`, `x`, `g`) match terminal UX of `ble_power_simulator.py`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: BLE 0x2A37 Bit 4 (0x10) 1/1024s Little-Endian RR-Interval Byte Packing
* File: `tools/ble_hr_simulator.py`
* Implement `flags = 0x10` and little-endian uint16 encoding in `1/1024 s` units (`round(rr_ms * 1024.0 / 1000.0)`). Add `--no-rr` flag.

### Step 2: Phase-Locked Beat Accumulator with Monotonic Millisecond Pacing
* File: `tools/ble_hr_simulator.py`
* Implement fractional-beat phase accumulator tracking elapsed time across 1 Hz ticks; guarantees 60s cumulative drift $\le 50\text{ ms}$.

### Step 3: AR(1) Fractional Noise DFA a1 Synthesis Regimes
* File: `tools/ble_hr_simulator.py`
* Implement pure stdlib autoregressive physiological noise: `aerobic` ($\phi \approx 0.70, \alpha_1 \approx 1.00$), `threshold` ($\phi \approx 0.35, \alpha_1 \approx 0.70$), `anaerobic` ($\phi \approx -0.20, \alpha_1 \approx 0.45$).

### Step 4: Controllable rMSSD Scaling & Interactive Artifact/Dropout Injection
* File: `tools/ble_hr_simulator.py`
* Scale innovation variance $\sigma_\epsilon = \text{rMSSD}\sqrt{(1+\phi)/2}$. Wire hotkeys: `e` (ectopic), `m` (missed), `x` (extra), `g` ($> 2.5\text{ s}$ gap).

### Step 5: Automated Test Suite in tools/test_ble_hr_simulator.py
* File: `tools/test_ble_hr_simulator.py`
* Unit tests for byte packing, accumulator drift, statistical regime ordering, artifact injection, and AST stdlib verification.
* Test Command: `python3 -m unittest tools/test_ble_hr_simulator.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `python3 -m unittest tools/test_ble_hr_simulator.py` and `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Clean revert on `feature/ATT-6675` leaves `sprint/2026-46.3` unaffected.
