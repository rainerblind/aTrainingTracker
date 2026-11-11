# Stage 1 Analysis: ATT-3554 - Calculate Lag-Aligned, Confidence-Gated HRVT1 and HRVT2 Thresholds via DFA a1 Regression and Display Summary Card

**Ticket**: [ATT-3554](https://rainerblind.atlassian.net/browse/ATT-3554)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-3554` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
In *aTrainingTracker*, post-workout DFA $\alpha_1$ analysis currently provides an unconstrained Ordinary Least Squares (OLS) regression line and threshold crosshairs in `HrvAlpha1ScatterCard.kt` (`HrvScatterMath.calculateLinearRegression`). However, naive session-wide linear regression exhibits four major physiological and statistical defects:
1. *No Lag Alignment / Fresh-Window Bounding*: Evaluates all points over the entire session duration—including the initial 10-minute warm-up, non-steady transitions, paused periods, high-artifact segments ($>5.0\%$), and late-ride autonomic fatigue/drift ($>60\text{ min}$ active time), biasing threshold estimates downward.
2. *Coupled & Extrapolated Thresholds*: Lacks independent span gating. In tempo rides where $\alpha_1$ descends from $1.05$ to $0.60$ (cleanly crossing $HRVT_1 = 0.75$), current code extrapolates an imaginary $HRVT_2$ outside the observed data range.
3. *Serial Correlation Inflation on Dense Telemetry*: Regressing against raw 5-second points inflates sample size ($N \approx 720\text{/h}$) due to serial autocorrelation, artificially inflating $R^2$ while failing to evaluate genuine steady-state transition stability.
4. *Absence of Quality & Confidence Governance*: Lacks structured confidence gating (`HIGH`, `MEDIUM`, `LOW` / `INSUFFICIENT_SPAN`), diagnostic suppression reasons (e.g. steady endurance, high artifacts, insufficient duration), and protocol provenance badges.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Underlying Architecture**:
  * `HrvScatterMath.filterScatterPoints` in `HrvAlpha1ScatterChart.kt` filters points on $\alpha_1 \in [0.35, 1.15]$ and artifact rate $\le 5.0\%$ with simple 5-second decimation.
  * `HrvScatterMath.calculateLinearRegression` computes slope and intercept across the entire workout without window restrictions or span checks.
  * In `HrvAlpha1ScatterCard.kt`, $HRVT_1$ and $HRVT_2$ are displayed whenever calculated intensities fall within $[30, 600]$ (bpm/W), allowing out-of-span extrapolation.
* **Independent Peer Review & Verification**:
  * *Fresh Window Invariant (`10–60 min`)*: Confirmed that in `PathPoint`, `timeSec` captures active session elapsed time (`WorkoutRepository.kt` line 1653). Restricting regression to $t \in [600, 3600]\text{ s}$ eliminates warm-up noise and prevents hour 2–3 cardiac fatigue drift from corrupting aerobic thresholds.
  * *Decimated Grid ($N_{\text{eff}} = \lfloor K / 12 \rfloor$ or 60s bins)*: Confirmed that decimating 5s samples into 60s window averages (or 1 point per step for guided steps) breaks serial autocorrelation and satisfies statistical regression assumptions.
  * *Independent Span Gating*: For $HRVT_1$ ($\alpha_{1,\text{T1}} = 0.75$), requiring $\min \alpha_1 \le 0.65$ and $\max \alpha_1 \ge 0.85$; for $HRVT_2$ ($\alpha_{1,\text{T2}} = 0.50$), requiring $\min \alpha_1 \le 0.45$ and $\max \alpha_1 \ge 0.60$. When $HRVT_2$ span is unmet, $HRVT_1$ is emitted independently and $HRVT_2$ is assigned `null` (`INSUFFICIENT_SPAN`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Implement `HrvThresholdEstimator.kt` with fresh-window filtering ($10\text{--}60\text{ min}$), independent span gating, decimated 60s grid regression, and confidence categorization (`HIGH`, `MEDIUM`, `LOW` / `INSUFFICIENT_SPAN`).
  * Support protocol provenance: `FREE_WORKOUT`, `GUIDED_STEP`, and `GUIDED_RAMP` with Mean Response Time ($\tau_{\text{MRT}} = 45\text{ s}$) lag compensation.
  * Implement `HrvThresholdSummaryCard.kt` in Compose displaying estimated $HRVT_1$/$HRVT_2$ (HR, Power, Pace), confidence badge, provenance badge, $R^2$, $N_{\text{eff}}$, artifact rate, and diagnostic feedback.
  * Wire `HrvThresholdSummaryCard` into `WorkoutDetailTabbedContent.kt` under `WorkoutDetailSectionType.ZONES`.
  * Maintain 100% 9-language localization parity in `strings_hrv.xml`.
  * Comprehensive unit tests in `HrvThresholdEstimatorTest.kt` and Compose tests in `HrvThresholdSummaryCardTest.kt`.
* **Out-of-Scope Non-Goals**:
  * Live cockpit pacing UI (`ATT-3542`).
  * Interactive guided ramp test protocol screen (`ATT-4969`).
  * Automatic athlete profile FTP/zone updates (`ATT-3555`).
  * Autonomic durability / multi-hour drift modeling (`ATT-4970`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PER-036` (*Confidence-Gated HRVT1/HRVT2 Threshold Estimation & Summary Card*) in `docs/requirements/req_aftermath_periods.md`, extending `REQ-PER-035` (*Post-Workout DFA Alpha 1 Visualization & Scatter Plot*).
* **Historical Origin & Commit Trace**: Commit `03f90ff8` (`ATT-3544`) and `83e5f529` (`ATT-6717`) created `HrvAlpha1ScatterCard`.
* **Root Reason for Existing Formulation**: Naive linear regression was initially introduced as a visual aid on the scatter chart without formal validation criteria.
* **Preservation of Core Invariants**:
  * *Visual Scatter Crosshairs*: `HrvAlpha1ScatterCard` continues to display the full-session scatter points and visual guide lines without disruption.
  * *Threshold Isolation*: If span or quality criteria are not met, thresholds are safely omitted (`--`) rather than displaying invalid extrapolated estimates.

---

## 5. Architectural Strategy & High-Level Solution
* **Domain Engine (`HrvThresholdEstimator.kt`)**:
  * Input: `List<PathPoint>`, intensity mode (`HEART_RATE`, `POWER`, `PACE`), protocol type, configured thresholds ($T_1 = 0.75$, $T_2 = 0.50$).
  * Window Selection: `timeSec in 600..3600`, artifact $\le 5.0\%$, valid intensity & $\alpha_1 \in [0.35, 1.15]$.
  * Decimation: Group into 60s intervals (or discrete steps for `GUIDED_STEP`), computing mean intensity, mean $\alpha_1$, and mean artifact rate per decimated bin.
  * Regression & Gating: Calculate OLS regression on decimated points. Evaluate separate span gates for $HRVT_1$ and $HRVT_2$.
  * Classification: Assign `HIGH`, `MEDIUM`, or `LOW` / `INSUFFICIENT_SPAN` with localized diagnostic reasons.
* **Presentation Layer (`HrvThresholdSummaryCard.kt`)**:
  * Material 3 `ElevatedCard` placed in `WorkoutDetailTabbedContent.kt` (under `WorkoutDetailSectionType.ZONES`).
  * Renders threshold metrics, confidence chip, protocol badge, and diagnostic text.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regressions in existing `HrvAlpha1ScatterChartTest`, `HrvAlpha1ScatterCardTest`, or `WorkoutDetailTabbedContentTest`.
  2. No extrapolation: Thresholds outside the observed span must return `null`.
  3. Decimated grid regression ensures statistical decorrelation and rigorous $R^2$ validation.
  4. Localization parity across all 9 languages (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  5. Human Decision Gate remains strictly enforced at Final Review.
* **Risk Rating**: **LOW**. Pure mathematical evaluation engine and isolated Compose UI card with no schema migrations or background thread mutations.
