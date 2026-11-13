# Stage 3: Implementation Plan - ATT-7526: Fix IllegalStateException in MainActivityWithNavigation.navigateToDrawerItem before NavHost sets graph

**Ticket**: [ATT-7526](https://rainerblind.atlassian.net/browse/ATT-7526)  
**Sub-task**: [ATT-7553](https://rainerblind.atlassian.net/browse/ATT-7553) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No Crashes & App Stability (Crash-Free Resilience)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.5`  
**Requirement Mapping**: `REQ-UI-162` (*Single Source of Truth & Scoped Overlay Back Navigation*)  
**Test Mapping**: `TST-UI-114` (*ModalNavigationDrawer Back Navigation Isolation & State Synchronization Test*)  
**Branch**: `feature/ATT-7526`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background
In production `v4.9.41 (272)`, when `MainActivityWithNavigation` queues a pending drawer navigation (`mPendingDrawerItemId = itemId`) prior to Compose initialization (e.g. cold start, state restoration, early intents), `DisposableEffect(navController)` located outside `Scaffold` invokes `activity.onNavControllerReady(navController)` before `NavHost` inside `Scaffold`'s content subcomposes and sets the navigation graph. Calling `controller.graph.findStartDestination()` triggers `IllegalStateException: You must call setGraph() before calling getGraph()`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-162` (Drawer State & Navigation Controller Lifecycle Synchronization).
* **Test Cases**:
  - `TST-UI-114.8a`: Unit test uninitialized/throwing `controller.graph` defensive queuing in `navigateToDrawerItem`.
  - `TST-UI-114.8b`: Unit test deferred drawer item execution & clearing on `onNavControllerReady`.
  - `TST-UI-114.8c`: `TranslationParityTest` (100% parity across 9 locales).
  - `TST-UI-114.8d`: Tier 1 regression suite (`com.atrainingtracker.trainingtracker.ui.navigation.*` and `com.atrainingtracker.trainingtracker.activities.*`).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Unintended Regressions**: All 21 navigation routes, drawer state transitions, and backstack behaviors remain identical.
2. **Defensive Non-Crashing Guarantee**: Calling any navigation method before graph initialization must never throw `IllegalStateException`; pending actions buffer safely.
3. **Subtask & Gate Invariance**: Code Lock strictly enforced until Gate 3 PASS; Parent ticket Human Decision Gate remains strictly in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `ATrainingTrackerApp.kt` (UI Navigation Layer)
* Move `DisposableEffect(navController)` and initial route `LaunchedEffect(Unit)` inside `Scaffold`'s content lambda directly after `NavHost(...)`.
* Ensures `NavHost`'s graph construction and `navController.setGraph(...)` complete before `onNavControllerReady(navController)` or initial route routing executes.

### Component 2: `MainActivityWithNavigation.kt` (Activity Layer)
* Add `private fun isNavGraphReady(controller: NavHostController?): Boolean = controller != null && runCatching { controller.graph }.isSuccess`.
* In `navigateToDrawerItem(itemId)`: check `if (!isNavGraphReady(controller)) { mPendingDrawerItemId = itemId } else { ... }`.
* In `navigateToFilteredWorkouts(...)` and `startWorkoutSummaryListFromPeriod(...)`: check `isNavGraphReady(controller)`; if false, fall back safely to `navigateToDrawerItem(R.id.drawer_workouts)`.

### UI Consistency (Rule 23)
* No UI components, styles, or layouts added or changed. Purely lifecycle ordering and defensive guard logic.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement Defensive Graph Readiness in `MainActivityWithNavigation.kt`
* Add `isNavGraphReady(controller: NavHostController?): Boolean`.
* Guard `navigateToDrawerItem`, `navigateToFilteredWorkouts`, and `startWorkoutSummaryListFromPeriod`.

### Step 2: Relocate NavController Binding in `ATrainingTrackerApp.kt`
* Move `DisposableEffect(navController)` and `LaunchedEffect(Unit)` inside `Scaffold` content lambda after `NavHost(...)`.

### Step 3: Implement Unit & Contract Tests
* Add test cases to `MainActivityWithNavigationInteropTest.kt` verifying uninitialized graph handling, pending item buffering, and deferred dispatch on ready graph.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.MainActivityWithNavigationInteropTest"`

### Step 4: Execute Tier 1 Verification Suite
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.activities.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* Run `python3 tools/verify_requirement_governance.py` to assert living document parity.

---

## 6. Verification & Rollback Plan
* **Verification**: Incremental unit test runs in Stage 4, followed by clean-room compilation and test suite in Stage 5.
* **Rollback**: Atomic commit on branch `feature/ATT-7526`; easy revert via `git revert` or branch reset if regressions arise.
