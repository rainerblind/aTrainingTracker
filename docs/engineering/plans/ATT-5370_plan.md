# Stage 3: Implementation Plan - ATT-5370: Universal Marginal Histograms & Widened Strip Layout Parity

**Ticket**: [ATT-5370](https://rainerblind.atlassian.net/browse/ATT-5370)  
**Sub-task**: [ATT-5389](https://rainerblind.atlassian.net/browse/ATT-5389) (`[Impl-Plan]`) | **Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-45.5` | **Branch**: `feature/ATT-5370`  
**Requirement Mapping**: `REQ-UI-446` (`REQ-UI-446.1`, `REQ-UI-446.2`, `REQ-UI-446.3`)  
**Test Mapping**: `TST-UI-446` (`TST-UI-446.1` .. `TST-UI-446.4`), `TST-LOC-001`  
**Author**: AI Agent 1 (`subagent_pro`) | **Risk**: **LOW** | **Date**: 2026-11-04  

---

## 1. Architecture Decomposition (SWE.2) & UI Consistency
* **Data & Math Helpers (`TelemetryHistogramMath.kt` / `TelemetryMetricGraph.kt`)**:
  * Define `LinearHistogramBin(binIndex: Int, rangeMin: Double, rangeMax: Double, durationSec: Long, percentage: Float, color: Color)`.
  * Pure calculation `computeLinearMarginalHistogram(...)`: divides `[dataMin, dataMax]` into 20 uniform linear bins, accumulating sample durations $\Delta t$ (or distance weights for elevation), computing percentage normalized to maximum bin percentage.
* **UI Layer - Jetpack Compose**:
  * `TelemetryMetricGraph.kt`: Widen portrait margin from `25.dp` to `52.dp` (`PORTRAIT_HISTOGRAM_MARGIN_DP = 52.dp`). For non-zoned metrics (`SPEED`, `PACE`, `W_PRIME_BAL`), render the 44dp marginal strip in portrait and `AlignedMarginalLinearDistribution` in landscape 28% pane using the graph's series color (`colorScheme.primary` or `TTColor.WPrimeBalGreen`). Retain 5/7-zone coloring for `HEART_RATE` and `POWER`. Obey Rule 69 (unconditional `alpha = 0.15f` background track) and Rule 70 (clamping without stacking at $y=0$).
  * `ElevationProfile.kt`: Add `alignWithTelemetryCharts: Boolean = false`. When `true`, allocate `endPadding = 52.dp` in portrait and 72%/28% split in landscape, rendering a series-colored altitude marginal histogram across 20 bins in `[minAlt, maxAlt]`. When `false`, retain compact layout without histograms.
  * Callers (`MapDetailLayout.kt`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt`): Pass `alignWithTelemetryCharts = true` to `ElevationProfile` and ensure multi-chart plot alignment (`Rule 64`).
* **UI Consistency (Rule 23)**: Reuses `MaterialTheme.colorScheme` tokens, `TTColor.WPrimeBalGreen`, `TTAlpha`, and `LayoutConstants`.

---

## 2. Invariant Protection & System Safety
1. **Rule 64 Invariant**: 1:1 horizontal plot width (`startPadding = 50.dp`, `endPadding = 52.dp` in portrait; 72%/28% in landscape) and synchronized scrubbing across all stacked charts.
2. **Rules 69 & 70 Invariant**: Unconditional background track continuity (`alpha = 0.15f`) across `[dataMin, dataMax]` and domain clamping without out-of-domain $y=0$ stacking.
3. **Standalone Callers Invariant**: `SensorGridScreen`, `LiveClimbSheet`, `ClimbDetailSheet`, `RouteItem`, `SegmentItem` default to `alignWithTelemetryCharts = false` with clean margins.
4. **Physiology Model Invariant**: Zero changes to HR/Power zone calculations or threshold storage.

---

## 3. Atomic Implementation Steps (Stage 4 Construction)

### Step 1: Linear Histogram Calculation Model & Helpers (`REQ-UI-446.2`)
* Add `LinearHistogramBin` and `computeLinearMarginalHistogram(...)` in `TelemetryHistogramCalculator.kt` or `TelemetryMetricGraph.kt`. Add unit tests verifying 20 linear bins, positive duration gating, and domain clamping.

### Step 2: Widened Portrait Strip & Non-Zoned Histogram in TelemetryMetricGraph (`REQ-UI-446.1`, `REQ-UI-446.3`)
* In `TelemetryMetricGraph.kt`, update `endPaddingPx = if (isLandscape) 0.dp.toPx() else 52.dp.toPx()`.
* Render series-colored marginal histogram in portrait strip (`44.dp` width) and landscape 28% pane (`AlignedMarginalLinearDistribution`) for `SPEED`, `PACE`, and `W_PRIME_BAL`, removing empty landscape spacers.

### Step 3: ElevationProfile Parameterization & Marginal Altitude Histogram (`REQ-UI-446.1`, `REQ-UI-446.3`)
* In `ElevationProfile.kt`, add `alignWithTelemetryCharts: Boolean = false`.
* When `true`, apply `endPadding = 52.dp` in portrait (with 44dp altitude marginal strip in series color) and 72%/28% split in landscape. When `false`, retain compact padding without marginal histogram.

### Step 4: Wire Callers & Align Stacked Layouts (`REQ-UI-446.1`)
* In `MapDetailLayout.kt`, `WorkoutSummary.kt`, and `TrackOnMapScreen.kt`, pass `alignWithTelemetryCharts = true` to `ElevationProfile`.

### Step 5: Update Unit Tests & Verification Suite (`TST-UI-446.1` .. `TST-UI-446.4`)
* Update `TelemetryMetricGraphTest.kt`, `TelemetryMetricGraphZoneTest.kt`, and `TelemetryMetricGraphLandscapeAlignmentTest.kt` to assert `52.dp` portrait padding and universal marginal histograms. Verify 100% test pass rate.

---

## 4. Verification & Rollback Plan
* **Commands**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  * `python3 tools/verify_requirement_governance.py`
* **Rollback**: Clean revert on `feature/ATT-5370`; UI-only change without database migrations.
