# Stage 2: Requirement & Test Specification - ATT-4289: Move 'Sport Types' drawer item to Settings and rename 'Equipment' item to 'Bikes & Shoes'

**Ticket**: [ATT-4289](https://rainerblind.atlassian.net/browse/ATT-4289)  
**Sub-task**: [ATT-4321](https://rainerblind.atlassian.net/browse/ATT-4321) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-UI-422` (*Relocate 'Sport Types' drawer item to Settings and rename 'Equipment' item to 'Bikes & Shoes'*)  
**Test Spec ID**: `TST-UI-385`  
**Branch**: `feature/ATT-4289`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification (REQ-UI-422)

### 1.1 Problem Statement & Rationale

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

---

### 1.2 Functional & Architectural Requirements

1. **Hardware-Only Equipment Drawer Hub (`REQ-UI-422.1`)**:
   - In `AppNavigationDrawer.kt`, `createDrawerGroups(startTrackingTitleRes: Int)` SHALL configure the `DrawerGroup` for `R.string.drawer__my_stuff` (`collapsible = true`) to contain strictly two destination items (`DrawerItemConfig`) in exact order:
     1. `R.id.drawer_my_sensors` (`R.drawable.ic_my_paired_devices`, `R.string.devices_myRemoteDevices`)
     2. `R.id.drawer_equipment` (`R.drawable.ic_equipment_combined`, `R.string.equipment_management_title`)
   - `R.id.drawer_sport_types` SHALL be excised completely from `R.string.drawer__my_stuff`.

2. **Sport Types Placement in Settings Hub (`REQ-UI-422.2`)**:
   - In `AppNavigationDrawer.kt`, `createDrawerGroups(startTrackingTitleRes: Int)` SHALL position `DrawerItemConfig(R.id.drawer_sport_types, R.drawable.ic_sports_combined, R.string.sport_types)` inside the `R.string.drawer__settings` group (`collapsible = true`), placed immediately after `R.id.drawer_tracking_layouts`.
   - The resulting `R.string.drawer__settings` group SHALL contain strictly seven destination items (`DrawerItemConfig`) in logical sequence:
     1. `R.id.drawer_units` (*Units* / *Einheiten* — `R.drawable.ic_square_foot`, `R.string.prefsUnitsTitle`)
     2. `R.id.drawer_display_settings` (*Display* / *Anzeige* — `R.drawable.ic_display_settings`, `R.string.Display`)
     3. `R.id.drawer_tracking_layouts` (*Tracking Layouts* / *Cockpit-Ansichten* — `R.drawable.ic_table_edit`, `R.string.prefsConfigureDisplaysTitle`)
     4. `R.id.drawer_sport_types` (*Sport Types* / *Sportarten* — `R.drawable.ic_sports_combined`, `R.string.sport_types`)
     5. `R.id.drawer_backup_restore` (*Backup & Restore* / *Sicherung & Wiederherstellung* — `R.drawable.ic_save_to_disc`, `R.string.import_backup`)
     6. `R.id.drawer_privacy_policy` (*Privacy Policy* / *Datenschutzerklärung* — `R.drawable.ic_privacy`, `R.string.privacy_policy`)
     7. `R.id.drawer_advanced_tuning` (*Advanced Tuning* / *Erweiterte Abstimmung* — `R.drawable.ic_tune`, `R.string.advanced_tuning_title`)

3. **Rename Equipment Title to "Bikes & Shoes" Across All 9 Locales (`REQ-UI-422.3`)**:
   - The string resource `R.string.equipment_management_title` SHALL be updated across all 9 canonical application locales (`values/strings.xml`, `values-de/strings.xml`, `values-es/strings.xml`, `values-fr/strings.xml`, `values-it/strings.xml`, `values-ja/strings.xml`, `values-nl/strings.xml`, `values-pl/strings.xml`, `values-pt/strings.xml`) to denote *"Bikes & Shoes"* (*"Räder & Schuhe"*).
   - In `values/strings.xml`, `values-de/strings.xml`, and `values-nl/strings.xml`, literal ampersand characters SHALL use standard XML entity escaping (`&amp;`).
   - The screen title in `EquipmentTabsScreen.kt` (`stringResource(R.string.equipment_management_title)`) SHALL reflect this updated string, ensuring 1:1 parity between the navigation drawer destination row and the screen header.

4. **Drawer State Machine & Auto-Expansion Invariant (`REQ-UI-422.4`)**:
   - Evaluating `isDrawerGroupInitiallyExpanded(settingsGroup, R.id.drawer_sport_types)` SHALL return `true`.
   - Evaluating `isDrawerGroupInitiallyExpanded(myStuffGroup, R.id.drawer_sport_types)` SHALL return `false`.
   - Selecting any item in `myStuffGroup` (`drawer_my_sensors`, `drawer_equipment`) or its deep-link aliases (`drawer_bikes`, `drawer_shoes`) SHALL continue to return `true` for `isDrawerGroupInitiallyExpanded(myStuffGroup, ...)`.

5. **Preservation of System Invariants (`REQ-UI-422.5`)**:
   - **Total Category Hubs**: The navigation drawer SHALL retain strictly 5 groups (`Training`, `Maps`, `My Stuff`, `Online Communities`, `Settings`).
   - **Total Navigation Destinations**: The navigation drawer SHALL retain strictly 22 unique destinations across all 5 groups ($4 + 5 + 2 + 4 + 7 = 22$).
   - **Default Visible Destinations Density**: When secondary collapsible groups are collapsed, the 2 non-collapsible primary drawer groups (`Training` = 4, `Maps` = 5) SHALL yield strictly 9 visible destination rows (`groups.filter { !it.collapsible }.sumOf { it.items.size } == 9`).
   - **Route Integrity & Deep-Link Aliases**: Navigation routes in `NavRoutes.kt` (`EQUIPMENT`, `SPORT_TYPES`, `BIKES`, `SHOES`) and alias selection matching in `isDrawerItemSelected` SHALL remain 100% backward compatible.
   - **Clean-Room Test Pass Rate**: 100% unit and contract test pass rate under `./gradlew testDebugUnitTest`.

---

### 1.3 9-Locale Localization Audit Table for `equipment_management_title`

The complete 9-locale localization parity table for `R.string.equipment_management_title`:

| Locale Code | Res Directory | Language | XML Value (`<string name="equipment_management_title">...`) | UX Rationale & Verification Notes |
| :---: | :--- | :--- | :--- | :--- |
| **EN** | `values/strings.xml` | English (Base) | `Bikes &amp; Shoes` | Replaces generic "Equipment" with specific gear nouns; XML entity escaped ampersand. |
| **DE** | `values-de/strings.xml` | German | `Räder &amp; Schuhe` | Eliminates duplicate "Ausrüstung" child under "Ausrüstung" hub; XML entity escaped ampersand. |
| **ES** | `values-es/strings.xml` | Spanish | `Bicicletas y zapatillas` | Natural Spanish sports terminology for cycling and running gear. |
| **FR** | `values-fr/strings.xml` | French | `Vélos et chaussures` | Standard French sports nomenclature. |
| **IT** | `values-it/strings.xml` | Italian | `Bici e scarpe` | Standard Italian athletic gear terminology. |
| **JA** | `values-ja/strings.xml` | Japanese | `バイク＆シューズ` | Full-width katakana notation standard for Japanese mobile UI. |
| **NL** | `values-nl/strings.xml` | Dutch | `Fietsen &amp; schoenen` | Standard Dutch sports terminology; XML entity escaped ampersand. |
| **PL** | `values-pl/strings.xml` | Polish | `Rowery i buty` | Standard Polish athletic gear terminology. |
| **PT** | `values-pt/strings.xml` | Portuguese | `Bicicletas e calçado` | Standard Portuguese sports terminology. |

---

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1: Equipment Hub Item Count and Cleanliness**:
  * *Given* the application navigation drawer rendered with `createDrawerGroups(R.string.tab_start)`,
  * *When* the `R.string.drawer__my_stuff` group is inspected,
  * *Then* it has `collapsible = true` and contains strictly 2 items: `R.id.drawer_my_sensors` and `R.id.drawer_equipment`.
  * *And* `R.id.drawer_sport_types` is absent from `R.string.drawer__my_stuff`.

* **Criterion 2: Sport Types Placement in Settings Hub**:
  * *Given* the application navigation drawer rendered with `createDrawerGroups(R.string.tab_start)`,
  * *When* the `R.string.drawer__settings` group is inspected,
  * *Then* it has `collapsible = true` and contains strictly 7 items with `R.id.drawer_sport_types` positioned at index 3, directly between `R.id.drawer_tracking_layouts` (index 2) and `R.id.drawer_backup_restore` (index 4).

* **Criterion 3: Dynamic Auto-Expansion for Relocated Sport Types**:
  * *Given* `isDrawerGroupInitiallyExpanded(group, selectedItemId)` evaluated with `selectedItemId = R.id.drawer_sport_types`,
  * *When* evaluated against the `Settings` group,
  * *Then* it returns `true`.
  * *When* evaluated against the `My Stuff` group,
  * *Then* it returns `false`.

* **Criterion 4: Equipment Item Renaming & Screen Header Parity**:
  * *Given* `R.string.equipment_management_title` loaded across each of the 9 supported locales,
  * *When* displayed in the navigation drawer or `EquipmentTabsScreen`,
  * *Then* the label reads "Bikes & Shoes" (or localized equivalent, e.g. "Räder & Schuhe" in German), eliminating the duplicate "Ausrüstung" header-child label.

* **Criterion 5: Structural & Density Invariants**:
  * *Given* `createDrawerGroups(R.string.tab_start)`,
  * *When* counting total items and visible items,
  * *Then* the total unique item count across all 5 groups is strictly 22, and the visible destination count across non-collapsible groups (`Training` + `Maps`) is strictly 9.

---

### 1.5 System Invariants

1. **Category Hub Count Invariant**: Exactly 5 drawer groups (`Training`, `Maps`, `My Stuff`, `Online Communities`, `Settings`).
2. **Total Navigation Item Invariant**: Exactly 22 unique `DrawerItemConfig` destination IDs across the entire drawer ($4 + 5 + 2 + 4 + 7 = 22$).
3. **Default Visible Destination Density Invariant**: Exactly 9 destination items visible when secondary collapsible groups are closed ($4 + 5 = 9$).
4. **Deep-Link & Selection Alias Invariant**: `isDrawerItemSelected(R.id.drawer_equipment, ...)` continues to match `drawer_equipment`, `drawer_bikes`, and `drawer_shoes`.
5. **Localization Parity Invariant**: 100% translation coverage across all 9 canonical app locales with zero missing keys and valid XML entity escaping.
6. **Parent Ticket Human Decision Gate (Rule 3)**: Advancing parent ticket `ATT-4289` to `Erledigt` is an inviolable Human Decision Gate reserved exclusively for the human user during Ceremony 2 (Joint Review).

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Net-new requirement `REQ-UI-422`, amending `REQ-UI-405.2` and `REQ-UI-405.3` (`ATT-3928`), which previously amended `REQ-UI-397.1` (`ATT-3851`).
   - Targets `AppNavigationDrawer.kt`, `NavigationDrawerStateTest.kt`, `EquipmentTabsScreen.kt`, and `strings.xml` (all 9 locales).

2. **Historical Origin & Commit Trace**:
   - `ATT-3851` (commit `04b6b158`, Sprint 2026-42.7): Restructured the legacy flat navigation drawer into 5 category hubs, consolidated Bikes and Shoes into a single tabbed destination `R.id.drawer_equipment` (`R.string.equipment_management_title`), and placed `drawer_athlete_profile`, `drawer_my_sensors`, `drawer_equipment`, and `drawer_sport_types` in `R.string.drawer__my_stuff`.
   - `ATT-3928` (commit `4623bb6f`, Sprint 2026-42.8): Moved `drawer_athlete_profile` out of `My Stuff` into `Training`, leaving `My Stuff` with 3 items (`my_sensors`, `equipment`, `sport_types`) and configuring `My Stuff` as collapsible (`collapsible = true`).

3. **Root Reason for Existing Formulation**:
   - `drawer_sport_types` was historically left in `My Stuff` because earlier versions viewed athlete configuration (sports, sensors, bikes, shoes) as personal equipment settings before the distinction between physical hardware (sensors, bikes, shoes) and application taxonomy (sport types, speed thresholds) was formalized.
   - `equipment_management_title` was given the title *"Equipment"* (*"Ausrüstung"*) when Bikes and Shoes were first merged into a tabbed screen. However, this produced the redundant *"Ausrüstung"* $\rightarrow$ *"Ausrüstung"* hierarchy when placed inside the *"Ausrüstung"* category hub.

4. **Preservation of Core Invariants**:
   - The 5 drawer category hubs are strictly preserved.
   - Total unique navigation destinations across the drawer remain exactly 22 ($4 + 5 + 2 + 4 + 7 = 22$).
   - Default visible items count across non-collapsible sections when secondary sections are collapsed remains strictly 9 (Training: 4, Maps: 5).
   - Deep-link matching via `isDrawerItemSelected` for `drawer_equipment`, `drawer_bikes`, and `drawer_shoes` is 100% preserved.
   - Auto-expansion via `isDrawerGroupInitiallyExpanded` continues to correctly expand the group holding the active destination.
   - 100% 9-language localization parity and clean-room unit test pass rate are maintained.

---

## 2. Test Specification (TST-UI-385)

### Test Case 1: Drawer Structure & Group Item Count Integrity Unit Test (`[TST-UI-385.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/NavigationDrawerStateTest.kt`
* **Method**: `testDrawerItemStructureIntegrity()`
* **Preconditions**:
  - `val groups = createDrawerGroups(R.string.tab_start)`
* **Action**:
  - Extract `myStuffGroup.items.map { it.id }`.
  - Extract `settingsGroup.items.map { it.id }`.
  - Compute total items `groups.flatMap { it.items.map { it.id } }`.
  - Compute visible items `groups.filter { !it.collapsible }.sumOf { it.items.size }`.
* **Expected Result**:
  - `myStuffIds` equals `listOf(R.id.drawer_my_sensors, R.id.drawer_equipment)` (size 2).
  - `settingsIds` equals `listOf(R.id.drawer_units, R.id.drawer_display_settings, R.id.drawer_tracking_layouts, R.id.drawer_sport_types, R.id.drawer_backup_restore, R.id.drawer_privacy_policy, R.id.drawer_advanced_tuning)` (size 7).
  - `settingsIds[3]` equals `R.id.drawer_sport_types`.
  - `settingsIds.last()` equals `R.id.drawer_advanced_tuning`.
  - Total item count across all groups equals 22.
  - All 22 item IDs are distinct (unique).
  - Default visible items count across non-collapsible groups (`Training` = 4, `Maps` = 5) equals 9.

### Test Case 2: Drawer Auto-Expansion & Alias Selection State Unit Test (`[TST-UI-385.2]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/NavigationDrawerStateTest.kt`
* **Method**: `testCollapsibleDrawerInitialExpansionAndConsolidatedSelection()`
* **Preconditions**:
  - `val groups = createDrawerGroups(R.string.tab_start)`
  - `val myStuffGroup = groups.first { it.titleRes == R.string.drawer__my_stuff }`
  - `val settingsGroup = groups.first { it.titleRes == R.string.drawer__settings }`
* **Action**:
  - Assert `isDrawerGroupInitiallyExpanded(settingsGroup, R.id.drawer_sport_types)`.
  - Assert `!isDrawerGroupInitiallyExpanded(myStuffGroup, R.id.drawer_sport_types)`.
  - Assert `isDrawerGroupInitiallyExpanded(myStuffGroup, R.id.drawer_my_sensors)`.
  - Assert `isDrawerGroupInitiallyExpanded(myStuffGroup, R.id.drawer_equipment)`.
  - Assert `isDrawerGroupInitiallyExpanded(myStuffGroup, R.id.drawer_bikes)`.
  - Assert `isDrawerGroupInitiallyExpanded(myStuffGroup, R.id.drawer_shoes)`.
  - Assert `isDrawerItemSelected(R.id.drawer_equipment, R.id.drawer_equipment)`.
  - Assert `isDrawerItemSelected(R.id.drawer_equipment, R.id.drawer_bikes)`.
  - Assert `isDrawerItemSelected(R.id.drawer_equipment, R.id.drawer_shoes)`.
* **Expected Result**:
  - Selecting `R.id.drawer_sport_types` auto-expands `settingsGroup` and leaves `myStuffGroup` collapsed.
  - Selecting `drawer_equipment`, `drawer_bikes`, or `drawer_shoes` auto-expands `myStuffGroup` and resolves `isDrawerItemSelected` to `true`.

### Test Case 3: 9-Locale Localization & Entity Escaping Audit Test (`[TST-UI-385.3]`)
* **Scope**: JVM Localization & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt` & `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/NavigationDrawerLocalizationTest.kt`
* **Goal**: Verify that `R.string.equipment_management_title` is defined across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with exact expected translations and proper XML entity escaping (`&amp;`).
* **Expected Result**:
  - Base `values/strings.xml`: `Bikes &amp; Shoes`
  - `values-de/strings.xml`: `Räder &amp; Schuhe`
  - `values-es/strings.xml`: `Bicicletas y zapatillas`
  - `values-fr/strings.xml`: `Vélos et chaussures`
  - `values-it/strings.xml`: `Bici e scarpe`
  - `values-ja/strings.xml`: `バイク＆シューズ`
  - `values-nl/strings.xml`: `Fietsen &amp; schoenen`
  - `values-pl/strings.xml`: `Rowery i buty`
  - `values-pt/strings.xml`: `Bicicletas e calçado`
  - Zero missing entries, zero unescaped `&` characters, 100% translation parity.

### Test Case 4: Screen Header Parity Contract Test (`[TST-UI-385.4]`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentTabsScreenContractTest.kt`
* **Goal**: Verify that `EquipmentTabsScreen.kt` references `R.string.equipment_management_title` for its top header, guaranteeing identical text between the navigation drawer destination row and the screen header.
* **Expected Result**:
  - `EquipmentTabsScreen.kt` source code binds `stringResource(R.string.equipment_management_title)` to the screen header `Text` composable.

### Test Case 5: Clean-Room Regression Suite Execution (`[TST-UI-385.5]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the complete unit and contract test suite with zero regressions.
* **Expected Result**: `BUILD SUCCESSFUL` with 100% tests passing.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-385.1]` | Unit | `NavigationDrawerStateTest.testDrawerItemStructureIntegrity` | `REQ-UI-422.1`, `REQ-UI-422.2`, `REQ-UI-422.5` | Specified |
| `[TST-UI-385.2]` | Unit | `NavigationDrawerStateTest.testCollapsibleDrawerInitialExpansionAndConsolidatedSelection` | `REQ-UI-422.4`, `REQ-UI-422.5` | Specified |
| `[TST-UI-385.3]` | Localization | `TranslationParityTest` & 9-Locale Resource Audit | `REQ-UI-422.3`, `REQ-LOC-001` | Specified |
| `[TST-UI-385.4]` | Contract | `EquipmentTabsScreenContractTest` | `REQ-UI-422.3` | Specified |
| `[TST-UI-385.5]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001`, `REQ-UI-422.5` | Specified |
