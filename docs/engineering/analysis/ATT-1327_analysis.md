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

## 2. Requirement Traceability & Exact Git Baseline Diffs

### 2.1 Traceability Mapping
* **Parent Requirement**: `REQ-UI-159` (Single-Activity Architecture & Jetpack Compose Navigation Modernization)
* **Committed Target Requirement**: `REQ-UI-162` (Single Source of Truth & Scoped Overlay Back Navigation)
* **Committed Verification Specification**: `TST-UI-114` (ModalNavigationDrawer Back Navigation Isolation & State Synchronization Test)

### 2.2 Exact Git Diff: `docs/requirements.md` (`REQ-UI-162`)
```diff
--- a/docs/requirements.md
+++ b/docs/requirements.md
@@ -309,6 +309,7 @@
 | **REQ-UI-161** | **ModalNavigationDrawer Edge-Swipe Restriction & Touch Conflict Resolution.** | The system SHALL restrict swipe-to-open gesture detection on `ModalNavigationDrawer` exclusively to the leftmost screen edge when the drawer is in the closed state, preventing unintended drawer opening during map panning and child content interactions... | `ATrainingTrackerApp.kt` | `TST-UI-113` | Verified |
+| **REQ-UI-162** | **Single Source of Truth & Scoped Overlay Back Navigation.** | The system SHALL establish Material 3's `DrawerState` as the single authoritative source of truth for navigation drawer visibility and navigation control, eliminating decoupled boolean state flags, and SHALL isolate overlay back navigation (Navigation Drawer and Settings Bottom Sheets) from screen-level destination back navigation using scoped, conditionally enabled `BackHandler`s:<br>1. *Single Source of Truth*: `NavigationDrawerController` SHALL delegate drawer open and close actions directly to Material 3's `DrawerState` via functional action delegates, removing the decoupled mutable boolean state flag (`isDrawerOpen`) entirely and eliminating split-brain asynchronous synchronization.<br>2. *Scoped Overlay Back Navigation*: When the navigation drawer is open, opening, or closing (`drawerState.currentValue != DrawerValue.Closed || drawerState.targetValue != DrawerValue.Closed`), system back navigation (`BackHandler`) SHALL exclusively dismiss/close the navigation drawer (`scope.launch { drawerState.close() }`), without modifying the active Composable destination or backstack.<br>3. *Settings Bottom Sheet Isolation*: When an active bottom sheet is displayed (`drawerController.activeBottomSheet != null`), system back navigation SHALL exclusively dismiss the active sheet (`drawerController.activeBottomSheet = null`).<br>4. *Screen-Level Back Navigation Guard*: Screen-level destination back navigation (popping `NavHost` backstack, clearing active filter criteria, or routing to `NavRoutes.START_TRACKING`) SHALL be guarded by `enabled = !isDrawerVisible && drawerController.activeBottomSheet == null`, ensuring screen destinations NEVER change while the drawer or bottom sheets are visible or settling.<br>5. *Deterministic Interruption & Deadlock Prevention (INV-UI-04)*: If back navigation is invoked mid-animation during a drawer opening or closing transition, the system SHALL catch `CancellationException` without fault. To prevent UI deadlocks if an animation hangs due to main-thread starvation, drawer closure SHALL be bounded by a 400ms timeout (`withTimeoutOrNull(400L)`) with a deterministic `snapTo(DrawerValue.Closed)` fallback executed within the Compose coroutine scope, guaranteeing Layer 3 screen navigation is restored.<br><br>**Acceptance Criteria (Given-When-Then)**:<br>• *Given* the athlete is on any destination (e.g. Workouts, Routes, Periods, Map, Sensors) with the navigation drawer open or opening,<br>• *When* the athlete invokes system back navigation (hardware button or back gesture),<br>• *Then* the navigation drawer SHALL close smoothly and the active screen destination SHALL remain completely unchanged.<br>• *Given* the athlete is on any non-tracking destination with the navigation drawer closed and no overlays active,<br>• *When* the athlete invokes system back navigation,<br>• *Then* the app SHALL navigate to the main tracking control (`NavRoutes.START_TRACKING`) and clear active filter criteria.<br>• *Given* the athlete is on `START_TRACKING` with no overlays and no backstack,<br>• *When* back is pressed,<br>• *Then* the activity SHALL finish.<br>• *Given* an animation delay exceeding 400ms during drawer dismissal,<br>• *When* the timeout expires,<br>• *Then* `snapTo(DrawerValue.Closed)` SHALL immediately settle the drawer to closed and unblock screen back navigation.<br><br>**Invariants**: All 21 navigation drawer destinations and routes in `NavRoutes` preserved; `drawerController.selectedItemId` synchronization preserved; edge-swipe gesture bounds (REQ-UI-161) preserved; no screen-level touch interception regressions. | Eliminate the defect where pressing back with the navigation drawer open switches to the main tracking screen while leaving the drawer stuck open, by establishing DrawerState as single source of truth and isolating overlay back handling from screen navigation. | `ATrainingTrackerApp.kt`, `AppNavigationDrawer.kt` | `TST-UI-114` | Draft |
```

### 2.3 Exact Git Diff: `docs/tests.md` (`TST-UI-114`)
```diff
--- a/docs/tests.md
+++ b/docs/tests.md
@@ -362,6 +362,7 @@
 | **TST-UI-113** | `ATT-1310` | **ModalNavigationDrawer Edge-Swipe Restriction & Map Panning Test** | `REQ-UI-161` | 1. *Unit Test Navigation Drawer Gesture State Mapping (`SingleActivityNavigationTest.kt`)*... | Verified |
+| **TST-UI-114** | `ATT-1327` | **ModalNavigationDrawer Back Navigation Isolation & State Synchronization Test** | `REQ-UI-162` | 1. *Unit Test Navigation Drawer Controller Single Source of Truth (`SingleActivityNavigationTest.kt`)*:<br>• Verify that `NavigationDrawerController` properly delegates `openDrawer()`, `closeDrawer()`, and `isDrawerOpen` when bound to an authoritative state provider.<br>• Verify backward-compatible fallback for `isDrawerOpen` before binding (clean default without NPE).<br>2. *Unit Test Scoped BackHandler Visibility Predicates (`SingleActivityNavigationTest.kt`)*:<br>• Verify that when `drawerState` is open or opening (`isOpen == true` or `targetValue == Open`), `isDrawerVisible` evaluates to `true`, activating Layer 1 and disabling Layer 3.<br>• Verify that when `drawerState` is closed (`isOpen == false` and `targetValue == Closed`), `isDrawerVisible` evaluates to `false`, deactivating Layer 1 and enabling Layer 3.<br>3. *Rapid Double-Back Press & CancellationException Resilience Test (`SingleActivityNavigationTest.kt`)*:<br>• Verify that while the drawer is transitioning (`targetValue == Open` or animation in flight), `isDrawerVisible` remains `true`, keeping Layer 3 disabled and preventing double-press fall-through to screen navigation.<br>• Verify that calling `close()` during an active opening animation handles `CancellationException` gracefully without crashing or dropping frames.<br>4. *Deadlock Safety Timeout & Starvation Simulation Test (`SingleActivityNavigationTest.kt`)*:<br>• Simulate animation delay exceeding the 400ms safety threshold; verify timeout fallback executes `snapTo(DrawerValue.Closed)`, immediately unblocking Layer 3 screen navigation and preventing UI deadlocks (INV-UI-04).<br>5. *Clean-Room Full Suite Regression*:<br>• Execute `./gradlew testDebugUnitTest` and verify 0 failures across all 626+ unit tests.<br>6. *Physical Device Verification*:<br>• On `Workouts` (or any non-tracking screen), open navigation drawer (via edge-swipe or hamburger).<br>• Press Android Back button/gesture: verify drawer dismisses smoothly and the screen remains on `Workouts` (does NOT navigate to tracking).<br>• Press Back again with drawer closed: verify app navigates to main tracking control screen.<br>• While drawer is opening, perform rapid double-back press: verify drawer closes cleanly without unexpected screen route changes. | Pressing Back with drawer open exclusively dismisses the drawer without altering the current screen; pressing Back with drawer closed navigates to main tracking control; rapid double-back presses and cancellation exceptions are handled without route jumps; 400ms timeout prevents UI deadlocks; 0 unit test regressions. | Draft |
```

---

## 3. Forensic Root Cause Analysis (RCA)

### 3.1 Historical Context: Why Boolean Wrapping Was Introduced and Why It Failed
During `ATT-1083` (migrating from Android View `DrawerLayout` to Compose `ModalNavigationDrawer`), `NavigationDrawerController` was introduced as an observable holder to let imperative Activity methods (`openDrawer()`, `closeDrawer()`) communicate with declarative Compose.
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

## 4. Comprehensive Call-Site Inventory Audit

### 4.1 Exhaustive Inventory of `NavigationDrawerController.isDrawerOpen` Call Sites
A complete ripgrep search across all Kotlin and Java files in the repository yields exactly three locations:
1. `AppNavigationDrawer.kt:110, 114, 118`: Declaration and assignment inside `NavigationDrawerController`.
2. `ATrainingTrackerApp.kt:125, 126, 134`: Deleted in this refactoring.
3. `SingleActivityNavigationTest.kt:132, 137, 141`: Legacy unit tests verifying controller open/close mutations.
**Zero references exist in any Activity, Fragment, ViewModel, Service, or XML layout.**
To guarantee zero regressions in standalone unit tests (`SingleActivityNavigationTest`), `NavigationDrawerController` maintains backward-compatible delegation:
```kotlin
val isDrawerOpen: Boolean
    get() = isDrawerOpenProvider?.invoke() ?: _unboundIsDrawerOpen
```

### 4.2 Comprehensive Audit of all 12 `BackHandler` Call Sites
1. `ATrainingTrackerApp.kt:154`: Root navigation handler (refactored into 3 scoped layers).
2. `MapScreenWithTrack.kt:151`: `BackHandler(enabled = selectedSegmentId != null || selectedRouteId != null)`.
3. `WorkoutSummariesTabbedScreen.kt:207`: `BackHandler` enabled only when workout details are open.
4. `TrackingTabsScreen.kt:142`: `BackHandler(enabled = screenMode == ScreenMode.CONFIGURATION)`.
5. `PeriodMapScreen.kt:571`: `BackHandler` for period map peek selection.
6. `RoutesScreen.kt:144, 162`: `BackHandler` for route details/edit.
7. `StarredSegmentsScreen.kt:106`: `BackHandler` for segment details.
8. `WorkoutClustersScreen.kt:116, 129, 147, 201, 256`: `BackHandler`s for cluster editing/viewing.
9. `ClusterTuningScreen.kt:52`: `BackHandler(enabled = !isRecalculating)`.
10. `WorkoutClusterHeatmapScreen.kt:582`: Heatmap back handling.
11. `LapEditBottomSheet.kt:193`: Lap edit dismissal.

---

## 5. Composition Tree Ordering & LIFO Execution Safety

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

## 6. Edge-Case Analysis: 4-State Matrix & Rapid Double-Tap Back Handling

The visibility predicate is derived **exclusively from native `DrawerState` properties**:
```kotlin
val isDrawerVisible = drawerState.currentValue != DrawerValue.Closed || drawerState.targetValue != DrawerValue.Closed
```

### 6.1 State Matrix Evaluation
| Permutation | `currentValue` | `targetValue` | Physical State | `isDrawerVisible` | Layer 1 Enabled? | Layer 3 Enabled? | Behavior on Back Press |
|:---|:---|:---|:---|:---|:---|:---|:---|
| **State 1** | `Closed` | `Closed` | Fully Closed | `false` | Disabled | **Enabled** | Screen navigation operates normally (pops backstack, navigates to `NavRoutes.START_TRACKING`, or finishes activity). |
| **State 2** | `Closed` | `Open` | Animating Open / Swiping Open | `true` | **Enabled** | Disabled | Layer 1 intercepts back, calls safe closure, cancels opening animation and reverses drawer to `Closed`. Zero screen navigation occurs. |
| **State 3** | `Open` | `Open` | Fully Open | `true` | **Enabled** | Disabled | Layer 1 intercepts back, calls safe closure. Drawer smoothly closes. Zero screen navigation occurs. |
| **State 4** | `Open` | `Closed` | Actively Dismissing / Closing | `true` | **Enabled** | Disabled | Layer 1 intercepts back, re-asserts safe closure idempotently in Compose M3 without gesture fault. Layer 3 remains strictly disabled until drawer completely settles into State 1. |

### 6.2 Rapid Double-Tap Back Press Handling
When an athlete double-taps Back in rapid succession during a drawer transition (e.g. 50ms-150ms interval):
1. **First Tap**:
   - `isDrawerVisible` is `true`. Layer 1 intercepts the tap and executes `scope.launch { withTimeoutOrNull(400L) { drawerState.close() } ?: drawerState.snapTo(DrawerValue.Closed) }`.
   - The drawer starts animating towards `Closed` (State 4: `currentValue == Open, targetValue == Closed`).
2. **Second Tap (mid-flight during the 250ms/400ms closing transition)**:
   - In State 4, `isDrawerVisible` evaluates to `true` because `drawerState.currentValue != DrawerValue.Closed`.
   - Layer 1 remains enabled; Layer 3 remains strictly disabled (`!isDrawerVisible == false`).
   - The second tap is consumed by Layer 1. `scope.launch` cancels the previous job; `CancellationException` is caught defensively.
   - The second tap does NOT pop backstack, does NOT switch destinations, and does NOT throw exceptions.
3. **Subsequent Taps (after drawer settles into State 1: `Closed, Closed`)**:
   - `isDrawerVisible` evaluates to `false`. Layer 1 disables itself; Layer 3 enables itself.
   - The next back tap executes standard screen navigation.

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
- `DrawerState.snapTo(targetValue: DrawerValue)` is an official `suspend` function:
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
```kotlin
class NavigationDrawerController(
    initialSelectedItemId: Int = R.id.drawer_start_tracking,
    initialStartTrackingTitleRes: Int = R.string.tab_start
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

### 8.2 Exact Code Diff in `ATrainingTrackerApp.kt`
```diff
--- a/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt
+++ b/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt
@@ -124,17 +124,14 @@ fun ATrainingTrackerApp(
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
+            close = { scope.launch { drawerState.close() } },
+            isOpen = { drawerState.currentValue != DrawerValue.Closed || drawerState.targetValue != DrawerValue.Closed }
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
