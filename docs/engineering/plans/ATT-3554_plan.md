# Stage 3: Implementation Plan - ATT-3554: Calculate Lag-Aligned, Confidence-Gated HRVT1 and HRVT2 Thresholds via DFA a1 Regression and Display Summary Card

**Ticket**: [ATT-3554](https://rainerblind.atlassian.net/browse/ATT-3554)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PER-036` (*Confidence-Gated HRVT1/HRVT2 Threshold Estimation & Summary Card*)  
**Test Mapping**: `TST-PER-044` (*Confidence-Gated HRVT1/HRVT2 Threshold Estimation & Summary Card Verification Suite*)  
**Branch**: `feature/ATT-3554` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
In *aTrainingTracker*, post-workout DFA $\alpha_1$ analysis in `HrvAlpha1ScatterCard.kt` currently uses naive session-wide linear regression without lag alignment or span gating. This results in warm-up contamination, out-of-span threshold extrapolation on tempo rides, serial autocorrelation bias on dense 5s telemetry, and absence of quality/confidence badges. ATT-3554 implements `HrvThresholdEstimator.kt` (fresh 10–60 min window, 60s decimated grid regression, independent span gates, and three-tier confidence classification) and `HrvThresholdSummaryCard.kt` in Workout Details.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PER-036` (*Confidence-Gated HRVT1/HRVT2 Threshold Estimation & Summary Card*, `REQ-PER-036.1` to `REQ-PER-036.6`)
* **Test Mapping**: `TST-PER-044` (*Confidence-Gated HRVT1/HRVT2 Threshold Estimation & Summary Card Verification Suite*, `TST-PER-044.1` to `TST-PER-044.7`)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Unintended Regressions**: Existing `HrvAlpha1ScatterChartTest`, `HrvAlpha1ScatterCardTest`, and telemetry tests continue to pass 100%.
2. **Zero Out-of-Span Extrapolation**: If observed $\alpha_1$ fails span gating ($[0.65, 0.85]$ for $HRVT_1$, $[0.45, 0.60]$ for $HRVT_2$), threshold is strictly returned as `null` with confidence `INSUFFICIENT_SPAN`.
3. **Decimated Grid Decorrelation**: $R^2$ and regression evaluate on 60-second decimated averages (or discrete steps for `GUIDED_STEP`), eliminating serial autocorrelation inflation.
4. **Graceful Omission & Localization Parity**: Card is cleanly omitted for workouts without HRV telemetry; all user-facing strings support 100% 9-language localization parity.
5. **Human Decision Gate**: Parent ticket ATT-3554 advances to `Final Review (Human)` and is never marked `Erledigt` by agents.

---

## 4. Proposed Architectural Changes

### Component 1: Domain Estimator (`HrvThresholdEstimator.kt`)
* **Package**: `com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail`
* **Enums & Models**:
  * `enum class HrvProtocolType { FREE_WORKOUT, GUIDED_STEP, GUIDED_RAMP }`
  * `enum class ThresholdConfidence { HIGH, MEDIUM, LOW, INSUFFICIENT_SPAN }`
  * `data class DecimatedHrvPoint(val intensity: Double, val alpha1: Double, val artifactRate: Double, val sampleCount: Int, val timeSec: Long)`
  * `data class SingleThresholdEstimate(val thresholdTarget: Double, val intensity: Double?, val confidence: ThresholdConfidence, val spanSatisfied: Boolean, @StringRes val diagnosticReasonRes: Int?)`
  * `data class HrvThresholdEstimateResult(val hrvt1: SingleThresholdEstimate, val hrvt2: SingleThresholdEstimate, val rSquared: Double?, val slope: Double?, val intercept: Double?, val effectiveSampleSize: Int, val meanArtifactRate: Double, val protocolType: HrvProtocolType, val intensityMode: HrvScatterIntensityMode, val decimatedPoints: List<DecimatedHrvPoint>)`
* **Core Logic**:
  * Fresh window filter: active time $t \in [600, 3600]\text{ s}$, artifact $\le 5.0\%$, valid intensity & $\alpha_1 \in [0.35, 1.15]$.
  * Decimation into 60s bins (or discrete steps), computing mean intensity, mean $\alpha_1$, and mean artifact rate.
  * OLS regression on decimated points. Mean Response Time correction ($\tau_{\text{MRT}} = 45\text{ s}$) applied for continuous ramps.
  * Independent span checks: $[0.65, 0.85]$ for $HRVT_1$; $[0.45, 0.60]$ for $HRVT_2$.
  * Three-tier confidence gating (`HIGH`, `MEDIUM`, `LOW` / `INSUFFICIENT_SPAN`) with localized diagnostic reasons.

### Component 2: Presentation Layer (`HrvThresholdSummaryCard.kt`)
* **Package**: `com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail`
* **Composable**: `fun HrvThresholdSummaryCard(points: List<PathPoint>, protocolType: HrvProtocolType = HrvProtocolType.FREE_WORKOUT, dfaA1ThresholdT1: Float = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T1, dfaA1ThresholdT2: Float = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2, modifier: Modifier = Modifier)`
* **Layout**:
  * Material 3 `ElevatedCard` with rounded shape (`12.dp`).
  * Header row with title (`R.string.hrv_threshold_card_title`) and provenance badge (`FREE_WORKOUT`, `GUIDED_STEP`, `GUIDED_RAMP`).
  * Intensity mode switcher: `SingleChoiceSegmentedButtonRow` for HR (bpm), Power (W), and Pace (min/km).
  * Metrics row using `MetricItem`: $HRVT_1$ value, $HRVT_2$ value, $R^2$ with $N_{\text{eff}}$, and mean artifact %.
  * Confidence badge: `HIGH` (primary/success container), `MEDIUM` (warning container), `LOW` / `INSUFFICIENT_SPAN` (neutral/outline container).
  * Diagnostic explanation row when confidence is not `HIGH`.

### Component 3: Live Host Mounting (`WorkoutDetailTabbedContent.kt`, Rule 81)
* Mount `HrvThresholdSummaryCard` immediately adjacent to `HrvAlpha1ScatterCard` in `WorkoutDetailTabbedContent.kt` under `WorkoutDetailSectionType.ZONES` when `TelemetryMetricUtils.hasHrvData(activeScrubPath)` is true.

### UI Consistency (Rule 23)
* **Reference Component**: `HrvAlpha1ScatterCard.kt` and `HeartRateZoneDistributionCard.kt`.
* **Reused Components**: `MetricItem`, `MetricLayout`, `SingleChoiceSegmentedButtonRow`, `SegmentedButton`, `HorizontalDivider`, `ElevatedCard`.
* **Theme Tokens**: `RoundedCornerShape(12.dp)`, `16.dp` card padding, `8.dp` inner spacing, `TTColor.ArtifactWarning*`, `MaterialTheme.colorScheme`.
* **New One-Off Styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement `HrvThresholdEstimator.kt` Domain Logic
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutdetail/HrvThresholdEstimator.kt`
* Details: Implement data models, window filtering, 60s decimation, OLS regression, independent span gating, MRT ramp lag adjustment, and confidence classification.

### Step 2: Implement Unit Tests in `HrvThresholdEstimatorTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutdetail/HrvThresholdEstimatorTest.kt`
* Verification: Test fresh-window bounding ($10\text{--}60\text{ min}$), decimated grid decorrelation, independent span gating ($HRVT_1$ valid while $HRVT_2$ null), and `HIGH`/`MEDIUM`/`LOW`/`INSUFFICIENT_SPAN` confidence gating.

### Step 3: Implement `HrvThresholdSummaryCard.kt` Composable
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutdetail/HrvThresholdSummaryCard.kt`
* Details: Compose `ElevatedCard` layout with provenance badge, confidence badge, segmented mode selector, threshold values, and diagnostic strings.

### Step 4: Mount in `WorkoutDetailTabbedContent.kt` (Rule 81)
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContent.kt`
* Details: Wire `HrvThresholdSummaryCard` under `WorkoutDetailSectionType.ZONES` guarded by `TelemetryMetricUtils.hasHrvData(activeScrubPath)`.

### Step 5: Implement Compose Tests in `HrvThresholdSummaryCardTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutdetail/HrvThresholdSummaryCardTest.kt`
* Verification: Test node tree rendering, confidence badge variants, threshold formatting, and omission when no HRV data.

### Step 6: Target Test Execution & Localization Parity
* Execute:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests and Compose tests in Step 6, followed by clean-room Tier 1 suite in Stage 5.
* **Rollback Plan**: All changes isolated to `feature/ATT-3554`. Clean `git checkout sprint/2026-46.2` safely aborts changes without branch pollution.
