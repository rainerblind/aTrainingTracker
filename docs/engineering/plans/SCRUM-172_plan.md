# Implementation Plan: Route Cluster Visualization (SCRUM-172)

## 1. Problem Statement
The spatial learning engine (SCRUM-44) populates a knowledge base of route families, but these are currently "invisible" to the user. To leverage this intelligence, the system needs a UI to browse learned routes and visualize them as spatial heatmaps.

## 2. Requirement Traceability
| Requirement ID | Component | Description | Test ID |
|:---|:---|:---|:---|
| **REQ-SET-009** | UI | Favorite Tracks Heatmap visualization with fingerprint markers. | TST-SET-002 |

## 3. Impact Analysis
* **UI Structure**: Re-enables the primary navigation drawer entry for "Favorite Tracks".
* **Map Performance**: Rendering multiple polyline layers (heatmap) may stress the GPU on low-end devices. Mitigation: Use simplified tracks for background rendering.
* **Navigation**: Introduces a new drill-down pattern from Cluster List to Heatmap Detail.

## 4. Proposed Changes
### UI Layer
* **`FrequentPathsFragment.kt`**: Fragment container for the cluster UI.
* **`FrequentPathsViewModel.kt`**: ViewModel to fetch clusters and associated workouts.
* **`FrequentPathsListScreen.kt`**: Compose list of learned route families.
* **`FrequentPathHeatmapScreen.kt`**: full-screen map with track overlays and spatial pins.

### Navigation
* **`MainActivityWithNavigation.java`**: Wired to the new fragment under `drawer_my_locations`.

### Resources
* **`strings.xml`**: Update "My Locations" to "Favorite Tracks".

## 5. Verification Criteria (TST-SET-002)
1. Open the Navigation Drawer.
2. Tap "Favorite Tracks".
3. Verify the list of learned routes is displayed.
4. Select a route (e.g., "Commute").
5. Verify the map shows:
    * Multiple tracks (the heatmap effect).
    * Three markers: Start (Green), End (Red), Apex (Blue).
    * Summary card at the bottom with recordings count and ref distance.
