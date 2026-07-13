# Walkthrough: Manual Workout Cluster Reassignment (SCRUM-180)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-SET-017** | Manual Workout Cluster Reassignment via Heatmap. | Verified |

## 2. Verification Evidence (TST-SET-009)
* **Interaction**:
    * Navigated to **Regular Tracks > Heatmap**.
    * Tapped on an individual workout track on the map.
    * A dialog titled "Move Workout" appeared, listing all existing clusters sorted by similarity score.
* **Recalculation**:
    * Selected a different cluster as the target.
    * Tapped **Save**.
    * The workout was successfully moved:
        * Source cluster's centroids were updated (removing the workout's spatial weight).
        * Target cluster's centroids were updated (adding the workout's spatial weight).
        * Workout's `clusterId` was updated in the summaries database.
    * The UI correctly refreshed, showing the workout in its new home.
* **Result**: **PASS**

## 3. Technical Implementation
### Mathematical Integrity
* Implemented the **Moving Average Reversal** formula in `RouteClusterEngine.moveWorkoutToCluster`:
    * `newVal = (oldAvg * count - workoutVal) / (count - 1)`
* This ensures that manually moving a workout doesn't leave "ghost" data in the old cluster's centroid.

### UX & Transparency
* The reassignment dialog displays the **Similarity Score** for each candidate (lower = better spatial match).
* This provides transparency into why the engine made its original suggestion and helps users make informed corrections.
