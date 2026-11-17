# Stage 1 Analysis: ATT-8468 - PhysiologyModelsSection: DFA α1 Info-Card aus Experten-Einstellungen entfernen

**Ticket**: [ATT-8468](https://rainerblind.atlassian.net/browse/ATT-8468)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-16_03_Nacht`  
**Branch**: `feature/ATT-8468`  
**Author**: AI Agent 1 (Implementer, `Model: "flash"`, `--as agent1`)  
**Date**: 2026-11-17  

---

## 1. Problem Statement & Motivation
During PO review of ATT-8117 on Pixel 10 (`att8117_pixel10.png`), a redundant informational element was identified in the *Physiology & Training Models* accordion section of Expert Settings (`AdvancedTuningScreen` / `AdvancedTuningDialog`). Situated between Model 5 (*Cardio-Power Dynamics*) and Model 6 (*RPE & Feel*), a static blue Info-Card displays `R.string.tuning_physiology_hrv_zones_link` (*"DFA α1 aerobe und anaerobe Schwellen in den Trainingszonen anpassen"*).

Because this card does not control any setting, duplicates thematic context properly housed under *Profil & Zonen -> Trainingszonen*, and clutters the settings hierarchy, it must be removed along with redundant divider framing and dead string resources across all 9 locales.

---

## 2. Root Cause Analysis & Independent Peer Review & Verification
- **Mechanism**: `PhysiologyModelsSection.kt:214-243` renders a static `Card` with `Icons.Default.Info` and `R.string.tuning_physiology_hrv_zones_link`, enclosed by two consecutive `HorizontalDivider` calls (`L212` and `L245`).
- **Independent Peer Review & Verification**:
  - Code inspection confirms `Card` and `CardDefaults` imports (`L30-31`) are solely utilized by this info-card; removing the card makes them unused imports.
  - Redundant dividers: Removing the card without removing one divider leaves two consecutive dividers between Cardio-Power Dynamics and RPE & Feel. One divider must be removed.
  - Cross-resource verification: `tuning_physiology_hrv_zones_link` exists in all 9 `strings_hrv.xml` files (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`). It has zero other call sites across the entire application codebase and will become completely dead.
  - Test audit: `PhysiologyModelsVisibilityContractTest.kt:362-365` contains a strict contract assertion `assertTrue(content.contains("R.string.tuning_physiology_hrv_zones_link"))` that must be inverted to `assertFalse`.
  - Visual screenshot: `AdvancedTuningSectionsScreenshotTest.kt` renders `PhysiologyModelsSection` and generates `docs/engineering/walkthroughs/img/ATT-8117_tuning_sections.png` (Light & Dark), which will reflect the cleaned layout.

---

## 3. User Scope Grounding (ATT-1250)
- **In-Scope Goals**:
  1. Remove the DFA α1 info-card (`Card` block) and one redundant `HorizontalDivider` from `PhysiologyModelsSection.kt`.
  2. Remove unused imports (`androidx.compose.material3.Card`, `CardDefaults`) from `PhysiologyModelsSection.kt`.
  3. Remove dead string `tuning_physiology_hrv_zones_link` across all 9 `strings_hrv.xml` files with 100% translation parity.
  4. Invert contract assertion in `PhysiologyModelsVisibilityContractTest.kt` (`assertFalse`).
  5. Update living documentation (`req_settings_profiles.md`, `tst_settings_profiles.md`) and regenerate Compose screenshot via `AdvancedTuningSectionsScreenshotTest`.
- **Out-of-Scope Non-Goals (Scope Bounding)**:
  - No changes to Training Zones 4th tab (`HrvDfaA1SettingsContent` in `ZonesSettingsActivity.kt`).
  - No modifications to DataStore keys, values, or default physiological thresholds.
  - No changes to any other accordion section or tuning preferences.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
- **Original Requirement ID & Target**: `REQ-SET-113.1` (refining `REQ-TRK-061.4` and `REQ-UI-403`) in `docs/requirements/req_settings_profiles.md`.
- **Historical Origin & Commit Trace**: Introduced as a transitional notice in Sprint 2026-46.4 via `ATT-7317` (`fce6777b`) when DFA α1 sliders were extracted to Training Zones; preserved during reorganization in `ATT-8117` (`f628f0a6`).
- **Root Reason for Existing Formulation**: Served as an interim navigational signpost to guide legacy users seeking threshold sliders in Advanced Settings toward the new Training Zones tab.
- **Preservation of Core Invariants**: The Training Zones hub is now fully established as the primary location for DFA α1 threshold customization (`REQ-TRK-061.4`). Removing the interim signpost cleans up the UI without altering physiological models, threshold persistence, or zone calculations.

---

## 5. Change-Request Impact Analysis (SUP.10)
- **Affected Requirements**: `REQ-SET-113.1` (amended: remove legacy info-card), `TST-SET-115.1` (amended: contract test inverts assertion).
- **Affected Architecture Elements**: `PhysiologyModelsSection.kt`, 9 `strings_hrv.xml` resource files.
- **Verification Impact & Test Surface**: `PhysiologyModelsVisibilityContractTest`, `AdvancedTuningSectionsScreenshotTest`, `TranslationParityTest`.
- **Risk Assessment & Invariants**: LOW risk. Localized Compose UI subtraction; zero DataStore or business logic changes.

---

## 6. Architectural Strategy & High-Level Solution
1. In `PhysiologyModelsSection.kt`, delete `L214-245` (info-card and trailing divider), leaving a single `HorizontalDivider` between Cardio-Power Dynamics and RPE & Feel. Remove unused `Card` and `CardDefaults` imports.
2. Remove `<string name="tuning_physiology_hrv_zones_link">...</string>` across all 9 `strings_hrv.xml` files.
3. Update `PhysiologyModelsVisibilityContractTest.kt` to assert `assertFalse(content.contains("R.string.tuning_physiology_hrv_zones_link"))`.
4. Run `AdvancedTuningSectionsScreenshotTest` to capture updated Compose rendering and verify visual harmony.

---

## 7. System Invariants & Risk Assessment
- **Core Invariants**:
  1. All 5 physiological models and RPE & Feel retain identical DataStore bindings and layout order.
  2. Exactly one `HorizontalDivider` separates each item in `PhysiologyModelsSection`.
  3. 100% translation parity across all 9 supported locales (`TranslationParityTest`).
- **Complexity & Story Points**: **1 SP** (Trivial/Standard localized Compose cleanup; Risk: **LOW**).
