# Stage 1 Analysis: ATT-8953 - Invert DFA alpha 1 Graph Y-Axis in Workout Details

**Ticket**: [ATT-8953](https://rainerblind.atlassian.net/browse/ATT-8953)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-19_02_Spät` | **Branch**: `feature/ATT-8953`  
**Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-19

---

## 1. Problem Statement & Motivation
In Workout Details (`MapDetailLayout`), `TelemetryMetricGraph` (`metricType = TelemetryMetricType.HRV_DFA_A1`) currently uses standard upward Y-axis orientation (`1.40` Aerob/easy at top, `0.20` Anaerob/hard at bottom). Because `TelemetryMetricGraph(HRV_DFA_A1)` stacks directly below Heart Rate, Power, and inverted `PACE` graphs sharing a synchronized X-axis, higher exercise intensity currently drops downward visually (green easy zone on top, red hard zone on bottom). Inverting the DFA $\alpha_1$ Y-axis (`0.20` Anaerob at top, `1.40` Aerob at bottom — identical to `TelemetryMetricType.PACE`) aligns visual effort direction across all stacked Workout Details charts.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Mechanism**:
  - `TelemetryMetricGraph.kt:636-646` (`valueToY`), `L1470-1478` (`AlignedMarginalLinearDistribution`), and `L1577-1585` (`AlignedMarginalZoneDistribution`) invert Y strictly for `metricType == TelemetryMetricType.PACE`, leaving `HRV_DFA_A1` on standard `(1.0 - ratio)` mapping.
  - `TelemetryMetricGraph.kt:812-826` renders `dataMax` (`1.40`) as top label (`maxLabel`) and `dataMin` (`0.20`) as bottom label (`minLabel`) for `HRV_DFA_A1`, whereas `PACE` swaps them (`dataMin` at top, `dataMax` at bottom).
  - `TelemetryZoneMath.kt:384-439` (`buildHrvDfaA1VerticalGradientStops`) maps stop `0f` (top) to `dataMax` (`Zone2` green) and `1f` (bottom) to `dataMin` (`Zone4` red), ordering stops `effT1` then `effT2` via `(dataMax - effT) / range`.
- **Independent Peer Review & Verification**:
  - Verified all cited files/lines and inspected `docs/engineering/walkthroughs/img/ATT-8710_dfa_a1_zone_graph.png` via `read_file`.
  - **All Chart Layers Audited**:
    1. *3 `valueToY` sites (`L636`, `L1470`, `L1577`)*: Including `HRV_DFA_A1` alongside `PACE` (`isInverted = metricType == PACE || metricType == HRV_DFA_A1`) automatically inverts zone bands (`L653-656`, safe via `minOf`/`abs`), portrait & landscape marginal histograms (`L760-763`, `L1489-1492`, safe via `minOf`/`abs`), threshold dashes (`L791`), threshold Y-labels (`L831`, yielding even cleaner spacing: `0.20` top -> `0.50` at 25% -> `0.75` at 45.8% -> `1.40` bottom), curve segments (`L993`), single-point dot (`L1054`), scrubbing cursor dot (`L1308`), and multi-sensor overlay curves/dots (`L1264`, `L1344`).
    2. *Fill Polygon (`L1072-1091`)*: `fillBaselineY = baselineY` (bottom = `1.40` Aerob), so higher-effort curves (`0.20` Anaerob at top) fill downward toward the easy aerobic baseline with `topAlpha = 0.22f` -> `bottomAlpha = 0.04f`, matching `PACE`, `HEART_RATE`, and `POWER`.
    3. *Gradient Stops (`TelemetryZoneMath.kt:384-439`)*: Must invert to `0f` = `dataMin` (`Zone4` red top) -> `effT2` at `(effT2 - dataMin)/range` -> `effT1` at `(effT1 - dataMin)/range` -> `1f` = `dataMax` (`Zone2` green bottom), preserving degenerate `effT1 == effT2` and out-of-range threshold clamping (`T1 = 1.50f`, `T2 = 0.15f`).
    4. *Touch Scrub/Tooltip (`L517-584`)*: Purely X-axis (`localX -> findNearestPoint`) — unaffected and remains 100% aligned.
    5. **`HrvAlpha1ScatterChart.kt` (`410` LoC) Orientation Decision**: Keep standard Cartesian orientation (`0.30` bottom -> `1.20` top, untouched). It is a scientific $\alpha_1$-vs-Intensity linear regression scatter plot (`REQ-PER-035.4`) where `y = slope * x + intercept` conventionally plots increasing $\alpha_1$ upward (negative slope dropping left-to-right); only stacked time/distance `TelemetryMetricGraph` should invert Y.
  - **Monolith LoC Ceilings (`ArchitectureConformanceContractTest.kt`)**: `TelemetryMetricGraph.kt` is `1,779` LoC vs. `1,965` LoC baseline ceiling (`186` LoC headroom); `TelemetryZoneMath.kt` is `440` LoC (`<= 1,500`); `TelemetryMetricUtils.kt` (`408` LoC) & `HrvAlpha1ScatterChart.kt` (`410` LoC) require `0` edits.
  - **Localization & Rule 8.5**: `Localization Parity: N/A - no UI strings`. Mandatory Rule 8.5 Light/Dark PNG (`docs/engineering/walkthroughs/img/ATT-8953_dfa_a1_inverted_yaxis.png`) + `private fun` `@Preview` (`DeadCodeEliminationContractTest`).

---

## 3. Scope Grounding & Non-Goals (`ATT-1250`)
- **In-Scope Goals (`2 SP` confirmed; no split needed)**:
  1. Invert `HRV_DFA_A1` Y-axis across `TelemetryMetricGraph`, `AlignedMarginalLinearDistribution`, and `AlignedMarginalZoneDistribution` (`valueToY` + Y-axis top/bottom labels `0.20` top / `1.40` bottom).
  2. Invert `TelemetryZoneMath.buildHrvDfaA1VerticalGradientStops` (`0f` = `dataMin`/Anaerob top, `1f` = `dataMax`/Aerob bottom) and add unit-testable Y-mapping helper.
  3. Update `TelemetryZoneMathTest.kt`, add spatial top-vs-bottom zone pixel assertions (portrait + landscape) and Rule 8.5 Light/Dark PNG capture in `TelemetryMetricGraphHrvUiTest.kt`, and reconcile `REQ-HRV-044` / `TST-HRV-044` with net-new `REQ-HRV-046` / `TST-HRV-046`.
- **Out-of-Scope Non-Goals**: Zero edits to `HrvAlpha1ScatterChart.kt`, `SensorSparklineView.kt`, `TelemetryMetricUtils.kt`, `HEART_RATE`/`POWER`/`PACE`/`SLOPE` orientation, DB schemas, or `values*/strings_*.xml`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: `REQ-HRV-044` (`REQ-HRV-044.1`, `TST-HRV-044.1`) in `docs/requirements/req_aftermath_periods.md` & `docs/tests/tst_aftermath_periods.md` (and `REQ-PER-035.3`), targeting `TelemetryZoneMath.kt` and `TelemetryMetricGraph.kt`.
- **Historical Origin & Commit Trace**: `REQ-PER-035` introduced `[0.2, 1.4]` clamping in `ATT-3544` (`3db57718`); `REQ-HRV-044` (`ATT-8710`, `886eae05`) added 3-zone bands & vertical gradient with `Zone2` top / `Zone4` bottom following the pre-existing non-inverted Y-axis.
- **Root Reason for Existing Formulation**: `ATT-8710` matched `TelemetryMetricGraph`'s default `(1.0 - ratio)` mapping without changing axis polarity, leaving Aerob green on top and Anaerob red on bottom (inverted relative to HR/Power/Pace effort).
- **Preservation of Core Invariants**: Preserves `[0.2, 1.4]` Y-clamping (`REQ-PER-035.3`), `> 5.0%` artifact hatching (`REQ-PER-035.2`), `startPadding = 50.dp` / `endPadding = 52.dp` (`Rule 64`), `α1 == T1 -> Zone3` polarity (`TrackingHrvZoneIndicatorTest`), `HEART_RATE`/`POWER` solid curves (`REQ-UI-230`), `PACE` inversion, and `HrvAlpha1ScatterChart` Cartesian orientation (`REQ-PER-035.4`).

---

## 5. Change-Request Impact Analysis (`SUP.10` / `REQ-PRO-064`)
- **Affected Requirements**: Refines `REQ-HRV-044.1` (`TST-HRV-044.1`) to remove top/bottom band contradiction; net-new **`REQ-HRV-046`** / **`TST-HRV-046`** requested for reservation in `docs/requirements/req_aftermath_periods.md` & `docs/tests/tst_aftermath_periods.md`. (`SYS.2`/`SYS.3` Impact Check `REQ-PRO-131.4`: **None — Derived SW-only**.)
- **Affected Architecture Elements**: `TelemetryZoneMath.kt` (`440` LoC), `TelemetryMetricGraph.kt` (`1,779` LoC; ceiling `1,965` LoC). Zero changes to `HrvAlpha1ScatterChart.kt`, `TelemetryMetricUtils.kt`, repositories, DB, or `ANT-Android-SDKs`.
- **Verification Impact & Test Surface**: `TelemetryZoneMathTest.kt`, `TelemetryMetricGraphHrvUiTest.kt` (spatial Y-band pixel verification in portrait + landscape & Rule 8.5 Light/Dark PNG), `TelemetryMetricGraphHrvTest.kt`, `TelemetryMetricGraphZoneTest.kt`, `ArchitectureConformanceContractTest.kt`, `DeadCodeEliminationContractTest.kt`, `SourceTextReadingAuditContractTest.kt`.
- **Risk Assessment & Invariants**: **LOW** risk — isolated Y-axis coordinate inversion & gradient stop ordering for `HRV_DFA_A1` matching existing `PACE` pattern.

---

## 6. System Invariants & Risk Assessment
- **Core Invariants**: `HEART_RATE`, `POWER`, `SPEED`, `PACE`, `CADENCE`, `SLOPE`, `ALTITUDE`, `W_PRIME_BAL`, `HRV_RMSSD` plotting 100% unchanged; `TelemetryMetricGraph.kt <= 1,965` LoC; `α1 > T1 -> Zone2`, `T2 <= α1 <= T1 -> Zone3`, `α1 < T2 -> Zone4` unchanged; `HrvAlpha1ScatterChart` Cartesian axis preserved; `Localization Parity: N/A - no UI strings`.
- **Risk Rating**: **LOW** — Self-contained 2 SP Canvas Y-mapping + gradient stop fix with exact pixel-level Robolectric verification.
