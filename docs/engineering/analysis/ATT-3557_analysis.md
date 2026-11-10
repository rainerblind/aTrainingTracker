# Stage 1 Analysis: ATT-3557 - Render Longitudinal HRV Readiness Trend in Periods Screen

**Ticket**: [ATT-3557](https://rainerblind.atlassian.net/browse/ATT-3557)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3557`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  
**Requirement Mapping**: `REQ-PER-033` (`docs/requirements/req_aftermath_periods.md`)  
**Test Mapping**: `TST-PER-041` (`docs/tests/tst_aftermath_periods.md`)  

---

## 1. Problem Statement & Motivation
* **Context**: Once morning resting HRV entries are persisted (`HrvReadinessRepository`, `ATT-3545`, `ATT-3556`), athletes require longitudinal autonomic recovery insights across weeks and months.
* **Gap**: `PeriodsScreen` currently aggregates workout volume, distance, and routes, but lacks autonomic readiness trends, preventing athletes from visualizing physiological fatigue alongside period summaries.
* **Objective**: Render an interactive longitudinal HRV readiness trend card (`HrvReadinessTrendCard`) in `PeriodsScreen`, featuring daily resting $\ln(\text{rMSSD})$ / $\text{rMSSD}$ scatter points, 7-day rolling average ($\mu_{7d}$), shaded 60-day Smallest Worthwhile Change (SWC) corridor ($[\mu_{60d} \pm 0.5\sigma_{60d}]$), posture isolation chips, and optional training-load context overlay (*Plews et al. 2013*).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**: `PeriodsViewModel` previously coordinated only workout data and period rollups without observing `HrvReadinessRepository`.
* **Independent Peer Review & Verification (ATT-3534)**:
  * *Posture Isolation*: Postures (`SUPINE`, `SEATED`, `STANDING`) must remain strictly segregated; blending postures skews SWC normal baselines due to orthostatic vagal withdrawal. Posture chip defaults to the posture with the most readings.
  * *Daily Deduplication & Quality Gating*: Select primary entry (`is_primary_for_day = 1`, then earliest timestamp) per date; exclude entries where `artifact_rate_pct > 5.0%`.
  * *Rolling Baseline Math*: Continuous 7-day rolling mean $\mu_{7d}$ ($N_{7d} \ge 3$) and 60-day SWC corridor $[\mu_{60d} \pm 0.5\sigma_{60d}]$ ($N_{60d} \ge 14$, Bessel's correction $N-1$).
  * *Gesture Disambiguation (`design_guidelines.md` §2.2)*: Employ `ChartGestureDisambiguator` (`isDominantHorizontal` vs `isDominantVertical`) so horizontal date scrubbing never traps vertical parent scrolling.
  * *Axis Scaling & Gutter Labels (Rule 48 — `ATT-4287`)*: Render explicit X-axis date labels and Y-axis scale markers on `Canvas` with outlier-resilient Y-scaling.
  * *Training Load Context*: Optional synchronized daily training load (TSS/ATL) background bars on the shared time axis.
  * *Graceful Empty Omission*: When zero HRV entries exist, omit the card entirely to avoid UI clutter.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * `HrvReadinessTrendMath.kt`: Pure Kotlin model and math engine (deduplication, rolling 7d, 60d SWC, Y-scaling, touch-point detection).
  * `HrvReadinessTrendCard.kt`: Compose card with posture selector chips, metric toggle, training load overlay, Canvas plot, and scrubbing tooltips.
  * Integration into `PeriodsViewModel.kt` and `PeriodsTabsScreen.kt`.
  * 9-language strings in `app/src/main/res/values*/strings_hrv.xml`.
  * Unit tests in `HrvReadinessTrendMathTest` and `PeriodsViewModelTest`.
* **Out-of-Scope**: Modifying `HrvReadiness.db` schema; Health Connect sync (`ATT-4981`); live DFA $\alpha_1$ pacing (`ATT-3542`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022, Rule 68)
* **Status**: Net-new requirement (`REQ-PER-033`) and test specification (`TST-PER-041`). No existing requirements modified.
* **Side-Effect Audit (`Rule 68`)**:
  * `PeriodsViewModel`: Adding `HrvReadinessRepository` observation is strictly additive; existing period tabs, sport filters, and map transitions remain untouched.
  * Layout safety: Empty state check ensures athletes without HRV data see identical UI with zero performance overhead.

---

## 5. Architectural Strategy & High-Level Solution
* **Data Flow**: `HrvReadinessRepository.getAllEntries()` $\rightarrow$ `PeriodsViewModel` $\rightarrow$ `HrvReadinessTrendMath.buildSeries(...)` $\rightarrow$ `HrvReadinessTrendCard`.
* **Gesture Handling**: `pointerInput` scrubbing governed by `ChartGestureDisambiguator`.
* **Overlay Layering**: Daily training load bars rendered as semi-transparent backdrop behind the 60-day SWC corridor and 7-day rolling line.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression in existing `PeriodsScreen` navigation, tabs, or sport filtering.
  2. Strict posture segregation: SWC baselines never mix readings across postures.
  3. Clean empty state: Card is completely invisible if no HRV readings exist.
* **Risk Rating**: **LOW** (2 SP) — Modular Compose UI and pure calculation engine consuming existing repositories.
