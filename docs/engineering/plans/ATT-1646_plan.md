# Stage 3 Implementation Plan: ATT-1646

**Ticket**: [ATT-1646](https://atrainingtracker.atlassian.net/browse/ATT-1646)  
**Sub-task**: [ATT-1671](https://atrainingtracker.atlassian.net/browse/ATT-1671) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1646`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Traceability & Scope Matrix
* **Requirements Traced**: `REQ-SET-074` (*Expert Settings Drawer Positioning, Localization & 50m Default GPS Tolerance*)
* **Tests Traced**: `TST-SET-063` (*Expert Settings Navigation Drawer Grouping, Localization and 50m GPS Default Verification*)
* **Scope Definition**:
  1. Relocate `R.id.drawer_advanced_tuning` in `AppNavigationDrawer.kt` out of general settings into a dedicated 6th `DrawerGroup` situated at the bottom with header `R.string.drawer__expert_settings`.
  2. Implement 100% 9-language localization parity across default and 8 regional resource files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) for `drawer__expert_settings`, `advanced_tuning_title`, and `advanced_tuning_open`.
  3. Standardize `TuningPreferencesDefaults.GPS_ACCURACY_THRESHOLD_M` from `200.0f` to `50.0f` in `TuningPreferencesDataStore.kt`.
  4. Update `TuningConfigTest.kt` assertion to `50.0f` and add navigation drawer grouping assertions in `AppNavigationDrawerTest.kt`.

---

## 2. Step-by-Step Atomic Implementation Tasks

### Task 1: 9-Language String Localization (`strings.xml`)
- Files:
  - `app/src/main/res/values/strings.xml` (EN / Default)
  - `app/src/main/res/values-de/strings.xml` (German)
  - `app/src/main/res/values-es/strings.xml` (Spanish)
  - `app/src/main/res/values-fr/strings.xml` (French)
  - `app/src/main/res/values-it/strings.xml` (Italian)
  - `app/src/main/res/values-ja/strings.xml` (Japanese)
  - `app/src/main/res/values-nl/strings.xml` (Dutch)
  - `app/src/main/res/values-pl/strings.xml` (Polish)
  - `app/src/main/res/values-pt/strings.xml` (Portuguese)
- Add new resource `drawer__expert_settings` and update `advanced_tuning_title` / `advanced_tuning_open`:
  - **Default / EN**:
    - `drawer__expert_settings`: `"Expert Settings"`
    - `advanced_tuning_title`: `"Expert Settings"`
    - `advanced_tuning_open`: `"Expert Settings…"`
  - **DE**:
    - `drawer__expert_settings`: `"Experten-Einstellungen"`
    - `advanced_tuning_title`: `"Experten-Einstellungen"`
    - `advanced_tuning_open`: `"Experten-Einstellungen…"`
  - **ES**:
    - `drawer__expert_settings`: `"Ajustes de experto"`
    - `advanced_tuning_title`: `"Ajustes de experto"`
    - `advanced_tuning_open`: `"Ajustes de experto…"`
  - **FR**:
    - `drawer__expert_settings`: `"Paramètres d'expert"`
    - `advanced_tuning_title`: `"Paramètres d'expert"`
    - `advanced_tuning_open`: `"Paramètres d'expert…"`
  - **IT**:
    - `drawer__expert_settings`: `"Impostazioni per esperti"`
    - `advanced_tuning_title`: `"Impostazioni per esperti"`
    - `advanced_tuning_open`: `"Impostazioni per esperti…"`
  - **JA**:
    - `drawer__expert_settings`: `"エキスパート設定"`
    - `advanced_tuning_title`: `"エキスパート設定"`
    - `advanced_tuning_open`: `"エキスパート設定…"`
  - **NL**:
    - `drawer__expert_settings`: `"Expertinstellingen"`
    - `advanced_tuning_title`: `"Expertinstellingen"`
    - `advanced_tuning_open`: `"Expertinstellingen…"`
  - **PL**:
    - `drawer__expert_settings`: `"Ustawienia eksperckie"`
    - `advanced_tuning_title`: `"Ustawienia eksperckie"`
    - `advanced_tuning_open`: `"Ustawienia eksperckie…"`
  - **PT**:
    - `drawer__expert_settings`: `"Configurações de especialista"`
    - `advanced_tuning_title`: `"Configurações de especialista"`
    - `advanced_tuning_open`: `"Configurações de especialista…"`

### Task 2: Navigation Drawer Group Relocation (`AppNavigationDrawer.kt`)
- File: [AppNavigationDrawer.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt)
- In `createDrawerGroups`:
  - Remove `DrawerItemConfig(R.id.drawer_advanced_tuning, R.drawable.ic_tune, R.string.advanced_tuning_title)` from the 5th group (`drawer__settings`).
  - Add dedicated 6th group at the bottom:
    ```kotlin
    DrawerGroup(
        titleRes = R.string.drawer__expert_settings,
        items = listOf(
            DrawerItemConfig(R.id.drawer_advanced_tuning, R.drawable.ic_tune, R.string.advanced_tuning_title)
        )
    )
    ```

### Task 3: Standardize Default GPS Tolerance (`TuningPreferencesDataStore.kt`)
- File: [TuningPreferencesDataStore.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt)
- Update constant:
  ```kotlin
  const val GPS_ACCURACY_THRESHOLD_M = 50.0f
  ```

### Task 4: Unit Test Updates & Assertions
- File: [TuningConfigTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/settings/TuningConfigTest.kt)
  - Update `defaultValues_conformToEstablishedBehavior`:
    ```kotlin
    assertEquals(50.0f, config.gpsAccuracyThresholdMeters, 0.001f)
    ```
- File: [AppNavigationDrawerTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawerTest.kt)
  - Add tests:
    - `testDrawerGroups_expertSettings_placedInDedicatedBottomGroup`: Verify `groups.last().titleRes == R.string.drawer__expert_settings` and `groups.last().items` contains `R.id.drawer_advanced_tuning`.
    - `testDrawerGroups_generalSettings_doesNotContainAdvancedTuning`: Verify the `drawer__settings` group does not contain `R.id.drawer_advanced_tuning`.

### Task 5: Targeted Unit Verification
- Execute targeted unit test suite:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.AppNavigationDrawerTest" --tests "com.atrainingtracker.trainingtracker.settings.TuningConfigTest" --tests "com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStoreTest"
  ```
- Ensure 100% pass rate.

---

## 3. Invariant & Regression Guards
1. **Route Mapping & Invariants**: `NavRoutes.fromDrawerItemId(R.id.drawer_advanced_tuning)` and `toBottomSheetType(R.id.drawer_advanced_tuning)` remain mapped to `SettingsBottomSheetType.ADVANCED_TUNING`.
2. **User Preference Integrity**: DataStore preference key `tuning_gps_accuracy_threshold` is unchanged; existing athlete modifications are preserved.
3. **Clamping Invariants**: Clamping bounds ($10.0\text{m} .. 500.0\text{m}$) are preserved.
4. **Visual Layout**: Automatic `HorizontalDivider` in `AppNavigationDrawer` visually isolates the bottom expert group cleanly without manual spacing hacks.
