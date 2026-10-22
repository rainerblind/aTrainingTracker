# Stage 3: Implementation Plan - ATT-3491: Recursive directory workout file discovery and bulk import via Storage Access Framework

**Ticket**: [ATT-3491](https://rainerblind.atlassian.net/browse/ATT-3491)  
**Sub-task**: [ATT-3523](https://rainerblind.atlassian.net/browse/ATT-3523) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `Sprint 2026-42.3`  
**Active Sprint**: `Sprint 2026-42.3`  
**Requirement Mapping**: `REQ-UI-363` (*Recursive Directory Workout File Discovery and Bulk Import via Storage Access Framework*), complementing `REQ-UI-293` and `REQ-UI-362`  
**Test Mapping**: `TST-UI-326` (*Recursive Directory Workout File Discovery and Bulk Import via Storage Access Framework Verification*)  
**Branch**: `feature/ATT-3491`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Problem Description & Background

Following the removal of the direct Google Drive REST API scan under ATT-3489 (due to Google's sensitive scope verification policy for `drive.readonly`), athletes importing historical workouts from desktop archives, Garmin, Wahoo, or Polar need an automated way to bulk-import hundreds of workout files (`.fit`, `.tcx`, `.gpx`) arranged across nested directory trees (e.g. `Workouts/2024/05/`, `TCX/Road/`).

Individual multi-document selection via `ACTION_OPEN_DOCUMENT` requires athletes to manually select files flatly within a single folder. When archives are organized in folder hierarchies, manual selection across dozens of subfolders is tedious and error-prone.

By leveraging Android's native Storage Access Framework (SAF) Directory Picker (`Intent.ACTION_OPEN_DOCUMENT_TREE` / `ActivityResultContracts.OpenDocumentTree()`), athletes can select a root directory once. The app can then recursively traverse all subdirectories, discover all matching workout files, and bulk-import them with automated duplicate detection.

Crucially, this approach operates completely under native Android platform permissions:
1. **Zero OAuth Sensitive Scope Burden**: Bypasses Google Cloud Console sensitive scope reviews, video submissions, and privacy policy audits.
2. **Universal Cloud & Storage Agnostic**: Seamlessly scans Google Drive, Dropbox, Nextcloud, local device storage, USB-OTG, and SD cards through Android's native DocumentProvider architecture.
3. **Athlete Privacy & Least Privilege**: Grants access strictly to the selected directory tree.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-363` (*Recursive Directory Workout File Discovery and Bulk Import via Storage Access Framework*), complementing `REQ-UI-293` and `REQ-UI-362`
* **Test Mapping**: `TST-UI-326` (*Recursive Directory Workout File Discovery and Bulk Import via Storage Access Framework Verification*)
* **Living Documentation**:
  - `docs/requirements.md` (`REQ-UI-363` added)
  - `docs/tests.md` (`TST-UI-326` added)

---

## 3. System Invariants & Preserved Behavior

1. **Storage Access Framework (SAF) Invariant**: Operates strictly via Android SAF `OpenDocumentTree()` and `DocumentFile` without requiring external storage runtime permissions (`MANAGE_EXTERNAL_STORAGE` or `READ_EXTERNAL_STORAGE`).
2. **Traversal Safety Invariant**: Depth limit bound (max depth 6) and cycle detection guarantee zero stack overflow or infinite recursion risk.
3. **Local SAF & Cloud Invariance**: Local file picking via `OpenMultipleDocuments()`, Dropbox format scanning, and Google Drive full database backup/restore (`.attbackup`) remain 100% operational without regression.
4. **Pre-Import Parameter Tuning Invariant**: Pre-import cluster parameter tuning (`PreImportTuningBottomSheet`) remains mandatory before recursive bulk imports commence.
5. **Deduplication Invariant**: Existing duplicate detection (`isWorkoutExisting`) and post-import database reconciliation (`loadAllWorkouts()`, `syncPeriodsIfDiscrepancy()`, `refreshClusters()`) are preserved.
6. **Clean-Room Full Suite Regression Invariant**: 100% test pass rate across all modules (`./gradlew testDebugUnitTest`).

---

## 4. Proposed Architectural Changes

### Component 1: `SafDirectoryScanner.kt` (New Engine)
* Path: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/SafDirectoryScanner.kt`
* **Responsibilities**:
  - Encapsulate recursive directory discovery logic using `androidx.documentfile.provider.DocumentFile`.
  - Provide `suspend fun scanDirectory(context: Context, treeUri: Uri, targetFormat: String, maxDepth: Int = 6, cancelSignal: (() -> Boolean)? = null): List<Uri>`.
  - Enforce traversal guards:
    * Bound depth to `maxDepth = 6`.
    * Prevent cyclic symlinks or infinite loops using a `visitedUris: MutableSet<String>` tracking set.
    * Check `coroutineContext.isActive` and `cancelSignal` between directory iterations.
    * Filter matching files case-insensitively by extension (`.fit`, `.tcx`, `.gpx`).
    * Ignore non-matching files, hidden directories (names starting with `.`), and unreadable entries.

### Component 2: `BackupRestoreViewModel.kt` (Batch Ingestion)
* Path: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt`
* **Changes**:
  - Introduce `fun importWorkoutDirectory(context: Context, treeUri: Uri, format: String, dispatcher: CoroutineDispatcher = Dispatchers.IO): Job`.
  - In coroutine:
    1. Emit `UiState.Loading(context.getString(R.string.import_directory_scanning))`.
    2. Invoke `SafDirectoryScanner.scanDirectory(context, treeUri, format)`.
    3. If 0 matching files discovered: emit `UiState.Success(context.getString(R.string.import_directory_no_files_found, format.uppercase()), null)`.
    4. If matching files found: delegate to `importWorkoutFiles(context, discoveredUris, format, dispatcher)`.

### Component 3: `ImportBackupTabsScreen.kt` (Composable UI & SAF Launcher)
* Path: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt`
* **Changes**:
  1. `FormatImportCard`:
     - Add parameters: `folderButtonText: String`, `onFolderClick: () -> Unit`.
     - Insert an OutlinedButton for directory import positioned between Local File Import and Dropbox Scan:
       ```kotlin
       OutlinedButton(
           onClick = onFolderClick,
           modifier = Modifier.fillMaxWidth(),
           enabled = !isBusy
       ) {
           Icon(Icons.Default.FolderOpen, contentDescription = null)
           Spacer(modifier = Modifier.width(8.dp))
           Text(folderButtonText)
       }
       ```
  2. `ImportTabContent`:
     - Add parameter: `onFolderImportClick: (format: String) -> Unit`.
     - Wire FIT, TCX, and GPX `FormatImportCard` instances with folder button text and `onFolderImportClick(format)`.
  3. `ImportBackupTabsScreen`:
     - Introduce state: `var pendingDirectoryUri by remember { mutableStateOf<Uri?>(null) }` and `var pendingDirectoryFormat by remember { mutableStateOf("fit") }`.
     - Register `pickDirectoryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree())`:
       * On valid URI, optionally take persistable read permission: `context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)`.
       * Set `pendingDirectoryUri = uri`.
     - Render `pendingDirectoryUri?.let { uri -> PreImportTuningBottomSheet(...) { viewModel.importWorkoutDirectory(context, uri, pendingDirectoryFormat); pendingDirectoryUri = null } }`.
     - Pass `onFolderImportClick = { format -> pendingDirectoryFormat = format; pickDirectoryLauncher.launch(null) }` to `ImportTabContent`.

### Component 4: 9-Language Localization Parity (`strings.xml`)
* Paths: `app/src/main/res/values*/strings.xml` (EN, DE, ES, FR, IT, JA, NL, PL, PT)
* **New String Keys**:
  - `import_fit_folder_button`: "Scan Folder (FIT)" / "Ordner scannen (FIT)"
  - `import_tcx_folder_button`: "Scan Folder (TCX)" / "Ordner scannen (TCX)"
  - `import_gpx_folder_button`: "Scan Folder (GPX)" / "Ordner scannen (GPX)"
  - `import_directory_scanning`: "Scanning folder for workouts…" / "Ordner wird nach Trainings durchsucht…"
  - `import_directory_no_files_found`: "No compatible %1$s workout files found in selected folder." / "Keine kompatiblen %1$s-Trainingsdateien im ausgewählten Ordner gefunden."

### Component 5: Unit & Contract Tests
* Paths:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/SafDirectoryScannerTest.kt` (New)
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportFormatCardsContractTest.kt` (Updated)
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportFormatLocalizationTest.kt` (Updated)
* **Changes**:
  - `SafDirectoryScannerTest`: Test format filtering, max depth bound (6), cycle detection, empty folder handling, cancellation.
  - `ImportFormatCardsContractTest`: Assert `folderButtonText` and `onFolderClick` exist on `FormatImportCard`, `onFolderImportClick` exists on `ImportTabContent`, all 3 cards define folder buttons.
  - `ImportFormatLocalizationTest`: Assert all 5 new string resources exist across all 9 locales with exact parameter specifier matching.

### UI Consistency (Rule 23)
* **Reference Screen / Component**: `ImportBackupTabsScreen.kt` format cards.
* **Reused Components**: `FormatImportCard`, `ElevatedCard` (shape 16.dp, elevation 2.dp), `OutlinedButton`, `Icons.Default.FolderOpen`, Material Theme typography and colorScheme.
* **Theme Tokens**: `RoundedCornerShape(16.dp)`, `MaterialTheme.typography.titleMedium`, `MaterialTheme.typography.bodySmall`, `MaterialTheme.colorScheme.onSurfaceVariant`, `ButtonDefaults.outlinedButtonColors()`.
* **New One-Off Styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement `SafDirectoryScanner.kt`
* Create `app/src/main/java/com/atrainingtracker/trainingtracker/migration/SafDirectoryScanner.kt`.
* Implement recursive traversal with `DocumentFile`, cycle detection (`visitedUris`), max depth bounding (6), cancellation checks, and format filtering.
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/SafDirectoryScanner.kt`.

### Step 2: Implement `BackupRestoreViewModel.importWorkoutDirectory`
* Add `importWorkoutDirectory(context, treeUri, format, dispatcher)` to `BackupRestoreViewModel.kt`.
* Wire scanning progress state (`UiState.Loading`), empty directory handling, and delegation to `importWorkoutFiles`.
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt`.

### Step 3: Integrate Folder Scan Action into `ImportBackupTabsScreen.kt`
* Add `folderButtonText` and `onFolderClick` to `FormatImportCard`.
* Add `onFolderImportClick` to `ImportTabContent`.
* Register `ActivityResultContracts.OpenDocumentTree()` launcher and connect with `PreImportTuningBottomSheet`.
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt`.

### Step 4: Add 9-Language Localization Resources
* Add `import_fit_folder_button`, `import_tcx_folder_button`, `import_gpx_folder_button`, `import_directory_scanning`, `import_directory_no_files_found` across all 9 `strings.xml` files.
* Targets: `app/src/main/res/values*/strings.xml`.

### Step 5: Author Unit, Contract, and Localization Tests
* Create `SafDirectoryScannerTest.kt`.
* Update `ImportFormatCardsContractTest.kt` to verify folder scan parameters and triad button structure.
* Update `ImportFormatLocalizationTest.kt` to include newly added keys in 9-locale verification.
* Targets:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/SafDirectoryScannerTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportFormatCardsContractTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportFormatLocalizationTest.kt`

### Step 6: Targeted Unit Test Execution
* Command:
  ```bash
  ./gradlew testDebugUnitTest \
    --tests "com.atrainingtracker.trainingtracker.migration.SafDirectoryScannerTest" \
    --tests "com.atrainingtracker.trainingtracker.migration.ImportFormatCardsContractTest" \
    --tests "com.atrainingtracker.trainingtracker.migration.ImportFormatLocalizationTest"
  ```
* Verify 100% pass rate.

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests during construction, followed by full clean-room suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation (`feature/ATT-3491`) allows clean rollback without affecting `sprint/2026-42.3`.
