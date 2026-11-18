# Stage 2: Requirement & Test Specification - ATT-8080: Multi-Source Overlays & Per-Chart Visibility in Workout Details

**Ticket**: [ATT-8080](https://rainerblind.atlassian.net/browse/ATT-8080)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-18_02_Spät`  
**Requirement Mapping**: `REQ-PER-044` (extends `REQ-PER-042`, `REQ-UI-206`, `REQ-UI-260`, `REQ-DAT-033`, `REQ-UI-408`, `REQ-UI-530`); refined `REQ-MAP-011`, `REQ-MAP-012`  
**Test Spec ID**: `TST-PER-052` (paired with `TST-UI-055`)  
**Branch**: `feature/ATT-8080` | **Author**: AI Agent 1 (`ATT-8080`) | **Date**: 2026-11-18  

---

## 1. Requirement Specification (`REQ-PER-044`, `REQ-MAP-011`, `REQ-MAP-012`)
* **Attributes**: Priority: `Medium` | Type: `Functional` | Verification Method: `Unit / Integration` | Target Status: `[Verified]`
* **System Requirement(s)**: `REQ-PER-044`: `Derived (SW-only) — Post-workout multi-source visualization & per-chart visibility`; `REQ-MAP-011`, `REQ-MAP-012`: `SYSREQ-TRK-004`
* **`REQ-PER-044.1` / `REQ-MAP-011` / `REQ-MAP-012` (`ATT_FUSED`, Alias Resolution & Bottom-Sheet `Layers` Wiring)**: `TrackType` (`MapModels.kt`) SHALL add `ATT_FUSED` (distinct color/zIndex resolving `LATITUDE_att_fused`/`LONGITUDE_att_fused`), remove dead `TrackType.NETWORK` (`ATT-5492`), and resolve legacy/underscore/localized aliases for `GPS` & `FUSED` against `PRAGMA table_info`. `PeriodMapScreen.kt`, `WorkoutClusterHeatmapScreen.kt`, and `WorkoutClustersScreen.kt` SHALL wire `showTechnicalTracks = true`, `availableTrackTypes`, `enabledTrackTypes`, `onToggleTrackType`, and `multiSensorOverlays`.
* **`REQ-PER-044.2` (Generic Multi-Source Discovery, Metric-Aware Accumulation & Extracted Helper)**: Extract discovery/accumulation into `WorkoutMultiSourceSampleDiscovery.kt` (retaining `WorkoutRepository$Companion` delegates, `WorkoutRepository.kt <= 5,117` LoC). `discoverNamedSourceColumns` SHALL match `"^${type.name} \\((.+)\\)$"` (including `"att_fused_altitude_slope"` & nested parens) and `"^${type.name}_(gps|google_fused|att_fused)$"` (`CSVFileWriter.java:87`, excluding `_network`), mapping technical suffixes to clean labels while keeping `ATT-8096` `HR`/`POWER`/`CADENCE` intact. `discoverMultiSourceOverlayColumns` SHALL discover `ALTITUDE`, `SPEED_mps`, and `SLOPE` (`loadWorkoutDetailTelemetry` & `loadTracklessWorkoutTelemetry`), and `OverlaySeriesAccumulator` SHALL retain `<= 0.0` `ALTITUDE`/`SLOPE` (`isFinite()`), `>= 0.0` `SPEED_mps`/`POWER`, and `> 0.0` `HR`/`CADENCE`.
* **`REQ-PER-044.3` (`ElevationProfile`, `SPEED`/`PACE` Conversion & `TelemetryMetricType.SLOPE`)**: Add `TelemetryMetricType.SLOPE` (`%`, `telemetry_graph_color_SENSOR_SLOPE` key). `ElevationProfile.kt` (`<= 1,500` LoC via `ElevationProfileOverlayComponents.kt`) SHALL show `Layers`, secondary curves, `Ø` mean legend, and synced scrub readouts strictly when `>= 2` `ALTITUDE` sources exist. `TelemetryMetricGraph.kt` (`<= 1,965` LoC via `TelemetryOverlayComponents.kt`) SHALL render `SPEED` (`MyHelper.mps2userUnit`), `PACE` (inverted `min/km` or `min/mi`), and `SLOPE` (`%`) overlays with synced scrubbing and `Rule 64` alignment.
* **`REQ-PER-044.4` (Persisted Per-Chart Visibility Control)**: `MyPreferenceManager` SHALL persist `hidden_telemetry_charts` (`stringSetPreferencesKey`, default empty = all visible) over `SPEED_PACE`, `SLOPE`, `HEART_RATE`, `POWER`, `CADENCE`, `W_PRIME_BAL`, `HRV_DFA_A1`, `HRV_RMSSD`. Shared `TelemetryChartStack.kt` (`MapDetailLayout.kt <= 1,500` LoC, `TrackOnMapScreen.kt`) SHALL expose an M3 `DropdownMenu` filter that remains accessible when charts are hidden, maintains `Rule 64`/`Rule 48` alignment, and includes `SLOPE`/`CADENCE` in `hasTelemetrySeries`/`hasTelemetryGraphs`.
* **`REQ-PER-044.5` (9-Locale Parity & `Rule 8.5` PNG)**: All new strings (`track_type_att_fused`, `graph_heading_slope`, visibility/overlay labels, indexed `%1$s`/`%1$d` mean formats, removing `track_type_network`) SHALL achieve 100% 9-locale parity (`EN, DE, ES, FR, IT, JA, NL, PL, PT`) with zero emojis (`ATT-5400`). Stage 4 SHALL inspect Light/Dark PNG `ATT-8080_multisource_charts_visibility.png` via `read_file`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Modifies `REQ-MAP-011`, `REQ-MAP-012` (`SYSREQ-TRK-004`, `TST-UI-055`), extends `REQ-PER-042.1`, `REQ-SEN-027.2`, `REQ-UI-206`, `REQ-UI-260`, `REQ-DAT-033`, `REQ-UI-408`, `REQ-UI-530` via net-new `REQ-PER-044` (`MapModels.kt`, `WorkoutRepository.kt`, `ElevationProfile.kt`, `TelemetryMetricGraph.kt`, `MapDetailLayout.kt`, `TrackOnMapScreen.kt`, `MyPreferenceManager.kt`).
* **Historical Origin & Commit Trace**: `REQ-MAP-011`/`012` predated `ATT-5492` (`SPEED_AND_LOCATION_NETWORK` excision) and `ATT-7751` (`ATT_FUSED`); `71fdb559` (`ATT-8096`) added `REQ-PER-042` discovery/overlays for `HR`/`POWER`/`CADENCE`.
* **Root Reason for Existing Formulation**: Early providers listed `Network` and single-source charts; `ATT-8096` matched only `(<deviceName>)` and `> 0.0` because BLE/ANT+ HR/Power/Cadence lack underscore suffixes and negative values.
* **Preservation of Core Invariants**: Single-source (`<= 1`) views show zero `Layers` clutter; `ATT-8096` `HR`/`POWER`/`CADENCE` & `WorkoutSummary` untouched; `BEST` track & all charts enabled by default; `safeEnumValueOfOrNull` skips stale `"NETWORK"`; `Rule 64`/`Rule 48` & all 4 LoC ceilings preserved.

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (`ATT Fused` Map Layer)**: *Given* a workout with `ATT_FUSED`, `GPS`, and `Google Fused`, *When* opening Map `Layers` in Workout Details (incl. Period/Cluster peek sheets), *Then* `Best`, `GPS`, `Google Fused`, and `ATT Fused` toggle cleanly and `Network` is absent.
* **Criterion 2 (Multi-Source `ElevationProfile`, `SPEED`/`PACE` & `SLOPE`)**: *Given* `>= 2` `ALTITUDE*`, `SPEED_mps*`, or `SLOPE*` columns (incl. `SPEED_mps_att_fused`, `ALTITUDE_att_fused`, `att_fused_altitude_slope`, negative slopes), *When* enabling `Layers`, *Then* curves, `Ø` means, and synced `DISTANCE`/`TIME` scrubbers update accurately.
* **Criterion 3 (Persisted Per-Chart Visibility)**: *Given* `CHARTS` in Workout Details, *When* toggling charts (e.g. showing `Slope`/`Cadence`, hiding `W'bal`), *Then* UI updates immediately, persists across workouts, keeps filter reachable, and preserves `Rule 64` alignment.

---

## 4. Test Specification (`TST-PER-052` & `TST-UI-055`)
* **`TST-PER-052.1` / `TST-UI-055` (`ATT_FUSED`, Aliases & `NETWORK` Removal)**: `WorkoutRepositoryProjectionTest.kt`, `TrackOnMapAftermathViewModelTest.kt`, `TelemetryChartStackVisibilityTest.kt`, `PeriodsViewModelTest.kt`, `WorkoutClustersViewModelTest.kt` verify `ATT_FUSED`, GPS/Fused localized/underscore aliases, `NETWORK` removal, and bottom-sheet `Layers` wiring.
* **`TST-PER-052.2` (Generic Discovery & Signed `SLOPE`/`ALTITUDE` Accumulation)**: `WorkoutRepositoryTelemetryTest.kt`, `WorkoutRepositoryProjectionTest.kt` verify `(<name>)` + `_(gps|google_fused|att_fused)` + `att_fused_altitude_slope`, negative `SLOPE`/`<=0` `ALTITUDE`, and `ATT-8096` `HR`/`POWER`/`CADENCE` parity.
* **`TST-PER-052.3` (`ElevationProfile`, `SPEED`/`PACE` Conversion, Locale & `SLOPE`)**: `TelemetryMetricUtilsTest.kt`, `TelemetryGraphColorPreferencesTest.kt`, `ElevationProfileOverlayTest.kt`, `TelemetryMetricGraphOverlayTest.kt`, `MapDetailLayoutScrubSynchronizationTest.kt`, `TelemetryChartStackVisibilityTest.kt` verify `SLOPE` `%`, `SENSOR_SLOPE` key, `SPEED`/`PACE` units, `MapDetailLayout` hoisted `ALTITUDE` overlay scrub readout, `Locale.GERMANY` decimal parity, and scrub sync.
* **`TST-PER-052.4` (Per-Chart Visibility, Landscape Clearance & Peek Wiring)**: `TelemetryChartStackVisibilityTest.kt`, `PeriodsViewModelTest.kt`, `WorkoutClustersViewModelTest.kt`, `TrackOnMapAftermathViewModelFallbackTest.kt` verify DataStore persistence, default-all-on upgrade, non-overlapping Landscape `GlobalTelemetryZoomToolbar` vs `Visible Charts` bounds when `showElevationProfile == false`, `Rule 64` alignment, and peek overlays.
* **`TST-PER-052.5` (9-Locale Parity, Contracts & `Rule 8.5` PNG)**: `TranslationParityTest.kt`, `ArchitectureConformanceContractTest.kt`, `DetailedDesignParityContractTest.kt`, `SourceTextReadingAuditContractTest.kt`, `TelemetryChartStackVisibilityTest.kt` (`ATT-8080_multisource_charts_visibility.png` Light/Dark).

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-052.1` / `TST-UI-055` | Unit / UI | `WorkoutRepositoryProjectionTest.kt`, `TrackOnMapAftermathViewModelTest.kt`, `TelemetryChartStackVisibilityTest.kt` | `REQ-PER-044.1`, `REQ-MAP-011`, `REQ-MAP-012` | Verified |
| `TST-PER-052.2` | Unit | `WorkoutRepositoryTelemetryTest.kt`, `WorkoutRepositoryProjectionTest.kt` | `REQ-PER-044.2` | Verified |
| `TST-PER-052.3` | Unit / UI | `TelemetryMetricUtilsTest.kt`, `ElevationProfileOverlayTest.kt`, `TelemetryMetricGraphOverlayTest.kt`, `MapDetailLayoutScrubSynchronizationTest.kt`, `TelemetryChartStackVisibilityTest.kt` | `REQ-PER-044.3` | Verified |
| `TST-PER-052.4` | Unit / UI | `TelemetryChartStackVisibilityTest.kt`, `PeriodsViewModelTest.kt`, `WorkoutClustersViewModelTest.kt`, `TrackOnMapAftermathViewModelFallbackTest.kt` | `REQ-PER-044.4`, `REQ-PER-044.1` | Verified |
| `TST-PER-052.5` | Contract / UI | `TranslationParityTest.kt`, `ArchitectureConformanceContractTest.kt`, `DetailedDesignParityContractTest.kt`, `SourceTextReadingAuditContractTest.kt`, `TelemetryChartStackVisibilityTest.kt` | `REQ-PER-044.5` | Verified |
