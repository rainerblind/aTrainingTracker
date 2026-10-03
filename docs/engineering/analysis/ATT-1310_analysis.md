# Engineering Analysis - ATT-1310: ModalNavigationDrawer Opens on Right Swipe From Anywhere on Screen in Details and Map Views

**Ticket**: [ATT-1310](https://rainerblind.atlassian.net/browse/ATT-1310)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Component**: `com.atrainingtracker.trainingtracker.ui.navigation.ATrainingTrackerApp.kt`  
**Parent Requirement**: `REQ-UI-159` (Single-Activity Architecture & Jetpack Compose Navigation Modernization)  
**Proposed Requirement**: `REQ-UI-161` (Restricted Edge-Swipe & Touch Conflict Resolution for ModalNavigationDrawer)  
**Proposed Test Spec**: `TST-UI-113` (ModalNavigationDrawer Edge-Swipe Restriction & Map Panning Test)  

---

## 1. Executive Summary & Problem Statement

In the Jetpack Compose Single-Activity architecture (`ATT-1083` / `REQ-UI-159`), navigating to screens that lack child horizontal gesture consumers (such as Workout Details, Segments Details, Routes Details, Locations, or the Map screen) exhibits an unintended navigation drawer behavior:
Performing a swipe or drag gesture to the right starting anywhere across the middle or right of the screen (e.g. $x > 40\text{dp}$, mid-screen, or near the right edge) inadvertently drags open the `ModalNavigationDrawer`.

This creates severe user interaction defects:
1. **Map Panning Disruption**: In `MapScreen` (and any Google Maps view), an athlete attempting to pan the map eastward (swiping fingers rightward) triggers the drawer opening instead of or concurrently with map camera movement.
2. **Details & Content Screen Usability**: On details screens, charts, or vertical lists, incidental horizontal drag gestures pull the navigation drawer across the content.
3. **Contrast with Legacy Android View Architecture**: In the legacy Android View hierarchy, `DrawerLayout` strictly used `ViewDragHelper.EDGE_LEFT`, opening the drawer only when touch gestures originated within the left edge margin (typically 20dp-40dp).

---

## 2. Requirement Traceability & ASPICE Mapping

* **Parent Requirement (`REQ-UI-159`)**: Mandated the modernization of `MainActivityWithNavigation` into a single-activity architecture with `ModalNavigationDrawer` and `NavHost`. Section 2 specifies the drawer implementation with Material 3.
* **Defect Identified**: Material 3's `ModalNavigationDrawer` lacks an `edgeWidth` configuration parameter (unlike legacy `DrawerLayout`). Setting `gesturesEnabled = true` attaches a full-screen draggable modifier that overrides child touch handlers when children do not consume horizontal drag events.
* **Proposed Target Requirement (`REQ-UI-161`)**:
  Formulates the exact behavioral constraint:
  > The system SHALL restrict navigation drawer swipe-to-open gestures exclusively to the leftmost 40dp margin (`x <= 40dp`) when the drawer is closed. The system MUST NOT intercept or consume horizontal drag gestures occurring outside this margin (`x > 40dp`), ensuring child views (including Google Maps panning and details views) operate unhindered. When the drawer is open, the system SHALL enable full-screen swipe-to-close, scrim tap dismissal, and system back navigation.
* **Proposed Verification (`TST-UI-113`)**:
  Automated unit test coverage in `SingleActivityNavigationTest.kt` verifying drawer gesture activation contracts, edge threshold invariants, and clean-room full regression pass.

---

## 3. Forensic Root Cause Analysis (RCA)

### 3.1 Full-Screen Draggable Modifier in Material 3 `ModalNavigationDrawer`
In `ATrainingTrackerApp.kt:173-176`:
```kotlin
ModalNavigationDrawer(
    drawerState = drawerState,
    gesturesEnabled = true,
    drawerContent = { ... }
)
```
Material 3's `ModalNavigationDrawer` API does not provide an `edgeWidth` parameter. When `gesturesEnabled = true`, it attaches an internal `draggable` modifier across the entire composable container.
Because `gesturesEnabled` was set unconditionally to `true`:
- When the drawer is closed (`drawerState.isClosed`), the draggable modifier actively intercepts right-ward horizontal gestures across the full screen width and height.

### 3.2 Gesture Bubble-Up & Consumption Asymmetry Across Screens
- **Screens with `HorizontalPager`** (e.g., `TrackingTabsScreen`, `WorkoutTabsScreen`, `PeriodTabsScreen`):
  `HorizontalPager` consumes horizontal pointer drags during `PointerEventPass.Main`. As a result, horizontal swipes in the center of the screen switch tabs and do not bubble up to `ModalNavigationDrawer`. (The extreme left edge is captured by the custom edge interceptor at `PointerEventPass.Initial`).
- **Screens WITHOUT `HorizontalPager`** (e.g., `MapScreen`, `WorkoutDetailsScreen`, `SegmentDetailsScreen`, `RouteDetailsScreen`, `LocationsScreen`):
  The child composables (or Map view) do not consume horizontal drag events or let unconsumed horizontal deltas propagate. Consequently, touch events bubble up to the parent `ModalNavigationDrawer` container, which processes them as drawer-open drag events from anywhere on the display.

---

## 4. Preserved Invariants & Boundary Verification

1. **Edge-Only Drawer Opening**: When the drawer is closed, right-swipe gestures MUST ONLY open the drawer if initiated within the 40dp left edge margin (`down.position.x <= 40.dp`).
2. **Gesture Immunity for Child Views**: Mid-screen or right-screen gestures ($x > 40\text{dp}$) on the Map screen or Details screens MUST NEVER trigger the navigation drawer. Map panning must operate unhindered.
3. **Closing Gestures Parity**: When the drawer is open (`drawerState.isOpen`), all standard dismissal mechanisms MUST remain 100% operational:
   - Full-screen swiping left to close the drawer.
   - Tapping the background scrim to dismiss the drawer.
   - System/gesture Back press (`BackHandler`) to close the drawer.
4. **Direct Navigation Controls**: Opening the drawer via the top app bar hamburger icon and selecting items from `AppNavigationDrawer` MUST remain completely functional.
5. **Architectural Separation**: The fix MUST NOT require invasive gesture hacks or custom touch interceptors across individual screens, preserving clean modular Compose architecture.

---

## 5. Architectural Evaluation & Mid-Gesture Transition Analysis

### 5.1 Evaluation of Technical Alternatives
* **Alternative A: Consume Non-Edge Drags in Parent Wrapper Interceptor**
  - *Concept*: Add a `pointerInput` on the root Box that consumes horizontal drag deltas whenever $x > 40\text{dp}$ and `drawerState.isClosed`.
  - *Fatal Flaw*: Consuming pointer events in a parent interceptor at `PointerEventPass.Initial` permanently consumes the touches before child composables receive them. This would completely paralyze map panning and child scrolling, replacing one defect with an even worse regression!
* **Alternative B: Conditional `gesturesEnabled = drawerState.isOpen` (Recommended)**
  - *Concept*: Set `gesturesEnabled = drawerState.isOpen` on `ModalNavigationDrawer`.
  - *Behavioral Flow*:
    1. **Drawer Closed (`!drawerState.isOpen`)**:
       - `gesturesEnabled = false`. Material 3's full-screen draggable modifier is completely detached/deactivated.
       - Child composables (such as Google Map) receive all touch events across $x > 40\text{dp}$ without any interference from the parent drawer.
       - The edge interceptor on the inner `Box` (`ATrainingTrackerApp.kt:195-221`) intercepts touches starting at $x \le 40\text{dp}$ on `PointerEventPass.Initial`.
       - When the user drags rightward past touch slop, the edge interceptor calls `scope.launch { drawerState.open() }` and consumes the gesture stream until pointer release (`while (change.pressed)`).
       - Because the edge interceptor directly animates `drawerState.open()` and consumes the event stream, there is NO reliance on `ModalNavigationDrawer`'s internal draggable to complete the opening animation.
    2. **Drawer Open (`drawerState.isOpen`)**:
       - `gesturesEnabled = true`. Material 3's full-screen draggable modifier is active.
       - Athletes can swipe left anywhere on the drawer sheet or scrim to close the drawer.
       - Tapping the scrim dismisses the drawer cleanly via Material 3's built-in scrim layer.

### 5.2 Mid-Gesture State Transition Validation
* *Hazard Analyzed*: Does flipping `gesturesEnabled` mid-gesture break continuous opening?
* *Findings*:
  When a user starts an edge-swipe from $x \le 40\text{dp}$, the edge interceptor consumes pointer events on `PointerEventPass.Initial` and starts `drawerState.open()`.
  While the finger remains pressed (`while (change.pressed)`), the edge interceptor continues to consume every pointer event.
  The drawer open animation runs via Compose animation coroutine (`drawerState.open()`).
  Even after `drawerState.currentValue` flips to `DrawerValue.Open` (and `gesturesEnabled` becomes `true`), the active pointer stream is already being consumed by the edge interceptor until the user lifts their finger.
  Therefore, no touch pointer cancellation or dropped-frame glitch occurs.

---

## 6. Verification & Test Plan

1. **Automated Unit Testing (`SingleActivityNavigationTest.kt`)**:
   - Add unit test validating the navigation drawer gesture contract:
     - `drawerState` closed implies `gesturesEnabled == false`.
     - `drawerState` open implies `gesturesEnabled == true`.
     - Edge threshold invariant (`40.dp`) strictly bounded.
2. **Full Repository Clean-Room Regression**:
   - Run `./gradlew testDebugUnitTest` to guarantee 0 regressions across all 624+ unit tests.
3. **Physical / Manual Device Testing Checklist**:
   - [ ] Navigate to `MapScreen`. Pan the map eastward, westward, northward, and southward across the middle of the screen. Confirm drawer remains closed and map pans smoothly.
   - [ ] Navigate to `WorkoutDetailsScreen` (or Segments/Routes Details). Swipe right from $x > 40\text{dp}$. Confirm drawer remains closed.
   - [ ] Swipe right from the left screen edge ($x \le 40\text{dp}$). Confirm drawer opens smoothly.
   - [ ] Tap the top app bar hamburger icon. Confirm drawer opens smoothly.
   - [ ] While drawer is open, swipe left anywhere across the drawer or scrim. Confirm drawer closes smoothly.
   - [ ] While drawer is open, tap the dimmed scrim. Confirm drawer dismisses immediately.
   - [ ] While drawer is open, press the system back button. Confirm drawer closes.
