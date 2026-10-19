# Stage 2: Requirement & Test Specification - ATT-4014: Google Drive client: unescaped queries, stale folder cache and partial downloads

**Ticket**: [ATT-4014](https://rainerblind.atlassian.net/browse/ATT-4014)  
**Sub-task**: [ATT-4247](https://rainerblind.atlassian.net/browse/ATT-4247) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-DAT-030` (*Google Drive Query Escaping, Encapsulated Account Cache, Atomic Download Staging & Transparent Scan Results*)  
**Test Spec ID**: `TST-DAT-025`  
**Branch**: `feature/ATT-4014`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Requirement Specification (REQ-DAT-030)

### 1.1 Problem Statement & Rationale
An independent whole-app code review (2026-10-18, branch `feature/ATT-3921` @ `0234bfc0`, label `code-review-2026-10`) identified four critical deficiencies in `GoogleDriveClient.kt`:
1. Search queries containing unescaped single quotes (`'`) or backslashes (`\`) break Drive API v3 query parsing with HTTP 400 Bad Request.
2. The static folder cache is keyed solely by folder path without account isolation, causing cross-account leaks upon user switching and permanent 404 errors when remote folders are deleted.
3. Downloads stream directly into the destination file, corrupting local files with truncated payloads on dropped connections.
4. Recursive folder listing silently aborts on pagination/network errors and returns partial scans, hiding un-synced workouts from users.

### 1.2 Functional & Architectural Requirements
The system SHALL ensure secure query construction, account-isolated folder caching with self-healing re-creation, atomic download streaming, and transparent directory scans (`REQ-DAT-030`):

1. **Centralized Query Construction & Escaping (`GoogleDriveQueryBuilder`, `REQ-DAT-030.1`)**:
   - `GoogleDriveQueryBuilder` SHALL provide `escapeQueryValue(value: String): String`, escaping backslashes (`\\`) and single quotes (`\'`) in strict accordance with Google Drive API v3 query syntax.
   - `GoogleDriveQueryBuilder` SHALL construct search queries for folders (`buildFolderQuery`), files (`buildFileQuery`), and child entries (`buildChildrenQuery`).
   - `GoogleDriveClient` SHALL route all search query generation exclusively through `GoogleDriveQueryBuilder`, eliminating raw string query assembly.

2. **Encapsulated, Thread-Safe, Account-Scoped Folder Cache (`GoogleDriveFolderCache`, `REQ-DAT-030.2`)**:
   - `GoogleDriveFolderCache` SHALL encapsulate internal map storage and concurrency synchronization under a dedicated monitor lock.
   - In production, `accountProvider` SHALL bind to `TrainingApplication.getGoogleDriveAccountEmail()`, ensuring the cache key remains stable across OAuth token refreshes. In headless test harnesses where `accountProvider` is omitted, the cache key SHALL derive from a token fingerprint; on token rotation, the cache for the client instance SHALL be cleanly cleared to prevent duplicate or stale entries.
   - `GoogleDriveFolderCache` SHALL provide atomic eviction methods: `evictFolder(folderId: String)` (atomically removing all entries resolving to that folder ID) and `invalidateHierarchy(accountKey: String, folderNames: List<String>)`.
   - `GoogleDriveClient` SHALL expose `evictFolder(folderId: String)` and `invalidateFolderHierarchy(folderNames: List<String>)` delegating to the encapsulated cache.

3. **Bounded Self-Healing Folder Re-Creation (`REQ-DAT-030.3`)**:
   - In `GoogleDriveUploader.doExport()` and `GoogleDriveBackupManager`, if a file upload fails with HTTP 404 Not Found, the system SHALL evict the stale folder ID via `client.evictFolder(folderId)`.
   - The upload process SHALL invoke `ensureFolderHierarchy` to recreate the remote folder and retry the upload exactly once (`maxAttempts = 1`), preventing infinite loops if permissions are missing. If the second attempt fails, the uploader SHALL return failure immediately.

4. **Atomic Download Staging, Durability Flush & Bounded 24h Orphan Cleanup (`REQ-DAT-030.4`)**:
   - In `GoogleDriveClient.downloadFileById(fileId: String, destinationFile: File)`, download streams SHALL write to a temporary staging file (`.part_<timestamp>_<uuid>`) co-located in `destinationFile.parentFile` (guaranteeing same filesystem inode mount point).
   - Before closing the output stream, the system SHALL invoke `FileOutputStream.fd.sync()` to durably flush file contents and inode metadata to non-volatile storage. If `fd.sync()` fails with `SyncFailedException` or `IOException`, the staging file SHALL be discarded and the download marked as failed.
   - If `contentLength > 0`, the client SHALL verify that the temporary file length matches `contentLength`; mismatches SHALL trigger immediate abort and file deletion.
   - Upon successful verification, the client SHALL atomically move the temporary file to `destinationFile` using `java.nio.file.Files.move` with `StandardCopyOption.ATOMIC_MOVE` and `StandardCopyOption.REPLACE_EXISTING`.
   - If `AtomicMoveNotSupportedException` or filesystem move failure occurs (e.g. non-POSIX external mount), the client SHALL fall back to `copyTo(destinationFile, overwrite = true)` followed by deleting the temporary file.
   - If an exception or stream interruption occurs, the temporary file SHALL be deleted in a `finally` block, leaving `destinationFile` completely untouched.
   - On download invocation, `GoogleDriveClient` SHALL lazily scan `destinationFile.parentFile` (bounded to at most 50 matching files to prevent I/O latency) and delete orphaned `.part_*` files older than 24 hours (`86_400_000L`).

5. **Transparent Recursive Directory Traversal (`RecursiveScanResult`, `REQ-DAT-030.5`)**:
   - `GoogleDriveClient` SHALL provide `listFilesRecursivelyResult(folderId: String, extensions: List<String>): RecursiveScanResult`, where `RecursiveScanResult` exposes `val entries: List<DriveFileEntry>`, `val isComplete: Boolean`, and `val error: Exception? = null`.
   - If an HTTP error or exception occurs on any page or subfolder, `isComplete` SHALL be false and `error` SHALL contain the failure exception, while accumulated entries remain available for diagnostic inspection.
   - `listFilesRecursively` SHALL maintain backward compatibility by delegating to `listFilesRecursivelyResult`.
   - `LegacyImportEngine` SHALL inspect `RecursiveScanResult.isComplete`; if false, it SHALL log a descriptive warning and update listener status without silently treating the directory as completely processed.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Query Escaping)**:
  * *Given* a folder or file name containing single quotes or backslashes (e.g. `"Runner's Track"`, `"Workouts\\2026"`),
  * *When* `findFolderIdByName` or `findFileIdByName` executes,
  * *Then* the generated query escapes quotes as `\'` and backslashes as `\\`, and the request succeeds with HTTP 200 without HTTP 400 Bad Request parse errors.
* **Criterion 2 (Account Isolation & Stale Cache Eviction)**:
  * *Given* Account A has resolved `"aTrainingTracker/Workouts"` to folder ID `"folder_a"`,
  * *When* the user switches to Account B,
  * *Then* `ensureFolderHierarchy` resolves a separate folder ID `"folder_b"` without returning `"folder_a"`.
* **Criterion 3 (Self-Healing 404 Re-Creation)**:
  * *Given* a previously cached Google Drive folder was deleted remotely,
  * *When* `GoogleDriveUploader.doExport()` executes an upload and receives HTTP 404,
  * *Then* the client evicts the stale folder ID, recreates the folder via `ensureFolderHierarchy`, and successfully uploads the file on the single retry.
* **Criterion 4 (Atomic Download Protection & Durability)**:
  * *Given* an active file download via `downloadFileById`,
  * *When* the network connection drops or throws an `IOException` mid-stream,
  * *Then* the partially written `.part_*` file is deleted, and `destinationFile` is never corrupted or left in a truncated state.
* **Criterion 5 (Transparent Recursive Scan)**:
  * *Given* a recursive folder scan encountering an HTTP error on page 2,
  * *When* `listFilesRecursivelyResult` executes,
  * *Then* `isComplete` is false, `error` contains the HTTP failure details, and `LegacyImportEngine` surfaces the incomplete status rather than masking it.

### 1.4 System Invariants & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-DAT-022` (*Google Drive Background Token Refresh, 401 Re-Authentication, Folder Hierarchy Resolution Resilience & Concurrency Synchronization*).
* **Historical Origin & Commit Trace**: Ticket `ATT-2631` (Sprint `2026-41.3`), refined from `ATT-1306`.
* **Root Reason for Existing Formulation**: `globalFolderIdCache` and `hierarchyLock` were created to prevent concurrent WorkManager workers from creating duplicate folders.
* **Preservation of Core Invariants**: Concurrency deduplication across workers under the same account is strictly preserved. In addition, queries with special characters execute reliably, cross-account leaks are eliminated, deleted folders recover self-healingly, file downloads are atomic with durability sync and 24h cleanup, and folder scans report errors transparently.

---

## 2. Test Specification (TST-DAT-025)

### Test Case 1: `testGoogleDriveQueryBuilder_escapesQuotesAndBackslashes` (`TST-DAT-025.1`)
* **Scope**: Unit Test (`GoogleDriveClientTest.kt`)
* **Action**: Invoke `GoogleDriveQueryBuilder.escapeQueryValue` with `"Runner's Track"`, `"Path\\Name"`, and `"O'Connor's \\Special\\ Folder"`.
* **Expected Result**: Single quotes escaped as `\'`, backslashes escaped as `\\`. Verifies generated queries contain properly escaped clauses.

### Test Case 2: `testEnsureFolderHierarchy_withSpecialCharacters_executesEscapedQuery` (`TST-DAT-025.2`)
* **Scope**: Unit Test (`GoogleDriveClientTest.kt`)
* **Action**: Invoke `ensureFolderHierarchy(listOf("Runner's Club", "Today's 'Special'"))`.
* **Expected Result**: OkHttp interceptor verifies URL query string contains encoded `name = 'Runner\'s Club'` and `name = 'Today\'s \'Special\''`.

### Test Case 3: `testGoogleDriveFolderCache_accountIsolation` (`TST-DAT-025.3`)
* **Scope**: Unit Test (`GoogleDriveClientTest.kt`)
* **Action**: Configure Client A with account `"user1@example.com"` and Client B with `"user2@example.com"`. Call `ensureFolderHierarchy(listOf("aTrainingTracker"))` on both.
* **Expected Result**: Client A receives folder `"id_user1"` and Client B receives folder `"id_user2"`. Entries in cache do not collide or leak across accounts.

### Test Case 4: `testEvictFolder_atomicallyClearsMatchingEntries` (`TST-DAT-025.4`)
* **Scope**: Unit Test (`GoogleDriveClientTest.kt`)
* **Action**: Populate cache with `"aTrainingTracker/Workouts" -> "stale_folder_123"`. Invoke `client.evictFolder("stale_folder_123")`.
* **Expected Result**: Cache lookup for `"aTrainingTracker/Workouts"` returns null, and next `ensureFolderHierarchy` re-queries Drive.

### Test Case 5: `testUploadOrOverwriteFile_onHttp404_evictsFolderAndRecreatesHierarchy` (`TST-DAT-025.5`)
* **Scope**: Unit Test (`GoogleDriveUploaderTest.kt`)
* **Action**: Simulate initial upload returning HTTP 404 on cached folder ID, followed by folder re-creation and successful upload.
* **Expected Result**: Uploader evicts stale folder ID, calls `ensureFolderHierarchy` to recreate, retries upload once, and returns `ExportResult(true, ...)`.

### Test Case 6: `testDownloadFileById_atomicStaging_cleansUpPartFileOnFailureAndDoesNotCorruptDestination` (`TST-DAT-025.6`)
* **Scope**: Unit Test (`GoogleDriveClientTest.kt`)
* **Action**: Create an existing destination file with original content. Mock OkHttp response body that throws `IOException` mid-stream during download.
* **Expected Result**: `client.downloadFileById(...)` returns false. `destinationFile` retains original content untouched. No `.part_*` files remain in the directory.

### Test Case 7: `testDownloadFileById_atomicStaging_successRenamesDurablyToDestination` (`TST-DAT-025.7`)
* **Scope**: Unit Test (`GoogleDriveClientTest.kt`)
* **Action**: Download valid binary payload to destination file.
* **Expected Result**: Download succeeds, destination file contains exact downloaded bytes, and no `.part_*` file remains.

### Test Case 8: `testDownloadFileById_purgesStalePartFilesOlderThan24Hours` (`TST-DAT-025.8`)
* **Scope**: Unit Test (`GoogleDriveClientTest.kt`)
* **Action**: Create a `.part_old` file with `lastModified` set to 25 hours ago, and a `.part_fresh` file modified 10 minutes ago. Trigger `downloadFileById`.
* **Expected Result**: Stale part file is deleted; fresh part file remains untouched.

### Test Case 9: `testListFilesRecursivelyResult_onHttpError_returnsIncompleteScanResultWithError` (`TST-DAT-025.9`)
* **Scope**: Unit Test (`GoogleDriveClientRecursiveListingTest.kt`)
* **Action**: Simulate root folder returning 1 subfolder, but subfolder query failing with HTTP 500.
* **Expected Result**: `listFilesRecursivelyResult` returns `RecursiveScanResult` with `isComplete == false` and non-null `error` explaining the failure.

### Test Case 10: `testListFilesRecursivelyResult_onSuccess_returnsCompleteScanResult` (`TST-DAT-025.10`)
* **Scope**: Unit Test (`GoogleDriveClientRecursiveListingTest.kt`)
* **Action**: Perform complete recursive scan across multiple pages and subfolders.
* **Expected Result**: `RecursiveScanResult` has `isComplete == true`, `error == null`, and contains all discovered files.

### Test Case 11: 9-Language Localization & Specifier Audit (`TST-DAT-025.11`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Action**: Verify all Google Drive error strings (`google_drive_error_auth_expired`, `google_drive_error_api_disabled`, `google_drive_error_folder_hierarchy`) exist across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 12: Clean-Room Full Regression Suite (`TST-DAT-025.12`)
* **Scope**: Full Clean-Room Regression (`./gradlew testDebugUnitTest`)
* **Expected Result**: 100% test pass rate with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-DAT-025.1]` | Unit | `GoogleDriveQueryBuilder.escapeQueryValue` | `REQ-DAT-030.1` | Specified |
| `[TST-DAT-025.2]` | Unit | `GoogleDriveClient.ensureFolderHierarchy` | `REQ-DAT-030.1` | Specified |
| `[TST-DAT-025.3]` | Unit | `GoogleDriveFolderCache.get` / `put` | `REQ-DAT-030.2` | Specified |
| `[TST-DAT-025.4]` | Unit | `GoogleDriveFolderCache.evictFolder` | `REQ-DAT-030.2` | Specified |
| `[TST-DAT-025.5]` | Unit | `GoogleDriveUploader.doExport` | `REQ-DAT-030.3` | Specified |
| `[TST-DAT-025.6]` | Unit | `GoogleDriveClient.downloadFileById` | `REQ-DAT-030.4` | Specified |
| `[TST-DAT-025.7]` | Unit | `GoogleDriveClient.downloadFileById` | `REQ-DAT-030.4` | Specified |
| `[TST-DAT-025.8]` | Unit | `GoogleDriveClient.purgeStalePartFiles` | `REQ-DAT-030.4` | Specified |
| `[TST-DAT-025.9]` | Unit | `GoogleDriveClient.listFilesRecursivelyResult` | `REQ-DAT-030.5` | Specified |
| `[TST-DAT-025.10]` | Unit | `GoogleDriveClient.listFilesRecursivelyResult` | `REQ-DAT-030.5` | Specified |
| `[TST-DAT-025.11]` | Localization | `TranslationParityTest` | `REQ-DAT-030`, `REQ-LOC-001` | Specified |
| `[TST-DAT-025.12]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
