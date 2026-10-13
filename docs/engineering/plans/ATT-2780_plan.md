# Stage 3: Implementation Plan - ATT-2780: Relocate sensor search settings from navigation drawer to advanced tuning

**Ticket**: [ATT-2780](https://atrainingtracker.atlassian.net/browse/ATT-2780)  
**Sub-task**: [ATT-2887](https://atrainingtracker.atlassian.net/browse/ATT-2887) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-211](https://atrainingtracker.atlassian.net/browse/ATT-211) (*Reorder MainNavigationDrawer*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.5`  
**Requirement Mapping**: `REQ-UI-314` (*Sensor Search Settings Relocation to Advanced Tuning Accordion & Navigation Drawer Decluttering*)  
**Amending**: `REQ-UI-154` (*Search Settings Modal Bottom Sheet & Navigation Integration*)  
**Test Mapping**: `TST-UI-274`  
**Branch**: `improvement/ATT-2780`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Description & Background

Currently, the primary navigation drawer presents an item labeled *"Suche"* / *"Search Settings"* (`R.string.Search_Settings`) under the general *Settings* group (`drawer__settings`):
```kotlin
DrawerItemConfig(R.id.drawer_search_settings, R.drawable.ic_search, R.string.Search_Settings)
```

This presents two distinct UX and cognitive problems:
1. **Misleading Mental Model**: The search icon (`ic_search`) and label lead users to anticipate a global entity search (for routes, workouts, places, or segments), rather than hardware sensor scanning configuration.
2. **Technical Parameter Overexposure**: The item launches `SearchSettingsDialog`, exposing technical BLE/ANT+ radio parameters (retry rounds, automated scanning triggers, sport-based sensor filtering) that athletes configure rarely, if ever.

By decluttering the navigation drawer and relocating these parameters into **Advanced Tuning** (`AdvancedTuningDialog.kt`) as a dedicated collapsible accordion section (`TuningSection.SENSOR_SEARCH`), the navigation drawer is streamlined to essential application hubs, and technical sensor options are unified under *Expert Settings*.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-314` (*Sensor Search Settings Relocation to Advanced Tuning Accordion & Navigation Drawer Decluttering*, amending `REQ-UI-154`)
* **Test Mapping**: `TST-UI-274` (*Navigation Drawer Decluttering and Advanced Tuning Sensor Search Section Contract Verification*)
  * `TST-UI-274.1`: Navigation Drawer State & Item Count Contract (`NavigationDrawerStateTest.kt`, `AppNavigationDrawer.kt`)
  * `TST-UI-274.2`: Advanced Tuning Modularity & Structural Constraints (`AdvancedTuningModularityTest.kt`)
  * `TST-UI-274.3`: Advanced Tuning Visual & Section Expansion Contract (`AdvancedTuningVisualContractTest.kt`, `AdvancedTuningDialog.kt`)
  * `TST-UI-274.4`: 9-Language Localization & Specifier Audit (`TranslationParityTest.kt`, `strings.xml`)
  * `TST-UI-274.5`: Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Preference Key and Schema Invariance**:
   All 7 existing `SharedPreferences` keys and default values MUST remain identical:
   - `TrainingApplication.SP_NUMBER_OF_SEARCH_TRIES_INT` (int, default: 3)
   - `startSearchWhenAppStarts` (boolean, default: true)
   - `startSearchWhenResumeFromPaused` (boolean, default: true)
   - `startSearchWhenUserChangesSport` (boolean, default: true)
   - `startSearchWhenTrackingStarts` (boolean, default: false)
   - `searchOnlyForSportSpecificDevices` (boolean, default: true)
   - `changeSportWhenDeviceGetsLost` (boolean, default: true)
2. **Modal Bottom Sheet Reflection Invariance**:
   `ModalBottomSheetDialogsIntegrityTest` reflection test for `SearchSettingsDialogKt` and `SearchSettingsDialogFragment` MUST continue to pass without regression.
3. **Modularity Constraint (< 400 lines per file)**:
   Every source file in `com.atrainingtracker.trainingtracker.ui.settings.tuning` MUST remain strictly under 400 lines of code (`AdvancedTuningModularityTest.kt`).
4. **Transactional Staging Integrity**:
   Changes within `AdvancedTuningDialog` remain staged in memory until the user taps "Speichern" (`AppDialogActions.SaveCancel`). Tapping "Abbrechen" or dismissing the dialog discards changes without mutating `SharedPreferences`. Reset defaults restores standard defaults in the staged state.
5. **Localization Parity**:
   All new string resources MUST be translated across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with identical format tokens.

---

## 4. Proposed Architectural Changes

### Component 1: Navigation Drawer Decluttering (`AppNavigationDrawer.kt`, `NavRoutes.kt`)
- Remove `DrawerItemConfig(R.id.drawer_search_settings, R.drawable.ic_search, R.string.Search_Settings)` from `createDrawerGroups()` in `AppNavigationDrawer.kt`.
- The `R.string.drawer__settings` category will now contain exactly 5 items: Units, Display, Tracking Layouts, Backup & Restore, and Privacy Policy.
- Total drawer item count across all 5 categories drops from 22 to exactly 21.
- In `NavRoutes.kt`, clean up or adjust drawer ID resolution so `NavRoutes.fromDrawerItemId(R.id.drawer_search_settings)` returns `null`.

### Component 2: Dedicated Modular Tuning Section (`SensorSearchTuningSection.kt`)
- Create `com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.SensorSearchTuningSection.kt`.
- Provide `@Composable fun SensorSearchTuningSection(...)` containing:
  - Retry rounds slider: 1 to 5 (steps = 3, default: 3) with dynamic badge indicator.
  - Automated trigger switches:
    - App starts (`startSearchWhenAppStarts`, default: true)
    - Tracking starts (`startSearchWhenTrackingStarts`, default: false)
    - Resume after pause (`startSearchWhenResumeFromPaused`, default: true)
    - Sport changes (`startSearchWhenUserChangesSport`, default: true)
  - Filtering behavior switches:
    - Sport-specific sensors (`searchOnlyForSportSpecificDevices`, default: true)
    - Change sport when device gets lost (`changeSportWhenDeviceGetsLost`, default: true)
- Add subtitle formatting in `TuningSubtitleFormatter.kt`:
  `fun formatSensorSearchSubtitle(context: Context, searchTries: Int, autoSearchEnabled: Boolean): String`

### Component 3: Integration into Advanced Tuning Accordion (`AdvancedTuningDialog.kt`, `AdvancedTuningAccordion.kt`)
- Declare `SENSOR_SEARCH` in `TuningSection` enum in `AdvancedTuningAccordion.kt`.
- In `AdvancedTuningDialog.kt`:
  - Manage staged state for the 7 sensor search properties.
  - Render `TuningAccordionSection` for `TuningSection.SENSOR_SEARCH` with `Icons.Default.Sensors` (or `ic_sensors`), title `R.string.tuning_cat_sensor_search`, and dynamic subtitle.
  - Keep section collapsed by default (`expandedSections` initialized to `emptySet()`).
  - Atomically commit staged values in `onSave`.
  - Revert staged values in `onResetDefaults`.

### UI Consistency (Rule 23)
* **Reference screen / component**: [SensorsGpsFilterSection.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/SensorsGpsFilterSection.kt) and [ElevationsSection.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/ElevationsSection.kt).
* **Reused components**: `TuningAccordionSection`, `TuningSliderRow`, `TuningSwitchRow`, `TuningDivider` from `com.atrainingtracker.trainingtracker.ui.settings.tuning.components`.
* **Theme tokens**: Standard Material 3 tokens, `MaterialTheme.colorScheme.primary`, `MaterialTheme.typography.bodyMedium`, standard 8.dp / 16.dp paddings.
* **New one-off styles & justification**: None. Completely adheres to existing Advanced Tuning design patterns.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Localization Updates across 9 Locales
* **Files**:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
* **Changes**: Add strings for `tuning_cat_sensor_search`, `tuning_sensor_search_subtitle`, etc.
* **Verification**: `TranslationParityTest.kt`.

### Step 2: Navigation Drawer Decluttering & Route Updates
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/NavRoutes.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/NavigationDrawerStateTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/SingleActivityNavigationTest.kt`
* **Changes**: Remove `R.id.drawer_search_settings` from `createDrawerGroups()` and `fromDrawerItemId()`. Update drawer count assertions to 21 items across 5 categories.
* **Verification**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*"`

### Step 3: Subtitle Formatter Addition
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningSubtitleFormatter.kt`
* **Changes**: Add `formatSensorSearchSubtitle(...)` summarizing retry limit and active trigger states.

### Step 4: Create Modular `SensorSearchTuningSection.kt`
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/SensorSearchTuningSection.kt`
* **Changes**: Construct composable with slider and switches using existing tuning building blocks.
* **Verification**: Keep file length under 250 lines (strictly < 400).

### Step 5: Advanced Tuning Accordion & Dialog Integration
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordion.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`
* **Changes**:
  - Add `SENSOR_SEARCH` to `TuningSection`.
  - Wire staged state variables in `AdvancedTuningDialog.kt`.
  - Embed `SensorSearchTuningSection` inside collapsible accordion item.
  - Handle atomic commit in `onSave` and reset in `onResetDefaults`.
  - Verify `AdvancedTuningDialog.kt` remains strictly < 400 lines (currently ~263 lines).

### Step 6: Test Suite & Regression Verification
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningModularityTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningVisualContractTest.kt`
* **Changes**: Update modularity and visual contract tests to include `SensorSearchTuningSection` and `TuningSection.SENSOR_SEARCH`.
* **Execution**: Run `./gradlew testDebugUnitTest` to ensure 100% pass rate.

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Targeted unit test runs at each step.
  - Automated density fallback audit test (`DrawableDensityFallbackAuditTest`).
  - Full clean-room test suite run (`./gradlew testDebugUnitTest`).
* **Rollback**:
  - Changes are completely isolated on branch `improvement/ATT-2780`.
  - Any regression can be rolled back via git revert before merging to `sprint/2026-41.5`.
