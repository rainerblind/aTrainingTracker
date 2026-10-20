# Stage 5: Walkthrough & Verification - ATT-4369: Add 'Show in Workout Summary' toggle for each model in Physiology & Training Models settings

**Ticket**: [ATT-4369](https://rainerblind.atlassian.net/browse/ATT-4369)  
**Sub-task**: [Impl & Test Subtasks](https://rainerblind.atlassian.net/browse/ATT-4369) (`[Test]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Requirement Mapping**: `REQ-UI-424` (`REQ-UI-424.1`–`REQ-UI-424.6`)  
**Test Mapping**: `TST-UI-388` (`TST-UI-388.1`–`TST-UI-388.6`)  
**Branch**: `feature/ATT-4369`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Executive Summary & Verification Overview

During Sprint 2026-43.1 human Joint Review (ticket **ATT-4292**), physical on-device evaluation on Google Pixel 10 revealed that when an athlete enables multiple training models to track derived metrics live during activity in Cockpit tabs or to review post-workout telemetry graphs on the map, `WorkoutDetails` automatically surfaces up to 4 additional domain-grouped rows (`Power & Coggan Load`, `Aerobic Efficiency`, and `Anaerobic Battery`). Consequently, `WorkoutSummary` cards exceed 600dp in vertical height, pushing subsequent rich sections (Map Preview, Elevation Profile, Telemetry Charts, Zone Distribution Cards) far below the viewport fold and requiring excessive vertical scrolling in `WorkoutListScreen`.

Ticket **ATT-4369** resolves this presentation explosion by introducing granular subordinate **"Show in Workout Summary"** switches for each model contributing rows to `WorkoutDetails.kt`:
1. **Basic Power Metrics** (`enableBasicPowerMetrics`, with subordinate `showBasicPowerInWorkoutSummary`)
2. **Coggan Power Metrics** (`enableCogganPowerMetrics`, with subordinate `showCogganPowerInWorkoutSummary`)
3. **Performance Management (PMC)**: master toggle only (`enablePmcModel`, no summary rows in `WorkoutDetails`)
4. **Aerobic Efficiency & Decoupling** (`enableAerobicEfficiencyModel`, with subordinate `showAerobicEfficiencyInWorkoutSummary`)
5. **Critical Power & Anaerobic Battery** (`enableCriticalPowerModel`, with subordinate `showCriticalPowerInWorkoutSummary`)

All five subordinate preferences default to `true` (`TuningPreferencesDefaults`), ensuring 100% backward compatibility for clean installs and existing users. Disabling summary toggles completely suppresses their summary telemetry rows from `WorkoutDetails` without affecting live Cockpit tile availability, background recording in `TrackerService`, or SQLite telemetry persistence.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-424.1` | `TST-UI-388.3` | Contract Test: Subordinate switches in `PhysiologyModelsSection.kt` with 16dp indentation | **PASSED** | `Verified` |
| `REQ-UI-424.2` | `TST-UI-388.3` | Contract Test: `TuningPreferencesDataStore` & `TuningConfig` persistence, `ALL_KEYS`, `resetToDefaults()` | **PASSED** | `Verified` |
| `REQ-UI-424.3` | `TST-UI-388.1` | Unit Test: `PowerMetricSlot.resolvePowerMetricSlots` 6-case resolution & fallback matrix | **PASSED** | `Verified` |
| `REQ-UI-424.4` | `TST-UI-388.2` | Contract Test: `WorkoutDetails.kt` gating, single-divider cadence, zero trailing dividers | **PASSED** | `Verified` |
| `REQ-UI-424.5` | `TST-UI-388.3`, `TST-UI-388.4` | Invariant Test: Sensor inclusion decoupling, modularity budgets (< 400 lines in tuning, < 500 lines in WorkoutDetails) | **PASSED** | `Verified` |
| `REQ-UI-424.6` | `TST-UI-388.5` | Localization Audit: 9-language parity across `values*/strings.xml` for all 4 new strings | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-388.6` | Full Clean-Room Suite: `./gradlew testDebugUnitTest` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Contract Tests
```text
> Task :app:testDebugUnitTest
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.PowerMetricSlotResolutionTest > TST-UI-388_1 Case 1 Full Coggan with summary enabled returns Coggan Trio PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.PowerMetricSlotResolutionTest > TST-UI-388_1 Case 2 Coggan summary disabled falls back to Basic Physics Work kJ and W per kg PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.PowerMetricSlotResolutionTest > TST-UI-388_1 Case 3 Coggan summary disabled and Basic Power disabled returns empty list PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.PowerMetricSlotResolutionTest > TST-UI-388_1 Case 4 Both Coggan and Basic Power summary disabled returns empty list PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.PowerMetricSlotResolutionTest > TST-UI-388_1 Case 5 Coggan enabled without FTP fills available slots from basic power when showBasicPower is true PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.PowerMetricSlotResolutionTest > TST-UI-388_1 Case 6 Coggan enabled without FTP does not fill from basic power when showBasicPower is false PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsLayoutTest > TST-UI-388_2 testWorkoutDetails_gatingAndDividerIntegrity PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsLayoutTest > TST-UI-378_1 testResolvePowerMetricSlots FullCogganTrio PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsLayoutTest > TST-UI-378_1 testResolvePowerMetricSlots MissingFtpFallback PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsLayoutTest > TST-UI-378_1 testResolvePowerMetricSlots MissingFtpPartialPhysics PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsLayoutTest > TST-UI-378_1 testResolvePowerMetricSlots BasicPhysicsOnly PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsLayoutTest > TST-UI-378_1 testResolvePowerMetricSlots AllNull PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsLayoutTest > TST-UI-378_2 and TST-UI-378_3 WorkoutDetails source contract verifies single-line row, dividers, and modularity PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsLayoutTest > TST-UI-378_4 Divider count invariant guarantees zero dividers for single section and N-1 dividers for N sections PASSED
com.atrainingtracker.trainingtracker.ui.settings.tuning.PhysiologyModelsVisibilityContractTest > TST-UI-366_1 default TuningConfig enables only Coggan power metrics PASSED
com.atrainingtracker.trainingtracker.ui.settings.tuning.PhysiologyModelsVisibilityContractTest > TST-UI-366_3 EditSensorFieldViewModel filters Coggan and CP sensors when disabled PASSED
com.atrainingtracker.trainingtracker.ui.settings.tuning.PhysiologyModelsVisibilityContractTest > TST-UI-366_4 and 366_6 AthleteProfile plot and history filters respect enableCriticalPowerModel PASSED
com.atrainingtracker.trainingtracker.ui.settings.tuning.PhysiologyModelsVisibilityContractTest > TST-UI-366_3_5_6 WorkoutDetails and MapDetailLayout source contract gates physiology metrics PASSED
com.atrainingtracker.trainingtracker.ui.settings.tuning.PhysiologyModelsVisibilityContractTest > TST-UI-366_7 and TST-UI-367_2 all 9 locales define physiology models and Section 5_10 string keys PASSED
com.atrainingtracker.trainingtracker.ui.settings.tuning.PhysiologyModelsVisibilityContractTest > TST-UI-388_3 testTuningConfig_persistenceAndModularity PASSED
com.atrainingtracker.trainingtracker.ui.settings.tuning.PhysiologyModelsVisibilityContractTest > TST-UI-388_4 testLiveCockpitAndBackgroundPersistenceInvariants PASSED
com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningModularityTest > testModularFilesExist PASSED
com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningModularityTest > testAllTuningSourceFilesUnder400Lines PASSED

BUILD SUCCESSFUL in 6s
```

### Full Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
BUILD SUCCESSFUL in 4m 7s
32 actionable tasks: 1 executed, 31 up-to-date
All 3,568 tests passed with zero failures and zero regressions.
```

---

## 4. UI Consistency & Modularity Budgets (Rule 23 per `docs/design_guidelines.md`)

* **Reference Screen / Component**:
  Existing `PhysiologyModelsSection.kt` and `WorkoutMasksAndCardsSection.kt` in Advanced Settings.
* **Component Reuse & Hierarchy**:
  - `TuningToggleItem` reused for both primary and subordinate toggles.
  - Subordinate indentation: `Modifier.padding(start = 16.dp)` establishes clear visual subordination under each active model.
  - Category row separation: `HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))`.
  - Grouped metrics layout in `WorkoutDetails.kt`: `WorkoutDomainRow` conforming to `docs/design_guidelines.md` Section 5.10 (single horizontal row of at most 2-3 equal-weight `MetricItem`s).
* **Divider Cadence & Invariant Protection**:
  - Subtly separated rows using `HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = outlineVariant.copy(alpha = 0.5f))` with `if (index > 0)` guard, ensuring zero leading, zero trailing, and zero consecutive dividers.
* **Modularity Budgets (< 400 lines in tuning, < 500 lines in WorkoutDetails)**:
  - `PhysiologyModelsSection.kt`: **233 lines** (Limit: 400 lines) — **PASS**
  - `AdvancedTuningScreen.kt`: **393 lines** (Limit: 400 lines) — **PASS**
  - `AdvancedTuningDialog.kt`: **371 lines** (Limit: 400 lines) — **PASS**
  - `WorkoutDetails.kt`: **493 lines** (Limit: 500 lines) — **PASS**

---

## 5. Invariant & Governance Verification

1. **System Invariants Intact**:
   - Background recording (`TrackerService`, `CyclingPowerMetricsDevice`, SQLite tables) unchanged.
   - Cockpit tile availability (`EditSensorFieldViewModel`, `SensorGridScreen`) remains governed strictly by primary model toggles.
   - Clean installs and upgrades default all summary switches to `true`, preserving 100% backward compatibility.
2. **Living Documentation Synchronized**:
   - `docs/requirements.md` (`REQ-UI-424`) updated to `Verified`.
   - `docs/tests.md` (`TST-UI-388`) updated to `Verified`.
   - `tools/verify_requirement_governance.py` passed with exit code 0.
3. **9-Language Localization Parity**:
   - `tuning_physiology_basic_power_title`, `tuning_physiology_basic_power_desc`, `tuning_physiology_show_in_workout_summary`, and `tuning_physiology_show_in_workout_summary_desc` defined across all 9 supported locales: `EN`, `DE`, `ES`, `FR`, `IT`, `JA`, `NL`, `PL`, `PT`.
