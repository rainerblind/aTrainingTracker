# Engineering Analysis - ATT-1310: ModalNavigationDrawer Opens on Right Swipe From Anywhere on Screen in Details and Map Views

**Ticket**: [ATT-1310](https://rainerblind.atlassian.net/browse/ATT-1310)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Component**: `com.atrainingtracker.trainingtracker.ui.navigation.ATrainingTrackerApp.kt`  
**Requirement Mapping**: `REQ-UI-161` (Proposed)  
**Test Spec Mapping**: `TST-UI-113` (Proposed)  

---

## 1. Executive Summary & Problem Statement

In the Jetpack Compose Single-Activity architecture (`ATT-1083`), navigating to screens that lack child horizontal gesture consumers (such as Workout Details, Segments Details, Routes Details, Locations, or the Map screen) exhibits an unintended navigation drawer behavior:
Performing a swipe or drag gesture to the right starting anywhere across the middle or right of the screen (e.g. $x > 40\text{dp}$, mid-screen, or near the right edge) inadvertently drags open the `ModalNavigationDrawer`.

This creates severe user interaction defects:
1. **Map Panning Disruption**: In `MapScreen` (and any Google Maps view), an athlete attempting to pan the map eastward (swiping fingers rightward) triggers the drawer opening instead of or concurrently with map camera movement.
2. **Details & Content Screen Usability**: On details screens, charts, or vertical lists, incidental horizontal drag gestures pull the navigation drawer across the content.
3. **Contrast with Legacy Android View Architecture**: In the legacy Android View hierarchy, `DrawerLayout` strictly used `ViewDragHelper.EDGE_LEFT`, opening the drawer only when touch gestures originated within the left edge margin (typically 20dp-40dp).

---

## 2. Forensic Root Cause Analysis (RCA)

### 2.1 Full-Screen Draggable Modifier in Material 3 `ModalNavigationDrawer`
In `ATrainingTrackerApp.kt:173-176`:
```kotlin
ModalNavigationDrawer(
    drawerState = drawerState,
    gesturesEnabled = true,
    drawerContent = { ... }
)
```
Material 3's `ModalNavigationDrawer` attaches an internal horizontal draggable modifier across its entire composable container whenever `gesturesEnabled = true`.
Because `gesturesEnabled` was set unconditionally to `true`:
- When the drawer is closed (`drawerState.isClosed`), the draggable modifier actively intercepts right-ward horizontal gestures across the full screen width and height.

### 2.2 Gesture Bubble-Up & Consumption Asymmetry Across Screens
- **Screens with `HorizontalPager`** (e.g., `TrackingTabsScreen`, `WorkoutTabsScreen`, `PeriodTabsScreen`):
  `HorizontalPager` consumes horizontal pointer drags during `PointerEventPass.Main`. As a result, horizontal swipes in the center of the screen switch tabs and do not bubble up to `ModalNavigationDrawer`. (The extreme left edge is captured by the custom edge interceptor at `PointerEventPass.Initial`).
- **Screens WITHOUT `HorizontalPager`** (e.g., `MapScreen`, `WorkoutDetailsScreen`, `SegmentDetailsScreen`, `RouteDetailsScreen`, `LocationsScreen`):
  The child composables (or Map view) do not consume horizontal drag events or let unconsumed horizontal deltas propagate. Consequently, touch events bubble up to the parent `ModalNavigationDrawer` container, which processes them as drawer-open drag events from anywhere on the display.

---

## 3. Preserved Invariants & Boundary Verification

1. **Edge-Only Drawer Opening**: When the drawer is closed, right-swipe gestures MUST ONLY open the drawer if initiated within the 40dp left edge margin (`down.position.x <= 40.dp`).
2. **Gesture Immunity for Child Views**: Mid-screen or right-screen gestures ($x > 40\text{dp}$) on the Map screen or Details screens MUST NEVER trigger the navigation drawer. Map panning must operate unhindered.
3. **Closing Gestures Parity**: When the drawer is open (`drawerState.isOpen`), all standard dismissal mechanisms MUST remain 100% operational:
   - Full-screen swiping left to close the drawer.
   - Tapping the background scrim to dismiss the drawer.
   - System/gesture Back press (`BackHandler`) to close the drawer.
4. **Direct Navigation Controls**: Opening the drawer via the top app bar hamburger icon and selecting items from `AppNavigationDrawer` MUST remain completely functional.
5. **Architectural Separation**: The fix MUST NOT require invasive gesture hacks or custom touch interceptors across individual screens (e.g. MapScreen or Details screens), preserving clean modular Compose architecture.

---

## 4. Proposed Solution & Architecture

### 4.1 Conditional Gesture Activation (`gesturesEnabled = drawerState.isOpen`)
In `ATrainingTrackerApp.kt:175`:
Change:
```kotlin
ModalNavigationDrawer(
    drawerState = drawerState,
    gesturesEnabled = drawerState.isOpen,
    drawerContent = { ... }
)
```

### 4.2 Mechanism Breakdown
- **When Drawer is Closed (`!drawerState.isOpen`)**:
  - `gesturesEnabled = false` deactivates Material 3's internal full-screen `draggable` modifier on `ModalNavigationDrawer`.
  - The drawer will completely ignore any horizontal drags occurring across the screen body ($x > 40\text{dp}$).
  - The existing dedicated edge-swipe interceptor on the inner `Box` (`ATrainingTrackerApp.kt:195-221`) remains the sole gesture mechanism for opening the drawer:
    ```kotlin
    val edgeThresholdPx = 40.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
        if (down.position.x <= edgeThresholdPx && !drawerState.isOpen) {
            ...
            drawerState.open()
        }
    }
    ```
    This guarantees that opening the drawer via touch is restricted strictly to the leftmost 40dp margin, replicating the legacy `ViewDragHelper.EDGE_LEFT` behavior.
- **When Drawer is Open (`drawerState.isOpen`)**:
  - `gesturesEnabled = true` activates `ModalNavigationDrawer`'s draggable modifier.
  - The user can drag left anywhere on the drawer sheet or scrim to close it, and tapping the scrim dismisses it immediately.

---

## 5. Verification Plan

1. **Unit Tests (`SingleActivityNavigationTest.kt`)**:
   - Verify `drawerState` closed vs. open gesture contract and state machine.
   - Verify edge threshold and route bindings.
2. **Clean-Room Regression Suite**:
   - Execute `./gradlew testDebugUnitTest` to guarantee 0 regressions across all modules.
3. **Manual / Physical Device Testing**:
   - On `MapScreen`, pan map in all directions (especially rightward); verify drawer never opens.
   - On `WorkoutDetailsScreen` and other details screens, swipe right across middle of screen; verify drawer never opens.
   - Swipe right starting from the leftmost edge ($\le 40\text{dp}$); verify drawer opens smoothly.
   - While drawer is open, swipe left anywhere or tap scrim; verify drawer closes smoothly.
