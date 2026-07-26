# Implementation Plan - ATT-360: Concurrency Safety for Cluster Selection

Resolve the race condition where a previously selected Workout Cluster would sometimes overwrite a more recent selection in the UI.

## User Review Required

> [!IMPORTANT]
> - **Job Cancellation**: The system will now explicitly cancel any pending data loading or map preparation tasks when the user selects a new cluster or navigates away.
> - **Active Check**: High-fidelity spatial processing will be made "cancellation-aware" to ensure that CPU resources are immediately released for stale tasks.

## Proposed Changes

### ViewModel Concurrency Management
Fulfills REQ-PER-002 | Test: TST-BUG-001

#### [MODIFY] [WorkoutClustersViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt)
- Declare `private var selectionJob: Job? = null`.
- Refactor `selectCluster(cluster: WorkoutCluster?)`:
    - Invoke `selectionJob?.cancel()` at the very beginning of the method.
    - Assign the new `viewModelScope.launch` to `selectionJob`.
    - Within the `withContext(Dispatchers.Default)` block, call `ensureActive()` periodically (e.g., during the markers flatMap and heatmap processing) to guarantee rapid cancellation responsiveness.

## Verification Plan

### Manual Verification (TST-BUG-001)
1. Open the Workout Cluster list.
2. Select Cluster A (large family).
3. Immediately go back and select Cluster B.
4. **Verify** that only Cluster B's data is shown when loading completes.
5. **Verify** in LogCat that the background processing for Cluster A was correctly cancelled.
