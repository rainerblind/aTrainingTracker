# SCRUM-217: Route Cluster Deletion

## 1. Requirement Summary
*   **Goal**: Provide an option to delete a Route Cluster.
*   **Behavior**: Similar to workout deletion—long-clicking on the header shows an option menu.
*   **Data Integrity**: Association with workouts and routes must be cleared without deleting the actual recordings.

## 2. Implementation Overview

### Data Storage Layer
*   **`WorkoutSummariesDatabaseManager.java`**:
    *   Implemented `clearClusterLink(long clusterId)` to atomically set `clusterId = -1` for all associated workouts.
*   **`RoutesDatabaseManager.kt`**:
    *   Implemented `clearClusterLink(long clusterId)` to atomically set `clusterId = -1` for all associated explicit routes.
*   **`RouteClusterDatabaseManager.kt`**:
    *   Utilizes existing `deleteCluster(long id)` method to remove the cluster from its dedicated database.

### Repository & ViewModel
*   **`RouteClusterRepository.kt`**:
    *   Implemented `deleteCluster(long clusterId)` which orchestrates the cleanup across all three database tables (Workouts, Routes, Clusters).
*   **`FrequentPathsViewModel.kt`**:
    *   Exposed `deleteCluster(RouteCluster)` to trigger the asynchronous deletion process and update the UI state.

### UI Layer
*   **`FrequentPathHeatmapScreen.kt`**:
    *   Updated `ClusterSummaryHeader` to support `combinedClickable` with a long-click handler.
    *   Added a `DropdownMenu` with a "Delete" action.
    *   Implemented a standard Material 3 `AlertDialog` for confirmation before proceeding with deletion.

## 3. Verification Details
*   **Test Case**: `TST-SET-024`
*   **Steps**:
    1.  Navigated to the detail view of a "Frequent Path".
    2.  Long-clicked the header area.
    3.  Selected "Delete" from the context menu.
    4.  Confirmed the deletion in the resulting dialog.
*   **Result**: **PASS**
    *   The cluster was successfully removed from the list.
    *   Workouts previously assigned to this cluster were verified to still exist in the main history but without a cluster name.
