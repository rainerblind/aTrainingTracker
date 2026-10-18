# Stage 3: Implementation Plan - ATT-3930: Redesign WorkoutDetails power and physiology rows to follow the AltitudeRow + MetricItem pattern (Section 5.10) aligned with ATT-3929 schools

**Ticket**: [ATT-3930](https://rainerblind.atlassian.net/browse/ATT-3930)  
**Sub-task**: [ATT-3942](https://rainerblind.atlassian.net/browse/ATT-3942) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` (unassigned during in-sprint work per Rule 14)  
**Active Sprint**: `Sprint 2026-42.8`  
**Requirement Mapping**: `REQ-UI-404`  
**Test Mapping**: `TST-UI-367`  
**Branch**: `feature/ATT-3930`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Background

During the Sprint 2026-42.7 review of `ATT-3858`, on-device inspection revealed that the power and physiology summary metrics in `WorkoutDetails.kt` were rendered as a wrapping `FlowRow` of grey pill badges (`Surface(color = surfaceContainerHighest)`) and raw inline `Text("EF: ...")` / `Text("Min W'bal: ...")` lines instead of matching the established `AltitudeRow` + `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` pattern codified in `docs/design_guidelines.md` Section 5.10 (Rule 42).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-404` (*AltitudeRow + MetricItem Grouped Row Layout for Power and Physiology Metrics in WorkoutDetails (`docs/design_guidelines.md` Section 5.10)*)
* **Test Mapping**: `TST-UI-367` (*WorkoutDetails Section 5.10 AltitudeRow + MetricItem Grouped Row Layout & 9-Language Parity Verification Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites continue to pass cleanly, and existing string keys (`workout_power_metrics_*`, `workout_ef_*`, `workout_w_prime_*`) remain preserved in `values*/strings.xml`.
2. **ATT-3929 School Gating Invariance**: `enableCogganPowerMetrics`, `enableAerobicEfficiencyModel`, and `enableCriticalPowerModel` toggles and unconditional `workKj`/`avgPowerToWeight` visibility remain 100% intact.
3. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
4. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: 9-Locale String Resources (`app/src/main/res/values*/strings.xml`)
Add short metric labels and pure value formatters across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`):
* `workout_domain_efficiency` ("Efficiency" / "Effizienz")
* `workout_ef_short` ("EF")
* `workout_pwhr_short` ("Pw:Hr")
* `workout_pahr_short` ("Pa:Hr")
* `workout_ef_value_pw_format` (`"%1$.2f W/bpm"`)
* `workout_ef_value_pa_format` (`"%1$.2f m/min/bpm"`)
* `workout_decoupling_pct_format` (`"%1$.1f%%"`)
* `workout_w_prime_min_kj_label` ("Min W\'bal")
* `workout_w_prime_min_pct_label` ("Min %" / "Min. %")
* `workout_w_prime_min_kj_value_format` (`"%1$.1f kJ"`)
* `workout_w_prime_min_pct_value_format` (`"%1$.0f%%"`)
* `workout_power_label_work` ("Work" / "Arbeit")
* `workout_power_value_watts_format` (`"%1$.0f W"`)
* `workout_power_value_if_format` (`"%1$.2f"`)
* `workout_power_value_tss_format` (`"%1$.0f"`)
* `workout_power_value_work_format` (`"%1$.0f kJ"`)

### Component 2: `WorkoutDetails.kt` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetails.kt`)
* Define private helper `WorkoutDomainRow(domainLabel: String, @DrawableRes domainIconRes: Int, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)` with the exact geometry of `AltitudeRow`:
  - `Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically)`
  - Left `Column`: `Text(text = domainLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)`, `Spacer(modifier = Modifier.height(4.dp))`, `Icon(painter = painterResource(id = domainIconRes), contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)`
  - `Spacer(modifier = Modifier.width(20.dp))`
  - Right `Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)`
* Refactor `AltitudeRow`, `PowerTrainingMetricsSection`, `AerobicEfficiencyRow`, and `AnaerobicBatteryRow` to use `WorkoutDomainRow` and equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` columns.
* Remove all `FlowRow` and `surfaceContainerHighest` usages from `WorkoutDetails.kt`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `AltitudeRow` in `WorkoutDetails.kt` (`docs/design_guidelines.md` Section 5.10).
* **Reused components**: `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))`, `WorkoutDomainRow` (`24.dp` domain icon + `20.dp` `Spacer`).
* **Theme tokens**: spacing `4.dp`, `8.dp`, `16.dp`, `20.dp`; typography `MaterialTheme.typography.labelMedium`, `labelSmall`, `bodyMedium`, `bodySmall`; colors `MaterialTheme.colorScheme.onSurfaceVariant`, `onSurface`, `error`.
* **New one-off styles & justification**: None — strictly eliminates previous one-off `FlowRow` pill badges in favor of the canonical `AltitudeRow` + `MetricItem` layout.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Add 16 Localized String Resources Across All 9 Locales
* Files: `app/src/main/res/values*/strings.xml` (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
* Changes: Add the 16 `workout_*` domain/metric label and value format string keys while preserving existing keys.

### Step 2: Refactor `WorkoutDetails.kt` to `WorkoutDomainRow` + `MetricItem`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetails.kt`.
* Changes: Implement `WorkoutDomainRow`, `PowerTrainingMetricsSection`, `AerobicEfficiencyRow`, and `AnaerobicBatteryRow` using `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))`, and update `@Preview` composables.

### Step 3: Update & Run Targeted Unit Tests
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileContractAndLocalizationTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/PhysiologyModelsVisibilityContractTest.kt`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteProfileContractAndLocalizationTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.PhysiologyModelsVisibilityContractTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests in Stage 4, followed by full clean-room regression suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-3930` allows clean revert without affecting `sprint/2026-42.8`.
