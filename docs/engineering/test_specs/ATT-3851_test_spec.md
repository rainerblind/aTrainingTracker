# Stage 2: Requirement & Test Specification - ATT-3851: Restructure and declutter Navigation Drawer items and sections

**Ticket**: [ATT-3851](https://rainerblind.atlassian.net/browse/ATT-3851)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `2026-42.7`  
**Target Release**: `V4.9.40`  
**Requirement Mapping**: `REQ-UI-397`  
**Test Mapping**: `TST-UI-360`  
**Branch**: `feature/ATT-3851`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Formal Requirement Specification (`REQ-UI-397`)

### `REQ-UI-397`: Restructured 5-Group Navigation Drawer, Consolidated Equipment & Profile/Zones Destinations, and Collapsible Secondary Sections
The system SHALL declutter and restructure the main Navigation Drawer (`AppNavigationDrawer.kt`, `NavRoutes.kt`, `ATrainingTrackerApp.kt`, `AthleteProfileScreen.kt`, `ZonesSettingsActivity.kt`) into 5 groups with 12 permanently visible everyday items and 2 collapsible secondary configuration sections (`ATT-3851`, amending `REQ-UI-159` and extending `REQ-UI-391`):

1. **Consolidated "My Stuff" Group in Athlete -> Gear Order (`REQ-UI-397.1`)**:
   - In `createDrawerGroups(startTrackingTitleRes: Int)`, the `R.string.drawer__my_stuff` group SHALL contain strictly 4 items in Athlete -> Gear order:
     1. `DrawerItemConfig(R.id.drawer_athlete_profile, R.drawable.ic_athlete_profile, R.string.drawer_athlete_profile)` (**Profile & Zones** / **Profil & Zonen**)
     2. `DrawerItemConfig(R.id.drawer_my_sensors, R.drawable.ic_my_paired_devices, R.string.devices_myRemoteDevices)` (**My Sensors** / **Meine Sensoren**)
     3. `DrawerItemConfig(R.id.drawer_equipment, R.drawable.ic_equipment_combined, R.string.equipment_management_title)` (**Equipment** / **Ausrüstung**)
     4. `DrawerItemConfig(R.id.drawer_sport_types, R.drawable.ic_sports_combined, R.string.sport_types)` (**Sport Types** / **Sportarten**)
   - `R.id.drawer_equipment` (`res/values/ids.xml`) SHALL map bidirectionally to `NavRoutes.EQUIPMENT = "equipment"` in `NavRoutes.fromDrawerItemId` and `NavRoutes.toDrawerItemId`, and `ATrainingTrackerApp.kt` SHALL host `EquipmentTabsScreen(viewModel = equipmentViewModel, initialTab = 0, ...)` at `composable(NavRoutes.EQUIPMENT)` while preserving `NavRoutes.BIKES` and `NavRoutes.SHOES` mappings for backward compatibility.
   - `isDrawerItemSelected(itemId: Int, selectedItemId: Int)` SHALL treat `R.id.drawer_athlete_profile` as selected when `selectedItemId` is `R.id.drawer_athlete_profile` or `R.id.drawer_training_zones`, and `R.id.drawer_equipment` as selected when `selectedItemId` is `R.id.drawer_equipment`, `R.id.drawer_bikes`, or `R.id.drawer_shoes`.

2. **Unified Tabbed "Profile & Zones" Screen (`REQ-UI-397.2`)**:
   - `AthleteProfileScreen` (`AthleteProfileScreen.kt`) SHALL render a unified top header (`R.string.athlete_profile_title`) and a Material 3 `PrimaryScrollableTabRow` + `HorizontalPager` hosting 4 tabs:
     - Tab 0: **Profile** (`R.string.athlete_profile_tab_profile`, rendering the Athlete Profile `LazyColumn` with Weight, Resting HR, FTP, and `W/kg` summary cards, time-series plot, and history log, with the `+` `IconButton` in the top header).
     - Tab 1: **HR Run** (`R.string.tab_hr_run`, rendering the Running Heart Rate zone configuration via `ZoneProfileTabContent` from `ZonesSettingsActivity.kt`, with the `Icons.Default.Calculate` `IconButton` in the top header).
     - Tab 2: **HR Bike** (`R.string.tab_hr_bike`, rendering the Cycling Heart Rate zone configuration via `ZoneProfileTabContent`, with the `Icons.Default.Calculate` `IconButton` in the top header).
     - Tab 3: **Power Bike** (`R.string.tab_pwr_bike`, rendering the Cycling Power zone configuration via `ZoneProfileTabContent`, with the `Icons.Default.Calculate` `IconButton` in the top header).

3. **Merged "Advanced Tuning" in "Settings" Group (`REQ-UI-397.3`)**:
   - `R.id.drawer_advanced_tuning` (`R.drawable.ic_tune`, `R.string.advanced_tuning_title`) SHALL be moved into the `R.string.drawer__settings` group (`Units`, `Display`, `Configure Displays`, `Import & Backup`, `Advanced Tuning`, `Privacy Policy` — 6 items total).
   - The standalone 1-item `R.string.drawer__expert_settings` group SHALL be removed from `createDrawerGroups`.

4. **Collapsible Accordion Sections for "Online Communities" & "Settings" (`REQ-UI-397.4`)**:
   - `DrawerGroup` SHALL declare `val collapsible: Boolean = false`.
   - The top 3 everyday groups (`R.string.drawer__training`, `R.string.drawer__maps`, `R.string.drawer__my_stuff` — 12 items total) SHALL have `collapsible = false` (permanently expanded).
   - `R.string.prefsOnlineCommunities` (4 items) and `R.string.drawer__settings` (6 items) SHALL have `collapsible = true`.
   - `isDrawerGroupInitiallyExpanded(group: DrawerGroup, selectedItemId: Int): Boolean` SHALL return `true` when `!group.collapsible`, and when `group.collapsible` SHALL return `group.items.any { isDrawerItemSelected(it.id, selectedItemId) }` (collapsed by default, auto-expanded when the active `selectedItemId` belongs to that group).
   - In `DrawerGroupView`, collapsible group headers SHALL be clickable (`Modifier.fillMaxWidth().clickable`), display a trailing chevron icon (`Icons.Default.ExpandLess` when expanded, `Icons.Default.ExpandMore` when collapsed), and conditionally render their child `DrawerItemView` rows only when expanded.

5. **9-Language Localization Parity (`REQ-UI-397.5` — Rule 29)**:
   - `drawer_athlete_profile`, `athlete_profile_title`, and `athlete_profile_tab_profile` SHALL exist and be non-blank across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

---

## 2. Acceptance Criteria (Given-When-Then)

1. **Consolidated "My Stuff" Group & Ordering (`REQ-UI-397.1`)**:
   - *Given* `createDrawerGroups(R.string.tab_start)`,
   - *When* inspecting `R.string.drawer__my_stuff`,
   - *Then* it contains 4 items in exact order: `[R.id.drawer_athlete_profile, R.id.drawer_my_sensors, R.id.drawer_equipment, R.id.drawer_sport_types]`.
2. **Unified Tabbed "Profile & Zones" Screen (`REQ-UI-397.2`)**:
   - *Given* `AthleteProfileScreen` opened via `NavRoutes.ATHLETE_PROFILE`,
   - *When* rendered,
   - *Then* it hosts a `PrimaryScrollableTabRow` and `HorizontalPager` with 4 tabs (`athlete_profile_tab_profile`, `tab_hr_run`, `tab_hr_bike`, `tab_pwr_bike`), displaying the `+` action on Tab 0 and `Icons.Default.Calculate` on Tabs 1..3.
3. **Merged "Advanced Tuning" & Collapsible Sections (`REQ-UI-397.3`, `REQ-UI-397.4`)**:
   - *Given* `createDrawerGroups(R.string.tab_start)`,
   - *When* inspecting all groups,
   - *Then* there are 5 groups (`drawer__training`, `drawer__maps`, `drawer__my_stuff`, `prefsOnlineCommunities`, `drawer__settings`), `drawer__expert_settings` is absent, `drawer_advanced_tuning` is inside `drawer__settings`, the first 3 groups have `collapsible == false`, and the last 2 groups have `collapsible == true` (collapsed by default when `selectedItemId == R.id.drawer_start_tracking` and auto-expanded when `selectedItemId` belongs to that group).

---

## 3. Test Specification (`TST-UI-360`)

| Test Case ID | Test Class & Method | Requirement | Verification Description |
| :--- | :--- | :--- | :--- |
| `TST-UI-360.1` | `NavigationDrawerStateTest` (`testDrawerItemStructureIntegrity`, `testCollapsibleDrawerGroupsAndAutoExpansion`, `testConsolidatedItemSelectionMatching`) | `REQ-UI-397.1`, `REQ-UI-397.3`, `REQ-UI-397.4` | Verify `createDrawerGroups` returns 5 groups (3 non-collapsible with 12 items, 2 collapsible with 10 items), `My Stuff` has 4 items in Athlete->Gear order, `Settings` includes `drawer_advanced_tuning`, and `isDrawerGroupInitiallyExpanded` / `isDrawerItemSelected` behave deterministically. |
| `TST-UI-360.2` | `SingleActivityNavigationTest` (`testDrawerItemIdToRouteMapping`, `testRouteToDrawerItemIdMapping`) | `REQ-UI-397.1` | Verify `R.id.drawer_equipment` <-> `NavRoutes.EQUIPMENT` mapping alongside preserved backward-compatible mappings. |
| `TST-UI-360.3` | `AthleteProfileContractAndLocalizationTest` | `REQ-UI-397.2`, `REQ-UI-397.5` | Verify `AthleteProfileScreen.kt` hosts `PrimaryScrollableTabRow` and `HorizontalPager` with `athlete_profile_tab_profile`, `tab_hr_run`, `tab_hr_bike`, `tab_pwr_bike` and `ZoneProfileTabContent`, and verify all 9 locales contain `drawer_athlete_profile`, `athlete_profile_title`, and `athlete_profile_tab_profile`. |
| `TST-UI-360.4` | Clean-Room Full Suite (`./gradlew testDebugUnitTest`) | `REQ-UI-397` | Verify 100% pass across all unit tests with zero regressions. |
