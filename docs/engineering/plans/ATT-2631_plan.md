# Stage 3: Implementation Plan - ATT-2631: Google Drive workout export fails with Failed to resolve Google Drive folder hierarchy

**Ticket**: [ATT-2631](https://atrainingtracker.atlassian.net/browse/ATT-2631)  
**Sub-task**: [ATT-2652](https://atrainingtracker.atlassian.net/browse/ATT-2652) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-162](https://atrainingtracker.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.2`  
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

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-DAT-022` (*Resilient Google Drive Folder Hierarchy Resolution, 401 Auto-Refresh & Actionable Diagnostic Feedback*)
* **Test Mapping**: `TST-DAT-017` (*Google Drive Folder Hierarchy Resolution, 401 Auto-Refresh & Actionable Feedback Verification*)
  * `TST-DAT-017.1`: Synchronous token renewal & automatic 401 retry in `GoogleDriveClient` (`GoogleDriveClientTest.kt`)
  * `TST-DAT-017.2`: Canonical root folder creation payload omitting `parents` (`GoogleDriveClientTest.kt`)
  * `TST-DAT-017.3`: Actionable localized error message on unrecoverable 401 (`GoogleDriveUploaderTest.kt`)
  * `TST-DAT-017.4`: 9-language localization parity audit (`TranslationParityTest.kt`)
  * `TST-DAT-017.5`: Full clean-room regression test suite (`./gradlew testDebugUnitTest`)

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

### Component 3: `GoogleDriveUploader.kt`
* In `createClient()`:
  * Supply `tokenRefresher = { GoogleDriveAuthManager.refreshTokenSync(mContext).getOrNull() }`.
* In `doExport()`:
  * If `client.ensureFolderHierarchy()` returns `null`:
    * If `client.lastHttpCode == 401`:
      * Return `ExportResult(false, false, mContext.getString(R.string.google_drive_error_auth_expired))`.
    * Else:
      * Return `ExportResult(false, false, "Failed to resolve Google Drive folder hierarchy (${client.lastHttpCode ?: "unknown"})")`.

### Component 4: String Resources (9-Language Parity)
* Add `google_drive_error_auth_expired` to:
  * `app/src/main/res/values/strings.xml`: `"Google Drive session expired or unauthorized. Please re-authenticate in Settings."`
  * `app/src/main/res/values-de/strings.xml`: `"Google Drive-Sitzung abgelaufen oder nicht autorisiert. Bitte in den Einstellungen neu anmelden."`
  * `app/src/main/res/values-es/strings.xml`: `"La sesión de Google Drive caducó o no está autorizada. Vuelva a autenticarse en Ajustes."`
  * `app/src/main/res/values-fr/strings.xml`: `"Session Google Drive expirée ou non autorisée. Veuillez vous reconnecter dans les paramètres."`
  * `app/src/main/res/values-it/strings.xml`: `"Sessione di Google Drive scaduta o non autorizzata. Esegui nuovamente l'accesso nelle Impostazioni."`
  * `app/src/main/res/values-ja/strings.xml`: `"Google ドライブのセッションの有効期限が切れたか、認証されていません。設定で再認証してください。"`
  * `app/src/main/res/values-nl/strings.xml`: `"Google Drive-sessie verlopen of niet geautoriseerd. Meld u opnieuw aan in Instellingen."`
  * `app/src/main/res/values-pl/strings.xml`: `"Sesja Google Drive wygasła lub brak autoryzacji. Zaloguj się ponownie w Ustawieniach."`
  * `app/src/main/res/values-pt/strings.xml`: `"Sessão do Google Drive expirada ou não autorizada. Faça login novamente nas Configurações."`

### UI Consistency (Rule 23)
* **Reference screen / component**: No visual UI screens are modified. This change affects background cloud export and error reporting.
* **Reused components**: Existing export error dialog / Toast / Snackbar flow displaying `ExportResult.answer()`.
* **Theme tokens**: Standard string resources; no custom typography, shapes, or colors introduced.
* **New one-off styles & justification**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: 9-Language Localization Resources
* Add `google_drive_error_auth_expired` to all 9 `strings.xml` files.
* Verify format specifier and presence parity with `TranslationParityTest`.

### Step 2: Synchronous Token Refresh in `GoogleDriveAuthManager.kt`
* Implement `fun refreshTokenSync(context: Context): Result<String>`.
* Refactor `suspend fun refreshToken(context: Context)` to call `refreshTokenSync(context)` on `Dispatchers.IO`.

### Step 3: 401 Auto-Retry, Canonical Root Payload & Error Capture in `GoogleDriveClient.kt`
* Add `tokenRefresher: (() -> String?)? = null` parameter to constructor.
* Add `lastHttpCode` and `lastErrorMessage` state fields.
* Implement 401 retry interceptor/wrapper logic in `executeCallWithRetry`.
* Update `createFolder()` to omit `parents` when `parentId == "root"` or blank.

### Step 4: Token Refresher Injection & Actionable Error in `GoogleDriveUploader.kt`
* Inject `tokenRefresher` in `createClient()`.
* Inspect `client.lastHttpCode == 401` on folder resolution failure and return localized `R.string.google_drive_error_auth_expired`.

### Step 5: Unit Tests
* Update `GoogleDriveClientTest.kt` with tests for 401 refresh retry and canonical root folder payload.
* Update `GoogleDriveUploaderTest.kt` with test for 401 auth expired localized error result.
* Run targeted unit tests:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.cloud.googledrive.*" --tests "com.atrainingtracker.trainingtracker.exporter.uploader.GoogleDriveUploaderTest" --tests "com.atrainingtracker.trainingtracker.translations.TranslationParityTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted unit tests during Stage 4 construction, followed by full clean-room `./gradlew testDebugUnitTest` in Stage 5.
* **Rollback**: Work is isolated on `feature/ATT-2631`. Any regression can be reverted cleanly by discarding commits on `feature/ATT-2631` before merging into `sprint/2026-41.2`.
