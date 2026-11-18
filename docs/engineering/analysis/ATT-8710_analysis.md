# Stage 1 Analysis: ATT-8710 - Color DFA alpha 1 Graph by Configurable Aerob / Schwelle / Anaerob Zones

**DOC-ID**: `DOC-ANA-ATT-8710` | **Ticket**: [ATT-8710](https://rainerblind.atlassian.net/browse/ATT-8710)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*) | **Target Release**: `V5.0.0`  
**Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8710` | **Author**: `--as agent1`

---

## 1. Problem Statement & Motivation
In Workout Details (`MapDetailLayout.kt:728–752`), `TelemetryMetricGraph` (`metricType = HRV_DFA_A1`) renders dashed lines at `dfaA1ThresholdT2` / `dfaA1ThresholdT1` (`L391`), but `zoneBands` (`L371–381`) returns `emptyList()` for `HRV_DFA_A1` and the curve/fill/scrubbing dot render in a single static `accentColor` (`TelemetryGraphColor.AMBER`, `TelemetryGraphColorPreferences.kt:97`) instead of reflecting the athlete's 3 configurable HRV zones (*Aerob*, *Schwelle*, *Anaerob*).

## 2. Independent Peer Review & Verification
Audited `TelemetryMetricGraph.kt`, `TelemetryZoneMath.kt`, `TelemetryMetricUtils.kt`, `MapDetailLayout.kt`, `HrvAlpha1ScatterCard.kt`, `HrvAlpha1ScatterChart.kt`, `HrvZoneIndicator.kt`, `SensorSparklineView.kt`, `SettingsDataStore.kt`, `TuningPreferencesDataStore.kt`, and existing unit tests on `62c5d410`:
* **`TelemetryMetricGraph.kt` Gap & Margin Collision**:
  * `zoneBands` (`L371–381`) only populates `HEART_RATE` and `POWER`; `HRV_DFA_A1` hits `else -> emptyList()`, omitting background bands (`L587–610`) and right-hand zone labels (`L601–608`).
  * Curve stroke, fill gradient, single-point dot, and cursor highlight (`L975–1021, L1192–1202`) use `accentColor`. Unlike `HEART_RATE`/`POWER` (solid `accentColor` stroke per `REQ-UI-230` §5), `HRV_DFA_A1` should color both background bands and curve/fill using `Brush.verticalGradient` mapped to `valueToY(T1)`/`valueToY(T2)` (matching `SensorSparklineView.kt:495–560`, honoring `valueToY` where higher `α1` is near top `0f` and lower `α1` is near bottom `1f`, plus artifact gap breaks `L910–968`).
  * **Right-Margin Overlap Guard**: When `zoneDistribution == null && !isLandscape`, `L601` draws `band.label` in the right `52.dp` margin while `L683–721` ALSO draws `nonZonedHistogram` into the exact same strip. For `HRV_DFA_A1` (where `zoneDistribution == null`), either suppress `nonZonedHistogram` or color `nonZonedHistogram` bins by zone and omit duplicate right-axis text.
  * **Threshold Dash Clamping**: `L391` omits `(dataMin, dataMax)` filtering (`dataMin=0.2, dataMax=1.4`), whereas `TuningPreferencesDefaults.MAX_DFA_A1_THRESHOLD` allows `1.50f`; filter dashes/labels to `(dataMin, dataMax)` like `TelemetryZoneMath.calculateThresholdDashes`.
* **`HrvAlpha1ScatterChart.kt` Bug (`L384`)**: `HrvAlpha1ScatterCard.kt:311–312` passes `t1`/`t2` into `HrvAlpha1ScatterChart`, and `L275–298` draws `t1`/`t2` guidelines, **but `L384` calls `HrvZoneIndicator.calculateZoneColor(p.alpha1)` without `t1, t2`**, so scatter dots ignore custom thresholds.
* **Boundary Polarity Alignment**: `HrvZoneIndicator.kt:68–72` (`TrackingHrvZoneIndicatorTest.kt:45`), `ZonesSettingsActivity.kt:1142–1164`, and `SensorSparklineView.kt:470–474` define `α1 > T1` (`TTColor.Zone2` Green), `T2 <= α1 <= T1` (`TTColor.Zone3` Orange), `α1 < T2` (`TTColor.Zone4` Red). Preserving `> T1` at exact equality avoids breaking `TrackingHrvZoneIndicatorTest`.
* **Preferences & Snapshots**: `MapDetailLayout.kt:180–183, 735–736` reactively collects `TuningPreferencesDataStore.tuningConfigFlow` (`dfaA1ThresholdT1`/`T2`). `SettingsDataStore.ZoneType` (`L128–136`) only snapshots `HR_RUN`, `HR_BIKE`, `PWR_BIKE` (no DB snapshot exists for DFA α1), and `hrvDisplayOptions` (`L840–846`) is strictly for live cockpit `SensorFieldView` tiles (`TrackingViewModel`).

## 3. User Scope Grounding (ATT-1250)
* **In-Scope (`2 SP`)**: Add 3-zone HRV band/zone-math helpers in `TelemetryZoneMath.kt`; render `HRV_DFA_A1` zone bands and vertical-gradient zone-colored curve/fill/cursor dot in `TelemetryMetricGraph.kt` using `TTColor.Zone2`/`Zone3`/`Zone4` across Light/Dark/AMOLED; pass `t1, t2` in `HrvAlpha1ScatterChart.kt:384`; optional `HRV_DFA_A1` zone tag in `TelemetryMetricUtils.formatValue`; unit + Robolectric Compose screenshot tests.
* **Out-of-Scope**: Adding `ZoneSnapshotEntry` SQLite persistence for DFA α1, wiring `SettingsDataStore.hrvDisplayOptions` (cockpit tile flags) into Aftermath graphs, or changing solid `HEART_RATE`/`POWER` stroke behavior (`REQ-UI-230`).

## 4. Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-PER-035.1`/`REQ-PER-035.3` (`docs/requirements/req_aftermath_periods.md:1081–1083`), `REQ-UI-230` (`docs/requirements/req_ui_design_system.md:5057–5085`), `REQ-TRK-061.3` (`docs/requirements/req_tracking_cockpit.md:3863`).
* **Historical Origin & Commit Trace**: `3db57718` (`ATT-3544`) added `HRV_DFA_A1` to `TelemetryMetricGraph` with dashed thresholds and `HrvAlpha1ScatterCard`; `5deec7cd` (`ATT-3554`) and `ATT-6717` wired custom `dfaA1ThresholdT1`/`T2` into `MapDetailLayout` & `HrvAlpha1ScatterCard`.
* **Root Reason for Existing Formulation**: `ATT-3544` focused on centered recompute, `[0.2, 1.4]` clamping, and artifact gap hatching (`REQ-PER-035.2`), leaving `zoneBands` (`TelemetryMetricGraph.kt:379`) unimplemented for `HRV_DFA_A1` and `HrvAlpha1ScatterChart.kt:384` on default thresholds.
* **Preservation of Core Invariants**: Preserve `startPadding = 50.dp` / `endPadding = 52.dp` alignment (`Rule 64`), `[0.2, 1.4]` Y-axis bounds, `> 5.0%` artifact polyline break + hatching, `HEART_RATE`/`POWER` solid curves (`REQ-UI-230`), and `TrackingHrvZoneIndicatorTest` boundary `α1 == T1 -> Zone3`.

## Change Impact Analysis
* **Affected Requirements**: Refines `REQ-PER-035.1`, `REQ-PER-035.3`, `REQ-PER-035.4` (`docs/requirements/req_aftermath_periods.md`) and `TST-PER-043` (`docs/tests/tst_aftermath_periods.md`); `REQ-UI-230` & `REQ-TRK-061.3` preserved.
* **Affected Architecture Elements**: `TelemetryZoneMath.kt`, `TelemetryMetricGraph.kt`, `TelemetryMetricUtils.kt`, `HrvAlpha1ScatterChart.kt`. No DB schema, DataStore key, or `SensorType` changes.
* **Verification Impact & Test Surface**: Extend `TelemetryZoneMathTest.kt`, `TelemetryMetricGraphHrvTest.kt`, `HrvAlpha1ScatterChartTest.kt`, and add Light/Dark/AMOLED Robolectric render verification in `TelemetryMetricGraphColorTest.kt` (or dedicated HRV graph UI test).
* **Risk Assessment & Invariants**: **LOW** — isolated to Aftermath HRV graph & scatter color mapping; zero impact on non-HRV workouts, live tracking, or DB schemas.

## 5. High-Level Solution
Add `calculateHrvDfaA1ZoneBands`, `calculateHrvDfaA1ThresholdDashes`, and `determineHrvDfaA1Zone` to `TelemetryZoneMath.kt`; wire `HRV_DFA_A1` background bands, vertical-gradient zone stroke/fill (`Zone2` top `>= T1` / `Zone3` middle / `Zone4` bottom `< T2` mapped to `[dataMin, dataMax]`), and zone-colored cursor dot in `TelemetryMetricGraph.kt`; pass `t1, t2` to `HrvZoneIndicator.calculateZoneColor` in `HrvAlpha1ScatterChart.kt:384`.
