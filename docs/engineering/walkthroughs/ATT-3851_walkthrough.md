# Stage 5: Verification & Walkthrough - ATT-3851: Restructure and declutter Navigation Drawer items and sections

**Ticket**: [ATT-3851](https://rainerblind.atlassian.net/browse/ATT-3851)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `2026-42.7`  
**Requirement Mapping**: `REQ-UI-397` (`Verified`)  
**Test Mapping**: `TST-UI-360` (`Verified`)  
**Branch**: `feature/ATT-3851`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Summary of Verified Changes

1. **Consolidated "My Stuff" Section in Athlete -> Gear Order (`REQ-UI-397.1`)**:
   - Reduced `R.string.drawer__my_stuff` in [`AppNavigationDrawer.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt) from 6 items to 4 items ordered from Athlete to Gear:
     1. **Profile & Zones** (`R.id.drawer_athlete_profile`, `R.drawable.ic_athlete_profile`, `R.string.drawer_athlete_profile`)
     2. **My Sensors** (`R.id.drawer_my_sensors`, `R.drawable.ic_my_paired_devices`, `R.string.devices_myRemoteDevices`)
     3. **Equipment** (`R.id.drawer_equipment`, `R.drawable.ic_equipment_combined`, `R.string.equipment_management_title`)
     4. **Sport Types** (`R.id.drawer_sport_types`, `R.drawable.ic_sports_combined`, `R.string.sport_types`)
   - Added `R.id.drawer_equipment` (`ids.xml`) and `NavRoutes.EQUIPMENT = "equipment"` (`NavRoutes.kt`, `ATrainingTrackerApp.kt`) opening `EquipmentTabsScreen(initialTab = 0)`, while preserving `NavRoutes.BIKES` and `NavRoutes.SHOES` for backward-compatible deep links.

2. **Unified Tabbed "Profile & Zones" Screen (`REQ-UI-397.2`)**:
   - Exposed `@Composable fun ZoneProfileTabContent(...)` in [`ZonesSettingsActivity.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt) while leaving `ZoneSettingsScreen` intact for onboarding deep links (`NavRoutes.TRAINING_ZONES_PATTERN`).
   - Updated [`AthleteProfileScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileScreen.kt) to host a 4-tab `PrimaryScrollableTabRow` + `HorizontalPager`:
     - **Profile** (`R.string.athlete_profile_tab_profile`, with `+` header action and Rule 41 bottom navigation bar inset clearance)
     - **HR Run** (`R.string.tab_hr_run`, with `Icons.Default.Calculate` header action)
     - **HR Bike** (`R.string.tab_hr_bike`, with `Icons.Default.Calculate` header action)
     - **Power Bike** (`R.string.tab_pwr_bike`, with `Icons.Default.Calculate` header action)

3. **Merged "Advanced Tuning" & Collapsible Secondary Sections (`REQ-UI-397.3`, `REQ-UI-397.4`)**:
   - Merged `R.id.drawer_advanced_tuning` into the bottom of `R.string.drawer__settings` and removed the standalone `R.string.drawer__expert_settings` group (reducing total groups from 6 to 5).
   - Configured `prefsOnlineCommunities` and `drawer__settings` with `collapsible = true` (collapsed by default so only the 12 everyday items across `Training`, `Maps`, and `My Stuff` are visible, auto-expanding when `selectedItemId` belongs to the group, with clickable header and trailing chevron `Icons.Default.ExpandLess` / `ExpandMore`).

4. **9-Language Localization Parity (`REQ-UI-397.5`)**:
   - Updated `drawer_athlete_profile`, `athlete_profile_title`, and `athlete_profile_tab_profile` across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

---

## 2. Verification Results (`TST-UI-360`)

- **Targeted Unit & Contract Tests**:
  - `NavigationDrawerStateTest`: Passed (verifies 5 groups, 12 default visible items, Athlete->Gear ordering in `My Stuff`, `Advanced Tuning` at bottom of `Settings`, `isDrawerGroupInitiallyExpanded`, and `isDrawerItemSelected`).
  - `AppNavigationDrawerTest`: Passed.
  - `SingleActivityNavigationTest`: Passed (verifies `NavRoutes.EQUIPMENT <-> R.id.drawer_equipment` and backward-compatible deep links).
  - `AthleteProfileContractAndLocalizationTest`: Passed (verifies 4-tab `PrimaryScrollableTabRow` + `HorizontalPager`, `ZoneProfileTabContent`, Rule 41 bottom inset clearance, and 9-locale parity).
- **Full Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)**:
  - **Total Tests Executed**: `3,063` unit tests
  - **Pass Rate**: `100%` (`0` failures, `0` errors)
