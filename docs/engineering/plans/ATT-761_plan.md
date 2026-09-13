# Implementation Plan: Sort Favorite Tracks / Lieblingsstrecken (ATT-761)

* **Parent Ticket**: [ATT-761](https://rainerblind.atlassian.net/browse/ATT-761) ([Feature] Sort Lieblingsstrecken)
* **Sub-Task**: [ATT-769](https://rainerblind.atlassian.net/browse/ATT-769) ([Impl-Plan] Sort Lieblingsstrecken)
* **Target Lösungsversion (Fix Version/s)**: `V4.9.36`
* **Requirement**: `REQ-UI-136` (*Favorite Tracks (Lieblingsstrecken) Sorting by Recordings, Distance to User, Length, and Name*)
* **Test Specification**: `TST-UI-089` (*Favorite Tracks (Lieblingsstrecken) Sorting Verification*)
* **Branch**: `feature/ATT-761`

---

## 1. Executive Summary & Architectural Overview

This plan specifies the implementation of sorting capabilities for Favorite Tracks / Workout Clusters in `WorkoutClustersTabsScreen.kt` and `WorkoutClustersViewModel.kt`.

To maximize visual and operational consistency across the application, the sorting interface strictly adopts the established design language from Routes (`RouteSortOrder` / `RouteTabbedScreen.kt`) and Segments (`SegmentSortOrder` / `SegmentsTabsScreen.kt`):
* An `IconButton` with `Icons.Default.Sort` located in the collapsing top app bar header, positioned between Tuning and Filter.
* A `DropdownMenu` showing all sort orders defined in `ClusterSortOrder`, marking the active sort order with `Icons.Default.Check`.
* Dynamic GPS location awareness: `DISTANCE_TO_USER` is disabled (`enabled = false`) and styled with disabled alpha (`0.38f`) when location telemetry is unavailable.
* In-memory, non-destructive reactive sorting applied to the cluster list across all sport tabs (*All*, *Bike*, *Run*, *Other*), while the *Unclustered* tab retains its native date-sorted workout order.

---

## 2. Domain Model: `ClusterSortOrder`

### 2.1 Enum Specification
A new enum `ClusterSortOrder` implementing `MappableSortOrder` will be created in `com.atrainingtracker.trainingtracker.ui.clusters`:

```kotlin
enum class ClusterSortOrder(@StringRes override val labelResId: Int) : MappableSortOrder {
    RECORDINGS(R.string.filter_section_recordings),
    DISTANCE_TO_USER(R.string.sort_closest),
    DISTANCE(R.string.sort_length),
    NAME(R.string.sort_name)
}
```

### 2.2 Sorting Logic & Predicates
1. **`RECORDINGS`** (Default):
   - Primary: `hitCount` descending (most frequented favorite tracks first).
   - Secondary tie-breaker: `name.lowercase()` ascending.
2. **`DISTANCE_TO_USER`**:
   - Geodesic distance (meters) calculated via `Location.distanceBetween` from the user's current GPS location (`currentLocation`) to the cluster's reference start coordinate (`startLat`, `startLng`).
   - If `currentLocation == null`, fall back to `name.lowercase()` ascending.
3. **`DISTANCE`**:
   - Primary: `refDistance` descending (longest favorite routes first).
   - Secondary tie-breaker: `name.lowercase()` ascending.
4. **`NAME`**:
   - Primary: `name.lowercase()` ascending (alphabetical).

---

## 3. Modifications to Existing Files

### 3.1 `WorkoutClustersViewModel.kt`
* **Sort Order State**:
  Expose reactive state flow:
  ```kotlin
  private val _sortOrder = MutableStateFlow(ClusterSortOrder.RECORDINGS)
  val sortOrder: StateFlow<ClusterSortOrder> = _sortOrder.asStateFlow()

  fun setSortOrder(order: ClusterSortOrder) {
      _sortOrder.value = order
  }
  ```
* **Location Availability State**:
  Expose:
  ```kotlin
  val isLocationAvailable: StateFlow<Boolean> = banalRepository.currentLocation
      .map { it != null }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
  ```
* **Distance Calculation Utility**:
  Provide geodetic calculation helper:
  ```kotlin
  fun calculateDistance(uLat: Double, uLon: Double, cLat: Double, cLon: Double): Float {
      val results = FloatArray(1)
      android.location.Location.distanceBetween(uLat, uLon, cLat, cLon, results)
      return results[0]
  }
  ```
* **Cluster Sorting Helper**:
  Provide cluster list sorting function:
  ```kotlin
  fun sortClusters(
      clusters: List<WorkoutCluster>,
      order: ClusterSortOrder,
      location: LatLng?
  ): List<WorkoutCluster> {
      return when (order) {
          ClusterSortOrder.RECORDINGS ->
              clusters.sortedWith(
                  compareByDescending<WorkoutCluster> { it.hitCount }
                      .thenBy { it.name.lowercase() }
              )
          ClusterSortOrder.DISTANCE ->
              clusters.sortedWith(
                  compareByDescending<WorkoutCluster> { it.refDistance }
                      .thenBy { it.name.lowercase() }
              )
          ClusterSortOrder.NAME ->
              clusters.sortedBy { it.name.lowercase() }
          ClusterSortOrder.DISTANCE_TO_USER -> {
              if (location == null) {
                  clusters.sortedBy { it.name.lowercase() }
              } else {
                  clusters.sortedBy { cluster ->
                      calculateDistance(
                          location.latitude, location.longitude,
                          cluster.startLat, cluster.startLng
                      )
                  }
              }
          }
      }
  }
  ```

### 3.2 `WorkoutClustersTabsScreen.kt`
* **State Collection**:
  Collect sort order and location states:
  ```kotlin
  val sortOrder by viewModel.sortOrder.collectAsState()
  val currentLocation by viewModel.currentLocation.collectAsState()
  val isLocationAvailable by viewModel.isLocationAvailable.collectAsState()
  ```
* **Top App Bar Action Row**:
  In the collapsing header action row, position the Sort button between Tuning and Filter:
  ```kotlin
  Row(verticalAlignment = Alignment.CenterVertically) {
      // 1. Tuning Settings
      IconButton(onClick = onTuneClick) {
          Icon(
              painter = painterResource(id = R.drawable.ic_settings_24),
              contentDescription = stringResource(R.string.cluster_tuning_content_desc),
              tint = MaterialTheme.colorScheme.onPrimaryContainer
          )
      }

      // 2. Sort Button & Dropdown Menu
      var showSortMenu by remember { mutableStateOf(false) }
      Box {
          IconButton(onClick = { showSortMenu = true }) {
              Icon(
                  imageVector = Icons.Default.Sort,
                  contentDescription = stringResource(R.string.sort),
                  tint = MaterialTheme.colorScheme.onPrimaryContainer
              )
          }
          DropdownMenu(
              containerColor = MaterialTheme.colorScheme.surface,
              expanded = showSortMenu,
              onDismissRequest = { showSortMenu = false }
          ) {
              ClusterSortOrder.entries.forEach { order ->
                  DropdownMenuItem(
                      text = {
                          Text(
                              text = stringResource(order.labelResId),
                              color = if (order == ClusterSortOrder.DISTANCE_TO_USER && !isLocationAvailable) {
                                  MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                              } else {
                                  MaterialTheme.colorScheme.onSurface
                              }
                          )
                      },
                      onClick = {
                          viewModel.setSortOrder(order)
                          showSortMenu = false
                      },
                      leadingIcon = {
                          if (sortOrder == order) {
                              Icon(
                                  Icons.Default.Check,
                                  contentDescription = null,
                                  tint = if (order == ClusterSortOrder.DISTANCE_TO_USER && !isLocationAvailable) {
                                      MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                  } else {
                                      MaterialTheme.colorScheme.onSurface
                                  }
                              )
                          }
                      },
                      enabled = !(order == ClusterSortOrder.DISTANCE_TO_USER && !isLocationAvailable)
                  )
              }
          }
      }

      // 3. Filter Action Button
      FilterActionButton(
          onClick = { showFilterBottomSheet = true },
          isFilterActive = filterCriteria.isNotEmpty,
          activeFilterCount = filterCriteria.activeFilterCount,
          tint = MaterialTheme.colorScheme.onPrimaryContainer
      )
  }
  ```
* **List Sorting Integration**:
  Apply sorting after filtering:
  ```kotlin
  val filteredClusters = if (filterCriteria.isEmpty) {
      sportFilteredClusters
  } else {
      sportFilteredClusters.filter { cluster ->
          val linkedEquipment = viewModel.getLinkedEquipmentSet(cluster.probableSportId)
          filterCriteria.matches(cluster, linkedEquipment)
      }
  }

  val finalClusters = remember(filteredClusters, sortOrder, currentLocation) {
      viewModel.sortClusters(filteredClusters, sortOrder, currentLocation)
  }
  ```

---

## 4. Test Strategy & Verification Plan

### 4.1 Unit Tests (`ClusterSortOrderTest.kt`)
Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterSortOrderTest.kt`:
1. `testSortByRecordings_mostRecordingsFirst_andTieBreaksByName`:
   - Clusters with hitCounts [1, 5, 10, 10] -> 10 ("Alpha"), 10 ("Beta"), 5, 1.
2. `testSortByDistance_longestTrackFirst`:
   - Clusters with refDistance [5000, 25000, 12000] -> 25000, 12000, 5000.
3. `testSortByName_alphabeticalCaseInsensitive`:
   - Clusters with names ["Zebra", "alpe", "Beta"] -> "alpe", "Beta", "Zebra".
4. `testSortByDistanceToUser_whenLocationAvailable_closestFirst`:
   - User location at (48.137, 11.576) (Munich).
   - Cluster A at (48.140, 11.580) (~400m).
   - Cluster B at (48.200, 11.600) (~7km).
   - Assert Cluster A is sorted before Cluster B.
5. `testSortByDistanceToUser_whenLocationNull_fallsBackToName`:
   - Null user location -> clusters sorted by name alphabetically.

### 4.2 Full Regression Test
- Run `./gradlew testDebugUnitTest` ensuring 100% clean test execution.

---

## 5. System Invariant Checklist ("What MUST NOT Change")

1. **Filtering Parity**: `filterCriteria` (`REQ-UI-135`), bottom sheet scaffold, and active filter chips row must operate seamlessly with sorting.
2. **Unclustered Workouts Tab**: Unclustered workouts remain sorted chronologically by workout date; cluster sorting is not applied to individual unclustered workouts.
3. **Database & Core Clustering Invariants**: Zero changes to Room/SQLite database schema, cluster discovery, or background tuning recalculation.
4. **Route & Segment Sorting Invariants**: `RouteSortOrder` and `SegmentSortOrder` remain untouched.
