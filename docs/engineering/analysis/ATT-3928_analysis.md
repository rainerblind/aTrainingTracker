# Stage 1: Problem Domain & Root Cause Analysis - ATT-3928: Move 'Profile & Zones' to Training drawer section and make 'My Stuff' collapsible by default

**Ticket**: [ATT-3928](https://rainerblind.atlassian.net/browse/ATT-3928)  
**Sub-task**: [ATT-3945](https://rainerblind.atlassian.net/browse/ATT-3945) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Unassigned (Assigned upon Final Review acceptance per Rule 14)`  
**Active Sprint**: `2026-42.8`  
**Branch**: `feature/ATT-3928`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Executive Summary & Problem Statement

During the Sprint 2026-42.7 Joint Review of `ATT-3851` (`REQ-UI-397`), a natural UX refinement was identified for the Navigation Drawer hierarchy (`AppNavigationDrawer.kt`):

1. **'Profile & Zones' Belongs in Daily 'Training'**:
   - Following `ATT-3851` (unified Profile & Zones screen), `ATT-3859` (Performance Management Chart: Fitness `CTL`, Fatigue `ATL`, Form `TSB`), and `ATT-3861` (Critical Power `CP` & `W'`), **Profile & Zones** (`R.id.drawer_athlete_profile`) is part of the daily training & analytics loop alongside *Start Tracking*, *Workouts*, and *Stats & Periods*.
   - Moving `R.id.drawer_athlete_profile` from `R.string.drawer__my_stuff` to the bottom of `R.string.drawer__training` gives `Training` 4 items: `Start Tracking`, `Workouts`, `Stats & Periods`, and `Profile & Zones`.
2. **'My Stuff' Becomes a Pure Gear/Setup Section & Collapsible by Default**:
   - With `Profile & Zones` moved to `Training`, `R.string.drawer__my_stuff` (*My Stuff* / *Meine Ausrüstung*) contains strictly infrequent hardware/gear setup items: `My Sensors` (`R.id.drawer_my_sensors`), `Equipment` (`R.id.drawer_equipment`), and `Sport Types` (`R.id.drawer_sport_types`).
   - Like `Online Communities` and `Settings`, `My Stuff` should be configured with `collapsible = true` so it defaults to collapsed (unless the currently active destination belongs to `My Stuff`), further reducing default drawer visual density from 12 visible items to **9 default visible items** (`Training` = 4, `Maps` = 5).

---

## 2. Forensic Codebase & Gap Analysis

### 2.1 Navigation Drawer Definition (`AppNavigationDrawer.kt`)
- In `createDrawerGroups(startTrackingTitleRes: Int)` (`AppNavigationDrawer.kt` lines 182–237):
  - `DrawerGroup(titleRes = R.string.drawer__training, collapsible = false, items = ...)` currently has 3 items: `drawer_start_tracking`, `drawer_workouts`, `drawer_periods`.
  - `DrawerGroup(titleRes = R.string.drawer__my_stuff, collapsible = false, items = ...)` currently has `collapsible = false` and 4 items: `drawer_athlete_profile`, `drawer_my_sensors`, `drawer_equipment`, `drawer_sport_types`.
- **Required Change**:
  - Append `DrawerItemConfig(R.id.drawer_athlete_profile, R.drawable.ic_athlete_profile, R.string.drawer_athlete_profile)` as the 4th item of `R.string.drawer__training`.
  - Remove `drawer_athlete_profile` from `R.string.drawer__my_stuff` (leaving 3 items: `drawer_my_sensors`, `drawer_equipment`, `drawer_sport_types`) and set `collapsible = true` on the `R.string.drawer__my_stuff` `DrawerGroup`.

### 2.2 Affected Unit & Contract Tests
- `NavigationDrawerStateTest.kt`:
  - `testDrawerItemStructureIntegrity`:
    - `groups[2]` (`drawer__my_stuff`) now has `collapsible == true` (alongside `groups[3]` `prefsOnlineCommunities` and `groups[4]` `drawer__settings`).
    - `groups[0]` (`drawer__training`) has 4 items ending with `R.id.drawer_athlete_profile`.
    - `groups[2]` (`drawer__my_stuff`) has 3 items: `[R.id.drawer_my_sensors, R.id.drawer_equipment, R.id.drawer_sport_types]`.
    - Default visible items across non-collapsible groups (`!it.collapsible`) is now `9` (`4 + 5`), while total items across all 5 groups remains `22`.
  - `testCollapsibleDrawerInitialExpansionAndConsolidatedSelection`:
    - Verify `isDrawerGroupInitiallyExpanded(myStuffGroup, R.id.drawer_start_tracking)` is `false`, and `isDrawerGroupInitiallyExpanded(myStuffGroup, R.id.drawer_equipment)` / `R.id.drawer_bikes` / `R.id.drawer_my_sensors` is `true`.
    - Verify `isDrawerGroupInitiallyExpanded(trainingGroup, R.id.drawer_athlete_profile)` is `true`.
- `AthleteProfileContractAndLocalizationTest.kt`:
  - Line 49 currently checks `val myStuffGroup = groups.first { it.titleRes == R.string.drawer__my_stuff }` for `R.id.drawer_athlete_profile`. Update this assertion to verify `R.id.drawer_athlete_profile` is in `R.string.drawer__training` (as its 4th item).

---

## 3. Chesterton's Fence & Requirement Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   - Amends `REQ-UI-391.1` (`ATT-3704`) and `REQ-UI-397.1` / `REQ-UI-397.4` (`ATT-3851`) via net-new requirement `REQ-UI-405`.
   - Targets `AppNavigationDrawer.kt`, `NavigationDrawerStateTest.kt`, and `AthleteProfileContractAndLocalizationTest.kt`.
2. **Historical Origin & Commit Trace**:
   - `ATT-3704` (`b660ef43`, Sprint 2026-42.6) introduced `R.id.drawer_athlete_profile` under `R.string.drawer__my_stuff`.
   - `ATT-3851` (`REQ-UI-397`, Sprint 2026-42.7) consolidated `Profile & Zones` into a 4-tab screen and made `Online Communities` and `Settings` collapsible while keeping `My Stuff` expanded with 4 items.
3. **Root Reason for Existing Formulation**:
   - `Athlete Profile` originally started as a body weight / FTP settings screen before `ATT-3859` added the longitudinal Performance Management Chart (`PMC`) and `ATT-3851` unified it with Training Zones. During the Sprint 2026-42.7 Joint Review, the human user noted that `Profile & Zones` is now part of daily training & analytics (`Training` section), leaving `My Stuff` as pure hardware/gear configuration that can be collapsed by default.
4. **Preservation of Core Invariants**:
   - Total drawer groups (`5`) and total unique drawer destinations (`22`) remain unchanged.
   - Bidirectional route mapping (`R.id.drawer_athlete_profile <-> NavRoutes.ATHLETE_PROFILE`, `R.id.drawer_equipment <-> NavRoutes.EQUIPMENT`), deep-link selection matching (`isDrawerItemSelected`), and automatic accordion expansion when a child item is selected (`isDrawerGroupInitiallyExpanded`) remain 100% preserved.

---

## 4. User Scope Grounding (`ATT-1250`)

### In-Scope
1. Move `DrawerItemConfig(R.id.drawer_athlete_profile, R.drawable.ic_athlete_profile, R.string.drawer_athlete_profile)` from `R.string.drawer__my_stuff` to the bottom of `R.string.drawer__training` in `AppNavigationDrawer.kt`.
2. Set `collapsible = true` on `DrawerGroup(titleRes = R.string.drawer__my_stuff, ...)` in `AppNavigationDrawer.kt`.
3. Update `NavigationDrawerStateTest.kt` and `AthleteProfileContractAndLocalizationTest.kt` to verify the updated `Training` (4 items, non-collapsible) and `My Stuff` (3 items, collapsible) structure and `9` default visible items.

### Out-of-Scope
- Changing any routes in `NavRoutes.kt` or screen composables in `ATrainingTrackerApp.kt`.
- Adding or removing any drawer items (total count remains 22).
