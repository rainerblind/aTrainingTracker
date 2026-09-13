# Implementation Plan: ATT-553 - Robust Cloud Pagination & Informative Summary Breakdown

## 1. Goal Description
Resolve the issue where bulk Dropbox TCX recovery reported "Import finished" while many files remained unimported:
1. **Robust Multi-Path Folder Pagination (`LegacyImportEngine.kt`)**: Wrap `listFolder` and `listFolderContinue` in error recovery so that folders containing 1,000+ files are fully paginated across all valid paths (`/TCX` and `/apps/Workouts/TCX`) without discarding entries on transient network glitches.
2. **`RecoveryResult` Metric Object (`LegacyImportEngine.kt`)**: Return a `RecoveryResult(importedCount, skippedCount, totalScanned)` object from `bulkRecoverFromDropbox()`.
3. **Informative Summary Message (`BackupRestoreViewModel.kt`)**: Display a complete summary in the UI: `"Recovery finished. Imported X new workouts (Y skipped/already existing) out of Z files scanned."`

---

## 2. Proposed Changes

### Component 1: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`
#### [MODIFY] [LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt)
- Define data class `RecoveryResult(val importedCount: Int, val skippedCount: Int, val totalScanned: Int)`.
- Update `bulkRecoverFromDropbox(...)`:
  - Perform robust pagination across `possiblePaths` with exception isolation so entries are preserved.
  - Track `skippedCount` for files that already exist (`isWorkoutExisting == true`).
  - Return `RecoveryResult(importedCount.get(), skippedCount.get(), totalScanned)`.

### Component 2: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt`
#### [MODIFY] [BackupRestoreViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt)
- Update `bulkRecoverLegacyData(...)`:
  - Call `LegacyImportEngine.bulkRecoverFromDropbox(...)`.
  - Format success UI message: `"Recovery finished. Imported ${result.importedCount} new workouts (${result.skippedCount} skipped/already existing) out of ${result.totalScanned} files scanned."`

---

## 3. Verification Plan

### Automated Tests
- Execute `:app:testDebugUnitTest` to verify recovery result formatting and engine calculations.

### Manual Verification Steps (`TST-MIG-018`)
1. **Folder Scanning Verification**:
   - Run bulk recovery against a Dropbox directory with 1,000+ TCX files.
   - Verify all files are scanned and paginated cleanly.
2. **Summary Breakdown Verification**:
   - Observe the final success notification message.
   - Confirm it explicitly states the imported count, skipped count, and total scanned files.
