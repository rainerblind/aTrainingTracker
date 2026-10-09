# Stage 1 Analysis: ATT-2780 - Relocate sensor search settings from navigation drawer to advanced tuning

**Ticket**: [ATT-2780](https://atrainingtracker.atlassian.net/browse/ATT-2780)  
**Sub-task**: [ATT-2885](https://atrainingtracker.atlassian.net/browse/ATT-2885) (`[Analysis]`)  
**Parent Epic**: [ATT-211](https://atrainingtracker.atlassian.net/browse/ATT-211) (*Reorder MainNavigationDrawer*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.5`  
**Branch**: `improvement/ATT-2780`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

Currently, the primary navigation drawer exposes an item labeled *"Suche"* / *"Search Settings"* (`R.string.Search_Settings`) paired with a magnifying glass icon (`R.drawable.ic_search` / `R.id.drawer_search_settings`) in the standard *Settings* (`R.string.drawer__settings`) group in [AppNavigationDrawer.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt).

This creates two distinct UX defects:
1. **Misleading Mental Model**: In modern Android design, an item labeled *"Suche"* / *"Search"* accompanied by a magnifying glass icon strongly suggests global entity search (e.g. searching across workouts, routes, segments, or favorite locations).
2. **Technical Parameter Overexposure**: Tapping the item opens [SearchSettingsDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/search/SearchSettingsDialog.kt), which controls low-level BLE and ANT+ radio discovery parameters (number of search attempts, automated search trigger conditions upon app launch / tracking start / resume / sport changes, and sport-specific sensor filtering). Everyday athletes almost never modify these technical parameters once configured.

Relocating these controls into **Advanced Tuning** ([AdvancedTuningDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt)) under the *Expert Settings* (`R.string.drawer__expert_settings`) hub declutters the primary navigation drawer, reduces cognitive load, and aligns technical sensor tuning with existing advanced parameters (GPS accuracy, altitude window, cockpit typography, AMOLED battery saver).

---

## 2. Root Cause Analysis (Forensic Investigation & Architectural Evaluation)

### 2.1 Current Implementation Trace
- In [AppNavigationDrawer.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt):
  ```kotlin
  DrawerGroup(
      titleRes = R.string.drawer__settings,
      items = listOf(
          DrawerItemConfig(R.id.drawer_units, R.drawable.ic_square_foot, R.string.prefsUnitsTitle),
          DrawerItemConfig(R.id.drawer_display_settings, R.drawable.ic_display_settings, R.string.Display),
          DrawerItemConfig(R.id.drawer_tracking_layouts, R.drawable.ic_table_edit, R.string.prefsConfigureDisplaysTitle),
          DrawerItemConfig(R.id.drawer_search_settings, R.drawable.ic_search, R.string.Search_Settings), // <-- Outlier
          DrawerItemConfig(R.id.drawer_backup_restore, R.drawable.ic_save_to_disc, R.string.import_backup),
          DrawerItemConfig(R.id.drawer_privacy_policy, R.drawable.ic_privacy, R.string.privacy_policy)
      )
  )
  ```
- In [NavRoutes.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/NavRoutes.kt):
  ```kotlin
  R.id.drawer_search_settings -> SettingsBottomSheetType.SEARCH
  ```
- In [NavigationDrawerStateTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/NavigationDrawerStateTest.kt):
  Asserts that `expectedItems` contains 22 destinations including `R.id.drawer_search_settings`.

### 2.2 Advanced Tuning Architecture & Options Evaluation
[AdvancedTuningDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt) utilizes a modular Material 3 accordion architecture ([AdvancedTuningAccordion.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordion.kt)) with six existing sections (`COCKPIT_TYPOGRAPHY`, `BATTERY_SAVER`, `SENSORS_GPS`, `AFTERMATH_ANALYSIS`, `WORKOUT_MASKS_CARDS`, `NAVIGATION`).

We evaluated two architectural placement options:

#### Option A: Dedicated Collapsible Accordion Section `TuningSection.SENSOR_SEARCH` (RECOMMENDED)
- Add `TuningSection.SENSOR_SEARCH` to the `TuningSection` enum.
- Create a dedicated category composable [SensorSearchTuningSection.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/SensorSearchTuningSection.kt).
- Section Header:
  - Title: `R.string.tuning_cat_sensor_search` ("Sensorsuche & Verhalten" / "Sensor Search & Triggers").
  - Icon: `Icons.Default.Sensors` (or `R.drawable.ic_search`).
  - Subtitle: Dynamic summary generated by `TuningSubtitleFormatter.formatSensorSearchSubtitle(...)` (e.g. `"3 Versuche · 3 Trigger · Sport-Filter aktiv"`).
- Contents:
  1. *Search Attempts*: Slider from 1 to 5 (steps = 3) bound to `SP_NUMBER_OF_SEARCH_TRIES_INT` with dynamic value badge.
  2. *Automatic Triggers*: Switches for `startSearchWhenAppStarts`, `startSearchWhenResumeFromPaused`, `startSearchWhenUserChangesSport`, and `startSearchWhenTrackingStarts`.
  3. *Search Behaviors*: Switches for `searchOnlyForSportSpecificDevices` and `changeSportWhenDeviceGetsLost`.
- **Pros**:
  - Perfectly preserves the modular accordion paradigm where all sections are initially collapsed, maintaining a compact dialog overview.
  - Provides a distinct, informative subtitle summarizing active scanning triggers at a glance.
  - Keeps [SensorsGpsFilterSection.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/SensorsGpsFilterSection.kt) focused on mathematical GPS and altitude filtering parameters.
  - High discoverability within Advanced Tuning.

#### Option B: Embedded inside `SensorsGpsFilterSection.kt`
- Embed the search controls directly into [SensorsGpsFilterSection.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/SensorsGpsFilterSection.kt) under `TuningSection.SENSORS_GPS`.
- **Cons**:
  - Bloats `SensorsGpsFilterSection.kt` by combining sensor connection radio scanning policies with mathematical GPS accuracy/altitude DSP filters.
  - Overloads the `SensorsGpsSubtitle` summary string.

**Decision**: Implement **Option A**.

### 2.3 File Modularity & Line Constraints (`REQ-UI-262` / `AdvancedTuningModularityTest`)
[AdvancedTuningModularityTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningModularityTest.kt) enforces that every file in `com.atrainingtracker.trainingtracker.ui.settings.tuning` must strictly remain under 400 lines (`lineCount < 400`).
- [AdvancedTuningDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt) currently has 400 lines.
- To prevent breaking `AdvancedTuningModularityTest`, the sensor search UI controls must be placed in a dedicated file [categories/SensorSearchTuningSection.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/SensorSearchTuningSection.kt), and state extraction/pruning in `AdvancedTuningDialog.kt` must be executed to keep `AdvancedTuningDialog.kt` strictly below 390 lines.
- [categories/SensorSearchTuningSection.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/SensorSearchTuningSection.kt) will be registered in `expectedFiles` in `AdvancedTuningModularityTest.kt`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Remove `drawer_search_settings` from `createDrawerGroups()` in [AppNavigationDrawer.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt).
  2. Update [NavigationDrawerStateTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/NavigationDrawerStateTest.kt) to assert 21 destinations (Settings group reduced from 6 to 5 items) and assert that `drawer_search_settings` is absent from the drawer.
  3. Clean up navigation routing in [NavRoutes.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/NavRoutes.kt) and [SingleActivityNavigationTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/SingleActivityNavigationTest.kt).
  4. Implement `TuningSection.SENSOR_SEARCH` in [AdvancedTuningAccordion.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordion.kt) and [AdvancedTuningDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt).
  5. Implement [SensorSearchTuningSection.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/SensorSearchTuningSection.kt) with search attempts slider (1..5), 4 automated trigger switches, and 2 behavior switches.
  6. Maintain 100% backward compatibility for all 7 `SharedPreferences` keys.
  7. Support transactional Save/Cancel staging: changes in the accordion are applied only when "Speichern" is tapped.
  8. Maintain 100% 9-language localization parity across all supported application locales.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Do NOT delete [SearchSettingsDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/search/SearchSettingsDialog.kt) or [SearchSettingsDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/search/SearchSettingsDialogFragment.kt) to preserve reflection contracts in [ModalBottomSheetDialogsIntegrityTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/ModalBottomSheetDialogsIntegrityTest.kt).
  2. Do NOT alter Bluetooth LE or ANT+ background scanning algorithms in `DeviceManager.java` or `BANALService.java`.
  3. Do NOT redesign other drawer groups or other Advanced Tuning accordion sections.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: `REQ-UI-154` (*Search Settings Modal Bottom Sheet & Navigation Integration*), targeting `SearchSettingsDialog.kt`, `SearchSettingsDialogFragment.kt`, `AppNavigationDrawer.kt`, `NavRoutes.kt`, and `MainActivityWithNavigation.kt`.
* **Historical Origin & Commit Trace**: Commit `4f38808d` (ATT-1044, 2026-09-15), titled *"docs(spec): specify REQ-UI-154 and TST-UI-107 for search settings bottom popup (ATT-1044)"*.
* **Root Reason for Existing Formulation**: `ATT-1044` replaced a legacy AndroidX XML preference fragment (`SearchSettingsFragment`) with a modal Compose bottom sheet (`SearchSettingsDialog`). Because the drawer previously hosted the legacy fragment, `R.id.drawer_search_settings` was retained in the drawer under the Settings group to maintain access to the modernized sheet.
* **Preservation of Core Invariants**: The underlying sensor scanning parameters, retry thresholds (`TrainingApplication.SP_NUMBER_OF_SEARCH_TRIES_INT`), automated triggers (app launch, tracking start, resume, sport change), and filtering rules remain 100% operational in `SharedPreferences`. Everyday athletes benefit from a decluttered primary drawer where "Suche" is not misinterpreted as an app-wide entity search, while power users retain full transactional control over low-level BLE/ANT+ radio discovery within the dedicated "Expert Settings" -> "Advanced Tuning" hub.

---

## 5. Architectural Strategy & High-Level Solution

```
+-----------------------------------------------------------------------------------+
|                           Main Navigation Drawer                                 |
|                                                                                   |
|  [Training]             [Maps]               [Equipment]        [Settings]        |
|  - Start Tracking       - Map                - Sensors          - Units           |
|  - Workouts             - Segments           - Bikes            - Display         |
|  - Periods              - Routes             - Shoes            - Tracking Layouts|
|                         - Favorites          - Sport Types      - Backup & Restore|
|                                              - Training Zones   - Privacy Policy  |
|                                                                                   |
|                                     [Expert Settings]                             |
|                                     - Advanced Tuning  <--------------------+     |
+-----------------------------------------------------------------------------|-----+
                                                                              |
                                     (Tapping Advanced Tuning opens dialog)   |
                                                                              v
+-----------------------------------------------------------------------------------+
|                        Advanced Tuning Dialog (Accordion)                         |
|                                                                                   |
|  [>] Cockpit & Typografie                                                        |
|  [>] AMOLED-Akkuschoner & Helligkeit                                              |
|  [>] Sensoren, GPS & Filter                                                      |
|  [v] Sensorsuche & Verhalten  (NEW SECTION: TuningSection.SENSOR_SEARCH)          |
|      ---------------------------------------------------------------------------  |
|      * Suchversuche (Slider 1..5, default: 3)                                     |
|      * Suche starten bei:                                                         |
|        - App-Start                                  [ Switch ON  ]                |
|        - Fortsetzen nach Pause                      [ Switch ON  ]                |
|        - Sportart-Wechsel                           [ Switch ON  ]                |
|        - Start Aufzeichnung                         [ Switch OFF ]                |
|      * Suchverhalten:                                                             |
|        - Nur sportspezifische Sensoren suchen       [ Switch ON  ]                |
|        - Sportart wechseln bei Verbindungsverlust   [ Switch ON  ]                |
|      ---------------------------------------------------------------------------  |
|  [>] Aftermath & Analyse                                                          |
|  [>] Workout-Masken & Detailkarten                                                |
|  [>] Navigation                                                                   |
|                                                                                   |
|  [ Abbrechen ]                                                      [ Speichern ] |
+-----------------------------------------------------------------------------------+
```

### 5.1 Call-Site & Affected Files Audit
1. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt`:
   - Remove `DrawerItemConfig(R.id.drawer_search_settings, ...)` from `createDrawerGroups()`.
2. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/NavRoutes.kt`:
   - Remove `R.id.drawer_search_settings -> SettingsBottomSheetType.SEARCH`.
3. `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/NavigationDrawerStateTest.kt`:
   - Update `expectedItems` count from 22 to 21, remove `R.id.drawer_search_settings`.
4. `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/SingleActivityNavigationTest.kt`:
   - Remove `R.id.drawer_search_settings` assertions from `testSettingsBottomSheetTypeMapping` and `testDrawerItemIdToRouteMapping`.
5. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordion.kt`:
   - Add `SENSOR_SEARCH` to `enum class TuningSection`.
   - Add `formatSensorSearchSubtitle(...)` to `TuningSubtitleFormatter`.
6. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/SensorSearchTuningSection.kt`:
   - New composable implementing the slider and 6 switches.
7. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`:
   - Add state initialization from `TrainingApplication` getters.
   - Embed `TuningAccordionSection` for `TuningSection.SENSOR_SEARCH`.
   - On "Speichern", write updated values transactionally to `PreferenceManager.getDefaultSharedPreferences(context)`.
   - On "Auf Standardwerte zurücksetzen", restore default values.
   - Maintain file length strictly under 400 lines.
8. `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningModularityTest.kt`:
   - Add `"categories/SensorSearchTuningSection.kt"` to `expectedFiles`.
9. `app/src/main/res/values/strings.xml` and 8 localized variants:
   - Provide `tuning_cat_sensor_search`, `tuning_sensor_search_subtitle`, etc.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. SharedPreferences keys (`SP_NUMBER_OF_SEARCH_TRIES_INT`, `startSearchWhenAppStarts`, `startSearchWhenResumeFromPaused`, `startSearchWhenUserChangesSport`, `startSearchWhenTrackingStarts`, `searchOnlyForSportSpecificDevices`, `changeSportWhenDeviceGetsLost`) MUST remain strictly identical.
  2. Transactional Staging: modifications in `AdvancedTuningDialog` are committed ONLY when "Speichern" is tapped; tapping "Abbrechen" or dismissing the dialog discards uncommitted changes.
  3. `ModalBottomSheetDialogsIntegrityTest` reflection test for `SearchSettingsDialogKt` and `SearchSettingsDialogFragment` MUST continue to pass.
  4. All source files in `com.atrainingtracker.trainingtracker.ui.settings.tuning` MUST remain strictly under 400 lines of code (`AdvancedTuningModularityTest`).
  5. 100% clean-room test suite pass rate across all project modules.
* **Risk Rating**: **LOW**
  - Justification: Pure UI rearrangement and declarative Compose refactoring. Sensor search mechanics and underlying preference storage remain unchanged.
