# Engineering Walkthrough - ATT-1335: Dynamic LIFO Precedence for ModalNavigationDrawer Back Handling

**Ticket**: [ATT-1335](https://rainerblind.atlassian.net/browse/ATT-1335)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Components**: 
* `com.atrainingtracker.trainingtracker.ui.navigation.ATrainingTrackerApp.kt`
* `com.atrainingtracker.trainingtracker.ui.navigation.SingleActivityNavigationTest.kt`
**Requirement**: `REQ-UI-162`  
**Test Spec**: `TST-UI-114`  
**Branch**: `bugfix/ATT-1335`  

---

## 1. Problem & Root Cause Analysis

### Problem:
On physical devices, when an athlete opened the `ModalNavigationDrawer` from any secondary destination (such as `WORKOUTS`, `HISTORY`, etc.) and pressed system Back (or performed the system back swipe gesture), the application navigated back to the main tracking screen (`NavRoutes.START_TRACKING`), but the drawer remained visible and stuck open over the tracking screen.

### Root Cause:
1. **LIFO Registration Mechanics**: Android's `OnBackPressedDispatcher` evaluates active callbacks in **LIFO order (most recently added first)**.
2. **Static Declaration Limitation**: In ATT-1327, `BackHandler(enabled = isDrawerVisible)` was declared statically at the root of `ATrainingTrackerApp`. The `NavHost` (`navigation-compose:2.8.8`) was composed deeper in the tree, registering its internal `PredictiveBackHandler` *after* the root back handler.
3. **`isEnabled` vs. Stack Reordering**: In Jetpack Compose's `BackHandler`, changing `enabled = true` merely updates `backCallback.isEnabled` via a `SideEffect`; it does **not** move the callback to the top of the dispatcher stack.
4. When `navController.previousBackStackEntry != null` was true (secondary screen), `NavHost`'s back handler was enabled and higher in the dispatcher's LIFO stack than the root `BackHandler`. When Back was pressed, `NavHost` intercepted it first, popping the screen destination while the drawer remained open.

---

## 2. Key Modifications

### 1. `ATrainingTrackerApp.kt`:
* **Comprehensive Drawer Visibility Predicate**:
  Expanded `isDrawerVisible` to account for mid-flight animation transitions and settling phases:
  ```kotlin
  val isDrawerVisible = drawerState.currentValue != DrawerValue.Closed ||
      drawerState.targetValue != DrawerValue.Closed ||
      drawerState.isAnimationRunning
  ```
* **Dynamic LIFO Composition for Drawer BackHandler (Layer 1)**:
  Moved the drawer's `BackHandler` inside `ModalNavigationDrawer`'s `drawerContent` and guarded it with conditional composition:
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
  When the drawer starts opening or becomes visible, `if (isDrawerVisible)` enters composition. Jetpack Compose's `BackHandler` executes `dispatcher.addCallback()`, appending the callback to the **top of the LIFO stack** (superseding `NavHost` and all child screen back handlers).
  When the drawer settles closed, the block leaves composition, invoking `remove()` via `DisposableEffect` and cleanly restoring `NavHost` backstack navigation.
* **Dynamic LIFO Composition for Settings Bottom Sheet (Layer 2)**:
  Similarly guarded Layer 2 with `if (drawerController.activeBottomSheet != null)` so bottom sheets dynamically take LIFO precedence when displayed.

### 2. `SingleActivityNavigationTest.kt`:
* **`testOnBackPressedDispatcherDynamicLIFOPrecedence()`**:
  Simulates `OnBackPressedDispatcher` with an active `NavHost` callback and dynamically mounts/unmounts the drawer callback. Validates:
  1. Drawer closed: Back pops destination (`navHostPopCount == 1`).
  2. Drawer opened: Back triggers drawer close (`drawerCloseCount == 1`), destination stays untouched (`navHostPopCount == 1`).
  3. Drawer closed again: Back pops destination again (`navHostPopCount == 2`).
* **`testIsDrawerVisibleComprehensivePredicate()`**:
  Validates all 5 states of drawer motion:
  1. Settled Closed (idle) -> `false`
  2. Opening transition (Closed -> Open, animating) -> `true`
  3. Settled Open (idle) -> `true`
  4. Closing transition (Open -> Closed, animating) -> `true`
  5. Post-target finalize phase (Closed -> Closed, animating) -> `true`

---

## 3. Verification Results

### Automated Unit Tests (`com.atrainingtracker.trainingtracker.ui.navigation.*`):
* `SingleActivityNavigationTest`: 14 passed, 0 failed, 0 skipped.
* **Result**: **PASS** (14/14 tests green).

### Clean-Room Full Suite Regression:
* `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL** (0 failures, 626+ tests passing across all modules).

---

## 4. Preserved Invariants & Boundary Safety

1. **System Invariants (INV-UI-01)**: All 21 navigation items in `AppNavigationDrawer.kt` remain unchanged.
2. **Edge-Swipe Gestures (INV-UI-02 / REQ-UI-161)**: Leftmost 40dp margin swipe bounds and `gesturesEnabled = drawerState.isOpen` are completely preserved.
3. **Drawer Item Selection (INV-UI-03)**: `drawerController.selectedItemId` continues to synchronize with active routes.
4. **Deadlock Prevention (INV-UI-04)**: Bounded by 400ms timeout with `snapTo(DrawerValue.Closed)` fallback inside coroutine scope.
5. **No Unhandled Coroutine Exceptions (INV-UI-05)**: Cooperative `CancellationException` handling on rapid consecutive taps.
