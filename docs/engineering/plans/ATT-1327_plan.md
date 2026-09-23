# Engineering Implementation Plan - ATT-1327: ModalNavigationDrawer Back Navigation Isolation & Single Source of Truth Synchronization

**Ticket**: [ATT-1327](https://rainerblind.atlassian.net/browse/ATT-1327)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Components**: 
* `com.atrainingtracker.trainingtracker.ui.navigation.AppNavigationDrawer.kt`
* `com.atrainingtracker.trainingtracker.ui.navigation.ATrainingTrackerApp.kt`
* `com.atrainingtracker.trainingtracker.ui.navigation.SingleActivityNavigationTest.kt`
**Requirement Mapping**: `REQ-UI-162` (`docs/requirements.md`)  
**Test Spec Mapping**: `TST-UI-114` (`docs/tests.md`)  
**Branch**: `bugfix/ATT-1327`  

---

## 1. Executive Summary & Objective

In the Jetpack Compose Single-Activity architecture (`ATT-1083` / `REQ-UI-159`), when the `ModalNavigationDrawer` is open/shown and the user clicks or gestures system Back, the application navigates back to the main tracking control (`NavRoutes.START_TRACKING`), but the navigation drawer remains visible and stuck open.

### Root Cause
1. **Split-Brain State Duplication**: `NavigationDrawerController` maintained a decoupled mutable boolean `var isDrawerOpen by mutableStateOf(false)`. Compose Material 3 maintains its own internal `DrawerState` (`currentValue` and `targetValue`). An asynchronous `LaunchedEffect(drawerController.isDrawerOpen)` attempted to bridge them imperatively.
2. **Fall-Through Defect**: During opening animations and state transitions, `drawerState.isOpen` evaluated to `false`. The monolithic `BackHandler` in `ATrainingTrackerApp.kt` fell through its `if (drawerState.isOpen)` branch to `else if (currentRoute != NavRoutes.START_TRACKING)`, dispatching `activity.navigateToDrawerItem(R.id.drawer_start_tracking)` and leaving the drawer stuck open over the tracking screen.

### Objectives
1. Eliminate state duplication in `NavigationDrawerController` by establishing Material 3's `DrawerState` as the Single Source of Truth (SSOT) via functional delegates (`bindDrawer`).
2. Implement scoped, layered `BackHandler`s with strict LIFO composition ordering:
   - **Layer 1 (Drawer Overlay)**: Intercepts Back exclusively when `isDrawerVisible` is `true`, executing safe closure.
   - **Layer 2 (Settings Bottom Sheet)**: Intercepts Back exclusively when an active sheet is displayed.
   - **Layer 3 (Screen Navigation)**: Guarded by `enabled = !isDrawerVisible && drawerController.activeBottomSheet == null`.
3. Provide deadlock safety valve (400ms timeout with `snapTo(DrawerValue.Closed)` fallback) preventing UI thread starvation deadlocks (`INV-UI-04`).
4. Implement cooperative `CancellationException` handling for rapid consecutive double-tap back presses.
5. Add automated unit test verification in `SingleActivityNavigationTest.kt` satisfying `TST-UI-114`.

---

## 2. Requirements Traceability Matrix

| Requirement Clause | Architecture / Component | Implementation Detail | Test Verification (`TST-UI-114`) |
| :--- | :--- | :--- | :--- |
| **`REQ-UI-162.1`** (Single Source of Truth) | `AppNavigationDrawer.kt`, `ATrainingTrackerApp.kt` | `NavigationDrawerController` delegates `openDrawer()`, `closeDrawer()`, and `isDrawerOpen` to `DrawerState` via `bindDrawer()`. Decoupled `isDrawerOpen` state flag removed; asynchronous `LaunchedEffect` bridge eliminated. | `SingleActivityNavigationTest.testNavigationDrawerControllerActionDelegationAndFallback` |
| **`REQ-UI-162.2`** (Scoped Overlay Back Navigation) | `ATrainingTrackerApp.kt` | Layer 1 `BackHandler(enabled = isDrawerVisible)` intercepts back presses when `drawerState.currentValue != DrawerValue.Closed || drawerState.targetValue != DrawerValue.Closed`. Smoothly closes drawer without altering destination. | `SingleActivityNavigationTest.testScopedBackHandlerVisibilityPredicatesAnd4StateMatrix` |
| **`REQ-UI-162.3`** (Settings Bottom Sheet Isolation) | `ATrainingTrackerApp.kt` | Layer 2 `BackHandler(enabled = drawerController.activeBottomSheet != null)` dismisses sheet without modifying route. | `SingleActivityNavigationTest.testScopedBackHandlerVisibilityPredicatesAnd4StateMatrix` |
| **`REQ-UI-162.4`** (Screen-Level Back Navigation Guard) | `ATrainingTrackerApp.kt` | Layer 3 `BackHandler(enabled = !isDrawerVisible && drawerController.activeBottomSheet == null)` manages destination navigation only when overlays are closed. | `SingleActivityNavigationTest.testScopedBackHandlerVisibilityPredicatesAnd4StateMatrix` |
| **`REQ-UI-162.5`** (Deterministic Interruption & Deadlock Prevention - INV-UI-04) | `ATrainingTrackerApp.kt` | `withTimeoutOrNull(400L) { drawerState.close() } ?: drawerState.snapTo(DrawerValue.Closed)` in cooperative `try-catch (_: CancellationException)` block. | `SingleActivityNavigationTest.testDeadlockSafetyTimeoutAndStarvationFallback` & `testRapidDoubleBackPressCancellationSafety` |

---

## 3. Step-by-Step Implementation Changes

### 3.1 Component 1: `AppNavigationDrawer.kt`
**File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt`

Refactor `NavigationDrawerController` to eliminate decoupled mutable state and provide functional binding to Compose's authoritative `DrawerState`:

```kotlin
class NavigationDrawerController(
    initialSelectedItemId: Int = R.id.drawer_start_tracking,
    initialStartTrackingTitleRes: Int = R.string.tracking_drawer_title
) {
    var selectedItemId: Int by mutableIntStateOf(initialSelectedItemId)
    var startTrackingTitleRes: Int by mutableIntStateOf(initialStartTrackingTitleRes)
    var activeBottomSheet: SettingsBottomSheetType? by mutableStateOf(null)

    private var _unboundIsDrawerOpen by mutableStateOf(false)
    private var openDrawerAction: (() -> Unit)? = null
    private var closeDrawerAction: (() -> Unit)? = null
    private var isDrawerOpenProvider: (() -> Boolean)? = null

    val isDrawerOpen: Boolean
        get() = isDrawerOpenProvider?.invoke() ?: _unboundIsDrawerOpen

    fun openDrawer() {
        val action = openDrawerAction
        if (action != null) action.invoke() else _unboundIsDrawerOpen = true
    }

    fun closeDrawer() {
        val action = closeDrawerAction
        if (action != null) action.invoke() else _unboundIsDrawerOpen = false
    }

    fun bindDrawer(open: () -> Unit, close: () -> Unit, isOpen: () -> Boolean) {
        openDrawerAction = open
        closeDrawerAction = close
        isDrawerOpenProvider = isOpen
    }

    fun unbindDrawer() {
        openDrawerAction = null
        closeDrawerAction = null
        isDrawerOpenProvider = null
    }
}
```

### 3.2 Component 2: `ATrainingTrackerApp.kt`
**File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt`

1. Replace the asynchronous `LaunchedEffect(drawerController.isDrawerOpen)` synchronization bridge with `DisposableEffect(drawerState, scope)` to cleanly bind and unbind the delegates:
```kotlin
    // Bind drawerController to Compose DrawerState as Single Source of Truth
    DisposableEffect(drawerState, scope) {
        drawerController.bindDrawer(
            open = { scope.launch { drawerState.open() } },
            close = { scope.launch { drawerState.close() } },
            isOpen = { drawerState.currentValue != DrawerValue.Closed || drawerState.targetValue != DrawerValue.Closed }
        )
        onDispose {
            drawerController.unbindDrawer()
        }
    }
```

2. Establish scoped, layered `BackHandler`s with LIFO composition ordering:
```kotlin
    // Authoritative drawer visibility predicate across all animation phases
    val isDrawerVisible = drawerState.currentValue != DrawerValue.Closed || drawerState.targetValue != DrawerValue.Closed

    // --- Back Handling Hierarchy (LIFO composition ordering) ---
    // Layer 3: Root Screen Navigation (composed first -> evaluated last)
    BackHandler(enabled = !isDrawerVisible && drawerController.activeBottomSheet == null) {
        if (drawerController.activeFilterCriteria != null) {
            drawerController.activeFilterCriteria = null
        } else if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        } else if (currentRoute != NavRoutes.START_TRACKING) {
            activity.navigateToDrawerItem(R.id.drawer_start_tracking)
        } else {
            activity.finish()
        }
    }

    // Layer 2: Settings Bottom Sheet Overlay (composed second)
    BackHandler(enabled = drawerController.activeBottomSheet != null) {
        drawerController.activeBottomSheet = null
    }

    // Layer 1: Navigation Drawer Overlay (composed last -> evaluated first)
    BackHandler(enabled = isDrawerVisible) {
        scope.launch {
            try {
                withTimeoutOrNull(400L) {
                    drawerState.close()
                } ?: drawerState.snapTo(DrawerValue.Closed)
            } catch (_: CancellationException) {
                // Cooperative cancellation on rapid consecutive back taps
            }
        }
    }
```

### 3.3 Component 3: `SingleActivityNavigationTest.kt`
**File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/SingleActivityNavigationTest.kt`

Add comprehensive unit test coverage satisfying `TST-UI-114`:
1. `testNavigationDrawerControllerActionDelegationAndFallback()`:
   - Validates unbound fallback values and proper binding/unbinding lifecycle.
2. `testScopedBackHandlerVisibilityPredicatesAnd4StateMatrix()`:
   - Validates the 4-state truth table:
     - `State 1` (Closed, Closed): `isDrawerVisible == false`, Layer 1 disabled, Layer 3 enabled.
     - `State 2` (Closed, Open): `isDrawerVisible == true`, Layer 1 enabled, Layer 3 disabled.
     - `State 3` (Open, Open): `isDrawerVisible == true`, Layer 1 enabled, Layer 3 disabled.
     - `State 4` (Open, Closed): `isDrawerVisible == true`, Layer 1 enabled, Layer 3 disabled.
3. `testRapidDoubleBackPressCancellationSafety()`:
   - Verifies cooperative `CancellationException` handling and that mid-flight transitions absorb subsequent back presses without routing changes.
4. `testDeadlockSafetyTimeoutAndStarvationFallback()`:
   - Verifies 400ms timeout fallback to `snapTo(DrawerValue.Closed)` under simulated starvation, immediately clearing `isDrawerVisible` and restoring Layer 3 navigation (`INV-UI-04`).

---

## 4. Preserved Invariants & Boundary Safety

1. **System Invariants (INV-UI-01)**: All 21 navigation items in `AppNavigationDrawer.kt` remain unchanged.
2. **Edge-Swipe Gestures (INV-UI-02 / REQ-UI-161)**: Leftmost 40dp margin swipe bounds and `gesturesEnabled = drawerState.isOpen` are completely preserved.
3. **Drawer Item Selection (INV-UI-03)**: `drawerController.selectedItemId` continues to synchronize with active routes.
4. **Deadlock Prevention (INV-UI-04)**: Bounded by 400ms timeout with `snapTo(DrawerValue.Closed)` fallback inside coroutine scope.
5. **No Unhandled Coroutine Exceptions (INV-UI-05)**: Cooperative `CancellationException` handling on rapid consecutive taps.
6. **Zero External Breakage**: Backward compatibility preserved for any external consumers reading `drawerController.isDrawerOpen`.

---

## 5. Verification Plan

1. **Automated Unit Tests**:
   - Run `SingleActivityNavigationTest.kt` to verify all 4 new test methods and existing navigation suites.
   - Run `./gradlew testDebugUnitTest` to verify clean-room 100% pass across all 626+ repository unit tests.
2. **Programmatic Stage Gate Verification**:
   - Execute `python3 tools/jira_util.py check-gate ATT-1332` before starting Stage 4 code modifications.
3. **Physical Device Verification (Pixel 10 / Android 15)**:
   - Open drawer on Workouts -> press Back -> verify drawer closes and active screen stays on Workouts.
   - Press Back again with drawer closed -> verify navigation to main tracking screen.
   - Press Back on main tracking screen -> verify app exits.
   - Double-tap Back rapidly during drawer open animation -> verify clean dismissal without routing glitch.
