# Stage 5: Walkthrough & Verification - ATT-4922: Resilient Google Drive Upload Retry, Post-Restore Re-Auth & Stuck Export Recovery

**Ticket**: [ATT-4922](https://rainerblind.atlassian.net/browse/ATT-4922)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-DAT-037` (`REQ-DAT-037.1`..`REQ-DAT-037.5`)  
**Test Mapping**: `TST-DAT-033` (`TST-DAT-033.1`..`TST-DAT-033.7`)  
**Branch**: `feature/ATT-4922`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-11-02  

---

## 1. Executive Summary & Verification Overview
`ATT-4922` resolves three interlocking bugs causing queued WLAN workout uploads to permanently fail on attempt 0 with `FINISHED_FAILED: Google Drive credential is null or not linked` and never retry:
1. **Silent Token Acquisition Fallback (AC 1)**: In `GoogleDriveAuthManager`, `GoogleDriveUploader`, `GoogleDriveBackupManager`, `BackupRestoreViewModel`, and `LegacyImportEngine`, the system now attempts silent refresh via `GoogleDriveAuthManager.refreshTokenSync(context)` on null cached token before failing.
2. **Zombie-Connected State Elimination (AC 2)**: `AuthCredentialsStore.sanitizeAfterRestore()` resets `uploadToGoogleDrive` and `uploadToDropbox` in default preferences if credentials are wiped without an active device Google account; `MainActivityWithNavigation.checkPreferences()` self-heals; `GoogleDriveSettingsDialog.kt` computes `isConnected` based on credential presence.
3. **Transient Error Classification & WorkManager Retry (AC 3)**: `GoogleDriveUploader.kt` returns `shallRetry = true` on `IOException` and HTTP 408/429/5xx/null; `DropboxUploader.java` returns `shallRetry = true` on `IOException`, `NetworkIOException`, `RetryException`, `ServerException`, `RateLimitException`.
4. **Stuck Export Recovery (AC 4)**: Added `recoverStuckCredentialExports` in `ExportStatusDatabaseManager` and `recoverStuckGoogleDriveExports` in `ExportManager`; auto-recovers stuck credential rows on re-authentication and silent token refresh.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Component | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-DAT-037.1` | `TST-DAT-033.1` | `GoogleDriveUploaderTest`, `GoogleDriveBackupManagerTest` | **PASSED** | `Verified` |
| `REQ-DAT-037.2` | `TST-DAT-033.2` | `AuthCredentialsStoreTest`, `GoogleDriveSettingsDialogTest` | **PASSED** | `Verified` |
| `REQ-DAT-037.3` | `TST-DAT-033.3` | `GoogleDriveUploaderTest`, `DropboxUploaderTest` | **PASSED** | `Verified` |
| `REQ-DAT-037.4` | `TST-DAT-033.4` | `ExportStatusDatabaseManagerTest`, `ExportManager` | **PASSED** | `Verified` |
| `REQ-DAT-037.5` | `TST-DAT-033.5` | Invariant tests across uploader & credential suites | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Verification Suite (Rule 7 & Rule 60)
* **Explicit Test Scope**:
  - `com.atrainingtracker.trainingtracker.exporter.*` (56 tests)
  - `com.atrainingtracker.trainingtracker.cloud.*` (34 tests)
  - `com.atrainingtracker.trainingtracker.migration.*` (304 tests)
  - `com.atrainingtracker.trainingtracker.security.*` (43 tests)
  - `com.atrainingtracker.trainingtracker.ui.settings.googledrive.*` (3 tests)
  - `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest` (2 tests)
  - `com.atrainingtracker.trainingtracker.build.*` (13 tests)
* **Execution**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.exporter.*" --tests "com.atrainingtracker.trainingtracker.cloud.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.security.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.googledrive.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Result**: **BUILD SUCCESSFUL in 2m 7s** (455+ tests, 100% clean-room test pass rate, 0 failures).
* **ADB Device Verification**: `adb devices` checked; zero attached devices. Verification executed via headless JVM unit tests and Robolectric tests (`GoogleDriveSettingsDialogTest`, `AuthCredentialsStoreTest`).
* **Requirement Governance**: `python3 tools/verify_requirement_governance.py` -> **PASSED** (code 0, net-new requirement REQ-DAT-037).

---

## 4. Invariant & Governance Verification
1. **WorkManager Retry Bounding**: `ExportConfig.MAX_RETRIES = 5` cap strictly respected; non-transient errors (missing files, 401/403) retain `shallRetry = false`.
2. **Credential Isolation (`REQ-DAT-032.1`)**: OAuth tokens remain strictly isolated in `auth_credentials_prefs.xml`, never written to default preferences.
3. **Upgrade & Existing-User Stability**: Existing connected users remain unaffected; post-restore states reliably prompt re-authentication rather than failing silently.
4. **Living Docs Updated**: `REQ-DAT-037` and `TST-DAT-033` updated to `Verified` in `req_data_migration_export.md` and `tst_data_migration_export.md`.
5. **No Premature FixVersion**: Target Release `V4.9.41` maintained; `FixVersion` reserved for human acceptance in Ceremony 2.
