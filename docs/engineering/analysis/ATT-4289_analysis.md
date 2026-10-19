# Stage 1 Analysis: ATT-4289 - Move 'Sport Types' drawer item to Settings and rename 'Equipment' item to 'Bikes & Shoes'

**Ticket**: [ATT-4289](https://rainerblind.atlassian.net/browse/ATT-4289)  
**Sub-task**: [ATT-4320](https://rainerblind.atlassian.net/browse/ATT-4320) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Branch**: `feature/ATT-4289`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

The navigation drawer in **aTrainingTracker** (`AppNavigationDrawer.kt`, `createDrawerGroups`) is organized into five functional category hubs: *Training*, *Maps*, *Equipment* (*My Stuff*), *Online Communities*, and *Settings*. Following `ATT-3851` (consolidating Bikes & Shoes into `R.id.drawer_equipment`) and `ATT-3928` (moving *Profile & Zones* to *Training* and configuring `R.string.drawer__my_stuff` as collapsible), the third drawer category hub (`R.string.drawer__my_stuff`, localized as *"Equipment"* in English and *"Ausrüstung"* in German) exhibits two UX friction points:

1. **Semantic Category Mismatch**:
   - `R.id.drawer_sport_types` (*Sport Types* / *Sportarten*) is a configuration screen for athletic disciplines, speed calculation thresholds, and activity taxonomy. It does not represent physical hardware or gear.
   - Placing *Sport Types* in the *Equipment* (*Ausrüstung*) section alongside *My Sensors* and *Equipment* violates the athlete's mental model. In contrast, the *Settings* (*Einstellungen*) section already consolidates configuration destinations such as *Units*, *Display*, *Tracking Layouts* (*Cockpit-Ansichten*), *Backup & Restore*, *Privacy Policy*, and *Advanced Tuning*. Moving *Sport Types* into *Settings* directly after *Tracking Layouts* naturally unifies all tracking layout and activity taxonomy configuration.

2. **Duplicate Category & Item Label (*"Ausrüstung"* $\rightarrow$ *"Ausrüstung"*)**:
   - The drawer section header `R.string.drawer__my_stuff` is localized as *"Equipment"* (*"Ausrüstung"*), and its child destination `R.id.drawer_equipment` (`R.string.equipment_management_title`) is also localized as *"Equipment"* (*"Ausrüstung"*).
   - Expanding the section reveals a child row with the identical label as its parent section header.
   - Furthermore, `R.id.drawer_equipment` opens `EquipmentTabsScreen`, which hosts the consolidated *Bikes* (*Räder*) and *Shoes* (*Schuhe*) tabs. Renaming `R.string.equipment_management_title` to *"Bikes & Shoes"* (*"Räder & Schuhe"*) accurately reflects the actual gear managed on that screen and eliminates the redundant, confusing label repetition across all 9 supported application locales.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Navigation Drawer Hierarchy & Group Construction
In `AppNavigationDrawer.kt`, `createDrawerGroups(startTrackingTitleRes: Int)` constructs the 5 drawer groups. The third group (`R.string.drawer__my_stuff`) currently defines 3 items:
```kotlin
DrawerGroup(
    titleRes = R.string.drawer__my_stuff,
    collapsible = true,
    items = listOf(
        DrawerItemConfig(R.id.drawer_my_sensors, R.drawable.ic_my_paired_devices, R.string.devices_myRemoteDevices),
        DrawerItemConfig(R.id.drawer_equipment, R.drawable.ic_equipment_combined, R.string.equipment_management_title),
        DrawerItemConfig(R.id.drawer_sport_types, R.drawable.ic_sports_combined, R.string.sport_types)
    )
)
```
And the fifth group (`R.string.drawer__settings`) defines 6 items:
```kotlin
DrawerGroup(
    titleRes = R.string.drawer__settings,
    collapsible = true,
    items = listOf(
        DrawerItemConfig(R.id.drawer_units, R.drawable.ic_square_foot, R.string.prefsUnitsTitle),
        DrawerItemConfig(R.id.drawer_display_settings, R.drawable.ic_display_settings, R.string.Display),
        DrawerItemConfig(R.id.drawer_tracking_layouts, R.drawable.ic_table_edit, R.string.prefsConfigureDisplaysTitle),
        DrawerItemConfig(R.id.drawer_backup_restore, R.drawable.ic_save_to_disc, R.string.import_backup),
        DrawerItemConfig(R.id.drawer_privacy_policy, R.drawable.ic_privacy, R.string.privacy_policy),
        DrawerItemConfig(R.id.drawer_advanced_tuning, R.drawable.ic_tune, R.string.advanced_tuning_title)
    )
)
```

By removing `DrawerItemConfig(R.id.drawer_sport_types, ...)` from `drawer__my_stuff` and inserting it into `drawer__settings` right after `R.id.drawer_tracking_layouts`:
- `drawer__my_stuff` strictly holds 2 physical hardware destinations (`drawer_my_sensors` and `drawer_equipment`).
- `drawer__settings` contains 7 items in logical progression:
  1. `R.id.drawer_units` (*Units* / *Einheiten*)
  2. `R.id.drawer_display_settings` (*Display* / *Anzeige*)
  3. `R.id.drawer_tracking_layouts` (*Tracking Layouts* / *Cockpit-Ansichten*)
  4. `R.id.drawer_sport_types` (*Sport Types* / *Sportarten*)
  5. `R.id.drawer_backup_restore` (*Backup & Restore* / *Sicherung & Wiederherstellung*)
  6. `R.id.drawer_privacy_policy` (*Privacy Policy* / *Datenschutzerklärung*)
  7. `R.id.drawer_advanced_tuning` (*Advanced Tuning* / *Erweiterte Abstimmung*)

### 2.2 Screen Header Parity in `EquipmentTabsScreen.kt`
In `EquipmentTabsScreen.kt`, line 167 renders `stringResource(R.string.equipment_management_title)` as the top-level screen header:
```kotlin
Text(
    text = stringResource(R.string.equipment_management_title),
    style = MaterialTheme.typography.titleLarge,
    color = MaterialTheme.colorScheme.onPrimaryContainer
)
```
Updating `R.string.equipment_management_title` automatically synchronizes the screen title with the drawer item label, clearly signaling to the athlete that this screen manages their bikes and running shoes.

### 2.3 9-Locale Resource Audit
In `app/src/main/res/values*/strings.xml`, `equipment_management_title` is currently defined as:
- `values`: `Equipment`
- `values-de`: `Ausrüstung`
- `values-es`: `Equipamiento`
- `values-fr`: `Équipement`
- `values-it`: `Attrezzatura`
- `values-ja`: `装備管理`
- `values-nl`: `Materiaal`
- `values-pl`: `Sprzęt`
- `values-pt`: `Equipamento`

Updating this key across all 9 locales to the specified strings using XML entity escaping (`&amp;`):
- `en` (`values`): `Bikes &amp; Shoes`
- `de` (`values-de`): `Räder &amp; Schuhe`
- `es` (`values-es`): `Bicicletas y zapatillas`
- `fr` (`values-fr`): `Vélos et chaussures`
- `it` (`values-it`): `Bici e scarpe`
- `ja` (`values-ja`): `バイク＆シューズ`
- `nl` (`values-nl`): `Fietsen &amp; schoenen`
- `pl` (`values-pl`): `Rowery i buty`
- `pt` (`values-pt`): `Bicicletas e calçado`

### 2.4 State Machine & Unit Test Call Sites
In `NavigationDrawerStateTest.kt`:
- `testDrawerItemStructureIntegrity()`:
  - Expects `myStuffGroup.items.map { it.id }` to equal `[drawer_my_sensors, drawer_equipment, drawer_sport_types]`. Must be updated to `[drawer_my_sensors, drawer_equipment]`.
  - Expects `settingsGroup.items` to have 6 items with `drawer_advanced_tuning` at the end. Must be updated to verify 7 items with `drawer_sport_types` placed right after `drawer_tracking_layouts` (index 3) and `drawer_advanced_tuning` at index 6.
  - Overall total items count remains invariant: $4 + 5 + 2 + 4 + 7 = 22$.
  - Default visible items count across non-collapsible groups (`Training` = 4, `Maps` = 5) remains strictly 9.
- `testCollapsibleDrawerInitialExpansionAndConsolidatedSelection()`:
  - Iterates over `myStuffGroup.items` and asserts auto-expansion of `myStuffGroup`.
  - With `drawer_sport_types` moved to `settingsGroup`, selecting `drawer_sport_types` must now expand `settingsGroup` and leave `myStuffGroup` collapsed.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Relocate `DrawerItemConfig(R.id.drawer_sport_types, R.drawable.ic_sports_combined, R.string.sport_types)` from `R.string.drawer__my_stuff` to `R.string.drawer__settings` in `AppNavigationDrawer.kt` (`createDrawerGroups`), positioning it directly after `R.id.drawer_tracking_layouts`.
  * Restrict `R.string.drawer__my_stuff` to strictly 2 physical equipment destinations: `R.id.drawer_my_sensors` and `R.id.drawer_equipment`.
  * Update `R.string.equipment_management_title` across all 9 localized `strings.xml` resource files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) to *"Bikes & Shoes"* (*"Räder & Schuhe"*) with valid XML entity escaping (`&amp;`).
  * Update unit test assertions in `NavigationDrawerStateTest.kt` to enforce the new group counts (My Stuff = 2, Settings = 7) and group auto-expansion behavior.
  * Add unit test coverage verifying that `R.string.equipment_management_title` exists and is non-empty across all 9 supported application locales.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to navigation routes in `NavRoutes.kt` or route handlers in `ATrainingTrackerApp.kt` (`NavRoutes.EQUIPMENT`, `NavRoutes.SPORT_TYPES`, `NavRoutes.BIKES`, `NavRoutes.SHOES` remain unchanged).
  * No modification to the internal implementation or tab layouts of `EquipmentTabsScreen.kt` or `SportTypesScreen` / `SportTypesActivity`.
  * No modification to icon drawables (`ic_equipment_combined`, `ic_sports_combined`, `ic_my_paired_devices`).
  * No alteration of other drawer sections (`drawer__training`, `drawer__maps`, `prefsOnlineCommunities`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

<!-- Required if any existing requirement in docs/requirements.md is to be modified (REQ-PRO-022). -->

* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-422`, amending `REQ-UI-405.2` and `REQ-UI-405.3` (`ATT-3928`), which previously amended `REQ-UI-397.1` (`ATT-3851`). Targets `AppNavigationDrawer.kt`, `NavigationDrawerStateTest.kt`, `EquipmentTabsScreen.kt`, and `strings.xml` (all 9 locales).
* **Historical Origin & Commit Trace**:
  - `ATT-3851` (commit `04b6b158`, Sprint 2026-42.7): Restructured the legacy flat navigation drawer into 5 category hubs, consolidated Bikes and Shoes into a single tabbed destination `R.id.drawer_equipment` (`R.string.equipment_management_title`), and placed `drawer_athlete_profile`, `drawer_my_sensors`, `drawer_equipment`, and `drawer_sport_types` in `R.string.drawer__my_stuff`.
  - `ATT-3928` (commit `4623bb6f`, Sprint 2026-42.8): Moved `drawer_athlete_profile` out of `My Stuff` into `Training`, leaving `My Stuff` with 3 items (`my_sensors`, `equipment`, `sport_types`) and configuring `My Stuff` as collapsible (`collapsible = true`).
* **Root Reason for Existing Formulation**:
  - `drawer_sport_types` was historically left in `My Stuff` because earlier versions viewed athlete configuration (sports, sensors, bikes, shoes) as personal equipment settings before the distinction between physical hardware (sensors, bikes, shoes) and application taxonomy (sport types, speed thresholds) was formalized.
  - `equipment_management_title` was given the title *"Equipment"* (*"Ausrüstung"*) when Bikes and Shoes were first merged into a tabbed screen. However, this produced the redundant *"Ausrüstung"* $\rightarrow$ *"Ausrüstung"* hierarchy when placed inside the *"Ausrüstung"* category hub.
* **Preservation of Core Invariants**:
  - The 5 drawer category hubs are strictly preserved.
  - Total unique navigation destinations across the drawer remain exactly 22 ($4 + 5 + 2 + 4 + 7 = 22$).
  - Default visible items count across non-collapsible sections when secondary sections are collapsed remains strictly 9 (Training: 4, Maps: 5).
  - Deep-link matching via `isDrawerItemSelected` for `drawer_equipment`, `drawer_bikes`, and `drawer_shoes` is 100% preserved.
  - Auto-expansion via `isDrawerGroupInitiallyExpanded` continues to correctly expand the group holding the active destination.
  - 100% 9-language localization parity and clean-room unit test pass rate are maintained.

---

## 5. Architectural Strategy & High-Level Solution

1. **`AppNavigationDrawer.kt`**:
   - In `createDrawerGroups(startTrackingTitleRes: Int)`:
     - Remove `DrawerItemConfig(R.id.drawer_sport_types, R.drawable.ic_sports_combined, R.string.sport_types)` from the `R.string.drawer__my_stuff` group.
     - Add `DrawerItemConfig(R.id.drawer_sport_types, R.drawable.ic_sports_combined, R.string.sport_types)` into the `R.string.drawer__settings` group immediately after `R.id.drawer_tracking_layouts`.

2. **9-Locale XML String Resources (`app/src/main/res/values*/strings.xml`)**:
   - Update `equipment_management_title` across all 9 files:
     - `values/strings.xml`: `<string name="equipment_management_title">Bikes &amp; Shoes</string>`
     - `values-de/strings.xml`: `<string name="equipment_management_title">Räder &amp; Schuhe</string>`
     - `values-es/strings.xml`: `<string name="equipment_management_title">Bicicletas y zapatillas</string>`
     - `values-fr/strings.xml`: `<string name="equipment_management_title">Vélos et chaussures</string>`
     - `values-it/strings.xml`: `<string name="equipment_management_title">Bici e scarpe</string>`
     - `values-ja/strings.xml`: `<string name="equipment_management_title">バイク＆シューズ</string>`
     - `values-nl/strings.xml`: `<string name="equipment_management_title">Fietsen &amp; schoenen</string>`
     - `values-pl/strings.xml`: `<string name="equipment_management_title">Rowery i buty</string>`
     - `values-pt/strings.xml`: `<string name="equipment_management_title">Bicicletas e calçado</string>`

3. **`NavigationDrawerStateTest.kt`**:
   - Update `testDrawerItemStructureIntegrity()`:
     - `myStuffGroup.items.map { it.id }` asserted against `listOf(R.id.drawer_my_sensors, R.id.drawer_equipment)` (2 items).
     - `settingsGroup.items.map { it.id }` asserted against:
       `listOf(R.id.drawer_units, R.id.drawer_display_settings, R.id.drawer_tracking_layouts, R.id.drawer_sport_types, R.id.drawer_backup_restore, R.id.drawer_privacy_policy, R.id.drawer_advanced_tuning)` (7 items).
     - Verify total items remains 22.
   - Update `testCollapsibleDrawerInitialExpansionAndConsolidatedSelection()`:
     - Verify `isDrawerGroupInitiallyExpanded(settingsGroup, R.id.drawer_sport_types)` returns `true`.
     - Verify `isDrawerGroupInitiallyExpanded(myStuffGroup, R.id.drawer_sport_types)` returns `false`.
   - Add verification test ensuring `equipment_management_title` is non-empty across all 9 locales.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing navigation routes, deep links, or drawer state machine.
  2. Total drawer group count (5) and unique items count (22) remain invariant.
  3. Non-collapsible default visible destination items count remains strictly 9.
  4. All 9 supported locales have non-empty, grammatically correct translations with XML entity escaping (`&amp;`).
  5. Parent ticket Human Decision Gate remains strictly enforced.

* **Risk Rating**: **LOW**
  - **Justification**: Changes are strictly confined to declarative Jetpack Compose drawer configuration, string resources, and unit test assertions. There are zero database schema alterations, zero background service lifecycle changes, and zero thread concurrency modifications.
