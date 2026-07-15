# Walkthrough: Workout Peek in Heatmap (SCRUM-196)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-SET-020** | Workout Peek with reassignment option in heatmap. | Verified |

## 2. Verification Evidence (TST-SET-012)
* **Interaction**:
    * Navigated to **Favorite Tracks > Heatmap**.
    * Tapped on an individual workout track on the map.
    * A Bottom Sheet "peek" appeared, showing the workout header and its specific track (similar to the Periods view).
* **Reassignment**:
    * Tapped the **Move (Swap)** FAB within the Bottom Sheet.
    * The "Move Workout" dialog appeared correctly.
    * Selected a different cluster and saved.
    * The Bottom Sheet closed and the workout was reassigned.
* **Result**: **PASS**

## 3. Technical Changes
### UI & Interaction (Refined)
* **`FrequentPathHeatmapScreen.kt`**:
    * Integrated `BottomSheetScaffold` to provide the peek behavior.
    * Integrated the **Move (Swap)** action directly into the `WorkoutHeader` area of the peek sheet for a cleaner, non-overlapping UI.
    * Updated `onPathClick` to trigger the peek state in the ViewModel.
    * Handled back-press behavior to dismiss the peek before exiting the screen.

### ViewModel & Repository
* **`FrequentPathsViewModel.kt`**: Added state and logic for `peekedWorkoutDataWithTrack`.
* **`RouteClusterRepository.kt`**: Added `getWorkoutTrackPoints` helper to fetch rich path data for the peeked workout.
