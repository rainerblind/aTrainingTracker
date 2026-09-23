# Engineering Implementation Plan - ATT-1310: ModalNavigationDrawer Edge-Swipe Restriction & Touch Conflict Resolution

**Ticket**: [ATT-1310](https://rainerblind.atlassian.net/browse/ATT-1310)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Components**: 
* `com.atrainingtracker.trainingtracker.ui.navigation.ATrainingTrackerApp.kt`
* `com.atrainingtracker.trainingtracker.ui.navigation.SingleActivityNavigationTest.kt`
**Requirement Mapping**: `REQ-UI-161` (`docs/requirements.md`)  
**Test Spec Mapping**: `TST-UI-113` (`docs/tests.md`)  
**Branch**: `bugfix/ATT-1310`  

---

## 1. Executive Summary & Objective

In the Jetpack Compose Single-Activity architecture (`ATT-1083` / `REQ-UI-159`), screens without child horizontal drag consumers (such as Workout Details, Segments Details, Routes Details, Locations, or the Map screen) currently allow swiping right from anywhere across the middle or right of the screen to open the `ModalNavigationDrawer`. This severely disrupts map panning (swiping eastward opens the drawer instead of panning) and introduces accidental drawer openings across details views.

The root cause is Material 3's `ModalNavigationDrawer`, which lacks an `edgeWidth` parameter and attaches an unconditional full-screen horizontal `draggable` modifier when `gesturesEnabled = true`.

The objective of this implementation is to:
1. Configure `gesturesEnabled = drawerState.isOpen` on `ModalNavigationDrawer` in `ATrainingTrackerApp.kt`.
2. Restrict touch-based drawer opening exclusively to the leftmost 40dp margin (`down.position.x <= 40.dp`) via the existing dedicated edge-swipe interceptor.
3. Ensure child composables (specifically Google Maps panning on `MapScreen` and scrollables in details screens) receive raw pointer gestures unhindered at $x > 40\text{dp}$.
4. Retain 100% full-screen swipe-to-close, scrim-tap dismissal, and system back-press closing when `drawerState.isOpen == true`.
5. Add automated unit test verification in `SingleActivityNavigationTest.kt` satisfying `TST-UI-113`.

---

## 2. Requirements Traceability Matrix

| Requirement Clause | Architecture / Component | Implementation Detail | Test Verification (`TST-UI-113`) |
| :--- | :--- | :--- | :--- |
| **`REQ-UI-161.1`** (Closed-State Gesture Restriction) | `ATrainingTrackerApp.kt` | Set `gesturesEnabled = drawerState.isOpen` on `ModalNavigationDrawer`. When closed, full-screen draggable modifier is detached. | `SingleActivityNavigationTest.testDrawerGesturesEnabledMapping` |
| **`REQ-UI-161.2`** (Edge-Only Opening Interceptor) | `ATrainingTrackerApp.kt` | Root `Box` pointerInput interceptor captures `down.position.x <= 40.dp` on `PointerEventPass.Initial` and animates `drawerState.open()`. | `SingleActivityNavigationTest.testEdgeSwipeThresholdBounds` |
| **`REQ-UI-161.3`** (Child View Drag Immunity) | `ATrainingTrackerApp.kt`, `MapScreen.kt` | Drag gestures at $x > 40\text{dp}$ are completely ignored by the drawer container, enabling unhindered map panning. | Physical verification on Pixel 10 & unit tests |
| **`REQ-UI-161.4`** (Open-State Dismissal Parity) | `ATrainingTrackerApp.kt` | When `drawerState.isOpen == true`, `gesturesEnabled = true` enables full-screen swipe left to close and scrim tap dismissal. | `SingleActivityNavigationTest.testDrawerGesturesEnabledMapping` & physical testing |
| **`REQ-UI-161.5`** (Direct Controls & Invariants) | `ATrainingTrackerApp.kt`, `AppNavigationDrawer.kt` | Top app bar hamburger icon, `drawerController.selectedItemId`, and all 21 drawer destinations remain intact. | `SingleActivityNavigationTest.testDrawerItemStructureIntegrity` |

---

## 3. Step-by-Step Implementation Changes

### 3.1 Component 1: `ATrainingTrackerApp.kt`

In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt`:
Update line 175:
```kotlin
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                windowInsets = WindowInsets.statusBars
            ) {
                AppNavigationDrawer(
                    selectedItemId = drawerController.selectedItemId,
                    startTrackingTitleRes = drawerController.startTrackingTitleRes,
                    onItemSelected = { itemId ->
                        scope.launch { drawerState.close() }
                        activity.navigateToDrawerItem(itemId)
                    }
                )
            }
        },
        modifier = modifier
    ) {
```

### 3.2 Component 2: `SingleActivityNavigationTest.kt`

In `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/SingleActivityNavigationTest.kt`:
Add test methods verifying the drawer gesture activation contracts:
1. `testDrawerGesturesEnabledMapping()`:
   Verifies that the conditional logic `drawerState.isOpen` correctly evaluates to `false` when drawer is closed and `true` when drawer is open.
2. `testEdgeSwipeThresholdBounds()`:
   Asserts that the edge threshold invariant is bounded to `40.dp` (preventing accidental regressions or enlargement that would re-introduce map panning conflicts).
3. `testEdgeSwipeGesturePredicate()`:
   Validates the boundary predicate logic:
   - $x = 0\text{dp}$ -> Edge swipe valid
   - $x = 20\text{dp}$ -> Edge swipe valid
   - $x = 40\text{dp}$ -> Edge swipe valid (boundary)
   - $x = 41\text{dp}$ -> Edge swipe invalid (child touch preserved)
   - $x = 200\text{dp}$ -> Edge swipe invalid (map panning preserved)

---

## 4. Preserved Invariants & Boundary Safety

1. **Map Panning Safety**: On `MapScreen`, gestures starting at $x > 40\text{dp}$ will never be intercepted or consumed by `ModalNavigationDrawer`. Map panning eastward, westward, northward, and southward operates with 100% responsiveness.
2. **Details Screens Scrolling**: Charts, elevation profiles, and lists on `WorkoutDetailsScreen`, `SegmentDetailsScreen`, and `RouteDetailsScreen` will not inadvertently pull open the drawer.
3. **Closing Gestures**: Tapping the dimmed scrim, swiping left across the drawer, or pressing system back (`BackHandler`) all reliably close the drawer.
4. **Edge Swiping**: Swiping right from the extreme left margin ($x \le 40\text{dp}$) remains fully functional across all 21 destinations.
5. **No Screen-Specific Touch Hacks**: Resolves the conflict cleanly at the root navigation container without scattering ad-hoc gesture modifiers across individual screens.

---

## 5. Verification & Test Execution Plan

### 5.1 Automated Unit Tests (SWE.4)
Run targeted unit tests:
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.SingleActivityNavigationTest"
```

### 5.2 Clean-Room Full Regression (SWE.5)
Run full regression suite:
```bash
./gradlew testDebugUnitTest
```
Confirm 100% test pass (624+ tests passing, 0 failures, 0 errors).

### 5.3 Physical Device Verification Checklist (Manual Gate)
- Deploy debug build to physical device.
- Navigate to `MapScreen` -> Swipe right from center of map -> Verify map pans, drawer does not open.
- Navigate to `WorkoutDetailsScreen` -> Swipe right across chart/content -> Verify drawer does not open.
- Swipe right from leftmost edge ($x \le 40\text{dp}$) -> Verify drawer opens smoothly.
- Tap hamburger icon -> Verify drawer opens.
- While drawer is open -> Swipe left anywhere or tap scrim -> Verify drawer closes cleanly.
