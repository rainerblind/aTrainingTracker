# Test Specification: ModalNavigationDrawer Back Navigation Isolation & Single Source of Truth Synchronization (ATT-1327)

## 1. Traceability & Requirements Mapping

* **Ticket Key**: `ATT-1327`
* **Sub-Task Key**: `ATT-1331` (`[Test-Spec]`)
* **Target Version**: `V4.9.38`
* **Target Branch**: `bugfix/ATT-1327`
* **Parent Requirement**: `REQ-UI-159` (Single-Activity Architecture & Jetpack Compose Navigation Modernization)
* **Mapped Requirement**: `REQ-UI-162` (Single Source of Truth & Scoped Overlay Back Navigation, documented in `docs/requirements.md`)
* **Verification Test ID**: `TST-UI-114` (ModalNavigationDrawer Back Navigation Isolation & State Synchronization Test, documented in `docs/tests.md`)

---

## 2. Requirement Specification (REQ-UI-162)

### Title
**Single Source of Truth & Scoped Overlay Back Navigation**

### Functional Specification
The system SHALL establish Material 3's `DrawerState` as the single authoritative source of truth for navigation drawer visibility and navigation control, eliminating decoupled boolean state flags, and SHALL isolate overlay back navigation (Navigation Drawer and Settings Bottom Sheets) from screen-level destination back navigation using scoped, conditionally enabled `BackHandler`s:

1. **Single Source of Truth**:
   `NavigationDrawerController` SHALL delegate drawer open and close actions directly to Material 3's `DrawerState` via functional action delegates, removing the decoupled mutable boolean state flag (`isDrawerOpen`) entirely and eliminating split-brain asynchronous synchronization.
2. **Scoped Overlay Back Navigation**:
   When the navigation drawer is open, opening, or closing (`drawerState.currentValue != DrawerValue.Closed || drawerState.targetValue != DrawerValue.Closed`), system back navigation (`BackHandler`) SHALL exclusively dismiss/close the navigation drawer (`scope.launch { drawerState.close() }`), without modifying the active Composable destination or backstack.
3. **Settings Bottom Sheet Isolation**:
   When an active bottom sheet is displayed (`drawerController.activeBottomSheet != null`), system back navigation SHALL exclusively dismiss the active sheet (`drawerController.activeBottomSheet = null`).
4. **Screen-Level Back Navigation Guard**:
   Screen-level destination back navigation (popping `NavHost` backstack, clearing active filter criteria, or routing to `NavRoutes.START_TRACKING`) SHALL be guarded by `enabled = !isDrawerVisible && drawerController.activeBottomSheet == null`, ensuring screen destinations NEVER change while the drawer or bottom sheets are visible or settling.
5. **Deterministic Interruption & Deadlock Prevention (INV-UI-04)**:
   If back navigation is invoked mid-animation during a drawer opening or closing transition, the system SHALL catch `CancellationException` without fault. To prevent UI deadlocks if an animation hangs due to main-thread starvation, drawer closure SHALL be bounded by a 400ms timeout (`withTimeoutOrNull(400L)`) with a deterministic `snapTo(DrawerValue.Closed)` fallback executed within the Compose coroutine scope, guaranteeing Layer 3 screen navigation is restored.

### Acceptance Criteria (Given-When-Then)
* **AC-1 (Drawer Overlay Isolation on Back Press)**:
  * *Given* the athlete is on any destination (e.g. Workouts, Routes, Periods, Map, Sensors) with the navigation drawer open or opening,
  * *When* the athlete invokes system back navigation (hardware button or back gesture),
  * *Then* the navigation drawer SHALL close smoothly and the active screen destination SHALL remain completely unchanged.
* **AC-2 (Screen Navigation with Drawer Closed)**:
  * *Given* the athlete is on any non-tracking destination with the navigation drawer closed and no overlays active,
  * *When* the athlete invokes system back navigation,
  * *Then* the app SHALL navigate to the main tracking control (`NavRoutes.START_TRACKING`) and clear active filter criteria.
* **AC-3 (Root Exit on Main Tracking Screen)**:
  * *Given* the athlete is on `START_TRACKING` with no overlays and no backstack,
  * *When* back is pressed,
  * *Then* the activity SHALL finish cleanly.
* **AC-4 (Rapid Double-Back Press Interruption Resilience)**:
  * *Given* the navigation drawer is transitioning or opening,
  * *When* the athlete inputs rapid consecutive back presses (e.g., 50ms–150ms interval),
  * *Then* Layer 1 SHALL absorb all back presses until the drawer settles into `Closed`, preventing premature fall-through to screen routing jumps, and coroutine cancellation SHALL be handled cooperatively without exceptions.
* **AC-5 (Deadlock Safety Timeout & Starvation Fallback - INV-UI-04)**:
  * *Given* an animation delay or frame freeze exceeding 400ms during drawer dismissal,
  * *When* the 400ms safety timeout expires,
  * *Then* `snapTo(DrawerValue.Closed)` SHALL immediately settle the drawer to closed, deactivating Layer 1 and unblocking Layer 3 screen navigation.

---

## 3. Detailed Verification Specification (TST-UI-114)

### 3.1 Test Architecture & Scope
* **Target Test File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/SingleActivityNavigationTest.kt`
* **Test Framework**: JUnit 4 / AndroidX Test / Robolectric
* **Verification Scope**: SWE.4 Unit Verification & SWE.5 System Regression

### 3.2 Step-by-Step Test Procedures

#### Procedure 1: `NavigationDrawerController` Single Source of Truth & Action Delegation
1. Instantiate `NavigationDrawerController`.
2. Verify backward-compatible default fallback (`isDrawerOpen == false`) prior to binding without throwing NPE.
3. Bind mock functional delegates via `bindDrawer(open = { openCount++ }, close = { closeCount++ }, isOpen = { mockIsOpen })`.
4. Trigger `controller.openDrawer()` -> assert `openCount == 1`.
5. Trigger `controller.closeDrawer()` -> assert `closeCount == 1`.
6. Toggle `mockIsOpen` -> assert `controller.isDrawerOpen` strictly reflects provider state.
7. Invoke `unbindDrawer()` -> assert delegates safely detached.

#### Procedure 2: Scoped `BackHandler` Visibility Predicates & Layer Isolation
1. Evaluate visibility predicate:
   `val isDrawerVisible = drawerState.currentValue != DrawerValue.Closed || drawerState.targetValue != DrawerValue.Closed`
2. Test State 1 (`Closed`, `Closed`):
   - Assert `isDrawerVisible == false`.
   - Assert Layer 1 `BackHandler(enabled = isDrawerVisible)` is **disabled**.
   - Assert Layer 3 `BackHandler(enabled = !isDrawerVisible && ...)` is **enabled**.
3. Test State 2 (`Closed`, `Open` - Animating Open):
   - Assert `isDrawerVisible == true`.
   - Assert Layer 1 is **enabled**; Layer 3 is **disabled**.
4. Test State 3 (`Open`, `Open` - Fully Open):
   - Assert `isDrawerVisible == true`.
   - Assert Layer 1 is **enabled**; Layer 3 is **disabled**.
5. Test State 4 (`Open`, `Closed` - Actively Dismissing):
   - Assert `isDrawerVisible == true`.
   - Assert Layer 1 is **enabled**; Layer 3 is **disabled**, preventing premature screen jumps while settling.

#### Procedure 2.1: 4-State Matrix Verification
| Permutation | `currentValue` | `targetValue` | Physical State | `isDrawerVisible` | Layer 1 Enabled? | Layer 3 Enabled? | Behavior on Back Press |
|:---|:---|:---|:---|:---|:---|:---|:---|
| **State 1** | `Closed` | `Closed` | Fully Closed | `false` | Disabled | **Enabled** | Screen navigation operates normally (pops backstack, navigates to `NavRoutes.START_TRACKING`, or finishes activity). |
| **State 2** | `Closed` | `Open` | Animating Open / Swiping Open | `true` | **Enabled** | Disabled | Layer 1 intercepts back, calls safe closure, cancels opening animation and reverses drawer to `Closed`. Zero screen navigation occurs. |
| **State 3** | `Open` | `Open` | Fully Open | `true` | **Enabled** | Disabled | Layer 1 intercepts back, calls safe closure. Drawer smoothly closes. Zero screen navigation occurs. |
| **State 4** | `Open` | `Closed` | Actively Dismissing / Closing | `true` | **Enabled** | Disabled | Layer 1 intercepts back, re-asserts safe closure idempotently in Compose M3 without gesture fault. Layer 3 remains strictly disabled until drawer completely settles into State 1. |

#### Procedure 3: Rapid Double-Tap Back Press & Cancellation Resilience
1. In State 2 or State 3, trigger back press callback.
2. In quick succession (simulating 50ms interval), trigger second back press callback while first coroutine is mid-flight.
3. Verify that coroutine cancellation throws `CancellationException`, which is cooperatively caught without crashing.
4. Verify `isDrawerVisible` remains `true` throughout the transition, ensuring Layer 3 is NEVER invoked.

#### Procedure 4: Deadlock Safety Timeout (400ms) & `snapTo` Fallback (INV-UI-04)
1. Simulate a delayed drawer close animation exceeding 400ms (e.g. mock coroutine or delayed block).
2. Execute the safe closure routine:
   ```kotlin
   try {
       withTimeoutOrNull(400L) { drawerState.close() } ?: drawerState.snapTo(DrawerValue.Closed)
   } catch (_: CancellationException) {
       // Cooperative cancellation
   }
   ```
3. Assert that after timeout expires, `snapTo(DrawerValue.Closed)` executes and the drawer state settles to `Closed`.
4. Assert that `isDrawerVisible` transitions to `false`, immediately unblocking Layer 3 screen back navigation.

#### Procedure 5: Clean-Room Full Suite Regression
1. Execute `./gradlew testDebugUnitTest`.
2. Confirm 100% test pass (626+ tests passing, 0 failures, 0 regressions).

#### Procedure 6: Physical Device End-to-End Verification Protocol
1. Launch app on physical device running Android 14/15.
2. Navigate to `Workouts` screen.
3. Open navigation drawer (via hamburger button or leftmost edge swipe).
4. Tap Android system Back button / swipe back gesture:
   - **Verification**: Navigation drawer dismisses smoothly to the left.
   - **Verification**: Active screen remains on `Workouts`; does NOT navigate to `START_TRACKING`.
5. Tap Back button again with navigation drawer closed:
   - **Verification**: App navigates back to main tracking screen (`START_TRACKING`).
6. Tap Back button on `START_TRACKING`:
   - **Verification**: Activity finishes / exits cleanly.
7. From `Routes` screen, open drawer and rapidly double-tap Back:
   - **Verification**: Drawer dismisses cleanly without glitching, crashing, or double-jumping routes.

---

## 4. Invariant Preservation & Boundary Analysis

| Invariant ID | Target Property | Preservation Mechanism | Verification Method |
|:---|:---|:---|:---|
| **INV-UI-01** | 21 Navigation Drawer Destinations | All 21 navigation items in `AppNavigationDrawer.kt` remain unchanged. | Unit test in `SingleActivityNavigationTest.kt` |
| **INV-UI-02** | Left-Edge Swipe Bounds (`REQ-UI-161`) | Gestures restricted to `x <= 40.dp` when closed; `gesturesEnabled = drawerState.isOpen` preserved. | Unit test & manual swipe verification |
| **INV-UI-03** | Selected Drawer Item Synchronization | `drawerController.selectedItemId` continues to reflect current route. | Navigation state assertions |
| **INV-UI-04** | Deadlock Prevention on Back Dispatch | Bounded by 400ms timeout with `snapTo(DrawerValue.Closed)` fallback. | Procedure 4 timeout test |
| **INV-UI-05** | No Unhandled Coroutine Exceptions | Cooperative `CancellationException` handling on rapid consecutive back taps. | Procedure 3 rapid-tap test |
