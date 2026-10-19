# Stage 3: Implementation Plan: ATT-3995

**Ticket**: [ATT-3995](https://rainerblind.atlassian.net/browse/ATT-3995)  
**Subtask**: `ATT-4188` (`[Impl-Plan]`)  
**Parent Epic**: None (*Product Backlog*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-DAT-029` (*Resilient Background Export & Upload Pipeline, Attempt Capping, Cooperative Cancellation, Transactional Upload Remediation, and Stuck State Recovery*)  
**Test Case Mapping**: `TST-DAT-024` (`TST-DAT-024.1` – `TST-DAT-024.16`)  
**Branch**: `feature/ATT-3995`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Executive Summary & Problem Description

In `com.atrainingtracker.trainingtracker.exporter`, background export and cloud upload workers suffer from systemic reliability and lifecycle defects:
1. **Infinite Retries on Deterministic Errors**: `BaseExporter.export()` catches general exceptions and marks them as retryable, while `ExportWorker.doWork()` lacks an attempt cap. Deterministic errors (e.g. `ParseException`, `JSONException`, invalid sample parameters) cause workers to retry indefinitely, draining battery and flooding system logs.
2. **Stranded `PROCESSING` Status on Uncaught Exceptions**: When an unexpected `RuntimeException` escapes `ExportWorker.doWork()`, `informOthersFailed()` is bypassed, abandoning the database row permanently in `PROCESSING` with a perpetual spinning indicator.
3. **Orphaned `WAITING` Uploads on File Export Failure**: When local file creation fails permanently, WorkManager cancels downstream dependent cloud uploads (Dropbox, Google Drive, Strava) without executing their workers, leaving dependent rows stranded in `WAITING` indefinitely.
4. **Ineffective Cooperative Cancellation & Tag Mismatch**: `ExportManager.invalidateWorkoutExport(fileBaseName)` cancels by `fileBaseName`, but `WorkRequest`s are tagged exclusively with `exportInfo.toString()`, making cancellation a no-op. Furthermore, file writer cursor loops lack cancellation polling and continue processing large workouts even after a workout is cropped or cancelled.
5. **Decoupled Non-Blocking Recovery, Attempt ID Matching & Race Defense**: Stale `PROCESSING` rows from process death are never cleaned up, while arbitrary timeouts risk false positives during heavy I/O. Recovery requires a non-blocking 3-phase double-checked WorkManager liveness guard without holding locks across asynchronous WorkManager calls, accompanied by attempt count matching, grace period padding, periodic heartbeats, and timestamp re-verification.
6. **Input Sanitization & Deletion Safety**: Corrupted or `"null"` base file names cause crashes in `WorkoutDeletionHelper` and pollute `ExportStatus.db`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-DAT-029`
  * `REQ-DAT-029.1`: Deterministic vs retryable error classification in `BaseExporter.export()`. Catch `Throwable`; deterministic errors (`ParseException`, `JSONException`, `RuntimeException`) yield `shallRetry = false`; transient `IOException` yields `shallRetry = true`.
  * `REQ-DAT-029.2`: WorkManager retry capping (`MAX_RETRIES = 3`). `ExportWorker` returns `Result.retry()` only when `runAttemptCount < MAX_RETRIES` and `result.shallRetry()`. Otherwise, terminates with `informOthersFailed()` and `Result.failure()`.
  * `REQ-DAT-029.3`: Cooperative cancellation signal in `BaseExporter` wired from `ExportWorker.isStopped`. Checked in file writer cursor loops at the start of every iteration and immediately after blocking I/O, deleting partial files and closing streams.
  * `REQ-DAT-029.4`: Unique work enqueuing via `WorkManager.beginUniqueWork(getUniqueWorkName(fileBaseName, fileFormat), ExistingWorkPolicy.REPLACE, ...)` and dual tagging (`fileBaseName` and `exportInfo.toString()`). Synchronous invalidation in `invalidateWorkoutExport`.
  * `REQ-DAT-029.5`: Atomic dependent upload failure cascading via `ExportStatusDatabaseManager.markDependentUploadsFailed(fileBaseName, fileFormat, errorMsg)` in a dedicated SQLite transaction (`beginTransaction` / `setTransactionSuccessful`) with comprehensive `try-catch(Throwable)` logging.
  * `REQ-DAT-029.6`: Self-healing stuck state recovery bound to `MAX_EXPORT_DURATION_MS = 15 * 60 * 1000L` with `RECOVERY_GRACE_PERIOD_MS = 30_000L`, 15s periodic heartbeats, decoupled non-blocking WorkManager liveness guard (no locks across WorkManager calls), attempt count matching, heartbeat re-verification before final transition, entry sanitization for `fileBaseName`, and safe workout deletion in `WorkoutDeletionHelper`.
* **Test Case Mapping**: `TST-DAT-024` (`TST-DAT-024.1` through `TST-DAT-024.16`).

---

## 3. System Invariants & Preserved Behavior

1. **Format Generator Integrity**: Existing valid export generators (FIT, GPX, TCX, CSV, GC) continue functioning seamlessly without output degradation.
2. **Definitive Status Invariant**: Every export operation terminates in a definitive status (`FINISHED_SUCCESS` or `FINISHED_FAILED`), never lingering permanently in `PROCESSING` or `WAITING`.
3. **Strict Retry Boundary**: WorkManager attempts are capped at 3 with exponential backoff.
4. **Transactional Database Integrity**: Database mutations in `ExportStatus.db` preserve ACID transactionality under concurrency, process death, and cancellation.
5. **No Locks Across Asynchronous IPC**: Database locks or synchronized blocks are NEVER held while querying external systems like `WorkManager`.
6. **Non-Blocking Caller Execution**: WorkManager liveness checks execute asynchronously on background executors (`Dispatchers.IO`), never blocking the calling thread or UI thread with synchronous `.get()`.
7. **UI Consistency (Rule 23)**: No UI layouts, colors, typography, or Jetpack Compose themes are changed; background worker pipelines and status repository integrity are preserved.
8. **One Commit per Ticket (Rule 44)**: Changes remain on `feature/ATT-3995` without intermediate commits until squashed into `sprint/2026-42.10` in Stage 5.

---

## 4. Proposed Architectural Design (SWE.2)

### 4.1 Central Constants: `ExportConfig.kt`
Centralize operational limits and timing parameters:
```kotlin
object ExportConfig {
    const val MAX_RETRIES = 3
    const val HEARTBEAT_INTERVAL_MS = 15_000L // 15 seconds
    const val MAX_EXPORT_DURATION_MS = 15 * 60 * 1000L // 15 minutes
    const val RECOVERY_GRACE_PERIOD_MS = 30_000L // 30 seconds
}
```

### 4.2 Component 1: `BaseExporter.java`
- **CancellationSignal Interface**:
  ```java
  public interface CancellationSignal {
      boolean isCancelled();
  }
  protected volatile CancellationSignal mCancellationSignal;
  public void setCancellationSignal(CancellationSignal signal) {
      this.mCancellationSignal = signal;
  }
  public boolean isCancelled() {
      return mCancellationSignal != null && mCancellationSignal.isCancelled();
  }
  ```
- **Error Classification in `export()`**:
  ```java
  public ExportResult export(ExportInfo exportInfo) {
      try {
          if (isCancelled()) {
              return new ExportResult(false, false, "Export cancelled");
          }
          return doExport(exportInfo);
      } catch (CancellationException e) {
          Log.i(TAG, "Export cancelled cooperatively: " + exportInfo);
          return new ExportResult(false, false, "Export cancelled");
      } catch (IOException e) {
          Log.w(TAG, "Transient I/O failure during export: " + exportInfo, e);
          return new ExportResult(false, true, "Transient I/O error: " + e.getMessage());
      } catch (Throwable t) {
          Log.e(TAG, "Deterministic or unexpected fatal error during export: " + exportInfo, t);
          return new ExportResult(false, false, "Fatal error: " + t.getMessage());
      }
  }
  ```

### 4.3 Component 2: File Writers (`BaseFileWriter`, `FitFileWriter`, `GPXFileWriter`, `TCXFileWriter`, `CSVFileWriter`, `GCFileWriter`)
- **Cancellation Polling**: In sample cursor traversal loops:
  ```java
  while (cursor.moveToNext()) {
      if (isCancelled()) {
          throw new CancellationException("Export cancelled by user or system");
      }
      writeSampleRecord(cursor);
      if (isCancelled()) {
          throw new CancellationException("Export cancelled by user or system post-I/O");
      }
  }
  ```
- **Cancellation Handling & Caller Safety**:
  - All file writer `doExport` implementations explicitly catch `CancellationException`.
  - Upon cancellation:
    1. Immediately close output streams and database cursors in `try-with-resources` / `finally`.
    2. Delete the partial output file:
       ```java
       if (outputFile != null && outputFile.exists()) {
           boolean deleted = outputFile.delete();
           Log.i(TAG, "Deleted partial file on cancellation: " + outputFile.getAbsolutePath() + " (success=" + deleted + ")");
       }
       ```
    3. Re-throw `CancellationException` to `BaseExporter.export()`, which catches it cleanly and returns `ExportResult(false, false, "Export cancelled")` without crashing the worker process or escaping unhandled.

### 4.4 Component 3: `ExportWorker.kt`
- **Max Retries Policy, Heartbeat Ticker & Attempt Recording**:
  ```kotlin
  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
      if (isStopped) {
          Log.i(TAG, "Worker stopped before execution: $mExportInfo")
          return@withContext Result.failure()
      }
      mExporter?.setCancellationSignal { isStopped }
      
      // Record current attempt count and initial heartbeat
      ExportStatusDatabaseManager.getInstance(applicationContext)
          .recordWorkerAttempt(mExportInfo.fileBaseName, mExportInfo.exportType, mExportInfo.fileFormat, runAttemptCount)

      // Periodic heartbeat ticker to prove worker liveness
      val heartbeatJob = launch(Dispatchers.IO) {
          while (isActive && !isStopped) {
              delay(ExportConfig.HEARTBEAT_INTERVAL_MS)
              ExportStatusDatabaseManager.getInstance(applicationContext)
                  .updateHeartbeat(mExportInfo.fileBaseName, mExportInfo.exportType, mExportInfo.fileFormat)
          }
      }

      try {
          updateStatus(ExportStatus.PROCESSING)
          val result = mExporter.export(mExportInfo)
          if (isStopped) {
              Log.i(TAG, "Worker stopped during/after execution: $mExportInfo")
              return@withContext Result.failure()
          }
          if (result.isSuccess) {
              updateStatus(ExportStatus.FINISHED_SUCCESS)
              Result.success()
          } else if (result.shallRetry() && runAttemptCount < ExportConfig.MAX_RETRIES) {
              updateStatus(ExportStatus.WAITING, "Retry attempt ${runAttemptCount + 1}/${ExportConfig.MAX_RETRIES}: ${result.message}")
              Result.retry()
          } else {
              informOthersFailed(result.message)
              if (mExportInfo.exportType == ExportType.FILE) {
                  ExportStatusDatabaseManager.getInstance(applicationContext)
                      .markDependentUploadsFailed(mExportInfo.fileBaseName, mExportInfo.fileFormat, result.message)
              }
              Result.failure()
          }
      } catch (t: Throwable) {
          Log.e(TAG, "Uncaught exception in ExportWorker: $mExportInfo", t)
          informOthersFailed("Unexpected worker error: ${t.message}")
          if (mExportInfo?.exportType == ExportType.FILE) {
              ExportStatusDatabaseManager.getInstance(applicationContext)
                  .markDependentUploadsFailed(mExportInfo.fileBaseName, mExportInfo.fileFormat, t.message)
          }
          Result.failure()
      } finally {
          heartbeatJob.cancel()
      }
  }
  ```

### 4.5 Component 4: `ExportManager.java`
- **Unique Work Helper & Dual Tagging**:
  ```java
  public static String getUniqueWorkName(@NonNull String fileBaseName, @NonNull FileFormat fileFormat) {
      return "export_" + fileBaseName + "_" + fileFormat.name();
  }
  ```
- **Enqueueing with REPLACE Policy**:
  ```java
  OneTimeWorkRequest fileCreationWork = new OneTimeWorkRequest.Builder(ExportWorker.class)
      .setInputData(data)
      .addTag(fileBaseName)
      .addTag(exportInfo.toString())
      .build();

  workManager.beginUniqueWork(
      getUniqueWorkName(fileBaseName, fileFormat),
      ExistingWorkPolicy.REPLACE,
      fileCreationWork
  ).enqueue();
  ```
- **Synchronous Invalidation in `invalidateWorkoutExport(fileBaseName)`**:
  - Cancel WorkManager by tag: `workManager.cancelAllWorkByTag(fileBaseName)`.
  - Cancel unique work for all formats:
    ```java
    for (FileFormat format : FileFormat.values()) {
        workManager.cancelUniqueWork(getUniqueWorkName(fileBaseName, format));
    }
    ```
  - Cancel legacy tags: `workManager.cancelAllWorkByTag(fileBaseName + "_" + format.name())`.
  - Delete output files from filesystem and reset database rows to `WAITING`.
- **Input Sanitization**:
  - Validate `fileBaseName`: reject if null, empty, blank, or `"null"`.

### 4.6 Component 5: `ExportStatusDatabaseManager.java`
- **Schema & Columns**:
  - Include `RUN_ATTEMPT_COUNT` (integer, default 0) and `LAST_UPDATED` (long).
- **Entry Validation**:
  - Check `fileBaseName != null && !fileBaseName.trim().isEmpty() && !"null".equalsIgnoreCase(fileBaseName.trim())`.
- **Attempt & Heartbeat API**:
  ```java
  public void recordWorkerAttempt(String fileBaseName, ExportType exportType, FileFormat fileFormat, int attempt) {
      if (!isValidBaseName(fileBaseName)) return;
      SQLiteDatabase db = getWritableDatabase();
      ContentValues cv = new ContentValues();
      cv.put(RUN_ATTEMPT_COUNT, attempt);
      cv.put(LAST_UPDATED, System.currentTimeMillis());
      db.update(TABLE, cv, FILE_BASE_NAME + " = ? AND EXPORT_TYPE = ? AND FILE_FORMAT = ?",
          new String[]{fileBaseName, exportType.name(), fileFormat.name()});
  }

  public void updateHeartbeat(String fileBaseName, ExportType exportType, FileFormat fileFormat) {
      if (!isValidBaseName(fileBaseName)) return;
      SQLiteDatabase db = getWritableDatabase();
      ContentValues cv = new ContentValues();
      cv.put(LAST_UPDATED, System.currentTimeMillis());
      db.update(TABLE, cv, FILE_BASE_NAME + " = ? AND EXPORT_TYPE = ? AND FILE_FORMAT = ? AND STATUS = ?",
          new String[]{fileBaseName, exportType.name(), fileFormat.name(), String.valueOf(ExportStatus.PROCESSING.ordinal())});
  }
  ```
- **Atomic Dependent Upload Failure**:
  ```java
  public void markDependentUploadsFailed(String fileBaseName, FileFormat fileFormat, String reason) {
      if (!isValidBaseName(fileBaseName)) return;
      SQLiteDatabase db = getWritableDatabase();
      try {
          db.beginTransaction();
          ContentValues cv = new ContentValues();
          cv.put(STATUS, ExportStatus.FINISHED_FAILED.ordinal());
          cv.put(ANSWER, "Prerequisite file creation failed: " + reason);
          cv.put(LAST_UPDATED, System.currentTimeMillis());
          int updated = db.update(TABLE, cv,
              FILE_BASE_NAME + " = ? AND FILE_FORMAT = ? AND EXPORT_TYPE != ? AND STATUS = ?",
              new String[]{fileBaseName, fileFormat.name(), ExportType.FILE.name(), String.valueOf(ExportStatus.WAITING.ordinal())});
          db.setTransactionSuccessful();
          Log.i(TAG, "Marked " + updated + " dependent uploads failed for " + fileBaseName + " [" + fileFormat + "]");
      } catch (Throwable t) {
          Log.e(TAG, "Failed to mark dependent uploads failed for " + fileBaseName + " [" + fileFormat + "]", t);
      } finally {
          db.endTransaction();
      }
  }
  ```
- **Decoupled Non-Blocking 3-Phase Stuck State Recovery Pattern**:
  ```java
  public void healStaleProcessingRowsAsync(String fileBaseName, Executor backgroundExecutor) {
      if (!isValidBaseName(fileBaseName)) return;
      backgroundExecutor.execute(() -> {
          long now = System.currentTimeMillis();
          long cutoff = now - (ExportConfig.MAX_EXPORT_DURATION_MS + ExportConfig.RECOVERY_GRACE_PERIOD_MS);

          // Phase 1: Candidate scan (read-only, cursor closed immediately, NO locks held)
          List<CandidateRow> candidates = new ArrayList<>();
          SQLiteDatabase rDb = getReadableDatabase();
          try (Cursor c = rDb.query(TABLE, new String[]{ID, FILE_FORMAT, EXPORT_TYPE, LAST_UPDATED, RUN_ATTEMPT_COUNT},
                  FILE_BASE_NAME + " = ? AND STATUS = ? AND LAST_UPDATED < ?",
                  new String[]{fileBaseName, String.valueOf(ExportStatus.PROCESSING.ordinal()), String.valueOf(cutoff)},
                  null, null, null)) {
              while (c != null && c.moveToNext()) {
                  candidates.add(new CandidateRow(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getInt(4)));
              }
          }
          if (candidates.isEmpty()) return;

          // Phase 2: Asynchronous WorkManager liveness query (NO DB locks held)
          ListenableFuture<List<WorkInfo>> future = WorkManager.getInstance(mContext).getWorkInfosByTag(fileBaseName);
          future.addListener(() -> {
              try {
                  List<WorkInfo> workInfos = future.get();
                  boolean hasLiveWorker = false;
                  for (WorkInfo info : workInfos) {
                      if (info.getState() == WorkInfo.State.RUNNING || info.getState() == WorkInfo.State.ENQUEUED) {
                          hasLiveWorker = true;
                          break;
                      }
                  }
                  if (hasLiveWorker) {
                      Log.i(TAG, "WorkManager confirms worker still active for " + fileBaseName + "; suppressing timeout");
                      return;
                  }

                  // Phase 3: Short-lived serialized transaction with Attempt ID & Heartbeat re-verification
                  SQLiteDatabase wDb = getWritableDatabase();
                  try {
                      wDb.beginTransaction();
                      for (CandidateRow candidate : candidates) {
                          RowSnapshot snapshot = queryRowSnapshot(wDb, candidate.id);
                          // Re-verify that LAST_UPDATED and RUN_ATTEMPT_COUNT match candidate scan exactly
                          if (snapshot != null && snapshot.status == ExportStatus.PROCESSING.ordinal()
                                  && snapshot.lastUpdated == candidate.lastUpdated
                                  && snapshot.runAttemptCount == candidate.runAttemptCount
                                  && (System.currentTimeMillis() - snapshot.lastUpdated) > (ExportConfig.MAX_EXPORT_DURATION_MS + ExportConfig.RECOVERY_GRACE_PERIOD_MS)) {
                              ContentValues cv = new ContentValues();
                              cv.put(STATUS, ExportStatus.FINISHED_FAILED.ordinal());
                              cv.put(ANSWER, "Export timed out / process terminated");
                              cv.put(LAST_UPDATED, System.currentTimeMillis());
                              wDb.update(TABLE, cv, ID + " = ?", new String[]{String.valueOf(candidate.id)});
                              Log.w(TAG, "Lazily transitioned stale export row " + candidate.id + " to FINISHED_FAILED");
                          } else {
                              Log.i(TAG, "Attempt or heartbeat updated concurrently for row " + candidate.id + "; aborted failure transition");
                          }
                      }
                      wDb.setTransactionSuccessful();
                  } catch (Throwable t) {
                      Log.e(TAG, "Failed lazy stuck state recovery for " + fileBaseName, t);
                  } finally {
                      wDb.endTransaction();
                  }
              } catch (Exception e) {
                  Log.w(TAG, "WorkManager liveness check failed for " + fileBaseName + "; aborting recovery", e);
              }
          }, backgroundExecutor);
      });
  }
  ```

### 4.7 Component 7: `WorkoutDeletionHelper.java`
- Add defensive base name guard and cancel active WorkManager tasks:
  ```java
  if (fileBaseName != null && !fileBaseName.trim().isEmpty() && !"null".equalsIgnoreCase(fileBaseName.trim())) {
      ExportManager.getInstance(context).invalidateWorkoutExport(fileBaseName);
      WorkManager.getInstance(context).cancelAllWorkByTag(fileBaseName);
  }
  ```

---

## 5. Step-by-Step Implementation Steps

### Step 1: Create `ExportConfig.kt` and Update `BaseExporter.java` / File Writers
- Centralize `MAX_RETRIES`, `HEARTBEAT_INTERVAL_MS`, `MAX_EXPORT_DURATION_MS`, `RECOVERY_GRACE_PERIOD_MS` in `ExportConfig`.
- Add `CancellationSignal` interface, setter, and getter to `BaseExporter`.
- Refactor `BaseExporter.export()` with `Throwable` catch and deterministic vs transient classification.
- Add loop cancellation checks (`isCancelled()`) at start of loop and post-I/O in `BaseFileWriter`.
- Update all file writers (`FitFileWriter`, `GPXFileWriter`, `TCXFileWriter`, `CSVFileWriter`, `GCFileWriter`) to catch `CancellationException`, clean up partial files on abort via `outputFile.delete()`, safely close streams, and re-throw to `BaseExporter.export()`.

### Step 2: Update `ExportWorker.kt`
- Enforce `ExportConfig.MAX_RETRIES` attempt cap.
- Record `runAttemptCount` via `recordWorkerAttempt`.
- Launch periodic coroutine heartbeat ticker refreshing `ExportStatusDatabaseManager.updateHeartbeat` every 15 seconds.
- Wire `isStopped` into `mExporter.setCancellationSignal { isStopped }`.
- Enclose `doWork()` in top-level `try-catch(Throwable)` fail-safe to write `FINISHED_FAILED` if an unexpected exception occurs.
- On permanent failure of `ExportType.FILE`, invoke `ExportStatusDatabaseManager.markDependentUploadsFailed(fileBaseName, fileFormat, message)`.

### Step 3: Update `ExportManager.java`
- Implement `getUniqueWorkName(fileBaseName, fileFormat)`.
- Enqueue `fileCreationWork` via `beginUniqueWork(..., ExistingWorkPolicy.REPLACE, ...)`.
- Tag work requests with both `fileBaseName` and `exportInfo.toString()`.
- Update `invalidateWorkoutExport(fileBaseName)` to cancel by `fileBaseName` tag, unique names, and legacy tags before deleting files.
- Add entry validation for `fileBaseName`.

### Step 4: Update `ExportStatusDatabaseManager.java`
- Add entry sanitization for `fileBaseName`.
- Implement `recordWorkerAttempt` and `updateHeartbeat` methods.
- Add `markDependentUploadsFailed` with SQLite transaction and comprehensive error logging.
- Implement decoupled non-blocking 3-phase stuck state recovery pattern with `ListenableFuture.addListener`, attempt ID matching, grace period padding, and timestamp re-verification.
- Ensure `last_updated` is refreshed on all status updates.

### Step 5: Update `WorkoutDeletionHelper.java`
- Add defensive base name guard against null, empty, or `"null"`.
- Cancel pending WorkManager tasks for `fileBaseName` when deleting workouts.

### Step 6: Author Comprehensive Unit Tests
- `BaseExporterTest.kt`: Tests `TST-DAT-024.1`, `TST-DAT-024.2`, `TST-DAT-024.6`.
- `ExportWorkerTest.kt`: Tests `TST-DAT-024.3`, `TST-DAT-024.4`.
- `ExportStatusDatabaseManagerTest.kt`: Tests `TST-DAT-024.5`, `TST-DAT-024.11`, `TST-DAT-024.12`, `TST-DAT-024.14`, `TST-DAT-024.15`, `TST-DAT-024.16`.
- `ExportManagerTest.kt`: Tests `TST-DAT-024.7`, `TST-DAT-024.13`.
- `WorkoutDeletionHelperTest.kt`: Tests `TST-DAT-024.8`.
- 9-Language Localization Audit: Test `TST-DAT-024.9`.

### Step 7: Clean-Room Regression Verification
- Run `./gradlew testDebugUnitTest` verifying 100% pass rate (`TST-DAT-024.10`).

---

## 6. Thread & Dispatcher Architecture

| Component / Action | Thread / Dispatcher | Concurrency & Synchronization Guard |
|---|---|---|
| `ExportWorker.doWork()` | `Dispatchers.IO` | `isStopped` check, WorkManager attempt counter |
| Worker Heartbeat Ticker | `Dispatchers.IO` (coroutine) | Non-blocking coroutine loop, cancelled in `finally` |
| `BaseExporter.export()` | Background worker thread | Thread-safe `volatile CancellationSignal` |
| File Writer Sample Cursor Loop | Background worker thread | `isCancelled()` at loop start and post-I/O |
| `markDependentUploadsFailed` | Background worker thread | Dedicated SQLite native transaction (`beginTransaction`) |
| Stuck State Candidate Scan (Phase 1) | Background thread / Executor | Read-only SQLite query, cursor closed immediately |
| WorkManager Liveness Check (Phase 2) | Background thread / Executor | Non-blocking `ListenableFuture.addListener` (NO DB locks held) |
| Stuck State Re-verify & Commit (Phase 3) | Background thread / Executor | Short-lived SQLite write transaction with attempt matching |
| Work Invalidation / Cancel | Caller thread | `WorkManager.cancelAllWorkByTag`, `cancelUniqueWork` |

---

## 7. Verification Commands

1. Targeted tests:
   ```bash
   ./gradlew testDebugUnitTest --tests "*Exporter*" --tests "*ExportWorker*" --tests "*ExportStatusDatabaseManager*" --tests "*ExportManager*" --tests "*WorkoutDeletionHelper*"
   ```
2. Clean-room full test suite:
   ```bash
   ./gradlew testDebugUnitTest
   ```

---

## 8. Traceability Matrix

| Requirement Clause | Implementation Component | Implementation Step | Test Case ID | Status |
|---|---|---|---|---|
| `REQ-DAT-029.1` (Deterministic vs transient error classification) | `BaseExporter.java` | Step 1 | `TST-DAT-024.1`, `TST-DAT-024.2` | Planned |
| `REQ-DAT-029.2` (WorkManager retry capping & backoff) | `ExportWorker.kt`, `ExportConfig.kt` | Step 1, Step 2 | `TST-DAT-024.3` | Planned |
| `REQ-DAT-029.3` (Cooperative cancellation & file cleanup) | `BaseExporter.java`, File writers | Step 1 | `TST-DAT-024.6` | Planned |
| `REQ-DAT-029.4` (Unique work enqueuing & tagged invalidation) | `ExportManager.java` | Step 3 | `TST-DAT-024.7` | Planned |
| `REQ-DAT-029.5` (Atomic dependent upload failure cascading) | `ExportStatusDatabaseManager.java`, `ExportWorker.kt` | Step 2, Step 4 | `TST-DAT-024.5` | Planned |
| `REQ-DAT-029.6` (Decoupled non-blocking stuck state recovery) | `ExportStatusDatabaseManager.java` | Step 4 | `TST-DAT-024.11`, `TST-DAT-024.12` | Planned |
| `REQ-DAT-029.6` (Concurrent heartbeat race resilience) | `ExportStatusDatabaseManager.java` | Step 4 | `TST-DAT-024.14` | Planned |
| `REQ-DAT-029.6` (Active export heartbeat preservation) | `ExportStatusDatabaseManager.java`, `ExportWorker.kt` | Step 2, Step 4 | `TST-DAT-024.15` | Planned |
| `REQ-DAT-029.6` (Worker I/O in-flight grace period & attempt matching) | `ExportStatusDatabaseManager.java`, `ExportConfig.kt` | Step 1, Step 4 | `TST-DAT-024.16` | Planned |
| `REQ-DAT-029.6` (Entry sanitation for fileBaseName) | `ExportManager.java`, `ExportStatusDatabaseManager.java` | Step 3, Step 4 | `TST-DAT-024.13` | Planned |
| `REQ-DAT-029.6` (Safe deletion in WorkoutDeletionHelper) | `WorkoutDeletionHelper.java` | Step 5 | `TST-DAT-024.8` | Planned |
| `REQ-DAT-029` / `REQ-UI-106` (9-language localization audit) | `strings.xml` (all 9 locales) | Step 6 | `TST-DAT-024.9` | Planned |
| `REQ-PRO-001` (Clean-room full test suite regression pass) | Project test suite | Step 7 | `TST-DAT-024.10` | Planned |
