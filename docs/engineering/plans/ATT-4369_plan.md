# Stage 3: Implementation Plan - ATT-4369: [Verbesserung] Add 'Show in Workout Summary' toggle for each model in Physiology & Training Models settings

**Ticket**: [ATT-4369](https://rainerblind.atlassian.net/browse/ATT-4369)  
**Sub-task**: [ATT-4409](https://rainerblind.atlassian.net/browse/ATT-4409) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Requirement Mapping**: `REQ-UI-424` (`REQ-UI-424.1`–`REQ-UI-424.6`)  
**Test Mapping**: `TST-UI-388` (`TST-UI-388.1`–`TST-UI-388.6`)  
**Branch**: `feature/ATT-4369`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

In **aTrainingTracker**, the overview card for completed workouts (`WorkoutSummary.kt`) incorporates a prominent top telemetry section (`WorkoutDetails.kt`) displaying primary metrics (Active Time, Distance) and optional domain-grouped telemetry rows (Altitude, Power, Aerobic Efficiency, Anaerobic Battery).

In Sprint 2026-42.7 (**ATT-3929** / `REQ-UI-403`), a dedicated accordion category *"Physiology & Training Models"* was added to Advanced Settings (`PhysiologyModelsSection.kt`) with progressive disclosure master toggles for four training science models:
1. **Coggan Power Metrics** (`enableCogganPowerMetrics`: `NP`, `IF`, `TSS`, `VI`)
2. **Performance Management** (`enablePmcModel`: `CTL`, `ATL`, `TSB`, `ACWR`)
3. **Aerobic Efficiency & Decoupling** (`enableAerobicEfficiencyModel`: `EF`, `Pw:Hr / Pa:Hr`)
4. **Critical Power & Anaerobic Battery** (`enableCriticalPowerModel`: `CP`, `W'`, `W'bal`)

During Sprint 2026-43.1 human Joint Review (ticket **ATT-4292**), on-device testing on Google Pixel 10 revealed that when an athlete enables multiple training models to track derived metrics live during activity in Cockpit tabs or to review post-workout telemetry graphs on the map, `WorkoutDetails` automatically surfaces up to 4 additional domain-grouped rows (`Power & Coggan Load`, `Aerobic Efficiency`, and `Anaerobic Battery`). Consequently, `WorkoutSummary` cards exceed 600dp in vertical height, pushing subsequent rich sections (Map Preview, Elevation Profile, Telemetry Charts, Zone Distribution Cards) far below the viewport fold and requiring excessive vertical scrolling in `WorkoutListScreen`.

Currently, these model toggles are all-or-nothing: disabling a model suppresses its overview card summary row, but simultaneously removes its live tiles from the cockpit, disables its charts, and strips it from athlete profile statistics. To restore athlete presentation agency, ticket **ATT-4369** introduces granular subordinate **"Show in Workout Summary"** switches for each model contributing rows to `WorkoutDetails` (Basic Power, Coggan Power, Aerobic Efficiency, Critical Power), allowing athletes to keep models active for live tracking while choosing exactly which summary rows appear on the overview card.

---

## 2. Traceability & Requirements Mapping

This implementation plan establishes complete bidirectional traceability between `REQ-UI-424`, test suite `TST-UI-388`, and the atomic construction steps:

| Requirement ID | Requirement Clause | Test Case ID | Target Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-UI-424.1` | **Subordinate Summary Toggles in Settings UI**: Add Basic Power Metrics model alongside Coggan, PMC, Efficiency, and Critical Power in `PhysiologyModelsSection.kt`. For each model generating summary rows, expose an indented (`16.dp`) subordinate switch "Show in Workout Summary" when its master toggle is ON. | `TST-UI-388.3` | `PhysiologyModelsVisibilityContractTest.kt`, `AdvancedTuningModularityTest.kt`: Unit tests verifying subordinate switch presence, indentation, and conditional rendering when master toggle is enabled. |
| `REQ-UI-424.2` | **DataStore Persistence & Configuration Flow**: Persist 5 new boolean preferences in `TuningPreferencesDataStore` and `TuningConfig` (`enableBasicPowerMetrics`, `showBasicPowerInWorkoutSummary`, `showCogganPowerInWorkoutSummary`, `showAerobicEfficiencyInWorkoutSummary`, `showCriticalPowerInWorkoutSummary`), defaulting to `true`. | `TST-UI-388.3` | `PhysiologyModelsVisibilityContractTest.kt`: Contract tests verifying default values, DataStore `ALL_KEYS` registration, flow emission, and `resetToDefaults()`. |
| `REQ-UI-424.3` | **Power Metric Slot Resolution & Fallback**: In `PowerMetricSlot.kt` (`resolvePowerMetricSlots`), evaluate both master enablement and summary visibility. When Coggan summary is disabled, fallback cleanly to basic physics (`Work kJ`, `W/kg`). When both are disabled, return empty list. | `TST-UI-388.1` | `PowerMetricSlotResolutionTest.kt`: Unit test matrix covering full Coggan, physics fallback, missing FTP fallback, and all-disabled empty slot resolution. |
| `REQ-UI-424.4` | **Presentation Gating in WorkoutDetails**: In `WorkoutDetails.kt`, conditionally render `PowerTrainingMetricsSection`, `AerobicEfficiencyRow`, and `AnaerobicBatteryRow` based on summary visibility flags, preserving 12dp divider cadence and zero duplicate/trailing dividers. Wire callers in `WorkoutSummary.kt` and `WorkoutDetailTabbedContent.kt`. | `TST-UI-388.2` | `WorkoutDetailsLayoutTest.kt`: UI layout contract tests asserting complete row suppression, single-divider cadence, and `< 500` lines modularity limit. |
| `REQ-UI-424.5` | **Preservation of System Invariants & Modularity**: Live cockpit tile gating (`SensorGridScreen.kt`, `EditSensorFieldViewModel.kt`), chart telemetry, background recording in `TrackerService`, and SQLite tables remain completely unaffected. Files in `ui/settings/tuning/` strictly `< 400` lines; `WorkoutDetails.kt` strictly `< 500` lines. | `TST-UI-388.3`, `TST-UI-388.4` | `PhysiologyModelsVisibilityContractTest.kt`, `AdvancedTuningModularityTest.kt`: Contract tests verifying invariant decoupling and strict line count budgets. |
| `REQ-UI-424.6` | **9-Language Localization Parity**: Define `tuning_physiology_basic_power_title`, `tuning_physiology_basic_power_desc`, `tuning_physiology_show_in_workout_summary`, and `tuning_physiology_show_in_workout_summary_desc` across all 9 application locales with 100% parity. | `TST-UI-388.5` | `PhysiologyModelsVisibilityContractTest.kt`: Automated 9-locale parity audit asserting string presence, non-blank text, and matching format specifiers. |
| `REQ-PRO-001` | **Clean-Room Regression Suite**: 100% test pass rate across the entire project test suite with zero regressions. | `TST-UI-388.6` | `./gradlew testDebugUnitTest` |

---

## 3. System Invariants & Preserved Behavior

1. **Strict Code Lock in Stage 3 (Core Rule 4)**: Zero modifications to production source code (`app/src/main/...`) occur during Stage 3. Production construction is strictly deferred to Stage 4 after Gate 3 audit approval (`check-gate`).
2. **100% Backward Compatibility**: All 5 new preference keys default to `true` in `TuningPreferencesDefaults`. A clean installation or existing upgrade exhibits identical visual presentation and behavior until an athlete explicitly customizes summary row visibility.
3. **Background Recording & Telemetry Invariance**: `TrackerService`, `CyclingPowerMetricsDevice`, `LegacyImportEngine`, and SQLite persistence (`extrema`, `workout_summaries`) remain completely untouched. Telemetry data is calculated and stored in full fidelity regardless of UI display flags (`REQ-UI-403.4`).
4. **Live Cockpit Decoupling**: Live cockpit sensor selection (`EditSensorFieldViewModel.kt`, `SensorGridScreen.kt`, `filterVisibleFieldsForTuning`) and map telemetry graphs (`TelemetryMetricType.W_PRIME_BAL`, `TelemetryMetricType.EFFICIENCY_FACTOR`) remain governed strictly by master model toggles (`enableCogganPowerMetrics`, `enableCriticalPowerModel`), completely decoupled from summary card display toggles.
5. **Section 5.10 / REQ-UI-415 Layout & Divider Invariants**:
   - `WorkoutDetails.kt` renders at most one single horizontal row in `PowerTrainingMetricsSection`.
   - Subtle `HorizontalDivider` is rendered preceding each visible domain row after Time & Distance. When a domain row is omitted, its divider is omitted as well, guaranteeing zero consecutive, zero leading, and zero trailing dividers.
6. **Strict Modularity Budgets**:
   - Every file under `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/` (`AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `PhysiologyModelsSection.kt`) must remain strictly `< 400` lines (`REQ-UI-262`, `AdvancedTuningModularityTest`).
   - `WorkoutDetails.kt` must remain strictly `< 500` lines (`REQ-UI-415.3`).
7. **Parent Ticket Terminal Completion (Core Rule 3)**: Subtasks transition to `Erledigt` via `gate-verdict` upon passing review; parent ticket `ATT-4369` terminal closure remains strictly reserved for the human user at `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `TuningPreferencesDataStore.kt` & `TuningConfig`
* Layer: `settings`
* Add 5 preference keys to `TuningPreferencesDataStore`:
  - `KEY_ENABLE_BASIC_POWER_METRICS = booleanPreferencesKey("tuning_enable_basic_power_metrics")`
  - `KEY_SHOW_BASIC_POWER_IN_WORKOUT_SUMMARY = booleanPreferencesKey("tuning_show_basic_power_in_workout_summary")`
  - `KEY_SHOW_COGGAN_POWER_IN_WORKOUT_SUMMARY = booleanPreferencesKey("tuning_show_coggan_power_in_workout_summary")`
  - `KEY_SHOW_AEROBIC_EFFICIENCY_IN_WORKOUT_SUMMARY = booleanPreferencesKey("tuning_show_aerobic_efficiency_in_workout_summary")`
  - `KEY_SHOW_CRITICAL_POWER_IN_WORKOUT_SUMMARY = booleanPreferencesKey("tuning_show_critical_power_in_workout_summary")`
* Add corresponding defaults in `TuningPreferencesDefaults` (all `true`).
* Add properties to `TuningConfig` data class.
* Register all keys in `ALL_KEYS`, `tuningConfigFlow`, `saveTuningConfig(config)`, and `resetToDefaults()`.

### Component 2: `PhysiologyModelsSection.kt`
* Layer: `ui/settings/tuning/categories`
* Add parameters for `enableBasicPowerMetrics`, `onEnableBasicPowerMetricsChange`, and the 4 `show*InWorkoutSummary` flags with change callbacks.
* Model 1: Basic Power Metrics (`enableBasicPowerMetrics`) -> when checked, render subordinate `TuningToggleItem` with `Modifier.padding(start = 16.dp)` for `showBasicPowerInWorkoutSummary`.
* Model 2: Coggan Power Metrics (`enableCogganPowerMetrics`) -> when checked, render subordinate `TuningToggleItem` with `Modifier.padding(start = 16.dp)` for `showCogganPowerInWorkoutSummary`.
* Model 3: Performance Management (`enablePmcModel`) -> master toggle only (no summary row in `WorkoutDetails`).
* Model 4: Aerobic Efficiency & Decoupling (`enableAerobicEfficiencyModel`) -> when checked, render subordinate `TuningToggleItem` with `Modifier.padding(start = 16.dp)` for `showAerobicEfficiencyInWorkoutSummary`.
* Model 5: Critical Power & Anaerobic Battery (`enableCriticalPowerModel`) -> when checked, render subordinate `TuningToggleItem` with `Modifier.padding(start = 16.dp)` for `showCriticalPowerInWorkoutSummary`.
* Modularity constraint: Keep total file lines well under 250 (limit is 400).

### Component 3: `AdvancedTuningScreen.kt` & `AdvancedTuningDialog.kt`
* Layer: `ui/settings/tuning`
* Wire the 5 new state variables into `PhysiologyModelsSection`, `applyConfig`, `onSaveSettings`, and `onResetToDefaults`.
* **Modularity Budget Watch**:
  - `AdvancedTuningScreen.kt` currently has **383 lines** (threshold is 400 lines).
  - Declare state variables and `applyConfig` statements using compact formatting (matching existing conventions in the file) to add at most 5 lines, keeping file size around ~388 lines.
  - `AdvancedTuningDialog.kt` currently has **361 lines**, keeping file size around ~367 lines.

### Component 4: `PowerMetricSlot.kt` & `WorkoutDetails.kt`
* Layer: `ui/components/workoutdetails`
* In `PowerMetricSlot.kt`:
  - Enhance `resolvePowerMetricSlots(data, enableCogganPower, showCogganPower = true, showBasicPower = true, enableBasicPower = true): List<PowerMetricSlot>`.
  - Evaluate `showCoggan = enableCogganPower && showCogganPower && data.normalizedPowerWatts != null`.
  - Evaluate `showBasic = enableBasicPower && showBasicPower`.
  - If `showCoggan == true`: Coggan trio or fallback up to 3 slots.
  - If `showCoggan == false` and `showBasic == true`: basic physics (`Work kJ`, `W/kg`).
  - If `showCoggan == false` and `showBasic == false`: returns empty list.
* In `WorkoutDetails.kt`:
  - Add optional parameters with defaults (`enableBasicPowerMetrics = true`, `showBasicPowerInWorkoutSummary = true`, etc.).
  - Pass flags to `resolvePowerMetricSlots`.
  - Gate `AerobicEfficiencyRow` on `enableAerobicEfficiencyModel && showAerobicEfficiencyInWorkoutSummary && data.efficiencyFactor != null`.
  - Gate `AnaerobicBatteryRow` on `enableCriticalPowerModel && showCriticalPowerInWorkoutSummary && data.minWPrimeBalKj != null && data.minWPrimeBalPct != null`.
  - Modularity constraint: Keep total file lines strictly `< 500` lines (currently 484 lines; changes will add ~5 lines, finishing at ~489 lines).

### Component 5: Presentation Call Sites (`WorkoutSummary.kt`, `WorkoutDetailTabbedContent.kt`)
* In `WorkoutSummary.kt` (line 200): Pass `showBasicPowerInWorkoutSummary`, `showCogganPowerInWorkoutSummary`, `showAerobicEfficiencyInWorkoutSummary`, and `showCriticalPowerInWorkoutSummary` from `effectiveTuningConfig` to `WorkoutDetails`.
* In `WorkoutDetailTabbedContent.kt` (line 485): Pass flags from `tuningConfig` to `WorkoutDetails`.

### UI Consistency (Rule 23 — Mandatory Section)
* **Reference screen / component**: Existing `PhysiologyModelsSection.kt` and `WorkoutMasksAndCardsSection.kt` in Advanced Settings.
* **Reused components**:
  - `TuningToggleItem` from `com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningFormControls.kt` for both master and subordinate toggles.
  - `HorizontalDivider` with `color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)` for section separation.
  - `WorkoutDomainRow` in `WorkoutDetails.kt` conforming to `docs/design_guidelines.md` Section 5.10.
* **Theme tokens**:
  - Subordinate indentation: `Modifier.padding(start = 16.dp)`.
  - Spacing: `Arrangement.spacedBy(12.dp)` in `PhysiologyModelsSection` and outer `WorkoutDetails` column.
  - Typography: `MaterialTheme.typography.bodyMedium` for titles, `bodySmall` for descriptions.
  - Colors: Standard Material 3 semantic color tokens (`MaterialTheme.colorScheme.onSurface`, `onSurfaceVariant`, `outlineVariant`).
* **New one-off styles & justification**: None. 100% standard Material 3 theme tokens and existing tuning form controls reused.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: 9-Language Localization Resources (`REQ-UI-424.6`, `TST-UI-388.5`)
* **Files**: `app/src/main/res/values*/strings.xml` across all 9 canonical locales:
  - `values/strings.xml` (EN)
  - `values-de/strings.xml` (DE)
  - `values-es/strings.xml` (ES)
  - `values-fr/strings.xml` (FR)
  - `values-it/strings.xml` (IT)
  - `values-ja/strings.xml` (JA)
  - `values-nl/strings.xml` (NL)
  - `values-pl/strings.xml` (PL)
  - `values-pt/strings.xml` (PT)
* **Changes**: Add string resources for:
  - `tuning_physiology_basic_power_title`: "Basic Power Metrics (Work, W/kg)" / DE: "Basis-Leistungsmetriken (Arbeit, W/kg)"
  - `tuning_physiology_basic_power_desc`: "Track mechanical work (kJ) and power-to-weight ratio (W/kg) in workouts and cockpit" / DE: "Mechanische Arbeit (kJ) und Leistungsgewicht (W/kg) in Workouts und Cockpit erfassen"
  - `tuning_physiology_show_in_workout_summary`: "Show in Workout Summary" / DE: "In Workout-Zusammenfassung anzeigen"
  - `tuning_physiology_show_in_workout_summary_desc`: "Display summary telemetry row on the workout overview card" / DE: "Zusammenfassungszeile auf der Workout-Übersichtskarte anzeigen"

### Step 2: DataStore Preferences & Defaults (`REQ-UI-424.2`, `TST-UI-388.3`)
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`
* **Changes**:
  - Add 5 constants in `TuningPreferencesDefaults` (all `true`).
  - Add 5 `Preferences.Key<Boolean>` definitions in `TuningPreferencesDataStore`.
  - Add 5 properties to `TuningConfig` data class with defaults.
  - Register keys in `ALL_KEYS`, `tuningConfigFlow`, `saveTuningConfig`, and `resetToDefaults()`.

### Step 3: PhysiologyModelsSection UI Enhancement (`REQ-UI-424.1`, `TST-UI-388.3`)
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/PhysiologyModelsSection.kt`
* **Changes**:
  - Add parameters for `enableBasicPowerMetrics`, `onEnableBasicPowerMetricsChange`, and the 4 `show*InWorkoutSummary` flags with callbacks.
  - Render Basic Power Metrics `TuningToggleItem` followed by indented subordinate switch when enabled.
  - Under Coggan, Aerobic Efficiency, and Critical Power, render indented subordinate switches when their master toggles are enabled.
  - Update previews. Verify file length strictly `< 400` lines.

### Step 4: AdvancedTuningScreen & AdvancedTuningDialog Wiring (`REQ-UI-424.1`, `TST-UI-388.3`)
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`
* **Changes**:
  - Remember the 5 new state variables.
  - Pass variables and callbacks to `PhysiologyModelsSection`.
  - Update `applyConfig`, `onSaveSettings`, and `onResetToDefaults`.
  - **Audit file lengths**: Ensure `AdvancedTuningScreen.kt` remains strictly `< 400` lines (current: 383) and `AdvancedTuningDialog.kt` remains strictly `< 400` lines (current: 361).

### Step 5: PowerMetricSlot Resolution Logic (`REQ-UI-424.3`, `TST-UI-388.1`)
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/PowerMetricSlot.kt`
* **Changes**:
  - Extend `resolvePowerMetricSlots` signature with default arguments: `showCogganPower = true, showBasicPower = true, enableBasicPower = true`.
  - Update slot selection to respect `showCoggan` and `showBasic`.
  - Return empty list when both are false or no metrics available.

### Step 6: WorkoutDetails Presentation Gating (`REQ-UI-424.4`, `TST-UI-388.2`)
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetails.kt`
* **Changes**:
  - Add optional parameters with default values for `enableBasicPowerMetrics` and the 4 `show*InWorkoutSummary` flags.
  - Pass power flags to `resolvePowerMetricSlots`.
  - Gate `AerobicEfficiencyRow` on `showAerobicEfficiencyInWorkoutSummary`.
  - Gate `AnaerobicBatteryRow` on `showCriticalPowerInWorkoutSummary`.
  - Verify file length strictly `< 500` lines (current: 484).

### Step 7: Consumer Call Site Wiring (`REQ-UI-424.4`)
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContent.kt`
* **Changes**:
  - Pass the 5 summary and model preferences from `effectiveTuningConfig` / `tuningConfig` to `WorkoutDetails`.

### Step 8: Unit and Contract Tests Construction (`TST-UI-388.1`–`TST-UI-388.5`)
* **Files**:
  - Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/PowerMetricSlotResolutionTest.kt` (`TST-UI-388.1`).
  - Update `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetailsLayoutTest.kt` (`TST-UI-388.2`).
  - Update `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/PhysiologyModelsVisibilityContractTest.kt` (`TST-UI-388.3`, `TST-UI-388.4`, `TST-UI-388.5`).
  - Run `AdvancedTuningModularityTest.kt` (`TST-UI-388.3`).

### Step 9: Verification & Regression Test Execution
* **Targeted Commands**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.workoutdetails.PowerMetricSlotResolutionTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsLayoutTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.PhysiologyModelsVisibilityContractTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningModularityTest"
  ```
* **Full Clean-Room Suite**:
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Targeted unit tests verify priority resolution, gating permutations, and fallback rules.
  - Contract tests verify `< 400` lines in tuning files, `< 500` lines in `WorkoutDetails.kt`, and 100% 9-language translation parity.
  - Invariant contract tests assert live cockpit sensor selection and background tracking remain completely decoupled from summary display flags.
  - Full project test suite execution (`./gradlew testDebugUnitTest`) guarantees zero regressions.
* **Rollback Plan**:
  - All changes reside in branch `feature/ATT-4369`. If any blocker or irreconcilable defect is detected, the branch can be cleanly reverted or reset to the base commit without impacting `develop` or other sprint deliverables.
