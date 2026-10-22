# Stage 2: Requirement & Test Specification - ATT-4432: [Verbesserung] Remove Basic Power Metrics (Work, W/kg) toggle from Physiology & Training Models settings

**Ticket**: [ATT-4432](https://rainerblind.atlassian.net/browse/ATT-4432)  
**Sub-task**: `[Req & Test Spec]` (To be created via `submit-stage`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: Next Milestone / Unassigned  
**Active Sprint**: `2026-43.3`  
**Requirement Mapping**: `REQ-UI-424`, `REQ-UI-403`, `REQ-UI-415`  
**Test Spec ID**: `TST-UI-388`, `TST-UI-378`  
**Branch**: `feature/ATT-4432`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification (REQ-UI-424, REQ-UI-403, REQ-UI-415)

### 1.1 Problem Statement & Rationale
During Sprint 2026-43.2 (ticket `ATT-4369`), subordinate "Show in Workout Summary" toggles were introduced for training models. However, two critical discrepancies were identified during Joint Review:
1. An unrequested top-level model toggle `"Basic Power Metrics (Work, W/kg)"` with preference keys `enableBasicPowerMetrics` and `showBasicPowerInWorkoutSummary` was added to `PhysiologyModelsSection.kt`, and `PowerMetricSlot.kt` / `WorkoutDetails.kt` were configured to fall back to `WORK_KJ` and `POWER_TO_WEIGHT` when Coggan metrics were disabled or incomplete. Basic mechanical work ($kJ$) and power-to-weight ratio ($W/kg$) are fundamental physics and body-ratio metrics, not an independent physiology or sports-science training model. They must not appear as a settings model and must NOT be shown in the Workout Summary (`WorkoutDetails`) at all. The Power row in `WorkoutDetails` must exclusively display Coggan Power Metrics (`NP` with `VI`, `IF`, `TSS`) when enabled, and disappear completely when disabled or absent.
2. The subordinate `"Show in Workout Summary"` (`"In Workout-Übersicht anzeigen"`) rows reused full-sized `TuningToggleItem`s with only `16.dp` start padding, visually competing with parent model headers. Under `docs/design_guidelines.md` Section 3.3 / Rule 59, child toggles must exhibit a lighter visual hierarchy: smaller/lighter typography (`MaterialTheme.typography.bodySmall` / `labelMedium` with `onSurfaceVariant`), single concise line without a heavy subtitle, compact vertical padding, and a scaled-down `Switch` (`Modifier.scale(0.8f)`).

### 1.2 Functional & Architectural Requirements

#### 1.2.1 Settings UI & Accordion (`PhysiologyModelsSection.kt`, `TuningFormControls.kt`, `REQ-UI-424.1`)
1. `PhysiologyModelsSection.kt` SHALL present exactly four sports-science models:
   - **Model 1: Coggan Power Metrics** (`enableCogganPowerMetrics`, default `true`), with child toggle "Show in Workout Summary" (`showCogganPowerInWorkoutSummary`, default `true`).
   - **Model 2: Performance Management (PMC)** (`enablePmcModel`, default `false`), with no summary toggle (contributes no summary rows).
   - **Model 3: Aerobic Efficiency & Decoupling** (`enableAerobicEfficiencyModel`, default `false`), with child toggle "Show in Workout Summary" (`showAerobicEfficiencyInWorkoutSummary`, default `true`).
   - **Model 4: Critical Power & Anaerobic Battery** (`enableCriticalPowerModel`, default `false`), with child toggle "Show in Workout Summary" (`showCriticalPowerInWorkoutSummary`, default `true`).
2. The top-level `"Basic Power Metrics (Work, W/kg)"` toggle and its subordinate switch SHALL be completely eliminated from `PhysiologyModelsSection.kt`.
3. In `TuningFormControls.kt`, the system SHALL provide `SubordinateTuningToggleItem` conforming to Rule 59:
   - Smaller typography (`MaterialTheme.typography.bodySmall` or `labelMedium`) tinted with `MaterialTheme.colorScheme.onSurfaceVariant`.
   - Compact vertical padding (`padding(start = 16.dp, top = 2.dp, bottom = 2.dp)`).
   - Single concise title line without a subtitle.
   - Scaled-down `Switch` (`Modifier.scale(0.8f)`).
4. `PhysiologyModelsSection.kt` SHALL use `SubordinateTuningToggleItem` for all three subordinate summary toggles (Coggan, Aerobic Efficiency, Critical Power), displayed conditionally when their respective master model switch is `true`.

#### 1.2.2 Persistence & Configuration DataStore (`TuningPreferencesDataStore.kt`, `REQ-UI-424.2`)
1. `TuningPreferencesDataStore` and `TuningConfig` SHALL persist and emit the 3 legitimate summary flags:
   - `KEY_SHOW_COGGAN_POWER_IN_WORKOUT_SUMMARY` / `showCogganPowerInWorkoutSummary` (default `true`)
   - `KEY_SHOW_AEROBIC_EFFICIENCY_IN_WORKOUT_SUMMARY` / `showAerobicEfficiencyInWorkoutSummary` (default `true`)
   - `KEY_SHOW_CRITICAL_POWER_IN_WORKOUT_SUMMARY` / `showCriticalPowerInWorkoutSummary` (default `true`)
2. `KEY_ENABLE_BASIC_POWER_METRICS`, `KEY_SHOW_BASIC_POWER_IN_WORKOUT_SUMMARY`, `enableBasicPowerMetrics`, `showBasicPowerInWorkoutSummary`, `enableBasicPowerMetricsFlow`, and default constants in `TuningPreferencesDefaults` SHALL be completely removed from `TuningPreferencesDataStore`, `TuningConfig`, `AdvancedTuningScreen.kt`, and `AdvancedTuningDialog.kt`.

#### 1.2.3 PowerMetricSlot Resolution & Coggan-Only Presentation (`PowerMetricSlot.kt`, `REQ-UI-424.3`)
1. Enum `PowerMetricSlot` SHALL declare exclusively Coggan metrics:
   ```kotlin
   enum class PowerMetricSlot {
       NORMALIZED_POWER,
       INTENSITY_FACTOR,
       TSS
   }
   ```
   Enum entries `WORK_KJ` and `POWER_TO_WEIGHT` SHALL be permanently removed.
2. `resolvePowerMetricSlots` SHALL evaluate:
   `showCoggan = enableCogganPowerMetrics && showCogganPowerInWorkoutSummary && data.normalizedPowerWatts != null`.
   - When `showCoggan == true`: returns `NORMALIZED_POWER`, plus `INTENSITY_FACTOR` (if non-null), plus `TSS` (if non-null).
   - When `showCoggan == false`: returns `emptyList()`.
3. Basic mechanical work ($kJ$) and $W/kg$ SHALL NOT be returned under any condition in `resolvePowerMetricSlots` and SHALL NOT be displayed in `WorkoutDetails`.

#### 1.2.4 Granular Summary Presentation & Divider Integrity (`WorkoutDetails.kt`, `REQ-UI-424.4`, `REQ-UI-415.1`, `REQ-UI-415.2`)
1. `WorkoutDetails.kt` SHALL strip `enableBasicPowerMetrics` and `showBasicPowerInWorkoutSummary` from its parameters.
2. `PowerTrainingMetricsSection` SHALL render exclusively Coggan Power Metrics (`NP` with `VI` secondary subtitle, `IF`, `TSS`) when `powerSlots.isNotEmpty()`.
3. When `powerSlots.isEmpty()`, `PowerTrainingMetricsSection` and its preceding `HorizontalDivider` SHALL be omitted completely.
4. Divider cadence invariant: `HorizontalDivider` is rendered before a domain section if and only if that section is visible, preserving balanced 12dp spacing and zero leading, trailing, or duplicate dividers.
5. Consumers `WorkoutSummary.kt` and `WorkoutDetailTabbedContent.kt` SHALL remove basic power arguments when invoking `WorkoutDetails`.

#### 1.2.5 Modularity Budgets & System Invariants (`REQ-UI-424.5`)
1. Every `.kt` file in `ui/settings/tuning/` (`PhysiologyModelsSection.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `TuningFormControls.kt`) SHALL strictly remain `< 400` lines.
2. `WorkoutDetails.kt` SHALL strictly remain `< 500` lines.
3. Baseline physics metrics (`SensorType.WORK_KJ`, `SensorType.POWER_TO_WEIGHT`) in live cockpit sensor grids (`SensorGridScreen`, `EditSensorFieldViewModel`), `WorkoutExtrema`, `WorkoutDescription`, and `AthleteProfileScreen` remain fully functional and uninhibited.
4. Background sensor recording in `TrackerService` and `CyclingPowerMetricsDevice` and SQLite database persistence remain 100% active.

#### 1.2.6 9-Language Localization Clean-Up (`REQ-UI-424.6`)
1. Dead string resources `tuning_physiology_basic_power_title` and `tuning_physiology_basic_power_desc` SHALL be cleanly removed across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with zero leftover or orphaned references.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-UI-424` (*Show in Workout Summary Toggles for Physiology Models*), `REQ-UI-403` (*Progressive Disclosure of Physiology & Training Models in Advanced Settings*), and `REQ-UI-415` (*Single-Line Power Row in WorkoutDetails*). Targets `PhysiologyModelsSection.kt`, `TuningPreferencesDataStore.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `WorkoutDetails.kt`, `PowerMetricSlot.kt`, `WorkoutSummary.kt`, `WorkoutDetailTabbedContent.kt`, `TuningFormControls.kt`, and `res/values*/strings.xml`.
* **Historical Origin & Commit Trace**: Sprint 2026-42.7 (`ATT-3929`, commit `a888c381`) introduced `PhysiologyModelsSection.kt` with 4 master toggles; Sprint 2026-42.8 (`ATT-4042`, commit `5f178dc9`) established single-line power row priority with basic physics fallback in `WorkoutDetails.kt`; Sprint 2026-43.2 (`ATT-4369`, commit `157684c0`) added summary toggles but erroneously introduced "Basic Power Metrics (Work, W/kg)" as a 5th model and reused heavy toggle items; Sprint 2026-43.2 Joint Review (`ATT-4432`) mandated removing the basic power model/fallback and applying Rule 59 visual hierarchy.
* **Root Reason for Existing Formulation**: In `ATT-4369`, basic power was added under the mistaken assumption that every row in `WorkoutDetails` needed an explicit settings toggle in `PhysiologyModelsSection`, and full `TuningToggleItem` rows were reused with only 16dp indentation for convenience. In `REQ-UI-415`, fallback to Work kJ and W/kg was included so the power row wouldn't be blank when FTP was missing. Feedback in Joint Review confirmed mechanical work and W/kg are baseline physics, not a physiology model, and should not be shown in summary cards at all.
* **Preservation of Core Invariants**: Unconditional background computation, sensor recording, and SQLite storage remain 100% active; live cockpit sensor fields (`WORK_KJ`, `POWER_TO_WEIGHT`) and athlete profile work totals remain fully supported; Section 5.10 single-line grouped layout and 12dp divider invariants are preserved; modularity budgets (< 400 lines in tuning, < 500 lines in WorkoutDetails) are strengthened; 100% 9-language localization parity without dead strings.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (4 Models & Rule 59 Subordinate Visual Hierarchy)**:
  * *Given* `Settings -> Advanced Settings -> Physiology & Training Models` is opened,
  * *When* inspecting the list of models,
  * *Then* exactly 4 models are displayed (Coggan Power Metrics, Performance Management, Aerobic Efficiency & Decoupling, Critical Power & Anaerobic Battery), `Basic Power Metrics` is completely absent, and each model with a summary toggle renders a compact `SubordinateTuningToggleItem` with smaller text and a scaled-down switch (`Modifier.scale(0.8f)`) when its parent master toggle is ON.
* **Criterion 2 (Full Coggan Trio in Workout Summary)**:
  * *Given* a workout with `normalizedPowerWatts = 250.0`, `intensityFactor = 0.85`, and `tss = 95.0`,
  * *When* `enableCogganPowerMetrics = true` and `showCogganPowerInWorkoutSummary = true`,
  * *Then* `resolvePowerMetricSlots` returns `[NORMALIZED_POWER, INTENSITY_FACTOR, TSS]`, and `WorkoutDetails` renders the Power row with NP (and VI), IF, and TSS.
* **Criterion 3 (Missing FTP Coggan Without Basic Power Fallback)**:
  * *Given* a workout with `normalizedPowerWatts = 250.0`, `workKj = 1200.0`, `avgPowerToWeight = 3.1`, but unconfigured FTP (`intensityFactor = null`, `tss = null`),
  * *When* `enableCogganPowerMetrics = true` and `showCogganPowerInWorkoutSummary = true`,
  * *Then* `resolvePowerMetricSlots` returns strictly `[NORMALIZED_POWER]`, and `WorkoutDetails` renders only NP (with VI); `WORK_KJ` and `POWER_TO_WEIGHT` are NOT rendered.
* **Criterion 4 (Coggan Summary Disabled or Absent)**:
  * *Given* a workout with valid power data (`normalizedPowerWatts = 250.0`, `workKj = 1200.0`, `avgPowerToWeight = 3.1`),
  * *When* `showCogganPowerInWorkoutSummary = false` or `enableCogganPowerMetrics = false` or `normalizedPowerWatts = null`,
  * *Then* `resolvePowerMetricSlots` returns `emptyList()`, and `PowerTrainingMetricsSection` and its preceding divider are completely omitted from `WorkoutDetails`.
* **Criterion 5 (Cockpit Sensor Selection Invariance)**:
  * *Given* the athlete configuring cockpit tiles in `EditSensorFieldViewModel` or viewing `SensorGridScreen`,
  * *When* browsing available sensor fields,
  * *Then* `SensorType.WORK_KJ` and `SensorType.POWER_TO_WEIGHT` remain fully selectable and functional regardless of summary settings.

### 1.5 System Invariants
1. **Background Telemetry & Extrema Recording**: `TrackerService`, `CyclingPowerMetricsDevice`, and SQLite database storage compute and record power metrics unconditionally.
2. **Deterministic Divider Cadence**: `HorizontalDivider` in `WorkoutDetails` is rendered only when preceded and followed by visible sections, ensuring zero leading, zero trailing, and zero duplicate dividers.
3. **Strict Modularity Limits**: `< 400` lines for all files in `ui/settings/tuning/`; `< 500` lines for `WorkoutDetails.kt`.
4. **9-Language Localization Parity**: Zero untranslated keys, zero orphaned keys, 100% translation parity across all 9 canonical application locales.
5. **Parent Ticket Governance**: Human decision gate on parent ticket `ATT-4432` remains strictly intact.

---

## 2. Test Specification (TST-UI-388, TST-UI-378)

### Test Case 1: `PowerMetricSlotResolutionTest` (`TST-UI-388.1`, `TST-UI-378.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/PowerMetricSlotResolutionTest.kt`
* **Preconditions**: `WorkoutDetailsData` fixture with power telemetry (`normalizedPowerWatts = 250.0`, `intensityFactor = 0.85`, `tss = 95.0`, `workKj = 1200.0`, `avgPowerToWeight = 3.1`).
* **Actions & Expected Results**:
  1. Full Coggan Trio (`enableCoggan = true, showCoggan = true`): assert returns `[NORMALIZED_POWER, INTENSITY_FACTOR, TSS]`.
  2. Missing FTP (`intensityFactor = null, tss = null`): assert returns strictly `[NORMALIZED_POWER]` (no basic power fallback).
  3. Coggan summary disabled (`showCoggan = false`): assert returns `emptyList()`.
  4. Coggan master disabled (`enableCoggan = false`): assert returns `emptyList()`.
  5. Missing NP (`normalizedPowerWatts = null`): assert returns `emptyList()`.
  6. Verify function signature does not take basic power arguments.

### Test Case 2: `WorkoutDetailsLayoutTest` (`TST-UI-388.2`, `TST-UI-378.2`)
* **Scope**: Unit & Source Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetailsLayoutTest.kt`
* **Preconditions**: Source file `WorkoutDetails.kt`.
* **Actions & Expected Results**:
  1. Verify `PowerTrainingMetricsSection` contains exactly one `Row(modifier = Modifier.fillMaxWidth())` inside `WorkoutDomainRow`.
  2. Verify `PowerTrainingMetricsSection` handles only `PowerMetricSlot.NORMALIZED_POWER`, `INTENSITY_FACTOR`, and `TSS` (zero branches for `WORK_KJ` or `POWER_TO_WEIGHT`).
  3. Verify `WorkoutDetails.kt` line count is strictly `< 500` lines.
  4. Verify divider count invariant: zero dividers for single section, $N-1$ dividers for $N$ sections.

### Test Case 3: `PhysiologyModelsVisibilityContractTest` (`TST-UI-388.3`)
* **Scope**: Unit & Source Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/PhysiologyModelsVisibilityContractTest.kt`
* **Preconditions**: `TuningConfig`, `TuningPreferencesDefaults`, `TuningPreferencesDataStore`.
* **Actions & Expected Results**:
  1. `TuningConfig()` defaults: verify 4 master models and 3 summary flags (`showCogganPowerInWorkoutSummary = true`, `showAerobicEfficiencyInWorkoutSummary = true`, `showCriticalPowerInWorkoutSummary = true`); assert absence of `enableBasicPowerMetrics` and `showBasicPowerInWorkoutSummary`.
  2. Verify `TuningPreferencesDefaults` does not declare `DEFAULT_ENABLE_BASIC_POWER_METRICS` or `DEFAULT_SHOW_BASIC_POWER_IN_WORKOUT_SUMMARY`.
  3. Verify `PhysiologyModelsSection.kt` source contains zero occurrences of `enableBasicPowerMetrics`.
  4. Verify `PhysiologyModelsSection.kt` uses `SubordinateTuningToggleItem` for child switches.
  5. Verify line counts of `PhysiologyModelsSection.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, and `TuningFormControls.kt` are strictly `< 400` lines.

### Test Case 4: Cockpit & Background Persistence Invariant Test (`TST-UI-388.4`)
* **Scope**: Architecture & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/PhysiologyModelsVisibilityContractTest.kt`
* **Preconditions**: `EditSensorFieldViewModel`, `SensorGridScreen`.
* **Actions & Expected Results**:
  1. Verify `SensorType.WORK_KJ` and `SensorType.POWER_TO_WEIGHT` remain accessible in sensor picker catalogs and are unaffected by summary toggles.
  2. Verify live cockpit filtering depends strictly on master model switches (`enableCogganPowerMetrics`, `enableCriticalPowerModel`).

### Test Case 5: 9-Language Localization Clean-Up & Parity Audit (`TST-UI-388.5`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/PhysiologyModelsVisibilityContractTest.kt` & XML audits
* **Goal**:
  1. Verify `tuning_physiology_basic_power_title` and `tuning_physiology_basic_power_desc` are completely absent from `strings.xml` across all 9 canonical locales: `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`.
  2. Verify `tuning_physiology_show_in_workout_summary` exists with 100% translation parity across all 9 locales.

### Test Case 6: Clean-Room Regression Suite (`TST-UI-388.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-388.1` | Unit | `PowerMetricSlot.resolvePowerMetricSlots` | `REQ-UI-424.3`, `REQ-UI-415.1` | Specified |
| `TST-UI-388.2` | Contract / UI | `WorkoutDetails.PowerTrainingMetricsSection` | `REQ-UI-424.4`, `REQ-UI-415.2` | Specified |
| `TST-UI-388.3` | Contract | `PhysiologyModelsSection`, `TuningConfig` | `REQ-UI-424.1`, `REQ-UI-424.2`, `REQ-UI-403.1` | Specified |
| `TST-UI-388.4` | Invariant | `EditSensorFieldViewModel`, `SensorGridScreen` | `REQ-UI-424.5`, `REQ-UI-403.3` | Specified |
| `TST-UI-388.5` | Localization | 9-Locale Strings Audit | `REQ-UI-424.6` | Specified |
| `TST-UI-388.6` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
| `TST-UI-378.1` | Unit | Single-line Coggan resolution | `REQ-UI-415.1` | Specified |
| `TST-UI-378.2` | Contract | Single-line layout & divider cadence | `REQ-UI-415.2`, `REQ-UI-415.3` | Specified |
