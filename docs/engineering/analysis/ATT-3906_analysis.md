# Stage 1 Analysis: ATT-3906 - Plot Longitudinal Season Trends for Fresh HR@200W, Time Constant tau, and Slope 1/K in Athlete Profile

**Ticket**: [ATT-3906](https://rainerblind.atlassian.net/browse/ATT-3906)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Cardio-Power Analytics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-3906`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation

First-order dynamical Power-HR parameters identified during the fresh first hour of outdoor cycling track seasonal aerobic fitness without maximal exhaustion tests:
- $\text{HR@200W}$ ($\text{HR}_0 + 200 \cdot K$) reflects submaximal aerobic economy (e.g., dropping from 152 to 131 bpm from base season to peak summer).
- Cardiorespiratory time constant $\tau_{\text{start}}$ captures Phase II $\text{VO}_2$ response kinetics (e.g., accelerating from 70s down to 35s).
- Aerobic slope $1/K$ represents marginal work per heartbeat ($\text{W/bpm}$).
Currently, `AthleteProfileScreen.kt` plots static profile metrics (Weight, HR Rest, FTP, CP, W/kg) and PMC (CTL/ATL/TSB), but lacks longitudinal tracking for dynamical cardio-power parameters. `ATT-3906` delivers a dedicated "Cardio-Power Fitness Trends" chart card in Athlete Profile.

---

## 2. Root Cause Analysis & Independent Peer Review

* **Gap Analysis**: `WorkoutSummariesDatabaseManager.java` lacks a time-series query joining `cardio_power_dynamics` with `WorkoutSummaries`. `AthleteProfileViewModel.kt` lacks state flows for cardio-power samples.
* **Independent Peer Review & Verification**:
  * *Rule 48 Compliance (`design_guidelines.md` §2.6)*: The trend chart MUST render explicit X-axis date labels (`"dd.MM."` / `"MMM yy"`) and Y-axis scale markers (`"130 bpm"`, `"35 s"`, `"5.0 W/bpm"`).
  * *Outlier-Resilient Y-Scaling (Rule 48)*: Clamp Y-axis domain via interquartile range ($\text{IQR} = Q_3 - Q_1$, $[Q_1 - 1.5\cdot\text{IQR}, Q_3 + 1.5\cdot\text{IQR}]$) when $N \ge 4$ so noisy rides do not compress the seasonal curve.
  * *Explanatory Scale Direction Notes*: Render clear direction subtitles ($\text{HR@200W}$: *"Lower = Better Aerobic Economy"*; $\tau$: *"Lower = Faster HR Kinetics"*; $1/K$: *"Higher = More Watts per bpm"*).
  * *Parallel Sprint Isolation Invariant*: Restrict UI changes strictly to `AthleteProfileScreen.kt` / `AthleteProfileViewModel.kt`. Do NOT touch `PeriodsScreen.kt` (ensuring 0% file conflict with concurrent ticket `ATT-3556`).
  * *Quality Qualification Filter*: Only include workouts with model fit $R^2 \ge 0.50$.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Add `getCardioPowerDynamicsFitnessTrendSamples(minR2 = 0.50)` in `WorkoutSummariesDatabaseManager.java`.
  2. Expose `cardioPowerTrendSamples`, `selectedCardioTrendMetric`, and `selectedCardioTimeRange` in `AthleteProfileViewModel.kt` gated by `enableCardioPowerDynamics`.
  3. Implement `CardioPowerTrendsCard` in `AthleteProfileScreen.kt` with metric selector pills ($\text{HR@200W}$, $\tau$, $1/K$), time range selector pills (3M, 6M, 1Y, All), scatter points, rolling trend curve, and info button opening `PowerHrDynamicsInfoDialog`.
  4. Author 9-locale strings in `strings_cardio_power.xml` (zero inline emojis).
  5. Propose net-new `REQ-REV-011` and `TST-REV-012` in `docs/requirements/req_aftermath_reviews.md` and `docs/tests/tst_aftermath_reviews.md`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. `PeriodsScreen.kt` modifications (strictly isolated to preserve `ATT-3556`).
  2. Live tracking sensor telemetry (reserved for `ATT-3907`).
  3. Re-computation or mutation of persisted dynamical records.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Net-New Requirement Only (`REQ-REV-011`)**: Adds longitudinal visualization in Athlete Profile without modifying existing `REQ-UI-391` (Profile Metrics) or `REQ-UI-401` (PMC).

---

## 5. Architectural Strategy & High-Level Solution

* **Data Layer**: Inner join query in `WorkoutSummariesDatabaseManager.java` returning `CardioPowerTrendSample` sorted by `start_ms ASC`.
* **Presentation Layer**: `CardioPowerTrendsCard` composable in `AthleteProfileScreen.kt` with Canvas rendering scatter points, 30-day rolling trend line, Rule 48 scale labels, and IQR domain bounds.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**: Rule 48 explicit labels & IQR clamping; $R^2 \ge 0.50$ filter; 0% file overlap with `PeriodsScreen.kt`; 100% 9-locale parity in `strings_cardio_power.xml`.
* **Risk Rating**: **LOW** — Read-only SQL query and additive Compose Canvas card with zero database migrations or background service changes.
