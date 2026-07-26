# Implementation Plan - ATT-309: EditWorkout: Select cluster but do not rename

Ensure that selecting or changing a workout name in the `EditWorkout` screen does not automatically rename the associated `WorkoutCluster` (route identity), while still allowing for centroid and sport refinement.

## User Review Required

> [!IMPORTANT]
> The "Learning Loop" in `WorkoutRepository#saveWorkout` currently attempts to update the cluster name based on the workout name. We will refine this logic to be more conservative.

## Proposed Changes

### Core Logic

#### [MODIFY] [WorkoutClusterEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt)
- Update `learnFromWorkout` to avoid renaming the cluster if it already has a non-default name.
- Add logic to detect "default names" (e.g., names matching the pattern `cluster_default_name_format`).

#### [MODIFY] [WorkoutRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt)
- Refine the "LEARNING LOOP" in `saveWorkout` to ensure it only propagates name changes to clusters if the cluster currently has a generic name.

## Verification Plan

### Automated Tests
- None planned for this unit, focus on manual verification of integration.

### Manual Verification
- **TST-SET-042**:
    1. Track a workout matching cluster 'Park Loop'.
    2. Open 'Edit Workout' screen.
    3. Change name to 'Morning Run'.
    4. Save.
    5. **Expected**: Workout is named 'Morning Run', but Cluster name remains 'Park Loop'.
