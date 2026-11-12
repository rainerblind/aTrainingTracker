# Stage 3: Implementation Plan - ATT-7313: Standardize root-drawer Hamburger Menu vs pushed Back Arrow contract and Training and Maps headers

**Ticket**: [ATT-7313](https://rainerblind.atlassian.net/browse/ATT-7313)  
**Sub-task**: [[Impl-Plan]](https://rainerblind.atlassian.net/browse/ATT-7313)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.4`  
**Requirement Mapping**: `REQ-UI-517`  
**Test Mapping**: `TST-UI-517`  
**Branch**: `subagent-Lane-2-Agent---ATT-7313-self-a33867a0`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background
In `ATrainingTrackerApp.kt`, only 6 of 18 drawer destinations display a Hamburger Menu icon (`Icons.Default.Menu`), leaving primary top-level views without an obvious way to open the navigation drawer. Furthermore, drilling down into filtered Workouts from `Periods` or `Equipment` calls `navigateToDrawerItem`, inadvertently wiping the backstack. This plan standardizes the root-drawer vs pushed-above navigation contract and aligns leading header actions across all Training and Maps screens.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-517` (*Root-Drawer Hamburger Menu vs Pushed Back Arrow Contract and Training/Maps Headers*)
* **Test Mapping**: `TST-UI-517` (*Root Hamburger vs Pushed Back Arrow Contract and Training/Maps Header Leading Action Verification Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Drawer Destinations**: All 21 navigation drawer destinations and routes in `NavRoutes` preserved.
2. **Gesture Bounds (`REQ-UI-161`)**: 40 dp left-edge drawer interceptor preserved on root destinations; deactivated on pushed destinations.
3. **Back Handling Hierarchy (`REQ-UI-162`)**: Conditional LIFO back handling strictly preserved.
4. **Sub-View Integrity**: In-screen detail views (`TrackOnMapScreen`, `EditWorkoutScreen`, `PeriodMapScreen`, etc.) retain `ArrowBack`.
5. **Code Lock**: Strictly verify Gate 3 status before making production changes.

---

## 4. Proposed Architectural Changes

### Component 1: Route Contract & Interceptor (`NavRoutes.kt`, `ATrainingTrackerApp.kt`, `MainActivityWithNavigation.kt`)
* Add `pushed: Boolean = false` query parameter to `WORKOUTS` and `LOCATIONS` routes.
* Update `startWorkoutSummaryListFromPeriod()` and `navigateToFilteredWorkouts()` to push `NavRoutes.workouts(pushed = true)`.
* Deactivate 40 dp left-edge drawer swipe in `ATrainingTrackerApp.kt` when `isPushed == true`.

### Component 2: Standardized Collapsible Headers
* Wire `IconButton` with `Icons.Default.Menu` or `Icons.AutoMirrored.Filled.ArrowBack` across:
  * `WorkoutTabsScreen.kt` & `WorkoutSummariesTabbedScreen.kt`
  * `PeriodsTabsScreen.kt` & `PeriodsScreen.kt`
  * `SegmentsTabsScreen.kt` & `StarredSegmentsScreen.kt`
  * `RouteTabbedScreen.kt` & `RoutesScreen.kt`
  * `WorkoutClustersTabsScreen.kt` & `WorkoutClustersScreen.kt`
  * `KnownLocationsScreen.kt`

### UI Consistency (Rule 23)
* **Reference screen**: `AutoPauseTabsScreen.kt` / `MapScreenWithTrack.kt`
* **Reused components**: Standard Material 3 `IconButton`, `Icon`, `Text` inside `Surface(color = MaterialTheme.colorScheme.primaryContainer)`
* **Theme tokens**: `MaterialTheme.colorScheme.onPrimaryContainer`, `titleLarge` typography, `HEADER_TITLE_ROW_HEIGHT = 64.dp`, `start = 4.dp` horizontal icon padding.
* **New one-off styles**: None. Standard Material 3 design tokens only.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Localization Parity
* Files: `app/src/main/res/values*/strings.xml` (all 9 locales)
* Add `navigation_drawer_open` with exact translations.

### Step 2: Route & Activity Push Contract
* Files: `NavRoutes.kt`, `MainActivityWithNavigation.kt`
* Add `ARG_PUSHED`, `WORKOUTS_PATTERN`, `LOCATIONS_PATTERN`, `workouts(pushed)`, `locations(clusterId, pushed)`.
* Update `startWorkoutSummaryListFromPeriod()` and `navigateToFilteredWorkouts()` to push `NavRoutes.workouts(pushed = true)`.

### Step 3: ATrainingTrackerApp Navigation & Interceptor State
* Files: `ATrainingTrackerApp.kt`
* Check `isPushed` on `currentBackStackEntry`. Conditionally disable 40 dp swipe.
* Wire `onMenuClick` and `onBackClick` callbacks to screens.

### Step 4: Header Leading Actions in Training & Maps Screens
* Files: `WorkoutTabsScreen.kt`, `WorkoutSummariesTabbedScreen.kt`, `PeriodsTabsScreen.kt`, `PeriodsScreen.kt`, `SegmentsTabsScreen.kt`, `StarredSegmentsScreen.kt`, `RouteTabbedScreen.kt`, `RoutesScreen.kt`, `WorkoutClustersTabsScreen.kt`, `WorkoutClustersScreen.kt`, `KnownLocationsScreen.kt`.
* Wire standardized leading `IconButton` and title padding.

### Step 5: Unit & Contract Tests
* Files: `SingleActivityNavigationTest.kt`, `MainActivityWithNavigationInteropTest.kt`, `ATrainingTrackerAppNavigationTest.kt`.
* Add tests for route formatting, push methods, and interceptor deactivation. Run `TranslationParityTest` and `ThemeColorCentralizationAuditTest`.

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted package tests under `python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*"`.
* **Rollback**: Branch isolation allows full revert via git without affecting sprint branch.
