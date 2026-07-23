# Implementation Plan - ATT-316: Decouple Import Progress from User Interactions

Establish a non-blocking import workflow where background file processing (download and raw import) continues while the system waits for user clustering decisions.

## User Review Required

> [!IMPORTANT]
> This change decouples the **File Import** (downloading and parsing) from the **Clustering Workflow** (naming routes).
> - The progress bar will reflect the speed of the background import.
> - Clustering requests will be queued and presented sequentially without stopping the import of subsequent files.
> - This might lead to multiple naming prompts for similar routes if the user doesn't respond quickly, as subsequent files cannot "match" a cluster that hasn't been created yet.

## Proposed Changes

### Legacy Import Engine

#### [MODIFY] [LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt)
- Refactor `importFromTcx` to separate the raw database insertion from the `recalculateStats` logic.
- Ensure that `bulkRecoverFromDropbox` can initiate the raw import and then delegate the clustering to a background process (or continue the loop while `recalculateStats` runs).
- *Correction*: To maintain the "Learning Loop" benefit, `recalculateStats` should still run sequentially for each file, but it should run in a separate coroutine from the main "Download & Parse" loop.

### State Management & ViewModel

#### [MODIFY] [BackupRestoreViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt)
- Implement a `interactionQueue: MutableStateFlow<List<ClusterInteraction>>`.
- Refactor `onNewClusterCandidate` to:
    1. Append the new request to the `interactionQueue`.
    2. Wait for the decision (using a `CompletableDeferred` stored in the interaction object).
- Update the `activeInteraction` derived state to always return the first item in the queue.

### UI Components

#### [MODIFY] [ImportBackupTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt)
- Update `ClusterNamingDialog` to handle the queued state.
- Ensure the progress card (from `uiState`) remains visible and updates while the dialog is shown.

## Verification Plan

### Automated Tests
- None.

### Manual Verification
- **TST-PRO-004** (Jira [ATT-333](https://atrainingtracker.atlassian.net/browse/ATT-333)):
    1. Start a "Scan TCX" for 10 files where the first 5 need naming.
    2. Observe that the naming dialog for file #1 appears.
    3. **Verify** that the progress bar behind the dialog continues to move as files 6-10 are downloaded and imported.
    4. Complete naming for file #1 and verify that the dialog for file #2 appears immediately.
