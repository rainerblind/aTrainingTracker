# Stage 2: Requirement & Test Specification - ATT-8096: Multi-Sensor Chart Overlays & Mean Comparison (HR, Power, Cadence)

**Ticket**: [ATT-8096](https://rainerblind.atlassian.net/browse/ATT-8096)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1`  
**Requirement Mapping**: `REQ-PER-042` (*Multi-Sensor Telemetry Chart Overlays & Session Mean Comparison for HR, Power, and Cadence*; extends `REQ-UI-206`, `REQ-UI-260`, `REQ-DAT-033`, `REQ-UI-530`, `REQ-TRK-029`)  
**Test Spec ID**: `TST-PER-050`  
**Branch**: `feature/ATT-8096` | **Author**: AI Agent 2 (`subagent_flash`) | **Date**: 2026-11-16

---

## 1. Requirement Specification (REQ-PER-042)
* **Attributes**: Priority: `Medium` | Type: `Functional` | Verification Method: `Unit / Integration` | Target Status: `[Planned]`
* **System Requirement(s)**: `Derived (SW-only) — Post-workout multi-sensor telemetry overlay visualization and session mean comparison`
* **REQ-PER-042.1 (Strict Source-Column Discovery & Dotted-Name Lookup)**: `WorkoutRepository.kt` (`loadWorkoutDetailTelemetry` and `getWorkoutTelemetryPoints`) SHALL discover source-qualified columns in `WorkoutSamples.db` strictly via `"^${type.name} \\((.+)\\)$"` over `cursor.columnNames` (`columnNames.indexOf(col)` so dotted names like `"POWER (KICKR v5.0)"` never fail `Cursor.getColumnIndex()`). Canonical `ProxySensor` (`"HR"`) paired with `1` named column counts as `1` source; overlays activate strictly when `>= 2` distinct named sources exist.
* **REQ-PER-042.2 (`PathPoint.cadence` & `TelemetryMetricType.CADENCE`)**: `PathPoint` (`MapModels.kt`) SHALL expose `cadence: Int? = null`, and `TelemetryMetricType` (`TelemetryMetricGraph.kt`, `TelemetryMetricUtils.kt`, `MapDetailLayout.kt`) SHALL add `CADENCE`, reusing `TelemetryGraphColorPreferences`'s existing `telemetry_graph_color_SENSOR_CADENCE` key (`THEME_DEFAULT` default).
* **REQ-PER-042.3 (Progressive-Disclosure `Layers` Overlay, `Ø` Mean Comparison & Synchronized Scrubbing)**: When `<= 1` named source exists, `TelemetryMetricGraph` SHALL show zero `Layers` toggle or comparison legend clutter. When `>= 2` named sources exist, it SHALL expose a compact `Layers` toggle, render overlaid secondary curves with distinct stroke/color, show a 1-line `Ø` mean comparison (`Ø %1$d bpm`/`W`/`rpm`), and update scrub/header readouts synchronously across `DISTANCE` and `TIME` domains (GPS & trackless) while preserving `Rule 64` paddings (`50.dp`/`25.dp`/`24.dp`) and `Rule 48` labels.
* **REQ-PER-042.4 (9-Locale Parity & Rule 8.5 Visual Compose PNG)**: New strings (`graph_heading_cadence`, overlay/mean strings) SHALL maintain 100% 9-locale parity (`EN/DE/ES/FR/IT/JA/NL/PL/PT`) with zero emojis (`ATT-5400`). Stage 4 SHALL render `docs/engineering/walkthroughs/img/ATT-8096_multisensor_charts.png` (Light + Dark) and verify it via `read_file` (`Rule 8.5`).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-PER-042` extending `REQ-UI-206`, `REQ-UI-260`, `REQ-DAT-033`, `REQ-UI-530`, `REQ-TRK-029`; targets `MapModels.kt`, `WorkoutRepository.kt`, `TelemetryMetricGraph.kt`, `TelemetryMetricUtils.kt`, `TelemetryGraphColorPreferences.kt`, `MapDetailLayout.kt`, `WorkoutDetailTabbedContent.kt`, `res/values*/strings.xml`.
* **Historical Origin & Commit Trace**: `TrackerService` per-device persistence (`REQ-TRK-029`), `TelemetryMetricGraph` (`ATT-1740`), `TelemetryGraphColorPreferences` (`ATT-7759`).
* **Root Reason for Existing Formulation**: Graphs queried single canonical columns to keep mobile layouts clean prior to multi-source comparison support.
* **Preservation of Core Invariants**: Single-sensor (`<= 1` named source) UI untouched; `Rule 64` & `Rule 48` alignment preserved; `telemetry_graph_color_SENSOR_CADENCE` reused; `TrackerService.java` & `CSVFileWriter.java` untouched; zero DB schema bumps; `TelemetryMetricGraph` backward-compatible defaults intact.

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Zero UI Clutter on Single-Sensor Workouts)**: *Given* `<= 1` named source column per metric, *When* opening Workout Details (`CHARTS`), *Then* no `Layers` toggle or mean comparison legend renders.
* **Criterion 2 (Multi-Sensor Overlay & `Ø` Mean Readout)**: *Given* GPS or trackless workout with `>= 2` named columns (including dotted `"KICKR v5.0"`), *When* enabling `Layers`, *Then* `TelemetryMetricGraph` renders distinct overlaid curves + 1-line `Ø` mean comparison.
* **Criterion 3 (Synchronized X-Axis Scrubbing)**: *Given* an active overlay on `HR`, `POWER`, or `CADENCE`, *When* scrubbing across `DISTANCE` or `TIME`, *Then* active overlaid values update synchronously across all Workout Details charts.

---

## 4. Test Specification (TST-PER-050)
* **TST-PER-050.1 (Strict Discovery & Dotted-Name Lookup)**: Assert `"^${type.name} \\((.+)\\)$"` over `cursor.columnNames` handles `1` vs `>= 2` sources and dotted names across GPS & trackless workouts (`WorkoutRepositoryTelemetryTest.kt`).
* **TST-PER-050.2 (`CADENCE` Metric & Color Key Reuse)**: Assert `PathPoint.cadence`, `TelemetryMetricType.CADENCE` formatting, and `telemetry_graph_color_SENSOR_CADENCE` persistence (`TelemetryMetricUtilsTest.kt`, `TelemetryGraphColorPreferencesTest.kt`).
* **TST-PER-050.3 (`Layers` Toggle, Mean Legend & Zero Clutter)**: Assert `<= 1` hides UI and `>= 2` renders `Layers`, curves, and `Ø` means (`TelemetryMetricGraphOverlayTest.kt`).
* **TST-PER-050.4 (Synchronized Scrubbing)**: Assert `DISTANCE`/`TIME` scrub sync and `Rule 64` alignment (`MapDetailLayoutScrubSynchronizationTest.kt`).
* **TST-PER-050.5 (9-Locale Parity & `Rule 8.5` PNG)**: Run `TranslationParityTest` + render/inspect `ATT-8096_multisensor_charts.png` (`MultiSensorChartsScreenshotTest.kt`).

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-050.1` | Unit | `WorkoutRepositoryTelemetryTest.kt` | `REQ-PER-042.1` | Planned |
| `TST-PER-050.2` | Unit | `TelemetryMetricUtilsTest.kt`, `TelemetryGraphColorPreferencesTest.kt` | `REQ-PER-042.2` | Planned |
| `TST-PER-050.3` | UI / Unit | `TelemetryMetricGraphOverlayTest.kt` | `REQ-PER-042.3` | Planned |
| `TST-PER-050.4` | UI / Unit | `MapDetailLayoutScrubSynchronizationTest.kt` | `REQ-PER-042.3` | Planned |
| `TST-PER-050.5` | Localization / UI | `TranslationParityTest.kt`, `MultiSensorChartsScreenshotTest.kt` | `REQ-PER-042.4` | Planned |
