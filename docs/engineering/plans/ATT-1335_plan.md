# Implementation Plan - ATT-1335: ModalNavigationDrawer Back Navigation Dynamic LIFO Precedence & NavHost Isolation

**Ticket**: [ATT-1335](https://rainerblind.atlassian.net/browse/ATT-1335)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Component**: `com.atrainingtracker.trainingtracker.ui.navigation.ATrainingTrackerApp.kt`  
**Parent Requirement**: `REQ-UI-159` (Single-Activity Architecture & Jetpack Compose Navigation Modernization)  
**Committed Target Requirement**: `REQ-UI-162` (Single Source of Truth & Scoped Overlay Back Navigation) in `docs/requirements.md`  
**Committed Test Spec**: `TST-UI-114` (ModalNavigationDrawer Back Navigation Isolation & State Synchronization Test) in `docs/tests.md`  

---

## 1. Technical Context & Impact Analysis

### 1.1 Affected Files Audit
* **Production**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt`
* **Test**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/SingleActivityNavigationTest.kt`

### 1.2 Mapped Requirements Cross-Check
* `REQ-UI-159`: Single-Activity Compose root architecture — preserved (pure Compose, single Activity decor).
* `REQ-UI-161`: ModalNavigationDrawer edge-swipe restriction (40dp margin when closed, full-screen swipe/tap when open) — preserved.
* `REQ-UI-162`: Single Source of Truth & Scoped Overlay Back Navigation — refined with dynamic LIFO precedence and comprehensive visibility predicate.

---

## 2. Step-by-Step Implementation Steps

### Step 1: Refine `isDrawerVisible` Comprehensive Predicate
In `ATrainingTrackerApp.kt`, ensure `isDrawerVisible` observes `isAnimationRunning` in addition to `currentValue` and `targetValue`:
```kotlin
val isDrawerVisible = drawerState.currentValue != DrawerValue.Closed ||
    drawerState.targetValue != DrawerValue.Closed ||
    drawerState.isAnimationRunning
```

### Step 2: Dynamically Compose Layer 1 Drawer `BackHandler` inside `drawerContent`
Move the drawer `BackHandler` inside `drawerContent` and guard it with `if (isDrawerVisible)`:
```kotlin
drawerContent = {
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
}
```
Remove the static root `BackHandler(enabled = isDrawerVisible)` to prevent redundant low-priority callbacks in the dispatcher queue.

### Step 3: Dynamically Compose Layer 2 Bottom Sheet `BackHandler`
Update Layer 2 to conditional composition:
```kotlin
if (drawerController.activeBottomSheet != null) {
    BackHandler {
        drawerController.activeBottomSheet = null
    }
}
```

### Step 4: Add Unit Tests in `SingleActivityNavigationTest.kt`
Implement:
1. `testOnBackPressedDispatcherDynamicLIFOPrecedence`: Registers a simulated `NavHost` callback, dynamically attaches and removes the drawer callback in `OnBackPressedDispatcher`, and validates LIFO execution order.
2. `testIsDrawerVisibleComprehensivePredicate`: Validates `isDrawerVisible` across all 5 animation permutations.

### Step 5: Verification & Regression Testing
1. Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.SingleActivityNavigationTest"`
2. Execute full test suite: `./gradlew testDebugUnitTest`
