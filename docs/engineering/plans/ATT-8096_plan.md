# Stage 3: Implementation Plan - ATT-8096: Multi-Sensor Chart Overlays & Mean Comparison

**Ticket**: [ATT-8096](https://rainerblind.atlassian.net/browse/ATT-8096) | **Sub-task**: `[Impl-Plan]` (Pending `submit-stage`)
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1` | **Branch**: `feature/ATT-8096`
**Requirement / Test Mapping**: `REQ-PER-042` (`REQ-PER-042.1`..`.4`) / `TST-PER-050` (`TST-PER-050.1`..`.5`)
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-16

---

## 1. Problem Description & Background
`TrackerService.java:2688-2696` persists per-device `"<SENSOR_TYPE> (<deviceName>)"` columns in `WorkoutSamples.db`, yet Workout Details charts only display canonical single streams (`HR`, `POWER`) and omit `CADENCE` and multi-source overlays. Stage 4 adds generic named-source discovery, `CADENCE` chart support, progressive-disclosure `Layers` overlays, `Ø` mean legends, and synchronized scrubbing.

## 2. Traceability & Requirements Mapping
* `REQ-PER-042.1` -> `TST-PER-050.1`: `MapModels.kt`, `WorkoutRepository.kt`, `TrackOnMapAftermathViewModel.kt`
* `REQ-PER-042.2` -> `TST-PER-050.2`: `MapModels.kt`, `TelemetryMetricUtils.kt`, `TelemetryGraphColorPreferences.kt`
* `REQ-PER-042.3` -> `TST-PER-050.3`, `.4`: `TelemetryMetricGraph.kt`, `MapDetailLayout.kt`, `WorkoutDetailTabbedContent.kt`
* `REQ-PER-042.4` -> `TST-PER-050.5`: `values*/strings.xml`, `MultiSensorChartsScreenshotTest.kt`, `TranslationParityTest.kt`

## 3. UI Consistency & Design Guidelines (`docs/design_guidelines.md`)
* **Controls & Tokens**: Use compact `Surface`/`IconButton` (`Icons.Default.Layers`) + `DropdownMenu` with `Checkbox` and `TTColor` / `MaterialTheme.colorScheme` swatches (no raw `FilterChip` mode switches). Zero inline emojis (`ATT-5400`).
* **Chart Invariants**: Preserve `Rule 64` plot bounds (`50.dp` start, `52.dp` portrait / `0.72f` landscape) and `Rule 48` axis labels. Extract modular helpers so Detekt adds **0** new `LongMethod` / `CyclomaticComplexMethod` baseline entries.

## 4. System Invariants & Preserved Behavior
1. **Zero Single-Sensor Clutter**: `<= 1` named column (`ProxySensor` + `<= 1` device) displays zero `Layers` button or legend.
2. **Color & Schema Stability**: `TelemetryMetricType.CADENCE` reuses `telemetry_graph_color_SENSOR_CADENCE` (`THEME_DEFAULT`); zero `WorkoutSamples.db` or `TrackerService` changes; backward-compatible `TelemetryMetricGraph` defaults for `WorkoutSummary.kt` / `HrvRampTestScreen.kt`.

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 — Overlay Models & `PathPoint.cadence` (`MapModels.kt`, `TelemetryMetricUtils.kt`, `TelemetryGraphColorPreferences.kt`)**:
   - Add `cadence: Int? = null` to `PathPoint` and `TelemetrySourcePoint` / `TelemetrySourceSeries` (`metricType`, `sourceName`, `columnName`, `points`, `meanValue`).
   - Add `TelemetryMetricType.CADENCE` across `TelemetryMetricUtils` (`hasCadenceData`, `extractMetricValue`, `formatValue` -> `rpm`) and `TelemetryGraphColorPreferences` (mapping `CADENCE` to `"${KEY_PREFIX}SENSOR_CADENCE"`, `THEME_DEFAULT`).
2. **Step 2 — Strict Named-Source Discovery & Trackless Support (`WorkoutRepository.kt`, `TrackOnMapAftermathViewModel.kt`)**:
   - Add `discoverNamedSourceColumns(existingColumns, sensorType)` strictly matching `"^${type.name} \\((.+)\\)$"` and activating overlay loading **only when `>= 2` named sources exist** for `HR`, `POWER`, or `CADENCE`.
   - Use `cursor.columnNames` index map (`columnNames.indexOf(col)`) so dotted device names (`"POWER (KICKR v5.0)"`) resolve reliably.
   - Extract `CADENCE` and `multiSensorOverlays` in **both** `loadWorkoutDetailTelemetry` (GPS) and `getWorkoutTelemetryPoints` (trackless indoor), computing exact `meanValue` across all valid rows before `800`-point decimation, and expose via `AftermathMapUIState.multiSensorOverlays`.
3. **Step 3 — 9-Locale Strings (`app/src/main/res/values*/strings.xml`)**:
   - Add zero-emoji strings across all 9 locales (`values`, `-de`, `-es`, `-fr`, `-it`, `-ja`, `-nl`, `-pl`, `-pt`): `graph_heading_cadence`, `chart_overlay_layers_cd`, `chart_overlay_mean_bpm`, `chart_overlay_mean_watt`, `chart_overlay_mean_rpm`.
4. **Step 4 — Progressive `Layers` Overlay, `Ø` Mean Legend & Scrubbing (`TelemetryMetricGraph.kt`, `MapDetailLayout.kt`, `WorkoutDetailTabbedContent.kt`)**:
   - Add optional `overlaySeries`, `enabledOverlayIds`, `onToggleOverlay` to `TelemetryMetricGraph`. When `overlaySeries.size >= 2`, render compact `Layers` dropdown toggle, distinct secondary curve strokes (distinct `TTColor` + dash pattern), 1-line `Ø` mean comparison legend, and synchronized X-axis scrubbing (`DISTANCE` & `TIME` via binary search on `TelemetrySourcePoint`) with marker dots + header readouts.
   - Wire `CADENCE` graph and `multiSensorOverlays` in `MapDetailLayout.kt` and `WorkoutDetailTabbedContent.kt`, extracting modular helpers to keep Detekt clean.
5. **Step 5 — Unit, Contract, Detekt & `Rule 8.5` Screenshot Verification**:
   - Tag `// TST-PER-050.1`..`.5` across `WorkoutRepositoryTelemetryTest.kt`, `TelemetryMetricUtilsTest.kt`, `TelemetryGraphColorPreferencesTest.kt`, `TelemetryMetricGraphOverlayTest.kt`, `MapDetailLayoutScrubSynchronizationTest.kt`, `TranslationParityTest.kt`, and new `MultiSensorChartsScreenshotTest.kt` capturing `docs/engineering/walkthroughs/img/ATT-8096_multisensor_charts.png` (Light + Dark: clean single-sensor baseline + active multi-sensor overlay with `Ø` legend + scrub readout, inspected via `read_file`).
   - Run targeted `./gradlew :app:testDebugUnitTest --tests ...` + `./gradlew :app:detekt`.

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit/contract/parity/screenshot tests + `:app:detekt` + `Rule 8.5` PNG inspection + `verify_requirement_governance.py`.
* **Rollback**: Isolated on detached `HEAD` / `feature/ATT-8096` prior to Stage 5 squash-merge.
