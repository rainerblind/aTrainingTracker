# Stage 1 Analysis: ATT-8117 - Expert Settings: Move HRV Artifact Gate to Sensors & Add Morning Checks Section

**Ticket**: [ATT-8117](https://rainerblind.atlassian.net/browse/ATT-8117) | **Sub-task**: `[Analysis]` (Pending `submit-stage`)
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1` | **Branch**: `feature/ATT-8117`
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16

---

## 1. Problem Statement & Motivation
`PhysiologyModelsSection.kt` (`409` lines) in `AdvancedTuningScreen.kt` / `AdvancedTuningDialog.kt` bundles over 20 parameters across three distinct domains: physiological training models, HRV RR-interval artifact filtering (`hrvArtifactGateThresholdPercent`), and morning readiness checks (7 parameters). Relocating `hrvArtifactGateThresholdPercent` to `SensorsGpsFilterSection.kt` and extracting the 7 Morning Check settings into a dedicated `MorningCheckSection` (`TuningSection.MORNING_CHECK`) improves domain cohesion and reduces accordion clutter.

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Gap Analysis**: `PhysiologyModelsSection.kt:277-296` hosts the HRV Artifact Gate slider (`tuning_hrv_artifact_gate_title`), and lines `300-357` host all 7 Morning Check toggles/slider (`enableMorningSleepAndWellness`, `enableSleepDurationAndQuality`, `enableMorningEnergy`, `enableMuscleSoreness`, `enableContextTags`, `showMorningCheckOnStartScreen`, `morningCheckCutoffHour`).
* **Independent Peer Review & Verification** (audited against `9b5bfaef`):
  * *Ticket vs. Live Codebase*: Verified all 7 Morning Check fields, `hrvArtifactGateThresholdPercent`, and `showRpeAndFeel` reside in `PhysiologyModelsSection.kt` and are wired identically from `AdvancedTuningScreen.kt:415-505` and `AdvancedTuningDialog.kt:316-413`. `dfaA1ThresholdT1`/`T2` already live in Training Zones (`tuning_physiology_hrv_zones_link` card at `PhysiologyModelsSection.kt:247-275`).
  * *Blind Spots & Side-Effect Audit (`Rule 68`)*:
    1. **Contract Test Coupling**: `PhysiologyModelsVisibilityContractTest.kt:353-356` explicitly asserts `PhysiologyModelsSection` retains `R.string.tuning_hrv_artifact_gate_title`, and `AdvancedTuningAccordionTest.kt:610-625` asserts an exact 11-entry `TuningSection.values()` list. Both must be updated alongside `AdvancedTuningModularityTest.kt` and `AdvancedTuningScreenTest.kt`.
    2. **Dual Host Wiring (`Screen` + `Dialog`)**: Both `AdvancedTuningScreen.kt` and `@Deprecated AdvancedTuningDialog.kt` must update `SensorsGpsFilterSection(...)` and `PhysiologyModelsSection(...)` calls and mount `TuningSection.MORNING_CHECK`.
    3. **Design Guidelines Contract (`TuningDesignGuidelinesContractTest`)**: All files under `ui/settings/tuning/categories/` must strictly use `TuningToggleItem`, `SubordinateTuningToggleItem`, `TuningSliderItem`, and `HorizontalDivider` (no `FilterChip`, raw `Switch(`, or `ElevatedCard`).
    4. **Stage 4 `Rule 8.5` Screenshot Required**: Modifying Compose UI sections (`SensorsGpsFilterSection`, `MorningCheckSection`, `PhysiologyModelsSection`) requires Robolectric Compose PNG capture (`docs/engineering/walkthroughs/img/ATT-8117_tuning_sections.png`) and `read_file` visual verification.

## 3. Scope Grounding & Non-Goals (`ATT-1250`, `2 SP`)
* **In-Scope Goals**:
  1. Move `hrvArtifactGateThresholdPercent` slider from `PhysiologyModelsSection.kt` to `SensorsGpsFilterSection.kt` (`TuningSection.SENSORS_GPS`).
  2. Add `TuningSection.MORNING_CHECK`, `TuningSubtitleFormatter.formatMorningCheckSubtitle(...)`, and `categories/MorningCheckSection.kt` bundling all 7 Morning Check settings; mount in `AdvancedTuningScreen.kt` & `AdvancedTuningDialog.kt`.
  3. Keep `showRpeAndFeel` and `tuning_physiology_hrv_zones_link` in `PhysiologyModelsSection.kt`; add 9-locale string resources (`tuning_cat_morning_check`, subtitle helpers) with 100% `TranslationParityTest` parity.
  4. Update contract/unit tests (`PhysiologyModelsVisibilityContractTest`, `AdvancedTuningAccordionTest`, `AdvancedTuningModularityTest`, `AdvancedTuningScreenTest`, `AdvancedTuningVisualContractTest`) and capture `Rule 8.5` Compose PNG screenshot.
* **Out-of-Scope Non-Goals**: Changing `TuningPreferencesDataStore.kt` keys, defaults, or clamping bounds; moving `showRpeAndFeel` or DFA α1 zones; altering `SecondaryTabbedHeadersContractTest` navigation chrome. Complexity verified at **2 SP**.

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-403` (*Physiology & Training Models*), `REQ-UI-525.1` (*Morning HRV Check Cutoff & Start Screen Toggle*), and `REQ-UI-463.2` (*Expert Settings Thematic Grouping*) in `docs/requirements/req_ui_design_system.md`.
* **Historical Origin & Commit Trace**: `ATT-7532` (`647ef674`) & `ATT-7740` (`6267d2c0`) placed morning check preferences in `PhysiologyModelsSection.kt` to reuse existing accordion boilerplate (`REQ-UI-525.1`).
* **Root Reason for Existing Formulation**: Incremental additions avoided creating a new `TuningSection` enum entry and 9-locale section title during initial Morning HRV Check delivery.
* **Preservation of Core Invariants**: All `TuningConfig` DataStore keys, defaults (`08:00`, `6..23`, `3.0%..5.0%`), `ControlTrackingViewModel` gating, and `TrackingViewModelHrvTest` behavior remain 100% untouched.

## 5. Change-Request Impact Analysis (SUP.10 / `REQ-PRO-064`)
* **Affected Requirements**: Refine `REQ-UI-525.1` & `REQ-UI-403.2` / `REQ-UI-463.2` (or define `REQ-UI-531`) in `docs/requirements/req_ui_design_system.md`.
* **Affected Architecture Elements**: `AdvancedTuningAccordion.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `PhysiologyModelsSection.kt`, `SensorsGpsFilterSection.kt`, new `MorningCheckSection.kt`, `res/values*/strings_hrv.xml` (9 locales).
* **Verification Impact & Test Surface**: `AdvancedTuningAccordionTest`, `AdvancedTuningModularityTest`, `AdvancedTuningScreenTest`, `AdvancedTuningVisualContractTest`, `PhysiologyModelsVisibilityContractTest`, `TuningDesignGuidelinesContractTest`, `TranslationParityTest`, `SecondaryTabbedHeadersContractTest`, plus Stage 4 `Rule 8.5` screenshot test.
* **Risk Assessment & Invariants**: Pure UI/presentation relocation with zero DataStore schema changes; preserving default parameter values on section composables avoids breaking previews or tests.

## 6. System Invariants & Risk Assessment
* **Core Invariants**: DataStore keys/defaults unchanged; `SecondaryTabbedHeadersContractTest` navigation bars untouched; `TuningDesignGuidelinesContractTest` clean; 100% 9-language parity with zero emojis (`ATT-5400`).
* **Risk Rating**: **MEDIUM** (`2 SP`, Standard) — Multi-file Compose UI restructuring + 9-locale strings + contract test updates + `Rule 8.5` PNG verification; zero database or service risk.
