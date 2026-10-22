# Stage 1 Analysis: ATT-4432 - [Verbesserung] Remove Basic Power Metrics (Work, W/kg) toggle from Physiology & Training Models settings

**Ticket**: [ATT-4432](https://rainerblind.atlassian.net/browse/ATT-4432)  
**Sub-task**: `[Analysis]` (To be created via `submit-stage`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: Next Milestone / Unassigned  
**Active Sprint**: `2026-43.3`  
**Branch**: `feature/ATT-4432`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-43.2 Joint Review of ticket `ATT-4369` (*Add 'Show in Workout Summary' toggle for each model in Physiology & Training Models settings*), two critical UI/UX refinements were flagged by the human user:

1. **Unwanted "Basic Power Metrics (Work, W/kg)" Model Toggle & Summary Fallback**:
   - Mechanical work in kilojoules ($kJ$) and power-to-weight ratio ($W/kg$) are baseline physics and body-ratio metrics, not an independent physiological or training science school/model (unlike Coggan Power Metrics, Performance Management / PMC, Aerobic Efficiency / Decoupling, or Critical Power / W'bal).
   - In `ATT-4369`, an extra top-level model toggle `"Basic Power Metrics (Work, W/kg)"` with preference keys `enableBasicPowerMetrics` and `showBasicPowerInWorkoutSummary` was erroneously introduced into `PhysiologyModelsSection.kt`.
   - Furthermore, `resolvePowerMetricSlots` in `PowerMetricSlot.kt` and `PowerTrainingMetricsSection` in `WorkoutDetails.kt` were configured to fall back to `WORK_KJ` and `POWER_TO_WEIGHT` whenever Coggan metrics were disabled or incomplete.
   - **User Clarification**: Basic mechanical work ($kJ$) and $W/kg$ must **NOT** be shown in the Workout Summary (`WorkoutDetails`) at all. The Power domain row in `WorkoutDetails` must exclusively render Coggan Power Metrics (`NP` with `VI` secondary subtitle, `IF`, `TSS`) when Coggan power metrics and their summary display are enabled. When disabled or absent, the Power row must be omitted completely.

2. **Heavy Visual Weight of Subordinate 'Show in Workout Summary' Toggles**:
   - Currently, each subordinate `"Show in Workout Summary"` (`"In Workout-Übersicht anzeigen"`) row reuses the full-size `TuningToggleItem` composable with only a `16.dp` start indent.
   - Because `TuningToggleItem` enforces a `minHeight = 48.dp`, primary `bodyMedium` title font in `onSurface`, and a secondary `bodySmall` subtitle (`tuning_physiology_show_in_workout_summary_desc`), the subordinate row visually competes with its parent model header and looks like a peer top-level model toggle.
   - In accordance with `docs/design_guidelines.md` Section 3.3 / Rule 59 (*Subordinate Settings Toggles: Lighter Visual Weight & Compact Switches*), child toggles nested under a primary model must exhibit a clear, subordinate visual hierarchy: lighter/smaller typography (`MaterialTheme.typography.bodySmall` or `labelMedium` with `onSurfaceVariant` color), single concise line without a heavy subtitle, tighter vertical padding, and a compact `Switch` (`Modifier.scale(0.8f)`).

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Origin in Sprint 2026-43.2 (`ATT-4369`, commit `157684c0`)
In `ATT-4369`, the goal was to satisfy `REQ-UI-424` by providing individual `"Show in Workout Summary"` toggles for the training models introduced in `REQ-UI-403` (Sprint 2026-42.7) and `REQ-UI-415` (Sprint 2026-42.8). However, during implementation:
1. `PhysiologyModelsSection.kt` was given 5 accordion options instead of 4:
   - Model 1: Basic Power Metrics (`enableBasicPowerMetrics`, `showBasicPowerInWorkoutSummary`)
   - Model 2: Coggan Power Metrics (`enableCogganPowerMetrics`, `showCogganPowerInWorkoutSummary`)
   - Model 3: Performance Management (`enablePmcModel`)
   - Model 4: Aerobic Efficiency & Decoupling (`enableAerobicEfficiencyModel`, `showAerobicEfficiencyInWorkoutSummary`)
   - Model 5: Critical Power & Anaerobic Battery (`enableCriticalPowerModel`, `showCriticalPowerInWorkoutSummary`)
2. `TuningPreferencesDataStore.kt` persisted `KEY_ENABLE_BASIC_POWER_METRICS` and `KEY_SHOW_BASIC_POWER_IN_WORKOUT_SUMMARY`, and exposed `enableBasicPowerMetricsFlow`.
3. `TuningConfig.kt` and `TuningPreferencesDefaults.kt` included `enableBasicPowerMetrics` and `showBasicPowerInWorkoutSummary` (defaulting to `true`).
4. `PowerMetricSlot.kt` declared enum values `WORK_KJ` and `POWER_TO_WEIGHT`, and `resolvePowerMetricSlots(...)` actively appended them as fallback slots when Coggan metrics were absent or disabled.
5. `WorkoutDetails.kt` rendered `WORK_KJ` and `POWER_TO_WEIGHT` inside `PowerTrainingMetricsSection`.
6. Subordinate rows reused `TuningToggleItem` with `modifier = Modifier.padding(start = 16.dp)`, rendering full titles and subtitles.

### 2.2 Call Site & Architectural Investigation

| Component / Layer | Affected File | Detailed Impact & Remediation |
| :--- | :--- | :--- |
| **Settings UI** | `PhysiologyModelsSection.kt` | Remove Model 1 (Basic Power Metrics) entirely. Keep exactly 4 models (Coggan, PMC, Aerobic Efficiency, Critical Power). Replace `TuningToggleItem` on child rows with a dedicated subordinate toggle. |
| **Form Controls** | `TuningFormControls.kt` | Add `SubordinateTuningToggleItem` implementing Rule 59: `bodySmall` / `labelMedium` font, `onSurfaceVariant` color, compact padding, single line without subtitle, `Switch` with `Modifier.scale(0.8f)`. |
| **Settings Screens** | `AdvancedTuningScreen.kt` & `AdvancedTuningDialog.kt` | Remove `enableBasicPowerMetrics` and `showBasicPowerInWorkoutSummary` mutable states, parameter pass-throughs, and config bindings. Preserves strict `< 400` line budget (`AdvancedTuningScreen.kt` is currently 394 lines and will drop to ~382 lines). |
| **Persistence / Config** | `TuningPreferencesDataStore.kt` | Remove `KEY_ENABLE_BASIC_POWER_METRICS`, `KEY_SHOW_BASIC_POWER_IN_WORKOUT_SUMMARY`, `ALL_KEYS` entries, `enableBasicPowerMetricsFlow`, and save/reset bindings. Remove default constants in `TuningPreferencesDefaults.kt` and properties in `TuningConfig.kt`. |
| **Telemetry Summary UI** | `WorkoutDetails.kt` | Remove `enableBasicPowerMetrics` and `showBasicPowerInWorkoutSummary` parameters. Remove `WORK_KJ` and `POWER_TO_WEIGHT` branch cases from `PowerTrainingMetricsSection`. Preserves strict `< 500` line budget (currently 494 lines, will drop to ~470 lines). |
| **Slot Resolution Logic** | `PowerMetricSlot.kt` | Remove `WORK_KJ` and `POWER_TO_WEIGHT` from `PowerMetricSlot` enum. Strip `enableBasicPowerMetrics` and `showBasicPowerInWorkoutSummary` parameters from `resolvePowerMetricSlots`. Only return Coggan slots (`NP`, `IF`, `TSS`) when enabled and `normalizedPowerWatts != null`; otherwise return `emptyList()`. |
| **Consumer Callers** | `WorkoutSummary.kt` & `WorkoutDetailTabbedContent.kt` | Remove `enableBasicPowerMetrics` and `showBasicPowerInWorkoutSummary` argument passing to `WorkoutDetails`. |
| **String Resources** | `app/src/main/res/values*/strings.xml` (9 locales) | Remove unused string keys `tuning_physiology_basic_power_title` and `tuning_physiology_basic_power_desc` across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`). |
| **Test Suites** | `PowerMetricSlotResolutionTest.kt`, `WorkoutDetailsLayoutTest.kt`, `PhysiologyModelsVisibilityContractTest.kt`, `AthleteProfileContractAndLocalizationTest.kt` | Update assertions to reflect removal of basic power metrics from summary slots and settings contracts. |

---

## 3. User Scope Grounding (ATT-1250)

To prevent scope creep and maintain strict boundary fidelity:

* **In-Scope Goals**:
  1. Remove the `"Basic Power Metrics (Work, W/kg)"` toggle and its subordinate switch from `PhysiologyModelsSection.kt`.
  2. Maintain strictly 4 physiology models in `PhysiologyModelsSection.kt`: Coggan Power Metrics, Performance Management (PMC), Aerobic Efficiency & Decoupling, and Critical Power & Anaerobic Battery.
  3. Introduce `SubordinateTuningToggleItem` in `TuningFormControls.kt` conforming to Rule 59 (`bodySmall` / `labelMedium`, `onSurfaceVariant` color, compact padding, single concise line, scaled-down `Switch` `Modifier.scale(0.8f)`), and use it for all 3 subordinate `"Show in Workout Summary"` toggles (Coggan, Aerobic Efficiency, Critical Power).
  4. Clean up `enableBasicPowerMetrics` and `showBasicPowerInWorkoutSummary` across `TuningPreferencesDataStore.kt`, `TuningConfig.kt`, `TuningPreferencesDefaults.kt`, `AdvancedTuningScreen.kt`, and `AdvancedTuningDialog.kt`.
  5. Remove `WORK_KJ` and `POWER_TO_WEIGHT` from `PowerMetricSlot.kt` and `WorkoutDetails.kt` (`PowerTrainingMetricsSection`).
  6. Ensure `resolvePowerMetricSlots` returns exclusively Coggan metrics (`NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`) when Coggan power and summary are enabled, and `emptyList()` when disabled or absent.
  7. Update consumer callers `WorkoutSummary.kt` and `WorkoutDetailTabbedContent.kt`.
  8. Clean up dead string resources across all 9 locales.
  9. Update affected unit and contract tests to ensure 100% green test suite.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Do NOT remove or modify live cockpit sensor fields for Power, Mechanical Work (kJ), or Power-to-Weight (W/kg) in `SensorType`, `SensorGridScreen`, or `EditSensorFieldViewModel`. Baseline physics metrics remain fully available for live cockpit tiles.
  2. Do NOT remove or modify Work (kJ) or W/kg in `WorkoutExtrema`, `WorkoutDescription`, or `AthleteProfileScreen` where total session work and athlete metrics are legitimately displayed.
  3. Do NOT alter background sensor telemetry recording in `CyclingPowerMetricsDevice`, `TrackerService`, or SQLite database tables.
  4. Do NOT modify the behavior of the 4 valid physiology models (Coggan, PMC, EF, CP).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

This ticket refines three existing requirements in `docs/requirements.md`: `REQ-UI-403`, `REQ-UI-415`, and `REQ-UI-424`.

### 4.1 REQ-UI-403: Progressive Disclosure of Physiology & Training Models in Advanced Settings
* **Original Requirement ID & Target**: `REQ-UI-403` (`REQ-UI-403.1`, `REQ-UI-403.2`, `REQ-UI-403.3`). Targets `PhysiologyModelsSection.kt`, `TuningPreferencesDataStore.kt`, `TuningConfig.kt`, and `AdvancedTuningScreen.kt`.
* **Historical Origin & Commit Trace**: Sprint 2026-42.7 (`ATT-3929`, commit `a888c381`).
* **Root Reason for Existing Formulation**: `REQ-UI-403` introduced the progressive disclosure accordion category for training models to prevent overwhelming beginner athletes while giving advanced athletes deep telemetry. In `REQ-UI-403.1`, exactly 4 models were defined: Coggan (`true`), PMC (`false`), Aerobic Efficiency (`false`), Critical Power (`false`). It did NOT define a basic power metrics model.
* **Preservation of Core Invariants**: Retaining the 4 canonical models restores 100% architectural alignment with `REQ-UI-403.1`. The subtitle formatter (`TuningSubtitleFormatter.formatPhysiologyModelsSubtitle`) already only formatted these 4 models. Modularity budgets (< 400 lines in `ui/settings/tuning/`) remain strictly preserved.

### 4.2 REQ-UI-415: Single-Line Power Row in WorkoutDetails
* **Original Requirement ID & Target**: `REQ-UI-415` (`REQ-UI-415.1`, `REQ-UI-415.2`). Targets `WorkoutDetails.kt` and `PowerMetricSlot.kt`.
* **Historical Origin & Commit Trace**: Sprint 2026-42.8 (`ATT-4042`, commit `c8939a3f`).
* **Root Reason for Existing Formulation**: Replaced legacy pill badges with the Section 5.10 `WorkoutDomainRow` pattern. In `REQ-UI-415.1`, fallback to `Work (kJ)` and `W/kg` was included when FTP was unconfigured so that the row would not be empty. However, user feedback in Sprint 2026-43.2 concluded that basic mechanical work and W/kg should not be in the Workout Summary at all; the Power row should only represent Coggan Power Metrics.
* **Preservation of Core Invariants**: The single-line layout invariant (`WorkoutDomainRow` with `24.dp` icon, `20.dp` spacer, `Modifier.weight(1f)` items), conditional 12dp divider cadence, and strictly `< 500` lines modularity limit in `WorkoutDetails.kt` remain strictly preserved.

### 4.3 REQ-UI-424: Granular 'Show in Workout Summary' Gating for Physiology Models
* **Original Requirement ID & Target**: `REQ-UI-424` (`REQ-UI-424.1`, `REQ-UI-424.2`, `REQ-UI-424.3`). Targets `PhysiologyModelsSection.kt`, `TuningPreferencesDataStore.kt`, `PowerMetricSlot.kt`, and `WorkoutDetails.kt`.
* **Historical Origin & Commit Trace**: Sprint 2026-43.2 (`ATT-4369`, commit `157684c0`).
* **Root Reason for Existing Formulation**: Added subordinate switches to allow athletes to collapse domain rows in `WorkoutSummary` cards without disabling live cockpit tiles. During implementation, basic power was incorrectly added as a 5th model, and child switches reused full-size toggle items.
* **Preservation of Core Invariants**: The 3 legitimate summary toggles (`showCogganPowerInWorkoutSummary`, `showAerobicEfficiencyInWorkoutSummary`, `showCriticalPowerInWorkoutSummary`) remain active, defaulting to `true`. Subordinate switches now follow Rule 59 visual hierarchy.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Subordinate Toggle UI Pattern (`SubordinateTuningToggleItem`)
In `TuningFormControls.kt`, introduce:
```kotlin
/**
 * Compact subordinate toggle switch for secondary child settings (Rule 59, ATT-4432).
 * Displays lighter typography without a heavy subtitle and a scaled-down switch.
 */
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

### 5.2 Cleaned-Up `PhysiologyModelsSection.kt`
- Remove `enableBasicPowerMetrics`, `onEnableBasicPowerMetricsChange`, `showBasicPowerInWorkoutSummary`, `onShowBasicPowerInWorkoutSummaryChange`.
- Render exactly 4 models:
  1. Coggan Power Metrics (`TuningToggleItem`) $\rightarrow$ Child: `SubordinateTuningToggleItem` ("Show in Workout Summary")
  2. Performance Management / PMC (`TuningToggleItem`, no summary row)
  3. Aerobic Efficiency & Decoupling (`TuningToggleItem`) $\rightarrow$ Child: `SubordinateTuningToggleItem` ("Show in Workout Summary")
  4. Critical Power & Anaerobic Battery (`TuningToggleItem`) $\rightarrow$ Child: `SubordinateTuningToggleItem` ("Show in Workout Summary")

### 5.3 Pure Slot Resolution in `PowerMetricSlot.kt`
- Enum `PowerMetricSlot`:
  ```kotlin
  enum class PowerMetricSlot {
      NORMALIZED_POWER,
      INTENSITY_FACTOR,
      TSS
  }
  ```
- Function `resolvePowerMetricSlots`:
  ```kotlin
  fun resolvePowerMetricSlots(
      data: WorkoutDetailsData,
      enableCogganPowerMetrics: Boolean = true,
      showCogganPowerInWorkoutSummary: Boolean = true,
      enableCogganPower: Boolean = enableCogganPowerMetrics,
      showCogganPower: Boolean = showCogganPowerInWorkoutSummary
  ): List<PowerMetricSlot> {
      val effEnableCoggan = if (enableCogganPower != enableCogganPowerMetrics) enableCogganPower else enableCogganPowerMetrics
      val effShowCoggan = if (showCogganPower != showCogganPowerInWorkoutSummary) showCogganPower else showCogganPowerInWorkoutSummary
      val showCoggan = effEnableCoggan && effShowCoggan && data.normalizedPowerWatts != null

      if (!showCoggan) return emptyList()

      return buildList {
          add(PowerMetricSlot.NORMALIZED_POWER)
          data.intensityFactor?.let { add(PowerMetricSlot.INTENSITY_FACTOR) }
          data.tss?.let { add(PowerMetricSlot.TSS) }
      }
  }
  ```

### 5.4 File Modularity & Line Budget Safety
- `PhysiologyModelsSection.kt`: currently 234 lines $\rightarrow$ will decrease to ~180 lines (limit: 400).
- `AdvancedTuningScreen.kt`: currently 394 lines $\rightarrow$ will decrease to ~382 lines (limit: 400).
- `AdvancedTuningDialog.kt`: currently 372 lines $\rightarrow$ will decrease to ~360 lines (limit: 400).
- `TuningFormControls.kt`: currently 210 lines $\rightarrow$ will increase to ~235 lines (limit: 400).
- `WorkoutDetails.kt`: currently 494 lines $\rightarrow$ will decrease to ~470 lines (limit: 500).

---

## 6. System Invariants & Risk Assessment

### 6.1 Core System Invariants
1. **Unconditional Background Processing**: Background computation, sensor recording (`TrackerService`, `CyclingPowerMetricsDevice`), and SQLite storage remain 100% active and uninhibited.
2. **Live Cockpit Sensor Gating Invariance**: Live cockpit tile selection and sensor field filtering (`EditSensorFieldViewModel`, `SensorGridScreen`) depend exclusively on master model toggles (`enableCogganPowerMetrics`, `enableCriticalPowerModel`), completely independent of summary switches.
3. **Strict Modularity Limits**: Every `.kt` file in `ui/settings/tuning/` strictly remains `< 400` lines; `WorkoutDetails.kt` strictly remains `< 500` lines.
4. **9-Language Localization Parity**: Clean removal of dead strings across all 9 locales without leaving any orphaned or untranslated keys.
5. **Deterministic Divider Cadence**: `HorizontalDivider` in `WorkoutDetails` is rendered if and only if a subsequent domain section is visible, guaranteeing zero leading, zero trailing, and zero duplicate dividers.
6. **Parent Ticket Governance**: Human Decision Gate remains strictly enforced (`Final Review (Human)`).

### 6.2 Risk Rating
* **Risk Rating**: **LOW**
* **Technical Justification**:
  - The changes are strictly localized to UI presentation and settings preferences.
  - No database migrations, SQLite schema changes, or background service concurrency mechanisms are touched.
  - Removing an unwanted toggle and dead fallback branches simplifies the state space and reduces code complexity.
  - Modularity budgets are improved (decreasing line counts in critical files near the threshold).
