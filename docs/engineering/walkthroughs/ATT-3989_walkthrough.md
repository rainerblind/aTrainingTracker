# Stage 5 Walkthrough: ATT-3989 - Full restore deletes current data before validating the backup

**Ticket**: [ATT-3989](https://atrainingtracker.atlassian.net/browse/ATT-3989)  
**Sub-task**: [ATT-4160](https://atrainingtracker.atlassian.net/browse/ATT-4160) (`[Test]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-MIG-041` (*Pre-Restore Backup Validation, Schema Version Bounds, Connection Teardown, and Atomic Rollback Snapshot*)  
**Test Mapping**: `TST-MIG-038` (`TST-MIG-038.1` – `TST-MIG-038.6`)  
**Branch**: `feature/ATT-3989`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Executive Summary & Root Cause Resolution

During code review of `feature/ATT-3921`, a `High` severity data loss bug was discovered in `MigrationEngine.performFullRestore()`. The previous implementation deleted `databases/`, `shared_prefs/`, and `datastore/` *prior* to checking whether the extracted backup contained valid database files or uncorrupted SQLite headers. If a user attempted to restore a corrupted archive, an archive missing database files, or an archive with forward-incompatible database schema versions, all local workouts, settings, and equipment were irrevocably wiped, leaving the app in an unrecoverable, empty state, or entering an infinite `SQLiteDowngradeException` crash loop on cold start.

This defect has been completely resolved through:
1. **Pre-Restore Structure & SQLite Integrity Validation (`REQ-MIG-041.1`, `REQ-MIG-041.2`)**:
   - The archive is unpacked into partition-safe staging (`restore_staging`).
   - Verifies `databases/` exists and contains core database files (`WorkoutSummaries.db`).
   - Executes `PRAGMA integrity_check;` on all unpacked SQLite files, rejecting any file with B-tree corruption or index errors.
   - Enforces schema version bounds via `DatabaseSchemaRegistry.kt` across all 15 local SQLite databases, rejecting forward-version archives.
2. **Safe SQLite Connection Quiescence (`REQ-MIG-041.3`)**:
   - Centralized connection quiescence in `DatabaseConnectionManager.kt`.
   - Flushes WAL files to disk via `PRAGMA wal_checkpoint(TRUNCATE);` and closes open handles prior to file replacement, avoiding database locks.
3. **Partition-Safe Rollback Snapshot & Atomic Directory Swap (`REQ-MIG-041.4`)**:
   - Staged snapshot created in `dataDir/backup_rollback` (on the same physical filesystem partition as live data).
   - Validates that the rollback snapshot is non-empty before modifying live state.
   - Swaps directories using atomic rename into staging trash (`databases_staging_trash`), with automatic immediate recovery if any step fails.
   - Restores from `backup_rollback` via `executeEmergencyRollback()` if an unrecoverable error occurs.
4. **Crash-Resilient Transaction Journaling (`REQ-MIG-041.5`)**:
   - `RestoreJournalManager.kt` persists journal phases (`IDLE`, `STAGED`, `SWAPPING`, `COMMITTED`) in `dataDir/restore_journal.state`.
   - Wired `RestoreJournalManager.recoverInterruptedRestoreIfNecessary(this)` in `TrainingApplication.java:onCreate()`. If the process is killed midway through a restore, the original state is automatically restored on next startup.
5. **9-Language Localization Parity**:
   - Localized error messages (`backup_restore_error_missing_databases`, `backup_restore_error_integrity_failed`, `backup_restore_error_schema_downgrade`) across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 2. Architectural Hardening (SWE.2 / SWE.3)

### 2.1 `DatabaseConnectionManager.kt`
- Implemented `quiesceAllDatabases(context: Context, closeConnections: Boolean = true)`:
  - Iterates over all 15 active SQLite databases (`WorkoutSummaries`, `WorkoutSamples`, `Laps`, `Routes`, `WorkoutCluster`, `KnownLocations`, `SportTypeEquipmentLink`, `TrackingViews`, `Devices`, `SportType`, `StravaUpload`, `ExportStatus`, `Segments`, `Equipment`, `ActiveDevices`).
  - Executes `PRAGMA wal_checkpoint(TRUNCATE);` to truncate WAL logs to 0 bytes.
  - Closes open connections to prevent file descriptor locks during replacement.
- `BackupManager.checkpointAndCloseAllDatabases` delegates cleanly with `closeConnections = false` to preserve backup creation invariants.

### 2.2 `DatabaseSchemaRegistry.kt`
- Maps all 15 local SQLite database filenames to their maximum supported schema versions (`WorkoutSummaries.db: 25`, `Routes.db: 10`, `TrackingViews.db: 14`, etc.).
- Evaluates `isVersionSupported(dbName, userVersion)` to detect and reject forward-incompatible databases before installation.

### 2.3 `RestoreJournalManager.kt`
- Manages transactional states: `IDLE`, `STAGED`, `SWAPPING`, `COMMITTED`.
- Persists state in `dataDir/restore_journal.state` with legacy cache fallback.
- Detects interrupted restores on cold boot in `TrainingApplication.java:onCreate()` and automatically invokes `MigrationEngine.executeEmergencyRollback(context)`.

### 2.4 `MigrationEngine.kt`
- Refactored `performFullRestore(context, backupFile, listener)`:
  - Phase 1: Unpack into `dataDir/restore_staging`.
  - Phase 2: Validate archive structure, execute `PRAGMA integrity_check;`, verify schema versions via `DatabaseSchemaRegistry`.
  - Phase 3: Quiesce and close SQLite connections via `DatabaseConnectionManager.quiesceAllDatabases`.
  - Phase 4: Create verified rollback snapshot in `dataDir/backup_rollback` and record `STAGED`.
  - Phase 5: Record `SWAPPING`, perform atomic directory swap via `staging_trash` folders.
  - Phase 6: Record `COMMITTED`, purge rollback and staging folders, and trigger app restart.
- Implemented `executeEmergencyRollback(context)` with validation to guarantee live data is never wiped if the rollback snapshot is missing or empty.

### 2.5 `SecureZipExtractor.kt`
- Expanded `purgeStaleStagingDirs(context)` to clean `restore_temp`, `restore_staging`, `backup_rollback`, and staging trash across `cacheDir` and `dataDir`.

### 2.6 `TrainingApplication.java`
- Wired `RestoreJournalManager.recoverInterruptedRestoreIfNecessary(this)` into `onCreate()` prior to database singleton initialization.

---

## 3. Verification & Clean-Room Regression

### 3.1 Targeted Unit Test Suite (`FullRestoreValidationSecurityTest.kt`)
* Command: `./gradlew testDebugUnitTest --tests "*FullRestoreValidationSecurityTest*"`
* Result: **8 passed, 0 failed, 0 skipped, 100% pass rate**
* Test cases:
  1. `testPerformFullRestore_rejectsArchiveMissingDatabasesDirectory`: PASSED (TST-MIG-038.1)
  2. `testPerformFullRestore_rejectsCorruptDatabaseIntegrityCheckFailed`: PASSED (TST-MIG-038.2)
  3. `testPerformFullRestore_rejectsForwardSchemaVersionDowngrade`: PASSED (TST-MIG-038.3)
  4. `testPerformFullRestore_recoversFromInterruptedRestoreViaEmergencyRollback`: PASSED (TST-MIG-038.4)
  5. `testPerformFullRestore_succeedsWithValidBackup`: PASSED (TST-MIG-038.5)
  6. `testRestoreJournalManager_recoversInterruptedRestoreOnStartup`: PASSED (TST-MIG-038.6)
  7. `testValidateBackupArchive_returnsLocalizedErrorStringResourceIds`: PASSED
  8. `testExecuteEmergencyRollback_abortsIfRollbackSnapshotMissingOrEmpty`: PASSED

### 3.2 Full Clean-Room Regression Test Suite
* Command: `./gradlew testDebugUnitTest`
* Result: **100% clean-room test pass rate, 0 failures across entire project**

---

## 4. Traceability Matrix

| Requirement | Test ID | Verification Scope | Status |
| :--- | :--- | :--- | :--- |
| `REQ-MIG-041.1` | `TST-MIG-038.1` | Archives missing databases/ directory rejected without touching local state | Verified |
| `REQ-MIG-041.2` | `TST-MIG-038.2` | Corrupted SQLite files failing `PRAGMA integrity_check` rejected | Verified |
| `REQ-MIG-041.2` | `TST-MIG-038.3` | Backups with forward schema versions exceeding supported limits rejected | Verified |
| `REQ-MIG-041.3` | `TST-MIG-038.4`, `TST-MIG-038.5` | SQLite connections checkpointed and closed prior to swap | Verified |
| `REQ-MIG-041.4` | `TST-MIG-038.4` | Rollback snapshot reverts original state on failure or crash | Verified |
| `REQ-MIG-041.5` | `TST-MIG-038.6` | Crash during swap recovered on next cold boot via RestoreJournalManager | Verified |
