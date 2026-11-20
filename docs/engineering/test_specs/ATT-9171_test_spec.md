# Stage 2: Requirement & Test Specification - ATT-9171

**Ticket**: [ATT-9171](https://rainerblind.atlassian.net/browse/ATT-9171) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-19_03_Nacht` | **Branch**: `feature/ATT-9171`  
**Requirement Mapping**: `REQ-HRV-047` (refines `REQ-PER-035.1`) | **Test Spec ID**: `TST-HRV-047` | **Date**: 2026-11-19

---

## 1. Requirement Specification (`REQ-HRV-047`)

### 1.1 Attributes & System Traceability
- **System Requirement(s)**: Derived (SW-only) — Dynamic, outlier-resilient Y-axis bounds and axis label formatting for Workout Details `HRV_RMSSD` telemetry graphs and marginal histograms, refining `REQ-PER-035.1` (zero `SYS.2`/`SYS.3` impact).
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration
- **Localization Parity**: `N/A - no UI strings`.

### 1.2 Functional & Architectural Requirements
1. **`REQ-HRV-047.1` (Dynamic, Outlier-Resilient `HRV_RMSSD` Y-Axis Bounds)**: `TelemetryZoneMath.calculateHrvRmssdYAxisBounds(values: List<Double>): Pair<Double, Double>` SHALL filter finite values `>= 0.0` (returning `0.0 to 5.0` when empty), evaluate nearest-rank `p95 = sorted[floor(0.95 * (N - 1)).toInt()]` when `N >= 5`, select `base = if (N >= 5 && p95 > 0.0 && max > p95 * 2.0) p95 else max`, compute `rawUpper = (base * 1.15).coerceAtLeast(5.0)` with finite-overflow protection for extreme `Double` values, and round upward to a clean nice step (`1` for `<= 10`, `2` for `<= 20`, `5` for `<= 50`, `10` for `<= 100`, `20` for `> 100`), returning `0.0 to niceCeiling` with `niceCeiling >= 5.0` and zero clipping (`niceCeiling >= max * 1.15`) on genuine non-spike recordings.
2. **`REQ-HRV-047.2` (Artifact Exclusion, Reactive `remember` Keys & Clean Y-Axis Labels)**: `TelemetryMetricGraph.kt` SHALL delegate `HRV_RMSSD` bounds to `TelemetryZoneMath.calculateHrvRmssdYAxisBounds`, excluding primary samples with `hrvArtifactRate > 5.0` via `cleanPrimary.ifEmpty { validValues } + overlayValues` and MUST include `pathPoints` and `rawValues` in the `(dataMin, dataMax)` `remember(...)` keys so bounds never go stale. `TelemetryZoneMath.formatHrvRmssdAxisLabel(value: Double, locale: Locale): String` SHALL guard non-finite values (`-> "0"`), normalize `-0.0 -> "0"`, format exact integers without decimals (`"0"`, `"7"`, `"140"`), and format fractional values to 1 decimal (`"%.1f"`), wired into `TelemetryMetricGraph.kt` `maxLabel`/`minLabel`.
3. **`REQ-HRV-047.3` (Portrait/Landscape Rescaled Curve, Multi-Bin Histograms & Metric Invariance)**: Both the `HRV_RMSSD` curve/area-fill and portrait (`nonZonedHistogram`) + landscape (`AlignedMarginalLinearDistribution`) marginal histograms SHALL render against the dynamic `(0.0, dataMax)` domain so low-rMSSD exercise sessions (`1.89-5.64 ms`) and spike-contaminated sessions (`2-6 ms` + `45 ms` transient spike) occupy vertical chart height and distribute across multiple histogram bins. `HRV_DFA_A1` (`[0.2, 1.4]` inverted), `HEART_RATE`, `POWER`, `CADENCE`, `SPEED`, `PACE`, `SLOPE`, `ALTITUDE`, `W_PRIME_BAL`, `> 5.0%` artifact hatching, `TelemetryMetricUtils.kt`, and `TelemetryHistogramCalculator.kt` SHALL remain untouched.
4. **`REQ-HRV-047.4` (Ceilings, Private Previews, Rule 8.5 PNG & Asset Integrity)**: `TelemetryMetricGraph.kt` MUST stay `<= 1,965` LoC (`ArchitectureConformanceContractTest`), any added `@Preview` MUST be `private fun` (`DeadCodeEliminationContractTest`), tests MUST perform `0` `src/main` reads (`SourceTextReadingAuditContractTest`), historical `ATT-8710_dfa_a1_zone_graph.png` and `ATT-8953_dfa_a1_inverted_yaxis.png` MUST remain untouched while capturing deterministic Light/Dark PNG strictly to `docs/engineering/walkthroughs/img/ATT-9171_rmssd_dynamic_yaxis.png`, and `Localization Parity: N/A - no UI strings`.

### 1.3 Acceptance Criteria (Given-When-Then)
- *Given* an exercise workout with `HRV_RMSSD` spanning `1.89 - 5.64 ms`, *When* `TelemetryMetricGraph(HRV_RMSSD)` renders in portrait or landscape, *Then* `dataMin = 0.0`, `dataMax = 7.0` (labels `"0"` and `"7"`), the curve spans into the upper plot region, and the marginal histogram distributes across multiple bins.
- *Given* a workout with steady-state `HRV_RMSSD` in `2 - 6 ms` and a transient warmup or artifact spike (`45 ms`), *When* Y-axis bounds are computed, *Then* artifact exclusion / P95 spike-ratio gating keeps `dataMax = 7.0` so the steady-state curve is not flattened along `0`.
- *Given* a low-intensity or resting recording genuinely spanning `40 - 120 ms` (or `120 - 180 ms`), *When* `TelemetryMetricGraph(HRV_RMSSD)` renders, *Then* `dataMax` expands smoothly (`140.0` / `220.0`, `>= max * 1.15`) with zero clipping.
- *Given* empty, all-zero, single-point, negative/NaN, or extreme `Double.MAX_VALUE` inputs, *When* bounds and labels are computed, *Then* `calculateHrvRmssdYAxisBounds` returns finite bounds (`>= 5.0`) and `formatHrvRmssdAxisLabel` normalizes `-0.0` and non-finite inputs cleanly.

### Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: `REQ-PER-035.1` (`docs/requirements/req_aftermath_periods.md`, `ATT-3544`) targeting `TelemetryMetricGraph.kt`.
- **Historical Origin & Commit Trace**: Introduced in `ATT-3544` (`3db57718`, `0.0 to (max * 1.15).coerceAtLeast(100.0)`); palette refreshed in `ATT-7759` (`3ce94b53`).
- **Root Reason for Existing Formulation**: `ATT-3544` copied `CADENCE`'s `100.0` floor assuming resting morning HRV scales (`30-100+ ms`) rather than exercise rMSSD (`2-15 ms`).
- **Preservation of Core Invariants**: Preserves `dataMin = 0.0`, non-inverted upward Y orientation (`isInvertedTelemetryYAxis(HRV_RMSSD) == false`), `> 5.0%` artifact hatching (`REQ-PER-035.2`), `50.dp`/`52.dp` alignment (`Rule 64`), `TelemetryMetricUtils.formatValue` (`"%.1f ms"`), and all other metric scaling formulas.

---

## 2. Test Specification (`TST-HRV-047`)
- **`TST-HRV-047.1` (`TelemetryZoneMathTest.kt` — Dynamic Bounds, Outlier Guard & Label Unit Tests)**: Verify `calculateHrvRmssdYAxisBounds` across: exercise `1.89-5.64 ms -> (0.0, 7.0)`, moderate `8-45 ms -> (0.0, 60.0)`, spike `45 ms` over `2-6 ms` steady-state (`N >= 5`) -> `(0.0, 7.0)`, genuine resting `40-120 ms -> (0.0, 140.0)` (`>= max * 1.15`) and `120-180 ms -> (0.0, 220.0)`, empty/zero/single-value `[3.2] -> (0.0, 5.0)` & `[120.0] -> (0.0, 140.0)`, `NaN`/`Infinity`/negative filtering, and `Double.MAX_VALUE` overflow safety. Verify `formatHrvRmssdAxisLabel` (`0.0 -> "0"`, `-0.0 -> "0"`, `7.0 -> "7"`, `6.5 -> "6.5"`/`"6,5"`, `NaN`/`Infinity -> "0"`).
- **`TST-HRV-047.2` (`TelemetryMetricGraphHrvUiTest.kt` — Portrait & Landscape Spatial Spread + Spike/Artifact Resilience)**: Using `createComposeRule()` (`0` `src/main` reads), render `TelemetryMetricGraph(HRV_RMSSD)` for exercise `1.89-5.64 ms` and spike-contaminated (`2-6 ms` + `45 ms` artifact/warmup spike) datasets in portrait (`isLandscape = false`) and landscape (`isLandscape = true` with `AlignedMarginalLinearDistribution`): assert TEAL curve and marginal histogram pixels appear in upper-middle/upper vertical chart bands (not crushed into bottom 10%) and distribute across multiple histogram bands, while resting `40-120 ms` renders cleanly without clipping.
- **`TST-HRV-047.3` (`TelemetryMetricGraphHrvUiTest.kt` — Rule 8.5 Light/Dark PNG & Legacy Asset Integrity)**: Preserve `docs/engineering/walkthroughs/img/ATT-8710_dfa_a1_zone_graph.png` and `ATT-8953_dfa_a1_inverted_yaxis.png` untouched; capture deterministic side-by-side Light & Dark PNG (`1.89-5.64 ms` exercise rMSSD) strictly to `docs/engineering/walkthroughs/img/ATT-9171_rmssd_dynamic_yaxis.png` and inspect via `read_file`.
- **`TST-HRV-047.4` (Tier 1 Targeted Regression & Contracts)**: Run `./gradlew :app:testDebugUnitTest --rerun --tests "*TelemetryZoneMathTest" --tests "*TelemetryMetricGraphHrvUiTest" --tests "*TelemetryMetricGraphHrvTest" --tests "*TelemetryMetricGraphZoneTest" --tests "*ArchitectureConformanceContractTest" --tests "*DeadCodeEliminationContractTest" --tests "*SourceTextReadingAuditContractTest"` verifying `TelemetryMetricGraph.kt <= 1,965` LoC, `private fun` `@Preview`s, `0` `src/main` reads, and `0` regressions on `HRV_DFA_A1` / `HEART_RATE`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-HRV-047.1` | Unit (Math) | `TelemetryZoneMath` (bounds & axis label) | `REQ-HRV-047.1`, `REQ-HRV-047.2` | Specified |
| `TST-HRV-047.2` | Compose UI | `TelemetryMetricGraph` (Portrait + Landscape) | `REQ-HRV-047.2`, `REQ-HRV-047.3` | Specified |
| `TST-HRV-047.3` | Rule 8.5 PNG | `ATT-9171_rmssd_dynamic_yaxis.png` | `REQ-HRV-047.3`, `REQ-HRV-047.4` | Specified |
| `TST-HRV-047.4` | Contracts | LoC, Dead-Code, Source-Read & HRV Suites | `REQ-HRV-047.4` | Specified |
