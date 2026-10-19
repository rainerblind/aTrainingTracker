# Stage 2: Requirement & Test Specification - ATT-4042: Single-line Power row and clearer visual separation between domain rows in WorkoutDetails

**Ticket**: [ATT-4042](https://rainerblind.atlassian.net/browse/ATT-4042)  
**Sub-task**: [ATT-4227](https://rainerblind.atlassian.net/browse/ATT-4227) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-UI-415` (*Single-Line Power Row and Visual Domain Row Separation with Subtle Horizontal Dividers in WorkoutDetails*)  
**Test Spec ID**: `TST-UI-378` (*WorkoutDetails Single-Line Power Row Layout, Domain Row Dividers, Priority Fallback, and 9-Language Parity Verification Suite*)  
**Branch**: `feature/ATT-4042`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Requirement Specification (REQ-UI-415)

### 1.1 Problem Statement & Rationale
During the Sprint 2026-42.8 Joint Review of *ATT-3930* on Google Pixel 10 (see ticket attachment `ATT-3930_review_feedback.png`), visual inspection of the redesigned `WorkoutDetails` telemetry card revealed two usability and design cadence issues:
1. `PowerTrainingMetricsSection` stacked two horizontal rows (`NP | IF | TSS` on Row 1, and `Work (kJ) | W/kg` on Row 2), disrupting the single-line horizontal rhythm maintained by all other domain rows (*Höhenmeter*, *Effizienz*, *W'bal*).
2. Consecutive domain rows in `WorkoutDetails.kt` visually blended together vertically without dividers, reducing legibility across diverse telemetry domains.

### 1.2 Functional & Architectural Requirements

1. **Single-Line Power & Coggan Load Row (`PowerTrainingMetricsSection`, `REQ-UI-415.1`)**:
   - `WorkoutDetails.kt` (`PowerTrainingMetricsSection`) SHALL render at most ONE single horizontal `Row(modifier = Modifier.fillMaxWidth())` inside `WorkoutDomainRow` displaying 2 or 3 equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` columns, eliminating the stacked 2-row layout.
   - When `enableCogganPowerMetrics == true` and `data.normalizedPowerWatts != null`:
     - If both FTP-dependent metrics `data.intensityFactor != null` and `data.tss != null` are present: render `[NP (with secondary VI), IF, TSS]`.
     - If `data.intensityFactor == null` or `data.tss == null` (e.g. athlete has no configured FTP): render `NP` (with secondary `VI` if present) followed by available metrics up to 3 columns in priority order: `IF` (if present), `TSS` (if present), `Work (kJ)` (if present), `W/kg` (if present).
   - When `enableCogganPowerMetrics == false` or `data.normalizedPowerWatts == null`:
     - If `data.workKj != null || data.avgPowerToWeight != null`: render available basic physics metrics: `Work (kJ)` and `W/kg` (up to 2 columns).
   - If no power metrics are present, `PowerTrainingMetricsSection` SHALL return without rendering.

2. **Visual Separation Between Domain Rows in `WorkoutDetails` (`REQ-UI-415.2`)**:
   - `WorkoutDetails` SHALL render a subtle `HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))` immediately preceding any visible domain row after the initial Time/Distance row:
     - Preceding `AltitudeRow` when `data.ascentMeters > 0 || data.descentMeters > 0`.
     - Preceding `PowerTrainingMetricsSection` when power metrics are present (`hasCogganMetrics || hasBasicPowerMetrics`).
     - Preceding `AerobicEfficiencyRow` when `enableAerobicEfficiencyModel && data.efficiencyFactor != null`.
     - Preceding `AnaerobicBatteryRow` when `enableCriticalPowerModel && data.minWPrimeBalKj != null && data.minWPrimeBalPct != null`.
   - Dividers SHALL only be rendered when the corresponding section is actually visible, ensuring zero leading, zero trailing, and zero consecutive duplicate dividers.
   - The outer `Column` vertical spacing SHALL be adjusted to `Arrangement.spacedBy(12.dp)` to provide balanced 12dp margins above and below each divider.

3. **Preservation of Design System Standards & Line Count Modularity (`REQ-UI-415.3`)**:
   - `WorkoutDetails.kt` SHALL adhere to `docs/design_guidelines.md` Section 5.10 (`WorkoutDomainRow` with `24.dp` domain icon, `20.dp` spacer, `Modifier.weight(1f)` items).
   - `WorkoutDetails.kt` SHALL maintain zero `FlowRow` and zero `surfaceContainerHighest` pill badges.
   - `WorkoutDetails.kt` SHALL remain strictly under 500 lines.

4. **9-Language Localization Parity (`REQ-UI-415.4`)**:
   - All existing power metric strings (`normalized_power_short`, `intensity_factor_short`, `tss_short`, `workout_power_label_work`, `power_to_weight_short`, `workout_power_value_watts_format`, `workout_power_value_if_format`, `workout_power_value_tss_format`, `workout_power_value_work_format`, `workout_power_metrics_wkg_format`, `workout_power_metrics_vi_format`) remain fully defined across all 9 application locales.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement `REQ-UI-415`, refining `REQ-UI-404` (*AltitudeRow + MetricItem Grouped Row Layout for Power and Physiology Metrics in WorkoutDetails*). Targets `WorkoutDetails.kt` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetails.kt`).
2. **Historical Origin & Commit Trace**: Sprint 2026-42.8 (`ATT-3930`, commit `c8939a3f`).
3. **Root Reason for Existing Formulation**: When migrating from legacy pill badges to the Section 5.10 `WorkoutDomainRow` layout in `ATT-3930`, the developer retained both Coggan metrics (`NP`, `IF`, `TSS`) and physics metrics (`Work`, `W/kg`) by rendering two consecutive `Row` composables in `PowerTrainingMetricsSection` so that no metric was lost. However, on-device review on Pixel 10 revealed that two stacked rows disrupted the horizontal cadence of the card and crowded the layout.
4. **Preservation of Core Invariants**:
   - The canonical `WorkoutDomainRow` structure (`24.dp` domain icon, `20.dp` spacer, right-hand column with `Modifier.weight(1f)`) remains 100% intact.
   - Basic physics metrics (`Work kJ`, `W/kg`) remain available when Coggan metrics are disabled or when FTP is missing.
   - Zero `FlowRow` and zero `surfaceContainerHighest` pill badges.
   - 100% 9-language translation parity.

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Single-Line Power Row with Coggan Trio)**:
  * *Given* a workout with `normalizedPowerWatts = 215.0`, `intensityFactor = 0.78`, `tss = 91.0`, `workKj = 1080.0`, `avgPowerToWeight = 2.87`, and `enableCogganPowerMetrics = true`,
  * *When* `PowerTrainingMetricsSection` is rendered,
  * *Then* exactly ONE horizontal row is rendered displaying `NP` (with `VI`), `IF`, and `TSS` in 3 equal-weight columns, and `Work` / `W/kg` are omitted to preserve single-line cadence.

* **Criterion 2 (Single-Line Power Row with FTP-Missing Fallback)**:
  * *Given* a workout with `normalizedPowerWatts = 215.0`, `workKj = 1080.0`, `avgPowerToWeight = 2.87`, null `intensityFactor`, null `tss`, and `enableCogganPowerMetrics = true`,
  * *When* `PowerTrainingMetricsSection` is rendered,
  * *Then* exactly ONE horizontal row is rendered displaying `NP` (with `VI`), `Work (kJ)`, and `W/kg` in 3 equal-weight columns.

* **Criterion 3 (Single-Line Power Row with Basic Physics)**:
  * *Given* a workout with null `normalizedPowerWatts` (or `enableCogganPowerMetrics = false`), `workKj = 1080.0`, and `avgPowerToWeight = 2.87`,
  * *When* `PowerTrainingMetricsSection` is rendered,
  * *Then* exactly ONE horizontal row is rendered displaying `Work (kJ)` and `W/kg` in 2 equal-weight columns.

* **Criterion 4 (Visual Separation Between Domain Rows)**:
  * *Given* `WorkoutDetails` rendered for a workout with active time/distance, altitude, power, efficiency, and W'bal,
  * *When* inspected,
  * *Then* a `HorizontalDivider` is rendered before each subsequent visible section (altitude, power, efficiency, battery), separating all domain rows with balanced 12dp spacing.
  * *Given* an indoor workout without altitude data,
  * *When* inspected,
  * *Then* no divider is rendered for altitude, and the first divider appears directly before the first subsequent visible domain row (e.g. power).

---

## 2. Test Specification (TST-UI-378)

### Test Case 1: Power Training Metrics Priority & Single-Line Fallback Unit Test (`TST-UI-378.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetailsLayoutTest.kt`
* **Test Method**: `testPowerMetricsResolution_FullCogganTrio()`, `testPowerMetricsResolution_MissingFtpFallback()`, `testPowerMetricsResolution_BasicPhysicsOnly()`, `testPowerMetricsResolution_AllNull()`
* **Preconditions**: Test data fixtures with varying combinations of NP, IF, TSS, Work kJ, and W/kg.
* **Action**: Invoke the metric resolution helper and verify the returned list of items.
* **Expected Result**:
  - Full Coggan: returns 3 items: `[NORMALIZED_POWER, INTENSITY_FACTOR, TSS]`.
  - Missing FTP: returns 3 items: `[NORMALIZED_POWER, WORK_KJ, POWER_TO_WEIGHT]`.
  - Basic physics: returns 2 items: `[WORK_KJ, POWER_TO_WEIGHT]`.
  - All null: returns empty list.
  - The number of items never exceeds 3.

### Test Case 2: WorkoutDetails Layout & Divider Source Contract Test (`TST-UI-378.2`)
* **Scope**: Contract Test (AST / Structural inspection)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetailsLayoutTest.kt`
* **Action**: Parse `WorkoutDetails.kt`.
* **Expected Result**:
  - `PowerTrainingMetricsSection` contains exactly one `Row(modifier = Modifier.fillMaxWidth())` call within `WorkoutDomainRow`.
  - `WorkoutDetails` contains `HorizontalDivider` calls conditionally placed before each optional domain row (`AltitudeRow`, `PowerTrainingMetricsSection`, `AerobicEfficiencyRow`, `AnaerobicBatteryRow`).
  - Spacing rhythm uses `Arrangement.spacedBy(12.dp)`.
  - Total file lines in `WorkoutDetails.kt` is strictly under 500 lines.

### Test Case 3: Design Guidelines Section 5.10 & Physiology Visibility Contract Test (`TST-UI-378.3`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/PhysiologyModelsVisibilityContractTest.kt`
* **Action**: Execute existing test `TST-UI-366_3_5_6 WorkoutDetails and MapDetailLayout source contract gates physiology metrics`.
* **Expected Result**: All assertions pass without regression (zero `FlowRow`, zero `surfaceContainerHighest`, `WorkoutDomainRow` present, `ic_power` present, `Spacer(modifier = Modifier.width(20.dp))` present).

### Test Case 4: 9-Language Localization Parity Audit (`TST-UI-378.4`)
* **Scope**: Localization Audit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileContractAndLocalizationTest.kt`
* **Action**: Execute `TST-UI-366_7 and TST-UI-367_2 all 9 locales define physiology models and Section 5_10 string keys`.
* **Expected Result**: All 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) define all required string keys with 100% parity.

---

## 3. Traceability Matrix

| Requirement Clause | Test Spec ID | Target Implementation & Test File | Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-415.1` (Single-Line Power Row) | `TST-UI-378.1` | `WorkoutDetails.kt`, `WorkoutDetailsLayoutTest.kt` | Specified |
| `REQ-UI-415.2` (Domain Row Dividers) | `TST-UI-378.2` | `WorkoutDetails.kt`, `WorkoutDetailsLayoutTest.kt` | Specified |
| `REQ-UI-415.3` (Section 5.10 & Modularity) | `TST-UI-378.3` | `WorkoutDetails.kt`, `PhysiologyModelsVisibilityContractTest.kt` | Specified |
| `REQ-UI-415.4` (9-Language Parity) | `TST-UI-378.4` | `AthleteProfileContractAndLocalizationTest.kt` | Specified |
