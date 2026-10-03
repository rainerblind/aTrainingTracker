# Engineering Walkthrough - ATT-1310: ModalNavigationDrawer Edge-Swipe Restriction & Touch Conflict Resolution

**Ticket**: [ATT-1310](https://rainerblind.atlassian.net/browse/ATT-1310)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Components**: 
* `com.atrainingtracker.trainingtracker.ui.navigation.ATrainingTrackerApp.kt`
* `com.atrainingtracker.trainingtracker.ui.navigation.SingleActivityNavigationTest.kt`
**Requirement**: `REQ-UI-161`  
**Test Spec**: `TST-UI-113`  
**Branch**: `bugfix/ATT-1310`  

---

## 1. Changes Overview

This change resolves the issue where swiping right from anywhere across the middle or right of the screen on screens without horizontal pagers (such as `MapScreen`, `WorkoutDetailsScreen`, `SegmentDetailsScreen`, `RouteDetailsScreen`, and `LocationsScreen`) inadvertently opened the `ModalNavigationDrawer`.

### Key Modifications:
1. **`ATrainingTrackerApp.kt`**:
   * **Conditional Gesture Configuration**: Changed `ModalNavigationDrawer(drawerState = drawerState, gesturesEnabled = true)` to:
     ```kotlin
     gesturesEnabled = drawerState.isOpen
     ```
   * **Closed-State Drag Immunity**: When `!drawerState.isOpen`, Material 3's internal full-screen `draggable` modifier is detached. Map panning on `MapScreen` and scroll/chart gestures across details screens operate completely unhindered at $x > 40\text{dp}$.
   * **Edge-Only Opening Interceptor**: Opening the drawer via touch is restricted strictly to the leftmost 40dp margin (`down.position.x <= 40.dp`) via the dedicated `pointerInput` interceptor on the root container.
   * **Open-State Dismissal Parity**: When `drawerState.isOpen == true`, `gesturesEnabled = true` enables full-screen swipe-to-close, scrim-tap dismissal, and system back navigation.
2. **`SingleActivityNavigationTest.kt`**:
   * Added `testDrawerGesturesEnabledMapping()`: Validates that `gesturesEnabled` evaluates to `false` when drawer is closed and `true` when drawer is open.
   * Added `testEdgeSwipeThresholdBounds()`: Validates that touches within $\le 40\text{dp}$ trigger the edge interceptor, while touches at $x > 40\text{dp}$ (e.g. 40.1dp, 100dp, 200dp, 360dp) are ignored by the drawer to protect child views.

---

## 2. Verification Results

### Automated Unit Tests (`com.atrainingtracker.trainingtracker.ui.navigation.*`):
* `SingleActivityNavigationTest`: 8 passed, 0 failed, 0 skipped.
* **Result**: **PASS** (8/8 tests green, 0 failures).

---

## 3. Git Commits & Diffs

* Commits:
  * `docs(navigation): add engineering analysis for ATT-1310`
  * `docs(navigation): revise engineering analysis per Agent 2 feedback (ATT-1310)`
  * `docs(navigation): specify REQ-UI-161 and TST-UI-113 for drawer edge-swipe (ATT-1310)`
  * `docs(navigation): create implementation plan for ATT-1310 (REQ-UI-161, TST-UI-113)`
  * `fix(navigation): restrict drawer open gesture to edge and protect map panning (ATT-1310)`
