# Stage 1 Analysis: ATT-7312 - Add context-aware Hamburger Menu and Back Arrow to Sensors, Equipment, Backup, Settings, and Support views

**Ticket**: [ATT-7312](https://rainerblind.atlassian.net/browse/ATT-7312)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.4`  
**Branch**: `subagent-Lane-2-Agent---ATT-7312-self-c3867f51`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
Building on Part 1 (`ATT-7313`), which standardized the root-drawer vs. pushed-above navigation contract and header icon alignment across Training and Maps views, the remaining secondary drawer destinations (`My Stuff`, `Online Communities`, `Settings`, and `Info & Support`) still present navigation inconsistencies:
1. Four tabbed drawer destinations have no leading navigation icon at all:
   * `My Sensors` (`DevicesTabbedScreen.kt` in `:banalservice`)
   * `Equipment` (`EquipmentTabsScreen.kt` in `:app`, hosting `EQUIPMENT`, `BIKES`, and `SHOES`)
   * `Import & Backup` (`ImportBackupTabsScreen.kt` in `:app`)
   * `Sport Types` (`SportTypesTabsScreen.kt` in `:banalservice`)
2. Three top-level drawer destinations unconditionally render `ArrowBack` even when opened directly from `AppNavigationDrawer`:
   * `Advanced Tuning` (`AdvancedTuningScreen.kt`)
   * `Supporter` (`SupporterScreen.kt`)
   * `Tips & Guide` (`TipsGuideScreen.kt`)
3. One destination unconditionally renders `Menu` even when pushed above another screen:
   * `Training Zones` (`ZoneSettingsScreen` in `ZonesSettingsActivity.kt` / `ATrainingTrackerApp.kt:619`) renders `Icons.Default.Menu` even when pushed above `My Sensors` via `onNavigateToZones`!

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Missing Leading Icon RCA**: The collapsing `primaryContainer` header rows in `DevicesTabbedScreen.kt`, `EquipmentTabsScreen.kt`, `ImportBackupTabsScreen.kt`, and `SportTypesTabsScreen.kt` use hardcoded `padding(horizontal = 16.dp)` without leading navigation button slots.
2. **Hardcoded TopAppBar Icons RCA**: `AdvancedTuningScreen.kt`, `SupporterScreen.kt`, and `TipsGuideScreen.kt` use `Scaffold(topBar = { TopAppBar(...) })` where `navigationIcon` statically mounts `Icons.AutoMirrored.Filled.ArrowBack` calling `onNavigateBack`, unaware of whether they were opened as drawer roots or pushed.
3. **Hardcoded Drawer Menu in Zones RCA**: In `ATrainingTrackerApp.kt`, `ZoneSettingsScreen` unconditionally binds `onOpenDrawer = { activity.openDrawer() }`, causing `ZoneSettingsScreen` to render `Icons.Default.Menu` even when the user arrived from the pairing banner in `My Sensors`. Additionally, the existing `IconButton` in `ZoneSettingsScreen` had `contentDescription = null`.
4. **Independent Hypothesis Verification**: Verified against the codebase that all 4 tabbed screens share the exact same `HEADER_TITLE_ROW_HEIGHT` (48 dp) and `primaryContainer` background. Introducing the standard Part 1 pattern (`padding(start = 4.dp, end = 16.dp)` with 48 dp `IconButton` and 4 dp title padding) guarantees a consistent 56 dp title offset. Furthermore, verified that `NavRoutes.trainingZones()`, `NavRoutes.supporter()`, and `NavRoutes.advancedSettings()` can cleanly distinguish pushed states via `ARG_PUSHED` and contextual arguments (`promptReason != null` or `planId != null`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Add context-aware leading `IconButton` (Hamburger `Menu` when drawer root, `ArrowBack` when pushed) with localized accessibility descriptions to:
    - `DevicesTabbedScreen.kt` (`NavRoutes.SENSORS`)
    - `EquipmentTabsScreen.kt` (`NavRoutes.EQUIPMENT`, `NavRoutes.BIKES`, `NavRoutes.SHOES`)
    - `ImportBackupTabsScreen.kt` (`NavRoutes.BACKUP_RESTORE`)
    - `SportTypesTabsScreen.kt` (`NavRoutes.SPORT_TYPES`)
  * Make `ZoneSettingsScreen` context-aware: render `Menu` when opened from `drawer_training_zones`; render `ArrowBack` (calling `onFinish`) when pushed above `My Sensors` via `onNavigateToZones`.
  * Make `AdvancedTuningScreen.kt` context-aware: support `onOpenDrawer: (() -> Unit)? = null`, rendering `Menu` from `drawer_advanced_tuning` and `ArrowBack` when pushed from `DisplaySettingsDialog` or `WorkoutClustersScreen`. Keep `Save / Cancel` bar fully operational.
  * Make `SupporterScreen.kt` context-aware: support `onOpenDrawer: (() -> Unit)? = null`, rendering `Menu` from `drawer_supporter` and `ArrowBack` when pushed from `My Sensors`, `Equipment`, etc.
  * Make `TipsGuideScreen.kt` context-aware: support `onOpenDrawer: (() -> Unit)? = null`, rendering `Menu` from `drawer_tips_guide` and `ArrowBack` if pushed.
  * Wire `ATrainingTrackerApp.kt` route bindings and disable 40 dp edge drawer swipe when any of these screens are pushed (`isCurrentScreenPushed`).
* **Out-of-Scope Non-Goals**:
  * Modifying modal bottom sheet internals (`DropboxSettingsDialog`, `StravaSettingsDialog`, etc.).
  * Changing business logic or calculations in `ZoneSettingsScreen` or `AdvancedTuningScreen`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirements**:
  * `REQ-UI-159`: Compose single-activity migration and navigation architecture.
  * `REQ-UI-161`: ModalNavigationDrawer edge-swipe restriction (40 dp threshold).
  * `REQ-UI-166`: Standardized root-drawer Hamburger Menu vs pushed Back Arrow contract (established in Part 1, `ATT-7313`).
  * `REQ-UI-357`: Dedicated full-screen Advanced Tuning screen.
  * `REQ-UI-374.3`: Parameterized zone deep-linking from pairing banners.
* **Preservation of Core Invariants**:
  * `REQ-UI-161` remains active on all drawer root views; edge swipe is suppressed only when `isCurrentScreenPushed == true` to avoid gesture interference with child screens.
  * `ZoneOnboardingBannerContractTest` invariants are strictly preserved: argument names, route parameters, and existing deep link signatures remain backwards-compatible.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-UI-166` (extended across secondary views), `REQ-UI-161` (expanded `isCurrentScreenPushed` detection).
* **Affected Architecture Elements**:
  * `:app`: `NavRoutes.kt`, `ATrainingTrackerApp.kt`, `EquipmentTabsScreen.kt`, `ImportBackupTabsScreen.kt`, `ZonesSettingsActivity.kt`, `AdvancedTuningScreen.kt`, `SupporterScreen.kt`, `TipsGuideScreen.kt`.
  * `:banalservice`: `DevicesTabbedScreen.kt`, `SportTypesTabsScreen.kt`.
* **Verification Impact & Test Surface**:
  * Unit & Navigation Contract Tests: `SingleActivityNavigationTest.kt`, `ATrainingTrackerAppNavigationTest.kt`, `NavRoutesZoneTest.kt`, `ZoneOnboardingBannerContractTest.kt`, `AdvancedTuningNavigationTest.kt`, `TipsGuideScreenContractTest.kt`.
  * New unit/compose tests verifying leading icon existence and toggle semantics across all 8 target screens.
  * Localization parity across all 9 languages for `navigation_drawer_open` and `back`.
* **Risk Assessment & Invariants**: **LOW**. Visual and navigational consistency improvements without database migrations, service changes, or network alterations.

---

## 6. Architectural Strategy & High-Level Solution
1. **NavRoutes Contract**:
   * Add `pushed: Boolean = false` support to `NavRoutes.trainingZones()`, `NavRoutes.supporter()`, and `NavRoutes.advancedSettings()` using `ARG_PUSHED = "pushed"`.
   * Ensure `toDrawerItemId()` and `fromDrawerItemId()` continue to map cleanly without breaking existing route pattern matching.
2. **Header Alignment Pattern**:
   * In each tabbed screen, adopt the standardized header layout:
     ```kotlin
     Row(
         modifier = Modifier.fillMaxWidth().height(LayoutConstants.HEADER_TITLE_ROW_HEIGHT).padding(start = 4.dp, end = 16.dp),
         verticalAlignment = Alignment.CenterVertically,
         horizontalArrangement = Arrangement.SpaceBetween
     ) {
         Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
             if (onBackClick != null) {
                 IconButton(onClick = onBackClick) {
                     Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                 }
             } else if (onMenuClick != null) {
                 IconButton(onClick = onMenuClick) {
                     Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.navigation_drawer_open), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                 }
             }
             Text(text = title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(start = 4.dp))
         }
         // trailing action buttons...
     }
     ```
3. **ATrainingTrackerApp Wiring**:
   * Wire `onMenuClick = { drawerController.openDrawer() }` for root-opened destinations.
   * Provide `onNavigateBack` / `onBackClick` when destinations are pushed.
   * Extend `isCurrentScreenPushed` to include pushed settings and support screens.

---

## 7. System Invariants & Risk Assessment
* **Invariants**:
  * All 18 navigation drawer destinations must be reachable from `AppNavigationDrawer`.
  * Tapping the Hamburger Menu always opens the drawer.
  * Tapping the Back Arrow always pops to the previous screen.
  * Zero visual jumps: Title start position is consistently 56 dp across all tabbed screens.
* **Risk Rating**: **LOW**.
