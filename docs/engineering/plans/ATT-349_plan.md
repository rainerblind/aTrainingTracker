# Implementation Plan - ATT-349: Limit size of tcx ui queue

Limit the number of concurrent pending UI interactions (e.g., cluster naming requests) during a bulk TCX import to prevent excessive memory usage and ensure that new clusters are named promptly for optimal automatic grouping.

## User Review Required

> [!IMPORTANT]
> - **Queue Capacity**: The maximum number of pending interactions will be capped at **10**. 
> - **Engine Throttling**: When the queue reaches capacity (10 items), the background import engine will automatically pause (suspend) its processing for new workouts until the user resolves some of the pending tasks. 
> - **Rationale**: This low limit ensures that once you name a new cluster, the engine can immediately begin automatically assigning subsequent workouts to it, maximizing the efficiency of the import process.

## Proposed Changes

### 1. ViewModel: Backpressure Implementation
Fulfills REQ-MIG-017 | Test: TST-MIG-013

#### [MODIFY] [BackupRestoreViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt)
- **Semaphore Integration**:
    - Add a `private val interactionSemaphore = Semaphore(10)` to the ViewModel.
- **Throttled Listener**:
    - Refactor `onNewClusterCandidate` in the `createLegacyListener()` block to acquire/release the semaphore around the deferred task.

## Verification Plan

### Manual Verification (TST-MIG-013)
1. Prepare a test set of > 10 unclustered TCX workouts in Dropbox.
2. Trigger the bulk import.
3. **Verify** that the interaction queue does not exceed 10.
4. **Verify** that the background progress pauses once 10 interactions are pending.
5. Resolve one interaction and **verify** that the engine immediately processes one more file.
