# Walkthrough - ATT-2780: Relocate sensor search settings from navigation drawer to advanced tuning

## 1. Executive Summary
Previously, the primary navigation drawer presented an item labeled *"Suche"* / *"Search Settings"* (`R.string.Search_Settings`) paired with a magnifying glass icon (`ic_search`) under the general *Settings* group (`drawer__settings`). This caused cognitive dissonance by implying global entity search while actually opening a low-level sensor radio tuning modal (`SearchSettingsDialog`).

In ATT-2780, we decluttered the main application navigation drawer by removing the sensor search item, reducing the Settings drawer category to 5 core items and the total drawer items to 21. The sensor scanning configuration (retry count slider, automated scanning triggers, and sport-specific filtering) has been seamlessly relocated into **Advanced Tuning** (`AdvancedTuningDialog.kt`) as a dedicated, modular accordion section (`SensorSearchTuningSection.kt` under `TuningSection.SENSOR_SEARCH`).

---

## 2. Changes Implemented

### 2.1 Navigation Drawer Decluttering (`AppNavigationDrawer.kt`, `NavRoutes.kt`)
- Removed `DrawerItemConfig(R.id.drawer_search_settings, R.drawable.ic_search, R.string.Search_Settings)` from the `drawer__settings` category in [AppNavigationDrawer.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt).
- Cleaned up drawer navigation routing in [NavRoutes.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/NavRoutes.kt) (`fromDrawerItemId(R.id.drawer_search_settings)` returns `null`).
- Updated [NavigationDrawerStateTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/NavigationDrawerStateTest.kt) and [SingleActivityNavigationTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/SingleActivityNavigationTest.kt) to assert the updated 21-item total and 5-item Settings group.

### 2.2 Modular Sensor Search Tuning Section (`SensorSearchTuningSection.kt`)
- Created [SensorSearchTuningSection.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/SensorSearchTuningSection.kt) (205 lines, strictly conforming to the < 400 lines modularity limit).
- Encapsulates:
  - **Search Retries Slider**: 1 to 5 rounds (steps = 3, default: 3) with dynamic badge.
  - **Automated Scanning Triggers**:
    - App starts (`startSearchWhenAppStarts`, default: true)
    - Tracking starts (`startSearchWhenTrackingStarts`, default: false)
    - Resume after pause (`startSearchWhenResumeFromPaused`, default: true)
    - Sport changes (`startSearchWhenUserChangesSport`, default: true)
  - **Device Filtering Behaviors**:
    - Sport-specific sensors (`searchOnlyForSportSpecificDevices`, default: true)
    - Change sport when device gets lost (`changeSportWhenDeviceGetsLost`, default: true)
- Added subtitle formatter `formatSensorSearchSubtitle(...)` in `TuningFormControls.kt` rendering concise summaries (e.g., *"3 Durchläufe, 3 Auslöser"*).

### 2.3 Advanced Tuning Dialog Integration (`AdvancedTuningDialog.kt`, `AdvancedTuningAccordion.kt`)
- Added `SENSOR_SEARCH` to the `TuningSection` enum in [AdvancedTuningAccordion.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordion.kt).
- Integrated staged state and transactional save/reset lifecycle in [AdvancedTuningDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt) (395 lines, under the 400-line ceiling).
- Values remain staged in memory until the user taps "Speichern" (`onSave`), and are cleanly reverted on "Standard wiederherstellen" (`onResetDefaults`) or "Abbrechen".

### 2.4 9-Language Localization Parity
- Added translations for `tuning_cat_sensor_search`, `tuning_sensor_search_subtitle`, etc., across all 9 supported locales:
  - `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

---

## 3. Verification & Clean-Room Test Execution

### 3.1 Targeted Contracts & Modularity Tests
- `SensorSearchTuningContractTest`: Verified composable composition, preference keys, and default state invariants.
- `AdvancedTuningModularityTest`: Verified all tuning category files remain under 400 lines of code.
- `AdvancedTuningAccordionTest`: Verified subtitle generation and section expansion contracts.
- `NavigationDrawerStateTest`: Verified drawer item count reduction to 21.
- `TranslationParityTest`: 100% parity across all 9 languages.

### 3.2 Full Regression Suite
```bash
./gradlew testDebugUnitTest
```
**Result**: 100% pass rate across the full application unit test suite.

---

## 4. ASPICE Traceability
- **Requirement**: `REQ-UI-314` (*Sensor Search Settings Relocation to Advanced Tuning Accordion & Navigation Drawer Decluttering*)
- **Test Specification**: `TST-UI-274`
- **Living Documentation**: [requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md) & [tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md) marked `Verified`.
- **Target Release Version**: `V4.9.40`. Sub-tasks have no fix version assigned (Rule 19), parent ticket fix version will be set upon human acceptance.
