# Implementation Plan - ATT-371: Full Route Map Previews

Enhance the map previews in Workout Clusters, Unclustered Workouts, and Training Periods to display the entire route by utilizing persisted spatial bounds. This fix ensures that the camera correctly frames the complete track instead of just the signature points.

## User Review Required

> [!IMPORTANT]
> - **Spatial Integrity**: Map previews will now correctly include all recorded points of the route, preventing "cropped" previews for long or complex paths.
> - **Performance Optimization**: For training periods (Weeks/Months/Years), we will now use pre-calculated bounds from the database. This eliminates the expensive decoding of all member polylines during list rendering, resulting in smoother scrolling.
> - **Database Refresh (v25)**: A database version bump is recommended to ensure that all historical clusters and periods have their spatial bounds accurately re-calculated and stored.

## Proposed Changes

### 1. UI Layer: Enhanced Bound Calculation
Fulfills REQ-SET-042 | Test: TST-SET-032 (Updated)

#### [MODIFY] [WorkoutClusterComponents.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt)
- **Refactor `ClusterItem`**:
    - Update `bounds` calculation to prioritize `cluster.minLat`, `maxLat`, `minLng`, and `maxLng`.
    - Fallback to the signature points (Start, End, Apex) only if persisted bounds are missing.
- **Refactor `UnclusteredWorkoutItem`**:
    - Update `bounds` calculation to use `workout.minLat`, `maxLat`, `minLng`, and `maxLng`.

#### [MODIFY] [PeriodSummaryCard.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummaryCard.kt)
- **Optimize `PeriodMultiWorkoutMap`**:
    - Add an optional `bounds: LatLngBounds?` parameter.
    - If provided, bypass the manual decoding of all polylines for bound calculation.
- **Update `PeriodSummaryCard`**:
    - Calculate `LatLngBounds` from `summary.minLat`, `maxLat`, etc., using the `90.0` sentinel check.
    - Pass this pre-calculated bounds object to the map component.

### 2. Foundation: Data Consistency (v25)
#### [MODIFY] [PeriodSummariesDatabaseManager.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummariesDatabaseManager.kt)
- Bump `DATABASE_VERSION` to **25**.
- Update `onUpgrade` to trigger a fresh migration, ensuring all periods have valid persisted bounds.

## Verification Plan

### Manual Verification (TST-SET-032)
1. Navigate to 'My Locations' (Workout Clusters).
2. **Verify** that clusters with long routes (e.g. large loops or out-and-back) are fully visible in the mini-map preview.
3. Navigate to 'Periods' -> 'Months'.
4. **Verify** that the monthly heatmap preview correctly frames all member workouts.
5. **Verify** that scrolling through a large list of months is smooth (confirming the performance optimization).
