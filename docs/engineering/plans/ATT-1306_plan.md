# Stage 3 Implementation Plan: ATT-1306 - Google Drive Integration for Automated Workout Export and Backup Synchronization

**Ticket**: [ATT-1306](https://rainerblind.atlassian.net/browse/ATT-1306)  
**Sub-task**: [ATT-2408](https://rainerblind.atlassian.net/browse/ATT-2408) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-40.16`  
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
|    - ActivityResultLauncher: launches native GoogleSignInClient.signInIntent      |
|    - Toggles: uploadWorkoutsToGoogleDrive, uploadBackupToGoogleDrive, wifiOnly    |
|    - Last sync timestamp & status                                                 |
|  [WorkoutSummary] -> ExportStatus display for GOOGLE_DRIVE                        |
+-----------------------------------------+-----------------------------------------+
                                          |
+-----------------------------------------v-----------------------------------------+
| Authentication & Credential Management Layer                                      |
|  GoogleDriveAuthManager (com.google.android.gms:play-services-auth:21.3.0):       |
|    - getSignInOptions(): GoogleSignInOptions (drive.file scope + email)           |
|    - getSignInClient(context): GoogleSignInClient                                 |
|    - acquireBearerToken(context, account): GoogleAuthUtil.getToken() on IO        |
|    - disconnect(context): signOut(), revokeAccess(), clearToken(), deleteCreds()  |
|  TrainingApplication:                                                             |
|    - uploadToGoogleDrive(), uploadWorkoutsToGoogleDrive(), uploadBackup...()       |
|    - storeGoogleDriveCredential(email, token), deleteGoogleDriveCredential()      |
|    - Defensive unlinked null-safety                                               |
+-----------------------------------------+-----------------------------------------+
                                          |
+-----------------------------------------v-----------------------------------------+
| ViewModel / Application Core Layer                                                |
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

### Step 1: Authentication Architecture & Token Acquisition (`GoogleDriveAuthManager.kt`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/cloud/googledrive/GoogleDriveAuthManager.kt`
* **Responsibilities**:
  - `getSignInOptions()`: Builds `GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)` with `.requestEmail()` and `.requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))`.
  - `getClient(context: Context)`: Returns `GoogleSignIn.getClient(context, getSignInOptions())`.
  - `acquireBearerToken(context: Context, account: GoogleSignInAccount): Result<String>`:
    - Must run on `withContext(Dispatchers.IO)`.
    - Invokes `GoogleAuthUtil.getToken(context, account.account, "oauth2:https://www.googleapis.com/auth/drive.file")`.
    - Handles exceptions (`UserRecoverableAuthException`, `GoogleAuthException`, `IOException`).
    - On success, invokes `TrainingApplication.storeGoogleDriveCredential(account.email, token)` and sets `TrainingApplication.setUploadToGoogleDrive(true)`.
  - `disconnect(context: Context)`:
    - Invokes `client.signOut()` and `client.revokeAccess()`.
    - Clears cached tokens via `GoogleAuthUtil.clearToken(context, token)`.
    - Calls `TrainingApplication.deleteGoogleDriveCredential()`.

### Step 2: Settings Dialog Refactoring (`GoogleDriveSettingsDialog.kt`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/googledrive/GoogleDriveSettingsDialog.kt`
* **Changes**:
  - Completely eliminate the mock text-input `AlertDialog` (with `manualEmail` and `manualToken` fields).
  - Register `rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult())`.
  - On "Verbinden" click: launches `GoogleDriveAuthManager.getClient(context).signInIntent`.
  - In launcher result callback:
    - Parses task via `GoogleSignIn.getSignedInAccountFromIntent(result.data)`.
    - Launches coroutine on `Dispatchers.IO` to execute `acquireBearerToken(...)`.
    - Displays loading indicator during authentication and provides clear error feedback if cancelled or failed.
  - On "Trennen" click: executes `GoogleDriveAuthManager.disconnect(context)` and updates UI state to disconnected.

### Step 3: Google Drive REST Client & Token Refresh Verification (`GoogleDriveClient.kt`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/cloud/googledrive/GoogleDriveClient.kt`
* **Verification**:
  - Injects `Authorization: Bearer <token>` on all requests.
  - Returns `401 Unauthorized` handling: if a token expires, returns failure prompting re-authentication or token refresh.
  - Validates folder hierarchy resolution (`aTrainingTracker/Workouts/` and `aTrainingTracker/Backups/`).

### Step 4: Unit & Contract Tests
* **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/cloud/googledrive/GoogleDriveAuthManagerTest.kt` (New)
    - Verifies `GoogleSignInOptions` scope and email configuration.
    - Verifies disconnect logic calls `deleteGoogleDriveCredential()`.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/cloud/googledrive/GoogleDriveAuthSafetyTest.kt`
    - Verifies unlinked null safety.
    - Verifies preferences storage and wiping.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/cloud/googledrive/GoogleDriveClientTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/uploader/GoogleDriveUploaderTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/GoogleDriveBackupManagerTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/ExportManagerGoogleDriveTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupWorkerGoogleDriveTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/ModalBottomSheetDialogsIntegrityTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/translations/TranslationParityTest.kt`

### Step 5: Full Suite Clean-Room Regression
* **Command**: `./gradlew testDebugUnitTest`
* **Target**: 100% pass rate, 0 failures, 0 regressions.

---

## 3. Invariants & Guardrails

1. **Mandatory Programmatic Pre-Check (Rule 3)**:
   - Before modifying any source files in `app/src/main/...`, verify:
     `python3 tools/jira_util.py check-gate ATT-2408` exits with code 0 (`GATE_PASSED`).
2. **Scope Bounding**:
   - Only `https://www.googleapis.com/auth/drive.file` scope is requested. The app never accesses user documents, photos, or other folders outside `aTrainingTracker/`.
3. **Zero Developer Mock Dialogs**:
   - Manual text fields for email or auth tokens are strictly banned. Credentials are only persisted when verified by Google Play Services.
4. **Independent Provider Coexistence**:
   - Dropbox and Google Drive operate independently without shared failures or mutual blocking.
5. **Human Decision Gate (Rule 1)**:
   - Parent ticket `ATT-1306` must only transition to `Final Review (Human)`.
