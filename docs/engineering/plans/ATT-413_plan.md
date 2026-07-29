# Implementation Plan - ATT-413: Reliable Hit Count Synchronization

Correct the issue where Workout Clusters show incorrect (often zero) hit counts in the suggestion dialog by strictly utilizing the repository's self-healing layer and fixing redundant increment bugs.

## User Review Required

> [!IMPORTANT]
> - **Self-Healing Integration**: The "Assign to Route" dialog will now use the **WorkoutClusterRepository** instead of raw database access. This ensures that every time you see a suggestion, the app has already verified that the "Number of recordings" matches the actual sessions in your history.
> - **Logic Cleanup**: I will fix a hidden bug that was causing the hit count to be incremented twice in some cases, ensuring that your route statistics remain mathematically correct.

## Proposed Changes

### 1. UI Layer: Repository Integration
Fulfills REQ-SET-038 | Test: TST-SET-044

#### [MODIFY] [ImportBackupTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt)
- **Refactor `ClusterNamingDialog`**:
  - Replace the direct `WorkoutClusterDatabaseManager.getAllClusters()` call with `WorkoutClusterRepository.getInstance(localContext).refreshClusters()`.
  - Use the repository's `allClusters` flow to provide the candidate list.
  - **Rationale**: This triggers the repository's Phase 1 "Self-Healing" integrity check, which recalculates the redundant `hitCount` column based on the actual workout summary table.

### 2. Core Logic: Double-Counting Fix
Fulfills REQ-SET-038 | Test: TST-SET-044

#### [MODIFY] [WorkoutClusterEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt)
- **Refactor `migrateHistory`**:
  - Remove the manual `hitCount + 1` increment when a match is found. 
  - **Rationale**: `assignClusterToWorkout` (called immediately after) already handles the atomic increment of the hit count. Removing the manual step prevents over-counting.

## Verification Plan

### Manual Verification (TST-SET-044)
1. Ensure you have a cluster with at least 1 workout.
2. Trigger a TCX import interaction for a similar route.
3. **Verify** that the "Select Existing Route" dialog displays the correct number of recordings (not 0).
4. Verify in the cluster list that hit counts don't "jump" by more than 1 when assigning a single workout.
