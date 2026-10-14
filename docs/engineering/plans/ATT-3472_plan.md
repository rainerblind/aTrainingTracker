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
4. **Cloud-Tethered Execution Trap & Unbounded/Unsafe File Creation**: `BackupWorker.doWork()` skipped backup creation entirely if neither Dropbox nor Google Drive was connected, providing zero on-device backups for offline athletes. Furthermore, `BackupManager.createBackup` previously deleted and rewrote directly to `aTrainingTracker_backup.attbackup` without atomic staging or synchronization, creating potential data corruption upon process death during zipping.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MIG-036` (*Automated Background Backup WorkManager Scheduling, Monotonic Timer Preservation, and Bounded Local Storage*)
* **Test Spec**: `TST-MIG-033` (*Automated Background Backup WorkManager Scheduling, Monotonic Timer Preservation, and Bounded Local Storage Verification*)
* **Living Docs Updated**:
  - `docs/requirements.md` (`REQ-MIG-036` added)
  - `docs/tests.md` (`TST-MIG-033` added)

---

## 3. System Invariants & Preserved Behavior

1. **Monotonic Schedule Preservation**: WorkManager periodic requests SHALL use `ExistingPeriodicWorkPolicy.UPDATE`, preserving elapsed execution timers across application cold starts.
2. **WorkManager Constraint & Backoff Parity**: Periodic work constraints (`NetworkType.UNMETERED`, `setRequiresBatteryNotLow(true)`) and exponential backoff (`BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS`) remain 100% identical.
3. **Exception Safety**: `schedule()` catches and logs `Throwable` to prevent crashes on devices with broken WorkManager providers (`WorkManagerResilienceTest`).
4. **Bounded Storage ($O(1)$ Disk Invariant)**: Local backup generation maintains strictly one canonical `.attbackup` archive (`aTrainingTracker_backup.attbackup`). No unbounded rolling history is permitted on disk.
5. **Atomic File Replacement**: Staging is written to `aTrainingTracker_backup.tmp` in the same directory (`context.cacheDir`) and moved atomically via `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`, falling back defensively to `REPLACE_EXISTING`. Stale `.tmp` files are purged before bundling.
6. **Concurrency Safety & Lock Contention**: `BackupManager.backupLock` (`ReentrantLock`) synchronizes backup operations. If held by a concurrent manual backup, `BackupWorker.doWork()` yields immediately and returns `Result.retry()`.
7. **Clean-Room Full Suite Regression**: 100% unit test pass rate (`./gradlew testDebugUnitTest`).

---

## 4. Proposed Architectural Changes (SWE.2)

### 4.1 Component 1: `BackupWorker.kt`
* Path: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupWorker.kt`
* **Changes**:
  1. In `Companion.schedule(context)`:
     - Replace `ExistingPeriodicWorkPolicy.REPLACE` with `ExistingPeriodicWorkPolicy.UPDATE`.
  2. In `doWork()`:
     - Change `prefs.getBoolean("automated_backups", false)` to `prefs.getBoolean("automated_backups", true)`.
     - Remove cloud requirement guard: if `!automatedEnabled`, return `Result.success()`.
     - Acquire `BackupManager.backupLock.tryLock()`. If locked, log warning and return `Result.retry()`.
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
  1. Add `val backupLock = java.util.concurrent.locks.ReentrantLock()`.
  2. In `createBackup(context, listener)`:
     - Enclose execution in `backupLock.withLock` (or synchronized).
     - Define `tempBackupFile = File(context.cacheDir, "$BACKUP_FILENAME.tmp")`.
     - Stale cleanup: `if (tempBackupFile.exists()) tempBackupFile.delete()`.
     - Zip databases, shared_prefs, and datastore into `tempBackupFile`.
     - Atomic move to destination `canonicalBackupFile = File(context.cacheDir, BACKUP_FILENAME)` using `Files.move(tempBackupFile.toPath(), canonicalBackupFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)`, with fallback to `StandardCopyOption.REPLACE_EXISTING`.
     - Return `canonicalBackupFile`.

### 4.3 Component 3: `TrainingApplication.java`
* Path: `app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`
* **Changes**:
  1. Reorder lifecycle initialization in `onCreate()`:
     - Move `PreferenceManager.setDefaultValues(...)` and `cSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)` before `initWorkManager()` and `BackupWorker.Companion.schedule(this)`.

---

## 5. UI Consistency (Rule 23)

* **UI Impact**: None. This is a background daemon scheduling, concurrency synchronization, and storage integrity ticket. No UI views, composables, or layouts are modified.

---

## 6. Atomic Implementation Step Sequence

### Step 1: Architectural Ordering in `TrainingApplication.java`
* Move SharedPreferences and default preference initialization above `initWorkManager()` and `schedule()` in `TrainingApplication.onCreate()`.
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`.
* Verification: Compile check and `TrainingApplicationTest.kt`.

### Step 2: Atomic Staging & Concurrency in `BackupManager.kt`
* Add `backupLock = ReentrantLock()`.
* Add stale `.tmp` file purge, staging to `aTrainingTracker_backup.tmp`, and atomic same-directory move via `Files.move` with `StandardCopyOption.ATOMIC_MOVE`.
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupManager.kt`.
* Verification: Create `BackupManagerTest.kt` asserting atomic move, stale file purge, and $O(1)$ storage bounds.

### Step 3: Monotonic WorkManager Scheduling & Decoupled Local Backup in `BackupWorker.kt`
* Switch `ExistingPeriodicWorkPolicy` to `UPDATE`.
* Align default preference value to `true`.
* Add `backupLock.tryLock()` contention handling returning `Result.retry()`.
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
| WorkManager Scheduling Policy (`UPDATE`) | `BackupWorker.kt` | `BackupWorkerTest.kt` | Planned |
| Preference Default Parity (`true`) | `BackupWorker.kt` | `BackupWorkerTest.kt` | Planned |
| Local Backup Without Cloud | `BackupWorker.kt` | `BackupWorkerTest.kt` | Planned |
| Lock Contention Handling | `BackupWorker.kt` | `BackupWorkerTest.kt` | Planned |
| Co-located Atomic Move & Stale Cleanup | `BackupManager.kt` | `BackupManagerTest.kt` | Planned |
| Lifecycle Ordering | `TrainingApplication.java` | `TrainingApplicationTest.kt` | Planned |
| Clean-Room Regression | Entire Codebase | Full Suite | Planned |
