# Stage 2: Requirement & Test Specification - ATT-5200: Profile & Zones (Zonendarstellung): rename zone_display_background from "Heller Hintergrund" / "Light background" to "Eingefärbter Hintergrund" / "Tinted background" across all 9 locales

**Ticket**: [ATT-5200](https://rainerblind.atlassian.net/browse/ATT-5200)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-UI-172` (*Configurable Zone Visualization Options and Theme-Aware Live Preview*)  
**Test Spec ID**: `TST-UI-124`  
**Branch**: `feature/ATT-5200`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-UI-172)

### 1.1 Problem Statement & Rationale
In `ZonesSettingsActivity.kt`, the "Zonendarstellung" section provides 4 toggles (`showBackground`, `showLeftBar`, `showRightBar`, `showTextColor`). The toggle for `showBackground` uses `R.string.zone_display_background` ("Light background" / "Heller Hintergrund"). This wording is misleading in Dark Mode and on AMOLED screens, where athletes interpret "Light background" as forcing a light/daylight surface rather than a 12% alpha zone color tint.

### 1.2 Functional & Architectural Requirements
1. **Accurate Terminology across 9 Locales (`REQ-UI-172`)**: String resource `zone_display_background` SHALL be renamed across all 9 supported locales to convey zone color tinting:
   - `values` (EN): `"Tinted background"`
   - `values-de` (DE): `"Eingefärbter Hintergrund"`
   - `values-es` (ES): `"Fondo tintado"`
   - `values-fr` (FR): `"Arrière-plan teinté"`
   - `values-it` (IT): `"Sfondo tinto"`
   - `values-ja` (JA): `"色付き背景"`
   - `values-nl` (NL): `"Getinte achtergrond"`
   - `values-pl` (PL): `"Zabarwione tło"`
   - `values-pt` (PT): `"Fundo matizado"`
2. **Identifier & Storage Invariance**: The resource key `R.string.zone_display_background`, backing property `showBackground: Boolean`, default `true`, and DataStore preferences keys SHALL NOT be altered.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-172` (*Configurable Zone Visualization Options and Theme-Aware Live Preview*, `ATT-1265`). Targets `app/src/main/res/values*/strings.xml`, `ZoneDisplayLocalizationTest.kt`, and `docs/requirements/req_ui_design_system.md`.
* **Historical Origin & Commit Trace**: `ATT-1265` (commit `a48c8a38`, Sprint 2026-40.5).
* **Root Reason for Existing Formulation**: "Light background" originally denoted "lightly tinted" (12% alpha tint), but caused semantic ambiguity in Dark/AMOLED modes where athletes interpreted it as forcing a white/daylight surface.
* **Preservation of Core Invariants**: String identifier `R.string.zone_display_background`, backing property `showBackground: Boolean`, default `true`, DataStore schema, and 9-language translation parity are 100% preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Localization Parity)**: *Given* an athlete viewing the "Zonendarstellung" section in any of the 9 supported locales, *When* reading the first toggle label, *Then* it SHALL display "Tinted background" (EN) / "Eingefärbter Hintergrund" (DE) / "Fondo tintado" (ES) / "Arrière-plan teinté" (FR) / "Sfondo tinto" (IT) / "色付き背景" (JA) / "Getinte achtergrond" (NL) / "Zabarwione tło" (PL) / "Fundo matizado" (PT).
* **Criterion 2 (Behavioral Invariance)**: *Given* an athlete toggling the background option, *When* inspecting the live preview tile or cockpit telemetry, *Then* the 12% alpha zone color tinting logic and AMOLED surface rendering SHALL behave identically.

---

## 2. Test Specification (TST-UI-124)

* **TST-UI-124.1 (Localization Parity & Value Audit)**: Unit test in `ZoneDisplayLocalizationTest.kt` asserting that `zone_display_background` exists and matches the exact expected translations across all 9 locales.
* **TST-UI-124.2 (TranslationParityTest)**: Global localization test verifying 0 missing keys and 0 format specifier mismatches across the repository.
* **TST-UI-124.3 (Tier 1 Regression & Governance)**: Run targeted test suite and verify requirement governance (`verify_requirement_governance.py`).

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-124.1]` | Unit | `ZoneDisplayLocalizationTest` | `REQ-UI-172` | Specified |
| `[TST-UI-124.2]` | Localization | `TranslationParityTest` | `REQ-UI-106`, `REQ-UI-172` | Specified |
| `[TST-UI-124.3]` | Regression | Tier 1 Unit Tests | `REQ-PRO-001` | Specified |
