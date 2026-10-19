# Stage 2: Requirement & Test Specification - ATT-3995: [Bug] [High] Export jobs retry forever, get stuck and cannot be cancelled

**Ticket**: [ATT-3995](https://rainerblind.atlassian.net/browse/ATT-3995)  
**Sub-task**: [ATT-4187](https://rainerblind.atlassian.net/browse/ATT-4187) (`[Req & Test Spec]`)  
**Parent Epic**: None (*Product Backlog*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-DAT-029` (*Resilient Background Export & Upload Pipeline, Attempt Capping, Cooperative Cancellation, Transactional Upload Remediation, and Stuck State Recovery*)  
**Test Spec ID**: `TST-DAT-024`  
**Branch**: `feature/ATT-3995`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Requirement Specification (REQ-DAT-029)

### 1.1 Problem Statement & Rationale
Export background workers in `com.atrainingtracker.trainingtracker.exporter` exhibited severe reliability and lifecycle flaws:
- Deterministic errors (`ParseException`, `JSONException`, `RuntimeException`) were flagged as retryable in `BaseExporter.export()`, and `ExportWorker.doWork()` lacked an attempt cap, leading to infinite retry loops that drained battery and flooded system logs.
- Uncaught exceptions escaped `ExportWorker.doWork()` without invoking `informOthersFailed()`, abandoning the database status permanently in `PROCESSING`.
- When local file export failed, dependent cloud uploads were abandoned in `WAITING` indefinitely because WorkManager cancelled downstream tasks without executing their workers.
- `ExportManager.invalidateWorkoutExport()` was an ineffective no-op because WorkRequests were tagged with `exportInfo.toString()` (not matching `fileBaseName`) and enqueued non-uniquely. Active workers lacked cooperative cancellation checks and continued running during crop operations.

### 1.2 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-DAT-029`, extending `REQ-DAT-028` (`ATT-3994`) and `REQ-DAT-018` (`ATT-1827`). Targets `BaseExporter.java`, `ExportWorker.kt`, `ExportManager.java`, `ExportStatusDatabaseManager.java`, and `WorkoutDeletionHelper.java`.
* **Historical Origin & Commit Trace**: Ticket `ATT-3995`, Sprint `2026-42.10`, discovered during independent whole-app code review of `feature/ATT-3921` (`0234bfc0`).
* **Root Reason for Existing Formulation**: Early implementation mapped all exceptions to retry without attempt caps, omitted cooperative cancellation in cursor loops, and used non-matching WorkManager tags during invalidation under the assumption that network flakiness was the sole source of export failures.
* **Preservation of Core Invariants**: Valid exports to all formats (FIT, GPX, TCX, CSV, GC) continue functioning seamlessly; cloud uploads retain transient retry capability up to 3 attempts; 100% clean-room test pass rate strictly preserved.

### 1.3 Functional & Architectural Requirements
The system SHALL fulfill the following architectural and functional constraints (`REQ-DAT-029`):

1. **Deterministic vs. Retryable Error Classification (`REQ-DAT-029.1`)**:
   - `BaseExporter.export()` SHALL catch all `Throwable` instances so no unhandled exception escapes.
   - Deterministic errors (`ParseException`, `JSONException`, `IllegalArgumentException`, `NullPointerException`, `IllegalStateException`, `RuntimeException`) SHALL be classified as non-retryable (`shallRetry = false`).
   - Transient `IOException` (e.g. network/socket drops during upload or storage temporarily unavailable) SHALL be permitted to request retry (`shallRetry = true`).

2. **WorkManager Retry Capping & Backoff Policy (`REQ-DAT-029.2`)**:
   - `ExportWorker` SHALL define `MAX_RETRIES = 3`.
   - When `result.shallRetry()` is true and `runAttemptCount < MAX_RETRIES`, `ExportWorker` SHALL update database status to `WAITING` with retry attempt information and return `Result.retry()`.
   - When `runAttemptCount >= MAX_RETRIES` or `result.shallRetry()` is false, `ExportWorker` SHALL invoke `informOthersFailed()` and return `Result.failure()`, terminating the work chain.

3. **Cooperative Cancellation & Resource Cleanup (`REQ-DAT-029.3`)**:
   - `BaseExporter` SHALL expose a thread-safe `CancellationSignal` interface (`isCancelled(): Boolean`) and `setCancellationSignal(CancellationSignal)` method.
   - `ExportWorker` SHALL wire `isStopped` into `mExporter.setCancellationSignal { isStopped }`.
   - All sample cursor loops in file writers (`BaseFileWriter`, `FitFileWriter`, `GPXFileWriter`, `TCXFileWriter`, `CSVFileWriter`, `GCFileWriter`) SHALL check `isCancelled()` at the *start of every loop iteration AND immediately after any blocking I/O operation*, throwing `CancellationException` immediately upon worker cancellation.
   - On cancellation, all open streams and SQLite cursors SHALL be safely closed in `finally`/`try-with-resources`, partial output files SHALL be deleted, and `ExportResult(false, false, "Export cancelled")` returned without hanging.
   - If `isStopped` is detected in `ExportWorker` before or after execution, it SHALL exit cleanly with `Result.failure()` without overwriting database status.

4. **Unique Work Enqueuing & Tagged Invalidation (`REQ-DAT-029.4`)**:
   - `ExportManager` SHALL centralize unique work naming in public static helper `getUniqueWorkName(fileBaseName, fileFormat)` returning `"export_" + fileBaseName + "_" + fileFormat.name()`.
   - Every `OneTimeWorkRequest` SHALL be tagged with both `fileBaseName` and `exportInfo.toString()`.
   - `ExportManager.startFullExportProcess()` SHALL enqueue work via `WorkManager.beginUniqueWork(getUniqueWorkName(fileBaseName, fileFormat), ExistingWorkPolicy.REPLACE, fileCreationWork)`.
   - `invalidateWorkoutExport(fileBaseName)` SHALL synchronously cancel WorkManager tasks by `fileBaseName` tag, unique work names for all formats, and legacy tags before performing file cleanup and resetting database status to `WAITING`.

5. **Atomic Dependent Upload Remediation & Cascading Order (`REQ-DAT-029.5`)**:
   - Relationship and Sequencing: `informOthersFailed(answer)` strictly updates the parent row `(fileBaseName, ExportType.FILE, fileFormat)` to `FINISHED_FAILED`.
   - When `mExportInfo.exportType == ExportType.FILE` reaches permanent failure, `ExportWorker` SHALL immediately follow by calling `ExportStatusDatabaseManager.markDependentUploadsFailed(fileBaseName, fileFormat, errorMsg)`.
   - `markDependentUploadsFailed` SHALL execute within a dedicated native SQLite transaction (`beginTransaction` / `setTransactionSuccessful`), wrapped in a comprehensive `try-catch(Throwable)` logging `fileBaseName` and `fileFormat` with full stack trace on any failure, cascading terminal failure strictly to dependent cloud upload rows (`TYPE != FILE`) for that format currently in `WAITING` status, guaranteeing atomicity and eliminating double-processing risks.

6. **Self-Healing Stuck State Recovery with Configurable Timeout, Synchronized Liveness Double-Check & Entry Sanitation (`REQ-DAT-029.6`)**:
   - Entry Sanitation Invariant: `ExportManager` (`newWorkout`, `exportWorkout`, `exportWorkoutTo`) and `ExportStatusDatabaseManager` SHALL sanitize and validate `fileBaseName` upon entry; null, empty, blank, or literal "null" strings MUST be rejected before insertion into `ExportStatus.db`.
   - Heartbeat Maintenance: `ExportStatusDatabaseManager` SHALL persist `last_updated` on every status mutation, and `ExportWorker` SHALL refresh `last_updated` periodically during active exports (heartbeat interval: every 30 seconds or 5,000 samples).
   - Configurable Duration Bound & Synchronized Double-Check Guard: The stuck state timeout is bound to `ExportStatusDatabaseManager.MAX_EXPORT_DURATION_MS = 15 * 60 * 1000L` (15 minutes). A `PROCESSING` row is candidate for transition to `FINISHED_FAILED` if `currentTime - last_updated > MAX_EXPORT_DURATION_MS` of silence. The double-check guard SHALL be performed within a serialized block or by querying a snapshot of WorkManager state that is guaranteed to be consistent with the database transaction. Before finalizing the transition to `FINISHED_FAILED`, the system SHALL re-verify the `last_updated` heartbeat timestamp within the serialized database transaction to ensure no new heartbeat was recorded by a concurrently executing or late-starting worker. If a job is verified alive in WorkManager or a fresh heartbeat was recorded, the transition to `FINISHED_FAILED` is aborted and suppressed, eliminating race conditions and preventing false positives on long workouts or high I/O loads.
   - Idempotency & Safe Late-Exit: `informOthersFailed()` and `updateStatus()` SHALL be idempotent. If a late-finishing worker discovers its row was already transitioned or cancelled, it SHALL log a diagnostic warning and exit cleanly without corrupting database state.
   - `WorkoutDeletionHelper.deleteWorkout(long workoutId)` SHALL guard `fileBaseName` defensively and cancel active WorkManager tasks for `fileBaseName` when deleting a workout.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Deterministic Error Termination)**:
  * *Given* a workout export encountering a deterministic error (e.g. `ParseException`, `IllegalArgumentException`, or invalid sample data),
  * *When* `ExportWorker.doWork()` executes,
  * *Then* `result.shallRetry()` is false, status transitions to `FINISHED_FAILED`, and the worker returns `Result.failure()` without retrying.
* **Criterion 2 (Retry Attempt Capping)**:
  * *Given* a transient `IOException` during cloud upload,
  * *When* `ExportWorker.doWork()` executes with `runAttemptCount < 3`,
  * *Then* it updates status with retry attempt count and returns `Result.retry()`; when `runAttemptCount >= 3`, it terminates with `FINISHED_FAILED` and `Result.failure()`.
* **Criterion 3 (Cooperative Cancellation on Crop/Invalidation)**:
  * *Given* an active file export in progress,
  * *When* `ExportManager.invalidateWorkoutExport(fileBaseName)` is invoked or the workout is cropped,
  * *Then* the running worker detects cancellation at the start of the next sample loop iteration or immediately after blocking I/O, aborts immediately, deletes partial files, and WorkManager cancels pending upload tasks.
* **Criterion 4 (Ordered Dependent Upload Cleanup)**:
  * *Given* an export request configured for file export plus cloud uploads (Dropbox, Google Drive, Strava),
  * *When* the local file creation fails,
  * *Then* `informOthersFailed` writes `FINISHED_FAILED` for the file, and `markDependentUploadsFailed` atomically transitions all dependent cloud upload rows in `ExportStatus.db` from `WAITING` to `FINISHED_FAILED` with error logging on transaction failure.
* **Criterion 5 (Stuck State Self-Healing with Synchronized Double-Checked Liveness Guard)**:
  * *Given* an export row stranded in `PROCESSING` from an unexpected process kill with `last_updated` older than `MAX_EXPORT_DURATION_MS` of silence,
  * *When* `ExportStatusDatabaseManager` queries the status within a serialized block,
  * *Then* it queries WorkManager state and re-verifies `last_updated`; if zero workers are active and no heartbeat was updated, it lazily transitions the row to `FINISHED_FAILED`, clearing the spinning indicator.
  * *Given* an active, long-running export (e.g. 20 minutes) verified running in WorkManager or updating its heartbeat during the check,
  * *When* queried,
  * *Then* the transition to `FINISHED_FAILED` is aborted, and the row remains in `PROCESSING`.
* **Criterion 6 (Safe Entry Sanitation & Deletion)**:
  * *Given* an attempt to enqueue an export with corrupted, blank, or `"null"` `fileBaseName`,
  * *When* `ExportManager` or `ExportStatusDatabaseManager` receives the call,
  * *Then* it rejects the input at entry, preventing invalid rows in `ExportStatus.db`.
  * *Given* a legacy workout deletion with invalid `fileBaseName`,
  * *When* `WorkoutDeletionHelper.deleteWorkout()` is invoked,
  * *Then* it executes safely without throwing `IllegalArgumentException`.

### 1.5 System Invariants
1. Zero regression in existing valid export generators (FIT, GPX, TCX, CSV, GC).
2. All export operations must terminate in a definitive status (`FINISHED_SUCCESS` or `FINISHED_FAILED`), never permanently remaining in `PROCESSING` or `WAITING`.
3. WorkManager retries must be capped at 3 attempts with exponential backoff.
4. Transactional integrity of `ExportStatus.db` must be preserved under concurrency and failure.

---

## 2. Test Specification (TST-DAT-024)

### Test Case 1: `testExport_deterministicErrors_shallNotRetry` (`TST-DAT-024.1`)
* **Scope**: Unit Test (`BaseExporterTest.kt` / `ExportWorkerTest.kt`)
* **Preconditions**: Mock `BaseExporter` configured to throw `ParseException`, `JSONException`, and `IllegalArgumentException`.
* **Action**: Invoke `exporter.export(exportInfo)`.
* **Expected Result**: Returns `ExportResult(success=false, shallRetry=false)`.

### Test Case 2: `testExport_transientIOException_shallRetry` (`TST-DAT-024.2`)
* **Scope**: Unit Test (`BaseExporterTest.kt`)
* **Preconditions**: Mock `BaseExporter` configured to throw transient `IOException("Connection reset")`.
* **Action**: Invoke `exporter.export(exportInfo)`.
* **Expected Result**: Returns `ExportResult(success=false, shallRetry=true)`.

### Test Case 3: `testExportWorker_maxRetriesEnforced` (`TST-DAT-024.3`)
* **Scope**: Unit Test (`ExportWorkerTest.kt`)
* **Preconditions**: `ExportWorker` constructed with `runAttemptCount = 3` and exporter returning `shallRetry = true`.
* **Action**: Invoke `doWork()`.
* **Expected Result**: Returns `Result.failure()`, status in database is set to `FINISHED_FAILED`, and no further retries are scheduled.

### Test Case 4: `testExportWorker_uncaughtRuntimeException_writesFailedStatus` (`TST-DAT-024.4`)
* **Scope**: Unit Test (`ExportWorkerTest.kt`)
* **Preconditions**: Exporter throws unexpected `IllegalStateException` or `RuntimeException`.
* **Action**: Invoke `doWork()`.
* **Expected Result**: Exception is caught, status is updated to `FINISHED_FAILED`, and returns `Result.failure()`. Status is never left in `PROCESSING`.

### Test Case 5: `testExportWorker_fileFailure_marksDependentUploadsFailedInOrder` (`TST-DAT-024.5`)
* **Scope**: Unit Test (`ExportWorkerTest.kt` / `ExportStatusDatabaseManagerTest.kt`)
* **Preconditions**: `ExportStatus.db` contains `FILE` row and dependent `DROPBOX`, `GOOGLE_DRIVE` rows in `WAITING`.
* **Action**: File export fails with `Result.failure()`.
* **Expected Result**: File row transitions to `FINISHED_FAILED`, followed by all dependent rows for that `fileBaseName` and `fileFormat` atomically updating to `FINISHED_FAILED` inside a single SQLite transaction with explicit error catching.

### Test Case 6: `testCooperativeCancellation_abortsExportAndDeletesPartialFile` (`TST-DAT-024.6`)
* **Scope**: Unit Test (`BaseExporterTest.kt` / `BaseFileWriterTest.kt`)
* **Preconditions**: File exporter running with `CancellationSignal` returning true mid-stream.
* **Action**: Sample loop checks cancellation signal at start of loop iteration and after blocking I/O.
* **Expected Result**: `CancellationException` is thrown immediately, partial file is deleted, all resources closed, and `ExportResult(false, false, "Export cancelled")` is returned.

### Test Case 7: `testExportManager_uniqueWorkAndTagging` (`TST-DAT-024.7`)
* **Scope**: Unit Test (`ExportManagerTest.kt`)
* **Preconditions**: `ExportManager.startFullExportProcess(fileBaseName, fileFormat)` called.
* **Action**: Inspect enqueued WorkRequest tags and unique work name.
* **Expected Result**: WorkRequest has tags `fileBaseName` and `exportInfo.toString()`, unique work name equals `getUniqueWorkName(fileBaseName, fileFormat)`, and `invalidateWorkoutExport(fileBaseName)` invokes cancellation with matching tags and unique names.

### Test Case 8: `testWorkoutDeletionHelper_invalidBaseName_safe` (`TST-DAT-024.8`)
* **Scope**: Unit Test (`WorkoutDeletionHelperTest.kt`)
* **Preconditions**: Workout with blank or `"null"` `fileBaseName`.
* **Action**: Call `deleteWorkout(workoutId)`.
* **Expected Result**: Completes without throwing `IllegalArgumentException`.

### Test Case 9: 9-Language Localization & Specifier Audit (`TST-DAT-024.9`)
* **Scope**: Localization Parity Test
* **Goal**: Verify string presence and matching `%s`/`%d` tokens across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 10: Clean-Room Regression Suite (`TST-DAT-024.10`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

### Test Case 11: `testExportStatusDatabaseManager_staleProcessingRecoveryWithWorkManagerGuard` (`TST-DAT-024.11`)
* **Scope**: Unit Test (`ExportStatusDatabaseManagerTest.kt`)
* **Preconditions**: Insert export row in `PROCESSING` with `last_updated` timestamp older than `MAX_EXPORT_DURATION_MS` and double-check confirms no active WorkManager worker running.
* **Action**: Query `getExportStatusMap(fileBaseName)` or `getExportRows(fileBaseName)`.
* **Expected Result**: The stale row is lazily transitioned to `FINISHED_FAILED` with answer "Export timed out / process terminated". Recent `PROCESSING` rows (< `MAX_EXPORT_DURATION_MS`) or rows verified active in WorkManager are unaffected.

### Test Case 12: `testExport_longRunningExportWithHeartbeat_doesNotTriggerTimeout` (`TST-DAT-024.12`)
* **Scope**: Unit Test (`ExportStatusDatabaseManagerTest.kt`)
* **Preconditions**: Export row in `PROCESSING` created 20 minutes ago, but `last_updated` was refreshed 1 minute ago via active heartbeat or verified active in WorkManager.
* **Action**: Query `getExportStatusMap(fileBaseName)` or `getExportRows(fileBaseName)`.
* **Expected Result**: Status remains `PROCESSING` and is NOT prematurely marked as failed.

### Test Case 13: `testExportManager_inputSanitizationAtEntry` (`TST-DAT-024.13`)
* **Scope**: Unit Test (`ExportManagerTest.kt`)
* **Preconditions**: Call `exportWorkoutTo` or `newWorkout` with blank or `"null"` `fileBaseName`.
* **Action**: Verify validation rejects the call before database insertion.
* **Expected Result**: Input is rejected, and zero invalid rows are written to `ExportStatus.db`.

### Test Case 14: `testExportStatusDatabaseManager_staleProcessingRecovery_abortsIfHeartbeatUpdatedDuringCheck` (`TST-DAT-024.14`)
* **Scope**: Unit Test (`ExportStatusDatabaseManagerTest.kt`)
* **Preconditions**: Stale `PROCESSING` row whose `last_updated` timestamp is updated by a concurrent worker tick while the WorkManager liveness check is in flight.
* **Action**: Re-verification step of recovery check inspects `last_updated` within the serialized block before final commit.
* **Expected Result**: The recovery check detects the fresh heartbeat timestamp, aborts transition to `FINISHED_FAILED`, and keeps status in `PROCESSING`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-DAT-024.1` | Unit | `BaseExporter.export` | `REQ-DAT-029.1` | Specified |
| `TST-DAT-024.2` | Unit | `BaseExporter.export` | `REQ-DAT-029.1` | Specified |
| `TST-DAT-024.3` | Unit | `ExportWorker.doWork` | `REQ-DAT-029.2` | Specified |
| `TST-DAT-024.4` | Unit | `ExportWorker.doWork` | `REQ-DAT-029.2`, `REQ-DAT-029.6` | Specified |
| `TST-DAT-024.5` | Unit | `ExportStatusDatabaseManager.markDependentUploadsFailed` | `REQ-DAT-029.5` | Specified |
| `TST-DAT-024.6` | Unit | `BaseFileWriter.doExport` / `CancellationSignal` | `REQ-DAT-029.3` | Specified |
| `TST-DAT-024.7` | Unit | `ExportManager.startFullExportProcess` | `REQ-DAT-029.4` | Specified |
| `TST-DAT-024.8` | Unit | `WorkoutDeletionHelper.deleteWorkout` | `REQ-DAT-029.6` | Specified |
| `TST-DAT-024.9` | Localization | `TranslationParityTest` | `REQ-DAT-029`, `REQ-UI-106` | Specified |
| `TST-DAT-024.10` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
| `TST-DAT-024.11` | Unit | `ExportStatusDatabaseManager.getExportStatusMap` | `REQ-DAT-029.6` | Specified |
| `TST-DAT-024.12` | Unit | `ExportStatusDatabaseManager.getExportStatusMap` | `REQ-DAT-029.6` | Specified |
| `TST-DAT-024.13` | Unit | `ExportManager.newWorkout` / `exportWorkoutTo` | `REQ-DAT-029.6` | Specified |
| `TST-DAT-024.14` | Unit | `ExportStatusDatabaseManager.getExportStatusMap` | `REQ-DAT-029.6` | Specified |

