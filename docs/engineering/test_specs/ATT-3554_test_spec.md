# Stage 2: Requirement & Test Specification - ATT-3554: Calculate Lag-Aligned, Confidence-Gated HRVT1 and HRVT2 Thresholds via DFA a1 Regression and Display Summary Card

**Ticket**: [ATT-3554](https://rainerblind.atlassian.net/browse/ATT-3554)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PER-036` (*Confidence-Gated HRVT1/HRVT2 Threshold Estimation & Summary Card*)  
**Test Spec ID**: `TST-PER-044` (*Confidence-Gated HRVT1/HRVT2 Threshold Estimation & Summary Card Verification Suite*)  
**Branch**: `feature/ATT-3554` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PER-036)

### 1.1 Problem Statement & Rationale
Session-wide OLS regression on raw 5s DFA $\alpha_1$ samples suffers from warm-up bias, late-ride cardiac drift, coupled/extrapolated thresholds, and serial autocorrelation. `REQ-PER-036` introduces lag-aligned, confidence-gated threshold estimation and an interactive Summary Card in Workout Details.

### 1.2 Functional & Architectural Requirements
1. **Fresh-Window Selection (`REQ-PER-036.1`)**: Restrict free-progression regression to active elapsed time $t \in [600, 3600]\text{ s}$ (`10–60 min`), excluding the initial 10-minute warm-up, pauses, windows with artifact rate $> 5.0\%$, and fatigue drift $> 60\text{ min}$. For guided tests (`ATT-4969`), evaluate the post-warmup monotonic test stage.
2. **Decimated Grid Regression (`REQ-PER-036.2`)**: Compute regression and $R^2$ on decimated 60-second window averages ($N_{\text{eff}} = \lfloor K / 12 \rfloor$) or discrete steps (`GUIDED_STEP`). For continuous ramps, subtract Mean Response Time lag compensation $\tau_{\text{MRT}} = 45\text{ s}$.
3. **Independent Span Gating (`REQ-PER-036.3`)**: Require decimated samples spanning $[0.65, 0.85]$ for $HRVT_1$ ($\alpha_1 = 0.75$) and $[0.45, 0.60]$ for $HRVT_2$ ($\alpha_1 = 0.50$). If either span fails, emit that threshold as `null` (`INSUFFICIENT_SPAN`), strictly prohibiting out-of-span linear extrapolation.
4. **Three-Tier Confidence Classification (`REQ-PER-036.4`)**:
   - `HIGH`: Span satisfied, $R^2 \ge 0.70$ on decimated grid, mean artifact rate $\le 3.0\%$, and $N_{\text{eff}} \ge 10$ (or $\ge 5$ steps for `GUIDED_STEP`).
   - `MEDIUM`: Span satisfied, $R^2 \ge 0.55$ on decimated grid, mean artifact rate $\le 5.0\%$, and $N_{\text{eff}} \ge 8$ (or $\ge 4$ steps for `GUIDED_STEP`).
   - `LOW` / `INSUFFICIENT_SPAN`: Sessions failing `MEDIUM`; informational-only with localized diagnostic reasons.
5. **Summary Card Presentation (`REQ-PER-036.5`)**: Render `HrvThresholdSummaryCard` in `WorkoutDetailTabbedContent.kt` under `WorkoutDetailSectionType.ZONES`, displaying estimated $HRVT_1$/$HRVT_2$ (HR, Power, Pace), confidence chip, protocol badge, $R^2$, $N_{\text{eff}}$, artifact %, and diagnostic reasons.
6. **Localization & Omission (`REQ-PER-036.6`)**: 100% localization parity across all 9 canonical languages in `strings_hrv.xml`. Omit card entirely when workout lacks HRV trackpoints.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Independent Span Gating)**:
  * *Given* a tempo workout where centered-window DFA $\alpha_1$ spans $[0.60, 1.05]$ (crossing $HRVT_1 = 0.75$ but not $0.45$),
  * *When* `HrvThresholdEstimator` evaluates the session,
  * *Then* it emits a valid $HRVT_1$ estimate and returns $HRVT_2 = \text{null}$ (`INSUFFICIENT_SPAN`) without extrapolation.
* **Criterion 2 (Multi-Hour Fatigue Drift Exclusion)**:
  * *Given* a 3-hour workout with downward $\alpha_1$ fatigue drift in hour 3,
  * *When* `HrvThresholdEstimator` runs,
  * *Then* regression is restricted strictly to $t \in [600, 3600]\text{ s}$.
* **Criterion 3 (Confidence Badge & Diagnostics)**:
  * *Given* `HrvThresholdSummaryCard` rendered in Workout Details,
  * *When* displayed,
  * *Then* it displays the confidence chip, protocol provenance badge, $R^2$, $N_{\text{eff}}$, artifact %, and diagnostic reasons across all 9 locales.

### 1.4 System Invariants
- Zero out-of-span linear extrapolation.
- 60s decimation decorrelation ensures statistical independence.
- 100% localization parity across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
- Zero layout shift or overhead for non-HRV sessions.

---

## 2. Test Specification (TST-PER-044)

### Test Case 1: Fresh-Window Telemetry Filtering (`TST-PER-044.1`)
* **Scope**: Unit Test (`HrvThresholdEstimatorTest.kt`)
* **Preconditions**: Synthetic path points spanning $t = 0\text{ s}$ to $7200\text{ s}$ with high artifact segments.
* **Action**: Execute `HrvThresholdEstimator.estimateThresholds`.
* **Expected Result**: Excludes points outside $[600, 3600]\text{ s}$ and points with artifact rate $> 5.0\%$.

### Test Case 2: Decimated Grid Decorrelation & R² Calculation (`TST-PER-044.2`)
* **Scope**: Unit Test (`HrvThresholdEstimatorTest.kt`)
* **Preconditions**: 5s dense telemetry stream with known linear relationship.
* **Action**: Compute regression on decimated 60s bins ($N_{\text{eff}} = \lfloor K / 12 \rfloor$).
* **Expected Result**: $R^2$ evaluates against 60s bins; MRT adjustment ($\tau_{\text{MRT}} = 45\text{ s}$) shifts continuous ramp correctly.

### Test Case 3: Independent Span Gating & Extrapolation Prevention (`TST-PER-044.3`)
* **Scope**: Unit Test (`HrvThresholdEstimatorTest.kt`)
* **Preconditions**: Dataset spanning $\alpha_1 \in [0.60, 1.05]$.
* **Action**: Estimate thresholds.
* **Expected Result**: $HRVT_1$ is non-null; $HRVT_2$ is `null` with diagnostic `INSUFFICIENT_SPAN`.

### Test Case 4: Three-Tier Confidence Classification (`TST-PER-044.4`)
* **Scope**: Unit Test (`HrvThresholdEstimatorTest.kt`)
* **Preconditions**: Test fixtures matching `HIGH`, `MEDIUM`, and `LOW` criteria.
* **Action**: Classify sessions.
* **Expected Result**: Confidence levels match criteria; appropriate diagnostic reasons returned.

### Test Case 5: HrvThresholdSummaryCard Compose Rendering (`TST-PER-044.5`)
* **Scope**: Compose UI Test (`HrvThresholdSummaryCardTest.kt`)
* **Preconditions**: Render card with valid and empty threshold states.
* **Action**: Inspect node tree.
* **Expected Result**: Badges, metrics, units (bpm, W, min/km), and diagnostic text displayed; card omitted for non-HRV workouts.

### Test Case 6: 9-Language Localization Parity (`TST-PER-044.6`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Preconditions**: 21 new string keys in `strings_hrv.xml`.
* **Action**: Run `TranslationParityTest`.
* **Expected Result**: 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Test Case 7: Tier 1 Targeted Regression Suite (`TST-PER-044.7`)
* **Scope**: Clean-room Unit Regression
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-044.1` | Unit | `HrvThresholdEstimator.kt` | `REQ-PER-036.1` | Specified |
| `TST-PER-044.2` | Unit | `HrvThresholdEstimator.kt` | `REQ-PER-036.2` | Specified |
| `TST-PER-044.3` | Unit | `HrvThresholdEstimator.kt` | `REQ-PER-036.3` | Specified |
| `TST-PER-044.4` | Unit | `HrvThresholdEstimator.kt` | `REQ-PER-036.4` | Specified |
| `TST-PER-044.5` | Compose UI | `HrvThresholdSummaryCard.kt` | `REQ-PER-036.5` | Specified |
| `TST-PER-044.6` | Localization | `TranslationParityTest.kt` | `REQ-PER-036.6`, `REQ-LOC-001` | Specified |
| `TST-PER-044.7` | Regression | Tier 1 Suite | `REQ-PRO-001` | Specified |
