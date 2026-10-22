# Stage 3: Implementation Plan - ATT-4289: Move 'Sport Types' drawer item to Settings and rename 'Equipment' item to 'Bikes & Shoes'

**Ticket**: [ATT-4289](https://rainerblind.atlassian.net/browse/ATT-4289)  
**Sub-task**: [ATT-4322](https://rainerblind.atlassian.net/browse/ATT-4322) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-UI-422` (`REQ-UI-422.1` – `REQ-UI-422.5`)  
**Test Mapping**: `TST-UI-385` (`TST-UI-385.1` – `TST-UI-385.5`)  
**Branch**: `feature/ATT-4289`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

The navigation drawer in **aTrainingTracker** (`AppNavigationDrawer.kt`, `createDrawerGroups`) is organized into five functional category hubs:
1. *Training* (`R.string.drawer__training` — `collapsible = false`)
2. *Maps* (`R.string.drawer__maps` — `collapsible = false`)
3. *Equipment* / *My Stuff* (`R.string.drawer__my_stuff` — `collapsible = true`)
4. *Online Communities* (`R.string.prefsOnlineCommunities` — `collapsible = true`)
5. *Settings* (`R.string.drawer__settings` — `collapsible = true`)

Following `ATT-3851` (consolidating Bikes & Shoes into `R.id.drawer_equipment`) and `ATT-3928` (moving *Profile & Zones* to *Training* and configuring `R.string.drawer__my_stuff` as collapsible), the third drawer category hub (`R.string.drawer__my_stuff`, localized as *"Equipment"* in English and *"Ausrüstung"* in German) exhibits two UX friction points:

1. **Semantic Category Mismatch**:
   - `R.id.drawer_sport_types` (*Sport Types* / *Sportarten*) is a configuration screen for athletic disciplines, speed calculation thresholds, and activity taxonomy. It does not represent physical hardware, tracking sensors, or athletic gear.
   - Placing *Sport Types* in the *Equipment* (*Ausrüstung*) section alongside *My Sensors* and *Equipment* violates the athlete's mental model. In contrast, the *Settings* (*Einstellungen*) section already consolidates configuration destinations such as *Units*, *Display*, *Tracking Layouts* (*Cockpit-Ansichten*), *Backup & Restore*, *Privacy Policy*, and *Advanced Tuning*. Moving *Sport Types* into *Settings* directly after *Tracking Layouts* naturally unifies all tracking layout and activity taxonomy configuration.

2. **Duplicate Category & Item Label (*"Ausrüstung"* $\rightarrow$ *"Ausrüstung"*)**:
   - The drawer section header `R.string.drawer__my_stuff` is localized as *"Equipment"* (*"Ausrüstung"*), and its child destination `R.id.drawer_equipment` (`R.string.equipment_management_title`) is also localized as *"Equipment"* (*"Ausrüstung"*).
   - Expanding the section reveals a child row with the identical label as its parent section header.
   - Furthermore, `R.id.drawer_equipment` opens `EquipmentTabsScreen`, which hosts the consolidated *Bikes* (*Räder*) and *Shoes* (*Schuhe*) tabs. Renaming `R.string.equipment_management_title` to *"Bikes & Shoes"* (*"Räder & Schuhe"*) accurately reflects the actual gear managed on that screen and eliminates the redundant, confusing label repetition across all 9 supported application locales.

The objective of **ATT-4289** is to:
1. Excise `R.id.drawer_sport_types` from `R.string.drawer__my_stuff`, leaving `drawer__my_stuff` with strictly 2 physical hardware destinations (`R.id.drawer_my_sensors` and `R.id.drawer_equipment`).
2. Insert `R.id.drawer_sport_types` into `R.string.drawer__settings` immediately after `R.id.drawer_tracking_layouts`, bringing `drawer__settings` to 7 destination items.
3. Update `R.string.equipment_management_title` across all 9 localized `strings.xml` resource files to denote *"Bikes & Shoes"* (*"Räder & Schuhe"*), maintaining valid XML entity escaping (`&amp;`) and screen title parity with `EquipmentTabsScreen.kt`.
4. Update unit test assertions in `NavigationDrawerStateTest.kt` and establish contract and localization tests validating structure and 9-language translation parity.

---

## 2. Traceability & Requirements Mapping

| Requirement | Description | Test Mapping | Test Scope & Command |
| :--- | :--- | :--- | :--- |
| `REQ-UI-422.1` | Hardware-only Equipment hub in `AppNavigationDrawer.kt`: `drawer__my_stuff` holds strictly 2 items (`drawer_my_sensors`, `drawer_equipment`) in exact order; `drawer_sport_types` excised | `TST-UI-385.1` | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.NavigationDrawerStateTest.testDrawerItemStructureIntegrity"` |
| `REQ-UI-422.2` | Sport Types placement in Settings hub: `drawer_sport_types` inserted into `drawer__settings` directly after `drawer_tracking_layouts`; `drawer__settings` holds strictly 7 items | `TST-UI-385.1` | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.NavigationDrawerStateTest.testDrawerItemStructureIntegrity"` |
| `REQ-UI-422.3` | Rename `equipment_management_title` to *"Bikes & Shoes"* (*"Räder & Schuhe"*) across all 9 locales with XML entity escaping (`&amp;`) and `EquipmentTabsScreen` header parity | `TST-UI-385.3`, `TST-UI-385.4` | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`<br>`./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.NavigationDrawerLocalizationTest"`<br>`./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.equipment.EquipmentTabsScreenContractTest"` |
| `REQ-UI-422.4` | Drawer state machine auto-expansion: selecting `drawer_sport_types` auto-expands `settingsGroup` (`true`) and leaves `myStuffGroup` collapsed (`false`); `myStuffGroup` auto-expands on `drawer_equipment`, `drawer_bikes`, `drawer_shoes` | `TST-UI-385.2` | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.NavigationDrawerStateTest.testCollapsibleDrawerInitialExpansionAndConsolidatedSelection"` |
| `REQ-UI-422.5` | Invariant preservation: 5 category hubs, 22 total items, 9 non-collapsible visible items, route backward compatibility (`NavRoutes.SPORT_TYPES`), clean-room regression pass | `TST-UI-385.5` | `./gradlew testDebugUnitTest` |

---

## 3. System Invariants & Preserved Behavior

1. **Category Hub Count Invariant**: Exactly 5 drawer groups (`Training`, `Maps`, `My Stuff`, `Online Communities`, `Settings`).
2. **Total Navigation Item Invariant**: Exactly 22 unique `DrawerItemConfig` destination IDs across the entire drawer ($4 + 5 + 2 + 4 + 7 = 22$).
3. **Default Visible Destination Density Invariant**: Exactly 9 destination items visible when secondary collapsible groups are closed ($4 + 5 = 9$).
4. **Deep-Link & Selection Alias Invariant**: `isDrawerItemSelected(R.id.drawer_equipment, ...)` continues to match `drawer_equipment`, `drawer_bikes`, and `drawer_shoes`.
5. **Route Integrity & Controller Decoupling**: Routes in `NavRoutes.kt` (`EQUIPMENT`, `SPORT_TYPES`, `BIKES`, `SHOES`) and `MainActivityWithNavigation.kt` routing handlers remain 100% backward compatible without changes.
6. **Localization Parity Invariant**: 100% translation coverage across all 9 canonical app locales with zero missing keys and valid XML entity escaping (`&amp;`).
7. **Thread Safety & Dispatcher Affinity**: Zero background service lifecycle changes, zero SQLite migrations, and zero thread concurrency modifications.
8. **Subtask Self-Sufficiency (Rule 3)**: Subtasks transition directly to `Erledigt` upon passing Gate audit via transition `freigabe` (`tools/jira_util.py move <KEY> freigabe`), which synchronously advances the parent ticket `ATT-4289` to `Implementation`.
9. **Parent Human Gate Invariance (Rule 3)**: Advancing parent ticket `ATT-4289` to `Erledigt` is an inviolable Human Decision Gate reserved exclusively for the human user during Ceremony 2 (Joint Review).
10. **Code Lock in Stage 3**: Zero modifications to production source code in `app/src/main/...` during Stage 3 planning.
11. **One Commit per Ticket (Rule 44)**: Changes remain on `feature/ATT-4289` without intermediate per-stage commits until squashed into `sprint/2026-43.1` in Stage 5.

---

## 4. Proposed Architectural Changes

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                       NAVIGATION DRAWER HIERARCHY RESTRUCTURING                 │
└─────────────────────────────────────────────────────────────────────────────────┘

  BEFORE (Sprint 2026-42.8 baseline):
  ├── Group 1: Training (4 items, collapsible = false)
  ├── Group 2: Maps (5 items, collapsible = false)
  ├── Group 3: My Stuff / Equipment (3 items, collapsible = true)
  │     ├── R.id.drawer_my_sensors ("My Sensors")
  │     ├── R.id.drawer_equipment ("Equipment")  <── DUPLICATE "Ausrüstung" LABEL!
  │     └── R.id.drawer_sport_types ("Sport Types") <── TAXONOMY MISMATCH!
  ├── Group 4: Online Communities (4 items, collapsible = true)
  └── Group 5: Settings (6 items, collapsible = true)
        ├── R.id.drawer_units ("Units")
        ├── R.id.drawer_display_settings ("Display")
        ├── R.id.drawer_tracking_layouts ("Tracking Layouts")
        ├── R.id.drawer_backup_restore ("Backup & Restore")
        ├── R.id.drawer_privacy_policy ("Privacy Policy")
        └── R.id.drawer_advanced_tuning ("Advanced Tuning")

  AFTER (ATT-4289):
  ├── Group 1: Training (4 items, collapsible = false)
  ├── Group 2: Maps (5 items, collapsible = false)
  ├── Group 3: My Stuff / Equipment (2 items, collapsible = true)  <── HARDWARE ONLY!
  │     ├── R.id.drawer_my_sensors ("My Sensors")
  │     └── R.id.drawer_equipment ("Bikes & Shoes")  <── RENAMED, ELIMINATES DUPLICATE!
  ├── Group 4: Online Communities (4 items, collapsible = true)
  └── Group 5: Settings (7 items, collapsible = true)  <── UNIFIED CONFIGURATION HUB!
        ├── R.id.drawer_units ("Units")
        ├── R.id.drawer_display_settings ("Display")
        ├── R.id.drawer_tracking_layouts ("Tracking Layouts")
        ├── R.id.drawer_sport_types ("Sport Types")  <── LOGICAL PLACEMENT AFTER LAYOUTS!
        ├── R.id.drawer_backup_restore ("Backup & Restore")
        ├── R.id.drawer_privacy_policy ("Privacy Policy")
        └── R.id.drawer_advanced_tuning ("Advanced Tuning")
```

### Component 1: Navigation Drawer Group Definition (`AppNavigationDrawer.kt`)
In `createDrawerGroups(startTrackingTitleRes: Int)`:
- Update group 3 (`R.string.drawer__my_stuff`):
  ```kotlin
  DrawerGroup(
      titleRes = R.string.drawer__my_stuff,
      collapsible = true,
      items = listOf(
          DrawerItemConfig(R.id.drawer_my_sensors, R.drawable.ic_my_paired_devices, R.string.devices_myRemoteDevices),
          DrawerItemConfig(R.id.drawer_equipment, R.drawable.ic_equipment_combined, R.string.equipment_management_title)
      )
  )
  ```
- Update group 5 (`R.string.drawer__settings`):
  ```kotlin
  DrawerGroup(
      titleRes = R.string.drawer__settings,
      collapsible = true,
      items = listOf(
          DrawerItemConfig(R.id.drawer_units, R.drawable.ic_square_foot, R.string.prefsUnitsTitle),
          DrawerItemConfig(R.id.drawer_display_settings, R.drawable.ic_display_settings, R.string.Display),
          DrawerItemConfig(R.id.drawer_tracking_layouts, R.drawable.ic_table_edit, R.string.prefsConfigureDisplaysTitle),
          DrawerItemConfig(R.id.drawer_sport_types, R.drawable.ic_sports_combined, R.string.sport_types),
          DrawerItemConfig(R.id.drawer_backup_restore, R.drawable.ic_save_to_disc, R.string.import_backup),
          DrawerItemConfig(R.id.drawer_privacy_policy, R.drawable.ic_privacy, R.string.privacy_policy),
          DrawerItemConfig(R.id.drawer_advanced_tuning, R.drawable.ic_tune, R.string.advanced_tuning_title)
      )
  )
  ```

### Component 2: 9-Locale String Resources (`app/src/main/res/values*/strings.xml`)
Update `R.string.equipment_management_title` across all 9 canonical resource files with proper XML entity escaping (`&amp;`):

| Locale | Resource Path | String Value |
| :---: | :--- | :--- |
| **EN** (Base) | `app/src/main/res/values/strings.xml` | `<string name="equipment_management_title">Bikes &amp; Shoes</string>` |
| **DE** | `app/src/main/res/values-de/strings.xml` | `<string name="equipment_management_title">Räder &amp; Schuhe</string>` |
| **ES** | `app/src/main/res/values-es/strings.xml` | `<string name="equipment_management_title">Bicicletas y zapatillas</string>` |
| **FR** | `app/src/main/res/values-fr/strings.xml` | `<string name="equipment_management_title">Vélos et chaussures</string>` |
| **IT** | `app/src/main/res/values-it/strings.xml` | `<string name="equipment_management_title">Bici e scarpe</string>` |
| **JA** | `app/src/main/res/values-ja/strings.xml` | `<string name="equipment_management_title">バイク＆シューズ</string>` |
| **NL** | `app/src/main/res/values-nl/strings.xml` | `<string name="equipment_management_title">Fietsen &amp; schoenen</string>` |
| **PL** | `app/src/main/res/values-pl/strings.xml` | `<string name="equipment_management_title">Rowery i buty</string>` |
| **PT** | `app/src/main/res/values-pt/strings.xml` | `<string name="equipment_management_title">Bicicletas e calçado</string>` |

### Component 3: Unit, Contract & Localization Verification Suites
1. **`NavigationDrawerStateTest.kt`**:
   - Update `testDrawerItemStructureIntegrity()`:
     - `myStuffGroup.items.map { it.id }` asserted against `listOf(R.id.drawer_my_sensors, R.id.drawer_equipment)` (2 items).
     - `settingsGroup.items.map { it.id }` asserted against `listOf(R.id.drawer_units, R.id.drawer_display_settings, R.id.drawer_tracking_layouts, R.id.drawer_sport_types, R.id.drawer_backup_restore, R.id.drawer_privacy_policy, R.id.drawer_advanced_tuning)` (7 items).
     - Assert `settingsIds[3] == R.id.drawer_sport_types` and `settingsIds.last() == R.id.drawer_advanced_tuning`.
     - Retain invariant assertions: total items = 22, visible items = 9.
   - Update `testCollapsibleDrawerInitialExpansionAndConsolidatedSelection()`:
     - Assert `isDrawerGroupInitiallyExpanded(settingsGroup, R.id.drawer_sport_types)` returns `true`.
     - Assert `isDrawerGroupInitiallyExpanded(myStuffGroup, R.id.drawer_sport_types)` returns `false`.
     - Assert `isDrawerGroupInitiallyExpanded(myStuffGroup, ...)` returns `true` for `drawer_my_sensors`, `drawer_equipment`, `drawer_bikes`, and `drawer_shoes`.
2. **`NavigationDrawerLocalizationTest.kt`**:
   - New test suite parsing all 9 `strings.xml` files and asserting that `equipment_management_title` equals the exact localized string and does not contain raw unescaped `&` characters.
3. **`EquipmentTabsScreenContractTest.kt`**:
   - Contract test asserting that `EquipmentTabsScreen.kt` references `R.string.equipment_management_title` for its top header text composable, preserving 1:1 parity with the drawer item label.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `AppNavigationDrawer.kt` (navigation drawer) and `EquipmentTabsScreen.kt` (screen header).
* **Reused components**: Standard `DrawerGroup`, `DrawerItemConfig`, `AppNavigationDrawer`, and `EquipmentTabsScreen` header layout.
* **Theme tokens**: Surfaces, texts, and icons utilize standard `MaterialTheme.colorScheme` tokens (`onPrimaryContainer`, `onSurface`, `primaryContainer`, `surfaceContainerHighest`) and standard typography (`MaterialTheme.typography.titleLarge`).
* **New one-off styles & justification**: None. Reuses 100% of the existing declarative Compose structure, icons (`ic_sports_combined`, `ic_equipment_combined`), and color roles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Relocate 'Sport Types' Item in `AppNavigationDrawer.kt` (`REQ-UI-422.1`, `REQ-UI-422.2`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt`
* **Changes**:
  - Remove `DrawerItemConfig(R.id.drawer_sport_types, R.drawable.ic_sports_combined, R.string.sport_types)` from `R.string.drawer__my_stuff` group (reducing items from 3 to 2).
  - Add `DrawerItemConfig(R.id.drawer_sport_types, R.drawable.ic_sports_combined, R.string.sport_types)` to `R.string.drawer__settings` group directly after `R.id.drawer_tracking_layouts` (increasing items from 6 to 7).

### Step 2: Update 9-Locale XML String Resources (`REQ-UI-422.3`)
* **Target Files**:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
* **Changes**:
  - Update `<string name="equipment_management_title">` in each file to the specified localized string with XML entity escaping (`&amp;` for EN, DE, NL).

### Step 3: Update Navigation Drawer State Machine Unit Test (`REQ-UI-422.1`, `REQ-UI-422.2`, `REQ-UI-422.4`, `REQ-UI-422.5`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/NavigationDrawerStateTest.kt`
* **Changes**:
  - In `testDrawerItemStructureIntegrity()`:
    - Update `myStuffIds` assertion to expect `listOf(R.id.drawer_my_sensors, R.id.drawer_equipment)`.
    - Update `settingsIds` assertion to expect 7 items with `R.id.drawer_sport_types` at index 3.
  - In `testCollapsibleDrawerInitialExpansionAndConsolidatedSelection()`:
    - Add assertion that selecting `R.id.drawer_sport_types` expands `settingsGroup` and leaves `myStuffGroup` collapsed.

### Step 4: Add Drawer Localization & Screen Header Parity Contract Tests (`REQ-UI-422.3`)
* **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/NavigationDrawerLocalizationTest.kt` (new)
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentTabsScreenContractTest.kt` (new)
* **Changes**:
  - Verify all 9 locales contain non-empty, validly escaped translations for `equipment_management_title`.
  - Verify `EquipmentTabsScreen.kt` references `R.string.equipment_management_title` for the screen header.

### Step 5: Execute Targeted Unit & Contract Tests (`TST-UI-385.1` – `TST-UI-385.4`)
* **Commands**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.NavigationDrawerStateTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.NavigationDrawerLocalizationTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.equipment.EquipmentTabsScreenContractTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
  ```

---

## 6. Verification & Rollback Plan

### Verification Plan
1. **Targeted Unit Test Verification**:
   - `NavigationDrawerStateTest`: Confirms group items count, list ordering, unique item IDs (22), and auto-expansion behavior.
   - `NavigationDrawerLocalizationTest`: Confirms 9-locale translation accuracy and entity escaping.
   - `EquipmentTabsScreenContractTest`: Confirms header title binding parity.
   - `TranslationParityTest`: Confirms overall translation parity.
2. **Clean-Room Regression Suite**:
   - Run full `./gradlew testDebugUnitTest` to guarantee 100% pass rate with zero regressions across existing components.
3. **Build & APK Assembly Check**:
   - Run `./gradlew assembleDebug` to guarantee resource compilation and DEX generation succeed cleanly.

### Rollback Plan
- All changes are contained within the Git feature branch `feature/ATT-4289`.
- In the event of an unexpected anomaly or review rejection, the branch can be cleanly reset or discarded using `git checkout sprint/2026-43.1` and `git branch -D feature/ATT-4289` without affecting the active sprint integration branch or `develop`.
