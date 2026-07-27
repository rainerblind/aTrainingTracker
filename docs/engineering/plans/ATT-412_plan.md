# Implementation Plan - ATT-412: Enforce Strict Sport-Type Penalty

Correct the workout clustering engine to strictly apply the sport-type similarity penalty even when one of the entities has an 'UNKNOWN' sport type. This ensures that different activities are correctly isolated into separate clusters.

## User Review Required

> [!IMPORTANT]
> - **Strict Isolation**: I will remove the logic that currently "skips" the penalty if a cluster is marked as 'UNKNOWN'. 
> - **Rationale**: If you have a 'Running' workout and it's compared to an 'UNKNOWN' cluster, the system will now correctly add the **+2.0 penalty**. This prevents your new workouts from being merged into generic legacy clusters, forcing the creation of a proper sport-specific group.
> - **Total Consistency**: This follows your explicit directive: "When the BSportType are not identical, a value of 2 is added."

## Proposed Changes

### 1. Core Logic: Strict Similarity Comparison
Fulfills REQ-SET-043 | Test: TST-SET-035

#### [MODIFY] [WorkoutClusterEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt)
- **Refine `calculateSimilarity`**:
  ```kotlin
  // ATT-350: Sport Type Awareness
  if (TrainingApplication.useSportTypeForClustering()) {
      if (workoutSportType != cluster.bSportType) {
          totalScore += 2.0 // Heavy penalty for mismatched sports (including UNKNOWN)
      }
  }
  ```
- **Result**: The penalty is now applied whenever the sports are not an exact match. 

## Verification Plan

### Manual Verification (TST-SET-035)
1. Ensure 'Take Sport Type into account' is ON in Tuning.
2. Identify a Workout Cluster with sport type 'UNKNOWN'.
3. Record or Import a workout with sport type 'Running' on the same path.
4. **Verify** that the 'Running' workout creates a NEW cluster instead of joining the 'UNKNOWN' one.
5. **Verify** that the 'UNKNOWN' cluster remains unchanged.
