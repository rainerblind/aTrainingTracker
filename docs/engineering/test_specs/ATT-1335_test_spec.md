# Test Specification - ATT-1335: ModalNavigationDrawer Back Navigation Dynamic LIFO Precedence & NavHost Isolation

**Ticket**: [ATT-1335](https://rainerblind.atlassian.net/browse/ATT-1335)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Component**: `com.atrainingtracker.trainingtracker.ui.navigation.ATrainingTrackerApp.kt`  
**Parent Requirement**: `REQ-UI-159` (Single-Activity Architecture & Jetpack Compose Navigation Modernization)  
**Committed Target Requirement**: `REQ-UI-162` (Single Source of Truth & Scoped Overlay Back Navigation) in `docs/requirements.md`  
**Committed Test Spec**: `TST-UI-114` (ModalNavigationDrawer Back Navigation Isolation & State Synchronization Test) in `docs/tests.md`  

---

## 1. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Method | Target File(s) | Status |
|:---|:---|:---|:---|:---|
| **REQ-UI-162** | **TST-UI-114** | Automated Unit Tests (`SingleActivityNavigationTest.kt`) & Physical Device Verification | `ATrainingTrackerApp.kt` | Draft |

---

## 2. Updated Requirement Specification (`REQ-UI-162`)

### Textual Specification:
The system SHALL establish Material 3's `DrawerState` as the single authoritative source of truth for navigation drawer visibility and navigation control, eliminating decoupled boolean state flags, and SHALL isolate overlay back navigation (Navigation Drawer and Settings Bottom Sheets) from screen-level destination back navigation using scoped, conditionally composed `BackHandler`s:
1. *Single Source of Truth*: `NavigationDrawerController` SHALL delegate drawer open and close actions directly to Material 3's `DrawerState` via functional action delegates, removing the decoupled mutable boolean state flag (`isDrawerOpen`) entirely and eliminating split-brain asynchronous synchronization.
2. *Scoped Overlay Back Navigation & Dynamic LIFO Precedence*: Overlay back handlers for `ModalNavigationDrawer` and Settings Bottom Sheets SHALL be conditionally composed (`if (isDrawerVisible) { BackHandler { ... } }` and `if (activeBottomSheet != null) { BackHandler { ... } }`). Conditionally composing `BackHandler` dynamically appends its callback to the head of the `OnBackPressedDispatcher` LIFO evaluation stack whenever the overlay is presented, guaranteeing that system Back exclusively dismisses the active overlay and strictly supersedes `NavHost`'s internal backstack popping and destination screen back handlers across all animated phases (`currentValue != Closed || targetValue != Closed || isAnimationRunning`). Upon settling to closed, the handler leaves composition and is cleanly unmounted, fully restoring standard screen back navigation.
3. *Settings Bottom Sheet Isolation*: When an active bottom sheet is displayed (`drawerController.activeBottomSheet != null`), system back navigation SHALL exclusively dismiss the active sheet (`drawerController.activeBottomSheet = null`).
4. *Screen-Level Back Navigation Guard*: Screen-level destination back navigation (popping `NavHost` backstack, clearing active filter criteria, or routing to `NavRoutes.START_TRACKING`) SHALL be guarded by `enabled = !isDrawerVisible && drawerController.activeBottomSheet == null`, ensuring screen destinations NEVER change while the drawer or bottom sheets are visible or settling.
5. *Deterministic Interruption & Deadlock Prevention (INV-UI-04)*: If back navigation is invoked mid-animation during a drawer opening or closing transition, the system SHALL catch `CancellationException` without fault. To prevent UI deadlocks if an animation hangs due to main-thread starvation, drawer closure SHALL be bounded by a 400ms timeout (`withTimeoutOrNull(400L)`) with a deterministic `snapTo(DrawerValue.Closed)` fallback executed within the Compose coroutine scope, guaranteeing Layer 3 screen navigation is restored.

### Acceptance Criteria (Given-When-Then):
* **Scenario A (Drawer Open over Destination Screen)**:
  * *Given* the athlete is on any navigated destination (e.g. `Workouts`, `Routes`, `Periods`, `Map`, `Sensors`) where `navController.previousBackStackEntry != null`, with the navigation drawer open or opening,
  * *When* the athlete invokes system back navigation (hardware button or back gesture),
  * *Then* the navigation drawer SHALL close smoothly and the active screen destination SHALL remain completely unchanged (does NOT navigate to tracking).
* **Scenario B (Drawer Open over Start Tracking)**:
  * *Given* the athlete is on `START_TRACKING` with the navigation drawer open,
  * *When* the athlete invokes system back navigation,
  * *Then* the navigation drawer SHALL close smoothly and the app SHALL remain on `START_TRACKING`.
* **Scenario C (Drawer Closed on Destination Screen)**:
  * *Given* the athlete is on any non-tracking destination with the navigation drawer closed and no overlays active,
  * *When* the athlete invokes system back navigation,
  * *Then* `NavHost` SHALL pop destination back to `NavRoutes.START_TRACKING`.
* **Scenario D (Rapid Consecutive Back Taps)**:
  * *Given* the navigation drawer is opening or closing,
  * *When* the athlete performs rapid consecutive back presses,
  * *Then* `CancellationException` SHALL be handled without fault, the drawer SHALL settle to `Closed`, and screen destinations SHALL NOT change.
* **Scenario E (Timeout Fallback)**:
  * *Given* an animation delay exceeding 400ms during drawer dismissal,
  * *When* the timeout expires,
  * *Then* `snapTo(DrawerValue.Closed)` SHALL immediately settle the drawer to closed and unblock screen back navigation.

---

## 3. Automated Test Cases in `SingleActivityNavigationTest.kt` (`TST-UI-114`)

1. **`testOnBackPressedDispatcherDynamicLIFOPrecedence`**:
   * Instantiate an `OnBackPressedDispatcher`.
   * Register a simulated `NavHost` callback (`isEnabled = true`) simulating an active destination on top of the backstack.
   * Verify that with drawer closed (no drawer callback attached), `dispatcher.onBackPressed()` triggers `NavHost` pop action.
   * Simulate drawer open: attach the conditional drawer `OnBackPressedCallback`.
   * Verify that with both callbacks enabled, `dispatcher.onBackPressed()` evaluates in LIFO order and executes the drawer callback ONLY; `NavHost` callback invocation count remains unchanged.
   * Simulate drawer settled to closed: remove the drawer callback (simulating `onDispose`).
   * Verify that invoking `dispatcher.onBackPressed()` once more executes the `NavHost` pop action.

2. **`testIsDrawerVisibleComprehensivePredicate`**:
   * Verify predicate under all permutations:
     * `currentValue == Closed, targetValue == Closed, isAnimationRunning == false` -> `isDrawerVisible == false`.
     * `currentValue == Closed, targetValue == Open, isAnimationRunning == true` -> `isDrawerVisible == true`.
     * `currentValue == Open, targetValue == Open, isAnimationRunning == false` -> `isDrawerVisible == true`.
     * `currentValue == Open, targetValue == Closed, isAnimationRunning == true` -> `isDrawerVisible == true`.
     * `currentValue == Closed, targetValue == Closed, isAnimationRunning == true` -> `isDrawerVisible == true`.

3. **`testSingleSourceOfTruthStateBinding`**:
   * Verify `NavigationDrawerController` properly delegates `openDrawer()`, `closeDrawer()`, and `isDrawerOpen` when bound to `DrawerState`.

4. **`testCancellationExceptionAndTimeoutDeadlockSafety`**:
   * Verify rapid consecutive cancellation handling and 400ms deadlock safety timeout.

---

## 4. Physical Device Verification Protocol

1. Launch application on physical device.
2. From the drawer, navigate to `Workouts`.
3. Open the navigation drawer (via leftmost edge-swipe or top hamburger menu).
4. Tap the Android system Back button or perform the system back gesture.
5. **VERIFY**: The navigation drawer closes smoothly. The active screen remains on `Workouts` (does NOT navigate to tracking).
6. Tap system Back again with the drawer closed.
7. **VERIFY**: The app navigates back to `START_TRACKING`.
