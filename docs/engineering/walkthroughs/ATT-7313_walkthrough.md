# Walkthrough: Standardize Root-Drawer Hamburger Menu vs Pushed Back Arrow Contract and Training and Maps Screen Headers (ATT-7313)

* **Parent Ticket**: [ATT-7313](https://rainerblind.atlassian.net/browse/ATT-7313) (*[Verbesserung] Standardize root-drawer Hamburger Menu vs pushed Back Arrow contract and Training and Maps headers*)
* **Sub-Tasks**:
  * [ATT-7372](https://rainerblind.atlassian.net/browse/ATT-7372) (*[SWE.1] System & Software Requirements Analysis*) - `Erledigt`
  * [ATT-7374](https://rainerblind.atlassian.net/browse/ATT-7374) (*[SWE.4] Verification Specification*) - `Erledigt`
  * [ATT-7375](https://rainerblind.atlassian.net/browse/ATT-7375) (*[SWE.2 / SWE.3] Architecture, Detailed Design & Implementation Plan*) - `Erledigt`
  * [ATT-7381](https://rainerblind.atlassian.net/browse/ATT-7381) (*[SWE.3] Construction & Implementation*) - `Erledigt`
* **Target Version**: `V5.0.0`
* **Requirement**: [`REQ-UI-517`](file:///home/rainer/.gemini/antigravity/worktrees/e228d70c-9ed0-47cc-a7a4-f4fb443c5e2b/subagent-Lane-2-Agent---ATT-7313-self-a33867a0/docs/requirements/req_ui_design_system.md#L15548) (*Standardize Root-Drawer Hamburger Menu vs Pushed Back Arrow Contract and Training and Maps Screen Headers*)
* **Test Specification**: [`TST-UI-517`](file:///home/rainer/.gemini/antigravity/worktrees/e228d70c-9ed0-47cc-a7a4-f4fb443c5e2b/subagent-Lane-2-Agent---ATT-7313-self-a33867a0/docs/tests/tst_ui_design_system.md#L8338) (*Root Hamburger vs Pushed Back Arrow Contract and Training/Maps Header Leading Action Verification Suite*)
* **Branch**: `subagent-Lane-2-Agent---ATT-7313-self-a33867a0`

---

## 1. Overview & Root Cause Analysis

In `ATrainingTrackerApp.kt`, drawer swipe-to-open is intentionally restricted to a narrow `40 dp` left-edge gesture interceptor (`REQ-UI-161`) to avoid accidental drawer openings while interacting with horizontal tab carousels or panning maps. However, only 6 of the 18 drawer destinations rendered a Hamburger Menu button (`Icons.Default.Menu`), leaving primary top-level views (`Workouts`, `Periods`, `Segments`, `Routes`, `Lieblingsorte`) without any visible navigation control.

Additionally, two navigation contract defects broke the root-vs-pushed UX model:
1. **Backstack Reset on Filtered Workouts**: Navigating from `Periods` (`startWorkoutSummaryListFromPeriod`) or from `Equipment` / `Sport Types` (`navigateToFilteredWorkouts`) called `navigateToDrawerItem(R.id.drawer_workouts)` which executed `popUpTo(START_TRACKING)`, destroying caller backstack state instead of pushing Workouts *above* the origin view.
2. **Ambiguous Root vs Pushed State**: Because `navigateToDrawerItem()` retains `START_TRACKING` on the backstack, `previousBackStackEntry != null` alone could not distinguish whether a screen was opened from the drawer root or pushed contextually above another screen.

---

## 2. Summary of Implementation Changes

### 2.1 Navigation Route Contract (`NavRoutes.kt`)
* Added `WORKOUTS_PATTERN = "workouts?pushed={pushed}"` and `ARG_PUSHED = "pushed"`.
* Added helper builder `NavRoutes.workouts(pushed: Boolean = false)` returning `"workouts?pushed=true"` when pushed and `"workouts"` otherwise.
* Preserved `LOCATIONS_PATTERN = "locations?clusterId={clusterId}"` with cluster query resolution.

### 2.2 Caller Interoperability & Backstack Preservation (`MainActivityWithNavigation.kt`)
* Updated `startWorkoutSummaryListFromPeriod(periodSummary, bSportType, scrollToWorkoutId)`:
  - Removed `navigateToDrawerItem(R.id.drawer_workouts)`.
  - Now dispatches `navController?.navigate(NavRoutes.workouts(pushed = true))`, maintaining the caller screen on the backstack.
* Updated `navigateToFilteredWorkouts(bSportType, equipmentType, equipmentId)`:
  - Removed `navigateToDrawerItem(R.id.drawer_workouts)`.
  - Dispatches `navController?.navigate(NavRoutes.workouts(pushed = true))`.

### 2.3 Navigation Graph & Drawer Interceptor Wiring (`ATrainingTrackerApp.kt`)
* Computed `isCurrentScreenPushed`:
  ```kotlin
  val isCurrentScreenPushed = currentBackStackEntry?.arguments?.getBoolean(NavRoutes.ARG_PUSHED) == true ||
      (currentBackStackEntry?.arguments?.getLong(NavRoutes.ARG_LOCATION_ID, -1L) ?: -1L) > 0 ||
      (currentBackStackEntry?.arguments?.getLong(NavRoutes.ARG_CLUSTER_ID, -1L) ?: -1L) > 0
  ```
* Passed `isCurrentScreenPushed` to `pointerInput(drawerState, isCurrentScreenPushed)`. When `isCurrentScreenPushed == true`, the `40 dp` edge-swipe interceptor bails out early, leaving system Back gestures and horizontal scrolling clean.
* Wired `WORKOUTS_PATTERN`:
  - Registers `navArgument(NavRoutes.ARG_PUSHED) { type = NavType.BoolType; defaultValue = false }`.
  - Supplies `onMenuClick = if (isPushed) null else { { drawerController.openDrawer() } }`.
  - Supplies `onBackClick = if (isPushed) { { summariesViewModel.clearFilterCriteria(); navController.popBackStack() } } else null`.
* Wired `PERIODS`, `SEGMENTS`, `ROUTES`, and `START_LOCATIONS`:
  - Provided `onMenuClick = { drawerController.openDrawer() }` to open the drawer from root views.
  - In `START_LOCATIONS`, drill-downs to filtered workouts or clusters navigate with `workouts(pushed = true)` and `locations(clusterId)`.
* Wired `LOCATIONS_PATTERN`:
  - Correctly evaluates `isPushed = clusterIdArg != null || clusterFilterCriteria.startLocationName != null`.
  - Clears `filterCriteria` and pops backstack on Back.

### 2.4 Shared Collapsible Header Alignment
Standardized leading `IconButton` placement and typography across 6 Training and Maps views:
* `WorkoutTabsScreen.kt` & `WorkoutSummariesTabbedScreen.kt`
* `PeriodsTabsScreen.kt` & `PeriodsScreen.kt`
* `SegmentsTabsScreen.kt` & `StarredSegmentsScreen.kt`
* `RouteTabbedScreen.kt` & `RoutesScreen.kt`
* `WorkoutClustersTabsScreen.kt` & `WorkoutClustersScreen.kt`
* `KnownLocationsScreen.kt`

Key styling standards:
- Header Row padding: `padding(start = 4.dp, end = 16.dp)`.
- Leading icon size: `48.dp` target (`size = 48.dp` / default `IconButton`).
- Icon tint: `MaterialTheme.colorScheme.onPrimaryContainer`.
- Icon content description: `R.string.navigation_drawer_open` when rendering Menu; `R.string.back` when rendering ArrowBack.
- Title text padding: `Modifier.padding(start = 4.dp)` with `MaterialTheme.typography.titleLarge` for exact `56.dp` alignment across all views.
- Sub-views (`TrackOnMapScreen`, `EditWorkoutScreen`, `PeriodMapScreen`, `RouteOnMapScreen`, `WorkoutClusterHeatmapScreen`) retain their existing `ArrowBack` and navigation hierarchy.

### 2.5 9-Language Localization Parity
Added `navigation_drawer_open` across all supported locales:
- `values/strings.xml`: `Open navigation drawer`
- `values-de/strings.xml`: `Navigationsmenü öffnen`
- `values-es/strings.xml`: `Abrir menú de navegación`
- `values-fr/strings.xml`: `Ouvrir le menu de navigation`
- `values-it/strings.xml`: `Apri menu di navigazione`
- `values-ja/strings.xml`: `ナビゲーション ドロワーを開く`
- `values-nl/strings.xml`: `Navigatielade openen`
- `values-pl/strings.xml`: `Otwórz panel nawigacji`
- `values-pt/strings.xml`: `Abrir gaveta de navegação`

---

## 3. Verification & Test Evidence

### 3.1 Targeted Navigation and Localization Tests
Executed targeted unit tests, translation parity tests, and theme audit under sprint build lock:
```bash
python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest \
  --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" \
  --tests "com.atrainingtracker.trainingtracker.build.TranslationParityTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"
```
**Result**: `BUILD SUCCESSFUL in 30s` (33 actionable tasks: 6 executed, 27 up-to-date, 0 failures).

### 3.2 ASPICE Governance & Living Documentation Verification
Executed automated governance and living documentation consistency tool:
```bash
python3 tools/verify_requirement_governance.py --sync-indexes
python3 tools/verify_requirement_governance.py
```
**Result**:
- `PASS`: Living documentation master tables match all 9 domain shards with 100% parity.
- `PASS`: Zero unmounted Composable islands detected in git diff.
- `PASS`: Repository encapsulation verified (zero leaky DatabaseManager/Cursor members).
- `PASS`: Zero architecture or system specification modifications detected.
- `PASS`: Net-new requirement `REQ-UI-517` verified.

---

## 4. Traceability & Stage Status

| Artifact / Entity | ID | Status | Notes |
|:---|:---|:---|:---|
| Requirement | `REQ-UI-517` | **Verified** | Standardize Root-Drawer Hamburger Menu vs Pushed Back Arrow Contract |
| Test Case | `TST-UI-517` | **Verified** | Root Hamburger vs Pushed Back Arrow Contract Verification Suite |
| Analysis Subtask | `ATT-7372` | **Erledigt** | Stage 1 Analysis delivered |
| Spec Subtask | `ATT-7374` | **Erledigt** | Stage 2 Req & Test Spec delivered |
| Plan Subtask | `ATT-7375` | **Erledigt** | Stage 3 Implementation Plan delivered |
| Implementation Subtask | `ATT-7381` | **Erledigt** | Stage 4 Implementation delivered |
| Parent Ticket | `ATT-7313` | **In Review** | Ready for Stage 5 Gate Verdict |
