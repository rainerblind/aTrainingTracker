# Stage 5: Walkthrough & Verification - ATT-3930: Redesign WorkoutDetails power and physiology rows to follow the AltitudeRow + MetricItem pattern (Section 5.10) aligned with ATT-3929 schools

**Ticket**: [ATT-3930](https://rainerblind.atlassian.net/browse/ATT-3930)  
**Sub-task**: [ATT-3944](https://rainerblind.atlassian.net/browse/ATT-3944) (`[Test]`)  
**Parent Epic**: [ATT-1759](https://rainerblind.atlassian.net/browse/ATT-1759) (*UI Consolidation & Modernization*)  
**Target Release**: `Unassigned (Assigned upon Final Review acceptance per Rule 14)`  
**Active Sprint**: `2026-42.8`  
**Requirement Mapping**: `REQ-UI-404`  
**Test Mapping**: `TST-UI-367`  
**Branch**: `feature/ATT-3930`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Executive Summary & Verification Overview

This deliverable documents the Stage 5 verification of **ATT-3930**, which redesigns all power and physiology summary sections in `WorkoutDetails.kt` (`PowerTrainingMetricsSection`, `AerobicEfficiencyRow`, and `AnaerobicBatteryRow`) to strictly follow the canonical `AltitudeRow` + `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` grouped row layout pattern defined in `docs/design_guidelines.md` Section 5.10 (Rule 42), aligned with the `ATT-3929` training science school toggles:

1. **Shared `WorkoutDomainRow` Header Container**:
   - Introduced `WorkoutDomainRow` in `WorkoutDetails.kt` encapsulating the left domain header column (`labelMedium` domain label + `4.dp` `Spacer` + `24.dp` domain `Icon` tinted `onSurfaceVariant` + `20.dp` horizontal `Spacer`) and the right `Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp))`.
   - Refactored `AltitudeRow` to delegate to `WorkoutDomainRow` with `Modifier.weight(1f)` on each `MetricItem(layout = MetricLayout.VERTICAL)`.
2. **Power & Coggan Row (`PowerTrainingMetricsSection`)**:
   - Eliminated all `FlowRow` and `Surface(color = surfaceContainerHighest)` pill chips.
   - Left domain header: `R.string.power` + `R.drawable.ic_power` (`24.dp`).
   - Right column renders up to 2 balanced `Row`s of equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` columns:
     - **Coggan Row** (gated on `enableCogganPowerMetrics == true`): **NP** (`W`, with optional `VI` as `secondaryValue`), **IF**, and **TSS**.
     - **Basic Physics Row** (unconditionally visible whenever `workKj != null || avgPowerToWeight != null`): **Work** (`kJ`) and **W/kg**.
3. **Aerobic Efficiency & Decoupling Row (`AerobicEfficiencyRow`)**:
   - Replaced raw inline `Text` and colored pill `Surface` with `WorkoutDomainRow` (`R.string.workout_domain_efficiency` + `R.drawable.ic_cardio_load`) and 2 equal-weight `MetricItem` columns: **EF** (`W/bpm` or `m/min/bpm`) and **Pw:Hr** / **Pa:Hr** (`%` with Coupled / High Drift `secondaryValue`).
4. **Critical Power / Anaerobic Battery Row (`AnaerobicBatteryRow`)**:
   - Replaced raw inline `Text` and error pill `Surface` with `WorkoutDomainRow` (`R.string.w_prime_bal_short` + `R.drawable.ic_battery_full`) and 2 equal-weight `MetricItem` columns: **Min W'bal** (`kJ`) and **Min W'bal %** (`%` with optional Empty Tank warning `secondaryValue`).
5. **9-Language Localization Parity**:
   - Added 16 localized string keys across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-404.1` – `REQ-UI-404.4` | `TST-UI-367.1` | `PhysiologyModelsVisibilityContractTest` & `AthleteProfileContractAndLocalizationTest` | **PASSED** | `Verified` |
| `REQ-UI-404.5` | `TST-UI-367.2` | 9-Language Localization Parity Audit (`AthleteProfileContractAndLocalizationTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-367.3` | Full Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL
```

### Targeted Unit & Integration Tests
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteProfileContractAndLocalizationTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.PhysiologyModelsVisibilityContractTest"
BUILD SUCCESSFUL in 34s
```

---

## 4. Hardware / Physical Verification (Pixel 10)

### Visual Consistency (Rule 23 & Rule 42 — `docs/design_guidelines.md` Section 5.10)
- `WorkoutDetails.kt` now reuses `WorkoutDomainRow` (`AltitudeRow` pattern) and `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` across all grouped metric sections (`AltitudeRow`, `PowerTrainingMetricsSection`, `AerobicEfficiencyRow`, `AnaerobicBatteryRow`).
- Multi-state Light and Dark `@Preview` composables (`WorkoutDetailsFullPreview`, `WorkoutDetailsDarkPreview`, `WorkoutDetailsMinimalPreview`) verified in `WorkoutDetails.kt`.
- Full on-device verification on Google Pixel 10 will be executed at the start of Ceremony 2 (Sprint Review) via `./gradlew installDebug` per Rule 10.

* **Checked against `docs/design_guidelines.md` §5 & §5.10**: shapes ☑ spacing ☑ colors/themes ☑ typography/icons ☑ placement ☑
* **Deviations & justification**: None.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Clean-room unit test suite executed with 100% pass rate.
2. **Living Documentation Synchronized**: Status of `REQ-UI-404` in `docs/requirements.md` and `TST-UI-367` in `docs/tests.md` updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask `ATT-3944` transitioned to `Erledigt` via `freigabe`.
4. **Parent Ticket Final Review**: `feature/ATT-3930` merged into `sprint/2026-42.8` (`--no-ff`) and `ATT-3930` transitioned to `Final Review (Human)`.
