# Stage 2: Requirement & Test Specification - ATT-4369: [Verbesserung] Add 'Show in Workout Summary' toggle for each model in Physiology & Training Models settings

**Ticket**: [ATT-4369](https://rainerblind.atlassian.net/browse/ATT-4369)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Requirement Mapping**: `REQ-UI-424` (*Show in Workout Summary Toggles for Physiology Models in Advanced Settings and Granular Presentation Gating in WorkoutDetails*)  
**Test Spec ID**: `TST-UI-388`  
**Branch**: `feature/ATT-4369`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification (REQ-UI-424)

### 1.1 Problem Statement & Rationale
During Sprint 2026-43.1 Joint Review (ticket ATT-4292), testing on Google Pixel 10 revealed that when athletes enable multiple training science models in `Settings -> Advanced Settings -> Physiology & Training Models` (`enableCogganPowerMetrics`, `enableAerobicEfficiencyModel`, `enableCriticalPowerModel`), the overview card in `WorkoutDetails` (`WorkoutDetails.kt`) automatically expands with up to 4 additional domain-grouped rows (`Power & Coggan Load`, `Aerobic Efficiency`, `Anaerobic Battery`), exceeding 600dp in vertical height.

Athletes who wish to track live metrics (e.g., Normalized Power or W'bal on cockpit tiles per REQ-UI-409) or inspect post-workout telemetry graphs currently have no choice but to accept giant overview cards in `WorkoutListScreen`. To restore athlete presentation agency, the system must provide granular subordinate "Show in Workout Summary" switches for each model contributing rows to `WorkoutDetails`, allowing athletes to keep models active for live tracking while choosing exactly which telemetry rows appear on the overview card.

### 1.2 Functional & Architectural Requirements

1. **Subordinate Summary Toggles in Physiology & Training Models (`PhysiologyModelsSection.kt`, `REQ-UI-424.1`)**:
   - In `Settings -> Advanced Settings -> Physiology & Training Models` (`PhysiologyModelsSection.kt`), each training science model that generates rows in `WorkoutDetails` SHALL provide a secondary switch titled "Show in Workout Summary" (`R.string.tuning_physiology_show_in_workout_summary`, with description `R.string.tuning_physiology_show_in_workout_summary_desc`):
     - **Basic Power Metrics**: `enableBasicPowerMetrics` (master, default `true`), with subordinate `showBasicPowerInWorkoutSummary` (default `true`).
     - **Coggan Power Metrics**: `enableCogganPowerMetrics` (master, default `true`), with subordinate `showCogganPowerInWorkoutSummary` (default `true`).
     - **Aerobic Efficiency & Decoupling**: `enableAerobicEfficiencyModel` (master, default `false`), with subordinate `showAerobicEfficiencyInWorkoutSummary` (default `true`).
     - **Critical Power & Anaerobic Battery**: `enableCriticalPowerModel` (master, default `false`), with subordinate `showCriticalPowerInWorkoutSummary` (default `true`).
   - Subordinate toggles SHALL be indented (`16.dp` start padding) and displayed conditionally when the corresponding master model toggle is enabled (`true`).
   - The Performance Management model (`enablePmcModel`) SHALL NOT render a summary toggle, as it contributes no telemetry rows to `WorkoutDetails`.

2. **Jetpack DataStore Persistence & Configuration Flow (`TuningPreferencesDataStore.kt`, `REQ-UI-424.2`)**:
   - `TuningPreferencesDataStore` and `TuningConfig` SHALL persist and emit the 5 new boolean preferences:
     - `KEY_ENABLE_BASIC_POWER_METRICS` / `enableBasicPowerMetrics` (default `true`)
     - `KEY_SHOW_BASIC_POWER_IN_WORKOUT_SUMMARY` / `showBasicPowerInWorkoutSummary` (default `true`)
     - `KEY_SHOW_COGGAN_POWER_IN_WORKOUT_SUMMARY` / `showCogganPowerInWorkoutSummary` (default `true`)
     - `KEY_SHOW_AEROBIC_EFFICIENCY_IN_WORKOUT_SUMMARY` / `showAerobicEfficiencyInWorkoutSummary` (default `true`)
     - `KEY_SHOW_CRITICAL_POWER_IN_WORKOUT_SUMMARY` / `showCriticalPowerInWorkoutSummary` (default `true`)
   - All new keys SHALL be included in `ALL_KEYS`, `tuningConfigFlow`, `saveTuningConfig(config)`, and `resetToDefaults()`.

3. **Power Metric Slot Resolution & Priority Fallback (`PowerMetricSlot.kt`, `REQ-UI-424.3`)**:
   - `resolvePowerMetricSlots` SHALL evaluate both master enablement and summary visibility:
     - `showCoggan = enableCogganPowerMetrics && showCogganPowerInWorkoutSummary && data.normalizedPowerWatts != null`
     - `showBasic = enableBasicPowerMetrics && showBasicPowerInWorkoutSummary`
   - When `showCoggan == true`:
     - If both `data.intensityFactor != null` and `data.tss != null`: return `[NORMALIZED_POWER, INTENSITY_FACTOR, TSS]`.
     - If either FTP metric is null: return `NORMALIZED_POWER`, followed by available metrics up to 3 slots: `IF`, `TSS`, and if `showBasic == true`, `WORK_KJ` and `POWER_TO_WEIGHT`.
   - When `showCoggan == false` and `showBasic == true`:
     - Return available basic physics metrics: `WORK_KJ` and `POWER_TO_WEIGHT` (up to 2 slots).
   - When both `showCoggan == false` and `showBasic == false` (or when no metrics are present):
     - Return an empty list, omitting `PowerTrainingMetricsSection` completely from `WorkoutDetails`.

4. **Granular Presentation Gating & Visual Cadence in `WorkoutDetails` (`WorkoutDetails.kt`, `REQ-UI-424.4`)**:
   - `WorkoutDetails.kt` SHALL consume the new summary preferences:
     - Preceding `HorizontalDivider` and `PowerTrainingMetricsSection` SHALL be rendered if and only if `powerSlots.isNotEmpty()`.
     - Preceding `HorizontalDivider` and `AerobicEfficiencyRow` SHALL be rendered if and only if `enableAerobicEfficiencyModel && showAerobicEfficiencyInWorkoutSummary && data.efficiencyFactor != null`.
     - Preceding `HorizontalDivider` and `AnaerobicBatteryRow` SHALL be rendered if and only if `enableCriticalPowerModel && showCriticalPowerInWorkoutSummary && data.minWPrimeBalKj != null && data.minWPrimeBalPct != null`.
   - When an athlete disables summary display for all power and physiology models, `WorkoutDetails` SHALL render only the top Time & Distance row (and Altitude if elevation is present), eliminating unnecessary card expansion while preserving 12dp divider cadence and zero duplicate/trailing dividers (`REQ-UI-415.2`).
   - Callers `WorkoutSummary.kt` and `WorkoutDetailTabbedContent.kt` SHALL pass the corresponding preferences from `TuningConfig`.

5. **Preservation of System Invariants & Modularity Budgets (`REQ-UI-424.5`)**:
   - Live cockpit tile gating (`SensorGridScreen.kt`, `EditSensorFieldViewModel.kt`, `filterVisibleFieldsForTuning`) and chart telemetry metrics SHALL continue to depend strictly on master model toggles (`enableCogganPowerMetrics`, `enableCriticalPowerModel`), completely unaffected by the summary toggles.
   - Background recording in `TrackerService`, `CyclingPowerMetricsDevice`, and SQLite extrema/streams SHALL remain 100% active and uninhibited.
   - All `.kt` files in `ui/settings/tuning/` (`AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `PhysiologyModelsSection.kt`) SHALL strictly remain `< 400` lines.
   - `WorkoutDetails.kt` SHALL strictly remain `< 500` lines.

6. **9-Language Localization Parity (`REQ-UI-424.6`)**:
   - All new string keys (`tuning_physiology_basic_power_title`, `tuning_physiology_basic_power_desc`, `tuning_physiology_show_in_workout_summary`, `tuning_physiology_show_in_workout_summary_desc`) SHALL be defined across all 9 supported application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with 100% parity.

### 1.3 Acceptance Criteria (Given-When-Then)

- **Criterion 1 (Accordion Layout & Progressive Disclosure)**:
  - *Given* `Settings -> Advanced Settings -> Physiology & Training Models` is opened,
  - *When* inspecting the models list,
  - *Then* `Basic Power Metrics` is listed alongside Coggan, PMC, Aerobic Efficiency, and Critical Power, and each model with summary rows (Basic Power, Coggan, Efficiency, Critical Power) renders an indented subordinate switch "Show in Workout Summary" when its master toggle is ON.

- **Criterion 2 (Granular Overview Card Suppression)**:
  - *Given* an athlete has enabled all physiology models (`enableCogganPowerMetrics = true`, `enableAerobicEfficiencyModel = true`, `enableCriticalPowerModel = true`),
  - *When* the athlete toggles OFF "Show in Workout Summary" for Coggan, Basic Power, Aerobic Efficiency, and Critical Power,
  - *Then* live cockpit tiles (`NP`, `IF`, `TSS`, `W'bal`) and post-workout telemetry charts on the map remain fully functional and available, but `WorkoutDetails` on `WorkoutSummary` cards completely omits the Power, Aerobic Efficiency, and Anaerobic Battery domain rows (and their preceding dividers), reducing the card height to only Active Time, Distance, and Altitude.

- **Criterion 3 (Physics Fallback When Coggan Summary Disabled)**:
  - *Given* a workout with valid power data and `enableCogganPowerMetrics = true`,
  - *When* `showCogganPowerInWorkoutSummary = false` while `enableBasicPowerMetrics = true` and `showBasicPowerInWorkoutSummary = true`,
  - *Then* `WorkoutDetails` displays the Power domain row containing strictly basic physics metrics (`Work kJ`, `W/kg`) without Coggan metrics (`NP`, `IF`, `TSS`).

- **Criterion 4 (100% Backward Compatibility on Install/Upgrade)**:
  - *Given* a fresh install or existing user upgrade,
  - *When* `TuningConfig` is initialized,
  - *Then* all subordinate summary switches default to `true`, preserving 100% visual and functional backward compatibility.

### 1.4 System Invariants
- **Background Telemetry Integrity**: `TrackerService`, `CyclingPowerMetricsDevice`, `LegacyImportEngine`, and SQLite tables remain completely untouched. All metrics are computed and persisted in full fidelity.
- **Cockpit Tile Isolation**: Cockpit tile visibility (`SensorGridScreen.kt`, `isSensorTypeVisibleForTuning`, `filterVisibleFieldsForTuning`) continues to depend strictly on master model toggles (`enableCogganPowerMetrics`, `enableCriticalPowerModel`).
- **Modularity Budgets**: All files under `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/` strictly `< 400` lines; `WorkoutDetails.kt` strictly `< 500` lines.
- **Human Gate Sovereignty**: Moving parent ticket `ATT-4369` to `Erledigt` is reserved strictly for the human user (`Core Rule 3`).
- **9-Locale Translation Parity**: 100% string key and format parity across all 9 locales.

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   Net-new requirement `REQ-UI-424`, refining `REQ-UI-403` (*Progressive Disclosure of Physiology & Training Models in Advanced Settings, Profile & Zones, Live Cockpit Sensor Selector, and Aftermath Views* - `ATT-3929`) and `REQ-UI-415` (*Single-Line Power Row and Visual Domain Row Separation with Subtle Horizontal Dividers in WorkoutDetails* - `ATT-4042`). Targets `PhysiologyModelsSection.kt`, `TuningPreferencesDataStore.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `WorkoutDetails.kt`, `PowerMetricSlot.kt`, `WorkoutSummary.kt`, `WorkoutDetailTabbedContent.kt`, and `res/values*/strings.xml`.

2. **Historical Origin & Commit Trace**:
   - Sprint 2026-42.7 (`ATT-3929`, commit `a888c381`): Introduced `PhysiologyModelsSection.kt` with initial toggles for Coggan, PMC, Aerobic Efficiency, and Critical Power.
   - Sprint 2026-42.8 (`ATT-4042`, commit `5f178dc9`): Established `PowerMetricSlot.kt`, single-line power row priority fallback, and subtle horizontal dividers in `WorkoutDetails.kt`.
   - Sprint 2026-43.1 Joint Review (`ATT-4292`): Human testing across typical workout scenarios on Pixel 10 discovered that with all models enabled, `WorkoutDetails` expands beyond 600dp in height, crowding out map previews, charts, and zone cards.

3. **Root Reason for Existing Formulation**:
   When physiology models were first created in `ATT-3929`, progressive disclosure used a single master toggle per training science school to keep settings minimal and avoid overwhelming users with options. However, tightly coupling model calculation/cockpit telemetry with overview card summary rows forced athletes into an all-or-nothing trade-off, cluttering `WorkoutListScreen`.

4. **Preservation of Core Invariants**:
   - 100% backward compatibility: All 4 "Show in Workout Summary" flags and the Basic Power Metrics toggle default to `true`.
   - Background computation & SQLite storage: Unconditionally active and unaffected (`REQ-UI-403.4`).
   - Section 5.10 / `REQ-UI-415` alignment: Single-line grouped layout and divider invariants strictly preserved; zero orphan dividers or gaps when rows are suppressed.
   - Modularity budgets: `< 400` lines for all files in `ui/settings/tuning/` (`REQ-UI-262`) and `< 500` lines for `WorkoutDetails.kt` (`REQ-UI-415.3`).
   - 9-language parity: 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 2. Test Specification (TST-UI-388)

### Test Case 1: `testPowerMetricSlot_resolutionWithGranularSummaryFlags` (`[TST-UI-388.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/PowerMetricSlotResolutionTest.kt`
* **Preconditions**: Workout details data with non-null `normalizedPowerWatts`, `intensityFactor`, `tss`, `workKj`, and `avgPowerToWeight`.
* **Action**:
  1. Invoke `resolvePowerMetricSlots(data, enableCogganPower = true, showCogganPower = true, enableBasicPower = true, showBasicPower = true)`.
  2. Invoke with `enableCogganPower = true, showCogganPower = false, enableBasicPower = true, showBasicPower = true`.
  3. Invoke with `enableCogganPower = true, showCogganPower = false, enableBasicPower = false`.
  4. Invoke with `showCogganPower = false, showBasicPower = false`.
  5. Invoke with `enableCogganPower = true, showCogganPower = true`, null `intensityFactor`/`tss`, and `showBasicPower = true`.
  6. Invoke with `enableCogganPower = true, showCogganPower = true`, null `intensityFactor`/`tss`, and `showBasicPower = false`.
* **Expected Result**:
  - Case 1: Returns strictly `[NORMALIZED_POWER, INTENSITY_FACTOR, TSS]`.
  - Case 2: Returns strictly `[WORK_KJ, POWER_TO_WEIGHT]`.
  - Case 3: Returns empty list `[]`.
  - Case 4: Returns empty list `[]`.
  - Case 5: Returns `[NORMALIZED_POWER, WORK_KJ, POWER_TO_WEIGHT]`.
  - Case 6: Returns `[NORMALIZED_POWER]`.

### Test Case 2: `testWorkoutDetails_gatingAndDividerIntegrity` (`[TST-UI-388.2]`)
* **Scope**: UI Layout & Divider Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetailsLayoutTest.kt`
* **Preconditions**: `WorkoutDetails.kt` source and runtime rendering.
* **Action**:
  1. Inspect `WorkoutDetails` signature for `enableBasicPowerMetrics`, `showBasicPowerInWorkoutSummary`, `showCogganPowerInWorkoutSummary`, `showAerobicEfficiencyInWorkoutSummary`, `showCriticalPowerInWorkoutSummary`.
  2. Verify that when all summary flags are `false`, `PowerTrainingMetricsSection`, `AerobicEfficiencyRow`, and `AnaerobicBatteryRow` are omitted.
  3. Verify that dividers are only rendered preceding visible sections, producing zero consecutive/duplicate dividers.
  4. Assert `WorkoutDetails.kt` line count is strictly `< 500` lines.
* **Expected Result**: Complete presentation gating, clean single-divider cadence, and `< 500` lines modularity verified.

### Test Case 3: `testTuningConfig_persistenceAndModularity` (`[TST-UI-388.3]`)
* **Scope**: Contract & Modularity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/PhysiologyModelsVisibilityContractTest.kt`, `AdvancedTuningModularityTest.kt`
* **Preconditions**: `TuningConfig`, `TuningPreferencesDefaults`, and `TuningPreferencesDataStore`.
* **Action**:
  1. Assert default values of all 5 new preferences are `true`.
  2. Assert `TuningPreferencesDataStore.ALL_KEYS` contains all 5 preference keys.
  3. Assert `PhysiologyModelsSection.kt` renders subordinate switches when master switches are checked.
  4. Parse and verify file lengths: `PhysiologyModelsSection.kt < 400`, `AdvancedTuningScreen.kt < 400`, `AdvancedTuningDialog.kt < 400`.
* **Expected Result**: All defaults verified, keys registered, subordinate UI rendered, and all tuning files strictly `< 400` lines.

### Test Case 4: `testLiveCockpitAndBackgroundPersistenceInvariants` (`[TST-UI-388.4]`)
* **Scope**: Invariant Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/PhysiologyModelsVisibilityContractTest.kt`
* **Preconditions**: `EditSensorFieldViewModel.shouldIncludeSensorType` and `SensorGridScreen` field filter.
* **Action**:
  1. Evaluate `shouldIncludeSensorType` with `showCogganPowerInWorkoutSummary = false` and `showCriticalPowerInWorkoutSummary = false`.
  2. Verify that `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, and `W_PRIME_BAL` sensor field availability is determined strictly by `enableCogganPowerMetrics` and `enableCriticalPowerModel`.
  3. Verify `TrackerService` metric computation pipeline is unaffected.
* **Expected Result**: Live cockpit and background sensor recording invariants remain 100% decoupled from summary card display preferences.

### Test Case 5: 9-Language Localization & Specifier Audit (`[TST-UI-388.5]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/PhysiologyModelsVisibilityContractTest.kt`
* **Goal**: Verify string presence, non-blank text, and matching format specifiers across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt`):
  - `tuning_physiology_basic_power_title`
  - `tuning_physiology_basic_power_desc`
  - `tuning_physiology_show_in_workout_summary`
  - `tuning_physiology_show_in_workout_summary_desc`
* **Expected Result**: 100% translation parity across all 9 locales with zero missing keys.

### Test Case 6: Clean-Room Regression Suite (`[TST-UI-388.6]`)
* **Scope**: Regression Test
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero regressions.
* **Expected Result**: 100% test pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-388.1]` | Unit | `PowerMetricSlot.resolvePowerMetricSlots` | `REQ-UI-424.3` | Specified |
| `[TST-UI-388.2]` | Contract | `WorkoutDetails.kt` (gating & dividers) | `REQ-UI-424.4`, `REQ-UI-415.2` | Specified |
| `[TST-UI-388.3]` | Contract | `TuningPreferencesDataStore`, `TuningConfig`, `PhysiologyModelsSection` | `REQ-UI-424.1`, `REQ-UI-424.2`, `REQ-UI-424.5` | Specified |
| `[TST-UI-388.4]` | Invariant | `EditSensorFieldViewModel.shouldIncludeSensorType`, `SensorGridScreen` | `REQ-UI-424.5` | Specified |
| `[TST-UI-388.5]` | Localization | 9-locale parity audit across `values*/strings.xml` | `REQ-UI-424.6`, `REQ-LOC-001` | Specified |
| `[TST-UI-388.6]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
