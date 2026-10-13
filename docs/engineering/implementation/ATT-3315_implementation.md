# Stage 4: Implementation Summary - ATT-3315: Enable import of externally copied workout files from Google Drive with file picker fallback

**Ticket**: [ATT-3315](https://rainerblind.atlassian.net/browse/ATT-3315)  
**Sub-task**: [ATT-3400](https://rainerblind.atlassian.net/browse/ATT-3400) (`[Implementation]`)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-MIG-034` (amended & extended), `REQ-UI-293` (amended & extended)  
**Test Mapping**: `TST-MIG-031`, `TST-UI-253`  
**Branch**: `feature/ATT-3315`  
**Author**: AI Agent 1 (Software Construction Engineer)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-MIG-034` (*Google Drive Bulk Recovery for Historical Workout Files*), targeting `GoogleDriveClient.kt`, `LegacyImportEngine.kt`, `BackupRestoreViewModel.kt`.
   - `REQ-UI-293` (*Format-Specific Import Tab Architecture with Uniform Source Triad (FIT, TCX, GPX)*), targeting `ImportBackupTabsScreen.kt`, `BackupRestoreViewModel.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-41.10 (`ATT-2346`, commit `53316db1`) introduced Google Drive historical workout file discovery and bulk recovery.
   - Sprint 2026-41.1 (`ATT-2623`, commit `d5a9d217`) structured the Import tab into format-specific blocks (FIT, TCX, GPX).
3. **Root Reason for Existing Formulation**:
   - `ATT-2346` used `https://www.googleapis.com/auth/drive.file` and probed `aTrainingTracker/TCX/`, `aTrainingTracker/Workouts/`, etc. In Sprint 2026-41.10 Joint Review, an athlete copied files into a top-level `TCX` folder. Because files created externally are invisible under `drive.file`, and top-level folders were not probed, 0 files were discovered. Furthermore, TCX and GPX local buttons were limited to single-file `OpenDocument()`.
4. **Preservation of Core Invariants**:
   - OAuth Least Privilege: The application strictly retains `https://www.googleapis.com/auth/drive.file` to protect user privacy and avoid Google Play OAuth sensitive data restrictions.
   - Cloud Pre-Import Tuning: The pre-import cluster parameter tuning sheet (`PreImportTuningBottomSheet`, `REQ-MIG-012`, `REQ-UI-150`) remains mandatory before scanning or batch local import commences.
   - Multi-Dimensional Deduplication: `LegacyImportEngine` duplicate skipping via `isWorkoutExisting` remains active.
   - Post-Import Reconciliation: `syncPeriodsIfDiscrepancy()`, `refreshClusters()`, and post-import navigation remain intact.
   - Localization Parity: 100% 9-language translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
   - Clean-Room Test Pass Rate: 100% unit test pass rate across all modules.

---

## 1. Summary of Changes

### 1.1 `LegacyImportEngine.kt` (Candidate Folder Probing Expansion)
- Extended `candidateFolderHierarchies` across all format modes (`fit`, `tcx`, `gpx`, `all`):
  - Added top-level folder candidates: `listOf("FIT")`, `listOf("TCX")`, `listOf("GPX")`, `listOf("Workouts")` prior to nested paths (`listOf("aTrainingTracker", ...)`).
  - Enables discovery of historical workout archives located at the root of Google Drive or organized in simple folder structures.

### 1.2 `BackupRestoreViewModel.kt` (Batch Workout Importer & Zero-Scan Diagnostic)
- Added `importWorkoutFiles(context: Context, uris: List<Uri>, format: String, dispatcher: CoroutineDispatcher = Dispatchers.IO): Job`:
  - Iterates over multiple document URIs sequentially.
  - Dynamically switches parsing delegates (`importFromTcxResult`, `importFromGpxResult`, `importFromFitResult`).
  - Implements duplicate skipping, failure counting, and progress updates (`import_fit_progress` / `import_workout_files_progress`).
  - Triggers reactive reconciliation upon completion (`loadAllWorkouts`, `syncPeriodsIfDiscrepancy`, `refreshClusters`).
  - Preserves single-file success message parity and multi-file summaries.
- Refactored `importFitFiles` and `importLegacyFile` to delegate to `importWorkoutFiles`.
- In `bulkRecoverGoogleDriveData`:
  - When `result.totalScanned == 0`, emits localized diagnostic `R.string.google_drive_scan_empty_diagnostic` explaining OAuth security constraints and guiding users to tap "Select local file(s)" to import files from Google Drive via the Storage Access Framework document picker.

### 1.3 `ImportBackupTabsScreen.kt` (Multi-Document SAF Selection & Pre-Import Tuning)
- Upgraded `pickLegacyFileLauncher` from `ActivityResultContracts.OpenDocument()` to `ActivityResultContracts.OpenMultipleDocuments()`.
- Added state holders `pendingLegacyUris` and `pendingFitUris`.
- Bound document picker results to `PreImportTuningBottomSheet`, ensuring clustering parameters (`eps`, `minPoints`) can be verified before initiating local multi-file ingestion.
- Dispatches to `viewModel.importWorkoutFiles` (and `viewModel.importLegacyFile` / `viewModel.importFitFiles`).

### 1.4 Localization Parity (9 Languages)
- Added `google_drive_scan_empty_diagnostic` and `import_workout_files_progress` across all 9 supported language directories:
  - `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

---

## 2. Targeted Verification Results

- `TranslationParityTest`: PASS (100% parity across all 9 locales).
- `LegacyImportEngineGoogleDriveFolderResolutionTest`: PASS (verifies expanded top-level and nested folder hierarchy resolution).
- `BackupRestoreViewModelGoogleDriveTest`: PASS (verifies disconnected guards, successful recovery, and zero-scanned diagnostic message).
- `BackupRestoreViewModelFitTest`: PASS (verifies batch URI ingestion, progress, and error counting).
- `BackupRestoreViewModelNavigationTest`: PASS (verifies post-import navigation workout ID propagation).
- `BackupRestoreViewModelFormatScopingTest`: PASS (verifies format-scoped routing for TCX, GPX, FIT).
- `ImportFormatCardsContractTest`: PASS (verifies 3-card layout and uniform action triad).
- `ImportFormatFilePickerContractTest`: PASS (verifies format MIME arrays and validation).
