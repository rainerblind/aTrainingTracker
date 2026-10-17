# Stage 3: Architecture & Implementation Plan - ATT-3928: Move 'Profile & Zones' to Training drawer section and make 'My Stuff' collapsible by default

**Ticket**: [ATT-3928](https://rainerblind.atlassian.net/browse/ATT-3928)  
**Sub-task**: `ATT-3947` (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Unassigned (Assigned upon Final Review acceptance per Rule 14)`  
**Active Sprint**: `2026-42.8`  
**Requirement Mapping**: `REQ-UI-405`  
**Test Mapping**: `TST-UI-368`  
**Branch**: `feature/ATT-3928`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Architectural Overview & SWE.2 Design

This plan implements `REQ-UI-405` by updating the declarative Navigation Drawer hierarchy in `AppNavigationDrawer.kt` (`createDrawerGroups`):

1. **`R.string.drawer__training` Group (`REQ-UI-405.1`)**:
   - Retains `collapsible = false`.
   - Appends `DrawerItemConfig(R.id.drawer_athlete_profile, R.drawable.ic_athlete_profile, R.string.drawer_athlete_profile)` as the 4th destination item:
     1. `R.id.drawer_start_tracking` (`R.drawable.control_start`, `startTrackingTitleRes`)
     2. `R.id.drawer_workouts` (`R.drawable.workout_list`, `R.string.tab_workouts`)
     3. `R.id.drawer_periods` (`R.drawable.ic_calendar_month`, `R.string.workout_periods__periods`)
     4. `R.id.drawer_athlete_profile` (`R.drawable.ic_athlete_profile`, `R.string.drawer_athlete_profile`)
2. **`R.string.drawer__my_stuff` Group (`REQ-UI-405.2`)**:
   - Configures `collapsible = true` (defaulting to collapsed unless the active `selectedItemId` belongs to `My Stuff` via `isDrawerGroupInitiallyExpanded`).
   - Retains strictly 3 hardware/gear destination items:
     1. `R.id.drawer_my_sensors` (`R.drawable.ic_my_paired_devices`, `R.string.devices_myRemoteDevices`)
     2. `R.id.drawer_equipment` (`R.drawable.ic_equipment_combined`, `R.string.equipment_management_title`)
     3. `R.id.drawer_sport_types` (`R.drawable.ic_sports_combined`, `R.string.sport_types`)

---

## 2. UI Consistency (Rule 23 — `docs/design_guidelines.md` Section 5)

- **Reference Screen / Component**: `AppNavigationDrawer.kt` (`DrawerGroupView`, `DrawerItemView`).
- **Shapes, Spacing & Typography**: Reuses existing `DrawerGroupView` collapsible accordion header (`16.dp` horizontal padding, `8.dp` vertical padding, `14.sp` `FontWeight.SemiBold` primary header text with `20.dp` `Icons.Default.ExpandLess` / `ExpandMore` chevron) and `40.dp` compact `DrawerItemView` rows. Zero new styles, colors, or shapes introduced.

---

## 3. Atomic Implementation Steps

### Step 1: Update `createDrawerGroups` in `AppNavigationDrawer.kt`
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt`
- Move `DrawerItemConfig(R.id.drawer_athlete_profile, R.drawable.ic_athlete_profile, R.string.drawer_athlete_profile)` from `R.string.drawer__my_stuff` to the bottom of `R.string.drawer__training`.
- Set `collapsible = true` on `DrawerGroup(titleRes = R.string.drawer__my_stuff, ...)`.
- Update KDoc references on `DrawerGroup`, `isDrawerGroupInitiallyExpanded`, and `createDrawerGroups` to reference `REQ-UI-405`.

### Step 2: Update `NavigationDrawerStateTest.kt` and `AthleteProfileContractAndLocalizationTest.kt`
- **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/NavigationDrawerStateTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileContractAndLocalizationTest.kt`
- Query groups by `titleRes` (`groups.first { it.titleRes == ... }`).
- Verify `R.string.drawer__training` has `collapsible == false` and 4 items ending with `R.id.drawer_athlete_profile`.
- Verify `R.string.drawer__my_stuff` has `collapsible == true` and 3 items (`[drawer_my_sensors, drawer_equipment, drawer_sport_types]`).
- Verify default visible `DrawerItemConfig` count across non-collapsible groups is `9` (`4 + 5`, excluding the `5` section header rows) and total unique items across all 5 groups is `22`.
- Verify `isDrawerGroupInitiallyExpanded(myStuffGroup, selectedItemId)` returns `false` for `R.id.drawer_start_tracking` and iterates exhaustively over `myStuffGroup.items` plus `R.id.drawer_bikes` and `R.id.drawer_shoes` asserting `true`.
- Update `AthleteProfileContractAndLocalizationTest` to verify `R.id.drawer_athlete_profile` is the 4th item in `R.string.drawer__training` and verify 9-locale parity for `drawer_athlete_profile`.

---

## 4. Invariants & Verification Plan

- **Preserved Invariants**:
  - `5` category groups and `22` unique destination IDs.
  - `isDrawerItemSelected` deep-link matching for `R.id.drawer_athlete_profile` (`drawer_training_zones`) and `R.id.drawer_equipment` (`drawer_bikes`, `drawer_shoes`).
- **Stage 4 Targeted Test Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteProfileContractAndLocalizationTest"
  ```
