# Stage 3: Implementation Plan - ATT-2459: Remove tabs and Heimweg card from route selector in favor of clean proximity recency list

**Ticket**: [ATT-2459](https://atrainingtracker.atlassian.net/browse/ATT-2459)  
**Sub-task**: [ATT-2569](https://atrainingtracker.atlassian.net/browse/ATT-2569) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-280`  
**Test Mapping**: `TST-UI-240`  
**Branch**: `feature/ATT-2459`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Description & Background

During on-device review of the Route Selector bottom sheet ("Route wählen", `RouteSelectorSheet.kt`), the athlete experience was impaired by redundant controls and context-mismatched actions:
1. **Redundant Sort/Filter Tab Bar**: A 3-tab bar (*In der Nähe*, *Zuletzt gefahren*, *Länge*) was rendered whenever the athlete had 5 or more saved routes (`REQ-MAP-024` Clause 3). When preparing to ride, athletes overwhelmingly want routes relevant to their current location, not manual switching between multiple tabs.
2. **Context-Mismatched "Heimweg" Card**: A prominent "Heimweg" (Take Me Home) card was pinned to the top of the route selector (`REQ-MAP-029` / ATT-1953). When an athlete is starting a workout from home or a designated starting location, offering return navigation is redundant and distracting. Heimweg is strictly a mid-ride return navigation action (handled separately in ATT-2462) and does not belong in the pre-ride route selection sheet.

The goal of ATT-2459 is to streamline `RouteSelectorSheet.kt` and `RouteSelectorViewModel.kt` into a focused, proximity-recency route list without filter tabs and without the pinned Heimweg card.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-280` (*Streamlined Route Selector: Removal of Sort Tabs and Pinned Heimweg Card in Favor of Proximity-Recency Ranking*)
* **Test Mapping**: `TST-UI-240` (*Route Selector Streamlining: Pinned Heimweg Card and Sort Tab Bar Removal Contract and Unit Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites continue to pass cleanly.
2. **Route Selection & Cancellation Integrity**: 1-tap route selection (`selectRoute`) and cancellation (`stopRoute` / `clearRoute`) remain fully functional.
3. **In-Ride Auto-Detection Banner Intact**: In-ride automated route detection engine (`RouteAutoDetector`) and the candidate banner (`AutoDetectedRouteBanner`) in both `SensorGridScreen.kt` and `RouteSelectorSheet.kt` remain completely intact.
4. **Proximity Tie-Breaking Logic Preserved**: `RouteProximityRanker` continues to execute its 5-tier ranking (proximity group, sport profile matching, departure bearing alignment, recency, distance/name).
5. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `RouteSelectorSheet.kt` (UI Layer)
* Remove the pinned "Take Me Home" ("Heimweg") card (`Card(onClick = onTakeMeHome, ...)`).
* Remove the `onTakeMeHome` parameter from `RouteSelectorModalBottomSheet` and `RouteSelectorContent`.
* Remove the `TabRow` block and the conditional `if (uiState.showFilterTabs)` check.
* Retain `ActiveRouteBanner`, `AutoDetectedRouteBanner`, and `RouteCard` items within `LazyColumn`.
* Retain the empty state placeholder when no routes are available.

### Component 2: `RouteSelectorViewModel.kt` (ViewModel Layer)
* Eliminate `_selectedTab` StateFlow and `RouteFilterTab` enum.
* Streamline `uiState` combine block: directly evaluate `RouteProximityRanker.rankRoutes(...)` using current location, bearing, and recency tie-breaking.
* Remove `showFilterTabs` and `selectedTab` fields from `RouteSelectorUiState`.
* Remove `setFilterTab(tab: RouteFilterTab)` method.

### Component 3: `TrackingTabsScreen.kt` (Integration Call Site)
* Remove `onTakeMeHome` argument from `RouteSelectorModalBottomSheet` invocation (lines 712–718).

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `RouteSelectorSheet.kt` (clean Material 3 BottomSheet pattern established in ATT-1835 and ATT-2458).
* **Reused components**: `BottomSheetDesign.SheetShape`, `ActiveRouteBanner`, `AutoDetectedRouteBanner`, `RouteCard`.
* **Theme tokens**:
  - Shapes: `BottomSheetDesign.SheetShape` for bottom sheet container, `RoundedCornerShape(12.dp)` for cards.
  - Spacing: 16.dp sheet padding, 8.dp list item separation.
  - Colors: `MaterialTheme.colorScheme.surface`, `primaryContainer`, `secondaryContainer`, `surfaceVariant`.
* **New one-off styles & justification**: None. All styling strictly conforms to existing design tokens.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `RouteSelectorViewModel.kt` & `RouteSelectorUiState`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorViewModel.kt`
* Changes:
  - Remove `RouteFilterTab` enum.
  - Remove `showFilterTabs` and `selectedTab` from `RouteSelectorUiState`.
  - Remove `_selectedTab` StateFlow and `setFilterTab(...)`.
  - In `uiState`, combine `allRoutes`, `activeRouteId`, `_lastLocation`, and `_autoDetectedCandidate` directly into `RouteProximityRanker.rankRoutes(...)`.

### Step 2: Update `RouteSelectorSheet.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheet.kt`
* Changes:
  - Remove `onTakeMeHome` parameter from `RouteSelectorModalBottomSheet` and `RouteSelectorContent`.
  - Remove the pinned "Heimweg" `Card` composable block (lines 128–167).
  - Remove the `if (uiState.showFilterTabs) { TabRow(...) }` composable block (lines 196–209).
  - Clean up unused imports (`Tab`, `TabRow`, `painterResource`, etc.).

### Step 3: Update `TrackingTabsScreen.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`
* Changes:
  - Remove `onTakeMeHome` callback argument from `RouteSelectorModalBottomSheet(...)`.

### Step 4: Update Contract & ViewModel Tests
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheetTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorViewModelTest.kt`
* Changes:
  - In `RouteSelectorSheetTest.kt`: Assert that `RouteSelectorSheet.kt` does NOT contain `TabRow`, does NOT contain `showFilterTabs`, and does NOT contain `onTakeMeHome` or `ic_nav_home`.
  - In `RouteSelectorViewModelTest.kt`: Replace tab switching tests with assertions that `uiState.routes` is always proximity-recency ranked directly via `RouteProximityRanker`. Remove obsolete `showFilterTabs` tests.

### Step 5: Run Targeted Unit Tests
* Commands:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.*"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.*"`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Step-by-step compilation and targeted test execution during Stage 4.
  - Full clean-room test suite (`./gradlew testDebugUnitTest`) during Stage 5.
* **Rollback**:
  - Feature branch `feature/ATT-2459` provides complete isolation from `sprint/2026-41.1` and `develop`. Git revert or resetting branch state allows immediate recovery in case of unexpected regressions.
