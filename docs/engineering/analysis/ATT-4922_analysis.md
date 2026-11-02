# Stage 1 Analysis: ATT-4922 - Resilient Google Drive Background Upload Retry, Post-Restore Re-Auth Parity & Stuck Export Recovery

**Ticket**: [ATT-4922](https://rainerblind.atlassian.net/browse/ATT-4922)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Branch**: `feature/ATT-4922`  
**Author**: AI Agent 1 (Implementer - subagent_pro)  
**Date**: 2026-11-02  

---

## 1. Problem Statement & Motivation
When workouts are recorded offline or on mobile data with Wi-Fi-only constraints (`NetworkType.UNMETERED`), `WorkManager` defers export jobs. Upon reconnecting to WLAN, Dropbox uploads succeed, but all queued Google Drive workout uploads fail permanently on attempt 0 with `FINISHED_FAILED: Google Drive credential is null or not linked` and never retry. Furthermore, post-restore credential sanitization leaves Google Drive in a "zombie connected" state, scheduling doomed export jobs even though credentials have been wiped.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Bug A (Zombie Connected State)**: `AuthCredentialsStore.sanitizeAfterRestore()` purges `auth_credentials_prefs.xml`, but leaves `uploadToGoogleDrive = true` in default preferences. Unlike Dropbox (where `DropboxSettingsDialog` and `MainActivityWithNavigation` check `readDropboxCredential() != null`), `GoogleDriveSettingsDialog` and `ExportManager` evaluate only the boolean flag `TrainingApplication.uploadToGoogleDrive()`, falsely presenting Google Drive as connected and scheduling unexecutable jobs.
* **Bug B (Premature Cached-Token Abort)**: `GoogleDriveUploader.kt` (lines 61–64), `GoogleDriveBackupManager.kt` (lines 60, 122), `BackupRestoreViewModel.kt` (lines 381, 410, 766), and `LegacyImportEngine.kt` (line 324) abort immediately with an unlinked error whenever the cached token in `AuthCredentialsStore` is null or blank. They never attempt `GoogleDriveAuthManager.refreshTokenSync(context)`, bypassing Google Play Services silent token acquisition despite an active Google account on the device.
* **Bug C (Missing Transient Retry Classification)**: In `GoogleDriveUploader.kt` (lines 82, 110, 114, 117) and `DropboxUploader.java` (lines 70, 73), every failure returns `ExportResult(false, false, ...)` (`shallRetry = false`). In `ExportWorker.kt` (line 124), `WorkManager` only triggers `Result.retry()` when `result.shallRetry() == true`. Any transient Wi-Fi reconnection hiccup, captive portal, rate limit (429), or 5xx server error permanently marks the upload `FINISHED_FAILED` on attempt 0.
* **Independent Peer Review & Verification**:
  * Verified `GoogleDriveClient.kt` (lines 135–151): `executeCallWithRetry` possesses 401 token refresh capability, but `GoogleDriveUploader` never reached it due to the premature null token guard at line 62.
  * Verified `DropboxUploader.java` (lines 68–74): `IOException` and `DbxException` (`NetworkIOException`, `RetryException`, `ServerException`) unconditionally return `shallRetry = false`.
  * Verified `ExportWorker.kt` (line 124): Without `shallRetry = true`, exponential backoff and retry attempt tracking (`runAttemptCount < MAX_RETRIES - 1`) are completely bypassed.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Silent token recovery: Attempt `GoogleDriveAuthManager.refreshTokenSync(context)` in `GoogleDriveUploader`, `GoogleDriveBackupManager`, `BackupRestoreViewModel`, and `LegacyImportEngine` before reporting null credential.
  * Eliminate zombie connected state: Guard Google Drive connected state with credential/account presence in `GoogleDriveSettingsDialog`, `MainActivityWithNavigation.checkPreferences()`, and reset `uploadToGoogleDrive = false` in `AuthCredentialsStore.sanitizeAfterRestore()` if unrecoverable.
  * Transient retry classification: Return `ExportResult(false, true, ...)` on `IOException`, HTTP 408/429/5xx, and network `null` codes in `GoogleDriveUploader.kt`, and on `IOException`, `NetworkIOException`, `RetryException`, `ServerException`, `RateLimitException` in `DropboxUploader.java`.
  * Recovery of stuck exports: Provide clean re-queueing of `FINISHED_FAILED` exports stuck on credential errors when re-authenticating or retrying.
* **Out-of-Scope Non-Goals**:
  * Modifying Google Drive REST client endpoints or altering the least-privilege `drive.file` OAuth scope.
  * Modifying Strava token exchange or uploader mechanics.
  * Altering WorkManager core configuration or changing `MAX_RETRIES` (5).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Amends `REQ-DAT-030` (Google Drive Integration) and `REQ-DAT-032` (OAuth Credential Isolation & Restore Sanitization).
* **Historical Origin & Commit Trace**: `931bf45d` (ATT-1306, initial Drive integration); `01b4b49c` & `b2d4e3d6` (ATT-2631, 401 client token refresh); `e3f6de91` (ATT-3995, WorkManager export pipeline & `shallRetry`); `203baa27` (ATT-4023, credential isolation); `8ad50842` (ATT-4459, restore sanitization); `07ac3cd6` (ATT-4666, settings dialog unification).
* **Root Reason for Existing Formulation**: Upfront null checks were defensive guards to avoid unnecessary network calls without a cached token. Restore sanitization aimed to prevent credential leakage into portable backups without accounting for active device Google accounts. Hardcoded `shallRetry = false` was inherited from local file writers (`FitFileWriter`).
* **Preservation of Core Invariants**: Credential isolation in `auth_credentials_prefs.xml` (`REQ-DAT-032.1`) and clean re-auth invariant (`REQ-DAT-032.4`) remain inviolate. Permanent errors (missing local file, unrecoverable 401/403) strictly set `shallRetry = false`.

---

## 5. Architectural Strategy & High-Level Solution
* **Silent Token Acquisition**: In `GoogleDriveAuthManager`, expose or utilize `getOrRefreshTokenSync(context)` / `refreshTokenSync(context)` when `uploadToGoogleDrive()` is enabled but cached token is null.
* **Connected State Guarding**: Align `GoogleDriveSettingsDialog.kt` with `DropboxSettingsDialog.kt` by checking actual credential availability (`TrainingApplication.getGoogleDriveAuthToken() != null || GoogleSignIn.getLastSignedInAccount(context) != null`). In `MainActivityWithNavigation.checkPreferences()`, self-heal `uploadToGoogleDrive = false` if credentials cannot be recovered. In `AuthCredentialsStore.sanitizeAfterRestore()`, reset `SP_UPLOAD_TO_GOOGLE_DRIVE = false` and `SP_UPLOAD_TO_DROPBOX = false` if no active device account exists.
* **Transient Error Handling**: Update `GoogleDriveUploader.kt` and `DropboxUploader.java` to return `shallRetry = true` for transient network exceptions and HTTP status codes (408, 429, 5xx, null), letting `WorkManager` handle backoff.
* **Stuck Export Remediation**: Add `requeueFailedGoogleDriveExports()` in `ExportManager` / `ExportStatusDatabaseManager` to transition stuck credential failure rows back to `WAITING` upon successful connection.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. WorkManager retry attempts must never exceed `ExportConfig.MAX_RETRIES` (5).
  2. Tokens must remain strictly confined to `auth_credentials_prefs.xml`, never written to default preferences.
  3. Non-transient errors (missing files, permanent authentication revocation) must never trigger retry loops.
* **Risk Rating**: **MEDIUM** — Touches background upload error handling, OAuth token refresh lifecycle, and export status transitions. Mitigated by robust targeted unit tests across `GoogleDriveUploaderTest`, `DropboxUploaderTest`, and `AuthCredentialsStoreTest`.
