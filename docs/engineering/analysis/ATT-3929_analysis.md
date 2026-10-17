# Stage 1 Analysis: ATT-3929 - Add 'Physiology & Training Models' section in Advanced Settings to toggle training science schools

**Ticket**: [ATT-3929](https://rainerblind.atlassian.net/browse/ATT-3929)  
**Sub-task**: [ATT-3931](https://rainerblind.atlassian.net/browse/ATT-3931) (`[Analysis]`)  
**Parent Epic**: [ATT-2828](https://rainerblind.atlassian.net/browse/ATT-2828) (*Athlete Physiology, Training Load & Fitness Analytics (Coggan, TSS, PMC, Zones)*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.8`  
**Branch**: `feature/ATT-3929`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-17  

---

## 1. Problem Statement & Motivation

Over the course of Epic `ATT-2828`, `aTrainingTracker` has implemented four distinct sports-science and exercise-physiology schools:
1. **Coggan Power Metrics**: Normalized Power (`NP`), Intensity Factor (`IF`), Training Stress Score (`TSS`), and Variability Index (`VI`).
2. **Performance Management Chart (PMC / Banister Impulse-Response)**: Acute Training Load (`ATL`), Chronic Training Load (`CTL`), Training Stress Balance (`TSB`), and ACWR.
3. **Aerobic Efficiency & Decoupling (Friel)**: Efficiency Factor (`EF`) and Aerobic Decoupling (`Pw:Hr %` / `Pa:Hr %`).
4. **Critical Power & Anaerobic Battery (Skiba / Monod-Scherrer)**: Critical Power (`CP`), Anaerobic Work Capacity (`W'`), and Dynamic Anaerobic Work Balance (`W'bal`).

Currently, all four physiological model groups are unconditionally rendered across the application whenever power or heart rate telemetry is present. For recreational athletes or users who only follow basic physics metrics (`Power W`, `Work kJ`, `W/kg`) or classic Coggan metrics (`NP`, `IF`, `TSS`), displaying every advanced model simultaneously introduces visual clutter across:
* **Workout Summary cards & Workout Details** (`WorkoutDetails.kt`, `WorkoutSummary.kt`, `MapDetailLayout.kt`, `TrackOnMapScreen.kt`)
* **Profile & Zones (`AthleteProfileScreen.kt`)**: CP and W' summary cards (expanding the 2x2 grid into 3 rows), CP/W' plot selector chips, CP/W' history filter chips, and the full-width `PerformanceManagementChartCard`.
* **Live Cockpit Sensor Selector (`EditSensorFieldDialog.kt` / `EditSensorFieldViewModel.kt`)**: Sensor picker lists `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, and `W_PRIME_BAL` even when the athlete does not use those models.

**Expected Behavior**:
Introduce a dedicated **Physiology & Training Models** (*Physiologie & Trainingsmodelle*) accordion category in **Advanced Settings** (`AdvancedTuningScreen.kt` / `AdvancedTuningDialog.kt`) with 4 independent boolean toggles persisted in `TuningPreferencesDataStore`:
1. **Coggan Power Metrics (`NP`, `IF`, `TSS`, `VI`)** — Default: **`true` (ON)**
2. **Performance Management (`PMC`: `ATL`, `CTL`, `TSB`)** — Default: **`false` (OFF, opt-in)**
3. **Aerobic Efficiency & Decoupling (`EF`, `Pw:Hr`, `Pa:Hr`)** — Default: **`false` (OFF, opt-in)**
4. **Critical Power & Anaerobic Battery (`CP`, `W'`, `W'bal`)** — Default: **`false` (OFF, opt-in)**

Crucially, **database persistence and background recording/calculation remain unconditionally active** when sensors are connected so historical workouts retain complete physiological telemetry if an athlete later enables a school in Advanced Settings.

---

## 2. Root Cause Analysis (Forensic Investigation)

A forensic audit of the codebase identified all architectural touchpoints required to implement `ATT-3929`:

1. **DataStore & Domain Configuration (`TuningPreferencesDataStore.kt`)**:
   * `TuningConfig` holds all advanced tuning preferences and exposes both individual `Flow<Boolean>` streams and a unified `tuningConfigFlow: Flow<TuningConfig>`.
   * Adding `enableCogganPowerMetrics: Boolean = true`, `enablePmcModel: Boolean = false`, `enableAerobicEfficiencyModel: Boolean = false`, and `enableCriticalPowerModel: Boolean = false` to `TuningConfig` (with corresponding `booleanPreferencesKey` constants, individual flows, setter suspend functions, and defaults restoration in `resetToDefaults()`) cleanly provides reactive state across all screens.

2. **Advanced Settings Accordion (`ui/settings/tuning/`)**:
   * `AdvancedTuningAccordion.kt` defines `enum class TuningSection` and `buildSectionSummary(...)`.
   * **Modularity Invariant (`AdvancedTuningModularityTest.kt`)**: `testAllTuningSourceFilesUnder400Lines` strictly enforces that every `.kt` file under `ui/settings/tuning/` has `< 400` lines. Currently:
     * `AdvancedTuningScreen.kt` is 381 lines.
     * `AdvancedTuningDialog.kt` is 376 lines.
     * `AdvancedTuningAccordion.kt` is 376 lines.
   * Consequently, the new section UI must be encapsulated in `categories/PhysiologyModelsSection.kt`, and state wiring in `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, and `AdvancedTuningAccordion.kt` must be kept compact (strictly `< 400` lines per file).
   * `AdvancedTuningAccordionTest.kt` tests `TuningSection.entries` in `testTuningSectionEnum_canonicalLifecycleSequence` and must be updated to include `TuningSection.PHYSIOLOGY_MODELS`.

3. **Workout Summary & Details (`WorkoutDetails.kt`, `WorkoutSummary.kt`, `MapDetailLayout.kt`, `TrackOnMapScreen.kt`)**:
   * `WorkoutDetails(...)` currently renders:
     * Power & Physiology badges (`NP`, `IF`, `TSS`, `VI`, `Work`, `W/kg`): Basic physics metrics (`Work kJ` and `W/kg`) must remain **always visible** whenever power/energy data exists, whereas `NP`, `IF`, `TSS`, and `VI` must be gated by `enableCogganPowerMetrics`.
     * Aerobic Efficiency & Decoupling (`EF` and `Pw:Hr %` / `Pa:Hr %`): Must be gated by `enableAerobicEfficiencyModel`.
     * Anaerobic Battery (`Min W'bal` and `Empty Tank` badge): Must be gated by `enableCriticalPowerModel`.
   * In `TrackOnMapScreen.kt`, `MapDetailLayout.kt`, and `WorkoutSummary.kt`:
     * The `TelemetryMetricType.W_PRIME_BAL` interactive and preview charts (and `hasTelemetryGraphs` check for `wPrimeBalPoints`) must be gated by `enableCriticalPowerModel`.

4. **Profile & Zones Screen (`AthleteProfileScreen.kt` & `AthleteProfileViewModel.kt`)**:
   * `AthleteProfileScreen` renders:
     * Summary Grid: Currently a 3x2 grid showing Weight, Resting HR, FTP, W/kg, CP, and W'. When `enableCriticalPowerModel == false`, hiding the 3rd row (CP and W' cards) restores the clean 2x2 grid (Weight, Resting HR, FTP, W/kg).
     * Historical Evolution Plot (`HistoricalEvolutionPlotCard`): Plot selector pills include `PlotMetricType.CP` and `PlotMetricType.W_PRIME`. When `enableCriticalPowerModel == false`, those pills must be filtered out (and if the currently selected plot metric is `CP` or `W_PRIME`, it must fall back to `PlotMetricType.WEIGHT`).
     * `PerformanceManagementChartCard`: Must only be rendered when `enablePmcModel == true`.
     * History Log Filter Chips: Filter pills for `AthleteMetricType.CP_WATTS` and `AthleteMetricType.W_PRIME_KJ` must be hidden when `enableCriticalPowerModel == false` (and if active, reset filter to `null`).

5. **Cockpit Sensor Field Selector (`EditSensorFieldViewModel.kt` / `EditSensorFieldDialog.kt`)**:
   * `EditSensorFieldViewModel` constructs the available `SensorType` list in `init`. By injecting or reading `TuningPreferencesDataStore` (or passing visibility flags), `SensorType.NORMALIZED_POWER`, `SensorType.INTENSITY_FACTOR`, and `SensorType.TSS` are filtered out when `!enableCogganPowerMetrics`, and `SensorType.W_PRIME_BAL` is filtered out when `!enableCriticalPowerModel` (unless it is the field's currently selected `initialSensorType`, preserving existing layout integrity when editing).

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Add 4 boolean preferences (`enableCogganPowerMetrics = true`, `enablePmcModel = false`, `enableAerobicEfficiencyModel = false`, `enableCriticalPowerModel = false`) to `TuningPreferencesDataStore` and `TuningConfig`.
  * Create `PhysiologyModelsSection.kt` in `ui/settings/tuning/categories/` and integrate `TuningSection.PHYSIOLOGY_MODELS` into `AdvancedTuningAccordion.kt`, `AdvancedTuningDialog.kt`, and `AdvancedTuningScreen.kt` (keeping all files `< 400` lines).
  * Gate UI visibility of the 4 physiology schools across `WorkoutDetails.kt`, `WorkoutSummary.kt`, `MapDetailLayout.kt`, `TrackOnMapScreen.kt`, `AthleteProfileScreen.kt`, and `EditSensorFieldViewModel.kt`.
  * Keep basic physics metrics (`Power W`, `Work kJ`, `W/kg`) always visible when power is present.
  * Keep background computation and SQLite persistence unconditionally active.
  * Provide full 9-language localization (`en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) for the new section title, summary states, and 4 toggle titles/subtitles.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Redesigning the `WorkoutDetails` power/physiology layout into `AltitudeRow` + `MetricItem` columns (explicitly scoped to the immediate next ticket in this sprint, `ATT-3930`).
  * Modifying background calculation formulas for NP, IF, TSS, PMC, EF, Decoupling, or W'bal.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-TUN-001` (Advanced Tuning Preferences & Accordion Categories) and `REQ-PRF-001` / `REQ-PMC-001` / `REQ-DEC-001` / `REQ-WBL-001`.
* **Historical Origin & Commit Trace**: Introduced across `ATT-2828` physiology tickets (`ATT-3494`, `ATT-3495`, `ATT-3496`, `ATT-3497`, `ATT-3672`) and modularized in `ATT-3325` (`AdvancedTuningModularityTest`).
* **Root Reason for Existing Formulation**: Originally, each physiology school was surfaced immediately upon implementation so athletes could inspect the new metrics. Simultaneously, `AdvancedTuningModularityTest` was instituted to prevent monolithic settings files exceeding 400 lines.
* **Preservation of Core Invariants**: We preserve all calculation and persistence invariants in `WorkoutSummaryViewModel`, `TrainingLoadRepository`, and `WorkoutRepository`, while introducing net-new requirement `REQ-TUN-012` for progressive disclosure of physiological models in the UI and strictly respecting the `< 400` line modularity cap.

---

## 5. Architectural Strategy & High-Level Solution

1. **Persistence Layer (`TuningPreferencesDataStore.kt`)**:
   * Keys:
     * `ENABLE_COGGAN_POWER_METRICS = booleanPreferencesKey("enable_coggan_power_metrics")` (default `true`)
     * `ENABLE_PMC_MODEL = booleanPreferencesKey("enable_pmc_model")` (default `false`)
     * `ENABLE_AEROBIC_EFFICIENCY_MODEL = booleanPreferencesKey("enable_aerobic_efficiency_model")` (default `false`)
     * `ENABLE_CRITICAL_POWER_MODEL = booleanPreferencesKey("enable_critical_power_model")` (default `false`)
   * Expose flows, setters, and include in `TuningConfig` and `resetToDefaults()`.
2. **Settings UI Layer (`ui/settings/tuning/`)**:
   * Add `PHYSIOLOGY_MODELS` to `TuningSection`.
   * Implement `PhysiologyModelsSection.kt` using `SwitchWithLabel` / standard M3 `Switch` toggle rows matching `ClimbSegmentTuningSection.kt`.
   * Update `buildSectionSummary` in `AdvancedTuningAccordion.kt` to show active model count or active school names in the collapsed accordion subtitle.
3. **Consumer Screens**:
   * `WorkoutDetails`: Accept `enableCogganPowerMetrics: Boolean = true`, `enableAerobicEfficiencyModel: Boolean = false`, `enableCriticalPowerModel: Boolean = false` (or read from `TuningConfig` passed down from `WorkoutSummariesWithMap` / `TrackOnMapScreen` / `MapDetailLayout`). Note: defaulting `enableCogganPowerMetrics = true` and passing values from `TuningPreferencesDataStore` ensures reactive updates.
   * `AthleteProfileScreen`: Read `tuningConfig` from `AthleteProfileViewModel` (or `TuningPreferencesDataStore`) and conditionally display CP/W' summary cards, plot chips, history filter chips, and `PerformanceManagementChartCard`.
   * `EditSensorFieldViewModel`: Filter `SensorType.NORMALIZED_POWER`, `SensorType.INTENSITY_FACTOR`, `SensorType.TSS`, and `SensorType.W_PRIME_BAL` based on the tuning flags.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in background telemetry recording and SQLite persistence (`NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, `EFFICIENCY_FACTOR`, `AEROBIC_DECOUPLING`, `MIN_W_PRIME_BAL` are still calculated and stored).
  2. Strict adherence to `< 400` lines per file in `ui/settings/tuning/` (`AdvancedTuningModularityTest`).
  3. 9-language localization parity across all `values*/strings.xml` files.
  4. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  * Changes are purely additive in `TuningPreferencesDataStore` and conditionally gate UI rendering without altering underlying database schemas or sensor recording pipelines.
