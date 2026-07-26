# Implementation Plan - ATT-322: Enable automated backup by default

Enable the automated Dropbox backup feature by default to ensure data safety out-of-the-box.

## User Review Required

> [!IMPORTANT]
> Enabling automated backups by default means the system will attempt to schedule backups even before a user has configured Dropbox. The `BackupWorker` already handles missing credentials gracefully by failing/stopping, but it's worth noting that the "Automated Backups" switch will be ON by default.

## Proposed Changes

### Configuration

#### [MODIFY] [prefs_dropbox.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/xml/prefs_dropbox.xml)
- Change `android:defaultValue` of `automated_backups` from `"false"` to `"true"`.

### Migration & Background Logic

#### [MODIFY] [BackupRestoreViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt)
- Update the default value for `automatedBackupsEnabled` initialization to use `true` as fallback.

#### [MODIFY] [BackupWorker.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupWorker.kt)
- Update the default value for `automatedEnabled` in the `schedule` method to use `true` as fallback.

## Verification Plan

### Automated Tests
- None.

### Manual Verification
- **New Install Simulation**: Clear app data and verify that the "Automated Backups" switch in Dropbox settings is ON by default.
- **Scheduling Audit**: Verify that `BackupWorker.schedule` considers backups enabled if no preference is stored.
- **Credential Safety**: Verify that if automated backup is enabled but no Dropbox account is linked, the background task does not cause crashes or unwanted side effects (already handled by existing `TrainingApplication.readDropboxCredential()` check in `BackupWorker`).
