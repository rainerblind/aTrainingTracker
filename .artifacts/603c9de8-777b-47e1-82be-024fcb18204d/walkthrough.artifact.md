# Walkthrough - ATT-463: Selectable Map Markers for Workout Clusters

Successfully implemented user-selectable marker visibility for Workout Clusters. This feature allows users to independently toggle Start, End, and Max Distance markers for both the cluster signature and the member session distribution heatmap, ensuring a clean and professional analytical experience.

## Fulfilled Requirements

| ID | Description | Rationale |
|:---|:---|:---|
| **REQ-SET-060** | The system SHALL allow the user to toggle the visibility of individual marker types within the Workout Cluster heatmap. | Provide professional-grade analytical control over spatial data visualization. |

## Changes Made

### 📊 Data & State Management

#### [NEW] [ClusterData.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterData.kt)
- Introduced `ClusterMarkerType` enum and `ClusterPeakMarker` DTO to encapsulate typed marker metadata.

#### [MyPreferenceManager.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/MyPreferenceManager.kt)
- Added `ENABLED_CLUSTER_MARKER_TYPES` preference key to store cluster-specific marker visibility settings independently from the Periods module.

#### [WorkoutClustersViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt)
- Refactored `ClusterMapState` to utilize typed `ClusterPeakMarker` objects for member sessions.
- Implemented background pre-calculation of markers to ensure smooth UI responsiveness during selection changes.
- Exposed `enabledMarkerTypes` StateFlow and `toggleMarkerType` function to manage reactive preference updates.

### 🗺️ UI & Visualization Layer

#### [WorkoutClusterHeatmapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt)
- **Selective Filtering**: Implemented real-time filtering for both the authoritative cluster signature markers and the member session distribution markers based on the user's active selection.
- **Marker Control UI**: Added a standard `Place` (Pin) icon button to the map overlay that triggers a checkbox-based `DropdownMenu` for marker type selection, matching the aesthetic of the Periods module. The button is correctly positioned below the Share button to prevent overlapping.
- **Unified Branding**: Utilized the project's technical color palette (Green/Red/Blue) for Start, End, and Max Distance markers to maintain visual consistency.

## Verification Results

### Integration Verification (SWE.5)
- **Test ID**: TST-SET-045 (Selectable Cluster Markers)
- **Result**: **PASS**.
    - Verified that unchecking "Start Point" hides both the large cluster start pin and all small member start markers.
    - Confirmed that marker visibility is preserved across application restarts.
    - Audited the UI in both Light and Dark modes; the dropdown menu and markers remain perfectly legible.

> [!TIP]
> This improvement brings Workout Clusters to parity with the Periods module, allowing for high-precision spatial audits of your training routes.
