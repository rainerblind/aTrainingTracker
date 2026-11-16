# Stage 3: Implementation Plan - ATT-8117: Move HRV Artifact Gate to Sensors & Add Morning Checks Section

**Ticket**: [ATT-8117](https://rainerblind.atlassian.net/browse/ATT-8117) | **Sub-task**: `[Impl-Plan]` (Pending `submit-stage`)
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)
**Target Release**: `V5.0.0` | **Sprint**: `2026-47.1` | **Branch**: `feature/ATT-8117`
**Requirement Mapping**: `REQ-SET-113` (`REQ-SET-113.1`..`REQ-SET-113.4`)
**Test Mapping**: `TST-SET-115` (`TST-SET-115.1`..`TST-SET-115.5`)
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16

---

## 1. Problem Description & Background
`PhysiologyModelsSection.kt` (`409` lines) mixes physiological training models with HRV RR-interval artifact filtering (`hrvArtifactGateThresholdPercent`) and 7 morning readiness check settings. This plan relocates the HRV Artifact Gate slider into `SensorsGpsFilterSection.kt` (`TuningSection.SENSORS_GPS`) and extracts the 7 Morning Check settings into a dedicated `TuningSection.MORNING_CHECK` (`MorningCheckSection.kt`) wired across both `AdvancedTuningScreen.kt` and `@Deprecated AdvancedTuningDialog.kt`.

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-SET-113.1` (HRV Artifact Gate relocation, `showRpeAndFeel` + HRV zones link kept), `REQ-SET-113.2` (`TuningSection.MORNING_CHECK` & `MorningCheckSection` dual-host wiring), `REQ-SET-113.3` (Design guidelines, contract tests, 9-locale parity), `REQ-SET-113.4` (Rule 8.5 PNG screenshot).
* **Test Mapping**: `TST-SET-115.1`..`TST-SET-115.5` in `docs/tests/tst_settings_profiles.md` (`// TST-SET-115` tags in test methods).

## 3. System Invariants & Preserved Behavior
1. **DataStore & Schema Invariance**: `TuningPreferencesDataStore` keys, defaults (`08:00`, `6..23`, `3.0%..5.0%`), and clamping bounds remain 100% unchanged.
2. **UI Consistency (`docs/design_guidelines.md` §1 & `TuningDesignGuidelinesContractTest`)**: `SensorsGpsFilterSection`, `PhysiologyModelsSection`, and `MorningCheckSection` strictly use `TuningToggleItem`, `SubordinateTuningToggleItem`, `TuningSliderItem`, and `HorizontalDivider` (zero `FilterChip`, raw `Switch(`, or `ElevatedCard(`).
3. **Navigation Chrome & 9-Language Parity**: `SecondaryTabbedHeadersContractTest` TopAppBar navigation untouched; all new strings localized across all 9 locales with zero emojis (`ATT-5400`).

## 4. Proposed Architectural Changes
* **Component 1 (`SensorsGpsFilterSection.kt` & `PhysiologyModelsSection.kt`)**: Add `hrvArtifactGateThresholdPercent` + `onHrvArtifactGateThresholdPercentChange` (defaulted) with `HorizontalDivider` + `TuningSliderItem` (`3.0f..5.0f`, `steps = 3`) to `SensorsGpsFilterSection`; remove HRV Artifact Gate and the 7 Morning Check controls from `PhysiologyModelsSection` (shrinking it `< 340` lines; keeping `tuning_physiology_hrv_zones_link` & `showRpeAndFeel`).
* **Component 2 (`categories/MorningCheckSection.kt`)**: New category composable bundling the 7 Morning Check toggles/slider (`enableMorningSleepAndWellness`, 4 subordinate wellness toggles, `showMorningCheckOnStartScreen`, `morningCheckCutoffHour`) with Light/Dark `@Preview`s.
* **Component 3 (`AdvancedTuningAccordion.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`)**: Add `TuningSection.MORNING_CHECK` (12 entries) and `TuningSubtitleFormatter.formatMorningCheckSubtitle(...)`; mount `TuningAccordionSection(TuningSection.MORNING_CHECK)` in Block 3 (`tuning_group_analysis_models`) and wire `hrvArtifactGateThresholdPercent` to `SensorsGpsFilterSection` in both `AdvancedTuningScreen` and `AdvancedTuningDialog`.

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Risk Classification**: **STANDARD (Medium Risk, 2 SP -> Flash executes Stage 4)**.
* **Step 1 (`SensorsGpsFilterSection.kt` & `PhysiologyModelsSection.kt`)**: Move HRV Artifact Gate slider to `SensorsGpsFilterSection`; remove HRV Artifact Gate & 7 Morning Check params/controls from `PhysiologyModelsSection` (keep `tuning_physiology_hrv_zones_link` & `showRpeAndFeel`).
* **Step 2 (`categories/MorningCheckSection.kt`)**: Create `MorningCheckSection` strictly using `TuningFormControls` wrappers (`TuningToggleItem`, `SubordinateTuningToggleItem`, `TuningSliderItem`, `HorizontalDivider`).
* **Step 3 (`AdvancedTuningAccordion.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`)**: Add `TuningSection.MORNING_CHECK` (12 entries) + `formatMorningCheckSubtitle(...)`; wire `SensorsGpsFilterSection`, `PhysiologyModelsSection`, and `MorningCheckSection` in both `Screen` and `Dialog`.
* **Step 4 (`res/values*/strings_hrv.xml`)**: Add `tuning_cat_morning_check` and Morning Check subtitle strings across all 9 locales (zero emojis, `&amp;` XML escaping).
* **Step 5 (Contract & Unit Tests with `// TST-SET-115`)**: Update `PhysiologyModelsVisibilityContractTest.kt`, `AdvancedTuningAccordionTest.kt`, `AdvancedTuningModularityTest.kt`, `AdvancedTuningScreenTest.kt`, and `AdvancedTuningVisualContractTest.kt` (`ATT-8143`).
* **Step 6 (Rule 8.5 Compose PNG Screenshot)**: Add `AdvancedTuningSectionsScreenshotTest.kt` (`// TST-SET-115`) capturing `docs/engineering/walkthroughs/img/ATT-8117_tuning_sections.png` (Light & Dark `SensorsGpsFilterSection`, `MorningCheckSection`, `PhysiologyModelsSection`) and inspect via `read_file`.
* **Tier 1 Test Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.PhysiologyModelsVisibilityContractTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningAccordionTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningModularityTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningScreenTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningVisualContractTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningDesignGuidelinesContractTest" --tests "com.atrainingtracker.trainingtracker.ui.navigation.SecondaryTabbedHeadersContractTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningSectionsScreenshotTest"
  ```

## 6. Verification & Rollback Plan
* **Verification**: Tier 1 targeted unit + localization + contract + screenshot suite (`100%` pass), `python3 tools/verify_requirement_governance.py`, and Rule 8.5 `read_file` PNG inspection.
* **Rollback**: Isolated on `feature/ATT-8117` prior to Stage 5 squash-merge onto `sprint/2026-47.1`.
