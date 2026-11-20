# Stage 1 Analysis: ATT-9171 - Fix rMSSD Telemetry Graph Y-Axis Scaling Hardcoded to 100 ms Floor

**Ticket**: [ATT-9171](https://rainerblind.atlassian.net/browse/ATT-9171)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-19_03_Nacht` | **Branch**: `feature/ATT-9171`  
**Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-19

---

## 1. Problem Statement & Motivation
In Workout Details (`MapDetailLayout` -> `TelemetryChartStack`), `TelemetryMetricGraph(metricType = TelemetryMetricType.HRV_RMSSD)` hardcodes its Y-axis upper bound to a minimum floor of `100.0 ms`. During normal aerobic/moderate exercise (e.g., `Kurze Runde var 2 #53`, rMSSD `1.89 - 5.64 ms`, avg `2.85 ms`), the trace renders as a flat line crushed against `y = 0` (>94% wasted vertical space) and the right-hand marginal histogram collapses into a single bottom bin. Transient warmup or artifact spikes (e.g., `45.5 ms` against `2 - 6 ms` steady-state) can similarly flatten the exercise curve if scaling relies blindly on raw `max`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Primary Defect (`TelemetryMetricGraph.kt:359-361`)**:
  - `TelemetryMetricType.HRV_RMSSD -> 0.0 to (max * 1.15).coerceAtLeast(100.0)` applies a `100.0 ms` floor copy-pasted from `CADENCE` (`L317`) during `ATT-3544` (`3db57718`), suitable only for resting morning HRV (`30-100+ ms`) rather than exercise rMSSD (`2-15 ms`).
- **Independent Peer Review & Verification**:
  - Verified all cited files/lines against live `sprint/2026-11-19_03_Nacht`:
  1. **Artifact-Contaminated Ceiling Blind Spot (`L275-311` vs. `L985-986`)**: Curve rendering suppresses high-artifact points (`hrvArtifactRate > 5.0` -> hatched gaps per `REQ-PER-035.2`), yet `validValues`/`effectiveValues` still feeds those suppressed samples into `max`. Filtering out `hrvArtifactRate > 5.0` primary samples before scaling (falling back to all finite `>= 0.0` values if every point is artifact-flagged) prevents hidden dropouts from inflating `dataMax`.
  2. **Outlier Spike Guard vs. Zero-Clipping Resting Range**: A naive global P98 cap risks clipping genuine `40-120 ms` or `120-180 ms` resting/low-intensity recordings (`max * 1.15`). Using a spike-ratio guard over sorted finite values (`N >= 5`, nearest-rank `p95 = sorted[floor(0.95 * (N - 1))]`; if `p95 > 0.0 && max > p95 * 2.0`, scale from `p95 * 1.15`, else `max * 1.15`) prevents transient spikes (`45 ms` over `2-6 ms` steady-state) from crushing the trace while guaranteeing `dataMax >= max * 1.15` (zero clipping) for genuine `1.89-5.64 ms`, `8-45 ms`, `40-120 ms`, and `120-180 ms` recordings.
  3. **Safe Floor, Nice Ceiling & Y-Axis Labels (`L811-825`)**: Rounding `rawUpper` upward to a clean nice integer step (`1` for `<= 10`, `2` for `<= 20`, `5` for `<= 50`, `10` for `<= 100`, `20` for `> 100`) with safe floor `5.0 ms` guarantees: (a) empty / all-zero / single-point low streams stay well-formed (`0 .. 5 ms`), (b) `TelemetryZoneMath.mapTelemetryValueToY` (`L403`, `valRange.coerceAtLeast(1.0)`) never distorts small spans, (c) `maxLabel` (`TelemetryMetricGraph.kt:815`) never silently truncates fractional bounds or duplicates `minLabel = "0"` (explicit `HRV_RMSSD` label helper formats integers cleanly and sub-10 fractional values as `%.1f`).
  4. **Portrait/Landscape Marginal Histograms, Area Fill & Overlays**: `nonZonedHistogram` (`L591-619`) calls `TelemetryHistogramCalculator.computeLinearMarginalHistogram(values = rawValues, dataMin = dataMin, dataMax = dataMax, numBins = 20)` which skips `v > dataMax` (`TelemetryHistogramCalculator.kt:430`), so both portrait (`L746-784`) and landscape `AlignedMarginalLinearDistribution` (`L1446-1531`) automatically rescale 1:1 with the curve across multiple bins. Area fill (`L1071-1125`) uses `baselineY = 0.0` bottom (`isInvertedYAxis = false`), and `activeOverlaySeries` (`L301-309`) remains included in `effectiveValues`.
  5. **Call-Site Audit**: Only `TelemetryChartStack.kt:354-373` invokes `TelemetryMetricGraph(HRV_RMSSD)` (Workout Details -> `Übersicht` & `Details`). `WorkoutSummary.kt` renders `PACE/SPEED`, `HEART_RATE`, `POWER`, `W_PRIME_BAL` only (`0` changes needed). `TelemetryMetricUtils.extractMetricValue` (`L274`, `hrvRmssd?.takeIf { it >= 0.0 }`) and `formatValue` (`L362`, `"%.1f ms"`) need zero signature breaks (defensive `isFinite()` guard inside bounds helper).
  6. **Architecture Ceilings & Governance**: `TelemetryMetricGraph.kt` is `1,799` LoC vs. `1,965` LoC ceiling (`166` LoC headroom); `TelemetryZoneMath.kt` is `475` LoC (`<= 1,500`); `TelemetryMetricUtils.kt` is `408` LoC (`<= 1,500`). Any added `@Preview` MUST be `private fun` (`DeadCodeEliminationContractTest`); mandatory **Rule 8.5** Light/Dark PNG (`docs/engineering/walkthroughs/img/ATT-9171_rmssd_dynamic_yaxis.png`); `Localization Parity: N/A - no UI strings`.

---

## 3. User Scope Grounding (`ATT-1250`)
- **In-Scope Goals (`1 SP` confirmed; no split needed)**:
  1. Replace hardcoded `100.0` ceiling floor for `HRV_RMSSD` in `TelemetryMetricGraph.kt` with a pure, dynamic, outlier-resilient Y-axis bounds helper in `TelemetryZoneMath.kt` (safe floor `5.0 ms`, artifact-rate exclusion, P95 spike guard, nice-step rounding, zero clipping on `40-120+ ms`).
  2. Wire explicit `HRV_RMSSD` Y-axis label formatting (`TelemetryMetricGraph.kt:811-822`) and verify portrait + landscape marginal histograms rescale across multiple bins.
  3. Add targeted unit tests (`TelemetryZoneMathTest.kt`), Compose UI + pixel-spread + Rule 8.5 Light/Dark PNG test (`TelemetryMetricGraphHrvUiTest.kt`), and living docs (`REQ-HRV-047` / `TST-HRV-047`).
- **Out-of-Scope Non-Goals**: No changes to `HRV_DFA_A1` (`[0.2, 1.4]` inverted), `HEART_RATE`/`POWER`/`CADENCE`/`PACE`/`SLOPE`/`ALTITUDE`/`W_PRIME_BAL` scaling, `TelemetryHistogramCalculator.kt`, `WorkoutSummary.kt`, DB schemas, or `values*/strings_*.xml`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: `REQ-PER-035.1` (`docs/requirements/req_aftermath_periods.md`, `ATT-3544`) targeting `TelemetryMetricGraph.kt` (plus `REQ-HRV-046` invariance note citing `HRV_RMSSD`).
- **Historical Origin & Commit Trace**: Introduced in `ATT-3544` (`3db57718`, line `0.0 to (max * 1.15).coerceAtLeast(100.0)`); palette refreshed in `ATT-7759` (`3ce94b53`).
- **Root Reason for Existing Formulation**: `ATT-3544` focused on `[0.2, 1.4]` clamping for `HRV_DFA_A1` and copied `CADENCE`'s `100.0` floor for `HRV_RMSSD` assuming resting HRV magnitudes (`30-100+ ms`) rather than suppressed exercise rMSSD (`2-15 ms`).
- **Preservation of Core Invariants**: Preserves `dataMin = 0.0`, non-inverted upward polarity (`isInvertedTelemetryYAxis(HRV_RMSSD) == false`), `> 5.0%` artifact gap hatching (`REQ-PER-035.2`), `startPadding = 50.dp` / `endPadding = 52.dp` (`Rule 64`), `TelemetryMetricUtils.formatValue` (`"%.1f ms"`), and all other metric scaling formulas.

---

## 5. Change-Request Impact Analysis (`SUP.10` / `REQ-PRO-064`)
- **Affected Requirements**: Net-new **`REQ-HRV-047`** (`REQ-HRV-047.1`..`REQ-HRV-047.4`) / **`TST-HRV-047`** (`TST-HRV-047.1`..`TST-HRV-047.4`) in `docs/requirements/req_aftermath_periods.md` & `docs/tests/tst_aftermath_periods.md` (refining `REQ-PER-035.1`). (`SYS.2`/`SYS.3` Impact Check `REQ-PRO-131.4`: **None — Derived SW-only**.)
- **Affected Architecture Elements**: `TelemetryZoneMath.kt` (`475` LoC, new pure scaling + label helpers), `TelemetryMetricGraph.kt` (`1,799` LoC; ceiling `1,965` LoC, delegates `HRV_RMSSD` bounds & labels + `private fun` `@Preview`). Zero changes to repositories, DB, `TelemetryMetricUtils.kt`, `TelemetryHistogramCalculator.kt`, or `ANT-Android-SDKs`.
- **Verification Impact & Test Surface**: `TelemetryZoneMathTest.kt` (low `1.89-5.64 ms`, `8-45 ms`, spike `45 ms` over `2-6 ms`, resting `40-120 ms` & `120-180 ms`, empty/zero/single-value/NaN/artifact cases, label formatting), `TelemetryMetricGraphHrvUiTest.kt` (portrait + landscape curve/histogram vertical spread & Rule 8.5 Light/Dark PNG), `TelemetryMetricGraphHrvTest.kt`, `ArchitectureConformanceContractTest.kt`, `DeadCodeEliminationContractTest.kt`, `SourceTextReadingAuditContractTest.kt`.
- **Risk Assessment & Invariants**: **LOW** risk — isolated pure-math bounds & label helpers scoped strictly to `TelemetryMetricType.HRV_RMSSD`.

---

## 6. Architectural Strategy & High-Level Solution
1. Add pure helpers to `TelemetryZoneMath.kt`:
   - `calculateHrvRmssdYAxisBounds(values: List<Double>): Pair<Double, Double>`: filters finite `>= 0.0` samples; if empty returns `0.0 to 5.0`; for `N >= 5` checks nearest-rank `p95 = sorted[floor(0.95 * (N - 1))]`, using `base = if (p95 > 0.0 && max > p95 * 2.0) p95 else max`; computes `rawUpper = (base * 1.15).coerceAtLeast(5.0)` and rounds upward to a clean nice step (`1` for `<= 10`, `2` for `<= 20`, `5` for `<= 50`, `10` for `<= 100`, `20` for `> 100`), returning `0.0 to niceCeiling`.
   - `formatHrvRmssdAxisLabel(value: Double, locale: Locale): String`: formats exact integers (`abs(value - round(value)) < 1e-6`) as integer string (`"0"`, `"7"`, `"140"`) and fractional values as `String.format(locale, "%.1f", value)`.
2. Wire `TelemetryMetricGraph.kt`:
   - For `HRV_RMSSD` in `dataMin, dataMax` (`L359-361`), exclude primary samples with `hrvArtifactRate > 5.0` (falling back to `effectiveValues` if none remain) + keep finite overlay values, and delegate to `TelemetryZoneMath.calculateHrvRmssdYAxisBounds(...)`.
   - In Y-axis label formatting (`L811-822`), add `TelemetryMetricType.HRV_RMSSD -> TelemetryZoneMath.formatHrvRmssdAxisLabel(..., currentLocale)`.
   - Add `private fun TelemetryMetricGraphRmssdDynamicPreview()` (`@Preview`).

---

## 7. System Invariants & Risk Assessment
- **Core Invariants**: `HEART_RATE`, `POWER`, `CADENCE`, `SPEED`, `PACE`, `SLOPE`, `ALTITUDE`, `W_PRIME_BAL`, `HRV_DFA_A1` scaling and plotting 100% unchanged; `TelemetryMetricGraph.kt <= 1,965` LoC; `HRV_RMSSD` remains non-inverted (`0.0` bottom); `> 5.0%` artifact gap hatching preserved; `Localization Parity: N/A - no UI strings`; Human Decision Gate enforced.
- **Risk Rating**: **LOW** — Self-contained `1 SP` pure-math Y-axis bounds & formatting fix with exhaustive unit and Robolectric pixel verification.
