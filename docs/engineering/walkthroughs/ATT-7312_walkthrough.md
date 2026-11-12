# Walkthrough: Add context-aware Hamburger Menu and Back Arrow to Sensors, Equipment, Backup, Settings, and Support views (ATT-7312)

* **Parent Ticket**: [ATT-7312](https://rainerblind.atlassian.net/browse/ATT-7312) (*[Verbesserung] Add context-aware Hamburger Menu and Back Arrow to Sensors, Equipment, Backup, Settings, and Support views*)
* **Sub-Tasks**:
  * [ATT-7389](https://rainerblind.atlassian.net/browse/ATT-7389) (*[SWE.1] System & Software Requirements Analysis*) - `Erledigt`
  * [ATT-7391](https://rainerblind.atlassian.net/browse/ATT-7391) (*[SWE.4] Verification Specification*) - `Erledigt`
  * [ATT-7392](https://rainerblind.atlassian.net/browse/ATT-7392) (*[SWE.2 / SWE.3] Architecture, Detailed Design & Implementation Plan*) - `Erledigt`
  * [ATT-7402](https://rainerblind.atlassian.net/browse/ATT-7402) (*[SWE.3] Construction & Implementation*) - `Erledigt`
* **Target Version**: `V5.0.0`
* **Requirement**: [`REQ-UI-518`](file:///home/rainer/.gemini/antigravity/worktrees/e228d70c-9ed0-47cc-a7a4-f4fb443c5e2b/subagent-Lane-2-Agent---ATT-7312-self-c3867f51/docs/requirements/req_ui_design_system.md#L15583) (*Standardize Context-Aware Hamburger Menu vs Back Arrow Contract Across Secondary Drawer Groups*)
* **Test Specification**: [`TST-UI-518`](file:///home/rainer/.gemini/antigravity/worktrees/e228d70c-9ed0-47cc-a7a4-f4fb443c5e2b/subagent-Lane-2-Agent---ATT-7312-self-c3867f51/docs/tests/tst_ui_design_system.md#L8364) (*Context-Aware Hamburger Menu vs Back Arrow Contract Across Secondary Drawer Groups Verification Suite*)
* **Branch**: `subagent-Lane-2-Agent---ATT-7312-self-c3867f51`

---

## 1. Overview & Problem Resolution

Building on Part 1 (`ATT-7313`), which established the root-drawer vs pushed-above navigation contract and standardized header leading actions for Training and Maps screens, this ticket completes the contract across all remaining secondary drawer groups (`My Stuff`, `Online Communities`, `Settings`, `Info & Support`):

1. **Four Secondary Tabbed Views Missing Leading Icon**:
   * `My Sensors` (`DevicesTabbedScreen.kt` in `:banalservice`)
   * `Equipment` (`EquipmentTabsScreen.kt` in `:app`, hosting `EQUIPMENT`, `BIKES`, and `SHOES`)
   * `Import & Backup` (`ImportBackupTabsScreen.kt` in `:app`)
   * `Sport Types` (`SportTypesTabsScreen.kt` in `:banalservice`)
   * *Resolution*: Added context-aware leading `IconButton` (Hamburger `Menu` when drawer root, `ArrowBack` when pushed), `padding(start = 4.dp, end = 16.dp)`, `onPrimaryContainer` tint, and 4 dp title padding to achieve the exact standard 56 dp title offset without visual jump across tab switches.

2. **Three TopAppBar Screens Hardcoding Back Arrow at Drawer Root**:
   * `Advanced Tuning` (`AdvancedTuningScreen.kt`)
   * `Supporter` (`SupporterScreen.kt`)
   * `Tips & Guide` (`TipsGuideScreen.kt`)
   * *Resolution*: Introduced optional `onOpenDrawer: (() -> Unit)? = null`. When opened from `AppNavigationDrawer`, they render `Icons.Default.Menu` (`navigation_drawer_open`); when pushed above another destination, they render `Icons.AutoMirrored.Filled.ArrowBack` (`back` / `cancel`).

3. **Training Zones Hardcoding Hamburger Menu When Pushed**:
   * `ZoneSettingsScreen` in `ZonesSettingsActivity.kt` / `ATrainingTrackerApp.kt` unconditionally rendered `Icons.Default.Menu` even when pushed above `My Sensors` via pairing onboarding banners.
   * *Resolution*: When pushed (`promptReason != null` or `pushed == true`), `ATrainingTrackerApp.kt` passes `onOpenDrawer = null`, causing `ZoneSettingsScreen` to render `Icons.AutoMirrored.Filled.ArrowBack` invoking `onFinish = { navController.popBackStack() }`. When opened from the drawer, `onOpenDrawer` is provided to render `Icons.Default.Menu`. Added missing localized accessibility description to both states.

4. **Pushed Edge-Swipe Interceptor Suppression**:
   * Extended `isCurrentScreenPushed` in `ATrainingTrackerApp.kt` to encompass pushed secondary destinations (`ARG_PROMPT_REASON != null`, `ARG_PLAN_ID != null`, or `ARG_PUSHED == true`), cleanly deactivating the 40 dp left-edge drawer interceptor on child screens to avoid gesture conflicts.

---

## 2. Summary of Implementation Changes

### 2.1 Route Contracts & Patterns (`NavRoutes.kt`)
* Extended `trainingZones(initialTab: Int = 0, promptReason: String? = null, pushed: Boolean = false)`.
* Extended `supporter(planId: String? = null, pushed: Boolean = false)`.
* Added `advancedSettings(pushed: Boolean = false)` and `tipsGuide(pushed: Boolean = false)`.
* Added route patterns:
  - `TRAINING_ZONES_PATTERN = "training_zones?initialTab={initialTab}&promptReason={promptReason}&pushed={pushed}"`
  - `SUPPORTER_PATTERN = "supporter?planId={planId}&pushed={pushed}"`
  - `ADVANCED_SETTINGS_PATTERN = "advanced_settings?pushed={pushed}"`
  - `TIPS_GUIDE_PATTERN = "tips_guide?pushed={pushed}"`
* All `fromDrawerItemId()` and `toDrawerItemId()` mappings preserved with query-parameter stripping.

### 2.2 Secondary Tabbed Screen Headers (:banalservice & :app)
* **`DevicesTabbedScreen.kt`**: Added `onMenuClick` and `onBackClick` parameters. Standardized header Row to `padding(start = 4.dp, end = 16.dp)` with 48 dp `IconButton` (`Icons.Default.Menu` or `Icons.AutoMirrored.Filled.ArrowBack`) in `onPrimaryContainer`. Title start offset is 56 dp.
* **`EquipmentTabsScreen.kt`**: Added `onMenuClick` and `onBackClick` parameters and leading `IconButton` in header row.
* **`ImportBackupTabsScreen.kt`**: Added `onMenuClick` and `onBackClick` parameters and leading `IconButton` in header row.
* **`SportTypesTabsScreen.kt`**: Added `onMenuClick` and `onBackClick` parameters and leading `IconButton` in header row.

### 2.3 Context-Aware TopAppBar Screens (:app)
* **`ZonesSettingsActivity.kt`**:
  - `ZoneSettingsScreen`: Evaluates `onOpenDrawer != null` to render `Icons.Default.Menu` (`R.string.navigation_drawer_open`); otherwise renders `Icons.AutoMirrored.Filled.ArrowBack` (`R.string.back`) invoking `onFinish`.
  - In `ZonesSettingsActivity.onCreate`: passes `onFinish = { finish() }`.
* **`AdvancedTuningScreen.kt`**:
  - Added `onOpenDrawer: (() -> Unit)? = null`.
  - TopAppBar renders `Menu` when `onOpenDrawer != null` and `ArrowBack` when `onOpenDrawer == null`.
  - Bottom `Save / Cancel` bar remains fully functional in both modes.
* **`SupporterScreen.kt`**:
  - Added `onOpenDrawer: (() -> Unit)? = null`.
  - TopAppBar renders `Menu` when `onOpenDrawer != null` and `ArrowBack` when `onOpenDrawer == null`.
* **`TipsGuideScreen.kt`**:
  - Added `onOpenDrawer: (() -> Unit)? = null`.
  - TopAppBar renders `Menu` when `onOpenDrawer != null` and `ArrowBack` when `onOpenDrawer == null`.

### 2.4 Navigation Graph Wiring (`ATrainingTrackerApp.kt`)
* Extended `isCurrentScreenPushed`:
  ```kotlin
  val isCurrentScreenPushed = currentBackStackEntry?.arguments?.getBoolean(NavRoutes.ARG_PUSHED) == true ||
      (currentBackStackEntry?.arguments?.getLong(NavRoutes.ARG_LOCATION_ID, -1L) ?: -1L) > 0 ||
      (currentBackStackEntry?.arguments?.getLong(NavRoutes.ARG_CLUSTER_ID, -1L) ?: -1L) > 0 ||
      (currentBackStackEntry?.arguments?.getString(NavRoutes.ARG_PROMPT_REASON) != null) ||
      (currentBackStackEntry?.arguments?.getString(NavRoutes.ARG_PLAN_ID) != null)
  ```
* Wired `onMenuClick = { drawerController.openDrawer() }` for `SENSORS`, `EQUIPMENT`, `BIKES`, `SHOES`, `BACKUP_RESTORE`, and `SPORT_TYPES`.
* Wired `onNavigateToSupporter` to pass `pushed = true` from `SENSORS` and `EQUIPMENT`.
* Wired `onNavigateToAdvancedSettings` in `WorkoutClustersScreen` and `DisplaySettingsDialog` to pass `pushed = true`.
* In `TRAINING_ZONES`: `isPushed = backStackEntry.arguments?.getBoolean(NavRoutes.ARG_PUSHED) == true || promptReason != null`. Passes `onOpenDrawer = if (isPushed) null else { { activity.openDrawer() } }`.
* In `ADVANCED_SETTINGS`: Passes `onOpenDrawer = if (isPushed) null else { { activity.openDrawer() } }`.
* In `SUPPORTER`: Passes `onOpenDrawer = if (isPushed) null else { { activity.openDrawer() } }`.
* In `TIPS_GUIDE`: Passes `onOpenDrawer = if (isPushed) null else { { drawerController.openDrawer() } }`.

---

## 3. Verification & Test Evidence

### 3.1 Targeted Unit & Contract Tests
Executed all targeted contract, unit, and navigation tests under `parallel_sprint_lock.py`:
* `SecondaryTabbedHeadersContractTest`: 100% pass (verifies all 4 tabbed screen headers and 4 TopAppBar screens declare context-aware parameters and reference correct icons/strings).
* `SingleActivityNavigationTest`: 100% pass (verifies parameterized routes, drawer ID mapping, and `pushed` builders).
* `NavRoutesZoneTest`: 100% pass (verifies backward-compatible zone route building with and without `pushed`).
* `AdvancedTuningNavigationTest`: 100% pass (verifies `advancedSettings` route with `pushed`).
* `AdvancedTuningScreenTest`: 100% pass (verifies architecture and section ordering).
* `TipsGuideScreenContractTest`: 100% pass (verifies 8 tips and deep link dispatch).
* `ZoneOnboardingBannerContractTest`: 100% pass (verifies deep link contracts and argument bindings).
* `ATrainingTrackerAppNavigationTest`: 100% pass (verifies LIFO back handling and edge interceptor deactivation when secondary screens are pushed).
* `TranslationParityTest`: 100% pass across all 9 supported locales (`navigation_drawer_open`, `back`, `cancel`).

### 3.2 ASPICE Living Documentation & Architecture Governance
* `python3 tools/verify_requirement_governance.py --sync-indexes`
* `python3 tools/verify_requirement_governance.py`
  - `PASS`: Master index tables match all 9 domain shards with 100% parity.
  - `PASS`: Zero unmounted Composable islands detected in git diff.
  - `PASS`: Repository encapsulation verified.
  - `PASS`: Zero architecture modifications required (SWE.2 preserved).
  - `PASS`: Net-new requirement `REQ-UI-518` verified.

---

## 4. Traceability & Stage Status

| Artifact / Entity | ID | Status | Notes |
|:---|:---|:---|:---|
| Requirement | `REQ-UI-518` | **Verified** | Context-Aware Hamburger Menu vs Back Arrow Contract Across Secondary Drawer Groups |
| Test Case | `TST-UI-518` | **Verified** | Secondary Drawer Groups Verification Suite |
| Analysis Subtask | `ATT-7389` | **Erledigt** | Stage 1 Analysis delivered |
| Spec Subtask | `ATT-7391` | **Erledigt** | Stage 2 Req & Test Spec delivered |
| Plan Subtask | `ATT-7392` | **Erledigt** | Stage 3 Implementation Plan delivered |
| Implementation Subtask | `ATT-7402` | **Erledigt** | Stage 4 Implementation delivered |
| Parent Ticket | `ATT-7312` | **In Review** | Ready for Stage 5 Gate Verdict |
