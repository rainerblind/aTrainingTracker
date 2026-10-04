# Stage 2 Test Specification: ATT-1306 - Google Drive Integration for Automated Workout Export and Backup Synchronization

**Ticket**: [ATT-1306](https://rainerblind.atlassian.net/browse/ATT-1306)  
**Sub-task**: [ATT-2407](https://rainerblind.atlassian.net/browse/ATT-2407) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-40.16`  
**Branch**: `feature/ATT-1306`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Overview & Verification Strategy

This test specification defines the verification procedures for `REQ-DAT-020` under ticket [ATT-1306](https://rainerblind.atlassian.net/browse/ATT-1306) (Rework Cycle 2).

During the physical Google Pixel 10 evaluation of Cycle 1, the PO rejected the mock manual text-input dialog for email/token and dummy fallback token (`"gdrive_oauth_token"`). Cycle 2 mandates authentic, native Google Sign-In via Google Play Services (`com.google.android.gms:play-services-auth:21.3.0`) and genuine OAuth2 Bearer token retrieval via `GoogleAuthUtil.getToken(...)` on `Dispatchers.IO` with least-privilege `drive.file` scope.

The verification strategy ensures that:
1. **Native Google Sign-In & Verified OAuth2 Token Retrieval (`GoogleDriveAuthManager`, `TrainingApplication`)**:
   - `GoogleDriveAuthManager` configures `GoogleSignInOptions` requesting strictly `Scope("https://www.googleapis.com/auth/drive.file")` and athlete email.
   - `GoogleAuthUtil.getToken(context, account, "oauth2:https://www.googleapis.com/auth/drive.file")` executes on `Dispatchers.IO` and yields a verified Bearer token.
   - Manual text input dialogs for credentials or tokens are completely eliminated.
   - Credentials and `uploadToGoogleDrive = true` are stored IF AND ONLY IF Google Play Services authentication and token retrieval succeed.
   - Disconnecting executes `signOut()`, `revokeAccess()`, `GoogleAuthUtil.clearToken`, and wipes credentials cleanly.
   - Unlinked safety: reading tokens when unlinked returns `null` safely without `NullPointerException`.
2. **Google Drive REST Client (`GoogleDriveClient`)**:
   - Interacts with Google Drive API v3 using lightweight `OkHttp 5.5.0` without heavy GMS Drive dependencies.
   - Enforces the least-privilege `https://www.googleapis.com/auth/drive.file` OAuth scope.
   - Resolves and creates folder hierarchies (`aTrainingTracker/Workouts/` and `aTrainingTracker/Backups/`) idempotently.
   - Handles multipart upload, file overwriting, and streaming downloads with full resilience.
3. **Automated Workout Export (`GoogleDriveUploader`, `ExportManager`)**:
   - `GoogleDriveUploader` extends `BaseExporter` parallel to `DropboxUploader.java`.
   - `ExportType.GOOGLE_DRIVE` supports standard export formats (`CSV`, `GC`, `GPX`, `TCX`, `FIT`).
   - WorkManager jobs respect the configurable Wi-Fi only constraint (`NetworkType.UNMETERED` vs `NetworkType.CONNECTED`).
4. **Database Backup & Restore (`GoogleDriveBackupManager`, `BackupWorker`, `BackupRestoreViewModel`)**:
   - Periodic background backups upload `aTrainingTracker_backup.attbackup` to `aTrainingTracker/Backups/`.
   - Manual backup and restore operations execute smoothly via `BackupRestoreViewModel`.
   - Google Drive and Dropbox operate completely independently without mutual interference.
5. **Settings Dialog & Navigation Architecture (`GoogleDriveSettingsDialog`)**:
   - Modal bottom sheet complies with `AppBottomSheetContent` and `AppDialogActions.SaveCancel`.
   - Tapping "Verbinden" launches the native Google Sign-In activity result contract instead of any text input dialog.
   - Navigation drawer item `R.id.drawer_google_drive` and `SettingsBottomSheetType.GOOGLE_DRIVE` dispatch accurately.
6. **Localization & Full Suite Regression**:
   - 100% translation and plural parity across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
   - Zero regressions across the existing test suite (`./gradlew testDebugUnitTest`).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)

### Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: `REQ-DAT-020` (*Google Drive Integration for Automated Workout Export and Database Backup*), targeting `GoogleDriveClient.kt`, `GoogleDriveUploader.kt`, `GoogleDriveBackupManager.kt`, `GoogleDriveSettingsDialog.kt`, `GoogleDriveAuthManager.kt`, `ExportManager.java`, `BackupWorker.kt`, `BackupRestoreViewModel.kt`.
2. **Historical Origin & Commit Trace**: Introduced in `ATT-1306` (commit `931bf45d`) during Sprint 2026-40.14. Evaluated on physical Google Pixel 10 during Sprint 2026-40.15 Joint Review where mock text input dialog was rejected.
3. **Root Reason for Existing Formulation**: Initial cycle implemented the REST transport and export pipelines, but relied on a mock manual text-input dialog for email/auth token with a dummy fallback token (`"gdrive_oauth_token"`), failing production validation.
4. **Preservation of Core Invariants**: 
   - Least-privilege OAuth scope `https://www.googleapis.com/auth/drive.file` is strictly enforced.
   - Unlinked safety (reading credentials when unlinked returns `null` safely without `NullPointerException`) is preserved.
   - Existing cloud storage integrations (Dropbox, Strava) and local SQLite tables remain 100% independent and unaffected.

---

## 3. Requirement Traceability Matrix

| Requirement Clause | Test Specification ID | Test Classes / Suites | Verification Method | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-DAT-020` (1: Native Auth & Token Exchange) | `TST-DAT-015` (Group 1) | `GoogleDriveAuthSafetyTest.kt`, `GoogleDriveAuthManagerTest.kt` | JUnit 4 Unit Test | Specified |
| `REQ-DAT-020` (2: REST Client & Folders) | `TST-DAT-015` (Group 2) | `GoogleDriveClientTest.kt` | MockWebServer / Unit Test | Specified |
| `REQ-DAT-020` (3: Workout Uploader) | `TST-DAT-015` (Group 3) | `GoogleDriveUploaderTest.kt` | JUnit 4 Unit Test | Specified |
| `REQ-DAT-020` (4: Backup & Restore Manager) | `TST-DAT-015` (Group 4) | `GoogleDriveBackupManagerTest.kt` | Coroutines / Unit Test | Specified |
| `REQ-DAT-020` (5: ExportManager & Wi-Fi Constraints) | `TST-DAT-015` (Group 5) | `ExportManagerGoogleDriveTest.kt` | Unit / WorkManager Test | Specified |
| `REQ-DAT-020` (6: BackupWorker & ViewModel) | `TST-DAT-015` (Group 6) | `BackupWorkerGoogleDriveTest.kt`, `BackupRestoreViewModelGoogleDriveTest.kt` | Coroutines / ViewModel Test | Specified |
| `REQ-DAT-020` (7: UI Dialog & Native Launcher) | `TST-DAT-015` (Group 7) | `ModalBottomSheetDialogsIntegrityTest.kt`, `NavRoutesGoogleDriveTest.kt` | Reflection & Unit Test | Specified |
| `REQ-DAT-020` (8: Localization Parity) | `TST-DAT-015` (Group 8) | `TranslationParityTest.kt` | JUnit 4 Resource Test | Specified |
| `REQ-ALL` (Regression Invariant) | `TST-DAT-015` (Group 9) | Full `./gradlew testDebugUnitTest` | Clean-Room CI Suite | Specified |

---

## 4. Concrete Test Cases (`TST-DAT-015`)

### Group 1: Native Google Sign-In & Verified OAuth2 Token Acquisition
* **Test Class**: `com.atrainingtracker.trainingtracker.cloud.googledrive.GoogleDriveAuthSafetyTest` & `GoogleDriveAuthManagerTest`
* **Test Cases**:
  1. `testReadCredential_whenUnlinked_returnsNullSafely`:
     - Given unconfigured SharedPreferences.
     - Asserts `TrainingApplication.getGoogleDriveAuthToken()` and `getGoogleDriveAccountEmail()` return null without throwing `NullPointerException`.
  2. `testGoogleSignInOptions_requestsDriveFileScopeAndEmail`:
     - Verifies `GoogleDriveAuthManager.getSignInOptions()` configures `GoogleSignInOptions.DEFAULT_SIGN_IN` with `.requestEmail()` and `Scope("https://www.googleapis.com/auth/drive.file")`.
  3. `testAcquireBearerToken_executesOnIO_returnsVerifiedToken`:
     - Given a valid `GoogleSignInAccount`, executes `GoogleDriveAuthManager.acquireBearerToken(context, account)`.
     - Asserts token is retrieved and stored only upon successful exchange.
  4. `testStoreAndDisconnect_managesPreferencesAndTokens`:
     - Given user links account `athlete@gmail.com` with token `ya29.xyz`.
     - Asserts `uploadToGoogleDrive()` returns true and email matches.
     - When `disconnectGoogleDrive()` is invoked, `GoogleSignInClient.signOut()`, `revokeAccess()`, and `GoogleAuthUtil.clearToken` are called, credentials are wiped, and `uploadToGoogleDrive()` returns false.

### Group 2: Google Drive REST Client & Folder Hierarchy
* **Test Class**: `com.atrainingtracker.trainingtracker.cloud.googledrive.GoogleDriveClientTest`
* **Test Cases**:
  1. `testAuthorizationHeader_injectedOnRequests`:
     - Verifies every request emitted by `GoogleDriveClient` includes `Authorization: Bearer <token>`.
  2. `testEnsureFolderHierarchy_whenFolderExists_reusesExistingId`:
     - Mocks folder query returning existing folder ID `folder_123`.
     - Asserts client returns `folder_123` without emitting `POST` folder creation request.
  3. `testEnsureFolderHierarchy_whenFolderMissing_createsFolderWithParent`:
     - Mocks folder query returning empty list, followed by creation returning new folder ID `folder_456`.
     - Asserts folder creation payload specifies `mimeType: application/vnd.google-apps.folder` and parent ID.
  4. `testUploadOrOverwriteFile_createsMultipartUpload`:
     - Mocks file upload: verifies multipart body contains JSON metadata part with file name and parent ID, plus binary data part.
  5. `testDownloadFile_streamsDirectlyToDestination`:
     - Mocks file download: verifies file bytes are streamed into local destination file.

### Group 3: Google Drive Workout Uploader (`BaseExporter`)
* **Test Class**: `com.atrainingtracker.trainingtracker.exporter.uploader.GoogleDriveUploaderTest`
* **Test Cases**:
  1. `testDoExport_whenUnlinked_returnsFailureResult`:
     - When Google Drive is not linked, `doExport()` returns `ExportResult(false, false, "Google Drive credential is null or not linked")`.
  2. `testDoExport_whenLocalFileMissing_returnsFailureResult`:
     - When target workout file does not exist on disk, returns failure with descriptive message.
  3. `testDoExport_whenLinkedAndFileExists_uploadsToWorkoutsFolder`:
     - Mocks successful Drive client upload: returns `ExportResult(true, false, "successfully uploaded ... to Google Drive")`.

### Group 4: Google Drive Backup Manager
* **Test Class**: `com.atrainingtracker.trainingtracker.migration.GoogleDriveBackupManagerTest`
* **Test Cases**:
  1. `testUploadBackup_whenConnected_uploadsToBackupsFolder`:
     - Mocks `uploadBackup(context, backupFile)`: verifies file uploaded to `aTrainingTracker/Backups/` and returns true.
  2. `testDownloadBackup_whenConnected_writesToDestination`:
     - Mocks `downloadBackup(context, destinationFile)`: verifies download stream and returns true.
  3. `testBackupOperations_whenUnlinked_returnFalseWithoutCrashing`:
     - Asserts both upload and download return false safely when unlinked.

### Group 5: Export Subsystem & WorkManager Integration
* **Test Class**: `com.atrainingtracker.trainingtracker.exporter.ExportManagerGoogleDriveTest`
* **Test Cases**:
  1. `testExportType_googleDriveIncludesStandardFormats`:
     - Asserts `ExportType.GOOGLE_DRIVE` contains `CSV`, `GC`, `GPX`, `TCX`, `FIT`.
  2. `testExportManager_getExporter_returnsGoogleDriveUploader`:
     - Asserts `ExportManager.getExporter(context, ExportInfo(..., ExportType.GOOGLE_DRIVE))` returns instance of `GoogleDriveUploader`.
  3. `testWorkRequestConstraints_whenOnlyWifiEnabled_enforcesUnmetered`:
     - Asserts WorkManager work request uses `NetworkType.UNMETERED` when `uploadToGoogleDriveOnlyOnWifi == true`, and `NetworkType.CONNECTED` otherwise.

### Group 6: BackupWorker & ViewModel Integration
* **Test Class**: `com.atrainingtracker.trainingtracker.migration.BackupWorkerGoogleDriveTest`
* **Test Cases**:
  1. `testBackupWorker_whenGoogleDriveBackupEnabled_executesBackup`:
     - Mocks `automated_backups = true`, `google_drive_connected = true`, `uploadBackupToGoogleDrive = true`.
     - Asserts backup is created and `GoogleDriveBackupManager.uploadBackup` is called.
  2. `testBackupWorker_independentCoexistenceWithDropbox`:
     - When both Dropbox and Google Drive are enabled and Dropbox upload fails, Google Drive upload still executes and succeeds.
  3. `testBackupRestoreViewModel_uploadAndRestoreGoogleDrive`:
     - Verifies `uploadToGoogleDrive` and `restoreFromGoogleDrive` dispatch loading and success/error states.

### Group 7: UI Dialog & Navigation Contracts
* **Test Class**: `com.atrainingtracker.trainingtracker.ui.components.core.ModalBottomSheetDialogsIntegrityTest`
* **Test Cases**:
  1. `testGoogleDriveSettingsDialog_existsAndExposesComposableAndFragment`:
     - Uses reflection to verify `GoogleDriveSettingsDialog` composable function exists and is public.
     - Verifies `GoogleDriveSettingsDialogFragment` extends `AppBottomSheetDialogFragment`.
  2. `testNavRoutes_drawerGoogleDriveMapping`:
     - Asserts `NavRoutes.toSettingsBottomSheetType(R.id.drawer_google_drive)` returns `SettingsBottomSheetType.GOOGLE_DRIVE`.

### Group 8: Localization Parity
* **Test Class**: `com.atrainingtracker.trainingtracker.ui.translations.TranslationParityTest`
* **Test Cases**:
  1. `testGoogleDriveStrings_definedAcrossAll9Locales`:
     - Verifies all new string keys and plural resources exist in `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

### Group 9: Full Suite Clean-Room Regression
* **Command**: `./gradlew testDebugUnitTest`
* **Criteria**: 100% pass rate, 0 failures, 0 regressions.
