# Stage 2: Requirement & Test Specification - ATT-3168: Organize Google Drive workouts into format-specific directories (FIT, TCX, GPX) for export and import

**Ticket**: [ATT-3168](https://atrainingtracker.atlassian.net/browse/ATT-3168)  
**Sub-task**: [ATT-3268](https://atrainingtracker.atlassian.net/browse/ATT-3268) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-162](https://atrainingtracker.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Requirement Mapping**: `REQ-DAT-024` (*Google Drive Format-Specific Directory Organization for Workout Exports and Imports (FIT, TCX, GPX, CSV, GC)*)  
**Test Spec ID**: `TST-DAT-019`  
**Branch**: `feature/ATT-3168`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Requirement Specification (REQ-DAT-024)

### 1.1 Problem Statement & Rationale
Currently, Google Drive export (`GoogleDriveUploader.kt`) hardcodes its destination directory to a single flat folder: `aTrainingTracker/Workouts/`. Regardless of whether an athlete exports FIT, TCX, GPX, CSV, or GC JSON files, all files are mixed in this single folder.
In contrast, Dropbox export (`DropboxUploader.java` via `exportInfo.shortPath` / `FileFormat.dirName`) categorizes workouts into format-specific subdirectories (`FIT/`, `TCX/`, `GPX/`, etc.).
Furthermore, when scanning Google Drive for a specific file format (e.g., clicking "Google Drive scannen (FIT)" on the Import tab per `REQ-UI-293`), `LegacyImportEngine.kt` scans all candidate folders rather than prioritizing the requested format folder.
This requirement establishes directory parity between Google Drive and Dropbox by storing and prioritizing files in format-specific subdirectories (`aTrainingTracker/<FORMAT>`), while preserving backward-compatible discovery for legacy files in `aTrainingTracker/Workouts/`.

### 1.2 Functional & Architectural Requirements
The system SHALL organize Google Drive workout activities into format-specific subdirectories matching their `FileFormat.dirName` and Dropbox directory parity, while preserving backward-compatible import discovery for legacy flat folders:
1. *Format-Specific Destination Hierarchy (`GoogleDriveUploader.kt`)*:
   - When exporting a workout session to Google Drive (`GoogleDriveUploader.doExport`), the system SHALL resolve the destination folder hierarchy dynamically: `listOf("aTrainingTracker", exportInfo.fileFormat.dirName)` (e.g. `["aTrainingTracker", "FIT"]`, `["aTrainingTracker", "TCX"]`, `["aTrainingTracker", "GPX"]`, `["aTrainingTracker", "CSV"]`, `["aTrainingTracker", "GC"]`).
   - If `dirName` is null or empty, the system SHALL fall back defensively to `"Workouts"`.
2. *Format-Scoped Candidate Folder Resolution (`LegacyImportEngine.kt`)*:
   - When initiating a format-specific bulk recovery from Google Drive (`bulkRecoverFromGoogleDrive(context, format, ...)`):
     - For `format == "fit"`: scan candidate folders `listOf("aTrainingTracker", "FIT")`, followed by legacy fallback `listOf("aTrainingTracker", "Workouts")` and `listOf("aTrainingTracker")`.
     - For `format == "tcx"`: scan candidate folders `listOf("aTrainingTracker", "TCX")`, followed by legacy fallback `listOf("aTrainingTracker", "Workouts")` and `listOf("aTrainingTracker")`.
     - For `format == "gpx"`: scan candidate folders `listOf("aTrainingTracker", "GPX")`, followed by legacy fallback `listOf("aTrainingTracker", "Workouts")` and `listOf("aTrainingTracker")`.
     - For `format == "all"`: scan candidate folders `listOf("aTrainingTracker", "FIT")`, `listOf("aTrainingTracker", "TCX")`, `listOf("aTrainingTracker", "GPX")`, followed by legacy fallback `listOf("aTrainingTracker", "Workouts")` and `listOf("aTrainingTracker")`.
3. *Cross-Directory Base Filename Deduplication*:
   - Discovered entries across format-specific and legacy directories SHALL be deduplicated by base filename (`removeSuffix("-TMP").removeSuffix("~").lowercase()`) per `REQ-MIG-031`, guaranteeing that an activity present in both `aTrainingTracker/<FORMAT>` and `aTrainingTracker/Workouts` is imported at most once.
4. *Preservation of System Invariants*:
   - Least-privilege OAuth scope `https://www.googleapis.com/auth/drive.file` is strictly preserved.
   - Database backups remain strictly isolated in `aTrainingTracker/Backups/` (`GoogleDriveBackupManager.kt`).
   - Unlinked safety and 100% test pass rate are preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Format-Specific Cloud Export)**:
  * *Given* Google Drive workout auto-export is enabled
  * *When* a workout is exported as FIT, TCX, GPX, CSV, or GC
  * *Then* `GoogleDriveUploader` calls `ensureFolderHierarchy` with `listOf("aTrainingTracker", exportInfo.fileFormat.dirName)` and uploads the file to that folder.
* **Criterion 2 (Format-Scoped Cloud Import)**:
  * *Given* Google Drive recovery initiated for format `"fit"`, `"tcx"`, or `"gpx"`
  * *When* `bulkRecoverFromGoogleDrive` executes
  * *Then* candidate folder hierarchies prioritize the format folder (`["aTrainingTracker", "<FORMAT>"]`), followed by legacy fallback `["aTrainingTracker", "Workouts"]` and `["aTrainingTracker"]`.
* **Criterion 3 (Backward Compatibility & Deduplication)**:
  * *Given* workout files existing in both format-specific folders and the legacy `Workouts/` folder
  * *When* Google Drive scan runs
  * *Then* files from both locations are discovered and deduplicated by base filename without importing duplicates.

### 1.4 System Invariants
* Least-privilege `drive.file` scope must never be expanded.
* Database backups in `aTrainingTracker/Backups/` must not be affected.
* Historical workouts in `aTrainingTracker/Workouts/` must continue to be imported cleanly.

---

## 2. Test Specification (TST-DAT-019)

### Test Case 1: `testDoExport_formatSpecificFolderResolution` (`TST-DAT-019.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/uploader/GoogleDriveUploaderTest.kt`
* **Preconditions**: Google Drive authenticated with valid token and export enabled.
* **Action**: Invoke `uploader.doExport(exportInfo)` for `FileFormat.FIT`, `FileFormat.TCX`, `FileFormat.GPX`, and `FileFormat.CSV`.
* **Expected Result**: Assert `mockClient.ensureFolderHierarchy(listOf("aTrainingTracker", format.dirName))` is called with exact format folder name, and `ExportResult(true, ...)` is returned.

### Test Case 2: `testBulkRecover_formatScopedCandidateFolders` (`TST-DAT-019.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngineGoogleDriveFolderResolutionTest.kt`
* **Preconditions**: Mocked Google Drive client.
* **Action**: Verify candidate folder hierarchies generated for format `"fit"`, `"tcx"`, `"gpx"`, and `"all"`.
* **Expected Result**: Format `"fit"` resolves `["aTrainingTracker", "FIT"]` then `["aTrainingTracker", "Workouts"]` and `["aTrainingTracker"]`. Cross-folder entries are deduplicated by base filename.

### Test Case 3: 9-Language Localization Audit (`TST-DAT-019.3`)
* **Scope**: Localization Parity Test
* **Goal**: Verify string presence and matching tokens across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, zero missing entries.

### Test Case 4: Clean-Room Regression Suite (`TST-DAT-019.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-DAT-019.1` | Unit | `GoogleDriveUploader.doExport` | `REQ-DAT-024` | Specified |
| `TST-DAT-019.2` | Unit | `LegacyImportEngine.bulkRecoverFromGoogleDrive` | `REQ-DAT-024` | Specified |
| `TST-DAT-019.3` | Localization | `TranslationParityTest` | `REQ-LOC-001` | Specified |
| `TST-DAT-019.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
