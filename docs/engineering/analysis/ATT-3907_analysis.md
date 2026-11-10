# Stage 1 Analysis: ATT-3907 - Real-Time Live HR Drift Sensor Metric in CyclingPowerMetricsDevice & Tracking Cockpit

**Ticket**: [ATT-3907](https://rainerblind.atlassian.net/browse/ATT-3907)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Dynamical Power-to-Heart-Rate System Identification*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-3907`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation

During endurance cycling, cardiac drift ($\text{HR}_{\text{actual}}(t) - \text{HR}_{\text{fresh\_model}}(t)$) provides immediate, power-normalized feedback on hydration, thermal stress, and autonomic decoupling. While `ATT-3904` introduced the post-workout 1st-order dynamical identification engine and `ATT-3905`/`ATT-3906` visual summary and trend cards, athletes currently lack in-session real-time feedback. Static ratios ($\text{EF}$ or $\text{Pw:Hr}$) misinterpret hilly terrain and bursts as drift due to phase lag. `ATT-3907` adds `LIVE_HR_DRIFT` to `CyclingPowerMetricsDevice` and the tracking cockpit using lag-compensated 1st-order state simulation.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Gap Analysis**: `CyclingPowerMetricsDevice.java` computes W/kg, NP, IF, TSS, MMP, and $W'_{\text{bal}}$, but does not evaluate paired heart rate or track dynamical cardio-power drift in real time.
* **Independent Peer Review & Verification**:
  * *Sensor Categorization & Formatting*: In `SensorType.java`, `LIVE_HR_DRIFT` represents cardiac decoupling in $\text{bpm}$ (`units_heart_rate`). Categorized under `HEART_RATE_AND_ENERGY`, formatted via `SignedIntegerFormatter` (e.g. `+5` / `-2` / `0 bpm`).
  * *Baseline Seeding & Real-Time Simulation*: Before 10 min of paired pedaling accumulates, seed baseline $(\tau, \text{HR}_0, K)$ from recent qualified rides ($\ge 0.50\text{ }R^2$) via `WorkoutSummariesDatabaseManager.getCardioPowerDynamicsFitnessTrendSamples`. During live tracking, propagate discrete-time state $x_k = \alpha x_{k-1} + (1-\alpha) P_k$ at 1 Hz and compute $\widehat{\text{HR}}_k = \text{HR}_0 + K x_k$.
  * *Growing Warm-Up Window (Coding Standards §1.6 / Rule 40)*: 30-second smoothed residual $\text{HR}_{\text{actual}} - \widehat{\text{HR}}$ computes over available samples ($1 \le k < 30$) to prevent 30s blank `"--"` delays.
  * *Sensor-Presence Gating (Coding Standards §1.8 / Rules 51 & 52)*: `LIVE_HR_DRIFT` requires BOTH active `SensorType.POWER` and `SensorType.HR`, plus `enableCardioPowerDynamics == true`. For non-power workouts or unlinked straps, emissions remain `null` to prevent phantom data.
  * *Cockpit Accenting*: In `TrackingViewModel.kt`, map drift values into intuitive visual thresholds: $\le +3\text{ bpm}$ (neutral/green `TTColor.Zone2`), $+4\dots+8\text{ bpm}$ (moderate decoupling `TTColor.Zone3`), $\ge +9\text{ bpm}$ (elevated fatigue `TTColor.Zone4`).

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Add `SensorType.LIVE_HR_DRIFT` (`HEART_RATE_AND_ENERGY`, `SignedIntegerFormatter`, `ic_cardio_load`).
  2. Implement `LIVE_HR_DRIFT` computation in `CyclingPowerMetricsDevice.java` (1 Hz simulation, 30s growing-window residual, baseline seeding, pause handling).
  3. Wire cockpit field selection, tuning visibility gating (`enableCardioPowerDynamics`), and color thresholds in `SensorFieldState.kt`, `EditSensorFieldViewModel.kt`, and `TrackingViewModel.kt`.
  4. Localization parity across 9 locales in `res/values*/strings_cardio_power.xml`.
  5. Living-doc mapping: `REQ-REV-012` and `TST-REV-013` in `req_aftermath_reviews.md` and `tst_aftermath_reviews.md`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Audio/haptic drift alarm dispatch (future sprint candidate).
  2. Changes to historical post-workout summaries (`ATT-3905`) or longitudinal trend queries (`ATT-3906`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Net-new requirement only** (`REQ-REV-012`, `TST-REV-013`). No existing requirements modified.

---

## 5. Architectural Strategy & High-Level Solution

* **Sensor Layer**: `CyclingPowerMetricsDevice.java` instantiates `mLiveHrDriftSensor`, queries filtered/raw HR alongside power, and runs 1st-order state propagation.
* **Formatting**: `SignedIntegerFormatter.java` in `banalservice/sensor/formater/` ensures explicit signed display (`+5 bpm`, `0 bpm`, `-2 bpm`).
* **UI Cockpit**: `TrackingViewModel.kt` calculates semantic drift zone colors (`Zone2`/`Zone3`/`Zone4`).

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Rule 51 parity: `resetSession()` clears drift accumulators; constructor invokes `resetSession()`.
  2. Rule 52 gating: No emission unless both `POWER` and `HR` sensors are active and `enableCardioPowerDynamics == true`.
  3. Rule 40: Growing warm-up window for 30s residual smoothing.
  4. 100% 9-locale parity in `strings_cardio_power.xml`.
* **Risk Rating**: **LOW** — Additive telemetry metric within existing `CyclingPowerMetricsDevice` execution loop with zero database schema migrations.
