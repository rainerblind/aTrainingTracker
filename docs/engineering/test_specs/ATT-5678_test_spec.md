# Stage 2: Requirement & Test Specification - ATT-5678: Trademark-Free Physiology Terminology

**Ticket**: [ATT-5678](https://rainerblind.atlassian.net/browse/ATT-5678)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5678`  
**Requirement Mapping**: `REQ-UI-459` (refining `REQ-UI-401`, `REQ-UI-402`, `REQ-UI-403`) | **Test Spec ID**: `TST-UI-459`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Requirement Specification (`REQ-UI-459`)

### 1.1 Problem Statement & Rationale
Peaksware, LLC (TrainingPeaks) holds registered trademarks on cycling/endurance terms: `Normalized Power®` (`NP®`), `Intensity Factor®` (`IF®`), `Training Stress Score®` (`TSS®`), and `Performance Management Chart®` (`PMC®`), along with `CTL`, `ATL`, and `TSB`. While mathematical formulas are public domain, using proprietary trademarks in UI strings exposes third-party apps to trademark enforcement. The system SHALL replace all user-facing trademarked terms across all 9 locales with trademark-free, descriptive terminology (`WP`, `RI`, `Load`, `Load / Form`, `Fitness (42d)`, `Fatigue (7d)`, `Form (Balance)`).

### 1.2 Functional & Architectural Requirements
1. **Power & Training Load Parity (`REQ-UI-459.1`)**: Across all 9 locales, the system SHALL display **Weighted Power** (**WP**) / DE: **Gewichtete Leistung**, **Relative Intensity** (**RI**) / DE: **Relative Intensität**, and **Training Load** (**Load**) / DE: **Trainingsbelastung** for `normalized_power`, `intensity_factor`, and `tss` resources. Non-translatable badges `normalized_power_short` (`WP`), `intensity_factor_short` (`RI`), and `tss_short` (`Load`) SHALL reside solely in base `values/strings.xml` (`translatable="false"`).
2. **Aftermath Workout Detail Formats (`REQ-UI-459.2`)**: Workout detail formats SHALL display `WP: %1$.0f W`, `RI: %1$.2f`, and `Load: %1$.0f` across all 9 locales in `workout_power_metrics_*_format`.
3. **Training Load & Form (Profile & PMC) (`REQ-UI-459.3`)**: In the Athlete Profile, the system SHALL replace `PMC` / `CTL` / `ATL` / `TSB` with **Training Load & Form** (DE: **Belastungssteuerung**), **Fitness (42d)**, **Fatigue (7d)** (DE: **Ermüdung (7d)**), **Form (Balance)**, and ramp rate format `%1$+.1f/wk` (DE: `%1$+.1f/Wo.`) in `athlete_profile_pmc_*`.
4. **Advanced Settings (Tuning) & Tips Parity (`REQ-UI-459.4`)**: In `TuningSection.PHYSIOLOGY_MODELS` and Tips, titles/descriptions SHALL reference trademark-free terms (`tuning_physiology_coggan_*`, `tuning_physiology_pmc_*`, `tip_training_zones_*`). Short indicator `tuning_physiology_pmc_short` SHALL be **Load / Form** (DE: **Belastung / Form**).
5. **System Invariants & Schema Stability (`REQ-UI-459.5`)**: Internal SQLite columns, sensor enums (`SensorType.NORMALIZED_POWER`, `TSS`), DTO properties (`normalizedPowerWatts`, `tss`), and calculation algorithms (`PowerPhysiologyCalculator.kt`) SHALL remain 100% unchanged for backward compatibility. Zero emojis SHALL appear in updated strings (`ATT-5400`).

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **AC-1 (Power & Load Metrics)**: *Given* an athlete viewing post-workout details or sensor tiles, *When* reading power metrics, *Then* labels display `WP`, `RI`, and `Load` (DE: `Gewichtete Leistung`, `Relative Intensität`, `Trainingsbelastung`).
* **AC-2 (Profile Load & Form)**: *Given* an athlete viewing the Athlete Profile, *When* reading longitudinal status cards, *Then* cards display `Fitness (42d)`, `Fatigue (7d)`, `Form (Balance)`, and `%1$+.1f/wk` without `CTL`, `ATL`, `TSB`, or `PMC`.
* **AC-3 (Settings & Tips)**: *Given* an athlete viewing Advanced Tuning or Tips, *When* inspecting physiology options, *Then* options display trademark-free titles and descriptions across all 9 locales.
* **AC-4 (9-Language Parity)**: *Given* `TranslationParityTest`, *When* validating all 9 locales, *Then* zero missing keys, zero positional specifier mismatches, and zero inline emojis exist.
* **Invariants**: 100% mathematical invariance; zero database schema or DTO mutations; backward backup compatibility.

---

## 2. Test Specification (`TST-UI-459`)

### Test Case 1: 9-Language Localization & Positional Specifier Audit (`TST-UI-459.1`)
* **Scope**: Localization Contract Test (`TranslationParityTest.kt`, `AthleteProfileContractAndLocalizationTest.kt`)
* **Action**: Verify all 20 updated string keys exist across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with exact format specifiers (`%1$+.1f`, `%1$.0f`, `%1$.2f`) and zero inline emojis.

### Test Case 2: Tuning Accordion & Subtitle Assertions (`TST-UI-459.2`)
* **Scope**: Unit Test (`AdvancedTuningAccordionTest.kt`)
* **Action**: Verify `tuning_physiology_pmc_short` resolves to `"Load / Form"` (or localized) and accordion category subtitle concatenates trademark-free models without error.

### Test Case 3: Sensor Category Search Filter Contract (`TST-UI-459.3`)
* **Scope**: Contract Test (`SensorCategoryAndSelectorContractTest.kt`)
* **Action**: Verify sensor query matching for `NORMALIZED_POWER` works with updated short badge `"WP"` and full name `"Weighted Power"`.

### Test Case 4: Mathematical Calculation & Schema Invariance (`TST-UI-459.4`)
* **Scope**: Unit Test (`PowerPhysiologyCalculatorTest.kt`, `WorkoutDataMappingSymmetryTest.kt`)
* **Action**: Verify `calculateHrTss`, `computePerformanceManagementChart`, and `resolvePowerTrainingSummary` output identical numeric results with unchanged DTO mapping.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-UI-459.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteProfileContractAndLocalizationTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningAccordionTest" --tests "com.atrainingtracker.trainingtracker.ui.help.TipsGuideScreenContractTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.SensorCategoryAndSelectorContractTest"`
* **Action**: Confirm 100% test pass rate across all modified contract suites.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-459.1` | Localization | `TranslationParityTest`, 9 `strings.xml` files | `REQ-UI-459.1`, `REQ-UI-459.2`, `REQ-UI-459.3` | Specified |
| `TST-UI-459.2` | Unit | `AdvancedTuningAccordionTest` | `REQ-UI-459.4` | Specified |
| `TST-UI-459.3` | Contract | `SensorCategoryAndSelectorContractTest` | `REQ-UI-459.1` | Specified |
| `TST-UI-459.4` | Invariance | `PowerPhysiologyCalculatorTest`, DTOs | `REQ-UI-459.5` | Specified |
| `TST-UI-459.5` | Regression | Full Affected Contract Suite | `REQ-UI-459.1`..`REQ-UI-459.5` | Specified |
