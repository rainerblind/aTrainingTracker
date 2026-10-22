# Stage 2: Requirement & Test Specification - ATT-3930: Redesign WorkoutDetails power and physiology rows to follow the AltitudeRow + MetricItem pattern (Section 5.10) aligned with ATT-3929 schools

**Ticket**: [ATT-3930](https://rainerblind.atlassian.net/browse/ATT-3930)  
**Sub-task**: [ATT-3941](https://rainerblind.atlassian.net/browse/ATT-3941) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` (unassigned during in-sprint work per Rule 14)  
**Active Sprint**: `Sprint 2026-42.8`  
**Requirement Mapping**: `REQ-UI-404` (*AltitudeRow + MetricItem Grouped Row Layout for Power and Physiology Metrics in WorkoutDetails (`docs/design_guidelines.md` Section 5.10)*)  
**Test Spec ID**: `TST-UI-367`  
**Branch**: `feature/ATT-3930`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Requirement Specification (REQ-UI-404)

### 1.1 Problem Statement & Rationale
During the Sprint 2026-42.7 review of `ATT-3858`, on-device inspection revealed that the power and physiology summary metrics in `WorkoutDetails.kt` were rendered as a wrapping `FlowRow` of grey pill badges and raw inline `Text("EF: ...")` / `Text("Min W'bal: ...")` lines instead of matching the established `AltitudeRow` + `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` pattern codified in `docs/design_guidelines.md` Section 5.10 (Rule 42).

### 1.2 Functional & Architectural Requirements
1. **Canonical Domain Row Structure (`REQ-UI-404.1` — Section 5.10)**:
   * Each grouped row (`WorkoutDomainRow` / `PowerTrainingMetricsSection` / `AerobicEfficiencyRow` / `AnaerobicBatteryRow`) SHALL render an outer `Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically)` containing:
     - **Left Domain Header Column**: `Column` with localized domain label `Text(style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)`, `Spacer(modifier = Modifier.height(4.dp))`, and a `24.dp` domain `Icon(tint = MaterialTheme.colorScheme.onSurfaceVariant)`, followed by `Spacer(modifier = Modifier.width(20.dp))`.
     - **Right Metrics Column / Grid**: `Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp))` containing horizontal `Row(modifier = Modifier.fillMaxWidth())` rows of 2–3 equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` columns.
2. **Coggan & Power Load Row (`PowerTrainingMetricsSection`, `REQ-UI-404.2`)**:
   * Left header: `R.string.power` + `R.drawable.ic_power` (`24.dp`).
   * Right column:
     - When `enableCogganPowerMetrics == true` and any of `normalizedPowerWatts`, `intensityFactor`, or `tss` is present: a horizontal `Row` of equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` columns for **NP** (`label = stringResource(R.string.normalized_power_short)`, `value = stringResource(R.string.workout_power_value_watts_format, np)`, `secondaryValue = data.variabilityIndex?.let { stringResource(R.string.workout_power_metrics_vi_format, it) }`), **IF** (`label = stringResource(R.string.intensity_factor_short)`, `value = stringResource(R.string.workout_power_value_if_format, ifVal)`), and **TSS** (`label = stringResource(R.string.tss_short)`, `value = stringResource(R.string.workout_power_value_tss_format, tssVal)`).
     - When `workKj != null || avgPowerToWeight != null`: a horizontal `Row` of equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` columns for **Work** (`label = stringResource(R.string.workout_power_label_work)`, `value = stringResource(R.string.workout_power_value_work_format, workKj)`) and **W/kg** (`label = stringResource(R.string.power_to_weight_short)`, `value = stringResource(R.string.workout_power_metrics_wkg_format, avgPowerToWeight)`).
3. **Aerobic Efficiency & Decoupling Row (`AerobicEfficiencyRow`, `REQ-UI-404.3`)**:
   * Gated on `enableAerobicEfficiencyModel && data.efficiencyFactor != null`.
   * Left header: `R.string.workout_domain_efficiency` + `R.drawable.ic_cardio_load` (`24.dp`).
   * Right row: 2 equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` columns:
     - **EF**: `label = stringResource(R.string.workout_ef_short)`, `value = stringResource(if (data.isPowerBasedEfficiency) R.string.workout_ef_value_pw_format else R.string.workout_ef_value_pa_format, data.efficiencyFactor)`.
     - **Pw:Hr / Pa:Hr** (when `data.aerobicDecouplingPct != null`): `label = stringResource(if (data.isPowerBasedEfficiency) R.string.workout_pwhr_short else R.string.workout_pahr_short)`, `value = stringResource(R.string.workout_decoupling_pct_format, data.aerobicDecouplingPct)`, `secondaryValue = stringResource(if (data.isAerobicDecouplingHighDrift) R.string.workout_decoupling_drift else R.string.workout_decoupling_coupled)`.
4. **Critical Power / Anaerobic Battery Row (`AnaerobicBatteryRow`, `REQ-UI-404.4`)**:
   * Gated on `enableCriticalPowerModel && data.minWPrimeBalKj != null && data.minWPrimeBalPct != null`.
   * Left header: `R.string.w_prime_bal_short` + `R.drawable.ic_battery_full` (`24.dp`).
   * Right row: 2 equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` columns:
     - **Min W'bal**: `label = stringResource(R.string.workout_w_prime_min_kj_label)`, `value = stringResource(R.string.workout_w_prime_min_kj_value_format, data.minWPrimeBalKj)`.
     - **Min W'bal %**: `label = stringResource(R.string.workout_w_prime_min_pct_label)`, `value = stringResource(R.string.workout_w_prime_min_pct_value_format, data.minWPrimeBalPct)`, `secondaryValue = if (data.isEmptyTankWarning) stringResource(R.string.workout_w_prime_empty_tank) else null`.
5. **Prohibition of `FlowRow` Pills & 9-Language Localization Parity (`REQ-UI-404.5`)**:
   * `WorkoutDetails.kt` SHALL NOT use `FlowRow` or `surfaceContainerHighest` pill badges.
   * All new domain/metric label and value format string resources SHALL be localized across all 9 supported application locales.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1**:
  * *Given* `WorkoutDetails.kt` inspected by source contract tests,
  * *When* evaluated against `docs/design_guidelines.md` Section 5.10,
  * *Then* `WorkoutDetails.kt` contains zero `FlowRow` or `surfaceContainerHighest` calls and renders `PowerTrainingMetricsSection`, `AerobicEfficiencyRow`, and `AnaerobicBatteryRow` using `WorkoutDomainRow` (`24.dp` domain icon + `20.dp` `Spacer`) and equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` columns.
* **Criterion 2**:
  * *Given* `enableCogganPowerMetrics == false` and a workout with `workKj` and `avgPowerToWeight`,
  * *When* `PowerTrainingMetricsSection` renders,
  * *Then* `Work` (`kJ`) and `W/kg` render in a 2-column `MetricItem` row while `NP`, `IF`, `TSS`, and `VI` are omitted.

### 1.4 System Invariants
* Zero `FlowRow` pill badges in `WorkoutDetails.kt`.
* All `ATT-3929` school toggles (`enableCogganPowerMetrics`, `enableAerobicEfficiencyModel`, `enableCriticalPowerModel`) remain 100% enforced.
* 9-language localization parity across all 9 `values*/strings.xml` files.

---

## 2. Test Specification (TST-UI-367)

### Test Case 1: `testWorkoutDetails_followsAltitudeRowAndMetricItemPattern_section5_10` (`TST-UI-367.1`)
* **Scope**: Architectural & UI Source Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/PhysiologyModelsVisibilityContractTest.kt` & `AthleteProfileContractAndLocalizationTest.kt`
* **Preconditions**: `WorkoutDetails.kt` source file on disk.
* **Action**: Inspect `WorkoutDetails.kt` structure, composables, icons, and modifiers.
* **Expected Result**:
  - Asserts `!detailsSource.contains("FlowRow(")` and `!detailsSource.contains("surfaceContainerHighest")`.
  - Asserts `WorkoutDomainRow`, `PowerTrainingMetricsSection`, `AerobicEfficiencyRow`, and `AnaerobicBatteryRow` are defined and use `R.drawable.ic_power`, `R.drawable.ic_cardio_load`, `R.drawable.ic_battery_full`, `Spacer(modifier = Modifier.width(20.dp))`, and `MetricItem(` with `layout = MetricLayout.VERTICAL` and `Modifier.weight(1f)`.

### Test Case 2: 9-Language Localization & Specifier Audit (`TST-UI-367.2`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileContractAndLocalizationTest.kt`
* **Goal**: Verify string presence and matching `%1$...` positional specifiers across all 9 locales (`EN`, `DE`, `ES`, `FR`, `IT`, `JA`, `NL`, `PL`, `PT`) for all new `workout_*` domain/metric labels and value format strings.
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 3: Clean-Room Regression Suite (`TST-UI-367.3`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-367.1` | Contract / Unit | `WorkoutDetails`, `PowerTrainingMetricsSection`, `AerobicEfficiencyRow`, `AnaerobicBatteryRow` | `REQ-UI-404.1`–`REQ-UI-404.5` | Specified |
| `TST-UI-367.2` | Localization | `AthleteProfileContractAndLocalizationTest` | `REQ-UI-404.5`, `REQ-UI-106` | Specified |
| `TST-UI-367.3` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
