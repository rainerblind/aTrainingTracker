# Implementation Plan - ATT-316: Importing: Queue user interaction

Decouple user interaction requests (e.g., cluster naming) from the primary import status to ensure progress visibility and sequential request handling.

## User Review Required

> [!IMPORTANT]
> Currently, when the system needs a cluster decision, it switches the entire UI state to `ClusterNamingRequired`, which hides the progress bar. This change will allow the progress card to stay visible behind the naming dialog.

## Proposed Changes

### State Management

#### [MODIFY] [BackupRestoreViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt)
- Remove `ClusterNamingRequired` from the `UiState` sealed class.
- Add `activeInteraction` StateFlow of type `ClusterInteraction?`.
- Update `onNewClusterCandidate` listener to set `activeInteraction` and wait for completion, while keeping `_uiState` in its previous `Progress` or `Loading` state.

### UI Components

#### [MODIFY] [ImportBackupTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt)
- Update the observation logic to check for `activeInteraction` independently of `uiState`.
- Ensure the `ClusterNamingDialog` is shown as an overlay that doesn't block the rendering of the `StateOverlaySection` (progress card).

## Verification Plan

### Automated Tests
- None.

### Manual Verification
- **TST-PRO-004** (Jira [ATT-333](https://atrainingtracker.atlassian.net/browse/ATT-333)):
    1. Start a "Scan TCX" with multiple new routes in Dropbox.
    2. Verify that when the naming dialog appears, the progress card (e.g., "Importing file 1/10...") remains visible in the background.
    3. Verify that dismissing the dialog (or confirming) allows the progress to continue or shows the next dialog if applicable.
