# Stage 3: Implementation Plan - ATT-3472: Fix automated background backup execution and WorkManager scheduling

**Ticket**: [ATT-3472](https://atrainingtracker.atlassian.net/browse/ATT-3472)  
**Sub-task**: [ATT-3511](https://atrainingtracker.atlassian.net/browse/ATT-3511) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.3`  
**Requirement Mapping**: `REQ-MIG-036` (*Automated Background Backup WorkManager Scheduling, Monotonic Timer Preservation, and Bounded Local Storage*)  
**Test Spec ID**: `TST-MIG-033`  
**Branch**: `feature/ATT-3472`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Problem Description & Root Cause Analysis

Automated background backups have failed to execute reliably in production on devices where users launch the application frequently (at least once per day). Coupled root causes were forensically identified:
1. **Periodic Timer Reset Storm (`ExistingPeriodicWorkPolicy.REPLACE`)**: In `BackupWorker.Companion.schedule(context)`, the unique periodic work request was scheduled using `ExistingPeriodicWorkPolicy.REPLACE`. Because `BackupWorker.schedule()` is invoked in `TrainingApplication.onCreate()` on every cold start, daily app launches cancelled the existing work and reset the 24-hour delay into the future, perpetually starving the backup job.
2. **Default Preference Inconsistency**: `schedule()` and `prefs_dropbox.xml` default `"automated_backups"` to `true`. In contrast, `BackupWorker.doWork()` queried `prefs.getBoolean("automated_backups", false)`, defaulting to `false`. On devices where the user had not toggled the preference switch manually, the worker skipped execution.
3. **Application Lifecycle Ordering**: In `TrainingApplication.onCreate()`, `BackupWorker.Companion.schedule(this)` was invoked *before* `PreferenceManager.setDefaultValues()` and `cSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)`. As a result, default preferences were not loaded when scheduling occurred.
4. **Cloud-Tethered Execution Trap & Unbounded/Unsafe File Creation**: `BackupWorker.doWork()` skipped backup creation entirely if neither Dropbox nor Google Drive was connected, providing zero on-device backups for offline athletes. Furthermore, `BackupManager.createBackup` previously deleted and rewrote directly to `aTrainingTracker_backup.attbackup` in `cacheDir` without atomic staging, process-safe locking, or immunity from OS cache evictions.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MIG-036` (*Automated Background Backup WorkManager Scheduling, Monotonic Timer Preservation, and Bounded Local Storage*)
* **Test Spec**: `TST-MIG-033` (*Automated Background Backup WorkManager Scheduling, Monotonic Timer Preservation, and Bounded Local Storage Verification*)
* **Living Docs Updated**:
  - `docs/requirements.md` (`REQ-MIG-036` added)
  - `docs/tests.md` (`TST-MIG-033` added)

---

## 3. System Invariants & Preserved Behavior

1. **Monotonic Schedule Preservation & Queue Sanitization**: WorkManager periodic requests SHALL use `ExistingPeriodicWorkPolicy.UPDATE`, preserving elapsed execution timers across application cold starts. To guarantee immediate relief for existing devices stuck in legacy reset loops, a one-time queue sanitization (`cancelUniqueWork(WORK_NAME)`) is executed upon migrating to `UPDATE`.
2. **Persistent Storage in `context.filesDir`**: To ensure true local data sovereignty immune from OS cache purging under memory pressure, backups SHALL reside in persistent internal storage (`File(context.filesDir, "backups")`).
3. **Co-located Atomic Staging & Guaranteed Temp Cleanup**:
   - The staging file (`aTrainingTracker_backup.tmp`) resides in the exact same directory (`filesDir/backups`), guaranteeing identical mount point/partition for `StandardCopyOption.ATOMIC_MOVE`.
   - Any stale `.tmp` file is purged prior to zipping.
   - Staging is wrapped in a `try-finally` block: if an exception or `IOException` occurs during bundling or moving, the `.tmp` file is immediately deleted, preventing orphaned storage accumulation.
4. **Multi-Process Safe Concurrency (`FileLock` + `ReentrantLock`)**: Concurrency is guarded by a dual-layer synchronization mechanism: an in-memory `ReentrantLock` for threads within the process and an inter-process `FileLock` (`RandomAccessFile.channel.tryLock()`) on `backup.lock`. If either lock is held by a manual user operation, `BackupWorker.doWork()` yields cleanly and returns `Result.retry()`.
5. **Storage Boundedness ($O(1)$ Invariant)**: Exactly one canonical `.attbackup` archive (`aTrainingTracker_backup.attbackup`) is maintained.
6. **Decoupled Cloud Upload & Controlled Retry**: Local backup is created first. If cloud accounts are unlinked, status is `"LOCAL_SUCCESS"` and returns `Result.success()`. If cloud uploads fail, local backup remains intact and `Result.retry()` is scheduled via exponential backoff (`1 hour`).
7. **Clean-Room Full Suite Regression**: 100% unit test pass rate (`./gradlew testDebugUnitTest`).

---

## 4. Proposed Architectural Changes (SWE.2)

### 4.1 Component 1: `BackupWorker.kt`
* Path: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupWorker.kt`
* **Changes**:
  1. In `Companion.schedule(context)`:
     - Check one-time migration preference `"workmanager_backup_policy_migrated_v1"`. If false, invoke `cancelUniqueWork(WORK_NAME)` to clear stale legacy `REPLACE` queues, then set flag to `true`.
     - Register `enqueueUniquePeriodicWork` with `ExistingPeriodicWorkPolicy.UPDATE`.
  2. In `doWork()`:
     - Change `prefs.getBoolean("automated_backups", false)` to `prefs.getBoolean("automated_backups", true)`.
     - Remove cloud requirement guard: if `!automatedEnabled`, return `Result.success()`.
     - Try acquiring lock via `BackupManager.acquireLock(applicationContext)`. If unavailable, log warning and return `Result.retry()`.
     - Call `val backupFile = BackupManager.createBackup(applicationContext)`. If null, update status to `"Local backup creation failed"` and return `Result.retry()`.
     - If neither Dropbox nor Google Drive is connected:
       - Update `last_backup_timestamp` and `last_backup_status = "LOCAL_SUCCESS"`.
       - Return `Result.success()`.
     - If cloud provider(s) are connected:
       - Dispatch upload to connected providers.
       - If any upload succeeds, record `last_backup_timestamp` and status (`"SUCCESS"` or `"PARTIAL_SUCCESS"`), return `Result.success()`.
       - If all connected uploads fail, local backup remains intact, update status to `"Upload failed"`, and return `Result.retry()`.

### 4.2 Component 2: `BackupManager.kt`
* Path: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupManager.kt`
* **Changes**:
  1. Storage Directory: `fun getBackupDir(context: Context): File = File(context.filesDir, "backups").apply { mkdirs() }`.
  2. Process-Safe Lock Management:
     - `val jvmLock = ReentrantLock()`
     - `fun acquireLock(context: Context): AutoCloseable?` attempting both `jvmLock.tryLock()` and `FileChannel.tryLock()` on `File(getBackupDir(context), "backup.lock")`.
  3. In `createBackup(context, listener)`:
     - Wrap execution in lock.
     - `val backupDir = getBackupDir(context)`
     - `val tempFile = File(backupDir, "$BACKUP_FILENAME.tmp")`
     - `val canonicalFile = File(backupDir, BACKUP_FILENAME)`
     - `try { ... } finally { if (tempFile.exists()) tempFile.delete() }`
     - Move via `Files.move(tempFile.toPath(), canonicalFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)` (falling back defensively to `StandardCopyOption.REPLACE_EXISTING`).
     - Return `canonicalFile`.

### 4.3 Component 3: `TrainingApplication.java`
* Path: `app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`
* **Changes**:
  1. Reorder lifecycle initialization in `onCreate()`:
     - Move `PreferenceManager.setDefaultValues(...)` and `cSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)` before `initWorkManager()` and `BackupWorker.Companion.schedule(this)`.

---

## 5. UI Consistency (Rule 23)

* **UI Impact**: None. This is a background daemon scheduling, multi-process concurrency synchronization, and storage integrity ticket. No UI views, composables, or layouts are modified.

---

## 6. Atomic Implementation Step Sequence

### Step 1: Architectural Ordering in `TrainingApplication.java`
* Move SharedPreferences and default preference initialization above `initWorkManager()` and `schedule()` in `TrainingApplication.onCreate()`.
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`.
* Verification: Compile check and `TrainingApplicationTest.kt`.

### Step 2: Persistent Storage, Atomic Staging & Process-Safe Lock in `BackupManager.kt`
* Add `getBackupDir(context)` targeting `context.filesDir/backups`.
* Add `acquireLock(context)` providing dual JVM `ReentrantLock` and inter-process `FileLock`.
* Add stale `.tmp` purge, `try-finally` cleanup guarantee, and atomic same-partition move via `Files.move(..., ATOMIC_MOVE)`.
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupManager.kt`.
* Verification: Create `BackupManagerTest.kt` asserting atomic move, stale file purge, `try-finally` error cleanup on failed move, and $O(1)$ storage bounds.

### Step 3: Monotonic WorkManager Scheduling, Queue Sanitization & Local Backup in `BackupWorker.kt`
* Add one-time migration queue sanitization (`cancelUniqueWork`).
* Switch `ExistingPeriodicWorkPolicy` to `UPDATE`.
* Align default preference value to `true`.
* Add `BackupManager.acquireLock()` contention handling returning `Result.retry()`.
* Decouple local backup creation from cloud connection (record `"LOCAL_SUCCESS"`).
* On cloud upload failure, preserve local backup and return `Result.retry()`.
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupWorker.kt`.
* Verification: Create/update `BackupWorkerTest.kt` and update `BackupWorkerGoogleDriveTest.kt`.

### Step 4: Targeted Test Suite Execution
* Run: `./gradlew testDebugUnitTest --tests "*BackupWorker*" --tests "*BackupManager*"`.
* Verify 100% pass rate.

### Step 5: Full Clean-Room Regression Suite Execution
* Run: `./gradlew testDebugUnitTest`.
* Verify 0 failures, 0 regressions across all modules.

---

## 7. ASPICE Verification Matrix

| Verification Scope | Target File | Test Class | Status |
| :--- | :--- | :--- | :--- |
| WorkManager Scheduling Policy (`UPDATE`) & Sanitization | `BackupWorker.kt` | `BackupWorkerTest.kt` | Planned |
| Preference Default Parity (`true`) | `BackupWorker.kt` | `BackupWorkerTest.kt` | Planned |
| Local Backup in Persistent `filesDir` | `BackupWorker.kt` | `BackupWorkerTest.kt` | Planned |
| Multi-Process Safe Lock Contention (`FileLock`) | `BackupWorker.kt` | `BackupWorkerTest.kt` | Planned |
| Co-located Atomic Move, Stale Purge & Error Cleanup | `BackupManager.kt` | `BackupManagerTest.kt` | Planned |
| Lifecycle Ordering | `TrainingApplication.java` | `TrainingApplicationTest.kt` | Planned |
| Clean-Room Regression | Entire Codebase | Full Suite | Planned |
