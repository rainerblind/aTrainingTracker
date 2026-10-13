# Stage 2: Requirement & Test Specification - ATT-3344: Migrate Advanced Settings to full-screen screen and optimize category structure

**Ticket**: [ATT-3344](https://rainerblind.atlassian.net/browse/ATT-3344)  
**Sub-task**: [ATT-3428](https://rainerblind.atlassian.net/browse/ATT-3428) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-357` (*Dedicated Full-Screen Advanced Settings Screen Architecture, Zero-Dismissal Scrolling, and Lifecycle-Driven Category Reorganization*)  
**Test Spec ID**: `TST-UI-317`  
**Branch**: `feature/ATT-3344`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Amends and modernizes `REQ-SET-073` (*Dedicated Advanced Tuning Preferences Screen with Parameter Clamping and Reset-to-Defaults*, `ATT-1304`), `REQ-SET-074` (*Expert Settings Drawer Positioning*, `ATT-1646`), and `REQ-UI-222` (*Accordion Architecture for Advanced Settings*, `ATT-1821`, `ATT-1957`), targeting `ui/settings/tuning/AdvancedTuningDialog.kt`, `ui/settings/tuning/AdvancedTuningAccordion.kt`, `ui/navigation/NavRoutes.kt`, and `ui/navigation/ATrainingTrackerApp.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-40.4 (`ATT-1304`), Sprint 2026-40.8 (`ATT-1821`), Sprint 2026-40.9 (`ATT-1957`), and Sprint 2026-40.10 (`ATT-2033`).
3. **Root Reason for Existing Formulation**:
   - Advanced Tuning was embedded in `ModalBottomSheet` when settings modals were being unified. As tuning options grew across 7 subsections with nested scroll, scrolling to the top triggered parent sheet swipe-to-dismiss, accidentally dumping uncommitted user edits. Furthermore, Navigation options were relegated to the bottom, separating them from Cockpit options.
4. **Preservation of Core Invariants**:
   - Parameter schemas, DataStore persistence (`TuningConfig`, `TuningPreferencesDataStore`), factory reset, category composables, advisory warning card, and 400-line modularity boundaries remain 100% strictly intact.

---

## 1. Requirement Specification (REQ-UI-357)

### 1.1 Problem Statement & Rationale
The "Erweiterte Einstellungen" (Advanced Settings / Advanced Tuning) interface provides power users and athletes with granular control over rendering parameters, hardware sensors, battery management, and navigation algorithms. Over several sprints, two severe usability and architectural defects have emerged:
1. **Accidental Screen Dismissal on Scroll (ModalBottomSheet Defect)**: Inside `ModalBottomSheet`, nested scrolling dispatches unconsumed upward drag or momentum flings to the parent sheet when the scroll position reaches the top, causing the sheet to immediately collapse and dismiss, discarding unapplied adjustments.
2. **Counter-Intuitive Categorical Sequence**: Navigation settings are relegated to the bottom (Section 7), while hardware sensors and sensor search are fragmented across disjoint sections.

Migrating Advanced Settings to a dedicated full-screen Compose destination `AdvancedTuningScreen.kt` (`NavRoutes.ADVANCED_SETTINGS`) and reordering categories into a 3-phase athletic lifecycle completely resolves both defects while honoring all architectural and modularity constraints.

### 1.2 Functional & Architectural Requirements
The system SHALL fulfill the following functional and architectural clauses:

1. **Dedicated Full-Screen Screen Architecture (`AdvancedTuningScreen.kt`)**:
   - The system SHALL host Advanced Settings within a Material 3 `Scaffold` in `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreen.kt`.
   - The screen SHALL include a `TopAppBar` rendering the localized title `R.string.advanced_tuning_title`, a navigation back arrow (`Icons.AutoMirrored.Filled.ArrowBack`) calling `onNavigateBack()`, and an action icon button to restore factory defaults (`Icons.Default.RestartAlt`, `R.string.reset_to_defaults`).
   - The screen SHALL provide a sticky bottom bar or action control hosting Save (`R.string.save`) and Cancel actions (`AppDialogActions.SaveCancel`).
   - Tapping Back or invoking system back gesture SHALL safely pop back to the previous screen without saving uncommitted edits; tapping Save SHALL atomically persist all settings before navigating back.

2. **Elimination of Scroll Dismissals (Lösung A)**:
   - Advanced Settings SHALL NOT be hosted inside `ModalBottomSheet` or `BottomSheetScaffold`.
   - The content container SHALL utilize full-height vertical scrolling (`Modifier.fillMaxSize().verticalScroll(rememberScrollState())`) decoupled from modal dismiss gestures, guaranteeing zero accidental closures upon upward scrolling or momentum flings.

3. **Navigation & Route Integration (`NavRoutes.kt`, `ATrainingTrackerApp.kt`)**:
   - `NavRoutes` SHALL define `ADVANCED_SETTINGS = "advanced_settings"`.
   - `NavRoutes.fromDrawerItemId(R.id.drawer_advanced_tuning)` SHALL return `NavRoutes.ADVANCED_SETTINGS`.
   - `NavRoutes.toDrawerItemId(NavRoutes.ADVANCED_SETTINGS)` SHALL return `R.id.drawer_advanced_tuning`.
   - `NavRoutes.toBottomSheetType(R.id.drawer_advanced_tuning)` SHALL return `null`.
   - `ATrainingTrackerApp.kt` SHALL register `composable(NavRoutes.ADVANCED_SETTINGS)` in `NavHost`.
   - `DisplaySettingsDialog.kt`'s tuning link (`onNavigateToTuning`) SHALL navigate directly to `NavRoutes.ADVANCED_SETTINGS`.

4. **Lifecycle-Driven Category Reorganization (`TuningSection`)**:
   - The category enum `TuningSection` and the accordion layout SHALL enforce the canonical 7-section sequence:
     1. `NAVIGATION`: Navigation & Kartenführung (`R.string.tuning_cat_navigation`, promoted to Section 1).
     2. `COCKPIT_TYPOGRAPHY`: Cockpit-Typografie & Sensorfelder (`R.string.tuning_cat_cockpit_typography`).
     3. `BATTERY_SAVER`: AMOLED & Akkuschoner (`R.string.tuning_cat_battery_saver`).
     4. `SENSORS_GPS`: Sensoren & GPS-Filterung (`R.string.tuning_cat_sensors_gps`).
     5. `SENSOR_SEARCH`: Sensorsuche & Verbindungen (`R.string.tuning_cat_sensor_search`).
     6. `WORKOUT_MASKS_CARDS`: Workout-Masken & Detailkarten (`R.string.tuning_cat_workout_masks_cards`).
     7. `AFTERMATH_ANALYSIS`: Aftermath & Analyse (`R.string.tuning_cat_aftermath`).

5. **Preservation of Modularity & Invariants**:
   - All 25+ parameter DataStore keys, default values, and clamping bounds MUST remain strictly identical.
   - Modularity constraint: Every file in `ui.settings.tuning` MUST remain strictly < 400 lines of code (`AdvancedTuningModularityTest`).
   - All accordion cards retain `RoundedCornerShape(12.dp)`, `surfaceVariant`, `outlineVariant`, animated chevron rotation, live active-value summary subtitles, and initially collapsed state (`emptySet()`).
   - 100% 9-language translation parity maintained across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1: Zero Accidental Dismissal on Scroll**:
  * *Given* the athlete opening Advanced Settings
  * *When* expanding accordion categories and rapidly scrolling up or down across the full list
  * *Then* the screen remains steadily open and never dismisses due to scroll overscroll gestures, exiting only when the user explicitly taps Back or Save.
* **Criterion 2: Dedicated Full-Screen Screen Architecture**:
  * *Given* navigation to Advanced Settings from the drawer or Display Settings dialog
  * *When* the screen renders
  * *Then* it displays as a dedicated full-screen screen with `TopAppBar`, back arrow, and screen title, matching `ZoneSettingsScreen` and `ImportBackupTabsScreen`.
* **Criterion 3: Navigation Promoted to Top Category**:
  * *Given* the athlete viewing the categories
  * *When* inspecting the first section
  * *Then* Navigation & Kartenführung appears as Section 1, eliminating the need to scroll to the bottom.
* **Criterion 4: Coherent Telemetry & Post-Workout Grouping**:
  * *Given* the remaining categories
  * *When* browsing the screen
  * *Then* Sensors and Sensor Search appear consecutively under Hardware, and Workout Masks and Aftermath Analysis appear at the conclusion under History & Post-Workout.
* **Criterion 5: Architectural & Visual Contract Parity**:
  * *Given* the updated screen and route
  * *When* running the automated test suite
  * *Then* all contract tests pass and state persistence (collapsed/expanded states across rotations) functions properly.

### 1.4 System Invariants
1. Parameter Schema & Persistence: All DataStore keys, default values, and range validations in `TuningConfig` remain untouched.
2. Zero Modularity Violation: All source files in `ui.settings.tuning` strictly obey the < 400 lines of code constraint.
3. Zero Tonal Elevation: All tuning cards and dialog containers maintain `tonalElevation = 0.dp`.
4. Translation Parity: 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT for all string tokens.

---

## 2. Test Specification (TST-UI-317)

### Test Case 1: `AdvancedTuningScreen` Architecture and Invariant Tests (`TST-UI-317.1`)
* **Scope**: UI & Architectural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreenTest.kt`
* **Preconditions**: `AdvancedTuningScreen` instantiated with mock/fake ViewModel or callback handlers.
* **Action**: Verify Scaffold structure, TopAppBar elements, sticky bottom actions, and back/save callback dispatches.
* **Expected Result**:
  - `TopAppBar` renders back button calling `onNavigateBack()`.
  - Title renders `R.string.advanced_tuning_title`.
  - Reset icon button triggers factory reset dialog / callback.
  - Sticky bottom action bar renders Save and Cancel buttons.
  - Tapping Save commits state and triggers `onNavigateBack()`.
  - Tapping Cancel/Back triggers `onNavigateBack()` without persisting uncommitted edits.

### Test Case 2: Zero-Dismissal Full-Height Scroll Verification (`TST-UI-317.2`)
* **Scope**: Compose Layout & Gesture Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreenTest.kt`
* **Preconditions**: `AdvancedTuningScreen` composable rendered.
* **Action**: Inspect modifier chain and parent container types.
* **Expected Result**:
  - Content container uses `Modifier.fillMaxSize().verticalScroll(rememberScrollState())`.
  - Screen is NOT nested inside `ModalBottomSheet` or `BottomSheetScaffold`.

### Test Case 3: Navigation Route & Call-Site Contract Tests (`TST-UI-317.3`)
* **Scope**: Navigation Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningNavigationTest.kt`
* **Preconditions**: `NavRoutes` class under test.
* **Action**: Evaluate route constants and mapping helpers.
* **Expected Result**:
  - `NavRoutes.ADVANCED_SETTINGS` equals `"advanced_settings"`.
  - `NavRoutes.fromDrawerItemId(R.id.drawer_advanced_tuning)` returns `NavRoutes.ADVANCED_SETTINGS`.
  - `NavRoutes.toDrawerItemId(NavRoutes.ADVANCED_SETTINGS)` returns `R.id.drawer_advanced_tuning`.
  - `NavRoutes.toBottomSheetType(R.id.drawer_advanced_tuning)` returns `null`.

### Test Case 4: Canonical Category Ordering Contract Tests (`TST-UI-317.4`)
* **Scope**: Unit & Layout Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordionTest.kt`
* **Preconditions**: `TuningSection` enum and `AdvancedTuningAccordion` composable.
* **Action**: Assert `TuningSection.values()` sequence and rendered layout sections.
* **Expected Result**:
  - `TuningSection.values()` sequence:
    1. `NAVIGATION`
    2. `COCKPIT_TYPOGRAPHY`
    3. `BATTERY_SAVER`
    4. `SENSORS_GPS`
    5. `SENSOR_SEARCH`
    6. `WORKOUT_MASKS_CARDS`
    7. `AFTERMATH_ANALYSIS`
  - Rendered accordion sections match this exact sequence.

### Test Case 5: Preservation of Modularity & Invariants (`TST-UI-317.5`)
* **Scope**: Architecture Rule Enforcement Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningModularityTest.kt`
* **Preconditions**: Repository directory scan of `ui/settings/tuning`.
* **Action**: Count line numbers of each `.kt` file.
* **Expected Result**:
  - Every file in `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning` has < 400 lines of code.

### Test Case 6: 9-Language Localization Audit (`TST-UI-317.6`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Preconditions**: Android resource XMLs in 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
* **Action**: Scan all string tokens utilized by Advanced Tuning.
* **Expected Result**: 100% parity, zero missing tokens, zero format specifier mismatches.

### Test Case 7: Clean-Room Full Suite Regression Execution (`TST-UI-317.7`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case ID | Test Name | Scope | Method / Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `TST-UI-317.1` | Screen Architecture & Invariants | UI Contract | `AdvancedTuningScreen` | `REQ-UI-357` (Clause 1) | Specified |
| `TST-UI-317.2` | Zero Scroll Dismissal | Contract | `AdvancedTuningScreen` | `REQ-UI-357` (Clause 2) | Specified |
| `TST-UI-317.3` | Route & Navigation Mapping | Navigation | `NavRoutes` | `REQ-UI-357` (Clause 3) | Specified |
| `TST-UI-317.4` | Lifecycle Category Ordering | Unit / UI | `TuningSection`, `AdvancedTuningAccordion` | `REQ-UI-357` (Clause 4) | Specified |
| `TST-UI-317.5` | Modularity & File Size Limits | Architecture | `ui.settings.tuning.*` | `REQ-UI-357` (Clause 5) | Specified |
| `TST-UI-317.6` | 9-Language Localization Audit | Localization | `TranslationParityTest` | `REQ-UI-357` (Clause 5) | Specified |
| `TST-UI-317.7` | Clean-Room Full Regression Suite | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
