# Engineering Analysis - ATT-1327: NavigationDrawer Back Navigation Defect (Navigation to Start Tracking Instead of Drawer Dismissal)

**Ticket**: [ATT-1327](https://rainerblind.atlassian.net/browse/ATT-1327)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Component**: `com.atrainingtracker.trainingtracker.ui.navigation.ATrainingTrackerApp.kt`, `AppNavigationDrawer.kt`  
**Parent Requirement**: `REQ-UI-159` (Single-Activity Architecture & Jetpack Compose Navigation Modernization)  
**Proposed Requirement**: `REQ-UI-162` (Single Source of Truth & Scoped Overlay Back Navigation)  
**Proposed Test Spec**: `TST-UI-114` (ModalNavigationDrawer Back Navigation Isolation & State Synchronization Test)  

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
* **Proposed Target Requirement (`REQ-UI-162`)**:
  > The system SHALL establish Material 3's `DrawerState` as the single authoritative source of truth for drawer visibility and navigation control, eliminating decoupled boolean state flags. The system SHALL isolate overlay back navigation (Navigation Drawer and Settings Bottom Sheets) from screen-level destination back navigation using scoped, conditionally enabled `BackHandler`s. When the navigation drawer is open or transitioning to open (`drawerState.isOpen || drawerState.targetValue == DrawerValue.Open`), system back navigation SHALL exclusively close the drawer without altering the current destination or backstack. Back navigation to parent destinations or the main tracking screen SHALL be enabled only when all modal overlays and drawers are completely closed.
* **Proposed Verification (`TST-UI-114`)**:
  Automated unit test coverage in `SingleActivityNavigationTest.kt` verifying drawer back-handling predicate states, single-source-of-truth delegation, mid-gesture interruption handling, and clean-room full suite regression (626+ tests).

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

### Key Finding on Android `OnBackPressedDispatcher` LIFO Priority:
Android's `OnBackPressedDispatcher` maintains an `ArrayDeque` of callbacks and invokes the most recently added **enabled** callback first.
When the user opens the `ModalNavigationDrawer`:
- If the drawer's `BackHandler` is declared inside `drawerContent` (within `ModalDrawerSheet`), it is composed into the tree after/alongside the drawer presentation.
- By scoping `BackHandler(enabled = drawerState.isOpen || drawerState.targetValue == DrawerValue.Open)` directly to the drawer, the drawer handler is guaranteed to be enabled when the drawer is open.
- Simultaneously, guarding the root screen handler with `enabled = !isDrawerVisible && drawerController.activeBottomSheet == null` guarantees that root screen navigation can NEVER trigger while the drawer is visible or transitioning.

---

## 5. Preserved Invariants & Invariant Criteria

1. **Overlay Isolation (Invariant 1)**:
   Whenever the navigation drawer is visible or transitioning (`drawerState.isOpen || drawerState.targetValue == DrawerValue.Open`), Back press MUST ONLY dismiss the drawer. The current route and fragment state MUST remain 100% unchanged.
2. **Bottom Sheet Isolation (Invariant 2)**:
   Whenever an active bottom sheet is displayed (`drawerController.activeBottomSheet != null`), Back press MUST ONLY dismiss the sheet.
3. **Screen Hierarchy Integrity (Invariant 3)**:
   Screen-level navigation on Back (popping child backstack, clearing active workout/route/cluster filters, and routing to `START_TRACKING`) MUST ONLY execute when NO drawer and NO modal bottom sheet are active.
4. **Activity Finish (Invariant 4)**:
   When on `START_TRACKING` with no overlays and no backstack, Back press MUST finish the activity (`activity.finish()`).
5. **No Trapping / Lock-Out**:
   The drawer back-handler MUST disable itself immediately once `drawerState.targetValue == DrawerValue.Closed`, ensuring the user is never trapped in a disabled navigation state.

---

## 6. Architectural Design & Proposed Solution

### 6.1 Elimination of State Duplication
Refactor `NavigationDrawerController`:
- Remove the independent `var isDrawerOpen: Boolean by mutableStateOf(false)`.
- Introduce a delegate mechanism where `openDrawer()` and `closeDrawer()` trigger coroutine actions on the bound Compose `DrawerState`:
  ```kotlin
  class NavigationDrawerController(...) {
      var selectedItemId: Int by mutableIntStateOf(initialSelectedItemId)
      var startTrackingTitleRes: Int by mutableIntStateOf(initialStartTrackingTitleRes)
      var activeBottomSheet: SettingsBottomSheetType? by mutableStateOf(null)

      internal var openDrawerAction: (() -> Unit)? = null
      internal var closeDrawerAction: (() -> Unit)? = null
      internal var isDrawerOpenProvider: (() -> Boolean)? = null

      val isDrawerOpen: Boolean
          get() = isDrawerOpenProvider?.invoke() ?: false

      fun openDrawer() { openDrawerAction?.invoke() }
      fun closeDrawer() { closeDrawerAction?.invoke() }

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
- In `ATrainingTrackerApp.kt`:
  ```kotlin
  DisposableEffect(drawerState, scope) {
      drawerController.bindDrawer(
          open = { scope.launch { drawerState.open() } },
          close = { scope.launch { drawerState.close() } },
          isOpen = { drawerState.isOpen || drawerState.targetValue == DrawerValue.Open }
      )
      onDispose { drawerController.unbindDrawer() }
  }
  ```
This makes Material 3's `DrawerState` the **single authoritative source of truth**, completely eliminating duplicate state variables and ping-ponging `LaunchedEffect`s!

### 6.2 Scoped Hierarchical `BackHandler` Architecture
In `ATrainingTrackerApp.kt`:
```kotlin
val isDrawerVisible = drawerState.isOpen || drawerState.targetValue == DrawerValue.Open

// Layer 1: Navigation Drawer Dismissal (Scoped to Drawer Visibility)
BackHandler(enabled = isDrawerVisible) {
    scope.launch { drawerState.close() }
}

// Layer 2: Settings Bottom Sheet Dismissal
BackHandler(enabled = !isDrawerVisible && drawerController.activeBottomSheet != null) {
    drawerController.activeBottomSheet = null
}

// Layer 3: Screen Navigation (Only enabled when all overlays are closed)
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
```

### 6.3 Deterministic Animation & Interruption Handling
When back is pressed during an opening animation (`targetValue == DrawerValue.Open`):
- Layer 1 is enabled and executes `scope.launch { drawerState.close() }`.
- In Compose Material 3, invoking `drawerState.close()` cancels the current opening animation job and launches a reverse animation smoothly returning the offset to 0.
- Layer 3 remains disabled throughout this entire transition, ensuring zero screen flicker or route jumping.
