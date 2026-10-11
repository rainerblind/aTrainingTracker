# ATT-3168 Walkthrough: Organize Google Drive workouts into format-specific directories (FIT, TCX, GPX) for export and import

## Overview
- **Issue**: [ATT-3168](https://atrainingtracker.atlassian.net/browse/ATT-3168)
- **Sub-task**: [ATT-3271](https://atrainingtracker.atlassian.net/browse/ATT-3271) (`[Test]`)
- **Parent Epic**: [ATT-162](https://atrainingtracker.atlassian.net/browse/ATT-162) (*Cloud integration*)
- **Target Release**: `V4.9.39`
- **Requirement**: `REQ-DAT-024`
- **Test Specification**: `TST-DAT-019`
- **Sprint**: `2026-41.10`
- **Status**: Verified

---

## 1. Problem Addressed
Previously, Google Drive export uploaded workout files into a single flat directory: `aTrainingTracker/Workouts/`. In contrast, Dropbox export organized workout files into format-specific subdirectories:
- `aTrainingTracker/FIT/`
- `aTrainingTracker/TCX/`
- `aTrainingTracker/GPX/`

This discrepancy created cloud parity inconsistencies, cluttered the flat `Workouts/` directory when multiple formats were exported, and required scanning unrelated file types during format-filtered recovery. Furthermore, historical workouts previously placed in `aTrainingTracker/Workouts/` needed backward-compatible recovery without generating duplicate workout imports.

---

## 2. Architectural Changes & Solution

### 1. Dynamic Format Directory Upload Resolution (`GoogleDriveUploader.kt`)
In `GoogleDriveUploader.kt`, replaced hardcoded `listOf("aTrainingTracker", "Workouts")` with format-driven dynamic hierarchy resolution:
```kotlin
val folderHierarchy = listOf(
    "aTrainingTracker",
    exportInfo.fileFormat.dirName.takeIf { it.isNotBlank() } ?: "Workouts"
)
```
- For `FileFormat.FIT`: Uploads to `["aTrainingTracker", "FIT"]`.
- For `FileFormat.TCX`: Uploads to `["aTrainingTracker", "TCX"]`.
- For `FileFormat.GPX`: Uploads to `["aTrainingTracker", "GPX"]`.
- For `FileFormat.CSV`: Uploads to `["aTrainingTracker", "CSV"]`.
- For unknown/empty `dirName`: Defensively falls back to `["aTrainingTracker", "Workouts"]`.

### 2. Format-Scoped Prioritized Candidate Folders (`LegacyImportEngine.kt`)
In `LegacyImportEngine.kt`, modified `bulkRecoverFromGoogleDrive()` to prioritize format-specific folders followed by legacy fallbacks:
```kotlin
val candidateFolderHierarchies: List<List<String>> = when (format.lowercase()) {
    "fit" -> listOf(
        listOf("aTrainingTracker", "FIT"),
        listOf("aTrainingTracker", "Workouts"),
        listOf("aTrainingTracker")
    )
    "tcx" -> listOf(
        listOf("aTrainingTracker", "TCX"),
        listOf("aTrainingTracker", "Workouts"),
        listOf("aTrainingTracker")
    )
    "gpx" -> listOf(
        listOf("aTrainingTracker", "GPX"),
        listOf("aTrainingTracker", "Workouts"),
        listOf("aTrainingTracker")
    )
    else -> listOf(
        listOf("aTrainingTracker", "FIT"),
        listOf("aTrainingTracker", "TCX"),
        listOf("aTrainingTracker", "GPX"),
        listOf("aTrainingTracker", "Workouts"),
        listOf("aTrainingTracker")
    )
}
```

### 3. Cross-Folder Filename Deduplication (`LegacyImportEngine.kt`)
Preserved cross-folder base filename deduplication (`removeSuffix("-TMP").removeSuffix("~").lowercase()`):
- If an activity exists in both `aTrainingTracker/FIT/` and `aTrainingTracker/Workouts/`, it is discovered in the format folder first and skipped in subsequent folders, guaranteeing zero duplicate imports.

---

## 3. Verification & Test Results

### 1. Targeted Unit Tests
- `GoogleDriveUploaderTest.kt`:
  - `testDoExport_uploadSuccess_updatesTimestampAndReturnsSuccess`: Verified export of FIT file resolves to `listOf("aTrainingTracker", "FIT")`.
  - `testDoExport_formatSpecificFolders_tcxGpxCsvTargetRespectiveDirectories`: Verified exports of TCX, GPX, and CSV target their respective subdirectories.
  - All tests passed (100%).
- `LegacyImportEngineGoogleDriveFolderResolutionTest.kt`:
  - `testCandidateFolders_forFit_prioritizesFitThenLegacy`: Verified format "fit" prioritizes FIT folder.
  - `testCandidateFolders_forTcx_prioritizesTcxThenLegacy`: Verified format "tcx" prioritizes TCX folder.
  - `testCandidateFolders_forGpx_prioritizesGpxThenLegacy`: Verified format "gpx" prioritizes GPX folder.
  - `testCandidateFolders_forAll_scansAllFormatFoldersThenLegacy`: Verified format "all" scans all format folders then legacy.
  - `testDeduplication_crossFolderActivity_preventsDuplicates`: Verified deduplication logic across candidate folders.
  - All tests passed (100%).

### 2. Full Clean-Room Unit Test Suite Execution
- **Command**: `./gradlew testDebugUnitTest`
- **Result**: BUILD SUCCESSFUL (100% pass rate across entire unit test suite, zero regressions).

### 3. Requirement Governance Verification
- **Command**: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-41.10`
- **Result**: Net-new requirement `REQ-DAT-024` and test `TST-DAT-019` verified compliant.

---

## 4. Invariants Preserved
- **Security & Permissions**: The least-privilege OAuth scope `https://www.googleapis.com/auth/drive.file` is preserved unchanged.
- **Database Backup Isolation**: Database backups remain strictly isolated in `aTrainingTracker/Backups/` (`GoogleDriveBackupManager.kt`).
- **Unlinked Safety**: Operations safely terminate without crash when Google Drive is unlinked.
- **Backward Compatibility**: Workouts stored in legacy `aTrainingTracker/Workouts/` remain fully recoverable.
