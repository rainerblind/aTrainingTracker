# Implementation Plan - ATT-311: Remove CSV Import

Remove the legacy CSV import functionality from the application while preserving CSV export capabilities.

## User Review Required

> [!IMPORTANT]
> This plan strictly removes the **CSV Import** logic. **CSV Export** remains functional.
> We will also remove the `CSVReader` and `CSVParser` utility classes as they are exclusively used for importing.

## Proposed Changes

### Migration & Import Logic

#### [MODIFY] [LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt)
- Remove `importFromCsv` method.
- Update `bulkRecoverFromDropbox` to remove "csv" from supported formats and possible paths.
- Remove `au.com.bytecode.opencsv.CSVReader` import.

#### [MODIFY] [BackupRestoreViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt)
- Update `importLegacyFile` to remove the "csv" branch.
- Update `bulkRecoverLegacyData` to remove the "csv" branch (caller should not pass "csv" anymore).

### UI Components

#### [MODIFY] [BackupRestoreScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreScreen.kt)
- Remove the "Scan CSV" `OutlinedButton`.
- Update `pickLegacyFileLauncher` to strictly handle "tcx" (remove the conditional logic for "csv").

### Cleanup

#### [DELETE] [CSVReader.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/au/com/bytecode/opencsv/CSVReader.java)
- Remove unused utility.

#### [DELETE] [CSVParser.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/au/com/bytecode/opencsv/CSVParser.java)
- Remove unused utility (only used by `CSVReader`).

#### [MODIFY] [strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values/strings.xml) (and all translations)
- Remove `scan_csv` string resource.

## Verification Plan

### Automated Tests
- None.

### Manual Verification
- **Build Verification**: Ensure the app compiles successfully after removing the files.
- **Import UI Audit**: Navigate to 'Backup & Restore' and verify that the "Scan CSV" button is gone.
- **TCX Verification**: Ensure "Scan TCX" still works correctly for bulk recovery.
- **Single File Verification**: Verify that "Import single legacy file" still works for TCX files.
- **Export Verification**: Verify that exporting a workout to CSV still works (as the `CSVWriter` and `CSVFileWriter` are preserved).
