# Stage 2: Requirement & Test Specification - ATT-7539: Standardize Category Segmented Rows & Contract Test

**Ticket**: [ATT-7539](https://rainerblind.atlassian.net/browse/ATT-7539)  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Requirement Mapping**: `REQ-UI-262.5`, `REQ-CLM-010.3`  
**Test Spec ID**: `TST-UI-221.6`, `TST-CLM-010.6` | **Branch**: `feature/ATT-7539`  
**Author**: AI Agent 1 (Flash, `agent1`) | **Date**: 2026-11-13  

---

## 1. Requirement Specification (REQ-UI-262.5, REQ-CLM-010.3)

### 1.1 Attributes
- **Priority**: Medium | **Type**: Functional & Non-Functional | **Verification Method**: Unit

### 1.2 Functional & Architectural Requirements
1. `NavigationSection.kt` SHALL render the Climb Categorization Formula selector using `TuningSegmentedButtonRow` (`SingleChoiceSegmentedButtonRow` with `icon = {}`, `bodyMedium` `SemiBold` title, and `bodySmall` `onSurfaceVariant` helper text) for selecting between `ClimbScoringFormula.FIETS` and `ClimbScoringFormula.STRAVA_GARMIN` (`REQ-CLM-010.3`).
2. `AftermathAnalysisSection.kt` SHALL replace both legacy binary `FilterChip` rows (elevation and telemetry X-axis domain: `DISTANCE` vs. `TIME`) with `TuningSegmentedButtonRow` (`REQ-UI-262.5a`).
3. `CockpitTypographySection.kt` SHALL standardize the 3-option font weight selector (`CockpitFontWeight.LIGHT`, `NORMAL`, `BOLD`) to `TuningSegmentedButtonRow` (`REQ-UI-262.5b`).
4. Composables in `ui/settings/tuning/categories/` SHALL NOT use raw `FilterChip(` for binary or discrete choices (allowlisting only `SupporterDebugSection.kt` for developer tier overrides), SHALL NOT use raw unscaled `Switch(`, and SHALL NOT nest `ElevatedCard(` containers (`REQ-UI-262.5c`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC1 (Navigation Climb Formula Selector)**:
  * *Given* the user navigates to *Expert Settings -> Navigation & Routen*,
  * *When* viewing *Anstiegs-Kategorisierungsformel*,
  * *Then* it displays as a unified `TuningSegmentedButtonRow` without checkmark text-shifting (`icon = {}`) matching surrounding form control typography.
* **AC2 (Aftermath & Cockpit Typography Parity)**:
  * *Given* the user navigates to *Aftermath-Analyse* or *Cockpit-Typografie*,
  * *When* selecting X-axis domains or font weights,
  * *Then* each option renders via `TuningSegmentedButtonRow` with 100% functional and localization parity.
* **AC3 (Automated Style Guide Contract Enforcement)**:
  * *Given* all Kotlin source files in `ui/settings/tuning/categories/`,
  * *When* `TuningDesignGuidelinesContractTest` executes,
  * *Then* it asserts zero disallowed `FilterChip(`, zero unscaled `Switch(`, and zero nested `ElevatedCard(`.

### 1.4 Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-UI-262.5` (`ATT-2033`, `ATT-7536`) & `REQ-CLM-010.3` (`ATT-7162`).
* `Historical Origin & Commit Trace`: `ATT-7162` (`3a5d840d`) implemented climb scoring formula with `FilterChip` before `TuningSegmentedButtonRow` existed (`ATT-7536`).
* `Root Reason for Existing Formulation`: Lack of centralized segmented button wrapper in `TuningFormControls.kt` led developers to duplicate legacy chip patterns.
* `Preservation of Core Invariants`: DataStore keys, preference flows, defaults, stateless composable signatures, and 9-language strings are 100% preserved.

---

## 2. Test Specification

### 2.1 Test Cases
1. `TST-CLM-010.6` (Unit): Verify `NavigationSection.kt` climb formula segmented row bindings and parameters.
2. `TST-UI-221.6` (Unit / Architectural Contract — `TuningDesignGuidelinesContractTest.kt`): Scans `ui/settings/tuning/categories/` asserting zero disallowed `FilterChip(`, zero raw `Switch(`, and zero nested `ElevatedCard(`.
3. `TST-UI-221.7` (Localization & Theme Audit): Run `TranslationParityTest` and `ThemeColorCentralizationAuditTest`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CLM-010.6` | Unit | `NavigationSection.kt`, `AdvancedTuningVisualContractTest.kt` | `REQ-CLM-010.3` | Specified |
| `TST-UI-221.6` | Architecture Unit | `TuningDesignGuidelinesContractTest.kt` | `REQ-UI-262.5` | Specified |
| `TST-UI-221.7` | Contract | `TranslationParityTest.kt` | `REQ-UI-262.5` | Specified |
