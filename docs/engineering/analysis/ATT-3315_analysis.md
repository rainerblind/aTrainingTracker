# Stage 1: Problem Domain & Root Cause Analysis - ATT-3315: Enable import of externally copied workout files from Google Drive with file picker fallback

**Ticket**: [ATT-3315](https://rainerblind.atlassian.net/browse/ATT-3315)  
**Sub-task**: [ATT-3397](https://rainerblind.atlassian.net/browse/ATT-3397) (`[Analysis]`)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-MIG-034` (amended), `REQ-UI-293` (amended)  
**Test Mapping**: `TST-MIG-031`, `TST-UI-253`  
**Branch**: `feature/ATT-3315`  
**Author**: AI Agent 1 (Forensic Requirements & Systems Architect)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-MIG-034` (*Google Drive Historical Workout File Discovery & Bulk Recovery (.fit, .tcx, .gpx)*, `ATT-2346`), targeting `GoogleDriveClient.kt`, `LegacyImportEngine.kt`, and `BackupRestoreViewModel.kt`.
   - `REQ-UI-293` (*Format-Specific Import Tab Architecture with Uniform Source Triad (FIT, TCX, GPX)*, `ATT-2623`), targeting `ImportBackupTabsScreen.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-41.1 (`ATT-2346`, commit `ec000c11`) introduced automated recursive discovery and bulk recovery from Google Drive using `GoogleDriveClient.listFilesRecursively`.
   - Sprint 2026-41.1 (`ATT-2623`, commit `d5a9d217`) structured the Import tab into three cards (FIT, TCX, GPX), each with a triad: Local File Picker, Dropbox Scan, and Google Drive Scan.
   - Sprint 2026-41.10 (`ATT-3168`) added format-specific subdirectories (`aTrainingTracker/FIT`, `aTrainingTracker/TCX`, `aTrainingTracker/GPX`).
3. **Root Reason for Existing Formulation**:
   - Least-Privilege OAuth: The application intentionally uses `https://www.googleapis.com/auth/drive.file` to protect user privacy and comply with Google Play Store data safety requirements.
   - Under `drive.file`, the app is restricted to files created by the application itself or explicitly opened by the user via Storage Access Framework (SAF).
   - In `ImportBackupTabsScreen.kt`, the local file picker for FIT used `ActivityResultContracts.OpenMultipleDocuments()`, whereas TCX and GPX were historically restricted to `ActivityResultContracts.OpenDocument()` (single file only).
   - Furthermore, `LegacyImportEngine.candidateFolderHierarchies` only scanned paths prefixed with `aTrainingTracker` (`listOf("aTrainingTracker", "TCX")`, `listOf("aTrainingTracker", "Workouts")`, `listOf("aTrainingTracker")`). Files placed directly in root `TCX/` or `FIT/` were never probed.
   - When Google Drive bulk recovery returned 0 files due to these permission or hierarchy constraints, `BackupRestoreViewModel` presented a generic "0 of 0 imported" message, giving the athlete zero indication why files were not discovered.
4. **Preservation of Core Invariants**:
   - Least-Privilege Security Scope: Maintain `https://www.googleapis.com/auth/drive.file` without escalating to broad, sensitive scopes (`drive` or `drive.readonly`).
   - Cluster Reconciliation & Deduplication: Ensure all imported files run through base filename deduplication (`REQ-MIG-031`) and trigger `WorkoutRepository`, `PeriodsRepository`, and `WorkoutClusterRepository` reconciliation.
   - Pre-Import Tuning: Preserve mandatory `PreImportTuningBottomSheet` presentation prior to starting imports.
   - 9-Language Localization Parity: Guarantee 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
   - Clean-Room Test Pass Rate: 100% test pass rate across all unit tests and layout contracts.

---

## 1. Executive Summary

During the Sprint 2026-41.10 Joint Review of `ATT-2346` (Google Drive bulk recovery), an athlete copied historical `.tcx` workout files from Dropbox into the Google Drive `TCX` directory. Running the automated Google Drive scan failed to discover or import any of these files.

Forensic analysis reveals a dual root cause:
1. **Google Drive API Semantics (`drive.file`)**: The app operates under the least-privilege `drive.file` OAuth scope. Google Drive REST API queries (`drive/v3/files?q=...`) made with a `drive.file` token intentionally exclude any files not created directly by this app, unless explicitly selected by the user via Android Storage Access Framework (SAF).
2. **Asymmetrical Local Picker Capabilities & Missing Hierarchy Paths**: While the FIT card permitted multi-file selection via `ActivityResultContracts.OpenMultipleDocuments()`, the TCX and GPX cards only launched `ActivityResultContracts.OpenDocument()`, restricting athletes to importing a single file at a time. Furthermore, `LegacyImportEngine.kt` did not probe top-level folders (`listOf("TCX")`, `listOf("FIT")`, `listOf("GPX")`, `listOf("Workouts")`), and `BackupRestoreViewModel.kt` offered no diagnostic guidance when 0 files were discovered.

This ticket resolves the issue through a holistic solution:
1. **Multi-Document SAF Batch Ingestion for All Formats**: Enable `ActivityResultContracts.OpenMultipleDocuments()` across TCX and GPX cards in `ImportBackupTabsScreen.kt`, allowing athletes to open the Android system document picker, select Google Drive in the drawer, and batch-select as many externally copied files as desired.
2. **Unified Batch Importer in `BackupRestoreViewModel`**: Introduce `importWorkoutFiles(context, uris, format)` handling multi-file URI ingestion with progress updates, deduplication, error handling, and post-import cluster reconciliation for all formats.
3. **Top-Level Candidate Folder Scanning**: Extend `LegacyImportEngine.candidateFolderHierarchies` to include `listOf("TCX")`, `listOf("FIT")`, `listOf("GPX")`, and `listOf("Workouts")`.
4. **Descriptive Zero-Result Diagnostic**: Provide a localized diagnostic message explaining the `drive.file` constraint and guiding the user to the "Select files" button when a Google Drive scan returns 0 files.

---

## 2. Problem Statement & Root Cause

### 2.1 The Issue
Athletes migrating historical workout archives from Dropbox or desktop computers to Google Drive copy their files directly into Google Drive (e.g. into `TCX/` or `aTrainingTracker/TCX/`). When tapping "Google Drive scannen (TCX)", the scan terminates with 0 files found.

### 2.2 Forensic Root Cause
1. **Google Drive Security Boundary (`drive.file`)**:
   Under `https://www.googleapis.com/auth/drive.file`:
   - An application can query and download only files it created or files that the user opened with the app via the Google Drive Android picker or Android SAF.
   - Any file uploaded via desktop web browser, Google Drive desktop sync client, or external script is tagged as non-app-created.
   - Google Drive REST API `drive/v3/files` calls return an empty file list for these files.
2. **Asymmetry in `ImportBackupTabsScreen.kt`**:
   - `pickFitFilesLauncher` used `ActivityResultContracts.OpenMultipleDocuments()`.
   - `pickLegacyFileLauncher` used `ActivityResultContracts.OpenDocument()`.
   - When athletes turned to the local file picker to access Google Drive via the SAF provider, they could only select one `.tcx` or `.gpx` file at a time.
3. **Missing Root Folder Fallbacks**:
   - In `LegacyImportEngine.kt`, `candidateFolderHierarchies` only looked inside `aTrainingTracker/...`. Top-level directories (e.g. `TCX`, `FIT`, `GPX`, `Workouts`) created in the Drive root were ignored.
4. **Opaque Error Feedback**:
   - When 0 files were found, the UI displayed `R.string.legacy_import__finished_all_new` ("Imported 0 of 0 activities") without explaining the security scope limitation or pointing to the local file picker.

---

## 3. Impact Analysis & Technical Strategy

### 3.1 Architectural Decomposition
1. **UI Layer (`ImportBackupTabsScreen.kt`)**:
   - Replace single-document `pickLegacyFileLauncher` with `OpenMultipleDocuments()` launchers for TCX and GPX.
   - Store `pendingLocalImportUris: List<Uri>` and `pendingLocalImportFormat: String`.
   - Present `PreImportTuningBottomSheet` when local files are selected, matching the UX of FIT and cloud scans.
2. **ViewModel Layer (`BackupRestoreViewModel.kt`)**:
   - Unify `importFitFiles` and single-file legacy import into `importWorkoutFiles(context: Context, uris: List<Uri>, format: String)`.
   - Support `.fit`, `.tcx`, `.gpx`, and mixed batch imports with per-file progress updates, duplicate skipping, error recovery, and post-import reconciliation.
   - In `bulkRecoverGoogleDriveData`, detect `result.totalScanned == 0` and emit a descriptive diagnostic `google_drive_scan_empty_diagnostic`.
3. **Core Engine (`LegacyImportEngine.kt`)**:
   - Update `candidateFolderHierarchies` to include top-level folder candidates: `listOf("TCX")`, `listOf("FIT")`, `listOf("GPX")`, `listOf("Workouts")`.
4. **Localization (`res/values*/strings.xml`)**:
   - Add `google_drive_scan_empty_diagnostic` across all 9 languages (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 4. Risk Assessment & Mitigation

| Risk | Severity | Mitigation |
| :--- | :--- | :--- |
| Breaking least-privilege OAuth scope | Critical | Do NOT change OAuth scope. Maintain `drive.file` and leverage Android SAF for external files. |
| Memory pressure on large batch imports (e.g. 500 files) | Medium | Process files sequentially via temporary cache files; delete cache file in `finally` block after each iteration. |
| Duplicate activity ingestion | Low | Leverage `isWorkoutExisting(summaryDb, baseFileName)` and base filename sanitization to skip duplicates. |
| Inconsistent clustering parameters | Low | Retain mandatory `PreImportTuningBottomSheet` gate before executing local multi-document import. |
