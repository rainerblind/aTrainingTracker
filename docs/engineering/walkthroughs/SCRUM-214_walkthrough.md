# Walkthrough: Improved Route Cluster Selection Layout (SCRUM-214)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-SET-018** | Improved layout for cluster suggestions in Edit Workout. | Verified |
| **REQ-SET-017** | Improved layout for manual workout cluster reassignment. | Verified |

## 2. Verification Evidence (TST-SET-010 / TST-SET-009 Refinement)
* **Edit Workout Suggestions**:
    * Navigated to **Edit Workout**.
    * Tapped the "My Locations" icon.
    * **Observation**:
        * **Identity Row**: Cluster name is appropriately sized (`titleMedium`) and bold. The similarity score is right-aligned in brackets (e.g., `(0.015)`) in a smaller font (`bodySmall`).
        * **Long Names**: Verified that long cluster names are truncated with ellipsis and do not push the score to a new line.
        * **Correct Branding**: The **Top-Aligned sport icon** (32dp) correctly matches the route's determined sport.
        * **Sport Row**: The sport type name is shown clearly on its own row.
        * **Hit Count Row**: The number of recordings is displayed on a third row in **primary blue** and is not bold.
        * **Separation**: Verified a horizontal divider between each suggestion for improved legibility.
* **Manual Reassignment**:
    * Opened a route heatmap and tapped a track to move it.
    * **Observation**:
        * The "Move Workout" dialog now uses the same improved layout as the edit workout suggestions.
* **Result**: **PASS**

## 3. Technical Changes
### ViewModels
* **`EditWorkoutViewModel.kt`**:
    * Added `getSportName(sportId)` and `getBSportType(sportId)` helpers to facilitate rich metadata display in the suggestions list.

### UI & Layout
* **`EditWorkoutScreen.kt`**:
    * Refactored the `DropdownMenuItem` layout for cluster suggestions.
    * Promoted Name and Score to a top row with larger typography.
    * Added a metadata row with Sport Name and color-coded Hit Count.
    * Fixed the sport icon resolution logic to use database-mapped `BSportType`.
* **`FrequentPathHeatmapScreen.kt`**:
    * Updated `MoveWorkoutClusterDialog` to use the same harmonized layout.
    * Injected `viewModel` into the dialog to resolve sport names and icons.
