# Implementation Plan - ATT-315: Set clustering parameters before importing

Provide a tuning dialog that appears immediately before starting a TCX import, allowing users to optimize route detection parameters.

## User Review Required

> [!NOTE]
> This change introduces a mandatory "Pre-Import Tuning" step. When a user clicks "Scan TCX" or "Import Single TCX", they will see a dialog with sensitivity sliders. This ensures high-quality route matching for legacy data.

## Proposed Changes

### UI Components

#### [MODIFY] [ClusterTuningScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterTuningScreen.kt)
- Extract the core slider logic and "Master" sensitivity calculation into a reusable `@Composable fun ClusterTuningContent`.
- This component will be shared between the full-screen tuning view and the new import dialog.

#### [MODIFY] [ImportBackupTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt)
- Implement a new `ClusterTuningDialog` that wraps `ClusterTuningContent`.
- Update `ImportTabContent` to trigger this dialog when legacy import actions are initiated.
- Upon confirmation in the dialog, proceed with the actual bulk recovery or single file selection.

### Migration & State Logic

#### [MODIFY] [BackupRestoreViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt)
- Add `endpointTolerance`, `apexTolerance`, and `distanceTolerance` as `MutableState`.
- Initialize these from `TrainingApplication` defaults.
- Implement persistence: save these values to `SharedPreferences` when the user confirms the tuning dialog, ensuring the `WorkoutClusterEngine` operates with the chosen settings.

## Verification Plan

### Automated Tests
- None.

### Manual Verification
- **TST-MIG-009** (Jira [ATT-329](https://atrainingtracker.atlassian.net/browse/ATT-329)):
    1. Navigate to 'Import & Backup' -> 'Import'.
    2. Tap "Scan TCX".
    3. **Expected**: A dialog appears with clustering sensitivity sliders.
    4. Adjust the sliders and tap "Start Scan".
    5. Verify that the import process begins using the chosen tolerances.
    6. Verify that these settings are now also visible in 'My Locations' -> 'Tuning'.
