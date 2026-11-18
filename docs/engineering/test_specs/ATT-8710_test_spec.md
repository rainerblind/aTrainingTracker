# Stage 2: Requirement & Test Specification - ATT-8710

**DOC-ID**: `DOC-TST-ATT-8710` | **Ticket**: [ATT-8710](https://rainerblind.atlassian.net/browse/ATT-8710) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-18_01_Früh` | **Branch**: `feature/ATT-8710`  
**Requirement Mapping**: `REQ-HRV-044` | **Test Spec ID**: `TST-HRV-044` | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-HRV-044`)
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration | **Localization**: `N/A - no UI strings`
- **Rationale**: `TelemetryMetricGraph` (`HRV_DFA_A1`) renders dashed `T1`/`T2` lines without 3-zone bands or zone-colored curve/fill/histogram/cursor, risks `52.dp` margin overlap, lacks `(dataMin, dataMax)` dash filtering, and `HrvAlpha1ScatterChart.kt:384` ignores custom `t1`/`t2`.
1. **`REQ-HRV-044.1` (Inverted 3-Zone Bands & Styling)**: `TelemetryZoneMath` SHALL partition `[dataMin, dataMax]` into Aerob (`α1 > T1`, `TTColor.Zone2` top), Schwelle (`T2 <= α1 <= T1`, `TTColor.Zone3` middle), and Anaerob (`α1 < T2`, `TTColor.Zone4` bottom) with `HrvZoneIndicator` polarity (`α1 == T1 -> Zone3`). `TelemetryMetricGraph` (`HRV_DFA_A1`) SHALL render horizontal bands, `Brush.verticalGradient` stroke/fill mapped to `valueToY(T1)`/`valueToY(T2)` (preserving `> 5.0%` artifact breaks), single-point dot, cursor dot/halo, and zone-colored `nonZonedHistogram` bins.
2. **`REQ-HRV-044.2` (52.dp Margin Disjointness)**: In portrait (`!isLandscape`), right-hand zone labels (`band.label`) SHALL render only when `zoneDistribution == null && nonZonedHistogram.isEmpty()`, preventing overlap with `nonZonedHistogram` inside the `52.dp` strip.
3. **`REQ-HRV-044.3` (Dynamic `T1`/`T2` Propagation)**: `dfaA1ThresholdT1`/`T2` from `TuningPreferencesDataStore` SHALL propagate through `TelemetryMetricGraph`, `TelemetryZoneMath`, and `HrvAlpha1ScatterChart.kt:384` (`HrvZoneIndicator.calculateZoneColor(p.alpha1, t1, t2)`).
4. **`REQ-HRV-044.4` (Threshold Dash Filtering)**: `calculateHrvDfaA1ThresholdDashes` SHALL filter `T1`/`T2` strictly to `(dataMin, dataMax)` matching `calculateThresholdDashes` (excluding `MAX_DFA_A1_THRESHOLD = 1.50f > 1.4`).
5. **`REQ-HRV-044.5` (Invariants)**: Preserve `HEART_RATE`/`POWER` solid curves (`REQ-UI-230`), `50.dp`/`52.dp` padding, `TrackingHrvZoneIndicatorTest`, and zero `src/main` file reads in tests (`REQ-PRO-049.1`). If any localized label is added, update all 9 `values*` locales.

## 2. Requirement Archaeology
1. *Original Requirement ID & Target*: `REQ-PER-035.1`, `REQ-PER-035.3`, `REQ-UI-230`, `REQ-TRK-061.3` (`TelemetryZoneMath.kt`, `TelemetryMetricGraph.kt`, `TelemetryMetricUtils.kt`, `HrvAlpha1ScatterChart.kt`).
2. *Historical Origin & Commit Trace*: `3db57718` (`ATT-3544`) added `HRV_DFA_A1` graph/scatter; `5deec7cd` (`ATT-3554`) and `ATT-6717` wired `dfaA1ThresholdT1`/`T2`.
3. *Root Reason for Existing Formulation*: `ATT-3544` focused on centered recompute, `[0.2, 1.4]` clamping, and artifact hatching, leaving `zoneBands` unimplemented for `HRV_DFA_A1` and `HrvAlpha1ScatterChart` on default thresholds.
4. *Preservation of Core Invariants*: Solid `HR`/`Power` strokes, `[0.2, 1.4]` clamping, `> 5.0%` artifact hatching, `50.dp`/`52.dp` margins, and `α1 == T1 -> Zone3` remain unchanged.

## 3. Acceptance Criteria
- *Given* `HRV_DFA_A1` trackpoints with custom `T1`/`T2` in `(0.2, 1.4)`, *When* rendering `TelemetryMetricGraph`, *Then* 3 inverted bands, zone-gradient curve/fill, filtered dashes, disjoint `52.dp` margin, and zone-colored cursor dot render in Light/Dark/AMOLED.
- *Given* `T1 = 1.50f > 1.4`, *When* computing dashes/bands on `[0.2, 1.4]`, *Then* `T1` dash is omitted and top band clamps to `1.4`.
- *Given* custom `t1`/`t2` in `HrvAlpha1ScatterChart`, *When* rendering dots, *Then* colors match `HrvZoneIndicator.calculateZoneColor(p.alpha1, t1, t2)`.

## 4. Test Specification (`TST-HRV-044`)
- **`TST-HRV-044.1` (`TelemetryZoneMathTest.kt`)**: Verify `calculateHrvDfaA1ZoneBands`, `determineHrvDfaA1Zone` (`α1 == T1 -> Zone3`), and `calculateHrvDfaA1ThresholdDashes` clamping (`T1 = 1.50f > 1.4` excluded).
- **`TST-HRV-044.2` (`HrvAlpha1ScatterChartTest.kt`)**: Verify `HrvAlpha1ScatterChart` passes dynamic `t1`/`t2` to `HrvZoneIndicator.calculateZoneColor` (custom vs default thresholds).
- **`TST-HRV-044.3` (`TelemetryMetricGraphHrvTest.kt`)**: Behavioral `createComposeRule()` Robolectric UI tests (`0` `src/main` reads) verifying Light/Dark `HRV_DFA_A1` zone bands, gradient curve/cursor, `nonZonedHistogram` margin disjointness, `HR`/`Power` invariance, and `Rule 8.5` PNG inspection.
- **`TST-HRV-044.4` (Tier 1 Regression)**: `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryZoneMathTest" --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphHrvTest" --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphZoneTest" --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphColorTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.HrvAlpha1ScatterChartTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.TrackingHrvZoneIndicatorTest"`.

## 5. Traceability Matrix
| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-HRV-044.1` | Unit (Math) | `TelemetryZoneMath` | `REQ-HRV-044.1`, `REQ-HRV-044.4` | Specified |
| `TST-HRV-044.2` | Unit / UI | `HrvAlpha1ScatterChart` | `REQ-HRV-044.3` | Specified |
| `TST-HRV-044.3` | Compose UI & PNG | `TelemetryMetricGraph` (`HRV_DFA_A1`) | `REQ-HRV-044.1`..`5`, `REQ-PRO-049.1` | Specified |
| `TST-HRV-044.4` | Regression | Tier 1 Map/HRV Suite | `REQ-HRV-044.5`, `REQ-PRO-001` | Specified |
