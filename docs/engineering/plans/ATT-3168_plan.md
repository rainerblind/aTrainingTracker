# Stage 3: Implementation Plan - ATT-3168: Organize Google Drive workouts into format-specific directories (FIT, TCX, GPX) for export and import

**Ticket**: [ATT-3168](https://atrainingtracker.atlassian.net/browse/ATT-3168)  
**Sub-task**: [ATT-3269](https://atrainingtracker.atlassian.net/browse/ATT-3269) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-162](https://atrainingtracker.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Requirement Mapping**: `REQ-DAT-024`  
**Test Mapping**: `TST-DAT-019`  
**Branch**: `feature/ATT-3168`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Description & Background

Google Drive export (`GoogleDriveUploader.kt`) currently hardcodes its destination directory to a single flat folder: `aTrainingTracker/Workouts/`. Regardless of whether an athlete exports FIT, TCX, GPX, CSV, or GC JSON files, all files are stored in this single folder.
In contrast, Dropbox export (`DropboxUploader.java` via `exportInfo.shortPath` / `FileFormat.dirName`) and import categorize workouts into format-specific subdirectories:
- `/FIT/`
- `/TCX/`
- `/GPX/`
- `/CSV/`
- `/GC/`

Furthermore, when scanning Google Drive for a specific file format (e.g., clicking "Google Drive scannen (FIT)" on the Import tab per `REQ-UI-293`), `LegacyImportEngine.kt` scans all candidate folders rather than prioritizing the requested format folder.

This implementation plan establishes directory parity between Google Drive and Dropbox by storing and prioritizing files in format-specific subdirectories (`aTrainingTracker/<FORMAT>`), while preserving backward-compatible discovery for legacy files in `aTrainingTracker/Workouts/`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-DAT-024` (*Google Drive Format-Specific Directory Organization for Workout Exports and Imports (FIT, TCX, GPX, CSV, GC)*)
* **Test Mapping**: `TST-DAT-019` (*Google Drive Format-Specific Directory Organization for Workout Exports and Imports Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites and export mechanisms continue to pass cleanly.
2. **Least-Privilege OAuth Scope**: Google Drive OAuth2 scope remains strictly constrained to `https://www.googleapis.com/auth/drive.file`.
3. **Database Backup Isolation**: Database backup files (`.attbackup`) remain strictly isolated to `aTrainingTracker/Backups/` (`GoogleDriveBackupManager.kt`).
4. **Historical File Backward Compatibility**: Existing workouts in `aTrainingTracker/Workouts/` continue to be discoverable and imported seamlessly.
5. **Cross-Directory Deduplication**: Deduplication across format and legacy folders by base filename (`removeSuffix("-TMP").removeSuffix("~").lowercase()`) prevents duplicate imports.
6. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
7. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `GoogleDriveUploader.kt` (`com.atrainingtracker.trainingtracker.exporter.uploader`)
* Modify line 67 in `doExport`:
  ```kotlin
  val formatDir = exportInfo.fileFormat.dirName.takeIf { it.isNotBlank() } ?: "Workouts"
  val targetHierarchy = listOf("aTrainingTracker", formatDir)
  val folderId = client.ensureFolderHierarchy(targetHierarchy)
  ```
* Update error logging and diagnostic messaging to reflect the format-specific folder hierarchy.

### Component 2: `LegacyImportEngine.kt` (`com.atrainingtracker.trainingtracker.migration`)
* In `bulkRecoverFromGoogleDrive`, dynamically generate `candidateFolderHierarchies` based on `format.lowercase()`:
  * For `"fit"`: `["aTrainingTracker", "FIT"]`, then `["aTrainingTracker", "Workouts"]`, `["aTrainingTracker"]`.
  * For `"tcx"`: `["aTrainingTracker", "TCX"]`, then `["aTrainingTracker", "Workouts"]`, `["aTrainingTracker"]`.
  * For `"gpx"`: `["aTrainingTracker", "GPX"]`, then `["aTrainingTracker", "Workouts"]`, `["aTrainingTracker"]`.
  * For `"all"` (or other): `["aTrainingTracker", "FIT"]`, `["aTrainingTracker", "TCX"]`, `["aTrainingTracker", "GPX"]`, then `["aTrainingTracker", "Workouts"]`, `["aTrainingTracker"]`.

### Component 3: Unit Tests (`GoogleDriveUploaderTest.kt` & `LegacyImportEngineGoogleDriveFolderResolutionTest.kt`)
* Update `GoogleDriveUploaderTest.kt` to expect format-specific folder hierarchies (e.g. `listOf("aTrainingTracker", "FIT")` for FIT files).
* Add test cases asserting that `FileFormat.TCX`, `FileFormat.GPX`, and `FileFormat.CSV` resolve their corresponding format directories.
* Add unit tests verifying format-scoped candidate folder generation in `LegacyImportEngine`.

### UI Consistency (Rule 23)
No UI changes. (Backend cloud file export and recovery folder taxonomy).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `GoogleDriveUploader.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/GoogleDriveUploader.kt`
* Changes: Replace hardcoded `listOf("aTrainingTracker", "Workouts")` with `listOf("aTrainingTracker", exportInfo.fileFormat.dirName.takeIf { it.isNotBlank() } ?: "Workouts")`.

### Step 2: Update `LegacyImportEngine.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`
* Changes: In `bulkRecoverFromGoogleDrive`, construct `candidateFolderHierarchies` dynamically based on `format.lowercase()`.

### Step 3: Update `GoogleDriveUploaderTest.kt`
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/uploader/GoogleDriveUploaderTest.kt`
* Changes: Update existing upload success mock to expect `listOf("aTrainingTracker", "FIT")` and add tests for TCX, GPX, and CSV formats.

### Step 4: Add `LegacyImportEngineGoogleDriveFolderResolutionTest.kt`
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngineGoogleDriveFolderResolutionTest.kt`
* Changes: Add targeted tests verifying candidate folder hierarchy resolution and deduplication.

### Step 5: Execute Targeted Unit Tests
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.exporter.uploader.GoogleDriveUploaderTest" --tests "com.atrainingtracker.trainingtracker.migration.LegacyImportEngineGoogleDriveFolderResolutionTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Run targeted unit tests during Stage 4, followed by full clean-room test suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-3168` ensures clean git revert without affecting `sprint/2026-41.10` or `develop`.
