# Stage 2: Requirement & Test Specification - ATT-2631: Google Drive workout export fails with Failed to resolve Google Drive folder hierarchy

**Ticket**: [ATT-2631](https://atrainingtracker.atlassian.net/browse/ATT-2631)  
**Sub-task**: [ATT-2651](https://atrainingtracker.atlassian.net/browse/ATT-2651) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-162](https://atrainingtracker.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Requirement ID**: `REQ-DAT-022`  
**Test Spec ID**: `TST-DAT-017`  
**Branch**: `feature/ATT-2631`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Requirement Specification (REQ-DAT-022)

### 1.1 Problem Statement & Rationale
Athletes exporting completed workouts (FIT, TCX, GPX, CSV) to Google Drive require reliable background folder resolution and transparent token lifecycle management:
1. Google OAuth2 Bearer tokens expire after 1 hour (3,600s). Post-ride exports typically occur long after the initial sign-in, leading to HTTP 401 Unauthorized responses when querying or creating folders.
2. Without an automated token refresh mechanism on HTTP 401, folder resolution fails completely, displaying a confusing and misleading technical error (`Failed to resolve Google Drive folder hierarchy`) rather than informing the user of the real cause or refreshing the token automatically.
3. Folder creation in Google Drive API v3 requires canonical payload structure (omitting `parents` for root-level folders) and diagnostic visibility into API error response bodies.
4. When parallel exports occur (e.g. TCX and FIT exports triggered simultaneously by WorkManager), concurrent folder creation requests race against each other. Because Google Drive API allows multiple folders with the identical name under the same parent, this results in duplicate root or subfolders if not synchronized process-wide.
5. When the Google Drive API is not enabled in the Google Cloud Console project, requests fail with HTTP 403 `SERVICE_DISABLED` / `accessNotConfigured`, which must be mapped to an actionable localized error message directing the user or developer to enable the API.
6. `GoogleDriveBackupManager` also requires background token refresh capability during automatic backup migrations.

### 1.2 Functional & Architectural Requirements
The system SHALL provide resilient folder hierarchy resolution, automatic 401 token refresh, thread-safe hierarchy resolution, and user-actionable localized error feedback during Google Drive export and backup operations (ATT-2631):

1. *Thread-Safe Token Renewal (`GoogleDriveAuthManager.kt`)*:
   - The system SHALL provide `refreshTokenSync(context: Context): Result<String>` that executes synchronously on background/worker threads.
   - It SHALL invalidate the stale token in Google Play Services cache using `GoogleAuthUtil.clearToken()`.
   - It SHALL acquire a fresh Bearer token using `GoogleAuthUtil.getToken(context, account, scope)`.
   - It SHALL persist the refreshed credentials to `SharedPreferences` via `TrainingApplication.storeGoogleDriveCredential()`.
   - `suspend fun refreshToken(context: Context)` SHALL delegate to `refreshTokenSync(context)` on `Dispatchers.IO`.

2. *Automatic 401 Token Refresh & Request Retry (`GoogleDriveClient.kt`)*:
   - `GoogleDriveClient` SHALL accept an optional `tokenRefresher: (() -> String?)? = null`.
   - When an API request (`findFolderIdByName`, `createFolder`, `findFileIdByName`, `uploadOrOverwriteFile`, `downloadFileById`) receives an HTTP 401 Unauthorized response, `GoogleDriveClient` SHALL invoke `tokenRefresher()`.
   - If a new Bearer token is acquired, it SHALL automatically update the Authorization header and retry the request once.
   - `GoogleDriveClient` SHALL track `lastHttpCode: Int?` and `lastErrorMessage: String?` extracted from `response.body?.string()`, providing actionable diagnostics on failure.

3. *Canonical Google Drive API v3 Folder Creation (`GoogleDriveClient.kt`)*:
   - In `createFolder(folderName: String, parentId: String)`, when `parentId == "root"` or is blank, the system SHALL omit the `parents` field from the request JSON payload, conforming to Google Drive API v3 canonical root placement.
   - When `parentId != "root"` and is not blank, the system SHALL include `"parents": [parentId]`.

4. *Thread-Safe Process-Wide Hierarchy Creation & Caching (`GoogleDriveClient.kt`)*:
   - The system SHALL maintain a static `globalFolderIdCache: ConcurrentHashMap<String, String>` mapping parent-child folder paths to resolved IDs.
   - Folder creation in `ensureFolderHierarchy` SHALL be guarded by a companion object lock (`hierarchyLock`) with double-checked cache verification, preventing concurrent duplicate folder creation during parallel exports.

5. *User-Actionable Localized Error Reporting (`GoogleDriveUploader.kt`)*:
   - In `GoogleDriveUploader.doExport()`, `GoogleDriveClient` SHALL be created with `tokenRefresher = { GoogleDriveAuthManager.refreshTokenSync(mContext).getOrNull() }`.
   - If folder hierarchy resolution fails due to an unauthorized / expired state (HTTP 401) that could not be refreshed, `doExport()` SHALL return localized message `google_drive_error_auth_expired`.
   - If folder hierarchy resolution fails due to Google Drive API disabled (HTTP 403 `SERVICE_DISABLED` / `accessNotConfigured`), `doExport()` SHALL return localized message `google_drive_error_api_disabled`.
   - For other failures, it SHALL return localized message `google_drive_error_folder_hierarchy` formatted with the HTTP status code.

6. *Backup Manager Token Refresh Integration (`GoogleDriveBackupManager.kt`)*:
   - `GoogleDriveBackupManager` SHALL pass `tokenRefresher = { GoogleDriveAuthManager.refreshTokenSync(context).getOrNull() }` when instantiating `GoogleDriveClient`.

7. *100% 9-Language Localization Parity (`REQ-LOC-001`)*:
   - String resources `google_drive_error_auth_expired`, `google_drive_error_api_disabled`, and `google_drive_error_folder_hierarchy` SHALL be defined across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Automatic Token Refresh on 401)**:
  * *Given* an expired Google Drive access token in `SharedPreferences`,
  * *When* `GoogleDriveUploader.doExport()` runs and `GoogleDriveClient` encounters HTTP 401 on `findFolderIdByName()`,
  * *Then* `GoogleDriveClient` SHALL invoke `tokenRefresher()`, acquire a fresh token, and retry the request with HTTP 200, successfully resolving the folder hierarchy.

* **Criterion 2 (Canonical Root Folder Creation)**:
  * *Given* folder `aTrainingTracker` does not exist on Google Drive,
  * *When* `GoogleDriveClient.createFolder("aTrainingTracker", "root")` is invoked,
  * *Then* the request JSON payload SHALL contain `"name": "aTrainingTracker"` and `"mimeType": "application/vnd.google-apps.folder"` with no `parents` field.

* **Criterion 3 (Subfolder Creation with Parent ID)**:
  * *Given* root folder `aTrainingTracker` resolved with ID `"folder_root_123"`,
  * *When* `GoogleDriveClient.createFolder("Workouts", "folder_root_123")` is invoked,
  * *Then* the request JSON payload SHALL contain `"parents": ["folder_root_123"]`.

* **Criterion 4 (Actionable Error on Refresh Failure)**:
  * *Given* an athlete whose Google account permissions have been revoked,
  * *When* `doExport()` runs and token refresh fails with HTTP 401,
  * *Then* `GoogleDriveUploader` SHALL return failure with localized message `google_drive_error_auth_expired` informing the athlete to reconnect in Settings.

* **Criterion 5 (Localization Parity)**:
  * *Given* all 9 supported locales,
  * *When* running `TranslationParityTest`,
  * *Then* `google_drive_error_auth_expired`, `google_drive_error_api_disabled`, and `google_drive_error_folder_hierarchy` SHALL have zero missing translations and zero specifier discrepancies.

* **Criterion 6 (Concurrent Duplicate Prevention)**:
  * *Given* two export workers running concurrently for TCX and FIT,
  * *When* both call `ensureFolderHierarchy("aTrainingTracker/Workouts")` simultaneously,
  * *Then* the system SHALL synchronize on `hierarchyLock`, reuse the cached ID, and create the root and child folders exactly once without generating duplicates.

* **Criterion 7 (Disabled API Error Feedback)**:
  * *Given* Google Drive API disabled in Google Cloud Console,
  * *When* `GoogleDriveUploader.doExport()` runs and receives HTTP 403,
  * *Then* the uploader SHALL return localized message `google_drive_error_api_disabled`.

### 1.4 System Invariants
- Least-privilege OAuth scope `https://www.googleapis.com/auth/drive.file` MUST NOT be broadened.
- Local workout export file generation (FIT, TCX, GPX, CSV) MUST NOT be altered.
- Database schemas for `TrainingTracker.db`, `Routes.db`, and `KnownLocations.db` MUST NOT be altered.
- Coroutine thread safety and non-blocking background export execution MUST be preserved.
- Zero unit test regressions across the clean-room test suite.

---

## 2. Test Specification (TST-DAT-017)

### Test Case 1: `GoogleDriveClientTest_tokenRefreshOn401_retriesAndSucceeds` (`TST-DAT-017.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/cloud/googledrive/GoogleDriveClientTest.kt`
* **Preconditions**: Mock OkHttpClient with interceptor simulating HTTP 401 on initial call and HTTP 200 on retry with new token.
* **Action**:
  1. Configure `tokenProvider` to return `"expired_token"`.
  2. Configure `tokenRefresher` to return `"refreshed_token"`.
  3. Invoke `client.findFolderIdByName("aTrainingTracker", "root")`.
* **Expected Result**: Interceptor receives first call with `Authorization: Bearer expired_token` (401), triggers `tokenRefresher`, and retries with `Authorization: Bearer refreshed_token` returning 200 with folder ID. Method returns folder ID cleanly.

### Test Case 2: `GoogleDriveClientTest_createFolder_omitsParentsWhenRoot` (`TST-DAT-017.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/cloud/googledrive/GoogleDriveClientTest.kt`
* **Preconditions**: Mock OkHttpClient capturing request body JSON.
* **Action**:
  1. Call `client.createFolder("aTrainingTracker", "root")`.
  2. Call `client.createFolder("Workouts", "parent_123")`.
* **Expected Result**: Root folder payload has NO `"parents"` key. Child folder payload contains `"parents": ["parent_123"]`.

### Test Case 3: `GoogleDriveUploaderTest_authExpired_returnsLocalizedActionableMessage` (`TST-DAT-017.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/uploader/GoogleDriveUploaderTest.kt`
* **Preconditions**: Mock client where `ensureFolderHierarchy` returns null and `lastHttpCode` is 401.
* **Action**:
  1. Call `uploader.doExport(exportInfo)`.
* **Expected Result**: `result.success()` is false, and `result.answer()` matches `R.string.google_drive_error_auth_expired`.

### Test Case 4: 9-Language Localization & Format Parity Audit (`TST-DAT-017.4`)
* **Scope**: Automated Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/translations/TranslationParityTest.kt`
* **Action**: Execute `TranslationParityTest` asserting 100% presence of `google_drive_error_auth_expired`, `google_drive_error_api_disabled`, and `google_drive_error_folder_hierarchy` across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
* **Expected Result**: 100% pass rate with zero discrepancies.

### Test Case 5: Full Clean-Room Regression Test Suite (`TST-DAT-017.5`)
* **Scope**: Clean-room integration regression
* **Action**: Execute `./gradlew testDebugUnitTest`.
* **Expected Result**: All unit tests pass with zero failures and zero regressions.

### Test Case 6: Concurrent Hierarchy Creation Synchronization (`TST-DAT-017.6`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/cloud/googledrive/GoogleDriveClientTest.kt`
* **Expected Result**: Parallel calls across threads to `ensureFolderHierarchy` share the global cache and create folders exactly once without duplicates.

### Test Case 7: Actionable Error on 403 API Disabled (`TST-DAT-017.7`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/uploader/GoogleDriveUploaderTest.kt`
* **Expected Result**: Returns localized message matching `R.string.google_drive_error_api_disabled`.

### Test Case 8: Backup Manager Token Refresher Wiring (`TST-DAT-017.8`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/GoogleDriveBackupManagerTest.kt`
* **Expected Result**: `clientProvider(context)` instantiates client with active `tokenRefresher`.

---

## 3. Traceability Matrix

| Requirement | Description | Test Case | Target Artifacts | Living Doc Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-DAT-022.1` | Synchronous token renewal | `TST-DAT-017.1` | `GoogleDriveAuthManager.kt` | `Specified` |
| `REQ-DAT-022.2` | 401 retry & token refresh | `TST-DAT-017.1` | `GoogleDriveClient.kt` | `Specified` |
| `REQ-DAT-022.3` | Canonical root folder payload | `TST-DAT-017.2` | `GoogleDriveClient.kt` | `Specified` |
| `REQ-DAT-022.4` | Actionable error message | `TST-DAT-017.3` | `GoogleDriveUploader.kt` | `Specified` |
| `REQ-DAT-022.5` | 9-language parity | `TST-DAT-017.4` | `strings.xml` (all 9 locales) | `Specified` |
| `REQ-DAT-022.6` | Concurrent folder creation lock | `TST-DAT-017.6` | `GoogleDriveClient.kt` | `Specified` |
| `REQ-DAT-022.7` | 403 API disabled error message | `TST-DAT-017.7` | `GoogleDriveUploader.kt` | `Specified` |
| `REQ-DAT-022.8` | Backup manager token refresh | `TST-DAT-017.8` | `GoogleDriveBackupManager.kt` | `Specified` |
| `REQ-PRO-001` | Full clean-room suite | `TST-DAT-017.5` | Entire test suite | `Specified` |
