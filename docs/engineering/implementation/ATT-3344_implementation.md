# Stage 4: Implementation - ATT-3344: Migrate Advanced Settings to full-screen screen and optimize category structure

**Ticket**: [ATT-3344](https://rainerblind.atlassian.net/browse/ATT-3344)  
**Sub-task**: [ATT-3430](https://rainerblind.atlassian.net/browse/ATT-3430) (`[Implementation]`)  
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

## 1. Summary of Changes

In accordance with the Stage 3 Implementation Plan and `REQ-UI-357`:
1. **Dedicated Full-Screen Destination (`AdvancedTuningScreen.kt`)**:
   - Implemented `AdvancedTuningScreen` with Material 3 `Scaffold`, `TopAppBar` (back navigation arrow, title, reset-to-defaults action button), full-height vertically scrollable container (`Modifier.fillMaxSize().verticalScroll(rememberScrollState())`), and sticky bottom action bar hosting Save and Cancel (`AppDialogActions.SaveCancel`).
   - Kept file length strictly under 400 lines (371 lines).
2. **Elimination of Scroll Dismissals (Lösung A)**:
   - Replaced modal bottom sheet hosting with standard Compose full-screen screen destination, completely decoupling scrolling gestures from sheet dismiss flings.
3. **Navigation Routes & Drawer Dispatch (`NavRoutes.kt`, `ATrainingTrackerApp.kt`)**:
   - Added `NavRoutes.ADVANCED_SETTINGS = "advanced_settings"`.
   - Updated `fromDrawerItemId(R.id.drawer_advanced_tuning)` to return `NavRoutes.ADVANCED_SETTINGS`.
   - Updated `toDrawerItemId(NavRoutes.ADVANCED_SETTINGS)` to return `R.id.drawer_advanced_tuning`.
   - Updated `toBottomSheetType(R.id.drawer_advanced_tuning)` to return `null`.
   - Registered `composable(NavRoutes.ADVANCED_SETTINGS)` inside `ATrainingTrackerApp`'s `NavHost`.
   - Routed `DisplaySettingsDialog`'s `onNavigateToTuning` callback directly to `NavRoutes.ADVANCED_SETTINGS`.
4. **Lifecycle-Driven Category Reorganization (`TuningSection`)**:
   - Reordered `TuningSection` enum and accordion rendering in `AdvancedTuningScreen` (and `AdvancedTuningDialog`):
     1. `NAVIGATION`: Navigation & Kartenführung (Promoted to top)
     2. `COCKPIT_TYPOGRAPHY`: Cockpit-Typografie & Sensorfelder
     3. `BATTERY_SAVER`: AMOLED & Akkuschoner
     4. `SENSORS_GPS`: Sensoren & GPS-Filterung
     5. `SENSOR_SEARCH`: Sensorsuche & Verbindungen
     6. `WORKOUT_MASKS_CARDS`: Workout-Masken & Detailkarten
     7. `AFTERMATH_ANALYSIS`: Aftermath & Analyse
5. **Backwards Compatibility**:
   - Deprecated `AdvancedTuningDialog` with `@Deprecated("Use AdvancedTuningScreen instead (REQ-UI-357)")`.
   - Maintained all existing public composables, DataStore keys, default values, and clamping limits.
6. **Automated Unit Tests**:
   - Authored `AdvancedTuningScreenTest.kt` verifying architecture, full-height scrolling, zero bottom sheet nesting, canonical category order, and initial collapse state.
   - Authored `AdvancedTuningNavigationTest.kt` verifying route constants and drawer dispatch mappings.
   - Added canonical lifecycle sequence test to `AdvancedTuningAccordionTest.kt`.
   - Updated `SingleActivityNavigationTest.kt` and `AdvancedTuningModularityTest.kt`.

---

## 2. Modified & Created Files

* `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreen.kt` (Created, 371 lines)
* `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordion.kt` (Modified)
* `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt` (Modified, 387 lines)
* `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/NavRoutes.kt` (Modified)
* `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt` (Modified)
* `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreenTest.kt` (Created)
* `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningNavigationTest.kt` (Created)
* `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordionTest.kt` (Modified)
* `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningModularityTest.kt` (Modified)
* `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/SingleActivityNavigationTest.kt` (Modified)

---

## 3. Targeted Unit Test Execution

Executed commands:
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.SingleActivityNavigationTest"
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
```

Results:
* `com.atrainingtracker.trainingtracker.ui.settings.tuning.*`: 100% PASSED (14 test classes including `AdvancedTuningScreenTest`, `AdvancedTuningNavigationTest`, `AdvancedTuningModularityTest`, `AdvancedTuningAccordionTest`, `AdvancedTuningVisualContractTest`).
* `SingleActivityNavigationTest`: 100% PASSED.
* `TranslationParityTest`: 100% PASSED across all 9 locales.
