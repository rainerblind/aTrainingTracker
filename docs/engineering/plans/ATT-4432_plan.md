# Stage 3: Implementation Plan - ATT-4432: [Verbesserung] Remove Basic Power Metrics (Work, W/kg) toggle from Physiology & Training Models settings

**Ticket**: [ATT-4432](https://rainerblind.atlassian.net/browse/ATT-4432)  
**Sub-task**: `[Impl-Plan]` (to be created via `submit-stage`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: Next Milestone / Unassigned  
**Active Sprint**: `2026-43.3`  
**Requirement Mapping**: `REQ-UI-424` (`REQ-UI-424.1` .. `REQ-UI-424.6`), `REQ-UI-403`, `REQ-UI-415`  
**Test Mapping**: `TST-UI-388` (`TST-UI-388.1` .. `TST-UI-388.6`), `TST-UI-378` (`TST-UI-378.1`, `TST-UI-378.2`)  
**Branch**: `feature/ATT-4432`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

During Sprint 2026-43.2 (ticket `ATT-4369`), granular `"Show in Workout Summary"` toggles were introduced for sports-science training models. However, during the Sprint 2026-43.2 Joint Review, two critical design deficiencies were identified by the human user:

1. **Unwanted "Basic Power Metrics (Work, W/kg)" Model Toggle & Summary Fallback**:
   - Mechanical work ($kJ$) and power-to-weight ratio ($W/kg$) are baseline physics and body-ratio metrics, not an independent physiological or training science school/model (unlike Coggan Power Metrics, Performance Management / PMC, Aerobic Efficiency / Decoupling, or Critical Power / W'bal).
   - In `ATT-4369`, an unnecessary top-level model toggle `"Basic Power Metrics (Work, W/kg)"` with preference keys `enableBasicPowerMetrics` and `showBasicPowerInWorkoutSummary` was erroneously introduced into `PhysiologyModelsSection.kt`.
   - Furthermore, `resolvePowerMetricSlots` in `PowerMetricSlot.kt` and `PowerTrainingMetricsSection` in `WorkoutDetails.kt` were configured to fall back to `WORK_KJ` and `POWER_TO_WEIGHT` when Coggan metrics were absent or disabled.
   - **User Requirement**: Basic mechanical work ($kJ$) and $W/kg$ must **NOT** be shown in the Workout Summary (`WorkoutDetails`) at all. The Power domain row in `WorkoutDetails` must exclusively display Coggan Power Metrics (`NP` with `VI` secondary subtitle, `IF`, `TSS`) when enabled and present, and disappear completely when disabled or when no Coggan metrics exist.

2. **Heavy Visual Weight of Subordinate 'Show in Workout Summary' Toggles**:
   - Currently, each subordinate `"Show in Workout Summary"` (`"In Workout-Übersicht anzeigen"`) row reuses the full-size `TuningToggleItem` composable with only a `16.dp` start indent.
   - Because `TuningToggleItem` enforces a `minHeight = 48.dp`, primary `bodyMedium` title font in `onSurface`, and a secondary `bodySmall` subtitle (`tuning_physiology_show_in_workout_summary_desc`), the subordinate row visually competes with its parent model header and looks like a peer top-level model toggle.
   - Under `docs/design_guidelines.md` Section 3.3 / Rule 59 (*Subordinate Settings Toggles: Lighter Visual Weight & Compact Switches*), child toggles nested under a primary model must exhibit a clear, subordinate visual hierarchy: lighter/smaller typography (`MaterialTheme.typography.bodySmall` or `labelMedium` with `onSurfaceVariant` color), single concise line without a heavy subtitle, compact vertical padding, and a scaled-down `Switch` (`Modifier.scale(0.8f)`).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-424` (*Granular 'Show in Workout Summary' Gating for Physiology Models & Coggan-Only Power Row*)
  * `REQ-UI-424.1`: Settings UI & Accordion (`PhysiologyModelsSection.kt`, `TuningFormControls.kt`) — exactly 4 models; remove Basic Power; introduce `SubordinateTuningToggleItem` conforming to Rule 59 for child toggles.
  * `REQ-UI-424.2`: Persistence & Configuration DataStore (`TuningPreferencesDataStore.kt`, `TuningConfig.kt`) — persist 3 legitimate summary flags (`Coggan`, `EF`, `CP`); remove `enableBasicPowerMetrics` and `showBasicPowerInWorkoutSummary`.
  * `REQ-UI-424.3`: Power Metric Slot Resolution (`PowerMetricSlot.kt`) — remove `WORK_KJ` and `POWER_TO_WEIGHT`; return exclusively Coggan slots (`NP`, `IF`, `TSS`) when enabled and present, otherwise empty list.
  * `REQ-UI-424.4`: Granular Summary Presentation & Divider Integrity (`WorkoutDetails.kt`) — strip basic power arguments; render Power row only for Coggan metrics; enforce deterministic 12dp divider cadence.
  * `REQ-UI-424.5`: Preservation of Invariants & Modularity Budgets — cockpit sensor picker invariance; background recording intact; `< 400` lines for all files in `ui/settings/tuning/`; `< 500` lines for `WorkoutDetails.kt`.
  * `REQ-UI-424.6`: 9-Language Localization Clean-Up — remove dead strings `tuning_physiology_basic_power_title` and `tuning_physiology_basic_power_desc` across all 9 canonical locales.
* **Requirement**: `REQ-UI-403` (*Progressive Disclosure of Physiology & Training Models in Advanced Settings*) — restores strictly 4 canonical sports science models in `PhysiologyModelsSection.kt`.
* **Requirement**: `REQ-UI-415` (*Single-Line Power Row in WorkoutDetails*) — single-line layout exclusively rendering Coggan Trio without basic physics fallback.
* **Test Mapping**: `TST-UI-388` & `TST-UI-378`
  * `[TST-UI-388.1]` / `[TST-UI-378.1]`: Pure `resolvePowerMetricSlots` unit tests (Coggan trio, missing FTP without basic fallback, disabled returns empty list).
  * `[TST-UI-388.2]` / `[TST-UI-378.2]`: `WorkoutDetailsLayoutTest` source contract and divider integrity tests.
  * `[TST-UI-388.3]`: `PhysiologyModelsVisibilityContractTest` verifying DataStore defaults, absence of basic power keys, and `< 400` line modularity budgets.
  * `[TST-UI-388.4]`: Cockpit tile & background persistence invariant test.
  * `[TST-UI-388.5]`: 9-language localization clean-up audit.
  * `[TST-UI-388.6]`: Clean-room full test suite regression (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Unconditional Background Processing**: Background computation, sensor recording (`TrackerService`, `CyclingPowerMetricsDevice`), and SQLite database storage compute and record power metrics unconditionally.
2. **Live Cockpit Sensor Invariance**: Live cockpit tile selection and sensor field filtering (`EditSensorFieldViewModel`, `SensorGridScreen`) depend exclusively on master model toggles (`enableCogganPowerMetrics`, `enableCriticalPowerModel`), completely independent of summary switches.
3. **Extrema & Profile Work Invariance**: Mechanical work ($kJ$) and $W/kg$ remain fully available and visible in `WorkoutExtrema`, `WorkoutDescription`, and `AthleteProfileScreen` where session-level physical work is legitimately displayed.
4. **Deterministic Divider Cadence**: `HorizontalDivider` in `WorkoutDetails` is rendered if and only if preceded and followed by visible domain sections, guaranteeing zero leading, zero trailing, and zero duplicate dividers with balanced 12dp spacing.
5. **Strict Modularity Budgets**: Every `.kt` file in `ui/settings/tuning/` strictly remains `< 400` lines; `WorkoutDetails.kt` strictly remains `< 500` lines.
6. **9-Language Localization Parity**: Clean removal of dead strings across all 9 locales without leaving any orphaned or untranslated keys.
7. **Parent Human Gate Invariance**: Moving parent ticket `ATT-4432` to `Erledigt` is reserved exclusively for the human user.

---

## 4. Proposed Architectural Changes (SWE.2)

```
+-----------------------------------------------------------------------------------------+
|                                    UI Layer (Compose)                                   |
|                                                                                         |
|  +--------------------------------+       +------------------------------------------+  |
|  |    PhysiologyModelsSection     |       |          TuningFormControls              |  |
|  | - 4 Models (Coggan, PMC, EF,   | ----> | - TuningToggleItem (Parent Models)       |  |
|  |   CP)                          |       | - SubordinateTuningToggleItem (Rule 59:  |  |
|  | - Removed Basic Power Model    |       |   bodySmall, onSurfaceVariant, scale 0.8)|  |
|  +--------------------------------+       +------------------------------------------+  |
|                 |                                                                       |
|                 v                                                                       |
|  +--------------------------------+       +------------------------------------------+  |
|  |     AdvancedTuningScreen /     |       |              WorkoutDetails              |  |
|  |      AdvancedTuningDialog      |       | - Removed enable/showBasicPower args     |  |
|  | - Cleaned up basic power state |       | - PowerTrainingMetricsSection:           |  |
|  | - Strict < 400 lines budget    |       |   Coggan-only (NP, IF, TSS)              |  |
|  +--------------------------------+       +------------------------------------------+  |
|                 |                                               ^                       |
+-----------------|-----------------------------------------------|-----------------------+
                  |                                               |
                  v                                               v
+-----------------------------------------------------------------------------------------+
|                               Domain & Settings Layer                                   |
|                                                                                         |
|  +--------------------------------+       +------------------------------------------+  |
|  |  TuningPreferencesDataStore    |       |             PowerMetricSlot              |  |
|  | - 3 Summary Keys (Coggan, EF,  |       | - Enum: NORMALIZED_POWER,                |  |
|  |   CP)                          |       |   INTENSITY_FACTOR, TSS                  |  |
|  | - TuningConfig: removed basic  |       | - Removed WORK_KJ, POWER_TO_WEIGHT       |  |
|  |   power properties             |       | - resolvePowerMetricSlots: Coggan only   |  |
|  +--------------------------------+       +------------------------------------------+  |
+-----------------------------------------------------------------------------------------+
```

### 4.1 UI Consistency (Rule 23 — mandatory for UI changes)
* **Reference screen / component**: `PhysiologyModelsSection.kt` and `TuningFormControls.kt` in `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/`.
* **Reused components**: Material 3 `Switch`, `Text`, `Row`, `HorizontalDivider`.
* **Theme tokens**:
  - Typography: `MaterialTheme.typography.bodySmall` for subordinate toggle label (contrasting with `bodyMedium` on parent `TuningToggleItem`).
  - Colors: `MaterialTheme.colorScheme.onSurfaceVariant` for subordinate toggle label (contrasting with `onSurface` on parent `TuningToggleItem`).
  - Spacing: `Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp)` for compact vertical cadence without 48dp min-height expansion.
  - Scale: `Modifier.scale(0.8f)` for compact switch footprint.
* **New one-off styles & justification**: None. Follows canonical Rule 59 specification in `docs/design_guidelines.md` Section 3.3.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Subordinate Toggle Composable (`TuningFormControls.kt`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningFormControls.kt`
* **Changes**:
  - Implement `@Composable fun SubordinateTuningToggleItem` conforming to Rule 59:
    ```kotlin
    @Composable
    fun SubordinateTuningToggleItem(
        title: String,
        isChecked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true
    ) {
        val contentAlpha = if (enabled) 1f else TTAlpha.Disabled
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            )
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.scale(0.8f)
            )
        }
    }
    ```
* **Line Budget Check**: Currently 210 lines $\rightarrow$ will increase to ~235 lines (well below 400).

### Step 2: Remove Basic Power from PhysiologyModelsSection (`PhysiologyModelsSection.kt`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/PhysiologyModelsSection.kt`
* **Changes**:
  - Remove parameters `enableBasicPowerMetrics`, `onEnableBasicPowerMetricsChange`, `showBasicPowerInWorkoutSummary`, `onShowBasicPowerInWorkoutSummaryChange`.
  - Remove Model 1 block ("Basic Power Metrics (Work, W/kg)").
  - For the remaining 3 models with summary rows (Coggan, Aerobic Efficiency, Critical Power):
    Replace the child `TuningToggleItem(modifier = Modifier.padding(start = 16.dp), ...)` with `SubordinateTuningToggleItem(title = stringResource(R.string.tuning_physiology_show_in_workout_summary), isChecked = ..., onCheckedChange = ...)`.
  - Update `@Preview` composables.
* **Line Budget Check**: Currently 234 lines $\rightarrow$ will decrease to ~180 lines (well below 400).

### Step 3: Clean Up Persistence & Configuration DataStore (`TuningPreferencesDataStore.kt`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`
* **Changes**:
  - Remove `DEFAULT_ENABLE_BASIC_POWER_METRICS` and `DEFAULT_SHOW_BASIC_POWER_IN_WORKOUT_SUMMARY` constants from `TuningPreferencesDefaults`.
  - Remove `enableBasicPowerMetrics` and `showBasicPowerInWorkoutSummary` from data class `TuningConfig`.
  - Remove `KEY_ENABLE_BASIC_POWER_METRICS` and `KEY_SHOW_BASIC_POWER_IN_WORKOUT_SUMMARY` preference keys.
  - Remove keys from `ALL_KEYS` set.
  - Remove basic power parsing from `tuningConfigFlow`.
  - Remove `enableBasicPowerMetricsFlow`.
  - Remove basic power key writes in `saveTuningConfig(...)`.

### Step 4: Clean Up Settings Screens Wiring (`AdvancedTuningScreen.kt` & `AdvancedTuningDialog.kt`)
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`
* **Changes**:
  - Remove mutable states `var enableBasicPowerMetrics` and `var showBasicPowerInWorkoutSummary`.
  - Remove basic power assignments in `applyConfig` and config flows.
  - Remove basic power parameters from `TuningConfig(...)` construction when saving.
  - Remove basic power arguments passed into `PhysiologyModelsSection(...)`.
* **Line Budget Check**:
  - `AdvancedTuningScreen.kt`: currently 394 lines $\rightarrow$ drops to ~382 lines (safely below 400).
  - `AdvancedTuningDialog.kt`: currently 372 lines $\rightarrow$ drops to ~360 lines (safely below 400).

### Step 5: PowerMetricSlot Enum & Pure Slot Resolution (`PowerMetricSlot.kt`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/PowerMetricSlot.kt`
* **Changes**:
  - In `enum class PowerMetricSlot`, remove `WORK_KJ` and `POWER_TO_WEIGHT`:
    ```kotlin
    enum class PowerMetricSlot {
        NORMALIZED_POWER,
        INTENSITY_FACTOR,
        TSS
    }
    ```
  - In `resolvePowerMetricSlots`:
    - Remove `enableBasicPowerMetrics`, `showBasicPowerInWorkoutSummary`, `enableBasicPower`, `showBasicPower` parameters.
    - Evaluate: `showCoggan = effEnableCoggan && effShowCoggan && data.normalizedPowerWatts != null`.
    - If `!showCoggan`, return `emptyList()`.
    - If `showCoggan`, build list: add `NORMALIZED_POWER`, plus `INTENSITY_FACTOR` (if non-null), plus `TSS` (if non-null).
    - Zero fallback to work or W/kg.

### Step 6: Refactor WorkoutDetails & PowerTrainingMetricsSection (`WorkoutDetails.kt`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetails.kt`
* **Changes**:
  - Remove parameters `enableBasicPowerMetrics` and `showBasicPowerInWorkoutSummary` from `WorkoutDetails` and `buildVisibleWorkoutDetailSections`.
  - In `PowerTrainingMetricsSection`: remove `PowerMetricSlot.WORK_KJ` and `PowerMetricSlot.POWER_TO_WEIGHT` `when` branches.
  - In `PreviewWorkoutDetails`: update arguments.
* **Line Budget Check**: Currently 494 lines $\rightarrow$ drops to ~470 lines (safely below 500).

### Step 7: Update Consumer Callers (`WorkoutSummary.kt` & `WorkoutDetailTabbedContent.kt`)
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContent.kt`
* **Changes**:
  - Remove `enableBasicPowerMetrics = ...` and `showBasicPowerInWorkoutSummary = ...` argument passing when invoking `WorkoutDetails(...)`.

### Step 8: Clean Up Dead Strings in All 9 Locales (`strings.xml`)
* **Target Files**:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
* **Changes**:
  - Remove `tuning_physiology_basic_power_title` and `tuning_physiology_basic_power_desc`.
  - Verify `tuning_physiology_show_in_workout_summary` is retained in all 9 files.

### Step 9: Update Unit and Contract Tests
* **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/PowerMetricSlotResolutionTest.kt`:
    - Case 1: Full Coggan with summary enabled $\rightarrow$ `[NORMALIZED_POWER, INTENSITY_FACTOR, TSS]`.
    - Case 2: Coggan summary disabled $\rightarrow$ `emptyList()`.
    - Case 3: Coggan master disabled $\rightarrow$ `emptyList()`.
    - Case 4: Missing NP $\rightarrow$ `emptyList()`.
    - Case 5: Missing FTP (null IF/TSS) $\rightarrow$ strictly `[NORMALIZED_POWER]` (no basic power fallback).
    - Case 6: Coggan with IF only $\rightarrow$ `[NORMALIZED_POWER, INTENSITY_FACTOR]`.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetailsLayoutTest.kt`:
    - Update `testResolvePowerMetricSlots` assertions:
      - `FullCogganTrio`: `[NP, IF, TSS]`
      - `MissingFtpFallback`: `[NP]` (no basic fallback)
      - `BasicPhysicsOnly`: `emptyList()`
    - Update `testWorkoutDetails_summaryTogglesContract_gatingAndModularity`:
      - Remove `enableBasicPowerMetrics` / `showBasicPowerInWorkoutSummary` checks.
      - Retain check for `showCogganPowerInWorkoutSummary`, `showAerobicEfficiencyInWorkoutSummary`, `showCriticalPowerInWorkoutSummary`.
      - Assert `WorkoutDetails.kt` line count is strictly `< 500`.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/PhysiologyModelsVisibilityContractTest.kt`:
    - Update `testTuningConfigDefaults`: verify 4 master models and 3 summary toggles (remove basic power assertions).
    - Update `testTuningConfig_persistenceAndModularity`:
      - Assert `ALL_KEYS` contains the 3 summary keys and does not contain basic power keys.
      - Assert `PhysiologyModelsSection.kt` source does not contain `enableBasicPowerMetrics`.
      - Assert `PhysiologyModelsSection.kt` uses `SubordinateTuningToggleItem`.
      - Assert line counts of `PhysiologyModelsSection.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, and `TuningFormControls.kt` are strictly `< 400`.
    - Update string key audit list: remove `tuning_physiology_basic_power_title` and `tuning_physiology_basic_power_desc`.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileContractAndLocalizationTest.kt`:
    - Remove assertions requiring `R.string.workout_power_value_work_format` and `R.string.workout_power_metrics_wkg_format` inside `WorkoutDetails.kt`.

### Step 10: Targeted Test Suite Execution
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.workoutdetails.PowerMetricSlotResolutionTest" \
                              --tests "com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsLayoutTest" \
                              --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.PhysiologyModelsVisibilityContractTest" \
                              --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteProfileContractAndLocalizationTest"
  ```

---

## 6. Verification & Rollback Plan

* **Automated Test Verification**:
  1. Targeted test verification of modified suites after Stage 4 construction.
  2. Full clean-room regression suite execution (`./gradlew testDebugUnitTest`) during Stage 5.
  3. Requirement governance audit (`python3 tools/verify_requirement_governance.py`).
* **Line Budget Modularity Verification**:
  - `PhysiologyModelsSection.kt`: $< 400$ lines.
  - `AdvancedTuningScreen.kt`: $< 400$ lines.
  - `AdvancedTuningDialog.kt`: $< 400$ lines.
  - `TuningFormControls.kt`: $< 400$ lines.
  - `WorkoutDetails.kt`: $< 500$ lines.
* **Rollback Strategy**:
  - All changes are isolated on `feature/ATT-4432`.
  - Any blocker can be rolled back via `git checkout .` before committing or discarding the feature branch without affecting `develop` or the active sprint.
