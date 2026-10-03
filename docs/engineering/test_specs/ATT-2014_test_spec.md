# Stage 2: Requirement & Test Specification - ATT-2014: Configurable Minimum Pace Ceiling in Expert Settings (Default 3:00 min/km)

**Ticket**: [ATT-2014](https://rainerblind.atlassian.net/browse/ATT-2014)  
**Sub-task**: [ATT-2103](https://rainerblind.atlassian.net/browse/ATT-2103) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.12`  
**Requirement Mapping**: `REQ-UI-243` (*Aftermath/Graphs: Configurable Minimum Pace Ceiling in Expert Settings with Athletic Default 3:00 min/km*)  
**Test Spec ID**: `TST-UI-202`  
**Branch**: `feature/ATT-2014`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Requirement Specification (REQ-UI-243)

### 1.1 Problem Statement & Rationale
In the Aftermath workout inspection screens (`TrackOnMapScreen.kt`, `MapDetailLayout.kt`, and `TelemetryMetricGraph.kt`), running workouts (`BSportType.RUN`) render a continuous running pace curve ("Tempo").

Previously, `TelemetryMetricGraph.kt` clamped pace values to a hardcoded `1.5` min/km (1:30 min/km = 40 km/h) to prevent division-by-zero. When transient GPS noise spikes occur upon initial lock or signal occlusion, the top Y-axis label scaled to `1:30`.
1. A 1:30 min/km pace is physiologically impossible for human distance runners.
2. An upper bound of 1:30 against a lower bound of 20:00 compresses the athlete's actual running pace curve (typically 4:30 – 6:30 min/km) into an unreadable thin line in the middle of the graph.

Providing a user-configurable minimum pace ceiling in Expert Settings (Section 4: Aftermath & Analyse) defaulting to **3:00 min/km** (and ~4:50 min/mi in Imperial) restores athletic realism, protects chart readability against GPS noise spikes, and allows athletes to tailor the dynamic range of their running pace charts.

### 1.2 Functional & Architectural Requirements
The system SHALL provide a configurable minimum pace ceiling in Expert Settings and enforce it across Aftermath running pace telemetry graphs (`REQ-UI-243` / `ATT-2014`):

1. **Preference Data Model & Persistence (`TuningPreferencesDataStore.kt`)**:
   - `TuningPreferencesDefaults` SHALL declare:
     - `DEFAULT_PACE_CEILING_MIN_KM = 3.0f` (3:00 min/km)
     - `MIN_PACE_CEILING_MIN_KM = 2.0f` (2:00 min/km)
     - `MAX_PACE_CEILING_MIN_KM = 6.0f` (6:00 min/km)
   - `TuningConfig` SHALL declare `val paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM`.
   - `TuningPreferencesDataStore` SHALL persist this setting under key `KEY_PACE_CEILING_MIN_KM = floatPreferencesKey("tuning_pace_ceiling_min_km")`.
   - On read and write, values SHALL be clamped to `[MIN_PACE_CEILING_MIN_KM, MAX_PACE_CEILING_MIN_KM]`.
   - Factory reset (`resetToDefaults()`) SHALL restore `paceCeilingMinKm` to `DEFAULT_PACE_CEILING_MIN_KM`.

2. **Expert Settings UI Integration (`AdvancedTuningDialog.kt`)**:
   - In Section 4 (*"Aftermath & Analyse"* / `tuning_cat_aftermath`), the system SHALL render a `TuningSliderItem`:
     - Title: `stringResource(R.string.tuning_pace_ceiling_title)` ("Schnellste Tempo-Grenze" / "Minimum Pace Ceiling").
     - Description: `stringResource(R.string.tuning_pace_ceiling_desc)` ("Filtert GPS-Ausreißer im Tempo-Diagramm. Werte schneller als diese Grenze werden abgeschnitten." / "Filters GPS noise spikes in the pace chart. Speeds faster than this ceiling are clamped.").
     - Slider bounds: `valueRange = 2.0f..6.0f`, `steps = 15` (16 discrete intervals of 0.25 min / 15 seconds: 2:00, 2:15, 2:30, 2:45, 3:00, 3:15, ..., 6:00).
     - Value display: formatted with active units (`3:00 min/km` in Metric; converted to `min/mi` in Imperial).
     - Default text: formatted default (`Default: 3:00 min/km` / `Default: 4:50 min/mi`).

3. **Telemetry Pace Graph Clamping & Y-Axis Bounding (`TelemetryMetricGraph.kt`)**:
   - `TelemetryMetricGraph` SHALL accept optional parameter:
     `paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM`.
   - In `extractMetricValue` for `TelemetryMetricType.PACE`:
     - Unit-adjusted ceiling: `effectiveCeiling = if (unit == MyUnits.METRIC) paceCeilingMinKm.toDouble() else paceCeilingMinKm.toDouble() * (BANALService.METER_PER_MILE / 1000.0)`.
     - Data points faster than `effectiveCeiling` SHALL be clamped:
       `(secPerUnit / 60.0).coerceIn(effectiveCeiling, 20.0)`.
   - In `calculateBounds` for `TelemetryMetricType.PACE`:
     - `val paceMin = (min * 0.95).coerceAtLeast(effectiveCeiling)`.
     - Top Y-axis label (`formatPaceMinutes(bounds.first)`) SHALL NEVER be faster than `effectiveCeiling`.
     - The hardcoded `1.5` bound SHALL be eliminated.

4. **Layout Integration (`MapDetailLayout.kt`)**:
   - `MapDetailLayout` SHALL read `tuningConfig.paceCeilingMinKm` from `tuningDataStore.tuningConfigFlow` and forward it to `TelemetryMetricGraph` when `metricType == TelemetryMetricType.PACE`.

5. **100% 9-Language Localization Parity**:
   - Newly introduced string keys `tuning_pace_ceiling_title` and `tuning_pace_ceiling_desc` SHALL be translated across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Expert Setting Availability)**:
  * *Given* an athlete in Settings -> Expert Settings,
  * *When* expanding Section 4 (Aftermath & Profile Analytics),
  * *Then* a slider for "Minimum Pace Ceiling" ("Schnellste Tempo-Grenze") SHALL be visible and interactive.
* **AC-2 (Factory Default 3:00 min/km)**:
  * *Given* a fresh install or unconfigured state,
  * *When* querying the preference,
  * *Then* the default pace ceiling SHALL be exactly 3:00 min/km (3.0 decimal minutes).
* **AC-3 (Pace Graph Clamping & Y-Axis Scaling)**:
  * *Given* a running workout with GPS noise spikes faster than the configured ceiling (e.g. 1:15 or 1:30 min/km),
  * *When* rendering the Tempo graph in Aftermath,
  * *Then* the top Y-axis label SHALL NOT exceed the configured ceiling (e.g. 3:00 min/km), data points faster than the ceiling SHALL be clamped to the ceiling, and 1:30 min/km SHALL never be shown.
* **AC-4 (Unit Parity)**:
  * *Given* Imperial units are enabled (`MyUnits.IMPERIAL`),
  * *When* viewing and setting the preference,
  * *Then* the value SHALL be formatted and clamped in min/mile consistently (~4:50 min/mi for 3:00 min/km).
* **AC-5 (Localization Parity)**:
  * *Given* any of the 9 supported locales,
  * *When* browsing Expert Settings,
  * *Then* the preference title, summary, and default text SHALL be 100% localized.

### 1.4 System Invariants
* Inverted pace plotting (faster pace higher, slower pace lower) MUST remain strictly preserved.
* Athletic `mm:ss` formatting (`formatPaceMinutes`) MUST remain intact.
* Paused / stopped speed filtering (<0.55 m/s) MUST remain intact.
* Non-running sports (cycling speed, hiking) MUST NOT be affected.
* Backward compatibility for standalone callers of `TelemetryMetricGraph` MUST be preserved via default parameter `paceCeilingMinKm = DEFAULT_PACE_CEILING_MIN_KM`.

---

## 2. Test Specification (TST-UI-202)

### Test Case 1: `TuningPreferencesDataStorePaceCeilingTest` (`TST-UI-202.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStorePaceCeilingTest.kt`
* **Preconditions**: Test DataStore instance.
* **Action**:
  1. Verify `TuningConfig()` defaults `paceCeilingMinKm` to `3.0f`.
  2. Verify saving a custom ceiling (e.g. `2.5f`, `4.0f`) persists and re-reads cleanly.
  3. Verify values outside `[2.0f, 6.0f]` are clamped to the valid range.
  4. Verify `resetToDefaults()` restores `paceCeilingMinKm` to `3.0f`.
* **Expected Result**: 100% assertions pass.

### Test Case 2: `AdvancedTuningPaceCeilingContractTest` (`TST-UI-202.2`)
* **Scope**: Structural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningPaceCeilingContractTest.kt`
* **Preconditions**: Project checked out on branch `feature/ATT-2014`.
* **Action**:
  1. Inspect `AdvancedTuningDialog.kt` source code to verify `AftermathAnalysisSection` hosts `TuningSliderItem` for `paceCeilingMinKm`.
  2. Verify slider `valueRange` is `2.0f..6.0f` with `steps = 15`.
  3. Verify `MapDetailLayout.kt` forwards `paceCeilingMinKm` to `TelemetryMetricGraph`.
* **Expected Result**: 100% assertions pass.

### Test Case 3: `TelemetryMetricGraphPaceCeilingTest` (`TST-UI-202.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraphPaceCeilingTest.kt`
* **Preconditions**: Synthesized `PathPoint` track with GPS spike points (e.g. speed = 15 m/s, raw pace = 1.11 min/km).
* **Action**:
  1. Extract metric value with default ceiling `3.0f` min/km. Verify returned value is clamped to `3.0` min/km (never 1.11 or 1.5).
  2. Extract metric value with custom ceiling `2.5f` min/km. Verify returned value is clamped to `2.5` min/km.
  3. Compute bounds with GPS spike points. Verify `bounds.first` is at least `effectiveCeiling` (e.g. `3.0`), ensuring top Y-axis label formats to "3:00" and never "1:30".
  4. Test Imperial conversion: verify ceiling scales by `BANALService.METER_PER_MILE / 1000.0`.
* **Expected Result**: 100% assertions pass.

### Test Case 4: 9-Language Localization Audit (`TST-UI-202.4`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Action**: Run `TranslationParityTest` to confirm all localized strings remain 100% synchronized across all 9 locales.
* **Expected Result**: 100% pass rate.

### Test Case 5: Clean-Room Full Suite Regression (`TST-UI-202.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite (1,380+ tests passing).

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-202.1` | Unit | `TuningPreferencesDataStorePaceCeilingTest` | `REQ-UI-243` | Specified |
| `TST-UI-202.2` | Contract | `AdvancedTuningPaceCeilingContractTest` | `REQ-UI-243` | Specified |
| `TST-UI-202.3` | Unit | `TelemetryMetricGraphPaceCeilingTest` | `REQ-UI-243` | Specified |
| `TST-UI-202.4` | Localization | `TranslationParityTest` | `REQ-UI-243`, `REQ-LOC-001` | Specified |
| `TST-UI-202.5` | Full Suite Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-014` | Specified |
