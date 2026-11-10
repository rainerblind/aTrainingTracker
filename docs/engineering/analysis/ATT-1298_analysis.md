# Stage 1 Analysis: ATT-1298 - Live Mean Maximal Power (MMP) curve chart and rolling/peak duration metrics (5s, 1m, 5m, 20m)

**Ticket**: [ATT-1298](https://rainerblind.atlassian.net/browse/ATT-1298)  
**Sub-task**: [ATT-6380](https://rainerblind.atlassian.net/browse/ATT-6380) (`[Analysis]`)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-1298`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation

Athletes analyzing power-based cycling efforts rely on the Mean Maximal Power (MMP) / Power-Duration Curve to evaluate physiological capabilities and pace high-intensity efforts. `CyclingPowerMetricsDevice` currently calculates instantaneous W/kg, Coggan NP (30s window), IF, Work, TSS, and Skiba W'bal, but lacks duration-specific rolling averages (5s, 1m, 5m, 20m) and session peak power tracking. Furthermore, the tracking cockpit provides no real-time graphical MMP curve visualization during live workouts.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Root Cause / Architectural Gap**: `CyclingPowerMetricsDevice` only maintains a single 30s FIFO queue (`mPowerWindow30s`) dedicated to Coggan NP. No sliding window accumulators exist for benchmark intervals (5s, 60s, 300s, 1200s), session peak powers are untracked, and `SensorType` lacks duration-based power metrics. The cockpit lacks a dynamic vector MMP curve composable.
* **Independent Peer Review & Verification**:
  * Confirmed in `CyclingPowerMetricsDevice.java`: 1-Hz executor loop (`scheduleWithFixedDelay`) ingests filtered/raw power. Pause gating (`isSuspended`) halts accumulation.
  * Verified Rule 40 (`REQ-TRK-023`): Rolling averages must compute over the growing window ($1 \le k < W$) from the first second without blank delay (`"--"`).
  * Verified Rule 52 (`REQ-TRK-038`): Virtual metrics must not publish values before valid power telemetry is received (`mActivePowerDurationSec > 0`).
  * $O(1)$ circular ring-buffer accumulator ensures minimal memory ($\approx 9.6\text{ KB}$ for 1200 doubles) and $< 0.1\text{ ms}$ CPU overhead per tick.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * $O(1)$ ring-buffer sliding window accumulators in `CyclingPowerMetricsDevice` for benchmark durations: 5s, 1m (60s), 5m (300s), and 20m (1200s).
  * Compute rolling mean powers (`POWER_5S`, `POWER_1M`, `POWER_5M`, `POWER_20M`) and session peak powers (`POWER_PEAK_5S`, `POWER_PEAK_1M`, `POWER_PEAK_5M`, `POWER_PEAK_20M`) at 1Hz during active tracking.
  * Register 8 new `SensorType` entries with full 9-language localization, formatters, and icons.
  * Implement `LiveMmpCurveChart` Compose vector chart with logarithmic time scale ($1\text{s} \dots 7200\text{s}$) and benchmark duration markers.
  * In-session peak breakthrough detection and visual highlighting.
  * Living documentation update in `docs/requirements/req_tracking_cockpit.md` (`REQ-TRK-058`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Multi-session historical season-best PR database aggregation (separate database indexing ticket).
  * Running pace and heart rate MMP extensions (architecture is extensible, but sport-specific types belong to future tickets).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-TRK-058`, extending `REQ-TRK-019` (W/kg), `REQ-TRK-020` (NP/IF), `REQ-TRK-021` (Work/TSS), and `REQ-TRK-023` (Warm-Up Window). Targets `CyclingPowerMetricsDevice.java`, `SensorType.java`, `LiveMmpCurveChart.kt`, and `strings*.xml`.
* **Historical Origin & Commit Trace**: `ATT-1289` (commit `5eee662f`), `ATT-1290` (commit `4fe4996c`), `ATT-1291`, and `ATT-3778` established power computation infrastructure.
* **Root Reason for Existing Formulation**: Prior tickets focused on summary Coggan metrics and $W'_{\text{bal}}$; duration curve benchmarks were deferred to live-tracking epic `ATT-5908`.
* **Preservation of Core Invariants**: 1-Hz executor timing, Rule 40 growing window, Rule 52 sensor-presence gating, pause suspension, and bounded CPU/RAM budget strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

* **Accumulator Engine**: Create `SlidingWindowPowerAccumulator` managing circular ring-buffer array, head/tail pointers, and running sum with $O(1)$ sample additions.
* **Telemetry Integration**: `CyclingPowerMetricsDevice` updates 4 accumulators on valid power samples, calculates rolling means (`sum / size`), tracks session peaks (`max(peak, mean)`), and publishes to 8 new `MySensor` instances. `resetSession()` clears all buffers and peaks.
* **Cockpit Visualization**: `LiveMmpCurveChart` renders the live power-duration curve with logarithmic X-axis and benchmark markers in the cockpit.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Existing power metrics (`POWER`, `NP`, `IF`, `WORK_KJ`, `TSS`, `W_PRIME_BAL`) remain 100% regression-free.
  2. Pauses freeze rolling accumulators without corrupting running sum or session peaks.
  3. No snapshot state writes during composition (Rule 66 / ATT-4863).
  4. 100% 9-language localization parity across all resource files.
  5. Parent ticket Human Decision Gate strictly enforced.
* **Risk Rating**: **MEDIUM** — Adds multi-sensor 1-Hz accumulator engine to `CyclingPowerMetricsDevice` with pause lifecycle synchronization and a new Compose vector chart.
