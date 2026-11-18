# Stage 3: Implementation Plan - ATT-8080: Multi-Source Overlays & Per-Chart Visibility

**Ticket**: [ATT-8080](https://rainerblind.atlassian.net/browse/ATT-8080) | **Sub-task**: `[Impl-Plan]` (Pending `submit-stage`)
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-18_02_Spät` | **Branch**: `feature/ATT-8080`
**Requirement / Test Mapping**: `REQ-PER-044` (`.1`..`.5`), `REQ-MAP-011`, `REQ-MAP-012` / `TST-PER-052` (`.1`..`.5`), `TST-UI-055`
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-18

## 1. Problem Description & Background
Post-workout Workout Details (`TrackOnMapScreen`, `MapDetailLayout`, `ElevationProfile`, plus peek sheets in `PeriodMapScreen`/`WorkoutClusterHeatmapScreen`) lacks `TrackType.ATT_FUSED`, multi-source overlays for `ALTITUDE`, `SPEED`/`PACE`, and `SLOPE`, and persisted per-chart visibility selection in `CHARTS`, while retaining retired `TrackType.NETWORK`. `ATT-8096` (`REQ-PER-042`) already built overlay infrastructure for `HR`/`POWER`/`CADENCE`.

## 2. Traceability & Requirements Mapping
* `REQ-MAP-011`, `REQ-MAP-012`, `REQ-PER-044.1` -> `TST-UI-055`, `TST-PER-052.1`: `MapModels.kt`, `WorkoutMultiSourceSampleDiscovery.kt`, `WorkoutRepository.kt`, `TrackOnMapScreen.kt`, `PeriodMapScreen.kt`, `WorkoutClusterHeatmapScreen.kt`, `WorkoutClustersScreen.kt`
* `REQ-PER-044.2` -> `TST-PER-052.2`: `WorkoutMultiSourceSampleDiscovery.kt`, `WorkoutRepository.kt`, `TrackOnMapAftermathViewModel.kt`, `DetailedDesignParityContractTest.kt`
* `REQ-PER-044.3` -> `TST-PER-052.3`: `TelemetryMetricType.SLOPE`, `TelemetryGraphColorPreferences.kt`, `TelemetryMetricUtils.kt`, `TelemetryOverlayComponents.kt`, `TelemetryMetricGraph.kt`, `ElevationProfileOverlayComponents.kt`, `ElevationProfile.kt`
* `REQ-PER-044.4` -> `TST-PER-052.4`: `MyPreferenceManager.kt`, `TelemetryChartStack.kt`, `MapDetailLayout.kt`, `TrackOnMapScreen.kt`, `WorkoutDetailTabbedContent.kt`
* `REQ-PER-044.5` -> `TST-PER-052.5`: `res/values*/strings_workout_details.xml`, `res/values*/strings.xml`, `docs/architecture.md`, `docs/architecture/detailed_design/workout_repository.md`, `MultiSensorChartsScreenshotTest.kt`, `TranslationParityTest.kt`

## 3. UI Consistency & Design Guidelines (`docs/design_guidelines.md`)
* **Controls (`§1`)**: Use `IconButton`/`Surface` + `DropdownMenu` with `Checkbox` for non-exclusive multi-source `Layers` and per-chart visibility (`CHARTS`) — never `FilterChip` for binary modes and never nest `ElevatedCard`. Keep filter action reachable even when all charts are unchecked.
* **Chart Invariants (`Rule 64`, `Rule 48`)**: Preserve `startPadding = 50.dp` and `endPadding = 52.dp` portrait / `0.72f` landscape plot alignment, axis tick spacing, zero inline emojis (`ATT-5400`), and light/dark `TTColor` / `MaterialTheme` tokens.

## 4. System Invariants & LoC-Budget Decomposition (`ArchitectureConformanceContractTest`)
* **LoC Budgets**: Extract `WorkoutMultiSourceSampleDiscovery.kt` (`WorkoutRepository.kt <= 5,117`, keeping `$Companion` delegates `discoverNamedSourceColumns`, `discoverMultiSourceOverlayColumns`, `parseTimestampOffset`, `quoteSqlIdentifier`, `getSamplesTableName` intact for `DetailedDesignParityContractTest`), `TelemetryOverlayComponents.kt` (`TelemetryMetricGraph.kt <= 1,965`), `TelemetryChartStack.kt` (`MapDetailLayout.kt <= 1,500`, also deduplicating `TrackOnMapScreen.kt:730-831`), and `ElevationProfileOverlayComponents.kt` (`ElevationProfile.kt <= 1,500`).
* **Compatibility & Zero-Clutter**: `<= 1` source renders zero overlay chrome; `ATT-8096` `HR`/`POWER`/`CADENCE` overlays & `WorkoutSummary.kt` graph callers remain intact; all charts default to visible via `hidden_telemetry_charts` negative-set key; legacy `"NETWORK"` entries in DataStore are safely ignored.

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 — TrackTypes, SLOPE Metric & Chart Preferences (`MapModels.kt`, `TelemetryMetricUtils.kt`, `TelemetryGraphColorPreferences.kt`, `MyPreferenceManager.kt`)**:
   - Replace `TrackType.NETWORK` with `TrackType.ATT_FUSED` (`att_fused`); add `TelemetryMetricType.SLOPE` (`%` formatter, `hasSlopeData`, key `telemetry_graph_color_SENSOR_SLOPE` -> `THEME_DEFAULT`).
   - Add `ChartVisibilityKey` / `HIDDEN_TELEMETRY_CHARTS` DataStore `stringSetPreferencesKey` + `hiddenTelemetryChartsFlow` / `setTelemetryChartVisible(...)` in `MyPreferenceManager.kt`.
2. **Step 2 — LoC-Safe Sample Discovery & Bottom-Sheet Wiring (`WorkoutMultiSourceSampleDiscovery.kt`, `WorkoutRepository.kt`, `TrackOnMapAftermathViewModel.kt`, `TrackOnMapScreen.kt`, `PeriodMapScreen.kt`, `PeriodsScreen.kt`, `WorkoutClusterHeatmapScreen.kt`, `WorkoutClustersScreen.kt`)**:
   - Extract discovery/accumulation into `WorkoutMultiSourceSampleDiscovery.kt` while keeping `WorkoutRepository$Companion` delegating signatures. Support `(<name>)` + `_(gps|google_fused|att_fused)` + `att_fused_altitude_slope` + localized `GPS`/`FUSED` track aliases across GPS and trackless paths, retaining `<= 0.0` values (`isFinite()`) for `ALTITUDE` and `SLOPE`.
   - Wire `showTechnicalTracks = true`, `availableTrackTypes`, `enabledTrackTypes`, `onToggleTrackType`, `telemetryPath`, and `multiSensorOverlays` in `PeriodsScreen`/`PeriodMapScreen`, `WorkoutClustersScreen`, and `WorkoutClusterHeatmapScreen` peek bottom sheets (leaving overview canvases clean).
3. **Step 3 — Multi-Source `ElevationProfile`, `SPEED`/`PACE`/`SLOPE` Overlays & Shared `TelemetryChartStack` (`ElevationProfileOverlayComponents.kt`, `ElevationProfile.kt`, `TelemetryOverlayComponents.kt`, `TelemetryMetricGraph.kt`, `TelemetryChartStack.kt`, `MapDetailLayout.kt`, `WorkoutDetailTabbedContent.kt`, `TrackOnMapScreen.kt`)**:
   - Extract overlay helpers (`TelemetryOverlayComponents.kt`, `ElevationProfileOverlayComponents.kt`) and add `overlaySeries` (`ALTITUDE` in `m` converted to user units, `SPEED_mps` -> user speed/pace, `SLOPE` `%`) with `>= 2`-source `Layers` dropdown, distinct strokes, `Ø` mean legend, and synchronized `DISTANCE`/`TIME` scrub readouts.
   - Extract `TelemetryChartStack.kt` deduplicating `MapDetailLayout.kt` and `TrackOnMapScreen.kt`, adding `SLOPE`, `CADENCE` parity, persisted `CHARTS` visibility menu (`Rule 64` preserved), and updating `hasTelemetrySeries`/`hasTelemetryGraphs` for `SLOPE`/`CADENCE`.
4. **Step 4 — 9-Locale Strings & SWE.2/SWE.3 Architecture Maintenance (`res/values*/strings_workout_details.xml`, `res/values*/strings.xml`, `docs/architecture.md`, `docs/architecture/detailed_design/workout_repository.md`)**:
   - Remove `track_type_network` in `values*/strings.xml`; add localized strings in `values*/strings_workout_details.xml` (`Rule 9.4`) across all 9 locales (`EN, DE, ES, FR, IT, JA, NL, PL, PT`).
   - Update `docs/architecture.md` (`Rule 8.8`) and `docs/architecture/detailed_design/workout_repository.md` for the extracted modules.
5. **Step 5 — Targeted Unit, UI (`createComposeRule()`), Contract & `Rule 8.5` Screenshot Tests**:
   - Update/add tests tagged `// TST-PER-052.1`..`.5` & `// TST-UI-055` (`WorkoutRepositoryProjectionTest`, `WorkoutRepositoryTelemetryTest`, `TelemetryMetricUtilsTest`, `TelemetryGraphColorPreferencesTest`, `TelemetryMetricGraphOverlayTest`, `ElevationProfileOverlayTest`, `TelemetryChartStackVisibilityTest`, `TranslationParityTest`, `DetailedDesignParityContractTest`, `ArchitectureConformanceContractTest`).
   - Capture Light/Dark `docs/engineering/walkthroughs/img/ATT-8080_multisource_charts_visibility.png` via `createComposeRule()` and inspect with `read_file` (`Rule 8.5`).

## 6. Verification & Rollback Plan
* **Verification**: Targeted `./gradlew :app:testDebugUnitTest --tests ...`, `:app:detekt`, `verify_requirement_governance.py`, and `Rule 8.5` PNG inspection.
* **Rollback**: Isolated on `feature/ATT-8080` prior to Stage 6 squash-merge (`Rule 8.2`).
