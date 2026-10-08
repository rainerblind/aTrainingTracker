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

### 2.5 On-Device Live Logcat Discovery: Google Drive API Disabled in Google Cloud Console
* During interactive live on-device testing on Google Pixel 10 (`66020DLCR002FL`) at 06:49:32, logcat revealed that `GoogleDriveAuthManager` successfully refreshed the Bearer token for `rainer.blind@gmail.com`.
* However, Google Drive REST API calls were rejected with **HTTP 403 `SERVICE_DISABLED`** (`accessNotConfigured`):
  ```text
  "message": "Google Drive API has not been used in project 717488426058 before or it is disabled. Enable it by visiting https://console.developers.google.com/apis/api/drive.googleapis.com/overview?project=717488426058 then retry."
  ```
* Because the HTTP code was 403 rather than 401, `GoogleDriveUploader` mapped it to the generic English string `"Failed to resolve Google Drive folder hierarchy"`.
* Enabling the Google Drive API in Google Cloud Console for project `717488426058` immediately resolved the permission rejection.

### 2.6 Parallel Export Concurrency Race Condition: Duplicate Root Folders
* When workout export is triggered, WorkManager executes two workers in parallel:
  - `Google Drive: TCX` at `07:06:27.393`
  - `Google Drive: FIT` at `07:06:27.399` (6ms later)
* Because `folderIdCache` was previously an instance field of `GoogleDriveClient`, each worker possessed its own isolated cache.
* On initial export when `aTrainingTracker` did not yet exist:
  1. Both workers queried `findFolderIdByName("aTrainingTracker", "root")`. Neither found an existing folder.
  2. Both workers called `createFolder("aTrainingTracker", "root")`.
  3. Because Google Drive allows duplicate folder names (files/folders are keyed by ID, not unique name), Google Drive created **two separate folders named `aTrainingTracker`**.
  4. Worker 1 created `Workouts/` in folder 1 and uploaded `2026-10-08_060104.tcx`.
  5. Worker 2 created `Workouts/` in folder 2 and uploaded `2026-10-08_060104.fit`.
* Resolution requires a static/process-wide `globalFolderIdCache` and `synchronized(hierarchyLock)` with double-checked locking in `ensureFolderHierarchy`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Thread-Safe Token Refresh in `GoogleDriveAuthManager`**:
     Expose a thread-safe synchronous / worker-friendly token refresh method (`refreshTokenSync(context: Context): Result<String>`) that clears stale tokens via `GoogleAuthUtil.clearToken()` and acquires a fresh Bearer token from Google Play Services, persisting it to `SharedPreferences`.
  2. **Automatic 401 Retry & Refresh in `GoogleDriveClient`**:
     Equip `GoogleDriveClient` with a `tokenRefresher: (() -> String?)?` provider. When a request returns HTTP 401, invoke the refresher and retry the request seamlessly.
  3. **Process-Wide Concurrency Synchronization in `GoogleDriveClient`**:
     Implement a static `globalFolderIdCache` and `synchronized(hierarchyLock)` with double-checked locking in `ensureFolderHierarchy()` to guarantee that concurrent workers share resolved folder IDs and never create duplicate root or child folders in Google Drive.
  4. **Database Backup Token Renewal in `GoogleDriveBackupManager`**:
     Pass `tokenRefresher = { GoogleDriveAuthManager.refreshTokenSync(context).getOrNull() }` via `clientProvider(context)` so database backups also refresh expired tokens.
  5. **Actionable HTTP 403 & API Disabled Diagnostics in `GoogleDriveUploader`**:
     Detect `SERVICE_DISABLED` / `accessNotConfigured` and return localized `google_drive_error_api_disabled`, and return `google_drive_error_folder_hierarchy` with HTTP status code for unmapped folder resolution errors across all 9 languages.
  6. **Canonical Folder Creation in `GoogleDriveClient`**:
     In `createFolder()`, omit the `parents` array when `parentId == "root"` or blank, conforming strictly to Google Drive API v3 standards.
  7. **Comprehensive Unit Testing & On-Device Verification**:
     Add unit tests verifying concurrent folder creation safety, HTTP 403 API disabled mapping, canonical folder creation payload structure, and on-device export validation.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Altering the OAuth2 scope (strictly maintain least-privilege `https://www.googleapis.com/auth/drive.file`).
  * Modifying local export file generators (`FitExporter.java`, `TcxExporter.java`, `GpxExporter.java`, `CsvExporter.java`).
  * Altering Dropbox or Strava export services.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-DAT-020` (*Google Drive Authentication, Settings UI & Disconnected State Parity*), refined into `REQ-DAT-022`.
* **Historical Origin & Commit Trace**: Ticket `ATT-1306` (Sprint 2026-41.1, commit `f57007ef`), updated under `ATT-2631` (Sprint 2026-41.3).
* **Root Reason for Existing Formulation**: ATT-1306 introduced native Google Play Services sign-in with `drive.file` scope and error code mapping for Google Sign-In `ApiException` (Status 10 `DEVELOPER_ERROR`). However:
  1. `GoogleDriveUploader` used static cached tokens without integrating token renewal during post-ride exports.
  2. Google Drive API was not enabled in Google Cloud Console for project `717488426058`.
  3. Parallel WorkManager worker execution caused duplicate folder creation race condition.
* **Preservation of Core Invariants**:
  - The `drive.file` least-privilege scope must be strictly preserved.
  - Zero duplicate folder creation in Google Drive.
  - Independent cloud service operation (Google Drive vs. Dropbox) must remain decoupled.
  - Zero performance regression during ride tracking or export operations.

---

## 5. Architectural Strategy & High-Level Solution

1. **`GoogleDriveAuthManager.kt`**:
   - Implement `refreshTokenSync(context: Context): Result<String>` which executes on background/IO threads, invalidating the stale token in Google Play Services cache and acquiring a fresh one.
   - Refactor `suspend fun refreshToken(context: Context)` to delegate to `refreshTokenSync(context)` via `withContext(Dispatchers.IO)`.

2. **`GoogleDriveClient.kt`**:
   - In `companion object`: declare `hierarchyLock = Any()`, `globalFolderIdCache = ConcurrentHashMap<String, String>()`, and `@VisibleForTesting fun clearFolderCache()`.
   - In `ensureFolderHierarchy()`: use double-checked locking over `hierarchyLock` to synchronize folder resolution and creation across concurrent worker threads.
   - Accept optional `tokenRefresher: (() -> String?)? = null`.
   - In `createFolder()`: omit `"parents"` when `parentId == "root"` or blank.
   - Retain `lastHttpCode: Int?` and detailed response error body for diagnostics.

3. **`GoogleDriveBackupManager.kt`**:
   - Update `clientProvider: (Context) -> GoogleDriveClient` to pass `tokenRefresher = { GoogleDriveAuthManager.refreshTokenSync(context).getOrNull() }`.

4. **`GoogleDriveUploader.kt`**:
   - Pass `tokenRefresher = { GoogleDriveAuthManager.refreshTokenSync(mContext).getOrNull() }` to `GoogleDriveClient`.
   - If folder hierarchy resolution fails:
     - HTTP 401: return `context.getString(R.string.google_drive_error_auth_expired)`.
     - HTTP 403 (`SERVICE_DISABLED` / `accessNotConfigured`): return `context.getString(R.string.google_drive_error_api_disabled)`.
     - Other: return `context.getString(R.string.google_drive_error_folder_hierarchy) + (HTTP $code)`.

5. **Localization**:
   - Ensure `google_drive_error_auth_expired`, `google_drive_error_api_disabled`, and `google_drive_error_folder_hierarchy` exist across all 9 localized `values*/strings.xml` files with 100% parity.

---

## 6. System Invariants & Risk Assessment

* **Invariants**:
  1. Clean-room test suite (`./gradlew testDebugUnitTest`) must pass 100% with zero regressions.
  2. 100% 9-language translation parity verified via `TranslationParityTest`.
  3. Zero duplicate folders created on Google Drive during parallel exports.
  4. No changes to SQLite schemas or core tracking services.
* **Risk Assessment**:
  - *Low Risk*: Token refresh uses official Google Play Services APIs (`GoogleAuthUtil`) already bundled and tested in the app. Concurrency lock is scoped strictly to folder hierarchy resolution.
r.
