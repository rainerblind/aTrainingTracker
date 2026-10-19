# Stage 1 Analysis: ATT-4014 - [Bug] [Medium] Google Drive client: unescaped queries, stale folder cache and partial downloads

**Ticket**: [ATT-4014](https://rainerblind.atlassian.net/browse/ATT-4014)  
**Sub-task**: [ATT-4246](https://rainerblind.atlassian.net/browse/ATT-4246) (`[Analysis]`)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Branch**: `feature/ATT-4014`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Statement & Motivation

During an independent whole-app code review (2026-10-18, branch `feature/ATT-3921` @ `0234bfc0`, label `code-review-2026-10`), four critical vulnerabilities and resilience deficiencies were identified in `GoogleDriveClient.kt`:

1. **Unescaped Drive Query String Injection**:
   Query construction in `findFolderIdByName`, `findFileIdByName`, and `listFilesRecursively` inserts folder names, file names, and parent IDs directly into single-quoted literals:
   ```kotlin
   val query = "name = '$folderName' and '$parentId' in parents and mimeType = '$FOLDER_MIME_TYPE' and trashed = false"
   val query = "name = '$fileName' and '$folderId' in parents and trashed = false"
   val query = "'$currentFolderId' in parents and trashed = false"
   ```
   If a folder or file name contains a single quote (`'`) (e.g. `"Runner's Track"`, `"Today's Workout.fit"`) or a backslash (`\`), Google Drive API v3 rejects the query with HTTP 400 Bad Request query parse error.

2. **Process-Global, Account-Agnostic, Un-evictable Folder Cache**:
   `globalFolderIdCache = ConcurrentHashMap<String, String>()` was established in `REQ-DAT-022` to synchronize concurrent WorkManager workers and avoid duplicate folder creations. However:
   - **Account collision / leak**: Cache keys are un-scoped path strings (e.g. `"aTrainingTracker/Workouts"`). If an athlete switches Google accounts (or unlinks and relinks a different account), folder IDs from Account A are reused in Account B, leading to HTTP 404 (File not found) or HTTP 403 (Forbidden) on every subsequent upload.
   - **Stale cache on remote deletion**: If the folder hierarchy is deleted remotely (via the Google Drive web interface or another device), `ensureFolderHierarchy` continues returning the stale cached folder ID indefinitely until process death. Subsequent file uploads fail permanently with HTTP 404.
   - **Race Conditions**: Reading outside synchronized blocks while evicting creates read-after-eviction stale races.

3. **Partial Downloads on Interrupted Connections**:
   In `downloadFileById(fileId: String, destinationFile: File)`, the HTTP response body stream is piped directly into `destinationFile`:
   ```kotlin
   FileOutputStream(destinationFile).use { out ->
       body.byteStream().use { input -> input.copyTo(out) }
   }
   ```
   If the network connection drops, times out, or throws an `IOException` mid-stream, a truncated, corrupted file remains on disk at `destinationFile`. Downstream FIT/TCX/GPX parsers or ZIP extractors subsequently fail or import corrupt partial data. Furthermore, interrupted downloads may leave orphaned temporary files if not systematically purged.

4. **Silent Failure & Partial Scan Results in Recursive Listing**:
   In `listFilesRecursively`, when an API query encounters an HTTP error (`!response.isSuccessful`) or throws an exception, it logs the error, clears `pageToken = null`, and aborts further traversal of that folder branch while returning whatever partial results were accumulated. The caller (`LegacyImportEngine`) has no visibility that the directory traversal failed prematurely and cannot distinguish a complete directory scan from a truncated one.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Google Drive API v3 Search Query Syntax
Google Drive v3 API specification for search queries requires string values to be enclosed in single quotes. Within string literals:
- Single quotes (`'`) must be escaped with a backslash (`\'`).
- Backslashes (`\`) must be escaped with another backslash (`\\`).
Without a structured query builder, manual concatenation is error-prone and vulnerable to query breakages:
`400 Invalid Value: parseError: Invalid query`.
To eliminate injection risks, query construction must be strictly centralized in an internal utility object, prohibiting raw string query assembly in network methods.

### 2.2 Companion Cache Architecture & Account Isolation
In `GoogleDriveClient.kt`:
```kotlin
companion object {
    private val hierarchyLock = Any()
    private val globalFolderIdCache = ConcurrentHashMap<String, String>()
}
```
`globalFolderIdCache` lives in the static companion object. In `ensureFolderHierarchy`:
```kotlin
val cacheKey = folderNames.joinToString("/")
globalFolderIdCache[cacheKey]?.let { return it }
```
Because the key is only `folderNames.joinToString("/")`:
1. It contains no stable account discriminator. If the account identifier is not stable or missing, using a transient JVM hash code is dangerous. A dedicated `GoogleDriveFolderCache` encapsulating state and synchronization must key by stable account email or unique token fingerprint (never a shared fallback).
2. It lacks atomic eviction hooks when uploads fail with HTTP 404.
3. Decoupled map operations without encapsulating lock synchronization permit race conditions between key-value invalidation and lookup.

### 2.3 Direct File Streaming & Non-Atomic Renames
Direct streaming to the target file destination violates atomic file persistence principles. If OkHttp socket timeouts or disconnects occur after partial bytes are written, the file exists on disk with incomplete headers/data. Subsequent operations inspecting `file.exists()` treat the file as present and attempt decoding.
Furthermore, on Android/Linux storage, staging in a temporary file requires:
- Co-location in the exact same parent directory (same filesystem inode mount point) to guarantee POSIX atomic rename semantics.
- `FileOutputStream.fd.sync()` before closing to guarantee data and inode metadata are flushed to non-volatile storage.
- Atomic move via `java.nio.file.Files.move` with `StandardCopyOption.ATOMIC_MOVE` and `StandardCopyOption.REPLACE_EXISTING`.
- 24-hour cleanup policy for orphaned `.part_*` files to prevent disk clutter while avoiding purging active long-term transfers.

### 2.4 Unchecked Pagination & Error Swallowing
In `listFilesRecursively`:
```kotlin
if (!response.isSuccessful) {
    Log.e(TAG, "listFilesRecursively failed for folder $currentFolderId with code: ${response.code}")
    pageToken = null
    return@use
}
```
Setting `pageToken = null` cleanly terminates the `do { ... } while (pageToken != null)` loop for that folder, but `results` is returned to the caller as if traversal completed successfully. The caller (`LegacyImportEngine`) cannot distinguish an empty directory from an authorization failure (HTTP 401/403) or rate limit (HTTP 429), leading to silent loss of un-imported workout files.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Enforced Query Construction**: Introduce an internal `GoogleDriveQueryBuilder` object that encapsulates escaping logic (`\` and `'`) and clause assembly. Refactor `GoogleDriveClient` to construct all Drive queries exclusively through `GoogleDriveQueryBuilder`.
  2. **Encapsulated Thread-Safe Account-Scoped Cache**: Create `GoogleDriveFolderCache` that encapsulates the map and lock, with keys strictly bound to account email or SHA-256 token fingerprint (rejecting cross-account collisions).
  3. **Atomic Key-Value Eviction**: Ensure `evictFolder(folderId)` atomically removes matching folder IDs and path keys under the internal cache lock.
  4. **Bounded Self-Healing Re-Creation**: If upload fails with HTTP 404, evict the stale folder ID, call `ensureFolderHierarchy` to recreate the folder, and retry the upload exactly once (bounded retry limit of 1 attempt; if re-creation or retry fails, fail cleanly without looping).
  5. **Atomic File Staging, Same-Directory Durability Flush & 24h Orphan Cleanup**: In `downloadFileById`, stage the download in a sibling `.part_<timestamp>_<uuid>` file within the exact same parent directory, flush via `fd.sync()`, verify byte length against `contentLength` (when positive), execute atomic move (`StandardCopyOption.ATOMIC_MOVE`), and clean up `.part_*` files older than 24 hours.
  6. **Resilient Error-Transparent Directory Traversal**: Provide `listFilesRecursivelyResult(folderId, extensions): RecursiveScanResult` exposing `entries: List<DriveFileEntry>`, `isComplete: Boolean`, and `error: Exception?`. Update `LegacyImportEngine` to check `isComplete` and `error`, notifying the athlete/UI if a scan was interrupted rather than silently assuming completeness.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Do not rewrite Google Drive OAuth authentication flow or replace Google Play Services `GoogleAuthUtil` / `GoogleSignIn`.
  2. Do not alter least-privilege `https://www.googleapis.com/auth/drive.file` scope.
  3. Do not modify Dropbox or Strava cloud client implementations.
  4. Do not alter existing directory hierarchy naming conventions (`aTrainingTracker/FIT`, `aTrainingTracker/TCX`, `aTrainingTracker/Workouts`, `aTrainingTracker/Backups`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-DAT-022` (*Google Drive Background Token Refresh, 401 Re-Authentication, Folder Hierarchy Resolution Resilience & Concurrency Synchronization*), targeting `GoogleDriveClient.kt`, `GoogleDriveUploader.kt`, `GoogleDriveBackupManager.kt`.
* **Historical Origin & Commit Trace**: Ticket `ATT-2631` (Sprint `2026-41.3`), refined from `ATT-1306` (Sprint `2026-41.1`, commit `f57007ef`).
* **Root Reason for Existing Formulation**: `globalFolderIdCache` and `hierarchyLock` were introduced in `REQ-DAT-022` because parallel WorkManager workers (e.g. TCX and FIT exports executing simultaneously) triggered duplicate folder creation requests in Google Drive (`aTrainingTracker` and `Workouts`). The companion cache successfully deduplicated concurrent resolution calls across client instances. However, the cache key was account-agnostic and un-evictable, causing stale 404 failures if folders were deleted remotely or accounts switched.
* **Preservation of Core Invariants**: The concurrency deduplication and thread-safety invariants established by `REQ-DAT-022` are strictly preserved: concurrent workers under the same account still share the cache and lock without creating duplicate folders. In addition, account switching is isolated, remote deletions trigger self-healing folder re-creation, queries with special characters execute reliably, downloads are atomic with durability sync and 24h orphaned file cleanup, and listing errors are transparently reported via `RecursiveScanResult` without data loss.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Centralized Query Builder (`GoogleDriveQueryBuilder`)
A structured internal query builder object encapsulates escaping logic and clause assembly. Raw query strings are eliminated from `GoogleDriveClient`:
```kotlin
internal object GoogleDriveQueryBuilder {
    fun escapeQueryValue(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'")

    fun buildFolderQuery(folderName: String, parentId: String): String =
        "name = '${escapeQueryValue(folderName)}' and '${escapeQueryValue(parentId)}' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"

    fun buildFileQuery(fileName: String, folderId: String): String =
        "name = '${escapeQueryValue(fileName)}' and '${escapeQueryValue(folderId)}' in parents and trashed = false"

    fun buildChildrenQuery(folderId: String): String =
        "'${escapeQueryValue(folderId)}' in parents and trashed = false"
}
```

### 5.2 Encapsulated Account-Scoped Cache (`GoogleDriveFolderCache`)
```kotlin
internal class GoogleDriveFolderCache {
    private val lock = Any()
    private val cache = mutableMapOf<String, String>()

    fun get(accountKey: String, folderNames: List<String>): String? = synchronized(lock) {
        cache["$accountKey:${folderNames.joinToString("/")}"]
    }

    fun put(accountKey: String, folderNames: List<String>, folderId: String) = synchronized(lock) {
        cache["$accountKey:${folderNames.joinToString("/")}"] = folderId
    }

    fun evictFolder(folderId: String) = synchronized(lock) {
        val keysToRemove = cache.filterValues { it == folderId }.keys
        keysToRemove.forEach { cache.remove(it) }
    }

    fun invalidateHierarchy(accountKey: String, folderNames: List<String>) = synchronized(lock) {
        cache.remove("$accountKey:${folderNames.joinToString("/")}")
    }

    fun clear() = synchronized(lock) {
        cache.clear()
    }
}
```
- **Stable Account Key Computation**:
  ```kotlin
  private fun resolveAccountKey(): String {
      val email = accountProvider?.invoke()?.trim()
      if (!email.isNullOrEmpty()) return email
      val token = getAuthToken()
      if (!token.isNullOrEmpty()) {
          // Cryptographic fingerprint to isolate tokens without shared fallback
          return java.security.MessageDigest.getInstance("SHA-256")
              .digest(token.toByteArray())
              .take(8)
              .joinToString("") { "%02x".format(it) }
      }
      return "anonymous"
  }
  ```
- **Bounded Self-Healing Re-Creation**: In `GoogleDriveUploader` and `GoogleDriveBackupManager`, if `uploadOrOverwriteFile` encounters HTTP 404, it evicts the stale folder ID, re-resolves the hierarchy with `ensureFolderHierarchy`, and retries upload with an explicit `maxAttempts = 1` guard. If re-creation fails or the single retry fails, it exits cleanly with failure diagnostics, preventing infinite loops.

### 5.3 Atomic File Staging, Same-Directory Durability Flush & 24h Orphan Cleanup
```kotlin
fun downloadFileById(fileId: String, destinationFile: File): Boolean {
    val parentDir = destinationFile.parentFile ?: File(".")
    purgeStalePartFiles(parentDir, maxAgeMillis = 86_400_000L) // 24 hours

    val tempFile = File(parentDir, "${destinationFile.name}.part_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}")
    var completed = false
    try {
        executeCallWithRetry { token -> ... }.use { response ->
            if (!response.isSuccessful) return false
            val body = response.body ?: return false
            FileOutputStream(tempFile).use { fos ->
                body.byteStream().use { input ->
                    input.copyTo(fos)
                }
                fos.fd.sync() // Durably sync data and inode to storage
            }

            val contentLength = body.contentLength()
            if (contentLength > 0 && tempFile.length() != contentLength) {
                Log.e(TAG, "Incomplete download: expected $contentLength, got ${tempFile.length()}")
                return false
            }

            // Atomic move within the exact same directory (same filesystem mount point)
            try {
                java.nio.file.Files.move(
                    tempFile.toPath(),
                    destinationFile.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            } catch (e: Exception) {
                if (destinationFile.exists()) destinationFile.delete()
                if (!tempFile.renameTo(destinationFile)) {
                    tempFile.copyTo(destinationFile, overwrite = true)
                    tempFile.delete()
                }
            }
            completed = true
            return true
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error downloading file $fileId: ${e.message}", e)
        return false
    } finally {
        if (!completed && tempFile.exists()) {
            tempFile.delete()
        }
    }
}
```

### 5.4 Resilient Error-Transparent Directory Traversal
1. `RecursiveScanResult(val entries: List<DriveFileEntry>, val isComplete: Boolean, val error: Exception? = null)`:
   - Contains discovered entries up to point of completion.
   - If an error occurs, `isComplete = false` and `error` contains the descriptive `IOException`.
2. In `LegacyImportEngine`:
   - Invokes `listFilesRecursivelyResult(folderId, targetExtensions)`.
   - If `!scanResult.isComplete`, logs a diagnostic warning and updates `listener?.onStatus("Google Drive scan incomplete: ${scanResult.error?.message}")`.
   - Incorporates discovered files while signaling partial completion, avoiding silent data omission.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing Google Drive exports (`GoogleDriveUploaderTest`) and database backups (`GoogleDriveBackupManagerTest`).
  2. Strict preservation of least-privilege `drive.file` scope and canonical Drive API v3 root folder creation (`parents` omitted for root).
  3. Concurrent WorkManager workers under the same account share cache and create folders exactly once (`testEnsureFolderHierarchy_concurrentCalls_sharesGlobalCacheWithoutDuplicateCreations`).
  4. Atomic download guarantees: co-located temporary staging, `fd.sync()`, POSIX `ATOMIC_MOVE`, and 24-hour cleanup policy.
  5. Bounded self-healing: 404 eviction retry is strictly bounded to 1 attempt to avoid infinite loops.
  6. Transparent recursive listing: `RecursiveScanResult` provides complete error visibility without silent omissions.
  7. 100% clean-room test pass rate across the full test suite (`./gradlew testDebugUnitTest`).
* **Risk Rating**: **MEDIUM**
  - Justification: Modifies core cloud client used for workout exports, database backups, and legacy imports. Mitigated by comprehensive unit test coverage with MockWebServer / OkHttp interceptors simulating single quotes, remote 404 deletions, account switching, interrupted streams, and pagination failures.
