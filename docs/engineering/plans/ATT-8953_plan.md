# Stage 3: Implementation Plan - ATT-8953: Invert Workout Details DFA alpha 1 Graph Y-Axis

**Ticket**: [ATT-8953](https://rainerblind.atlassian.net/browse/ATT-8953) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-19_02_Spät` | **Branch**: `feature/ATT-8953`  
**Requirement Mapping**: `REQ-HRV-046` (`REQ-HRV-046.1`..`REQ-HRV-046.4`, refines `REQ-HRV-044.1`) | **Test Mapping**: `TST-HRV-046` (`TST-HRV-046.1`..`TST-HRV-046.5`)  
**Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-19

---

## 1. Problem Description & Background
In Workout Details, `TelemetryMetricGraph(HRV_DFA_A1)` stacks directly below Heart Rate, Power, and inverted `PACE` graphs (`Rule 64`) yet renders `1.40` (Aerob green) at the top and `0.20` (Anaerob red) at the bottom. Stage 4 implements `REQ-HRV-046` so all 3 `valueToY` canvases, Y-axis labels, and vertical gradient stops invert `HRV_DFA_A1` (`0.20` Anaerob top -> `1.40` Aerob bottom), matching physiological effort direction across all stacked telemetry charts while keeping `HrvAlpha1ScatterChart` untouched.

## 2. Traceability & Requirements Mapping
- `REQ-HRV-046.1` <-> `TST-HRV-046.1`, `TST-HRV-046.2`: `TelemetryZoneMath` (`isInvertedTelemetryYAxis`, `mapTelemetryValueToY`) wired into all 3 `valueToY` sites (`TelemetryMetricGraph.kt:637`, `L1471`, `L1578`).
- `REQ-HRV-046.2` <-> `TST-HRV-046.1`: `TelemetryZoneMath.buildHrvDfaA1VerticalGradientStops` inverted stop ordering (`0f = dataMin` `Zone4` -> `effT2` -> `effT1` -> `1f = dataMax` `Zone2`).
- `REQ-HRV-046.3` <-> `TST-HRV-046.2`, `TST-HRV-046.3`, `TST-HRV-046.4`: `TelemetryMetricGraph.kt` (`L813-820`) Y-axis labels (`0.20` top / `1.40` bottom), portrait+landscape spatial pixel tests, `HR` & `HrvAlpha1ScatterChart` invariance.
- `REQ-HRV-046.4` <-> `TST-HRV-046.3`, `TST-HRV-046.5`: `TelemetryMetricGraph.kt <= 1,965` LoC, `private fun` `@Preview`, `0` `src/main` test reads, `ATT-8710_dfa_a1_zone_graph.png` preserved, new Rule 8.5 PNG `ATT-8953_dfa_a1_inverted_yaxis.png`.

## 3. System Invariants & Preserved Behavior
1. **Gate 3 Code Lock (`Rule 4.1`)**: Zero edits to `app/` until `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` returns `GATE_PASSED`.
2. **Monolith & Boundary Ceilings**: `TelemetryMetricGraph.kt` MUST stay `<= 1,965` LoC (`1,779` LoC current; projected `~1,795` LoC); `0` edits to `HrvAlpha1ScatterChart.kt` (`410` LoC), `TelemetryMetricUtils.kt` (`408` LoC), `values*/strings_*.xml` (`Localization Parity: N/A - no UI strings`), `ANT-Android-SDKs`, and `docs/architecture*.md`.
3. **Bit-Identical Non-HRV Math**: `mapTelemetryValueToY` preserves `(dataMax - dataMin).coerceAtLeast(1.0)` so `HEART_RATE`, `POWER`, `SPEED`, `PACE`, `CADENCE`, `SLOPE`, `ALTITUDE`, `W_PRIME_BAL`, and `HRV_RMSSD` remain 100% bit-identical. All new `@Preview`s strictly `private fun`; tests perform `0` `src/main` reads.

## 4. Proposed Architectural Changes
- **Component 1 (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryZoneMath.kt`, `440` LoC)**:
  - Add `fun isInvertedTelemetryYAxis(metricType: TelemetryMetricType?): Boolean = metricType == TelemetryMetricType.PACE || metricType == TelemetryMetricType.HRV_DFA_A1`.
  - Add `fun mapTelemetryValueToY(value: Double, dataMin: Double, dataMax: Double, topPaddingPx: Float, chartHeightPx: Float, isInverted: Boolean): Float` preserving `val valRange = (dataMax - dataMin).coerceAtLeast(1.0)` and `ratio = ((value - dataMin) / valRange).coerceIn(0.0, 1.0)`.
  - Invert `buildHrvDfaA1VerticalGradientStops`: `0f` = `dataMin` (`topBaseColor = when { dataMin < effT2 -> Zone4; dataMin < effT1 -> Zone3; else -> Zone2 }`), `1f` = `dataMax` (`bottomBaseColor = when { dataMax <= effT2 -> Zone4; dataMax <= effT1 -> Zone3; else -> Zone2 }`), emitting `effT2` (`(effT2 - dataMin) / range`, `Zone4 -> if (effT2 < effT1) Zone3 else Zone2`) before `effT1` (`(effT1 - dataMin) / range` when `effT1 > effT2`, `Zone3 -> Zone2`) — zero phantom `Zone3` when `T1 == T2`.
- **Component 2 (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt`, `1,779` LoC)**:
  - Replace all 3 `valueToY` closures (`L636-646`, `L1470-1478`, `L1577-1585`) with calls to `TelemetryZoneMath.mapTelemetryValueToY(..., isInverted = TelemetryZoneMath.isInvertedTelemetryYAxis(metricType))`.
  - Swap `HRV_DFA_A1` Y-axis labels (`L814`, `L820`): `maxLabel` (top) renders `dataMin` (`0.20`) and `minLabel` (bottom) renders `dataMax` (`1.40`), matching `PACE`.
  - Add one concise `private fun TelemetryMetricGraphDfaA1InvertedPreview()` `@Preview` (reusing existing theme tokens; keeping file `<= 1,965` LoC).
- **UI Consistency (`docs/design_guidelines.md`)**:
  - Reuses existing `TelemetryMetricGraph` Canvas styling, `startPadding = 50.dp` / `endPadding = 52.dp` (`Rule 64`), `TTColor.Zone2`/`Zone3`/`Zone4` tokens, `ZONE_BAND_ALPHA = 0.10f`, `topAlpha = 0.22f -> bottomAlpha = 0.04f` fill to bottom baseline (`1.40`), and zero new UI controls or hardcoded `Color(0x...)` literals.

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Gate 3 Lock & `TelemetryZoneMath.kt` — `REQ-HRV-046.1`, `REQ-HRV-046.2`)**: Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>`, add `isInvertedTelemetryYAxis` & `mapTelemetryValueToY`, and invert `buildHrvDfaA1VerticalGradientStops` with updated KDoc.
2. **Step 2 (`TelemetryMetricGraph.kt` — `REQ-HRV-046.1`, `REQ-HRV-046.3`, `REQ-HRV-046.4`)**: Wire shared `mapTelemetryValueToY` into `L636`, `L1470`, and `L1577`, swap `HRV_DFA_A1` `maxLabel`/`minLabel` (`L814`, `L820`), add `private fun` `@Preview`, and verify `wc -l <= 1,965`.
3. **Step 3 (Pure-JVM Math Tests in `TelemetryZoneMathTest.kt` — `TST-HRV-046.1`)**: Update `testBuildHrvDfaA1VerticalGradientStops_monotonicAndBoundarySafe` to inverted `Zone4` (top) -> `Zone2` (bottom), and add tests for `isInvertedTelemetryYAxis`, `mapTelemetryValueToY` (`HRV_DFA_A1`/`PACE`/`HEART_RATE`), exact bounds (`T2 = 0.20`, `T1 = 1.40`), degenerate `T1 == T2`, and out-of-range (`T1 = 1.50f`/`T2 = 0.15f`).
4. **Step 4 (Behavioral Compose Spatial Pixel Tests & Rule 8.5 PNG in `TelemetryMetricGraphHrvUiTest.kt` — `TST-HRV-046.2`, `TST-HRV-046.3`, `TST-HRV-046.4`)**:
   - Redirect legacy `ATT-8710` test check to in-memory/temp verification so historical `docs/engineering/walkthroughs/img/ATT-8710_dfa_a1_zone_graph.png` remains 100% untouched.
   - Add spatial top-vs-bottom band pixel assertions for BOTH portrait (`isLandscape = false`) and landscape (`isLandscape = true` with `AlignedMarginalLinearDistribution`), proving `Zone4` red is at top (not bottom) and `Zone2` green is at bottom (not top) across main plot & marginal histogram, with `0` `src/main` reads.
   - Capture deterministic side-by-side Light & Dark PNG strictly to `docs/engineering/walkthroughs/img/ATT-8953_dfa_a1_inverted_yaxis.png`.
5. **Step 5 (Rule 7.5 Incremental Verification, Rule 8.5 Visual Inspection & Living Docs Sync — `TST-HRV-046.5`)**:
   - Run under lock (strictly `--rerun`, NEVER `--rerun-tasks`, NEVER `-PstampGitHash=true`):
     `./gradlew :app:testDebugUnitTest --rerun --tests "*TelemetryZoneMathTest" --tests "*TelemetryMetricGraphHrvUiTest" --tests "*TelemetryMetricGraphHrvTest" --tests "*TelemetryMetricGraphZoneTest" --tests "*HrvAlpha1ScatterChartUiTest" --tests "*ArchitectureConformanceContractTest" --tests "*DeadCodeEliminationContractTest" --tests "*SourceTextReadingAuditContractTest"`
   - Inspect `docs/engineering/walkthroughs/img/ATT-8953_dfa_a1_inverted_yaxis.png` via `read_file` against the 4-point Rule 8.5 checklist and promote `REQ-HRV-046` & `TST-HRV-046` to `[Implemented]` with `--sync-indexes`.

## 6. Verification & Rollback Plan
- **Verification**: 100% pass rate on Tier 1 + contract suites (`TST-HRV-046.1`..`.5`), Rule 8.5 Light/Dark PNG `read_file` audit, live `emulator-5554` verification in Stage 6, and `verify_requirement_governance.py` exit `0`.
- **Rollback**: Isolated `feature/ATT-8953` branch allows clean single-branch revert without impacting `sprint/2026-11-19_02_Spät` or `develop`.
