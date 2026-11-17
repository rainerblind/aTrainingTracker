# Stage 2: Requirement & Test Specification - ATT-8468: Remove DFA α1 Info-Card from PhysiologyModelsSection

**Ticket**: [ATT-8468](https://rainerblind.atlassian.net/browse/ATT-8468)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht`  
**Requirement Mapping**: `REQ-SET-113` (`REQ-SET-113.1`, `REQ-SET-113.3`, `REQ-SET-113.4`)  
**Test Spec ID**: `TST-SET-115` (`TST-SET-115.1`, `TST-SET-115.4`, `TST-SET-115.5`)  
**Branch**: `feature/ATT-8468` | **Author**: AI Agent 1 (`Model: "flash"`, `--as agent1`) | **Date**: 2026-11-17  

---

## 1. Requirement Specification (`REQ-SET-113.1` [Amended] ATT-8468)
- **Attributes**: Priority: `Medium` | Type: `Functional` | Verification Method: `Unit / Compose Screenshot` | Status: `[Verified]`
- **REQ-SET-113.1 (HRV Artifact Gate Relocation & DFA α1 Info-Card Removal — [Amended] ATT-8468)**: `PhysiologyModelsSection.kt` SHALL omit the static DFA α1 info-card (`R.string.tuning_physiology_hrv_zones_link`), redundant trailing `HorizontalDivider`, and unused `Card`/`CardDefaults` imports (`L214-245`), leaving a single `HorizontalDivider` separating Cardio-Power Dynamics and RPE & Feel. The unreferenced string resource `tuning_physiology_hrv_zones_link` SHALL be symmetrically removed across all 9 `strings_hrv.xml` locale files (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`), maintaining 100% 9-language translation parity.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-SET-113.1` (refining `REQ-TRK-061.4` and `REQ-UI-403`) in `docs/requirements/req_settings_profiles.md`. Targets `PhysiologyModelsSection.kt`, `PhysiologyModelsVisibilityContractTest.kt`, and 9 `strings_hrv.xml` locale files.
* **Historical Origin & Commit Trace**: Introduced as a transitional notice in Sprint 2026-46.4 via `ATT-7317` (`fce6777b`) when DFA α1 thresholds moved to Training Zones; preserved during reorganization in `ATT-8117` (`f628f0a6`).
* **Root Reason for Existing Formulation**: Served as an interim navigational breadcrumb to guide legacy users seeking threshold sliders in Advanced Settings toward the new Training Zones tab.
* **Preservation of Core Invariants**: The Training Zones hub is now fully established as the primary location for DFA α1 threshold customization (`REQ-TRK-061.4`). Removing the interim signpost cleans up the UI without altering physiological models, threshold persistence, or zone calculations.

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Card & Divider Removal)**: *Given* `PhysiologyModelsSection`, *When* rendered in Expert Settings, *Then* the DFA α1 card is absent and exactly one `HorizontalDivider` separates Cardio-Power Dynamics from RPE & Feel.
* **Criterion 2 (Visibility Contract Inversion)**: *Given* `PhysiologyModelsVisibilityContractTest`, *When* scanning `PhysiologyModelsSection.kt`, *Then* it asserts `assertFalse(content.contains("R.string.tuning_physiology_hrv_zones_link"))`.
* **Criterion 3 (9-Locale Translation Parity)**: *Given* all 9 `strings_hrv.xml` files, *When* `TranslationParityTest` runs, *Then* `tuning_physiology_hrv_zones_link` is absent from all 9 files and parity is 100%.
* **Criterion 4 (Visual Screenshot Inspection)**: *Given* `AdvancedTuningSectionsScreenshotTest`, *When* executed via Robolectric, *Then* `ATT-8117_tuning_sections.png` is generated displaying the clean Light/Dark layout and certified via `read_file`.

---

## 4. Test Specification (`TST-SET-115.1`, `TST-SET-115.4`, `TST-SET-115.5`)
* **TST-SET-115.1 (DFA α1 Card Removal & Inverted Contract Test)**: In `PhysiologyModelsVisibilityContractTest.kt`, verify `PhysiologyModelsSection.kt` content does not contain `R.string.tuning_physiology_hrv_zones_link` (`assertFalse`) and dead `Card`/`CardDefaults` imports are absent.
* **TST-SET-115.4 (9-Language Localization Parity Test)**: Run `TranslationParityTest` verifying all 9 locales (`values*/strings_hrv.xml`) after symmetric removal of `tuning_physiology_hrv_zones_link` with 100% parity and zero missing keys or format mismatches.
* **TST-SET-115.5 (Visual Compose Screenshot Verification Test, Rule 8.5)**: In `AdvancedTuningSectionsScreenshotTest.kt`, capture `docs/engineering/walkthroughs/img/ATT-8117_tuning_sections.png` (Light & Dark rows) and inspect via `read_file` confirming single divider and absence of info-card.

---

## 5. Traceability Matrix

| Test Case | Scope | Method / Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-115.1` | Contract | `PhysiologyModelsVisibilityContractTest` | `REQ-SET-113.1` | Specified |
| `TST-SET-115.4` | Localization | `TranslationParityTest` (`strings_hrv.xml`) | `REQ-SET-113.3` | Specified |
| `TST-SET-115.5` | Screenshot | `AdvancedTuningSectionsScreenshotTest` | `REQ-SET-113.4` | Specified |
