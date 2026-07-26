# Implementation Plan - ATT-320: Default to daily backup

Change the default automated backup interval from 3 days to daily (1 day).

## User Review Required

> [!NOTE]
> This change only affects the **default** setting for new installations or users who haven't explicitly changed their backup interval. Users who have already configured a different interval will retain their current preference.

## Proposed Changes

### Configuration

#### [MODIFY] [prefs_dropbox.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/xml/prefs_dropbox.xml)
- Change `android:defaultValue` of `backup_interval_days` from `"3"` to `"1"`.

### Migration & Background Logic

#### [MODIFY] [BackupRestoreViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt)
- Update the default value for `backupIntervalDays` initialization to use `"1"` and `1` as fallback.

#### [MODIFY] [BackupWorker.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupWorker.kt)
- Update the default value for `intervalDays` in the `schedule` method to use `"1"` and `1L` as fallback.

## Verification Plan

### Automated Tests
- None.

### Manual Verification
- **New Install Simulation**: Clear app data and verify that the "Backup Interval" in Dropbox settings defaults to "Daily".
- **Scheduling Audit**: Verify that `BackupWorker.schedule` uses 1 day as the interval if no preference is stored.
