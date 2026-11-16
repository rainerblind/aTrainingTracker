# Stage 2: Requirement & Test Specification - ATT-8117: Move HRV Artifact Gate to Sensors & Add Morning Checks Section

**Ticket**: [ATT-8117](https://rainerblind.atlassian.net/browse/ATT-8117)  
**Sub-task**: [[Req & Test Spec]](https://rainerblind.atlassian.net/browse/ATT-8117)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1` | **Branch**: `feature/ATT-8117`  
**Requirement Mapping**: `REQ-SET-113` | **Test Spec ID**: `TST-SET-115`  
**Author**: AI Agent 2 (`agent2`, `subagent_flash`) | **Date**: 2026-11-16

---

## 1. Requirement Specification (`REQ-SET-113`)

### 1.1 Problem Statement & Rationale
`PhysiologyModelsSection.kt` in `AdvancedTuningScreen` / `AdvancedTuningDialog` bundles over 20 parameters across training models, HRV RR-interval artifact filtering (`hrvArtifactGateThresholdPercent`), and 7 morning wellness check settings. Relocating the HRV Artifact Gate slider to `SensorsGpsFilterSection` and moving the 7 Morning Check settings into a dedicated `TuningSection.MORNING_CHECK` (`MorningCheckSection`) improves domain cohesion while preserving all `TuningPreferencesDataStore` keys (`ATT-8117`, refining `REQ-UI-403`, `REQ-UI-463.2`, `REQ-UI-525.1`).

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. **HRV Artifact Gate Relocation (`REQ-SET-113.1`)**: `hrvArtifactGateThresholdPercent` (`tuning_hrv_artifact_gate_title` slider, `3.0%..5.0%`) SHALL move from `PhysiologyModelsSection` to `SensorsGpsFilterSection` (`TuningSection.SENSORS_GPS`) in both `AdvancedTuningScreen.kt` and `@Deprecated AdvancedTuningDialog.kt` without changing DataStore keys/defaults; `tuning_physiology_hrv_zones_link` and `showRpeAndFeel` SHALL remain in `PhysiologyModelsSection`.
2. **Dedicated Morning Checks Section (`REQ-SET-113.2`)**: `TuningSection` (`AdvancedTuningAccordion.kt`) SHALL add `TuningSection.MORNING_CHECK` (12 canonical entries), paired with `TuningSubtitleFormatter.formatMorningCheckSubtitle(...)` and `MorningCheckSection` hosting all 7 Morning Check settings (`enableMorningSleepAndWellness`, `enableSleepDurationAndQuality`, `enableMorningEnergy`, `enableMuscleSoreness`, `enableContextTags`, `showMorningCheckOnStartScreen`, `morningCheckCutoffHour`), wired in both `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt`.
3. **Design Guidelines, Contract Sync & 9-Language Parity (`REQ-SET-113.3`)**: Category composables MUST comply with `TuningDesignGuidelinesContractTest` (`TuningToggleItem`, `SubordinateTuningToggleItem`, `TuningSliderItem`, `HorizontalDivider` only). `PhysiologyModelsVisibilityContractTest`, `AdvancedTuningAccordionTest` (12 entries), `AdvancedTuningModularityTest`, `AdvancedTuningScreenTest`, and `AdvancedTuningVisualContractTest` SHALL be updated symmetrically. New strings (`tuning_cat_morning_check`, subtitle strings) SHALL maintain 100% 9-locale parity with zero emojis (`TranslationParityTest`).
4. **Visual Compose Screenshot Verification (`REQ-SET-113.4`, Rule 8.5)**: Capture `docs/engineering/walkthroughs/img/ATT-8117_tuning_sections.png` verifying `SensorsGpsFilterSection`, `MorningCheckSection`, and `PhysiologyModelsSection` against the 4-point visual checklist.

### 1.4 Acceptance Criteria & Invariants
* *Given* Expert Settings is opened, *When* expanding *Sensors, GPS & Filtering*, *Then* `tuning_hrv_artifact_gate_title` is hosted there and persists to the unchanged DataStore key.
* *Given* *Morning Checks* (`TuningSection.MORNING_CHECK`) is expanded, *When* toggling or adjusting cutoff hour, *Then* all 7 Morning Check settings operate there with live subtitle formatting while *Physiology & Training Models* keeps training models, HRV zones link, and `showRpeAndFeel`.
* *Invariants*: Zero DataStore key/default/bound changes; zero `FilterChip`, raw `Switch(`, or `ElevatedCard(` in tuning categories; 100% 9-language parity (zero emojis); dual-host symmetry (`Screen` + `Dialog`).

---

## 2. Test Specification (`TST-SET-115`)

1. **`TST-SET-115.1` (`PhysiologyModelsVisibilityContractTest.kt`)**: Verify HRV Artifact Gate & 7 Morning Check controls removed from `PhysiologyModelsSection`, `tuning_hrv_artifact_gate_title` hosted in `SensorsGpsFilterSection`, and `tuning_physiology_hrv_zones_link` + `showRpeAndFeel` retained in `PhysiologyModelsSection`.
2. **`TST-SET-115.2` (`AdvancedTuningAccordionTest.kt`, `AdvancedTuningModularityTest.kt`, `AdvancedTuningScreenTest.kt`, `AdvancedTuningVisualContractTest.kt`)**: Verify 12-entry `TuningSection` enum incl. `MORNING_CHECK`, `formatMorningCheckSubtitle`, `MorningCheckSection` public composable & dual-host wiring.
3. **`TST-SET-115.3` (`TuningDesignGuidelinesContractTest.kt`)**: Verify zero `FilterChip`, raw `Switch(`, or nested `ElevatedCard(` across all tuning category files.
4. **`TST-SET-115.4` (`TranslationParityTest.kt`)**: Verify 100% 9-language localization parity, format specifiers, and zero emojis across all 9 locales.
5. **`TST-SET-115.5` (`AdvancedTuningSectionsScreenshotTest.kt`, Rule 8.5)**: Capture `docs/engineering/walkthroughs/img/ATT-8117_tuning_sections.png` and certify via `read_file`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-SET-115.1]` | Contract | HRV Artifact Gate in `SensorsGpsFilterSection`; `showRpeAndFeel` + zones link kept in `PhysiologyModelsSection` | `REQ-SET-113.1` | Planned |
| `[TST-SET-115.2]` | Unit/Modularity | `TuningSection.MORNING_CHECK` (12 entries), `MorningCheckSection`, subtitle formatter, dual-host wiring | `REQ-SET-113.2` | Planned |
| `[TST-SET-115.3]` | Design Contract | `TuningDesignGuidelinesContractTest` compliance (zero `FilterChip`/`Switch`/`ElevatedCard`) | `REQ-SET-113.3` | Planned |
| `[TST-SET-115.4]` | Localization | 9-locale parity & zero emojis via `TranslationParityTest` | `REQ-SET-113.3` | Planned |
| `[TST-SET-115.5]` | Screenshot | Compose PNG capture & 4-point visual checklist (Rule 8.5) | `REQ-SET-113.4` | Planned |
