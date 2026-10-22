# Stage 3: Implementation Plan: ATT-3992

**Ticket**: [ATT-3992](https://atrainingtracker.atlassian.net/browse/ATT-3992)  
**Subtask**: `ATT-4173` (`[Impl-Plan]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-MIG-043` (*Atomic Cross-Database Import via SQLite ATTACH, Dedicated Scoped Connection, Shared ReentrantLock, Non-Destructive Orphan Repair, insertOrThrow Enforcement & Cooperative Coroutine Responsiveness*)  
**Test Case Mapping**: `TST-MIG-040`  
**Branch**: `feature/ATT-3992`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Executive Summary & Architectural Design (SWE.2)

### 1.1 Architecture & Component Boundaries
The remediation targets the cross-database atomicity, schema intersection, insert error handling, and coroutine cancellation pipelines across four components:

1. **Atomic Multi-Database Transaction Coordinator via SQLite `ATTACH` in `ImportEngine.kt`**:
   - **Dedicated Scoped Connection**: Never use the global singleton helper for `ATTACH`. Open a dedicated `SQLiteDatabase` handle on `WorkoutSummaries.db` strictly for the import session (`openDatabase(canonicalSummariesPath, null, OPEN_READWRITE)`).
   - **Canonical Absolute Path Validation**: Validate canonical absolute paths for `Laps.db` and `WorkoutSamples.db` using `File.canonicalPath` to prevent TOCTOU and directory escape.
   - **Cross-Process Concurrency Guard**: Acquire the dual JVM `ReentrantLock` and OS-level `FileLock` (`BackupManager.acquireLock(context)`) before starting the import session and release it in `finally`.
   - **WAL Checkpointing**: Execute `DatabaseConnectionManager.checkpointAll(context)` before opening the dedicated connection to ensure clean WAL state.
   - **Single Unified Transaction**:
     - `dedicatedSummariesDb.execSQL("ATTACH DATABASE ? AS lapsDb", arrayOf(canonicalLapsPath))`
     - `dedicatedSummariesDb.execSQL("ATTACH DATABASE ? AS samplesDb", arrayOf(canonicalSamplesPath))`
     - For each workout: `dedicatedSummariesDb.beginTransaction()`.
     - Insert summary into `main.tblWorkoutSummaries` via `insertOrThrow()`. Validate `require(newId > 0)`.
     - Copy extrema into `main.tblExtremaValues` and accumulated sensors into `main.tblAccumulatedSensors`.
     - Copy laps directly into `lapsDb.tblLaps`.
     - Create and copy samples table into `samplesDb.[tableName]`.
     - `dedicatedSummariesDb.setTransactionSuccessful()` and `endTransaction()`.
     - If any exception or cancellation occurs, `endTransaction()` atomically rolls back `main`, `lapsDb`, and `samplesDb` simultaneously.
   - **Hardened Detach & Connection Close**: In outer `finally`, execute `DETACH DATABASE lapsDb`, `DETACH DATABASE samplesDb`, and `close()` in independent nested `try/catch` blocks.

2. **Strict `insertOrThrow` & Formal Schema Mapping in `ImportEngine.kt`**:
   - Replace all `db.insert()` calls with `db.insertOrThrow()`.
   - In `copyRelatedRows` and `copyLapsAttached`, filter source cursor columns against static column whitelists.
   - Apply explicit default values for `NOT NULL` columns when missing in older archives:
     - `TABLE_EXTREMA_VALUES`: `WORKOUT_ID = newId`, `SENSOR_TYPE = 0`, `MIN_VALUE = 0.0`, `MAX_VALUE = 0.0`, `AVG_VALUE = 0.0`.
     - `TABLE_ACCUMULATED_SENSORS`: `WORKOUT_ID = newId`, `SENSOR_TYPE = 0`, `VALUE = 0.0`.
     - `tblLaps`: `WORKOUT_ID = newId`, `LAP_NUMBER = 1`, `START_TIME = 0L`, `DURATION = 0L`, `DISTANCE = 0.0`.

3. **Pipeline Inversion & Coroutine Cancellation Compliance in `LegacyImportEngine.kt`**:
   - In `saveParsedWorkout()`, validate `require(firstTime != null)` up front. If null, return `ImportResult(ImportStatus.FAILED)` immediately before creating any sample table.
   - Invert write sequence: insert summary first using `insertOrThrow()`. Only upon summary success, create sample table and insert samples inside a synchronized transaction.
   - Catch and rethrow `CancellationException` across all worker coroutine scopes (`LegacyImportEngine.kt:208-258`).
   - Replace all `Thread.sleep()` in `downloadFileWithRetry` and `downloadGoogleDriveFileWithRetry` with suspending `delay()`.
   - Call `coroutineContext.ensureActive()` at the start of each import loop iteration.

4. **Non-Destructive Diagnostic Orphan Detection in `OrphanDataDiagnosticHelper.kt`**:
   - Introduce `OrphanDataDiagnosticHelper.scanForOrphans(context)`:
     - Queries `WorkoutSamples.db` for tables matching `tblWorkoutSamples_*` lacking a matching `fileBaseName` in `WorkoutSummaries.db`.
     - Queries child tables (`tblExtremaValues`, `tblAccumulatedSensors`, `tblLaps`) for rows with `workout_id <= 0` or missing parent summaries.
     - Logs findings diagnostically without automated destructive deletion.

---

## 2. Step-by-Step Implementation Steps

### Step 1: Implement `OrphanDataDiagnosticHelper.kt`
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/OrphanDataDiagnosticHelper.kt`
- **Action**:
  - Author helper scanning for unreferenced sample tables and invalid child rows (`workout_id <= 0`).
  - Log findings to diagnostic logs; provide non-destructive inspection API.

### Step 2: Update `ImportEngine.kt` with SQLite `ATTACH` Transaction & Schema Mapping
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportEngine.kt`
- **Action**:
  - Acquire `BackupManager.acquireLock(context)` protecting import session.
  - Checkpoint WAL via `DatabaseConnectionManager.checkpointAll(context)`.
  - Open dedicated scoped `SQLiteDatabase` on `WorkoutSummaries.db`.
  - Validate canonical paths and attach `Laps.db` and `WorkoutSamples.db`.
  - Replace `insert` with `insertOrThrow` in `insertSummaryWithResolution` and validate `require(newId > 0)`.
  - Implement static schema mapping and explicit default values for `copyRelatedRows` and `copyLapsAttached`.
  - Create sample table and copy samples using attached connection `samplesDb.[tableName]`.
  - Coordinate all operations in a single `beginTransaction() / setTransactionSuccessful() / endTransaction()` block.
  - In outer `finally`, execute robust nested `DETACH` and `close()`.

### Step 3: Refactor `LegacyImportEngine.kt` with Pipeline Inversion & Coroutine Compliance
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`
- **Action**:
  - Validate `firstTime != null` before any table creation or database mutation in `saveParsedWorkout()`.
  - Insert summary row first via `insertOrThrow()`; only create samples table if summary insertion succeeds.
  - In worker loops (lines 208–258), catch `CancellationException` explicitly and rethrow immediately.
  - Replace `Thread.sleep` with `delay` in `downloadFileWithRetry` and `downloadGoogleDriveFileWithRetry`.
  - Add `coroutineContext.ensureActive()` at the start of import loops.

### Step 4: Author `WorkoutImportAtomicityTest.kt`
- **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/WorkoutImportAtomicityTest.kt`
- **Action**:
  - Test atomic rollback across attached databases (zero orphan rows, zero samples table).
  - Test partial failure during `ATTACH` (graceful connection closure and no dangling transactions).
  - Test `BackupManager.acquireLock` concurrency serialization.
  - Test non-destructive diagnostic orphan detection.
  - Test `insertOrThrow` and rejection of non-positive `newId`.
  - Test static schema mapping with surplus column omission and `NOT NULL` defaults.
  - Test inverted legacy import with `firstTime = null` (no table creation).
  - Test coroutine cancellation immediate propagation without thread blocking.

### Step 5: Clean-Room Full Suite Regression Execution
- **Command**: `./gradlew testDebugUnitTest`
- **Criteria**: 100% pass rate across all unit and integration tests with zero regressions.

---

## 3. Thread & Dispatcher Architecture

| Operation | Dispatcher / Thread | Concurrency Guard |
|---|---|---|
| Incremental Backup Import (`ImportEngine`) | `Dispatchers.IO` | `BackupManager.acquireLock` (Dual `ReentrantLock` + `FileLock`) |
| SQLite Multi-DB Transaction | Dedicated connection on `Dispatchers.IO` | Scoped SQLite transaction (`dedicatedSummariesDb.beginTransaction()`) |
| Legacy File Download & Retry | `Dispatchers.IO` worker channels | Suspending `delay()`, `coroutineContext.ensureActive()` |
| Legacy DB Write & Recalculation | `Dispatchers.IO` | `importMutex.withLock` |

---

## 4. Invariant Protection & Rollback Safety

1. **True ACID Multi-Database Atomicity**: SQLite's native master journal guarantees all-or-nothing atomicity across `main`, `lapsDb`, and `samplesDb`. If an error occurs, SQLite reverts all changes across all three files simultaneously.
2. **Zero Orphan Rows**: Child rows (`extrema_values`, `accumulated_sensors`, `lapsDb.tblLaps`) are only inserted after `insertOrThrow` confirms `newId > 0`. If any subsequent step fails, rollback purges the summary and all children.
3. **Connection Lifecycle Safety**: Dedicated connection is closed and attachments detached in a hardened nested `finally` block, ensuring zero connection leaks or WAL lock retention.
4. **Preservation of Existing Table Naming & Storage**: Existing file-based table naming and physical database separation are 100% preserved.
5. **No Regressions**: Full clean-room test suite pass rate must remain 100%.

---

## 5. Traceability Matrix

| Requirement Clause | Implementation File | Implementation Step | Test Case ID | Status |
|---|---|---|---|---|
| `REQ-MIG-043.1` (Dedicated connection & ATTACH atomicity) | `ImportEngine.kt` | Step 2 | `TST-MIG-040.1`, `TST-MIG-040.2` | Planned |
| `REQ-MIG-043.2` (Shared concurrency lock) | `ImportEngine.kt`, `BackupManager.kt` | Step 2 | `TST-MIG-040.3` | Planned |
| `REQ-MIG-043.3` (Non-destructive orphan diagnostics) | `OrphanDataDiagnosticHelper.kt` | Step 1 | `TST-MIG-040.4` | Planned |
| `REQ-MIG-043.4` (Strict `insertOrThrow` & ID check) | `ImportEngine.kt`, `LegacyImportEngine.kt` | Step 2, Step 3 | `TST-MIG-040.5` | Planned |
| `REQ-MIG-043.5` (Schema mapping & `NOT NULL` defaults) | `ImportEngine.kt` | Step 2 | `TST-MIG-040.6` | Planned |
| `REQ-MIG-043.6` (Legacy pipeline inversion) | `LegacyImportEngine.kt` | Step 3 | `TST-MIG-040.7` | Planned |
| `REQ-MIG-043.7` (Coroutine cancellation & `delay`) | `LegacyImportEngine.kt` | Step 3 | `TST-MIG-040.8` | Planned |
| `REQ-MIG-043` (Full regression pass) | Project test suite | Step 5 | `TST-MIG-040.9` | Planned |
