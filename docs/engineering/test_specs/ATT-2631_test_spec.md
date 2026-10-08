# Stage 2: Requirement & Test Specification - ATT-2631: Google Drive workout export fails with Failed to resolve Google Drive folder hierarchy

**Ticket**: [ATT-2631](https://atrainingtracker.atlassian.net/browse/ATT-2631)  
**Sub-task**: [ATT-2651](https://atrainingtracker.atlassian.net/browse/ATT-2651) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-162](https://atrainingtracker.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.2`  
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

### 1.2 Functional & Architectural Requirements
The system SHALL provide resilient folder hierarchy resolution, automatic 401 token refresh, and user-actionable localized error feedback during Google Drive export and backup operations (ATT-2631):

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

4. *User-Actionable Localized Error Reporting (`GoogleDriveUploader.kt`)*:
   - In `GoogleDriveUploader.doExport()`, `GoogleDriveClient` SHALL be created with `tokenRefresher = { GoogleDriveAuthManager.refreshTokenSync(mContext).getOrNull() }`.
   - If folder hierarchy resolution fails due to an unauthorized / expired state (HTTP 401) that could not be refreshed, `doExport()` SHALL return:
     `ExportResult(false, false, mContext.getString(R.string.google_drive_error_auth_expired))`
     guiding the athlete to re-authenticate in Settings.

5. *100% 9-Language Localization Parity (`REQ-LOC-001`)*:
   - String resource `google_drive_error_auth_expired` SHALL be defined across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

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
  * *Then* `google_drive_error_auth_expired` SHALL have zero missing translations and zero specifier discrepancies.

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
* **Action**: Execute `TranslationParityTest` asserting 100% presence of `google_drive_error_auth_expired` across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
* **Expected Result**: 100% pass rate with zero discrepancies.

### Test Case 5: Full Clean-Room Regression Test Suite (`TST-DAT-017.5`)
* **Scope**: Clean-room integration regression
* **Action**: Execute `./gradlew testDebugUnitTest`.
* **Expected Result**: All unit tests pass with zero failures and zero regressions.

---

## 3. Traceability Matrix

| Requirement | Description | Test Case | Target Artifacts | Living Doc Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-DAT-022.1` | Synchronous token renewal | `TST-DAT-017.1` | `GoogleDriveAuthManager.kt` | `Specified` |
| `REQ-DAT-022.2` | 401 retry & token refresh | `TST-DAT-017.1` | `GoogleDriveClient.kt` | `Specified` |
| `REQ-DAT-022.3` | Canonical root folder payload | `TST-DAT-017.2` | `GoogleDriveClient.kt` | `Specified` |
| `REQ-DAT-022.4` | Actionable error message | `TST-DAT-017.3` | `GoogleDriveUploader.kt` | `Specified` |
| `REQ-DAT-022.5` | 9-language parity | `TST-DAT-017.4` | `strings.xml` (all 9 locales) | `Specified` |
| `REQ-PRO-001` | Full clean-room suite | `TST-DAT-017.5` | Entire test suite | `Specified` |
