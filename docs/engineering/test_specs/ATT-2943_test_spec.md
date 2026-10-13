# Stage 2 Requirement & Test Specification: ATT-2943 - Remove duplicate upper active route banner from Route Selector dialog

**Ticket**: [ATT-2943](https://atrainingtracker.atlassian.net/browse/ATT-2943)  
**Sub-task**: [ATT-2995](https://atrainingtracker.atlassian.net/browse/ATT-2995) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2943`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-UI-325)

### 1.1 Requirement Definition
* **Requirement ID**: `REQ-UI-325`
* **Title**: Removal of Duplicate Upper Active Route Banner and Direct RouteCard Cancellation Integration
* **Type**: Functional / UI Interaction Specification
* **Target Release**: `V4.9.40`
* **Status**: Specified
* **Amends**: `REQ-UI-280` (*Streamlined Route Selector: Removal of Sort Tabs and Pinned Heimweg Card in Favor of Proximity-Recency Ranking*, Clause 3, ATT-2459)
* **Parent Ticket**: ATT-2943

### 1.2 Description
The system shall eliminate duplicate active route representation in `RouteSelectorSheet.kt` by removing the pinned upper `ActiveRouteBanner` from `RouteSelectorContent` and integrating direct 1-tap route cancellation onto the active `RouteCard` in the scrollable route list:
1. *Elimination of Upper Active Route Banner (`RouteSelectorSheet.kt`)*:
   - `RouteSelectorContent` shall NOT compose `ActiveRouteBanner` or render an upper active route card beneath the sheet header.
   - The active route shall appear exclusively within the scrollable `LazyColumn` route list rendered by `RouteCard`.
2. *Direct 1-Tap Active Route Cancellation on `RouteCard`*:
   - `RouteCard` shall accept parameter `onClearRoute: (() -> Unit)? = null`.
   - When `isActive == true`, `RouteCard` shall render an `IconButton` with `Icons.Default.Close` (`contentDescription = stringResource(id = R.string.route_action_clear)`), allowing the athlete to stop/clear active navigation with 1 tap directly from the route item.
   - Clicking on an inactive `RouteCard` shall select that route (`viewModel.selectRoute(route.summary.id)`) and trigger `onRouteSelected`.
3. *Preservation of System Invariants*:
   - `ActiveRouteBanner` composable definition shall remain present in `RouteSelectorSheet.kt` for API/test contract backward compatibility.
   - Active route visual distinction via `secondaryContainer` card background and `"ACTIVE"` badge chip must remain strictly intact.
   - Mid-Ride Heimweg card (`MidRideHeimwegCard`), authentic sport icon colors (`Color.Unspecified`), empty state handling, and 100% test pass rate must be preserved.

### 1.3 Acceptance Criteria (Given-When-Then)

#### Scenario 1: Single Representation of Active Route
* **Given** an active navigated route set in the route repository,
* **When** the athlete opens `RouteSelectorModalBottomSheet`,
* **Then** `RouteSelectorContent` shall NOT render the upper `ActiveRouteBanner`,
* **And** the active route shall appear exactly once inside the scrollable route list.

#### Scenario 2: Direct 1-Tap Route Cancellation
* **Given** the active route rendered as `RouteCard` in the route list,
* **When** inspecting the card,
* **Then** it displays the `"ACTIVE"` badge chip and a close/clear action button (`Icons.Default.Close`),
* **And** tapping the close button immediately stops active navigation (`viewModel.stopRoute()`).

#### Scenario 3: Candidate Route Selection
* **Given** an inactive route card in the list,
* **When** the athlete taps the card,
* **Then** that route is selected and the bottom sheet dismisses.

---

## 2. Test Specification (TST-UI-285)

### 2.1 Test Definition
* **Test ID**: `TST-UI-285`
* **Title**: Single Active Route Card Presentation and Direct In-List Cancellation Verification
* **Target Requirement**: `REQ-UI-325`
* **Test Type**: Automated Architectural Contract & UI Tests (`RouteSelectorSheetTest.kt`)
* **Status**: Specified

### 2.2 Test Cases

#### Case 1: `testRouteSelectorContent_doesNotRenderUpperActiveRouteBanner` (`RouteSelectorSheetTest.kt`)
* **Verification**:
  - Extract `RouteSelectorContent` source code block from `RouteSelectorSheet.kt`.
  - Assert that `RouteSelectorContent` does NOT contain `ActiveRouteBanner(`.
  - Assert that `ActiveRouteBanner` function definition remains exposed in `RouteSelectorSheet.kt`.

#### Case 2: `testRouteCard_supportsDirectCancellationAction` (`RouteSelectorSheetTest.kt`)
* **Verification**:
  - Verify `RouteCard` signature accepts `onClearRoute: (() -> Unit)? = null`.
  - Verify that when `isActive && onClearRoute != null`, `RouteCard` composes `IconButton` with `Icons.Default.Close` and content description `R.string.route_action_clear`.

#### Case 3: Clean-Room Full Regression Suite
* **Verification**:
  - Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across the full suite.

---

## 3. Traceability Matrix

| Requirement ID | Test Case ID | Test Class | Verification Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-325` | `TST-UI-285` | `RouteSelectorSheetTest` | Upper banner exclusion & in-list cancellation | Specified |
| `REQ-UI-280` | `TST-UI-240` | `RouteSelectorSheetTest` | Absence of sort tabs and pinned Heimweg card | Verified |
| `REQ-UI-310` | `TST-UI-280` | `RouteSelectorSheetTest` | Active badge chip and authentic sport icons | Verified |
