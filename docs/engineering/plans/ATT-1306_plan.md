# Stage 3 Implementation Plan: ATT-1306 - Google Drive Integration for Automated Workout Export and Backup Synchronization

**Ticket**: [ATT-1306](https://rainerblind.atlassian.net/browse/ATT-1306)  
**Sub-task**: [ATT-2279](https://rainerblind.atlassian.net/browse/ATT-2279) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-1306`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Architectural Design (SWE.2)

```
+-----------------------------------------------------------------------------------+
| UI Layer (Jetpack Compose)                                                        |
|                                                                                   |
|  [AppNavigationDrawer] -> drawer_google_drive                                     |
|  [ATrainingTrackerApp] -> SettingsBottomSheetType.GOOGLE_DRIVE                    |
|  [GoogleDriveSettingsDialog] (AppBottomSheetContent + AppDialogActions.SaveCancel)|
|    - GoogleDriveConnectionHeader: connected status, email, Connect/Disconnect     |
|    - Toggles: uploadWorkoutsToGoogleDrive, uploadBackupToGoogleDrive, wifiOnly    |
|    - Last sync timestamp & status                                                 |
|  [WorkoutSummary] -> ExportStatus display for GOOGLE_DRIVE                        |
+-----------------------------------------+-----------------------------------------+
                                          |
+-----------------------------------------v-----------------------------------------+
| ViewModel / Application Core Layer                                                |
|  TrainingApplication:                                                             |
|    - uploadToGoogleDrive(), uploadWorkoutsToGoogleDrive(), uploadBackup...()       |
|    - storeGoogleDriveCredential(email, token), deleteGoogleDriveCredential()      |
|  BackupRestoreViewModel: uploadToGoogleDrive(), restoreFromGoogleDrive()          |
|  BackupWorker: parallel backup upload to Dropbox & Google Drive                   |
+--------------------+------------------------------------+-------------------------+
                     |                                    |
+--------------------v--------------------+ +-------------v-------------------------+
| Workout Export Subsystem                | | Backup Subsystem                      |
|  - ExportType.GOOGLE_DRIVE              | |  - GoogleDriveBackupManager.kt        |
|  - ExportManager.java                   | |    * uploadBackup(backupFile)         |
|  - GoogleDriveUploader.kt (BaseExporter)| |    * downloadBackup(destinationFile)  |
+--------------------+--------------------+ +-------------+-------------------------+
                     |                                    |
                     +-----------------+------------------+
                                       |
+--------------------------------------v--------------------------------------------+
| GoogleDriveClient (OkHttp 5.5.0)                                                  |
|  - Scope: https://www.googleapis.com/auth/drive.file                              |
|  - ensureFolderHierarchy("aTrainingTracker", "Workouts" / "Backups")              |
|  - uploadOrOverwriteFile(folderId, fileName, mimeType, file)                      |
|  - downloadFile(folderId, fileName, destinationFile)                              |
+--------------------------------------+--------------------------------------------+
                                       |
+--------------------------------------v--------------------------------------------+
| Google Drive REST API v3                                                          |
|  https://www.googleapis.com/drive/v3/files                                        |
|  https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart            |
+-----------------------------------------------------------------------------------+
```

---

## 2. Order-Dependent Construction Steps

### Step 1: Localization & Plurals Parity (9 Locales) + Assets
* **Target Files**:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
  - `app/src/main/res/values/ids.xml` (`drawer_google_drive`)
  - `app/src/main/res/drawable/ic_google_drive.xml`
* **Tokens**:
  - `google_drive`: "Google Drive"
  - `google_drive_connect`: "Mit Google Drive verbinden" / "Connect to Google Drive"
  - `google_drive_disconnect`: "Trennen" / "Disconnect"
  - `google_drive_connected_status`: "Mit Google Drive verbunden" / "Connected to Google Drive"
  - `google_drive_disconnected_status`: "Nicht mit Google Drive verbunden" / "Not connected to Google Drive"
  - `upload_workouts_to_google_drive`: "Workouts automatisch exportieren" / "Automatically export workouts"
  - `upload_workouts_to_google_drive_summary`: "Lädt Aktivitäten (FIT, TCX, GPX, CSV) nach dem Speichern in Google Drive hoch" / "Uploads activities (FIT, TCX, GPX, CSV) to Google Drive after saving"
  - `upload_backup_to_google_drive`: "Datenbank-Backup automatisch synchronisieren" / "Automatically synchronize database backup"
  - `upload_backup_to_google_drive_summary`: "Sichert die Datenbank regelmäßig in Google Drive" / "Periodically backs up the database to Google Drive"
  - `google_drive_only_wifi`: "Nur über WLAN hochladen" / "Upload over Wi-Fi only"
  - `google_drive_only_wifi_summary`: "Schont das mobile Datenvolumen" / "Conserves mobile data"
  - `google_drive_last_sync`: "Letzter Upload: %1$s" / "Last upload: %1$s"
  - `google_drive_never_synced`: "Bisher keine Synchronisierung" / "Not synchronized yet"
  - `restore_from_google_drive`: "Aus Google Drive wiederherstellen" / "Restore from Google Drive"
  - `upload_to_google_drive`: "In Google Drive sichern" / "Back up to Google Drive"
  - Plurals: `export_notification__detail__GoogleDrive_waiting`, `ongoing`, `success`, `failed`.

### Step 2: Core Preferences & Credential Management (`TrainingApplication.java`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`
* **Changes**:
  - Add preference keys:
    - `SP_UPLOAD_TO_GOOGLE_DRIVE = "uploadToGoogleDrive"`
    - `SP_UPLOAD_WORKOUTS_TO_GOOGLE_DRIVE = "uploadWorkoutsToGoogleDrive"`
    - `SP_UPLOAD_BACKUP_TO_GOOGLE_DRIVE = "uploadBackupToGoogleDrive"`
    - `SP_GOOGLE_DRIVE_ONLY_WIFI = "googleDriveOnlyWifi"`
    - `SP_GOOGLE_DRIVE_ACCOUNT_EMAIL = "googleDriveAccountEmail"`
    - `SP_GOOGLE_DRIVE_AUTH_TOKEN = "googleDriveAuthToken"`
    - `SP_GOOGLE_DRIVE_LAST_SYNC = "googleDriveLastSync"`
    - `SP_GOOGLE_DRIVE_LAST_SYNC_STATUS = "googleDriveLastSyncStatus"`
  - Add getters/setters with defensive null safety.
  - Add `storeGoogleDriveCredential(String email, String token)`, `deleteGoogleDriveCredential()`.

### Step 3: Google Drive REST Client (`GoogleDriveClient.kt`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/cloud/googledrive/GoogleDriveClient.kt`
* **Architecture**:
  - Uses `OkHttpClient` with connection/read timeouts.
  - Injects `Authorization: Bearer <token>` on all requests.
  - Implements:
    - `ensureFolderHierarchy(folderNames: List<String>): String?`: recursive parent-child folder resolution with ID caching.
    - `uploadOrOverwriteFile(folderId: String, fileName: String, mimeType: String, file: File): Boolean`: multipart upload with metadata + binary content.
    - `downloadFile(folderId: String, fileName: String, destinationFile: File): Boolean`: streams response directly to destination.
    - `findFileId(folderId: String, fileName: String): String?`

### Step 4: Export Subsystem Integration
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/ExportType.java`
    - Add `GOOGLE_DRIVE(R.string.google_drive, new FileFormat[]{FileFormat.CSV, FileFormat.GC, FileFormat.GPX, FileFormat.TCX, FileFormat.FIT})`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/GoogleDriveUploader.kt`
    - Subclasses `BaseExporter`
    - Uploads target file to `aTrainingTracker/Workouts/` via `GoogleDriveClient`.
  - `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/ExportManager.java`
    - `getExporter`: return `new GoogleDriveUploader(context)` for `GOOGLE_DRIVE`.
    - `newWorkout`: set status to `TRACKING` if `uploadToGoogleDrive()` and `uploadWorkoutsToGoogleDrive()`.
    - `startFullExportProcess`: enqueue Google Drive work request.
    - `createWorkRequest`: configure `NetworkType.UNMETERED` if `uploadToGoogleDriveOnlyOnWifi()`.
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/export/ExportStatusDataProvider.kt`
    - Handle `ExportType.GOOGLE_DRIVE` in `getPluralIdsFor` mapping to `R.plurals.export_notification__detail__GoogleDrive_*`.
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`
    - Include `ExportType.GOOGLE_DRIVE` in `orderedExportTypes`.

### Step 5: Database Backup & Restore Integration
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/migration/GoogleDriveBackupManager.kt`
    - `uploadBackup(context: Context, backupFile: File): Boolean`
    - `downloadBackup(context: Context, destinationFile: File): Boolean`
    - Target: `aTrainingTracker/Backups/aTrainingTracker_backup.attbackup`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupWorker.kt`
    - Evaluate both Dropbox and Google Drive connections independently:
      - If `automated_backups` and (`dropboxConnected || googleDriveConnected`), create `.attbackup`.
      - Upload to Dropbox if connected; upload to Google Drive if connected.
      - Neither blocks the other.
  - `app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt`
    - Add `uploadToGoogleDrive(context: Context)`
    - Add `restoreFromGoogleDrive(context: Context)`

### Step 6: UI & Navigation Integration
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/googledrive/GoogleDriveConnectionHeader.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/googledrive/GoogleDriveSettingsDialog.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/googledrive/GoogleDriveSettingsDialogFragment.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/NavRoutes.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt`

### Step 7: Unit Testing & CI Verification
* **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/cloud/googledrive/GoogleDriveAuthSafetyTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/cloud/googledrive/GoogleDriveClientTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/uploader/GoogleDriveUploaderTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/GoogleDriveBackupManagerTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/ExportManagerGoogleDriveTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupWorkerGoogleDriveTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/ModalBottomSheetDialogsIntegrityTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/translations/TranslationParityTest.kt`
* **Full CI Command**: `./gradlew testDebugUnitTest`

---

## 3. Invariants & Guardrails

1. **Scope Bounding**: Only `https://www.googleapis.com/auth/drive.file` scope is used. The app never accesses user documents, photos, or other folders outside `aTrainingTracker/`.
2. **Independent Provider Coexistence**: Dropbox and Google Drive must operate independently without shared failures or mutual blocking.
3. **Data Integrity**: Database backups maintain 100% format compatibility with `.attbackup`.
4. **Clean Code & Line Limit**: All newly authored UI files stay well under 400 lines of code.
5. **Human Decision Gate**: The parent ticket `ATT-1306` must only transition to `Final Review (Human)` upon Stage 5 completion.
