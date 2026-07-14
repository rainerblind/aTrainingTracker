# Implementation Plan: Hit Count Synchronization (SCRUM-228)

## 1. Problem Statement
Discrepancies exist between the `hitCount` field in the `RouteClusters` table and the actual number of workouts linked to a cluster in the `WorkoutSummaries` table. This results in clusters showing high hit counts (e.g., 17) but displaying no workouts in previews or heatmaps.

## 2. Requirement Mapping
| Requirement ID | Component | Test ID | Description |
|:---|:---|:---|:---|
| **REQ-SET-037** | `WorkoutClusterEngine` | **TST-SET-028** | Atomic Cluster Linking. |
| **REQ-SET-038** | `WorkoutClusterRepository` | **TST-SET-028** | Self-Healing Hit Counts. |

## 3. Impact Analysis
*   **Performance**: One additional aggregation query (`COUNT(*) GROUP BY clusterId`) during cluster refresh.
*   **Data Integrity**: Significant improvement; ensures UI always matches the actual data state.

## 4. Proposed Changes

### `WorkoutClusterEngine.kt`
*   Refactor `assignClusterToWorkout`:
    *   Add logic to increment the `hitCount` of the target cluster in `RouteClusters.db`.
    *   This ensures that end-of-session matching and manual moving both update the count.
*   Update `learnFromWorkout`:
    *   Do NOT increment `hitCount` inside this method. Instead, let the caller decide if a link should be created via `assignClusterToWorkout`.

### `WorkoutRepository.kt`
*   Update `saveWorkout`:
    *   Capture the `clusterId` returned by `engine.learnFromWorkout`.
    *   If the ID is new (different from the workout's current ID), call `engine.assignClusterToWorkout` to perform a clean link + increment.

### `WorkoutClusterRepository.kt`
*   Update `refreshClusters`:
    *   Execute a query on `WorkoutSummaries.db` to get current counts for all cluster IDs.
    *   Compare these actual counts with the `hitCount` stored in `WorkoutCluster` objects.
    *   If a mismatch is found, update the `hitCount` in `RouteClusters.db` to match reality.

## 5. Verification Plan
### Automated/Logic Verification (TST-SET-028)
1.  Verify that `assignClusterToWorkout` now updates both databases.
2.  Verify that `saveWorkout` persists the learned `clusterId`.
3.  Verify that `refreshClusters` corrects a manually induced hit count mismatch.
