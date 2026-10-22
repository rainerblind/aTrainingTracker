# Stage 1 Analysis: ATT-3930 - Redesign WorkoutDetails power and physiology rows to follow the AltitudeRow + MetricItem pattern (Section 5.10) aligned with ATT-3929 schools

**Ticket**: [ATT-3930](https://rainerblind.atlassian.net/browse/ATT-3930)  
**Sub-task**: [ATT-3940](https://rainerblind.atlassian.net/browse/ATT-3940) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` (unassigned during in-sprint work per Rule 14)  
**Active Sprint**: `Sprint 2026-42.8`  
**Branch**: `feature/ATT-3930`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-42.7 review of `ATT-3858`, on-device inspection revealed that the power and physiology summary sections in `WorkoutDetails.kt` (shared by `WorkoutSummary` cards and `TrackOnMapScreen` / `MapDetailLayout`) were rendered as:
1. A wrapping `FlowRow` of grey pill badges (`Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest)` with `"NP: 175 W"`, `"IF: 0,58"`, `"TSS: 88"`, `"Arbeit: 1631 kJ"`, `"2,19 W/kg"`, `"VI: 1,05"`).
2. Raw inline `Text("EF: 1,32 W/bpm • Pw:Hr: 3,2%")` and `Text("Min W'bal: 4,2 kJ (21%)")` rows with floating right-aligned `Surface` badges.

These ad-hoc layouts broke the tabular visual alignment established by `AltitudeRow` in `WorkoutDetails.kt` and violated Rule 23 / Rule 42 (`docs/design_guidelines.md` Section 5.10).

---

## 2. Root Cause Analysis (Forensic Investigation)

Inspecting `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetails.kt`:
* **Lines 258–306 (`AltitudeRow`)**: Defines the canonical grouped metric row pattern in `WorkoutDetails.kt`:
  - **Left Domain Header Column**: `Column` containing `Text(text = stringResource(R.string.elevation), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)`, `Spacer(Modifier.height(4.dp))`, and `Icon(painter = painterResource(id = R.drawable.ic_altitude), modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)`, followed by `Spacer(Modifier.width(20.dp))`.
  - **Right Metrics Column**: `Column` containing `Row(modifier = Modifier.fillMaxWidth())` with equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` cells (`label` above in `labelSmall`/`onSurfaceVariant`, bold `value` below in `bodyMedium`/`FontWeight.Bold`, optional `secondaryValue` in `bodySmall`).
* **Lines 101–130 (`W'bal` Row)**: Uses a raw `Row(horizontalArrangement = Arrangement.SpaceBetween)` with a single combined `Text(R.string.workout_w_prime_bal_min_format)` and a `Surface` chip instead of a domain header column + 2 equal-weight `MetricItem` columns.
* **Lines 133–192 (`EF & Decoupling` Row)**: Uses a raw `Row(horizontalArrangement = Arrangement.SpaceBetween)` with a combined `Text(R.string.workout_ef_pwhr_format)` and a `Surface` chip instead of a domain header column + 2 equal-weight `MetricItem` columns.
* **Lines 214–255 (`PowerTrainingMetricsSection`)**: Uses a `FlowRow` of `Surface(color = surfaceContainerHighest)` pill badges instead of a domain header column (`R.string.power` + `R.drawable.ic_power`) + balanced `Row`s of 2–3 equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` columns.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Refactor `PowerTrainingMetricsSection` (`PowerTrainingMetricsRow`) in `WorkoutDetails.kt` to follow the `AltitudeRow` + `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` pattern (`docs/design_guidelines.md` Section 5.10):
     - Left header: `R.string.power` + `R.drawable.ic_power` (`24.dp`).
     - Right column (`verticalArrangement = Arrangement.spacedBy(8.dp)`):
       - Coggan row (when `enableCogganPowerMetrics` is enabled and NP/IF/TSS are present): up to 3 equal-weight `MetricItem` columns for **NP** (`R.string.normalized_power_short`, value in `W`, optional `secondaryValue` for `VI`), **IF** (`R.string.intensity_factor_short`), and **TSS** (`R.string.tss_short`).
       - Basic power physics row (when `workKj` or `avgPowerToWeight` is present): up to 2 equal-weight `MetricItem` columns for **Work** (`R.string.workout_power_label_work`, value in `kJ`) and **W/kg** (`R.string.power_to_weight_short`, value in `W/kg`).
  2. Refactor the **Aerobic Efficiency & Decoupling** section into `AerobicEfficiencyRow` following the `AltitudeRow` + `MetricItem` pattern (gated by `enableAerobicEfficiencyModel`):
     - Left header: `R.string.workout_domain_efficiency` + `R.drawable.ic_cardio_load` (`24.dp`).
     - Right row: 2 equal-weight `MetricItem` columns for **EF** (`R.string.workout_ef_short`, value `"%1$.2f W/bpm"` or `"%1$.2f m/min/bpm"`) and **Pw:Hr / Pa:Hr** (`R.string.workout_pwhr_short` / `R.string.workout_pahr_short`, value `"%1$.1f%%"`, `secondaryValue` = Coupled / High Drift).
  3. Refactor the **Critical Power / Anaerobic Battery (`W'bal`)** section into `AnaerobicBatteryRow` following the `AltitudeRow` + `MetricItem` pattern (gated by `enableCriticalPowerModel`):
     - Left header: `R.string.w_prime_bal_short` + `R.drawable.ic_battery_full` (`24.dp`).
     - Right row: 2 equal-weight `MetricItem` columns for **Min W'bal** (`R.string.workout_w_prime_min_kj_label`, value `"%1$.1f kJ"`) and **Min W'bal %** (`R.string.workout_w_prime_min_pct_label`, value `"%1$.0f%%"`, `secondaryValue` = Empty Tank warning when `isEmptyTankWarning == true`).
  4. Remove all `FlowRow` `Surface` pill badges and raw inline `Text("EF: ...")` / `Text("Min W'bal: ...")` rows from `WorkoutDetails.kt`.
  5. Maintain 9-language localization parity across all 9 `values*/strings.xml` files while keeping existing string keys in `strings.xml` intact so `AthleteProfileContractAndLocalizationTest` and `PhysiologyModelsVisibilityContractTest` continue to pass.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No changes to `PowerPhysiologyCalculator` math, `WorkoutDataMapper` DTO resolution, or `AthleteProfileScreen` PMC charts.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Amends the UI presentation sub-clauses of `REQ-UI-399.3` (`ATT-3862`), `REQ-UI-400.3` (`ATT-3861`), and `REQ-UI-402.2` (`ATT-3858`), codified as net-new requirement `REQ-UI-404` (`TST-UI-367`). Targets `WorkoutDetails.kt`, `values*/strings.xml`, `AthleteProfileContractAndLocalizationTest.kt`, and `PhysiologyModelsVisibilityContractTest.kt`.
* **Historical Origin & Commit Trace**: Sprint 2026-42.7 (`ATT-3862`, `ATT-3861`, `ATT-3858`) and Sprint 2026-42.8 (`ATT-3929`, commit `ad82d34b`).
* **Root Reason for Existing Formulation**: When `W'bal`, `EF / Decoupling`, and `NP / IF / TSS / Work / W/kg / VI` were first added to `WorkoutDetails.kt` in Sprint 2026-42.7, compact inline `Row`s and `FlowRow` pill badges were used before Rule 42 (`docs/design_guidelines.md` Section 5.10) standardized all domain-grouped rows in `WorkoutDetails.kt` on the `AltitudeRow` + `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` pattern.
* **Preservation of Core Invariants**: All underlying `WorkoutDetailsData` fields, `ATT-3929` school visibility flags (`enableCogganPowerMetrics`, `enableAerobicEfficiencyModel`, `enableCriticalPowerModel`), unconditional visibility of basic physics (`Work kJ` and `W/kg`), 9-locale string resources, and 100% clean-room test pass rate remain strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **Reusable `WorkoutDomainRow` / `AltitudeRow`-Aligned Composables in `WorkoutDetails.kt`**:
   - Extract a shared private helper `WorkoutDomainRow(domainLabel: String, @DrawableRes domainIconRes: Int, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)` matching `AltitudeRow`'s exact geometry:
     - Outer `Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically)`
     - Left `Column` with `Text(text = domainLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)`, `Spacer(Modifier.height(4.dp))`, `Icon(painter = painterResource(id = domainIconRes), contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)`
     - `Spacer(modifier = Modifier.width(20.dp))`
     - Right `Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)`
2. **Three Aligned Domain Rows**:
   - `PowerTrainingMetricsSection` (`domainLabel = stringResource(R.string.power)`, `domainIconRes = R.drawable.ic_power`):
     - Row 1 (Coggan Power Load, when `enableCogganPowerMetrics` and at least one of NP/IF/TSS is non-null): up to 3 equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` columns for `NP` (with `VI` as `secondaryValue`), `IF`, and `TSS`.
     - Row 2 (Basic Power Work & W/kg, when `workKj != null || avgPowerToWeight != null`): up to 2 equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` columns for `Work` (`kJ`) and `W/kg`.
   - `AerobicEfficiencyRow` (`domainLabel = stringResource(R.string.workout_domain_efficiency)`, `domainIconRes = R.drawable.ic_cardio_load`):
     - Single `Row(modifier = Modifier.fillMaxWidth())` with 2 equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` columns:
       1. `EF` (`label = stringResource(R.string.workout_ef_short)`, `value = stringResource(if (data.isPowerBasedEfficiency) R.string.workout_ef_value_pw_format else R.string.workout_ef_value_pa_format, data.efficiencyFactor)`).
       2. `Pw:Hr` / `Pa:Hr` (when `data.aerobicDecouplingPct != null`, `label = stringResource(if (data.isPowerBasedEfficiency) R.string.workout_pwhr_short else R.string.workout_pahr_short)`, `value = stringResource(R.string.workout_decoupling_pct_format, data.aerobicDecouplingPct)`, `secondaryValue = stringResource(if (data.isAerobicDecouplingHighDrift) R.string.workout_decoupling_drift else R.string.workout_decoupling_coupled)`).
   - `AnaerobicBatteryRow` (`domainLabel = stringResource(R.string.w_prime_bal_short)`, `domainIconRes = R.drawable.ic_battery_full`):
     - Single `Row(modifier = Modifier.fillMaxWidth())` with 2 equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` columns:
       1. `Min W'bal` (`label = stringResource(R.string.workout_w_prime_min_kj_label)`, `value = stringResource(R.string.workout_w_prime_min_kj_value_format, data.minWPrimeBalKj)`).
       2. `Min W'bal %` (`label = stringResource(R.string.workout_w_prime_min_pct_label)`, `value = stringResource(R.string.workout_w_prime_min_pct_value_format, data.minWPrimeBalPct)`, `secondaryValue = if (data.isEmptyTankWarning) stringResource(R.string.workout_w_prime_empty_tank) else null`).

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero `FlowRow` pill badges in `WorkoutDetails.kt` (`docs/design_guidelines.md` Section 5.10 / Rule 42).
  2. All `ATT-3929` progressive disclosure flags (`enableCogganPowerMetrics`, `enableAerobicEfficiencyModel`, `enableCriticalPowerModel`) and unconditional `workKj`/`avgPowerToWeight` visibility remain 100% intact.
  3. 9-language localization parity across all 9 `values*/strings.xml` files.
  4. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** — Pure UI layout refactoring inside `WorkoutDetails.kt` with additive localized labels/formatters.
