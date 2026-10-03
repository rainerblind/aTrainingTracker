# Implementation Plan - ATT-1448: Navigation from Workouts to Workout Cluster

**Parent Ticket**: [ATT-1448](https://rainerblind.atlassian.net/browse/ATT-1448)  
**Parent Epic**: Technical Debt & UX Navigation Integrity  
**Sub-task**: [ATT-1484](https://rainerblind.atlassian.net/browse/ATT-1484) (`[Impl-Plan]`)  
**Target Release**: `V4.9.38` (Sprint `2026-39.3`)  
**Requirement ID**: `REQ-UI-178`  
**Test ID**: `TST-UI-130`  
**Analysis Reference**: `docs/engineering/analysis/ATT-1448_analysis.md`  
**Test Spec Reference**: `docs/engineering/test_specs/ATT-1448_test_spec.md`  
**Branch**: `feature/ATT-1448`  

---

## 1. Executive Summary & Architectural Scope

The goal of **ATT-1448** is to restore the broken direct navigation from individual workout summary cards and period map inspection sheets to the targeted workout cluster detail view (`WorkoutClusterHeatmapScreen`). 

### Problem Definition
1. **Target Dropped**: When tapping a cluster badge on a workout card or on the period map, `WorkoutNavigationEvents.triggerCluster(clusterId)` is fired, but `MainActivityWithNavigation.kt` calls `navigateToDrawerItem(R.id.drawer_my_locations)` without forwarding `clusterId`.
2. **Backstack Cleared**: `navigateToDrawerItem` treats direct cluster inspection as a top-level drawer transition, executing `popUpTo(startDestination) { saveState = true }` and wiping active workout list filters via `MyPreferenceManager.clearWorkoutFilterCriteria()`.
3. **Missing Backstack Delegate**: `ATrainingTrackerApp.kt` omitted passing `initialClusterId` and `onBackToNav` to `WorkoutClustersScreen`, leaving the existing direct navigation handling in `WorkoutClustersScreen.kt` disconnected.

### Architectural Solution
1. **Parameterized Navigation Route (`NavRoutes.kt`)**:
   - Introduce `LOCATIONS_PATTERN = "locations?clusterId={clusterId}"` and `ARG_CLUSTER_ID = "clusterId"`.
   - Provide helper `fun locations(clusterId: Long? = null): String` returning `"locations?clusterId=$clusterId"` when `clusterId > 0`, and `LOCATIONS` (`"locations"`) otherwise.
   - Update `toDrawerItemId(route: String?)` to parse `route?.substringBefore("?")?.substringBefore("/")`, ensuring query parameters do not disrupt drawer item synchronization.
2. **Navigation Graph Argument Registration (`ATrainingTrackerApp.kt`)**:
   - Register `navArgument(NavRoutes.ARG_CLUSTER_ID) { type = NavType.LongType; defaultValue = -1L }` on `composable(NavRoutes.LOCATIONS_PATTERN)`.
   - Extract `clusterIdArg = backStackEntry.arguments?.getLong(NavRoutes.ARG_CLUSTER_ID)?.takeIf { it > 0 }` and pass to `WorkoutClustersScreen(initialClusterId = clusterIdArg, ...)`.
   - Wire `onBackToNav = { clustersViewModel.selectCluster(null); navController.popBackStack() }`.
3. **Direct Navigation Dispatch (`MainActivityWithNavigation.kt`)**:
   - In `WorkoutNavigationEvents.navigateToClusterLiveData` observer, pre-select the cluster on `WorkoutClustersViewModel` via `selectClusterById(clusterId)`.
   - Push `navController?.navigate(NavRoutes.locations(clusterId))` onto the current backstack without invoking `navigateToDrawerItem`.
   - Preserve workout list filters and scroll state (do NOT call `clearWorkoutFilterCriteria()`).
4. **Lifecycle & Backstack Popping (`WorkoutClustersScreen.kt`)**:
   - Re-use existing `isDirectNavigation` logic in `WorkoutClustersScreen.kt`: when direct navigation is active (`targetClusterId != null`), both `BackHandler` and the header `onBack` arrow invoke `onBackToNav?.invoke()`, smoothly popping back to the originating workout view.

---

## 2. Step-by-Step Implementation Strategy

### Phase 1: Parameterized Route in `NavRoutes.kt`
In [NavRoutes.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/NavRoutes.kt):
1. Add route pattern constants:
   ```kotlin
   const val LOCATIONS_PATTERN = "locations?clusterId={clusterId}"
   const val ARG_CLUSTER_ID = "clusterId"
   ```
2. Add route builder helper:
   ```kotlin
   fun locations(clusterId: Long? = null): String =
       if (clusterId != null && clusterId > 0) "locations?$ARG_CLUSTER_ID=$clusterId" else LOCATIONS
   ```
3. Update `toDrawerItemId`:
   ```kotlin
   fun toDrawerItemId(route: String?): Int = when (route?.substringBefore("?")?.substringBefore("/")) {
       START_TRACKING -> R.id.drawer_start_tracking
       WORKOUTS -> R.id.drawer_workouts
       PERIODS -> R.id.drawer_periods
       MAP -> R.id.drawer_map
       SEGMENTS -> R.id.drawer_segments
       ROUTES -> R.id.drawer_routes
       START_LOCATIONS -> R.id.drawer_start_locations
       LOCATIONS -> R.id.drawer_my_locations
       SENSORS -> R.id.drawer_my_sensors
       BIKES -> R.id.drawer_bikes
       SHOES -> R.id.drawer_shoes
       SPORT_TYPES -> R.id.drawer_sport_types
       TRAINING_ZONES -> R.id.drawer_training_zones
       BACKUP_RESTORE -> R.id.drawer_backup_restore
       else -> R.id.drawer_start_tracking
   }
   ```

### Phase 2: Argument Registration and Backstack Wiring in `ATrainingTrackerApp.kt`
In [ATrainingTrackerApp.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt):
1. Import `androidx.navigation.NavType` and `androidx.navigation.navArgument`.
2. Update `composable(NavRoutes.LOCATIONS)`:
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

### Phase 3: Direct Push in `MainActivityWithNavigation.kt`
In [MainActivityWithNavigation.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt):
1. Update lines 440-448 in `observeNavigationEvents()`:
   ```kotlin
   WorkoutNavigationEvents.navigateToClusterLiveData.observe(this) { clusterId: Long? ->
       if (clusterId == null || clusterId <= 0) return@observe

       val clustersViewModel = ViewModelProvider(this)[WorkoutClustersViewModel::class.java]
       clustersViewModel.selectClusterById(clusterId)

       navController?.navigate(NavRoutes.locations(clusterId))

       WorkoutNavigationEvents.resetCluster()
   }
   ```

---

## 3. Automated Verification Strategy

### Dedicated Unit Test Suite: `NavRoutesClusterTest.kt`
Create `app/src/test/java/com/atrainingtracker/trainingtracker/navigation/NavRoutesClusterTest.kt` verifying:
1. `testLocationsRoute_withoutArguments_returnsBasePath`:
   - Validates `NavRoutes.locations(null) == "locations"`.
   - Validates `NavRoutes.locations(-1L) == "locations"`.
   - Validates `NavRoutes.locations(0L) == "locations"`.
2. `testLocationsRoute_withValidClusterId_returnsParameterizedQuery`:
   - Validates `NavRoutes.locations(42L) == "locations?clusterId=42"`.
   - Validates `NavRoutes.locations(1001L) == "locations?clusterId=1001"`.
3. `testToDrawerItemId_withParameterizedRoutes_correctlyExtractsBaseRoute`:
   - Validates `NavRoutes.toDrawerItemId("locations") == R.id.drawer_my_locations`.
   - Validates `NavRoutes.toDrawerItemId("locations?clusterId=42") == R.id.drawer_my_locations`.
   - Validates `NavRoutes.toDrawerItemId("locations?clusterId=999&debug=true") == R.id.drawer_my_locations`.
   - Validates `NavRoutes.toDrawerItemId("workouts") == R.id.drawer_workouts`.
   - Validates `NavRoutes.toDrawerItemId(null) == R.id.drawer_start_tracking`.
4. `testWorkoutNavigationEvents_clusterDispatchAndReset`:
   - Validates `triggerCluster(42L)` and `resetCluster()`.

---

## 4. Physical On-Device Verification Protocol (Google Pixel 10)

1. **Light Mode Pre-condition**: Ensure Pixel 10 is configured in Light Mode (`adb shell cmd uimode night no`).
2. **Workout List Direct Navigation**:
   - Open workout list (`drawer_workouts`).
   - Select a filter (e.g. "Radeln") and scroll down.
   - Tap a cluster chip on any workout card.
   - Verify `WorkoutClusterHeatmapScreen` opens immediately displaying the targeted cluster heatmap and metrics.
   - Tap the top bar back arrow.
   - Verify app returns directly to the workout list with scroll position and sport filter preserved.
3. **Period Map Direct Navigation**:
   - Open Period map view -> tap a workout polyline -> tap cluster chip in bottom sheet.
   - Verify `WorkoutClusterHeatmapScreen` opens.
   - Press system Back -> verify app returns to Period map view.
4. **Drawer Navigation Baseline**:
   - Open drawer -> tap "Lieblingsstrecken".
   - Verify the 5-tab cluster overview opens normally.

---

## 5. System Invariants & Non-Regression Checklist

- [x] **Top-Level Drawer Invariant**: Opening "Lieblingsstrecken" from drawer displays the 5-tab cluster overview with `clusterId = -1L`.
- [x] **Filter Integrity**: Workout list filter criteria are NOT cleared when navigating to cluster heatmap.
- [x] **Backstack Hierarchy**: Tapping back in cluster detail returns to caller (workout list or period map), never dropping the user on start tracking or exiting the app prematurely.
- [x] **Database & Heatmap Integrity**: Cluster database schema, heatmap calculations, and member workout listings remain 100% unaltered.
- [x] **Light Mode State**: Device remains in Light Mode at turn completion.
