# Stage 2: Requirement & Test Specification - ATT-4922: Resilient Google Drive Background Upload Retry, Post-Restore Re-Auth Parity & Stuck Export Recovery

**Ticket**: [ATT-4922](https://rainerblind.atlassian.net/browse/ATT-4922)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-DAT-037` (`REQ-DAT-037.1`..`REQ-DAT-037.5`, amending `REQ-DAT-030` and `REQ-DAT-032`)  
**Test Mapping**: `TST-DAT-033` (`TST-DAT-033.1`..`TST-DAT-033.7`)  
**Branch**: `feature/ATT-4922`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-11-02  

---

## 1. Overview & Verification Strategy
When workouts are recorded offline with Wi-Fi-only constraints (`NetworkType.UNMETERED`), Google Drive exports fail permanently on attempt 0 with `FINISHED_FAILED: Google Drive credential is null or not linked` and never retry. `ATT-4922` resolves this through four interlocking mechanisms:
1. **Silent Token Recovery**: Attempt `GoogleDriveAuthManager.refreshTokenSync(context)` on null cached token before failing.
2. **Zombie-Connected Elimination**: Reset `uploadToGoogleDrive = false` in `AuthCredentialsStore.sanitizeAfterRestore()` if unrecoverable; align `GoogleDriveSettingsDialog.kt` to check credential presence.
3. **Transient Retry Classification**: Return `ExportResult(false, true, ...)` (`shallRetry = true`) on `IOException`, HTTP 408/429/5xx in `GoogleDriveUploader.kt`, and `IOException`/`DbxException` network subclasses in `DropboxUploader.java`.
4. **Stuck Export Recovery**: Transition stuck `FINISHED_FAILED` credential rows back to `WAITING` upon reconnection or silent refresh.

---

## 2. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Component | Scope & Target |
| :--- | :--- | :--- | :--- |
| `REQ-DAT-037.1` | `TST-DAT-033.1` | `GoogleDriveUploaderTest`, `GoogleDriveBackupManagerTest` | Silent token refresh fallback on null cached token via `refreshTokenSync`. |
| `REQ-DAT-037.2` | `TST-DAT-033.2` | `AuthCredentialsStoreTest`, `GoogleDriveSettingsDialogTest` | Reset cloud flags on restore sanitization; verify credential presence in dialog. |
| `REQ-DAT-037.3` | `TST-DAT-033.3` | `GoogleDriveUploaderTest`, `DropboxUploaderTest` | `shallRetry = true` on `IOException`, HTTP 408/429/5xx, and `NetworkIOException`. |
| `REQ-DAT-037.4` | `TST-DAT-033.4` | `ExportManagerTest`, `ExportStatusDatabaseManagerTest` | Re-queue stuck `FINISHED_FAILED` credential errors on re-authentication. |
| `REQ-DAT-037.5` | `TST-DAT-033.5` | Unit Regression Suite | Hard limit `MAX_RETRIES = 5`; permanent errors keep `shallRetry = false`. |

---

## 3. Concrete Test Scenarios (Given-When-Then)

### TST-DAT-033.1: Silent Token Refresh Fallback
* **Given** `uploadToGoogleDrive == true` and cached token is null with valid device Google account,
* **When** `GoogleDriveUploader.doExport()` or `GoogleDriveBackupManager.uploadBackup()` executes,
* **Then** `GoogleDriveAuthManager.refreshTokenSync(context)` is called, acquiring a fresh token and completing upload.

### TST-DAT-033.2: Post-Restore Preference Sanitization & Connected State
* **Given** a restored backup with wiped credentials and no active Google account,
* **When** `AuthCredentialsStore.sanitizeAfterRestore(context)` executes,
* **Then** `uploadToGoogleDrive` and `uploadToDropbox` are set to `false`, and `GoogleDriveSettingsDialog` displays disconnected.

### TST-DAT-033.3: Transient Error Classification & Retry Contract
* **Given** a queued upload encountering a transient network error (`IOException`, HTTP 429, HTTP 503),
* **When** `GoogleDriveUploader` or `DropboxUploader` executes,
* **Then** `ExportResult.shallRetry()` returns `true`, and `ExportWorker` returns `Result.retry()`.

### TST-DAT-033.4: Stuck Export Re-Queueing on Re-Authentication
* **Given** exports stuck in `FINISHED_FAILED` with `"Google Drive credential is null or not linked"`,
* **When** Google Drive is re-connected or silent refresh succeeds,
* **Then** stuck export rows transition to `WAITING` and re-queue.

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-DAT-030` (*Google Drive Query Escaping*) and `REQ-DAT-032` (*OAuth Credential Isolation*).
* **Historical Origin & Commit Trace**: `931bf45d` (ATT-1306, Drive integration); `01b4b49c` & `b2d4e3d6` (ATT-2631, 401 refresh); `e3f6de91` (ATT-3995, WorkManager retry); `203baa27` (ATT-4023, credential isolation); `8ad50842` (ATT-4459, restore sanitization).
* **Root Reason for Existing Formulation**: Null token check was an upfront guard to avoid unnecessary HTTP requests; restore sanitization purged credentials to prevent backup leakage without handling device accounts; `shallRetry = false` was inherited from file exporters.
* **Preservation of Core Invariants**: Credential isolation in `auth_credentials_prefs.xml` (`REQ-DAT-032.1`) and clean re-auth invariant (`REQ-DAT-032.4`) strictly preserved; permanent errors (missing files, 401/403) retain `shallRetry = false`.

---

## 4. Invariants & Localization
* **No UI String Changes**: Zero localized string modifications; existing resources reused; `TranslationParityTest` 100% PASS.
* **Target Release Parity**: `V4.9.41` verified as active/unreleased in Jira.
