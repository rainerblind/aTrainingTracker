# Stage 2: Requirement & Test Specification - ATT-3491: Recursive directory workout file discovery and bulk import via Storage Access Framework

**Ticket**: [ATT-3491](https://rainerblind.atlassian.net/browse/ATT-3491)  
**Sub-task**: [ATT-3522](https://rainerblind.atlassian.net/browse/ATT-3522) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `Sprint 2026-42.3`  
**Active Sprint**: `Sprint 2026-42.3`  
**Requirement Mapping**: `REQ-UI-363` (*Recursive Directory Workout File Discovery and Bulk Import via Storage Access Framework*), complementing `REQ-UI-293` and `REQ-UI-362`  
**Test Spec ID**: `TST-UI-326`  
**Branch**: `feature/ATT-3491`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Requirement Specification (REQ-UI-363)

### 1.1 Problem Statement & Rationale
With the removal of the direct Google Drive REST API scan under ATT-3489 (which was constrained by Google's sensitive scope verification policy for `drive.readonly`), athletes migrating historical training archives from Garmin, Wahoo, Polar, or desktop backups need an automated method to bulk-import hundreds of workout files (`.fit`, `.tcx`, `.gpx`) arranged across nested directory trees (e.g. `Workouts/2024/05/`, `TCX/Road/`).

Individual multi-document selection via `ACTION_OPEN_DOCUMENT` requires athletes to manually select files flatly within a single folder. When archives are organized in folder hierarchies, manual selection across dozens of subfolders is tedious and error-prone.

By leveraging Android's Storage Access Framework (SAF) Directory Picker (`Intent.ACTION_OPEN_DOCUMENT_TREE` / `ActivityResultContracts.OpenDocumentTree()`), athletes can select a root directory once. The app can then recursively traverse all subdirectories, discover all matching workout files, and bulk-import them with automated duplicate detection.

Crucially, this approach operates completely under native Android platform permissions:
1. **Zero OAuth Sensitive Scope Burden**: Bypasses Google Cloud Console sensitive scope reviews, video submissions, and privacy policy audits.
2. **Universal Cloud & Storage Agnostic**: Seamlessly scans Google Drive, Dropbox, Nextcloud, local device storage, USB-OTG, and SD cards through Android's native DocumentProvider architecture.
3. **Athlete Privacy & Least Privilege**: Grants access strictly to the selected directory tree.

### 1.2 Functional & Architectural Requirements
1. **Directory Picker & Composable Integration (`ImportBackupTabsScreen.kt`)**:
   - `FormatImportCard` SHALL introduce `folderButtonText: String` and `onFolderClick: () -> Unit` action parameters.
   - `FormatImportCard` SHALL render three action buttons:
     * Button 1: Local File Picker (`localButtonText`, `onLocalClick`)
     * Button 2: Directory Folder Scan (`folderButtonText`, `onFolderClick`)
     * Button 3: Dropbox Cloud Scan (`dropboxButtonText`, `onDropboxClick`)
   - `ImportTabContent` SHALL accept `onFolderImportClick: (format: String) -> Unit` and configure FIT, TCX, and GPX format cards to trigger folder selection for their respective format (`fit`, `tcx`, `gpx`).
   - `ImportBackupTabsScreen` SHALL register an `OpenDocumentTree()` launcher (`pickDirectoryLauncher`) that captures the selected directory tree URI and the target format (`pendingDirectoryUri`, `pendingDirectoryFormat`).
   - Upon tree selection, `ImportBackupTabsScreen` SHALL display the pre-import cluster parameter tuning bottom sheet (`PreImportTuningBottomSheet`), ensuring athlete cluster tolerances are verified before recursive ingestion commences.
2. **Recursive Traversal Engine (`SafDirectoryScanner.kt`)**:
   - The system SHALL implement a dedicated scanner component `SafDirectoryScanner` in package `com.atrainingtracker.trainingtracker.migration`.
   - The scanner SHALL provide `suspend fun scanDirectory(context: Context, treeUri: Uri, targetFormat: String, maxDepth: Int = 6, cancelSignal: (() -> Boolean)? = null): List<Uri>`.
   - The scanner SHALL inspect document children recursively using `DocumentFile.fromTreeUri(context, treeUri)`.
   - The scanner SHALL enforce:
     * **Depth Limiting**: Traversal ceases at `maxDepth = 6` to prevent runaway recursion in deep or misconfigured directory trees.
     * **Cycle Detection**: Tracks visited document URI strings to prevent infinite loops from cyclic directory links or misbehaving DocumentProviders.
     * **Extension Matching**: Matches file names ending in `.$targetFormat` (case-insensitive, e.g. `.fit`, `.FIT`, `.tcx`, `.TCX`, `.gpx`, `.GPX`). If `targetFormat == "all"`, matches any supported workout extension.
     * **Cancellation & Responsiveness**: Checks `coroutineContext.isActive` and `cancelSignal` between directory iterations to ensure instantaneous cancellation.
3. **Batch Ingestion Pipeline & Reconciliation (`BackupRestoreViewModel.kt`)**:
   - `BackupRestoreViewModel` SHALL provide `fun importWorkoutDirectory(context: Context, treeUri: Uri, format: String, dispatcher: CoroutineDispatcher = Dispatchers.IO): Job`.
   - Progress Feedback: Emits `UiState.Loading` while scanning (`R.string.import_directory_scanning`), then streams into sequential file ingestion reporting step-by-step progress (`R.string.import_workout_files_progress` / `R.string.import_fit_progress`).
   - Empty Directory Handling: If 0 compatible files are discovered, emits `UiState.Success` with an informative message (`R.string.import_directory_no_files_found`).
   - Deduplication & Ingestion: Ingests discovered files sequentially via `LegacyImportEngine`, skipping existing duplicates (`isWorkoutExisting`).
   - Database Reconciliation: Upon completing non-empty imports, triggers `WorkoutRepository.loadAllWorkouts()`, `PeriodsRepository.syncPeriodsIfDiscrepancy()`, and `WorkoutClusterRepository.refreshClusters()`.
   - Final Summary Digest: Emits success/error summary via `R.string.import_fit_summary_success` or `R.string.import_fit_summary_with_errors`.
4. **9-Language Localization Parity (`strings.xml`)**:
   - All newly introduced string keys SHALL maintain 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT:
     * `import_fit_folder_button`: Button label for FIT folder scan
     * `import_tcx_folder_button`: Button label for TCX folder scan
     * `import_gpx_folder_button`: Button label for GPX folder scan
     * `import_directory_scanning`: Progress string during directory traversal
     * `import_directory_no_files_found`: Informative notification when directory contains no compatible files

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Complements `REQ-UI-293` (*Format-Specific Import Tab Architecture with Uniform Multi-Document Source Triad (FIT, TCX, GPX)*) and `REQ-UI-362` (*Removal of Google Drive Format Scan Option from Workout Import Cards*).
2. *Historical Origin & Commit Trace*: `ATT-2623` (commit `d5a9d217`), Sprint 2026-41.1; refined under `ATT-3489` (commit `3961ed8f`), Sprint 2026-42.3.
3. *Root Reason for Existing Formulation*: Following the removal of the sensitive OAuth scope Google Drive REST scan, athletes required a non-sensitive, platform-native mechanism to scan and bulk-import folders containing historical workout files from cloud storage providers (Google Drive, Dropbox, Nextcloud) and local directories. Android's native `OpenDocumentTree` provides this capability without OAuth permissions.
4. *Preservation of Core Invariants*: Local multi-document file selection via `OpenMultipleDocuments()`, Dropbox folder scanning, pre-import cluster parameter tuning, and post-import database reconciliation remain strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1: Recursive Traversal Across Nested Folders**:
  - *Given* an athlete with a folder hierarchy containing nested subdirectories (e.g. `Root/2024/Jan/` and `Root/2024/Feb/`) with `.fit`, `.tcx`, or `.gpx` files,
  - *When* selecting `Root` via the SAF directory picker and confirming cluster tuning,
  - *Then* the app recursively traverses all subfolders up to max depth 6 and identifies all matching workout files.
* **Criterion 2: Cloud Provider Interoperability via SAF**:
  - *Given* an athlete browsing a Google Drive, Dropbox, or SD card folder in the Android SAF picker,
  - *When* selecting that folder,
  - *Then* the app streams and imports the files through the SAF content resolver without prompting for Google Drive OAuth scopes or third-party cloud credentials.
* **Criterion 3: Automated Duplicate Skipping & Summary**:
  - *Given* a folder containing 10 workouts, 4 of which already exist in `WorkoutSummaries`,
  - *When* the directory import executes,
  - *Then* the 6 new workouts are imported, the 4 existing workouts are skipped, and the summary displays "6 importiert, 4 Duplikate übersprungen".
* **Criterion 4: Graceful Empty Folder & Cancellation Handling**:
  - *Given* the athlete selects an empty folder or a folder with no matching extensions,
  - *When* directory traversal completes,
  - *Then* the app displays an informative notification indicating no compatible files were found, without crashing or reporting errors.
* **Criterion 5: 9-Language Localization Parity**:
  - *Given* all 9 supported application locales,
  - *When* verifying string resources,
  - *Then* all folder import labels, progress text, and empty messages exist and match format specifiers.

### 1.5 System Invariants
1. **Storage Access Framework Invariant**: Operates strictly via Android SAF `OpenDocumentTree()` and `DocumentFile` without requiring external storage runtime permissions (`MANAGE_EXTERNAL_STORAGE` or `READ_EXTERNAL_STORAGE`).
2. **Traversal Safety Invariant**: Max depth bound (6) and cycle detection guarantee zero stack overflow or infinite recursion risk.
3. **Clean-Room Full Suite Regression Invariant**: 100% test pass rate across all modules (`./gradlew testDebugUnitTest`).

---

## 2. Test Specification (TST-UI-326)

### Test Case 1: `SafDirectoryScannerTest` Unit Tests (`TST-UI-326.1`)
* **Scope**: Unit & Architectural Tests
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/SafDirectoryScannerTest.kt`
* **Test Scenarios**:
  1. *Extension Filtering*: Verifies scanner matches target format (`fit`, `tcx`, `gpx`) case-insensitively and filters out unmatched files.
  2. *Depth Limit Enforcement*: Verifies scanner truncates recursion when depth exceeds `maxDepth = 6`.
  3. *Cycle Detection*: Verifies scanner detects previously visited directory URIs and terminates cycle gracefully.
  4. *Empty Directory Handling*: Verifies scanner returns empty list for empty folders.
  5. *Cancellation Support*: Verifies scanner ceases traversal immediately when cancellation signal is triggered.

### Test Case 2: `ImportFormatCardsContractTest` Contract Assertions (`TST-UI-326.2`)
* **Scope**: Architectural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportFormatCardsContractTest.kt`
* **Test Scenarios**:
  1. Verifies `FormatImportCard` defines `folderButtonText` and `onFolderClick` parameters.
  2. Verifies `ImportTabContent` defines `onFolderImportClick: (format: String) -> Unit` and passes it to all 3 format cards (FIT, TCX, GPX).
  3. Verifies each format card contains all 3 action buttons: Local File Import, Folder Scan, and Dropbox Scan.

### Test Case 3: 9-Language Localization Audit (`TST-UI-326.3`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportFormatLocalizationTest.kt`
* **Test Scenarios**:
  1. Verifies keys `import_fit_folder_button`, `import_tcx_folder_button`, `import_gpx_folder_button`, `import_directory_scanning`, and `import_directory_no_files_found` exist and are non-empty across all 9 locales: EN, DE, ES, FR, IT, JA, NL, PL, PT.
  2. Verifies format specifier `%1$s` matches in `import_directory_no_files_found` across all 9 locales.

### Test Case 4: Clean-Room Regression Suite (`TST-UI-326.4`)
* **Scope**: Clean-room regression test
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with 0 failures and 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-326.1` | Unit | `SafDirectoryScannerTest.*` | `REQ-UI-363` | Specified |
| `TST-UI-326.2` | Contract Unit | `ImportFormatCardsContractTest.testFormatImportCard_containsFolderScanButton` | `REQ-UI-363` | Specified |
| `TST-UI-326.3` | Localization | `ImportFormatLocalizationTest.testFolderImportStringsExistAcrossAllNineLocales` | `REQ-UI-363`, `REQ-LOC-001` | Specified |
| `TST-UI-326.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
