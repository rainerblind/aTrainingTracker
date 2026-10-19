# Stage 3: Implementation Plan - ATT-4014: Google Drive client: unescaped queries, stale folder cache and partial downloads

**Ticket**: [ATT-4014](https://rainerblind.atlassian.net/browse/ATT-4014)  
**Sub-task**: [ATT-4248](https://rainerblind.atlassian.net/browse/ATT-4248) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-DAT-030` (*Google Drive Query Escaping, Encapsulated Account Cache, Atomic Download Staging & Transparent Scan Results*)  
**Test Mapping**: `TST-DAT-025` (*Google Drive Query Escaping, Account Cache Isolation, Atomic Download Staging & Transparent Scan Results Verification Suite*)  
**Branch**: `feature/ATT-4014`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Description & Background

During an independent whole-app architecture review (2026-10-18, branch `feature/ATT-3921` @ `0234bfc0`, label `code-review-2026-10`), four critical vulnerabilities and resilience deficiencies were identified in `GoogleDriveClient.kt`:
1. Search queries containing unescaped single quotes (`'`) or backslashes (`\`) break Google Drive API v3 query parsing with HTTP 400 Bad Request.
2. The folder cache is process-global and keyed solely by folder path without account isolation, causing cross-account leaks upon user switching and permanent 404 errors when remote folders are deleted.
3. Downloads stream directly into the destination file, corrupting local files with truncated payloads on dropped connections.
4. Recursive folder listing silently aborts on pagination/network errors and returns partial scans, hiding un-synced workouts from users.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-DAT-030` (*Google Drive Query Escaping, Encapsulated Account Cache, Atomic Download Staging & Transparent Scan Results*)
  * `REQ-DAT-030.1`: Centralized Query Construction & Escaping (`GoogleDriveQueryBuilder`)
  * `REQ-DAT-030.2`: Encapsulated, Thread-Safe, Account-Scoped Folder Cache (`GoogleDriveFolderCache`)
  * `REQ-DAT-030.3`: Bounded Self-Healing Folder Re-Creation
  * `REQ-DAT-030.4`: Atomic Download Staging, Durability Flush & Bounded 24h Orphan Cleanup
  * `REQ-DAT-030.5`: Transparent Recursive Directory Traversal (`RecursiveScanResult`)
* **Test Mapping**: `TST-DAT-025` (Test cases `TST-DAT-025.1` through `TST-DAT-025.12`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing Google Drive workout exports (`GoogleDriveUploaderTest`) and database backups (`GoogleDriveBackupManagerTest`) continue to pass cleanly.
2. **Process-Wide Concurrency Invariance**: Concurrent WorkManager workers under the same account share cache and create folders exactly once (`testEnsureFolderHierarchy_concurrentCalls_sharesGlobalCacheWithoutDuplicateCreations`), preserving `REQ-DAT-022`.
3. **API Scope & Placement Invariance**: Strict preservation of least-privilege `drive.file` scope and canonical Google Drive API v3 root folder creation (`parents` omitted for root).
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `GoogleDriveQueryBuilder` (Centralized Query Assembly & Escaping)
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/cloud/googledrive/GoogleDriveQueryBuilder.kt`
* Encapsulates `escapeQueryValue(value: String): String` escaping `\` to `\\` and `'` to `\'`.
* Provides structured factory methods:
  - `buildFolderQuery(folderName: String, parentId: String): String`
  - `buildFileQuery(fileName: String, folderId: String): String`
  - `buildChildrenQuery(folderId: String): String`

### Component 2: `GoogleDriveFolderCache` (Thread-Safe Account-Scoped Cache)
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/cloud/googledrive/GoogleDriveFolderCache.kt`
* Encapsulates internal map and monitor lock.
* Provides:
  - `get(accountKey: String, folderNames: List<String>): String?`
  - `put(accountKey: String, folderNames: List<String>, folderId: String)`
  - `evictFolder(folderId: String)`: atomically removes all entries where value == folderId
  - `invalidateHierarchy(accountKey: String, folderNames: List<String>)`
  - `clear()`

### Component 3: `GoogleDriveClient` (Core Client Refactoring)
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/cloud/googledrive/GoogleDriveClient.kt`
* Constructor accepts `accountProvider: (() -> String?)? = null`.
* Account key resolution: `accountProvider?.invoke()?.trim()?.takeIf { it.isNotEmpty() } ?: tokenFingerprint`.
* Direct queries in `findFolderIdByName`, `findFileIdByName`, and `listFilesRecursively` replaced with `GoogleDriveQueryBuilder`.
* `downloadFileById` refactored to stage into co-located `.part_<timestamp>_<uuid>`, flush with `FileOutputStream.fd.sync()` (handling sync exceptions), verify length against `contentLength` (if > 0), execute atomic move via `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` with fallback to `copyTo` + `delete`, cleanup in `finally`, and lazily purge `.part_*` files older than 24 hours (bounded to 50 files).
* Introduces `data class RecursiveScanResult(val entries: List<DriveFileEntry>, val isComplete: Boolean, val error: Exception? = null)`.
* Implements `listFilesRecursivelyResult(...)` returning `RecursiveScanResult` without swallowing errors.
* `listFilesRecursively(...)` delegates to `listFilesRecursivelyResult(...)`.

### Component 4: `GoogleDriveUploader` & `GoogleDriveBackupManager` (Self-Healing 404 Retry)
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/GoogleDriveUploader.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/migration/GoogleDriveBackupManager.kt`
* Inject `accountProvider = { TrainingApplication.getGoogleDriveAccountEmail() }`.
* If file upload returns HTTP 404, evict the stale folder ID via `client.evictFolder(folderId)`, re-invoke `ensureFolderHierarchy`, and retry upload with `maxAttempts = 1`.

### Component 5: `LegacyImportEngine` (Transparent Incomplete Scan Handling)
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`
* Uses `client.listFilesRecursivelyResult(...)`. If `!scanResult.isComplete`, logs warning and updates listener status while processing discovered entries.

### UI Consistency (Rule 23)
* **No UI changes**. This ticket modifies background cloud integration and file streaming components.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Create `GoogleDriveQueryBuilder`
* Target File: `app/src/main/java/com/atrainingtracker/trainingtracker/cloud/googledrive/GoogleDriveQueryBuilder.kt`
* Implementation: Define pure singleton object with escaping and query assembly.

### Step 2: Create `GoogleDriveFolderCache`
* Target File: `app/src/main/java/com/atrainingtracker/trainingtracker/cloud/googledrive/GoogleDriveFolderCache.kt`
* Implementation: Encapsulated thread-safe cache with account scoping and atomic eviction.

### Step 3: Update `GoogleDriveClient.kt`
* Target File: `app/src/main/java/com/atrainingtracker/trainingtracker/cloud/googledrive/GoogleDriveClient.kt`
* Implementation:
  1. Add `accountProvider` parameter and `resolveAccountKey()`.
  2. Wire queries to `GoogleDriveQueryBuilder`.
  3. Wire cache to companion `GoogleDriveFolderCache`.
  4. Implement atomic download staging with `fd.sync()`, `Files.move`, and bounded 24h cleanup.
  5. Add `RecursiveScanResult` and `listFilesRecursivelyResult`.

### Step 4: Update `GoogleDriveUploader.kt` & `GoogleDriveBackupManager.kt`
* Target Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/GoogleDriveUploader.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/migration/GoogleDriveBackupManager.kt`
* Implementation: Supply `accountProvider` and add bounded 404 eviction + single retry logic.

### Step 5: Update `LegacyImportEngine.kt`
* Target File: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`
* Implementation: Supply `accountProvider` and handle `RecursiveScanResult.isComplete`.

### Step 6: Update & Expand Unit Test Suite
* Target Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/cloud/googledrive/GoogleDriveClientTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/cloud/googledrive/GoogleDriveClientRecursiveListingTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/uploader/GoogleDriveUploaderTest.kt`
* Implementation: Add all 10 unit test cases specified in `TST-DAT-025`.

### Step 7: Targeted Unit Test Execution
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.cloud.googledrive.*" --tests "com.atrainingtracker.trainingtracker.exporter.uploader.GoogleDriveUploaderTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Targeted unit tests verify all edge cases (quotes, backslashes, account switching, 404 re-creation, download stream drop, 24h purge, scan errors).
  - Clean-room regression suite execution in Stage 5 (`./gradlew testDebugUnitTest`).
* **Rollback Plan**:
  - Branch isolation on `feature/ATT-4014` guarantees zero impact on `develop` or `sprint/2026-42.10` in case of revert.
