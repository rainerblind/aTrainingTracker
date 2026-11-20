# Stage 3: Implementation Plan - ATT-9171: Dynamic Outlier-Resilient rMSSD Telemetry Graph Y-Axis Scaling

**Ticket**: [ATT-9171](https://rainerblind.atlassian.net/browse/ATT-9171) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-19_03_Nacht` | **Branch**: `feature/ATT-9171`  
**Requirement Mapping**: `REQ-HRV-047` (`REQ-HRV-047.1`..`REQ-HRV-047.4`, refines `REQ-PER-035.1`) | **Test Mapping**: `TST-HRV-047` (`TST-HRV-047.1`..`TST-HRV-047.4`)  
**Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-19

---

## 1. Problem Description & Background
In Workout Details (`TelemetryChartStack`), `TelemetryMetricGraph(HRV_RMSSD)` hardcodes its Y-axis ceiling via `0.0 to (max * 1.15).coerceAtLeast(100.0)` (`TelemetryMetricGraph.kt:359-361`). Exercise rMSSD (`1.89-5.64 ms`) is crushed flat along `0` (>94% wasted vertical space) and its marginal histogram collapses into one bottom bin, while transient warmup/artifact spikes (`45 ms` over `2-6 ms`) similarly squash steady-state exercise curves. Stage 4 implements `REQ-HRV-047` with pure dynamic outlier-resilient bounds, artifact-rate exclusion, clean axis labels, and full portrait/landscape verification.

## 2. Traceability & Requirements Mapping
- `REQ-HRV-047.1` <-> `TST-HRV-047.1`: `TelemetryZoneMath.calculateHrvRmssdYAxisBounds(values)` — finite `>= 0.0` filter, `5.0 ms` floor, `N >= 5` nearest-rank `p95` spike-ratio guard (`p95 > 0.0 && max > p95 * 2.0`), nice steps (`1/2/5/10/20`), and `Double.MAX_VALUE` overflow safety.
- `REQ-HRV-047.2` <-> `TST-HRV-047.1`, `TST-HRV-047.2`: `TelemetryZoneMath.formatHrvRmssdAxisLabel(value, locale)` (`-0.0`/non-finite -> `"0"`, clean integer / `%.1f`) + `TelemetryMetricGraph.kt` wiring (`pathPoints` & `rawValues` in `remember(...)` keys, `cleanPrimary.ifEmpty { validValues } + overlayValues` precedence).
- `REQ-HRV-047.3` <-> `TST-HRV-047.2`, `TST-HRV-047.3`: Portrait (`nonZonedHistogram`) & landscape (`AlignedMarginalLinearDistribution`) curve/histogram multi-bin rescaling + invariance of all other metrics (`HRV_DFA_A1`, `HEART_RATE`, `POWER`, `CADENCE`, `PACE`, etc.).
- `REQ-HRV-047.4` <-> `TST-HRV-047.3`, `TST-HRV-047.4`: `TelemetryMetricGraph.kt <= 1,965` LoC, `private fun` `@Preview`, `0` `src/main` test reads, `ATT-8710` & `ATT-8953` PNGs untouched, new Rule 8.5 PNG strictly at `docs/engineering/walkthroughs/img/ATT-9171_rmssd_dynamic_yaxis.png`.

## 3. System Invariants & Preserved Behavior
1. **Gate 3 Code Lock (`Rule 4.1`)**: Zero edits to `app/` until `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` returns `GATE_PASSED`.
2. **Monolith & Boundary Ceilings**: `TelemetryMetricGraph.kt` MUST stay `<= 1,965` LoC (`1,799` LoC current; projected `~1,835` LoC); `0` edits to `TelemetryMetricUtils.kt` (`408` LoC), `TelemetryHistogramCalculator.kt` (`468` LoC), `WorkoutSummary.kt`, `values*/strings_*.xml` (`Localization Parity: N/A - no UI strings`), `ANT-Android-SDKs`, and `docs/architecture*.md`.
3. **Non-Target Metric & Contract Safety**: All non-`HRV_RMSSD` bounds/orientations (`HRV_DFA_A1` `[0.2, 1.4]` inverted, `HEART_RATE`, `POWER`, `CADENCE`, `SPEED`, `PACE`, `SLOPE`, `ALTITUDE`, `W_PRIME_BAL`), `> 5.0%` artifact hatching (`REQ-PER-035.2`), `50.dp`/`52.dp` margins (`Rule 64`), and `mapTelemetryValueToY` remain 100% unchanged. New `@Preview` strictly `private fun`; tests perform `0` `src/main` reads.

## 4. Proposed Architectural Changes
- **Component 1 (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryZoneMath.kt`, `475` LoC)**:
  - Add `const val MIN_HRV_RMSSD_Y_CEILING = 5.0` and `fun calculateHrvRmssdYAxisBounds(values: List<Double>): Pair<Double, Double>`:
    1. Filter `finiteValues = values.filter { it.isFinite() && it >= 0.0 }`; if empty return `0.0 to MIN_HRV_RMSSD_Y_CEILING`.
    2. Sort ascending (`sorted`), `max = sorted.last()`; if `sorted.size >= 5`, compute `p95 = sorted[floor(0.95 * (sorted.size - 1)).toInt()]`, else `p95 = max`.
    3. `base = if (sorted.size >= 5 && p95 > 0.0 && max > p95 * 2.0) p95 else max`.
    4. `scaled = base * 1.15`; `rawUpper = (if (scaled.isFinite()) scaled else base).coerceAtLeast(MIN_HRV_RMSSD_Y_CEILING)`.
    5. Nice step: `step = when { rawUpper <= 10.0 -> 1.0; rawUpper <= 20.0 -> 2.0; rawUpper <= 50.0 -> 5.0; rawUpper <= 100.0 -> 10.0; else -> 20.0 }`.
    6. `rounded = ceil(rawUpper / step) * step`; `niceCeiling = if (rounded.isFinite()) rounded else rawUpper`; return `0.0 to niceCeiling`.
  - Add `fun formatHrvRmssdAxisLabel(value: Double, locale: Locale = Locale.getDefault()): String`: if `!value.isFinite()` return `"0"`; normalize `val clean = if (abs(value) < 1e-9) 0.0 else value`; if `abs(clean - round(clean)) < 1e-6` return `"${round(clean).toLong()}"` else `String.format(locale, "%.1f", clean)`.
- **Component 2 (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt`, `1,799` LoC)**:
  - Add `pathPoints` and `rawValues` to the `(dataMin, dataMax)` `remember(...)` key list (`L282-291`) so artifact-rate or point updates always recompute bounds cleanly.
  - In `TelemetryMetricType.HRV_RMSSD` branch (`L359-361`): build `cleanPrimary = pathPoints.indices.mapNotNull { i -> val v = rawValues[i]; if (v != null && (pathPoints[i].hrvArtifactRate == null || pathPoints[i].hrvArtifactRate <= 5.0)) v else null }`, build `overlayValues` from `activeOverlaySeries`, and delegate `TelemetryZoneMath.calculateHrvRmssdYAxisBounds(cleanPrimary.ifEmpty { validValues } + overlayValues)`.
  - In `maxLabel`/`minLabel` (`L811-822`): add `TelemetryMetricType.HRV_RMSSD -> TelemetryZoneMath.formatHrvRmssdAxisLabel(dataMax / dataMin, currentLocale)`.
  - Add concise `private fun TelemetryMetricGraphRmssdDynamicPreview()` (`@Preview`, keeping file `<= 1,965` LoC).
- **UI Consistency (`docs/design_guidelines.md`)**: Reuses existing `TelemetryMetricGraph` Canvas styling, `TelemetryGraphColor.TEAL` accent token, `startPadding = 50.dp` / `endPadding = 52.dp` (`Rule 64`), and zero new interactive controls or strings.

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Gate 3 Lock & `TelemetryZoneMath.kt` — `REQ-HRV-047.1`, `REQ-HRV-047.2`)**: Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>`, implement `calculateHrvRmssdYAxisBounds` & `formatHrvRmssdAxisLabel` with KDoc.
2. **Step 2 (`TelemetryMetricGraph.kt` — `REQ-HRV-047.2`, `REQ-HRV-047.3`, `REQ-HRV-047.4`)**: Add `pathPoints`/`rawValues` to `remember(...)` keys, wire artifact-excluded `HRV_RMSSD` bounds (`cleanPrimary.ifEmpty { validValues } + overlayValues`), wire `formatHrvRmssdAxisLabel`, add `private fun` `@Preview`, verify `wc -l <= 1,965`.
3. **Step 3 (Pure-JVM Unit Tests in `TelemetryZoneMathTest.kt` — `TST-HRV-047.1`)**: Test `calculateHrvRmssdYAxisBounds` (`1.89-5.64 -> (0,7)`, `8-45 -> (0,60)`, `2-6 + 45 spike -> (0,7)`, `40-120 -> (0,140)`, `120-180 -> (0,220)`, empty/zero/single `[3.2]` & `[120.0]`, `NaN`/`Infinity`/negative, `Double.MAX_VALUE` overflow) and `formatHrvRmssdAxisLabel` (`0.0`, `-0.0`, `7.0`, `6.5` EN/DE, `NaN`, `Infinity`).
4. **Step 4 (Behavioral Compose Spatial Spread & Rule 8.5 PNG in `TelemetryMetricGraphHrvUiTest.kt` — `TST-HRV-047.2`, `TST-HRV-047.3`)**:
   - Guard legacy `ATT-8953` test write via temp file / existence assertion so BOTH `docs/engineering/walkthroughs/img/ATT-8710_dfa_a1_zone_graph.png` and `ATT-8953_dfa_a1_inverted_yaxis.png` remain 100% untouched (`git status` check mandatory).
   - Add portrait (`isLandscape = false`) + landscape (`isLandscape = true` with `AlignedMarginalLinearDistribution`) spatial pixel tests for exercise `1.89-5.64 ms`, spike-contaminated (`2-6 ms` + `45 ms` warmup & `> 5%` artifact spike), and resting `40-120 ms`, asserting TEAL curve & histogram pixels span upper-middle/upper vertical bands across multiple bins (`0` `src/main` reads).
   - Capture deterministic side-by-side Light & Dark PNG strictly to `docs/engineering/walkthroughs/img/ATT-9171_rmssd_dynamic_yaxis.png`.
5. **Step 5 (Locked Incremental Verification, Rule 8.5 Inspection & Living Docs Sync — `TST-HRV-047.4`)**:
   - Run under lock (strictly `--rerun`, NEVER `--rerun-tasks`):
     `python3 tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --rerun --tests "*TelemetryZoneMathTest" --tests "*TelemetryMetricGraphHrvUiTest" --tests "*TelemetryMetricGraphHrvTest" --tests "*TelemetryMetricGraphZoneTest" --tests "*ArchitectureConformanceContractTest" --tests "*DeadCodeEliminationContractTest" --tests "*SourceTextReadingAuditContractTest"`
   - Verify `git status` shows ONLY `ATT-9171_rmssd_dynamic_yaxis.png` (legacy PNGs untouched), inspect PNG via `read_file` against the 4-point Rule 8.5 checklist, promote `REQ-HRV-047` & `TST-HRV-047` to `[Implemented]` with `--sync-indexes`, and verify `verify_requirement_governance.py` exits `0`.

## 6. Verification & Rollback Plan
- **Verification**: 100% pass rate on Tier 1 + contract suites (`TST-HRV-047.1`..`.4`), Rule 8.5 Light/Dark PNG `read_file` audit, and `verify_requirement_governance.py` exit `0`.
- **Rollback**: Isolated `feature/ATT-9171` branch allows clean single-branch revert without impacting `sprint/2026-11-19_03_Nacht` or `develop`.
