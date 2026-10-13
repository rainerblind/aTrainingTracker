# Stage 5: Walkthrough - ATT-3315: Enable import of externally copied workout files from Google Drive with file picker fallback

**Ticket**: [ATT-3315](https://rainerblind.atlassian.net/browse/ATT-3315)  
**Sub-task**: [ATT-3401](https://rainerblind.atlassian.net/browse/ATT-3401) (`[Test]`)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-MIG-034` (amended & extended), `REQ-UI-293` (amended & extended)  
**Test Mapping**: `TST-MIG-031`, `TST-UI-253`  
**Branch**: `feature/ATT-3315`  
**Author**: AI Agent 1 (Verification & Quality Engineer)  
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

## 1. Executive Summary

This deliverable fulfills Stage 5 verification for **ATT-3315**. During Sprint 2026-41.10 Joint Review of ATT-2346, the athlete copied historical workout files (.tcx) into the Google Drive `TCX` root folder, but the bulk scanner discovered 0 files. Investigation identified two core causes:
1. **Google Drive API Scope Restrictions (`drive.file`)**: Under least-privilege `drive.file` OAuth scope, apps can only query files created by the application itself or explicitly chosen by the user via Android's Storage Access Framework (SAF). Files uploaded from desktop or web interfaces are invisible to raw API listing queries.
2. **Missing Top-Level Folder Candidates**: The candidate folder hierarchy only searched nested paths (`aTrainingTracker/TCX/`, `aTrainingTracker/Workouts/`) rather than top-level directories (`TCX/`, `FIT/`, `GPX/`, `Workouts/`).
3. **Single-Document Picker Constraint**: Local TCX and GPX buttons previously invoked `ActivityResultContracts.OpenDocument()`, requiring athletes to import files one-by-one rather than bulk-selecting an entire directory of workouts.

### Solutions Delivered in ATT-3315:
1. **Multi-Document SAF Selection**: Upgraded TCX and GPX file pickers in `ImportBackupTabsScreen.kt` to `ActivityResultContracts.OpenMultipleDocuments()`. Athletes can now select multiple workout files from Google Drive (or local storage/SD card) through the system document picker, completely bypassing `drive.file` API visibility limitations.
2. **Expanded Candidate Folder Probing**: Added top-level folder candidates (`listOf("FIT")`, `listOf("TCX")`, `listOf("GPX")`, `listOf("Workouts")`) in `LegacyImportEngine.kt` before searching nested folders.
3. **Zero-Scan Actionable Diagnostic**: If a Google Drive API scan discovers 0 accessible files (`totalScanned == 0`), the app emits a descriptive localized diagnostic message explaining the Google Drive security model and guiding the user to tap "Select local file(s)" to pick their files from Google Drive via the system document picker.
4. **Batch Ingestion Pipeline**: Introduced `importWorkoutFiles` in `BackupRestoreViewModel.kt` with progress feedback, duplicate skipping, error aggregation, and post-import database reconciliation (`WorkoutRepository.loadAllWorkouts`, `PeriodsRepository.syncPeriodsIfDiscrepancy`, `WorkoutClusterRepository.refreshClusters`).
5. **Pre-Import Parameter Tuning**: Both single and batch file selections present `PreImportTuningBottomSheet` before ingestion, guaranteeing that clustering tolerances are calibrated before inserting workouts.
6. **9-Language Localization Parity**: All new diagnostic and progress tokens translated across EN, DE, ES, FR, IT, JA, NL, PL, and PT.

---

## 2. Verification Results

### 2.1 Targeted Unit & Contract Tests
- **`TranslationParityTest`**: **PASSED** (100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT).
- **`LegacyImportEngineGoogleDriveFolderResolutionTest`**: **PASSED** (verifies top-level candidates `TCX`, `FIT`, `GPX`, `Workouts` prioritized correctly).
- **`BackupRestoreViewModelGoogleDriveTest`**:
  - `testBulkRecoverGoogleDriveData_whenDisconnected_emitsErrorState`: **PASSED**
  - `testBulkRecoverGoogleDriveData_whenConnected_executesRecoveryAndEmitsSuccess`: **PASSED**
  - `testBulkRecoverGoogleDriveData_whenZeroScanned_emitsDiagnosticMessage`: **PASSED**
- **`BackupRestoreViewModelFitTest`**: **PASSED** (verifies batch ingestion, progress reporting, duplicate skipping).
- **`BackupRestoreViewModelNavigationTest`**: **PASSED** (verifies post-import navigation workout ID propagation).
- **`BackupRestoreViewModelFormatScopingTest`**: **PASSED** (verifies format-scoped routing for TCX, GPX, FIT).
- **`ImportFormatCardsContractTest`**: **PASSED** (verifies 3-card layout, triad buttons, and scoped format dispatch).
- **`ImportFormatFilePickerContractTest`**: **PASSED** (verifies format MIME arrays and validation).

### 2.2 Clean-Room Test Suite Pass Rate
- Executed `./gradlew testDebugUnitTest`.
- 100% pass rate with zero regressions across all modules.
