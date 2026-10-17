# Stage 3: Implementation Plan - ATT-3929: Add 'Physiology & Training Models' section in Advanced Settings to toggle training science schools

**Ticket**: [ATT-3929](https://rainerblind.atlassian.net/browse/ATT-3929)  
**Sub-task**: [ATT-3936](https://rainerblind.atlassian.net/browse/ATT-3936) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2828](https://rainerblind.atlassian.net/browse/ATT-2828) (*Athlete Physiology, Training Load & Fitness Analytics (Coggan, TSS, PMC, Zones)*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.8`  
**Requirement Mapping**: `REQ-UI-403`  
**Test Mapping**: `TST-UI-366`  
**Branch**: `feature/ATT-3929`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-17  

---

## 1. Problem Description & Background

To prevent UI overload across Workout Summaries, Workout Details, Profile & Zones, and the Live Cockpit Sensor Selector, `ATT-3929` adds a dedicated **Physiology & Training Models** (*Physiologie & Trainingsmodelle*) accordion category in **Advanced Settings** (`AdvancedTuningScreen` / `AdvancedTuningDialog`) with 4 independent boolean toggles:
1. `enableCogganPowerMetrics: Boolean = true` (Default: `true` / ON — `NP`, `IF`, `TSS`, `VI`).
2. `enablePmcModel: Boolean = false` (Default: `false` / OFF — `PMC`: `ATL`, `CTL`, `TSB`, `ACWR`, `Ramp Rate`).
3. `enableAerobicEfficiencyModel: Boolean = false` (Default: `false` / OFF — `EF`, `Pw:Hr %`, `Pa:Hr %`).
4. `enableCriticalPowerModel: Boolean = false` (Default: `false` / OFF — `CP`, `W'`, `W'bal`).

Basic physics metrics (`Power W`, `Work kJ`, `W/kg`) remain permanently visible when power data exists, and background computation/database persistence remain unconditionally active.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-403` (*Progressive Disclosure of Physiology & Training Models in Advanced Settings, Profile & Zones, Live Cockpit Sensor Selector, and Aftermath Views*)
* **Test Mapping**: `TST-UI-366` (*Physiology & Training Models Advanced Tuning Category, Consumer UI Gating, < 400 Line Modularity & 9-Language Parity Verification Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites continue to pass cleanly.
2. **Unconditional Background Persistence**: `CyclingPowerMetricsDevice`, `TrackerService.finalizeLiveSession`, and `LegacyImportEngine.recalculateStats` continue computing and persisting all physiology metrics in SQLite regardless of UI toggle states.
3. **Strict `< 400` Line Modularity (`REQ-UI-262`)**: Every `.kt` file in `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/` strictly remains `< 400` lines (`AdvancedTuningModularityTest`).
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Preferences & DataStore (`TuningPreferencesDataStore.kt`)
* Add `enableCogganPowerMetrics: Boolean = true`, `enablePmcModel: Boolean = false`, `enableAerobicEfficiencyModel: Boolean = false`, and `enableCriticalPowerModel: Boolean = false` to `TuningConfig`.
* Define DataStore keys (`ENABLE_COGGAN_POWER_METRICS`, `ENABLE_PMC_MODEL`, `ENABLE_AEROBIC_EFFICIENCY_MODEL`, `ENABLE_CRITICAL_POWER_MODEL`), default constants, individual `Flow<Boolean>` properties, and `suspend fun setEnable...` mutators.
* Populate the 4 fields in `tuningConfigFlow` and restore defaults (`true`, `false`, `false`, `false`) in `resetToDefaults()`.

### Component 2: Advanced Settings Accordion (`ui/settings/tuning/`)
* Add `PHYSIOLOGY_MODELS` to `enum class TuningSection` in `AdvancedTuningAccordion.kt` (between `WORKOUT_MASKS` and `AUDIO_CUES`), and format a concise summary of active schools in `buildSectionSummary(...)`.
* Create `categories/PhysiologyModelsSection.kt` rendering 4 Material 3 `Switch` toggle rows (`Coggan Power Metrics`, `Performance Management (PMC)`, `Aerobic Efficiency & Decoupling`, `Critical Power & Anaerobic Battery`).
* Wire `TuningSection.PHYSIOLOGY_MODELS` and `PhysiologyModelsSection(...)` into `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt`, compacting blank lines/formatting so all files stay strictly `< 400` lines.

### Component 3: Consumer UI Gating
* **WorkoutDetails, WorkoutSummary, MapDetailLayout, TrackOnMapScreen**:
  * Add `enableCogganPowerMetrics: Boolean = true`, `enableAerobicEfficiencyModel: Boolean = false`, and `enableCriticalPowerModel: Boolean = false` parameters (with defaults matching `TuningConfig()`) to `WorkoutDetails`, `WorkoutSummary`, and `MapDetailLayout`, and read `tuningConfig` from `TuningPreferencesDataStore` in `WorkoutSummariesWithMap` and `TrackOnMapScreen`.
  * In `WorkoutDetails.kt`:
    * Keep `Work (kJ)` and `W/kg` unconditionally visible when power/work metrics are present; only render `NP`, `IF`, `TSS`, and `VI` when `enableCogganPowerMetrics == true`.
    * Render the Aerobic Efficiency (`EF`, `Pw:Hr %` / `Pa:Hr %`) row only when `enableAerobicEfficiencyModel == true`.
    * Render the Anaerobic Battery (`Min W'bal` / `Empty Tank`) row only when `enableCriticalPowerModel == true`.
  * In `WorkoutSummary.kt`, `MapDetailLayout.kt`, and `TrackOnMapScreen.kt`:
    * Gate `wPrimeBalPoints` in `hasTelemetryGraphs` and `TelemetryMetricGraph(metricType = TelemetryMetricType.W_PRIME_BAL, ...)` on `enableCriticalPowerModel == true`.
* **Profile & Zones (`AthleteProfileScreen.kt` & `AthleteProfileViewModel.kt`)**:
  * Expose `enablePmcModel: Boolean = false` and `enableCriticalPowerModel: Boolean = false` in `AthleteProfileUiState` (subscribed reactively from `TuningPreferencesDataStore.tuningConfigFlow` in `AthleteProfileViewModel`).
  * In `AthleteProfileContent`:
    * Render the 3rd summary card row (`CP` and `W'`) only when `state.enableCriticalPowerModel == true` (restoring the clean 2x2 grid when `false`).
    * Filter `PlotMetricType.CP` and `PlotMetricType.W_PRIME` out of the plot selector pills when `!state.enableCriticalPowerModel` (falling back to `PlotMetricType.WEIGHT` if currently selected).
    * Render `PerformanceManagementChartCard` only when `state.enablePmcModel == true`.
    * Filter `AthleteMetricType.CP_WATTS` and `AthleteMetricType.W_PRIME_KJ` out of history filter chips when `!state.enableCriticalPowerModel`.
* **Live Cockpit Sensor Selector (`EditSensorFieldViewModel.kt`)**:
  * Add helper `shouldIncludeSensorType(type: SensorType, initialSensorType: SensorType?, enableCogganPowerMetrics: Boolean, enableCriticalPowerModel: Boolean): Boolean` and subscribe/read `TuningPreferencesDataStore` in `EditSensorFieldViewModel` so `NORMALIZED_POWER`, `INTENSITY_FACTOR`, and `TSS` are excluded when `!enableCogganPowerMetrics` and `W_PRIME_BAL` is excluded when `!enableCriticalPowerModel` (unless `type == initialSensorType`).

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `ClimbSegmentTuningSection.kt` and `AdvancedTuningAccordion.kt` in `ui/settings/tuning/`.
* **Reused components**: `TuningSectionCard`, Material 3 `Switch`, `HorizontalDivider`.
* **Theme tokens**: shapes `RoundedCornerShape(12.dp)`, spacing `8.dp` / `12.dp` / `16.dp`, colors `MaterialTheme.colorScheme.onSurface` / `onSurfaceVariant` / `outlineVariant`.
* **New one-off styles & justification**: None. Reuses existing `AdvancedTuningScreen` accordion and switch row visual patterns 1:1.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Localization Strings across all 9 Locales
* Files: `app/src/main/res/values*/strings.xml` (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`)
* Changes: Add `tuning_cat_physiology_models`, `tuning_physiology_none_enabled`, `tuning_physiology_coggan_short`, `tuning_physiology_pmc_short`, `tuning_physiology_ef_short`, `tuning_physiology_cp_short`, and title/desc strings for the 4 toggles (`tuning_physiology_coggan_title`, `tuning_physiology_coggan_desc`, `tuning_physiology_pmc_title`, `tuning_physiology_pmc_desc`, `tuning_physiology_ef_title`, `tuning_physiology_ef_desc`, `tuning_physiology_cp_title`, `tuning_physiology_cp_desc`).

### Step 2: `TuningPreferencesDataStore` & Accordion Category UI
* Files: `TuningPreferencesDataStore.kt`, `AdvancedTuningAccordion.kt`, `categories/PhysiologyModelsSection.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`
* Changes: Implement DataStore keys/flows/setters, `TuningSection.PHYSIOLOGY_MODELS`, `PhysiologyModelsSection`, and screen/dialog integration while maintaining `< 400` lines per file.

### Step 3: Consumer UI Gating (`WorkoutDetails`, `WorkoutSummary`, `MapDetailLayout`, `TrackOnMapScreen`, `AthleteProfileScreen`, `EditSensorFieldViewModel`)
* Files: `WorkoutDetails.kt`, `WorkoutSummary.kt`, `WorkoutSummariesWithMap.kt`, `MapDetailLayout.kt`, `TrackOnMapScreen.kt`, `AthleteProfileViewModel.kt`, `AthleteProfileScreen.kt`, `EditSensorFieldViewModel.kt`
* Changes: Wire `enableCogganPowerMetrics`, `enablePmcModel`, `enableAerobicEfficiencyModel`, and `enableCriticalPowerModel` across all consumer views and sensor filtering.

### Step 4: Targeted Unit & Contract Tests
* Files: `AdvancedTuningAccordionTest.kt`, `AdvancedTuningModularityTest.kt`, `AdvancedTuningScreenTest.kt`, `PhysiologyModelsVisibilityContractTest.kt`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit and modularity tests in Stage 4, followed by full `./gradlew testDebugUnitTest` clean-room regression suite in Stage 5.
* **Rollback**: Isolated on `feature/ATT-3929`; can be reverted cleanly without affecting `sprint/2026-42.8` or `develop`.
