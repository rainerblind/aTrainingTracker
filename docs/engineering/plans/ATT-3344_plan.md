# Stage 3: Implementation Plan - ATT-3344: Migrate Advanced Settings to full-screen screen and optimize category structure

**Ticket**: [ATT-3344](https://rainerblind.atlassian.net/browse/ATT-3344)  
**Sub-task**: [ATT-3429](https://rainerblind.atlassian.net/browse/ATT-3429) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-357` (*Dedicated Full-Screen Advanced Settings Screen Architecture, Zero-Dismissal Scrolling, and Lifecycle-Driven Category Reorganization*)  
**Test Mapping**: `TST-UI-317`  
**Branch**: `feature/ATT-3344`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Problem Description & Background

The "Erweiterte Einstellungen" (Advanced Settings / Advanced Tuning) interface provides athletes and power users with granular control over rendering parameters, hardware sensors, battery management, and navigation algorithms. Over several sprints, two severe usability and architectural defects have emerged:
1. **Accidental Screen Dismissal on Scroll (ModalBottomSheet Defect)**: Inside `ModalBottomSheet`, nested scrolling dispatches unconsumed upward drag or momentum flings to the parent sheet when the scroll position reaches the top, causing the sheet to immediately collapse and dismiss, discarding unapplied adjustments.
2. **Suboptimal Categorical Sequence**: Navigation settings are buried at the bottom (Section 7), while hardware sensors and sensor search are fragmented across disjoint sections.

Migrating Advanced Settings to a dedicated full-screen Compose destination `AdvancedTuningScreen.kt` (`NavRoutes.ADVANCED_SETTINGS`) and reordering categories into a 3-phase athletic lifecycle completely resolves both defects while honoring all architectural and modularity constraints.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-357` (*Dedicated Full-Screen Advanced Settings Screen Architecture, Zero-Dismissal Scrolling, and Lifecycle-Driven Category Reorganization*)
* **Test Mapping**: `TST-UI-317` (*Dedicated Full-Screen Advanced Settings Screen Architecture, Zero-Dismissal Scrolling, and Lifecycle Category Reorganization Verification*)
  * `TST-UI-317.1`: Screen Architecture & Invariants (`AdvancedTuningScreenTest.kt`)
  * `TST-UI-317.2`: Zero Scroll Dismissal (`AdvancedTuningScreenTest.kt`)
  * `TST-UI-317.3`: Navigation Route & Call-Site Contracts (`AdvancedTuningNavigationTest.kt`)
  * `TST-UI-317.4`: Lifecycle Category Ordering (`AdvancedTuningAccordionTest.kt`)
  * `TST-UI-317.5`: Modularity & File Size Limits (`AdvancedTuningModularityTest.kt`)
  * `TST-UI-317.6`: 9-Language Localization Audit (`TranslationParityTest.kt`)
  * `TST-UI-317.7`: Clean-Room Full Regression Suite (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing 25+ parameters in `TuningConfig`, DataStore keys, default values, and range validations MUST NOT be altered.
2. **Modularity Constraint (< 400 lines)**: Every file in `ui/settings/tuning` MUST remain strictly under 400 lines of code (`AdvancedTuningModularityTest`).
3. **Zero Tonal Elevation**: All accordion cards and containers retain `tonalElevation = 0.dp`, `RoundedCornerShape(12.dp)`, `surfaceVariant`, `outlineVariant`, animated chevron rotation, and initially collapsed state (`emptySet()`).
4. **9-Language Translation Parity**: 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT for all string tokens.
5. **Backwards Compatibility**: Retain `AdvancedTuningDialog.kt` and `AdvancedTuningDialogFragment.kt` marked as `@Deprecated` so that legacy references compile safely.
6. **Subtask Self-Sufficiency & Human Gate Invariance**: Subtasks transition directly to `Erledigt` via transition `freigabe` once Agent 2 passes the gate audit. Parent tickets terminate exclusively in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `AdvancedTuningScreen.kt` (New Full-Screen Screen)
* Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreen.kt`.
* Implement standard Material 3 `Scaffold`:
  - `topBar`: `TopAppBar` with navigation back arrow (`Icons.AutoMirrored.Filled.ArrowBack`) calling `onNavigateBack()`, localized title `R.string.advanced_tuning_title`, and reset action button (`Icons.Default.RestartAlt`, `R.string.reset_to_defaults`).
  - `bottomBar`: Sticky footer bar with `AppDialogActions.SaveCancel` hosting Save (`R.string.save`) and Cancel buttons, applying `Modifier.navigationBarsPadding()`.
  - `content`: Full-height scrollable column with `Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)`.
* Decouples scrolling completely from `ModalBottomSheet`, guaranteeing zero accidental dismissals.

### Component 2: `NavRoutes.kt` & Navigation Dispatch
* In `NavRoutes.kt`:
  - Define `const val ADVANCED_SETTINGS = "advanced_settings"`.
  - Map `R.id.drawer_advanced_tuning -> ADVANCED_SETTINGS` in `fromDrawerItemId()`.
  - Map `ADVANCED_SETTINGS -> R.id.drawer_advanced_tuning` in `toDrawerItemId()`.
  - Remove `R.id.drawer_advanced_tuning` from `toBottomSheetType()`, returning `null`.
* In `ATrainingTrackerApp.kt`:
  - Register `composable(NavRoutes.ADVANCED_SETTINGS)` in `NavHost`.
  - In `DisplaySettingsDialog.kt`: update `onNavigateToTuning` callback to dismiss sheet and navigate to `NavRoutes.ADVANCED_SETTINGS`.

### Component 3: Lifecycle Category Reorganization (`TuningSection` & Layouts)
* In `AdvancedTuningAccordion.kt`:
  - Update `TuningSection` enum sequence to:
    1. `NAVIGATION`
    2. `COCKPIT_TYPOGRAPHY`
    3. `BATTERY_SAVER`
    4. `SENSORS_GPS`
    5. `SENSOR_SEARCH`
    6. `WORKOUT_MASKS_CARDS`
    7. `AFTERMATH_ANALYSIS`
* In `AdvancedTuningScreen.kt` (and `AdvancedTuningDialog.kt`):
  - Render accordion sections matching this exact 3-phase athletic lifecycle sequence.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `ZoneSettingsScreen.kt` and `ImportBackupTabsScreen.kt` (Material 3 `Scaffold` destination with `TopAppBar`, back navigation arrow, scrollable column, and sticky bottom action bar).
* **Reused components**:
  - `TuningAccordionSection` (`AdvancedTuningAccordion.kt`)
  - `TuningWarningNotice` (`TuningFormControls.kt`)
  - `CockpitTypographySection`, `AmoledBatterySaverSection`, `SensorsGpsFilterSection`, `SensorSearchTuningSection`, `WorkoutMasksAndCardsSection`, `AftermathAnalysisSection`, `NavigationSection`
  - `AppDialogActions.SaveCancel` (`AppDialogActions.kt`)
* **Theme tokens**:
  - Shapes: `RoundedCornerShape(12.dp)` for accordion cards, `MaterialTheme.shapes.extraLarge` for dialogs.
  - Spacing: `8.dp`, `12.dp`, `16.dp` per Section 5.2.
  - Colors: `MaterialTheme.colorScheme.surface`, `surfaceVariant`, `outlineVariant`, `onSurface`, `primary`, zero tonal elevation (`0.dp`).
* **New one-off styles & justification**: None. Strictly adheres to established Material 3 design tokens and shared components.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `TuningSection` Enum Sequence
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordion.kt`
* **Changes**: Reorder enum entries: `NAVIGATION`, `COCKPIT_TYPOGRAPHY`, `BATTERY_SAVER`, `SENSORS_GPS`, `SENSOR_SEARCH`, `WORKOUT_MASKS_CARDS`, `AFTERMATH_ANALYSIS`.

### Step 2: Update Navigation Routes & Mappings
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/NavRoutes.kt`
* **Changes**:
  - Add `const val ADVANCED_SETTINGS = "advanced_settings"`.
  - In `fromDrawerItemId`: return `ADVANCED_SETTINGS` for `R.id.drawer_advanced_tuning`.
  - In `toDrawerItemId`: return `R.id.drawer_advanced_tuning` for `ADVANCED_SETTINGS`.
  - In `toBottomSheetType`: remove `R.id.drawer_advanced_tuning` (returns null).

### Step 3: Implement `AdvancedTuningScreen.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreen.kt`
* **Changes**: Create full-screen Compose destination with `Scaffold`, `TopAppBar`, sticky bottom bar, and vertical scrolling column rendering the 7 accordion categories in canonical order. Ensure file is < 400 lines.

### Step 4: Register Route in `ATrainingTrackerApp.kt` & Update Callers
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt`
* **Changes**:
  - Add `composable(NavRoutes.ADVANCED_SETTINGS)` in `NavHost`.
  - Update `DisplaySettingsDialog` `onNavigateToTuning` callback to dismiss sheet and navigate to `NavRoutes.ADVANCED_SETTINGS`.

### Step 5: Update `AdvancedTuningDialog.kt` Section Order (Backwards Compatibility)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`
* **Changes**: Reorder accordion sections to match canonical sequence. Mark `@Deprecated`. Keep line count < 400 lines.

### Step 6: Update Modularity & Contract Tests
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningModularityTest.kt`: Add `"AdvancedTuningScreen.kt"` to expected files.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningNavigationTest.kt`: Add route tests.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreenTest.kt`: Add screen contract and zero-dismissal tests.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningVisualContractTest.kt`: Verify contract matches.

### Step 7: Clean-Room Regression Verification
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% test pass rate across the full test suite with zero regressions.

---

## 6. Verification & Rollback Plan

* **Verification**: Run targeted unit tests during construction, followed by full `./gradlew testDebugUnitTest` suite and git diff inspection.
* **Rollback**: Branch isolation (`feature/ATT-3344`) allows a clean rollback via `git checkout sprint/2026-42.1` with zero impact on main development lines.
