# Stage 1 Analysis: ATT-7526 - Fix IllegalStateException in MainActivityWithNavigation.navigateToDrawerItem before NavHost sets graph

**Ticket**: [ATT-7526](https://rainerblind.atlassian.net/browse/ATT-7526)  
**Sub-task**: [ATT-7549](https://rainerblind.atlassian.net/browse/ATT-7549) (`[Analysis]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No Crashes & App Stability (Crash-Free Resilience)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.5`  
**Branch**: `feature/ATT-7526`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
In production `v4.9.41 (272)`, when `MainActivityWithNavigation` queues a pending drawer navigation (`mPendingDrawerItemId = itemId`) prior to Compose initialization (e.g. during cold startup, saved state restoration, or early intent handling), the application crashes during initial composition (`Recomposer.composeInitial`) with `java.lang.IllegalStateException: You must call setGraph() before calling getGraph()` at `NavController.getGraph(NavController.kt:103)` via `navigateToDrawerItem` -> `onNavControllerReady` -> `DisposableEffect`. The app must ensure that navigation callbacks and route restoration only execute after `NavHost` has attached the navigation graph, and defensively protect `MainActivityWithNavigation` navigation helpers from uninitialized `navController.graph` states.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Order of Composition**: In `ATrainingTrackerApp.kt` (lines 205–210), `DisposableEffect(navController) { activity.onNavControllerReady(navController) ... }` is positioned in the outer composition body outside `Scaffold`.
2. **SubcomposeLayout Timing**: `NavHost` is positioned within the content lambda of `Scaffold` (`ATrainingTrackerApp.kt:348–355`). Material 3 `Scaffold` uses `SubcomposeLayout`, so its content lambda (`NavHost`, where `navController.setGraph(...)` is invoked) is subcomposed during layout/measure after the outer `DisposableEffect`'s `onRemembered()` has executed.
3. **Premature Graph Access**: When `onNavControllerReady` executes queued `navigateToDrawerItem(pendingId)`, `controller.navigate(...)` executes `popUpTo(controller.graph.findStartDestination().id)`, invoking `NavController.getGraph()` before `_graph` is attached and crashing. Outer `LaunchedEffect(Unit)` (lines 238–243) similarly risks premature graph access if `selectedItemId != R.id.drawer_start_tracking`.
4. **Missing Defensive Graph Guards in Activity**: In `MainActivityWithNavigation.kt` (`navigateToDrawerItem`, `navigateToFilteredWorkouts`, `startWorkoutSummaryListFromPeriod`), the check is only `if (controller == null)`. When `navController` is assigned but its graph is not yet attached, any navigation attempt crashes.
5. **Independent Peer Review & Verification**:
   - Confirmed the production crash stacktrace (`6AF5F5B601AD000110EECACFEEF2F038`) matches lines 205–210 in `ATrainingTrackerApp.kt` and line 813 in `MainActivityWithNavigation.kt`.
   - Verified that moving `DisposableEffect(navController)` inside the `Scaffold` content lambda *after* `NavHost(...)` guarantees `NavHost` has called `setGraph()` before `onNavControllerReady` executes.
   - Verified that introducing `isNavGraphReady(controller: NavHostController?): Boolean` (checking `runCatching { controller.graph }.isSuccess`) in `MainActivityWithNavigation.kt` allows `navigateToDrawerItem` to safely queue `mPendingDrawerItemId = itemId` rather than throwing when the graph is uninitialized.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Relocate `DisposableEffect(navController)` and initial route `LaunchedEffect(Unit)` inside `Scaffold` content lambda after `NavHost` in `ATrainingTrackerApp.kt`.
  - Introduce defensive `isNavGraphReady(controller: NavHostController?): Boolean` helper in `MainActivityWithNavigation.kt`.
  - Update `navigateToDrawerItem(itemId)` to queue `mPendingDrawerItemId = itemId` when `!isNavGraphReady(controller)`.
  - Guard `navigateToFilteredWorkouts` and `startWorkoutSummaryListFromPeriod` with `isNavGraphReady(controller)`.
  - Add targeted unit tests verifying graph-readiness defense and deferred delivery of pending drawer destinations.
* **Out-of-Scope Non-Goals**:
  - Modifying drawer menu destinations or route patterns in `NavRoutes.kt`.
  - Refactoring settings bottom sheet interactions or permissions onboarding flow.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-162` (Drawer State & Navigation Controller Binding, ATT-657 / ATT-658).
* **Historical Origin & Commit Trace**: Introduced during Single-Activity architecture refactoring to synchronize classic Activity navigation callbacks with Compose `NavHostController`.
* **Root Reason for Existing Formulation**: `DisposableEffect(navController)` was placed at the top of `ATrainingTrackerApp` to ensure immediate binding on composition entry and clean disposal on exit.
* **Preservation of Core Invariants**: Moving the binding inside the `Scaffold` content lambda after `NavHost` preserves the exact lifecycle contract (bound on entry, torn down on exit) while respecting Compose layout order and eliminating race conditions.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-UI-162` (Drawer State & Navigation Controller Lifecycle Synchronization).
* **Affected Architecture Elements**: `ATrainingTrackerApp.kt`, `MainActivityWithNavigation.kt`.
* **Verification Impact & Test Surface**: `MainActivityWithNavigationInteropTest.kt` (new uninitialized graph contract tests), `ATrainingTrackerAppNavigationTest.kt`.
* **Risk Assessment & Invariants**: Low risk. All 21 navigation routes, drawer state transitions, and deep links remain identical.

---

## 6. Architectural Strategy & High-Level Solution
1. **Compose Order Alignment**: In `ATrainingTrackerApp.kt`, move `DisposableEffect(navController)` and `LaunchedEffect(Unit)` inside `Scaffold` content block, directly after `NavHost(...)`.
2. **Defensive Graph Check**: In `MainActivityWithNavigation.kt`, implement:
   ```kotlin
   private fun isNavGraphReady(controller: NavHostController?): Boolean =
       controller != null && runCatching { controller.graph }.isSuccess
   ```
3. **Queue on Uninitialized Graph**: If `!isNavGraphReady(controller)`, `navigateToDrawerItem(itemId)` stores `mPendingDrawerItemId = itemId`. `navigateToFilteredWorkouts` and `startWorkoutSummaryListFromPeriod` safely fall back to `navigateToDrawerItem(R.id.drawer_workouts)`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. No unhandled `IllegalStateException` on cold start or pending drawer navigation.
  2. Queued drawer items are guaranteed to navigate once `NavHost` attaches the graph.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**. Purely defensive lifecycle and null-safety fixes without database or background service impacts.
