# Implementation Plan: Show heatmap in preview (SCRUM-224)

## 1. Problem Statement
The mini-map in the Workout Cluster list (Favorite Tracks) currently only shows 3 point markers. This lacks visual context for the user to recognize the route's shape at a glance.

## 2. Requirement Mapping
| Requirement ID | Component | Test ID | Description |
|:---|:---|:---|:---|
| **REQ-SET-035** | `WorkoutClusterRepository`, `WorkoutClusterComponents` | **TST-SET-026** | Show representative spatial preview on list mini-maps. |

## 3. Impact Analysis
*   **Performance**: Loading polylines for every list item could cause scroll stutter if not handled carefully.
*   **Data Usage**: Multiple database queries to fetch recent workouts for each cluster.
*   **Mitigation**:
    *   Load polylines lazily in the `ViewModel` or `Repository`.
    *   Limit to 5 recent workouts or 1 linked route.
    *   Use `PolyUtil.decode` sparingly or cache results.

## 4. Proposed Changes

### `WorkoutCluster.kt`
*   Add a field `previewPaths: List<String>` (list of encoded polylines) to the data class.

### `WorkoutClusterRepository.kt`
*   Update `refreshClusters` to populate `previewPaths`.
    *   First, check if there's a linked route via `routesRepository.getRouteByClusterId`.
    *   If yes, use that single polyline.
    *   If no, query the 5 most recent workouts for the cluster and take their `mapPolyline`.

### `WorkoutClusterComponents.kt`
*   Update `ClusterItem` map to render the `previewPaths`.
*   If multiple paths, render as a heatmap (using `TileOverlay` with `HeatmapTileProvider` if possible, or simple `Polyline` with very low alpha).
*   If a single path (Route), render as a solid line.

## 5. Verification Plan
### Manual Verification (TST-SET-026)
1.  Open the **Favorite Tracks** screen.
2.  Verify that each list item shows a map with:
    *   Start/End/Apex markers.
    *   A blue path representing the linked Route (if applicable).
    *   OR a subtle heatmap-like cluster of paths from recent workouts.
3.  Scroll the list and ensure performance is acceptable.
