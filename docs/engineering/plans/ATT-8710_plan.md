# Stage 3: Implementation Plan - ATT-8710: Color DFA alpha 1 Graph by Configurable HRV Zones

**DOC-ID**: `DOC-PLN-ATT-8710` | **Ticket**: [ATT-8710](https://rainerblind.atlassian.net/browse/ATT-8710) (`2 SP`) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8710`  
**Requirement Mapping**: `REQ-HRV-044` (`REQ-HRV-044.1`–`REQ-HRV-044.5`) | **Test Mapping**: `TST-HRV-044` (`TST-HRV-044.1`–`TST-HRV-044.4`)  
**Author**: `--as agent1` | **Date**: 2026-11-18

---

## 1. Traceability & System Invariants
* **Requirements & Tests**: `REQ-HRV-044` (`docs/requirements/req_aftermath_periods.md`) $\leftrightarrow$ `TST-HRV-044` (`docs/tests/tst_aftermath_periods.md`).
* **Preserved Invariants**:
  1. `HEART_RATE` and `POWER` graphs keep solid `accentColor` strokes/fills (`REQ-UI-230`).
  2. `HRV_DFA_A1` preserves `[0.2, 1.4]` Y-clamping (`REQ-PER-035.3`), `> 5.0%` artifact polyline breaks/hatching (`REQ-PER-035.2`), `50.dp`/`52.dp` padding (`Rule 64`), and `α1 == T1 -> TTColor.Zone3` (`TrackingHrvZoneIndicatorTest`).
  3. Zero SQLite/DataStore schema changes, zero new localized UI strings (`N/A - no UI strings`), and zero `src/main` file reads in tests (`REQ-PRO-049.1`).

---

## 2. UI Consistency (`docs/design_guidelines.md`)
* **Color & Theme Tokens (§5.4)**: Exclusively reuse `TTColor.Zone2`, `TTColor.Zone3`, `TTColor.Zone4`, and `TelemetryZoneMath.ZONE_BAND_ALPHA` (`0.10f`); zero inline `Color(0x...)` literals.
* **Layout & Margins (§2.1 / `Rule 64`)**: Keep `start = 50.dp`, `end = 52.dp` (portrait) / `0.dp` (landscape) and prevent `band.label` vs. `nonZonedHistogram` overlap inside `52.dp`.
* **Previews & Verification (`Rule 8.5`)**: Verify Light/Dark/AMOLED via `createComposeRule()` and inspect composite Light/Dark PNG `docs/engineering/walkthroughs/img/ATT-8710_dfa_a1_zone_graph.png` via `read_file`; any added `@Preview` is `private fun`.

---

## 3. Atomic Step Sequence (Stage 4 Construction)

### Step 1: Inverted 3-Zone Math & Gradient Stops (`REQ-HRV-044.1`, `REQ-HRV-044.4`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryZoneMath.kt`, `TelemetryMetricUtils.kt`
* **Changes**:
  1. Add `determineHrvDfaA1Zone(alpha1: Double, t1: Float, t2: Float): Int` (`> t1 -> 1` Aerob/`Zone2`, `>= t2 -> 2` Schwelle/`Zone3`, `< t2 -> 3` Anaerob/`Zone4`) and `resolveHrvDfaA1ZoneColor(alpha1: Double, t1: Float, t2: Float): Color`.
  2. Add `calculateHrvDfaA1ZoneBands(t1: Float, t2: Float, dataMin: Double, dataMax: Double): List<TelemetryZoneBand>` clamping Anaerob (`[dataMin, T2]`, `Zone4`, `"Z3"`), Schwelle (`[T2, T1]`, `Zone3`, `"Z2"`), Aerob (`[T1, dataMax]`, `Zone2`, `"Z1"`).
  3. Add `calculateHrvDfaA1ThresholdDashes(t1: Float, t2: Float, dataMin: Double, dataMax: Double): List<Double>` filtering strictly to `it > dataMin && it < dataMax` (excluding `1.50f > 1.4`).
  4. Add `buildHrvDfaA1VerticalGradientStops(t1: Float, t2: Float, dataMin: Double, dataMax: Double, topAlpha: Float = 1f, bottomAlpha: Float = 1f): List<Pair<Float, Color>>` mapping `0f -> dataMax` (top) to `1f -> dataMin` (bottom) with monotonic step transitions at `T1`/`T2`.
  5. Optional default-`null` `dfaA1ThresholdT1`/`T2` params on `TelemetryMetricUtils.formatValue` (preserving `"0.75"` when omitted).

### Step 2: `TelemetryMetricGraph` Zone Bands, Gradient Curve & Margin Disjointness (`REQ-HRV-044.1`, `REQ-HRV-044.2`, `REQ-HRV-044.3`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt`
* **Changes**:
  1. Include `dfaA1ThresholdT1`, `dfaA1ThresholdT2` in `zoneBands` `remember` keys and compute `TelemetryZoneMath.calculateHrvDfaA1ZoneBands(dfaA1ThresholdT1, dfaA1ThresholdT2, dataMin, dataMax)` for `HRV_DFA_A1`.
  2. Update `thresholdDashes` (`L391`) to `TelemetryZoneMath.calculateHrvDfaA1ThresholdDashes(...)` and gate `L754–759` Y-axis threshold labels on `in (dataMin, dataMax)`.
  3. Guard right-margin `band.label` (`L601`) with `zoneDistribution == null && nonZonedHistogram.isEmpty() && !isLandscape`; for `HRV_DFA_A1`, recolor `nonZonedHistogram` bin colors by midpoint via `resolveHrvDfaA1ZoneColor`.
  4. For `HRV_DFA_A1` segments (`L971–1022`), render `Brush.verticalGradient` stroke & under-curve fill (`0.22f..0.04f` alpha) using `buildHrvDfaA1VerticalGradientStops` (`startY = topPaddingPx, endY = baselineY`), and color single-point dot (`L975`) + scrubbing cursor dot/halo (`L1192–1202`) via `resolveHrvDfaA1ZoneColor`. Keep `HEART_RATE`/`POWER` solid `accentColor`.

### Step 3: `HrvAlpha1ScatterChart` Dynamic Threshold Parity (`REQ-HRV-044.3`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutdetail/HrvAlpha1ScatterChart.kt`
* **Changes**: Pass `t1 = t1, t2 = t2` to `HrvZoneIndicator.calculateZoneColor(p.alpha1, t1 = t1, t2 = t2)` at `L384`.

### Step 4: Verification Tests & `Rule 8.5` Screenshots (`TST-HRV-044.1`–`TST-HRV-044.4`)
* **Files**: `TelemetryZoneMathTest.kt`, `HrvAlpha1ScatterChartTest.kt`, `TelemetryMetricGraphHrvTest.kt`
* **Changes**: Add math/clamping unit tests (`TelemetryZoneMathTest`), custom `t1`/`t2` scatter dot test (`HrvAlpha1ScatterChartTest`), and `createComposeRule()` Robolectric tests (`0` `src/main` reads) checking Light/Dark/AMOLED pixel colors, margin disjointness, `HEART_RATE` solid invariance, and exporting `docs/engineering/walkthroughs/img/ATT-8710_dfa_a1_zone_graph.png`.
* **Command**: `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryZoneMathTest" --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphHrvTest" --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphZoneTest" --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphColorTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.HrvAlpha1ScatterChartTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.TrackingHrvZoneIndicatorTest"`
