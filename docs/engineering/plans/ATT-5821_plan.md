# Stage 3: Implementation Plan - ATT-5821: EditSportTypeDialog Progressive Disclosure

**Ticket**: [ATT-5821](https://rainerblind.atlassian.net/browse/ATT-5821)  
**Sub-task**: [ATT-5898](https://rainerblind.atlassian.net/browse/ATT-5898) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-UI-365` (`REQ-UI-365.4`)  
**Test Mapping**: `TST-UI-328` (`TST-UI-328.6`)  
**Branch**: `feature/ATT-5821`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-07  

---

## 1. Problem Description & Scope
In `EditSportTypeDialog.kt`, Min/Max speed thresholds (lacking explanation that they govern automatic sport type detection) and niche TCX/GC export dropdowns are displayed openly among everyday settings. Following Design Guidelines Section 3 (*Settings & Accordion Structure*), everyday fields (Name, Base Sport, Equipment, Strava) SHALL be placed upfront, while speed thresholds (with detection hint) and TCX/GC mappings SHALL be encapsulated in an initially collapsed "Erweiterte Optionen" accordion.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-365.4` (Progressive Disclosure & Advanced Options in Sport Type Editor)
* **Test Mapping**: `TST-UI-328.6` (`TST-UI-328.6a` Contract, `TST-UI-328.6b` Parity, `TST-UI-328.6c` Tier 1 Regression)

---

## 3. System Invariants & Preserved Behavior
1. **Data Model & Save Integrity**: `SportTypeItem` values, SQLite database updates via `SportTypeDatabaseManager`, and decimal sanitization remain identical.
2. **Speed Conversion Precision**: Symmetrical conversions via `MyHelper.mps2userUnit` and `UserUnit2mps` are strictly preserved.
3. **9-Language Parity & Theme Centralization**: 100% translation parity with indexed positional specifiers (`%1$s`), zero raw hardcoded colors.

---

## 4. Proposed Architectural Changes

### Component 1: `EditSportTypeDialog.kt` Reordering & Encapsulation
* Position primary fields upfront: Name, Base Sport Type, Linked Equipment (`MultiSelectEquipmentSpinner`), and Strava Sport Mapping (`SportTypeDropdown`).
* Introduce `var isAdvancedExpanded by rememberSaveable { mutableStateOf(false) }`.
* Encapsulate Min/Max speed fields (preceded by `sport_type_speed_detection_hint`) and TCX/GoldenCheetah export dropdowns inside `TuningAccordionSection` with icon `Icons.Default.Tune` titled `sport_type_advanced_options`.

### Component 2: Localization (`strings.xml` x 9)
* Add `sport_type_advanced_options`, `sport_type_advanced_options_desc`, and `sport_type_speed_detection_hint` across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### UI Consistency (Rule 23)
* **Reference Component**: `TuningAccordionSection` in `AutoPauseTabsScreen.kt` and `AdvancedTuningScreen.kt`.
* **Reused Components**: `AppModalBottomSheet`, `AppDialogActions.SaveCancel`, `TuningAccordionSection`, `OutlinedTextField`.
* **Theme Tokens**: `RoundedCornerShape(12.dp)`, `surfaceVariant` (alpha 0.35f), `outlineVariant` (alpha 0.5f).
* **New One-off Styles**: None (100% canonical design system reuse).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Strings & 9-Language Localization Parity
* Files: `app/src/main/res/values*/strings.xml` (all 9 locales)
* Changes: Add `sport_type_advanced_options`, `sport_type_advanced_options_desc`, and `sport_type_speed_detection_hint`.

### Step 2: Dialog Layout Reordering & Accordion Encapsulation
* Files: `app/src/main/java/com/atrainingtracker/banalservice/ui/sporttype/EditSportTypeDialog.kt`
* Changes: Move Equipment and Strava to primary column; wrap speed hint, speed inputs, TCX, and GoldenCheetah dropdowns in `TuningAccordionSection`.

### Step 3: Contract Test & Tier 1 Test Suite
* Files: `app/src/test/java/com/atrainingtracker/banalservice/ui/sporttype/EditSportTypeDialogContractTest.kt`
* Changes: Assert primary field ordering, default collapsed state (`isAdvancedExpanded = false`), and encapsulation of speed/export fields.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.sporttype.*" --tests "com.atrainingtracker.trainingtracker.ui.sporttypes.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Tier 1 targeted tests pass with 100% success; Code Lock checked via `check-gate`.
* **Rollback**: Clean git working tree revert on `feature/ATT-5821` if any regression occurs.
