# Stage 1 Analysis: ATT-4970 - Model DFA a1 Autonomic Durability and Drift Across Multi-Hour Workouts

**Ticket**: [ATT-4970](https://rainerblind.atlassian.net/browse/ATT-4970)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-4970` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
During long endurance workouts ($\ge 90\text{ min}$), `DFA a1` progressively declines at a given external workload (power or pace) due to autonomic fatigue, vagal withdrawal, and cardiovascular drift (*Rogers et al. 2021; Gronwald et al. 2021*). While downward drift confounds naive threshold regression on long sessions (`ATT-3554`), **quantifying the rate of `DFA a1` decline at matched intensity is a high-value endurance durability metric**—complementing the stroke-volume Heart Efficiency drift ($\Delta C_{\text{O}_2}\%$) in companion ticket `ATT-4842` from an autonomic nervous system perspective.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Underlying Architecture**:
  * Centered-window HRV telemetry (`SensorType.HRV_DFA_A1`, `SensorType.HRV_ARTIFACT_RATE`) is already computed and persisted into `WorkoutSamples.db` trackpoints by `HrvPostWorkoutRecomputer.kt`.
  * `HrvThresholdEstimator.kt` truncates evaluation at $t = 3600\text{ s}$ (`FRESH_WINDOW_MAX_SEC`) specifically to avoid late-workout autonomic drift, but no component currently analyzes the late segment ($> 60\text{ min}$) or measures durability drift against the fresh baseline (`15–45 min`).
* **Independent Hypothesis Verification (4-Augen-Prinzip)**:
  * *Iso-Intensity Binning vs. Linear Regression*: Confirmed hypothesis that on steady Zone 2 rides, narrow power variance causes regression to collapse. Determining median reference intensity $P_{\text{ref}}$ (or speed $v_{\text{ref}}$) over the fresh baseline (`15–45 min`) and selecting $\pm 10\%$ iso-intensity windows ($[0.90 P_{\text{ref}}, 1.10 P_{\text{ref}}]$) provides robust, noise-resilient comparison.
  * *Autonomic Efficiency Normalization*: Normalizing minor power variations within the $\pm 10\%$ band via $\alpha_{1,\text{norm}} = \alpha_1 / (P / P_{\text{ref}})$ prevents pacing fluctuations from biasing drift.
  * *Minimum Data Guard*: Requiring at least 10 min (600s / 120 5s points) of matched-intensity data in both `15–45 min` and `> 60 min` slices prevents artifact-prone conclusions on short or interval-heavy rides.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Objectives**:
  * Implement `HrvDurabilityCalculator.kt` with iso-intensity matched-power/pace binning, 15-minute slice trajectories (`15–30m`, `30–45m`, `45–60m`, ...), and autonomic drift percentage ($\Delta\alpha_{1,\%}$).
  * Add `ExtremaType` enum entries (`DFA_A1_FRESH_BASELINE`, `DFA_A1_LATE_MEAN`, `DFA_A1_DRIFT_PCT`) to persist summary metrics into `WorkoutSummaries.TABLE_EXTREMA_VALUES` without schema migration.
  * Extend `HrvPostWorkoutRecomputer.kt` to trigger durability calculation and persist extrema during post-workout processing.
  * Expose `dfaA1FreshBaseline`, `dfaA1LateMean`, and `dfaA1DurabilityDriftPct` on `WorkoutData` with mapping in `WorkoutDataMapper.kt`.
  * Create `HrvDurabilityCard.kt` and integrate into `WorkoutDetailTabbedContent.kt` alongside `HrvThresholdSummaryCard`.
  * Maintain 100% 9-language localization parity in `strings_hrv.xml`.
* **Out-of-Scope Non-Goals**:
  * Live in-workout durability alerting (post-workout analysis only).
  * Automated threshold recalculation during fatigue (handled separately by `ATT-3554`).
  * Metabolic simulation models (scoped to `ATT-4842` / `ATT-4786`).
* **Complexity Split Guardrail**: Assessed at **2 SP** (Standard). Clean algorithmic calculation, zero SQLite table migrations, and standard Compose card rendering.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PER-038` (*Model DFA a1 Autonomic Durability, Drift & Iso-Intensity Bins Across Multi-Hour Workouts*) in `docs/requirements/req_aftermath_periods.md`, companion test `TST-PER-046` in `docs/tests/tst_aftermath_periods.md`.
* **Historical Origin & Commit Trace**: Conceived in Epic `ATT-3539` review (`ATT-4966`, `ATT-4975` Finding 7); paired with `ATT-4842` (`HeartEfficiencyCalculator.kt`).
* **Root Reason for Existing Formulation**: `HrvThresholdEstimator` discarded data after 60 min (`FRESH_WINDOW_MAX_SEC = 3600L`) to protect threshold regression accuracy, leaving late fatigue unmodeled.
* **Preservation of Core Invariants**: Existing threshold estimator bounds, live tracking, and `WorkoutSamples` schemas remain 100% unchanged.

---

## 5. Architectural Strategy & High-Level Solution
* **Algorithm (`HrvDurabilityCalculator.kt`)**:
  * Fresh baseline window: $t \in [900\text{ s}, 2700\text{ s}]$ (`15–45 min`).
  * Reference intensity: Median power $P_{\text{ref}}$ (or speed $v_{\text{ref}}$) over fresh baseline.
  * Matched filter: Windows with artifact rate $\le 5.0\%$ and intensity $\in [0.90 P_{\text{ref}}, 1.10 P_{\text{ref}}]$.
  * Durability drift: $\Delta\alpha_{1,\%} = \frac{\overline{\alpha}_{1,\text{late,norm}} - \overline{\alpha}_{1,\text{fresh,norm}}}{\overline{\alpha}_{1,\text{fresh,norm}}} \times 100\%$ where late window is $t > 3600\text{ s}$.
* **Data Flow & UI**:
  * `HrvPostWorkoutRecomputer` $\to$ `WorkoutSummariesDatabaseManager.updateExtremaValue` $\to$ `WorkoutDataMapper` $\to$ `WorkoutData` $\to$ `HrvDurabilityCard` in `WorkoutDetailTabbedContent`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. No database schema migration required (`ExtremumValues` table dynamically stores new `ExtremaType` enum keys).
  2. Fallback to `INSUFFICIENT_MATCHED_DATA` when $< 10\text{ min}$ of matched data exists.
  3. 100% 9-language localization parity in `strings_hrv.xml`.
  4. Human Decision Gate preserved at Final Review (Rainer Blind).
* **Risk Rating**: **LOW**. Modular calculator with isolated persistence and standard Compose presentation.
