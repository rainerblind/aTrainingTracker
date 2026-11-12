# Stage 1 Analysis: ATT-7313 - Standardize root-drawer Hamburger Menu vs pushed Back Arrow contract and Training and Maps headers

**Ticket**: [ATT-7313](https://rainerblind.atlassian.net/browse/ATT-7313)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.4`  
**Branch**: `subagent-Lane-2-Agent---ATT-7313-self-a33867a0`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
In `ATrainingTrackerApp.kt`, drawer swipe-to-open is restricted to a 40 dp left-edge interceptor (`REQ-UI-161`). However, only 6 of 18 drawer destinations render a Hamburger Menu icon (`Icons.Default.Menu`), leaving primary top-level views (`Workouts`, `Periods`, `Segments`, `Routes`, `Lieblingsorte`) without any visible navigation button. Furthermore, opening filtered Workouts from `Periods`, `Equipment`, or `Sport Types` calls `navigateToDrawerItem(R.id.drawer_workouts)`, wiping the caller off the backstack with `popUpTo(START_TRACKING)` instead of pushing Workouts *above* the caller.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Root RCA**: `WorkoutTabsScreen`, `PeriodsTabsScreen`, `SegmentsTabsScreen`, `RouteTabbedScreen`, and `WorkoutClustersTabsScreen` lack leading navigation action slots in their collapsing primaryContainer headers. In `KnownLocationsScreen.kt`, `onMenuClick` is declared in the signature but unused in the layout.
2. **Backstack Collapse RCA**: `startWorkoutSummaryListFromPeriod()` and `navigateToFilteredWorkouts()` route via `navigateToDrawerItem()`, resetting the navigation graph to `START_TRACKING` and preventing system Back from returning to the origin.
3. **Independent Hypothesis Verification**: Verified that `navController.previousBackStackEntry != null` alone cannot reliably differentiate root from pushed screens because `START_TRACKING` is always preserved under `navigateToDrawerItem()`. An explicit `pushed: Boolean = false` argument on `NavRoutes.WORKOUTS` and `NavRoutes.LOCATIONS` establishes a deterministic contract.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Establish explicit `pushed: Boolean` route contract for `NavRoutes.WORKOUTS` and `NavRoutes.LOCATIONS`.
  * Fix `MainActivityWithNavigation` caller methods to push `NavRoutes.workouts(pushed = true)` onto `navController`.
  * Disable 40 dp left-edge drawer interceptor when the active destination is pushed (`isPushed == true`).
  * Standardize leading `IconButton` (Hamburger vs Back Arrow), tint (`onPrimaryContainer`), contentDescription (`R.string.navigation_drawer_open` vs `R.string.back`), and title spacing across Training and Maps headers.
  * Wire Menu/Back buttons in `WorkoutTabsScreen`, `PeriodsTabsScreen`, `SegmentsTabsScreen`, `RouteTabbedScreen`, `WorkoutClustersTabsScreen`, and `KnownLocationsScreen`.
* **Out-of-Scope Non-Goals**:
  * Altering bottom sheet behavior or refactoring settings drawers.
  * Redesigning telemetry chart layout or map controls.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement**: `REQ-UI-161` (ModalNavigationDrawer Edge-Swipe Restriction, ATT-1310).
* **Historical Origin**: Restricted edge gestures to `x <= 40.dp` to prevent map panning and horizontal pager swipes from inadvertently triggering drawer opening.
* **Preservation of Core Invariants**: `REQ-UI-161` remains 100% active on all top-level drawer destinations. For pushed child destinations (`isPushed == true`), disabling the edge swipe prevents child backstack navigation interference and predictive back conflicts.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-UI-161` (extended for pushed destination immunity), new `REQ-UI-166` (Root Hamburger vs Pushed Back Arrow Contract).
* **Affected Architecture Elements**: `NavRoutes.kt`, `ATrainingTrackerApp.kt`, `MainActivityWithNavigation.kt`, `WorkoutTabsScreen.kt`, `PeriodsTabsScreen.kt`, `SegmentsTabsScreen.kt`, `RouteTabbedScreen.kt`, `WorkoutClustersTabsScreen.kt`, `KnownLocationsScreen.kt`.
* **Verification Impact & Test Surface**: `SingleActivityNavigationTest.kt`, `ATrainingTrackerAppNavigationTest.kt`, `TranslationParityTest.kt`, `ThemeColorCentralizationAuditTest.kt`.
* **Risk Assessment & Invariants**: Low risk. All 21 drawer destinations and existing bottom sheet flows remain intact.

---

## 6. Architectural Strategy & High-Level Solution
1. **NavRoutes & Navigation Contract**:
   * Add `WORKOUTS_PATTERN = "workouts?pushed={pushed}"` and `LOCATIONS_PATTERN = "locations?clusterId={clusterId}&pushed={pushed}"` with `ARG_PUSHED = "pushed"`.
   * Add helper builder `NavRoutes.workouts(pushed: Boolean = false)`.
2. **ATrainingTrackerApp Integration**:
   * Inspect current destination `pushed` argument; deactivate 40 dp swipe when `isPushed == true`.
   * Pass `onMenuClick = { drawerController.openDrawer() }` and `onBackClick = { navController.popBackStack() }` based on `isPushed`.
3. **Collapsible Header Alignment**:
   * Standardize leading `IconButton` with `Icons.Default.Menu` or `Icons.AutoMirrored.Filled.ArrowBack` at `start = 4.dp`, followed by `titleLarge` text.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Back navigation pops cleanly; filter criteria cleared when popping filtered views; zero visual title jumping.
* **Risk Rating**: **LOW**. Pure UI/Compose navigation contract without database or background service impact.
