# SCRUM-216: Reference Route Visualization in Cluster Heatmap

## 1. Requirement Summary
*   **Goal**: Show the authoritative route on the detailed heatmap view when a cluster is based on a route.
*   **Rationale**: Providing the reference route as a baseline helps users visualize how closely their workouts adhere to the intended path.

## 2. Implementation Overview

### Data Layer
*   **`RoutesDatabaseManager.kt`**:
    *   Implemented `getRouteByClusterId(clusterId)`: Fetches a route using the `cluster_id` link added in SCRUM-207.
*   **`RoutesRepository.kt`**:
    *   Exposed `getRouteByClusterId()` as a suspend function.

### ViewModel & UI
*   **`FrequentPathsViewModel.kt`**:
    *   Added `linkedRoute` StateFlow.
    *   Updated `selectCluster()` to fetch the linked route whenever a cluster is selected for detail view.
*   **`FrequentPathHeatmapScreen.kt`**:
    *   Collects `linkedRoute` state.
    *   In the `mapContent` block, renders the linked route as a primary path (using `isSelected = true` styling) overlaid on the workout heatmap.

## 3. Verification Details
*   **Test Case**: `TST-SET-023`
*   **Steps**:
    1.  Imported a GPX route (which created a linked cluster).
    2.  Opened the heatmap for that cluster.
    3.  Verified that the route's path is clearly visible as a solid ForestGreen line.
*   **Result**: **PASS**
