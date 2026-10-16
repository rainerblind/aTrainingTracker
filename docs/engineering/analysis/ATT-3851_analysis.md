# Stage 1: Problem Domain & Root Cause Analysis - ATT-3851: Restructure and declutter Navigation Drawer items and sections

**Ticket**: [ATT-3851](https://rainerblind.atlassian.net/browse/ATT-3851)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `2026-42.7`  
**Target Release**: `V4.9.40`  
**Branch**: `feature/ATT-3851`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Executive Summary & Problem Statement

As new features were added across recent sprints (including *Google Drive* cloud sync, *Start Locations*, *Workout Clusters*, *Athlete Profile*, and *Advanced Tuning*), the main Navigation Drawer (`AppNavigationDrawer.kt`) grew to **24 items across 6 permanently expanded groups**:
1. **Training** (3 items): *Start*, *Workouts*, *Periods*
2. **Maps** (5 items): *Map*, *Segments*, *Routes*, *My Locations*, *Start Locations*
3. **My Stuff / Equipment** (6 items): *My Sensors*, *Bikes*, *Shoes*, *Sport Types*, *Training Zones*, *Athlete Profile*
4. **Online Communities** (4 items): *Strava*, *Dropbox*, *Google Drive*, *Export*
5. **Settings** (5 items): *Units*, *Display*, *Configure Displays*, *Import & Backup*, *Privacy Policy*
6. **Expert Settings** (1 item): *Advanced Tuning*

On standard smartphone viewports, 24 permanently expanded rows plus 6 section headers and dividers require significant vertical scrolling to reach everyday items or settings, and several entries duplicate existing tabbed destinations:
- *Bikes* (`R.id.drawer_bikes`) and *Shoes* (`R.id.drawer_shoes`) both open the exact same `EquipmentTabsScreen` (just with `initialTab = 0` vs. `initialTab = 1`), while `ic_equipment_combined.xml` and `R.string.equipment_management_title` already exist in the codebase.
- *Training Zones* (`R.id.drawer_training_zones`) and *Athlete Profile* (`R.id.drawer_athlete_profile`) both manage the athlete's physiological profile (Weight, Resting HR, FTP, W/kg, and HR/Power Zones) as separate top-level screens.
- *Advanced Tuning* (`R.id.drawer_advanced_tuning`) occupies its own 1-item section (*Expert Settings*, `R.string.drawer__expert_settings`) below *Settings*.
- Infrequently accessed configuration groups (*Online Communities* and *Settings*) remain permanently expanded alongside daily training and navigation destinations.

---

## 2. Forensic Root Cause & Architectural Gap Analysis

### 2.1 Drawer Group Configuration (`AppNavigationDrawer.kt`)
- [`createDrawerGroups(startTrackingTitleRes: Int)`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt#L154-L211) statically returns 6 `DrawerGroup` instances with 24 `DrawerItemConfig` items.
- `DrawerGroup` currently lacks a `collapsible: Boolean` property, and `DrawerGroupView` unconditionally renders all `group.items` without expand/collapse state or chevron indicators.

### 2.2 Equipment & Profile/Zones Consolidation (`NavRoutes.kt`, `ATrainingTrackerApp.kt`, `AthleteProfileScreen.kt`, `ZonesSettingsActivity.kt`)
- **Equipment Consolidation**:
  - `res/drawable/ic_equipment_combined.xml` (`<layer-list>` combining `ic_equipment_bike` and `ic_equipment_shoe`) and `R.string.equipment_management_title` (*Equipment* / *Ausrüstung*) already exist across all 9 locales.
  - Adding `R.id.drawer_equipment` (`ids.xml`) and `NavRoutes.EQUIPMENT = "equipment"` mapped to `EquipmentTabsScreen(initialTab = 0)` replaces the two separate *Bikes* and *Shoes* drawer rows while keeping `NavRoutes.BIKES` and `NavRoutes.SHOES` valid for backward-compatible deep links and existing route tests.
- **Profile & Zones Consolidation**:
  - `R.id.drawer_athlete_profile` (at the top of *My Stuff*) can serve as the unified **Profile & Zones** (*Profil & Zonen*) destination (`NavRoutes.ATHLETE_PROFILE`), updating `R.string.drawer_athlete_profile` and `R.string.athlete_profile_title` across all 9 locales.
  - Inside `AthleteProfileScreen.kt`, adding a Material 3 `PrimaryScrollableTabRow` and `HorizontalPager` with 4 tabs — **Profile** (`R.string.athlete_profile_tab_profile`), **HR Run** (`R.string.tab_hr_run`), **HR Bike** (`R.string.tab_hr_bike`), and **Power Bike** (`R.string.tab_pwr_bike`) — unifies physiological baseline tracking (`AthleteProfileContent` body) and zone threshold configuration (`ZoneProfileTabContent` from `ZonesSettingsActivity.kt`) under one single-header screen (`+` action on the Profile tab, `Calculate` action on the 3 Zone tabs).
  - Keeping `NavRoutes.TRAINING_ZONES_PATTERN` (`ZoneSettingsScreen`) registered in `ATrainingTrackerApp.kt` preserves sensor-pairing onboarding deep links (`NavRoutes.trainingZones(initialTab, promptReason)`) and all existing zone contract tests (`ZoneOnboardingBannerContractTest`, `ZonesSettingsHeaderContractTest`, `ZonesSettingsPreviewPillsContractTest`).

---

## 3. Chesterton's Fence & Requirement Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   - Amends drawer item composition in `REQ-UI-159` (*Single-Activity Jetpack Compose Navigation Architecture*, `ATT-1083`) and extends `REQ-UI-391.1` / `REQ-UI-391.5` (*Dedicated Athlete Profile Screen*, `ATT-3704`). Tracked as net-new requirement **`REQ-UI-397`**.
2. **Historical Origin & Commit Trace**:
   - `AppNavigationDrawer.kt` was introduced in `ATT-526` / `ATT-243` and expanded in `ATT-1083` (`REQ-UI-159`), `ATT-2936` (Equipment tabs), and `ATT-3704` (`REQ-UI-391`, commit `b660ef43`).
3. **Root Reason for Existing Formulation**:
   - Drawer items were originally ported 1:1 from legacy XML preference headers and menu resources (`Manage Bikes`, `Manage Shoes`, `Training Zones`, `Expert Settings`). As each screen was modernized into a tabbed Compose destination (`EquipmentTabsScreen`, `ZoneSettingsScreen`, `AthleteProfileScreen`), the legacy granular drawer items remained unmerged.
4. **Preservation of Core Invariants**:
   - Compact `40.dp` drawer item height (`ATT-243`), `40.dp` left-edge swipe gesture interceptor (`REQ-UI-161`), LIFO `BackHandler` hierarchy (`REQ-UI-162`), sensor-pairing zone onboarding deep links (`REQ-UI-374.3`), and bottom system navigation bar inset clearance (`REQ-UI-396.1` / Rule 41) are 100% preserved.

---

## 4. Scope Bounding (`ATT-1250`)

### In-Scope
1. Consolidate **My Stuff** (`R.string.drawer__my_stuff`) from 6 items to 4 items in Athlete -> Gear order:
   - `R.id.drawer_athlete_profile` (**Profile & Zones** / **Profil & Zonen**)
   - `R.id.drawer_my_sensors` (**My Sensors** / **Meine Sensoren**)
   - `R.id.drawer_equipment` (**Equipment** / **Ausrüstung**, using `R.drawable.ic_equipment_combined` and `R.string.equipment_management_title`)
   - `R.id.drawer_sport_types` (**Sport Types** / **Sportarten**)
2. Unify **Profile & Zones** (`AthleteProfileScreen.kt` + `ZonesSettingsActivity.kt`) with tabs for **Profile** (Weight, Resting HR, FTP, W/kg) and **Training Zones** (*HR Run*, *HR Bike*, *Power Bike*).
3. Move **Advanced Tuning** (`R.id.drawer_advanced_tuning`) into **Settings** (`R.string.drawer__settings`) as the 5th item (before *Privacy Policy*) and remove the standalone 1-item *Expert Settings* (`R.string.drawer__expert_settings`) drawer group.
4. Make **Online Communities** (`R.string.prefsOnlineCommunities`) and **Settings** (`R.string.drawer__settings`) collapsible accordion sections in `AppNavigationDrawer.kt` with a trailing chevron icon (`Icons.Default.ExpandMore` / `Icons.Default.ExpandLess`), defaulting to collapsed (`12` visible everyday items + `2` collapsed headers) and auto-expanding when `selectedItemId` belongs to that group.
5. Maintain 9-language localization parity (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

### Out-of-Scope
- Modifying internal zone calculation math or SQLite schema for `AthleteMetrics.db` or `EquipmentDbHelper`.
- Changing settings bottom sheet contents (`StravaSettingsDialog`, `UnitsSettingsDialog`, `DisplaySettingsDialog`, etc.).
