# Stage 2: Requirement & Test Specification - ATT-7526: Fix IllegalStateException in MainActivityWithNavigation.navigateToDrawerItem before NavHost sets graph

**Ticket**: [ATT-7526](https://rainerblind.atlassian.net/browse/ATT-7526)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No Crashes & App Stability (Crash-Free Resilience)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.5`  
**Requirement Mapping**: `REQ-UI-162` (*Single Source of Truth & Scoped Overlay Back Navigation*)  
**Test Spec ID**: `TST-UI-114`  
**Branch**: `feature/ATT-7526`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-UI-162)

### 1.1 Problem Statement & Rationale
When `MainActivityWithNavigation` queues a pending drawer navigation (`mPendingDrawerItemId = itemId`) prior to Compose initialization (e.g. cold start, state restoration, early intents), `DisposableEffect(navController)` located outside `Scaffold` executes `activity.onNavControllerReady(navController)` before `NavHost` inside `Scaffold` content subcomposes and invokes `navController.setGraph()`. Calling `controller.graph.findStartDestination()` triggers `IllegalStateException: You must call setGraph() before calling getGraph()`.

### 1.2 Attributes
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. **Order of Composition**: In `ATrainingTrackerApp.kt`, `DisposableEffect(navController)` and initial route synchronization SHALL be composed inside `Scaffold` content block *after* `NavHost(...)`, guaranteeing `NavHost` attaches the graph before `activity.onNavControllerReady(navController)` executes.
2. **Defensive Graph Readiness Guard**: `MainActivityWithNavigation.kt` SHALL provide `private fun isNavGraphReady(controller: NavHostController?): Boolean` verifying `controller != null && runCatching { controller.graph }.isSuccess`.
3. **Queueing on Uninitialized Graph**: `navigateToDrawerItem(itemId)` SHALL check `if (!isNavGraphReady(controller))` and safely buffer `mPendingDrawerItemId = itemId` without touching `controller.graph`.
4. **Filtered Workouts & Period Fallback**: `navigateToFilteredWorkouts(...)` and `startWorkoutSummaryListFromPeriod(...)` SHALL verify `isNavGraphReady(controller)` before navigating, falling back safely to `navigateToDrawerItem(R.id.drawer_workouts)` when uninitialized.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (Early Pending Navigation)**: *Given* `navigateToDrawerItem` called before Compose initializes, *When* `ATrainingTrackerApp` composes, *Then* `NavHost` attaches graph before `onNavControllerReady` executes without `IllegalStateException`.
* **AC-2 (Defensive Queuing)**: *Given* `navController` has no graph attached, *When* `navigateToDrawerItem(itemId)` is invoked, *Then* it safely records `mPendingDrawerItemId = itemId` and returns `true`.
* **AC-3 (Deferred Dispatch)**: *Given* buffered `mPendingDrawerItemId`, *When* `onNavControllerReady` runs with ready graph, *Then* it dispatches navigation and clears pending ID.

### 1.5 System Invariants
* 21 drawer destinations preserved; `DrawerState` remains single source of truth; zero DB/service impact.

### 1.6 Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-UI-162` (Drawer State & Navigation Controller Binding, ATT-657 / ATT-658).
* `Historical Origin & Commit Trace`: Introduced under `ATT-1327` / `ATT-1335` to bind Activity navigation to Compose `NavHostController`. Refined under `ATT-7526`.
* `Root Reason for Existing Formulation`: `DisposableEffect(navController)` was placed outside `Scaffold`, firing before `NavHost` inside `Scaffold` subcomposed and set the graph.
* `Preservation of Core Invariants`: Moving binding inside `Scaffold` after `NavHost` eliminates race while preserving full lifecycle contract.

---

## 2. Test Specification (TST-UI-114)
* **`TST-UI-114.8a` (Unit)**: `testNavigateToDrawerItem_uninitializedGraph_queuesPendingItemSafely` (`MainActivityWithNavigationTest.kt`). Pre: mock controller where `controller.graph` throws. Action: `navigateToDrawerItem(R.id.drawer_workouts)`. Expected: returns `true`, sets `mPendingDrawerItemId`, zero exceptions.
* **`TST-UI-114.8b` (Unit)**: `testOnNavControllerReady_withPendingItemAndValidGraph_dispatchesQueuedNavigation` (`MainActivityWithNavigationTest.kt`). Pre: `mPendingDrawerItemId = R.id.drawer_routes`, attached graph. Action: `onNavControllerReady(controller)`. Expected: navigates to routes, clears pending ID.
* **`TST-UI-114.8c` (Localization)**: Run `TranslationParityTest` (100% parity across 9 locales).
* **`TST-UI-114.8d` (Tier 1 Regression)**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.activities.*" --tests "com.atrainingtracker.trainingtracker.build.*"` (100% PASS).

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-114.8a` | Unit | `MainActivityWithNavigation.navigateToDrawerItem` | `REQ-UI-162` | Specified |
| `TST-UI-114.8b` | Unit | `MainActivityWithNavigation.onNavControllerReady` | `REQ-UI-162` | Specified |
| `TST-UI-114.8c` | Localization | `TranslationParityTest` | `REQ-UI-106` | Specified |
| `TST-UI-114.8d` | Regression | Navigation & Activity unit tests | `REQ-PRO-001` | Specified |