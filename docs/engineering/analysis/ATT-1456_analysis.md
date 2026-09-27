# Architectural Analysis - ATT-1456: [Cockpit] Can't change the tracking tabs anymore :(

## 1. Context & Executive Summary

* **Issue Key**: `ATT-1456`
* **Sub-tasks**: `ATT-1458` (Stage 1: Analysis [In Bearbeitung])
* **Parent Issue**: `ATT-1456` (*[Bug] Can't change the tracking tabs anymore :(* )
* **Target Version**: `V4.9.38` (Sprint `2026-39.3`)
* **Associated Requirements**: `REQ-UI-175` (Tracking Tabs Configuration Transition and Hierarchical Navigation), referencing `REQ-SET-050` (Navigation Drawer Structure) and `REQ-UI-103` (Customizable Cockpits)
* **Associated Verification**: `TST-UI-127` (Tracking Tabs Configuration Transition and Navigation Verification)
* **Branch**: `feature/ATT-1456`

---

## 2. Problem Statement & Root Cause Analysis

### A. Symptom Description
When an athlete navigates to the navigation drawer and selects **"Tracking Tabs"** (`R.id.drawer_tracking_layouts`), an `ActivityTypeSelectionDialog` is displayed. The athlete picks a sport type (e.g., *Radeln*, *Laufen*), the dialog dismisses, but the tracking screen fails to transition to configuration mode (`ScreenMode.CONFIGURATION`). Instead, the screen remains in standard `ScreenMode.TRACKING` on Page 0 (*Start*).

### B. Live On-Device Reproduction Trace
On a physical Google Pixel 10 (Build Android 16), the issue was reproduced 100% reliably:
1. Open navigation drawer -> Tap "Tracking Tabs".
2. `ActivityTypeSelectionDialog` renders properly.
3. Select "Radeln".
4. Dialog dismisses -> Screen stays on `NavRoutes.START_TRACKING` in standard tracking mode; configuration controls never appear.

### C. Deep-Dive Root Cause Analysis

Tracing the invocation across the application reveals three intertwined state management and scoping defects:

#### 1. Unobserved State in Activity (`MainActivityWithNavigation.kt`)
In `MainActivityWithNavigation.kt` (line 176):
```kotlin
var pendingActivityType: ActivityType? = null
```
And in `onActivityTypeSelected(activityType)` (line 796):
```kotlin
fun onActivityTypeSelected(activityType: ActivityType) {
    pendingActivityType = activityType
    navigateToDrawerItem(R.id.drawer_start_tracking)
}
```
`pendingActivityType` is a plain Kotlin mutable property. It is **not** a Jetpack Compose `MutableState<ActivityType?>`, nor is it backed by a `StateFlow` or `LiveData`.

#### 2. Recomposition Suppression on SingleTop Navigation (`ATrainingTrackerApp.kt`)
In `ATrainingTrackerApp.kt` (lines 265–277):
```kotlin
composable(NavRoutes.START_TRACKING) {
    val trackingTabsViewModel: TrackingTabsViewModel = viewModel(
        factory = TrackingTabsViewModelFactory(activity.application)
    )
    LaunchedEffect(activity.pendingActivityType) {
        activity.pendingActivityType?.let { type ->
            trackingTabsViewModel.setExplicitActivityType(type)
            trackingTabsViewModel.setScreenMode(ScreenMode.CONFIGURATION)
            activity.pendingActivityType = null
        }
    }
    TrackingTabsScreen(trackingTabsViewModel = trackingTabsViewModel)
}
```
When `navigateToDrawerItem(R.id.drawer_start_tracking)` executes:
- If the athlete was already on `NavRoutes.START_TRACKING` (which is the default landing screen), NavController navigates to `START_TRACKING` with `launchSingleTop = true`.
- Because the route is identical, `NavHost` performs a NO-OP and does not recreate or recompose the `composable(NavRoutes.START_TRACKING)` entry.
- Because `activity.pendingActivityType` is a plain field, Compose's snapshot observation system has no subscription to it.
- Consequently, `LaunchedEffect(activity.pendingActivityType)` **never executes**. The mutation of `pendingActivityType` is completely lost.

#### 3. Sub-Optimal ViewModel Scoping
`TrackingTabsViewModel` in `ATrainingTrackerApp.kt` was instantiated via:
```kotlin
val trackingTabsViewModel: TrackingTabsViewModel = viewModel(
    factory = TrackingTabsViewModelFactory(activity.application)
)
```
Without specifying `viewModelStoreOwner = activity`, the ViewModel was scoped strictly to the `NavBackStackEntry`. Meanwhile, other drawer workflows in `MainActivityWithNavigation` (such as `navigateToFilteredWorkouts` for `WorkoutSummariesViewModel`) cleanly share ViewModel instances across the Activity scope via `ViewModelProvider(this)`.

#### 4. Incomplete Hierarchical Back Navigation in Embedded Mode (`TrackingTabsScreen.kt`)
In `TrackingTabsScreen.kt` (lines 277–281):
```kotlin
// BACK NAVIGATION HANDLER: CONFIG -> PREVIEW (ATT-245)
// PREVIEW -> FINISH is handled by the Activity
BackHandler(enabled = screenMode == ScreenMode.CONFIGURATION) {
    trackingTabsViewModel.handleBackPressToPreview()
}
```
Historically, tab configuration existed in a standalone `ConfigTrackingTabsActivity`. When embedded into `TrackingTabsScreen`, `BackHandler` was only registered for `ScreenMode.CONFIGURATION`. When in `ScreenMode.PREVIEW`, pressing the system back button or back gesture falls through to Android's activity dispatcher, exiting the application rather than returning to `ScreenMode.TRACKING` via `trackingTabsViewModel.exitConfiguration()`.

---

## 3. Call Site Audit

All relevant files and call sites have been audited:

1. **[MainActivityWithNavigation.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt)**:
   - Line 176: `var pendingActivityType: ActivityType? = null` (to be removed).
   - Lines 796–799: `onActivityTypeSelected(activityType: ActivityType)` -> Update to retrieve `TrackingTabsViewModel` from the Activity `ViewModelStore` and mutate `setExplicitActivityType` and `setScreenMode(ScreenMode.CONFIGURATION)` directly.
   - Lines 425–435: `ActivityTypeSelectionDialog` invocation upon drawer click `R.id.drawer_tracking_layouts`.

2. **[ATrainingTrackerApp.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt)**:
   - Lines 265–277: `composable(NavRoutes.START_TRACKING)` -> Scope `trackingTabsViewModel` to `activity` (`viewModelStoreOwner = activity`). Remove fragile `pendingActivityType` bridge and `LaunchedEffect`.

3. **[TrackingTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt)**:
   - Lines 277–282: Add `BackHandler(enabled = screenMode == ScreenMode.PREVIEW)` to invoke `trackingTabsViewModel.exitConfiguration()`.
   - Lines 479–487: `ScreenMode.PREVIEW` header rendering.

4. **[TrackingTabPreviewHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabPreviewHeader.kt)**:
   - Provide an explicit `onExitConfig: () -> Unit` action / Done button alongside the edit toggle, allowing athletes an intuitive tap target to exit preview mode back to tracking without requiring the system back gesture.

---

## 4. Proposed Solution & Architecture

### A. Architectural Principles
1. **Activity-Scoped Single Source of Truth**:
   Scope `TrackingTabsViewModel` to `MainActivityWithNavigation`. Both the Compose UI tree and the host Activity access the identical ViewModel instance.
2. **Direct Reactive Mutation**:
   `MainActivityWithNavigation.onActivityTypeSelected(activityType)` immediately mutates `trackingTabsViewModel.setExplicitActivityType(activityType)` and `trackingTabsViewModel.setScreenMode(ScreenMode.CONFIGURATION)`. Because `screenMode` is an observed `StateFlow`, Compose recomposes immediately regardless of navigation stack state.
3. **Hierarchical Back Navigation**:
   - `ScreenMode.CONFIGURATION` -> system Back or Check icon -> transitions to `ScreenMode.PREVIEW`.
   - `ScreenMode.PREVIEW` -> system Back or Done icon -> calls `trackingTabsViewModel.exitConfiguration()`, restoring `ScreenMode.TRACKING` and clearing explicit activity type.

### B. Implementation Blueprint

#### 1. `MainActivityWithNavigation.kt`
```kotlin
fun onActivityTypeSelected(activityType: ActivityType) {
    val trackingTabsViewModel = androidx.lifecycle.ViewModelProvider(
        this,
        TrackingTabsViewModelFactory(application)
    )[TrackingTabsViewModel::class.java]
    trackingTabsViewModel.setExplicitActivityType(activityType)
    trackingTabsViewModel.setScreenMode(ScreenMode.CONFIGURATION)
    navigateToDrawerItem(R.id.drawer_start_tracking)
}
```

#### 2. `ATrainingTrackerApp.kt`
```kotlin
composable(NavRoutes.START_TRACKING) {
    val trackingTabsViewModel: TrackingTabsViewModel = viewModel(
        viewModelStoreOwner = activity,
        factory = TrackingTabsViewModelFactory(activity.application)
    )
    TrackingTabsScreen(trackingTabsViewModel = trackingTabsViewModel)
}
```

#### 3. `TrackingTabsScreen.kt`
```kotlin
// Back navigation for CONFIGURATION -> PREVIEW
BackHandler(enabled = screenMode == ScreenMode.CONFIGURATION) {
    trackingTabsViewModel.handleBackPressToPreview()
}

// Back navigation for PREVIEW -> TRACKING
BackHandler(enabled = screenMode == ScreenMode.PREVIEW) {
    trackingTabsViewModel.exitConfiguration()
}
```

#### 4. `TrackingTabPreviewHeader.kt`
Add an exit/done button (`Icons.Default.Check`) that invokes `onExitConfig`, matching the Done button in `TrackingTabConfigHeader.kt`.

---

## 5. Requirement Mapping

* **Parent Requirements**:
  - `REQ-SET-050`: Navigation Drawer Structure.
  - `REQ-UI-103`: Customizable Cockpits.
* **New Targeted Requirement**:
  - `REQ-UI-175`: Tracking Tabs Configuration Transition and Hierarchical Navigation.
* **New Targeted Test Case**:
  - `TST-UI-127`: Tracking Tabs Configuration Transition and Navigation Verification.

---

## 6. System Invariants Verification

1. **Active Workout Tracking Invariant**:
   `ScreenMode.TRACKING` behavior, active sensor streams, GPS telemetry, Strava segments, dynamic zone highlighting, and lap splits MUST NOT be altered.
2. **Page 0 Control Tracking Invariant**:
   Page 0 (*Start/Pause/Stop*) and connection management in `ControlTrackingScreen` MUST NOT be affected.
3. **Tab Configuration Data Invariant**:
   Adding tabs, deleting tabs, renaming tabs, and reordering tiles in `TrackingViewsRepository` MUST NOT be altered.
4. **Theme Invariant**:
   Cockpit theming (AMOLED Pure Black vs System Dark/Light) established in `REQ-UI-168` and `REQ-UI-170` MUST be preserved in both configuration and preview modes.

---

## 7. Risk Rating & Recommendation

* **Risk Rating**: **LOW**
  - The changes strictly correct the ViewModel scoping and eliminate an unobserved bridge property (`pendingActivityType`).
  - No database schemas, broadcast receivers, or telemetry calculation pipelines are touched.
* **Recommendation**: **RECOMMEND PASS**. Proceed to Stage 2 (`[Test-Spec]`).
