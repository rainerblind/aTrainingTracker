# Stage 2: Requirement & Test Specification - ATT-3929: Add 'Physiology & Training Models' section in Advanced Settings to toggle training science schools

**Ticket**: [ATT-3929](https://rainerblind.atlassian.net/browse/ATT-3929)  
**Sub-task**: [ATT-3934](https://rainerblind.atlassian.net/browse/ATT-3934) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2828](https://rainerblind.atlassian.net/browse/ATT-2828) (*Athlete Physiology, Training Load & Fitness Analytics (Coggan, TSS, PMC, Zones)*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.8`  
**Requirement Mapping**: `REQ-UI-403` (*Progressive Disclosure of Physiology & Training Models in Advanced Settings, Profile & Zones, Live Cockpit Sensor Selector, and Aftermath Views*)  
**Test Spec ID**: `TST-UI-366`  
**Branch**: `feature/ATT-3929`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-17  

---

## 1. Requirement Specification (`REQ-UI-403`)

### 1.1 Problem Statement & Rationale
Athletes vary in the training-science schools they follow. Displaying all four physiology models (Coggan NP/IF/TSS/VI, PMC Fitness/Fatigue/Form, Friel Aerobic Efficiency/Decoupling, and Skiba Critical Power/W'/W'bal) simultaneously overwhelms users who only want basic physics metrics (`Power W`, `Work kJ`, `W/kg`) or classic Coggan power metrics. `REQ-UI-403` introduces a dedicated **Physiology & Training Models** accordion section in Advanced Settings (`AdvancedTuningScreen` / `AdvancedTuningDialog`) to toggle UI visibility of each school independently while preserving background recording and database persistence.

### 1.2 Functional & Architectural Requirements
1. **DataStore Persistence & Default Configuration (`REQ-UI-403.1`)**:
   * `TuningPreferencesDataStore` and `TuningConfig` SHALL define and persist 4 boolean toggles:
     * `enableCogganPowerMetrics: Boolean = true` (Default: **`true` / ON** — `NP`, `IF`, `TSS`, `VI`).
     * `enablePmcModel: Boolean = false` (Default: **`false` / OFF** — `PMC`: `CTL`, `ATL`, `TSB`, `ACWR`, `Ramp Rate`).
     * `enableAerobicEfficiencyModel: Boolean = false` (Default: **`false` / OFF** — `EF`, `Pw:Hr %`, `Pa:Hr %`).
     * `enableCriticalPowerModel: Boolean = false` (Default: **`false` / OFF** — `CP`, `W'`, `W'bal`).
   * `resetToDefaults()` SHALL restore these 4 defaults (`true`, `false`, `false`, `false`).
2. **Advanced Settings Accordion Category & `< 400` Line Modularity (`REQ-UI-403.2` — `REQ-UI-262`)**:
   * `TuningSection` (`AdvancedTuningAccordion.kt`) SHALL include `PHYSIOLOGY_MODELS`, with `buildSectionSummary` rendering a localized summary of active models.
   * `categories/PhysiologyModelsSection.kt` SHALL render 4 Material 3 `Switch` toggle rows with descriptive titles and subtitles.
   * Every `.kt` file in `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/` SHALL strictly remain `< 400` lines (`AdvancedTuningModularityTest`).
3. **Progressive UI Gating Across Consumer Surfaces (`REQ-UI-403.3`)**:
   * **Basic Physics Always Visible**: Instantaneous/Avg/Max `Power (W)`, `Mechanical Work (kJ)`, and `Power-to-Weight (W/kg)` SHALL remain visible whenever power data is present regardless of model toggles.
   * **Coggan Power Metrics (`enableCogganPowerMetrics`)**: Gates `NP`, `IF`, `TSS`, and `VI` in `WorkoutDetails.kt` and filters `SensorType.NORMALIZED_POWER`, `SensorType.INTENSITY_FACTOR`, and `SensorType.TSS` in `EditSensorFieldViewModel.kt` / `EditSensorFieldDialog.kt` when `false`.
   * **PMC (`enablePmcModel`)**: Gates `PerformanceManagementChartCard` in `AthleteProfileScreen.kt` when `false`.
   * **Aerobic Efficiency & Decoupling (`enableAerobicEfficiencyModel`)**: Gates `EF` and `Pw:Hr %` / `Pa:Hr %` in `WorkoutDetails.kt` when `false`.
   * **Critical Power & Anaerobic Battery (`enableCriticalPowerModel`)**: Gates `CP` and `W'` summary cards (restoring the 2x2 grid when `false`), `PlotMetricType.CP` / `PlotMetricType.W_PRIME` plot pills, and `AthleteMetricType.CP_WATTS` / `AthleteMetricType.W_PRIME_KJ` history filter pills in `AthleteProfileScreen.kt`; gates `Min W'bal` and `Empty Tank` warning in `WorkoutDetails.kt`; gates `TelemetryMetricType.W_PRIME_BAL` telemetry charts in `TrackOnMapScreen.kt`, `MapDetailLayout.kt`, and `WorkoutSummary.kt`; and filters `SensorType.W_PRIME_BAL` in `EditSensorFieldViewModel.kt` / `EditSensorFieldDialog.kt` when `false`.
4. **Unconditional Background Calculation & 9-Language Localization Parity (`REQ-UI-403.4`)**:
   * Background recording (`CyclingPowerMetricsDevice`, `TrackerService`) and file import calculation (`LegacyImportEngine`) SHALL remain unconditionally active so historical workouts retain complete telemetry.
   * All new string resources (`tuning_cat_physiology_*`, `tuning_physiology_*`) SHALL exist across all 9 supported locales.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Default Toggles)**:
  * *Given* default `TuningConfig()`,
  * *When* inspected without user overrides,
  * *Then* `enableCogganPowerMetrics == true`, `enablePmcModel == false`, `enableAerobicEfficiencyModel == false`, and `enableCriticalPowerModel == false`.
* **Criterion 2 (Basic Physics vs Coggan Power Metrics)**:
  * *Given* `enableCogganPowerMetrics == false`,
  * *When* `WorkoutDetails` renders a workout with `workKj` and `avgPowerToWeight`,
  * *Then* `Work (kJ)` and `W/kg` remain visible while `NP`, `IF`, `TSS`, and `VI` are hidden.
* **Criterion 3 (Profile & Zones Clean 2x2 Grid & PMC Gating)**:
  * *Given* `enableCriticalPowerModel == false` and `enablePmcModel == false`,
  * *When* `AthleteProfileScreen` renders the Profile tab,
  * *Then* the summary grid displays the 2x2 basic metrics (`Weight`, `Resting HR`, `FTP`, `W/kg`) without `CP`/`W'` cards, omits `CP`/`W'` plot and history filter chips, and hides `PerformanceManagementChartCard`.
* **Criterion 4 (Cockpit Sensor Picker Filtering)**:
  * *Given* `enableCogganPowerMetrics == false` and `enableCriticalPowerModel == false`,
  * *When* `EditSensorFieldViewModel` builds `availableSensors`,
  * *Then* `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, and `W_PRIME_BAL` are excluded from the selectable sensor list (unless currently assigned as `initialSensorType`).

### 1.4 System Invariants
1. Unconditional background calculation and SQLite persistence for all physiology metrics.
2. Every `.kt` file in `ui/settings/tuning/` strictly `< 400` lines (`AdvancedTuningModularityTest`).
3. 100% 9-language localization parity with positional format specifiers (`%1$s`, `%1$d`).
4. Zero regression across existing unit and contract tests.

---

## 2. Test Specification (`TST-UI-366`)

### Test Case 1: `TuningConfig` Defaults, Accordion Enum & `< 400` Line Modularity (`TST-UI-366.1`)
* **Scope**: Unit & Architectural Modularity Test
* **Target Files**: `AdvancedTuningAccordionTest.kt`, `AdvancedTuningModularityTest.kt`, `AdvancedTuningScreenTest.kt`
* **Preconditions**: Default `TuningConfig()` and `ui/settings/tuning/` source tree.
* **Action**: Verify `TuningConfig()` defaults, `TuningSection.entries` sequence, `PhysiologyModelsSection.kt` wiring, and `< 400` line count per file.
* **Expected Result**: `enableCogganPowerMetrics == true`, `enablePmcModel == false`, `enableAerobicEfficiencyModel == false`, `enableCriticalPowerModel == false`, `PhysiologyModelsSection.kt` exists and is wired, and all files in `ui/settings/tuning/` have `< 400` lines.

### Test Case 2: Consumer UI Gating & Sensor Filter Contract (`TST-UI-366.2`)
* **Scope**: Unit & Source Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/PhysiologyModelsVisibilityContractTest.kt`
* **Preconditions**: Consumer UI files (`WorkoutDetails.kt`, `AthleteProfileScreen.kt`, `TrackOnMapScreen.kt`, `MapDetailLayout.kt`, `WorkoutSummary.kt`, `EditSensorFieldViewModel.kt`).
* **Action**: Evaluate visibility filtering helper functions and source contracts across all consumer screens.
* **Expected Result**: All 4 schools are gated by their respective `TuningConfig` boolean flags while basic physics (`Work kJ`, `W/kg`) remain visible whenever power is present.

### Test Case 3: 9-Language Localization & Specifier Audit (`TST-UI-366.3`)
* **Scope**: Localization Parity Test
* **Target File**: `PhysiologyModelsVisibilityContractTest.kt`
* **Goal**: Verify string presence and matching positional format tokens across all 9 locales (`EN`, `DE`, `ES`, `FR`, `IT`, `JA`, `NL`, `PL`, `PT`) for `tuning_cat_physiology_*` and `tuning_physiology_*`.
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 4: Clean-Room Regression Suite (`TST-UI-366.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-366.1` | Unit & Modularity | `TuningConfig`, `AdvancedTuningAccordion`, `PhysiologyModelsSection` | `REQ-UI-403.1`, `REQ-UI-403.2` | Specified |
| `TST-UI-366.2` | Unit & Contract | `WorkoutDetails`, `AthleteProfileScreen`, `EditSensorFieldViewModel` | `REQ-UI-403.3`, `REQ-UI-403.4` | Specified |
| `TST-UI-366.3` | Localization | `PhysiologyModelsVisibilityContractTest` | `REQ-UI-403.4`, `REQ-UI-106` | Specified |
| `TST-UI-366.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
