# Stage 1 Analysis: ATT-4369 - [Verbesserung] Add 'Show in Workout Summary' toggle for each model in Physiology & Training Models settings

**Ticket**: [ATT-4369](https://rainerblind.atlassian.net/browse/ATT-4369)  
**Sub-task**: [ATT-4407](https://rainerblind.atlassian.net/browse/ATT-4407) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Branch**: `feature/ATT-4369`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-43.1 Joint Review of ticket **ATT-4292** (*"Remove double percent symbols in efficiency and W'bal strings"*), human on-device verification across typical workout scenarios on Pixel 10 identified a significant User Experience (UX) and visual ergonomics issue on the primary Workout Summary cards (`WorkoutSummary.kt` and `WorkoutDetails.kt`):

### 1.1 Excessive Vertical Card Height with Multiple Enabled Physiology Models
In Sprint 2026-42.7 (**ATT-3929** / `REQ-UI-403`), a dedicated accordion category *"Physiology & Training Models"* (*Physiologie & Trainingsmodelle*) was introduced in **Advanced Settings** (`PhysiologyModelsSection.kt`), providing progressive disclosure switches for advanced training science frameworks:
1. **Coggan Power Metrics** (`enableCogganPowerMetrics`: `NP`, `IF`, `TSS`, `VI`)
2. **Performance Management** (`enablePmcModel`: `CTL`, `ATL`, `TSB`, `ACWR`, `Ramp Rate`)
3. **Aerobic Efficiency & Decoupling** (`enableAerobicEfficiencyModel`: `EF`, `Pw:Hr`, `Pa:Hr`)
4. **Critical Power & Anaerobic Battery** (`enableCriticalPowerModel`: `CP`, `W'`, `W'bal`)

When an athlete enables these physiology models to track derived metrics during live tracking (e.g. `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, or `W_PRIME_BAL` tiles on `ActivityType.BIKE_POWER` cockpit tabs per `REQ-UI-409`) or to analyze post-workout telemetry graphs (`TelemetryMetricType.W_PRIME_BAL` and `TelemetryMetricType.EFFICIENCY_FACTOR` per `REQ-UI-403`), `WorkoutDetails` (`WorkoutDetails.kt`) automatically surfaces up to **4 additional domain-grouped rows**:
- **Row 1 (Top Identity Anchor)**: Active Time & Distance (always visible, `REQ-UI-255`)
- **Row 2 (Altitude)**: Ascent and Descent (visible whenever elevation gain is recorded)
- **Row 3 (Power & Coggan Load)**: Coggan Trio (`NP / VI`, `IF`, `TSS`) or Basic Physics (`Work kJ`, `W/kg`)
- **Row 4 (Aerobic Efficiency)**: `EF` and `Pw:Hr` / `Pa:Hr` (with high-drift status color)
- **Row 5 (Anaerobic Battery)**: `Min W'bal` (kJ and %) + Empty Tank warning

Each domain row follows `docs/design_guidelines.md` Section 5.10 (`WorkoutDomainRow` with a 24dp domain icon, 20dp horizontal spacer, vertical metric labels/values, and subtle `HorizontalDivider` separators per `REQ-UI-415`).

### 1.2 User Pain Point: Inflexible All-or-Nothing Model Gating
Because model toggles are currently **all-or-nothing**, an athlete who desires live W'bal tracking during cycling or post-workout W'bal graph analysis on the map route has no way to prevent the W'bal row from appearing on their `WorkoutSummary` cards in the workout list. 

With all models active:
- A single `WorkoutSummary` card expands to over 600dp in vertical height.
- Subsequent rich cards (Map Preview, Elevation Profile, Telemetry Charts, Heart Rate & Power Zone Distribution Cards, Strava Upload Status) are pushed far below the viewport fold.
- Browsing workout history in `WorkoutListScreen` requires substantial vertical scrolling through repetitive telemetry blocks.
- Many athletes prefer a minimalist overview card (e.g. Time, Distance, and Elevation, or Time, Distance, and Basic Work kJ) while reserving deep physiological inspection for the full detail view (`TrackOnMapScreen.kt`) or live cockpit.

### 1.3 Expected Behavior (Ticket ATT-4369)
In `Settings -> Advanced Settings -> Physiology & Training Models` (`PhysiologyModelsSection.kt`), for each physiology model that contributes a telemetry row to `WorkoutDetails`:
1. **Basic Power Metrics** (`Work kJ`, `W/kg`)
2. **Coggan Power Metrics** (`NP / VI`, `IF`, `TSS`)
3. **Aerobic Efficiency & Decoupling** (`EF`, `Pw:Hr / Pa:Hr`)
4. **Critical Power & Anaerobic Battery** (`Min W'bal`)

expose a subordinate toggle:
> **"Show in Workout Summary"** (*In Workout-Zusammenfassung anzeigen*)

- Subordinate toggles are nested under each respective model and progressively disclosed when the primary model toggle is enabled.
- All subordinate toggles default to `true` (ON) to ensure 100% backward compatibility (`REQ-PRO-022`).
- Setting a subordinate toggle to `false` removes that specific domain row from `WorkoutDetails` without affecting live tracking tiles, cockpit sensor selectors, profile statistics, or post-workout telemetry charts.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Monolithic Coupling in `WorkoutDetails.kt`
Forensic inspection of `WorkoutDetails.kt` (`buildVisibleWorkoutDetailSections`, lines 99–145) reveals the structural cause of the issue:

```kotlin
// WorkoutDetails.kt (lines 121-144):
val powerSlots = resolvePowerMetricSlots(data, enableCogganPowerMetrics)
if (powerSlots.isNotEmpty()) {
    add {
        PowerTrainingMetricsSection(
            data = data,
            enableCogganPowerMetrics = enableCogganPowerMetrics,
            slots = powerSlots
        )
    }
}

// Section 4: Efficiency Factor (EF) & Aerobic Decoupling
if (enableAerobicEfficiencyModel && data.efficiencyFactor != null) {
    add { AerobicEfficiencyRow(data = data) }
}

// Section 5: Anaerobic Battery (W'bal) Minimum & Empty-Tank Status
if (enableCriticalPowerModel && data.minWPrimeBalKj != null && data.minWPrimeBalPct != null) {
    add { AnaerobicBatteryRow(data = data) }
}
```

The domain row visibility check in `WorkoutDetails` relies **exclusively** on the primary feature flags:
- `enableCogganPowerMetrics`
- `enableAerobicEfficiencyModel`
- `enableCriticalPowerModel`

There is zero intermediate configuration or presentation preference that distinguishes between *“model is active for calculation and live sensor telemetry”* versus *“render summary row on the workout overview card”*.

### 2.2 Unconditional Basic Power Physics Display
Under `REQ-UI-403.3` (*"Basic Physics Always Visible"*), basic power metrics (`Work kJ` and `W/kg`) were designated to remain visible whenever power data is present, even when `enableCogganPowerMetrics == false`:

```kotlin
// PowerMetricSlot.kt (lines 61-64):
data.workKj != null || data.avgPowerToWeight != null -> buildList {
    data.workKj?.let { add(PowerMetricSlot.WORK_KJ) }
    data.avgPowerToWeight?.let { add(PowerMetricSlot.POWER_TO_WEIGHT) }
}
```

While this was a good safeguard against losing all power feedback when Coggan is turned off, it created an asymmetry: an athlete who records power data cannot hide the Power domain row at all. If `enableCogganPowerMetrics == true`, they get `NP / IF / TSS`. If `enableCogganPowerMetrics == false`, they get `Work kJ / W/kg`. There is currently no switch to suppress the Power domain row entirely from the summary card.

### 2.3 DataStore & Preferences Schema Gap
Inspection of `TuningPreferencesDataStore.kt` shows that only 4 boolean keys exist for physiology models:
- `KEY_ENABLE_COGGAN_POWER_METRICS`
- `KEY_ENABLE_PMC_MODEL`
- `KEY_ENABLE_AEROBIC_EFFICIENCY_MODEL`
- `KEY_ENABLE_CRITICAL_POWER_MODEL`

None of the summary visibility preferences exist in `TuningConfig`, `TuningPreferencesDefaults`, or `TuningPreferencesDataStore`.

### 2.4 Call Site Audit
`WorkoutDetails` is consumed at two distinct UI call sites:
1. **`WorkoutSummary.kt` (line 200)**: Primary workout card in `WorkoutListScreen`. Consumes `effectiveTuningConfig = tuningConfig ?: observedTuningConfig`.
2. **`WorkoutDetailTabbedContent.kt` (line 485)**: Details tab in `TrackOnMapScreen.kt`. Consumes `tuningConfig: TuningConfig`.
3. Previews and Unit Tests (`WorkoutDetailsLayoutTest.kt`, `PhysiologyModelsVisibilityContractTest.kt`).

Both production call sites already have direct access to `TuningConfig`, making it the architectural natural conduit for passing the summary visibility toggles into `WorkoutDetails`.

---

## 3. User Scope Grounding (ATT-1250)

To maintain focus and avoid scope creep during sprint execution, scope boundaries are defined as follows:

* **In-Scope Goals**:
  1. **New Preferences in `TuningPreferencesDataStore` & `TuningConfig`**:
     - `enableBasicPowerMetrics: Boolean` (Default: `true`)
     - `showBasicPowerInWorkoutSummary: Boolean` (Default: `true`)
     - `showCogganPowerInWorkoutSummary: Boolean` (Default: `true`)
     - `showAerobicEfficiencyInWorkoutSummary: Boolean` (Default: `true`)
     - `showCriticalPowerInWorkoutSummary: Boolean` (Default: `true`)
  2. **Accordion UI in `PhysiologyModelsSection.kt`**:
     - Expose Basic Power Metrics as a model option alongside Coggan, PMC, Aerobic Efficiency, and Critical Power.
     - For each model contributing a summary row (Basic Power, Coggan, Efficiency, Critical Power), render a subordinate "Show in Workout Summary" toggle indented under the primary switch when the primary switch is checked.
  3. **Wiring in Settings Screens**:
     - Wire new state in `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt` to load, display, and persist the new preferences.
     - Maintain strict `< 400` lines modularity invariant for both files (`REQ-UI-262`, `AdvancedTuningModularityTest`).
  4. **Wiring in `WorkoutDetails.kt` & `PowerMetricSlot.kt`**:
     - Update `resolvePowerMetricSlots` to accept `showCogganPower`, `showBasicPower`, and `enableBasicPower`.
     - Update `buildVisibleWorkoutDetailSections` in `WorkoutDetails.kt` to check `show*InWorkoutSummary` before adding rows.
     - When all power metrics are toggled off, omit `PowerTrainingMetricsSection` completely; ensure dividers adapt seamlessly.
     - Maintain strict `< 500` lines modularity invariant for `WorkoutDetails.kt` (`REQ-UI-415.3`).
  5. **Wiring in Consumers**:
     - Pass the new summary flags from `effectiveTuningConfig` / `tuningConfig` in `WorkoutSummary.kt` and `WorkoutDetailTabbedContent.kt`.
  6. **Localization Parity**:
     - Provide localized strings for all new keys across all 9 application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  7. **Automated Verification**:
     - Update unit and contract tests in `WorkoutDetailsLayoutTest.kt`, `PhysiologyModelsVisibilityContractTest.kt`, and `AdvancedTuningModularityTest.kt`.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. **Zero Changes to Sensor / Recording Pipelines**: No modification to `TrackerService`, `CyclingPowerMetricsDevice`, `PowerPhysiologyCalculator`, or SQLite tables (`extrema`, `workout_summaries`). Derived metrics continue to be computed and persisted unconditionally in the background (`REQ-UI-403.4`).
  2. **No Changes to Cockpit / Telemetry Graphs**: Live cockpit tile gating (`SensorGridScreen.kt`, `EditSensorFieldViewModel.kt`) and map telemetry graphs (`TelemetryMetricType.W_PRIME_BAL`, `TelemetryMetricType.EFFICIENCY_FACTOR`) remain governed strictly by the primary model toggles (`enableCogganPowerMetrics`, `enableCriticalPowerModel`, `enableAerobicEfficiencyModel`).
  3. **No Modification to PMC**: Performance Management (`enablePmcModel`) does not contribute a summary row to `WorkoutDetails` (it only surfaces the PMC chart in `AthleteProfileScreen.kt`), so it receives no subordinate summary toggle.
  4. **No Modification to `WorkoutMasksAndCardsSection.kt`**: Whole-card section reordering (Description, Extrema, Laps, Strava, Charts, Zones) remains separate from intra-card telemetry details.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:
  Refines and extends **REQ-UI-403** (*Progressive Disclosure of Physiology & Training Models in Advanced Settings, Profile & Zones, Live Cockpit Sensor Selector, and Aftermath Views* - `ATT-3929`) and **REQ-UI-415** (*Single-Line Power Row and Visual Domain Row Separation with Subtle Horizontal Dividers in WorkoutDetails* - `ATT-4042`).
  Targets:
  - `PhysiologyModelsSection.kt`
  - `TuningPreferencesDataStore.kt`
  - `AdvancedTuningScreen.kt` & `AdvancedTuningDialog.kt`
  - `WorkoutDetails.kt` & `PowerMetricSlot.kt`
  - `WorkoutSummary.kt` & `WorkoutDetailTabbedContent.kt`
  - `strings.xml` (all 9 canonical locales)

* **Historical Origin & Commit Trace**:
  - `ATT-3929` (Sprint 2026-42.7, commit `a888c381`): Created `PhysiologyModelsSection.kt` with initial toggles for Coggan, PMC, Aerobic Efficiency, and Critical Power.
  - `ATT-4042` (Sprint 2026-42.8, commit `5f178dc9`): Established `PowerMetricSlot.kt`, single-line power row priority fallback, and subtle horizontal dividers in `WorkoutDetails.kt`.
  - `ATT-4292` (Sprint 2026-43.1 Joint Review): Surfaced the usability friction that full model enablement leads to excessively tall `WorkoutDetails` cards on real devices.

* **Root Reason for Existing Formulation**:
  When physiology models were first introduced in `ATT-3929`, progressive disclosure was designed around a single master toggle per training science school. This minimized settings complexity during initial rollout. However, real-world athlete feedback showed that athletes frequently desire advanced telemetry during active tracking (cockpit) or in deep analytics (charts), while preferring a clean, compact overview on their summary cards.

* **Preservation of Core Invariants**:
  1. **100% Backward Compatibility**: All 4 "Show in Workout Summary" flags and the Basic Power Metrics toggle default to `true`. An athlete upgrading the app experiences identical layout and behavior until they explicitly customize summary row visibility.
  2. **Background Persistence**: SQLite extrema and stream recording remain 100% intact regardless of toggle positions (`REQ-UI-403.4`).
  3. **Section 5.10 / REQ-UI-415 Alignment**: Single-line power row priority rules and conditional `HorizontalDivider` logic remain fully preserved. When all power metrics or all physiology metrics are hidden, zero orphan dividers or empty gaps are produced.
  4. **Modularity Constraints**: Strict `< 400` lines for all files in `ui/settings/tuning/` (`REQ-UI-262`, `AdvancedTuningModularityTest`) and strict `< 500` lines for `WorkoutDetails.kt` (`REQ-UI-415.3`).
  5. **Localization Parity**: 100% translation parity across all 9 supported application locales.

---

## 5. Architectural Strategy & High-Level Solution

```
+-----------------------------------------------------------------------------------------+
|                                    Advanced Settings                                    |
|                      Physiology & Training Models Accordion Category                    |
+-----------------------------------------------------------------------------------------+
|                                                                                         |
|  [X] Basic Power Metrics (Work kJ, W/kg) -----------------------------> enableBasicPower |
|      |-- [X] Show in Workout Summary ------------------------> showBasicPowerInSummary   |
|                                                                                         |
|  [X] Coggan Power Metrics (NP, IF, TSS, VI) --------------------------> enableCoggan     |
|      |-- [X] Show in Workout Summary -----------------------> showCogganPowerInSummary   |
|                                                                                         |
|  [ ] Performance Management (PMC: CTL, ATL, TSB) ---------------------> enablePmcModel   |
|      (No summary row in WorkoutDetails)                                                 |
|                                                                                         |
|  [X] Aerobic Efficiency & Decoupling (EF, Pw:Hr / Pa:Hr) -------------> enableEfModel     |
|      |-- [X] Show in Workout Summary ----------------------------> showEfInSummary       |
|                                                                                         |
|  [X] Critical Power & Anaerobic Battery (CP, W', W'bal) --------------> enableCpModel     |
|      |-- [X] Show in Workout Summary ----------------------------> showCpInSummary       |
|                                                                                         |
+--------------------------------------------+--------------------------------------------+
                                             |
                         Persisted in TuningPreferencesDataStore
                                             |
                                    TuningConfig Flow
                                             |
                     +-----------------------+-----------------------+
                     |                                               |
                     v                                               v
            [Live Cockpit Tiles]                        [WorkoutDetails / Summaries]
          Governed strictly by:                         Governed by:
          - enableCogganPowerMetrics                    - enableCoggan && showCogganInSummary
          - enableCriticalPowerModel                    - enableBasic && showBasicInSummary
          - enableAerobicEfficiencyModel                - enableEf && showEfInSummary
                                                        - enableCp && showCpInSummary
```

### 5.1 DataStore & Preferences Model (`TuningPreferencesDataStore.kt`)
Add the following preference keys and default values:

```kotlin
// In TuningPreferencesDefaults:
const val DEFAULT_ENABLE_BASIC_POWER_METRICS = true
const val DEFAULT_SHOW_BASIC_POWER_IN_WORKOUT_SUMMARY = true
const val DEFAULT_SHOW_COGGAN_POWER_IN_WORKOUT_SUMMARY = true
const val DEFAULT_SHOW_AEROBIC_EFFICIENCY_IN_WORKOUT_SUMMARY = true
const val DEFAULT_SHOW_CRITICAL_POWER_IN_WORKOUT_SUMMARY = true

// In TuningPreferencesDataStore:
val KEY_ENABLE_BASIC_POWER_METRICS = booleanPreferencesKey("tuning_enable_basic_power_metrics")
val KEY_SHOW_BASIC_POWER_IN_WORKOUT_SUMMARY = booleanPreferencesKey("tuning_show_basic_power_in_workout_summary")
val KEY_SHOW_COGGAN_POWER_IN_WORKOUT_SUMMARY = booleanPreferencesKey("tuning_show_coggan_power_in_workout_summary")
val KEY_SHOW_AEROBIC_EFFICIENCY_IN_WORKOUT_SUMMARY = booleanPreferencesKey("tuning_show_aerobic_efficiency_in_workout_summary")
val KEY_SHOW_CRITICAL_POWER_IN_WORKOUT_SUMMARY = booleanPreferencesKey("tuning_show_critical_power_in_workout_summary")
```

Include all 5 keys in `ALL_KEYS`, `tuningConfigFlow`, `saveTuningConfig(config)`, and `resetToDefaults()`.

### 5.2 Settings UI (`PhysiologyModelsSection.kt`)
Update `PhysiologyModelsSection` composable:
- Add parameters for `enableBasicPowerMetrics`, `onEnableBasicPowerMetricsChange`, and the 4 `show*InWorkoutSummary` flags with callbacks.
- When a primary model switch is enabled (`enable... == true`), render a subordinate toggle indented slightly (`Modifier.padding(start = 16.dp)`):
  - Title: `stringResource(R.string.tuning_physiology_show_in_workout_summary)`
  - Summary: `stringResource(R.string.tuning_physiology_show_in_workout_summary_desc)`
  - Switch checked state bound to the respective `show*InWorkoutSummary` state.
- Keep `PhysiologyModelsSection.kt` clean, concise, and well under the 400-line modularity threshold (~220 lines).

### 5.3 Modularity Budget for `AdvancedTuningScreen.kt` & `AdvancedTuningDialog.kt`
- `AdvancedTuningScreen.kt` currently has **383 lines** (threshold is 400 lines).
- To prevent breaching the 400-line limit:
  - Declare related state variables using compact semicolon notation (matching existing file pattern).
  - Pass the new flags into `PhysiologyModelsSection` compactly.
  - In `applyConfig`, assign the new flags compactly.
  - Line count increase will be strictly limited to ~5 lines, finishing at ~388 lines.
- `AdvancedTuningDialog.kt` currently has **361 lines**, easily accommodating the new state at ~367 lines.

### 5.4 Priority Resolution in `PowerMetricSlot.kt` & `WorkoutDetails.kt`
Update `resolvePowerMetricSlots`:
```kotlin
fun resolvePowerMetricSlots(
    data: WorkoutDetailsData,
    enableCogganPowerMetrics: Boolean,
    showCogganPower: Boolean = true,
    showBasicPower: Boolean = true,
    enableBasicPower: Boolean = true
): List<PowerMetricSlot> {
    val showCoggan = enableCogganPowerMetrics && showCogganPower && data.normalizedPowerWatts != null
    val showBasic = enableBasicPower && showBasicPower
    return when {
        showCoggan && data.intensityFactor != null && data.tss != null ->
            listOf(PowerMetricSlot.NORMALIZED_POWER, PowerMetricSlot.INTENSITY_FACTOR, PowerMetricSlot.TSS)
        showCoggan -> buildList {
            add(PowerMetricSlot.NORMALIZED_POWER)
            data.intensityFactor?.let { add(PowerMetricSlot.INTENSITY_FACTOR) }
            data.tss?.let { add(PowerMetricSlot.TSS) }
            if (showBasic && size < 3 && data.workKj != null) add(PowerMetricSlot.WORK_KJ)
            if (showBasic && size < 3 && data.avgPowerToWeight != null) add(PowerMetricSlot.POWER_TO_WEIGHT)
        }
        showBasic && (data.workKj != null || data.avgPowerToWeight != null) -> buildList {
            data.workKj?.let { add(PowerMetricSlot.WORK_KJ) }
            data.avgPowerToWeight?.let { add(PowerMetricSlot.POWER_TO_WEIGHT) }
        }
        else -> emptyList()
    }
}
```

In `WorkoutDetails.kt`:
- Add default-valued parameters:
  ```kotlin
  enableBasicPowerMetrics: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_BASIC_POWER_METRICS,
  showBasicPowerInWorkoutSummary: Boolean = TuningPreferencesDefaults.DEFAULT_SHOW_BASIC_POWER_IN_WORKOUT_SUMMARY,
  showCogganPowerInWorkoutSummary: Boolean = TuningPreferencesDefaults.DEFAULT_SHOW_COGGAN_POWER_IN_WORKOUT_SUMMARY,
  showAerobicEfficiencyInWorkoutSummary: Boolean = TuningPreferencesDefaults.DEFAULT_SHOW_AEROBIC_EFFICIENCY_IN_WORKOUT_SUMMARY,
  showCriticalPowerInWorkoutSummary: Boolean = TuningPreferencesDefaults.DEFAULT_SHOW_CRITICAL_POWER_IN_WORKOUT_SUMMARY
  ```
- In `buildVisibleWorkoutDetailSections`:
  - Pass the power summary flags to `resolvePowerMetricSlots`.
  - Gate `AerobicEfficiencyRow` on: `enableAerobicEfficiencyModel && showAerobicEfficiencyInWorkoutSummary && data.efficiencyFactor != null`.
  - Gate `AnaerobicBatteryRow` on: `enableCriticalPowerModel && showCriticalPowerInWorkoutSummary && data.minWPrimeBalKj != null && data.minWPrimeBalPct != null`.
- File length remains strictly under 500 lines (~490 lines).

---

## 6. System Invariants & Risk Assessment

### 6.1 System Invariants
1. **Zero Regression in Background Computation & Storage**:
   - `TrackerService`, `CyclingPowerMetricsDevice`, `LegacyImportEngine`, and SQLite tables remain completely untouched. All metrics are recorded and persisted in full fidelity.
2. **Zero Breaking Changes to Live Cockpit**:
   - Cockpit tile visibility (`SensorGridScreen.kt`, `isSensorTypeVisibleForTuning`, `filterVisibleFieldsForTuning`) continues to depend strictly on primary model toggles (`enableCogganPowerMetrics`, `enableCriticalPowerModel`).
3. **100% Backward Compatibility**:
   - Default values for all new flags are `true`. Any existing installation or unconfigured preference state behaves identically to the current production build.
4. **Modularity Limits**:
   - All files under `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/` must strictly remain `< 400` lines (`REQ-UI-262`, `AdvancedTuningModularityTest`).
   - `WorkoutDetails.kt` must strictly remain `< 500` lines (`REQ-UI-415.3`).
5. **Human Gate Sovereignty**:
   - Ticket `ATT-4369` transitions through the agile subtask lifecycle; parent ticket completion remains strictly with the human user at Final Review (`Core Rule 3`).
6. **9-Language Localization Parity**:
   - Every new string key must be translated and present in all 9 application locales.

### 6.2 Risk Rating & Technical Justification
* **Risk Rating**: **LOW**
* **Justification**:
  - The changes are strictly additive and scoped to UI presentation gating and user preference storage via Jetpack DataStore.
  - No database migrations, schema alterations, or background threading changes are involved.
  - Default values are `true`, ensuring zero breaking behavioral changes.
  - Pure mapping functions (`resolvePowerMetricSlots`) are isolated, deterministic, and easily verified via unit tests.
