# Implementation Plan - ATT-463: Selectable Map Markers for Workout Clusters

Introduce user-selectable marker visibility for Workout Clusters, mirroring the professional analytical functionality of the Periods module. This allows users to toggle Start, End, and Max Line Distance (Apex) markers independently for both cluster signatures and member distribution heatmaps.

## User Review Required

> [!IMPORTANT]
> - **Independent Preferences**: As requested, marker preferences for Workout Clusters will be stored separately from Periods. Changes in one module will not affect the other.
> - **Unified Iconography**: I will use the established technical markers: Green Pins (Start), Red Pins (End), and Blue Pins (Apex/Distance) to maintain project-wide visual parity.
> - **Signature Filtering**: By default, toggling a marker type (e.g., "Start") will hide both the cluster's authoritative start point AND the start points of all associated member sessions to ensure a clean analytical view.

## Proposed Changes

### 1. Data Foundation & Models
#### [NEW] [ClusterData.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterData.kt)
- Define `ClusterMarkerType` enum: `START`, `END`, `DISTANCE`.
- Define `ClusterPeakMarker` DTO to encapsulate marker metadata (workoutId, position, type).

### 2. Preference Management
#### [MODIFY] [MyPreferenceManager.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/MyPreferenceManager.kt)
- Add `ENABLED_CLUSTER_MARKER_TYPES` key.
- Implement `enabledClusterMarkerTypesFlow` (Default: All enabled).
- Implement `setClusterMarkerTypeEnabled` function.

### 3. ViewModel Logic
#### [MODIFY] [WorkoutClustersViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt)
- Update `ClusterMapState` to utilize `List<ClusterPeakMarker>` for `memberMarkers`.
- Expose `enabledMarkerTypes: StateFlow<Set<ClusterMarkerType>>`.
- Implement `toggleMarkerType(type: ClusterMarkerType)`.
- Refactor `selectCluster` background processing to generate `ClusterPeakMarker` objects for member workouts.

### 4. UI Layer: Reactive Filtering & Controls
#### [MODIFY] [WorkoutClusterHeatmapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt)
- **Implement Marker Filtering**:
    - Filter `fingerprintMarkers` (Signature) based on `enabledMarkerTypes`.
    - Filter `memberMarkers` (Distribution) based on `enabledMarkerTypes`.
- **Add Controls**:
    - Add a `Place` (Pin) icon button to the map overlay.
    - Implement a `DropdownMenu` with checkboxes for Start, End, and Max Distance, matching the `PeriodMapScreen` aesthetic.
- **Conversion**: Map the filtered `ClusterPeakMarker` list to `LocationMarker` for the `ATrainingTrackerMap` content DSL.

## Verification Plan

### Automated Tests
#### [NEW] [TST-SET-045](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md)
- **Procedure**:
    1. Open a Workout Cluster heatmap.
    2. Tap the Marker Options button.
    3. Uncheck "Start Point".
    4. **Verify** that all green pins (both large signature and small member markers) disappear from the map.
    5. Re-check and **Verify** reappearance.
    6. Close app and reopen; **Verify** preference persistence.

### Manual Verification
- Audit the UI on both Light and Dark modes to ensure the dropdown menu and markers remain legible.
- Verify that toggling markers does not interrupt the background loading of workout tracks.
