# Stage 2: Requirement & Test Specification - ATT-3982: [Highest] Backup import vulnerable to zip-slip path traversal

**Ticket**: [ATT-3982](https://atrainingtracker.atlassian.net/browse/ATT-3982)  
**Sub-task**: [ATT-4147](https://atrainingtracker.atlassian.net/browse/ATT-4147) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-MIG-040` (*Zip-Slip Path Traversal Protection, Top-Level Whitelist & Resource Exhaustion Defense in Backup Extraction*)  
**Test Spec ID**: `TST-MIG-037`  
**Branch**: `feature/ATT-3982`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Requirement Specification (`REQ-MIG-040`)

### 1.1 Problem Statement & Rationale
`MigrationEngine.performFullRestore()` and `ImportEngine.unzipBackup()` unpack incoming `.attbackup` archives to a temporary directory using `File(destDir, entry.name)` without canonical path validation. If an archive contains path traversal characters (`../`), `File(destDir, entry.name)` resolves to paths outside `destDir`, allowing arbitrary file overwrites across the app private sandbox.
Furthermore, in `ImportEngine.analyzeBackup()`, `unzipBackup()` is executed automatically as soon as a user selects a backup file to preview its metadata. A malicious archive can compromise internal databases, shared preferences, or libraries during analysis even before user confirmation!
In addition, `LegacyImportEngine.kt:413` downloads Google Drive files using `File(workerDir, entry.name)` without sanitizing path traversal elements in Drive file names.

### 1.2 Functional & Architectural Requirements (`REQ-MIG-040`)

1. **Canonical Destination Boundary Validation (`REQ-MIG-040.1`)**:
   - In `MigrationEngine.performFullRestore()`, `ImportEngine.unzipBackup()`, `ImportEngine.analyzeBackup()`, and `ImportEngine.performIncrementalImport()`, the system SHALL verify that for every `entry` in the zip archive, the target file satisfies `outFile.canonicalFile.toPath().startsWith(destDir.canonicalFile.toPath())`.
   - If an entry's canonical path falls outside `destDir` (e.g. `../../databases/evil.db`), extraction SHALL immediately abort, delete any partially extracted files in `destDir`, and throw a `SecurityException("Zip-slip path traversal attempt detected: " + entry.name)`.

2. **Top-Level Directory Whitelist Validation (`REQ-MIG-040.2`)**:
   - For `.attbackup` archives, all extracted file and directory entries SHALL reside strictly within one of the three canonical top-level directories: `databases/`, `shared_prefs/`, or `datastore/`.
   - Any archive entry whose relative path does not start with one of these three canonical prefixes SHALL be rejected with `SecurityException("Invalid backup entry outside whitelisted directories: " + entry.name)`.

3. **Resource Exhaustion & Zip Bomb Defense (`REQ-MIG-040.3`)**:
   - During streaming extraction, the system SHALL enforce: (a) maximum entry count of 10,000 entries, (b) maximum single-file uncompressed size of 512 MB, and (c) maximum total uncompressed size of 1,024 MB (1 GB).
   - If any limit is exceeded, extraction SHALL immediately terminate with `SecurityException("Archive exceeds maximum permitted size or entry limits")`.

4. **Remote Google Drive Filename Sanitization (`REQ-MIG-040.4`)**:
   - In `LegacyImportEngine.kt:413`, when downloading remote Google Drive files, the target file in `workerDir` SHALL be constructed using `File(workerDir, File(entry.name).name)`, stripping directory components, and verifying `tempFile.canonicalFile.toPath().startsWith(workerDir.canonicalFile.toPath())`.

5. **Fail-Safe Staging Cleanup on Exception (`REQ-MIG-040.5`)**:
   - In `performFullRestore`, `analyzeBackup`, and `performIncrementalImport`, if extraction fails or throws any `SecurityException` or `IOException`, all contents of the temporary staging directory SHALL be recursively deleted.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: Net-new requirement `REQ-MIG-040`, extending `REQ-MIG-004` (*Full Migration Mode*) and `REQ-MIG-036` (*Automated Background Backup WorkManager Scheduling*). Targets `MigrationEngine.kt`, `ImportEngine.kt`, `LegacyImportEngine.kt`, and `BackupImportSecurityTest.kt`.
2. **Historical Origin & Commit Trace**: Ticket `ATT-3982`, Sprint `2026-42.10`, discovered during independent code review of `feature/ATT-3921`.
3. **Root Reason for Existing Formulation**: Original archive unpacking in `MigrationEngine.kt` and `ImportEngine.kt` used standard `ZipInputStream` loops without canonical path validation, creating vulnerability CWE-22 (Zip Slip).
4. **Preservation of Core Invariants**: Valid `.attbackup` archives created by `BackupManager` continue to restore and import with 100% fidelity; temporary staging files cleaned up on failure; full restore and incremental import flows strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Zip-Slip Traversal Rejection in `MigrationEngine`)**:
  * *Given* an archive containing an entry with relative traversal path (e.g. `../../databases/evil.db`),
  * *When* `MigrationEngine.performFullRestore()` is invoked,
  * *Then* the operation fails immediately with `false`, no file outside `tempDir` is written, and temporary staging files are purged.
* **Criterion 2 (Zip-Slip Traversal Rejection in `ImportEngine.analyzeBackup`)**:
  * *Given* an archive with a relative traversal entry,
  * *When* `ImportEngine.analyzeBackup()` is invoked,
  * *Then* a `SecurityException` is thrown, zero files outside `tempDir` are written, and temporary files are cleaned up.
* **Criterion 3 (Zip-Slip Traversal Rejection in `ImportEngine.performIncrementalImport`)**:
  * *Given* an archive with a relative traversal entry,
  * *When* `ImportEngine.performIncrementalImport()` is invoked,
  * *Then* the import returns `0`, no file outside `tempDir` is written, and temporary staging files are deleted.
* **Criterion 4 (Directory Whitelist Enforcement)**:
  * *Given* an archive with entries outside `databases/`, `shared_prefs/`, or `datastore/` (e.g. `lib/libevil.so`),
  * *When* extracted,
  * *Then* unauthorized entries are rejected with `SecurityException`.
* **Criterion 5 (Zip Bomb Protection)**:
  * *Given* a zip archive exceeding 10,000 entries or uncompressing to > 1 GB of data,
  * *When* extraction is attempted,
  * *Then* extraction aborts immediately with `SecurityException`.
* **Criterion 6 (Google Drive File Name Sanitization)**:
  * *Given* a Google Drive file with entry name `../../malicious.fit`,
  * *When* downloaded by `LegacyImportEngine`,
  * *Then* the file is stored strictly as `workerDir/malicious.fit` without escaping `workerDir`.
* **Criterion 7 (Valid Archive Integrity & Compatibility)**:
  * *Given* a valid `.attbackup` created by `BackupManager`,
  * *When* imported or restored,
  * *Then* extraction succeeds cleanly with all databases and preferences placed in the staging directory.

---

## 2. Test Specification (`TST-MIG-037`)

### Test Case 1: Zip-Slip Path Traversal Rejection in `MigrationEngine` (`TST-MIG-037.1`)
* **Scope**: Security unit test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupImportSecurityTest.kt`
* **Preconditions**: Test zip archive crafted with an entry named `../../databases/evil.db`.
* **Action**: Invoke `MigrationEngine.performFullRestore(context, evilZip)`.
* **Expected Result**:
  - `performFullRestore` returns `false`.
  - Zero files are written outside `restore_temp`.
  - `restore_temp` is purged.

### Test Case 2: Zip-Slip Path Traversal Rejection in `ImportEngine.analyzeBackup` (`TST-MIG-037.2`)
* **Scope**: Security unit test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupImportSecurityTest.kt`
* **Preconditions**: Test zip archive crafted with an entry named `../../shared_prefs/secret.xml`.
* **Action**: Invoke `ImportEngine.analyzeBackup(context, evilZip)`.
* **Expected Result**:
  - `SecurityException` is thrown.
  - Zero files are created outside `import_analysis`.
  - Staging directory is cleaned up.

### Test Case 3: Zip-Slip Path Traversal Rejection in `ImportEngine.performIncrementalImport` (`TST-MIG-037.3`)
* **Scope**: Security unit test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupImportSecurityTest.kt`
* **Preconditions**: Test zip archive crafted with an entry named `../../evil.db`.
* **Action**: Invoke `ImportEngine.performIncrementalImport(context, evilZip, emptyMap(), emptyMap())`.
* **Expected Result**:
  - Function returns `0`.
  - No files are written outside `import_temp`.
  - Staging files are cleaned up.

### Test Case 4: Top-Level Directory Whitelist Enforcement (`TST-MIG-037.4`)
* **Scope**: Security unit test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupImportSecurityTest.kt`
* **Preconditions**: Test zip archive containing entries `lib/libevil.so` and `root_file.txt`.
* **Action**: Attempt extraction via `MigrationEngine` and `ImportEngine`.
* **Expected Result**:
  - Extraction rejects entries with `SecurityException`.
  - Unauthorized files are not extracted.

### Test Case 5: Zip Bomb & Resource Cap Protection (`TST-MIG-037.5`)
* **Scope**: Security unit test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupImportSecurityTest.kt`
* **Preconditions**: Test archive exceeding single file size or entry count thresholds.
* **Action**: Attempt extraction.
* **Expected Result**:
  - Extraction aborts immediately with `SecurityException`.

### Test Case 6: Remote Google Drive Filename Traversal Sanitization (`TST-MIG-037.6`)
* **Scope**: Security unit test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupImportSecurityTest.kt`
* **Preconditions**: Remote Google Drive entry with name `../../escaped.fit`.
* **Action**: Resolve target file path in worker directory.
* **Expected Result**:
  - Local target file is strictly `workerDir/escaped.fit`.
  - Canonical path is guaranteed within `workerDir`.

### Test Case 7: Valid Archive Integrity & Extraction Verification (`TST-MIG-037.7`)
* **Scope**: Integration test / Functional verification
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupImportSecurityTest.kt`
* **Preconditions**: Valid `.attbackup` archive created with `databases/WorkoutSummaries.db` and `shared_prefs/prefs.xml`.
* **Action**: Extract archive.
* **Expected Result**:
  - Extraction succeeds 100%.
  - Extracted files match source contents exactly.

### Test Case 8: Archive Symbolic Link Rejection Test (`TST-MIG-037.8`)
* **Scope**: Security unit test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupImportSecurityTest.kt`
* **Preconditions**: Test archive containing symbolic links or entries attempting to resolve symlinks (`Files.isSymbolicLink(path, LinkOption.NOFOLLOW_LINKS)`).
* **Action**: Attempt extraction via `SecureZipExtractor`.
* **Expected Result**:
  - Extraction aborts immediately with `SecurityException("Symbolic links are strictly prohibited in backup archives: ...")`.
  - Staging directory is cleaned up.

### Test Case 9: Clean-Room Full Suite Regression Execution (`TST-MIG-037.9`)
* **Scope**: Clean-room regression test
* **Action**: Run `./gradlew testDebugUnitTest`.
* **Expected Result**: 100% test pass rate across the full suite with 0 regressions.

---

## 3. Traceability Matrix

| Requirement | Test Spec | Scope | Test Target | Target Type |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-MIG-040.1` | `TST-MIG-037.1` | Security | `MigrationEngine.performFullRestore()` | Unit Test |
| `REQ-MIG-040.1` | `TST-MIG-037.2` | Security | `ImportEngine.analyzeBackup()` | Unit Test |
| `REQ-MIG-040.1` | `TST-MIG-037.3` | Security | `ImportEngine.performIncrementalImport()` | Unit Test |
| `REQ-MIG-040.3` | `TST-MIG-037.4` | Security | Whitelist validation (`databases/`, `shared_prefs/`, `datastore/`) | Unit Test |
| `REQ-MIG-040.4` | `TST-MIG-037.5` | Security / DoS | Zip bomb & resource limits (entries, size) | Unit Test |
| `REQ-MIG-040.5` | `TST-MIG-037.6` | Security | `LegacyImportEngine` Google Drive filename sanitization | Unit Test |
| `REQ-MIG-040.6` | `TST-MIG-037.7` | Functional | Valid `.attbackup` archive extraction & cleanup | Integration Test |
| `REQ-MIG-040.2` | `TST-MIG-037.8` | Security | Symbolic link rejection (`Files.isSymbolicLink`) | Unit Test |
| `REQ-MIG-040` | `TST-MIG-037.9` | Clean-Room | Full regression test suite (`testDebugUnitTest`) | Regression Suite |
