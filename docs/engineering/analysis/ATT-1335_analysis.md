# Engineering Analysis - ATT-1335: ModalNavigationDrawer Back Press Preempted by NavHost Internal Back Handler (Regression of ATT-1327)

**Ticket**: [ATT-1335](https://rainerblind.atlassian.net/browse/ATT-1335)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Component**: `com.atrainingtracker.trainingtracker.ui.navigation.ATrainingTrackerApp.kt`  
**Parent Requirement**: `REQ-UI-159` (Single-Activity Architecture & Jetpack Compose Navigation Modernization)  
**Committed Target Requirement**: `REQ-UI-162` (Single Source of Truth & Scoped Overlay Back Navigation) in `docs/requirements.md`  
**Committed Test Spec**: `TST-UI-114` (ModalNavigationDrawer Back Navigation Isolation & State Synchronization Test) in `docs/tests.md`  

---

## 1. Executive Summary & Problem Statement

Following the initial integration of `ATT-1327`, testing on a physical device revealed that when the `ModalNavigationDrawer` is open/shown and the user invokes system Back navigation (hardware back button or Android back gesture) while on any navigated destination screen (such as `Workouts`, `Routes`, `Periods`, `Sensors`, etc.), the misbehaviour persists:

The application transitions back to the main tracking screen (`NavRoutes.START_TRACKING`), but the navigation drawer remains visible and stuck open over the tracking screen.

### Expected Behavior (Invariants):
1. **Overlay Isolation Invariant**: When the `ModalNavigationDrawer` is visible (open, opening, or closing), pressing Back MUST exclusively dismiss/close the navigation drawer. The underlying active Composable destination screen and backstack MUST remain completely unchanged.
2. **Screen Navigation Invariant**: Only when the navigation drawer is fully closed (`DrawerValue.Closed` and no animations running) and no modal bottom sheets are active should Back navigate destinations (e.g. pop backstack to `NavRoutes.START_TRACKING` or finish the activity).

---

## 2. Requirement Traceability & Impacted Artifacts

* **Parent Requirement**: `REQ-UI-159` (Single-Activity Architecture & Jetpack Compose Navigation Modernization)
* **Committed Target Requirement**: `REQ-UI-162` (Single Source of Truth & Scoped Overlay Back Navigation)
* **Committed Verification Specification**: `TST-UI-114` (ModalNavigationDrawer Back Navigation Isolation & State Synchronization Test)
* **Impacted Production Files**:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt`
* **Impacted Verification Files**:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/SingleActivityNavigationTest.kt`

---

## 3. Forensic Root Cause Analysis (RCA)

### 3.1 The Chain of Responsibility & Composition Tree Precedence Flaw
In Android Jetpack Compose, the `BackHandler` composable registers callbacks with the activity's `OnBackPressedDispatcher`. Android's `OnBackPressedDispatcher` evaluates registered callbacks in **LIFO order** (`descendingIterator()`), meaning the callback registered **most recently** is evaluated **first**.

In `ATrainingTrackerApp.kt`:
```kotlin
// Layer 3: Root Screen Navigation (composed first)
BackHandler(enabled = !isDrawerVisible && drawerController.activeBottomSheet == null) { ... }

// Layer 2: Settings Bottom Sheet Overlay (composed second)
BackHandler(enabled = drawerController.activeBottomSheet != null) { ... }

// Layer 1: Navigation Drawer Overlay (composed third)
BackHandler(enabled = isDrawerVisible) {
    scope.launch {
        try {
            withTimeoutOrNull(400L) {
                drawerState.close()
            } ?: drawerState.snapTo(DrawerValue.Closed)
        } catch (_: CancellationException) {}
    }
}

ModalNavigationDrawer(...) {
    Box(...) {
        Scaffold(...) {
            NavHost(
                navController = navController,
                startDestination = NavRoutes.START_TRACKING,
                ...
            ) { ... }
        }
    }
}
```

Notice the structural placement:
1. `BackHandler(enabled = isDrawerVisible)` is composed at the root of `ATrainingTrackerApp`. Its `DisposableEffect` executes and registers its `OnBackPressedCallback` with the activity dispatcher.
2. Inside `ModalNavigationDrawer`, `NavHost` (`androidx.navigation:navigation-compose:2.8.8`) is composed.
3. Internally, `NavHost` invokes `PredictiveBackHandler` (or `BackHandler`), which registers its own callback with `OnBackPressedDispatcher`.
4. Furthermore, any child screen navigated to (e.g. `WorkoutSummariesTabbedScreen`) may register its own `BackHandler`.
5. Because `NavHost` and child screens are composed **inside** `ModalNavigationDrawer`'s content, their callbacks are registered **after** the root `BackHandler(enabled = isDrawerVisible)`.

### 3.2 The Flaw of Static Callback Registration
When `BackHandler(enabled = isDrawerVisible)` is declared unconditionally at the root, its callback is attached once during initial composition. Setting `enabled = isDrawerVisible` only mutates `backCallback.isEnabled` via `SideEffect`. It does **not** move the callback to the top of the dispatcher stack.

Consequently, in `OnBackPressedDispatcher.mOnBackPressedCallbacks`:
* Callback #1: Root Screen Navigation
* Callback #2: Bottom Sheet
* Callback #3: Navigation Drawer (`enabled = isDrawerVisible`)
* Callback #4: `NavHost` (`enabled = navController.previousBackStackEntry != null`)
* Callback #5: Child screen back handlers (e.g. `WorkoutSummariesTabbedScreen`)

When the user navigates from `START_TRACKING` to `WORKOUTS`, `navController.previousBackStackEntry` is `START_TRACKING` (non-null). Therefore, Callback #4 (`NavHost`) is `enabled = true`.
When the user opens the navigation drawer over `WORKOUTS`:
* `isDrawerVisible` becomes `true`, enabling Callback #3.
* The user presses Back.
* `OnBackPressedDispatcher.onBackPressed()` runs in descending order:
  * Callback #5: Disabled (unless details open).
  * Callback #4 (`NavHost`): **ENABLED**!
* `NavHost` executes its back handler, popping `WORKOUTS` back to `START_TRACKING`.
* Callback #3 (Navigation Drawer) is **never reached**!
* Result: The destination changes to `START_TRACKING`, while the drawer remains open.

---

## 4. Architectural Resolution

### 4.1 Conditional Composition for Dynamic LIFO Precedence
To guarantee that the Navigation Drawer's back handler sits at the **very top** of `OnBackPressedDispatcher` whenever the drawer is visible, the `BackHandler` must be **conditionally composed**:

```kotlin
if (isDrawerVisible) {
    BackHandler {
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
}
```

#### Lifecycle & Dispatch Mechanics:
1. **Drawer Closed**: `isDrawerVisible == false`. The `BackHandler` is not in composition. No callback is attached.
2. **Drawer Opened**: `isDrawerVisible` becomes `true`. Recomposition enters `if (isDrawerVisible)`. The `BackHandler`'s `DisposableEffect` executes and calls `addCallback(...)`.
   * Crucially, `addCallback` appends this callback to the **end** of `mOnBackPressedCallbacks`.
   * It becomes the **most recently added callback in the entire application**, higher than `NavHost` and all destination screens.
3. **Back Pressed**: `OnBackPressedDispatcher` evaluates in descending order. The dynamically mounted drawer `BackHandler` is checked first. It launches `drawerState.close()` and consumes the event. `NavHost` is never triggered.
4. **Drawer Settled**: `isDrawerVisible` becomes `false`. The `if (isDrawerVisible)` block leaves composition. `onDispose` executes and removes the callback from `OnBackPressedDispatcher`.
5. **Back Pressed Again**: With the drawer callback cleanly removed, normal screen and `NavHost` navigation is fully restored.

### 4.2 Comprehensive Visibility Predicate
To ensure the back handler remains mounted throughout all phases of opening, open, closing, and settling:
```kotlin
val isDrawerVisible = drawerState.currentValue != DrawerValue.Closed ||
    drawerState.targetValue != DrawerValue.Closed ||
    drawerState.isAnimationRunning
```

### 4.3 Settings Bottom Sheet Conditional Composition
Applying the identical pattern to Layer 2:
```kotlin
if (drawerController.activeBottomSheet != null) {
    BackHandler {
        drawerController.activeBottomSheet = null
    }
}
```

### 4.4 Drawer Content Overlay Scoping
Placing the conditional `BackHandler` inside `drawerContent` of `ModalNavigationDrawer` reinforces the overlay architectural hierarchy, ensuring the back handler is bound directly to the drawer overlay composable tree.

---

## 5. Risk Assessment & Side Effects Audit

1. **Backstack Integrity**: No alterations to `NavRoutes` or `navController.navigate(...)` logic. When the drawer is closed, `NavHost` behaves exactly as expected.
2. **Double-Back & Cancellation**: Rapid consecutive back presses during closing animations are protected by `CancellationException` handling and 400ms deadlock timeout fallback.
3. **Memory & Lifecycle**: `BackHandler` removes its callback cleanly upon exiting composition via `DisposableEffect`, preventing memory leaks or lingering callbacks.
