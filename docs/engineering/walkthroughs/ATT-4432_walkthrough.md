# Stage 5: Walkthrough & Verification - ATT-4432: [Verbesserung] Remove Basic Power Metrics (Work, W/kg) toggle from Physiology & Training Models settings

**Ticket**: [ATT-4432](https://rainerblind.atlassian.net/browse/ATT-4432)  
**Sub-task**: `[Test]` (To be created via `submit-stage`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.3`  
**Requirement Mapping**: `REQ-UI-424` (`REQ-UI-424.1`–`REQ-UI-424.6`), `REQ-UI-403`, `REQ-UI-415`  
**Test Mapping**: `TST-UI-388` (`TST-UI-388.1`–`TST-UI-388.6`), `TST-UI-378` (`TST-UI-378.1`–`TST-UI-378.4`)  
**Branch**: `feature/ATT-4432`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-10-20  

---

## 1. Executive Summary & Verification Overview

During Sprint 2026-43.2 Joint Review (ticket **ATT-4432**), two critical discrepancies introduced in ticket **ATT-4369** were flagged:
1. **Unrequested Model & Inappropriate Summary Fallback**:
   An unrequested top-level model toggle `"Basic Power Metrics (Work, W/kg)"` with preference keys `enableBasicPowerMetrics` and `showBasicPowerInWorkoutSummary` was added to `PhysiologyModelsSection.kt`. Furthermore, `PowerMetricSlot.kt` and `WorkoutDetails.kt` were configured to fall back to mechanical work ($kJ$) and power-to-weight ratio ($W/kg$) when Coggan metrics were disabled or uncalculated. Mechanical work and $W/kg$ are baseline physics and body-ratio metrics, not an independent physiology or sports-science training model. They must NOT appear as a settings model and must NOT be displayed in the Workout Summary (`WorkoutDetails`).
2. **Subordinate Toggle Visual Hierarchy (Rule 59 / Section 3.3)**:
   The subordinate `"Show in Workout Summary"` toggles reused full-sized `TuningToggleItem`s with only 16dp indentation, visually competing with parent model headers. Under `docs/design_guidelines.md` Section 3.3 / Rule 59, child toggles must exhibit a lighter visual hierarchy: smaller/lighter typography (`MaterialTheme.typography.bodySmall` / `labelMedium` tinted with `onSurfaceVariant`), compact vertical padding (`padding(start = 16.dp, top = 2.dp, bottom = 2.dp)`), single concise title line without a heavy subtitle, and a scaled-down switch (`Modifier.scale(0.8f)`).

### Architectural & Functional Resolution in ATT-4432:
- **`SubordinateTuningToggleItem`**: Implemented in `TuningFormControls.kt` adhering to Rule 59 / Section 3.3 and wired into `PhysiologyModelsSection.kt` for Coggan, Aerobic Efficiency, and Critical Power subordinate toggles.
- **Model Elimination**: Removed "Basic Power Metrics (Work, W/kg)" master toggle and its subordinate switch completely from `PhysiologyModelsSection.kt`.
- **Preference Elimination**: Removed all basic power preference keys, defaults, and flows from `TuningPreferencesDefaults`, `TuningPreferencesDataStore`, `TuningConfig`, `AdvancedTuningScreen.kt`, and `AdvancedTuningDialog.kt`.
- **Coggan-Only Power Row**: Refactored `PowerMetricSlot.kt` and `WorkoutDetails.kt` to remove `WORK_KJ` and `POWER_TO_WEIGHT`. When `enableCogganPowerMetrics && showCogganPowerInWorkoutSummary` are enabled and NP is present, the Power row renders exclusively Coggan metrics (`NP` with `VI`, `IF`, `TSS`). When disabled or NP is absent, the Power row and its preceding divider are omitted completely (zero basic power fallback).
- **Consumer Updates**: Stripped basic power arguments from callers `WorkoutDetailTabbedContent.kt` and `WorkoutSummary.kt`.
- **9-Language Localization Clean-Up**: Cleanly removed dead strings `tuning_physiology_basic_power_title`, `tuning_physiology_basic_power_desc`, and `tuning_physiology_show_in_workout_summary_desc` across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with 100% parity.
- **Scope Bounding & Invariants**: Baseline physics metrics (`SensorType.WORK_KJ`, `SensorType.POWER_TO_WEIGHT`) in live cockpit sensor grids (`SensorGridScreen`, `EditSensorFieldViewModel`), `WorkoutExtrema`, `WorkoutDescription`, and `AthleteProfileScreen` remain fully functional and uninhibited.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-424.1` | `TST-UI-388.3` | Contract Test: 4 models in `PhysiologyModelsSection.kt`, Rule 59 `SubordinateTuningToggleItem` | **PASSED** | `Verified` |
| `REQ-UI-424.2` | `TST-UI-388.3` | Contract Test: `TuningPreferencesDataStore` & `TuningConfig` persistence, absence of basic power keys, `resetToDefaults()` | **PASSED** | `Verified` |
| `REQ-UI-424.3` | `TST-UI-388.1`, `TST-UI-378.1` | Unit Test: `PowerMetricSlot.resolvePowerMetricSlots` Coggan-only resolution without basic power fallback | **PASSED** | `Verified` |
| `REQ-UI-424.4` | `TST-UI-388.2`, `TST-UI-378.2` | Contract Test: `WorkoutDetails.kt` Coggan-only power row, single-divider cadence, zero duplicate/trailing dividers | **PASSED** | `Verified` |
| `REQ-UI-424.5` | `TST-UI-388.3`, `TST-UI-388.4` | Invariant Test: Sensor inclusion decoupling, modularity budgets (< 400 lines in tuning, < 500 lines in WorkoutDetails) | **PASSED** | `Verified` |
| `REQ-UI-424.6` | `TST-UI-388.5` | Localization Audit: 9-language dead string removal across `values*/strings.xml` with zero orphaned keys | **PASSED** | `Verified` |
| `REQ-UI-403` | `TST-UI-366` | Contract Test: Progressive disclosure of physiology models in Advanced Settings, Profile & Zones, and Live Cockpit | **PASSED** | `Verified` |
| `REQ-UI-415` | `TST-UI-378` | Contract Test: Single-line power row and visual domain row separation with subtle horizontal dividers | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-388.6` | Full Clean-Room Suite: `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Contract Tests
```text
> Task :app:testDebugUnitTest
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.PowerMetricSlotResolutionTest > TST-UI-388_1 Case 1 Full Coggan with summary enabled returns Coggan Trio PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.PowerMetricSlotResolutionTest > TST-UI-388_1 Case 2 Missing FTP returns strictly Normalized Power without basic power fallback PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.PowerMetricSlotResolutionTest > TST-UI-388_1 Case 3 Coggan summary disabled returns empty list PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.PowerMetricSlotResolutionTest > TST-UI-388_1 Case 4 Coggan master disabled returns empty list PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.PowerMetricSlotResolutionTest > TST-UI-388_1 Case 5 Missing Normalized Power returns empty list PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.PowerMetricSlotResolutionTest > TST-UI-388_1 Case 6 Partial FTP with only IF present returns NP and IF PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.PowerMetricSlotResolutionTest > TST-UI-388_1 Case 7 Partial FTP with only TSS present returns NP and TSS PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsLayoutTest > TST-UI-378_1 testResolvePowerMetricSlots FullCogganTrio PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsLayoutTest > TST-UI-378_1 testResolvePowerMetricSlots MissingFtpReturnsOnlyNPWithoutBasicPowerFallback PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsLayoutTest > TST-UI-378_1 testResolvePowerMetricSlots MissingFtpPartialPhysics PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsLayoutTest > TST-UI-378_1 testResolvePowerMetricSlots DisabledCogganOrMissingNpReturnsEmpty PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsLayoutTest > TST-UI-378_1 testResolvePowerMetricSlots AllNull PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsLayoutTest > TST-UI-378_2 and TST-UI-378_3 WorkoutDetails source contract verifies single-line row, dividers, and modularity PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsLayoutTest > TST-UI-378_4 Divider count invariant guarantees zero dividers for single section and N-1 dividers for N sections PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsLayoutTest > TST-UI-388_2 testWorkoutDetails_gatingAndDividerIntegrity PASSED
com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsLayoutTest > TST-TRK-031_7 testWorkoutDetails_noPowerData_omitsAnaerobicBatteryRowAndDivider PASSED
com.atrainingtracker.trainingtracker.ui.settings.tuning.PhysiologyModelsVisibilityContractTest > TST-UI-366_1 default TuningConfig enables only Coggan power metrics PASSED
com.atrainingtracker.trainingtracker.ui.settings.tuning.PhysiologyModelsVisibilityContractTest > TST-UI-366_3 EditSensorFieldViewModel filters Coggan and CP sensors when disabled PASSED
com.atrainingtracker.trainingtracker.ui.settings.tuning.PhysiologyModelsVisibilityContractTest > TST-UI-366_4 and 366_6 AthleteProfile plot and history filters respect enableCriticalPowerModel PASSED
com.atrainingtracker.trainingtracker.ui.settings.tuning.PhysiologyModelsVisibilityContractTest > TST-UI-366_3_5_6 WorkoutDetails and MapDetailLayout source contract gates physiology metrics PASSED
com.atrainingtracker.trainingtracker.ui.settings.tuning.PhysiologyModelsVisibilityContractTest > TST-UI-366_7 and TST-UI-367_2 all 9 locales define physiology models and Section 5_10 string keys PASSED
com.atrainingtracker.trainingtracker.ui.settings.tuning.PhysiologyModelsVisibilityContractTest > TST-UI-388_3 testTuningConfig_persistenceAndModularity PASSED
com.atrainingtracker.trainingtracker.ui.settings.tuning.PhysiologyModelsVisibilityContractTest > TST-UI-388_4 testLiveCockpitAndBackgroundPersistenceInvariants PASSED
com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteProfileContractAndLocalizationTest > AthleteProfileScreen applies WindowInsets navigationBars to LazyColumn bottom contentPadding and hosts 4 Profile and Zones tabs (Rule 41, REQ-UI-397) PASSED
com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningModularityTest > testModularFilesExist PASSED
com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningModularityTest > testAllTuningSourceFilesUnder400Lines PASSED
com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningAccordionTest > testAccordionSectionCount PASSED

BUILD SUCCESSFUL in 6s
```

### Full Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
BUILD SUCCESSFUL in 4m 16s
32 actionable tasks: 1 executed, 31 up-to-date
All 3,568 tests passed with 100% pass rate, zero failures, and zero regressions.
```

---

## 4. UI Consistency & Modularity Budgets (Rule 23 per `docs/design_guidelines.md`)

* **Reference Screen / Component**:
  Existing `PhysiologyModelsSection.kt` and `WorkoutMasksAndCardsSection.kt` in Advanced Settings.
* **Component Reuse & Hierarchy (Rule 59 / Section 3.3)**:
  - `SubordinateTuningToggleItem` implemented in `TuningFormControls.kt`:
    - Typography: `MaterialTheme.typography.bodySmall` / `labelMedium` tinted with `MaterialTheme.colorScheme.onSurfaceVariant`.
    - Compact vertical padding: `padding(start = 16.dp, top = 2.dp, bottom = 2.dp)`.
    - Single concise title line without a subtitle.
    - Scaled-down `Switch`: `Modifier.scale(0.8f)`.
  - Grouped metrics layout in `WorkoutDetails.kt`: `WorkoutDomainRow` conforming to `docs/design_guidelines.md` Section 5.10 (single horizontal row of at most 2-3 equal-weight `MetricItem`s).
* **Divider Cadence & Invariant Protection**:
  - Subtly separated rows using `HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = outlineVariant.copy(alpha = 0.5f))` with `if (index > 0)` guard, ensuring zero leading, zero trailing, and zero consecutive dividers.
* **Modularity Budgets (< 400 lines in tuning, < 500 lines in WorkoutDetails)**:
  - `PhysiologyModelsSection.kt`: **193 lines** (Limit: 400 lines) — **PASS**
  - `AdvancedTuningScreen.kt`: **389 lines** (Limit: 400 lines) — **PASS**
  - `AdvancedTuningDialog.kt`: **367 lines** (Limit: 400 lines) — **PASS**
  - `TuningFormControls.kt`: **246 lines** (Limit: 400 lines) — **PASS**
  - `WorkoutDetails.kt`: **463 lines** (Limit: 500 lines) — **PASS**
  - `PowerMetricSlot.kt`: **63 lines** (Limit: 400 lines) — **PASS**
* **Physical Device Note**:
  No physical Android device connected (`adb devices` empty). Verified via Compose `@Preview`s and automated contract tests.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**:
   - Clean-room unit test suite executed with 100% pass rate (0 failures, 0 regressions across 3,568 tests).
2. **Living Documentation Synchronized**:
   - `docs/requirements.md` (`REQ-UI-403`, `REQ-UI-415`, `REQ-UI-424`) updated to `Verified`.
   - `docs/tests.md` (`TST-UI-378`, `TST-UI-388`) updated to `Verified`.
   - `tools/verify_requirement_governance.py` passed with exit code 0.
3. **9-Language Localization Parity**:
   - Clean removal of `tuning_physiology_basic_power_title`, `tuning_physiology_basic_power_desc`, and `tuning_physiology_show_in_workout_summary_desc` across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
   - 100% parity and zero orphaned strings verified by contract tests.
4. **Scope Bounding (ATT-1250)**:
   - Baseline physics metrics (`SensorType.WORK_KJ`, `SensorType.POWER_TO_WEIGHT`) in live cockpit sensor grids, `WorkoutExtrema`, `WorkoutDescription`, and `AthleteProfileScreen` remain fully functional and uninhibited.
5. **ASPICE Rule 8 Invariance**:
   - Zero intermediate git commits created during in-sprint stage transitions. Ready for Gate 5 review, final atomic squash commit, and branch integration by `subagent_pro`.
