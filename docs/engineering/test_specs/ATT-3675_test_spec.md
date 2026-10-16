# Stage 2: Requirement & Test Specification - ATT-3675: Replace overflowing text buttons in ZonesSettingsActivity header with compact icon actions to prevent vertical screen blowout

**Ticket**: [ATT-3675](https://rainerblind.atlassian.net/browse/ATT-3675)  
**Sub-task**: [ATT-3681](https://rainerblind.atlassian.net/browse/ATT-3681) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Requirement Mapping**: `REQ-UI-378` (*Compact Action Buttons and Header Height Containment in ZonesSettingsActivity*)  
**Test Spec ID**: `TST-UI-341`  
**Branch**: `feature/ATT-3675`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Requirement Specification (REQ-UI-378)

### 1.1 Problem Statement & Rationale
During Sprint 2026-42.4 review on Google Pixel 10 (Android 16 / SDK 37), opening `ZonesSettingsActivity` rendered a giant header consuming ~95% of the physical screen height (~800dp). This occurred because four unconstrained elements were packed side-by-side into a single horizontal `Row` (`"Trainings Zonen"` headline, `"Berechnet"` chip, `"Zonen berechnen"` button, and `"Auf Standardwerte zurücksetzen"` button). The fourth item wrapped 1 character per line over ~30 lines, blowing out the screen and pushing the zone tabs and boundary editing cards behind the navigation bar.

Adhering to `docs/design_guidelines.md` Section 5.6 (*Compact Icon Actions in Top Headers*), top screen headers (`Row`) must never pack multiple multi-word `TextButton`s alongside a title and status chip; instead, compact `IconButton` symbol actions (with accessible `contentDescription` tooltips) and bounded title containers must be used.

### 1.2 Functional & Architectural Requirements
The system SHALL ensure header compactness and prevent vertical layout expansion in `ZonesSettingsActivity.kt` (ATT-3675, amending `REQ-UI-370` and `REQ-UI-373`, adhering to `docs/design_guidelines.md` Section 5.6):
1. *Compact Symbol Icon Actions (`REQ-UI-378.1`)*:
   - In `ZonesSettingsActivity.kt`, the two horizontal `TextButton` components ("Zonen berechnen" and "Auf Standardwerte zurücksetzen") SHALL be replaced with compact Material 3 `IconButton` components:
     - **Calculate Zones Action**: `IconButton(onClick = { showCalculatorDialog = true })` hosting `Icon(imageVector = Icons.Default.Calculate, contentDescription = stringResource(R.string.zone_calc_button), tint = MaterialTheme.colorScheme.onPrimaryContainer)`.
     - **Reset to Defaults Action**: `IconButton(onClick = { scope.launch(Dispatchers.IO) { dataStore.resetToDefaults(currentType); reloadProfiles() } })` hosting `Icon(imageVector = Icons.Default.RestartAlt, contentDescription = stringResource(R.string.reset_to_defaults), tint = MaterialTheme.colorScheme.onPrimaryContainer)`.
2. *Bounded Title Container & Ellipsis Guard (`REQ-UI-378.2`)*:
   - The left-side title container holding `title_edit_zones` and the calculation status `SuggestionChip` SHALL apply `Modifier.weight(1f, fill = false)`.
   - The title `Text` component SHALL declare `maxLines = 1` and `overflow = TextOverflow.Ellipsis`, ensuring the title never forces wrapping into multi-line character columns or starves the right-side action buttons.
3. *Header Height Containment Invariant (`REQ-UI-378.3`)*:
   - The total measured height of the header row in `ZonesSettingsActivity.kt` SHALL remain strictly $\le 64\text{ dp}$ across all standard mobile viewports ($360\text{ dp} \dots 600\text{ dp}$) and across all 9 application locales.
4. *Accessibility Parity (`REQ-UI-378.4`)*:
   - Both action buttons SHALL maintain a minimum $48\text{ dp} \times 48\text{ dp}$ interactive touch target with localized `contentDescription` matching `R.string.zone_calc_button` and `R.string.reset_to_defaults`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1: Compact Header Height on Localized German**:
  * *Given* `ZonesSettingsActivity` opened on a Google Pixel 10 (or standard 412dp viewport) with German locale (`Locale.GERMANY`),
  * *When* the top header renders,
  * *Then* the header renders on a single compact row with height $\le 64\text{ dp}$, leaving the tab row (`"Lauf HF"`, `"Rad HF"`, `"Rad Leistung"`) and boundary cards fully visible above the fold.
* **Criterion 2: Icon Action Dispatch**:
  * *Given* `ZonesSettingsActivity` displayed,
  * *When* the athlete taps the Calculate icon button (`Icons.Default.Calculate`),
  * *Then* `showCalculatorDialog` is set to `true` and `ZoneCalculatorDialog` opens.
  * *When* the athlete taps the Reset icon button (`Icons.Default.RestartAlt`),
  * *Then* `dataStore.resetToDefaults(currentType)` is dispatched and zone profiles reload to factory defaults.
* **Criterion 3: Source Code & AST Invariants**:
  * *Given* `ZonesSettingsActivity.kt`,
  * *When* inspecting the header row composition,
  * *Then* zero `TextButton` invocations exist in the header row, `IconButton` is used with `Icons.Default.Calculate` and `Icons.Default.RestartAlt`, and the title `Text` specifies `maxLines = 1` with `overflow = TextOverflow.Ellipsis`.

### 1.4 System Invariants
* Zero regression in zone calculation dialog or reset-to-defaults functionality.
* Full WCAG 2.1 AA accessibility parity with localized `contentDescription`.
* 100% clean-room test suite pass rate.

---

## 2. Test Specification (TST-UI-341)

### Test Case 1: Header Source Contract & Architectural Lint Test (`TST-UI-341.1`)
* **Scope**: Architectural Unit Test / Source AST Inspection
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/ZonesSettingsHeaderContractTest.kt`
* **Preconditions**: `ZonesSettingsActivity.kt` source file accessible on disk.
* **Action**:
  1. Inspect the header composable within `ZonesSettingsActivity.kt`.
  2. Assert that `Icons.Default.Calculate` and `Icons.Default.RestartAlt` are present.
  3. Assert that zero `TextButton` invocations exist within the header block.
  4. Assert that `TextOverflow.Ellipsis` and `maxLines = 1` are applied to the title text.
* **Expected Result**: All assertions pass.

### Test Case 2: 9-Language ContentDescription Localization Parity (`TST-UI-341.2`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/ZoneCalculatorLocalizationTest.kt`
* **Action**: Verify `zone_calc_button` and `reset_to_defaults` string keys exist with non-empty translations across all 9 locales:
  * `values/strings.xml` (EN)
  * `values-de/strings.xml` (DE)
  * `values-es/strings.xml` (ES)
  * `values-fr/strings.xml` (FR)
  * `values-it/strings.xml` (IT)
  * `values-ja/strings.xml` (JA)
  * `values-nl/strings.xml` (NL)
  * `values-pl/strings.xml` (PL)
  * `values-pt/strings.xml` (PT)
* **Expected Result**: 100% translation parity.

### Test Case 3: Clean-Room Full Suite Regression (`TST-UI-341.3`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across all unit and contract tests in the repository.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-341.1]` | Contract Test | `ZonesSettingsHeaderContractTest` | `[REQ-UI-378.1]`, `[REQ-UI-378.2]`, `[REQ-UI-378.3]` | Specified |
| `[TST-UI-341.2]` | Localization | `ZoneCalculatorLocalizationTest` | `[REQ-UI-378.4]`, `REQ-UI-106` | Specified |
| `[TST-UI-341.3]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
