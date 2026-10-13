# Stage 5: Walkthrough - ATT-3344: Migrate Advanced Settings to full-screen screen and optimize category structure

**Ticket**: [ATT-3344](https://rainerblind.atlassian.net/browse/ATT-3344)  
**Sub-task**: [ATT-3431](https://rainerblind.atlassian.net/browse/ATT-3431) (`[Test]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-357`  
**Test Mapping**: `TST-UI-317`  
**Branch**: `feature/ATT-3344`  
**Author**: AI Agent 1 (Verification & Quality Engineer)  
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

## 1. Executive Summary

This deliverable documents Stage 5 verification and walkthrough for **ATT-3344**. Previously, the "Erweiterte Einstellungen" (Advanced Settings / Advanced Tuning) interface was hosted inside a Material 3 `ModalBottomSheet` via `SettingsBottomSheetType.ADVANCED_TUNING` in `ATrainingTrackerApp.kt`. Under Material 3 nested scrolling mechanics, upward scrolling or momentum flings at the top of the internal scroll list dispatched unconsumed drag gestures directly to the parent sheet, triggering an immediate swipe-to-dismiss that collapsed the dialog and discarded uncommitted adjustments. Additionally, the categorical layout placed navigation parameters at the very bottom (Section 7), while hardware sensors and sensor search were fragmented across separate sections.

ATT-3344 permanently resolves these defects by:
1. **Dedicated Full-Screen Compose Destination (`AdvancedTuningScreen.kt`)**: Hosting the interface within a standard Material 3 `Scaffold` featuring a `TopAppBar` (back arrow calling `onNavigateBack()`, localized title `R.string.advanced_tuning_title`, and reset-to-defaults action button), full-height scroll column (`Modifier.fillMaxSize().verticalScroll(rememberScrollState())`), and sticky bottom action bar (`AppDialogActions.SaveCancel`). Decoupled completely from `ModalBottomSheet`, guaranteeing zero accidental dismissals during scrolling.
2. **Navigation Integration (`NavRoutes.kt`, `ATrainingTrackerApp.kt`)**: Added `NavRoutes.ADVANCED_SETTINGS = "advanced_settings"`, registered destination in `NavHost`, updated drawer mappings, and routed `DisplaySettingsDialog.kt`'s tuning link directly to the new route.
3. **Lifecycle-Driven Category Reorganization (`TuningSection`)**: Reordered sections into an intuitive 3-phase athletic lifecycle sequence:
   - **Phase 1: Cockpit & Navigation**: 1. `NAVIGATION` (promoted to top), 2. `COCKPIT_TYPOGRAPHY`, 3. `BATTERY_SAVER`
   - **Phase 2: Hardware & Telemetry**: 4. `SENSORS_GPS`, 5. `SENSOR_SEARCH`
   - **Phase 3: History & Post-Workout**: 6. `WORKOUT_MASKS_CARDS`, 7. `AFTERMATH_ANALYSIS`
4. **Strict Invariant & Modularity Compliance**: Maintained all 25+ parameter DataStore keys, default values, and clamping limits. All files in `ui.settings.tuning` remain strictly under 400 lines of code.

---

## 2. Verification Results

### 2.1 Targeted Unit & Contract Testing

#### `AdvancedTuningScreenTest` (Screen Architecture & Scroll Contracts)
- `testAdvancedTuningScreen_architectureAndInvariants`: **PASSED** (Verifies Scaffold layout, TopAppBar with back arrow and title, sticky Save/Cancel bottom bar, and initial collapsed state of all sections).
- `testAdvancedTuningScreen_fullHeightScroll_zeroDismissal`: **PASSED** (Verifies `Modifier.verticalScroll` operates on full height and screen is completely decoupled from ModalBottomSheet).
- `testAdvancedTuningScreen_rendersCanonicalSectionOrder`: **PASSED** (Verifies all 7 sections appear in the exact lifecycle order: Navigation, Cockpit Typography, AMOLED Battery Saver, Sensors & GPS, Sensor Search, Workout Masks & Cards, Aftermath Analysis).

#### `AdvancedTuningNavigationTest` (Route & Drawer Mapping Contracts)
- `testAdvancedSettingsRouteConstant`: **PASSED** (`NavRoutes.ADVANCED_SETTINGS == "advanced_settings"`).
- `testFromDrawerItemId_routesToAdvancedSettings`: **PASSED** (`fromDrawerItemId(R.id.drawer_advanced_tuning) == "advanced_settings"`).
- `testToDrawerItemId_mapsToDrawerAdvancedTuning`: **PASSED** (`toDrawerItemId("advanced_settings") == R.id.drawer_advanced_tuning`).
- `testToBottomSheetType_returnsNullForAdvancedTuning`: **PASSED** (`toBottomSheetType(R.id.drawer_advanced_tuning) == null`).

#### `AdvancedTuningAccordionTest` (Lifecycle Category Sequence)
- `testTuningSection_enumSequenceMatchesLifecycleOrdering`: **PASSED** (Asserts enum ordinal order: `NAVIGATION`, `COCKPIT_TYPOGRAPHY`, `BATTERY_SAVER`, `SENSORS_GPS`, `SENSOR_SEARCH`, `WORKOUT_MASKS_CARDS`, `AFTERMATH_ANALYSIS`).
- `testInitialExpandedState_isEmptySet`: **PASSED**.
- `testToggleSection_expandsAndCollapses`: **PASSED**.

#### `AdvancedTuningModularityTest` (File Size Constraints)
- `testAllTuningFiles_under400Lines`: **PASSED** (Every file in `ui/settings/tuning` verified < 400 lines: `AdvancedTuningScreen.kt` = 371 lines, `AdvancedTuningDialog.kt` = 387 lines, `AdvancedTuningAccordion.kt` = 125 lines).

#### `SingleActivityNavigationTest` & `TranslationParityTest`
- `SingleActivityNavigationTest`: **PASSED** (Verified SingleActivity nav contracts).
- `TranslationParityTest`: **PASSED** (100% string parity across EN, DE, ES, FR, IT, JA, NL, PL, PT).

### 2.2 Clean-Room Test Suite Execution
- **Command**: `./gradlew testDebugUnitTest`
- **Result**: **BUILD SUCCESSFUL** (100% pass rate across entire suite, zero regressions).

---

## 3. Living Documentation Status

- **`docs/requirements.md`**: `REQ-UI-357` updated to `Verified`.
- **`docs/tests.md`**: `TST-UI-317` updated to `Verified`.
- **Governance Check**: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-42.1` -> Clean pass (net-new requirement `REQ-UI-357` verified).

---

## 4. Subtask & Ticket Advancement Plan

1. Stage 5 subtask **ATT-3431** (`[Test]`) description updated with this walkthrough.
2. Subtask transitioned to `In Überprüfung` and Gate 5 audit initiated via `python3 tools/review_agent.py audit ATT-3431`.
3. Upon Gate 5 sign-off (`RECOMMEND PASS`), subtask moved to `Erledigt` via transition `freigabe`.
4. Parent ticket **ATT-3344** transitioned to `Final Review (Human)`.
5. Changes committed to `feature/ATT-3344`.
6. Branch `feature/ATT-3344` merged into `sprint/2026-42.1` with `--no-ff`, and `feature/ATT-3344` deleted.
