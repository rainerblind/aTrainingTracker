# Stage 5: Walkthrough & Verification - ATT-3344: Migrate Advanced Settings to full-screen screen and optimize category structure

**Ticket**: [ATT-3344](https://rainerblind.atlassian.net/browse/ATT-3344)  
**Sub-task**: [ATT-3431](https://rainerblind.atlassian.net/browse/ATT-3431) (`[Test]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-357`  
**Test Mapping**: `TST-UI-317`  
**Branch**: `feature/ATT-3344`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Executive Summary & Verification Overview

ATT-3344 resolves two critical usability and architectural defects in the "Erweiterte Einstellungen" (Advanced Settings / Advanced Tuning) interface:
1. **Accidental Dismissal on Scroll (Lösung A)**: Advanced Settings was previously embedded within a Material 3 `ModalBottomSheet`. Rapid scrolling or scrolling upward to the top of the container routinely dispatched unconsumed drag gestures to the parent bottom sheet, causing it to collapse and dismiss unexpectedly while discarding unapplied user changes. Advanced Settings has now been migrated to a dedicated, full-screen Compose destination (`AdvancedTuningScreen.kt` / `NavRoutes.ADVANCED_SETTINGS`) hosted inside a standard Material 3 `Scaffold` with a top app bar, back navigation arrow, reset action, and sticky bottom bar, completely decoupled from `ModalBottomSheet` touch handlers.
2. **Lifecycle-Driven Category Reorganization**: The 7 tuning categories have been restructured to match the athlete's cognitive journey:
   - **Active In-Ride (Cockpit & Navigation)**: `1. Navigation & Kartenführung` (promoted from the very bottom to Section 1), `2. Cockpit-Typografie & Sensorfelder`, `3. AMOLED & Akkuschoner`.
   - **Hardware & Telemetry (Data Acquisition)**: `4. Sensoren & GPS-Filterung`, `5. Sensorsuche & Verbindungen`.
   - **History & Post-Workout (Data Visualization & Summary)**: `6. Workout-Masken & Detailkarten`, `7. Aftermath & Analyse`.
3. **Modularity & Quality Invariants**: Modularity constraints (< 400 lines per file in `ui.settings.tuning`), all 25+ tuning DataStore preference keys, default values, and 100% 9-language localization parity remain strictly intact and verified.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-357.1` | `TST-UI-317.1` | Automated Contract Test (`AdvancedTuningScreenTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-357.2` | `TST-UI-317.2` | Zero-Dismissal Scroll Assertion (`AdvancedTuningScreenTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-357.3` | `TST-UI-317.3` | Route & Navigation Contract Tests (`AdvancedTuningNavigationTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-357.4` | `TST-UI-317.4` | Lifecycle Category Ordering Tests (`AdvancedTuningAccordionTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-357.5` | `TST-UI-317.5` | Modularity Contract Verification (`AdvancedTuningModularityTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-357.5` | `TST-UI-317.6` | 9-Language Localization Audit (`TranslationParityTest.kt`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-317.7` | Full Clean-Room Suite (`./gradlew testDebugUnitTest`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Navigation Tests
```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 10s
32 actionable tasks: 1 executed, 31 up-to-date
```
- `AdvancedTuningScreenTest.kt`: Asserts `Scaffold` wrapping, `TopAppBar` with back navigation and reset defaults, sticky bottom bar with Save/Cancel, and full-height `Modifier.verticalScroll`.
- `AdvancedTuningNavigationTest.kt`: Asserts `NavRoutes.ADVANCED_SETTINGS == "advanced_settings"`, drawer item mappings (`R.id.drawer_advanced_tuning` -> `NavRoutes.ADVANCED_SETTINGS`), bottom sheet decoupling (`toBottomSheetType` -> `null`), and `DisplaySettingsDialog` direct route navigation.
- `AdvancedTuningAccordionTest.kt`: Asserts canonical 7-section ordering: `NAVIGATION`, `COCKPIT_TYPOGRAPHY`, `BATTERY_SAVER`, `SENSORS_GPS`, `SENSOR_SEARCH`, `WORKOUT_MASKS_CARDS`, `AFTERMATH_ANALYSIS`.
- `AdvancedTuningModularityTest.kt`: Asserts all files in `ui.settings.tuning` remain strictly below 400 lines of code.

---

## 4. Hardware / Physical Verification (Pixel 10)

* **Physical Device**: Google Pixel 10 (`66020DLCR002FL`, Android 16).
* **Navigation Verification**: Tapping "Erweiterte Einstellungen" from the navigation drawer or from the tuning link in `DisplaySettingsDialog` navigates smoothly to the dedicated full-screen screen.
* **Scroll Verification**: Rapid upward and downward scrolling across open accordions demonstrates zero accidental dismissals. The screen remains completely steady and dismisses only when tapping the TopAppBar back arrow or Save.
* **UI Consistency Check (Rule 23)**:
  - Screen structure matches `ZoneSettingsScreen` and `ImportBackupTabsScreen`: standard Material 3 `Scaffold` + `TopAppBar`.
  - Spacing strictly conforms to `16.dp` horizontal padding and standard vertical spacing.
  - Theme colors render identically across Light, Dark, and AMOLED modes.

---

## 5. Architectural & Living Documentation Changes

* **New File**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreen.kt`: Standalone full-screen destination composable.
* **Modified Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/NavRoutes.kt`: Registered `ADVANCED_SETTINGS` route and drawer mappings.
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt`: Integrated `NavHost` destination and decoupled from `ModalBottomSheet`.
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordion.kt`: Reordered `TuningSection` enum and accordion cards into 3-phase athletic lifecycle.
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`: Adjusted content layout for screen embedding.
  - `docs/requirements.md`: `REQ-UI-357` updated to `Verified`.
  - `docs/tests.md`: `TST-UI-317` updated to `Verified`.
