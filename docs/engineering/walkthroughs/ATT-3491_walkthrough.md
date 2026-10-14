# Stage 5 Walkthrough - ATT-3491: Recursive directory workout file discovery and bulk import via Storage Access Framework

**Ticket**: [ATT-3491](https://rainerblind.atlassian.net/browse/ATT-3491)  
**Stage 5 Subtask**: [ATT-3525](https://rainerblind.atlassian.net/browse/ATT-3525) (`[Test]`)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.3`  
**Requirement Mapping**: `REQ-UI-363` (`Verified`)  
**Test Mapping**: `TST-UI-326` (`Verified`)  
**Branch**: `feature/ATT-3491`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Executive Summary & Implementation Details

Following the removal of the sensitive Google Drive REST API scan in ATT-3489 (`REQ-UI-362`), ATT-3491 delivers a high-performance, platform-native recursive directory traversal and bulk import capability using the Android Storage Access Framework (SAF) `OpenDocumentTree()`.

This feature allows athletes to select an entire folder tree (from local storage, SD card, Google Drive, Nextcloud, or Dropbox via SAF providers) and automatically discover and import all nested workout files (`.fit`, `.tcx`, `.gpx`, or all formats) in a single operation, with full duplicate skipping and post-import reconciliation.

### Key Architectural Components

1. **Recursive Traversal Engine (`SafDirectoryScanner.kt`)**:
   - Implemented `SafDirectoryScanner` in package `com.atrainingtracker.trainingtracker.migration`.
   - Utilizes `DocumentFile.fromTreeUri(context, treeUri)` to traverse document trees.
   - Enforces a depth limit bound (`maxDepth = 6`) to prevent infinite recursion in deeply nested hierarchies.
   - Implements cycle detection via `visitedUris: MutableSet<Uri>` to guard against cyclic document symlinks.
   - Supports case-insensitive target format filtering (`fit`, `tcx`, `gpx`, or `all`).
   - Checks coroutine cancellation at every directory level for responsive cancellation.
   - Decoupled `scanDocumentFile` helper enabling mockable, pure JVM unit testing without framework stub exceptions.

2. **Batch Ingestion Pipeline (`BackupRestoreViewModel.kt`)**:
   - Added `importWorkoutDirectory(context, treeUri, format, dispatcher)`.
   - Reports folder scanning state (`R.string.import_directory_scanning`) via `_importStatus`.
   - If 0 matching files are discovered, displays informative toast/status (`R.string.import_directory_no_files_found`).
   - Reuses `importWorkoutFilesInternal(context, uris, format)` to execute sequential ingestion through `LegacyImportEngine`.
   - Automatically detects and skips existing workouts (`isWorkoutExisting`).
   - Triggers full post-import database reconciliation: `WorkoutRepository.loadAllWorkouts()`, `PeriodsRepository.syncPeriodsIfDiscrepancy()`, and `WorkoutClusterRepository.refreshClusters()`.

3. **Composable Format Import Cards & Launcher Integration (`ImportBackupTabsScreen.kt`)**:
   - Added `folderButtonText` and `onFolderClick` parameters to `FormatImportCard`.
   - Configured `FormatImportCard` to render three actions per card: Local File Picker, Folder Directory Scan, and Dropbox Cloud Scan.
   - Wired `ImportTabContent` with `onFolderImportClick: (format: String) -> Unit` across FIT, TCX, and GPX format cards.
   - Registered `pickDirectoryLauncher` via `ActivityResultContracts.OpenDocumentTree()`.
   - Integrated tree URI selection with `PreImportTuningBottomSheet` (`pendingDirectoryUri`, `pendingDirectoryFormat`), confirming cluster tolerances before traversal starts.

4. **9-Language Localization Parity**:
   - Added `import_fit_folder_button`, `import_tcx_folder_button`, `import_gpx_folder_button`, `import_directory_scanning`, and `import_directory_no_files_found` across all 9 supported locales:
     - `values/` (English)
     - `values-de/` (German)
     - `values-es/` (Spanish)
     - `values-fr/` (French - properly escaped apostrophes)
     - `values-it/` (Italian)
     - `values-ja/` (Japanese)
     - `values-nl/` (Dutch)
     - `values-pl/` (Polish)
     - `values-pt/` (Portuguese)
   - 100% translation parity confirmed by automated tests.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-363` (*Recursive Directory Workout File Discovery and Bulk Import via Storage Access Framework*) $\to$ **Status: Verified**
* **Test Mapping**: `TST-UI-326` (*Recursive Directory Workout File Discovery and Bulk Import via Storage Access Framework Verification*) $\to$ **Status: Verified**

---

## 3. Preserved Invariants & Invariant Verification

1. **Android SAF Least Privilege**: Uses standard `OpenDocumentTree()` without requesting any sensitive OAuth scopes or dangerous filesystem permissions.
2. **Infinite Recursion & Cycle Safety**: Depth limit bound 6 and visited URI set prevent any runaway directory traversal or stack overflow.
3. **Multi-Document File Picking Preserved**: Standard `OpenMultipleDocuments()` local file picking remains 100% operational on every card.
4. **Dropbox Format Scan Preserved**: Dropbox API format scanning remains intact.
5. **Pre-Import Cluster Parameter Tuning Preserved**: Athletes can configure cluster tolerances before batch directory imports execute.
6. **Post-Import Database Integrity**: Period reconciliation and cluster refresh run automatically after directory ingestion completes.
7. **Zero Full-Suite Regressions**: Full clean-room test suite `./gradlew testDebugUnitTest` executed with 100% pass rate.

---

## 4. Test Verification Results

### Unit & Architectural Test Suites

| Test Suite | Description | Result |
| :--- | :--- | :--- |
| `SafDirectoryScannerTest` | Verifies recursive file discovery, format filtering, depth limit bound (6), cycle detection, empty folder handling, and coroutine cancellation | **PASS** |
| `ImportFormatCardsContractTest` | Verifies `FormatImportCard` defines `folderButtonText` and `onFolderClick`, renders all 3 action buttons per card, and `ImportTabContent` wires folder handlers for FIT, TCX, and GPX | **PASS** |
| `ImportFormatLocalizationTest` | Verifies 100% presence, non-emptiness, and formatting parity for all folder scan string resources across all 9 supported locales | **PASS** |

### Clean-Room Full Suite Regression Run
* Command: `./gradlew testDebugUnitTest`
* Result: **100% Tests Passed** (0 failures, 0 errors, 0 regressions across all modules).
