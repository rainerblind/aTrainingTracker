# Stage 1 Analysis: ATT-6675 - Upgrade ble_hr_simulator with RR intervals, DFA a1/rMSSD synthesis, and artifact injection

**Ticket**: [ATT-6675](https://rainerblind.atlassian.net/browse/ATT-6675)  
**Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3` | **Branch**: `feature/ATT-6675`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  
**Requirement Mapping**: `REQ-TST-005` (`REQ-TST-005.1`..`REQ-TST-005.4`)  
**Test Mapping**: `TST-TST-005` (`TST-TST-005.1`..`TST-TST-005.4`)  

---

## 1. Problem Statement & Motivation
* **Context**: `tools/ble_hr_simulator.py` emulates a BLE Heart Rate Monitor (GATT `0x180D`, `0x2A37`), but currently emits `flags = 0x00` (8-bit BPM only) without Beat-to-Beat RR intervals (Bit 4 `0x10`).
* **Gap**: When connecting an Android test device to the simulator, `BTLEHeartRateDevice` receives zero RR intervals (`SensorType.RR_INTERVAL` never registers, `RrBeatListener` never fires). This prevents desk verification of real-time HRV metrics (`HRV_RMSSD`, `HRV_DFA_A1`), Lipponen & Tarvainen artifact filtering (`RrArtifactFilter`), lossless `WorkoutRrIntervals` persistence, and morning readiness checks (`MorningHrvCheckScreen`).
* **Objective**: Upgrade `tools/ble_hr_simulator.py` with phase-locked BLE `0x2A37` Bit 4 RR packing, pure stdlib physiological RR synthesis (selectable DFA $\alpha_1$ regimes and `rMSSD`), interactive artifact/gap injection, and standalone unit test suite `tools/test_ble_hr_simulator.py`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * `HeartRateMeasurementCharacteristic.build_payload()` hardcodes `flags = 0x00` and `payload = [flags, bpm]`, omitting RR intervals.
  * No beat-timing model or fractional phase accumulator exists across 1 Hz GLib timer ticks.
* **Independent Peer Review & Verification (ATT-3534, Rules 68 & 70)**:
  * *BLE 0x2A37 Specification Parity*: When Bit 4 is set (`0x10`), 1 to 3 little-endian `uint16` RR values in units of $1/1024\text{ s}$ (`round(rr_ms * 1024.0 / 1000.0)`) must be appended per tick.
  * *Phase Accumulator & Drift Invariant*: Cumulative emitted RR intervals must stay phase-locked to elapsed wall-clock time. If cumulative RR diverges from wall-clock by $> 2000\text{ ms}$, `BTLEHeartRateDevice.DRIFT_THRESHOLD_MS` triggers a spurious timestamp re-anchor and gap alert. A fractional phase accumulator eliminates drift ($< 50\text{ ms}$ over 60s).
  * *Zero Non-Stdlib Dependency Mandate (`TST-PRO-005.6`)*: All physiological noise synthesis (AR(1) pink/white noise and `rMSSD` scaling) must execute purely in Python standard library (`math`, `random`) without requiring `pip install numpy/scipy`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * Phase-locked BLE `0x2A37` Bit 4 (`0x10`) RR interval packing with fractional accumulator and `--no-rr` backwards compatibility flag.
  * Pure stdlib physiological RR series generator supporting DFA regimes (`aerobic` $\alpha_1 \approx 1.0$, `threshold` $\alpha_1 \approx 0.70$, `anaerobic` $\alpha_1 \approx 0.45$) and configurable `rMSSD`.
  * Interactive terminal hotkeys for regimes (`a1`, `a2`, `a3`), `r<ms>`, and artifact injection (`e` ectopic, `m` missed, `x` extra, `g` $> 2.5\text{ s}$ gap).
  * Standalone automated unit test suite `tools/test_ble_hr_simulator.py` covering byte layout, timing drift, artifact injection, and statistical regime separation.
* **Out-of-Scope**: Modifications to Android production code (`BTLEHeartRateDevice.java` is already complete and verified).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022, Rule 68)
* **Status**: Net-new requirement `REQ-TST-005` and test spec `TST-TST-005` under Epic `ATT-2466`.
* **Chesterton's Fence**: Preserves existing `--bpm` and `--battery` CLI flags, D-Bus GATT service registration, and `--no-rr` option for testing legacy 8-bit BPM devices without RR streams.

---

## 5. Architectural Strategy & High-Level Solution
* **RR Synthesizer Class (`RrSynthesizer`)**: Encapsulates target BPM, DFA persistence parameter $\phi$, and innovation scaling $\sigma_\epsilon = \text{rMSSD} \sqrt{(1+\phi)/2}$. Queues pending artifacts (`ECTOPIC`, `MISSED`, `EXTRA`).
* **Phase Accumulator**: On each 1 Hz tick, advances wall clock by delta time, generates all beats whose cumulative duration falls within the window, and converts to `1/1024 s` uint16.
* **GATT Characteristic**: Updates `build_payload()` to return `[flags, bpm, rr0_low, rr0_high, ...]` and handles gap injection by suppressing notifications for $> 2.5\text{ s}$.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Python 3 standard library invariant (`TST-PRO-005.6`): Zero external pip dependencies.
  2. Protocol fidelity: Little-endian 16-bit encoding in $1/1024\text{ s}$ matching `BTLEHeartRateDevice.java`.
  3. Timing drift: 60-second cumulative drift $\le 50\text{ ms}$, preventing false `ARTIFACT_GAP` events.
  4. Backward compatibility: Supports `--no-rr` legacy flag.
* **Risk Rating**: **LOW** — Self-contained Python developer simulation tool in `tools/` with zero production Android app code impact.
