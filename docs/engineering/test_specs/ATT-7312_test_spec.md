# Stage 2: Requirement & Test Specification - ATT-7312: Add context-aware Hamburger Menu and Back Arrow to Sensors, Equipment, Backup, Settings, and Support views

**Ticket**: [ATT-7312](https://rainerblind.atlassian.net/browse/ATT-7312)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.4`  
**Requirement Mapping**: `REQ-UI-518` (*Standardize Context-Aware Hamburger Menu vs Back Arrow Contract Across Secondary Drawer Groups*)  
**Test Spec ID**: `TST-UI-518`  
**Branch**: `subagent-Lane-2-Agent---ATT-7312-self-c3867f51`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-UI-518)

### 1.1 Problem Statement & Rationale
Completing Part 1 (`ATT-7313`), this specification addresses the remaining secondary drawer groups (`My Stuff`, `Online Communities`, `Settings`, `Info & Support`):
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
   * `Training Zones` (`ZoneSettingsScreen` in `ZonesSettingsActivity.kt` / `ATrainingTrackerApp.kt`) renders `Icons.Default.Menu` even when pushed above `My Sensors` via `onNavigateToZones`.

### 1.2 Functional & Architectural Requirements
1. **Tabbed Secondary Headers Contract (`REQ-UI-518.1`)**:
   * `DevicesTabbedScreen.kt`, `EquipmentTabsScreen.kt`, `ImportBackupTabsScreen.kt`, and `SportTypesTabsScreen.kt` SHALL add a leading navigation `IconButton` to the `primaryContainer` header row.
   * When opened as a root drawer destination, the button SHALL render `Icons.Default.Menu` with `R.string.navigation_drawer_open` accessibility description and open the drawer.
   * When pushed, the button SHALL render `Icons.AutoMirrored.Filled.ArrowBack` with `R.string.back` accessibility description and pop back.
   * The header row SHALL maintain `padding(start = 4.dp, end = 16.dp)`, `IconButton` size 48 dp, tint `MaterialTheme.colorScheme.onPrimaryContainer`, and 4 dp start padding on the title text, enforcing a consistent 56 dp start offset.
2. **Training Zones Context-Aware Navigation (`REQ-UI-518.2`)**:
   * In `ZonesSettingsActivity.kt` / `ATrainingTrackerApp.kt`, `ZoneSettingsScreen` SHALL display `Icons.Default.Menu` (`R.string.navigation_drawer_open`) when opened from `drawer_training_zones`.
   * When pushed above `My Sensors` via onboarding pairing banners (`onNavigateToZones`), `ZoneSettingsScreen` SHALL display `Icons.AutoMirrored.Filled.ArrowBack` (`R.string.back`), and tapping it SHALL pop back.
3. **Settings & Support TopAppBar Navigation Contract (`REQ-UI-518.3`)**:
   * `AdvancedTuningScreen.kt`, `SupporterScreen.kt`, and `TipsGuideScreen.kt` SHALL accept an optional `onOpenDrawer: (() -> Unit)? = null` callback alongside `onNavigateBack`.
   * When opened from `AppNavigationDrawer`, they SHALL render `Icons.Default.Menu` (`R.string.navigation_drawer_open`).
   * When pushed above another view, they SHALL render `Icons.AutoMirrored.Filled.ArrowBack` (`R.string.back` / `R.string.cancel`).
   * `AdvancedTuningScreen`'s bottom `Save / Cancel` bar SHALL remain fully functional in both drawer-root and pushed modes.
4. **Pushed Destination Interceptor Suppression (`REQ-UI-518.4`)**:
   * When any secondary destination is pushed (`isCurrentScreenPushed == true`), the root 40 dp left-edge drawer interceptor in `ATrainingTrackerApp.kt` SHALL be deactivated.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Hamburger Menu on Drawer-Opened Secondary Views)**:
  * *Given* the athlete opens `My Sensors`, `Equipment`, `Import & Backup`, `Sport Types`, `Training Zones`, `Advanced Tuning`, `Supporter`, or `Tips & Guide` from `AppNavigationDrawer`,
  * *When* the screen renders at its top level,
  * *Then* the header SHALL display `Icons.Default.Menu` and tapping it SHALL open `AppNavigationDrawer`.
* **Criterion 2 (Back Arrow on Pushed Views)**:
  * *Given* the athlete navigates to `Training Zones` from `My Sensors`, or to `Supporter` from `My Sensors` / `Equipment`, or to `Advanced Tuning` from `Display Settings` / `WorkoutClustersScreen`,
  * *When* the target screen renders above the caller screen,
  * *Then* the header SHALL display `Icons.AutoMirrored.Filled.ArrowBack` and tapping it SHALL pop back to the origin caller.
* **Criterion 3 (Visual & Accessibility Parity)**:
  * *Given* all newly added Hamburger and Back `IconButton` controls across `:app` and `:banalservice`,
  * *When* rendered in Light/Dark themes and across all 9 supported locales,
  * *Then* every button SHALL provide localized `contentDescription` (`navigation_drawer_open` or `back` / `cancel`) and align to the 56 dp title baseline.

### 1.4 System Invariants
* All 21 navigation drawer destinations in `NavRoutes` preserved.
* `REQ-UI-161` 40 dp threshold preserved for root screens.
* `REQ-UI-162` LIFO back handling hierarchy preserved.
* Zero regressions on existing deep links (`ZoneOnboardingBannerContractTest`).

---

## 2. Test Specification (TST-UI-518)

### Test Case 1: Route Contract & Parameterized Navigation Verification (`TST-UI-518.1`)
* **Scope**: Unit Tests (`SingleActivityNavigationTest.kt`, `NavRoutesZoneTest.kt`, `AdvancedTuningNavigationTest.kt`)
* **Action**: Verify route formatting, argument parsing, and drawer resource ID mapping for all secondary screens.

### Test Case 2: Secondary Tabbed Screens Header Icon Verification (`TST-UI-518.2`)
* **Scope**: Compose & Contract Tests (`DevicesTabbedScreenTest.kt`, `EquipmentTabsScreenTest.kt`, `ImportBackupTabsScreenTest.kt`, `SportTypesTabsScreenTest.kt`)
* **Action**: Verify `onMenuClick` / `onBackClick` signatures, icon rendering, tint (`onPrimaryContainer`), and 56 dp title offset.

### Test Case 3: Settings & Support Context-Aware TopAppBar Verification (`TST-UI-518.3`)
* **Scope**: Compose & Contract Tests (`AdvancedTuningScreenTest.kt`, `TipsGuideScreenContractTest.kt`, `ZoneSettingsScreenContractTest.kt`, `SupporterScreenContractTest.kt`)
* **Action**: Verify conditional Menu vs ArrowBack rendering based on `onOpenDrawer`.

### Test Case 4: ATrainingTrackerApp Navigation & Interceptor State Verification (`TST-UI-518.4`)
* **Scope**: UI Architecture Tests (`ATrainingTrackerAppNavigationTest.kt`)
* **Action**: Verify edge swipe suppression and correct callback passing across root vs pushed destinations.

### Test Case 5: 9-Language Localization Parity Verification (`TST-UI-518.5`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Action**: Verify `navigation_drawer_open`, `back`, and `cancel` exist across all 9 locales with 100% parity.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Test Class / Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-518.1` | Unit | `SingleActivityNavigationTest`, `NavRoutesZoneTest`, `AdvancedTuningNavigationTest` | `REQ-UI-518.1`, `REQ-UI-518.3` | Specified |
| `TST-UI-518.2` | Contract / UI | `SingleActivityNavigationTest`, `SecondaryTabbedHeadersContractTest` | `REQ-UI-518.1` | Specified |
| `TST-UI-518.3` | Contract / UI | `AdvancedTuningScreenTest`, `TipsGuideScreenContractTest`, `ZoneSettingsScreenContractTest` | `REQ-UI-518.2`, `REQ-UI-518.3` | Specified |
| `TST-UI-518.4` | Architecture | `ATrainingTrackerAppNavigationTest` | `REQ-UI-518.4` | Specified |
| `TST-UI-518.5` | Localization | `TranslationParityTest` | `REQ-UI-518`, `REQ-UI-106` | Specified |
