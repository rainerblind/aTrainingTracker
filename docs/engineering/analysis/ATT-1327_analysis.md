# Engineering Analysis - ATT-1327: NavigationDrawer Back Navigation Defect (Navigation to Start Tracking Instead of Drawer Dismissal)

**Ticket**: [ATT-1327](https://rainerblind.atlassian.net/browse/ATT-1327)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Component**: `com.atrainingtracker.trainingtracker.ui.navigation.ATrainingTrackerApp.kt`, `AppNavigationDrawer.kt`  
**Parent Requirement**: `REQ-UI-159` (Single-Activity Architecture & Jetpack Compose Navigation Modernization)  
**Committed Target Requirement**: `REQ-UI-162` (Single Source of Truth & Scoped Overlay Back Navigation) in `docs/requirements.md`  
**Committed Test Spec**: `TST-UI-114` (ModalNavigationDrawer Back Navigation Isolation & State Synchronization Test) in `docs/tests.md`  

---

## 1. Executive Summary & Problem Statement

In the Jetpack Compose Single-Activity architecture (`ATT-1083` / `REQ-UI-159`), when the `ModalNavigationDrawer` is open/shown and the user invokes system back navigation (hardware back button or Android back gesture), an anomalous navigation transition occurs:
The app switches destinations back to the main tracking control (`NavRoutes.START_TRACKING`), but the navigation drawer remains visible or stuck open on top of the screen.

### Expected Behavior (Invariants):
1. When the `ModalNavigationDrawer` is open or visibly presented, pressing Back MUST exclusively dismiss/close the navigation drawer. The underlying active destination / fragment / screen content MUST NOT change.
2. Only when the navigation drawer is completely closed / not visible and Back is clicked, should back navigation proceed down the hierarchy (e.g. pop destination backstack, clear filters, navigate to the main tracking control screen, or finish the activity).

---

## 2. Requirement Traceability & ASPICE Mapping

* **Parent Requirement (`REQ-UI-159`)**: Mandated the modernization of `MainActivityWithNavigation` into a single-activity architecture hosting `ModalNavigationDrawer`, `NavHost`, and centralized back handling.
* **Defect Identified**: 
  1. *State Duplication Anti-Pattern*: `NavigationDrawerController.isDrawerOpen` and Compose Material 3's `DrawerState` maintained separate, loosely coupled boolean states synchronized via asynchronous `LaunchedEffect` coroutines.
  2. *Monolithic BackHandler Fall-Through*: In `ATrainingTrackerApp.kt`, an unconditionally enabled `BackHandler` evaluated drawer dismissal strictly on `drawerState.isOpen` (`currentValue == DrawerValue.Open`). During opening transitions, gesture drags, or state desynchronization, this check evaluated to `false`, causing the state machine to fall through to `currentRoute != NavRoutes.START_TRACKING` and execute `activity.navigateToDrawerItem(R.id.drawer_start_tracking)`.
* **Committed Target Requirement (`REQ-UI-162`)**:
  Formally integrated into `docs/requirements.md`:
  > The system SHALL establish Material 3's `DrawerState` as the single authoritative source of truth for navigation drawer visibility and navigation control, eliminating decoupled boolean state flags, and SHALL isolate overlay back navigation (Navigation Drawer and Settings Bottom Sheets) from screen-level destination back navigation using scoped, conditionally enabled `BackHandler`s.
* **Committed Verification Specification (`TST-UI-114`)**:
  Formally integrated into `docs/tests.md`:
  Automated unit test coverage in `SingleActivityNavigationTest.kt` verifying drawer back-handling predicate states, single-source-of-truth delegation, backward compatibility for standalone unit tests, mid-gesture interruption handling, and clean-room full suite regression (626+ tests).

---

## 3. Forensic Root Cause Analysis (RCA)

### 3.1 Root Architectural Defect: State Duplication & Latency Gap
In `AppNavigationDrawer.kt:104-120`:
```kotlin
class NavigationDrawerController(...) {
    var isDrawerOpen: Boolean by mutableStateOf(false)
    fun openDrawer() { isDrawerOpen = true }
    fun closeDrawer() { isDrawerOpen = false }
}
```
And in `ATrainingTrackerApp.kt:124-135`:
```kotlin
// Synchronize drawerController.isDrawerOpen with Compose DrawerState
LaunchedEffect(drawerController.isDrawerOpen) {
    if (drawerController.isDrawerOpen) {
        if (!drawerState.isOpen) drawerState.open()
    } else {
        if (drawerState.isOpen) drawerState.close()
    }
}
LaunchedEffect(drawerState.isOpen) {
    drawerController.isDrawerOpen = drawerState.isOpen
}
```
This bidirectional synchronization architecture has severe flaws:
1. **Latency & Split-Brain State**: Setting `drawerController.isDrawerOpen = true` does not immediately make `drawerState.isOpen == true`. Material 3's `DrawerState.open()` is a suspending animation. Throughout the opening animation (and during any edge drag), `drawerState.currentValue` remains `DrawerValue.Closed`, so `drawerState.isOpen` evaluates to **`false`**.
2. **Animation Cancellation Failure**: When `navigateToDrawerItem` runs, it calls `mDrawerController.closeDrawer()`, setting `isDrawerOpen = false`. The `LaunchedEffect` runs: `if (drawerState.isOpen) drawerState.close()`. Because `drawerState.isOpen` was still evaluating to `false`, **`drawerState.close()` is never called**, leaving the drawer frozen in its open state over the new screen.

### 3.2 Monolithic `BackHandler` Fall-Through in `ATrainingTrackerApp.kt`
Lines 154-171 of `ATrainingTrackerApp.kt`:
```kotlin
BackHandler {
    when {
        drawerState.isOpen -> scope.launch { drawerState.close() }
        drawerController.activeBottomSheet != null -> drawerController.activeBottomSheet = null
        navController.previousBackStackEntry != null -> navController.popBackStack()
        currentRoute != NavRoutes.START_TRACKING -> {
            ...
            activity.navigateToDrawerItem(R.id.drawer_start_tracking)
        }
        else -> activity.finish()
    }
}
```
Because `drawerState.isOpen` is strictly `currentValue == DrawerValue.Open`:
- If back is pressed while the drawer is open or opening, `drawerState.isOpen` is `false`.
- Branch 1 does not match.
- Branch 2 does not match (`activeBottomSheet == null`).
- Branch 3 does not match (`previousBackStackEntry == null` for drawer items).
- Branch 4 matches: `currentRoute != NavRoutes.START_TRACKING`.
- It executes `activity.navigateToDrawerItem(R.id.drawer_start_tracking)`, swapping the destination to the tracking screen while the drawer stays visible.

---

## 4. Comprehensive Call-Site & Child-Composable BackHandler Audit

An exhaustive audit of all `BackHandler` call-sites across the codebase was conducted:
1. `ATrainingTrackerApp.kt:154`: Monolithic root navigation back-handler.
2. `MapScreenWithTrack.kt:151`: `BackHandler(enabled = selectedSegmentId != null || selectedRouteId != null)` - conditionally enabled only when a map feature is selected.
3. `WorkoutSummariesTabbedScreen.kt:207`: `BackHandler { selectedWorkoutIdForDetails = null }` - inside `if (selectedWorkoutIdForDetails != null)`.
4. `TrackingTabsScreen.kt:142`: `BackHandler(enabled = screenMode == ScreenMode.CONFIGURATION)` - conditionally enabled only in layout config mode.
5. `PeriodMapScreen.kt:571`: `BackHandler { if (peeked != null) clearPeek() else onBack() }` - active on period detail view.
6. `RoutesScreen.kt:144, 162`: Conditionally enabled when route details or route edit is active.
7. `StarredSegmentsScreen.kt:106`: Conditionally enabled when segment details are active.
8. `WorkoutClustersScreen.kt:116, 129, 147, 201, 256`: Conditionally enabled during cluster adding, filtering, and detail inspection.
9. `ClusterTuningScreen.kt:52`: `BackHandler(enabled = !isRecalculating)`.
10. `WorkoutClusterHeatmapScreen.kt:582`: Heatmap filter back handling.
11. `LapEditBottomSheet.kt:193`: Sheet dismissal back handler.

---

## 5. Composition Tree Ordering & LIFO Execution Safety

### 5.1 Android `OnBackPressedDispatcher` LIFO Priority
Android's `OnBackPressedDispatcher` maintains an `ArrayDeque` of registered callbacks. When back is pressed, the dispatcher iterates through the deque in reverse order (LIFO: newest to oldest) and invokes the **first callback whose `isEnabled` flag is `true`**.

In Jetpack Compose, `BackHandler` registers its `OnBackPressedCallback` during composition.
To guarantee mathematical LIFO execution safety between the overlay layers and root navigation in `ATrainingTrackerApp.kt`:
1. **Layer 3 (Screen Navigation)** is composed FIRST in the tree, but guarded by `enabled = !isDrawerVisible && drawerController.activeBottomSheet == null`.
2. **Layer 2 (Settings Bottom Sheet)** is composed SECOND in the tree, guarded by `enabled = !isDrawerVisible && drawerController.activeBottomSheet != null`.
3. **Layer 1 (Navigation Drawer)** is composed THIRD (newest) in the tree (or directly inside `ModalDrawerSheet`), guarded by `enabled = isDrawerVisible`.

```
Composition Order:
├── Layer 3: Screen Navigation BackHandler (enabled = !isDrawerVisible && activeBottomSheet == null)
├── Layer 2: Bottom Sheet BackHandler      (enabled = !isDrawerVisible && activeBottomSheet != null)
└── Layer 1: Navigation Drawer BackHandler (enabled = isDrawerVisible)
```

Because Layer 1 is composed after Layer 3:
- In `OnBackPressedDispatcher`, Layer 1 is positioned at the head of the dispatch deque.
- Whenever `isDrawerVisible == true`, Layer 1 evaluates first and exclusively consumes the back press.
- Even if Layer 3 were evaluated, its `enabled` condition (`!isDrawerVisible`) is strictly `false`, making it impossible for Layer 3 to execute while the drawer is visible or transitioning.

---

## 6. Architectural Design & Implementation Specifics

### 6.1 Refactoring `NavigationDrawerController` with Full Regression Mitigation
To eliminate state duplication while preventing breaking regressions in existing unit tests (`SingleActivityNavigationTest.kt:128-152`):
`NavigationDrawerController` retains the `isDrawerOpen` public property getter/setter, but implements a dual-mode delegating architecture:
- When unbound (e.g. in pure standalone unit tests without Compose runtime): it operates on an internal backing field `_unboundIsDrawerOpen`, ensuring all existing unit tests pass without modification.
- When bound (via `bindDrawer` in `ATrainingTrackerApp`): all calls to `openDrawer()`, `closeDrawer()`, and reads of `isDrawerOpen` delegate directly to Compose Material 3 `DrawerState`.

```kotlin
class NavigationDrawerController(
    initialSelectedItemId: Int = R.id.drawer_start_tracking,
    initialStartTrackingTitleRes: Int = R.string.tab_start
) {
    var selectedItemId: Int by mutableIntStateOf(initialSelectedItemId)
    var startTrackingTitleRes: Int by mutableIntStateOf(initialStartTrackingTitleRes)
    var activeBottomSheet: SettingsBottomSheetType? by mutableStateOf(null)

    private var _unboundIsDrawerOpen by mutableStateOf(false)
    private var openDrawerDelegate: (() -> Unit)? = null
    private var closeDrawerDelegate: (() -> Unit)? = null
    private var isDrawerOpenDelegate: (() -> Boolean)? = null

    var isDrawerOpen: Boolean
        get() = isDrawerOpenDelegate?.invoke() ?: _unboundIsDrawerOpen
        set(value) {
            _unboundIsDrawerOpen = value
            if (value) openDrawer() else closeDrawer()
        }

    fun openDrawer() {
        val delegate = openDrawerDelegate
        if (delegate != null) {
            delegate.invoke()
        } else {
            _unboundIsDrawerOpen = true
        }
    }

    fun closeDrawer() {
        val delegate = closeDrawerDelegate
        if (delegate != null) {
            delegate.invoke()
        } else {
            _unboundIsDrawerOpen = false
        }
    }

    fun bindDrawer(open: () -> Unit, close: () -> Unit, isOpen: () -> Boolean) {
        openDrawerDelegate = open
        closeDrawerDelegate = close
        isDrawerOpenDelegate = isOpen
    }

    fun unbindDrawer() {
        openDrawerDelegate = null
        closeDrawerDelegate = null
        isDrawerOpenDelegate = null
    }
}
```

### 6.2 Binding and Hierarchical BackHandlers in `ATrainingTrackerApp.kt`
In `ATrainingTrackerApp.kt`:
```kotlin
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 1. Single Source of Truth Binding: Attach DrawerController directly to DrawerState
    DisposableEffect(drawerState, scope) {
        drawerController.bindDrawer(
            open = { scope.launch { drawerState.open() } },
            close = { scope.launch { drawerState.close() } },
            isOpen = { drawerState.isOpen || drawerState.targetValue == DrawerValue.Open }
        )
        onDispose {
            drawerController.unbindDrawer()
        }
    }

    val isDrawerVisible = drawerState.isOpen || drawerState.targetValue == DrawerValue.Open

    // Layer 3: Screen-level Back Navigation (Composed first, guarded by overlay closure)
    BackHandler(enabled = !isDrawerVisible && drawerController.activeBottomSheet == null) {
        when {
            navController.previousBackStackEntry != null -> navController.popBackStack()
            currentRoute != NavRoutes.START_TRACKING -> {
                if (currentRoute == NavRoutes.WORKOUTS) {
                    MyPreferenceManager(activity).clearWorkoutFilterCriteria()
                } else if (currentRoute == NavRoutes.ROUTES) {
                    MyPreferenceManager(activity).clearRouteFilterCriteria()
                } else if (currentRoute == NavRoutes.LOCATIONS) {
                    MyPreferenceManager(activity).clearClusterFilterCriteria()
                }
                activity.navigateToDrawerItem(R.id.drawer_start_tracking)
            }
            else -> activity.finish()
        }
    }

    // Layer 2: Settings Bottom Sheet Dismissal
    BackHandler(enabled = !isDrawerVisible && drawerController.activeBottomSheet != null) {
        drawerController.activeBottomSheet = null
    }

    // Layer 1: Navigation Drawer Dismissal (Composed last, evaluated first in LIFO)
    BackHandler(enabled = isDrawerVisible) {
        scope.launch { drawerState.close() }
    }
```

### 6.3 Deterministic Mid-Animation Interruption Safety
When back is pressed mid-flight while the drawer is animating open (`targetValue == DrawerValue.Open`):
1. `isDrawerVisible` is `true`.
2. Layer 1 consumes the back press and calls `scope.launch { drawerState.close() }`.
3. In Compose Material 3, `drawerState.close()` suspends/cancels the in-flight opening animation and reverses the transition toward `DrawerValue.Closed`.
4. Layer 3 is disabled (`enabled = false`), ensuring zero route changes or screen reloads occur during the entire reversal animation.
