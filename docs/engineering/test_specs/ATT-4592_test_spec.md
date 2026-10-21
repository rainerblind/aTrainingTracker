# Stage 2: Requirement & Test Specification - ATT-4592: Backup restore and incremental import fail on Android due to symlink mismatch in SecureZipExtractor

**Ticket**: [ATT-4592](https://rainerblind.atlassian.net/browse/ATT-4592)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.6`  
**Requirement Mapping**: `REQ-MIG-047` (*Canonical Destination Resolution & Symlinked Parent Boundary Tolerance in SecureZipExtractor*)  
**Test Spec ID**: `TST-MIG-044`  
**Branch**: `feature/ATT-4592`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-21  

---

## 1. Requirement Specification (REQ-MIG-047)

### 1.1 Problem Statement & Rationale
* On Android devices, application sandbox directories (`filesDir`, `cacheDir`) reside under `/data/user/0/<packageName>`, where `/data/user/0` is an OS-level symlink pointing to `/data/data`.
* `SecureZipExtractor.extractBackupArchive` resolves `realDestPath = destPath.toRealPath()` (`/data/data/...`) but derives `entryPath` from non-canonical `destPath` (`/data/user/0/...`).
* Subsequent calls to `toRealPath(LinkOption.NOFOLLOW_LINKS)` fail `startsWith(realDestPath)` assertions because `NOFOLLOW_LINKS` prevents canonicalizing parent segments (`/data/user/0`), causing all backup restores and imports to abort with `SecurityException`.

### 1.2 Functional & Architectural Requirements
1. **Canonical Destination Root Resolution (`REQ-MIG-047.1`)**: `SecureZipExtractor` SHALL resolve `val realDestPath = destPath.toRealPath()` once after creating destination directories and SHALL resolve all archive entries directly against `realDestPath` (`val entryPath = realDestPath.resolve(entryName).normalize()`).
2. **Parent Symlink Tolerance & Boundary Containment (`REQ-MIG-047.2`)**: The ancestor symlink walk SHALL terminate at `ancestor == realDestPath` (`while (ancestor != null && ancestor != realDestPath)`), verifying no symlinks exist at or beneath `realDestPath` while ignoring pre-existing OS-level symlinks above `realDestPath`.
3. **Canonical NOFOLLOW_LINKS Assertions (`REQ-MIG-047.3`)**: Directory, parent, and file `toRealPath(LinkOption.NOFOLLOW_LINKS)` validations SHALL assert containment against canonical `realDestPath`.
4. **Preservation of System Invariants (`REQ-MIG-047.4`)**: Zero SQLite schema migrations, strict rejection of symlinks inside archives or destination hierarchies, 10,000 entry / 1 GB streaming limits, and 100% clean-room test pass rate.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Symlinked Parent Extraction)**: *Given* an extraction destination accessed through a symlinked parent directory (e.g. Android `/data/user/0 -> /data/data`), *When* `extractBackupArchive` unpacks a valid backup archive, *Then* extraction succeeds cleanly without `SecurityException`.
* **AC-2 (Zip-Slip Traversal Rejection)**: *Given* an archive with traversal entries (`../../databases/evil.db`), *When* extracted into a symlinked parent destination, *Then* extraction aborts immediately with `SecurityException` and staging is purged.
* **AC-3 (Internal Symlink Rejection)**: *Given* an archive containing symbolic link entries, *When* extracted, *Then* extraction strictly rejects the entry with `SecurityException`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-MIG-040` (`SecureZipExtractor.kt`).
* **Historical Origin & Commit Trace**: Commit `b87593805db6` (`ATT-3982`, Sprint `2026-42.10`, author: Rainer Blind).
* **Root Reason for Existing Formulation**: Introduced to block CWE-22 zip-slip traversal and symlink TOCTOU attacks; tested in `/tmp` without parent symlinks, which concealed Android `/data/user/0` divergence.
* **Preservation of Core Invariants**: Boundary checks, entry counts, 1 GB streaming limits, top-level whitelist, and internal symlink rejection are 100% preserved.

---

## 2. Test Specification (TST-MIG-044)

### Test Case 1: Symlinked Parent Directory Valid Archive Extraction (`TST-MIG-044.1`)
* **Scope**: Unit Test (`BackupImportSecurityTest.kt`)
* **Preconditions**: Target extraction path created through a parent directory symlink simulating Android `/data/user/0 -> /data/data`.
* **Action**: Unpack a canonical `.attbackup` archive containing `databases/`, `shared_prefs/`, and `datastore/` files.
* **Expected Result**: Extraction completes cleanly; all files exist at target; zero `SecurityException` thrown.

### Test Case 2: Symlinked Parent Directory Zip-Slip Traversal & Symlink Rejection (`TST-MIG-044.2`)
* **Scope**: Security Test (`BackupImportSecurityTest.kt`)
* **Preconditions**: Symlinked parent destination fixture.
* **Action**: Attempt extraction of archives containing (a) `../../evil.db` path traversal, and (b) internal symbolic links.
* **Expected Result**: Both fail immediately with `SecurityException`; destination staging is purged.

### Test Case 3: Whitelist & Resource Limit Enforcement (`TST-MIG-044.3`)
* **Scope**: Security Test (`BackupImportSecurityTest.kt`)
* **Action**: Attempt extraction of entries outside `databases/`, `shared_prefs/`, `datastore/`.
* **Expected Result**: Rejection with `SecurityException("Invalid backup entry outside whitelisted directories")`.

### Test Case 4: Clean-Room Full Suite Regression (`TST-MIG-044.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% pass rate with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MIG-044.1` | Unit | `SecureZipExtractor.extractBackupArchive` | `REQ-MIG-047.1`, `REQ-MIG-047.2` | Specified |
| `TST-MIG-044.2` | Security | `SecureZipExtractor.extractBackupArchive` | `REQ-MIG-047.2`, `REQ-MIG-040.2` | Specified |
| `TST-MIG-044.3` | Security | `SecureZipExtractor.extractBackupArchive` | `REQ-MIG-047.3`, `REQ-MIG-040.3` | Specified |
| `TST-MIG-044.4` | Regression | Full Test Suite (`./gradlew testDebugUnitTest`) | `REQ-MIG-047.4`, `REQ-PRO-001` | Specified |
