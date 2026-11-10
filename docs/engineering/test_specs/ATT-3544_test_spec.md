# Stage 2: Requirement & Test Specification - ATT-3544: Interactive DFA a1/rMSSD Charts & a1 Scatter Plot

**Ticket**: [ATT-3544](https://rainerblind.atlassian.net/browse/ATT-3544)  
**Sub-task**: Stage 2 `[Req & Test Spec]`  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-PER-035` (*Interactive Workout Details DFA a1/rMSSD Charts & a1 Scatter Plot*)  
**Test Spec ID**: `TST-PER-043`  
**Branch**: `feature/ATT-3544`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-PER-035)

### 1.1 Problem Statement & Rationale
Workout Details provides continuous telemetry curves for Speed, Heart Rate, and Power, but lacks visualization for centered post-workout HRV telemetry (`HRV_DFA_A1`, `HRV_RMSSD`, `HRV_ARTIFACT_RATE`). Athletes require interactive inspection of DFA $\alpha_1$ and $\text{rMSSD}$ time-series to assess autonomic recovery and threshold transitions ($\alpha_1 = 0.75$ aerobic, $\alpha_1 = 0.50$ anaerobic), while suppressing unreliable high-artifact ($> 5\%$) intervals and correlating $\alpha_1$ against Heart Rate and Cycling Power via an interactive scatter plot.

### 1.2 Functional & Architectural Requirements
1. **DFA $\alpha_1$ & rMSSD Tracks (`REQ-PER-035.1`)**: `TelemetryMetricGraph` SHALL support `TelemetryMetricType.HRV_DFA_A1` and `TelemetryMetricType.HRV_RMSSD`. Plot bounds (`startPadding = 50.dp`) and scrubbing synchronization MUST align with existing charts (Rule 64).
2. **Artifact Gap & Non-Interpolation Hatching (`REQ-PER-035.2`)**: Polylines SHALL break where `hrvDfaA1` is null or `hrvArtifactRate > 5.0%`. An angled hatched overlay SHALL delineate suppressed dropout intervals.
3. **Threshold Guide Lines & Clamping (`REQ-PER-035.3`)**: DFA $\alpha_1$ graphs SHALL render dashed horizontal reference lines at $\alpha_1 = 0.75$ and $\alpha_1 = 0.50$. Y-axis bounds SHALL be clamped to $[0.2, 1.4]$ with outlier resilience (Rule 48).
4. **$\alpha_1$ vs. Power/HR Scatter Plot (`REQ-PER-035.4`)**: Workout Details SHALL provide `HrvAlpha1ScatterCard` plotting intensity (HR in bpm or Power in Watts) against DFA $\alpha_1$ for valid points ($\alpha_1 \in [0.35, 1.15]$, artifact rate $\le 5\%$), with toggleable linear regression overlay.
5. **Graceful Omission for Non-HRV Workouts (`REQ-PER-035.5`)**: Workouts without valid HRV trackpoints SHALL completely omit the HRV telemetry tracks and scatter card with zero layout shifts.
6. **Touch Disambiguation & Localization (`REQ-PER-035.6`)**: Horizontal chart scrubbing SHALL be disambiguated via `ChartGestureDisambiguator`. All labels SHALL support 100% localization parity across all 9 supported locales.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (HRV Telemetry Curves)**: *Given* a workout with valid centered HRV samples, *When* selecting DFA $\alpha_1$ or $\text{rMSSD}$ in Workout Details, *Then* continuous curves are rendered with threshold reference lines ($\alpha_1 = 0.75, 0.50$) and artifact dropouts ($> 5\%$) rendered as hatched gaps.
* **AC-2 (Threshold Scatter Dynamics)**: *Given* a workout with valid $\alpha_1$ and HR/Power samples, *When* viewing `HrvAlpha1ScatterCard`, *Then* an interactive scatter plot is displayed with linear regression fit and threshold markers.
* **AC-3 (Non-HRV Omission)**: *Given* a workout without HRV data, *When* opening Workout Details, *Then* HRV curves and the scatter card are omitted cleanly.

### 1.4 System Invariants
* Pixel-perfect vertical chart alignment with `startPadding = 50.dp` per Rule 64.
* UI classes in `ui/**` query data strictly through `WorkoutRepository`, never referencing `*DatabaseManager` or `*DbHelper` directly.
* Zero snapshot state writes during composition (Rule 66).

---

## 2. Test Specification (TST-PER-043)

| Test ID | Target Component | Method / Test Name | Expected Outcome |
| :--- | :--- | :--- | :--- |
| `TST-PER-043.1` | `MapModels.kt`, `WorkoutRepository.kt` | `testPathPoint_hrvEnrichment_andJvmOverloadsParity` | `PathPoint` enriches `hrvDfaA1`, `hrvRmssd`, `hrvArtifactRate`; defaults to null safely. |
| `TST-PER-043.2` | `TelemetryMetricGraph.kt` | `testTelemetryMetricGraph_hrvTracks_rendersBoundsAndThresholds` | Renders `HRV_DFA_A1` and `HRV_RMSSD` with `startPadding = 50.dp`, $\alpha_1 \in [0.2, 1.4]$, and guides at 0.75 and 0.50. |
| `TST-PER-043.3` | `TelemetryMetricGraph.kt` | `testTelemetryMetricGraph_artifactGap_breaksPolylineAndHatches` | Non-continuous path across samples with `hrvArtifactRate > 5.0%` with hatched overlay. |
| `TST-PER-043.4` | `HrvAlpha1ScatterChart.kt` | `testHrvAlpha1Scatter_linearRegressionAndPointFiltering` | Plots valid $(\text{HR/Power}, \alpha_1)$ points, calculates least-squares fit line, handles empty state. |
| `TST-PER-043.5` | Resources & Theme | `TranslationParityTest`, `ThemeColorCentralizationAuditTest` | 100% 9-language localization parity and `TTColor` design token usage. |
| `TST-PER-043.6` | Whole Module | Tier 1 Targeted Regression Suite | 100% pass across `ui.map.*`, `ui.aftermath.*`, `localization.*`, `build.*`. |

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-043.1` | Unit | `HrvPathPointEnrichmentTest.kt` | `REQ-PER-035.1` | Specified |
| `TST-PER-043.2` | Unit / UI | `TelemetryMetricGraphHrvTest.kt` | `REQ-PER-035.1`, `REQ-PER-035.3` | Specified |
| `TST-PER-043.3` | Unit / UI | `TelemetryMetricGraphHrvTest.kt` | `REQ-PER-035.2` | Specified |
| `TST-PER-043.4` | Unit / UI | `HrvAlpha1ScatterChartTest.kt` | `REQ-PER-035.4`, `REQ-PER-035.5` | Specified |
| `TST-PER-043.5` | Contract | `TranslationParityTest`, `ThemeColorCentralizationAuditTest` | `REQ-PER-035.6` | Specified |
| `TST-PER-043.6` | Regression | Tier 1 Suite | `REQ-PRO-001`, `REQ-PER-035.5` | Specified |
