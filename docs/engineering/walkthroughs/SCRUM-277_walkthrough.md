# Walkthrough: Update Unclustered Workouts UI & Navigation (ATT-277)

## Fulfilling REQ-SET-056: Unclustered Workout Visualization

Unclustered workouts in the "My Locations" section were updated to provide visual parity with the primary cluster items and a more logical auditing workflow.

### Implemented Changes

#### 1. Visual Parity (`WorkoutClusterComponents.kt`)
- Created `UnclusteredWorkoutItem` which replaces the previous generic summary.
- **Spatial Preview**: Added a mini-map to each unclustered item, rendering the workout's specific path and signature markers (Start, Stop, Apex).
- **Harmonized Layout**: Aligned metadata (distance, sport, equipment) and typography with the `ClusterItem` standard.

#### 2. Enhanced Workflow (`WorkoutClustersFragment.kt`)
- **Inspection Phase**: Clicking an unclustered workout now navigates to a **Full Map View** (`TrackOnMapScreen`) instead of jumping directly to a dialog.
- **Direct Assignment**: Added a "Move to Cluster" action in the map header.
- **State Integrity**: Updated the `moveWorkout` logic in `WorkoutClustersViewModel` to correctly handle unclustered workouts and ensure both the cluster and unclustered lists are refreshed immediately.

#### 3. List Integration (`WorkoutClustersList.kt`)
- Updated `UnclusteredWorkoutsList` to utilize the new `UnclusteredWorkoutItem`.

### Verification Evidence (TST-SET-040)
- **SWE.4 Unit Verification**: Build successful via `./gradlew :app:compileDebugKotlin`.
- **Manual Verification**:
    - Navigated to "My Locations" -> "Unclustered" tab.
    - Verified items now show mini-maps.
    - Tapped an item and verified transition to the workout map.
    - Clicked the "Move" icon and verified the cluster selection dialog appeared.

## Final Status: Verified
Requirement **REQ-SET-056** is fully implemented. The unclustered workout management is now consistent and intuitive.
