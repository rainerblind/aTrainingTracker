# Walkthrough: Individual Workout Markers in Route Clusters (SCRUM-199)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-SET-022** | Show individual workout markers (Start, Stop, Extrema) in the cluster peek. | Verified |

## 2. Verification Evidence (TST-SET-014)
* **Interaction**:
    * Navigated to **Regular Tracks > Route Heatmap**.
* **Observation**:
    * The map now displays a "cloud" of markers for **every member workout** in the cluster.
    * **Start (Green)**, **Stop (Red)**, and **Apex (Blue)** markers are shown with subtle transparency (0.3 alpha) to visualize spatial drift across the cluster's history.
* **Peek Interaction**:
    * Tapped a specific workout track.
    * Tapped an individual member marker (Start/Stop/Apex).
    * In both cases, the Bottom Sheet "peek" expanded, showing full-opacity detailed markers (including sensor extrema) for that specific recording.
* **Result**: **PASS**

## 3. Technical Changes
### Core & Repository
* **`WorkoutRepository.kt`**: 
    * Centralized marker calculation logic from the ViewModels into the repository.
    * Added `getWorkoutMarkers(WorkoutData)` to compute all spatial metadata markers (Start, End, and Extrema) for a given workout.
* **`WorkoutData.kt`**: Updated `WorkoutDataWithTrack` to include a `markers: List<LocationMarker>` field.

### UI & Interaction
* **`FrequentPathsViewModel.kt`**: Updated `selectWorkoutForPeek` to load markers using the new repository method.
* **`FrequentPathHeatmapScreen.kt`**: Passed the loaded markers to the `TrackOnMapScreen` within the peek sheet.
* **`TrackOnMapAftermathViewModel.kt`**: Refactored to utilize the centralized repository method for marker loading, reducing code duplication.
