# Stage 3: Implementation Plan - ATT-2631: Google Drive workout export fails with Failed to resolve Google Drive folder hierarchy

**Ticket**: [ATT-2631](https://atrainingtracker.atlassian.net/browse/ATT-2631)  
**Sub-task**: [ATT-2652](https://atrainingtracker.atlassian.net/browse/ATT-2652) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-162](https://atrainingtracker.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Requirement Mapping**: `REQ-DAT-022` (*Resilient Google Drive Folder Hierarchy Resolution, 401 Auto-Refresh & Actionable Diagnostic Feedback*)  
**Test Mapping**: `TST-DAT-017` (*Google Drive Folder Hierarchy Resolution, 401 Auto-Refresh & Actionable Feedback Verification*)  
**Branch**: `feature/ATT-2631`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Description & Root Cause Analysis

### 1.1 Problem Statement
When athletes export recorded workouts (FIT, TCX, GPX, CSV) to Google Drive, the export routinely fails with the error:
`"Failed to resolve Google Drive folder hierarchy"`.

### 1.2 Root Cause Summary
1. **OAuth2 Bearer Token Expiry**: Google OAuth2 access tokens issued via Google Play Services expire after 3,600 seconds (1 hour). `TrainingApplication.getGoogleDriveAuthToken()` returns the static token stored at initial sign-in. After 1 hour, all Google Drive API v3 requests receive `HTTP 401 Unauthorized`.
2. **Missing Token Renewal Trigger**: `GoogleDriveAuthManager.refreshToken(context)` was implemented during ATT-1306 but is a coroutine suspend function that was never invoked by `GoogleDriveUploader` or `GoogleDriveClient`.
3. **Silent Failure & Missing Diagnostic State**: `GoogleDriveClient.findFolderIdByName()` and `createFolder()` caught non-200 responses and returned `null` without recording HTTP response codes or error messages.
4. **Non-Canonical Root Folder Creation Payload**: `createFolder()` unconditionally included `"parents": [parentId]`. In Google Drive API v3, passing `"parents": ["root"]` is non-standard and rejected with HTTP 400 in certain account/drive configurations; canonical Drive v3 requires omitting the `parents` field when creating files or folders directly in the root directory.
5. **Misleading User Feedback**: `GoogleDriveUploader` received `null` and displayed the generic message `"Failed to resolve Google Drive folder hierarchy"`, obscuring the fact that authentication expired and that the athlete needs to re-authenticate or that automatic token refresh was required.
6. **Parallel Export Race Condition (Duplicate Folders)**: WorkManager exports TCX and FIT files in parallel workers. Each worker instantiated its own `GoogleDriveClient` with an instance-scoped cache. Both queried Drive for `aTrainingTracker` simultaneously before either had completed folder creation, resulting in two duplicate `aTrainingTracker` folders in Google Drive. Process-wide synchronization via `hierarchyLock` and `globalFolderIdCache` is required.
7. **Google Cloud Console Drive API Disabled (HTTP 403)**: If the Google Drive API is disabled in the Google Cloud Console project, Drive returns HTTP 403 `SERVICE_DISABLED` / `accessNotConfigured`. The app previously caught this as a generic failure without indicating that the API must be enabled.
8. **Missing Token Refresher in Backup Manager**: `GoogleDriveBackupManager` also instantiated `GoogleDriveClient` without passing `tokenRefresher`, leaving automated database backup migrations vulnerable to 401 expiry.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-DAT-022` (*Resilient Google Drive Folder Hierarchy Resolution, 401 Auto-Refresh & Actionable Diagnostic Feedback*)
* **Test Mapping**: `TST-DAT-017` (*Google Drive Folder Hierarchy Resolution, 401 Auto-Refresh & Actionable Feedback Verification*)
  * `TST-DAT-017.1`: Synchronous token renewal & automatic 401 retry in `GoogleDriveClient` (`GoogleDriveClientTest.kt`)
  * `TST-DAT-017.2`: Canonical root folder creation payload omitting `parents` (`GoogleDriveClientTest.kt`)
  * `TST-DAT-017.3`: Actionable localized error message on unrecoverable 401 (`GoogleDriveUploaderTest.kt`)
  * `TST-DAT-017.4`: 9-language localization parity audit (`TranslationParityTest.kt`)
  * `TST-DAT-017.5`: Full clean-room regression test suite (`./gradlew testDebugUnitTest`)
  * `TST-DAT-017.6`: Concurrent hierarchy creation synchronization (`GoogleDriveClientTest.kt`)
  * `TST-DAT-017.7`: 403 API disabled error message (`GoogleDriveUploaderTest.kt`)
  * `TST-DAT-017.8`: Backup manager token refresh integration (`GoogleDriveBackupManagerTest.kt`)

---

## 3. System Invariants & Preserved Behavior

1. **OAuth Least-Privilege Scope**: Scope `https://www.googleapis.com/auth/drive.file` MUST remain strictly enforced.
2. **Database & Schema Invariance**: No modifications to SQLite / Room databases (`TrainingTracker.db`, `Routes.db`, `KnownLocations.db`).
3. **Export Integrity**: Export file generation (FIT, TCX, GPX, CSV) and local storage paths remain completely untouched.
4. **Subtask Self-Sufficiency**: Subtask `ATT-2652` transitions directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: `ATT-2631` completion is strictly reserved for the human user in `Final Review (Human)`.
6. **Gate 3 Pre-Check Invariant (Rule 4)**: Before modifying production code in `app/src/...`, verify `python3 tools/jira_util.py check-gate ATT-2652` exits with code 0.

---

## 4. Proposed Architectural Changes

```
+-----------------------------------------------------------------------------------+
|                            GoogleDriveUploader                                    |
|  - createClient(): injects tokenProvider AND tokenRefresher                       |
|  - doExport(): handles client.lastHttpCode == 401 -> localized auth expired error |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|                             GoogleDriveClient                                     |
|  - tokenProvider: () -> String?                                                   |
|  - tokenRefresher: (() -> String?)? = null                                        |
|  - executeWithAuthRetry(request): detects 401, invokes refresher, retries request  |
|  - createFolder(): omits 'parents' field when parentId == "root" or blank         |
|  - lastHttpCode: Int?, lastErrorMessage: String? for actionable diagnostics       |
+-----------------------------------------------------------------------------------+
                                         |
                                         v (on 401 Unauthorized)
+-----------------------------------------------------------------------------------+
|                           GoogleDriveAuthManager                                  |
|  - refreshTokenSync(context: Context): Result<String>                             |
|    * Clears stale token from GoogleAuthUtil cache                                 |
|    * Acquires fresh OAuth2 Bearer token                                           |
|    * Persists new credential to TrainingApplication (SharedPreferences)            |
|  - refreshToken(context: Context): delegates to refreshTokenSync on Dispatchers.IO|
+-----------------------------------------------------------------------------------+
```

### Component 1: `GoogleDriveAuthManager.kt`
* Add synchronous worker-thread token renewal:
  ```kotlin
  fun refreshTokenSync(context: Context): Result<String> {
      return try {
          val account = GoogleSignIn.getLastSignedInAccount(context)
              ?: return Result.failure(IllegalStateException("No signed-in Google account found"))
          val accountObj = account.account
              ?: return Result.failure(IllegalStateException("Google account object is null"))
          val oldToken = TrainingApplication.getGoogleDriveAuthToken()
          if (!oldToken.isNullOrBlank()) {
              try {
                  GoogleAuthUtil.clearToken(context, oldToken)
              } catch (e: Exception) {
                  Log.w(TAG, "Failed to clear expired token", e)
              }
          }
          val scope = "$OAUTH2_SCOPE_PREFIX$DRIVE_FILE_SCOPE"
          val newToken = GoogleAuthUtil.getToken(context, accountObj, scope)
          val email = account.email ?: accountObj.name ?: "Google Drive User"
          TrainingApplication.storeGoogleDriveCredential(email, newToken)
          Log.i(TAG, "Successfully refreshed Google Drive Bearer token for $email")
          Result.success(newToken)
      } catch (e: Exception) {
          Log.e(TAG, "Failed to refresh Google Drive Bearer token", e)
          Result.failure(e)
      }
  }
  ```
* Delegate `suspend fun refreshToken(context: Context)` to `withContext(Dispatchers.IO) { refreshTokenSync(context) }`.

### Component 2: `GoogleDriveClient.kt`
* Extend constructor to accept `tokenRefresher: (() -> String?)? = null`.
* Maintain `private var currentAuthToken: String? = null` initialized from `tokenProvider()`.
* Add diagnostic properties:
  * `var lastHttpCode: Int? = null`
  * `var lastErrorMessage: String? = null`
* Introduce a robust HTTP call execution helper `executeWithAuthRetry(requestBuilder: (String?) -> Request): Response`:
  * Build request with `currentAuthToken` (or `tokenProvider()`).
  * Execute call.
  * Record `lastHttpCode = response.code`.
  * If response code is 401 and `tokenRefresher != null`:
    * Invoke `tokenRefresher()`.
    * If refreshed token obtained:
      * Update `currentAuthToken = refreshedToken`.
      * Close initial response.
      * Re-execute request with new token.
      * Record updated `lastHttpCode = newResponse.code`.
      * Return `newResponse`.
  * If response is not successful (`!response.isSuccessful`):
    * Extract diagnostic error snippet: `lastErrorMessage = response.peekBody(2048).string()`.
* Update `createFolder(folderName: String, parentId: String)`:
  * If `parentId == "root"` or `parentId.isBlank()`:
    * Do NOT include `"parents"` array in payload.
  * If `parentId != "root"` and `parentId.isNotBlank()`:
    * Include `"parents": [parentId]`.
* In `ensureFolderHierarchy(folderPath: String)`:
  * Synchronize folder resolution and creation using `synchronized(hierarchyLock)` and double-checked caching against `globalFolderIdCache`.
  * Warn if Google Drive returns multiple folders for the same query to detect legacy duplicate folders.

### Component 3: `GoogleDriveUploader.kt`
* In `createClient()`:
  * Supply `tokenRefresher = { GoogleDriveAuthManager.refreshTokenSync(mContext).getOrNull() }`.
* In `doExport()`:
  * If `client.ensureFolderHierarchy()` returns `null`:
    * If `client.lastHttpCode == 401`:
      * Return `ExportResult(false, false, mContext.getString(R.string.google_drive_error_auth_expired))`.
    * If `client.lastHttpCode == 403` and error message indicates `SERVICE_DISABLED` or `accessNotConfigured`:
      * Return `ExportResult(false, false, mContext.getString(R.string.google_drive_error_api_disabled))`.
    * Else:
      * Return `ExportResult(false, false, mContext.getString(R.string.google_drive_error_folder_hierarchy, client.lastHttpCode?.toString() ?: "unknown"))`.

### Component 4: `GoogleDriveBackupManager.kt`
* Update `clientProvider: (Context) -> GoogleDriveClient`:
  * Pass `tokenRefresher = { GoogleDriveAuthManager.refreshTokenSync(context).getOrNull() }`.

### Component 5: String Resources (9-Language Parity)
* Add `google_drive_error_auth_expired`, `google_drive_error_api_disabled`, and `google_drive_error_folder_hierarchy` to all 9 `strings.xml` resource directories:
  * `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

### UI Consistency (Rule 23)
* **Reference screen / component**: No visual UI screens are modified. This change affects background cloud export and error reporting.
* **Reused components**: Existing export error dialog / Toast / Snackbar flow displaying `ExportResult.answer()`.
* **Theme tokens**: Standard string resources; no custom typography, shapes, or colors introduced.
* **New one-off styles & justification**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: 9-Language Localization Resources
* Add `google_drive_error_auth_expired`, `google_drive_error_api_disabled`, and `google_drive_error_folder_hierarchy` to all 9 `strings.xml` files.
* Verify format specifier and presence parity with `TranslationParityTest`.

### Step 2: Synchronous Token Refresh in `GoogleDriveAuthManager.kt`
* Implement `fun refreshTokenSync(context: Context): Result<String>`.
* Refactor `suspend fun refreshToken(context: Context)` to call `refreshTokenSync(context)` on `Dispatchers.IO`.

### Step 3: 401 Auto-Retry, Canonical Root Payload & Error Capture in `GoogleDriveClient.kt`
* Add `tokenRefresher: (() -> String?)? = null` parameter to constructor.
* Add `lastHttpCode` and `lastErrorMessage` state fields.
* Implement 401 retry interceptor/wrapper logic in `executeCallWithRetry`.
* Update `createFolder()` to omit `parents` when `parentId == "root"` or blank.
* Add static `hierarchyLock` and `globalFolderIdCache` with double-checked locking in `ensureFolderHierarchy`.

### Step 4: Token Refresher Injection & Actionable Errors in `GoogleDriveUploader.kt` & `GoogleDriveBackupManager.kt`
* Inject `tokenRefresher` in `GoogleDriveUploader.createClient()` and `GoogleDriveBackupManager.clientProvider(context)`.
* Inspect `client.lastHttpCode == 401` -> `google_drive_error_auth_expired`.
* Inspect `client.lastHttpCode == 403` -> `google_drive_error_api_disabled`.
* Default hierarchy failure -> `google_drive_error_folder_hierarchy` with HTTP status code.

### Step 5: Unit Tests
* Update `GoogleDriveClientTest.kt` with tests for 401 refresh retry, canonical root folder payload, and concurrent hierarchy synchronization.
* Update `GoogleDriveUploaderTest.kt` with tests for 401 auth expired and 403 API disabled error results.
* Update `GoogleDriveBackupManagerTest.kt` with mock client provider.
* Run targeted unit tests:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.cloud.googledrive.*" --tests "com.atrainingtracker.trainingtracker.exporter.uploader.GoogleDriveUploaderTest" --tests "com.atrainingtracker.trainingtracker.migration.GoogleDriveBackupManagerTest" --tests "com.atrainingtracker.trainingtracker.translations.TranslationParityTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted unit tests during Stage 4 construction, followed by full clean-room `./gradlew testDebugUnitTest` and on-device export test on Pixel 10.
* **Rollback**: Work is isolated on `feature/ATT-2631`. Any regression can be reverted cleanly by discarding commits on `feature/ATT-2631` before merging into `sprint/2026-41.3`.
