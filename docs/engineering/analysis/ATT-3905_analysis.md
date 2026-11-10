# Stage 1 Analysis: ATT-3905 - Display Cardio-Power Dynamics Card & Fresh Model HR Overlay

**Ticket**: [ATT-3905](https://rainerblind.atlassian.net/browse/ATT-3905)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Dynamical Power-to-Heart-Rate System Identification*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-3905`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation

With the 1st-order dynamical identification engine and DB v29 persistence delivered in `ATT-3904`, athletes require post-workout visual presentation of submaximal aerobic economy and fatigue drift.
Currently, `WorkoutDetails.kt` and `TelemetryMetricGraph.kt` only render static Coggan power metrics, static aerobic efficiency ($EF$), and raw heart rate curves without lag compensation.
`ATT-3905` provides:
1. A domain-grouped summary row in `WorkoutDetails.kt` displaying steady-state reference $\text{HR@200W}$, time constant $\tau$, aerobic slope $1/K$, and cardiac drift $\Delta\text{HR@200W}$ following design guidelines §5.10 (`AltitudeRow` pattern).
2. A "Fresh HR Model" toggle and visual curve overlay in `TelemetryMetricGraph.kt` rendering the cyan/blue dashed predicted fresh HR curve alongside actual measured HR, shading the cardiac drift fatigue gap.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Gap Analysis**: `WorkoutDataMapper.kt` and `WorkoutDetailsData.kt` do not yet map `CardioPowerDynamicsRecord` to UI state. `WorkoutDetails.kt` lacks a domain row for dynamical parameters. `TelemetryMetricGraph.kt` lacks trajectory projection and gap shading.
* **Independent Peer Review & Verification**:
  * *Design Guidelines §5.10 Compliance*: Grouped metrics inside `WorkoutDetails.kt` MUST strictly follow the single-line `AltitudeRow` pattern (left domain icon column + single row of at most 2–3 `MetricItem`s). Multi-row stacking and `FlowRow` pill clouds are strictly prohibited.
  * *Canvas Telemetry Graph Projection*: `TelemetryMetricGraph.kt` already samples time/distance series. `PowerHrDynamicsCalculator.simulateTrajectory` provides $O(N)$ client-side points without IPC overhead.
  * *Progressive Disclosure Gating*: Row is gated by `enableCardioPowerDynamics` (and `showCardioPowerInWorkoutSummary` in summaries) and null-safe record presence.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Hydrate `cardioPowerDynamics: CardioPowerDynamicsRecord?` in `WorkoutData` and `WorkoutDetailsData` via `WorkoutDataMapper.kt`.
  2. Add `CardioPowerDynamicsRow` in `WorkoutDetails.kt` adhering strictly to §5.10 ($\text{HR@200W}$, $\tau$, $1/K$ with drift deltas and fit quality).
  3. Wire settings gating in `WorkoutSummary.kt` and `WorkoutDetailTabbedContent.kt` (`TrackOnMapScreen.kt`).
  4. Add "Fresh HR Model" toggle chip and Canvas overlay in `TelemetryMetricGraph.kt` (cyan dashed curve, shaded drift gap, calibration window tint).
  5. Propose net-new `REQ-REV-010` and `TST-REV-011` in `docs/requirements/req_aftermath_reviews.md` and `docs/tests/tst_aftermath_reviews.md`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Longitudinal season trends across multiple workouts in Athlete Profile (reserved for `ATT-3906`).
  2. Live tracking cockpit sensor metric `LIVE_HR_DRIFT` (reserved for `ATT-3907`).
  3. Modifications to database schema or identification engine math (completed in `ATT-3904`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* Net-new requirement only (`REQ-REV-010`). No existing requirements modified.

---

## 5. Architectural Strategy & High-Level Solution

* **Data Layer (`WorkoutDataMapper.kt`)**: Hydrate `cardioPowerDynamics` from `WorkoutSummariesDatabaseManager.getCardioPowerDynamics(workoutId)`.
* **Summary Row (`WorkoutDetails.kt`)**: Implement `CardioPowerDynamicsRow` using `WorkoutDomainRow` with `ic_cardio_load` domain icon and 3 `MetricItem`s: $\text{HR@200W}$ (Start $\rightarrow$ End bpm + drift), $\tau$ (Start $\rightarrow$ End s + $\Delta\tau$), and $1/K$ (W/bpm + $R^2$).
* **Overlay Layer (`TelemetryMetricGraph.kt`)**: Render `HR_fresh(t)` dashed curve (`TTColor.Zone2` / cyan) with drift fill where $\text{HR}_{\text{actual}} > \text{HR}_{\text{fresh}}$.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Design guidelines §5.10 compliance (single horizontal row, 2–3 metrics, zero chip wrapping).
  2. Non-qualifying workouts (< 20 min or missing power/HR) cleanly hide row with zero visual distortion.
  3. Zero performance regression during chart scrubbing/panning.
* **Risk Rating**: **LOW** — Pure UI presentation and Canvas drawing with zero database schema or background service impact.
