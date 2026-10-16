# Stage 3: Implementation Plan - ATT-3851: Restructure and declutter Navigation Drawer items and sections

**Ticket**: [ATT-3851](https://rainerblind.atlassian.net/browse/ATT-3851)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `2026-42.7`  
**Target Release**: `V4.9.40`  
**Requirement Mapping**: `REQ-UI-397` (`REQ-UI-397.1`, `REQ-UI-397.2`, `REQ-UI-397.3`)  
**Test Mapping**: `TST-UI-360` (`TST-UI-360.1`, `TST-UI-360.2`, `TST-UI-360.3` in `docs/tests.md`)  
**Branch**: `feature/ATT-3851`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Architectural Overview (SWE.2)

This implementation restructures the Jetpack Compose Navigation Drawer (`AppNavigationDrawer.kt`, `NavRoutes.kt`, `ATrainingTrackerApp.kt`) and unifies the **Profile & Zones** destination (`AthleteProfileScreen.kt` + `ZonesSettingsActivity.kt`) while preserving 100% backward compatibility across existing deep links and contract tests:

1. **Resource & String Layer (`ids.xml` & 9 `values*/strings.xml`)**:
   - Add `<item name="drawer_equipment" type="id" />` to `app/src/main/res/values/ids.xml`.
   - Update `drawer_athlete_profile` and `athlete_profile_title` across all 9 locales to **Profile & Zones** (*Profil & Zonen*) and add `athlete_profile_tab_profile` (*Profile* / *Profil*).
2. **Route & Single-Activity Navigation Layer (`NavRoutes.kt`, `ATrainingTrackerApp.kt`)**:
   - Add `const val EQUIPMENT = "equipment"` to `NavRoutes.kt` and map `R.id.drawer_equipment <-> NavRoutes.EQUIPMENT` in `fromDrawerItemId` and `toDrawerItemId` (retaining `BIKES`, `SHOES`, `TRAINING_ZONES`, and `ATHLETE_PROFILE` mappings for backward compatibility).
   - Register `composable(NavRoutes.EQUIPMENT)` in `ATrainingTrackerApp.kt` rendering `EquipmentTabsScreen(viewModel = equipmentViewModel, initialTab = 0, onNavigateToWorkouts = ...)`.
   - Retain `composable(NavRoutes.BIKES)` (`EquipmentTabsScreen(initialTab = 0)`), `composable(NavRoutes.SHOES)` (`EquipmentTabsScreen(initialTab = 1)`), and `composable(NavRoutes.TRAINING_ZONES_PATTERN)` (`ZoneSettingsScreen(initialZoneType = zoneType)`) so all legacy deep links and external intents resolve cleanly without regression.
3. **Drawer Configuration & Collapsible Accordion UI (`AppNavigationDrawer.kt`)**:
   - Extend `DrawerGroup` with `val collapsible: Boolean = false`.
   - Update `createDrawerGroups(startTrackingTitleRes: Int)` to return 5 groups:
     1. `R.string.drawer__training` (`collapsible = false`, 3 items)
     2. `R.string.drawer__maps` (`collapsible = false`, 5 items)
     3. `R.string.drawer__my_stuff` (`collapsible = false`, 4 items in Athlete -> Gear order: `drawer_athlete_profile`, `drawer_my_sensors`, `drawer_equipment`, `drawer_sport_types`)
     4. `R.string.prefsOnlineCommunities` (`collapsible = true`, 4 items)
     5. `R.string.drawer__settings` (`collapsible = true`, 6 items including `R.id.drawer_advanced_tuning`)
   - Add `@VisibleForTesting fun isDrawerItemSelected(itemId: Int, selectedItemId: Int): Boolean` (mapping `R.id.drawer_equipment` to match `R.id.drawer_equipment`, `R.id.drawer_bikes`, and `R.id.drawer_shoes`, and mapping `R.id.drawer_athlete_profile` to match `R.id.drawer_athlete_profile` and `R.id.drawer_training_zones`) and `@VisibleForTesting fun isDrawerGroupInitiallyExpanded(group: DrawerGroup, selectedItemId: Int): Boolean`.
   - Update `AppNavigationDrawer` and `DrawerGroupView` so collapsible groups default to collapsed, auto-expand when `selectedItemId` belongs to them, and toggle via a clickable header row with a trailing chevron (`Icons.Default.ExpandLess` / `Icons.Default.ExpandMore`).
4. **Unified Tabbed "Profile & Zones" Screen & Activity vs. Composable Boundary (`ZonesSettingsActivity.kt`, `AthleteProfileScreen.kt`)**:
   - **Activity vs. Composable Boundary Clarification**: `ZonesSettingsActivity.kt` already defines a standalone pure-Compose `@Composable fun ZoneSettingsScreen(initialZoneType: ZoneType)` that uses `LocalContext.current`, `remember { ZonesRepository(context) }`, and Jetpack Compose `viewModel(key = zoneType.name, factory = ZonesSettingsViewModelFactory(repository, zoneType))` with zero dependency on `Activity` lifecycle callbacks. `ZonesSettingsActivity` itself is merely a thin legacy `AppCompatActivity` host that calls `setContent { ZoneSettingsScreen(...) }` for external intents.
   - We therefore extract and expose `@Composable fun ZoneProfileTabContent(zoneType: ZoneType, showCalculatorDialog: Boolean, onDismissCalculatorDialog: () -> Unit)` as a pure, self-contained Composable in `ZonesSettingsActivity.kt` (instantiating its scoped `ZonesSettingsViewModel` via `viewModel(key = zoneType.name, factory = ZonesSettingsViewModelFactory(repository, zoneType))` and managing `SettingsScreenContent` + `ZoneCalculatorDialog` in Compose state), while leaving `ZonesSettingsActivity` and `ZoneSettingsScreen` 100% intact for external intents and legacy deep-link routes (`NavRoutes.TRAINING_ZONES_PATTERN`).
   - In `AthleteProfileScreen.kt` (hosted inside `ATrainingTrackerApp.kt`'s single-activity `NavHost`), update `AthleteProfileContent` to render the top `primaryContainer` header (`R.string.athlete_profile_title`) with a `PrimaryScrollableTabRow` and `HorizontalPager` hosting 4 tabs:
     - Page 0: `R.string.athlete_profile_tab_profile` (the Athlete Profile `LazyColumn` with `+` `IconButton` in the header)
     - Page 1: `R.string.tab_hr_run` (`ZoneProfileTabContent(ZoneType.HR_RUN, ...)` with `Icons.Default.Calculate` `IconButton` in the header)
     - Page 2: `R.string.tab_hr_bike` (`ZoneProfileTabContent(ZoneType.HR_BIKE, ...)` with `Icons.Default.Calculate` `IconButton` in the header)
     - Page 3: `R.string.tab_pwr_bike` (`ZoneProfileTabContent(ZoneType.PWR_BIKE, ...)` with `Icons.Default.Calculate` `IconButton` in the header)

---

## 2. UI Consistency (Rule 23)

- **Closest Existing Reference Screens**:
  - Tabbed Screen Pattern: [`EquipmentTabsScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentTabsScreen.kt) & [`ZonesSettingsActivity.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt) (`primaryContainer` header + `LayoutConstants.HEADER_TITLE_ROW_HEIGHT` + `PrimaryScrollableTabRow(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, divider = {})`).
  - Accordion Section Pattern: [`EditSensorFieldDialog.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editfield/EditSensorFieldDialog.kt) (`Icons.Default.ExpandLess` / `Icons.Default.ExpandMore` trailing chevron).
- **Theme Tokens**:
  - `MaterialTheme.colorScheme.primaryContainer`, `onPrimaryContainer`, `surfaceContainerHighest`, `colorResource(id = R.color.color_primary)`, `16.dp` horizontal padding.
- **Justification for One-Off Styles**: None — reuses existing Material 3 `PrimaryScrollableTabRow`, `HorizontalPager`, and `DrawerGroupView` typography/colors.

---

## 3. Atomic Step Breakdown

### Step 1: Resource IDs & 9-Locale String Updates
- **Files**: `app/src/main/res/values/ids.xml` and `app/src/main/res/values*/strings.xml` (9 locales: `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
- **Action**: Add `drawer_equipment` to `ids.xml`. Update `drawer_athlete_profile` and `athlete_profile_title` to **Profile & Zones** (*Profil & Zonen*, *Perfil y zonas*, *Profil et zones*, *Profilo e zone*, *プロフィールとゾーン*, *Profiel & zones*, *Profil i strefy*, *Perfil e zonas*) and add `athlete_profile_tab_profile` (*Profile*, *Profil*, *Perfil*, *Profil*, *Profilo*, *プロフィール*, *Profiel*, *Profil*, *Perfil*).

### Step 2: Navigation Route & Drawer Restructuring
- **Files**: `NavRoutes.kt`, `AppNavigationDrawer.kt`, `ATrainingTrackerApp.kt`.
- **Action**: Add `NavRoutes.EQUIPMENT`, wire `composable(NavRoutes.EQUIPMENT)` in `ATrainingTrackerApp.kt`, and implement the 5-group `createDrawerGroups`, `isDrawerItemSelected`, `isDrawerGroupInitiallyExpanded`, and collapsible `DrawerGroupView` in `AppNavigationDrawer.kt`.

### Step 3: Unified Tabbed "Profile & Zones" Screen
- **Files**: `ZonesSettingsActivity.kt`, `AthleteProfileScreen.kt`.
- **Action**: Add `ZoneProfileTabContent` in `ZonesSettingsActivity.kt` and integrate the 4-tab `PrimaryScrollableTabRow` + `HorizontalPager` in `AthleteProfileScreen.kt`.

### Step 4: Targeted Unit & Contract Tests (`TST-UI-360` in `docs/tests.md`)
- **Files**: `NavigationDrawerStateTest.kt`, `SingleActivityNavigationTest.kt`, `AthleteProfileContractAndLocalizationTest.kt`, `docs/tests.md`.
- **Explicit Test Mapping to `docs/tests.md` (`TST-UI-360`)**:
  - `TST-UI-360.1` (`NavigationDrawerStateTest.kt`): Verifies the 5 drawer groups (`Training`, `Maps`, `My Stuff`, `Online Communities`, `Settings`), absence of `Advanced` group, consolidation of `Equipment` (`R.id.drawer_equipment` with `R.drawable.ic_equipment_combined` and `R.string.equipment_management_title`), `My Stuff` ordering (`drawer_athlete_profile`, `drawer_my_sensors`, `drawer_equipment`, `drawer_sport_types`), and collapsible auto-expansion logic (`isDrawerGroupInitiallyExpanded` returning `false` by default for `Online Communities` and `Settings` and `true` when `selectedItemId` belongs to the group, plus `isDrawerItemSelected` matching legacy `drawer_bikes`/`drawer_shoes` to `drawer_equipment` and `drawer_training_zones` to `drawer_athlete_profile`).
  - `TST-UI-360.2` (`SingleActivityNavigationTest.kt`): Verifies bidirectional mapping `R.id.drawer_equipment <-> NavRoutes.EQUIPMENT` alongside legacy deep-link route persistence (`NavRoutes.BIKES`, `NavRoutes.SHOES`, `NavRoutes.TRAINING_ZONES`, `NavRoutes.trainingZonesRoute(...)`) and resolution in `ATrainingTrackerApp.kt` to `EquipmentTabsScreen` and `ZoneSettingsScreen`.
  - `TST-UI-360.3` (`AthleteProfileContractAndLocalizationTest.kt`): Verifies 4-tab `PrimaryScrollableTabRow` + `HorizontalPager` (`athlete_profile_tab_profile`, `tab_hr_run`, `tab_hr_bike`, `tab_pwr_bike`), `ZoneProfileTabContent`, Rule 41 bottom inset clearance, and 9-locale string parity.
- **Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.*" --tests "com.atrainingtracker.trainingtracker.zones.*"
  ```

---

## 4. Invariants & Rollback Safety

- **Backward-Compatible Deep Links & External Intents**: `NavRoutes.BIKES`, `NavRoutes.SHOES`, and `NavRoutes.TRAINING_ZONES_PATTERN` (`training_zones?zoneType={zoneType}`) remain registered in `NavRoutes.kt` and `ATrainingTrackerApp.kt`, and `ZonesSettingsActivity` remains registered for external intents so legacy training zone URLs and deep links resolve with 100% persistence.
- **Existing Zone & Athlete Profile Contract Preservation**: `ZonesSettingsHeaderContractTest`, `ZonesSettingsPreviewPillsContractTest`, `ZoneOnboardingBannerContractTest`, and `AthleteProfileContractAndLocalizationTest` (including Rule 41 bottom inset clearance) remain 100% intact.
