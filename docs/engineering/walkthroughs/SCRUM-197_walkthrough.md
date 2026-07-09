# Walkthrough: Manual Fingerprint Editing (SCRUM-197)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-SET-021** | Manual adjustment of cluster signature points (Start, End, Apex). | Verified |

## 2. Verification Evidence (TST-SET-013)
* **Interaction**:
    * Navigated to **Frequent Paths > Select Cluster**.
    * Tapped the new **Edit Location (Map Pin with Pencil)** icon in the TopAppBar.
    * The UI entered "Fingerprint Edit Mode":
        * TopAppBar color changed to surfaceVariant.
        * A guidance overlay appeared at the bottom.
        * The Bottom Sheet (peek) was hidden to maximize map area.
* **Editing**:
    * Tapped the **Start** button in the overlay.
    * Tapped a new location on the map.
    * The Green marker immediately jumped to the new tap location.
    * Repeated for **End** (Red) and **Apex** (Blue).
* **Persistence**:
    * Tapped the **Save (Disk)** icon in the TopAppBar.
    * Mode deactivated and cluster was updated.
    * Verified in the cluster list that the mini-map preview reflects the new spatial signature.
* **Result**: **PASS**

## 3. Technical Changes
### UI & Interaction
* **`FrequentPathHeatmapScreen.kt`**:
    * Implemented `FingerprintEditMode` state machine (NONE, START, END, APEX).
    * Added a dedicated edit action to the TopAppBar.
    * Developed a guidance overlay with color-coded toggle buttons for point selection.
    * Wired the `onMapClick` handler to update the transient `editStart/End/Apex` coordinates.
    * Ensured that "Peek" functionality is disabled while editing to prevent interaction conflicts.

### Architecture
* **`MapDetailLayout.kt` & `ATrainingTrackerMap.kt`**: Upgraded the unified map layout to support raw map click callbacks (`onMapClick: (LatLng) -> Unit`), enabling spatial picking across multiple features.

### ViewModel
* **`FrequentPathsViewModel.kt`**: Added `updateClusterFingerprint` to persist the manually refined coordinates to the database.
