# Stage 3: Implementation Plan - ATT-4592: [Bug] Backup restore and incremental import fail on Android with SecurityException due to /data/user/0 -> /data/data symlink mismatch in SecureZipExtractor

**Ticket**: [ATT-4592](https://rainerblind.atlassian.net/browse/ATT-4592) | **Sub-task**: Pending Submission (`[Impl-Plan]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4592`  
**Requirement Mapping**: `REQ-MIG-047` (Clauses 1–4) | **Test Mapping**: `TST-MIG-044` (Clauses 1–6)  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Description & Background
On Android devices, application sandbox directories (`filesDir`, `cacheDir`) reside under `/data/user/0/<packageName>`, where `/data/user/0` is an OS-level symlink pointing to `/data/data`.
1. In `SecureZipExtractor.extractBackupArchive()`, `realDestPath = destPath.toRealPath()` resolves to `/data/data/...`, while `entryPath = destPath.resolve(entryName)` retains `/data/user/0/...`.
2. Subsequent `toRealPath(LinkOption.NOFOLLOW_LINKS)` checks on directories/parents/files fail `startsWith(realDestPath)` assertions because `NOFOLLOW_LINKS` prevents resolving symlinks in the path prefix, causing all full restores and incremental imports to abort with `SecurityException: Parent real path escapes destination boundary`.

---

## 2. Proposed Architectural Changes (SWE.2)

| Component / Class | Architectural Responsibility & Modification |
| :--- | :--- |
| **Secure Extractor**<br>`SecureZipExtractor.kt` | Canonicalize destination root once (`val realDestPath = destPath.toRealPath()`). Resolve all entries directly against `realDestPath` (`val entryPath = realDestPath.resolve(entryName).normalize()`). Terminate ancestor symlink traversal loop at `realDestPath` (`while (ancestor != null && ancestor != realDestPath)`). Relativize relative path against `realDestPath`. Maintain all post-write NOFOLLOW_LINKS real path assertions relative to `realDestPath`. |
| **Security Tests**<br>`BackupImportSecurityTest.kt` | Add test suite simulating Android `/data/user/0 -> /data/data` symlinked parent environment: valid archive extraction, zip-slip traversal rejection, internal symlink rejection, and unwhitelisted directory rejection through symlinked parent destination. |

---

## 3. Step-by-Step Implementation Sequence (Stage 4)

### Step 1: Update `SecureZipExtractor.kt` Entry Resolution & Boundary Walk
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/SecureZipExtractor.kt`
* **Changes**:
  1. Line 93: Change `val entryPath = destPath.resolve(entryName).normalize()` to `val entryPath = realDestPath.resolve(entryName).normalize()`.
  2. Line 96: Change `if (!entryPath.startsWith(destPath))` to `if (!entryPath.startsWith(realDestPath))`.
  3. Line 102: Change `while (ancestor != null && ancestor != destPath)` to `while (ancestor != null && ancestor != realDestPath)`.
  4. Line 110: Change `destPath.relativize(entryPath)` to `realDestPath.relativize(entryPath)`.
  5. Verify lines 122, 130, 189 `startsWith(realDestPath)` assertions cleanly match.

### Step 2: Add Symlinked Parent Directory Test Suite in `BackupImportSecurityTest.kt`
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupImportSecurityTest.kt`
* **Test Methods**:
  1. `testSecureZipExtractor_extractsValidArchiveThroughSymlinkedParent()` (`TST-MIG-044.1`)
  2. `testSecureZipExtractor_rejectsZipSlipThroughSymlinkedParent()` (`TST-MIG-044.2`)
  3. `testSecureZipExtractor_rejectsInternalSymlinksThroughSymlinkedParent()` (`TST-MIG-044.2`)
  4. `testSecureZipExtractor_rejectsUnwhitelistedDirectoryThroughSymlinkedParent()` (`TST-MIG-044.3`)

### Step 3: Targeted Unit Testing & Full Suite Verification (`TST-MIG-044`)
* Execute targeted test suite:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.BackupImportSecurityTest"`
* Run requirement governance check:
  `python3 tools/verify_requirement_governance.py`

---

## 4. System Invariants & Rollback Plan
* **Core Invariants**:
  1. Strict rejection of symlinks inside the archive or destination tree (`REQ-MIG-047.2`).
  2. Top-level directory whitelist (`databases/`, `shared_prefs/`, `datastore/`) and 10,000-entry / 1 GB limits (`REQ-MIG-040.3`, `REQ-MIG-040.4`).
  3. Atomic move and fail-safe temporary directory purge on exception (`REQ-MIG-040.6`).
  4. Zero SQLite schema migrations; 100% test suite pass rate.
* **Rollback Plan**: Changes are completely isolated to `feature/ATT-4592`; cleanly reversible via git discard.
