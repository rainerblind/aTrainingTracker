# Stage 2: Requirement & Test Specification - ATT-2346

**Ticket**: [ATT-2346](https://atrainingtracker.atlassian.net/browse/ATT-2346)  
**Parent Epic**: [ATT-162](https://atrainingtracker.atlassian.net/browse/ATT-162) (*[Epic] Cloud integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Branch**: `feature/ATT-2346`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Formal Requirement Specification

### REQ-MIG-034: Google Drive Historical Workout File Discovery & Bulk Recovery (.fit, .tcx, .gpx)

The system SHALL provide automated discovery, download, and multi-format bulk importing of historical workout activity files (`.fit`, `.tcx`, `.gpx`) from Google Drive (ATT-2346):

1. **Google Drive API Discovery (`GoogleDriveClient.kt`)**:
   - `GoogleDriveClient` SHALL provide recursive file discovery (`listFilesRecursively`) under specified folder hierarchies (`folderId`).
   - The query SHALL paginate through results using `nextPageToken` and filter entries strictly matching target extensions: `.fit`, `.tcx`, and `.gpx`.
   - `GoogleDriveClient` SHALL provide direct file download by Drive file ID (`downloadFileById(fileId, destinationFile)`).
   - Least-privilege OAuth scope `https://www.googleapis.com/auth/drive.file` SHALL be strictly preserved.
2. **Concurrent Recovery Pipeline (`LegacyImportEngine.kt`)**:
   - `LegacyImportEngine` SHALL implement `bulkRecoverFromGoogleDrive(context, format, listener, uploadToStrava): RecoveryResult`.
   - The engine SHALL scan standard folder paths: `["aTrainingTracker", "Workouts"]`, `["aTrainingTracker", "TCX"]`, `["aTrainingTracker", "GPX"]`, and `["aTrainingTracker", "FIT"]`.
   - Discovered entries SHALL be deduplicated across scanned paths by base filename (`removeSuffix("-TMP").removeSuffix("~").lowercase()`) per REQ-MIG-031.
   - The engine SHALL spawn 3 concurrent workers bounded by `interactionSemaphore(3)`.
   - Before downloading, each entry SHALL be checked against `isWorkoutExisting(summaryDb, baseFileName)`. Existing workouts SHALL be skipped immediately without downloading (REQ-MIG-015).
   - Discovered files SHALL be routed by extension:
     - `.fit` -> `importFromFitInternal`
     - `.tcx` -> `importFromTcxInternal`
     - `.gpx` -> `importFromGpxInternal`
   - Temporary download artifacts SHALL be cleaned up in a `finally` block.
3. **ViewModel & Post-Recovery Reconciliation (`BackupRestoreViewModel.kt`)**:
   - `BackupRestoreViewModel` SHALL provide `bulkRecoverFromGoogleDrive(context, format)`.
   - When Google Drive is disconnected (`!TrainingApplication.uploadToGoogleDrive() || token.isNullOrBlank()`), an error state SHALL be set without starting a scan.
   - Upon successful import of one or more activities, the system SHALL asynchronously trigger post-recovery reconciliation:
     - `WorkoutRepository.loadAllWorkouts()`
     - `PeriodsRepository.syncPeriodsIfDiscrepancy()`
     - `WorkoutClusterRepository.refreshClusters()`
4. **UI Integration & Connection Gating (`ImportBackupTabsScreen.kt`)**:
   - `ImportTabContent` SHALL render a "Scan Google Drive" action button within the cloud recovery section.
   - When Google Drive is connected, clicking "Scan Google Drive" SHALL present the pre-import cluster tuning bottom sheet (`PreImportTuningBottomSheet`), confirming which triggers `viewModel.bulkRecoverFromGoogleDrive(context, "all")`.
   - When Google Drive is disconnected, the button SHALL render in a visually deactivated state, and clicking it SHALL present the Google Drive connection prompt.
5. **100% 9-Language Localization Parity**:
   - All user-facing strings (`scan_google_drive`, `legacy_import__downloading_google_drive`) SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement only (`REQ-MIG-033`). Builds upon `REQ-MIG-015` (skip before download), `REQ-MIG-016` (recursive cloud recovery), `REQ-MIG-023` (unlinked cloud feature deactivation), `REQ-MIG-026` (reactive reconciliation), and `REQ-MIG-031` (cross-folder deduplication).
2. **Historical Origin & Commit Trace**: Ticket `ATT-2346`, sprint `2026-41.1`, target release `V4.9.39`, Epic `ATT-162` (*[Epic] Cloud integration*).
3. **Root Reason for Existing Formulation**: Athletes maintaining workout archives in Google Drive lacked bulk-recovery support and were forced into manual one-by-one file imports.
4. **Preservation of Core Invariants**:
   - Least-privilege OAuth scope `drive.file` must remain untouched.
   - Existing Room database schemas, `WorkoutSummaries` columns, and clustering math must remain strictly intact.
   - All tests across all modules must maintain a 100% pass rate.

---

## 3. Formal Acceptance Criteria (Given-When-Then)

### Criterion 1: Google Drive Recursive File Discovery
- **Given** a connected Google Drive account with workout files in `aTrainingTracker/Workouts/` and subfolders,
- **When** `GoogleDriveClient.listFilesRecursively` is invoked,
- **Then** all files ending in `.fit`, `.tcx`, and `.gpx` are discovered and returned as `DriveFileEntry` objects.

### Criterion 2: Duplicate Skipping Prior to Download
- **Given** workout files in Google Drive that already exist in `WorkoutSummariesDatabase`,
- **When** `bulkRecoverFromGoogleDrive` processes the queue,
- **Then** existing workouts are skipped immediately without executing a network download, and `skippedCount` is incremented.

### Criterion 3: Multi-Format Routing (.fit, .tcx, .gpx)
- **Given** a collection of `.fit`, `.tcx`, and `.gpx` files in Google Drive,
- **When** downloading and importing them,
- **Then** `.fit` files route to `importFromFitInternal`, `.tcx` files route to `importFromTcxInternal`, and `.gpx` files route to `importFromGpxInternal`.

### Criterion 4: Connection Gating
- **Given** Google Drive is disconnected,
- **When** the athlete views `ImportTabContent`,
- **Then** the "Scan Google Drive" button is visually muted, and clicking it prompts the connection dialog.

---

## 4. Formal Test Case Specification

### TST-MIG-031: Google Drive Historical Workout File Discovery & Bulk Recovery Verification

1. **`GoogleDriveClientRecursiveListingTest`**:
   - Verify that `listFilesRecursively` queries children of `folderId`.
   - Verify pagination handling when `nextPageToken` is returned.
   - Verify filtering by extensions (`.fit`, `.tcx`, `.gpx`).
   - Verify `downloadFileById` streams content into destination `File`.
2. **`GoogleDriveBulkRecoveryContractTest`**:
   - Verify that `bulkRecoverFromGoogleDrive` skips already existing workouts without downloading.
   - Verify that `.fit`, `.tcx`, and `.gpx` formats are routed to their respective internal import methods.
   - Verify cross-folder base name deduplication.
3. **`BackupRestoreViewModelGoogleDriveTest`**:
   - Verify that disconnected state sets error message and aborts recovery.
   - Verify that successful recovery triggers repository reloads (`loadAllWorkouts`, `syncPeriodsIfDiscrepancy`, `refreshClusters`).
4. **`TranslationParityTest`**:
   - Verify `scan_google_drive` and `legacy_import__downloading_google_drive` across EN, DE, ES, FR, IT, JA, NL, PL, PT.
5. **Clean-Room Full Suite Regression**:
   - Execute `./gradlew testDebugUnitTest` verifying 100% pass rate.
