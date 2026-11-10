# Stage 1 Analysis: ATT-3544 - Interactive DFA a1 / rMSSD Charts & alpha1 Scatter Plot

**Ticket**: [ATT-3544](https://rainerblind.atlassian.net/browse/ATT-3544)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3544`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  
**Requirement Mapping**: `REQ-PER-035` (`docs/requirements/req_aftermath_periods.md`)  
**Test Mapping**: `TST-PER-043` (`docs/tests/tst_aftermath_periods.md`)  

---

## 1. Problem Statement & Motivation
* **Context**: Following ATT-3543, lossless beat-to-beat RR intervals are persisted in `WorkoutRr.db` and centered-window HRV metrics (`HRV_DFA_A1`, `HRV_RMSSD`, `HRV_ARTIFACT_RATE`) are recomputed into `WorkoutSamples.db`.
* **Gap**: Workout Details currently lacks visualization for these centered HRV streams. In particular, drawing naive continuous lines across high-artifact intervals ($> 5\%$) falsely suggests valid physiological data during sensor dropouts, and athletes cannot inspect how $\alpha_1$ scales against Heart Rate and Cycling Power to identify individual aerobic thresholds ($\text{HRVT1} \approx 0.75$, $\text{HRVT2} \approx 0.50$).
* **Objective**: Render synchronized interactive time-series charts for DFA $\alpha_1$ (with dashed reference lines and artifact-gap hatching) and $\text{rMSSD}$ in `MapDetailLayout`, and provide an $\alpha_1$ vs. Power/HR scatter plot (`HrvAlpha1ScatterChart.kt`) in Workout Details with graceful omission when HRV data is absent.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**: Telemetry metric visualizers (`TelemetryMetricGraph.kt`) and `PathPoint` encapsulate Speed, Pace, HR, Power, and $W'\text{bal}$, but lack integration for HRV series and artifact rate quality gates.
* **Independent Peer Review & Verification (ATT-3534)**:
  * *Artifact-Gap Rendering*: Polyline segments must break where `HRV_DFA_A1` is null or `HRV_ARTIFACT_RATE > 5.0%`. Muted background hatching must visually delineate artifact suppression zones rather than interpolating across dropouts.
  * *Stacked Chart Horizontal Alignment (Rule 64)*: Time-series HRV charts must share exact horizontal plot bounds (`startPadding = 50.dp` and `0.72f` landscape boundary) to align vertically with Elevation, Speed, HR, and Power charts.
  * *Axis Scaling & Domain Clamping (Rule 48)*: Explicit X/Y scale markers on Canvas; clamp $\alpha_1$ to $[0.2, 1.4]$ and $\text{rMSSD}$ to $[0, 150\text{ ms}]$ with outlier resilience.
  * *Architecture Conformance*: UI classes in `ui/**` must query data strictly through `WorkoutRepository` / `WorkoutRrRepository`, never directly referencing `*DatabaseManager` or `*DbHelper` (`ArchitectureConformanceContractTest.kt`, `ATT-6182`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * Extend `PathPoint` with `@JvmOverloads` constructor and `hrvDfaA1`, `hrvRmssd`, `hrvArtifactRate` fields.
  * Extend `TelemetryMetricType` with `HRV_DFA_A1` and `HRV_RMSSD` in `TelemetryMetricGraph.kt` with reference thresholds ($\alpha_{1,\text{T1}} = 0.75, \alpha_{1,\text{T2}} = 0.50$) and artifact-gap visual hatching.
  * Implement `HrvAlpha1ScatterChart.kt` / `HrvAlpha1ScatterCard.kt` plotting $(\text{HR/Power}, \alpha_1)$ over the active transition domain $\alpha_1 \in [0.35, 1.15]$ with linear regression overlay.
  * Integrate into `MapDetailLayout.kt` and `WorkoutDetailTabbedContent.kt` with graceful omission for non-HRV workouts.
  * 9-language localization parity in `strings_hrv.xml`.
* **Out-of-Scope**: Guided ramp testing protocol (`ATT-4969`), automated anchor-based FTP zone recalculation (`ATT-3555`), longitudinal morning readiness trends (`ATT-3557`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022, Rule 68)
* **Status**: Net-new requirement (`REQ-PER-035`) and test specification (`TST-PER-043`). No existing requirements modified.
* **Side-Effect Audit (`Rule 68`)**:
  * Workouts without HRV data hide HRV chart tracks completely with zero layout shifts or performance overhead.
  * Composition state stability enforced: zero snapshot state writes during composition (Rule 66).

---

## 5. Architectural Strategy & High-Level Solution
* **Data Layer**: `WorkoutRepository` queries `HRV_DFA_A1`, `HRV_RMSSD`, and `HRV_ARTIFACT_RATE` into `PathPoint`.
* **UI Visualization**: `TelemetryMetricGraph` handles time-series tracks; `HrvAlpha1ScatterChart` handles intensity scatter plots.
* **Touch & Scrubbing**: `ChartGestureDisambiguator` ensures horizontal scrubbing never locks parent vertical scrolling.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Horizontal plot alignment parity (`startPadding = 50.dp`) per Rule 64.
  2. Architecture conformance: UI isolated from raw SQLite helpers via Repository layer.
  3. Strict graceful omission when zero HRV trackpoints are present.
* **Risk Rating**: **MEDIUM** (3 SP) — Compose Canvas path hatching, regression line fitting, and multi-chart gesture synchronization.
