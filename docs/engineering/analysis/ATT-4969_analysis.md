# Stage 1 Analysis: ATT-4969 - Implement Guided DFA a1 Ramp/Step Test Protocol Screen for High-Confidence HRVT1 and HRVT2 Determination

**Ticket**: [ATT-4969](https://rainerblind.atlassian.net/browse/ATT-4969)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-4969` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
While ATT-3554 introduced confidence-gated post-workout threshold estimation (`HrvThresholdEstimator`), athletes currently lack a structured, in-app guided protocol to elicit monotonic aerobic-to-anaerobic transitions under controlled conditions:
1. *Unstandardized Free Progression*: Athletes attempting to determine $HRVT_1$ and $HRVT_2$ in unstructured outdoor rides experience variable cadence, coasting, and non-monotonic efforts, leading to low $R^2$ or insufficient span.
2. *Exhaustion Requirement Misconception*: Without automated completion detection, athletes assume threshold determination requires riding to maximal volitional failure, whereas DFA $\alpha_1$ threshold capture is complete as soon as $\alpha_1$ sustainably crosses below $0.45$ (surpassing $HRVT_2 = 0.50$).
3. *Sensor Quality Pre-Flight Absence*: Athletes begin tests with high-artifact or firmware-smoothed sensors, discovering unusable data only after the workout is finalized.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Underlying Architecture**:
  * `HrvThresholdEstimator.kt` supports `HrvProtocolType.GUIDED_STEP` and `GUIDED_RAMP` with Mean Response Time ($\tau_{\text{MRT}} = 45\text{ s}$) lag compensation, but no guided execution screen or protocol configuration engine exists in the app.
  * In-workout pacing currently only offers manual zone targets without step/ramp pacing overlays or sub-exhaustion early termination cues.
* **Independent Peer Review & Verification**:
  * *Per-Step Evaluation Window (`[t_end - 120s, t_end]`)*: Confirmed that for `GUIDED_STEP`, evaluating each step over the final 120s eliminates step-transition transients and guarantees zero bleed into subsequent steps.
  * *Early Sub-Exhaustion Completion*: When live $\alpha_1 < 0.45$ for $\ge 60\text{ s}$, both $HRVT_1$ ($0.75$) and $HRVT_2$ ($0.50$) have been captured, allowing immediate transition to cool-down.
  * *Pre-Flight Sensor Quality*: Integrating `HrvSignalQualityAnalyzer` and `BANALServiceRepository` ensures chest strap RR transmission is verified ($\le 3.0\%$ artifacts) before protocol start.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Implement `HrvRampTestConfig.kt` defining protocol parameters (Sport: Cycling/Running; Type: 4-min default / 3-min min `GUIDED_STEP`, gentle `GUIDED_RAMP`; starting power/pace, increment).
  * Implement `HrvRampTestViewModel.kt` managing protocol lifecycle (`CONFIG`, `PRE_FLIGHT`, `WARMUP`, `TESTING`, `EARLY_COMPLETION`, `COOLDOWN`, `FINISHED`).
  * Implement `HrvRampTestScreen.kt` in Compose with real-time target pacing, remaining time, live $\alpha_1$, live artifact %, pre-flight quality checks, and early termination alerts.
  * Register route in `NavRoutes.HRV_TEST` and mount in `ATrainingTrackerApp.kt`.
  * Maintain 100% 9-language localization parity in `strings_hrv.xml`.
  * Unit tests in `HrvRampTestViewModelTest.kt` and contract tests in `HrvRampTestScreenTest.kt`.
* **Out-of-Scope Non-Goals**:
  * Modifying post-workout estimator math in `HrvThresholdEstimator.kt` (verified in ATT-3554).
  * Automatic zone/profile updates (`ATT-3555`).
  * BLE smart trainer ERG mode FE-C actuator resistance control (informational targets only).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-TRK-072` (*Guided DFA a1 Step & Ramp Test Protocol Screen*) in `docs/requirements/req_tracking_cockpit.md`, extending `REQ-TRK-061` (*Live HRV Telemetry*) and companion to `REQ-PER-036` (*HRV Threshold Estimation*).
* **Historical Origin & Commit Trace**: Conceived in Epic `ATT-3539`; companion to `ATT-3554` (`5deec7cd`).
* **Root Reason for Existing Formulation**: Pacing was previously purely free-form or heart-rate/power zone bound.
* **Preservation of Core Invariants**:
  * Zero interference with standard background tracking in `TrackerService`.
  * Preserves `Rule 81` live host mounting and `Rule 71 & 72` theme centralization.

---

## 5. Architectural Strategy & High-Level Solution
* **Domain & ViewModel (`ui/hrv/`)**:
  * `HrvRampTestConfig`: Immutable data class capturing protocol type, starting load, step duration, increment, and warm-up.
  * `HrvRampTestViewModel`: State machine tracking active phase, target intensity, elapsed time, live $\alpha_1$, and sustained sub-0.45 auto-completion.
* **UI Presentation Layer (`ui/hrv/HrvRampTestScreen.kt`)**:
  * Pre-flight card with real-time RR artifact rate check.
  * Live target guidance gauge, step countdown timer, and early completion banner.
  * Navigation wiring via `NavRoutes.HRV_TEST` in `ATrainingTrackerApp.kt`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. No regressions in live cockpit tracking or morning HRV check.
  2. Protocol runs independently of external sensors if none connected, with clear pre-flight failure state.
  3. 100% localization parity across all 9 languages.
  4. Human Decision Gate strictly preserved at Final Review.
* **Risk Rating**: **LOW**. Standalone Compose screen and ViewModel; consumes existing `BANALServiceRepository` and `HrvThresholdEstimator` domain contracts without modifying shared database schemas.
