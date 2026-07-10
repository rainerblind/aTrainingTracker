# Walkthrough: Manual Sport Type Editing for Route Clusters (SCRUM-203)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-SET-016** | Manual adjustment of cluster name and sport type. | Verified |

## 2. Verification Evidence (TST-SET-008 - Refinement)
* **Interaction**:
    * Navigated to **Frequent Paths > Route Heatmap**.
    * Tapped the **Edit (Pencil)** icon in the TopAppBar.
    * The **Edit Cluster Identity** dialog appeared.
* **Editing**:
    * Entered a new name.
    * Selected a different sport type from the dropdown selector.
    * Tapped **Save**.
* **Observation**:
    * The TopAppBar title and sport icon updated immediately.
    * Navigated back to the list view; confirmed the cluster shows the new name and sport.
* **Result**: **PASS**

## 3. Technical Changes
### UI & Interaction
* **`FrequentPathHeatmapScreen.kt`**:
    * Renamed `RenameClusterDialog` to `EditClusterIdentityDialog`.
    * Added `DropdownSelector` for sport type selection within the dialog.
    * Updated the dialog to initialize with the cluster's current name and sport.

### ViewModel
* **`FrequentPathsViewModel.kt`**:
    * Renamed `renameCluster` to `updateClusterIdentity`.
    * Updated the method to accept both `newName: String` and `newSportId: Long`.
    * Correctly updates the `RouteCluster` and refreshes the UI state.
