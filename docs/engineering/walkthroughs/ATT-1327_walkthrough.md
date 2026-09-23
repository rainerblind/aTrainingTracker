# Engineering Walkthrough - ATT-1327: ModalNavigationDrawer Back Navigation Isolation & Single Source of Truth Synchronization

**Ticket**: [ATT-1327](https://rainerblind.atlassian.net/browse/ATT-1327)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Components**: 
* `com.atrainingtracker.trainingtracker.ui.navigation.AppNavigationDrawer.kt`
* `com.atrainingtracker.trainingtracker.ui.navigation.ATrainingTrackerApp.kt`
* `com.atrainingtracker.trainingtracker.ui.navigation.SingleActivityNavigationTest.kt`
**Requirement**: `REQ-UI-162`  
**Test Spec**: `TST-UI-114`  
**Branch**: `bugfix/ATT-1327`  

---

## 1. Changes Overview

This change resolves the defect where pressing system Back when the `ModalNavigationDrawer` was open or opening caused the application to navigate to the main tracking control (`NavRoutes.START_TRACKING`), but left the navigation drawer stuck open.

### Key Modifications:

1. **`AppNavigationDrawer.kt` (`NavigationDrawerController`)**:
   - Refactored `NavigationDrawerController` to eliminate decoupled mutable state (`var isDrawerOpen by mutableStateOf(false)`).
   - Provided functional delegates (`bindDrawer(open, close, isOpen)`, `unbindDrawer()`) binding directly to Material 3's authoritative `DrawerState` as the Single Source of Truth (SSOT).
   - Maintained backward-compatible fallback (`_unboundIsDrawerOpen`) ensuring test isolation and safe execution when unbound.

2. **`ATrainingTrackerApp.kt`**:
   - Replaced asynchronous `LaunchedEffect(drawerController.isDrawerOpen)` synchronization with `DisposableEffect(drawerState, scope)` to cleanly bind and unbind drawer delegates.
   - Derived visibility predicate across all animation phases:
     ```kotlin
     val isDrawerVisible = drawerState.currentValue != DrawerValue.Closed || drawerState.targetValue != DrawerValue.Closed
     ```
   - Replaced monolithic `BackHandler` with a 3-layer scoped hierarchy obeying strict LIFO composition order:
     - **Layer 3 (Screen Navigation)**: Composed first -> evaluated last in LIFO queue. Guarded by `enabled = !isDrawerVisible && drawerController.activeBottomSheet == null`. Pops backstack, clears filter criteria, routes to `START_TRACKING`, or exits app.
     - **Layer 2 (Settings Bottom Sheet)**: Composed second. Guarded by `enabled = drawerController.activeBottomSheet != null`. Dismisses active sheet.
     - **Layer 1 (Navigation Drawer Overlay)**: Composed last -> evaluated first in LIFO queue. Guarded by `enabled = isDrawerVisible`. Closes drawer smoothly without altering screen destination.
   - Bounded drawer closure by a 400ms timeout with `snapTo(DrawerValue.Closed)` fallback inside coroutine scope, guaranteeing UI deadlock immunity under thread starvation (`INV-UI-04`).
   - Implemented cooperative `CancellationException` handling for rapid consecutive back taps.

3. **`SingleActivityNavigationTest.kt`**:
   - Added `testNavigationDrawerControllerActionDelegationAndFallback()`: Validates SSOT delegation, unbound fallback without NPE, and unbind lifecycle.
   - Added `testScopedBackHandlerVisibilityPredicatesAnd4StateMatrix()`: Validates all 4 states of the `DrawerValue` matrix (`Closed, Closed`, `Closed, Open`, `Open, Open`, `Open, Closed`), confirming Layer 1 intercepts back and Layer 3 remains disabled until drawer settles.
   - Added `testRapidDoubleBackPressCancellationSafety()`: Verifies cooperative coroutine cancellation handling during mid-flight transitions.
   - Added `testDeadlockSafetyTimeoutAndStarvationFallback()`: Validates 400ms timeout triggering `snapTo(DrawerValue.Closed)` under simulated starvation to unblock Layer 3 screen navigation.

---

## 2. Verification Results

### Automated Unit Tests (`com.atrainingtracker.trainingtracker.ui.navigation.*`):
* `SingleActivityNavigationTest`: 12 passed, 0 failed, 0 skipped.
* **Result**: **PASS** (12/12 tests green).

### Clean-Room Full Suite Regression:
* `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL** (0 failures, 626+ tests passing across all modules).

---

## 3. Preserved Invariants & Boundary Safety

1. **System Invariants (INV-UI-01)**: All 21 navigation items in `AppNavigationDrawer.kt` remain unchanged.
2. **Edge-Swipe Gestures (INV-UI-02 / REQ-UI-161)**: Leftmost 40dp margin swipe bounds and `gesturesEnabled = drawerState.isOpen` are completely preserved.
3. **Drawer Item Selection (INV-UI-03)**: `drawerController.selectedItemId` continues to synchronize with active routes.
4. **Deadlock Prevention (INV-UI-04)**: Bounded by 400ms timeout with `snapTo(DrawerValue.Closed)` fallback inside coroutine scope.
5. **No Unhandled Coroutine Exceptions (INV-UI-05)**: Cooperative `CancellationException` handling on rapid consecutive taps.
