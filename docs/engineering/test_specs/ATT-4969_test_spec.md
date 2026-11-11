# Stage 2: Requirement & Test Specification - ATT-4969: Implement Guided DFA a1 Ramp/Step Test Protocol Screen for High-Confidence HRVT1 and HRVT2 Determination

**Ticket**: [ATT-4969](https://rainerblind.atlassian.net/browse/ATT-4969)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-072` (*Guided DFA a1 Step & Ramp Test Protocol Screen*)  
**Test Spec ID**: `TST-TRK-072` (*Guided DFA a1 Step & Ramp Test Protocol Suite*)  
**Branch**: `feature/ATT-4969` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-TRK-072)

### 1.1 Problem Statement & Rationale
Unstructured, free-progression workouts frequently suffer from pacing surges, cadence shifts, and non-monotonic efforts that undermine linear regression $R^2$ and trigger span failures in `HrvThresholdEstimator` (`REQ-PER-036`). Furthermore, athletes often mistakenly believe threshold testing requires riding to maximal exhaustion. `REQ-TRK-072` provides a guided protocol screen with pre-flight sensor checks, live target guidance, zero-bleed step window tagging, and automated sub-exhaustion early completion when $\alpha_1 < 0.45$.

### 1.2 Functional & Architectural Requirements
1. **Protocol Configuration & Presets (`REQ-TRK-072.1`)**: Support Cycling (`BIKE_POWER`, Watts) and Running (`RUNNING`, speed/pace). Support Progressive Steps (`GUIDED_STEP`, default 4-min steps for complete cardiodynamic settling, 3-min minimum option; starting load e.g. 100 W or 45% FTP, +15 W / +20 W increment) and Continuous Ramp (`GUIDED_RAMP`, slope $+10$ or $+15\text{ W/min}$, tagging $\tau_{\text{MRT}} = 45\text{ s}$). Configurable 10-minute steady warm-up.
2. **Per-Step Evaluation Window & Zero Bleed (`REQ-TRK-072.2`)**: For `GUIDED_STEP`, each completed step $j$ ending at $t_{\text{end},j}$ contributes exactly one $(\overline{P}_j, \overline{\text{HR}}_j, \alpha_{1,j})$ point evaluated strictly over $[t_{\text{end},j} - 120\text{ s}, t_{\text{end},j}]$. Never evaluate past $t_{\text{end},j} - 60\text{ s}$ for centered windows to prevent step $j+1$ bleed.
3. **Pre-Flight Sensor Quality Check (`REQ-TRK-072.3`)**: Verify beat-to-beat RR streaming and grade signal quality: High ($\le 3.0\%$ artifacts), Moderate ($3.0\text{--}5.0\%$, warning of Medium confidence cap), or Unsuitable ($> 5.0\%$, smoothed stream, or optical sensor; start blocked with diagnostics).
4. **In-Workout Telemetry Guidance (`REQ-TRK-072.4`)**: Render current phase (`WARMUP`, `STEP`, `RAMP`, `COOLDOWN`), target intensity (Watts / Pace), step countdown timer ($MM:SS$), live DFA $\alpha_1$, live artifact %, and current HR.
5. **Early Sub-Exhaustion Completion (`REQ-TRK-072.5`)**: When live $\alpha_1 < 0.45$ sustainably for $\ge 60\text{ s}$ during test phase (traversing both $HRVT_1 = 0.75$ and $HRVT_2 = 0.50$), notify athlete both thresholds are captured and transition to cool-down without riding to exhaustion.
6. **Session Tagging & Decimated Grid Governance (`REQ-TRK-072.6`)**: Tag workout with `hrvTestProtocol = GUIDED_STEP` or `GUIDED_RAMP` plus metadata (`stepDurationSec`, `rampSlopeWattsPerMin`), instructing `HrvThresholdEstimator` to restrict regression strictly to post-warmup test window and enforce decimated grid quality gates.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Guided Ramp Progression & Early Sub-Exhaustion Completion)**:
  * *Given* an athlete conducting a guided step test (100 W start, +15 W / 4 min steps) with an ECG chest strap,
  * *When* live $\alpha_1$ traverses $0.75$ down to $< 0.45$ for $\ge 60\text{ s}$,
  * *Then* the screen guides target power at each step and alerts the athlete that thresholds are captured, enabling immediate cool-down.
* **Criterion 2 (Pre-Flight Sensor Check & RR Stream Gating)**:
  * *Given* an athlete with connected heart rate hardware,
  * *When* initiating pre-flight check,
  * *Then* artifact rate $\le 3.0\%$ passes with High Quality, $3.0\text{--}5.0\%$ issues a warning, and $> 5.0\%$ or missing RR blocks start.
* **Criterion 3 (Step Window Evaluation & Zero Bleed)**:
  * *Given* a completed `GUIDED_STEP` session,
  * *When* evaluated by `HrvThresholdEstimator`,
  * *Then* each step is evaluated strictly over $[t_{\text{end},j} - 120\text{ s}, t_{\text{end},j}]$ with zero bleed into adjacent steps.

### 1.4 System Invariants
- Zero background service sampling regressions in `TrackerService`.
- Zero SQLite schema modifications.
- Decimated grid decorrelation ($R^2 \ge 0.70$, artifact rate $\le 3.0\%$) strictly enforced for `Confidence.HIGH`.
- 100% localization parity across all 9 canonical languages (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 2. Test Specification (TST-TRK-072)

### Test Case 1: Protocol Configuration & State Transitions (`TST-TRK-072.1`)
* **Scope**: ViewModel Unit Test (`HrvRampTestViewModelTest.kt`)
* **Preconditions**: Initialized `HrvRampTestViewModel`.
* **Action**: Configure Cycling/Running presets, step durations (4-min default / 3-min min), step increments, and transition through `CONFIG` -> `PRE_FLIGHT` -> `WARMUP` -> `TESTING` -> `COOLDOWN` -> `FINISHED`.
* **Expected Result**: State machine transitions cleanly; protocol parameters are preserved.

### Test Case 2: Pre-Flight Sensor Quality & RR Stream Check (`TST-TRK-072.2`)
* **Scope**: ViewModel Unit Test (`HrvRampTestViewModelTest.kt`)
* **Preconditions**: Stream mock RR telemetry with varying artifact rates and streaming states.
* **Action**: Run `checkPreFlightSensor()`.
* **Expected Result**: High status for $\le 3.0\%$ artifacts, Warning for $3.0\text{--}5.0\%$, and Fail with blocked start for $> 5.0\%$ or missing RR.

### Test Case 3: Early Sub-Exhaustion Auto-Completion Detection (`TST-TRK-072.3`)
* **Scope**: ViewModel Unit Test (`HrvRampTestViewModelTest.kt`)
* **Preconditions**: Active test phase with live telemetry.
* **Action**: Inject $\alpha_1 < 0.45$ for $60\text{ s}$; test transient drop $< 60\text{ s}$.
* **Expected Result**: Sustained $\ge 60\text{ s}$ triggers early completion alert; transient drop does not trigger prematurely.

### Test Case 4: Per-Step Evaluation Window & Zero Bleed Contract (`TST-TRK-072.4`)
* **Scope**: Unit Test (`HrvRampTestViewModelTest.kt` / `HrvThresholdEstimatorTest.kt`)
* **Preconditions**: Completed multi-step workout.
* **Action**: Pass tagged session to `HrvThresholdEstimator`.
* **Expected Result**: Step data evaluated strictly on $[t_{\text{end},j} - 120\text{ s}, t_{\text{end},j}]$; zero contamination from subsequent steps.

### Test Case 5: Compose UI Screen & Pacing Guidance Contract (`TST-TRK-072.5`)
* **Scope**: Compose UI Test (`HrvRampTestScreenTest.kt`)
* **Preconditions**: Render `HrvRampTestScreen` in testing and completion states.
* **Action**: Inspect node tree.
* **Expected Result**: Target power/pace gauge, step countdown timer, live $\alpha_1$, live artifact %, and completion dialog are rendered properly.

### Test Case 6: 9-Language Localization Parity (`TST-TRK-072.6`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Preconditions**: 36 new string keys in `strings_hrv.xml`.
* **Action**: Run `TranslationParityTest`.
* **Expected Result**: 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Test Case 7: Clean-Room Targeted Test Suite Execution (`TST-TRK-072.7`)
* **Scope**: Clean-room Unit Regression
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.hrv.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-072.1` | Unit | `HrvRampTestViewModel.kt` | `REQ-TRK-072.1` | Specified |
| `TST-TRK-072.2` | Unit | `HrvRampTestViewModel.kt` | `REQ-TRK-072.3` | Specified |
| `TST-TRK-072.3` | Unit | `HrvRampTestViewModel.kt` | `REQ-TRK-072.5` | Specified |
| `TST-TRK-072.4` | Unit | `HrvRampTestViewModel.kt` / `HrvThresholdEstimator.kt` | `REQ-TRK-072.2`, `REQ-TRK-072.6` | Specified |
| `TST-TRK-072.5` | UI | `HrvRampTestScreen.kt` | `REQ-TRK-072.4` | Specified |
| `TST-TRK-072.6` | Parity | `TranslationParityTest.kt` | `REQ-TRK-072.1`..`REQ-TRK-072.6` | Specified |
| `TST-TRK-072.7` | Regression | Full Targeted Suite | `REQ-TRK-072` | Specified |
