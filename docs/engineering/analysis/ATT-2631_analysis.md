# Stage 1 Analysis: ATT-2631 - Google Drive workout export fails with Failed to resolve Google Drive folder hierarchy

**Ticket**: [ATT-2631](https://atrainingtracker.atlassian.net/browse/ATT-2631)  
**Sub-task**: [ATT-2650](https://atrainingtracker.atlassian.net/browse/ATT-2650) (`[Analysis]`)  
**Parent Epic**: [ATT-162](https://atrainingtracker.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.2`  
**Branch**: `feature/ATT-2631`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Statement & Motivation

During Sprint 2026-41.1 physical device review on Google Pixel 10 (ATT-1306), workout export to Google Drive was tested immediately after completing a cycling workout.
In the Export Details bottom sheet dialog, the TCX export failed with the following error:
```text
TCX: Fehlgeschlagen
Failed to resolve Google Drive folder hierarchy
```
(Attached evidence: `ATT-1306_drive_error_screenshot.png`).

The expected behavior is:
1. Workout files (FIT, TCX, GPX, CSV) are uploaded reliably to `aTrainingTracker/Workouts/` in Google Drive upon ride completion.
2. If the Google Drive OAuth Bearer token has expired during a workout session, the system automatically and transparently refreshes the token in the background using Google Play Services (`GoogleAuthUtil`).
3. If Google Drive credentials cannot be refreshed (e.g. user revoked permissions or changed account credentials), the system presents a clear, localized, user-actionable error message guiding the athlete to re-authenticate in Settings, rather than an opaque technical message about folder hierarchy failure.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Stale / Expired Bearer Token Lifecycle
* When the athlete connects Google Drive in `GoogleDriveSettingsDialog.kt`, `GoogleDriveAuthManager.acquireBearerToken()` invokes `GoogleAuthUtil.getToken()` and persists the resulting access token into `SharedPreferences` (`SP_GOOGLE_DRIVE_AUTH_TOKEN`).
* Google OAuth2 access tokens have a strict Time-To-Live (TTL) of **3,600 seconds (1 hour)**.
* In typical usage, the athlete starts a ride hours or days after initially linking Google Drive.
* When `GoogleDriveUploader.doExport()` executes post-ride, it reads `TrainingApplication.getGoogleDriveAuthToken()` directly from `SharedPreferences`. The stored token is already expired.
* `GoogleDriveAuthManager.refreshToken(context)` was authored under ATT-1306, but forensic inspection confirms it was **never called** by any export or backup component in the repository.

### 2.2 Missing 401 Handling & Token Refresh in `GoogleDriveClient`
* `GoogleDriveClient` is instantiated in `GoogleDriveUploader.createClient()` with a simple static lambda:
  ```kotlin
  GoogleDriveClient(tokenProvider = { TrainingApplication.getGoogleDriveAuthToken() })
  ```
* When `GoogleDriveClient` sends requests to the Google Drive v3 REST API (`https://www.googleapis.com/drive/v3/files?q=...`), Google returns **HTTP 401 Unauthorized** with:
  ```json
  { "error": { "code": 401, "message": "Request had invalid authentication credentials...", "status": "UNAUTHENTICATED" } }
  ```
* In `GoogleDriveClient.kt`:
  ```kotlin
  if (!response.isSuccessful) {
      Log.e(TAG, "findFolderIdByName failed with code: ${response.code}")
      return null
  }
  ```
  `findFolderIdByName()` logs code 401 and returns `null`.
* `ensureFolderHierarchy()` falls back to `createFolder()`, which sends another request with the same expired token, receives HTTP 401, and returns `null`.
* `ensureFolderHierarchy()` consequently returns `null`.

### 2.3 Opaque Error Propagation & Misleading Error String
* In `GoogleDriveUploader.kt` (lines 62–66):
  ```kotlin
  val folderId = client.ensureFolderHierarchy(listOf("aTrainingTracker", "Workouts"))
  if (folderId == null) {
      Log.e(TAG, "Failed to resolve folder hierarchy aTrainingTracker/Workouts")
      return ExportResult(false, false, "Failed to resolve Google Drive folder hierarchy")
  }
  ```
* `GoogleDriveUploader` assumes that a `null` return from `ensureFolderHierarchy()` is a directory structure failure, discarding the underlying root cause (HTTP 401 Unauthorized).
* The user is shown `Failed to resolve Google Drive folder hierarchy`, leading to confusion and preventing the user from knowing that re-authentication is required.

### 2.4 Canonical Google Drive API v3 Folder Creation
* In `GoogleDriveClient.createFolder()`, the payload specifies `"parents": ["root"]` when creating the top-level `aTrainingTracker` directory.
* While `"root"` is a valid query alias in `files.list` (`'root' in parents`), the canonical specification for `files.create` in Google Drive API v3 is to **omit the `parents` field entirely** when creating in the user's root My Drive folder.
* Furthermore, `GoogleDriveClient` currently discards `response.body?.string()` on failure, hindering live diagnostics.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Thread-Safe Token Refresh in `GoogleDriveAuthManager`**:
     Expose a thread-safe synchronous / worker-friendly token refresh method (`refreshTokenSync(context: Context): Result<String>`) that clears stale tokens via `GoogleAuthUtil.clearToken()` and acquires a fresh Bearer token from Google Play Services, persisting it to `SharedPreferences`.
  2. **Automatic 401 Retry & Refresh in `GoogleDriveClient`**:
     Equip `GoogleDriveClient` with a `tokenRefresher: (() -> String?)?` provider or OkHttp `Authenticator`. When a request returns HTTP 401, invoke the refresher and retry the request seamlessly.
  3. **Last Error Tracking in `GoogleDriveClient`**:
     Expose `lastStatusCode: Int?` and `lastErrorMessage: String?` in `GoogleDriveClient` so callers can distinguish between network errors, expired credentials, and permission rejections.
  4. **Canonical Folder Creation in `GoogleDriveClient`**:
     In `createFolder()`, omit the `parents` array when `parentId == "root"` or blank, conforming strictly to Google Drive API v3 standards.
  5. **Actionable & Localized Error Messages in `GoogleDriveUploader`**:
     If token refresh fails or authentication remains unauthorized (401), return a localized error message (`google_drive_error_auth_expired`: *"Google Drive-Autorisierung abgelaufen. Bitte in Einstellungen neu verbinden."* / *"Google Drive authorization expired. Please reconnect in Settings."*) across all 9 supported application locales.
  6. **Comprehensive Unit Testing**:
     Add unit tests verifying automatic 401 token refresh, root folder creation payload structure, error state capture, and localized message reporting.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Altering the OAuth2 scope (strictly maintain least-privilege `https://www.googleapis.com/auth/drive.file`).
  * Modifying local export file generators (`FitExporter.java`, `TcxExporter.java`, `GpxExporter.java`, `CsvExporter.java`).
  * Altering Dropbox or Strava export services.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-DAT-020` (*Google Drive Authentication, Settings UI & Disconnected State Parity*).
* **Historical Origin & Commit Trace**: Ticket `ATT-1306` (Sprint 2026-41.1, commit `f57007ef`).
* **Root Reason for Existing Formulation**: ATT-1306 introduced native Google Play Services sign-in with `drive.file` scope and error code mapping for Google Sign-In `ApiException` (Status 10 `DEVELOPER_ERROR`). However, `GoogleDriveUploader` used static cached tokens without integrating token renewal during post-ride exports.
* **Preservation of Core Invariants**:
  - The `drive.file` least-privilege scope must be strictly preserved.
  - Independent cloud service operation (Google Drive vs. Dropbox) must remain decoupled.
  - Zero performance regression during ride tracking or export operations.

---

## 5. Architectural Strategy & High-Level Solution

1. **`GoogleDriveAuthManager.kt`**:
   - Implement `refreshTokenSync(context: Context): Result<String>` which executes on background/IO threads, invalidating the stale token in Google Play Services cache and acquiring a fresh one.
   - Refactor `suspend fun refreshToken(context: Context)` to delegate to `refreshTokenSync(context)` via `withContext(Dispatchers.IO)`.

2. **`GoogleDriveClient.kt`**:
   - Accept optional `tokenRefresher: (() -> String?)? = null`.
   - When any REST API call (`findFolderIdByName`, `createFolder`, `findFileIdByName`, `uploadOrOverwriteFile`) encounters HTTP 401:
     - Invoke `tokenRefresher()`.
     - If a fresh token is returned, update the auth header and retry the request once.
   - In `createFolder()`: omit `"parents"` when `parentId == "root"` or blank.
   - Retain `lastHttpCode: Int?` and detailed response error body for diagnostics.

3. **`GoogleDriveUploader.kt`**:
   - Pass `tokenRefresher = { GoogleDriveAuthManager.refreshTokenSync(mContext).getOrNull() }` to `GoogleDriveClient`.
   - If folder hierarchy resolution fails due to 401 / expired auth:
     - Return localized error string: `context.getString(R.string.google_drive_error_auth_expired)`.
   - Update sync status and timestamp accordingly.

4. **Localization**:
   - Add `google_drive_error_auth_expired` to `values/strings.xml` and replicate across all 8 localized `values-<locale>/strings.xml` files with 100% parity.

---

## 6. System Invariants & Risk Assessment

* **Invariants**:
  1. Clean-room test suite (`./gradlew testDebugUnitTest`) must pass 100% with zero regressions.
  2. 100% 9-language translation parity verified via `TranslationParityTest`.
  3. No changes to SQLite schemas or core tracking services.
* **Risk Assessment**:
  - *Low Risk*: Token refresh uses official Google Play Services APIs (`GoogleAuthUtil`) already bundled and tested in the app. Automatic retry on 401 is standard HTTP client behavior.
