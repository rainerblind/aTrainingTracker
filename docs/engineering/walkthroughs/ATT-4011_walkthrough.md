# Stage 5: Walkthrough & Verification - ATT-4011: Backup snapshots are inconsistent under concurrent writes and leak DB helpers

**Ticket**: [ATT-4011](https://rainerblind.atlassian.net/browse/ATT-4011)  
**Sub-task**: [ATT-4235](https://rainerblind.atlassian.net/browse/ATT-4235) (`[Test]`)  
**Parent Epic**: None  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-DAT-031` (*Atomic Concurrent-Safe Database Backup Snapshots, Storage Verification, and Singleton Helper Lifecycle Management*)  
**Test Mapping**: `TST-DAT-026` (*Atomic Concurrent-Safe Database Backup Snapshots, Storage Verification, and Singleton Helper Lifecycle Verification Suite*)  
**Branch**: `feature/ATT-4011`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Executive Summary & Verification Overview

During an architectural audit of SQLite database backup snapshotting and connection management (`BackupManager.kt`, `DatabaseConnectionManager.kt`), several high-severity data consistency and connection leakage issues were uncovered under active concurrent write workloads:

1. **Inconsistent Live File Copying & Torn Reads Under Active Writes**:
   - `BackupManager` created backups by directly zipping the live `databases/` directory while excluding `-wal` files.
   - Any transactions residing in write-ahead log frames were omitted from the backup archive.
   - For active tracking sessions (`TrackerService` writing to `WorkoutSamples.db`), direct copying of live `.db` files resulted in torn page reads, leading to SQLite corruption on restore.

2. **Database Helper Instance & Connection Pool Leakage**:
   - `StravaUploadDbHelper`, `EquipmentDbHelper`, and `ActiveDevicesDbHelper` lacked singleton accessors.
   - Callers across repositories, services, view models, and `DatabaseConnectionManager.getActiveDatabases()` performed ad-hoc `new Helper(context)` instantiations, leaking SQLite connection pools, internal threads, and file descriptors.

3. **Storage Exhaustion Without Pre-Verification**:
   - Backup creation performed file copying without checking available filesystem storage.
   - On low-storage devices, creating a backup could exhaust device storage mid-copy, corrupting the archive and leaving orphaned staging files on disk.

4. **Missing Snapshot Integrity Verification**:
   - Staged snapshot files were compressed into `.attbackup` without validating their internal SQLite page integrity via `PRAGMA integrity_check`.

### Complete Resolution Architecture

- **Storage Space Pre-Verification (`REQ-DAT-031.1`)**: Before staging or snapshotting databases, `BackupManager` calculates total database size (`calculateTotalDatabaseSize(context)`) and validates `usableSpace >= totalDbSize * 1.5`. If space is insufficient, it aborts cleanly returning `null` with a warning, allocating zero staging files.
- **API 30+ Primary Pipeline via SQLite `VACUUM INTO` (`REQ-DAT-031.2`)**: For Android 11+ (`SDK_INT >= 30`), `BackupManager` executes `VACUUM INTO '<snapshot_file_path>'` across all open SQLite databases registered in `DatabaseConnectionManager`. This generates a defragmented, self-contained, standalone SQLite snapshot incorporating all committed WAL transactions without blocking concurrent writer threads.
- **API 26–29 Fallback Checkpoint & Retry Pipeline (`REQ-DAT-031.3`)**: For Android 8.0–10 (`SDK_INT < 30`), `BackupManager` coordinates with `DatabaseConnectionManager.databaseLock` (a `ReentrantReadWriteLock`) and executes `checkpointWithRetry` handling `SQLITE_BUSY` with exponential backoff (10ms, 25ms, 50ms) to ensure clean WAL checkpointing before file copying.
- **Thread-Safe Singleton Helper Lifecycle & Test Isolation (`REQ-DAT-031.4`)**: Replaced 100% of ad-hoc helper instantiations across 17 files with thread-safe `getInstance(context)` accessors on `StravaUploadDbHelper`, `EquipmentDbHelper`, and `ActiveDevicesDbHelper`, backed by `@VisibleForTesting fun resetForTesting(...)` to ensure strict test isolation.
- **Snapshot Integrity Verification & Staging Cleanup (`REQ-DAT-031.5`)**: Verified all staged database snapshots using `PRAGMA integrity_check` before archive packaging. Enforced staging directory (`temp_db_snapshot/`) purge in a `finally` block on all execution paths.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-DAT-031.1` (Storage Pre-Verification) | `TST-DAT-026.1` | `BackupManagerTest.kt` | **PASSED** (100%) | `Verified` |
| `REQ-DAT-031.2` (API 30+ VACUUM INTO) | `TST-DAT-026.2` | `BackupManagerRealSqliteTest.kt` | **PASSED** (100%) | `Verified` |
| `REQ-DAT-031.3` (API 26-29 Checkpoint & Retry) | `TST-DAT-026.3` | `BackupManagerTest.kt` | **PASSED** (100%) | `Verified` |
| `REQ-DAT-031.2` / `REQ-DAT-031.5` (Concurrency Non-Interference) | `TST-DAT-026.4` | `BackupManagerConcurrencyTest.kt` | **PASSED** (100%) | `Verified` |
| `REQ-DAT-031.4` (Singleton Identity & Lifecycle) | `TST-DAT-026.5` | `DbHelperSingletonTest.kt` | **PASSED** (100%) | `Verified` |
| `REQ-DAT-031.4` (Zero Connection Leaks in Manager) | `TST-DAT-026.6` | `DatabaseConnectionManagerTest.kt` | **PASSED** (100%) | `Verified` |
| `REQ-DAT-031` (Zero Localization / String Changes) | `TST-DAT-026.7` | Git diff audit of `res/values*/strings.xml` | **PASSED** (0 strings) | `Verified` |
| `REQ-PRO-001` (Clean-Room Full Test Suite) | `TST-DAT-026.8` | `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Integration Test Suites (5/5 Suites Passed in 22s)
```bash
./gradlew testDebugUnitTest --tests "*DbHelperSingletonTest*" \
                            --tests "*DatabaseConnectionManagerTest*" \
                            --tests "*BackupManagerTest*" \
                            --tests "*BackupManagerRealSqliteTest*" \
                            --tests "*BackupManagerConcurrencyTest*"
```
- `DbHelperSingletonTest`: Verified identical reference identity across concurrent calls for `StravaUploadDbHelper`, `EquipmentDbHelper`, and `ActiveDevicesDbHelper`, as well as clean instance reset via `resetForTesting()`.
- `DatabaseConnectionManagerTest`: Verified `databaseLock` synchronization, retry backoff on `BUSY` checkpoint responses, and helper singleton identity.
- `BackupManagerTest`: Verified `calculateTotalDatabaseSize`, insufficient storage pre-flight abort returning `null`, and zero-byte file skip handling.
- `BackupManagerRealSqliteTest`: Verified `VACUUM INTO` creates an authentic, standalone, defragmented SQLite database incorporating uncheckpointed WAL rows, and verified corruption detection via `PRAGMA integrity_check`.
- `BackupManagerConcurrencyTest`: Verified `VACUUM INTO` snapshotting executes concurrently with active background SQLite write transactions without deadlocks, SQLite lock errors, or page corruption.

### Clean-Room Regression Test Suite (100% Passed in 3m 14s)
```bash
./gradlew testDebugUnitTest
```
- Total Tests: **3,361 completed**
- Failures: **0**
- Errors: **0**
- Result: **BUILD SUCCESSFUL** with 100% pass rate across the entire test suite.

---

## 4. UI Consistency & Visual Verification (Rule 23)

- **UI Consistency**: **N/A**
  - This change is an internal database snapshot, backup pipeline, and SQLite helper connection pool hardening.
  - Zero UI composables, layouts, styles, themes, or user-facing strings were added or modified.

---

## 5. Invariant & Governance Verification

1. **Snapshot Consistency Under Concurrent Writes**: Snapshots generated via `VACUUM INTO` or writer-synchronized checkpointing incorporate all committed transactions with zero torn reads.
2. **Zero Connection / Descriptor Leaks**: 100% of helper access across repositories, view models, and services routes through singletons.
3. **Storage Pre-Verification**: Device storage is guarded against out-of-disk failures; insufficient storage cleanly aborts returning `null`.
4. **Living Documentation Synchronized**: Status for `REQ-DAT-031` and `TST-DAT-026` updated to `Verified` in `docs/requirements.md` and `docs/tests.md`.
5. **Single Atomic Commit per Ticket (Rule 44)**: All deliverables (Stage 1 analysis, Stage 2 test spec, Stage 3 plan, Stage 4 implementation and tests, Stage 5 walkthrough, and living docs) integrated into `sprint/2026-42.10` via a single atomic commit.
