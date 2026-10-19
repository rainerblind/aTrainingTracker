# Stage 2: Requirement & Test Specification: ATT-3992

**Ticket**: [ATT-3992](https://atrainingtracker.atlassian.net/browse/ATT-3992)  
**Subtask**: `ATT-4172` (`[Test-Spec]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-MIG-043` (*Atomic Cross-Database Import via SQLite ATTACH, Dedicated Scoped Connection, Shared ReentrantLock, Non-Destructive Orphan Repair, insertOrThrow Enforcement & Cooperative Coroutine Responsiveness*)  
**Test Case Mapping**: `TST-MIG-040`  
**Branch**: `feature/ATT-3992`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. System Requirements & Functional Specifications

### 1.1 Technical Constraints (MUST / MUST NOT)
1. **Dedicated Connection Lifecycle & Absolute Paths**:
   - `ImportEngine` MUST NOT use the global application singleton connection for `ATTACH DATABASE`. It MUST open a dedicated, scoped `SQLiteDatabase` connection (`dedicatedSummariesDb`) strictly for the duration of the multi-database import.
   - All database paths passed to `ATTACH DATABASE` MUST be validated as local canonical absolute paths (`file.canonicalPath`).
2. **Shared Concurrency Serialization Lock**:
   - The system MUST enforce a dual JVM `ReentrantLock` and OS-level `FileLock` (`BackupManager.acquireLock(context)`) across `ImportEngine`, `LegacyImportEngine`, and `BackupManager` to serialize access to `WorkoutSummaries.db`, `Laps.db`, and `WorkoutSamples.db` during the `ATTACH` window, eliminating WAL lock contention and `SQLiteDatabaseLockedException`.
3. **Prohibition of Silent Failures (`insertOrThrow`)**:
   - `ImportEngine` and `LegacyImportEngine` MUST NOT use `db.insert()`. All insertions MUST use `db.insertOrThrow()`.
   - `newId` MUST be asserted strictly positive (`require(newId > 0)`). Downstream copy operations MUST NOT execute if summary insertion returns non-positive.
4. **Prohibition of Destructive Automated Startup Drops**:
   - The system MUST NOT execute automated, silent table drops or row deletions on startup. Orphan detection MUST be non-destructive and diagnostic.
5. **Robust Detach in Finally**:
   - The dedicated connection MUST execute `DETACH DATABASE` for all attached aliases inside an outer `finally` block wrapped in independent `try/catch` handlers, and close the dedicated connection cleanly even if `ATTACH`, `beginTransaction()`, or import operations throw an exception.
6. **Cooperative Coroutine Compliance**:
   - Worker loops MUST NOT swallow `CancellationException`. They MUST rethrow `CancellationException` immediately.
   - Worker loops and retry helpers MUST NOT call `Thread.sleep()`. All backoffs MUST use suspending `delay()`.
   - Worker loops MUST invoke `coroutineContext.ensureActive()` at the start of each import iteration.

### 1.2 Functional Behavior (SHALL)
1. **Single Unified SQLite ACID Transaction**:
   - For each imported workout, `dedicatedSummariesDb.beginTransaction()` SHALL coordinate mutations across `main` (`tblWorkoutSummaries`, `extrema_values`, `accumulated_sensors`), `lapsDb` (`tblLaps`), and `samplesDb` (`samplesDb.[tableName]`).
   - If any step fails or coroutine cancellation occurs, `dedicatedSummariesDb.endTransaction()` SHALL atomically abort and roll back all mutations across all three databases simultaneously.
2. **Static Schema Mapping & Missing NOT NULL Column Defaults**:
   - Columns from source backup rows SHALL be filtered against static column whitelists for each target table. Unrecognized columns SHALL be discarded.
   - If a target column has a `NOT NULL` constraint and is absent in the source record, it SHALL be populated using the formal transformation policy defaults:
     | Table | Target Column | Type | Default Value |
     |---|---|---|---|
     | `TABLE_EXTREMA_VALUES` | `WORKOUT_ID` | `INTEGER` | `newId` |
     | `TABLE_EXTREMA_VALUES` | `SENSOR_TYPE` | `INTEGER` | `0` |
     | `TABLE_EXTREMA_VALUES` | `MIN_VALUE` | `REAL` | `0.0` |
     | `TABLE_EXTREMA_VALUES` | `MAX_VALUE` | `REAL` | `0.0` |
     | `TABLE_EXTREMA_VALUES` | `AVG_VALUE` | `REAL` | `0.0` |
     | `TABLE_ACCUMULATED_SENSORS` | `WORKOUT_ID` | `INTEGER` | `newId` |
     | `TABLE_ACCUMULATED_SENSORS` | `SENSOR_TYPE` | `INTEGER` | `0` |
     | `TABLE_ACCUMULATED_SENSORS` | `VALUE` | `REAL` | `0.0` |
     | `Laps.TABLE` (`tblLaps`) | `WORKOUT_ID` | `INTEGER` | `newId` |
     | `Laps.TABLE` (`tblLaps`) | `LAP_NUMBER` | `INTEGER` | `1` |
     | `Laps.TABLE` (`tblLaps`) | `START_TIME` | `INTEGER` | `0L` |
     | `Laps.TABLE` (`tblLaps`) | `DURATION` | `INTEGER` | `0L` |
     | `Laps.TABLE` (`tblLaps`) | `DISTANCE` | `REAL` | `0.0` |
3. **Legacy Import Pipeline Inversion**:
   - `LegacyImportEngine.saveParsedWorkout` SHALL validate all preconditions (specifically `firstTime != null`) before creating any tables or inserting rows.
   - Summary insertion with `insertOrThrow` SHALL precede samples table creation and population inside a synchronized transaction block.

---

## 2. Acceptance Criteria (Given-When-Then)

### Acceptance Criterion 1: Atomic Cross-Database Rollback
- **Given** an incremental backup restore session with attached `lapsDb` and `samplesDb`,
- **When** an error occurs during lap copying or sample table creation/population,
- **Then** `dedicatedSummariesDb.endTransaction()` rolls back the entire transaction, and zero rows remain in `tblWorkoutSummaries`, zero rows remain in `extrema_values` or `accumulated_sensors`, zero rows remain in `tblLaps`, and zero tables remain in `WorkoutSamples.db`.

### Acceptance Criterion 2: Partial Failure during ATTACH Resilience
- **Given** an invalid or missing path during `ATTACH DATABASE`,
- **When** `dedicatedSummariesDb.execSQL("ATTACH ...")` throws `SQLiteException`,
- **Then** the outer `finally` block executes safe detach and closes the dedicated connection without leaking open file handles or dangling transactions.

### Acceptance Criterion 3: Concurrency Serialization
- **Given** a background backup archive task running via `BackupManager`,
- **When** an incremental import is initiated concurrently,
- **Then** `BackupManager.acquireLock(context)` cleanly serializes execution across threads and processes, preventing `SQLiteDatabaseLockedException`.

### Acceptance Criterion 4: Non-Destructive Diagnostic Orphan Detection
- **Given** existing unreferenced tables in `WorkoutSamples.db` and orphan child rows with `workout_id = -1`,
- **When** `OrphanDataDiagnosticHelper` runs,
- **Then** findings are logged diagnostically without executing automated silent `DROP TABLE` or `DELETE` on startup.

### Acceptance Criterion 5: Strict `insertOrThrow` Enforcement
- **Given** a source workout summary record with conflicting or invalid constraints,
- **When** `insertSummaryWithResolution` is invoked,
- **Then** `db.insertOrThrow()` throws `SQLiteException` immediately, `newId` validation fails, and no child copy routines are called with `workout_id = -1`.

### Acceptance Criterion 6: Static Schema Mapping & NOT NULL Defaults
- **Given** a backup archive created from an older or modified schema containing surplus unknown columns and missing `NOT NULL` columns,
- **When** `copyRelatedRows` or `copyLaps` processes the records,
- **Then** surplus columns are discarded, missing `NOT NULL` columns receive documented default values, and rows are successfully inserted without constraint violations.

### Acceptance Criterion 7: Inverted Legacy Import Preconditions & Staging
- **Given** a parsed workout where `firstTime == null`,
- **When** `LegacyImportEngine.saveParsedWorkout()` is called,
- **Then** the method returns `ImportResult(ImportStatus.FAILED)` immediately, and zero tables are created in `WorkoutSamples.db`.

### Acceptance Criterion 8: Cooperative Coroutine Cancellation & Non-Blocking Delay
- **Given** a running bulk import worker in `LegacyImportEngine`,
- **When** the parent coroutine scope is cancelled during download or retry backoff,
- **Then** `CancellationException` propagates immediately, the worker terminates without continuing downloads, and `delay()` suspends cooperatively without blocking thread execution.

---

## 3. Chesterton's Fence & Requirement Archaeology (`REQ-MIG-043`)

1. **Original Requirement ID & Target**:
   - Net-new requirement `REQ-MIG-043`, extending `REQ-MIG-042` (`ATT-3991`) and `REQ-MIG-031` (`ATT-2023`).
   - Targets `ImportEngine.kt`, `LegacyImportEngine.kt`, `BackupManager.kt`, and `WorkoutImportAtomicityTest.kt`.
2. **Historical Origin & Commit Trace**:
   - Discovered during October 2026 independent whole-app code review of `feature/ATT-3921`, tracked under ticket `ATT-3992`.
3. **Root Reason for Existing Formulation**:
   - Separate database files were historically accessed via independent `SQLiteOpenHelper` singletons, preventing unified transactions.
   - `db.insert()` was used to avoid uncaught crashes, but resulted in silent `-1L` returns and orphan rows.
   - Legacy import created samples tables early to release memory buffers, but lacked transactional rollback if subsequent metadata operations failed.
   - Download retry loops originated in pre-coroutine Java Thread code using `Thread.sleep()`.
4. **Preservation of Core Invariants**:
   - Full backup import fidelity, duplicate workout detection, sport/equipment resolution, and 100% clean-room test pass rate MUST NOT be broken.

---

## 4. Test Specifications

### 4.1 TST-MIG-040.1: Atomic Cross-Database Rollback via SQLite ATTACH
- **File**: `com.atrainingtracker.trainingtracker.migration.WorkoutImportAtomicityTest.kt`
- **Method**: `testImport_failureDuringAttachedMutation_rollsBackAllDatabasesAtomically()`
- **Procedure**:
  1. Set up test SQLite databases for Summaries, Laps, and Samples.
  2. Open dedicated connection and attach Laps and Samples.
  3. Begin transaction on dedicated connection.
  4. Insert summary and related rows; simulate an exception during sample copy.
  5. End transaction (abort/rollback).
  6. Assert 0 summary rows in `tblWorkoutSummaries`, 0 rows in `extrema_values`, 0 rows in `tblLaps`, and no samples table created in `WorkoutSamples.db`.

### 4.2 TST-MIG-040.2: Partial Failure during ATTACH Resilience
- **File**: `com.atrainingtracker.trainingtracker.migration.WorkoutImportAtomicityTest.kt`
- **Method**: `testImport_partialFailureDuringAttach_safelyClosesConnection()`
- **Procedure**:
  1. Invoke attach logic with a nonexistent database path.
  2. Verify exception is caught.
  3. Verify connection is closed and leaves zero open file locks or dangling transactions.

### 4.3 TST-MIG-040.3: Shared Dual Concurrency Lock Serialization
- **File**: `com.atrainingtracker.trainingtracker.migration.WorkoutImportAtomicityTest.kt`
- **Method**: `testConcurrency_backupManagerLock_serializesAccess()`
- **Procedure**:
  1. Acquire `BackupManager.acquireLock(context)`.
  2. Attempt to acquire lock from a second thread; assert lock attempt returns `null`.
  3. Release lock from primary thread; assert second thread can acquire cleanly.

### 4.4 TST-MIG-040.4: Non-Destructive Diagnostic Orphan Detection
- **File**: `com.atrainingtracker.trainingtracker.migration.WorkoutImportAtomicityTest.kt`
- **Method**: `testDiagnostics_detectsOrphanTablesAndRowsNonDestructively()`
- **Procedure**:
  1. Manually insert unreferenced sample table and child rows with `workout_id = -1`.
  2. Run `OrphanDataDiagnosticHelper.scanForOrphans(context)`.
  3. Assert findings contain the unreferenced table and orphan rows.
  4. Assert the unreferenced table and orphan rows still exist on disk (zero automated deletion).

### 4.5 TST-MIG-040.5: Strict `insertOrThrow` & Non-Positive ID Assertion
- **File**: `com.atrainingtracker.trainingtracker.migration.WorkoutImportAtomicityTest.kt`
- **Method**: `testInsertSummary_failureThrowsAndPreventsChildRows()`
- **Procedure**:
  1. Trigger a constraint failure on summary insertion.
  2. Verify `SQLiteConstraintException` is thrown.
  3. Assert child copy methods are never invoked with `workout_id = -1`.

### 4.6 TST-MIG-040.6: Static Schema Mapping & NOT NULL Defaults
- **File**: `com.atrainingtracker.trainingtracker.migration.WorkoutImportAtomicityTest.kt`
- **Method**: `testSchemaMapping_discardsSurplusColumnsAndAppliesNotNullDefaults()`
- **Procedure**:
  1. Populate source cursor with surplus unknown columns and missing `NOT NULL` columns.
  2. Execute `copyRelatedRows` and `copyLaps`.
  3. Verify insertion succeeds, surplus columns are omitted, and default values are populated.

### 4.7 TST-MIG-040.7: Inverted Legacy Import Preconditions & Staging
- **File**: `com.atrainingtracker.trainingtracker.migration.WorkoutImportAtomicityTest.kt`
- **Method**: `testLegacyImport_nullFirstTime_doesNotCreateSamplesTable()`
- **Procedure**:
  1. Create a `ParsedWorkout` with `bufferedSamples` but `firstTime = null`.
  2. Invoke `saveParsedWorkout()`.
  3. Assert return status is `ImportStatus.FAILED`.
  4. Assert zero tables are created in `WorkoutSamplesDatabaseManager`.

### 4.8 TST-MIG-040.8: Cooperative Coroutine Cancellation & Non-Blocking Delay
- **File**: `com.atrainingtracker.trainingtracker.migration.WorkoutImportAtomicityTest.kt`
- **Method**: `testLegacyImport_cancellationRethrownPromptly()`
- **Procedure**:
  1. Launch import in a coroutine scope.
  2. Cancel scope while worker is executing.
  3. Assert `CancellationException` is rethrown immediately and worker terminates cleanly.

### 4.9 TST-MIG-040.9: Clean-Room Full Suite Regression Execution
- **Command**: `./gradlew testDebugUnitTest`
- **Criteria**: 100% pass rate across all unit and integration tests with zero regressions.

---

## 5. Traceability Matrix

| Requirement ID | Sub-Requirement Clause | Test Case ID | Verification Method | Status |
|---|---|---|---|---|
| `REQ-MIG-043.1` | Dedicated connection lifecycle & ATTACH atomicity | `TST-MIG-040.1`, `TST-MIG-040.2` | Automated Unit Test | Specified |
| `REQ-MIG-043.2` | Shared dual ReentrantLock + FileLock concurrency | `TST-MIG-040.3` | Automated Concurrency Test | Specified |
| `REQ-MIG-043.3` | Non-destructive diagnostic orphan detection | `TST-MIG-040.4` | Automated Diagnostic Test | Specified |
| `REQ-MIG-043.4` | Strict `insertOrThrow` and ID validation | `TST-MIG-040.5` | Automated Unit Test | Specified |
| `REQ-MIG-043.5` | Static schema mapping & `NOT NULL` defaults | `TST-MIG-040.6` | Automated Unit Test | Specified |
| `REQ-MIG-043.6` | Legacy import pipeline inversion & transactional staging | `TST-MIG-040.7` | Automated Unit Test | Specified |
| `REQ-MIG-043.7` | Cooperative coroutine cancellation & `delay()` | `TST-MIG-040.8` | Automated Coroutine Test | Specified |
| `REQ-MIG-043` | Clean-room full regression test suite pass | `TST-MIG-040.9` | `./gradlew testDebugUnitTest` | Specified |

---

## 6. Localization Parity Audit

This technical bug fix operates strictly on internal SQLite transaction coordination, schema whitelisting, concurrency locking, and background coroutine workers. It introduces zero new user-facing string resources and modifies zero existing UI string resources. Existing localized strings for import status and errors (`R.string.legacy_import__downloading_dropbox`, `R.string.error_workout_table_name_too_long`, etc.) remain 100% intact across all 9 supported languages (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
