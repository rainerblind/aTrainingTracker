# Stage 1 Analysis: ATT-8096 - Multi-Sensor Chart Overlays & Mean Comparison (HR, Power, Cadence)

**Ticket**: [ATT-8096](https://rainerblind.atlassian.net/browse/ATT-8096) | **Sub-task**: `[Analysis]` (Pending `submit-stage`)
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1` | **Branch**: `feature/ATT-8096`
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16

---

## 1. Problem Statement & Motivation
`TrackerService.java:2688-2696` already writes source-qualified columns (`"HR (<deviceName>)"`, `"POWER (<deviceName>)"`, `"CADENCE (<deviceName>)"`) to `WorkoutSamples.db` whenever multiple sensors of the same `SensorType` are active, and `CSVFileWriter.java:87-118` exports them. However, Workout Details (`MapDetailLayout.kt:518-813`, `TelemetryMetricGraph.kt`) only queries canonical `HR` / `POWER` (omitting `CADENCE` and all per-device columns), preventing on-device visual comparison of dual HR straps or pedal vs. trainer power.

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA (`WorkoutRepository.kt`, `MapDetailLayout.kt`, `TelemetryMetricGraph.kt`, `MapModels.kt`)**:
  * `WorkoutRepository.kt:2037-2205, 2483-2585` projects only canonical `HR`, `POWER`, `SPEED_mps`, `ALTITUDE`, `SLOPE`, `W_PRIME_BAL`, `HRV_*`; `PathPoint` (`MapModels.kt:175-188`) has no `cadence` field, and `TelemetryMetricType` (`TelemetryMetricGraph.kt:102-110`) has no `CADENCE` entry.
  * `TelemetryMetricGraph.kt:122-147` supports only `cardioPowerDynamics` fresh-HR overlay; no generic multi-source series model, `Layers` toggle, or `Ø` mean legend exists.
* **Independent Peer Review & Verification** (audited against `sprint/2026-47.1` @ `ce23a85e`):
  * *Blind Spot 1 — `ATT-8080` Not Yet Merged*: Jira `ATT-8080` is still `Zu erledigen`. `WorkoutRepository.kt` and `MapDetailLayout.kt` do NOT yet have `ATT-8080`'s `ALTITUDE*`/`SPEED_mps*` overlay model; `ATT-8096` must introduce a self-contained generic `<SENSOR_TYPE> (<deviceName>)` discovery & overlay model (`TelemetrySourceOverlay`) reusable by `ATT-8080`.
  * *Blind Spot 2 — Single-Sensor Proxy + Named Column Pairing*: `MySensorManager.java:330-335` & `TrackerService.java:2674-2696` record BOTH canonical `"HR"` (`ProxySensor`) AND `"HR (<deviceName>)"` even for **1** physical sensor. Discovery MUST count strictly `"^${type.name} \\((.+)\\)$"` columns (`>= 2` named sources) so single-sensor workouts (`<= 1` named source) show zero `Layers` UI.
  * *Blind Spot 3 — Dotted Device Names & Trackless Indoor Workouts*: Per `TrackerService.java:2911-2930`, `Cursor.getColumnIndex(col)` can return `-1` when `<deviceName>` contains `.` (e.g. `KICKR v5.0`); resolve via `cursor.columnNames` index lookup. Query overlays in BOTH `loadWorkoutDetailTelemetry` (GPS) and `getWorkoutTelemetryPoints` (trackless indoor trainers).
  * *Blind Spot 4 — Shared Color Keys & `Rule 8.5` Screenshot*: `TelemetryGraphColorPreferences.kt:86, 121` & `TelemetryGraphColorPreferencesTest.kt:104-119` already persist `SensorType.CADENCE` at `telemetry_graph_color_SENSOR_CADENCE` (`THEME_DEFAULT`); `TelemetryMetricType.CADENCE` must reuse that exact key. Stage 4 requires `docs/engineering/walkthroughs/img/ATT-8096_multisensor_charts.png` (`Rule 8.5`, Light + Dark).

## 3. Scope Grounding & Non-Goals (`ATT-1250`, `2 SP`)
* **In-Scope Goals**:
  1. Generic `WorkoutSamples.db` column discovery (`"<SENSOR_TYPE> (<deviceName>)"`, `>= 2` named sources) for `HR`, `POWER`, `CADENCE` + `PathPoint.cadence` in `WorkoutRepository.kt` (`loadWorkoutDetailTelemetry` & `getWorkoutTelemetryPoints`) and `MapModels.kt`.
  2. `TelemetryMetricType.CADENCE` (`TelemetryMetricGraph.kt`, `TelemetryMetricUtils.kt`, `TelemetryGraphColorPreferences.kt`) + 9-locale `strings.xml` (`graph_heading_cadence`, overlay/mean strings: `Ø %1$d bpm`, `Ø %1$d W`, `Ø %1$d rpm`).
  3. Progressive-disclosure `Layers` overlay toggle (hidden when `<= 1` named source), distinct secondary stroke color/dash, 1-line `Ø` mean comparison legend, and synchronized X-axis scrubbing/header readout in `TelemetryMetricGraph.kt` & `MapDetailLayout.kt`.
  4. Targeted unit/contract/parity tests + `Rule 8.5` Compose PNG (`ATT-8096_multisensor_charts.png`).
* **Out-of-Scope Non-Goals**: Dedicated dual-recording screen, time-lag sliders, Bland-Altman plots, `ALTITUDE*`/`SPEED_mps*` map track layers (`ATT-8080`), or `CSVFileWriter` edits. Complexity verified at **2–3 SP** (self-contained in Aftermath charts/repository; no split needed per `REQ-PRO-029`).

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Shard & ID Grounding**: `docs/requirements/req_workout_management.md` does not exist in this repo (9 shards only; `ATT-8148` is resolving legacy `REQ-WKO-035..039` / `TST-WKO-035..039`). Net-new `REQ-PER-042` / `TST-PER-050` in `docs/requirements/req_aftermath_periods.md` & `docs/tests/tst_aftermath_periods.md` (currently max `REQ-PER-041` / `TST-PER-049`) avoids `035..039` cleanly.
* **Original Requirement ID & Target**: Net-new `REQ-PER-042` extending `REQ-UI-206`, `REQ-UI-260`, `REQ-DAT-033`, `REQ-UI-530`, and `REQ-TRK-029`.
* **Preservation of Core Invariants**: Single-sensor (`<= 1` named source) chart UI unchanged; `Rule 64` plot bounds (`50.dp` / `52.dp` portrait, `0.72f` landscape); `Rule 48` axis labels; `SENSOR_CADENCE` pref key; `TrackerService` & `CSVFileWriter` untouched.

## 5. Change-Request Impact Analysis (SUP.10 / `REQ-PRO-064`)
* **Affected Requirements**: Net-new `REQ-PER-042` (`docs/requirements/req_aftermath_periods.md`).
* **Affected Architecture Elements**: `MapModels.kt`, `WorkoutRepository.kt`, `TrackOnMapAftermathViewModel.kt`, `TelemetryMetricGraph.kt`, `TelemetryMetricUtils.kt`, `TelemetryGraphColorPreferences.kt`, `MapDetailLayout.kt`, `WorkoutDetailTabbedContent.kt`, `values*/strings.xml`.
* **Verification Impact & Test Surface**: Net-new `TST-PER-050`, `WorkoutRepositoryTelemetryTest`, `TelemetryMetricUtilsTest`, `TelemetryGraphColorPreferencesTest`, `TelemetryMetricGraphOverlayTest`, `MapDetailLayoutScrubSynchronizationTest`, `TranslationParityTest`, + `Rule 8.5` PNG.
* **Risk Assessment & Invariants**: Medium risk (`2 SP`); guarded by `cursor.columnNames` lookup for dotted names, `>= 2` named-source gating, and keeping `TelemetryMetricGraph` defaults backward-compatible for `WorkoutSummary.kt` & `HrvRampTestScreen.kt`.

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero UI clutter for `<= 1` source; `Rule 64` & `Rule 48` chart alignment intact; `SENSOR_CADENCE` color key preserved; zero `WorkoutSamples.db` schema changes; zero emojis across 9 locales (`ATT-5400`).
* **Risk Rating**: **MEDIUM** (`2 SP`) — Extends `TelemetryMetricType` (`CADENCE`), SQLite source discovery, synchronized multi-curve Canvas rendering, 9-locale strings, and `Rule 8.5` PNG verification.
