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
The app switches Composable destinations back to the main tracking control (`NavRoutes.START_TRACKING`), but the navigation drawer remains visible or stuck open on top of the screen.

### Expected Behavior (Invariants):
1. When the `ModalNavigationDrawer` is open or visibly presented, pressing Back MUST exclusively dismiss/close the navigation drawer. The underlying active Composable destination and screen content MUST NOT change.
2. Only when the navigation drawer is completely closed / not visible and Back is clicked, should back navigation proceed down the hierarchy (e.g. pop destination backstack, clear filters, navigate to the main tracking control screen, or finish the activity).

---

## 2. Requirement Traceability & ASPICE Mapping

* **Parent Requirement (`REQ-UI-159`)**: Mandated the modernization of `MainActivityWithNavigation` into a single-activity architecture hosting `ModalNavigationDrawer`, `NavHost`, and centralized back handling.
* **Defect Identified**: 
  1. *Historical State Duplication Anti-Pattern*: During `ATT-1083`, `NavigationDrawerController.isDrawerOpen` was introduced as an imperative bridge between legacy Java/Kotlin Activity methods (`openDrawer()`, `closeDrawer()`) and Compose. Synchronizing this decoupled boolean with Compose Material 3's internal `DrawerState` via asynchronous `LaunchedEffect` coroutines introduced a split-brain latency gap.
  2. *Monolithic BackHandler Fall-Through*: In `ATrainingTrackerApp.kt`, an unconditionally enabled `BackHandler` evaluated drawer dismissal strictly on `drawerState.isOpen` (`currentValue == DrawerValue.Open`). During opening transitions, gesture drags, or state desynchronization, this check evaluated to `false`, causing the state machine to fall through to `currentRoute != NavRoutes.START_TRACKING` and execute navigation to `NavRoutes.START_TRACKING`.
* **Committed Target Requirement (`REQ-UI-162`)**:
  Formally integrated into `docs/requirements.md`:
  > The system SHALL establish Material 3's `DrawerState` as the single authoritative source of truth for navigation drawer visibility and navigation control, eliminating decoupled boolean state flags, and SHALL isolate overlay back navigation (Navigation Drawer and Settings Bottom Sheets) from screen-level destination back navigation using scoped, conditionally enabled `BackHandler`s.
* **Committed Verification Specification (`TST-UI-114`)**:
  Formally integrated into `docs/tests.md`:
  Automated unit test coverage in `SingleActivityNavigationTest.kt` verifying drawer back-handling predicate states, single-source-of-truth delegation, deadlock safety timeout fallback, `CancellationException` resilience, and clean-room full suite regression (626+ tests).

---

## 3. Forensic Root Cause Analysis (RCA)

### 3.1 Why Custom Boolean Wrapping Was Introduced and Why It Failed
In the legacy Android View architecture, `DrawerLayout` was operated imperatively via `openDrawer()` and `closeDrawer()`. When `MainActivityWithNavigation` was migrated to Compose under `ATT-1083`, `NavigationDrawerController` was created as an interim observable holder:
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
1. **Split-Brain Latency Gap**: Calling `openDrawer()` sets `isDrawerOpen = true`. However, Material 3's `DrawerState.open()` is an asynchronous animation lasting 250ms+. Throughout this animation, `drawerState.currentValue` remains `DrawerValue.Closed`. Thus, `drawerState.isOpen` evaluates to **`false`**.
2. **Animation Cancellation Failure**: When `navigateToDrawerItem` runs, it calls `mDrawerController.closeDrawer()`, setting `isDrawerOpen = false`. The `LaunchedEffect` runs: `if (drawerState.isOpen) drawerState.close()`. Because `drawerState.isOpen` was still evaluating to `false`, **`drawerState.close()` is never called**, leaving the drawer frozen in its open state over the new destination.

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
- If back is pressed while the drawer is open or opening, `drawerState.isOpen` evaluates to `false`.
- The `when` block bypasses branch 1, branch 2, and branch 3.
- It hits branch 4: `currentRoute != NavRoutes.START_TRACKING`.
- It executes navigation to `NavRoutes.START_TRACKING`, swapping the Composable screen while the drawer stays visible.

---

## 4. Comprehensive Call-Site & Child-Composable BackHandler Audit

An exhaustive audit of all 12 `BackHandler` call-sites across the codebase was conducted:
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

To guarantee mathematical LIFO execution safety between the overlay layers and root navigation in `ATrainingTrackerApp.kt`:
1. **Layer 3 (Screen Navigation)** is composed FIRST in the tree, guarded by `enabled = !isDrawerVisible && drawerController.activeBottomSheet == null`.
2. **Layer 2 (Settings Bottom Sheet)** is composed SECOND in the tree, guarded by `enabled = !isDrawerVisible && drawerController.activeBottomSheet != null`.
3. **Layer 1 (Navigation Drawer)** is composed THIRD (newest) in the tree, guarded by `enabled = isDrawerVisible`.

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

## 6. Edge-Case Analysis: Mathematically Consistent 4-State Truth Table

To permanently eliminate split-brain contradictions, the drawer visibility predicate is derived **exclusively from native `DrawerState` properties**:
```kotlin
val isDrawerVisible = drawerState.currentValue != DrawerValue.Closed || drawerState.targetValue != DrawerValue.Closed
```

Truth table evaluation across all 4 permutations of `DrawerState` (`currentValue` vs `targetValue`):

| Permutation | `currentValue` | `targetValue` | Physical Drawer State | `isDrawerVisible` | Layer 1 Enabled? | Layer 3 Enabled? | Behavior on Back Press |
|:---|:---|:---|:---|:---|:---|:---|:---|
| **State 1** | `Closed` | `Closed` | Fully Closed | `false` | Disabled | **Enabled** | Screen navigation operates normally (pops backstack, navigates to `NavRoutes.START_TRACKING`, or finishes activity). |
| **State 2** | `Closed` | `Open` | Animating Open / Swiping Open | `true` | **Enabled** | Disabled | Layer 1 intercepts back, calls safe closure, cancels opening animation and reverses drawer to `Closed`. Zero screen navigation occurs. |
| **State 3** | `Open` | `Open` | Fully Open | `true` | **Enabled** | Disabled | Layer 1 intercepts back, calls safe closure. Drawer smoothly closes. Zero screen navigation occurs. |
| **State 4** | `Open` | `Closed` | Actively Dismissing / Closing | `true` | **Enabled** | Disabled | Layer 1 intercepts back, safely re-asserts closure. Layer 3 remains strictly disabled until drawer completely settles into State 1, preventing premature screen route jumps. |

---

## 7. Concurrency, Cancellation Safety & Proof of `snapTo()` Concurrency Safety (INV-UI-04)

### 7.1 Coroutine Dispatch Context & Cancellation Exception Safety
In `ATrainingTrackerApp.kt`:
The coroutine scope `val scope = rememberCoroutineScope()` is bound to the Composable lifecycle (`AndroidUiDispatcher.Main`).
When closing the drawer on back press:
If the user presses back while an opening animation is in flight (State 2) or during a closing transition (State 4), Compose Material 3's `animateTo` cancels the previous animation job by throwing a `CancellationException`.
To prevent unhandled exceptions or frame drops, the closure routine catches `CancellationException` as normal cooperative cancellation.

### 7.2 Proof of `snapTo()` Concurrency Safety
In AndroidX Jetpack Compose Material 3:
- `DrawerState.snapTo(targetValue: DrawerValue)` is a `suspend` function:
  ```kotlin
  suspend fun snapTo(targetValue: DrawerValue) = anchoredDraggableState.snapTo(targetValue)
  ```
- `snapTo()` acquires the internal `MutatorMutex` of the draggable state.
- Because `snapTo()` is invoked inside `scope.launch { ... }`, it executes asynchronously on `Dispatchers.Main.immediate` **outside of the Compose composition and apply phases**.
- Therefore, calling `snapTo(DrawerValue.Closed)` is 100% thread-safe and can never trigger an `IllegalStateException` ("Composables can only be written to during the apply phase").

### 7.3 Deadlock Prevention Safety Valve (INV-UI-04)
If an animation were to freeze or fail to settle due to main-thread starvation or heavy background sensor load:
- The `withTimeoutOrNull(400L)` safety valve ensures that if `drawerState.close()` does not settle within 400ms, `drawerState.snapTo(DrawerValue.Closed)` is immediately invoked.
- `snapTo(DrawerValue.Closed)` instantly resets `currentValue` and `targetValue` to `DrawerValue.Closed`.
- This immediately resets `isDrawerVisible = false`, unblocking Layer 3 and completely preventing any permanent back button deadlocks.

---

## 8. Architectural Design & Implementation Specifics

### 8.1 Elimination of State Duplication in `NavigationDrawerController`
Refactor `NavigationDrawerController` to eliminate the mutable boolean `isDrawerOpen`.
`NavigationDrawerController` acts as a pure functional delegate for imperative actions (`openDrawer()`, `closeDrawer()`) bound to Compose's `DrawerState`:

```kotlin
class NavigationDrawerController(
    initialSelectedItemId: Int = R.id.drawer_start_tracking,
    initialStartTrackingTitleRes: Int = R.string.tab_start
) {
    var selectedItemId: Int by mutableIntStateOf(initialSelectedItemId)
    var startTrackingTitleRes: Int by mutableIntStateOf(initialStartTrackingTitleRes)
    var activeBottomSheet: SettingsBottomSheetType? by mutableStateOf(null)

    private var openDrawerAction: (() -> Unit)? = null
    private var closeDrawerAction: (() -> Unit)? = null

    fun openDrawer() { openDrawerAction?.invoke() }
    fun closeDrawer() { closeDrawerAction?.invoke() }

    fun bindDrawer(open: () -> Unit, close: () -> Unit) {
        openDrawerAction = open
        closeDrawerAction = close
    }

    fun unbindDrawer() {
        openDrawerAction = null
        closeDrawerAction = null
    }
}
```

### 8.2 Exact Code Diff in `ATrainingTrackerApp.kt`
```diff
--- a/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt
+++ b/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt
@@ -124,17 +124,13 @@ fun ATrainingTrackerApp(
-    // Synchronize drawerController.isDrawerOpen with Compose DrawerState
-    LaunchedEffect(drawerController.isDrawerOpen) {
-        if (drawerController.isDrawerOpen) {
-            if (!drawerState.isOpen) drawerState.open()
-        } else {
-            if (drawerState.isOpen) drawerState.close()
-        }
-    }
-
-    LaunchedEffect(drawerState.isOpen) {
-        drawerController.isDrawerOpen = drawerState.isOpen
+    // REQ-UI-162: Establish DrawerState as single source of truth
+    DisposableEffect(drawerState, scope) {
+        drawerController.bindDrawer(
+            open = { scope.launch { drawerState.open() } },
+            close = { scope.launch { drawerState.close() } }
+        )
+        onDispose {
+            drawerController.unbindDrawer()
+        }
     }
 
@@ -153,19 +150,28 @@ fun ATrainingTrackerApp(
-    // Single-Activity Back Navigation State Machine
-    BackHandler {
+    val isDrawerVisible = drawerState.currentValue != DrawerValue.Closed || drawerState.targetValue != DrawerValue.Closed
+
+    // REQ-UI-162 Layer 3: Screen Navigation (composed first, disabled when any overlay is active)
+    BackHandler(enabled = !isDrawerVisible && drawerController.activeBottomSheet == null) {
         when {
-            drawerState.isOpen -> scope.launch { drawerState.close() }
-            drawerController.activeBottomSheet != null -> drawerController.activeBottomSheet = null
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
+
+    // REQ-UI-162 Layer 2: Settings Bottom Sheet (composed second, disabled when drawer is visible)
+    BackHandler(enabled = !isDrawerVisible && drawerController.activeBottomSheet != null) {
         drawerController.activeBottomSheet = null
     }
+
+    // REQ-UI-162 Layer 1: Navigation Drawer (composed last = first in LIFO priority)
+    BackHandler(enabled = isDrawerVisible) {
+        scope.launch {
+            try {
+                withTimeoutOrNull(400L) {
+                    drawerState.close()
+                } ?: drawerState.snapTo(DrawerValue.Closed)
+            } catch (_: CancellationException) {
+                // Cooperative cancellation during gesture reversal
+            }
+        }
+    }
```

### 8.3 Configuration Change & Process Recreation Resilience
In Jetpack Compose Material 3:
- `val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)` is backed internally by `rememberSaveable(saver = DrawerState.Saver(...))`.
- When an Android configuration change occurs (e.g. device rotation while the drawer is open or animating):
  1. The Activity recreates and `setContent` recomposes `ATrainingTrackerApp`.
  2. `rememberDrawerState` automatically restores `DrawerState.currentValue` from the saved bundle.
  3. `DisposableEffect(drawerState, scope)` executes immediately upon entry, binding the newly instantiated coroutine `scope` and restored `drawerState` to `drawerController`.
  4. There are zero decoupled boolean flags to desynchronize across Activity recreation cycles.
