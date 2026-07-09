# Implementation Plan: Manually Add Cluster (SCRUM-193)

## 1. Problem Statement
Users want to manually define route clusters by specifying their spatial fingerprint (Start, End, and Apex) and distance, rather than relying solely on automated learning from recorded workouts.

## 2. Requirement Traceability
| Requirement ID | Component | Description | Test ID |
|:---|:---|:---|:---|
| **REQ-SET-019** | UI/Logic | Manual Cluster Creation from spatial fingerprints. | TST-SET-011 |

## 3. Impact Analysis
* **Core Logic**: `RouteClusterEngine` needs a method to insert a cluster directly from raw coordinates.
* **UI Layer**: A new map-based picker UI is needed to set the three spatial points.
* **Architecture**: Promoting `DropdownSelector` to a shared component to ensure UI consistency across screens.

## 4. Proposed Changes
### `RouteClusterEngine.kt`
* Implement `manuallyCreateCluster(...)` to perform unique name checks and direct database insertion.

### `FrequentPathsViewModel.kt`
* Implement `addManualCluster(...)` to bridge the manual UI with the engine and refresh the reactive cluster list.

### UI Components
* **`DropdownSelector.kt`**: Created as a shared component (extracted from `EditWorkoutScreen.kt`).
* **`ManualClusterScreen.kt`**: New screen for manual cluster definition:
    * Form for Name, Sport, and Distance.
    * Interactive map with point-selection modes for Start, End, and Apex markers.

### Main UI
* **`FrequentPathsListScreen.kt`**: Added a "+" Floating Action Button to trigger the manual creation flow.
* **`FrequentPathsFragment.kt`**: Updated navigation state to manage the new manual creation screen.

## 5. Verification Criteria (TST-SET-011)
1. Open "Frequent Paths".
2. Tap the "+" FAB.
3. Enter Name: "Test Trail", Sport: "Run", Distance: "5000".
4. Use the map to pick 3 points.
5. Tap "Save".
6. Verify "Test Trail" appears in the cluster list.
