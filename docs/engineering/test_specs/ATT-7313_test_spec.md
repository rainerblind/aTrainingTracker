# Stage 2: Requirement & Test Specification - ATT-7313: Standardize root-drawer Hamburger Menu vs pushed Back Arrow contract and Training and Maps headers

**Ticket**: [ATT-7313](https://rainerblind.atlassian.net/browse/ATT-7313)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.4`  
**Requirement Mapping**: `REQ-UI-517` (*Root-Drawer Hamburger Menu vs Pushed Back Arrow Contract and Training/Maps Headers*)  
**Test Spec ID**: `TST-UI-517`  
**Branch**: `subagent-Lane-2-Agent---ATT-7313-self-a33867a0`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-UI-517)

### 1.1 Problem Statement & Rationale
Only 6 of 18 drawer destinations render a visible Hamburger Menu icon, leaving core Training and Maps views without a visible way to open the navigation drawer. In addition, opening filtered Workouts from Periods or Equipment calls `navigateToDrawerItem`, inadvertently wiping the backstack.

### 1.2 Functional & Architectural Requirements
1. **Deterministic Route Contract**:
   * `NavRoutes.WORKOUTS` and `NavRoutes.LOCATIONS` SHALL support an optional query parameter `pushed: Boolean = false` (`ARG_PUSHED = "pushed"`).
   * Helper functions `NavRoutes.workouts(pushed: Boolean)` and `NavRoutes.locations(clusterId: Long?, pushed: Boolean)` SHALL build type-safe route strings.
2. **Pushed Destination Contract**:
   * When `isPushed == true`, the 40 dp left-edge drawer interceptor in `ATrainingTrackerApp.kt` SHALL be deactivated.
   * The top header SHALL display `Icons.AutoMirrored.Filled.ArrowBack` with contentDescription `R.string.back`.
   * Tapping Back or invoking system Back SHALL pop back to the caller and clear contextual filter criteria (`WorkoutFilterCriteria`, `ClusterFilterCriteria`).
   * `MainActivityWithNavigation.startWorkoutSummaryListFromPeriod()` and `navigateToFilteredWorkouts()` SHALL push `NavRoutes.workouts(pushed = true)` onto `navController`.
3. **Root Destination Contract**:
   * When opened from the drawer (`isPushed == false`), screens SHALL display `Icons.Default.Menu` with contentDescription `R.string.navigation_drawer_open`.
   * Tapping Menu SHALL invoke `drawerController.openDrawer()`.
   * The 40 dp edge interceptor SHALL remain active.
4. **Shared Header Leading-Icon Alignment**:
   * Headers in `WorkoutTabsScreen`, `PeriodsTabsScreen`, `SegmentsTabsScreen`, `RouteTabbedScreen`, `WorkoutClustersTabsScreen`, and `KnownLocationsScreen` SHALL standardize leading `IconButton` placement (48 dp target, `start = 4.dp`), tint (`MaterialTheme.colorScheme.onPrimaryContainer`), and title spacing.
5. **Sub-View Drill-Down Integrity**:
   * In-screen detail views (`TrackOnMapScreen`, `EditWorkoutScreen`, `PeriodMapScreen`, `RouteOnMapScreen`, `WorkoutClusterHeatmapScreen`) SHALL retain `ArrowBack` returning to their parent list.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Hamburger Menu on Root Screens)**:
  * *Given* the athlete navigates to Workouts, Periods, Segments, Routes, or Lieblingsorte from `AppNavigationDrawer`,
  * *When* the destination screen renders at its top level,
  * *Then* the header displays `Icons.Default.Menu` and clicking it opens the navigation drawer.
* **Criterion 2 (Back Arrow on Pushed Views)**:
  * *Given* the athlete opens Workouts from Periods, Start Locations, Equipment, or Sport Types (or opens Lieblingsorte from Start Locations),
  * *When* the screen renders,
  * *Then* the header displays `Icons.AutoMirrored.Filled.ArrowBack` and tapping it pops cleanly back to the origin screen, clearing filter criteria.
* **Criterion 3 (Visual Alignment)**:
  * *Given* the athlete switches between Training and Maps tabbed screens,
  * *When* observing the header title and leading action icon,
  * *Then* there is zero horizontal or vertical title shift.

### 1.4 System Invariants
* All 21 navigation drawer destinations and routes in `NavRoutes` preserved.
* `REQ-UI-161` 40 dp edge interceptor preserved for root screens.
* `REQ-UI-162` LIFO back handling hierarchy preserved.

---

## 2. Test Specification (TST-UI-517)

### Test Case 1: Route Contract & Pushed Argument Parsing (`TST-UI-517.1`)
* **Scope**: Unit Test (`SingleActivityNavigationTest.kt`)
* **Action**: Validate route string formatting for `workouts(pushed = true/false)` and `locations(clusterId, pushed)`.

### Test Case 2: MainActivityWithNavigation Push Contract (`TST-UI-517.2`)
* **Scope**: Unit Test (`MainActivityWithNavigationInteropTest.kt`)
* **Action**: Validate `startWorkoutSummaryListFromPeriod` and `navigateToFilteredWorkouts` push routes with `pushed = true`.

### Test Case 3: 9-Language Localization Audit (`TST-UI-517.3`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Goal**: Verify `navigation_drawer_open` and `back` are present in all 9 supported locales with 100% parity.

### Test Case 4: Collapsible Header Alignment & Theme Tint Audit (`TST-UI-517.4`)
* **Scope**: UI & Theme Contract Test (`ThemeColorCentralizationAuditTest.kt`)
* **Goal**: Verify `onPrimaryContainer` tint across all leading icons.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Test Class / Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-517.1` | Unit | `SingleActivityNavigationTest.testNavRoutesPushedContracts` | `REQ-UI-517` | Specified |
| `TST-UI-517.2` | Unit | `MainActivityWithNavigationInteropTest.testPushNavigationContracts` | `REQ-UI-517` | Specified |
| `TST-UI-517.3` | Localization | `TranslationParityTest` | `REQ-UI-517`, `REQ-UI-106` | Specified |
| `TST-UI-517.4` | Theme Contract | `ThemeColorCentralizationAuditTest` | `REQ-UI-517` | Specified |
