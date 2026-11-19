# Stage 2: Requirement & Test Specification - ATT-8953

**Ticket**: [ATT-8953](https://rainerblind.atlassian.net/browse/ATT-8953) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-19_02_Spät` | **Branch**: `feature/ATT-8953`  
**Requirement Mapping**: `REQ-HRV-046` (refines `REQ-HRV-044.1`) | **Test Spec ID**: `TST-HRV-046` | **Date**: 2026-11-19

---

## 1. Requirement Specification (`REQ-HRV-046`)

### 1.1 Attributes & System Traceability
- **System Requirement(s)**: Derived (SW-only) — Inverted Y-axis orientation for Workout Details `HRV_DFA_A1` telemetry graph and marginal histograms (`0.20` Anaerob top -> `1.40` Aerob bottom), refining `REQ-HRV-044.1` and `REQ-PER-035.3` (zero `SYS.2`/`SYS.3` impact).
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration
- **Localization Parity**: `N/A - no UI strings`.

### 1.2 Functional & Architectural Requirements
1. **`REQ-HRV-046.1` (Shared Inverted Y-Axis Mapping Across All 3 Canvases)**: `TelemetryZoneMath` SHALL provide a shared pure Y-inversion & coordinate helper (`isInvertedTelemetryYAxis(metricType)` returning `true` for `PACE` and `HRV_DFA_A1`; `mapTelemetryValueToY(value, dataMin, dataMax, topPaddingPx, chartHeightPx, isInverted)`) reused across all 3 `valueToY` canvases in `TelemetryMetricGraph.kt` (`TelemetryMetricGraph`, `AlignedMarginalLinearDistribution`, and `AlignedMarginalZoneDistribution`), mapping `dataMin` (`0.20`, Anaerob) to `topPaddingPx` (top) and `dataMax` (`1.40`, Aerob) to `topPaddingPx + chartHeightPx` (bottom).
2. **`REQ-HRV-046.2` (Inverted Vertical Gradient Stops & Exact Boundary Inequalities)**: `TelemetryZoneMath.buildHrvDfaA1VerticalGradientStops` SHALL map stop `0f` (top) to `dataMin` and `1f` (bottom) to `dataMax` with monotonically non-decreasing stops (`effT2` at `(effT2 - dataMin) / range`, then `effT1` at `(effT1 - dataMin) / range`). Boundary color resolution MUST satisfy: `topBaseColor = when { dataMin < effT2 -> Zone4; dataMin < effT1 -> Zone3; else -> Zone2 }` and `bottomBaseColor = when { dataMax > effT1 -> Zone2; dataMax > effT2 -> Zone3; else -> Zone4 }`, cleanly handling exact bounds (`T2 = 0.20`, `T1 = 1.40`), degenerate `T1 == T2` (direct `Zone4 -> Zone2`), and out-of-range thresholds (`T1 = 1.50f`, `T2 = 0.15f`).
3. **`REQ-HRV-046.3` (Inverted Y-Axis Labels, Zone Bands, Fill, Dashes, Histograms & Cursor)**: For `HRV_DFA_A1`, `TelemetryMetricGraph` SHALL render `dataMin` (`0.20`) at the top Y-axis label (`maxLabel`) and `dataMax` (`1.40`) at the bottom Y-axis label (`minLabel`), render Anaerob (`Zone4` red) at top / Schwelle (`Zone3` orange) middle / Aerob (`Zone2` green) at bottom across zone bands, gradient curve/fill (filling down to bottom `baselineY` = `1.40` easy baseline), threshold dashes/labels, cursor/single-point dots, and portrait/landscape marginal histograms. `HrvAlpha1ScatterChart.kt` SHALL remain untouched in standard Cartesian orientation (`REQ-PER-035.4`).
4. **`REQ-HRV-046.4` (Ceilings, Asset Integrity & Private Previews)**: `TelemetryMetricGraph.kt` MUST stay `<= 1,965` LoC (`ArchitectureConformanceContractTest`), any added `@Preview` MUST be `private fun` (`DeadCodeEliminationContractTest`), tests MUST perform `0` `src/main` reads (`SourceTextReadingAuditContractTest`), and historical `docs/engineering/walkthroughs/img/ATT-8710_dfa_a1_zone_graph.png` MUST remain untouched while capturing the new Light/Dark PNG to `docs/engineering/walkthroughs/img/ATT-8953_dfa_a1_inverted_yaxis.png`.

### 1.3 Acceptance Criteria (Given-When-Then)
- *Given* `HRV_DFA_A1` samples spanning `[0.2, 1.4]` with `T1 = 0.75`, `T2 = 0.50`, *When* rendering `TelemetryMetricGraph` (portrait or landscape), *Then* `0.20` appears at top, `1.40` at bottom, Anaerob `Zone4` red occupies top `[0%..25%]`, Schwelle `Zone3` orange occupies middle `[25%..45.8%]`, Aerob `Zone2` green occupies bottom `[45.8%..100%]`, and curve/fill/cursor/histogram align 1:1 with the inverted Y-axis.
- *Given* edge thresholds (`T1 == T2 = 0.70`, exact `T2 = 0.20`/`T1 = 1.40`, out-of-range `T1 = 1.50f`/`T2 = 0.15f`), *When* computing `buildHrvDfaA1VerticalGradientStops`, *Then* stops remain monotonic in `[0f, 1f]` with exact inverted `Zone4`/`Zone3`/`Zone2` colors.
- *Given* `HEART_RATE`, `POWER`, `PACE`, or `HrvAlpha1ScatterChart`, *When* rendered, *Then* all existing orientations, solid strokes (`REQ-UI-230`), `[0.2, 1.4]` bounds, and `> 5.0%` artifact hatching remain intact.

### Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: `REQ-HRV-044` (`REQ-HRV-044.1`, `TST-HRV-044.1`, `ATT-8710`) in `docs/requirements/req_aftermath_periods.md` and `docs/tests/tst_aftermath_periods.md`, targeting `TelemetryZoneMath.kt` and `TelemetryMetricGraph.kt`.
- **Historical Origin & Commit Trace**: Introduced in `ATT-8710` (`886eae05`), refining `REQ-PER-035` (`ATT-3544`, `3db57718`).
- **Root Reason for Existing Formulation**: `ATT-8710` added 3-zone bands and vertical gradient onto `TelemetryMetricGraph`'s pre-existing non-inverted Y-axis (`Zone2` top, `Zone4` bottom) without inverting `valueToY`, causing high effort to plot downward unlike stacked HR, Power, and inverted `PACE`.
- **Preservation of Core Invariants**: Reconciling `REQ-HRV-044.1` / `TST-HRV-044.1` to inverted band placement preserves `[0.2, 1.4]` clamping, `α1 == T1 -> Zone3` boundary polarity, `> 5.0%` artifact hatching, `50.dp`/`52.dp` alignment (`Rule 64`), `HEART_RATE`/`POWER` solid curves, `PACE` inversion, and `HrvAlpha1ScatterChart` Cartesian axes.

---

## 2. Test Specification (`TST-HRV-046`)
- **`TST-HRV-046.1` (`TelemetryZoneMathTest.kt`)**: Verify `isInvertedTelemetryYAxis` (`PACE`/`HRV_DFA_A1` true; `HEART_RATE`/`POWER`/`SPEED`/`HRV_RMSSD` false), `mapTelemetryValueToY` exact coordinates (`0.20 -> top`, `0.50 -> 25%`, `0.75 -> 45.83%`, `1.40 -> bottom`), and `buildHrvDfaA1VerticalGradientStops` across default (`0.75/0.50`), degenerate (`T1 == T2 = 0.70`), exact boundary (`T2 = 0.20`, `T1 = 1.40`), and out-of-range (`T1 = 1.50f`/`T2 = 0.50f`, `T1 = 1.50f`/`T2 = 0.15f`) inputs.
- **`TST-HRV-046.2` (`TelemetryMetricGraphHrvUiTest.kt` — Spatial Portrait & Landscape Inversion)**: Using `createComposeRule()` (`0` `src/main` reads), render `TelemetryMetricGraph(HRV_DFA_A1)` in portrait (`isLandscape = false`) AND landscape (`isLandscape = true` with `AlignedMarginalLinearDistribution`): assert top canvas band contains `Zone4` red (and NOT `Zone2` green) while bottom canvas band contains `Zone2` green (and NOT `Zone4` red), both in the main plot and in the right-hand marginal histogram.
- **`TST-HRV-046.3` (`TelemetryMetricGraphHrvUiTest.kt` — Rule 8.5 Light/Dark PNG & Legacy Asset Preservation)**: Keep `docs/engineering/walkthroughs/img/ATT-8710_dfa_a1_zone_graph.png` completely untouched (legacy test writes to temp/in-memory check), capture side-by-side Light & Dark PNG to `docs/engineering/walkthroughs/img/ATT-8953_dfa_a1_inverted_yaxis.png`, and inspect via `read_file`.
- **`TST-HRV-046.4` (`TelemetryMetricGraphHrvUiTest.kt` & `HrvAlpha1ScatterChartUiTest.kt` — Invariance)**: Assert `T1 = 1.50f` emits `Zone4` top / `Zone3` bottom without `Zone2`, `HEART_RATE` preserves solid `CRIMSON` stroke, and `HrvAlpha1ScatterChart` remains unchanged.
- **`TST-HRV-046.5` (Tier 1 Regression & Contracts)**: `./gradlew :app:testDebugUnitTest --rerun --tests "*TelemetryZoneMathTest" --tests "*TelemetryMetricGraphHrvUiTest" --tests "*TelemetryMetricGraphHrvTest" --tests "*TelemetryMetricGraphZoneTest" --tests "*HrvAlpha1ScatterChartUiTest" --tests "*ArchitectureConformanceContractTest" --tests "*DeadCodeEliminationContractTest" --tests "*SourceTextReadingAuditContractTest"`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-HRV-046.1` | Unit (Math) | `TelemetryZoneMath` (Y-map & gradient stops) | `REQ-HRV-046.1`, `REQ-HRV-046.2` | Specified |
| `TST-HRV-046.2` | Compose UI | `TelemetryMetricGraph` (Portrait + Landscape) | `REQ-HRV-046.1`, `REQ-HRV-046.3` | Specified |
| `TST-HRV-046.3` | Rule 8.5 PNG | `ATT-8953_dfa_a1_inverted_yaxis.png` | `REQ-HRV-046.3`, `REQ-HRV-046.4` | Specified |
| `TST-HRV-046.4` | Regression | `HR` invariance & `HrvAlpha1ScatterChart` | `REQ-HRV-046.3`, `REQ-HRV-044` | Specified |
| `TST-HRV-046.5` | Contracts | LoC, Dead-Code & Source-Read Audit Suites | `REQ-HRV-046.4`, `REQ-PRO-129.2` | Specified |
