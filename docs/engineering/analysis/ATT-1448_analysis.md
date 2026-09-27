# Problem Domain & Root Cause Analysis - ATT-1448: Navigation from Workouts to Workout Cluster

**Parent Ticket**: [ATT-1448](https://rainerblind.atlassian.net/browse/ATT-1448)  
**Parent Epic**: Technical Debt & UX Navigation Integrity  
**Sub-task**: [ATT-1481](https://rainerblind.atlassian.net/browse/ATT-1481) (`[Subtask] [Analysis]`)  
**Target Release**: `V4.9.38` (Sprint `2026-39.3`)  
**Requirement Target**: `REQ-UI-178` (Direct Workout-to-Cluster Navigation and Hierarchical Backstack Integrity)  
**Test Spec Target**: `TST-UI-130`  
**Branch**: `feature/ATT-1448`  

---

## 1. Executive Summary & Problem Domain Comprehension

### 1.1 Context & User Motivation
In aTrainingTracker, workouts can belong to geographical clusters ("Lieblingsstrecken" / `WorkoutCluster`). A cluster aggregates recordings along a recurring route, providing aggregate statistics (e.g. total distance, elevation, hit counts) and a consolidated GPS heatmap (`WorkoutClusterHeatmapScreen`).

Athletes reviewing past workouts in the workout list (`WorkoutSummariesTabbedScreen` / `WorkoutSummariesListFragment`) or exploring workout tracks on the period map (`PeriodMapScreen`) see cluster badges identifying the associated cluster. Tapping a cluster badge expresses a clear user intent: **instantly inspect that specific cluster's route trajectory, aggregate stats, and heatmap**, and subsequently press Back to return seamlessly to the originating workout list or period map without losing scroll position or search/filter state.

### 1.2 The Bug (Symptom vs. Cause)
When an athlete taps a cluster badge:
1. **Cluster Focus Dropped (Destination Mismatch)**:
   - Instead of opening the targeted cluster's detail view (`WorkoutClusterHeatmapScreen`), the app displays the generic top-level 5-tab cluster list (`WorkoutClustersTabsScreen` showing All, Bike, Run, Other, and Unclustered tabs). The athlete must manually locate and re-select the cluster from the list.
2. **Backstack Rupture & State Annihilation**:
   - `MainActivityWithNavigation.kt:445` handles the event by calling `navigateToDrawerItem(R.id.drawer_my_locations)`.
   - `navigateToDrawerItem`:
     - Calls `popUpTo(controller.graph.findStartDestination().id) { saveState = true }`, clearing the navigation backstack back to `START_TRACKING`.
     - Explicitly executes `MyPreferenceManager(applicationContext).clearWorkoutFilterCriteria()` because `mSelectedFragmentId == R.id.drawer_workouts && itemId != R.id.drawer_workouts`.
     - Pressing the system Back button or the header Back arrow exits the application or pops to the start tracking screen rather than returning to the originating workout. All workout list filter criteria and scroll positions are destroyed.

---

## 2. Architecture & Call-Site Audit

### 2.1 Event Generation Call Sites
The event bus `WorkoutNavigationEvents` exposes `triggerCluster(clusterId: Long)`. It is invoked at three distinct UI locations:
1. **`WorkoutSummariesTabbedScreen.kt:201 & 250`**:
   ```kotlin
   onClusterClick = { clusterId -> WorkoutNavigationEvents.triggerCluster(clusterId) }
   ```
   Triggered when tapping a cluster chip on a workout card in the workout list tab.
2. **`WorkoutSummariesListFragment.kt:193 & 262`**:
   ```kotlin
   onClusterClick = { clusterId -> WorkoutNavigationEvents.triggerCluster(clusterId) }
   ```
   Legacy list fragment interop for workout cards.
3. **`PeriodMapScreen.kt:269`**:
   ```kotlin
   onClusterClick = { clusterId -> WorkoutNavigationEvents.triggerCluster(clusterId) }
   ```
   Triggered when tapping a cluster chip in the bottom sheet inspecting a workout track on the period map.

### 2.2 Event Consumption & Handling in `MainActivityWithNavigation.kt`
Lines 440-448 in `MainActivityWithNavigation.kt`:
```kotlin
WorkoutNavigationEvents.navigateToClusterLiveData.observe(this) { clusterId: Long? ->
    if (clusterId == null || clusterId <= 0) return@observe

    mSelectedFragmentId = R.id.drawer_my_locations
    mDrawerController.selectedItemId = mSelectedFragmentId
    navigateToDrawerItem(R.id.drawer_my_locations)

    WorkoutNavigationEvents.resetCluster()
}
```
Flaws in this handler:
1. `clusterId` is never forwarded to the destination.
2. `navigateToDrawerItem` treats the navigation as a top-level drawer transition instead of a child screen push, triggering drawer state resets, popping the backstack to `findStartDestination().id`, and erasing workout filter preferences.

### 2.3 Existing Capabilities of `WorkoutClustersScreen.kt`
Lines 70-106 & 254-276 in `WorkoutClustersScreen.kt`:
```kotlin
fun WorkoutClustersScreen(
    viewModel: WorkoutClustersViewModel = viewModel(),
    summariesViewModel: WorkoutSummariesViewModel = viewModel(),
    trackOnMapViewModel: TrackOnMapAftermathViewModel = viewModel(),
    initialClusterId: Long? = null,
    onBackToNav: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) { ...
    val targetClusterId = remember { initialClusterId?.takeIf { it > 0 } }
    LaunchedEffect(targetClusterId) {
        if (targetClusterId != null) {
            viewModel.selectClusterById(targetClusterId)
        }
    }
...
    selectedCluster != null -> {
        val isDirectNavigation = targetClusterId != null
        BackHandler {
            if (isDirectNavigation) {
                onBackToNav?.invoke() ?: viewModel.selectCluster(null)
            } else {
                viewModel.selectCluster(null)
            }
        }
        WorkoutClusterHeatmapScreen(
            cluster = selectedCluster!!,
            viewModel = viewModel,
            onBack = {
                if (isDirectNavigation) {
                    onBackToNav?.invoke() ?: viewModel.selectCluster(null)
                } else {
                    viewModel.selectCluster(null)
                }
            },
            ...
        )
    }
}
```
**Key Finding**: `WorkoutClustersScreen` was *already designed* to accept `initialClusterId` and `onBackToNav`. When `initialClusterId > 0`:
- It automatically selects the cluster via `viewModel.selectClusterById(targetClusterId)`.
- It marks `isDirectNavigation = true`.
- When the athlete navigates back, it delegates to `onBackToNav?.invoke()`.

However, in `ATrainingTrackerApp.kt:320-329`:
```kotlin
composable(NavRoutes.LOCATIONS) {
    val clustersViewModel: WorkoutClustersViewModel = viewModel(activity)
    val summariesViewModel: WorkoutSummariesViewModel = viewModel(activity)
    val trackOnMapViewModel: TrackOnMapAftermathViewModel = viewModel(activity)
    WorkoutClustersScreen(
        viewModel = clustersViewModel,
        summariesViewModel = summariesViewModel,
        trackOnMapViewModel = trackOnMapViewModel
    )
}
```
Neither `initialClusterId` nor `onBackToNav` is wired up!

---

## 3. Root Cause Breakdown

| Symptom | Root Cause | Impact |
| :--- | :--- | :--- |
| **Top-level tabs shown instead of cluster detail** | 1. `MainActivityWithNavigation.kt` observes `navigateToClusterLiveData` but does not pass `clusterId` to the route or ViewModel.<br>2. `ATrainingTrackerApp.kt` does not pass `initialClusterId` to `WorkoutClustersScreen`. | User lands on generic cluster tabs instead of the tapped cluster. |
| **Backstack popped to Start Tracking** | `MainActivityWithNavigation.kt:445` calls `navigateToDrawerItem(R.id.drawer_my_locations)`, which invokes `popUpTo(controller.graph.findStartDestination().id) { saveState = true }`. | Pressing Back exits the app or returns to Page 0 instead of the originating workout list or period map. |
| **Workout filter criteria cleared** | `MainActivityWithNavigation.kt:749-751` explicitly wipes workout filter criteria when transitioning from `R.id.drawer_workouts` via drawer item navigation. | Any active date, sport type, or keyword filters on the workout list are lost. |
| **Back button inside Heatmap does not pop navigation** | `onBackToNav` parameter is omitted in `ATrainingTrackerApp.kt:324-328`. | Even if cluster was selected, pressing Back in heatmap would only clear the selection inside `WorkoutClustersScreen`, rather than popping the backstack to the originating screen. |

---

## 4. Proposed Solution Architecture

### 4.1 Route Parameterization in `NavRoutes.kt`
Define type-safe route patterns for `NavRoutes.LOCATIONS`:
```kotlin
object NavRoutes {
    const val LOCATIONS = "locations"
    const val LOCATIONS_PATTERN = "locations?clusterId={clusterId}"
    const val ARG_CLUSTER_ID = "clusterId"

    fun locations(clusterId: Long? = null): String =
        if (clusterId != null && clusterId > 0) "locations?$ARG_CLUSTER_ID=$clusterId" else LOCATIONS

    fun toDrawerItemId(route: String?): Int = when (route?.substringBefore("?")?.substringBefore("/")) {
        ...
        LOCATIONS -> R.id.drawer_my_locations
        ...
    }
}
```
- Query parameter `?clusterId={clusterId}` is optional with default `-1L`.
- Navigating from the drawer (`navController.navigate("locations")`) matches the pattern with default `-1L` (cluster tabs list).
- Navigating from a workout (`navController.navigate("locations?clusterId=$clusterId")`) supplies the targeted `clusterId`.
- `toDrawerItemId` safely ignores query parameters via `substringBefore("?")`.

### 4.2 Route Definition & Backstack Wiring in `ATrainingTrackerApp.kt`
Configure `composable(NavRoutes.LOCATIONS_PATTERN)` with navigation arguments and backstack wiring:
```kotlin
composable(
    route = NavRoutes.LOCATIONS_PATTERN,
    arguments = listOf(
        navArgument(NavRoutes.ARG_CLUSTER_ID) {
            type = NavType.LongType
            defaultValue = -1L
        }
    )
) { backStackEntry ->
    val clusterIdArg = backStackEntry.arguments?.getLong(NavRoutes.ARG_CLUSTER_ID)?.takeIf { it > 0 }
    val clustersViewModel: WorkoutClustersViewModel = viewModel(activity)
    val summariesViewModel: WorkoutSummariesViewModel = viewModel(activity)
    val trackOnMapViewModel: TrackOnMapAftermathViewModel = viewModel(activity)
    WorkoutClustersScreen(
        viewModel = clustersViewModel,
        summariesViewModel = summariesViewModel,
        trackOnMapViewModel = trackOnMapViewModel,
        initialClusterId = clusterIdArg,
        onBackToNav = {
            clustersViewModel.selectCluster(null)
            navController.popBackStack()
        }
    )
}
```

### 4.3 Direct Stack Push in `MainActivityWithNavigation.kt`
Update `observeNavigationEvents()` to push `NavRoutes.locations(clusterId)` onto the existing backstack without `popUpTo`:
```kotlin
WorkoutNavigationEvents.navigateToClusterLiveData.observe(this) { clusterId: Long? ->
    if (clusterId == null || clusterId <= 0) return@observe

    // Pre-select on ViewModel for instant reactive loading
    val clustersViewModel = ViewModelProvider(this)[WorkoutClustersViewModel::class.java]
    clustersViewModel.selectClusterById(clusterId)

    // Push cluster detail onto the existing backstack
    navController?.navigate(NavRoutes.locations(clusterId))

    WorkoutNavigationEvents.resetCluster()
}
```

### 4.4 Invariants & Non-Regression Guarantees
1. **Drawer Navigation Preserved**: Opening "Lieblingsstrecken" from the navigation drawer continues to open `NavRoutes.LOCATIONS` with `clusterId = -1L`, displaying all tabs normally.
2. **Backstack Integrity**: Navigating from `WORKOUTS` or `PERIODS` pushes `LOCATIONS` on top. Pressing Back in the heatmap calls `onBackToNav`, clearing the cluster selection and executing `popBackStack()`, directly restoring the originating screen.
3. **Filter & Scroll Preservation**: Workout filter criteria and scroll position in `WorkoutSummariesViewModel` remain completely intact because `clearWorkoutFilterCriteria()` is not invoked and the originating backstack entry is preserved.
4. **Reactivity & Direct Navigation Reset**: When popping back to the workout list, `currentRoute` updates reactively, automatically syncing `drawerController.selectedItemId = R.id.drawer_workouts`.
