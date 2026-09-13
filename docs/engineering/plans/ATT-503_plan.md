# Implementation Plan: Direct Navigation from Workout to Linked Workout Cluster (ATT-503)

* **Parent Ticket**: [ATT-503](https://rainerblind.atlassian.net/browse/ATT-503) ([Feature] Navigate from workout to workout cluster)
* **Sub-Task**: [ATT-834](https://rainerblind.atlassian.net/browse/ATT-834) ([Impl-Plan] Navigate from workout to workout cluster)
* **Requirement**: `REQ-SET-058` (*Cluster Visibility & Direct Cluster Navigation in Workout Summary*)
* **Test Specification**: `TST-SET-057` (*Direct Navigation from Workout to Linked Workout Cluster Verification*)
* **Target Version**: `V4.9.36`
* **Branch**: `feature/ATT-503`

---

## 1. Context & Architectural Motivation

In the current architecture, route cluster navigation is unidirectional: users can navigate from a Workout Cluster to its member workout recordings via the recordings count button introduced in `REQ-SET-057` / `REQ-SET-068` on `WorkoutClusterHeatmapScreen`. However, when inspecting a workout session in `WorkoutSummary` or `TrackOnMapScreen`, the linked cluster is displayed only as a passive, non-interactive text label.

Athletes require bidirectional spatial navigation: when reviewing a workout session, they want to navigate directly to the corresponding Workout Cluster to inspect aggregate performance volume, heatmaps, and spatial signatures.

### Architectural Solution & Visual Parity:
The user explicitly specified that the UI approach introduced in `WorkoutClusterHeatmapScreen` for navigating to the workout list must also be chosen for navigating from the workout to the linked cluster:
1. **Interactive Button for Clustered Workouts**:
   When `clusterId > 0` and `clusterName != null`, `WorkoutHeader` renders an interactive Material 3 `Button` mirroring the cluster detail screen's recordings button:
   * Container Color: `MaterialTheme.colorScheme.primaryContainer`
   * Content Color: `MaterialTheme.colorScheme.onPrimaryContainer`
   * Shape: Rounded pill shape (`RoundedCornerShape(8.dp)` / compact height 28–32 dp)
   * Content: Leading route icon (`R.drawable.my_locations`) + Cluster Name
   * Interaction: Tapping the button executes `onClusterClick(clusterId)` and isolates the click from the outer surface's edit action (`onClicked`).
2. **Subtle Label for Unclustered Workouts**:
   When `clusterId <= 0` or unclustered, `WorkoutHeader` preserves the subtle non-interactive text row (neutral icon + `unclustered` string in `onSurfaceVariant.copy(alpha = TTAlpha.Medium)`).
3. **Decoupled Event Stream & Seamless Back Navigation**:
   * Global event orchestration via `WorkoutNavigationEvents.triggerCluster(clusterId)` observed by `MainActivityWithNavigation`.
   * `MainActivityWithNavigation` switches to `WorkoutClustersFragment.newInstance(clusterId)` and pushes to the Fragment backstack.
   * `WorkoutClustersFragment` auto-selects the cluster on launch via `viewModel.selectClusterById(clusterId)`.
   * When navigating back from the cluster heatmap opened via a workout, the system pops the backstack to return the user directly to the originating workout view.

---

## 2. Impact Analysis & Proposed Code Changes

### 2.1 Component: Presentation Models (`WorkoutHeaderData.kt` & `WorkoutData.kt`)
* **Files**:
  * [`app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeaderData.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeaderData.kt)
  * [`app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutData.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutData.kt)
* **Changes**:
  1. In `WorkoutHeaderData`: Add `val clusterId: Long = -1L` with default value to ensure 100% backward compatibility.
  2. In `WorkoutData.headerData`: Map `clusterId = clusterId`.

### 2.2 Component: Workout Header Presentation (`WorkoutHeader.kt`)
* **File**: [`app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt)
* **Changes**:
  1. Add optional callback parameter: `onClusterClick: ((Long) -> Unit)? = null`.
  2. In the Workout Cluster row:
     * If `data.clusterId > 0 && !data.clusterName.isNullOrBlank()`:
       Render a Material 3 `Button`:
       ```kotlin
       Button(
           onClick = { onClusterClick?.invoke(data.clusterId) },
           contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
           modifier = Modifier.height(30.dp),
           shape = RoundedCornerShape(8.dp),
           colors = ButtonDefaults.buttonColors(
               containerColor = MaterialTheme.colorScheme.primaryContainer,
               contentColor = MaterialTheme.colorScheme.onPrimaryContainer
           )
       ) {
           Row(
               verticalAlignment = Alignment.CenterVertically,
               horizontalArrangement = Arrangement.spacedBy(4.dp)
           ) {
               Icon(
                   painter = painterResource(id = R.drawable.my_locations),
                   contentDescription = null,
                   modifier = Modifier.size(14.dp),
                   tint = MaterialTheme.colorScheme.onPrimaryContainer
               )
               Text(
                   text = data.clusterName,
                   style = MaterialTheme.typography.labelMedium,
                   fontWeight = FontWeight.Bold,
                   maxLines = 1,
                   overflow = TextOverflow.Ellipsis
               )
           }
       }
       ```
     * If unclustered (`data.clusterId <= 0` or `data.clusterName == null`):
       Maintain the existing passive `Row` with `R.drawable.my_locations` and `stringResource(R.string.unclustered)` in `onSurfaceVariant.copy(alpha = TTAlpha.Medium)`.

### 2.3 Component: View Wrappers (`WorkoutSummary.kt`, `TrackOnMapScreen.kt`, `WorkoutList.kt`, `WorkoutTabsScreen.kt`, `WorkoutSummariesListFragment.kt`)
* **Files**:
  * [`app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt)
  * [`app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt)
  * [`app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutList.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutList.kt)
  * [`app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutTabsScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutTabsScreen.kt)
  * [`app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesTabbedFragment.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesTabbedFragment.kt)
  * [`app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesListFragment.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesListFragment.kt)
* **Changes**:
  * Propagate `onClusterClick: ((Long) -> Unit)? = null` down through the layout hierarchy to `WorkoutHeader`.
  * In `WorkoutSummariesTabbedFragment`, `WorkoutSummariesListFragment`, and `TrackOnMapScreen`: connect `onClusterClick = { clusterId -> WorkoutNavigationEvents.triggerCluster(clusterId) }`.
  * In `WorkoutClustersFragment` (when viewing filtered workouts for a cluster): `onClusterClick = { viewingWorkoutsForCluster = null }` to return to the cluster heatmap directly.

### 2.4 Component: Event Routing & Host Navigation (`WorkoutNavigationEvents.kt` & `MainActivityWithNavigation.kt`)
* **Files**:
  * [`app/src/main/java/com/atrainingtracker/trainingtracker/ui/WorkoutNavigationEvents.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/WorkoutNavigationEvents.kt)
  * [`app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt)
* **Changes**:
  1. In `WorkoutNavigationEvents`:
     * Add `_navigateToCluster = MutableSharedFlow<Long?>` and `navigateToClusterLiveData: LiveData<Long?>`.
     * Add `triggerCluster(clusterId: Long)` and `resetCluster()`.
  2. In `MainActivityWithNavigation.observeNavigationEvents()`:
     * Observe `navigateToClusterLiveData`. When non-null and `> 0`:
       ```kotlin
       mSelectedFragmentId = R.id.drawer_my_locations
       mDrawerController.selectedItemId = mSelectedFragmentId
       val fragment = WorkoutClustersFragment.newInstance(clusterId)
       mFragment = fragment
       val tag = WorkoutClustersFragment.TAG
       supportFragmentManager.beginTransaction()
           .replace(R.id.content, fragment, tag)
           .addToBackStack(null)
           .commit()
       WorkoutNavigationEvents.resetCluster()
       ```

### 2.5 Component: Cluster Destination & ViewModel (`WorkoutClustersFragment.kt` & `WorkoutClustersViewModel.kt`)
* **Files**:
  * [`app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersFragment.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersFragment.kt)
  * [`app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt)
* **Changes**:
  1. In `WorkoutClustersViewModel`:
     * Add `fun selectClusterById(clusterId: Long)`:
       ```kotlin
       fun selectClusterById(clusterId: Long) {
           viewModelScope.launch {
               val cluster = allClusters.value.find { it.id == clusterId }
                   ?: withContext(Dispatchers.IO) {
                       WorkoutClusterDatabaseManager.getInstance(getApplication()).getClusterById(clusterId)
                   }
               selectCluster(cluster)
           }
       }
       ```
  2. In `WorkoutClustersFragment`:
     * Add `const val ARG_CLUSTER_ID = "ARG_CLUSTER_ID"` and `newInstance(clusterId: Long? = null)`.
     * When `ARG_CLUSTER_ID` is present:
       - In `LaunchedEffect(Unit)`: invoke `viewModel.selectClusterById(initialClusterId)`.
       - When in `WorkoutClusterHeatmapScreen`, back handling (both `onBack` in heatmap and `BackHandler`) checks if `arguments?.containsKey(ARG_CLUSTER_ID) == true`; if so, invokes `parentFragmentManager.popBackStack()` to seamlessly return to the calling workout view!

---

## 3. System Invariants & Safety Audit

| Subsystem / Layer | Invariant Constraint | Preservation Strategy |
|:---|:---|:---|
| **Workout Header Click Isolation** | Tapping the cluster button MUST NOT trigger the general workout edit dialog (`onClicked`). | Compose `Button` consumes click events within its bounds, leaving parent `Surface.combinedClickable` untouched for outer taps. |
| **Cluster Recordings Navigation** | Existing cluster-to-recordings navigation (`REQ-SET-057`, `REQ-SET-068`) MUST remain intact. | `onHitCountClick` in `WorkoutClusterHeatmapScreen` remains untouched. |
| **Unclustered Workouts** | Workouts without a cluster (`clusterId <= 0` or null name) MUST NOT display an interactive button or navigate to dead ends. | Explicit check `if (data.clusterId > 0 && !data.clusterName.isNullOrBlank())` controls button vs passive label rendering. |
| **Backstack Integrity** | Normal drawer navigation to My Locations MUST NOT close the screen when tapping Back from a cluster. | Fragment checks `arguments?.containsKey(ARG_CLUSTER_ID)`. Only direct workout-to-cluster sessions pop back to the workout list on back press. |
| **Database & Clustering Math** | SQLite tables (`RouteClusters.db`, `WorkoutSummaries.db`), spatial scoring, and statistics aggregations MUST NOT change. | Presentation and navigation layer modifications only; no schema or algorithm changes. |
| **Localization Parity** | All new user-facing strings / accessibility descriptions MUST be localized across all 9 languages (EN, DE, ES, FR, IT, JA, NL, PL, PT). | Resource `workout_cluster_button_desc` localized in all 9 locale directories. |

---

## 4. Verification & Testing Strategy (`TST-SET-057`)

### 4.1 Unit Test Suite: `WorkoutHeaderTest.kt`
* Add Compose tests:
  1. `testWorkoutHeader_clusteredWorkout_rendersPrimaryContainerButton`:
     - Provide `clusterId = 42L, clusterName = "Morning Run"`.
     - Verify button is displayed with `primaryContainer` / `onPrimaryContainer`.
     - Click button -> verify `onClusterClick` called with `42L`.
     - Verify `onClicked` (edit) is NOT called.
  2. `testWorkoutHeader_unclusteredWorkout_rendersPassiveLabel`:
     - Provide `clusterId = -1L, clusterName = null`.
     - Verify passive text "Unclustered" is rendered without button.
  3. `testWorkoutHeader_clickOutsideButton_triggersEdit`:
     - Click workout name -> verify `onClicked` is called.

### 4.2 Unit Test Suite: `WorkoutClustersViewModelTest.kt` & `WorkoutNavigationEventsTest.kt`
* Add ViewModel / event tests:
  1. Test `WorkoutNavigationEvents.triggerCluster(42L)`:
     - Verify `navigateToClusterLiveData` receives `42L`.
  2. Test `WorkoutClustersViewModel.selectClusterById(42L)`:
     - Verify target cluster is loaded and selected into `selectedCluster`.

### 4.3 Clean-Room Regression:
* Execute `./gradlew testDebugUnitTest` across all modules with 100% pass rate.
