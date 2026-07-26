# Implementation Plan - ATT-338: TCX Import: pagination support for Dropbox

Support pagination during bulk legacy recovery from Dropbox to ensure all workout files are imported, even if they exceed a single API result page.

## User Review Required

> [!IMPORTANT]
> The folder scanning process will now take slightly longer for very large folders as it must fetch all metadata pages before starting the downloads. This ensures accurate overall progress calculation.

## Proposed Changes

### Legacy Import Engine

#### [MODIFY] [LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt)
- Update `bulkRecoverFromDropbox` to handle paginated results from `dbxClient.files().listFolder(path)`.
- Implement a `while (result.hasMore)` loop using `dbxClient.files().listFolderContinue(result.cursor)` to collect all entries matching the requested format.
- Ensure `foundPath` detection correctly handles the first non-empty folder found across all its pages.

## Verification Plan

### Automated Tests
- None.

### Manual Verification
- **TST-MIG-012**:
    1. Prepare a Dropbox folder with a large number of TCX files (e.g., by copying a smaller set until > 1000 files exist).
    2. Run "Scan TCX" in the app.
    3. **Expected**: The progress indicator should reflect the total number of files found across all pages (e.g., "Scanning... 1 of 1200").
    4. Verify that the import continues past the first 1000 files.
