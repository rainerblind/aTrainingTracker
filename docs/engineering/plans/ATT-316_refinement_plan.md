# Implementation Plan - ATT-316 Refinement: Fix Interaction Queue Synchronization

Address the issue where newly created clusters are missing from the selection list in subsequent interaction requests.

## User Review Required

> [!IMPORTANT]
> The root cause is a technical bug in the UI state management. The cluster list was being fetched only once when the dialog first appeared, instead of being refreshed for every item in the queue.

## Proposed Changes

### UI Components

#### [MODIFY] [ImportBackupTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt)
- **Fix `produceState` Bug**: Add the `state` (Interaction object) as a key to the `produceState` block in `ClusterNamingDialog`. This ensures that whenever the dialog moves to the next workout in the queue, it re-fetches the latest clusters from the database.
- **Dynamic Refresh**: Move the cluster list fetching logic to be triggered whenever `showSelectionDialog` becomes true, providing an additional layer of data freshness.

### Verification Plan

### Automated Tests
- None.

### Manual Verification
1. Start a bulk import of 3 workouts that follow the same new route.
2. For Workout #1, create a new cluster named "New Route".
3. **Verify** that for Workout #2, the selection dialog (Locations icon) now shows "New Route" in the list.
4. **Verify** that Workout #3 also has "New Route" available.
