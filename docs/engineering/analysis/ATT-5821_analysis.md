# Stage 1 Analysis: ATT-5821 - Sportart bearbeiten (EditSportTypeDialog): Min/Max-Geschwindigkeit und TCX/GC-Mappings unter 'Erweiterte Optionen' einklappen

**Ticket**: [ATT-5821](https://rainerblind.atlassian.net/browse/ATT-5821)  
**Sub-task**: [ATT-5896](https://rainerblind.atlassian.net/browse/ATT-5896) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Branch**: `feature/ATT-5821`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-07  

---

## 1. Problem Statement & Motivation
In `EditSportTypeDialog.kt`, minimum and maximum speed thresholds as well as legacy TCX and GoldenCheetah export mappings are rendered openly in the main column. The speed fields lack any hint explaining their function (automatic sport type detection during tracking), creating confusion for regular users.  
In alignment with Design Guidelines Section 3 (*Settings & Accordion Structure*) and Section 5 (*Visual Consistency*), the primary surface should present only everyday configuration fields (Name, Base Sport Type, Linked Equipment, and Strava Sport Mapping). Technical speed bounds (with an explanatory detection hint) and niche export mappings (TCX, GoldenCheetah) should be bundled into an initially collapsed "Erweiterte Optionen" (Advanced Options) section at the bottom.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: `EditSportTypeDialog.kt` was assembled historically (`ATT-900`, `ATT-2751`, `ATT-3363`) by stacking all fields vertically inside a single `Column` without progressive disclosure or visual hierarchy.
* **Independent Peer Review & Verification**:
  * Hypothesis Verified: Code inspection of `EditSportTypeDialog.kt:251-338` confirms min/max speed rows are placed between base sport and equipment, while TCX and GoldenCheetah dropdowns are placed openly under an export divider.
  * Explanatory Text Gap: The speed fields currently display only generic labels (`Min (km/h)` and `Max (km/h)`) without explaining that these thresholds govern automatic sport type detection.
  * Accordion Component Reuse: `TuningAccordionSection` from `ui.settings.tuning` (or a styled collapsible card with `Icons.Default.Tune` / `Settings` and animated rotation) provides the established pattern for progressive disclosure.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Move Linked Equipment and Strava Sport Mapping up into the primary top-level dialog form.
  * Encapsulate Min/Max Speed fields (with explanatory hint `sport_type_speed_detection_hint`) and TCX/GoldenCheetah mappings into a default-collapsed accordion section titled `sport_type_advanced_options` ("Erweiterte Optionen").
  * Maintain collapsed default state (`isAdvancedExpanded = false` via `rememberSaveable`).
  * Add 9-language localized strings with 100% parity for advanced options title, subtitle, and speed detection hint.
  * Add architectural contract tests in `EditSportTypeDialogContractTest.kt` verifying section ordering and encapsulation.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No changes to `SportTypeDatabaseManager`, SQLite schema, or sport type auto-detection algorithms in `BANALService`.
  * No removal of TCX or GoldenCheetah export capabilities.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-365` (`REQ-UI-365.4`), refining `REQ-UI-148` in `docs/requirements/req_ui_design_system.md`. Targets `EditSportTypeDialog.kt` and `strings.xml`.
* **Historical Origin & Commit Trace**: `ATT-2751` (Sprint 2026-41.4), `ATT-3363` (Sprint 2026-42.3), and `ATT-5821` (Sprint 2026-45.12).
* **Root Reason for Existing Formulation**: Speed thresholds and export mappings were added linearly as features evolved without grouping or progressive disclosure.
* **Preservation of Core Invariants**: Locale-resilient numeric parsing (`toDoubleOrNull()` with comma/dot sanitization), save validation (`name.isNotBlank()`), and database update integrity are 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. **Reorder Primary Fields**: In `EditSportTypeDialog.kt`, position Name, Base Sport Type, Linked Equipment, and Strava Sport Mapping sequentially in the main `Column`.
2. **Encapsulate Advanced Options**: Introduce `var isAdvancedExpanded by rememberSaveable { mutableStateOf(false) }` and bundle speed detection hint, Min/Max speed row, and TCX/GC dropdowns in a collapsible accordion container.
3. **Localization**: Define `sport_type_advanced_options`, `sport_type_advanced_options_desc`, and `sport_type_speed_detection_hint` across all 9 supported locales.
4. **Contract Verification**: Create `EditSportTypeDialogContractTest.kt` to ensure primary field prominence, default collapsed state, and advanced field encapsulation.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `SportTypeItem` data model, save semantics, and database persistence remain completely intact.
  2. Input sanitization for comma/dot decimals and speed unit conversions (`MyHelper`) remain strictly preserved.
  3. 100% translation parity across all 9 application locales.
* **Risk Rating**: **LOW** (Pure UI layout restructuring and progressive disclosure within existing bottom sheet; zero database or background service logic changes).
