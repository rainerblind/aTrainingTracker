# Test Specification & Requirement Synchronization - ATT-1448: Navigation from Workouts to Workout Cluster

## 1. Feature / Bug Overview & Test Scope

* **Issue Key**: `ATT-1448` (Parent) / `ATT-1483` (Stage 2: Test-Spec)
* **Parent Epic**: Technical Debt & UX Navigation Integrity
* **Target Version**: `V4.9.38` (Sprint `2026-39.3`)
* **Related Requirements**: `REQ-UI-178` (*Direct Workout-to-Cluster Navigation and Hierarchical Backstack Integrity*), refining `REQ-SET-058` (*Cluster Visibility & Direct Cluster Navigation in Workout Summary*)
* **Related Tests**: `TST-UI-130` (*Direct Workout-to-Cluster Navigation and Backstack Integrity Verification*)
* **Branch**: `feature/ATT-1448`

### Objective
Ensure that tapping a cluster badge from a workout card (`WorkoutSummariesTabbedScreen.kt`, `WorkoutSummariesListFragment.kt`) or a period map peek sheet (`PeriodMapScreen.kt`) reliably navigates the athlete directly to the targeted cluster detail/heatmap view (`WorkoutClusterHeatmapScreen`). Guarantee backstack integrity by pushing the cluster destination onto the existing navigation stack without popping back to the start destination, ensuring that tapping Back returns the athlete directly to the originating workout list or period map with all active filter criteria and scroll positions fully preserved.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: `REQ-SET-058` (*Cluster Visibility & Direct Cluster Navigation in Workout Summary*) in `docs/requirements.md`, targeting `WorkoutNavigationEvents.kt`, `MainActivityWithNavigation.kt`, and `WorkoutClustersFragment.kt`.
2. **Historical Origin & Commit Trace**: `REQ-SET-058` (ATT-1150) originally introduced direct cluster navigation via `WorkoutNavigationEvents.triggerCluster(clusterId)` to allow athletes to inspect a workout's parent cluster route directly from workout summary cards.
3. **Root Reason for Existing Formulation**: During the subsequent consolidation of app navigation into Jetpack Compose (`MainActivityWithNavigation.kt` and `ATrainingTrackerApp.kt`), the event observer for `navigateToClusterLiveData` was wired to `navigateToDrawerItem(R.id.drawer_my_locations)` without passing `clusterId` or wiring `onBackToNav`. `navigateToDrawerItem` was specifically designed for top-level navigation drawer tab switches, executing `popUpTo(controller.graph.findStartDestination().id) { saveState = true }` and explicitly calling `clearWorkoutFilterCriteria()`. As a result, the targeted cluster was dropped (showing the generic 5-tab cluster list instead), the backstack was cleared to Page 0, and all workout search filters were wiped.
4. **Preservation of Core Invariants**: Top-level drawer navigation to `NavRoutes.LOCATIONS` remains 100% intact with default cluster overview tabs; direct navigation pushes `NavRoutes.locations(clusterId)` onto the backstack without clearing history; `WorkoutClusterHeatmapScreen` displays the requested cluster immediately; Back returns seamlessly to the originating workout view without losing active list filters or scroll state.

---

## 3. Requirement Specification (`REQ-UI-178`)

### REQ-UI-178: Direct Workout-to-Cluster Navigation and Hierarchical Backstack Integrity

The system SHALL support direct navigation from workout cards (`WorkoutSummariesTabbedScreen.kt`, `WorkoutSummariesListFragment.kt`) and period map inspection sheets (`PeriodMapScreen.kt`) to the targeted workout cluster detail view (`WorkoutClusterHeatmapScreen`), preserving backstack hierarchy, active filter criteria, and list scroll positions (ATT-1448):

1. **Parameterized Route Resolution (`NavRoutes.kt`)**:
   - In `NavRoutes.kt`, the system SHALL define `const val LOCATIONS_PATTERN = "locations?clusterId={clusterId}"` and `const val ARG_CLUSTER_ID = "clusterId"`.
   - The system SHALL provide helper `fun locations(clusterId: Long? = null): String`, returning `"locations?$ARG_CLUSTER_ID=$clusterId"` when `clusterId != null && clusterId > 0`, and `LOCATIONS` (`"locations"`) otherwise.
   - `NavRoutes.toDrawerItemId(route: String?)` SHALL parse the base route segment via `route?.substringBefore("?")?.substringBefore("/")`, ensuring route query parameters do not disrupt navigation drawer synchronization.
2. **Navigation Graph Argument & Backstack Wiring (`ATrainingTrackerApp.kt`)**:
   - In `ATrainingTrackerApp.kt`, the destination for `NavRoutes.LOCATIONS_PATTERN` SHALL register `navArgument(NavRoutes.ARG_CLUSTER_ID) { type = NavType.LongType; defaultValue = -1L }`.
   - The composable destination SHALL extract `clusterIdArg = backStackEntry.arguments?.getLong(NavRoutes.ARG_CLUSTER_ID)?.takeIf { it > 0 }` and pass `initialClusterId = clusterIdArg` to `WorkoutClustersScreen`.
   - The composable destination SHALL supply `onBackToNav = { clustersViewModel.selectCluster(null); navController.popBackStack() }` to `WorkoutClustersScreen`, ensuring that dismissing or exiting the heatmap pops back to the caller screen and clears cluster selection.
3. **Non-Destructive Navigation Dispatch (`MainActivityWithNavigation.kt`)**:
   - In `MainActivityWithNavigation.kt`, the observer for `WorkoutNavigationEvents.navigateToClusterLiveData` SHALL pre-select the cluster on the Activity-scoped `WorkoutClustersViewModel` via `selectClusterById(clusterId)`.
   - The observer SHALL navigate directly via `navController?.navigate(NavRoutes.locations(clusterId))` without invoking `navigateToDrawerItem(R.id.drawer_my_locations)`, strictly preserving the navigation backstack and preventing `popUpTo(startDestination)`.
   - The observer SHALL NOT invoke `MyPreferenceManager.clearWorkoutFilterCriteria()`, preserving active workout list filters and scroll state.
   - `WorkoutNavigationEvents.resetCluster()` SHALL be called immediately after dispatch.
4. **Direct Navigation Heatmap Lifecycle (`WorkoutClustersScreen.kt`)**:
   - When `initialClusterId > 0`, `WorkoutClustersScreen` SHALL immediately display `WorkoutClusterHeatmapScreen` for the specified cluster.
   - Tapping the top app bar back arrow or pressing the system Back button/gesture in `WorkoutClusterHeatmapScreen` SHALL invoke `onBackToNav`, cleanly popping the backstack to the originating workout screen.
5. **Preserved Drawer & Cluster Invariants**:
   - Navigating to "Lieblingsstrecken" via the navigation drawer (`R.id.drawer_my_locations` / `NavRoutes.LOCATIONS`) SHALL continue to open the generic 5-tab cluster list (`WorkoutClustersTabsScreen`) with `clusterId = -1L`.
   - Cluster creation, deletion, manual editing, tuning, and member workout listings MUST NOT be altered.
   - Workout list scroll position, sort order, and active filter criteria MUST remain intact upon returning from cluster detail view.

#### Acceptance Criteria (Given-When-Then)

* **AC-1 (Direct Heatmap Opening from Workout List)**:
  - *Given* an athlete viewing the workout list (`WorkoutSummariesTabbedScreen`) with active filters and specific scroll position,
  - *When* the athlete taps the cluster badge on a workout card associated with a cluster (`clusterId = 42`),
  - *Then* the app SHALL navigate directly to `WorkoutClusterHeatmapScreen` displaying cluster 42's heatmap, route trajectory, and aggregate metrics.
* **AC-2 (Hierarchical Backstack Pop & State Preservation)**:
  - *Given* an athlete in `WorkoutClusterHeatmapScreen` opened via direct navigation from the workout list,
  - *When* the athlete taps the back arrow in the top app bar or executes the system Back gesture,
  - *Then* the backstack SHALL pop directly to `WorkoutSummariesTabbedScreen`, restoring the exact workout list scroll position and preserving all active filter criteria.
* **AC-3 (Direct Heatmap Opening from Period Map)**:
  - *Given* an athlete inspecting a workout on the period map (`PeriodMapScreen`),
  - *When* the athlete taps the cluster chip in the workout peek bottom sheet,
  - *Then* the app SHALL navigate directly to `WorkoutClusterHeatmapScreen` for that cluster, and pressing Back SHALL return directly to `PeriodMapScreen`.
* **AC-4 (Drawer Navigation Integrity)**:
  - *Given* an athlete on any screen opening the navigation drawer,
  - *When* the athlete taps "Lieblingsstrecken" (`drawer_my_locations`),
  - *Then* the app SHALL open `WorkoutClustersTabsScreen` with the 5-tab cluster overview (`clusterId = -1L`).
* **AC-5 (Drawer Item Synchronization with Parameterized Route)**:
  - *Given* the app is on `NavRoutes.locations(42)`,
  - *When* `NavRoutes.toDrawerItemId` evaluates the route,
  - *Then* it SHALL resolve to `R.id.drawer_my_locations` without mismatch or crash.

---

## 4. Test Design & Test Cases (`TST-UI-130`)

### 4.1 Unit Test Suite: `NavRoutesClusterTest.kt`

A dedicated unit test file `app/src/test/java/com/atrainingtracker/trainingtracker/navigation/NavRoutesClusterTest.kt` will verify:

1. **`testLocationsRoute_withoutArguments_returnsBasePath`**:
   - Verify `NavRoutes.locations(null)` returns `"locations"`.
   - Verify `NavRoutes.locations(-1L)` returns `"locations"`.
   - Verify `NavRoutes.locations(0L)` returns `"locations"`.
2. **`testLocationsRoute_withValidClusterId_returnsParameterizedQuery`**:
   - Verify `NavRoutes.locations(42L)` returns `"locations?clusterId=42"`.
   - Verify `NavRoutes.locations(1001L)` returns `"locations?clusterId=1001"`.
3. **`testToDrawerItemId_withParameterizedRoutes_correctlyExtractsBaseRoute`**:
   - Verify `NavRoutes.toDrawerItemId("locations")` returns `R.id.drawer_my_locations`.
   - Verify `NavRoutes.toDrawerItemId("locations?clusterId=42")` returns `R.id.drawer_my_locations`.
   - Verify `NavRoutes.toDrawerItemId("locations?clusterId=999&debug=true")` returns `R.id.drawer_my_locations`.
   - Verify `NavRoutes.toDrawerItemId("workouts")` returns `R.id.drawer_workouts`.
   - Verify `NavRoutes.toDrawerItemId(null)` returns `R.id.drawer_start_tracking`.
4. **`testWorkoutNavigationEvents_clusterDispatchAndReset`**:
   - Verify `WorkoutNavigationEvents.triggerCluster(42L)` updates `navigateToClusterLiveData` to `42L`.
   - Verify `WorkoutNavigationEvents.resetCluster()` sets `navigateToClusterLiveData` to `null`.

### 4.2 On-Device Verification (Google Pixel 10)

1. **Workout List Direct Navigation & Backstack Test**:
   - Open aTrainingTracker on Google Pixel 10 (Light Mode).
   - Navigate to "Workouts" (`R.id.drawer_workouts`).
   - Filter by sport type (e.g. Radeln) or scroll down to a workout with an assigned cluster.
   - Note the scroll position and active filter.
   - Tap the cluster chip (e.g. "Hausrunde") on the workout card.
   - **Verification**: `WorkoutClusterHeatmapScreen` opens immediately displaying the route heatmap, apex/extrema markers, and statistics. The 5-tab cluster list is NOT shown.
   - Tap the top bar back arrow.
   - **Verification**: The app returns immediately to the workout list. The scroll position and active sport filter are completely preserved.
2. **Period Map Direct Navigation Test**:
   - Navigate to "Perioden" -> select a period -> open Map view.
   - Tap a workout polyline to open the peek bottom sheet.
   - Tap the cluster chip.
   - **Verification**: `WorkoutClusterHeatmapScreen` opens for the cluster.
   - Press system Back.
   - **Verification**: The app returns directly to the period map.
3. **Drawer Navigation Baseline Test**:
   - Open the navigation drawer and tap "Lieblingsstrecken".
   - **Verification**: `WorkoutClustersTabsScreen` opens displaying the 5 tabs ("Alle", "Radeln", "Laufen", "Sonstige", "Unzugeordnet") with tab selection and scroll working normally.

---

## 5. Bidirectional Traceability Matrix

| Requirement ID | Test Case ID | Test Type | Target File(s) | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-178` | `TST-UI-130` | Unit / Integration | `NavRoutes.kt`, `NavRoutesClusterTest.kt` | Ready |
| `REQ-UI-178` | `TST-UI-130` | Architecture / Nav | `MainActivityWithNavigation.kt`, `ATrainingTrackerApp.kt` | Ready |
| `REQ-UI-178` | `TST-UI-130` | Composable Lifecycle | `WorkoutClustersScreen.kt` | Ready |
| `REQ-UI-178` | `TST-UI-130` | End-to-End On-Device | Google Pixel 10 | Ready |
