# Stage 5: Walkthrough & Verification - ATT-2631: Google Drive workout export fails with Failed to resolve Google Drive folder hierarchy

**Ticket**: [ATT-2631](https://atrainingtracker.atlassian.net/browse/ATT-2631)  
**Sub-task**: [ATT-2654](https://atrainingtracker.atlassian.net/browse/ATT-2654) (`[Test]`)  
**Parent Epic**: [ATT-162](https://atrainingtracker.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Requirement Mapping**: `REQ-DAT-022` (*Resilient Google Drive Folder Hierarchy Resolution, 401 Auto-Refresh & Actionable Diagnostic Feedback*)  
**Test Mapping**: `TST-DAT-017` (*Google Drive Folder Hierarchy Resolution, 401 Auto-Refresh & Actionable Feedback Verification*)  
**Branch**: `feature/ATT-2631`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Executive Summary & Verification Overview

This deliverable resolves the Google Drive workout export failure where uploads were rejected with `"Failed to resolve Google Drive folder hierarchy"`:
1. **Synchronous Worker-Thread Token Renewal**: Added `GoogleDriveAuthManager.refreshTokenSync(context: Context): Result<String>` that synchronously clears the expired token in Google Play Services cache via `GoogleAuthUtil.clearToken()`, obtains a fresh OAuth2 Bearer token, and stores it in `SharedPreferences`.
2. **Automatic HTTP 401 Interception & Retry**: `GoogleDriveClient` now accepts an optional `tokenRefresher: (() -> String?)? = null`. When API calls encounter HTTP 401 Unauthorized, the client immediately invokes `tokenRefresher()`, updates its active Bearer token, and automatically retries the request once before failing.
3. **Canonical Google Drive API v3 Root Folder Creation**: Updated `createFolder()` to omit the `parents` field when `parentId == "root"` or is blank, conforming strictly to canonical Google Drive API v3 conventions and eliminating HTTP 400 Bad Request rejections on root directory creation.
4. **Diagnostic State Capture & User-Actionable Error Messages**: `GoogleDriveClient` captures `lastHttpCode` and `lastErrorMessage` from response bodies. When folder resolution fails due to an unrecoverable 401 error, `GoogleDriveUploader` returns localized string `R.string.google_drive_error_auth_expired`. When failure is due to Google Drive API disabled (HTTP 403 `SERVICE_DISABLED` / `accessNotConfigured`), it returns localized string `R.string.google_drive_error_api_disabled`.
5. **Thread-Safe Hierarchy Creation (Duplicate Folder Prevention)**: Implemented static `hierarchyLock` and `globalFolderIdCache` with double-checked locking in `GoogleDriveClient.ensureFolderHierarchy`. When parallel WorkManager export workers (e.g. TCX + FIT) resolve or create the folder hierarchy concurrently, race conditions leading to duplicate `aTrainingTracker` root or child folders are prevented.
6. **Backup Manager Token Refresh Wiring**: Updated `GoogleDriveBackupManager.clientProvider(context)` to supply `tokenRefresher = { GoogleDriveAuthManager.refreshTokenSync(context).getOrNull() }`, ensuring automated database backup migrations do not fail due to expired OAuth tokens.
7. **100% 9-Language Localization Parity**: `google_drive_error_auth_expired`, `google_drive_error_api_disabled`, and `google_drive_error_folder_hierarchy` are fully localized across all 9 supported locales: EN, DE, ES, FR, IT, JA, NL, PL, and PT, passing `TranslationParityTest` with 0 errors.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-DAT-022.1` | `TST-DAT-017.1` | Synchronous token renewal & 401 retry (`GoogleDriveClientTest.kt`) | **PASSED** | `Verified` |
| `REQ-DAT-022.2` | `TST-DAT-017.1` | Bearer token auto-retry on 401 (`GoogleDriveClientTest.kt`) | **PASSED** | `Verified` |
| `REQ-DAT-022.3` | `TST-DAT-017.2` | Canonical root folder payload omitting parents (`GoogleDriveClientTest.kt`) | **PASSED** | `Verified` |
| `REQ-DAT-022.4` | `TST-DAT-017.3` | Actionable localized error reporting on 401 (`GoogleDriveUploaderTest.kt`) | **PASSED** | `Verified` |
| `REQ-DAT-022.5` | `TST-DAT-017.4` | 9-language translation parity audit (`TranslationParityTest.kt`) | **PASSED** (100%) | `Verified` |
| `REQ-DAT-022.6` | `TST-DAT-017.6` | Concurrent folder creation synchronization (`GoogleDriveClientTest.kt`) | **PASSED** | `Verified` |
| `REQ-DAT-022.7` | `TST-DAT-017.7` | Actionable 403 API disabled error reporting (`GoogleDriveUploaderTest.kt`) | **PASSED** | `Verified` |
| `REQ-DAT-022.8` | `TST-DAT-017.8` | Backup manager token refresh wiring (`GoogleDriveBackupManagerTest.kt`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-DAT-017.5` | Clean-room unit test suite (`./gradlew testDebugUnitTest`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated & Live On-Device Test Evidence

### Targeted Unit Tests
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.cloud.googledrive.*" --tests "com.atrainingtracker.trainingtracker.exporter.uploader.GoogleDriveUploaderTest" --tests "com.atrainingtracker.trainingtracker.migration.GoogleDriveBackupManagerTest" --tests "com.atrainingtracker.trainingtracker.translations.TranslationParityTest"
BUILD SUCCESSFUL in 12s

- GoogleDriveClientTest: 10/10 PASSED (includes concurrent hierarchy creation test)
- GoogleDriveUploaderTest: 6/6 PASSED (includes 403 API disabled test)
- GoogleDriveBackupManagerTest: 3/3 PASSED (includes clientProvider tokenRefresher test)
- TranslationParityTest: 100% PASSED across all 9 languages
```

### Live On-Device Verification (Google Pixel 10)
1. **Interactive Logcat Trace (Google Drive API Enablement)**:
   - Initial run revealed Google Cloud Console project `717488426058` had Google Drive API disabled:
     `SERVICE_DISABLED / accessNotConfigured`
   - User enabled Google Drive API via direct Cloud Console link.
2. **Post-Enablement Live Export**:
   - Workout `2026-10-08_060104` exported to Dropbox & Google Drive.
   - TCX file (`2026-10-08_060104.tcx`) uploaded to `aTrainingTracker/Workouts` with HTTP 200.
   - FIT file (`2026-10-08_060104.fit`) uploaded to `aTrainingTracker/Workouts` with HTTP 200.
3. **Duplicate Folder Resolution**:
   - User observed duplicate `aTrainingTracker` folder in My Drive due to parallel WorkManager execution racing by 6ms.
   - Hardened with process-wide `hierarchyLock` + `globalFolderIdCache`. User deleted existing duplicate folders on Drive in preparation for clean usage.

---

## 4. UI Consistency (Rule 23)

* **Reference Screen / Component**: No visual UI layout screens were added or modified. The change affects background cloud export and error reporting.
* **Theme Tokens Reused**: Standard Android string resource framework (`R.string.google_drive_error_auth_expired`, `R.string.google_drive_error_api_disabled`, `R.string.google_drive_error_folder_hierarchy`).
* **Checked against `docs/design_guidelines.md` §5**: shapes N/A spacing N/A colors/themes N/A typography/icons N/A placement N/A
* **Deviations & justification**: None.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Targeted and clean-room unit test suite executed with 100% pass rate.
2. **OAuth Least-Privilege Scope Preserved**: Strict adherence to `https://www.googleapis.com/auth/drive.file`.
3. **Database Schemas Preserved**: Zero alterations to SQLite / Room schemas.
4. **Living Documentation Synchronized**: Status in `docs/requirements.md` (`REQ-DAT-022`) and `docs/tests.md` (`TST-DAT-017`) updated to `Verified`.
5. **Subtask Completion**: Stage 5 subtask (`ATT-2654`) transitioned to `Erledigt`.
6. **Strategy A Integration**: Branch `feature/ATT-2631` merged cleanly into `sprint/2026-41.3` (`--no-ff`), and parent ticket [ATT-2631](https://atrainingtracker.atlassian.net/browse/ATT-2631) moved to `Final Review (Human)`.
