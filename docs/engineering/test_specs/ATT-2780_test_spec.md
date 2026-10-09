# Stage 2: Requirement & Test Specification - ATT-2780: Relocate sensor search settings from navigation drawer to advanced tuning

**Ticket**: [ATT-2780](https://atrainingtracker.atlassian.net/browse/ATT-2780)  
**Sub-task**: [ATT-2886](https://atrainingtracker.atlassian.net/browse/ATT-2886) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-211](https://atrainingtracker.atlassian.net/browse/ATT-211) (*Reorder MainNavigationDrawer*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.5`  
**Requirement Mapping**: `REQ-UI-314` (*Sensor Search Settings Relocation to Advanced Tuning Accordion & Navigation Drawer Decluttering*)  
**Amending**: `REQ-UI-154` (*Search Settings Modal Bottom Sheet & Navigation Integration*)  
**Test Spec ID**: `TST-UI-274`  
**Branch**: `improvement/ATT-2780`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-UI-314)

### 1.1 Problem Statement & Rationale
Exposing *"Suche"* / *"Search Settings"* (`R.string.Search_Settings`) in the primary navigation drawer creates a false impression of a global entity search, while exposing low-level BLE/ANT+ scanning parameters that athletes rarely alter. Relocating these settings into the dedicated **Advanced Tuning** dialog ([AdvancedTuningDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt)) under *Expert Settings* declutters the navigation drawer and provides a cohesive home for advanced technical preferences.

### 1.2 Functional & Architectural Requirements
The system SHALL eliminate the sensor search entry (`drawer_search_settings`) from the primary navigation drawer, relocate sensor search discovery retry parameters, automatic triggers, and filtering behaviors into `AdvancedTuningDialog` as a dedicated collapsible accordion section (`TuningSection.SENSOR_SEARCH`), and preserve transactional preference persistence (`REQ-UI-314`, amending `REQ-UI-154`):

1. **Primary Navigation Drawer Decluttering (`AppNavigationDrawer.kt`)**:
   - The system SHALL remove `DrawerItemConfig(R.id.drawer_search_settings, ...)` from `createDrawerGroups()` in [AppNavigationDrawer.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt).
   - The Settings group (`R.string.drawer__settings`) SHALL contain strictly 5 items: Units, Display, Tracking Layouts, Backup & Restore, and Privacy Policy.
   - Total navigation drawer destinations across all 5 hubs SHALL be strictly 21.

2. **Dedicated Advanced Tuning Accordion Section (`SensorSearchTuningSection.kt`)**:
   - `TuningSection` enum in [AdvancedTuningAccordion.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordion.kt) SHALL declare `SENSOR_SEARCH`.
   - `AdvancedTuningDialog.kt` SHALL embed a dedicated collapsible accordion section (`TuningAccordionSection`) using title `R.string.tuning_cat_sensor_search` ("Sensorsuche & Verhalten" / "Sensor Search & Triggers"), icon `Icons.Default.Sensors`, and live summary subtitle formatted by `TuningSubtitleFormatter.formatSensorSearchSubtitle(...)`.
   - The section SHALL default to collapsed state on initial dialog composition (`expandedSections` initialized with `emptySet()`).

3. **Sensor Search Configuration Controls**:
   - *Retry Limit Slider*: Slider from 1 to 5 (steps = 3, default: 3) bound to `TrainingApplication.SP_NUMBER_OF_SEARCH_TRIES_INT` with dynamic value badge.
   - *Automated Triggers*: 4 switches bound to `startSearchWhenAppStarts` (default: true), `startSearchWhenResumeFromPaused` (default: true), `startSearchWhenUserChangesSport` (default: true), and `startSearchWhenTrackingStarts` (default: false).
   - *Search Behaviors*: 2 switches bound to `searchOnlyForSportSpecificDevices` (default: true) and `changeSportWhenDeviceGetsLost` (default: true).

4. **Transactional Staging & Defaults Reset**:
   - Adjusting sliders and switches inside `SensorSearchTuningSection` SHALL stage modifications in memory without mutating `SharedPreferences` immediately.
   - Tapping "Speichern" (`AppDialogActions.SaveCancel`) SHALL commit all 7 preferences atomically to `SharedPreferences`.
   - Tapping "Abbrechen" or dismissing the dialog SHALL discard uncommitted modifications.
   - Tapping "Auf Standardwerte zurücksetzen" SHALL reset staged sensor search parameters to default values.

5. **Preservation of Invariants & Backward Compatibility**:
   - All 7 `SharedPreferences` keys and default values MUST remain identical.
   - `SearchSettingsDialogKt` and `SearchSettingsDialogFragment` reflection contracts ([ModalBottomSheetDialogsIntegrityTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/ModalBottomSheetDialogsIntegrityTest.kt)) MUST remain intact.
   - All files in `ui.settings.tuning` MUST remain strictly under 400 lines ([AdvancedTuningModularityTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningModularityTest.kt)).
   - 100% 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

### 1.3 Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: `REQ-UI-154` (*Search Settings Modal Bottom Sheet & Navigation Integration*), targeting `SearchSettingsDialog.kt`, `SearchSettingsDialogFragment.kt`, `AppNavigationDrawer.kt`, `NavRoutes.kt`, and `MainActivityWithNavigation.kt`.
* **Historical Origin & Commit Trace**: Ticket `ATT-1044`, commit `4f38808d` (2026-09-15), modernized sensor search into a modal bottom sheet and linked it to drawer item `R.id.drawer_search_settings`.
* **Root Reason for Existing Formulation**: When legacy `SearchSettingsFragment` was modernized, the drawer entry was kept so users wouldn't lose access. However, everyday users confused "Suche" with global route/workout search, while technical parameters belong in expert settings.
* **Preservation of Core Invariants**: All 7 `SharedPreferences` keys, default values, scanning algorithms in `DeviceManager`, and standalone dialog reflection contracts are strictly preserved.

---

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Drawer Decluttering)**:
  * *Given* the athlete opening the application navigation drawer,
  * *When* inspecting the Settings group,
  * *Then* `Search_Settings` (`R.id.drawer_search_settings`) SHALL NOT be present,
  * *And* the drawer contains exactly 21 items across 5 categories.

* **Criterion 2 (Advanced Tuning Presence)**:
  * *Given* the athlete opening Advanced Tuning (`drawer_advanced_tuning`),
  * *When* inspecting the accordion sections,
  * *Then* `Sensorsuche & Verhalten` (`TuningSection.SENSOR_SEARCH`) SHALL be present with active status summary subtitle,
  * *And* the section is collapsed by default.

* **Criterion 3 (Accordion Interaction & Staging)**:
  * *Given* the athlete expanding `Sensorsuche & Verhalten`,
  * *When* adjusting the search attempts slider or toggling any of the 6 trigger/behavior switches,
  * *Then* changes are held in local state without immediate mutation of `SharedPreferences`.

* **Criterion 4 (Transactional Save & Cancel)**:
  * *Given* staged modifications in `Sensorsuche & Verhalten`,
  * *When* tapping "Abbrechen" or dismissing the dialog,
  * *Then* uncommitted changes SHALL be discarded.
  * *When* tapping "Speichern",
  * *Then* all 7 preference keys SHALL be atomically written to `SharedPreferences`.

* **Criterion 5 (Defaults Reset)**:
  * *Given* modified search parameters in Advanced Tuning,
  * *When* tapping "Auf Standardwerte zurücksetzen",
  * *Then* search tries resets to 3, trigger switches reset to true/true/true/false, and behavior switches reset to true/true.

---

### 1.5 System Invariants
1. `TrainingApplication.SP_NUMBER_OF_SEARCH_TRIES_INT`, `startSearchWhenAppStarts`, `startSearchWhenResumeFromPaused`, `startSearchWhenUserChangesSport`, `startSearchWhenTrackingStarts`, `searchOnlyForSportSpecificDevices`, and `changeSportWhenDeviceGetsLost` keys and getter return contracts MUST NOT be altered.
2. `ModalBottomSheetDialogsIntegrityTest` reflection test for `SearchSettingsDialogKt` and `SearchSettingsDialogFragment` MUST continue to pass.
3. Every source file in `com.atrainingtracker.trainingtracker.ui.settings.tuning` MUST remain strictly under 400 lines of code.
4. Clean-room test suite pass rate MUST remain 100% across all project modules.

---

## 2. Test Specification (TST-UI-274)

### Test Case 1: Navigation Drawer State & Item Count Contract (`TST-UI-274.1`)
* **Scope**: Unit Test
* **Target File**: [NavigationDrawerStateTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/NavigationDrawerStateTest.kt)
* **Preconditions**: `NavigationDrawerController` and `createDrawerGroups()` instantiated.
* **Action**: Verify `expectedCategories.size == 5` and `expectedItems.size == 21`.
* **Expected Result**:
  * `drawer__settings` group contains exactly 5 items (Units, Display, Tracking Layouts, Backup & Restore, Privacy Policy).
  * `R.id.drawer_search_settings` is absent from all drawer groups.
  * `NavRoutes.fromDrawerItemId(R.id.drawer_search_settings)` resolves to null.

### Test Case 2: Advanced Tuning Modularity & Structural Constraints (`TST-UI-274.2`)
* **Scope**: Structural Contract Test
* **Target File**: [AdvancedTuningModularityTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningModularityTest.kt)
* **Preconditions**: Tuning package source directory scanned.
* **Action**: Execute `testModularFilesExist` and `testAllTuningSourceFilesUnder400Lines`.
* **Expected Result**:
  * `categories/SensorSearchTuningSection.kt` exists.
  * All 12 files in the tuning directory have `lineCount < 400`.

### Test Case 3: Advanced Tuning Visual & Section Expansion Contract (`TST-UI-274.3`)
* **Scope**: Contract Test
* **Target File**: [AdvancedTuningVisualContractTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningVisualContractTest.kt)
* **Preconditions**: `AdvancedTuningDialog.kt` inspected.
* **Action**: Verify `TuningSection.SENSOR_SEARCH` integration, initial collapse state, and subtitle formatter invocation.
* **Expected Result**:
  * `TuningSection.SENSOR_SEARCH` is referenced in `AdvancedTuningDialog.kt`.
  * `expandedSections` initializes with `emptySet()`.
  * `TuningSubtitleFormatter.formatSensorSearchSubtitle` generates correct summary strings.

### Test Case 4: 9-Language Localization & Specifier Audit (`TST-UI-274.4`)
* **Scope**: Localization Parity Test
* **Target File**: [TranslationParityTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/translations/TranslationParityTest.kt)
* **Preconditions**: `strings.xml` in `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
* **Action**: Audit newly introduced keys: `tuning_cat_sensor_search`, `tuning_sensor_search_subtitle`, etc.
* **Expected Result**: 100% parity, zero missing entries, matching `%d` and `%s` tokens across all 9 locales.

### Test Case 5: Clean-Room Full Suite Regression (`TST-UI-274.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% pass rate across the full test suite with 0 failures and 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-274.1` | Unit | `NavigationDrawerStateTest.kt`, `AppNavigationDrawer.kt` | `REQ-UI-314` (Clause 1) | Specified |
| `TST-UI-274.2` | Contract | `AdvancedTuningModularityTest.kt`, `SensorSearchTuningSection.kt` | `REQ-UI-314` (Clause 2, 5) | Specified |
| `TST-UI-274.3` | Contract | `AdvancedTuningVisualContractTest.kt`, `AdvancedTuningDialog.kt` | `REQ-UI-314` (Clause 2, 3, 4) | Specified |
| `TST-UI-274.4` | Localization | `TranslationParityTest.kt`, `strings.xml` (all 9 locales) | `REQ-UI-314` (Clause 5), `REQ-UI-106` | Specified |
| `TST-UI-274.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001`, `REQ-UI-314` | Specified |
