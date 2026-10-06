# Stage 1: Problem Domain & Root Cause Analysis - ATT-2346

**Ticket**: [ATT-2346](https://atrainingtracker.atlassian.net/browse/ATT-2346)  
**Parent Epic**: [ATT-162](https://atrainingtracker.atlassian.net/browse/ATT-162) (*[Epic] Cloud integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Branch**: `feature/ATT-2346`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Statement & Operational Context

While ATT-1306 introduced Google Drive integration for automated workout export and full database backup/restore, it lacks support for scanning and bulk-recovering individual workout files (`.fit`, `.tcx`, `.gpx`).
On Dropbox, athletes have long relied on "Scan Dropbox" (`bulkRecoverFromDropbox` / `scan_tcx`) to discover and restore their historical activities from `/TCX`, `/apps/Workouts/TCX`, `/GPX`, and `/apps/Workouts/GPX`.
Athletes migrating from other platforms or recovering activities who maintain workout archives in Google Drive are forced to manually transfer and import files one-by-one.
Adding a parallel "Scan Google Drive" feature enables athletes to bulk-import their historical workouts directly from Google Drive with automatic duplicate skipping, 3-worker concurrent background processing, and reactive post-import stats and cluster reconciliation.

---

## 2. Forensic Investigation & Root Cause

1. **Absence of Recursive Listing & File Search in `GoogleDriveClient.kt`**:
   - `GoogleDriveClient.kt` currently implements folder resolution (`ensureFolderHierarchy`, `findFolderIdByName`, `createFolder`), single-file lookup (`findFileIdByName`), file upload (`uploadOrOverwriteFile`), and download (`downloadFile`).
   - It lacks recursive directory traversal or multi-extension querying to retrieve file lists (`List<DriveFileEntry>`) matching `.fit`, `.tcx`, and `.gpx`.
2. **Missing Direct File ID Downloader in `GoogleDriveClient.kt`**:
   - `downloadFile` searches for a file by filename in a given folder before fetching it. When iterating over query results where the Drive `fileId` is already known, a direct `downloadFileById(fileId, destinationFile)` endpoint is required for optimal performance and network efficiency.
3. **No Google Drive Recovery Pipeline in `LegacyImportEngine.kt`**:
   - `LegacyImportEngine.kt` contains `bulkRecoverFromDropbox(...)`, which downloads from Dropbox paths and feeds files into `importFromTcxInternal`, `importFromGpxInternal`, and `importFromFitInternal`.
   - No parallel `bulkRecoverFromGoogleDrive(context, format, listener, uploadToStrava)` exists.
4. **Missing UI & Action Triggers in `ImportBackupTabsScreen.kt` & `BackupRestoreViewModel.kt`**:
   - `BackupRestoreViewModel.kt` only exposes `bulkRecoverLegacyData(context, format)` for Dropbox.
   - `ImportTabContent` in `ImportBackupTabsScreen.kt` only provides a single scan button for Dropbox (`scan_tcx`). It requires a dedicated "Scan Google Drive" action with connection gating.

---

## 3. Requirement Archaeology & Chesterton's Fence Audit

1. **Historical Context**:
   - `REQ-MIG-016`: Paginated & Recursive Cloud Recovery (Dropbox).
   - `REQ-MIG-018`: Duplicate Skipping & Base Name Deduplication.
   - `REQ-MIG-023`: Unlinked Cloud Feature Deactivation.
   - `REQ-MIG-026`: Post-Bulk Recovery Reactive Reconciliation (`WorkoutRepository.loadAllWorkouts()`, `syncPeriodsIfDiscrepancy()`, `refreshClusters()`).
   - `REQ-MIG-031`: Cross-Folder Base Name Deduplication.
2. **Chesterton's Fence Invariant**:
   - Least-privilege OAuth scope: Google Drive access MUST remain strictly scoped to `https://www.googleapis.com/auth/drive.file`. Only folders created by the app (e.g. `aTrainingTracker/Workouts`, `aTrainingTracker/TCX`, `aTrainingTracker/GPX`, `aTrainingTracker/FIT`) or shared with the app are accessible under this scope.
   - Deduplication: Existing workouts must be skipped prior to downloading (`isWorkoutExisting(summaryDb, baseFileName)`).
   - 9-Language Localization: All new labels (`scan_google_drive`, `legacy_import__downloading_google_drive`) must maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
   - Room Database & Schema Invariants: SQLite schemas and Room entities must not be modified.

---

## 4. Scope & Architecture Strategy

### 4.1. Core Extensions
1. **`GoogleDriveClient.kt`**:
   - Introduce `data class DriveFileEntry(val id: String, val name: String, val size: Long? = null, val mimeType: String? = null)`.
   - Add `fun listFilesRecursively(folderId: String, extensions: List<String>): List<DriveFileEntry>`.
   - Add `fun downloadFileById(fileId: String, destinationFile: File): Boolean`.
2. **`LegacyImportEngine.kt`**:
   - Add `suspend fun bulkRecoverFromGoogleDrive(context: Context, format: String = "all", listener: ProgressListener? = null, uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()): RecoveryResult`.
   - Scan standard hierarchies: `["aTrainingTracker", "Workouts"]`, `["aTrainingTracker", "TCX"]`, `["aTrainingTracker", "GPX"]`, `["aTrainingTracker", "FIT"]`.
   - Deduplicate discovered entries across candidate directories by base filename (REQ-MIG-031).
   - Concurrently process files using 3-worker channel bounded by `interactionSemaphore(3)`.
   - Route files by extension: `.fit` -> `importFromFitInternal`, `.tcx` -> `importFromTcxInternal`, `.gpx` -> `importFromGpxInternal`.
3. **`BackupRestoreViewModel.kt`**:
   - Add `fun bulkRecoverFromGoogleDrive(context: Context, format: String = "all")`.
   - Handle connection validation (`TrainingApplication.uploadToGoogleDrive() && getGoogleDriveAuthToken() != null`).
   - Trigger post-import reactive reconciliation (`loadAllWorkouts()`, `syncPeriodsIfDiscrepancy()`, `refreshClusters()`).
4. **`ImportBackupTabsScreen.kt`**:
   - Add "Scan Google Drive" button in `ImportTabContent`.
   - Render muted when disconnected; trigger pre-import cluster tuning sheet when connected or Google Drive connection prompt when disconnected.
5. **Localization**:
   - Add `scan_google_drive` and `legacy_import__downloading_google_drive` to all 9 `strings.xml` files.

---

## 5. Verification Plan

1. **Unit & Integration Tests**:
   - `GoogleDriveClientRecursiveListingTest`: Verify recursive file discovery, pagination parsing, and extension filtering.
   - `GoogleDriveBulkRecoveryContractTest`: Verify file routing by extension (`.fit`, `.tcx`, `.gpx`), duplicate skipping, and base name deduplication.
   - `BackupRestoreViewModelGoogleDriveTest`: Verify connection gate, state emissions, and post-recovery reconciliation.
   - `TranslationParityTest`: Verify 100% 9-language localization parity.
2. **Regression**:
   - `./gradlew testDebugUnitTest` clean-room execution with 100% pass rate.
