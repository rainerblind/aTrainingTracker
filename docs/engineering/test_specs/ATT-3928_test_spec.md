# Stage 2: Requirement & Test Specification - ATT-3928: Move 'Profile & Zones' to Training drawer section and make 'My Stuff' collapsible by default

**Ticket**: [ATT-3928](https://rainerblind.atlassian.net/browse/ATT-3928)  
**Sub-task**: [ATT-3946](https://rainerblind.atlassian.net/browse/ATT-3946) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Unassigned (Assigned upon Final Review acceptance per Rule 14)`  
**Active Sprint**: `2026-42.8`  
**Requirement ID**: `REQ-UI-405`  
**Test ID**: `TST-UI-368`  
**Branch**: `feature/ATT-3928`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Formal Requirement Specification (`REQ-UI-405`)

**Title**: Profile & Zones Placement in Training Drawer Section and Collapsible My Stuff Section.

The system SHALL place **Profile & Zones** (`R.id.drawer_athlete_profile`) at the bottom of the **Training** drawer section (`R.string.drawer__training`) and configure the **My Stuff** drawer section (`R.string.drawer__my_stuff`) as collapsible by default (`collapsible = true`) in `AppNavigationDrawer.kt` (`createDrawerGroups`) (ATT-3928, amending `REQ-UI-391.1`, `REQ-UI-397.1`, and `REQ-UI-397.4`):

1. **Training Drawer Section (`REQ-UI-405.1`)**:
   - The `DrawerGroup` with `titleRes == R.string.drawer__training` in `createDrawerGroups(startTrackingTitleRes: Int)` SHALL have `collapsible = false` and contain strictly 4 destination items (`DrawerItemConfig`) in order:
     1. `R.id.drawer_start_tracking` (`R.drawable.control_start`, `startTrackingTitleRes`)
     2. `R.id.drawer_workouts` (`R.drawable.workout_list`, `R.string.tab_workouts`)
     3. `R.id.drawer_periods` (`R.drawable.ic_calendar_month`, `R.string.workout_periods__periods`)
     4. `R.id.drawer_athlete_profile` (`R.drawable.ic_athlete_profile`, `R.string.drawer_athlete_profile`)
2. **Collapsible My Stuff Gear Section (`REQ-UI-405.2`)**:
   - The `DrawerGroup` with `titleRes == R.string.drawer__my_stuff` in `createDrawerGroups(startTrackingTitleRes: Int)` SHALL have `collapsible = true` and contain strictly 3 hardware/gear destination items (`DrawerItemConfig`) in order:
     1. `R.id.drawer_my_sensors` (`R.drawable.ic_my_paired_devices`, `R.string.devices_myRemoteDevices`)
     2. `R.id.drawer_equipment` (`R.drawable.ic_equipment_combined`, `R.string.equipment_management_title`)
     3. `R.id.drawer_sport_types` (`R.drawable.ic_sports_combined`, `R.string.sport_types`)
3. **Default Visible Destination Item Density & Dynamic Auto-Expansion (`REQ-UI-405.3`)**:
   - When the 3 collapsible secondary sections (`R.string.drawer__my_stuff` = 3 items, `R.string.prefsOnlineCommunities` = 4 items, `R.string.drawer__settings` = 6 items; `13` hidden destination items total) are collapsed, the 2 non-collapsible primary drawer sections (`R.string.drawer__training` = 4 destination items, `R.string.drawer__maps` = 5 destination items) SHALL yield strictly `9` visible `DrawerItemConfig` destination rows (`groups.filter { !it.collapsible }.sumOf { it.items.size } == 9`, separate from the `5` always-visible category header rows; `9 + 13 = 22` total defined `DrawerItemConfig` items across all `5` groups).
   - `isDrawerGroupInitiallyExpanded(group, selectedItemId)` SHALL evaluate `!group.collapsible || group.items.any { isDrawerItemSelected(it.id, selectedItemId) }`, returning `false` for `R.string.drawer__my_stuff` when a non-My-Stuff destination is selected, and `true` dynamically for every `item.id` in `myStuffGroup.items` as well as consolidated equipment aliases (`R.id.drawer_bikes`, `R.id.drawer_shoes`).

### Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
1. **Original Requirement ID & Target**:
   - Net-new requirement `REQ-UI-405`, amending `REQ-UI-391.1` (`ATT-3704`), `REQ-UI-397.1`, and `REQ-UI-397.4` (`ATT-3851`). Targets `AppNavigationDrawer.kt`, `NavigationDrawerStateTest.kt`, and `AthleteProfileContractAndLocalizationTest.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-42.6 (`ATT-3704`, commit `b660ef43`) and Sprint 2026-42.7 (`ATT-3851`, `REQ-UI-397`).
3. **Root Reason for Existing Formulation (Why was this fence built?)**:
   - `Profile & Zones` initially resided in `My Stuff` (`collapsible = false`) when it only managed static body weight and FTP entries, prior to `ATT-3859` adding the longitudinal Performance Management Chart (`PMC`: `CTL`, `ATL`, `TSB`) and `ATT-3851` unifying it with Training Zones.
4. **Preservation of Core Invariants (Why is it safe to modify now?)**:
   - Moving `R.id.drawer_athlete_profile` into `R.string.drawer__training` aligns daily training & analytics destinations while leaving `R.string.drawer__my_stuff` with pure hardware/gear setup items (`My Sensors`, `Equipment`, `Sport Types`) that can safely default to collapsed (`collapsible = true`). Total groups (`5`), total unique destinations (`22`), route mappings (`NavRoutes.ATHLETE_PROFILE`, `NavRoutes.EQUIPMENT`), deep-link selection matching (`isDrawerItemSelected`), and 9-locale parity remain 100% preserved.

### Acceptance Criteria (Given-When-Then)
- **Given** `createDrawerGroups(R.string.tab_start)`, **When** queried by `titleRes`, **Then** the `R.string.drawer__training` group has `collapsible = false` and 4 destination items ending with `R.id.drawer_athlete_profile`, the `R.string.drawer__my_stuff` group has `collapsible = true` and 3 destination items (`[drawer_my_sensors, drawer_equipment, drawer_sport_types]`), and `groups.filter { !it.collapsible }.sumOf { it.items.size } == 9` (excluding the 5 section header rows).
- **Given** `myStuffGroup = groups.first { it.titleRes == R.string.drawer__my_stuff }`, **When** `isDrawerGroupInitiallyExpanded(myStuffGroup, selectedItemId)` is evaluated, **Then** it returns `false` for `R.id.drawer_start_tracking` and `true` exhaustively for every `item.id` in `myStuffGroup.items` plus `R.id.drawer_bikes` and `R.id.drawer_shoes`.

---

## 2. Test Specification (`TST-UI-368`)

### `TST-UI-368.1`: Drawer Structure, Training Group Ordering & Collapsible My Stuff Tests (`NavigationDrawerStateTest.kt`, `AthleteProfileContractAndLocalizationTest.kt`)
- Query `createDrawerGroups(R.string.tab_start)` by `titleRes` (`groups.first { it.titleRes == ... }`) rather than brittle index.
- Verify `R.string.drawer__training` has `collapsible == false` and places `R.id.drawer_athlete_profile` (`R.drawable.ic_athlete_profile`, `R.string.drawer_athlete_profile`) as its 4th destination item (`[drawer_start_tracking, drawer_workouts, drawer_periods, drawer_athlete_profile]`).
- Verify `R.string.drawer__my_stuff` has `collapsible == true` and contains strictly 3 destination items: `[R.id.drawer_my_sensors, R.id.drawer_equipment, R.id.drawer_sport_types]`.
- Verify non-collapsible groups (`Training` = 4 destination items, `Maps` = 5 destination items) yield `9` default visible `DrawerItemConfig` rows (`groups.filter { !it.collapsible }.sumOf { it.items.size } == 9`, separate from the `5` section header rows) and collapsible groups (`My Stuff` = 3, `Online Communities` = 4, `Settings` = 6) yield `13` hidden destination items (`9 + 13 = 22` total unique items across `5` groups).
- Verify `isDrawerGroupInitiallyExpanded(myStuffGroup, selectedItemId)` returns `false` when `R.id.drawer_start_tracking` is selected and iterates exhaustively over `myStuffGroup.items` (plus consolidated equipment aliases `R.id.drawer_bikes` and `R.id.drawer_shoes`) asserting `true` for every item.

### `TST-UI-368.2`: 9-Language Localization Parity Audit (`AthleteProfileContractAndLocalizationTest.kt`)
- Verify `drawer_athlete_profile` exists across all 9 supported application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

### `TST-UI-368.3`: Clean-Room Full Suite Regression Execution
- Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

---

## 3. Bidirectional Traceability Matrix

| Requirement Clause | Test Case ID | Verification Artifact |
| :--- | :--- | :--- |
| `REQ-UI-405.1` | `TST-UI-368.1`, `TST-UI-368.2` | `NavigationDrawerStateTest.kt`, `AthleteProfileContractAndLocalizationTest.kt` |
| `REQ-UI-405.2`, `REQ-UI-405.3` | `TST-UI-368.1` | `NavigationDrawerStateTest.kt` |
| `REQ-PRO-001` | `TST-UI-368.3` | `./gradlew testDebugUnitTest` |
