# Stage 2: Requirement & Test Specification - ATT-4011: Backup snapshots are inconsistent under concurrent writes and leak DB helpers

**Ticket**: [ATT-4011](https://rainerblind.atlassian.net/browse/ATT-4011)  
**Sub-task**: [ATT-4232](https://rainerblind.atlassian.net/browse/ATT-4232) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-DAT-031` (*Atomic Concurrent-Safe Database Backup Snapshots, Storage Verification, and Singleton Helper Lifecycle*)  
**Test Spec ID**: `TST-DAT-026` (*Atomic Concurrent-Safe Database Backup Snapshots, Storage Verification, and Singleton Helper Lifecycle Verification Suite*)  
**Branch**: `feature/ATT-4011`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Requirement Specification (REQ-DAT-031)

### 1.1 Problem Statement & Rationale
During backup bundle generation (`BackupManager.createBackup()`), the application checkpoints active SQLite databases using `DatabaseConnectionManager.quiesceAllDatabases(closeConnections = false)`. However:
1. `quiesceAllDatabases` dispatches `PRAGMA wal_checkpoint(TRUNCATE)` without verifying whether SQLite returned `BUSY` (`result == 1`). If background threads (such as `TrackerService` writing sensor telemetry or `ExportWorker` writing export status) hold an active read or write transaction, the checkpoint is aborted by SQLite without truncating the write-ahead log.
2. `BackupManager.zipFolder` then compresses `.db` files from `dataDir/databases` while strictly filtering out `-wal`, `-shm`, and `-journal` files. Any committed database transactions residing in WAL pages at that moment are permanently omitted from the `.attbackup` archive.
3. Even worse, if background writer threads or SQLite checkpoints write to the `.db` file while `FileInputStream` streams the file in 8KB blocks, the copied database file can suffer from torn page reads, producing a corrupted archive that fails `PRAGMA integrity_check` upon restore.
4. Furthermore, `DatabaseConnectionManager.getActiveDatabases` and several callers across the codebase (`LegacyImportEngine`, `WorkoutDeletionHelper`, `StravaUploader`, `TrackerService`, `StravaDataPurgeManager`) instantiate `StravaUploadDbHelper`, `EquipmentDbHelper`, and `ActiveDevicesDbHelper` via unmanaged ad-hoc constructors without closing them. Every invocation of `quiesceAllDatabases` or `checkpointAll` leaks open SQLite connection pools, handles, and file descriptors.

### 1.2 Functional & Architectural Requirements

1. **Storage Space Pre-Verification (`REQ-DAT-031.1`)**:
   - Before snapshotting databases or initiating backup bundling, `BackupManager` SHALL calculate the sum of sizes of all active `.db` and `-wal` files.
   - `BackupManager` SHALL assert that available filesystem storage in `context.filesDir.usableSpace` is at least 1.5 times the total database size (`totalDbSize * 1.5`).
   - If usable space is insufficient, `createBackup()` SHALL log a warning, notify `ProgressListener` of the error, and abort cleanly returning `null` without partially allocating disk space or exhausting device storage.

2. **API 30+ Primary Snapshot Pipeline via `VACUUM INTO` (`REQ-DAT-031.2`)**:
   - On Android 11+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.R`), for each active database registered in `DatabaseConnectionManager`, `BackupManager` SHALL create an atomic standalone snapshot into an isolated temporary staging directory (`backups/temp_db_snapshot/`) using the SQLite statement `VACUUM INTO '<snapshot_file_path>'`.
   - `VACUUM INTO` SHALL execute concurrently without holding exclusive application writer locks, guaranteeing that concurrent writer threads (`TrackerService`, `ExportWorker`) are not blocked.
   - The produced snapshot file SHALL be an atomic, self-contained, defragmented, non-WAL SQLite database incorporating all committed transactions up to the snapshot instant.

3. **API 26–29 Fallback Snapshot Pipeline & Checkpoint Retry (`REQ-DAT-031.3`)**:
   - On Android 8.0–10 (`Build.VERSION.SDK_INT < Build.VERSION_CODES.R`) or when storage is insufficient for `VACUUM INTO`, `BackupManager` SHALL execute a writer-synchronized checkpoint and copy pipeline:
     - Acquire `DatabaseConnectionManager.databaseLock` to prevent new write transactions from starting.
     - Execute `PRAGMA wal_checkpoint(FULL)` on all active databases. If `BUSY` (`result == 1`) is returned, the checkpoint SHALL retry up to 3 times with exponential backoff (10ms, 25ms, 50ms).
     - Copy the clean `.db` files and any non-empty `-wal` files into the temporary snapshot staging directory.
     - Release `DatabaseConnectionManager.databaseLock`.

4. **Thread-Safe Singleton Helper Lifecycle & Zero Leakage (`REQ-DAT-031.4`)**:
   - `StravaUploadDbHelper`, `EquipmentDbHelper`, and `ActiveDevicesDbHelper` SHALL provide a thread-safe singleton accessor `getInstance(Context context)` using double-checked locking and binding to `context.getApplicationContext()`.
   - All ad-hoc constructor instantiations across the codebase (`DatabaseConnectionManager.getActiveDatabases`, `LegacyImportEngine`, `WorkoutDeletionHelper`, `StravaUploader`, `TrackerService`, `StravaDataPurgeManager`) SHALL be eliminated and replaced with `getInstance(context)`.
   - `DatabaseConnectionManager.getActiveDatabases` SHALL exclusively reference the singleton instances.
   - Database helper singletons SHALL expose `@VisibleForTesting fun resetForTesting()` to ensure clean test isolation across unit test suites.
   - Zero unmanaged or unclosed `SQLiteOpenHelper` instances SHALL be leaked during application lifecycle or backup execution.

5. **Snapshot Integrity Verification, Atomic Packaging & Cleanup (`REQ-DAT-031.5`)**:
   - Before packaging, `BackupManager` SHALL verify each snapshot file in the staging directory using `PRAGMA integrity_check`, ensuring no corrupted or torn database files enter the backup archive.
   - `BackupManager` SHALL archive the verified snapshot directory as the `databases/` tree inside `aTrainingTracker_backup.attbackup.tmp` alongside `shared_prefs` and `datastore`.
   - The temporary backup file SHALL be atomically renamed to canonical `aTrainingTracker_backup.attbackup` using `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`.
   - The temporary staging directory (`temp_db_snapshot/`) SHALL be strictly purged in a `finally` block on all execution paths (success, failure, or cancellation).

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement `REQ-DAT-031`, extending `REQ-MIG-040` (`ATT-3982`), `REQ-MIG-041` (`ATT-3989`), and `REQ-MIG-043` (`ATT-3992`). Targets `BackupManager.kt`, `DatabaseConnectionManager.kt`, `StravaUploadDbHelper.java`, `EquipmentDbHelper.java`, and `ActiveDevicesDbHelper.java`.
2. **Historical Origin & Commit Trace**: Ticket `ATT-4011`, Sprint `2026-42.10`, discovered during independent whole-app architecture review of `feature/ATT-3921` (`0234bfc0`). `BackupManager.kt` was originally authored to zip the live `databases/` directory while excluding `-wal` files. Commit `f320b92b` (`ATT-3989`) introduced `quiesceAllDatabases`, but ignored `wal_checkpoint` return codes.
3. **Root Reason for Existing Formulation**: Direct zipping was written before WAL mode was pervasive across all databases. Filtering `-wal` files was intended to prevent copying ephemeral lock files (`-shm`), but did not account for uncheckpointed WAL frames or tearing from concurrent writers. `StravaUploadDbHelper`, `EquipmentDbHelper`, and `ActiveDevicesDbHelper` lacked singletons because early versions only accessed them infrequently from single threads.
4. **Preservation of Core Invariants**: The `.attbackup` archive structure (`databases/`, `shared_prefs/`, `datastore/`) remains 100% backward and forward compatible. Full restore (`RestoreManager`) and incremental import (`AttachDatabaseHelper`) operate unchanged. Passive `onOpen()` behavior and 100% clean-room test pass rate are strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Storage Pre-Verification)**:
  * *Given* available disk space in `filesDir` less than 1.5x total active database size,
  * *When* `BackupManager.createBackup(context)` is called,
  * *Then* backup generation aborts immediately, logs a warning, returns `null`, and allocates zero staging files.

* **Criterion 2 (API 30+ VACUUM INTO Atomicity Under Concurrent Writes)**:
  * *Given* Android 11+ (`Build.VERSION.SDK_INT >= 30`) and active concurrent write transactions in progress on `WorkoutSamples.db`,
  * *When* `createBackup(context)` executes,
  * *Then* databases are snapshotted via SQLite `VACUUM INTO` into temporary staging,
  * *And* the snapshot passes `PRAGMA integrity_check` with `"ok"`, contains all committed records, and zero writer threads are blocked.

* **Criterion 3 (API 26–29 Fallback Checkpoint & Retry)**:
  * *Given* Android 8.0–10 (`SDK_INT < 30`) and a database returning `BUSY` on the initial `wal_checkpoint(FULL)`,
  * *When* `createBackup(context)` executes,
  * *Then* it acquires `databaseLock`, retries the checkpoint with backoff, copies clean database files to staging, and releases `databaseLock`.

* **Criterion 4 (Singleton Helper Lifecycle & Zero Leakage)**:
  * *Given* multiple components accessing `StravaUploadDbHelper`, `EquipmentDbHelper`, and `ActiveDevicesDbHelper`,
  * *When* calling `getInstance(context)` repeatedly across threads,
  * *Then* exactly one shared instance is returned per helper class,
  * *And* `DatabaseConnectionManager.getActiveDatabases(context)` creates zero new helper instances, eliminating SQLite connection leaks.

* **Criterion 5 (Integrity Verification & Staging Cleanup)**:
  * *Given* a completed or aborted backup run,
  * *When* `createBackup()` returns,
  * *Then* `temp_db_snapshot/` is completely purged from disk on all execution paths.

### 1.5 System Invariants

1. **Archive Compatibility**: `.attbackup` format and internal layout remain 100% compatible with existing backup/restore logic.
2. **MinSdk 26 Compatibility**: System operates safely on API 26 through 35+.
3. **Passive `onOpen()`**: Database helpers perform zero mutations in `onOpen()`.
4. **Single Atomic Commit per Ticket (Rule 44)**.
5. **100% Clean-Room Test Pass Rate**.

---

## 2. Test Specification (TST-DAT-026)

### Test Case 1: Storage Space Verification & Guard Test (`TST-DAT-026.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupManagerTest.kt`
* **Preconditions**: Mock context with `filesDir.usableSpace` configured to return less than 1.5x active database sizes.
* **Action**: Invoke `BackupManager.createBackup(context)`.
* **Expected Result**:
  - `createBackup()` returns `null`.
  - Zero staging files or `.tmp` backup files remain on disk.

### Test Case 2: API 30+ VACUUM INTO Real SQLite Snapshot Test (`TST-DAT-026.2`)
* **Scope**: Integration Test (Real SQLite Robolectric)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupManagerRealSqliteTest.kt`
* **Preconditions**: Real SQLite database created with WAL mode enabled and pending uncheckpointed write transactions.
* **Action**: Execute snapshot pipeline via `VACUUM INTO`.
* **Expected Result**:
  - Output snapshot `.db` file is generated without `-wal` companion.
  - Snapshot passes `PRAGMA integrity_check` returning `"ok"`.
  - All committed data rows are present in the snapshot.

### Test Case 3: API 26–29 Fallback Checkpoint & Retry on BUSY Test (`TST-DAT-026.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupManagerTest.kt`
* **Preconditions**: Database configured to return `BUSY` (`1`) on first checkpoint call, then `0` on retry.
* **Action**: Execute fallback checkpoint logic.
* **Expected Result**:
  - Checkpoint retries with exponential backoff and succeeds.
  - Staging files are successfully captured.

### Test Case 4: Concurrent Writer Non-Interference & Integrity Test (`TST-DAT-026.4`)
* **Scope**: Integration Test (Robolectric / Multi-Threaded)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupManagerConcurrencyTest.kt`
* **Preconditions**: Real SQLite database backing `WorkoutSamples.db`. Background coroutine writing 100 samples per second.
* **Action**: Execute `BackupManager.createBackup(context)` while background writer is actively writing.
* **Expected Result**:
  - Background writer completes without `SQLiteDatabaseLockedException`.
  - Backup archive is produced successfully.
  - Extracted database passes `PRAGMA integrity_check`.

### Test Case 5: Singleton Helper Instance Identity & Reset Test (`TST-DAT-026.5`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/DbHelperSingletonTest.kt`
* **Preconditions**: Context available.
* **Action**:
  - Query `StravaUploadDbHelper.getInstance(context)` multiple times.
  - Query `EquipmentDbHelper.getInstance(context)` multiple times.
  - Query `ActiveDevicesDbHelper.getInstance(context)` multiple times.
  - Invoke `resetForTesting()` and verify clean instance recreation.
* **Expected Result**:
  - Repeated calls return the identical reference (`assertSame`).
  - Calls across threads return identical reference.
  - After `resetForTesting()`, new instance is created.

### Test Case 6: Zero Leaked Helpers in DatabaseConnectionManager Test (`TST-DAT-026.6`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/DatabaseConnectionManagerTest.kt`
* **Preconditions**: Track helper allocations before and after calling `quiesceAllDatabases(context, closeConnections = false)`.
* **Action**: Invoke `checkpointAll(context)` or `quiesceAllDatabases(context, closeConnections = false)`.
* **Expected Result**:
  - Zero new `SQLiteOpenHelper` instances instantiated.
  - Singletons reused without resource leakage.

### Test Case 7: 9-Language Localization & Specifier Audit (`TST-DAT-026.7`)
* **Scope**: Localization Parity Test
* **Goal**: Confirm zero string resource changes or additions.
* **Expected Result**: 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Test Case 8: Clean-Room Full Suite Regression Execution (`TST-DAT-026.8`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across all project unit tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-DAT-026.1` | Unit | `BackupManager.createBackup` | `REQ-DAT-031.1` | Specified |
| `TST-DAT-026.2` | Integration (Real SQLite) | `BackupManager.snapshotDatabases` | `REQ-DAT-031.2`, `REQ-DAT-031.5` | Specified |
| `TST-DAT-026.3` | Unit (Mock) | `BackupManager.checkpointWithRetry` | `REQ-DAT-031.3` | Specified |
| `TST-DAT-026.4` | Integration (Concurrent) | `BackupManager.createBackup` | `REQ-DAT-031.2`, `REQ-DAT-031.5` | Specified |
| `TST-DAT-026.5` | Unit | `*DbHelper.getInstance` | `REQ-DAT-031.4` | Specified |
| `TST-DAT-026.6` | Unit | `DatabaseConnectionManager.quiesceAllDatabases` | `REQ-DAT-031.4` | Specified |
| `TST-DAT-026.7` | Localization | Git diff audit of `res/values*/strings.xml` | `REQ-DAT-031` | Specified |
| `TST-DAT-026.8` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
