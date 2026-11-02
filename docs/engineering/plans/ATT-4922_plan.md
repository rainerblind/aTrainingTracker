# Stage 3: Implementation Plan - ATT-4922: Resilient Google Drive Upload Retry, Post-Restore Re-Auth & Stuck Export Recovery

**Ticket**: [ATT-4922](https://rainerblind.atlassian.net/browse/ATT-4922)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-DAT-037` (`REQ-DAT-037.1`..`REQ-DAT-037.5`)  
**Test Mapping**: `TST-DAT-033` (`TST-DAT-033.1`..`TST-DAT-033.7`)  
**Branch**: `feature/ATT-4922`  
**Author**: AI Agent 1 (Implementer - subagent_pro)  
**Date**: 2026-11-02  

---

## 1. Problem Description & Scope
Offline workouts queued with Wi-Fi-only constraints (`NetworkType.UNMETERED`) fail permanently on attempt 0 for Google Drive with `FINISHED_FAILED: Google Drive credential is null or not linked` without retrying. Post-restore sanitization leaves Google Drive in a "zombie connected" state. This plan defines the atomic SWE.2 architecture to: (1) acquire fresh Bearer tokens silently via Google Play Services on null cached tokens; (2) eliminate post-restore zombie states across UI and preferences; (3) classify transient I/O, rate-limit, and 5xx errors with `shallRetry = true` across Google Drive and Dropbox uploaders; and (4) recover stuck `FINISHED_FAILED` credential rows upon re-authentication.

---

## 2. Traceability & Requirements Mapping
* **`REQ-DAT-037.1` / `TST-DAT-033.1`**: Silent token acquisition in `GoogleDriveUploader.kt`, `GoogleDriveBackupManager.kt`, `BackupRestoreViewModel.kt`, and `LegacyImportEngine.kt`.
* **`REQ-DAT-037.2` / `TST-DAT-033.2`**: Zombie-state elimination in `AuthCredentialsStore.sanitizeAfterRestore()`, `MainActivityWithNavigation.checkPreferences()`, and `GoogleDriveSettingsDialog.kt`.
* **`REQ-DAT-037.3` / `TST-DAT-033.3`**: Transient retry classification (`shallRetry = true`) in `GoogleDriveUploader.kt` and `DropboxUploader.java`.
* **`REQ-DAT-037.4` / `TST-DAT-033.4`**: Stuck export recovery in `ExportStatusDatabaseManager.java` and `ExportManager.java`.
* **`REQ-DAT-037.5` / `TST-DAT-033.5`**: Preserved system invariants (`MAX_RETRIES = 5`, token isolation in `auth_credentials_prefs.xml`, permanent failure `shallRetry = false`).

---

## 3. System Invariants & Preserved Behavior
1. **Code Lock (Rule 4)**: No production source files modified in `app/src/...` until Gate 3 audit passes.
2. **Credential Isolation (`REQ-DAT-032.1`)**: OAuth Bearer tokens strictly confined to `auth_credentials_prefs.xml`; never written to default `SharedPreferences`.
3. **Retry Bound**: WorkManager retry attempts strictly capped at `ExportConfig.MAX_RETRIES` (5); permanent errors (missing files, 401/403) strictly return `shallRetry = false`.
4. **Upgrade & Existing-User Behavior (Rule 66 Section 5.6)**: Active athletes with existing credentials remain uninterrupted. Restoring an archive resets cloud export flags unless device Google Sign-In account can silently refresh.

---

## 4. Proposed Architectural Changes (SWE.2)
* **Silent Token Acquisition (AC 1)**: In `GoogleDriveAuthManager`, add `getOrRefreshTokenSync(context): String?` (or invoke `refreshTokenSync(context)`). In `GoogleDriveUploader.doExport()`, `GoogleDriveBackupManager.uploadBackup()/downloadBackup()`, `BackupRestoreViewModel`, and `LegacyImportEngine`, attempt silent token refresh when `uploadToGoogleDrive()` is true and cached token is null before throwing unlinked errors.
* **Zombie-State Elimination (AC 2)**: In `AuthCredentialsStore.sanitizeAfterRestore(context)`, inspect `GoogleSignIn.getLastSignedInAccount(context)`; if null or unrecoverable, reset `SP_UPLOAD_TO_GOOGLE_DRIVE = false` and `SP_UPLOAD_TO_DROPBOX = false` in default `SharedPreferences`. In `MainActivityWithNavigation.checkPreferences()`, self-heal `uploadToGoogleDrive = false` if credentials cannot be recovered. In `GoogleDriveSettingsDialog.kt`, gate `isConnected` on credential or active Google account presence (`getGoogleDriveAuthToken() != null || GoogleSignIn.getLastSignedInAccount(context) != null`).
* **Transient Retry Classification (AC 3)**:
  * In `GoogleDriveUploader.kt`: Return `ExportResult(false, true, ...)` on `IOException`, HTTP 408/429/5xx, or null HTTP response code. Return `shallRetry = false` on missing file, unrecoverable 401/403, or invalid format.
  * In `DropboxUploader.java`: Catch `DbxException` and check for transient subclasses (`NetworkIOException`, `RetryException`, `ServerException`, `RateLimitException`) -> `shallRetry = true`. Catch `IOException` -> `shallRetry = true`.
* **Stuck Export Recovery (AC 4)**: In `ExportStatusDatabaseManager.java`, implement `recoverStuckCredentialExports(ExportType, String)` to update `FINISHED_FAILED` rows to `WAITING`. In `ExportManager.java`, expose `recoverStuckGoogleDriveExports()`, called when Google Drive connects in `GoogleDriveSettingsDialog` or on successful silent token recovery.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Token Acquisition & Uploader Guards)**: Wire silent refresh fallback into `GoogleDriveUploader.kt`, `GoogleDriveBackupManager.kt`, `BackupRestoreViewModel.kt`, and `LegacyImportEngine.kt`.
2. **Step 2 (Transient Error Retry Classification)**: Update failure branches in `GoogleDriveUploader.kt` and `DropboxUploader.java` to set `shallRetry = true` on transient network/HTTP errors.
3. **Step 3 (Post-Restore Preference Sanitization & Settings Dialog)**: Update `AuthCredentialsStore.sanitizeAfterRestore()`, `MainActivityWithNavigation.checkPreferences()`, and `GoogleDriveSettingsDialog.kt` to eliminate zombie connected states.
4. **Step 4 (Stuck Export Recovery)**: Implement `recoverStuckCredentialExports()` in `ExportStatusDatabaseManager.java` and `recoverStuckGoogleDriveExports()` in `ExportManager.java`; wire to `GoogleDriveSettingsDialog`.
5. **Step 5 (Unit Tests & Test Isolation)**: Add targeted tests in `GoogleDriveUploaderTest`, `DropboxUploaderTest`, `AuthCredentialsStoreTest`, `GoogleDriveSettingsDialogTest`, and `ExportStatusDatabaseManagerTest`.

---

## 6. Verification Plan & Test Isolation (Rule 66 Section 5.5)
* **Targeted Unit Verification**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.exporter.uploader.*" --tests "com.atrainingtracker.trainingtracker.security.AuthCredentialsStoreTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.googledrive.GoogleDriveSettingsDialogTest" --tests "com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManagerTest"`
  * `python3 tools/verify_requirement_governance.py`
* **Test Isolation Invariant**: All tests use isolated in-memory or temporary DBs and mocked preferences, clearing static state in `@After` blocks.
* **Rollback Plan**: Atomic commit rollback on `feature/ATT-4922` via GitFlow if regressions occur.
