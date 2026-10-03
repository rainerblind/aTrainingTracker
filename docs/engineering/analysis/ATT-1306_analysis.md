# Stage 1 Analysis: ATT-1306 - Google Drive Integration for Automated Workout Export and Backup Synchronization

**Ticket**: [ATT-1306](https://rainerblind.atlassian.net/browse/ATT-1306)  
**Sub-task**: [ATT-2277](https://rainerblind.atlassian.net/browse/ATT-2277) (`[Analysis]`)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-1306`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Statement & Motivation

aTrainingTracker currently provides automated cloud synchronization and database backup exclusively through Dropbox (`DropboxUploader.java`, `DropboxBackupManager.kt`, `DropboxSettingsDialog.kt`). However, user adoption of cloud features is bottlenecked by account requirements:
1. **Third-Party Dependency Friction**: Virtually all Android athletes maintain an active Google account with 15 GB of bundled cloud storage. Requiring athletes to create and link an external Dropbox account creates a significant barrier to entry, leaving many users without automated off-device backup or desktop activity export.
2. **Disaster Recovery Vulnerability**: Athletes without Dropbox who drop, lose, or replace their Android smartphone risk catastrophic data loss of their entire training history, sensor calibrations, gear profiles, and custom tracking layouts.
3. **Desktop Analysis Workflow**: Athletes who analyze their recorded workouts in desktop training platforms (such as GoldenCheetah, WKO5, or training log spreadsheets) require immediate, zero-friction access to FIT, TCX, GPX, and CSV files in their personal cloud drive without manual USB cables or email exports.

Introducing a native **Google Drive Integration** alongside the existing Dropbox and Strava integrations resolves this gap. By utilizing the least-privilege `drive.file` scope, aTrainingTracker can securely create and synchronize to dedicated directories (`aTrainingTracker/Workouts/` and `aTrainingTracker/Backups/`) without exposing or requesting broader access to the athlete's personal files.

---

## 2. Root Cause & Gap Analysis (Forensic Investigation)

### 2.1 Current Export and Backup Architectural State
The application's export and backup subsystems are structured around clean abstraction layers:
- **Workout File Exporters (`ExportManager.java`, `BaseExporter.java`)**:
  - `ExportType` enum currently enumerates `FILE`, `DROPBOX`, and `COMMUNITY`.
  - `ExportManager.newWorkout(fileBaseName)` and `exportWorkout(workoutData)` orchestrate export jobs via `WorkManager`.
  - `DropboxUploader.java` handles uploading generated files to Dropbox.
- **Database Backup & Migration (`BackupManager.kt`, `BackupWorker.kt`, `BackupRestoreViewModel.kt`)**:
  - `BackupManager.createBackup(context)` generates an atomic `.attbackup` zip archive of databases, shared preferences, and datastores.
  - `DropboxBackupManager.kt` provides `uploadBackup` and `downloadBackup` to `/Backups/aTrainingTracker_backup.attbackup`.
  - `BackupWorker.kt` periodically triggers backups on Wi-Fi via `PeriodicWorkRequestBuilder`.
  - `BackupRestoreViewModel.kt` provides reactive states and triggers for manual backup creation, upload, and download restore.
- **UI & Navigation (`AppNavigationDrawer.kt`, `NavRoutes.kt`, `ATrainingTrackerApp.kt`)**:
  - Bottom sheet dialogs inherit from `AppBottomSheetContent` and `AppBottomSheetDialogFragment` using `AppDialogActions.SaveCancel`.

### 2.2 Architectural Gaps for Google Drive Integration
1. **Enum & Data Mapping Gap**:
   - `ExportType` lacks `GOOGLE_DRIVE` with associated formats (`CSV`, `GC`, `GPX`, `TCX`, `FIT`).
   - `ExportStatusDataProvider.kt` has an exhaustive `when (exportType)` requiring plural IDs for Google Drive status strings.
   - `WorkoutRepository.kt` lists `orderedExportTypes` without Google Drive.
2. **Transport & Client Gap**:
   - No Google Drive client or uploader currently exists in `com.atrainingtracker.trainingtracker.exporter.uploader`.
   - The app does not include heavy Google Drive Java SDK dependencies to avoid library bloat, DEX limits, and Guava/Android 14 compatibility risks. A direct, lightweight REST client utilizing the existing `OkHttp 5.5.0` runtime provides maximum stability, full multipart/resumable upload support, and effortless unit testability with mock responses.
3. **Folder Architecture & Idempotence**:
   - Unlike Dropbox which can implicitly create directories on file write, Google Drive requires hierarchical folder resolution:
     - Query root for folder `aTrainingTracker` (`mimeType = 'application/vnd.google-apps.folder' and trashed = false`).
     - Query / create subfolder `Workouts` or `Backups` under parent `aTrainingTracker`.
     - Query / create or overwrite target file inside the subfolder.
4. **Network Constraint Enforcement (Wi-Fi Only)**:
   - Athletes frequently request mobile data preservation. `ExportManager` currently enforces `NetworkType.CONNECTED` for cloud uploaders. Google Drive must respect an independent `googleDriveOnlyWifi` preference (`NetworkType.UNMETERED` vs `NetworkType.CONNECTED`).
5. **Independent Service Coexistence**:
   - Athletes must be able to use Dropbox, Google Drive, or both simultaneously without blocking or cross-service interference. Failure in one cloud provider must not abort export or backup to the other.

---

## 3. User Scope Grounding (ATT-1250)

### In-Scope Goals
1. **Google Drive Authentication & Credential Storage**:
   - Manage connection state via `TrainingApplication` (`uploadToGoogleDrive()`, `uploadWorkoutsToGoogleDrive()`, `uploadBackupToGoogleDrive()`, `uploadToGoogleDriveOnlyOnWifi()`).
   - Store Google account email and OAuth token / credentials securely in SharedPreferences.
   - Support seamless connect and disconnect (revocation & local token wipe).
2. **Google Drive REST Client (`GoogleDriveClient.kt`)**:
   - Lightweight, robust HTTP client based on `OkHttp 5.5.0`.
   - Least-privilege `https://www.googleapis.com/auth/drive.file` scope.
   - Idempotent directory creation and caching for `aTrainingTracker/Workouts/` and `aTrainingTracker/Backups/`.
   - Multipart file upload and overwrite capability for activity files and backup bundles.
   - Streaming file download for database restore operations.
3. **Automated Workout Export (`GoogleDriveUploader.kt`)**:
   - Subclass `BaseExporter` parallel to `DropboxUploader.java`.
   - Respect user's active export format selections (`FIT`, `TCX`, `GPX`, `CSV`).
   - Support background WorkManager execution with optional Wi-Fi constraint.
4. **Database Backup & Restore (`GoogleDriveBackupManager.kt`)**:
   - `uploadBackup(context, backupFile)` and `downloadBackup(context, destinationFile)`.
   - Integrated into `BackupWorker.kt` for scheduled automatic backups.
   - Integrated into `BackupRestoreViewModel.kt` for manual backup and cloud restore.
5. **Settings Bottom Sheet Dialog (`GoogleDriveSettingsDialog.kt`)**:
   - Compose bottom sheet conforming to `AppBottomSheetContent` and `AppDialogActions.SaveCancel`.
   - Authentic Google Drive branding icon and header displaying connected account email.
   - Feature toggles:
     - *"Workouts automatisch exportieren"*
     - *"Datenbank-Backup automatisch synchronisieren"*
     - *"Nur über WLAN hochladen"*
   - Display timestamp and status of last sync.
6. **Navigation & Navigation Drawer Integration**:
   - `R.id.drawer_google_drive` in navigation drawer and `SettingsBottomSheetType.GOOGLE_DRIVE` in `NavRoutes.kt` / `ATrainingTrackerApp.kt`.
7. **9-Language Localization Parity**:
   - Complete translations across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Out-of-Scope Non-Goals (Scope Bounding)
- Automatic background polling of remote Google Drive changes (export/backup is push-based and pull-on-demand).
- Modifying underlying SQLite schema or `.attbackup` format (retains existing verified bundle format).
- Google Fit / Health Connect fitness data synchronization (Google Drive is used for file and backup storage).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

### Requirement Archaeology
- **Original Requirement ID & Target**: Net-new requirement (`REQ-DAT-020` / `TST-DAT-015`), extending the cloud export framework (`REQ-DAT-006`, `REQ-DAT-018`, `REQ-MIG-023`) under Epic `ATT-162` (*Cloud integration*).
- **Historical Origin & Precedents**:
  - `REQ-MIG-023` established unlinked cloud feature deactivation and credential safety invariants (`DropboxCredentialSafetyTest.kt`).
  - `REQ-UI-152` established standard bottom sheet modal contracts for cloud dialogs (`DropboxSettingsDialog.kt`, `ModalBottomSheetDialogsIntegrityTest.kt`).
  - `REQ-DAT-018` integrated multi-format activity exports (`FitFileWriter.java`, `ExportManagerFitTest.kt`).
- **Chesterton's Fence Findings**:
  - `ExportManager` and `ExportStatusDatabaseManager` use string keys in SQLite (`TYPE = "DROPBOX"`). Adding `GOOGLE_DRIVE` is fully non-destructive and requires zero database migration.
  - `ExportStatusDataProvider.kt` uses an exhaustive `when (exportType)`. Adding `GOOGLE_DRIVE` requires matching plural strings in `strings.xml`.
  - `BackupWorker` currently exits early if `!dropboxConnected`. It must be adapted so that if *either* Dropbox or Google Drive (with backup enabled) is connected, the backup is produced and dispatched to each enabled service independently.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 System Architecture Diagram
```
+-----------------------------------------------------------------------------------+
|                              User Interface Layer                                 |
|  [AppNavigationDrawer] -> [GoogleDriveSettingsDialog] (Compose ModalBottomSheet)  |
|  [BackupRestoreScreen] -> BackupRestoreViewModel (upload/restore Google Drive)    |
|  [WorkoutSummary]      -> ExportStatus indicator (Google Drive status)            |
+-----------------------------------------+-----------------------------------------+
                                          |
+-----------------------------------------v-----------------------------------------+
|                              Application Core Layer                               |
|  TrainingApplication: google_drive_account_email, auth token, toggles             |
|  BackupRestoreViewModel: uploadToGoogleDrive(), restoreFromGoogleDrive()          |
|  BackupWorker: parallel backup upload to Dropbox & Google Drive                   |
+--------------------+------------------------------------+-------------------------+
                     |                                    |
+--------------------v--------------------+ +-------------v-------------------------+
|      Workout Export Subsystem           | |         Backup Subsystem              |
|  ExportType.GOOGLE_DRIVE                | |  GoogleDriveBackupManager.kt          |
|  ExportManager.java                     | |  - uploadBackup(backupFile)           |
|  GoogleDriveUploader.kt (BaseExporter)  | |  - downloadBackup(destinationFile)    |
+--------------------+--------------------+ +-------------+-------------------------+
                     |                                    |
                     +-----------------+------------------+
                                       |
+--------------------------------------v--------------------------------------------+
|                       GoogleDriveClient (OkHttp 5.5.0)                            |
|  - Scope: https://www.googleapis.com/auth/drive.file                              |
|  - ensureFolderHierarchy("aTrainingTracker", "Workouts" / "Backups")              |
|  - uploadOrOverwriteFile(folderId, fileName, mimeType, file)                      |
|  - downloadFile(folderId, fileName, destinationFile)                              |
+--------------------------------------+--------------------------------------------+
                                       |
+--------------------------------------v--------------------------------------------+
|                         Google Drive REST API v3                                  |
|  https://www.googleapis.com/drive/v3/files                                        |
|  https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart            |
+-----------------------------------------------------------------------------------+
```

### 5.2 Key Components
1. **`GoogleDriveClient.kt` (`com.atrainingtracker.trainingtracker.cloud.googledrive`)**:
   - Encapsulates Google Drive v3 REST interactions using `OkHttpClient`.
   - Handles Bearer authorization header injection.
   - Resolves folder ID by path, caching IDs to minimize roundtrips.
   - Handles multi-part upload and overwrite logic.
2. **`GoogleDriveUploader.kt` (`com.atrainingtracker.trainingtracker.exporter.uploader`)**:
   - Extends `BaseExporter`.
   - Resolves exported file from `getBaseDirFile(mContext)`.
   - Uploads to `aTrainingTracker/Workouts/` via `GoogleDriveClient`.
3. **`GoogleDriveBackupManager.kt` (`com.atrainingtracker.trainingtracker.migration`)**:
   - `uploadBackup`: uploads `.attbackup` to `aTrainingTracker/Backups/`.
   - `downloadBackup`: downloads `.attbackup` from `aTrainingTracker/Backups/` to local target.
4. **`ExportType.java` & `ExportManager.java`**:
   - Add `GOOGLE_DRIVE` enum value.
   - Update `getExporter` switch.
   - Update `newWorkout` and `startFullExportProcess`.
   - Configure WorkManager constraints (`NetworkType.UNMETERED` if `googleDriveOnlyWifi`).
5. **`GoogleDriveSettingsDialog.kt` & `GoogleDriveSettingsDialogFragment.kt`**:
   - Composable bottom sheet modal dialog and fragment wrapper.
   - Account connection header with brand styling.
   - Toggles and sync metadata.

---

## 6. System Invariants & Risk Assessment

### Core Invariants
1. **Least Privilege (`drive.file`)**: The app must never request broader Drive scopes (`drive` or `drive.readonly`). Only app-created files within `aTrainingTracker/` are manipulated.
2. **Non-Interference with Dropbox & Community**: Failures, timeouts, or disconnection of Google Drive must not affect Dropbox or Strava exports or backups.
3. **Resilience & Offline Handling**: Network failures trigger standard WorkManager exponential backoff without application crashes or database corruption.
4. **9-Language Parity**: All newly introduced string and plural resources must be translated and validated across 9 languages.

### Risk Rating: LOW-MEDIUM
- **Justification**: The export and backup subsystems are already cleanly abstracted behind `BaseExporter` and `BackupWorker`. OkHttp is a mature dependency with zero additional APK footprint. The REST approach prevents third-party SDK conflicts.
